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

package com.android.intentresolver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.service.chooser.ChooserTarget
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.TargetInfo
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PACKAGE_A = "package.a"
private const val PACKAGE_B = "package.b"
private const val CLASS_NAME = "./MainActivity"

@SmallTest
class ShortcutSelectionLogicTest {
    private val PERSONAL_USER_HANDLE: UserHandle = InstrumentationRegistry
            .getInstrumentation().getTargetContext().getUser()

    private val packageTargets = HashMap<String, Array<ChooserTarget>>().apply {
        arrayOf(PACKAGE_A, PACKAGE_B).forEach { pkg ->
            // shortcuts in reverse priority order
            val targets = Array(3) { i ->
                createChooserTarget(
                    "Shortcut $i",
                    (i + 1).toFloat() / 10f,
                    ComponentName(pkg, CLASS_NAME),
                    pkg.shortcutId(i),
                )
            }
            this[pkg] = targets
        }
    }

    private val baseDisplayInfo = DisplayResolveInfo.newDisplayResolveInfo(
            Intent(),
            ResolverDataProvider.createResolveInfo(3, 0, PERSONAL_USER_HANDLE),
            "label",
            "extended info",
            Intent()
    )

    private val otherBaseDisplayInfo = DisplayResolveInfo.newDisplayResolveInfo(
            Intent(),
            ResolverDataProvider.createResolveInfo(4, 0, PERSONAL_USER_HANDLE),
            "label 2",
            "extended info 2",
            Intent()
    )

    private operator fun Map<String, Array<ChooserTarget>>.get(pkg: String, idx: Int) =
        this[pkg]?.get(idx) ?: error("missing package $pkg")

    @Test
    fun testAddShortcuts_no_limits() {
        val serviceResults = ArrayList<TargetInfo>()
        val sc1 = packageTargets[PACKAGE_A, 0]
        val sc2 = packageTargets[PACKAGE_A, 1]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ false
        )

        val isUpdated = testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0.1f,
            /* targets = */ listOf(sc1, sc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )

        assertTrue("Updates are expected", isUpdated)
        assertShortcutsInOrder(
            listOf(sc2, sc1),
            serviceResults,
            "Two shortcuts are expected as we do not apply per-app shortcut limit"
        )
    }

    @Test
    fun testAddShortcuts_same_package_with_per_package_limit() {
        val serviceResults = ArrayList<TargetInfo>()
        val sc1 = packageTargets[PACKAGE_A, 0]
        val sc2 = packageTargets[PACKAGE_A, 1]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ true
        )

        val isUpdated = testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0.1f,
            /* targets = */ listOf(sc1, sc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )

        assertTrue("Updates are expected", isUpdated)
        assertShortcutsInOrder(
            listOf(sc2),
            serviceResults,
            "One shortcut is expected as we apply per-app shortcut limit"
        )
    }

    @Test
    fun testAddShortcuts_same_package_no_per_app_limit_with_target_limit() {
        val serviceResults = ArrayList<TargetInfo>()
        val sc1 = packageTargets[PACKAGE_A, 0]
        val sc2 = packageTargets[PACKAGE_A, 1]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ false
        )

        val isUpdated = testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0.1f,
            /* targets = */ listOf(sc1, sc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 1,
            /* serviceTargets = */ serviceResults
        )

        assertTrue("Updates are expected", isUpdated)
        assertShortcutsInOrder(
            listOf(sc2),
            serviceResults,
            "One shortcut is expected as we apply overall shortcut limit"
        )
    }

    @Test
    fun testAddShortcuts_different_packages_with_per_package_limit() {
        val serviceResults = ArrayList<TargetInfo>()
        val pkgAsc1 = packageTargets[PACKAGE_A, 0]
        val pkgAsc2 = packageTargets[PACKAGE_A, 1]
        val pkgBsc1 = packageTargets[PACKAGE_B, 0]
        val pkgBsc2 = packageTargets[PACKAGE_B, 1]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ true
        )

        testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0.1f,
            /* targets = */ listOf(pkgAsc1, pkgAsc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )
        testSubject.addServiceResults(
            /* origTarget = */ otherBaseDisplayInfo,
            /* origTargetScore = */ 0.2f,
            /* targets = */ listOf(pkgBsc1, pkgBsc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )

        assertShortcutsInOrder(
            listOf(pkgBsc2, pkgAsc2),
            serviceResults,
            "Two shortcuts are expected as we apply per-app shortcut limit"
        )
    }

    @Test
    fun testAddShortcuts_pinned_shortcut() {
        val serviceResults = ArrayList<TargetInfo>()
        val sc1 = packageTargets[PACKAGE_A, 0]
        val sc2 = packageTargets[PACKAGE_A, 1]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ false
        )

        val isUpdated = testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0.1f,
            /* targets = */ listOf(sc1, sc2),
            /* isShortcutResult = */ true,
            /* directShareToShortcutInfos = */ mapOf(
                sc1 to createShortcutInfo(
                    PACKAGE_A.shortcutId(1),
                    sc1.componentName, 1).apply {
                        addFlags(ShortcutInfo.FLAG_PINNED)
                    }
            ),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ mock(),
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )

        assertTrue("Updates are expected", isUpdated)
        assertShortcutsInOrder(
            listOf(sc1, sc2),
            serviceResults,
            "Two shortcuts are expected as we do not apply per-app shortcut limit"
        )
    }

    @Test
    fun test_available_caller_shortcuts_count_is_limited() {
        val serviceResults = ArrayList<TargetInfo>()
        val sc1 = packageTargets[PACKAGE_A, 0]
        val sc2 = packageTargets[PACKAGE_A, 1]
        val sc3 = packageTargets[PACKAGE_A, 2]
        val testSubject = ShortcutSelectionLogic(
            /* maxShortcutTargetsPerApp = */ 1,
            /* applySharingAppLimits = */ true
        )
        val context = mock<Context> {
            whenever(packageManager).thenReturn(mock())
        }

        testSubject.addServiceResults(
            /* origTarget = */ baseDisplayInfo,
            /* origTargetScore = */ 0f,
            /* targets = */ listOf(sc1, sc2, sc3),
            /* isShortcutResult = */ false,
            /* directShareToShortcutInfos = */ emptyMap(),
            /* directShareToAppTargets = */ emptyMap(),
            /* userContext = */ context,
            /* targetIntent = */ mock(),
            /* refererFillInIntent = */ mock(),
            /* maxRankedTargets = */ 4,
            /* serviceTargets = */ serviceResults
        )

        assertShortcutsInOrder(
            listOf(sc3, sc2),
            serviceResults,
            "At most two caller-provided shortcuts are allowed"
        )
    }

    // TODO: consider renaming. Not all `ChooserTarget`s are "shortcuts" and many of our test cases
    // add results with `isShortcutResult = false` and `directShareToShortcutInfos = emptyMap()`.
    private fun assertShortcutsInOrder(
        expected: List<ChooserTarget>, actual: List<TargetInfo>, msg: String? = ""
    ) {
        assertEquals(msg, expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(
                "Unexpected item at position $i",
                expected[i].componentName,
                actual[i].chooserTargetComponentName
            )
            assertEquals(
                "Unexpected item at position $i",
                expected[i].title,
                actual[i].displayLabel
            )
        }
    }

    private fun String.shortcutId(id: Int) = "$this.$id"
}
