import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:brief/core/constants.dart';
import 'package:brief/ui/widgets/app_footer.dart';

void main() {
  test('il nome dell\'app è quello scelto', () {
    expect(AppInfo.fullName, 'BRIEF - Vocal Sum Up');
    // Nome breve per l'icona: Android e iOS troncano le etichette lunghe.
    expect(AppInfo.name, 'BRIEF');
  });

  test('la riga di copyright è completa', () {
    expect(AppInfo.copyright, '© 2026 Lucio Salvatore Guzzo');
    expect(AppInfo.copyright, contains(AppInfo.copyrightOwner));
    expect(AppInfo.copyright, startsWith('©'));
  });

  testWidgets('il piè di pagina mostra il copyright', (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: SizedBox(), bottomNavigationBar: AppFooter())),
    );
    expect(find.text(AppInfo.copyright), findsOneWidget);
  });

  testWidgets('la barra inferiore senza pubblicità mostra solo il copyright',
      (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(),
          bottomNavigationBar: BottomBar(showAd: false),
        ),
      ),
    );
    expect(find.text(AppInfo.copyright), findsOneWidget);
    expect(find.byType(AppFooter), findsOneWidget);
  });

  testWidgets('il copyright resta leggibile su uno schermo stretto',
      (WidgetTester tester) async {
    tester.view.physicalSize = const Size(320 * 3, 640 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SizedBox(), bottomNavigationBar: BottomBar(showAd: false)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text(AppInfo.copyright), findsOneWidget);
    expect(tester.takeException(), isNull);

    // Il nome del titolare non deve finire troncato dai puntini: il testo
    // reso deve stare nella larghezza disponibile.
    final RenderParagraph paragraph =
        tester.renderObject<RenderParagraph>(find.text(AppInfo.copyright));
    expect(paragraph.didExceedMaxLines, isFalse,
        reason: 'il copyright deve andare a capo, non essere troncato');
    expect(tester.getSize(find.text(AppInfo.copyright)).width,
        lessThanOrEqualTo(320 - 32));
  });
}
