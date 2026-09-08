import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../data/progress_service.dart';
import '../features/forms/form_detail_screen.dart';
import '../features/forms/forms_screen.dart';
import '../features/history/history_screen.dart';
import '../features/home/home_screen.dart';
import '../features/intro/intro_screen.dart';
import '../features/principles/principle_detail_screen.dart';
import '../features/principles/principles_screen.dart';
import '../features/sifu3d/lesson_screen.dart';
import '../features/sifu3d/lessons_screen.dart';
import '../features/videos/video_screen.dart';
import '../features/videos/videos_screen.dart';

/// Transizione condivisa: dissolvenza con una minima scala, che rende la
/// navigazione continua invece di far "saltare" le schermate.
CustomTransitionPage<T> _fade<T>(Widget child, GoRouterState state) =>
    CustomTransitionPage<T>(
      key: state.pageKey,
      transitionDuration: const Duration(milliseconds: 320),
      reverseTransitionDuration: const Duration(milliseconds: 220),
      child: child,
      transitionsBuilder: (context, animation, secondary, child) {
        final curved =
            CurvedAnimation(parent: animation, curve: Curves.easeOutCubic);
        return FadeTransition(
          opacity: curved,
          child: Transform.scale(
            scale: 0.985 + 0.015 * curved.value,
            child: child,
          ),
        );
      },
    );

GoRouter buildRouter() => GoRouter(
      initialLocation: ProgressService.instance.introSeen ? '/menu' : '/',
      routes: [
        GoRoute(
          path: '/',
          pageBuilder: (c, s) => _fade(const IntroScreen(), s),
        ),
        GoRoute(
          path: '/menu',
          pageBuilder: (c, s) => _fade(const HomeScreen(), s),
        ),
        GoRoute(
          path: '/storia',
          pageBuilder: (c, s) => _fade(const HistoryScreen(), s),
        ),
        GoRoute(
          path: '/principi',
          pageBuilder: (c, s) => _fade(const PrinciplesScreen(), s),
          routes: [
            GoRoute(
              path: ':id',
              pageBuilder: (c, s) =>
                  _fade(PrincipleDetailScreen(id: s.pathParameters['id']!), s),
            ),
          ],
        ),
        GoRoute(
          path: '/forme',
          pageBuilder: (c, s) => _fade(const FormsScreen(), s),
          routes: [
            GoRoute(
              path: ':id',
              pageBuilder: (c, s) =>
                  _fade(FormDetailScreen(id: s.pathParameters['id']!), s),
            ),
          ],
        ),
        GoRoute(
          path: '/sifu3d',
          pageBuilder: (c, s) => _fade(const LessonsScreen(), s),
          routes: [
            GoRoute(
              path: ':id',
              pageBuilder: (c, s) =>
                  _fade(LessonScreen(id: s.pathParameters['id']!), s),
            ),
          ],
        ),
        GoRoute(
          path: '/video',
          pageBuilder: (c, s) => _fade(const VideosScreen(), s),
          routes: [
            GoRoute(
              path: ':id',
              pageBuilder: (c, s) =>
                  _fade(VideoScreen(id: s.pathParameters['id']!), s),
            ),
          ],
        ),
      ],
    );
