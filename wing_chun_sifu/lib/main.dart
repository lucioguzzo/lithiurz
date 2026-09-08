import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'core/theme.dart';
import 'data/content_repository.dart';
import 'data/progress_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(AppTheme.overlayStyle);
  await SystemChrome.setPreferredOrientations(
      const [DeviceOrientation.portraitUp, DeviceOrientation.portraitDown]);

  await Future.wait([
    ContentRepository.instance.load(),
    ProgressService.instance.load(),
  ]);

  runApp(const WingChunSifuApp());
}
