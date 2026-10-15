import 'package:flutter/material.dart';

import '../../core/constants.dart';
import 'banner_ad_slot.dart';

/// Riga di copyright mostrata in fondo a ogni schermata.
class AppFooter extends StatelessWidget {
  const AppFooter({super.key});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 8),
      child: Text(
        AppInfo.copyright,
        textAlign: TextAlign.center,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.8),
        ),
      ),
    );
  }
}

/// Barra inferiore comune: eventuale banner pubblicitario e copyright.
///
/// Una sola [SafeArea] per entrambi, altrimenti sui telefoni con la barra
/// gestuale il margine verrebbe applicato due volte.
class BottomBar extends StatelessWidget {
  const BottomBar({super.key, this.showAd = true});

  /// Le schermate che elaborano un audio passano `false`: nessuna
  /// pubblicità durante il lavoro.
  final bool showAd;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (showAd) const BannerAdSlot(),
          const AppFooter(),
        ],
      ),
    );
  }
}
