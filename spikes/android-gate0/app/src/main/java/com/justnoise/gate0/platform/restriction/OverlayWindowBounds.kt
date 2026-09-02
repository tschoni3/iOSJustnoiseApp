package com.justnoise.gate0.platform.restriction

/** Pixel bounds for the actual overlay window, excluding system-owned screen areas. */
internal data class OverlayWindowBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun calculate(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            insetLeft: Int,
            insetTop: Int,
            insetRight: Int,
            insetBottom: Int,
        ): OverlayWindowBounds? {
            if (minOf(insetLeft, insetTop, insetRight, insetBottom) < 0) return null
            val width = right.toLong() - left - insetLeft - insetRight
            val height = bottom.toLong() - top - insetTop - insetBottom
            if (width !in 1..Int.MAX_VALUE.toLong() || height !in 1..Int.MAX_VALUE.toLong()) {
                return null
            }
            return OverlayWindowBounds(
                left = left + insetLeft,
                top = top + insetTop,
                width = width.toInt(),
                height = height.toInt(),
            )
        }
    }
}
