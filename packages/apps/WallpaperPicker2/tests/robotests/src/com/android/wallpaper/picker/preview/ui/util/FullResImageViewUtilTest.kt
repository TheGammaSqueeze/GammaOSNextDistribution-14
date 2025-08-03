package com.android.wallpaper.picker.preview.ui.util

import android.graphics.Point
import android.graphics.PointF
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class FullResImageViewUtilTest {

    @Test
    fun getScaleAndCenter_wallpaperSizeSameAsScreen() {
        val screenWidth = 1080
        val screenHeight = 1920
        val wallpaperSize = Point(1080, 1920)
        assertThat(
                FullResImageViewUtil.getScaleAndCenter(
                    viewSize = Point(screenWidth, screenHeight),
                    rawWallpaperSize = wallpaperSize,
                    displaySize = Point(screenWidth, screenHeight),
                    cropRect = null,
                    isRtl = false,
                )
            )
            .isEqualTo(
                FullResImageViewUtil.ScaleAndCenter(
                    1F,
                    8F,
                    1F,
                    PointF(540F, 960F),
                )
            )
    }

    @Test
    fun getScaleAndCenter_wallpaperSizeLargerThanScreen() {
        val screenWidth = 1080
        val screenHeight = 1920
        val wallpaperSize = Point(3840, 3840)
        assertThat(
                FullResImageViewUtil.getScaleAndCenter(
                    viewSize = Point(screenWidth, screenHeight),
                    rawWallpaperSize = wallpaperSize,
                    displaySize = Point(screenWidth, screenHeight),
                    cropRect = null,
                    isRtl = false,
                )
            )
            .isEqualTo(
                FullResImageViewUtil.ScaleAndCenter(
                    0.5F,
                    8F,
                    0.5F,
                    PointF(1920F, 1920F),
                )
            )
    }

    @Test
    fun getScaleAndCenter_wallpaperSizeSmallerThanScreen() {
        val screenWidth = 1080
        val screenHeight = 1920
        val wallpaperSize = Point(960, 960)
        assertThat(
                FullResImageViewUtil.getScaleAndCenter(
                    viewSize = Point(screenWidth, screenHeight),
                    rawWallpaperSize = wallpaperSize,
                    displaySize = Point(screenWidth, screenHeight),
                    cropRect = null,
                    isRtl = false,
                )
            )
            .isEqualTo(
                FullResImageViewUtil.ScaleAndCenter(
                    2F,
                    8F,
                    2F,
                    PointF(480F, 480F),
                )
            )
    }
}
