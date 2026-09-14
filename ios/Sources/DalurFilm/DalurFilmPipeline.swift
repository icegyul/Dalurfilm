import CoreImage
import Metal

/// DALUR film pipeline (iOS): Metal/Core Image port of the recipe model.
/// Live preview can be LOG or LUT; the recorded PRO master stays LOG/flat on
/// genuinely supported hardware and the LUT is stored as sidecar metadata.
public final class DalurFilmPipeline {
    private let context: CIContext
    public init() {
        self.context = CIContext(mtlDevice: MTLCreateSystemDefaultDevice()!)
    }

    public func apply(recipe: FilmRecipe, to image: CIImage, intensity: Float) -> CIImage {
        var out = image
        // Tone + color approximations mirror Android FilmEngine.applyCpu.
        if recipe.tone.exposure != 0 {
            let f = CIFilter(name: "CIExposureAdjust")
            f?.setValue(out, forKey: kCIInputImageKey)
            f?.setValue(recipe.tone.exposure * intensity, forKey: kCIInputEVKey)
            if let r = f?.outputImage { out = r }
        }
        if recipe.color.saturation != 0 {
            let f = CIFilter(name: "CIColorControls")
            f?.setValue(out, forKey: kCIInputImageKey)
            f?.setValue(1 + recipe.color.saturation * intensity, forKey: kCIInputSaturationKey)
            if let r = f?.outputImage { out = r }
        }
        // 3D .cube LUT stage + grain/halation/bloom/vignette attach here on macOS
        // builds (Metal shaders in Sources/DalurFilm/Shaders/).
        return out
    }
}
