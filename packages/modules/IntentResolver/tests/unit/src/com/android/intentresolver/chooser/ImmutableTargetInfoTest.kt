/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *3
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.chooser

import android.app.Activity
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import com.android.intentresolver.createShortcutInfo
import com.android.intentresolver.mock
import com.android.intentresolver.ResolverActivity
import com.android.intentresolver.ResolverDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class ImmutableTargetInfoTest {
    private val PERSONAL_USER_HANDLE: UserHandle = InstrumentationRegistry
            .getInstrumentation().getTargetContext().getUser()

    private val resolvedIntent = Intent("resolved")
    private val targetIntent = Intent("target")
    private val referrerFillInIntent = Intent("referrer_fillin")
    private val resolvedComponentName = ComponentName("resolved", "component")
    private val chooserTargetComponentName = ComponentName("chooser", "target")
    private val resolveInfo = ResolverDataProvider.createResolveInfo(1, 0, PERSONAL_USER_HANDLE)
    private val displayLabel: CharSequence = "Display Label"
    private val extendedInfo: CharSequence = "Extended Info"
    private val displayIconHolder: TargetInfo.IconHolder = mock()
    private val sourceIntent1 = Intent("source1")
    private val sourceIntent2 = Intent("source2")
    private val displayTarget1 = DisplayResolveInfo.newDisplayResolveInfo(
        Intent("display1"),
        ResolverDataProvider.createResolveInfo(2, 0, PERSONAL_USER_HANDLE),
        "display1 label",
        "display1 extended info",
        Intent("display1_resolved")
    )
    private val displayTarget2 = DisplayResolveInfo.newDisplayResolveInfo(
        Intent("display2"),
        ResolverDataProvider.createResolveInfo(3, 0, PERSONAL_USER_HANDLE),
        "display2 label",
        "display2 extended info",
        Intent("display2_resolved")
    )
    private val directShareShortcutInfo = createShortcutInfo(
        "shortcutid", ResolverDataProvider.createComponentName(4), 4)
    private val directShareAppTarget = AppTarget(
        AppTargetId("apptargetid"),
        "test.directshare",
        "target",
        UserHandle.CURRENT)
    private val displayResolveInfo = DisplayResolveInfo.newDisplayResolveInfo(
        Intent("displayresolve"),
        ResolverDataProvider.createResolveInfo(5, 0, PERSONAL_USER_HANDLE),
        "displayresolve label",
        "displayresolve extended info",
        Intent("display_resolved")
    )
    private val hashProvider: ImmutableTargetInfo.TargetHashProvider = mock()

    @Test
    fun testBasicProperties() {  // Fields that are reflected back w/o logic.
        // TODO: we could consider passing copies of all the values into the builder so that we can
        // verify that they're not mutated (e.g. no extras added to the intents). For now that
        // should be obvious from the implementation.
        val info = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(resolvedIntent)
            .setTargetIntent(targetIntent)
            .setReferrerFillInIntent(referrerFillInIntent)
            .setResolvedComponentName(resolvedComponentName)
            .setChooserTargetComponentName(chooserTargetComponentName)
            .setResolveInfo(resolveInfo)
            .setDisplayLabel(displayLabel)
            .setExtendedInfo(extendedInfo)
            .setDisplayIconHolder(displayIconHolder)
            .setAlternateSourceIntents(listOf(sourceIntent1, sourceIntent2))
            .setAllDisplayTargets(listOf(displayTarget1, displayTarget2))
            .setIsSuspended(true)
            .setIsPinned(true)
            .setModifiedScore(42.0f)
            .setDirectShareShortcutInfo(directShareShortcutInfo)
            .setDirectShareAppTarget(directShareAppTarget)
            .setDisplayResolveInfo(displayResolveInfo)
            .setHashProvider(hashProvider)
            .build()

        assertThat(info.resolvedIntent).isEqualTo(resolvedIntent)
        assertThat(info.targetIntent).isEqualTo(targetIntent)
        assertThat(info.referrerFillInIntent).isEqualTo(referrerFillInIntent)
        assertThat(info.resolvedComponentName).isEqualTo(resolvedComponentName)
        assertThat(info.chooserTargetComponentName).isEqualTo(chooserTargetComponentName)
        assertThat(info.resolveInfo).isEqualTo(resolveInfo)
        assertThat(info.displayLabel).isEqualTo(displayLabel)
        assertThat(info.extendedInfo).isEqualTo(extendedInfo)
        assertThat(info.displayIconHolder).isEqualTo(displayIconHolder)
        assertThat(info.allSourceIntents).containsExactly(
            resolvedIntent, sourceIntent1, sourceIntent2)
        assertThat(info.allDisplayTargets).containsExactly(displayTarget1, displayTarget2)
        assertThat(info.isSuspended).isTrue()
        assertThat(info.isPinned).isTrue()
        assertThat(info.modifiedScore).isEqualTo(42.0f)
        assertThat(info.directShareShortcutInfo).isEqualTo(directShareShortcutInfo)
        assertThat(info.directShareAppTarget).isEqualTo(directShareAppTarget)
        assertThat(info.displayResolveInfo).isEqualTo(displayResolveInfo)
        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isFalse()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isFalse()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isFalse()
        assertThat(info.hashProvider).isEqualTo(hashProvider)
    }

    @Test
    fun testToBuilderPreservesBasicProperties() {
        // Note this is set up exactly as in `testBasicProperties`, but the assertions will be made
        // against a *copy* of the object instead.
        val infoToCopyFrom = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(resolvedIntent)
            .setTargetIntent(targetIntent)
            .setReferrerFillInIntent(referrerFillInIntent)
            .setResolvedComponentName(resolvedComponentName)
            .setChooserTargetComponentName(chooserTargetComponentName)
            .setResolveInfo(resolveInfo)
            .setDisplayLabel(displayLabel)
            .setExtendedInfo(extendedInfo)
            .setDisplayIconHolder(displayIconHolder)
            .setAlternateSourceIntents(listOf(sourceIntent1, sourceIntent2))
            .setAllDisplayTargets(listOf(displayTarget1, displayTarget2))
            .setIsSuspended(true)
            .setIsPinned(true)
            .setModifiedScore(42.0f)
            .setDirectShareShortcutInfo(directShareShortcutInfo)
            .setDirectShareAppTarget(directShareAppTarget)
            .setDisplayResolveInfo(displayResolveInfo)
            .setHashProvider(hashProvider)
            .build()

        val info = infoToCopyFrom.toBuilder().build()

        assertThat(info.resolvedIntent).isEqualTo(resolvedIntent)
        assertThat(info.targetIntent).isEqualTo(targetIntent)
        assertThat(info.referrerFillInIntent).isEqualTo(referrerFillInIntent)
        assertThat(info.resolvedComponentName).isEqualTo(resolvedComponentName)
        assertThat(info.chooserTargetComponentName).isEqualTo(chooserTargetComponentName)
        assertThat(info.resolveInfo).isEqualTo(resolveInfo)
        assertThat(info.displayLabel).isEqualTo(displayLabel)
        assertThat(info.extendedInfo).isEqualTo(extendedInfo)
        assertThat(info.displayIconHolder).isEqualTo(displayIconHolder)
        assertThat(info.allSourceIntents).containsExactly(
            resolvedIntent, sourceIntent1, sourceIntent2)
        assertThat(info.allDisplayTargets).containsExactly(displayTarget1, displayTarget2)
        assertThat(info.isSuspended).isTrue()
        assertThat(info.isPinned).isTrue()
        assertThat(info.modifiedScore).isEqualTo(42.0f)
        assertThat(info.directShareShortcutInfo).isEqualTo(directShareShortcutInfo)
        assertThat(info.directShareAppTarget).isEqualTo(directShareAppTarget)
        assertThat(info.displayResolveInfo).isEqualTo(displayResolveInfo)
        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isFalse()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isFalse()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isFalse()
        assertThat(info.hashProvider).isEqualTo(hashProvider)
    }

    @Test
    fun testBaseIntentToSend_defaultsToResolvedIntent() {
        val info = ImmutableTargetInfo.newBuilder().setResolvedIntent(resolvedIntent).build()
        assertThat(info.baseIntentToSend.filterEquals(resolvedIntent)).isTrue()
    }

    @Test
    fun testBaseIntentToSend_fillsInFromReferrerIntent() {
        val originalIntent = Intent()
        originalIntent.setPackage("original")

        val referrerFillInIntent = Intent("REFERRER_FILL_IN")
        referrerFillInIntent.setPackage("referrer")

        val info = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .setReferrerFillInIntent(referrerFillInIntent)
            .build()

        assertThat(info.baseIntentToSend.getPackage()).isEqualTo("original")  // Only fill if empty.
        assertThat(info.baseIntentToSend.action).isEqualTo("REFERRER_FILL_IN")
    }

    @Test
    fun testBaseIntentToSend_fillsInFromRefinementIntent() {
        val originalIntent = Intent()
        originalIntent.putExtra("ORIGINAL", true)

        val refinementIntent = Intent()
        refinementIntent.putExtra("REFINEMENT", true)

        val originalInfo = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .build()
        val info = checkNotNull(originalInfo.tryToCloneWithAppliedRefinement(refinementIntent))

        assertThat(info?.baseIntentToSend?.getBooleanExtra("ORIGINAL", false)).isTrue()
        assertThat(info?.baseIntentToSend?.getBooleanExtra("REFINEMENT", false)).isTrue()
    }

    @Test
    fun testBaseIntentToSend_twoFillInSourcesFavorsRefinementRequest() {
        val originalIntent = Intent("REFINE_ME")
        originalIntent.setPackage("original")

        val referrerFillInIntent = Intent("REFERRER_FILL_IN")
        referrerFillInIntent.setPackage("referrer_pkg")
        referrerFillInIntent.setType("test/referrer")

        val infoWithReferrerFillIn = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .setReferrerFillInIntent(referrerFillInIntent)
            .build()

        val refinementIntent = Intent("REFINE_ME")
        refinementIntent.setPackage("original")  // Has to match for refinement.

        val info =
            checkNotNull(infoWithReferrerFillIn.tryToCloneWithAppliedRefinement(refinementIntent))

        assertThat(info?.baseIntentToSend?.getPackage()).isEqualTo("original")  // Set all along.
        assertThat(info?.baseIntentToSend?.action).isEqualTo("REFINE_ME")  // Refinement wins.
        assertThat(info?.baseIntentToSend?.type).isEqualTo("test/referrer")  // Left for referrer.
    }

    @Test
    fun testBaseIntentToSend_doubleRefinementPreservesReferrerFillInButNotOriginalRefinement() {
        val originalIntent = Intent("REFINE_ME")
        val referrerFillInIntent = Intent("REFERRER_FILL_IN")
        referrerFillInIntent.putExtra("TEST", "REFERRER")
        val refinementIntent1 = Intent("REFINE_ME")
        refinementIntent1.putExtra("TEST1", "1")
        val refinementIntent2 = Intent("REFINE_ME")
        refinementIntent2.putExtra("TEST2", "2")

        val originalInfo = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .setReferrerFillInIntent(referrerFillInIntent)
            .build()

        val refined1 = checkNotNull(originalInfo.tryToCloneWithAppliedRefinement(refinementIntent1))
        // Cloned clone.
        val refined2 = checkNotNull(refined1.tryToCloneWithAppliedRefinement(refinementIntent2))

        // Both clones get the same values filled in from the referrer intent.
        assertThat(refined1?.baseIntentToSend?.getStringExtra("TEST")).isEqualTo("REFERRER")
        assertThat(refined2?.baseIntentToSend?.getStringExtra("TEST")).isEqualTo("REFERRER")
        // Each clone has the respective value that was set in their own refinement request.
        assertThat(refined1?.baseIntentToSend?.getStringExtra("TEST1")).isEqualTo("1")
        assertThat(refined2?.baseIntentToSend?.getStringExtra("TEST2")).isEqualTo("2")
        // The clones don't have the data from each other's refinements, even though the intent
        // field is empty (thus able to be populated by filling-in).
        assertThat(refined1?.baseIntentToSend?.getStringExtra("TEST2")).isNull()
        assertThat(refined2?.baseIntentToSend?.getStringExtra("TEST1")).isNull()
    }

    @Test
    fun testBaseIntentToSend_refinementToAlternateSourceIntent() {
        val originalIntent = Intent("DONT_REFINE_ME")
        originalIntent.putExtra("originalIntent", true)
        val mismatchedAlternate = Intent("DOESNT_MATCH")
        mismatchedAlternate.putExtra("mismatchedAlternate", true)
        val targetAlternate = Intent("REFINE_ME")
        targetAlternate.putExtra("targetAlternate", true)
        val extraMatch = Intent("REFINE_ME")
        extraMatch.putExtra("extraMatch", true)

        val originalInfo = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .setAllSourceIntents(listOf(
                    originalIntent, mismatchedAlternate, targetAlternate, extraMatch))
            .build()

        val refinement = Intent("REFINE_ME")  // First match is `targetAlternate`
        refinement.putExtra("refinement", true)

        val refinedResult = checkNotNull(originalInfo.tryToCloneWithAppliedRefinement(refinement))
        assertThat(refinedResult?.baseIntentToSend?.getBooleanExtra("refinement", false)).isTrue()
        assertThat(refinedResult?.baseIntentToSend?.getBooleanExtra("targetAlternate", false))
            .isTrue()
        // None of the other source intents got merged in (not even the later one that matched):
        assertThat(refinedResult?.baseIntentToSend?.getBooleanExtra("originalIntent", false))
            .isFalse()
        assertThat(refinedResult?.baseIntentToSend?.getBooleanExtra("mismatchedAlternate", false))
            .isFalse()
        assertThat(refinedResult?.baseIntentToSend?.getBooleanExtra("extraMatch", false)).isFalse()
    }

    @Test
    fun testBaseIntentToSend_noSourceIntentMatchingProposedRefinement() {
        val originalIntent = Intent("DONT_REFINE_ME")
        originalIntent.putExtra("originalIntent", true)
        val mismatchedAlternate = Intent("DOESNT_MATCH")
        mismatchedAlternate.putExtra("mismatchedAlternate", true)

        val originalInfo = ImmutableTargetInfo.newBuilder()
            .setResolvedIntent(originalIntent)
            .setAllSourceIntents(listOf(originalIntent, mismatchedAlternate))
            .build()

        val refinement = Intent("PROPOSED_REFINEMENT")
        assertThat(originalInfo.tryToCloneWithAppliedRefinement(refinement)).isNull()
    }

    @Test
    fun testLegacySubclassRelationships_empty() {
        val info = ImmutableTargetInfo.newBuilder()
            .setLegacyType(ImmutableTargetInfo.LegacyTargetType.EMPTY_TARGET_INFO)
            .build()

        assertThat(info.isEmptyTargetInfo).isTrue()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isTrue()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isTrue()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isFalse()
    }

    @Test
    fun testLegacySubclassRelationships_placeholder() {
        val info = ImmutableTargetInfo.newBuilder()
            .setLegacyType(ImmutableTargetInfo.LegacyTargetType.PLACEHOLDER_TARGET_INFO)
            .build()

        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isTrue()
        assertThat(info.isNotSelectableTargetInfo).isTrue()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isTrue()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isFalse()
    }

    @Test
    fun testLegacySubclassRelationships_selectable() {
        val info = ImmutableTargetInfo.newBuilder()
            .setLegacyType(ImmutableTargetInfo.LegacyTargetType.SELECTABLE_TARGET_INFO)
            .build()

        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isFalse()
        assertThat(info.isSelectableTargetInfo).isTrue()
        assertThat(info.isChooserTargetInfo).isTrue()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isFalse()
    }

    @Test
    fun testLegacySubclassRelationships_displayResolveInfo() {
        val info = ImmutableTargetInfo.newBuilder()
            .setLegacyType(ImmutableTargetInfo.LegacyTargetType.DISPLAY_RESOLVE_INFO)
            .build()

        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isFalse()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isFalse()
        assertThat(info.isMultiDisplayResolveInfo).isFalse()
        assertThat(info.isDisplayResolveInfo).isTrue()
    }

    @Test
    fun testLegacySubclassRelationships_multiDisplayResolveInfo() {
        val info = ImmutableTargetInfo.newBuilder()
            .setLegacyType(ImmutableTargetInfo.LegacyTargetType.MULTI_DISPLAY_RESOLVE_INFO)
            .build()

        assertThat(info.isEmptyTargetInfo).isFalse()
        assertThat(info.isPlaceHolderTargetInfo).isFalse()
        assertThat(info.isNotSelectableTargetInfo).isFalse()
        assertThat(info.isSelectableTargetInfo).isFalse()
        assertThat(info.isChooserTargetInfo).isFalse()
        assertThat(info.isMultiDisplayResolveInfo).isTrue()
        assertThat(info.isDisplayResolveInfo).isTrue()
    }

    @Test
    fun testActivityStarter_correctNumberOfInvocations_startAsCaller() {
        val activityStarter = object : TestActivityStarter() {
            override fun startAsUser(
                target: TargetInfo, activity: Activity, options: Bundle, user: UserHandle
            ): Boolean {
                throw RuntimeException("Wrong API used: startAsUser")
            }
        }

        val info = ImmutableTargetInfo.newBuilder().setActivityStarter(activityStarter).build()
        val activity: ResolverActivity = mock()
        val options = Bundle()
        options.putInt("TEST_KEY", 1)

        info.startAsCaller(activity, options, 42)

        assertThat(activityStarter.totalInvocations).isEqualTo(1)
        assertThat(activityStarter.lastInvocationTargetInfo).isEqualTo(info)
        assertThat(activityStarter.lastInvocationActivity).isEqualTo(activity)
        assertThat(activityStarter.lastInvocationOptions).isEqualTo(options)
        assertThat(activityStarter.lastInvocationUserId).isEqualTo(42)
        assertThat(activityStarter.lastInvocationAsCaller).isTrue()
    }

    @Test
    fun testActivityStarter_correctNumberOfInvocations_startAsUser() {
        val activityStarter = object : TestActivityStarter() {
            override fun startAsCaller(
                target: TargetInfo, activity: Activity, options: Bundle, userId: Int): Boolean {
                throw RuntimeException("Wrong API used: startAsCaller")
            }
        }

        val info = ImmutableTargetInfo.newBuilder().setActivityStarter(activityStarter).build()
        val activity: Activity = mock()
        val options = Bundle()
        options.putInt("TEST_KEY", 1)

        info.startAsUser(activity, options, UserHandle.of(42))

        assertThat(activityStarter.totalInvocations).isEqualTo(1)
        assertThat(activityStarter.lastInvocationTargetInfo).isEqualTo(info)
        assertThat(activityStarter.lastInvocationActivity).isEqualTo(activity)
        assertThat(activityStarter.lastInvocationOptions).isEqualTo(options)
        assertThat(activityStarter.lastInvocationUserId).isEqualTo(42)
        assertThat(activityStarter.lastInvocationAsCaller).isFalse()
    }

    @Test
    fun testActivityStarter_invokedWithRespectiveTargetInfoAfterCopy() {
        val activityStarter = TestActivityStarter()
        val info1 = ImmutableTargetInfo.newBuilder().setActivityStarter(activityStarter).build()
        val info2 = info1.toBuilder().build()

        info1.startAsCaller(mock(), Bundle(), 42)
        assertThat(activityStarter.lastInvocationTargetInfo).isEqualTo(info1)
        info2.startAsCaller(mock(), Bundle(), 42)
        assertThat(activityStarter.lastInvocationTargetInfo).isEqualTo(info2)
        info2.startAsUser(mock(), Bundle(), UserHandle.of(42))
        assertThat(activityStarter.lastInvocationTargetInfo).isEqualTo(info2)

        assertThat(activityStarter.totalInvocations).isEqualTo(3)  // Instance is still shared.
    }
}

private open class TestActivityStarter : ImmutableTargetInfo.TargetActivityStarter {
    var totalInvocations = 0
    var lastInvocationTargetInfo: TargetInfo? = null
    var lastInvocationActivity: Activity? = null
    var lastInvocationOptions: Bundle? = null
    var lastInvocationUserId: Integer? = null
    var lastInvocationAsCaller = false

    override fun startAsCaller(
            target: TargetInfo, activity: Activity, options: Bundle, userId: Int): Boolean {
        ++totalInvocations
        lastInvocationTargetInfo = target
        lastInvocationActivity = activity
        lastInvocationOptions = options
        lastInvocationUserId = Integer(userId)
        lastInvocationAsCaller = true
        return true
    }

    override fun startAsUser(
            target: TargetInfo, activity: Activity, options: Bundle, user: UserHandle): Boolean {
        ++totalInvocations
        lastInvocationTargetInfo = target
        lastInvocationActivity = activity
        lastInvocationOptions = options
        lastInvocationUserId = Integer(user.identifier)
        lastInvocationAsCaller = false
        return true
    }
}
