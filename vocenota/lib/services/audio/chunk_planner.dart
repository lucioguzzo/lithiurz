import '../../core/constants.dart';

/// Finestra di silenzio rilevata da ffmpeg (`silencedetect`).
class SilenceWindow {
  const SilenceWindow(this.start, this.end);
  final Duration start;
  final Duration end;

  Duration get center =>
      Duration(microseconds: (start.inMicroseconds + end.inMicroseconds) ~/ 2);
  Duration get length => end - start;

  @override
  String toString() => 'Silence(${start.inMilliseconds}-${end.inMilliseconds})';
}

/// Blocco di audio da trascrivere separatamente.
class AudioChunk {
  const AudioChunk({
    required this.index,
    required this.start,
    required this.end,
  });

  final int index;
  final Duration start;
  final Duration end;

  Duration get length => end - start;

  Map<String, Object?> toJson() => <String, Object?>{
        'index': index,
        'start_ms': start.inMilliseconds,
        'end_ms': end.inMilliseconds,
      };

  factory AudioChunk.fromJson(Map<String, Object?> json) => AudioChunk(
        index: (json['index'] as num).toInt(),
        start: Duration(milliseconds: (json['start_ms'] as num).toInt()),
        end: Duration(milliseconds: (json['end_ms'] as num).toInt()),
      );

  @override
  String toString() =>
      'Chunk#$index(${start.inSeconds}s→${end.inSeconds}s)';
}

/// Analizza l'output testuale del filtro `silencedetect` di ffmpeg.
///
/// Righe attese:
/// ```
/// [silencedetect @ 0x..] silence_start: 12.345
/// [silencedetect @ 0x..] silence_end: 13.5 | silence_duration: 1.155
/// ```
List<SilenceWindow> parseSilenceLog(String log) {
  final RegExp startRe = RegExp(r'silence_start:\s*(-?[\d.]+)');
  final RegExp endRe = RegExp(r'silence_end:\s*([\d.]+)');
  final List<SilenceWindow> out = <SilenceWindow>[];
  Duration? pendingStart;

  Duration toDuration(String seconds) => Duration(
        microseconds: ((double.tryParse(seconds) ?? 0) * 1e6).round(),
      );

  for (final String rawLine in log.split('\n')) {
    final RegExpMatch? s = startRe.firstMatch(rawLine);
    if (s != null) {
      final Duration d = toDuration(s.group(1)!);
      pendingStart = d.isNegative ? Duration.zero : d;
      continue;
    }
    final RegExpMatch? e = endRe.firstMatch(rawLine);
    if (e != null && pendingStart != null) {
      final Duration end = toDuration(e.group(1)!);
      if (end > pendingStart) out.add(SilenceWindow(pendingStart, end));
      pendingStart = null;
    }
  }
  return out;
}

/// Calcola la suddivisione in blocchi di un audio lungo.
///
/// I confini vengono spostati sul silenzio più vicino entro [drift], così da
/// non tagliare a metà una parola; se non c'è silenzio utile si taglia al
/// confine teorico. L'ultimo blocco, se troppo corto, viene fuso nel
/// precedente. Funzione pura: è il cuore verificato dai test.
List<AudioChunk> planChunks({
  required Duration total,
  Duration target = AudioLimits.chunkTarget,
  Duration drift = AudioLimits.chunkDrift,
  Duration threshold = AudioLimits.chunkThreshold,
  Duration minTail = AudioLimits.minTailChunk,
  List<SilenceWindow> silences = const <SilenceWindow>[],
}) {
  if (total <= Duration.zero) return const <AudioChunk>[];
  if (total <= threshold) {
    return <AudioChunk>[AudioChunk(index: 0, start: Duration.zero, end: total)];
  }

  final List<Duration> boundaries = <Duration>[];
  Duration cursor = Duration.zero;

  while (true) {
    final Duration ideal = cursor + target;
    if (total - ideal <= minTail) break;

    final Duration? snapped = _snapToSilence(ideal, drift, silences, cursor);
    final Duration boundary = snapped ?? ideal;
    if (boundary <= cursor) break;
    boundaries.add(boundary);
    cursor = boundary;
  }

  final List<AudioChunk> chunks = <AudioChunk>[];
  Duration start = Duration.zero;
  for (final Duration b in boundaries) {
    chunks.add(AudioChunk(index: chunks.length, start: start, end: b));
    start = b;
  }
  chunks.add(AudioChunk(index: chunks.length, start: start, end: total));
  return chunks;
}

Duration? _snapToSilence(
  Duration ideal,
  Duration drift,
  List<SilenceWindow> silences,
  Duration after,
) {
  Duration? best;
  int bestDelta = 1 << 62;
  for (final SilenceWindow w in silences) {
    final Duration c = w.center;
    if (c <= after) continue;
    final int delta = (c - ideal).inMicroseconds.abs();
    if (delta <= drift.inMicroseconds && delta < bestDelta) {
      bestDelta = delta;
      best = c;
    }
  }
  return best;
}
