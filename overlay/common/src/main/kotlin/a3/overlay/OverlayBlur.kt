package a3.overlay

/** Separable box blur on ARGB8888 pixels. Shared by JVM compositor and Android Bitmap. */
object OverlayBlur {
    fun blur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val r = radius.coerceIn(1, 24)
        val a = src.copyOf()
        val b = IntArray(src.size)
        box(a, b, width, height, r, horizontal = true)
        box(b, a, width, height, r, horizontal = false)
        box(a, b, width, height, r, horizontal = true)
        box(b, a, width, height, r, horizontal = false)
        return a
    }

    private fun box(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val span = radius * 2 + 1
        if (horizontal) {
            for (y in 0 until h) {
                var sr = 0
                var sg = 0
                var sb = 0
                var sa = 0
                fun add(x: Int) {
                    val p = src[y * w + x.coerceIn(0, w - 1)]
                    sa += (p ushr 24) and 0xFF
                    sr += (p ushr 16) and 0xFF
                    sg += (p ushr 8) and 0xFF
                    sb += p and 0xFF
                }
                fun sub(x: Int) {
                    val p = src[y * w + x.coerceIn(0, w - 1)]
                    sa -= (p ushr 24) and 0xFF
                    sr -= (p ushr 16) and 0xFF
                    sg -= (p ushr 8) and 0xFF
                    sb -= p and 0xFF
                }
                for (i in -radius..radius) add(i)
                for (x in 0 until w) {
                    dst[y * w + x] =
                        (sa / span shl 24) or (sr / span shl 16) or (sg / span shl 8) or (sb / span)
                    add(x + radius + 1)
                    sub(x - radius)
                }
            }
        } else {
            for (x in 0 until w) {
                var sr = 0
                var sg = 0
                var sb = 0
                var sa = 0
                fun add(y: Int) {
                    val p = src[y.coerceIn(0, h - 1) * w + x]
                    sa += (p ushr 24) and 0xFF
                    sr += (p ushr 16) and 0xFF
                    sg += (p ushr 8) and 0xFF
                    sb += p and 0xFF
                }
                fun sub(y: Int) {
                    val p = src[y.coerceIn(0, h - 1) * w + x]
                    sa -= (p ushr 24) and 0xFF
                    sr -= (p ushr 16) and 0xFF
                    sg -= (p ushr 8) and 0xFF
                    sb -= p and 0xFF
                }
                for (i in -radius..radius) add(i)
                for (y in 0 until h) {
                    dst[y * w + x] =
                        (sa / span shl 24) or (sr / span shl 16) or (sg / span shl 8) or (sb / span)
                    add(y + radius + 1)
                    sub(y - radius)
                }
            }
        }
    }
}
