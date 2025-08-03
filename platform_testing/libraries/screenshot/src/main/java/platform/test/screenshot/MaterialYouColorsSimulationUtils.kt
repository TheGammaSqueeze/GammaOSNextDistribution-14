/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package platform.test.screenshot

import android.graphics.Bitmap
import java.util.stream.IntStream

private val LIGHT_COLOR_MAPPING =
    arrayOf(
        -11640468 to -15111342,
        -986893 to -1641480,
    )

private val DARK_COLOR_MAPPING =
    arrayOf(
        -16750965 to -12075112,
        -16749487 to -10036302,
        -15590111 to -1,
        -15556945 to -11612698,
        -15131618 to -15590111,
        -14079187 to -1,
        -11640468 to -8128307,
        -4994349 to -13350318,
        -4069121 to -8128307,
        -3089183 to -16116455,
        -1808030 to -16749487,
        -986893 to -15590111,
        -852993 to -13616321,
        -1 to -1,
    )

private const val FILTER_SIZE = 2

private fun pixelWithinFilterRange(row: Int, col: Int, width: Int, height: Int): Boolean {
    return (row >= FILTER_SIZE &&
        row < height - FILTER_SIZE &&
        col >= FILTER_SIZE &&
        col < width - FILTER_SIZE)
}

private fun fillAverageColorForUnmappedPixel(
    bitmapArray: IntArray,
    colorValidArray: IntArray,
    row: Int,
    col: Int,
    bitmapWidth: Int
) {
    var validColorCount = 0
    var validRedSum = 0
    var validGreenSum = 0
    var validBlueSum = 0
    for (i in (row - FILTER_SIZE)..(row + FILTER_SIZE)) {
        for (j in (col - FILTER_SIZE)..(col + FILTER_SIZE)) {
            val pixelValue = colorValidArray[j + i * bitmapWidth]
            if (pixelValue != 0) {
                validColorCount = validColorCount + 1
                validRedSum = validRedSum + ((pixelValue shr 16) and 0xFF)
                validGreenSum = validGreenSum + ((pixelValue shr 8) and 0xFF)
                validBlueSum = validBlueSum + (pixelValue and 0xFF)
            }
        }
    }

    // If the valid color count of surrounding pixels is at least half of the total number,
    // get their averages.
    if (validColorCount > (FILTER_SIZE * 2 + 1) * (FILTER_SIZE * 2 + 1) / 2) {
        val red = (validRedSum / validColorCount + 0.5).toInt()
        val green = (validGreenSum / validColorCount + 0.5).toInt()
        val blue = (validBlueSum / validColorCount + 0.5).toInt()
        bitmapArray[col + row * bitmapWidth] =
            ((0xFF shl 24) or // alpha
            (red shl 16) or // red
                (green shl 8) or // green
                blue // blue
            )
    }
}

/**
 * Perform a Material You Color simulation for [originalBitmap] and return a bitmap after Material
 * You simulation.
 */
fun bitmapWithMaterialYouColorsSimulation(
    originalBitmap: Bitmap,
    isDarkTheme: Boolean,
    doPixelAveraging: Boolean = false,
): Bitmap {
    val bitmapArray = originalBitmap.toIntArray()
    val mappingToUse = if (isDarkTheme) DARK_COLOR_MAPPING else LIGHT_COLOR_MAPPING

    // An array to indicate whether a pixel has valid mapping. If yes, its actual color appears in
    // the array, otherwise 0 is stored.
    val colorValidArray = IntArray(originalBitmap.width * originalBitmap.height, { 0 })
    val stream = IntStream.range(0, originalBitmap.width * originalBitmap.height)
    stream
        .parallel()
        .forEach({
            val pixelValue = bitmapArray[it]
            for (k in mappingToUse) {
                if (pixelValue == k.first) {
                    bitmapArray[it] = k.second
                    colorValidArray[it] = k.second
                    break
                }
            }
        })

    if (doPixelAveraging) {
        val newStream = IntStream.range(0, originalBitmap.width * originalBitmap.height)
        newStream
            .parallel()
            .forEach({
                if (colorValidArray[it] == 0) {
                    val col = it % originalBitmap.width
                    val row = (it - col) / originalBitmap.width
                    if (
                        pixelWithinFilterRange(
                            row,
                            col,
                            originalBitmap.width,
                            originalBitmap.height
                        )
                    ) {
                        fillAverageColorForUnmappedPixel(
                            bitmapArray,
                            colorValidArray,
                            row,
                            col,
                            originalBitmap.width
                        )
                    }
                }
            })
    }

    return Bitmap.createBitmap(
        bitmapArray,
        originalBitmap.width,
        originalBitmap.height,
        originalBitmap.config
    )
}
