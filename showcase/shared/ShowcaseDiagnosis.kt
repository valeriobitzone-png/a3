package a3.showcase

/**
 * SV-001. Cause verified from v0.1 device screenshots + frozen renderer source,
 * not inferred from the brief.
 *
 * Evidence:
 * - review-assets/showcase/glass.png (v0.1): uniform gray, no chroma, no squircle rim.
 * - review-assets/showcase/all.png (v0.1): coarse tiled wash + gray fill; Material chips
 *   sit on an opaque paper bar.
 * - GlassSurface.kt / MacGlassSurface.kt: the blurred layer is `.background(Color.White)`,
 *   then a fillColor@fillOpacity overlay. RenderEffect/blur filters that layer's own
 *   pixels, not the scene behind (not CSS backdrop-filter).
 * - DynamicPalette.fixtureWallpaper is internal to the renderer and is never composed
 *   by :showcase. Host background is token paper. Level GLASS/AXIS/MOTION/DYNAMIC
 *   also disable ShowcaseGradient, so those screens have zero wallpaper chroma.
 * - ComposeCatalog already wraps stack/row/list/item in GlassSurface. The live scene
 *   looked flat because that glass had nothing chromatic to frost.
 * - Renderer LG tests look glassy because GlassRaster.paint() composites a checker /
 *   wallpaper bitmap *into* the raster, a different path from the Compose scene.
 */
object ShowcaseDiagnosis {
    const val ID = "SV-001"
    const val CAUSE = "flat-gray-because-white-glass-layer-plus-missing-wallpaper"
    const val WHITE_LAYER = ".background(Color.White)"
    const val FILL_OPACITY = "0.38"
    const val WALLPAPER_API = "fixtureWallpaper"
    const val NOT_TRANSPARENT_CHECKER = "v0.1 checker on ALL is ShowcaseGradient step-32, not a transparent checkerboard"
}