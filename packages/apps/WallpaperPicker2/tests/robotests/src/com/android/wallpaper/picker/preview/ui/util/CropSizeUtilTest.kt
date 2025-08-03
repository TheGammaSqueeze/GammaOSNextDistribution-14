package com.android.wallpaper.picker.preview.ui.util

import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.preview.ui.util.CropSizeUtil.findMaxRectWithRatioIn
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class CropSizeUtilTest {

    @Test
    fun testFiveByFive_findMaxRectWithRatioIn_FiveByTen() {
        val testRect = Point(5, 5)
        val targetRect = Point(5, 10)
        val result = PointF(5f, 5f)

        assertThat(testRect.findMaxRectWithRatioIn(targetRect)).isEqualTo(result)
    }

    @Test
    fun testThreeByNine_findMaxRectWithRatioIn_SixByThree() {
        val testRect = Rect(0, 0, 3, 9)
        val targetRect = Rect(0, 0, 6, 3)
        val result = PointF(1f, 3f)

        assertThat(testRect.findMaxRectWithRatioIn(targetRect)).isEqualTo(result)
    }
}
