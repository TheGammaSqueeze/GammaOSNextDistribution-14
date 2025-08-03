/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.shortcuts

import android.app.prediction.AppTarget
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager.ShareShortcutInfo
import android.service.chooser.ChooserTarget
import com.android.intentresolver.createAppTarget
import com.android.intentresolver.createShareShortcutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private const val PACKAGE = "org.package"

class ShortcutToChooserTargetConverterTest {
    private val testSubject = ShortcutToChooserTargetConverter()
    private val ranks = arrayOf(3 ,7, 1 ,3)
    private val shortcuts = ranks
        .foldIndexed(ArrayList<ShareShortcutInfo>(ranks.size)) { i, acc, rank ->
            val id = i + 1
            acc.add(
                createShareShortcutInfo(
                    id = "id-$i",
                    componentName = ComponentName(PACKAGE, "Class$id"),
                    rank,
                )
            )
            acc
        }

    @Test
    fun testConvertToChooserTarget_predictionService() {
        val appTargets = shortcuts.map { createAppTarget(it.shortcutInfo) }
        val expectedOrderAllShortcuts = intArrayOf(0, 1, 2, 3)
        val expectedScoreAllShortcuts = floatArrayOf(1.0f, 0.99f, 0.98f, 0.97f)
        val appTargetCache = HashMap<ChooserTarget, AppTarget>()
        val shortcutInfoCache = HashMap<ChooserTarget, ShortcutInfo>()

        var chooserTargets = testSubject.convertToChooserTarget(
            shortcuts,
            shortcuts,
            appTargets,
            appTargetCache,
            shortcutInfoCache,
        )

        assertCorrectShortcutToChooserTargetConversion(
            shortcuts,
            chooserTargets,
            expectedOrderAllShortcuts,
            expectedScoreAllShortcuts,
        )
        assertAppTargetCache(chooserTargets, appTargetCache)
        assertShortcutInfoCache(chooserTargets, shortcutInfoCache)

        val subset = shortcuts.subList(1, shortcuts.size)
        val expectedOrderSubset = intArrayOf(1, 2, 3)
        val expectedScoreSubset = floatArrayOf(0.99f, 0.98f, 0.97f)
        appTargetCache.clear()
        shortcutInfoCache.clear()

        chooserTargets = testSubject.convertToChooserTarget(
            subset,
            shortcuts,
            appTargets,
            appTargetCache,
            shortcutInfoCache,
        )

        assertCorrectShortcutToChooserTargetConversion(
            shortcuts,
            chooserTargets,
            expectedOrderSubset,
            expectedScoreSubset,
        )
        assertAppTargetCache(chooserTargets, appTargetCache)
        assertShortcutInfoCache(chooserTargets, shortcutInfoCache)
    }

    @Test
    fun testConvertToChooserTarget_shortcutManager() {
        val testSubject = ShortcutToChooserTargetConverter()
        val expectedOrderAllShortcuts = intArrayOf(2, 0, 3, 1)
        val expectedScoreAllShortcuts = floatArrayOf(1.0f, 0.99f, 0.99f, 0.98f)
        val shortcutInfoCache = HashMap<ChooserTarget, ShortcutInfo>()

        var chooserTargets = testSubject.convertToChooserTarget(
            shortcuts,
            shortcuts,
            null,
            null,
            shortcutInfoCache,
        )

        assertCorrectShortcutToChooserTargetConversion(
            shortcuts, chooserTargets,
            expectedOrderAllShortcuts, expectedScoreAllShortcuts
        )
        assertShortcutInfoCache(chooserTargets, shortcutInfoCache)

        val subset: MutableList<ShareShortcutInfo> = java.util.ArrayList()
        subset.add(shortcuts[1])
        subset.add(shortcuts[2])
        subset.add(shortcuts[3])
        val expectedOrderSubset = intArrayOf(2, 3, 1)
        val expectedScoreSubset = floatArrayOf(1.0f, 0.99f, 0.98f)
        shortcutInfoCache.clear()

        chooserTargets = testSubject.convertToChooserTarget(
            subset,
            shortcuts,
            null,
            null,
            shortcutInfoCache,
        )

        assertCorrectShortcutToChooserTargetConversion(
            shortcuts, chooserTargets,
            expectedOrderSubset, expectedScoreSubset
        )
        assertShortcutInfoCache(chooserTargets, shortcutInfoCache)
    }

    private fun assertCorrectShortcutToChooserTargetConversion(
        shortcuts: List<ShareShortcutInfo>,
        chooserTargets: List<ChooserTarget>,
        expectedOrder: IntArray,
        expectedScores: FloatArray,
    ) {
        assertEquals("Unexpected ChooserTarget count", expectedOrder.size, chooserTargets.size)
        for (i in chooserTargets.indices) {
            val ct = chooserTargets[i]
            val si = shortcuts[expectedOrder[i]].shortcutInfo
            val cn = shortcuts[expectedOrder[i]].targetComponent
            assertEquals(si.id, ct.intentExtras.getString(Intent.EXTRA_SHORTCUT_ID))
            assertEquals(si.label, ct.title)
            assertEquals(expectedScores[i], ct.score)
            assertEquals(cn, ct.componentName)
        }
    }

    private fun assertAppTargetCache(
        chooserTargets: List<ChooserTarget>, cache: Map<ChooserTarget, AppTarget>
    ) {
        for (ct in chooserTargets) {
            val target = cache[ct]
            assertNotNull("AppTarget is missing", target)
        }
    }

    private fun assertShortcutInfoCache(
        chooserTargets: List<ChooserTarget>, cache: Map<ChooserTarget, ShortcutInfo>
    ) {
        for (ct in chooserTargets) {
            val si = cache[ct]
            assertNotNull("AppTarget is missing", si)
        }
    }
}
