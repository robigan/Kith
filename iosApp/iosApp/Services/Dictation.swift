import AVFoundation
import Observation
import Speech

/// Speech-to-text for interaction notes (FR-044). Prefers on-device recognition (NFR-033).
@MainActor
@Observable
final class Dictation {
    private(set) var isRecording = false
    /// The note text including what has been dictated so far; nil until dictation produces something.
    private(set) var transcript: String?
    private(set) var error: String?

    @ObservationIgnored private let engine = AVAudioEngine()
    @ObservationIgnored private var request: SFSpeechAudioBufferRecognitionRequest?
    @ObservationIgnored private var task: SFSpeechRecognitionTask?
    @ObservationIgnored private let recognizer = SFSpeechRecognizer()

    func toggle(appendingTo base: String) {
        if isRecording { stop() } else { Task { await start(base: base) } }
    }

    func start(base: String) async {
        error = nil
        guard await Self.authorize() else {
            error = "Allow Speech Recognition and Microphone access in Settings to dictate."
            return
        }
        guard let recognizer, recognizer.isAvailable else {
            error = "Dictation isn't available right now."
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            request.requiresOnDeviceRecognition = recognizer.supportsOnDeviceRecognition
            request.addsPunctuation = true
            self.request = request

            let input = engine.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            isRecording = true

            let prefix = base.isEmpty || base.hasSuffix(" ") || base.hasSuffix("\n") ? base : base + " "
            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                let text = result?.bestTranscription.formattedString
                let isFinal = result?.isFinal ?? false
                Task { @MainActor in
                    guard let self else { return }
                    if let text { self.transcript = prefix + text }
                    if error != nil || isFinal { self.stop() }
                }
            }
        } catch {
            self.error = "Couldn't start dictation."
            stop()
        }
    }

    func stop() {
        guard isRecording || task != nil else { return }
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private static func authorize() async -> Bool {
        let speech = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0 == .authorized) }
        }
        guard speech else { return false }
        return await AVAudioApplication.requestRecordPermission()
    }
}
