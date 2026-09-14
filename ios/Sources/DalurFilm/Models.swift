import Foundation

/// Versioned DALUR Film Recipe (mirrors shared/ Kotlin model + JSON schema).
public struct FilmRecipe: Codable, Sendable {
    public var id: String
    public var version: Int
    public var name: String
    public var description: String
    public var creatorType: String
    /// Browse grouping tab; absent in older JSON files -> treat as "General".
    public var category: String? = nil
    public var lut: LutRef?
    public var intensity: Float
    public var tone: Tone
    public var color: ColorAdjust
    public var effects: Effects
    public struct LutRef: Codable, Sendable {
        public var format: String; public var size: Int
        public var source: String; public var hash: String
        public var kind: String
    }
    public struct Tone: Codable, Sendable {
        public var exposure: Float = 0; public var contrast: Float = 0
        public var highlights: Float = 0; public var shadows: Float = 0
        public var fade: Float = 0; public var blacks: Float = 0; public var whites: Float = 0
    }
    public struct ColorAdjust: Codable, Sendable {
        public var temperature: Float = 0; public var tint: Float = 0; public var saturation: Float = 0
    }
    public struct Effects: Codable, Sendable {
        public var grain: Float = 0; public var grainSize: Float = 0.5
        public var halation: Float = 0; public var bloom: Float = 0
        public var vignette: Float = 0; public var chromaticAberration: Float = 0
    }
}

public struct GpsPoint: Codable, Sendable {
    public var latitude: Double; public var longitude: Double
    public var altitude: Double?; public var heading: Double?
}

public struct CaptureMetadata: Codable, Sendable {
    public var mediaId: String; public var timestampMillis: Int64
    public var gps: GpsPoint?; public var locationUnavailable: Bool
    public var mediaType: String; public var mediaUri: String
    public var filmRecipeId: String?; public var filmRecipeVersion: Int?
    public var codec: String?; public var colorProfile: String?
    public var lutRecipeId: String?; public var lutRecipeVersion: Int?
    public var lutHash: String?; public var lutIntensity: Float?
    public var filmApplied: Bool?
    public var filmError: String?
    public var filmSupportReport: FilmSupportReport?
    public var sidecarUri: String?
}

public struct FilmSupportReport: Codable, Sendable {
    public var applied: [String]
    public var notSupported: [String]
}

/// LOG VIEW <-> LUT VIEW monitor state. Master is never baked with the LUT.
public enum MonitorMode: String, Sendable { case log = "LOG"; case lut = "LUT" }

/// Capability report (mirrors shared/ CapabilityReport; ProRes gated to iOS hardware).
public struct CapabilityReport: Codable, Sendable {
    public var logSupported: Bool
    public var logProfile: String?
    public var hevcSupported: Bool
    public var proResSupported: Bool
    public var usbMassStorageSupported: Bool
}
