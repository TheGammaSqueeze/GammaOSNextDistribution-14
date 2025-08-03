/*
 * Copyright 2022 The Android Open Source Project
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

import android.content.Context
import android.util.SparseIntArray
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A rule to overload the target context system colors by [colors].
 *
 * This is especially useful to apply the colors before you start an activity using an
 * [ActivityScenarioRule] or any other rule, given that the colors must be [applied]
 * [MaterialYouColors.apply] *before* doing any resource resolution.
 */
class MaterialYouColorsRule(private val colors: MaterialYouColors = MaterialYouColors.GreenBlue) :
    TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                colors.apply(InstrumentationRegistry.getInstrumentation().targetContext)
                base.evaluate()
            }
        }
    }
}

/**
 * A util class to overload the Material You colors of a [Context] to some fixed values. This can be
 * used by screenshot tests so that device-specific colors don't impact the outcome of the test.
 *
 * @see apply
 */
class MaterialYouColors(
    @get:VisibleForTesting val colors: SparseIntArray,
) {
    /**
     * Apply these colors to [context].
     *
     * Important: No resource resolution must have be done on the context given to that method.
     */
    fun apply(context: Context) {
        RemoteViews.ColorResources.create(context, colors)?.apply(context)
    }

    companion object {
        private const val FIRST_RESOURCE_COLOR_ID = android.R.color.system_neutral1_0
        private const val LAST_RESOURCE_COLOR_ID = android.R.color.system_accent3_1000

        /**
         * An instance of [MaterialYouColors] with green/blue colors seed, that can be used directly
         * by tests.
         */
        val GreenBlue = fromColors(GREEN_BLUE)

        /**
         * Create a [MaterialYouColors] from [colors], where:
         * - `colors[i]` should be the value of `FIRST_RESOURCE_COLOR_ID + i`.
         * - [colors] must contain all values of all system colors, i.e. `colors.size` should be
         *   `LAST_RESOURCE_COLOR_ID - FIRST_RESOURCE_COLOR_ID + 1`.
         */
        private fun fromColors(colors: IntArray): MaterialYouColors {
            val expectedSize = LAST_RESOURCE_COLOR_ID - FIRST_RESOURCE_COLOR_ID + 1
            check(colors.size == expectedSize) {
                "colors should have exactly $expectedSize elements"
            }

            val sparseArray = SparseIntArray(/* initialCapacity= */ expectedSize)
            colors.forEachIndexed { i, color ->
                sparseArray.put(FIRST_RESOURCE_COLOR_ID + i, color)
            }

            return MaterialYouColors(sparseArray)
        }
    }
}

/**
 * Some green/blue colors, from system_neutral1_0 to system_accent3_1000, extracted using 0xB1EBFF
 * as seed color and "FRUIT_SALAD" as theme style.
 */
private val GREEN_BLUE =
    intArrayOf(
        -1,
        -393729,
        -1641480,
        -2562838,
        -4405043,
        -6181454,
        -7892073,
        -9668483,
        -11181979,
        -12760755,
        -14208458,
        -15590111,
        -16777216,
        -1,
        -393729,
        -2296322,
        -3217680,
        -4994349,
        -6770760,
        -8547171,
        -10257790,
        -11836822,
        -13350318,
        -14863301,
        -16376283,
        -16777216,
        -1,
        -720905,
        -4456478,
        -8128307,
        -10036302,
        -12075112,
        -14638210,
        -16742810,
        -16749487,
        -16756420,
        -16762839,
        -16768746,
        -16777216,
        -1,
        -720905,
        -4456478,
        -5901613,
        -7678281,
        -9454947,
        -11231613,
        -13139095,
        -15111342,
        -16756420,
        -16762839,
        -16768746,
        -16777216,
        -1,
        -393729,
        -2361857,
        -5051393,
        -7941655,
        -9783603,
        -11625551,
        -13729642,
        -16750723,
        -16757153,
        -16763326,
        -16769241,
        -16777216,
    )
