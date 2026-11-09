import SwiftUI
import Vision
import VisionKit

/// Scans a business card with the document camera and returns its text lines (FR-026). OCR runs on-device.
struct BusinessCardScanner: UIViewControllerRepresentable {
    let onFinish: ([String]?) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        guard VNDocumentCameraViewController.isSupported else {
            let alert = UIAlertController(title: "Camera unavailable", message: "Scanning business cards needs a device camera.", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in onFinish(nil) })
            let host = UIViewController()
            DispatchQueue.main.async { host.present(alert, animated: true) }
            return host
        }
        let scanner = VNDocumentCameraViewController()
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onFinish: ([String]?) -> Void
        init(onFinish: @escaping ([String]?) -> Void) { self.onFinish = onFinish }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            guard scan.pageCount > 0, let image = scan.imageOfPage(at: 0).cgImage else { return onFinish(nil) }
            let request = VNRecognizeTextRequest { [onFinish] request, _ in
                let lines = (request.results as? [VNRecognizedTextObservation])?
                    .sorted { $0.boundingBox.minY > $1.boundingBox.minY }
                    .compactMap { $0.topCandidates(1).first?.string } ?? []
                DispatchQueue.main.async { onFinish(lines) }
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false
            DispatchQueue.global(qos: .userInitiated).async {
                try? VNImageRequestHandler(cgImage: image).perform([request])
            }
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            onFinish(nil)
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            onFinish(nil)
        }
    }
}
