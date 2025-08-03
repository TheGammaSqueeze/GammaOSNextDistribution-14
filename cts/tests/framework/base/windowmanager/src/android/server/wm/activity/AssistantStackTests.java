/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.activity;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.ANIMATION_TEST_ACTIVITY;
import static android.server.wm.app.Components.ASSISTANT_ACTIVITY;
import static android.server.wm.app.Components.ASSISTANT_VOICE_INTERACTION_SERVICE;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_DISPLAY_ID;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_ENTER_PIP;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_FINISH_SELF;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_LAUNCH_NEW_TASK;
import static android.server.wm.app.Components.DOCKED_ACTIVITY;
import static android.server.wm.app.Components.LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION;
import static android.server.wm.app.Components.LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK;
import static android.server.wm.app.Components.LaunchAssistantActivityIntoAssistantStack.EXTRA_ASSISTANT_IS_TRANSLUCENT;
import static android.server.wm.app.Components.PIP_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_ASSISTANT_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WaitForValidActivityState;
import android.server.wm.WindowManagerState;
import android.server.wm.settings.SettingsSession;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:AssistantStackTests
 */
@Presubmit
public class AssistantStackTests extends ActivityManagerTestBase {

    private int mAssistantDisplayId = DEFAULT_DISPLAY;

    public void setUp() throws Exception {
        super.setUp();
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);
            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK);
            waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            WindowManagerState.Task assistantStack =
                    mWmState.getRootTaskByActivityType(ACTIVITY_TYPE_ASSISTANT);
            mAssistantDisplayId = assistantStack.mDisplayId;
        }
    }

    @Test
    public void testLaunchingAssistantActivityIntoAssistantStack() throws Exception {
        // Enable the assistant and launch an assistant activity
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
            waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);

            // Ensure that the activity launched in the fullscreen assistant stack
            assertAssistantStackExists();
            // In a multi-window environment the assistant might not be fullscreen
            assumeTrue(getDefaultDisplayWindowingMode() == WINDOWING_MODE_FULLSCREEN);
            assertTrue("Expected assistant stack to be fullscreen",
                    mWmState.getRootTaskByActivityType(
                            ACTIVITY_TYPE_ASSISTANT).isFullscreen());
        }
    }

    @Test
    public void testAssistantStackZOrder() throws Exception {
        assumeTrue(assistantRunsOnPrimaryDisplay());
        assumeTrue(supportsPip());
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch a pinned stack task
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP, "true"));
        waitForValidStateWithActivityTypeAndWindowingMode(
                PIP_ACTIVITY, ACTIVITY_TYPE_STANDARD, WINDOWING_MODE_PINNED);
        mWmState.assertContainsStack("Must contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        // Dock a task
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(DOCKED_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        // Enable the assistant and launch an assistant activity, ensure it is on top
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
            waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();

            mWmState.assertFrontStack("Pinned stack should be on top.",
                    WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
            mWmState.assertFocusedRootTask("Assistant stack should be focused.",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        }
    }

    @Test
    public void testAssistantStackLaunchNewTask() throws Exception {
        assertAssistantStackCanLaunchAndReturnFromNewTask();
    }

    @Test
    @Ignore("b/77272253#comment10")
    public void testAssistantStackLaunchNewTaskWithDockedStack() throws Exception {
        assumeTrue(assistantRunsOnPrimaryDisplay());
        assumeTrue(supportsSplitScreenMultiWindow());

        // Dock a task
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(DOCKED_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        //assertAssistantStackCanLaunchAndReturnFromNewTask(WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
    }

    private void assertAssistantStackCanLaunchAndReturnFromNewTask()
            throws Exception {
        // Enable the assistant and launch an assistant activity which will launch a new task
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            launchActivityOnDisplayNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK, mAssistantDisplayId,
                    extraString(EXTRA_ASSISTANT_LAUNCH_NEW_TASK, getActivityName(TEST_ACTIVITY)),
                    extraString(EXTRA_ASSISTANT_DISPLAY_ID, Integer.toString(mAssistantDisplayId)));
            // Ensure that the fullscreen stack is on top and the test activity is now visible
            waitForValidStateWithActivityType(TEST_ACTIVITY, ACTIVITY_TYPE_STANDARD);
        }

        if (isAssistantOnTopOfDream()) {
            // If the assistant is configured to be always-on-top, then the new task should have
            // been started behind it and the assistant stack should still be on top.
            mWmState.assertFocusedActivity(
                    "AssistantActivity should be focused", ASSISTANT_ACTIVITY);
            mWmState.assertFrontStackActivityType(
                    "Assistant stack should be on top.", ACTIVITY_TYPE_ASSISTANT);
            mWmState.assertFocusedRootTask("Assistant stack should be focused.",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        } else {
            mWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
            mWmState.assertFrontStack("TestActivity stack should be on top.",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD);
            mWmState.assertFocusedRootTask("TestActivity stack should be focused.",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD);
        }

        // Now, tell it to finish itself and ensure that the assistant stack is brought back forward
        mBroadcastActionTrigger.doAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
        mWmState.waitForFocusedStack(WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        mWmState.assertFrontStackActivityType(
                "Assistant stack should be on top.", ACTIVITY_TYPE_ASSISTANT);
        mWmState.assertFocusedRootTask("Assistant stack should be focused.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
    }

    @Test
    public void testAssistantStackFinishToPreviousApp() throws Exception {
        // If the Assistant is configured to be always-on-top, then the assistant activity
        // started in setUp() will not allow any other activities to start. Therefore we should
        // remove it before launching a fullscreen activity.
        if (isAssistantOnTopOfDream()) {
            removeRootTasksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
        }

        // Launch an assistant activity on top of an existing activity, and ensure that the activity
        // is still visible and on top after the assistant activity finishes
        launchActivityOnDisplay(TEST_ACTIVITY, mAssistantDisplayId);
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_FINISH_SELF, "true"));
            mWmState.waitFor((amState) -> !amState.containsActivity(ASSISTANT_ACTIVITY),
                    getActivityName(ASSISTANT_ACTIVITY) + " finished");
        }
        waitForValidStateWithActivityType(TEST_ACTIVITY, ACTIVITY_TYPE_STANDARD);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, mAssistantDisplayId,
                "TestActivity should be resumed");
        mWmState.assertFocusedActivity("TestActivity should be focused", TEST_ACTIVITY);

        mWmState.assertFrontStack("TestActivity stack should be on top.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD);
        mWmState.assertFocusedRootTask("TestActivity stack should be focused.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    public void testDisallowEnterPiPFromAssistantStack() throws Exception {
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_ENTER_PIP, "true"));
        }
        waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    public void testTranslucentAssistantActivityStackVisibility() throws Exception {
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            // Go home, launch the assistant and check to see that home is visible
            removeRootTasksInWindowingModes(WINDOWING_MODE_FULLSCREEN);
            pressHomeButton();
            resumeAppSwitches();
            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_IS_TRANSLUCENT, "true"));
            waitForValidStateWithActivityType(
                    TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mWmState.waitForHomeActivityVisible();
            if (hasHomeScreen()) {
                mWmState.assertHomeActivityVisible(true);
            }

            // Launch a fullscreen app and then launch the assistant and check to see that it is
            // also visible
            removeRootTasksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
            launchActivityOnDisplay(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN, mAssistantDisplayId);
            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_IS_TRANSLUCENT, "true"));
            waitForValidStateWithActivityType(
                    TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mWmState.assertVisibility(TEST_ACTIVITY, true);

            // Go home, launch assistant, launch app into fullscreen with activity present, and go
            // back.Ensure home is visible.
            removeRootTasksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
            pressHomeButton();
            resumeAppSwitches();
            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_IS_TRANSLUCENT, "true"),
                    extraString(EXTRA_ASSISTANT_LAUNCH_NEW_TASK, getActivityName(TEST_ACTIVITY)));
            waitForValidStateWithActivityType(TEST_ACTIVITY, ACTIVITY_TYPE_STANDARD);

            final ComponentName homeActivity = mWmState.getHomeActivityName();
            int windowingMode = mWmState.getFocusedRootTaskWindowingMode();
            // In a multi-window environment the home activity might not be fully covered
            assumeTrue(windowingMode == WINDOWING_MODE_FULLSCREEN);

            // A WM Shell can implement a policy where there are more than one default
            // Task Display Areas.
            // If there are two task display areas, for example, left and right.
            // When an activity is organized in left task display area it is still running in
            // WINDOWING_MODE_FULLSCREEN. Having said that, home activity in default task display
            // area is not fully covered. Thus, home activity is still resumed and visible.
            // More than that, in the current AOSP 11 implementation, visibility and activity
            // lifecycle work differently if activities are in the same task display area or in
            // different task display areas.
            // For example, if two task display areas have the exact same bounds,
            // for the user, an activity running in the top task display area covers the activity
            // running in the bottom task display area. However, because they are in separate
            // display areas they will be both resumed.
            // There can be cases where the bounds match but because tasks are in different
            // task display area they are both resumed.
            // So we check the activity bounds for two activities to confirm if task display
            // areas are the same. If not the same, we will skip the visibility asserting test.
            if (compareActivityBounds(homeActivity,
                    ComponentName.unflattenFromString(mWmState.getFocusedActivity()))) {
                mWmState.waitAndAssertVisibilityGone(homeActivity);
            }

            mBroadcastActionTrigger.doAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
            mWmState.waitForFocusedStack(WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mWmState.waitForHomeActivityVisible();
            if (hasHomeScreen()) {
                mWmState.assertHomeActivityVisible(true);
            }

            // Launch a fullscreen and docked app and then launch the assistant and check to see
            // that it
            // is also visible
            if (supportsSplitScreenMultiWindow() &&  assistantRunsOnPrimaryDisplay()) {
                removeRootTasksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
                launchActivitiesInSplitScreen(
                        getLaunchActivityBuilder().setTargetActivity(DOCKED_ACTIVITY),
                        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
                launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                        extraString(EXTRA_ASSISTANT_IS_TRANSLUCENT, "true"));
                waitForValidStateWithActivityType(
                        TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
                assertAssistantStackExists();
                mWmState.assertVisibility(DOCKED_ACTIVITY, true);
                mWmState.assertVisibility(TEST_ACTIVITY, true);
            }
        }
    }

    @Test
    public void testLaunchIntoSameTask() throws Exception {
        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            // start home to ensure the foreground app is no longer the assistant activity.
            launchHomeActivity();
            // Launch the assistant
            launchActivityOnDisplayNoWait(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION,
                    mAssistantDisplayId);
            waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            mWmState.waitAndAssertActivityState(ASSISTANT_ACTIVITY, STATE_RESUMED);
            assertAssistantStackExists();
            mWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
            mWmState.assertFocusedRootTask("Expected assistant stack focused",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
            final WindowManagerState amState = mWmState;
            // If activity is hosted by created-by-organizer root task, the root task captured by
            // WindowManagerState will have 0 child task. Otherwise, root task contains 1 child
            // task.
            assertThat(amState.getRootTaskByActivityType(ACTIVITY_TYPE_ASSISTANT).getTasks(),
                    hasSize(lessThanOrEqualTo(1)));
            final int taskId = mWmState.getTaskByActivity(ASSISTANT_ACTIVITY).getTaskId();

            // Launch a new fullscreen activity
            // Using Animation Test Activity because it is opaque on all devices.
            int launchTDAId = mWmState.getTaskDisplayAreaFeatureId(ASSISTANT_ACTIVITY);
            launchActivityOnTaskDisplayArea(ANIMATION_TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                    launchTDAId, mAssistantDisplayId);
            // If the activity is not launched in same TDA, ASSISTANT_ACTIVITY will be visible.
            assumeTrue("Should launch in same TDA",
                    mWmState.getTaskDisplayArea(ASSISTANT_ACTIVITY)
                            == mWmState.getTaskDisplayArea(ANIMATION_TEST_ACTIVITY)
            );

            if (isAssistantOnTopOfDream()) {
                mWmState.waitAndAssertActivityState(ASSISTANT_ACTIVITY, STATE_RESUMED);
                mWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
            } else {
                mWmState.waitAndAssertActivityState(ANIMATION_TEST_ACTIVITY, STATE_RESUMED);
                mWmState.waitAndAssertVisibilityGone(ASSISTANT_ACTIVITY);
            }

            // Launch the assistant again and ensure that it goes into the same task
            launchActivityOnDisplayNoWait(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION,
                    mAssistantDisplayId);
            waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
            mWmState.assertFocusedRootTask("Expected assistant stack focused",
                    WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
            // If activity is hosted by created-by-organizer root task, the root task captured by
            // WindowManagerState will have 0 child task. Otherwise, root task contains 1 child
            // task.
            assertThat(amState.getRootTaskByActivityType(ACTIVITY_TYPE_ASSISTANT).getTasks(),
                    hasSize(lessThanOrEqualTo(1)));
            assertEquals(taskId,
                    mWmState.getTaskByActivity(ASSISTANT_ACTIVITY).getTaskId());

        }
    }

    @Test
    public void testPinnedStackWithAssistant() throws Exception {
        assumeTrue(supportsPip());
        assumeTrue(supportsSplitScreenMultiWindow());

        try (final AssistantSession assistantSession = new AssistantSession()) {
            assistantSession.setVoiceInteractionService(ASSISTANT_VOICE_INTERACTION_SERVICE);

            // Launch a fullscreen activity and a PIP activity, then launch the assistant, and
            // ensure that the test activity is still visible
            launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
            launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP, "true"));
            launchActivityNoWait(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    extraString(EXTRA_ASSISTANT_IS_TRANSLUCENT, String.valueOf(true)));
            waitForValidStateWithActivityType(
                    TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mWmState.assertVisibility(TRANSLUCENT_ASSISTANT_ACTIVITY, true);
            mWmState.assertVisibility(PIP_ACTIVITY, true);
            mWmState.assertVisibility(TEST_ACTIVITY, true);

        }
    }

    private void waitForValidStateWithActivityType(ComponentName activityName, int activityType)
            throws Exception {
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setActivityType(activityType)
                .build());
    }

    private void waitForValidStateWithActivityTypeAndWindowingMode(ComponentName activityName,
            int activityType, int windowingMode) throws Exception {
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setActivityType(activityType)
                .setWindowingMode(windowingMode)
                .build());
    }

    /**
     * Asserts that the assistant stack exists.
     */
    private void assertAssistantStackExists() throws Exception {
        mWmState.assertContainsStack("Must contain assistant stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
    }

    // Any 2D Activity in VR mode is run on a special VR virtual display, so check if the Assistant
    // is going to run on the same display as other tasks.
    protected boolean assistantRunsOnPrimaryDisplay() {
        return mAssistantDisplayId == DEFAULT_DISPLAY;
    }

    /**
     * @return Windowing Mode from the default display
     */
    private int getDefaultDisplayWindowingMode() {
        mWmState.computeState();
        return mWmState.getDisplay(DEFAULT_DISPLAY).getWindowingMode();
    }

    /** Helper class to save, set, and restore
     * {@link Settings.Secure#VOICE_INTERACTION_SERVICE} system preference.
     */
    private static class AssistantSession extends SettingsSession<String> {
        AssistantSession() {
            super(Settings.Secure.getUriFor(Settings.Secure.VOICE_INTERACTION_SERVICE),
                    Settings.Secure::getString, Settings.Secure::putString);
        }

        void setVoiceInteractionService(ComponentName assistantName) throws Exception {
            super.set(getActivityName(assistantName));
        }
    }

    /**
     * Compare if bounds were the same between activities
     */
    private boolean compareActivityBounds(ComponentName activityName1,
            ComponentName activityName2) {
        if (mWmState.getTaskByActivity(activityName1).getBounds()
                .equals(mWmState.getTaskByActivity(activityName2).getBounds())) {
            return true;
        }
        return false;
    }
}
