/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_1;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_TAB;
import static android.view.KeyEvent.META_SHIFT_ON;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestFocusActivity;
import android.server.wm.jetpack.utils.TestFocusPrimaryActivity;
import android.server.wm.jetpack.utils.TestFocusSecondaryActivity;
import android.util.Pair;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests focus and
 * navigation keys.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingFocusTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingFocusTests extends ActivityEmbeddingTestBase {

    /**
     * Tests if the focus can move to the other adjacent window after sending proper tab keys.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "android.app.Activity#hasWindowFocus"})
    @Test
    public void testMoveFocusToAdjacentWindow_tab() {
        final Pair<TestFocusActivity, TestFocusActivity> activityPair = setupActivities();
        final TestFocusActivity primaryActivity = activityPair.first;
        final TestFocusActivity secondaryActivity = activityPair.second;

        exitTouchMode(secondaryActivity);

        // Make sure the focus can go to primaryActivity.
        primaryActivity.resetFocusCounter();
        for (int i = 0; i < secondaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_TAB);
        }
        primaryActivity.waitForFocus();
        assertTrue("primaryActivity must be focused.", primaryActivity.hasWindowFocus());

        // Make sure the focus can go back to secondaryActivity.
        secondaryActivity.resetFocusCounter();
        for (int i = 0; i < primaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_TAB);
        }
        secondaryActivity.waitForFocus();
        assertTrue("secondaryActivity must be focused.", secondaryActivity.hasWindowFocus());
    }

    /**
     * Tests if the focus can move to the other adjacent window after sending proper back-tab
     * (shift + tab) keys.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "android.app.Activity#hasWindowFocus"})
    @Test
    public void testMoveFocusToAdjacentWindow_backTab() {
        final Pair<TestFocusActivity, TestFocusActivity> activityPair = setupActivities();
        final TestFocusActivity primaryActivity = activityPair.first;
        final TestFocusActivity secondaryActivity = activityPair.second;

        exitTouchMode(secondaryActivity);

        // Make sure the focus can go to primaryActivity.
        primaryActivity.resetFocusCounter();
        for (int i = 0; i < secondaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_TAB, META_SHIFT_ON);
        }
        primaryActivity.waitForFocus();
        assertTrue("primaryActivity must be focused.", primaryActivity.hasWindowFocus());

        // Make sure the focus can go back to secondaryActivity.
        secondaryActivity.resetFocusCounter();
        for (int i = 0; i < primaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_TAB, META_SHIFT_ON);
        }
        secondaryActivity.waitForFocus();
        assertTrue("secondaryActivity must be focused.", secondaryActivity.hasWindowFocus());
    }

    /**
     * Tests if the focus can move to the other adjacent window after sending proper D-PAD arrow
     * keys. If there is no adjacent window at the given direction, the focus must not move to the
     * other window.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "android.app.Activity#hasWindowFocus"})
    @Test
    public void testMoveFocusToAdjacentWindow_dpadArrows() {
        final Pair<TestFocusActivity, TestFocusActivity> activityPair = setupActivities();
        final TestFocusActivity primaryActivity = activityPair.first;
        final TestFocusActivity secondaryActivity = activityPair.second;

        exitTouchMode(secondaryActivity);

        // Make sure the focus can go to primaryActivity.
        primaryActivity.resetFocusCounter();
        sendKey(KEYCODE_DPAD_UP);
        primaryActivity.waitForFocus();
        assertTrue("primaryActivity must be focused.", primaryActivity.hasWindowFocus());

        // Make sure the focus can go back to secondaryActivity.
        secondaryActivity.resetFocusCounter();
        sendKey(KEYCODE_DPAD_DOWN);
        secondaryActivity.waitForFocus();
        assertTrue("secondaryActivity must be focused.", secondaryActivity.hasWindowFocus());

        // Make sure the focus cannot go to primaryActivity if the direction is invalid.
        primaryActivity.resetFocusCounter();
        for (int i = 0; i < secondaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_DPAD_RIGHT);
        }
        for (int i = 0; i < secondaryActivity.getFocusableViewCount(); i++) {
            sendKey(KEYCODE_DPAD_LEFT);
        }
        primaryActivity.waitForFocus();
        assertFalse("primaryActivity must not be focused.", primaryActivity.hasWindowFocus());
    }

    private @NonNull Pair<TestFocusActivity, TestFocusActivity> setupActivities() {
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(new SplitAttributes.Builder()
                        .setLayoutDirection(LayoutDirection.TOP_TO_BOTTOM)
                        .build())
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        final TestFocusActivity primaryActivity = startFullScreenActivityNewTask(
                TestFocusPrimaryActivity.class);
        final String secondaryActivityId = "secondaryActivityId";
        startActivityFromActivity(primaryActivity, TestFocusSecondaryActivity.class,
                secondaryActivityId);
        waitAndAssertResumed(secondaryActivityId);
        final TestFocusActivity secondaryActivity =
                (TestFocusActivity) getResumedActivityById(secondaryActivityId);
        secondaryActivity.waitForFocus();
        return new Pair<>(primaryActivity, secondaryActivity);
    }

    private void exitTouchMode(@NonNull TestFocusActivity focusedActivity) {
        sendKey(KEYCODE_1);
        waitForIdle();
        assertEquals("The focused window must receive the key event.",
                KEYCODE_1, focusedActivity.getLastKeyCode());
        assertFalse("This focused window must not be in touch mode.",
                focusedActivity.getWindow().getDecorView().isInTouchMode());
    }

    private static void sendKey(int keyCode) {
        getInstrumentation().sendKeySync(new KeyEvent(ACTION_DOWN, keyCode));
        getInstrumentation().sendKeySync(new KeyEvent(ACTION_UP, keyCode));
    }

    private static void sendKey(int keyCode, int metaState) {
        getInstrumentation().sendKeySync(new KeyEvent(0, 0, ACTION_DOWN, keyCode, 0, metaState));
        getInstrumentation().sendKeySync(new KeyEvent(0, 0, ACTION_UP, keyCode, 0, metaState));
    }

}
