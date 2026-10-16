package com.lucio.brief

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Riconoscitore vocale del sistema, alternativa a whisper.
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            SystemSpeechPlugin.CHANNEL,
        ).setMethodCallHandler(SystemSpeechPlugin(applicationContext))
    }
}
