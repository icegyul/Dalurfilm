import AVFoundation
import Foundation

/// AVFoundation camera foundation for DALUR film (iOS).
/// Reference: NextLevel/NextLevel @ 2fa4250 (MIT) — photo/RAW/video, focus,
/// exposure, white balance, configurable frame rate/encoding, HEVC option.
/// DALUR UI, film pipeline (Metal/Core Image) and journey engine are original.
/// Log is capability-gated: report a genuine profile only when the device
/// exposes it; otherwise disable Log and explain (never fake).
public final class DalurCameraManager: NSObject, Sendable {
    public let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "film.dalur.camera")

    public func configureForPhoto() {
        sessionQueue.async { [self] in
            session.beginConfiguration()
            session.sessionPreset = .photo
            // Device/inputs added by the hosting SwiftUI view; kept minimal here
            // so project structure validates without camera hardware.
            session.commitConfiguration()
        }
    }

    public func logProfileIfSupported() -> String? {
        // Genuine Log on iOS depends on device/format (e.g. Apple Log on Pro
        // hardware). Query actual formats; return nil when unsupported.
        // Implemented fully when building with Xcode 26 + iOS 26 SDK on macOS.
        return nil
    }
}
