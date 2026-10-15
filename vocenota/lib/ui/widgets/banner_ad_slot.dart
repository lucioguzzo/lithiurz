import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import '../../app_services.dart';

/// Banner discreto in fondo alla schermata.
///
/// Non occupa spazio finché l'annuncio non è caricato e non viene mai
/// mostrato durante l'elaborazione (le schermate che elaborano non lo
/// includono).
class BannerAdSlot extends StatefulWidget {
  const BannerAdSlot({super.key});

  @override
  State<BannerAdSlot> createState() => _BannerAdSlotState();
}

class _BannerAdSlotState extends State<BannerAdSlot> {
  BannerAd? _ad;
  bool _loaded = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_ad != null) return;
    final services = ServicesScope.of(context);
    if (!services.settings.value.adsEnabled) return;
    final BannerAd? ad = services.ads.createBanner(
      onLoaded: () {
        if (mounted) setState(() => _loaded = true);
      },
    );
    _ad = ad;
    ad?.load();
  }

  @override
  void dispose() {
    _ad?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final BannerAd? ad = _ad;
    if (ad == null || !_loaded) return const SizedBox.shrink();
    return SafeArea(
      top: false,
      child: SizedBox(
        width: ad.size.width.toDouble(),
        height: ad.size.height.toDouble(),
        child: AdWidget(ad: ad),
      ),
    );
  }
}
