"""Lettura dell'intestazione WAV.

L'app invia sempre WAV PCM 16 bit mono a 16 kHz (lo prepara ffmpeg sul
telefono). Serve conoscerne la durata per applicare le quote: fidarsi di
un valore dichiarato dal client significherebbe lasciare che chiunque
dichiari zero secondi.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass


class AudioError(ValueError):
    """Il file ricevuto non è un WAV leggibile."""


@dataclass(frozen=True)
class WavInfo:
    channels: int
    sample_rate: int
    bits_per_sample: int
    data_bytes: int

    @property
    def seconds(self) -> float:
        frame = self.channels * max(1, self.bits_per_sample // 8)
        if self.sample_rate <= 0 or frame <= 0:
            raise AudioError("WAV con intestazione incoerente.")
        return self.data_bytes / (self.sample_rate * frame)


def read_wav_info(payload: bytes) -> WavInfo:
    """Estrae i parametri dall'intestazione RIFF/WAVE.

    Percorre i blocchi invece di assumere i canonici 44 byte: molti
    encoder inseriscono un blocco `LIST` prima dei dati.
    """
    if len(payload) < 12 or payload[:4] != b"RIFF" or payload[8:12] != b"WAVE":
        raise AudioError("Il file non è un WAV.")

    pos = 12
    fmt: tuple[int, int, int] | None = None
    data_bytes: int | None = None

    while pos + 8 <= len(payload):
        chunk_id = payload[pos : pos + 4]
        (chunk_size,) = struct.unpack_from("<I", payload, pos + 4)
        body = pos + 8

        if chunk_id == b"fmt " and chunk_size >= 16 and body + 16 <= len(payload):
            _, channels, sample_rate, _, _, bits = struct.unpack_from(
                "<HHIIHH", payload, body
            )
            fmt = (channels, sample_rate, bits)
        elif chunk_id == b"data":
            # Un file troncato dichiara più byte di quanti ne porta: vale
            # quello che è arrivato davvero.
            data_bytes = min(chunk_size, len(payload) - body)
            break

        # I blocchi sono allineati a 2 byte.
        pos = body + chunk_size + (chunk_size % 2)

    if fmt is None or data_bytes is None:
        raise AudioError("WAV senza blocchi 'fmt ' e 'data'.")

    channels, sample_rate, bits = fmt
    return WavInfo(
        channels=channels,
        sample_rate=sample_rate,
        bits_per_sample=bits,
        data_bytes=data_bytes,
    )
