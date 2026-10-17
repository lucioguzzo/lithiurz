"""Lettura dell'intestazione WAV: è da lì che si ricava la durata."""

import struct

import pytest

from app.audio import AudioError, read_wav_info


def wav(seconds: float, sample_rate: int = 16000, channels: int = 1,
        bits: int = 16, extra_chunk: bool = False) -> bytes:
    frame = channels * bits // 8
    data = b"\x00" * int(seconds * sample_rate * frame)
    fmt = struct.pack("<HHIIHH", 1, channels, sample_rate,
                      sample_rate * frame, frame, bits)
    blocks = b"fmt " + struct.pack("<I", len(fmt)) + fmt
    if extra_chunk:
        info = b"INFOprova"
        # I blocchi RIFF di dimensione dispari portano un byte di
        # riempimento: senza, il file non è conforme.
        blocks += b"LIST" + struct.pack("<I", len(info)) + info + b"\x00"
    blocks += b"data" + struct.pack("<I", len(data)) + data
    return b"RIFF" + struct.pack("<I", 4 + len(blocks)) + b"WAVE" + blocks


def test_durata_di_un_wav_di_trenta_secondi():
    info = read_wav_info(wav(30))
    assert info.sample_rate == 16000
    assert info.channels == 1
    assert info.seconds == pytest.approx(30, abs=0.01)


def test_blocco_list_prima_dei_dati_non_confonde_il_calcolo():
    # Molti encoder infilano un blocco LIST fra 'fmt ' e 'data': saltare
    # i 44 byte canonici darebbe una durata sbagliata.
    assert read_wav_info(wav(5, extra_chunk=True)).seconds == pytest.approx(5, abs=0.01)


def test_stereo_a_44100_viene_calcolato_sui_suoi_parametri():
    info = read_wav_info(wav(2, sample_rate=44100, channels=2))
    assert info.seconds == pytest.approx(2, abs=0.01)


def test_un_file_troncato_vale_per_quello_che_contiene():
    # Dichiara 10 secondi, ne porta la metà: conta quello che è arrivato.
    completo = wav(10)
    troncato = completo[: len(completo) // 2]
    assert read_wav_info(troncato).seconds < 6


def test_un_file_che_non_e_wav_viene_rifiutato():
    with pytest.raises(AudioError):
        read_wav_info(b"OggS" + b"\x00" * 100)


def test_un_wav_senza_blocco_data_viene_rifiutato():
    with pytest.raises(AudioError):
        read_wav_info(b"RIFF" + struct.pack("<I", 4) + b"WAVE")
