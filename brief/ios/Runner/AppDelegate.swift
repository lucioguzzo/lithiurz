import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    // Riconoscitore vocale di sistema, alternativa a whisper.
    if let messenger = engineBridge.pluginRegistry
      .registrar(forPlugin: "SystemSpeechPlugin")?.messenger() {
      SystemSpeechPlugin.register(with: messenger)
    }
  }
}
