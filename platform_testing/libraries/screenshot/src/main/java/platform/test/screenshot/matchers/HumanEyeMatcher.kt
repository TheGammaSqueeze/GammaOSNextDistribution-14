package platform.test.screenshot.matchers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import platform.test.screenshot.proto.ScreenshotResultProto

/**
 * A matcher designed to replicate a human checking an image to see if there are differences.
 *
 * [accountForGrouping] When this setting is on, the matcher will be more more lenient on color
 * differences of pixels that are not found in a group of differing pixels. This means that a single
 * pixel that is not the same as the golden will be given more leniency than a line or circle of
 * pixels that are different from the golden.
 *
 * [accountForTransparency] The matcher will apply a background to any non-opaque pixel so the color
 * diffing is only comparing opaque colours. Multiple different backgrounds will be used to ensure
 * pixels close to the background color fail if their alpha is significantly different from the
 * golden.
 */
class HumanEyeMatcher(
    private val accountForGrouping: Boolean = true,
    private val accountForTransparency: Boolean = true,
) : BitmapMatcher() {
    override fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect>
    ): MatchResult {
        check(expected.size == given.size) {
            "Pixels in expected (${expected.size}) does not match pixels in actual (${given.size})"
        }

        val filter = getFilter(width, height, regions)
        var ignored = 0

        // Prepare colorDiffArray
        val colorDiffSqArray =
            IntArray(width * height) { index ->
                if (filter[index]) {
                    if (accountForTransparency) {
                        colorDiffSqWithTransparency(expected[index], given[index])
                    } else {
                        colorDiffSq(expected[index], given[index])
                    }
                } else {
                    ignored++
                    IGNORED_COLOR_DIFF
                }
            }

        fun isIndexSameForLargeArea(index: Int) = isSameForLargeArea(colorDiffSqArray[index])

        if (!accountForGrouping) {
            val diffArray = lazy { IntArray(width * height) { Color.TRANSPARENT } }
            var different = 0
            expected.indices.forEach { index ->
                if (!isIndexSameForLargeArea(index)) {
                    diffArray.value[index] = Color.MAGENTA
                    different++
                }
            }
            return createMatchResult(
                width,
                height,
                width * height - ignored - different,
                different,
                ignored,
                diffArray,
            )
        }

        fun getEasiestThresholdFailed(x: Int, y: Int): Int? {
            val colorDiff = colorDiffSqArray[x + width * y]
            return when {
                colorDiff == IGNORED_COLOR_DIFF -> null
                colorDiff > THRESHOLD_ISOLATED_PIXEL_SQ -> THRESHOLD_ISOLATED_PIXEL_SQ
                colorDiff > THRESHOLD_1PX_LINE_OF_PIXELS_SQ -> THRESHOLD_1PX_LINE_OF_PIXELS_SQ
                colorDiff > THRESHOLD_2PX_LINE_OF_PIXELS_SQ -> THRESHOLD_2PX_LINE_OF_PIXELS_SQ
                colorDiff > THRESHOLD_BLOCK_OF_PIXELS_SQ -> THRESHOLD_BLOCK_OF_PIXELS_SQ
                else -> 0
            }
        }

        var different = 0
        val diffArray = lazy { IntArray(colorDiffSqArray.size) { Color.TRANSPARENT } }

        colorDiffSqArray.indices.forEach { index ->
            // Also covers the ignored case
            if (isIndexSameForLargeArea(index)) return@forEach

            val x = index % width
            val y = index / width

            val currThreshold = getEasiestThresholdFailed(x, y)!!
            // null = ignored or out of bounds of image
            val upThreshold = if (y > 0) getEasiestThresholdFailed(x, y - 1) else null
            val downThreshold = if (y < height - 1) getEasiestThresholdFailed(x, y + 1) else null
            val leftThreshold = if (x > 0) getEasiestThresholdFailed(x - 1, y) else null
            val rightThreshold = if (x < width - 1) getEasiestThresholdFailed(x + 1, y) else null

            // Pixels with lower diff thresholds are not counted as neighbouring diffs
            var neighbouringDiffs = 4
            if (upThreshold != null && currThreshold > upThreshold) neighbouringDiffs--
            if (downThreshold != null && currThreshold > downThreshold) neighbouringDiffs--
            if (leftThreshold != null && currThreshold > leftThreshold) neighbouringDiffs--
            if (rightThreshold != null && currThreshold > rightThreshold) neighbouringDiffs--

            if (!isSame(colorDiffSqArray[index], neighbouringDiffs)) {
                diffArray.value[index] = Color.MAGENTA
                different++
            }
        }

        return createMatchResult(
            width,
            height,
            width * height - ignored - different,
            different,
            ignored,
            diffArray,
        )
    }

    private fun colorDiffSqWithTransparency(referenceColor: Int, testColor: Int): Int {
        val diffWithWhite =
            colorDiffSq(
                blendWithBackground(referenceColor, Color.WHITE),
                blendWithBackground(testColor, Color.WHITE)
            )
        val diffWithBlack =
            colorDiffSq(
                blendWithBackground(referenceColor, Color.BLACK),
                blendWithBackground(testColor, Color.BLACK)
            )

        return max(diffWithWhite, diffWithBlack)
    }

    // ref
    // R. F. Witzel, R. W. Burnham, and J. W. Onley. Threshold and suprathreshold perceptual color
    // differences. J. Optical Society of America, 63:615{625, 1973. 14
    private fun colorDiffSq(referenceColor: Int, testColor: Int): Int {
        val green = Color.green(referenceColor) - Color.green(testColor)
        val blue = Color.blue(referenceColor) - Color.blue(testColor)
        val red = Color.red(referenceColor) - Color.red(testColor)
        val redMean = (Color.red(referenceColor) + Color.red(testColor)) / 2
        val redScalar = if (redMean < 128) 2 else 3
        val blueScalar = if (redMean < 128) 3 else 2
        val greenScalar = 4

        return (redScalar * red * red) + (greenScalar * green * green) + (blueScalar * blue * blue)
    }

    /**
     * This function is more lenient (uses a higher diff threshold) on isolated pixels than it is on
     * lines or blocks of differing pixels. This is to emulate the human eye. It is harder to see
     * color differences in very small objects as compared to larger ones.
     */
    private fun getThresholdSq(neighbouringDiffs: Int): Int =
        when (neighbouringDiffs) {
            0,
            1 -> THRESHOLD_ISOLATED_PIXEL_SQ
            2 -> THRESHOLD_1PX_LINE_OF_PIXELS_SQ
            3 -> THRESHOLD_2PX_LINE_OF_PIXELS_SQ
            4 -> THRESHOLD_BLOCK_OF_PIXELS_SQ
            else ->
                throw IllegalArgumentException(
                    "Unsupported neighbouringDiffs value: $neighbouringDiffs"
                )
        }

    private fun isSameForLargeArea(colorDiff: Int) = colorDiff <= THRESHOLD_BLOCK_OF_PIXELS_SQ

    private fun isSame(colorDiff: Int, neighbouringDiffs: Int) =
        colorDiff <= getThresholdSq(neighbouringDiffs)

    /** Any alpha component of the [backgroundColor] will be ignored. */
    private fun blendWithBackground(color: Int, backgroundColor: Int): Int {
        val alpha: Float = Color.alpha(color) / 255f
        if (alpha == 1f) return color
        if (alpha == 0f) return backgroundColor

        val outRed = alpha * Color.red(color) + (1 - alpha) * Color.red(backgroundColor)
        val outGreen = alpha * Color.green(color) + (1 - alpha) * Color.green(backgroundColor)
        val outBlue = alpha * Color.blue(color) + (1 - alpha) * Color.blue(backgroundColor)

        return Color.valueOf(outRed / 255f, outGreen / 255f, outBlue / 255f).toArgb()
    }

    private fun createMatchResult(
        width: Int,
        height: Int,
        samePixels: Int,
        differentPixels: Int,
        ignoredPixels: Int,
        diffBitmapArray: Lazy<IntArray>,
    ): MatchResult {
        val stats =
            ScreenshotResultProto.DiffResult.ComparisonStatistics.newBuilder()
                .setNumberPixelsCompared(width * height)
                .setNumberPixelsIdentical(samePixels)
                .setNumberPixelsDifferent(differentPixels)
                .setNumberPixelsIgnored(ignoredPixels)
                .build()

        return if (differentPixels > 0) {
            val diff =
                Bitmap.createBitmap(diffBitmapArray.value, width, height, Bitmap.Config.ARGB_8888)
            MatchResult(matches = false, diff = diff, comparisonStatistics = stats)
        } else {
            MatchResult(matches = true, diff = null, comparisonStatistics = stats)
        }
    }

    private companion object {
        const val THRESHOLD_BLOCK_OF_PIXELS_SQ = 3 * 3
        const val THRESHOLD_2PX_LINE_OF_PIXELS_SQ = 10 * 10
        const val THRESHOLD_1PX_LINE_OF_PIXELS_SQ = 12 * 12
        const val THRESHOLD_ISOLATED_PIXEL_SQ = 40 * 40

        const val IGNORED_COLOR_DIFF = -1
    }
}
