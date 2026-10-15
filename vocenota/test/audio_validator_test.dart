import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:vocenota/core/constants.dart';
import 'package:vocenota/core/failures.dart';
import 'package:vocenota/domain/audio_source.dart';
import 'package:vocenota/services/audio/chunk_planner.dart';
import 'package:vocenota/services/audio/media_tools.dart';

/// Sostituisce ffprobe/ffmpeg: restituisce i metadati decisi dal test.
class FakeMediaTools implements MediaTools {
  FakeMediaTools({required this.info, this.probeError});

  final MediaInfo info;
  final AppFailure? probeError;

  @override
  Future<MediaInfo> probe(String path) async {
    if (probeError != null) throw probeError!;
    return info;
  }

  @override
  Future<void> extractWav({
    required String inputPath,
    required String outputPath,
    Duration? start,
    Duration? end,
  }) async {}

  @override
  Future<List<SilenceWindow>> detectSilences(
    String path, {
    double noiseDb = -32,
    double minSilenceSeconds = 0.45,
  }) async =>
      const <SilenceWindow>[];
}

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('vocenota_validator');
  });
  tearDown(() async => tempDir.delete(recursive: true));

  Future<AudioSource> candidate({int bytes = 2048, String name = 'nota.opus'}) async {
    final File f = File('${tempDir.path}/$name')
      ..writeAsBytesSync(List<int>.filled(bytes, 7));
    return AudioSource(
      path: f.path,
      displayName: name,
      sizeBytes: 0,
      duration: Duration.zero,
    );
  }

  AudioValidator validatorWith(MediaInfo info) =>
      AudioValidator(tools: FakeMediaTools(info: info));

  test('completa i metadati di un file valido', () async {
    final AudioValidator validator = validatorWith(const MediaInfo(
      duration: Duration(minutes: 2, seconds: 30),
      codec: 'opus',
      sampleRate: 48000,
      channels: 1,
    ));

    final AudioSource result = await validator.validate(await candidate());

    expect(result.duration, const Duration(minutes: 2, seconds: 30));
    expect(result.codec, 'opus');
    expect(result.sizeBytes, 2048);
    expect(result.subtitle, contains('OPUS'));
    expect(result.title, 'Nota');
  });

  test('rifiuta un file che non esiste più', () async {
    final AudioSource source = await candidate();
    File(source.path).deleteSync();

    await expectLater(
      validatorWith(const MediaInfo(duration: Duration(minutes: 1)))
          .validate(source),
      throwsA(isA<AppFailure>().having(
        (AppFailure f) => f.message,
        'messaggio',
        contains('non è più disponibile'),
      )),
    );
  });

  test('rifiuta un file vuoto', () async {
    await expectLater(
      validatorWith(const MediaInfo(duration: Duration(minutes: 1)))
          .validate(await candidate(bytes: 0)),
      throwsA(isA<AppFailure>()
          .having((AppFailure f) => f.message, 'messaggio', contains('vuoto'))),
    );
  });

  test('rifiuta un audio troppo breve', () async {
    await expectLater(
      validatorWith(const MediaInfo(duration: Duration(milliseconds: 100)))
          .validate(await candidate()),
      throwsA(isA<AppFailure>().having(
        (AppFailure f) => f.message,
        'messaggio',
        contains('troppo breve'),
      )),
    );
  });

  test('rifiuta un audio oltre il limite di durata', () async {
    await expectLater(
      validatorWith(MediaInfo(
        duration: AudioLimits.maxDuration + const Duration(minutes: 1),
      )).validate(await candidate()),
      throwsA(isA<AppFailure>().having(
        (AppFailure f) => f.message,
        'messaggio',
        contains('troppo lungo'),
      )),
    );
  });

  test('propaga l\'errore di ffprobe su un file non audio', () async {
    final AudioValidator validator = AudioValidator(
      tools: FakeMediaTools(
        info: const MediaInfo(duration: Duration.zero),
        probeError: const AppFailure('Il file non contiene una traccia audio.'),
      ),
    );

    await expectLater(
      validator.validate(await candidate(name: 'documento.pdf')),
      throwsA(isA<AppFailure>().having(
        (AppFailure f) => f.message,
        'messaggio',
        contains('traccia audio'),
      )),
    );
  });

  test('un audio appena sotto il limite di durata è accettato', () async {
    final AudioSource result = await validatorWith(
      MediaInfo(duration: AudioLimits.maxDuration - const Duration(seconds: 1)),
    ).validate(await candidate());
    expect(result.duration, lessThan(AudioLimits.maxDuration));
  });
}
