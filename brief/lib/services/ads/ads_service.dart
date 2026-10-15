import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

/// Pubblicità banner, discreta e mai durante l'elaborazione.
///
/// Gli ID reali si passano a compile time:
/// `--dart-define=ADMOB_BANNER_ANDROID=ca-app-pub-…/…`
/// In assenza si usano gli **ID di test ufficiali di Google**: così lo
/// sviluppo non genera mai impression non valide.
class AdsService {
  AdsService({this.enabled = true});

  final bool enabled;
  bool _initialized = false;

  static const String _testBannerAndroid =
      'ca-app-pub-3940256099942544/6300978111';
  static const String _testBannerIos =
      'ca-app-pub-3940256099942544/2934735716';

  static const String _bannerAndroid =
      String.fromEnvironment('ADMOB_BANNER_ANDROID');
  static const String _bannerIos = String.fromEnvironment('ADMOB_BANNER_IOS');

  /// `true` quando sono in uso gli ID di test (mostrato nelle impostazioni).
  bool get usingTestIds => bannerUnitId == _testBannerAndroid ||
      bannerUnitId == _testBannerIos;

  String get bannerUnitId {
    if (Platform.isAndroid) {
      return _bannerAndroid.isNotEmpty ? _bannerAndroid : _testBannerAndroid;
    }
    if (Platform.isIOS) {
      return _bannerIos.isNotEmpty ? _bannerIos : _testBannerIos;
    }
    return _testBannerAndroid;
  }

  bool get available => _initialized && enabled;

  Future<void> initialize() async {
    if (!enabled || _initialized) return;
    try {
      await MobileAds.instance.initialize();
      _initialized = true;
    } on Object catch (e) {
      // Nessuna funzione dell'app dipende dagli annunci: in caso di errore
      // si prosegue senza.
      debugPrint('AdMob non inizializzato: $e');
      _initialized = false;
    }
  }

  /// Crea un banner adattivo. Restituisce `null` se la pubblicità è
  /// disattivata o non disponibile.
  BannerAd? createBanner({required void Function() onLoaded}) {
    if (!available) return null;
    return BannerAd(
      adUnitId: bannerUnitId,
      size: AdSize.banner,
      request: const AdRequest(),
      listener: BannerAdListener(
        onAdLoaded: (Ad ad) => onLoaded(),
        onAdFailedToLoad: (Ad ad, LoadAdError error) {
          debugPrint('Banner non caricato: ${error.message}');
          ad.dispose();
        },
      ),
    );
  }
}
