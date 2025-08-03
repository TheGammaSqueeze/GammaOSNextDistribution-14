/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.graphics.cts

import android.R
import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import android.util.Pair
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertWithMessage
import java.io.Serializable
import java.util.Arrays
import java.util.Locale
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.xmlpull.v1.XmlPullParser

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemPaletteTest(
        private val color: String,
        private val style: String,
        private val expectedPalette: IntArray
) {
    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun a_testThemeStyles() {
        // THEME_CUSTOMIZATION_OVERLAY_PACKAGES is not available in Wear OS
        if (FeatureUtil.isWatch()) return

        val newSetting = assurePaletteSetting()

        assertWithMessage("Invalid tonal palettes for $color $style").that(newSetting).isTrue()
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun b_testShades0and1000() {
        val context = getInstrumentation().targetContext

        assurePaletteSetting(context)

        Log.d(TAG, "Color: $color, Style: $style")

        val allPalettes = listOf(
                getAllAccent1Colors(context),
                getAllAccent2Colors(context),
                getAllAccent3Colors(context),
                getAllNeutral1Colors(context),
                getAllNeutral2Colors(context),
                getAllErrorColors(context)
        )

        Log.d(TAG, "whiteColor: ${getAllAccent1Colors(context).joinToString()} ")
        allPalettes.forEach { palette ->
            assertColor(palette.first(), Color.WHITE)
            assertColor(palette.last(), Color.BLACK)
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun c_testColorsMatchExpectedLuminance() {
        val context = getInstrumentation().targetContext

        assurePaletteSetting(context)

        val allPalettes = listOf(
                getAllAccent1Colors(context),
                getAllAccent2Colors(context), getAllAccent3Colors(context),
                getAllNeutral1Colors(context), getAllNeutral2Colors(context)
        )

        val labColor = doubleArrayOf(0.0, 0.0, 0.0)
        val expectedL = doubleArrayOf(
                100.0, 99.0, 95.0, 90.0, 80.0, 70.0, 60.0, 49.0, 40.0, 30.0, 20.0, 10.0, 0.0)

        allPalettes.forEach { palette ->
            palette.forEachIndexed { i, paletteColor ->
                val expectedColor = expectedL[i]
                ColorUtils.colorToLAB(paletteColor, labColor)
                assertWithMessage(
                        "Color ${Integer.toHexString(paletteColor)} at index $i should " +
                                "have L $expectedColor in LAB space."
                ).that(labColor[0]).isWithin(3.0).of(expectedColor)
            }
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun d_testContrastRatio() {
        val context = getInstrumentation().targetContext

        assurePaletteSetting(context)

        val atLeast4dot5 = listOf(
                Pair(0, 500), Pair(50, 600), Pair(100, 600), Pair(200, 700),
                Pair(300, 800), Pair(400, 900), Pair(500, 1000))

        val atLeast3dot0 = listOf(
                Pair(0, 400), Pair(50, 500), Pair(100, 500), Pair(200, 600), Pair(300, 700),
                Pair(400, 800), Pair(500, 900), Pair(600, 1000))

        val allPalettes = listOf(getAllAccent1Colors(context),
                getAllAccent2Colors(context), getAllAccent3Colors(context),
                getAllNeutral1Colors(context), getAllNeutral2Colors(context))

        fun pairContrastCheck(palette: IntArray, shades: Pair<Int, Int>, contrastLevel: Double) {
            val background = palette[shadeToArrayIndex(shades.first)]
            val foreground = palette[shadeToArrayIndex(shades.second)]
            val contrast = ColorUtils.calculateContrast(foreground, background)

            assertWithMessage("Shade ${shades.first} (#${Integer.toHexString(background)}) " +
                    "should have at least $contrastLevel contrast ratio against " +
                    "${shades.second} (#${Integer.toHexString(foreground)}), but had $contrast"
            ).that(contrast).isGreaterThan(contrastLevel)
        }

        allPalettes.forEach { palette ->
            atLeast4dot5.forEach { shades -> pairContrastCheck(palette, shades, 4.5) }
            atLeast3dot0.forEach { shades -> pairContrastCheck(palette, shades, 3.0) }
        }
    }

    @Test
    fun e_testDynamicColorContrast() {
        val context = getInstrumentation().targetContext

        // Ideally this should be 3.0, but there's colorspace conversion that causes rounding
        // errors.
        val foregroundContrast = 2.9f
        assurePaletteSetting(context)

        val bulkTest: BulkContrastTester = BulkContrastTester.of(
                // Colors against Surface [DARK]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_surface_dark,
                        R.color.system_surface_dim_dark,
                        R.color.system_surface_bright_dark,
                        R.color.system_surface_container_dark,
                        R.color.system_surface_container_high_dark,
                        R.color.system_surface_container_highest_dark,
                        R.color.system_surface_container_low_dark,
                        R.color.system_surface_container_lowest_dark,
                        R.color.system_surface_variant_dark
                ).andForegrounds(4.5f,
                        R.color.system_on_surface_dark,
                        R.color.system_on_surface_variant_dark,
                        R.color.system_primary_dark,
                        R.color.system_secondary_dark,
                        R.color.system_tertiary_dark,
                        R.color.system_error_dark
                ).andForegrounds(foregroundContrast,
                        R.color.system_outline_dark
                ),

                // Colors against Surface [LIGHT]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_surface_light,
                        R.color.system_surface_dim_light,
                        R.color.system_surface_bright_light,
                        R.color.system_surface_container_light,
                        R.color.system_surface_container_high_light,
                        R.color.system_surface_container_highest_light,
                        R.color.system_surface_container_low_light,
                        R.color.system_surface_container_lowest_light,
                        R.color.system_surface_variant_light
                ).andForegrounds(4.5f,
                        R.color.system_on_surface_light,
                        R.color.system_on_surface_variant_light,
                        R.color.system_primary_light,
                        R.color.system_secondary_light,
                        R.color.system_tertiary_light,
                        R.color.system_error_light
                ).andForegrounds(foregroundContrast,
                        R.color.system_outline_light
                ),

                // Colors against accents [DARK]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_primary_dark).andForegrounds(4.5f,
                        R.color.system_on_primary_dark),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_primary_container_dark).andForegrounds(4.5f,
                        R.color.system_on_primary_container_dark),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_secondary_dark).andForegrounds(4.5f,
                        R.color.system_on_secondary_dark),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_secondary_container_dark).andForegrounds(4.5f,
                        R.color.system_on_secondary_container_dark),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_tertiary_dark).andForegrounds(4.5f,
                        R.color.system_on_tertiary_dark),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_tertiary_container_dark).andForegrounds(4.5f,
                        R.color.system_on_tertiary_container_dark),

                // Colors against accents [LIGHT]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_primary_light).andForegrounds(4.5f,
                        R.color.system_on_primary_light),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_primary_container_light).andForegrounds(4.5f,
                        R.color.system_on_primary_container_light),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_secondary_light).andForegrounds(4.5f,
                        R.color.system_on_secondary_light),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_secondary_container_light).andForegrounds(4.5f,
                        R.color.system_on_secondary_container_light),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_tertiary_light).andForegrounds(4.5f,
                        R.color.system_on_tertiary_light),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_tertiary_container_light).andForegrounds(4.5f,
                        R.color.system_on_tertiary_container_light),

                // Colors against accents [FIXED]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_primary_fixed,
                        R.color.system_primary_fixed_dim
                ).andForegrounds(4.5f,
                        R.color.system_on_primary_fixed,
                        R.color.system_on_primary_fixed_variant
                ),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_secondary_fixed,
                        R.color.system_secondary_fixed_dim
                ).andForegrounds(4.5f,
                        R.color.system_on_secondary_fixed,
                        R.color.system_on_secondary_fixed_variant
                ),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_tertiary_fixed,
                        R.color.system_tertiary_fixed_dim
                ).andForegrounds(4.5f,
                        R.color.system_on_tertiary_fixed,
                        R.color.system_on_tertiary_fixed_variant
                ),

                // Auxiliary Colors [DARK]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_error_dark
                ).andForegrounds(4.5f,
                        R.color.system_on_error_dark
                ),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_error_container_dark
                ).andForegrounds(4.5f,
                        R.color.system_on_error_container_dark
                ),

                // Auxiliary Colors [LIGHT]
                ContrastTester.ofBackgrounds(context,
                        R.color.system_error_light
                ).andForegrounds(4.5f,
                        R.color.system_on_error_light
                ),

                ContrastTester.ofBackgrounds(context,
                        R.color.system_error_container_light
                ).andForegrounds(4.5f,
                        R.color.system_on_error_container_light
                )
        )
        bulkTest.run()
        assertWithMessage(bulkTest.allMessages).that(bulkTest.testPassed).isTrue()
    }

    private fun getAllAccent1Colors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_accent1_0,
                R.color.system_accent1_10,
                R.color.system_accent1_50,
                R.color.system_accent1_100,
                R.color.system_accent1_200,
                R.color.system_accent1_300,
                R.color.system_accent1_400,
                R.color.system_accent1_500,
                R.color.system_accent1_600,
                R.color.system_accent1_700,
                R.color.system_accent1_800,
                R.color.system_accent1_900,
                R.color.system_accent1_1000
        )
    }

    private fun getAllAccent2Colors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_accent2_0,
                R.color.system_accent2_10,
                R.color.system_accent2_50,
                R.color.system_accent2_100,
                R.color.system_accent2_200,
                R.color.system_accent2_300,
                R.color.system_accent2_400,
                R.color.system_accent2_500,
                R.color.system_accent2_600,
                R.color.system_accent2_700,
                R.color.system_accent2_800,
                R.color.system_accent2_900,
                R.color.system_accent2_1000
        )
    }

    private fun getAllAccent3Colors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_accent3_0,
                R.color.system_accent3_10,
                R.color.system_accent3_50,
                R.color.system_accent3_100,
                R.color.system_accent3_200,
                R.color.system_accent3_300,
                R.color.system_accent3_400,
                R.color.system_accent3_500,
                R.color.system_accent3_600,
                R.color.system_accent3_700,
                R.color.system_accent3_800,
                R.color.system_accent3_900,
                R.color.system_accent3_1000
        )
    }

    private fun getAllNeutral1Colors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_neutral1_0,
                R.color.system_neutral1_10,
                R.color.system_neutral1_50,
                R.color.system_neutral1_100,
                R.color.system_neutral1_200,
                R.color.system_neutral1_300,
                R.color.system_neutral1_400,
                R.color.system_neutral1_500,
                R.color.system_neutral1_600,
                R.color.system_neutral1_700,
                R.color.system_neutral1_800,
                R.color.system_neutral1_900,
                R.color.system_neutral1_1000
        )
    }

    private fun getAllNeutral2Colors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_neutral2_0,
                R.color.system_neutral2_10,
                R.color.system_neutral2_50,
                R.color.system_neutral2_100,
                R.color.system_neutral2_200,
                R.color.system_neutral2_300,
                R.color.system_neutral2_400,
                R.color.system_neutral2_500,
                R.color.system_neutral2_600,
                R.color.system_neutral2_700,
                R.color.system_neutral2_800,
                R.color.system_neutral2_900,
                R.color.system_neutral2_1000
        )
    }

    private fun getAllErrorColors(context: Context): IntArray {
        return getAllResourceColors(
                context,
                R.color.system_error_0,
                R.color.system_error_10,
                R.color.system_error_50,
                R.color.system_error_100,
                R.color.system_error_200,
                R.color.system_error_300,
                R.color.system_error_400,
                R.color.system_error_500,
                R.color.system_error_600,
                R.color.system_error_700,
                R.color.system_error_800,
                R.color.system_error_900,
                R.color.system_error_1000
        )
    }

    // Helper functions

    private fun assurePaletteSetting(
            context: Context = getInstrumentation().targetContext
    ): Boolean {
        if (checkExpectedPalette(context).size > 0) {
            return setExpectedPalette(context)
        }

        return true
    }

    private fun setExpectedPalette(context: Context): Boolean {

        // Update setting, so system colors will change
        runWithShellPermissionIdentity {
            Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    "{\"android.theme.customization.system_palette\":\"${color}\"," +
                            "\"android.theme.customization.theme_style\":\"${style}\"}"
            )
        }

        return conditionTimeoutCheck({
            val mismatches = checkExpectedPalette(context)
            val noMismatches = mismatches.size == 0

            if (DEBUG) {
                val setting = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES)

                Log.d(TAG,
                        if (noMismatches)
                            "Palette $setting is correctly set with colors: " +
                                    intArrayToHexString(expectedPalette)
                        else """
                        Setting:
                        $setting
                        Mismatches [index](color, expected):
                        ${
                            mismatches.map { (i, current, expected) ->
                                val c = if (current != null)
                                    Integer.toHexString(current) else "Null"
                                val e = if (expected != null)
                                    Integer.toHexString(expected) else "Null"

                                return@map "[$i]($c, $e) "
                            }.joinToString(" ")
                        }
                   """.trimIndent()
                )
            }

            return@conditionTimeoutCheck noMismatches
        })
    }

    private fun checkExpectedPalette(
            context: Context,
    ): MutableList<Triple<Int, Int?, Int?>> {
        val allColors = IntArray(65)
        System.arraycopy(getAllAccent1Colors(context), 0, allColors, 0, 13)
        System.arraycopy(getAllAccent2Colors(context), 0, allColors, 13, 13)
        System.arraycopy(getAllAccent3Colors(context), 0, allColors, 26, 13)
        System.arraycopy(getAllNeutral1Colors(context), 0, allColors, 39, 13)
        System.arraycopy(getAllNeutral2Colors(context), 0, allColors, 52, 13)

        return getArraysMismatch( allColors, expectedPalette )
    }

    /**
     * Convert the Material shade to an array position.
     *
     * @param shade Shade from 0 to 1000.
     * @return index in array
     * @see .getAllAccent1Colors
     * @see .getAllNeutral1Colors
     */
    private fun shadeToArrayIndex(shade: Int): Int {
        return when (shade) {
            0 -> 0
            10 -> 1
            50 -> 2
            else -> {
                shade / 100 + 2
            }
        }
    }

    private fun assertColor(@ColorInt observed: Int, @ColorInt expected: Int) {
        Assert.assertEquals(
                "Color = ${Integer.toHexString(observed)}, " +
                        "${Integer.toHexString(expected)} expected",
                expected,
                observed
        )
    }

    private fun getAllResourceColors(context: Context, vararg resources: Int): IntArray {
        if (resources.size != 13) throw Exception("Color palettes must be 13 in size")
        return resources.map { resId -> context.getColor(resId) }.toIntArray()
    }

    private fun intArrayToHexString(src: IntArray): String {
        return src.joinToString { n -> Integer.toHexString(n) }
    }

    private fun conditionTimeoutCheck(
            evalFunction: () -> Boolean,
            totalTimeout: Int = 15000,
            loopTime: Int = 1000
    ): Boolean {
        if (totalTimeout < loopTime) throw Exception("Loop time must be smaller than total time.")

        var remainingTime = totalTimeout

        while (remainingTime > 0) {
            if (evalFunction()) return true

            Thread.sleep(loopTime.coerceAtMost(remainingTime).toLong())
            remainingTime -= loopTime
        }

        return false
    }

    private fun getArraysMismatch(a: IntArray, b: IntArray): MutableList<Triple<Int, Int?, Int?>> {
        val len = a.size.coerceAtLeast(b.size)
        val mismatches: MutableList<Triple<Int, Int?, Int?>> = mutableListOf()

        repeat(len) { i ->
            val valueA = if (a.size >= i + 1) a[i] else null
            val valueB = if (b.size >= i + 1) b[i] else null

            if (valueA != valueB) mismatches.add(Triple(i, valueA, valueB))
        }

        return mismatches
    }

    // Helper Classes

    private class ContrastTester private constructor(
            var mContext: Context,
            vararg var mForegrounds: Int
    ) {
        var mBgGroups = ArrayList<Background>()

        fun checkContrastLevels(): ArrayList<String> {
            val newFailMessages = ArrayList<String>()
            mBgGroups.forEach { background ->
                newFailMessages.addAll(background.checkContrast(mForegrounds))
            }

            return newFailMessages
        }

        fun andForegrounds(contrastLevel: Float, vararg res: Int): ContrastTester {
            mBgGroups.add(Background(contrastLevel, *res))
            return this
        }

        private inner class Background internal constructor(
                private val mContrasLevel: Float,
                private vararg val mEntries: Int
        ) {
            fun checkContrast(foregrounds: IntArray): ArrayList<String> {
                val newFailMessages = ArrayList<String>()
                val res = mContext.resources

                foregrounds.forEach { fgRes ->
                    mEntries.forEach { bgRes ->
                        if (!checkPair(mContext, mContrasLevel, fgRes, bgRes)) {
                            val background = mContext.getColor(bgRes)
                            val foreground = mContext.getColor(fgRes)
                            val contrast = ColorUtils.calculateContrast(foreground, background)
                            val msg = "Background Color '${res.getResourceName(bgRes)}'" +
                                    "(#${Integer.toHexString(mContext.getColor(bgRes))}) " +
                                    "should have at least $mContrasLevel " +
                                    "contrast ratio against Foreground Color '" +
                                    res.getResourceName(fgRes) +
                                    "' (#${Integer.toHexString(mContext.getColor(fgRes))}) " +
                                    " but had $contrast"

                            newFailMessages.add(msg)
                        }
                    }
                }

                return newFailMessages
            }
        }

        companion object {
            fun ofBackgrounds(context: Context, vararg res: Int): ContrastTester {
                return ContrastTester(context, *res)
            }

            fun checkPair(context: Context, minContrast: Float, fgRes: Int, bgRes: Int): Boolean {
                val background = context.getColor(bgRes)
                val foreground = context.getColor(fgRes)
                val contrast = ColorUtils.calculateContrast(foreground, background)
                return contrast > minContrast
            }
        }
    }

    private class BulkContrastTester private constructor(vararg testsArgs: ContrastTester) {
        private val tests = testsArgs
        private val errorMessages: MutableList<String> = mutableListOf()

        val testPassed: Boolean get() = errorMessages.isEmpty()

        val allMessages: String
            get() =
                if (testPassed) "Test OK" else errorMessages.joinToString("\n")

        fun run() {
            errorMessages.clear()
            tests.forEach { test -> errorMessages.addAll(test.checkContrastLevels()) }
        }

        companion object {
            fun of(vararg testers: ContrastTester): BulkContrastTester {
                return BulkContrastTester(*testers)
            }
        }
    }

    companion object {
        private const val TAG = "SystemPaletteTest"
        private const val DEBUG = true

        private var initialContrast: Float = 0f

        @BeforeClass
        fun setUp() {
            initialContrast = getInstrumentation()
                    .targetContext
                    .getSystemService(UiModeManager::class.java)
                    .contrast
            putContrastInSettings(0f)
        }

        @AfterClass
        fun tearDown() {
            putContrastInSettings(initialContrast)
        }

        private fun putContrastInSettings(contrast: Float) {
            runShellCommand("settings put secure contrast_level $contrast")
        }

        @Parameterized.Parameters(name = "Palette {1} with color {0}")
        @JvmStatic
        fun testData(): List<Array<Serializable>> {
            val context: Context = getInstrumentation().targetContext
            val parser: XmlPullParser =
                    context.resources.getXml(android.graphics.cts.R.xml.valid_themes)

            val dataList: MutableList<Array<Serializable>> = mutableListOf()

            try {
                parser.next()
                parser.next()
                parser.require(XmlPullParser.START_TAG, null, "themes")
                while (parser.next() != XmlPullParser.END_TAG) {
                    parser.require(XmlPullParser.START_TAG, null, "theme")
                    val color = parser.getAttributeValue(null, "color")
                    while (parser.next() != XmlPullParser.END_TAG) {
                        val styleName = parser.name
                        parser.next()
                        val colors = Arrays.stream(
                                parser.text.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                        .toTypedArray())
                                .mapToInt { s: String ->
                                    Color.parseColor(
                                            "#$s"
                                    )
                                }
                                .toArray()
                        parser.next()
                        parser.require(XmlPullParser.END_TAG, null, styleName)
                        dataList.add(
                                arrayOf(
                                        color,
                                        styleName.uppercase(Locale.getDefault()),
                                        colors
                                )
                        )
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Error parsing xml", e)
            }

            return dataList
        }
    }
}
