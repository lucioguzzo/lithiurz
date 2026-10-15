import 'dart:io';

import 'package:ffmpeg_kit_flutter_new_min/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new_min/ffmpeg_session.dart';
import 'package:ffmpeg_kit_flutter_new_min/ffprobe_kit.dart';
import 'package:ffmpeg_kit_flutter_new_min/media_information.dart';
import 'package:ffmpeg_kit_flutter_new_min/media_information_session.dart';
import 'package:ffmpeg_kit_flutter_new_min/return_code.dart';
import 'package:ffmpeg_kit_flutter_new_min/stream_information.dart';
import 'package:flutter/foundation.dart';

import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../domain/audio_source.dart';
import 'chunk_planner.dart';

/// Dati essenziali ricavati dal file audio.
class MediaInfo {
  const MediaInfo({
    required this.duration,
    this.codec,
    this.sampleRate,
    this.channels,
    this.format,
  });

  final Duration duration;
  final String? codec;
  final int? sampleRate;
  final int? channels;
  final String? format;
}

/// Accesso a ffprobe/ffmpeg (build "min" inclusa da whisper_ggml).
///
/// Tutte le operazioni native stanno qui, così il resto della pipeline resta
/// verificabile con i test unitari.
class MediaTools {
  const MediaTools();

  /// Legge durata, codec e canali. Lancia [AppFailure] se il file non è
  /// un audio leggibile.
  Future<MediaInfo> probe(String path) async {
    final MediaInformationSession session =
        await FFprobeKit.getMediaInformation(path);
    final MediaInformation? info = session.getMediaInformation();
    if (info == null) {
      throw const AppFailure(
        'Non riesco a leggere questo file: potrebbe essere danneggiato o non '
        'essere un audio.',
        recoverable: false,
      );
    }

    StreamInformation? audio;
    for (final StreamInformation s in info.getStreams()) {
      if (s.getType() == 'audio') {
        audio = s;
        break;
      }
    }
    if (audio == null) {
      throw const AppFailure(
        'Il file non contiene una traccia audio.',
        recoverable: false,
      );
    }

    final double seconds = double.tryParse(info.getDuration() ?? '') ?? 0;
    final Map<dynamic, dynamic>? props = audio.getAllProperties();
    final Object? channels = props?['channels'];

    return MediaInfo(
      duration: Duration(microseconds: (seconds * 1e6).round()),
      codec: audio.getCodec(),
      sampleRate: int.tryParse(audio.getSampleRate() ?? ''),
      channels: channels is num ? channels.toInt() : null,
      format: info.getFormat(),
    );
  }

  /// Converte (o ritaglia) una porzione in WAV PCM 16 bit, 16 kHz, mono:
  /// il formato richiesto da whisper.cpp.
  Future<void> extractWav({
    required String inputPath,
    required String outputPath,
    Duration? start,
    Duration? end,
  }) async {
    final List<String> args = <String>[
      '-hide_banner',
      '-nostdin',
      '-y',
      if (start != null) ...<String>['-ss', _ts(start)],
      '-i', inputPath,
      if (end != null && start != null) ...<String>['-t', _ts(end - start)],
      if (end != null && start == null) ...<String>['-t', _ts(end)],
      '-vn',
      '-sn',
      '-dn',
      '-ac', '1',
      '-ar', '16000',
      '-c:a', 'pcm_s16le',
      '-f', 'wav',
      outputPath,
    ];

    final FFmpegSession session = await FFmpegKit.executeWithArguments(args);
    final ReturnCode? code = await session.getReturnCode();
    if (!ReturnCode.isSuccess(code)) {
      final String? log = await session.getAllLogsAsString();
      debugPrint('ffmpeg fallito (${code?.getValue()}): ${log?.split('\n').takeLast(8).join('\n')}');
      throw AppFailure(
        'Conversione audio non riuscita'
        '${start != null ? ' sul blocco a ${start.inSeconds}s' : ''}.',
        cause: code?.getValue(),
      );
    }
    final File out = File(outputPath);
    if (!out.existsSync() || out.lengthSync() < 1024) {
      throw const AppFailure('La conversione ha prodotto un audio vuoto.');
    }
  }

  /// Rileva i silenzi, usati per tagliare i blocchi senza spezzare le parole.
  ///
  /// Un errore qui non è bloccante: senza silenzi si taglia al confine
  /// teorico, quindi in caso di problemi restituisce una lista vuota.
  Future<List<SilenceWindow>> detectSilences(
    String path, {
    double noiseDb = -32,
    double minSilenceSeconds = 0.45,
  }) async {
    try {
      final FFmpegSession session = await FFmpegKit.executeWithArguments(
        <String>[
          '-hide_banner',
          '-nostdin',
          '-i', path,
          '-af', 'silencedetect=noise=${noiseDb}dB:d=$minSilenceSeconds',
          '-f', 'null',
          '-',
        ],
      );
      final String log = await session.getAllLogsAsString() ?? '';
      return parseSilenceLog(log);
    } on Object catch (e) {
      debugPrint('silencedetect non disponibile: $e');
      return const <SilenceWindow>[];
    }
  }

  static String _ts(Duration d) =>
      (d.inMicroseconds / 1e6).toStringAsFixed(3);
}

/// Controlli su formato, dimensione e durata prima di iniziare.
class AudioValidator {
  const AudioValidator({this.tools = const MediaTools()});

  final MediaTools tools;

  /// Verifica il file e ne completa i metadati.
  Future<AudioSource> validate(AudioSource candidate) async {
    final File file = File(candidate.path);
    if (!file.existsSync()) {
      throw const AppFailure(
        'Il file non è più disponibile. Riprova a condividerlo.',
        recoverable: false,
      );
    }
    final int size = file.lengthSync();
    if (size == 0) {
      throw const AppFailure('Il file è vuoto.', recoverable: false);
    }
    if (size > AudioLimits.maxSizeBytes) {
      throw AppFailure(
        'File troppo grande (${(size / (1024 * 1024)).round()} MB). '
        'Il limite è ${AudioLimits.maxSizeBytes ~/ (1024 * 1024)} MB.',
        recoverable: false,
      );
    }

    final MediaInfo info = await tools.probe(candidate.path);
    if (info.duration < AudioLimits.minDuration) {
      throw const AppFailure(
        'L\'audio è troppo breve per essere trascritto.',
        recoverable: false,
      );
    }
    if (info.duration > AudioLimits.maxDuration) {
      throw AppFailure(
        'Audio troppo lungo (${info.duration.inMinutes} minuti). '
        'Il limite è ${AudioLimits.maxDuration.inHours} ore.',
        recoverable: false,
      );
    }

    return AudioSource(
      path: candidate.path,
      displayName: candidate.displayName,
      sizeBytes: size,
      duration: info.duration,
      codec: info.codec,
      sampleRate: info.sampleRate,
      channels: info.channels,
      origin: candidate.origin,
    );
  }
}

extension<T> on Iterable<T> {
  Iterable<T> takeLast(int n) => skip(length > n ? length - n : 0);
}
