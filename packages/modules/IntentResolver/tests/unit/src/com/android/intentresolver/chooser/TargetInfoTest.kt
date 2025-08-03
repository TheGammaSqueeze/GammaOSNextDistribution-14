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

package com.android.intentresolver.chooser

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.UserHandle
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.ResolverDataProvider
import com.android.intentresolver.ResolverDataProvider.createResolveInfo
import com.android.intentresolver.createChooserTarget
import com.android.intentresolver.createShortcutInfo
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class TargetInfoTest {
    private val PERSONAL_USER_HANDLE: UserHandle =
        InstrumentationRegistry.getInstrumentation().getTargetContext().getUser()

    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    @Before
    fun setup() {
        // SelectableTargetInfo reads DeviceConfig and needs a permission for that.
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .adoptShellPermissionIdentity("android.permission.READ_DEVICE_CONFIG")
    }

    @Test
    fun testNewEmptyTargetInfo() {
        val info = NotSelectableTargetInfo.newEmptyTargetInfo()
        assertThat(info.isEmptyTargetInfo).isTrue()
        assertThat(info.isChooserTargetInfo).isTrue() // From legacy inheritance model.
        assertThat(info.hasDisplayIcon()).isFalse()
        assertThat(info.getDisplayIconHolder().getDisplayIcon()).isNull()
    }

    @UiThreadTest // AnimatedVectorDrawable needs to start from a thread with a Looper.
    @Test
    fun testNewPlaceholderTargetInfo() {
        val info = NotSelectableTargetInfo.newPlaceHolderTargetInfo(context)
        assertThat(info.isPlaceHolderTargetInfo).isTrue()
        assertThat(info.isChooserTargetInfo).isTrue() // From legacy inheritance model.
        assertThat(info.hasDisplayIcon()).isTrue()
        assertThat(info.displayIconHolder.displayIcon)
            .isInstanceOf(AnimatedVectorDrawable::class.java)
        // TODO: assert that the animation is pre-started/running (IIUC this requires synchronizing
        // with some "render thread" per the `AnimatedVectorDrawable` docs). I believe this is
        // possible using `AnimatorTestRule` but I couldn't find any sample usage in Kotlin nor get
        // it working myself.
    }

    @Test
    fun testNewSelectableTargetInfo() {
        val resolvedIntent = Intent()
        val baseDisplayInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                resolvedIntent,
                createResolveInfo(1, 0, PERSONAL_USER_HANDLE),
                "label",
                "extended info",
                resolvedIntent
            )
        val chooserTarget =
            createChooserTarget(
                "title",
                0.3f,
                ResolverDataProvider.createComponentName(2),
                "test_shortcut_id"
            )
        val shortcutInfo = createShortcutInfo("id", ResolverDataProvider.createComponentName(3), 3)
        val appTarget =
            AppTarget(
                AppTargetId("id"),
                chooserTarget.componentName.packageName,
                chooserTarget.componentName.className,
                UserHandle.CURRENT
            )

        val targetInfo =
            SelectableTargetInfo.newSelectableTargetInfo(
                baseDisplayInfo,
                mock(),
                resolvedIntent,
                chooserTarget,
                0.1f,
                shortcutInfo,
                appTarget,
                mock(),
            )
        assertThat(targetInfo.isSelectableTargetInfo).isTrue()
        assertThat(targetInfo.isChooserTargetInfo).isTrue() // From legacy inheritance model.
        assertThat(targetInfo.displayResolveInfo).isSameInstanceAs(baseDisplayInfo)
        assertThat(targetInfo.chooserTargetComponentName).isEqualTo(chooserTarget.componentName)
        assertThat(targetInfo.directShareShortcutId).isEqualTo(shortcutInfo.id)
        assertThat(targetInfo.directShareShortcutInfo).isSameInstanceAs(shortcutInfo)
        assertThat(targetInfo.directShareAppTarget).isSameInstanceAs(appTarget)
        assertThat(targetInfo.resolvedIntent).isSameInstanceAs(resolvedIntent)
        // TODO: make more meaningful assertions about the behavior of a selectable target.
    }

    @Test
    fun test_SelectableTargetInfo_componentName_no_source_info() {
        val chooserTarget =
            createChooserTarget(
                "title",
                0.3f,
                ResolverDataProvider.createComponentName(1),
                "test_shortcut_id"
            )
        val shortcutInfo = createShortcutInfo("id", ResolverDataProvider.createComponentName(2), 3)
        val appTarget =
            AppTarget(
                AppTargetId("id"),
                chooserTarget.componentName.packageName,
                chooserTarget.componentName.className,
                UserHandle.CURRENT
            )
        val pkgName = "org.package"
        val className = "MainActivity"
        val backupResolveInfo =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = pkgName
                        name = className
                    }
            }

        val targetInfo =
            SelectableTargetInfo.newSelectableTargetInfo(
                null,
                backupResolveInfo,
                mock(),
                chooserTarget,
                0.1f,
                shortcutInfo,
                appTarget,
                mock(),
            )
        assertThat(targetInfo.resolvedComponentName).isEqualTo(ComponentName(pkgName, className))
    }

    @Test
    fun testSelectableTargetInfo_noSourceIntentMatchingProposedRefinement() {
        val resolvedIntent = Intent("DONT_REFINE_ME")
        resolvedIntent.putExtra("resolvedIntent", true)

        val baseDisplayInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                resolvedIntent,
                createResolveInfo(1, 0),
                "label",
                "extended info",
                resolvedIntent
            )
        val chooserTarget =
            createChooserTarget(
                "title",
                0.3f,
                ResolverDataProvider.createComponentName(2),
                "test_shortcut_id"
            )
        val shortcutInfo = createShortcutInfo("id", ResolverDataProvider.createComponentName(3), 3)
        val appTarget =
            AppTarget(
                AppTargetId("id"),
                chooserTarget.componentName.packageName,
                chooserTarget.componentName.className,
                UserHandle.CURRENT
            )

        val targetInfo =
            SelectableTargetInfo.newSelectableTargetInfo(
                baseDisplayInfo,
                mock(),
                resolvedIntent,
                chooserTarget,
                0.1f,
                shortcutInfo,
                appTarget,
                mock(),
            )

        val refinement = Intent("PROPOSED_REFINEMENT")
        assertThat(targetInfo.tryToCloneWithAppliedRefinement(refinement)).isNull()
    }

    @Test
    fun testNewDisplayResolveInfo() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "testing intent sending")
        intent.setType("text/plain")

        val resolveInfo = createResolveInfo(3, 0, PERSONAL_USER_HANDLE)

        val targetInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                intent,
                resolveInfo,
                "label",
                "extended info",
                intent
            )
        assertThat(targetInfo.isDisplayResolveInfo).isTrue()
        assertThat(targetInfo.isMultiDisplayResolveInfo).isFalse()
        assertThat(targetInfo.isChooserTargetInfo).isFalse()
    }

    @Test
    fun test_DisplayResolveInfo_refinementToAlternateSourceIntent() {
        val originalIntent = Intent("DONT_REFINE_ME")
        originalIntent.putExtra("originalIntent", true)
        val mismatchedAlternate = Intent("DOESNT_MATCH")
        mismatchedAlternate.putExtra("mismatchedAlternate", true)
        val targetAlternate = Intent("REFINE_ME")
        targetAlternate.putExtra("targetAlternate", true)
        val extraMatch = Intent("REFINE_ME")
        extraMatch.putExtra("extraMatch", true)

        val originalInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                createResolveInfo(3, 0),
                "label",
                "extended info",
                originalIntent
            )
        originalInfo.addAlternateSourceIntent(mismatchedAlternate)
        originalInfo.addAlternateSourceIntent(targetAlternate)
        originalInfo.addAlternateSourceIntent(extraMatch)

        val refinement = Intent("REFINE_ME") // First match is `targetAlternate`
        refinement.putExtra("refinement", true)

        val refinedResult = checkNotNull(originalInfo.tryToCloneWithAppliedRefinement(refinement))
        // Note `DisplayResolveInfo` targets merge refinements directly into their `resolvedIntent`.
        assertThat(refinedResult.resolvedIntent?.getBooleanExtra("refinement", false)).isTrue()
        assertThat(refinedResult.resolvedIntent?.getBooleanExtra("targetAlternate", false)).isTrue()
        // None of the other source intents got merged in (not even the later one that matched):
        assertThat(refinedResult.resolvedIntent?.getBooleanExtra("originalIntent", false)).isFalse()
        assertThat(refinedResult.resolvedIntent?.getBooleanExtra("mismatchedAlternate", false))
            .isFalse()
        assertThat(refinedResult.resolvedIntent?.getBooleanExtra("extraMatch", false)).isFalse()
    }

    @Test
    fun testDisplayResolveInfo_noSourceIntentMatchingProposedRefinement() {
        val originalIntent = Intent("DONT_REFINE_ME")
        originalIntent.putExtra("originalIntent", true)
        val mismatchedAlternate = Intent("DOESNT_MATCH")
        mismatchedAlternate.putExtra("mismatchedAlternate", true)

        val originalInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                createResolveInfo(3, 0),
                "label",
                "extended info",
                originalIntent
            )
        originalInfo.addAlternateSourceIntent(mismatchedAlternate)

        val refinement = Intent("PROPOSED_REFINEMENT")
        assertThat(originalInfo.tryToCloneWithAppliedRefinement(refinement)).isNull()
    }

    @Test
    fun testNewMultiDisplayResolveInfo() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "testing intent sending")
        intent.setType("text/plain")

        val packageName = "org.pkg.app"
        val componentA = ComponentName(packageName, "org.pkg.app.ActivityA")
        val componentB = ComponentName(packageName, "org.pkg.app.ActivityB")
        val resolveInfoA = createResolveInfo(componentA, 0, PERSONAL_USER_HANDLE)
        val resolveInfoB = createResolveInfo(componentB, 0, PERSONAL_USER_HANDLE)
        val firstTargetInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                intent,
                resolveInfoA,
                "label 1",
                "extended info 1",
                intent
            )
        val secondTargetInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                intent,
                resolveInfoB,
                "label 2",
                "extended info 2",
                intent
            )

        val multiTargetInfo =
            MultiDisplayResolveInfo.newMultiDisplayResolveInfo(
                listOf(firstTargetInfo, secondTargetInfo)
            )

        assertThat(multiTargetInfo.isMultiDisplayResolveInfo).isTrue()
        assertThat(multiTargetInfo.isDisplayResolveInfo).isTrue() // From legacy inheritance.
        assertThat(multiTargetInfo.isChooserTargetInfo).isFalse()

        assertThat(multiTargetInfo.extendedInfo).isNull()

        assertThat(multiTargetInfo.allDisplayTargets)
            .containsExactly(firstTargetInfo, secondTargetInfo)

        assertThat(multiTargetInfo.hasSelected()).isFalse()
        assertThat(multiTargetInfo.selectedTarget).isNull()

        multiTargetInfo.setSelected(1)

        assertThat(multiTargetInfo.hasSelected()).isTrue()
        assertThat(multiTargetInfo.selectedTarget).isEqualTo(secondTargetInfo)
        assertThat(multiTargetInfo.resolvedComponentName).isEqualTo(componentB)

        val refined = multiTargetInfo.tryToCloneWithAppliedRefinement(intent)
        assertThat(refined).isInstanceOf(MultiDisplayResolveInfo::class.java)
        assertThat((refined as MultiDisplayResolveInfo).hasSelected())
            .isEqualTo(multiTargetInfo.hasSelected())

        // TODO: consider exercising activity-start behavior.
        // TODO: consider exercising DisplayResolveInfo base class behavior.
    }

    @Test
    fun testNewMultiDisplayResolveInfo_getAllSourceIntents_fromSelectedTarget() {
        val sendImage = Intent("SEND").apply { type = "image/png" }
        val sendUri = Intent("SEND").apply { type = "text/uri" }

        val resolveInfo = createResolveInfo(1, 0)

        val imageOnlyTarget =
            DisplayResolveInfo.newDisplayResolveInfo(
                sendImage,
                resolveInfo,
                "Send Image",
                "Sends only images",
                sendImage
            )

        val textOnlyTarget =
            DisplayResolveInfo.newDisplayResolveInfo(
                sendUri,
                resolveInfo,
                "Send Text",
                "Sends only text",
                sendUri
            )

        val imageOrTextTarget =
            DisplayResolveInfo.newDisplayResolveInfo(
                    sendImage,
                    resolveInfo,
                    "Send Image or Text",
                    "Sends images or text",
                    sendImage
                )
                .apply { addAlternateSourceIntent(sendUri) }

        val multiTargetInfo =
            MultiDisplayResolveInfo.newMultiDisplayResolveInfo(
                listOf(imageOnlyTarget, textOnlyTarget, imageOrTextTarget)
            )

        multiTargetInfo.setSelected(0)
        assertThat(multiTargetInfo.selectedTarget).isEqualTo(imageOnlyTarget)
        assertThat(multiTargetInfo.allSourceIntents).isEqualTo(imageOnlyTarget.allSourceIntents)

        multiTargetInfo.setSelected(1)
        assertThat(multiTargetInfo.selectedTarget).isEqualTo(textOnlyTarget)
        assertThat(multiTargetInfo.allSourceIntents).isEqualTo(textOnlyTarget.allSourceIntents)

        multiTargetInfo.setSelected(2)
        assertThat(multiTargetInfo.selectedTarget).isEqualTo(imageOrTextTarget)
        assertThat(multiTargetInfo.allSourceIntents).isEqualTo(imageOrTextTarget.allSourceIntents)
    }

    @Test
    fun testNewMultiDisplayResolveInfo_tryToCloneWithAppliedRefinement_delegatedToSelectedTarget() {
        val refined = Intent("SEND")
        val sendImage = Intent("SEND")
        val targetOne =
            spy(
                DisplayResolveInfo.newDisplayResolveInfo(
                    sendImage,
                    createResolveInfo(1, 0),
                    "Target One",
                    "Target One",
                    sendImage
                )
            )
        val targetTwo =
            mock<DisplayResolveInfo> {
                whenever(tryToCloneWithAppliedRefinement(any())).thenReturn(this)
            }

        val multiTargetInfo =
            MultiDisplayResolveInfo.newMultiDisplayResolveInfo(listOf(targetOne, targetTwo))

        multiTargetInfo.setSelected(1)
        assertThat(multiTargetInfo.selectedTarget).isEqualTo(targetTwo)

        multiTargetInfo.tryToCloneWithAppliedRefinement(refined)
        verify(targetTwo, times(1)).tryToCloneWithAppliedRefinement(refined)
        verify(targetOne, never()).tryToCloneWithAppliedRefinement(any())
    }
}
