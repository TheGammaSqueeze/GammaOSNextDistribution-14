package platform.test.screenshot.matchers

import android.graphics.Color
import android.graphics.Color.argb
import android.graphics.Color.rgb
import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import platform.test.screenshot.toIntArray
import platform.test.screenshot.utils.loadBitmap

class HumanEyeMatcherTest {
    private val matcher = HumanEyeMatcher()

    @Test
    fun diffColor_exactMatch() {
        val expected = rgb(5, 200, 200)
        val test = rgb(5, 200, 200)

        val result =
            matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun diffColor_almostMatchLowRed() {
        val expected = rgb(5, 200, 200)
        val test = rgb(6, 200, 201)

        val result =
            matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun diffColor_almostMatchHighRed() {
        val expected = rgb(200, 200, 200)
        val test = rgb(200, 201, 199)

        val result =
            matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun diffColor_notMatch() {
        val expected = rgb(200, 200, 200)
        val test = rgb(203, 212, 194)

        val result =
            matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertHasDiff(result, intArrayOf(diff))
    }

    @Test
    fun performDiff_sameBitmaps() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray")

        val result =
            matcher.compareBitmaps(
                expected = first.toIntArray(),
                given = second.toIntArray(),
                width = first.width,
                height = first.height
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_sameSize_partialCompare_diffsInComparedArea() {
        val first = IntArray(6) { Color.BLACK }
        val second = IntArray(6) { Color.WHITE }
        val comparedRegion = Rect(/* left= */ 0, /* top= */ 0, /* right= */ 1, /* bottom= */ 1)
        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 2,
                regions = listOf(comparedRegion)
            )

        assertHasDiff(
            result,
            intArrayOf(
                diff,
                diff,
                same,
                diff,
                diff,
                same,
            )
        )
    }

    @Test
    fun performDiff_forIsolatedPixel_diffsOnBigDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[4] = rgb(0, 21, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                same,
                diff,
                same,
                same,
                same,
                same,
            )
        )
    }

    @Test
    fun performDiff_forIsolatedPixel_ignoresSmallDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[4] = rgb(0, 15, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forPairOfPixels_diffsOnBigDiff() {
        val first = IntArray(12) { Color.BLACK }
        val second = IntArray(12) { Color.BLACK }
        second[5] = rgb(0, 21, 0)
        second[6] = rgb(0, 21, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 3,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                same,
                same,
                diff,
                diff,
                same,
                same,
                same,
                same,
                same,
            )
        )
    }

    @Test
    fun performDiff_forPairOfPixels_ignoresSmallDiff() {
        val first = IntArray(12) { Color.BLACK }
        val second = IntArray(12) { Color.BLACK }
        second[5] = rgb(0, 6, 0)
        second[6] = rgb(0, 6, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forLineOfPixels_diffsOnBigDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[3] = rgb(0, 7, 0)
        second[4] = rgb(0, 7, 0)
        second[5] = rgb(0, 7, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                diff,
                diff,
                diff,
                same,
                same,
                same,
            )
        )
    }

    @Test
    fun performDiff_forLineOfPixels_ignoresSmallDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[3] = rgb(0, 3, 0)
        second[4] = rgb(0, 3, 0)
        second[5] = rgb(0, 3, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_for2pxLineOfPixels_diffsOnBigDiff() {
        val first = IntArray(12) { Color.BLACK }
        val second = IntArray(12) { Color.BLACK }
        second[3] = rgb(0, 6, 0)
        second[4] = rgb(0, 6, 0)
        second[5] = rgb(0, 6, 0)
        second[6] = rgb(0, 6, 0)
        second[7] = rgb(0, 6, 0)
        second[8] = rgb(0, 6, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 4,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                same,
                same,
                same,
            )
        )
    }

    @Test
    fun performDiff_for2pxLineOfPixels_ignoresSmallDiff() {
        val first = IntArray(12) { Color.BLACK }
        val second = IntArray(12) { Color.BLACK }
        second[3] = rgb(0, 2, 0)
        second[4] = rgb(0, 2, 0)
        second[5] = rgb(0, 2, 0)
        second[6] = rgb(0, 2, 0)
        second[7] = rgb(0, 2, 0)
        second[8] = rgb(0, 2, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 4,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forBlockOfPixels_diffsOnBigDiff() {
        val first = IntArray(16) { Color.BLACK }
        val second = IntArray(16) { index -> if (index > 3) rgb(0, 2, 0) else Color.BLACK }

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 4,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                same,
                same,
                same,
                same,
                same,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
            )
        )
    }

    @Test
    fun performDiff_forBlockOfPixels_ignoresSmallDiff() {
        val first = IntArray(16) { Color.BLACK }
        val second = IntArray(16) { index -> if (index > 3) rgb(0, 1, 0) else Color.BLACK }

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 4,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forEffectivelyIsolatedPixel_ignoresSmallDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[1] = rgb(0, 2, 0)
        second[3] = rgb(0, 2, 0)
        second[4] = rgb(0, 6, 0)
        second[5] = rgb(0, 2, 0)
        second[7] = rgb(0, 2, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forEffectively1pxLine_ignoresSmallDiff() {
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[1] = rgb(0, 2, 0)
        second[3] = rgb(0, 4, 0)
        second[4] = rgb(0, 6, 0)
        second[5] = rgb(0, 4, 0)
        second[7] = rgb(0, 2, 0)

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forBlockOfPixels_whenIgnoringGrouping_diffsOnMediumDiff() {
        val matcherWithoutGrouping = HumanEyeMatcher(accountForGrouping = false)
        val first = IntArray(16) { Color.BLACK }
        val second = IntArray(16) { index -> if (index > 3) rgb(0, 2, 0) else Color.BLACK }

        val result =
            matcherWithoutGrouping.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 4,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                same,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
                diff,
            )
        )
    }

    @Test
    fun performDiff_forBlockOfPixels_whenIgnoringGrouping_ignoresSmallDiff() {
        val matcherWithoutGrouping = HumanEyeMatcher(accountForGrouping = false)
        val first = IntArray(16) { Color.BLACK }
        val second = IntArray(16) { index -> if (index > 3) rgb(0, 1, 0) else Color.BLACK }

        val result =
            matcherWithoutGrouping.compareBitmaps(
                expected = first,
                given = second,
                width = 4,
                height = 4,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_forIsolatedPixel_whenIgnoringGrouping_diffsOnMediumDiff() {
        val matcherIgnoringGrouping = HumanEyeMatcher(accountForGrouping = false)
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[4] = rgb(0, 2, 0)

        val result =
            matcherIgnoringGrouping.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertHasDiff(
            result,
            intArrayOf(
                same,
                same,
                same,
                same,
                diff,
                same,
                same,
                same,
                same,
            )
        )
    }

    @Test
    fun performDiff_forIsolatedPixel_whenIgnoringGrouping_ignoresSmallDiff() {
        val matcherIgnoringGrouping = HumanEyeMatcher(accountForGrouping = false)
        val first = IntArray(9) { Color.BLACK }
        val second = IntArray(9) { Color.BLACK }
        second[4] = rgb(0, 1, 0)

        val result =
            matcherIgnoringGrouping.compareBitmaps(
                expected = first,
                given = second,
                width = 3,
                height = 3,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_ignoresSmallAlphaDiff() {
        val first = intArrayOf(argb(128, 255, 255, 255))
        val second = intArrayOf(argb(129, 255, 255, 255))

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_withWhiteColor_diffsOnBigAlphaDiff() {
        val first = intArrayOf(argb(251, 255, 255, 255))
        val second = intArrayOf(argb(255, 255, 255, 255))

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertHasDiff(result, intArrayOf(diff))
    }

    @Test
    fun performDiff_withBlackColor_diffsOnBigAlphaDiff() {
        val first = intArrayOf(argb(251, 0, 0, 0))
        val second = intArrayOf(argb(255, 0, 0, 0))

        val result =
            matcher.compareBitmaps(
                expected = first,
                given = second,
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertHasDiff(result, intArrayOf(diff))
    }

    @Test
    fun performDiff_whenIgnoringTransparency_ignoresBigAlphaDiff() {
        val matcherIgnoringTransparency = HumanEyeMatcher(accountForTransparency = false)
        val first = intArrayOf(argb(1, 255, 255, 255))
        val second = intArrayOf(argb(255, 255, 255, 255))

        val result =
            matcherIgnoringTransparency.compareBitmaps(
                expected = first,
                given = second,
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertMatches(result)
    }

    @Test
    fun performDiff_whenIgnoringTransparency_diffsBigColorDiff() {
        val matcherIgnoringTransparency = HumanEyeMatcher(accountForTransparency = false)
        val first = intArrayOf(rgb(255, 250, 255))
        val second = intArrayOf(rgb(255, 255, 255))

        val result =
            matcherIgnoringTransparency.compareBitmaps(
                expected = first,
                given = second,
                width = 1,
                height = 1,
                regions = emptyList()
            )

        assertHasDiff(result, intArrayOf(diff))
    }

    private fun assertMatches(result: MatchResult) {
        assertThat(result.matches).isTrue()
        assertThat(result.diff?.toIntArray()).isNull()
    }

    private fun assertHasDiff(result: MatchResult, diff: IntArray) {
        assertThat(result.matches).isFalse()
        assertThat(result.diff!!.toIntArray()).isEqualTo(diff)
    }

    private companion object {
        const val diff = Color.MAGENTA
        const val same = Color.TRANSPARENT
    }
}
