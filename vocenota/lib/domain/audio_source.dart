import '../core/formatters.dart';

/// File audio pronto per l'elaborazione, già copiato nella sandbox dell'app.
class AudioSource {
  const AudioSource({
    required this.path,
    required this.displayName,
    required this.sizeBytes,
    required this.duration,
    this.codec,
    this.sampleRate,
    this.channels,
    this.origin = AudioOrigin.manuale,
  });

  final String path;
  final String displayName;
  final int sizeBytes;
  final Duration duration;
  final String? codec;
  final int? sampleRate;
  final int? channels;
  final AudioOrigin origin;

  String get title => Fmt.titleFromFileName(displayName);

  String get subtitle {
    final List<String> parts = <String>[
      Fmt.duration(duration),
      Fmt.bytes(sizeBytes),
      if (codec != null) codec!.toUpperCase(),
    ];
    return parts.join(' · ');
  }

  AudioSource copyWith({Duration? duration, String? codec, int? sampleRate, int? channels}) =>
      AudioSource(
        path: path,
        displayName: displayName,
        sizeBytes: sizeBytes,
        duration: duration ?? this.duration,
        codec: codec ?? this.codec,
        sampleRate: sampleRate ?? this.sampleRate,
        channels: channels ?? this.channels,
        origin: origin,
      );
}

enum AudioOrigin { condivisione, manuale, ripresa }
