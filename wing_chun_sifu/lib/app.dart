import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/router.dart';
import 'core/theme.dart';
import 'data/progress_service.dart';

class WingChunSifuApp extends StatefulWidget {
  const WingChunSifuApp({super.key});

  @override
  State<WingChunSifuApp> createState() => _WingChunSifuAppState();
}

class _WingChunSifuAppState extends State<WingChunSifuApp> {
  late final _router = buildRouter();

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<ProgressService>.value(
      value: ProgressService.instance,
      child: MaterialApp.router(
        title: 'Wing Chun SiFu Online',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.build(),
        routerConfig: _router,
      ),
    );
  }
}
