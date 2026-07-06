import 'dart:math' as math;
import 'dart:typed_data';

/// Estrazione feature audio per la classificazione del pianto.
/// DEVE rimanere identica alla pipeline Python in tools/train_model.py.
class FeatureExtractor {
  static const int targetSr = 8000;
  static const int nFft = 512;
  static const int hop = 256;
  static const int nMels = 26;
  static const int nMfcc = 13;
  static const double fMin = 60.0;
  static const double fMax = 4000.0;
  static const double vadThreshold = 0.06; // frazione del max RMS

  /// Decodifica un file WAV PCM16 mono/stereo -> campioni float [-1,1] + sample rate.
  static (List<double>, int) decodeWav(Uint8List bytes) {
    final bd = ByteData.sublistView(bytes);
    if (bytes.length < 44 ||
        String.fromCharCodes(bytes.sublist(0, 4)) != 'RIFF' ||
        String.fromCharCodes(bytes.sublist(8, 12)) != 'WAVE') {
      throw const FormatException('File WAV non valido');
    }
    int pos = 12;
    int sampleRate = 0, numChannels = 1, bitsPerSample = 16;
    int dataStart = -1, dataLen = 0;
    while (pos + 8 <= bytes.length) {
      final id = String.fromCharCodes(bytes.sublist(pos, pos + 4));
      final size = bd.getUint32(pos + 4, Endian.little);
      if (id == 'fmt ') {
        numChannels = bd.getUint16(pos + 10, Endian.little);
        sampleRate = bd.getUint32(pos + 12, Endian.little);
        bitsPerSample = bd.getUint16(pos + 22, Endian.little);
      } else if (id == 'data') {
        dataStart = pos + 8;
        dataLen = size;
        break;
      }
      pos += 8 + size + (size & 1);
    }
    if (dataStart < 0 || sampleRate == 0) {
      throw const FormatException('Chunk dati WAV mancante');
    }
    if (bitsPerSample != 16) {
      throw const FormatException('Supportato solo PCM 16 bit');
    }
    final end = math.min(dataStart + dataLen, bytes.length);
    final nFrames = (end - dataStart) ~/ (2 * numChannels);
    final out = List<double>.filled(nFrames, 0);
    for (int i = 0; i < nFrames; i++) {
      double acc = 0;
      for (int c = 0; c < numChannels; c++) {
        acc += bd.getInt16(dataStart + 2 * (i * numChannels + c), Endian.little);
      }
      out[i] = (acc / numChannels) / 32768.0;
    }
    return (out, sampleRate);
  }

  /// Ricampionamento lineare a [targetSr].
  static List<double> resample(List<double> x, int srcSr) {
    if (srcSr == targetSr) return x;
    final ratio = targetSr / srcSr;
    final outLen = (x.length * ratio).floor();
    final out = List<double>.filled(outLen, 0);
    for (int i = 0; i < outLen; i++) {
      final t = i / ratio;
      final i0 = t.floor();
      final i1 = math.min(i0 + 1, x.length - 1);
      final frac = t - i0;
      out[i] = x[i0] * (1 - frac) + x[i1] * frac;
    }
    return out;
  }

  /// FFT reale (radix-2). Ritorna magnitudine dei primi nFft/2+1 bin.
  static List<double> _rfftMag(List<double> frame) {
    final n = nFft;
    final re = List<double>.filled(n, 0);
    final im = List<double>.filled(n, 0);
    for (int i = 0; i < math.min(frame.length, n); i++) {
      re[i] = frame[i];
    }
    // bit reversal
    int j = 0;
    for (int i = 0; i < n - 1; i++) {
      if (i < j) {
        final tr = re[i]; re[i] = re[j]; re[j] = tr;
        final ti = im[i]; im[i] = im[j]; im[j] = ti;
      }
      int m = n >> 1;
      while (m >= 1 && j >= m) {
        j -= m;
        m >>= 1;
      }
      j += m;
    }
    // butterfly
    for (int len = 2; len <= n; len <<= 1) {
      final ang = -2 * math.pi / len;
      final wRe = math.cos(ang), wIm = math.sin(ang);
      for (int i = 0; i < n; i += len) {
        double curRe = 1, curIm = 0;
        for (int k = 0; k < len ~/ 2; k++) {
          final aRe = re[i + k], aIm = im[i + k];
          final bRe = re[i + k + len ~/ 2] * curRe - im[i + k + len ~/ 2] * curIm;
          final bIm = re[i + k + len ~/ 2] * curIm + im[i + k + len ~/ 2] * curRe;
          re[i + k] = aRe + bRe;
          im[i + k] = aIm + bIm;
          re[i + k + len ~/ 2] = aRe - bRe;
          im[i + k + len ~/ 2] = aIm - bIm;
          final nRe = curRe * wRe - curIm * wIm;
          curIm = curRe * wIm + curIm * wRe;
          curRe = nRe;
        }
      }
    }
    final mag = List<double>.filled(n ~/ 2 + 1, 0);
    for (int i = 0; i <= n ~/ 2; i++) {
      mag[i] = math.sqrt(re[i] * re[i] + im[i] * im[i]);
    }
    return mag;
  }

  static double _hzToMel(double f) => 2595.0 * _log10(1.0 + f / 700.0);
  static double _melToHz(double m) => 700.0 * (math.pow(10, m / 2595.0) - 1.0);
  static double _log10(double x) => math.log(x) / math.ln10;

  /// Banco filtri mel triangolari (HTK, non normalizzati).
  static List<List<double>> _melFilterbank() {
    final nBins = nFft ~/ 2 + 1;
    final melPts = List<double>.generate(nMels + 2,
        (i) => _hzToMel(fMin) + (_hzToMel(fMax) - _hzToMel(fMin)) * i / (nMels + 1));
    final hzPts = melPts.map(_melToHz).toList();
    final binPts =
        hzPts.map((f) => ((nFft + 1) * f / targetSr).floor()).toList();
    final fb = List.generate(nMels, (_) => List<double>.filled(nBins, 0));
    for (int m = 0; m < nMels; m++) {
      final f0 = binPts[m], f1 = binPts[m + 1], f2 = binPts[m + 2];
      for (int k = f0; k < f1 && k < nBins; k++) {
        if (f1 != f0) fb[m][k] = (k - f0) / (f1 - f0);
      }
      for (int k = f1; k <= f2 && k < nBins; k++) {
        if (f2 != f1) fb[m][k] = (f2 - k) / (f2 - f1);
      }
    }
    return fb;
  }

  /// DCT-II ortonormale.
  static List<double> _dct(List<double> x, int nOut) {
    final m = x.length;
    final out = List<double>.filled(nOut, 0);
    for (int k = 0; k < nOut; k++) {
      double s = 0;
      for (int i = 0; i < m; i++) {
        s += x[i] * math.cos(math.pi * k * (i + 0.5) / m);
      }
      out[k] = s * math.sqrt(2.0 / m) * (k == 0 ? 1 / math.sqrt(2.0) : 1.0);
    }
    return out;
  }

  /// Estrae il vettore di 32 feature da campioni PCM.
  /// [samples]: mono float [-1,1] a [srcSr] Hz.
  static List<double> extract(List<double> samples, int srcSr) {
    var x = resample(samples, srcSr);
    // normalizzazione di picco
    double peak = 1e-9;
    for (final v in x) {
      final a = v.abs();
      if (a > peak) peak = a;
    }
    x = x.map((v) => v / peak).toList();

    final hann = List<double>.generate(
        nFft, (i) => 0.5 - 0.5 * math.cos(2 * math.pi * i / (nFft - 1)));
    final fb = _melFilterbank();

    final mfccFrames = <List<double>>[];
    final zcrs = <double>[];
    final logRmss = <double>[];
    final centroids = <double>[];
    final rmss = <double>[];

    for (int start = 0; start + nFft <= x.length; start += hop) {
      final frame = List<double>.generate(nFft, (i) => x[start + i]);
      double sq = 0;
      int zc = 0;
      for (int i = 0; i < nFft; i++) {
        sq += frame[i] * frame[i];
        if (i > 0 && (frame[i] >= 0) != (frame[i - 1] >= 0)) zc++;
      }
      final rms = math.sqrt(sq / nFft);
      rmss.add(rms);
      zcrs.add(zc / nFft);
      logRmss.add(math.log(rms + 1e-10));

      final windowed =
          List<double>.generate(nFft, (i) => frame[i] * hann[i]);
      final mag = _rfftMag(windowed);
      final power = mag.map((v) => v * v / nFft).toList();

      double num = 0, den = 1e-10;
      for (int k = 0; k < power.length; k++) {
        num += k * targetSr / nFft * power[k];
        den += power[k];
      }
      centroids.add(num / den);

      final melE = List<double>.filled(nMels, 0);
      for (int m = 0; m < nMels; m++) {
        double s = 0;
        for (int k = 0; k < power.length; k++) {
          s += fb[m][k] * power[k];
        }
        melE[m] = math.log(s + 1e-10);
      }
      mfccFrames.add(_dct(melE, nMfcc));
    }

    if (mfccFrames.isEmpty) {
      throw const FormatException('Audio troppo corto');
    }

    // VAD: tieni solo i frame sopra soglia
    final maxRms = rmss.reduce(math.max);
    var keep = <int>[];
    for (int i = 0; i < rmss.length; i++) {
      if (rmss[i] > vadThreshold * maxRms) keep.add(i);
    }
    if (keep.length < 5) keep = List.generate(rmss.length, (i) => i);

    List<double> meanStd(List<double> Function(int) get) {
      double mean = 0;
      for (final i in keep) {
        mean += get(i)[0];
      }
      mean /= keep.length;
      double varSum = 0;
      for (final i in keep) {
        final d = get(i)[0] - mean;
        varSum += d * d;
      }
      return [mean, math.sqrt(varSum / keep.length)];
    }

    final features = <double>[];
    // 13 mean + 13 std MFCC
    for (int c = 0; c < nMfcc; c++) {
      final ms = meanStd((i) => [mfccFrames[i][c]]);
      features.add(ms[0]);
      features.add(ms[1]);
    }
    final zcrMs = meanStd((i) => [zcrs[i]]);
    final rmsMs = meanStd((i) => [logRmss[i]]);
    final cenMs = meanStd((i) => [centroids[i]]);
    features.addAll([zcrMs[0], zcrMs[1], rmsMs[0], rmsMs[1], cenMs[0], cenMs[1]]);
    return features; // 32 feature
  }
}
