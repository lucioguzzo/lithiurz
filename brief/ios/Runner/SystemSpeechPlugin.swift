import Flutter
import Speech

/// Trascrive un file audio con il riconoscitore vocale di iOS, in modalità
/// su dispositivo: gratuito, offline e molto più rapido di whisper perché
/// ottimizzato da Apple per quel chip.
///
/// Serve `NSSpeechRecognitionUsageDescription` in Info.plist. Il supporto
/// offline dipende dalla lingua scaricata sul dispositivo.
///
/// ATTENZIONE: questo file non è mai stato compilato né provato — in
/// questo progetto iOS non è ancora stato costruito (serve un Mac).
/// Vedi docs/IOS.md.
class SystemSpeechPlugin: NSObject {

    static let channelName = "brief/system_speech"

    static func register(with messenger: FlutterBinaryMessenger) {
        let channel = FlutterMethodChannel(name: channelName, binaryMessenger: messenger)
        let instance = SystemSpeechPlugin()
        channel.setMethodCallHandler { call, result in
            instance.handle(call, result: result)
        }
    }

    private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "isAvailable":
            result(recognizer(for: "it-IT")?.supportsOnDeviceRecognition ?? false)
        case "unavailableReason":
            result(unavailableReason())
        case "transcribeFile":
            guard let args = call.arguments as? [String: Any],
                  let path = args["path"] as? String else {
                result(FlutterError(code: "ARGOMENTI",
                                    message: "Percorso del file mancante.",
                                    details: nil))
                return
            }
            let language = args["language"] as? String ?? "it-IT"
            transcribe(path: path, language: language, result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func recognizer(for language: String) -> SFSpeechRecognizer? {
        SFSpeechRecognizer(locale: Locale(identifier: language))
    }

    private func unavailableReason() -> String? {
        guard let recognizer = recognizer(for: "it-IT") else {
            return "Il riconoscitore vocale di sistema non supporta l'italiano su questo dispositivo."
        }
        if !recognizer.supportsOnDeviceRecognition {
            return "Il riconoscimento offline non è disponibile: scarica l'italiano dalle impostazioni di dettatura del telefono."
        }
        return nil
    }

    private func transcribe(path: String, language: String, result: @escaping FlutterResult) {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else {
                    result(FlutterError(code: "PERMESSO",
                                        message: "Permesso al riconoscimento vocale negato.",
                                        details: nil))
                    return
                }
                guard let recognizer = self.recognizer(for: language),
                      recognizer.isAvailable else {
                    result(FlutterError(code: "NON_DISPONIBILE",
                                        message: self.unavailableReason() ?? "Riconoscitore non disponibile.",
                                        details: nil))
                    return
                }

                let request = SFSpeechURLRecognitionRequest(url: URL(fileURLWithPath: path))
                request.shouldReportPartialResults = false
                // Niente rete: l'audio non deve lasciare il telefono.
                request.requiresOnDeviceRecognition = true

                var answered = false
                recognizer.recognitionTask(with: request) { taskResult, error in
                    guard !answered else { return }
                    if let error = error {
                        answered = true
                        result(FlutterError(code: "RICONOSCIMENTO",
                                            message: error.localizedDescription,
                                            details: nil))
                        return
                    }
                    guard let taskResult = taskResult, taskResult.isFinal else { return }
                    answered = true
                    result(taskResult.bestTranscription.formattedString)
                }
            }
        }
    }
}
