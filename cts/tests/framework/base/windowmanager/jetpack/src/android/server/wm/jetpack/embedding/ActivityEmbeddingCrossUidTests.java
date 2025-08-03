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

package android.server.wm.jetpack.embedding;

import static android.Manifest.permission.EMBED_ANY_APP_IN_UNTRUSTED_MODE;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.jetpack.second.Components.ACTION_ENTER_PIP;
import static android.server.wm.jetpack.second.Components.ACTION_EXIT_PIP;
import static android.server.wm.jetpack.second.Components.EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY;
import static android.server.wm.jetpack.second.Components.SECOND_ACTIVITY;
import static android.server.wm.jetpack.second.Components.SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY_STATE_SHARE;
import static android.server.wm.jetpack.signed.Components.SIGNED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit_expectFail;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitForVisible;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.NestedShellPermission;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * launch scenarios across UIDs.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingCrossUidTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingCrossUidTests extends ActivityEmbeddingTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Override
    @After
    public void tearDown() throws Throwable {
        super.tearDown();
        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.second"));
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.signed"));
    }

    /**
     * Tests that embedding an activity across UIDs is not allowed.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules"})
    @Test
    public void testCrossUidActivityEmbeddingIsNotAllowed() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID and verify that it is not split with the primary
        // activity.
        startActivityCrossUidInSplit_expectFail(primaryActivity, SECOND_ACTIVITY,
                mSplitInfoConsumer);
    }

    /**
     * Tests that embedding an activity across UIDs is not allowed if an activity requires a
     * permission that the host doesn't have.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "android.R.attr#knownActivityEmbeddingCerts"})
    @Test
    public void testCrossUidActivityEmbeddingIsNotAllowedWithoutPermission() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID and verify that it is not split with the primary
        // activity.
        startActivityCrossUidInSplit_expectFail(primaryActivity,
                SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS, mSplitInfoConsumer);
    }

    /**
     * Tests that embedding an activity across UIDs is allowed if an activity requires a
     * certificate that the host has.
     */
    @Test
    public void testCrossUidActivityEmbeddingIsAllowedWithPermission() {
        // Start an activity that will attempt to embed TestActivityKnownEmbeddingCerts
        final Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_EMBED_ACTIVITY, true);
        startActivityNoWait(mContext, SIGNED_EMBEDDING_ACTIVITY, extras);

        waitAndAssertResumed(EMBEDDED_ACTIVITY_ID);
        final TestActivityWithId embeddedActivity = getResumedActivityById(EMBEDDED_ACTIVITY_ID);
        assertNotNull(embeddedActivity);
        assertTrue(mActivityEmbeddingComponent.isActivityEmbedded(embeddedActivity));
    }

    /**
     * Tests that embedding an activity across UIDs is allowed if the app has opted in to allow
     * untrusted embedding.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "android.R.attr#allowUntrustedActivityEmbedding"})
    @Test
    public void testUntrustedCrossUidActivityEmbeddingIsAllowedWithOptIn() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an embeddable activity from a different UID and verify that it is split with the
        // primary activity.
        startActivityCrossUidInSplit(primaryActivity, SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                splitPairRule, mSplitInfoConsumer, "id", true /* verify */);
    }

    /**
     * Tests that launching a non-embeddable activity in the embedded container will not be allowed,
     * and the activity will be launched in full task bounds.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules"})
    @Test
    public void testUntrustedCrossUidActivityEmbedding_notAllowedForNonEmbeddable() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // First launch an embeddable activity to setup a split
        startActivityCrossUidInSplit(primaryActivity, SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                splitPairRule, mSplitInfoConsumer, "id", true /* verify */);

        // Launch an embeddable activity from a different UID and request to launch another one that
        // is not embeddable.
        final Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY, true);
        startActivityFromActivity(primaryActivity, SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, "id",
                extras);

        // Verify that the original split was covered by the non-embeddable activity that was
        // launched outside the embedded container and expanded to full task size.
        assertTrue(waitForVisible(primaryActivity, false));
    }

    /**
     * Tests that embedding an activity across UIDs is allowed if the host app has the role
     * permission {@link EMBED_ANY_APP_IN_UNTRUSTED_MODE}.
     */
    @ApiTest(apis = {"android.Manifest.permission#EMBED_ANY_APP_IN_UNTRUSTED_MODE"})
    @Test
    @RequiresFlagsEnabled("com.android.window.flags.untrusted_embedding_any_app_permission")
    public void testCrossUidActivityEmbeddingIsAllowedWithEmbedAnyAppPermission() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        try {
            InstrumentationRegistry
                    .getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(EMBED_ANY_APP_IN_UNTRUSTED_MODE);
            // With the EMBED_ANY_APP_IN_UNTRUSTED_MODE permission, cross UID embedding is allowed
            // even without the second app opt-in.
            startActivityCrossUidInSplit(primaryActivity, SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS,
                    splitPairRule, mSplitInfoConsumer, "id", true /* verify */);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Tests that embedding an activity across UIDs is not allowed when the flag is disabled even
     * if the host app has the role permission {@link EMBED_ANY_APP_IN_UNTRUSTED_MODE}.
     */
    @ApiTest(apis = {"android.Manifest.permission#EMBED_ANY_APP_IN_UNTRUSTED_MODE"})
    @Test
    @RequiresFlagsDisabled("com.android.window.flags.untrusted_embedding_any_app_permission")
    public void testCrossUidActivityEmbeddingNotAllowedWithEmbedAnyAppPermission_flagDisabled() {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        try {
            InstrumentationRegistry
                    .getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(EMBED_ANY_APP_IN_UNTRUSTED_MODE);
            // This should fail because the feature flag is disabled.
            startActivityCrossUidInSplit_expectFail(primaryActivity,
                    SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS, mSplitInfoConsumer);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Verify restoration of the split state when the activity enters and leaves PIP. The
     * {@code WindowManager.PROPERTY_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING_STATE_SHARING} is required
     * for untrusted cross-app embedding for pip restoration.
     */
    @ApiTest(apis = {
            "android.view.WindowManager#PROPERTY_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING_STATE_SHARING"})
    @Test
    @RequiresFlagsEnabled("com.android.window.flags.untrusted_embedding_state_sharing")
    public void testUntrustedCrossUidActivityEmbeddingRestoreFromPip_succeedWhenAppOptIn()
            throws InterruptedException {
        testUntrustedCrossUidActivityEmbeddingRestoreFromPip(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY_STATE_SHARE,
                true /* shouldRestoreSplit */
        );
    }

    /**
     * Verify restoration of the split state does not happen when the activity enters and leaves
     * PIP when the flag is disabled.
     */
    @ApiTest(apis = {
            "android.view.WindowManager#PROPERTY_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING_STATE_SHARING"})
    @Test
    @RequiresFlagsDisabled("com.android.window.flags.untrusted_embedding_state_sharing")
    public void testUntrustedCrossUidActivityEmbeddingRestoreFromPip_failWhenFlagDisabled()
            throws InterruptedException {
        testUntrustedCrossUidActivityEmbeddingRestoreFromPip(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY_STATE_SHARE,
                false /* shouldRestoreSplit */
        );
    }

    /**
     * Verify restoration of the split state does not happen when the activity enters and leaves
     * PIP if {@code WindowManager.PROPERTY_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING_STATE_SHARING} is
     * not set.
     */
    @ApiTest(apis = {
            "android.view.WindowManager#PROPERTY_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING_STATE_SHARING"})
    @Test
    public void testUntrustedCrossUidActivityEmbeddingRestoreFromPip_failWhenAppNotOptIn()
            throws InterruptedException {
        testUntrustedCrossUidActivityEmbeddingRestoreFromPip(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                false /* shouldRestoreSplit */
        );
    }

    private void testUntrustedCrossUidActivityEmbeddingRestoreFromPip(
            @NonNull ComponentName embeddedComponentName,
            boolean shouldRestoreSplit) throws InterruptedException {
        assumeTrue(supportsPip());

        final Activity primaryActivity =
                startFullScreenActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);
        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a cross-app embeddable activity to set up a split
        startActivityCrossUidInSplit(
                primaryActivity, embeddedComponentName,
                splitPairRule, mSplitInfoConsumer, "secondActivityId", true /* verify */);

        mWmState.waitForActivityState(embeddedComponentName, STATE_RESUMED);

        mSplitInfoConsumer.clearQueue();
        // Request the second activity to enter pip
        mBroadcastActionTrigger.doAction(ACTION_ENTER_PIP);

        waitForEnterPipAnimationComplete(embeddedComponentName);

        // Request the second activity to exit pip
        mBroadcastActionTrigger.doAction(ACTION_EXIT_PIP);

        mWmState.waitForActivityState(embeddedComponentName, STATE_RESUMED);

        // The first callback is when the embedded activity enters pip, so the SplitInfo is empty.
        List<SplitInfo> info = mSplitInfoConsumer.waitAndGet();
        assertTrue(
                "The first SplitInfo must be empty when the embedded activity enters pip",
                info.isEmpty());

        if (shouldRestoreSplit) {
            // The second callback should happen if split is restored after exiting pip, and the
            // SplitInfo should be non-empty.
            info = mSplitInfoConsumer.waitAndGet();
            assertFalse(
                    "The second SplitInfo must be non-empty when the embedded activity exits pip",
                    info.isEmpty());
        } else {
            info = mSplitInfoConsumer.waitAndGet();
            assertNull(info);
        }
    }
}
