// La configurazione di piattaforma non e' coperta da nessun altro test, e un
// suo errore non si vede finche' non si apre l'app su un telefono vero.
//
// Il visualizzatore 3D serve il modello alla WebView da un server HTTP locale
// sulla scheda di loopback. Android blocca il traffico in chiaro dalla API 28:
// senza la dichiarazione, la WebView non carica, il callback di completamento
// non arriva mai e il SiFu resta in caricamento per sempre. E' successo.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android consente il traffico in chiaro verso il server locale', () {
    final manifest = File('android/app/src/main/AndroidManifest.xml');
    expect(manifest.existsSync(), isTrue);
    final xml = manifest.readAsStringSync();

    final hasConfig = xml.contains('android:networkSecurityConfig=');
    final hasBlanket = xml.contains('android:usesCleartextTraffic="true"');
    expect(hasConfig || hasBlanket, isTrue,
        reason: 'senza questa dichiarazione il visualizzatore 3D non carica');

    if (hasConfig) {
      final config =
          File('android/app/src/main/res/xml/network_security_config.xml');
      expect(config.existsSync(), isTrue,
          reason: 'il manifest indica una configurazione che non esiste');
      final text = config.readAsStringSync();
      expect(text, contains('cleartextTrafficPermitted="true"'));
      expect(text, contains('127.0.0.1'));
      expect(text, contains('localhost'));
      // il permesso deve restare limitato al loopback
      expect(text, contains('<base-config cleartextTrafficPermitted="false"'),
          reason: 'il traffico in chiaro non deve essere aperto a tutta la rete');
    }
  });

  test('Android dichiara il permesso di rete', () {
    final xml =
        File('android/app/src/main/AndroidManifest.xml').readAsStringSync();
    expect(xml, contains('android.permission.INTERNET'));
  });

  test('iOS consente le connessioni alla rete locale', () {
    final plist = File('ios/Runner/Info.plist');
    expect(plist.existsSync(), isTrue);
    final text = plist.readAsStringSync();
    expect(text, contains('NSAppTransportSecurity'));
    expect(text, contains('NSAllowsLocalNetworking'));
  });

  test('il modello 3D e\' dichiarato fra gli asset', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    expect(pubspec, contains('assets/models/'));
    expect(File('assets/models/sifu.glb').existsSync(), isTrue);
  });
}
