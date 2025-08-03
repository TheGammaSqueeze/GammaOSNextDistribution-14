/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.server.wm.display;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.server.wm.StateLogger.logE;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.dpToPx;
import static android.server.wm.app.Components.BROADCAST_RECEIVER_ACTIVITY;
import static android.server.wm.app.Components.DIALOG_WHEN_LARGE_ACTIVITY;
import static android.server.wm.app.Components.LANDSCAPE_ORIENTATION_ACTIVITY;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.LandscapeOrientationActivity.EXTRA_APP_CONFIG_INFO;
import static android.server.wm.app.Components.LandscapeOrientationActivity.EXTRA_CONFIG_INFO_IN_ON_CREATE;
import static android.server.wm.app.Components.LandscapeOrientationActivity.EXTRA_DISPLAY_REAL_SIZE;
import static android.server.wm.app.Components.LandscapeOrientationActivity.EXTRA_SYSTEM_RESOURCES_CONFIG_INFO;
import static android.server.wm.app.Components.NIGHT_MODE_ACTIVITY;
import static android.server.wm.app.Components.PORTRAIT_ORIENTATION_ACTIVITY;
import static android.server.wm.app.Components.RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.translucentapp26.Components.SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;
import android.server.wm.CommandSession.ConfigInfo;
import android.server.wm.CommandSession.SizeInfo;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.RotationSession;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.WaitForValidActivityState;
import android.server.wm.WindowManagerState;
import android.view.Display;
import android.window.WindowContainerTransaction;

import org.junit.Test;

import java.util.function.Function;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:AppConfigurationTests
 */
@Presubmit
public class AppConfigurationTests extends MultiDisplayTestBase {

    private static final int SMALL_WIDTH_DP = 426;
    private static final int SMALL_HEIGHT_DP = 320;

    /**
     * Tests that the WindowManager#getDefaultDisplay() and the Configuration of the Activity
     * has an updated size when the Activity is resized from fullscreen to docked state.
     *
     * The Activity handles configuration changes, so it will not be restarted between resizes.
     * On Configuration changes, the Activity logs the Display size and Configuration width
     * and heights. The values reported in fullscreen should be larger than those reported in
     * docked state.
     */
    @Test
    public void testConfigurationUpdatesWhenResizedFromFullscreen() {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        separateTestJournal();
        launchActivity(RESIZEABLE_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        final SizeInfo fullscreenSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        separateTestJournal();
        putActivityInPrimarySplit(RESIZEABLE_ACTIVITY);
        final SizeInfo dockedSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        assertSizesAreSane(fullscreenSizes, dockedSizes);
    }

    /**
     * Same as {@link #testConfigurationUpdatesWhenResizedFromFullscreen()} but resizing
     * from docked state to fullscreen (reverse).
     */
    @Test
    public void testConfigurationUpdatesWhenResizedFromDockedStack() {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        separateTestJournal();
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_ACTIVITY)
                                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN));

        final SizeInfo dockedSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        separateTestJournal();
        dismissSplitScreen(true /* primaryOnTop */);
        final SizeInfo fullscreenSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        assertSizesAreSane(fullscreenSizes, dockedSizes);
    }

    /**
     * Tests whether the Display sizes change when rotating the device.
     */
    @Test
    public void testConfigurationUpdatesWhenRotatingWhileFullscreen() {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_0);

        separateTestJournal();
        final ActivitySessionClient resizeableActivityClient = createManagedActivityClientSession();
        resizeableActivityClient.startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(RESIZEABLE_ACTIVITY)
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN));
        final SizeInfo initialSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        rotateAndCheckSizes(rotationSession, resizeableActivityClient, initialSizes);
    }

    /**
     * Same as {@link #testConfigurationUpdatesWhenRotatingWhileFullscreen()} but when the Activity
     * is in the docked stack.
     */
    @Test
    public void testConfigurationUpdatesWhenRotatingWhileDocked() {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        final ActivitySessionClient resizeableActivityClient = createManagedActivityClientSession();
        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_0);

        separateTestJournal();
        // Launch our own activity to side in case Recents (or other activity to side) doesn't
        // support rotation.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
        // Launch target activity in docked stack.
        getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_ACTIVITY)
                .setActivitySessionClient(resizeableActivityClient).execute();
        final SizeInfo initialSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        rotateAndCheckSizes(rotationSession, resizeableActivityClient, initialSizes);
    }

    /**
     * Same as {@link #testConfigurationUpdatesWhenRotatingWhileDocked()} but when the Activity
     * is launched to side from docked stack.
     */
    @Test
    public void testConfigurationUpdatesWhenRotatingToSideFromDocked() {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        final ActivitySessionClient resizeableActivityClient = createManagedActivityClientSession();
        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_0);

        separateTestJournal();
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_ACTIVITY)
                        .setActivitySessionClient(resizeableActivityClient));
        final SizeInfo initialSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);

        rotateAndCheckSizes(rotationSession, resizeableActivityClient, initialSizes);
    }

    private void rotateAndCheckSizes(RotationSession rotationSession,
            ActivitySessionClient noRelaunchActivityClient, SizeInfo prevSizes) {
        final ActivitySession activitySession = noRelaunchActivityClient.getLastStartedSession();
        final ComponentName activityName = activitySession.getName();
        final WindowManagerState.Task task = mWmState.getTaskByActivity(activityName);
        final int displayId = mWmState.getRootTask(task.getRootTaskId()).mDisplayId;

        assumeTrue(supportsLockedUserRotation(rotationSession, displayId));

        final boolean isCloseToSquareDisplay = isCloseToSquareDisplay();
        final int[] rotations = { ROTATION_270, ROTATION_180, ROTATION_90, ROTATION_0 };
        for (final int rotation : rotations) {
            separateTestJournal();
            rotationSession.set(rotation);
            final int newDeviceRotation = getDeviceRotation(displayId);
            if (newDeviceRotation == INVALID_DEVICE_ROTATION) {
                logE("Got an invalid device rotation value. "
                        + "Continuing the test despite of that, but it is likely to fail.");
            }
            final boolean expectConfigChange = task.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                    && !isCloseToSquareDisplay;
            if (expectConfigChange) {
                assertActivityLifecycle(activityName, false /* relaunch */);
            }
            final SizeInfo rotatedSizes = activitySession.getConfigInfo().sizeInfo;
            assertSizesRotate(prevSizes, rotatedSizes,
                    // Skip orientation checks if we are not in fullscreen mode, or when the display
                    // is close to square because the app config orientation may always be landscape
                    // excluding the system insets.
                    !expectConfigChange /* skipOrientationCheck */);
            prevSizes = rotatedSizes;
        }
    }

    /**
     * Tests when activity moved from fullscreen stack to docked and back. Activity will be
     * relaunched twice and it should have same config as initial one.
     */
    @Test
    public void testSameConfigurationFullSplitFullRelaunch() {
        moveActivityFullSplitFull(true /* relaunch */);
    }

    /**
     * Same as {@link #testSameConfigurationFullSplitFullRelaunch} but without relaunch.
     */
    @Test
    public void testSameConfigurationFullSplitFullNoRelaunch() {
        moveActivityFullSplitFull(false /* relaunch */);
    }

    /**
     * Launches activity in fullscreen task, moves to docked task and back to fullscreen task.
     * Asserts that initial and final reported sizes in fullscreen task are the same.
     */
    private void moveActivityFullSplitFull(boolean relaunch) {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        final ComponentName activityName = relaunch ? TEST_ACTIVITY : RESIZEABLE_ACTIVITY;
        // Launch to fullscreen task and record size.
        separateTestJournal();
        launchActivity(activityName, WINDOWING_MODE_FULLSCREEN);
        final SizeInfo initialFullscreenSizes = getLastReportedSizesForActivity(activityName);

        // Ensure the orientation configuration is different while moving the activity into split
        // primary task later if we expected activity to be launched.
        if (relaunch) {
            mTaskOrganizer.registerOrganizerIfNeeded();
            Rect primaryTaskBounds = mTaskOrganizer.getPrimaryTaskBounds();
            if (initialFullscreenSizes.displayHeight > initialFullscreenSizes.displayWidth) {
                primaryTaskBounds.bottom = primaryTaskBounds.width() / 2;
            } else {
                primaryTaskBounds.right = primaryTaskBounds.height() / 2;
            }
            mTaskOrganizer.setRootPrimaryTaskBounds(primaryTaskBounds);
        }

        // Move the task to the primary split task.
        separateTestJournal();
        putActivityInPrimarySplit(activityName);
        // Currently launchActivityInPrimarySplit launches the target activity and then move it
        // to split task, so it requires waiting of lifecycle to get the stable initial size.
        if (relaunch) {
            assertActivityLifecycle(activityName, true /* relaunch */);
        } else {
            // The lifecycle callbacks contain the initial launch event so only wait for
            // multi-window mode changed.
            waitForOnMultiWindowModeChanged(activityName);
        }
        final SizeInfo dockedSizes = getLastReportedSizesForActivity(activityName);
        assertSizesAreSane(initialFullscreenSizes, dockedSizes);
        final boolean orientationChanged =
                initialFullscreenSizes.orientation != dockedSizes.orientation;

        separateTestJournal();
        // Restore to fullscreen.
        final int activityTaskId = mWmState.getTaskByActivity(activityName).getTaskId();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
            .setWindowingMode(mTaskOrganizer.getTaskInfo(activityTaskId).getToken(),
                WINDOWING_MODE_FULLSCREEN);
        mTaskOrganizer.dismissSplitScreen(wct, false /* primaryOnTop */);
        // Home task could be on top since it was the top-most task while in split-screen mode
        // (dock task was minimized), start the activity again to ensure the activity is at
        // foreground.
        launchActivity(activityName, WINDOWING_MODE_FULLSCREEN);
        if (relaunch && !orientationChanged) {
            // If there is no orientation changes while moving the non-resizeable activity out of
            // the split, the Activity won't be relaunched because size changes won't cross the
            // size config buckets. So, there won't be any lifecycle changes.
            waitForOnMultiWindowModeChanged(activityName);
        } else {
            assertActivityLifecycle(activityName, relaunch);
        }

        // It must report same size as original one after split-screen dismissed.
        final SizeInfo finalFullscreenSizes = getLastReportedSizesForActivity(activityName);
        assertSizesAreSame(initialFullscreenSizes, finalFullscreenSizes);
    }

    /**
     * Tests that an activity with the DialogWhenLarge theme can transform properly when in split
     * screen.
     */
    @Test
    public void testDialogWhenLargeSplitSmall() {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(DIALOG_WHEN_LARGE_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
        int displayId = mWmState.getDisplayByActivity(DIALOG_WHEN_LARGE_ACTIVITY);
        final WindowManagerState.DisplayContent display = mWmState.getDisplay(displayId);
        final int density = display.getDpi();
        final int smallWidthPx = dpToPx(SMALL_WIDTH_DP, density);
        final int smallHeightPx = dpToPx(SMALL_HEIGHT_DP, density);

        mTaskOrganizer.setRootPrimaryTaskBounds(new Rect(0, 0, smallWidthPx, smallHeightPx));
        mWmState.waitForValidState(
                new WaitForValidActivityState.Builder(DIALOG_WHEN_LARGE_ACTIVITY)
                        .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                        .setActivityType(ACTIVITY_TYPE_STANDARD)
                        .build());
    }

    /**
     * Test that device handles consequent requested orientations and displays the activities.
     */
    @Test
    public void testFullscreenAppOrientationRequests() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());

        separateTestJournal();
        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        mWmState.assertVisibility(PORTRAIT_ORIENTATION_ACTIVITY, true /* visible */);
        SizeInfo reportedSizes = getLastReportedSizesForActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        assertEquals("portrait activity should be in portrait",
                ORIENTATION_PORTRAIT, reportedSizes.orientation);
        assertTrue("portrait activity should have height >= width",
                reportedSizes.heightDp >= reportedSizes.widthDp);
        separateTestJournal();

        launchActivity(LANDSCAPE_ORIENTATION_ACTIVITY);
        mInstrumentation.getUiAutomation().syncInputTransactions();
        mWmState.assertVisibility(LANDSCAPE_ORIENTATION_ACTIVITY, true /* visible */);
        reportedSizes = getLastReportedSizesForActivity(LANDSCAPE_ORIENTATION_ACTIVITY);
        assertEquals("landscape activity should be in landscape",
                ORIENTATION_LANDSCAPE, reportedSizes.orientation);
        assertTrue("landscape activity should have height < width",
                reportedSizes.heightDp < reportedSizes.widthDp);
        separateTestJournal();

        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        mWmState.assertVisibility(PORTRAIT_ORIENTATION_ACTIVITY, true /* visible */);
        reportedSizes = getLastReportedSizesForActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        assertEquals("portrait activity should be in portrait",
                ORIENTATION_PORTRAIT, reportedSizes.orientation);
    }

    @Test
    public void testTranslucentAppOrientationRequests() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());
        disableIgnoreOrientationRequest();

        separateTestJournal();
        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        final SizeInfo initialReportedSizes =
                getLastReportedSizesForActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        assertEquals("portrait activity should be in portrait",
                ORIENTATION_PORTRAIT, initialReportedSizes.orientation);
        assertTrue("portrait activity should have height >= width",
                initialReportedSizes.heightDp >= initialReportedSizes.widthDp);
        assumeFalse("Skipping test: device is fixed to user rotation",
                mWmState.isFixedToUserRotation());

        separateTestJournal();

        launchActivity(SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        assumeNotIgnoringOrientation(SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY);
        assertEquals("Legacy translucent activity requested landscape orientation",
                SCREEN_ORIENTATION_LANDSCAPE, mWmState.getLastOrientation());

        // TODO(b/36897968): uncomment once we can suppress unsupported configurations
        // final ReportedSizes updatedReportedSizes =
        //      getLastReportedSizesForActivity(PORTRAIT_ACTIVITY_NAME, logSeparator);
        // assertEquals("portrait activity should not have moved from portrait",
        //         1 /* portrait */, updatedReportedSizes.orientation);
    }

    /**
     * Test that device handles consequent requested orientations and will not report a config
     * change to an invisible activity.
     */
    @Test
    public void testAppOrientationRequestConfigChanges() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());

        separateTestJournal();
        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        mWmState.assertVisibility(PORTRAIT_ORIENTATION_ACTIVITY, true /* visible */);

        assertLifecycleCounts(PORTRAIT_ORIENTATION_ACTIVITY,
                1 /* create */, 1 /* start */, 1 /* resume */,
                0 /* pause */, 0 /* stop */, 0 /* destroy */, 0 /* config */);

        launchActivity(LANDSCAPE_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        mWmState.assertVisibility(LANDSCAPE_ORIENTATION_ACTIVITY, true /* visible */);

        assertLifecycleCounts(PORTRAIT_ORIENTATION_ACTIVITY,
                1 /* create */, 1 /* start */, 1 /* resume */,
                1 /* pause */, 1 /* stop */, 0 /* destroy */, 0 /* config */);
        assertLifecycleCounts(LANDSCAPE_ORIENTATION_ACTIVITY,
                1 /* create */, 1 /* start */, 1 /* resume */,
                0 /* pause */, 0 /* stop */, 0 /* destroy */, 0 /* config */);

        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        mWmState.assertVisibility(PORTRAIT_ORIENTATION_ACTIVITY, true /* visible */);

        assertLifecycleCounts(PORTRAIT_ORIENTATION_ACTIVITY,
                2 /* create */, 2 /* start */, 2 /* resume */,
                1 /* pause */, 1 /* stop */, 0 /* destroy */, 0 /* config */);
        assertLifecycleCounts(LANDSCAPE_ORIENTATION_ACTIVITY,
                1 /* create */, 1 /* start */, 1 /* resume */,
                1 /* pause */, 1 /* stop */, 0 /* destroy */, 0 /* config */);
    }

    @Test
    public void testRotatedInfoWithFixedRotationTransform() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());

        // Start a portrait activity first to ensure that the orientation will change.
        launchActivity(PORTRAIT_ORIENTATION_ACTIVITY);
        mWmState.waitForDisplayOrientation(ORIENTATION_PORTRAIT);
        final int prevRotation = mWmState.getRotation();

        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(LANDSCAPE_ORIENTATION_ACTIVITY)
                // Request the info from onCreate because at that moment the real display hasn't
                // rotated but the activity is rotated.
                .setIntentExtra(bundle -> bundle.putBoolean(EXTRA_CONFIG_INFO_IN_ON_CREATE, true))
                .execute();
        mWmState.waitForDisplayOrientation(ORIENTATION_LANDSCAPE);

        final SizeInfo reportedSizes =
                getLastReportedSizesForActivity(LANDSCAPE_ORIENTATION_ACTIVITY);
        final Bundle extras = TestJournalContainer.get(LANDSCAPE_ORIENTATION_ACTIVITY).extras;
        final ConfigInfo appConfigInfo = extras.getParcelable(EXTRA_APP_CONFIG_INFO);
        final Point onCreateRealDisplaySize = extras.getParcelable(EXTRA_DISPLAY_REAL_SIZE);
        final ConfigInfo onCreateConfigInfo = extras.getParcelable(EXTRA_CONFIG_INFO_IN_ON_CREATE);
        final SizeInfo onCreateSize = onCreateConfigInfo.sizeInfo;
        final ConfigInfo globalConfigInfo =
                extras.getParcelable(EXTRA_SYSTEM_RESOURCES_CONFIG_INFO);
        final SizeInfo globalSizeInfo = globalConfigInfo.sizeInfo;

        assertEquals("The last reported size should be the same as the one from onCreate",
                reportedSizes, onCreateConfigInfo.sizeInfo);

        final WindowManagerState.DisplayContent dc = mWmState.getDisplay(Display.DEFAULT_DISPLAY);
        final Point realDisplaySize =
                new Point(dc.getDisplayRect().width(), dc.getDisplayRect().height());
        final int currentRotation = mWmState.getRotation();
        // Some devices may launch the activity in a letterboxed area so the display won't rotate.
        final boolean displayRotationChanged = prevRotation != currentRotation;

        assertEquals("The activity should get the final display rotation in onCreate",
                currentRotation, onCreateConfigInfo.rotation);
        assertEquals("The application should get the final display rotation in onCreate",
                currentRotation, appConfigInfo.rotation);
        assertEquals("The orientation of application must be landscape",
                ORIENTATION_LANDSCAPE, appConfigInfo.sizeInfo.orientation);
        assertEquals("The orientation of system resources must be landscape",
                ORIENTATION_LANDSCAPE, globalSizeInfo.orientation);

        final boolean isLandscape = onCreateSize.displayWidth > onCreateSize.displayHeight;
        if (displayRotationChanged) {
            assertEquals("The activity should get the final display size in onCreate",
                    realDisplaySize, onCreateRealDisplaySize);
            assertEquals("The app size of activity should have the same orientation", isLandscape,
                    realDisplaySize.x > realDisplaySize.y);
            assertEquals("The display metrics of system resources must be landscape", isLandscape,
                    globalSizeInfo.metricsWidth > globalSizeInfo.metricsHeight);
        }
        assertEquals("The application should get the same orientation", isLandscape,
                appConfigInfo.sizeInfo.displayWidth > appConfigInfo.sizeInfo.displayHeight);
        assertEquals("The app display metrics must be landscape", isLandscape,
                appConfigInfo.sizeInfo.metricsWidth > appConfigInfo.sizeInfo.metricsHeight);
    }

    @Test
    public void testTranslucentActivityPermitted() throws Exception {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());

        disableIgnoreOrientationRequest();

        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_0);

        launchActivity(SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        assumeNotIgnoringOrientation(SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY);
        mWmState.assertResumedActivity(
                "target SDK <= 26 translucent activity should be allowed to launch",
                SDK26_TRANSLUCENT_LANDSCAPE_ACTIVITY);
        assertEquals("translucent activity requested landscape orientation",
                SCREEN_ORIENTATION_LANDSCAPE, mWmState.getLastOrientation());
    }

    /**
     * Test that device handles moving between two tasks with different orientations.
     */
    @Test
    public void testTaskCloseRestoreFixedOrientation() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());
        disableIgnoreOrientationRequest();

        // Start landscape activity.
        launchActivity(LANDSCAPE_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        assumeFalse("Skipping test: device is fixed to user rotation",
                mWmState.isFixedToUserRotation());
        assumeNotIgnoringOrientation(LANDSCAPE_ORIENTATION_ACTIVITY);
        mWmState.assertVisibility(LANDSCAPE_ORIENTATION_ACTIVITY, true /* visible */);
        mWmState.waitAndAssertLastOrientation("Fullscreen app requested landscape orientation",
                SCREEN_ORIENTATION_LANDSCAPE);

        // Start another activity in a different task.
        launchActivityInNewTask(BROADCAST_RECEIVER_ACTIVITY);

        // Request portrait
        mBroadcastActionTrigger.requestOrientation(SCREEN_ORIENTATION_PORTRAIT);
        mWmState.waitForLastOrientation(SCREEN_ORIENTATION_PORTRAIT);
        waitForBroadcastActivityReady(ORIENTATION_PORTRAIT);

        // Finish activity
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        // Verify that activity brought to front is in originally requested orientation.
        mWmState.computeState(LANDSCAPE_ORIENTATION_ACTIVITY);
        mWmState.waitAndAssertLastOrientation("Should return to app in landscape orientation",
                SCREEN_ORIENTATION_LANDSCAPE);
    }

    /**
     * Test that device handles moving between two tasks with different orientations.
     */
    @Test
    public void testTaskCloseRestoreFreeOrientation() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());

        // Start landscape activity.
        launchActivity(RESIZEABLE_ACTIVITY);
        mWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);
        // This should be ActivityInfo#SCREEN_ORIENTATION_UNSPECIFIED (default value of
        // android:screenOrientation).
        final int initialServerOrientation = mWmState.getLastOrientation();

        // Verify fixed-landscape
        launchActivityInNewTask(BROADCAST_RECEIVER_ACTIVITY);
        mBroadcastActionTrigger.requestOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        mWmState.waitForLastOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        waitForBroadcastActivityReady(ORIENTATION_LANDSCAPE);
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        // Verify that activity brought to front is in originally requested orientation.
        mWmState.waitForActivityState(RESIZEABLE_ACTIVITY, STATE_RESUMED);
        // Note that the actual orientation of the activity can be either portrait or landscape
        // depending on the sensor, so only verify the "requested orientation".
        mWmState.waitAndAssertLastOrientation("Should come back in original server orientation",
                initialServerOrientation);

        // Verify fixed-portrait
        launchActivityInNewTask(BROADCAST_RECEIVER_ACTIVITY);
        mBroadcastActionTrigger.requestOrientation(SCREEN_ORIENTATION_PORTRAIT);
        mWmState.waitForLastOrientation(SCREEN_ORIENTATION_PORTRAIT);
        waitForBroadcastActivityReady(ORIENTATION_PORTRAIT);
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        // Verify that activity brought to front is in originally requested orientation.
        mWmState.waitForActivityState(RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.waitAndAssertLastOrientation("Should come back in original server orientation",
                initialServerOrientation);
    }

    /**
     * Test that activity orientation will change when device is rotated.
     * Also verify that occluded activity will not get config changes.
     */
    @Test
    public void testAppOrientationWhenRotating() throws Exception {
        assumeFalse("Skipping test: square size may not have configuration changes",
                isCloseToSquareDisplay());
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        // Start resizeable activity that handles configuration changes.
        separateTestJournal();
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        launchActivity(RESIZEABLE_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        mWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);

        final int displayId = mWmState.getDisplayByActivity(RESIZEABLE_ACTIVITY);

        // Rotate the activity and check that it receives configuration changes with a different
        // orientation each time.
        final RotationSession rotationSession = createManagedRotationSession();
        assumeTrue("Skipping test: no locked user rotation mode support.",
                supportsLockedUserRotation(rotationSession, displayId));

        rotationSession.set(ROTATION_0);
        SizeInfo reportedSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);
        int prevOrientation = reportedSizes.orientation;

        final int[] rotations = { ROTATION_270, ROTATION_180, ROTATION_90, ROTATION_0 };
        for (final int rotation : rotations) {
            separateTestJournal();
            rotationSession.set(rotation);

            // Verify lifecycle count and orientation changes.
            assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY, 0 /* numRelaunch */,
                    1 /* numConfigChange */);
            reportedSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);
            assertNotEquals(prevOrientation, reportedSizes.orientation);
            assertRelaunchOrConfigChanged(TEST_ACTIVITY, 0 /* numRelaunch */,
                    0 /* numConfigChange */);

            prevOrientation = reportedSizes.orientation;
        }
    }

    /**
     * Test that the orientation for a simulated display context derived from an application context
     * will not change when the device rotates.
     */
    @Test
    public void testAppContextDerivedDisplayContextOrientationWhenRotating() {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        assumeTrue("Skipping test: no multi-display support", supportsMultiDisplay());

        assertDisplayContextDoesntChangeOrientationWhenRotating(Activity::getApplicationContext);
    }

    /**
     * Test that the orientation for a simulated display context derived from an activity context
     * will not change when the device rotates.
     */
    @Test
    public void testActivityContextDerivedDisplayContextOrientationWhenRotating() {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        assumeTrue("Skipping test: no multi-display support", supportsMultiDisplay());

        assertDisplayContextDoesntChangeOrientationWhenRotating(activity -> activity);
    }

    /**
     * Asserts that the orientation for a simulated display context derived from a base context will
     * not change when the device rotates.
     *
     * @param baseContextSupplier function that returns a base context used to created the display
     *                            context.
     *
     * @see #testAppContextDerivedDisplayContextOrientationWhenRotating
     * @see #testActivityContextDerivedDisplayContextOrientationWhenRotating
     */
    private void assertDisplayContextDoesntChangeOrientationWhenRotating(
            Function<Activity, Context> baseContextSupplier) {
        RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_0);

        TestActivitySession<ConfigChangeHandlingActivity> activitySession
                = createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(
                ConfigChangeHandlingActivity.class,
                Display.DEFAULT_DISPLAY,
                WINDOWING_MODE_FULLSCREEN);
        final ConfigChangeHandlingActivity activity = activitySession.getActivity();

        VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();
        WindowManagerState.DisplayContent displayContent = virtualDisplaySession
                .setSimulateDisplay(true)
                .setSimulationDisplaySize(100 /* width */, 200 /* height */)
                .createDisplay();

        DisplayManager dm = activity.getSystemService(DisplayManager.class);
        Display simulatedDisplay = dm.getDisplay(displayContent.mId);
        Context simulatedDisplayContext = baseContextSupplier.apply(activity)
                .createDisplayContext(simulatedDisplay);
        assertEquals(ORIENTATION_PORTRAIT,
                simulatedDisplayContext.getResources().getConfiguration().orientation);

        separateTestJournal();

        final int[] rotations = {ROTATION_270, ROTATION_180, ROTATION_90, ROTATION_0};
        for (final int rotation : rotations) {
            rotationSession.set(rotation);

            assertRelaunchOrConfigChanged(activity.getComponentName(), 0 /* numRelaunch */,
                    1 /* numConfigChange */);
            separateTestJournal();

            assertEquals("Display context orientation must not be changed", ORIENTATION_PORTRAIT,
                    simulatedDisplayContext.getResources().getConfiguration().orientation);
        }
    }

    /**
     * Test that activity orientation will not change when trying to rotate fixed-orientation
     * activity.
     * Also verify that occluded activity will not get config changes.
     */
    @Test
    public void testFixedOrientationWhenRotating() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());
        // TODO(b/110533226): Fix test on devices with display cutout
        assumeFalse("Skipping test: display cutout present, can't predict exact lifecycle",
                hasDisplayCutout());
        disableIgnoreOrientationRequest();

        // Start portrait-fixed activity
        separateTestJournal();
        final ActivitySession activitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                        .setTargetActivity(RESIZEABLE_ACTIVITY));
        mWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);

        final int displayId = mWmState.getDisplayByActivity(RESIZEABLE_ACTIVITY);
        final ComponentName fixedOrientationActivity =
                mWmState.getDisplay(displayId).getFullConfiguration().orientation
                        == ORIENTATION_LANDSCAPE
                        ? LANDSCAPE_ORIENTATION_ACTIVITY : PORTRAIT_ORIENTATION_ACTIVITY;

        final RotationSession rotationSession = createManagedRotationSession();
        assumeTrue("Skipping test: no user locked rotation support.",
                supportsLockedUserRotation(rotationSession, displayId));

        launchActivity(fixedOrientationActivity, WINDOWING_MODE_FULLSCREEN);
        assumeNotIgnoringOrientation(fixedOrientationActivity);
        mWmState.assertVisibility(fixedOrientationActivity, true /* visible */);
        final SizeInfo initialSize = activitySession.getConfigInfo().sizeInfo;

        // Rotate the display and check that the orientation doesn't change
        final int[] rotations = { ROTATION_0, ROTATION_270, ROTATION_180, ROTATION_90 };
        for (final int rotation : rotations) {
            separateTestJournal();
            rotationSession.set(rotation, false /* waitDeviceRotation */);

            // Verify lifecycle count and orientation changes.
            assertRelaunchOrConfigChanged(fixedOrientationActivity, 0 /* numRelaunch */,
                    0 /* numConfigChange */);
            final SizeInfo currentSize = activitySession.getConfigInfo().sizeInfo;
            assertEquals("Sizes must not be changed", initialSize, currentSize);
            assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY, 0 /* numRelaunch */,
                    0 /* numConfigChange */);
        }
    }

    /**
     * Test that device handles moving between two tasks with different orientations.
     */
    @Test
    public void testTaskMoveToBackOrientation() {
        assumeTrue("Skipping test: no orientation request support", supportsOrientationRequest());
        disableIgnoreOrientationRequest();

        // Start landscape activity.
        launchActivity(LANDSCAPE_ORIENTATION_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        assumeNotIgnoringOrientation(LANDSCAPE_ORIENTATION_ACTIVITY);
        mWmState.assertVisibility(LANDSCAPE_ORIENTATION_ACTIVITY, true /* visible */);
        final boolean isFixedToUserRotation = mWmState.isFixedToUserRotation();
        if (!isFixedToUserRotation) {
            mWmState.waitAndAssertLastOrientation(
                        "Fullscreen app requested landscape orientation",
                        SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            final SizeInfo reportedSizes =
                    getLastReportedSizesForActivity(LANDSCAPE_ORIENTATION_ACTIVITY);
            assertEquals("landscape activity should be in landscape",
                    ORIENTATION_LANDSCAPE, reportedSizes.orientation);
            assertTrue("landscape activity should have height < width",
                    reportedSizes.heightDp < reportedSizes.widthDp);
        }

        // Start another activity in a different task.
        launchActivityInNewTask(BROADCAST_RECEIVER_ACTIVITY);

        // Request portrait
        mBroadcastActionTrigger.requestOrientation(SCREEN_ORIENTATION_PORTRAIT);
        mWmState.waitForLastOrientation(SCREEN_ORIENTATION_PORTRAIT);
        waitForBroadcastActivityReady(ORIENTATION_PORTRAIT);

        // Finish activity
        mBroadcastActionTrigger.moveTopTaskToBack();

        // Verify that activity brought to front is in originally requested orientation.
        mWmState.waitForValidState(LANDSCAPE_ORIENTATION_ACTIVITY);
        if (!isFixedToUserRotation) {
            mWmState.waitAndAssertLastOrientation(
                        "Fullscreen app requested landscape orientation",
                        SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            final SizeInfo reportedSizes =
                    getLastReportedSizesForActivity(LANDSCAPE_ORIENTATION_ACTIVITY);
            assertEquals("landscape activity should be in landscape",
                    ORIENTATION_LANDSCAPE, reportedSizes.orientation);
            assertTrue("landscape activity should have height < width",
                    reportedSizes.heightDp < reportedSizes.widthDp);
        }
    }

    /**
     * Test that device doesn't change device orientation by app request while in multi-window.
     */
    @Test
    public void testSplitscreenPortraitAppOrientationRequests() throws Exception {
        requestOrientationInSplitScreen(createManagedRotationSession(),
                isDisplayPortrait() ? ROTATION_0 : ROTATION_90, LANDSCAPE_ORIENTATION_ACTIVITY);
    }

    /**
     * Test that device doesn't change device orientation by app request while in multi-window.
     */
    @Test
    public void testSplitscreenLandscapeAppOrientationRequests() throws Exception {
        requestOrientationInSplitScreen(createManagedRotationSession(),
                isDisplayPortrait() ? ROTATION_90 : ROTATION_0, PORTRAIT_ORIENTATION_ACTIVITY);
    }

    /**
     * Launch specified activity in split-screen then rotate the divice, checking orientation
     * should always change by the device rotation.
     */
    private void requestOrientationInSplitScreen(RotationSession rotationSession, int rotation,
            ComponentName activity) throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        // Launch activity and move it to primary split-screen.
        launchActivityInPrimarySplit(LAUNCHING_ACTIVITY);

        // Launch target activity in secondary split-screen.
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        getLaunchActivityBuilder()
                .setTargetActivity(activity)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setNewTask(true)
                .setMultipleTask(true)
                .execute();
        mWmState.assertVisibility(activity, true /* visible */);

        // Rotate the device and it should always rotate regardless orientation app requested.
        rotationSession.set(rotation);
        assertEquals("Split-screen apps shouldn't influence device orientation",
                rotation, mWmState.getRotation());

        // Launch target activity during split-screen again and check orientation still not change.
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        getLaunchActivityBuilder().setMultipleTask(true).setTargetActivity(activity).execute();
        mWmState.computeState(activity);
        mWmState.assertVisibility(activity, true /* visible */);
        assertEquals("Split-screen apps shouldn't influence device orientation",
                rotation, mWmState.getRotation());
    }

    /**
     * Asserts that after rotation, the aspect ratios of display size, metrics, and configuration
     * have flipped.
     */
    private static void assertSizesRotate(SizeInfo rotationA, SizeInfo rotationB,
            boolean skipOrientationCheck) {
        assertEquals(rotationA.displayWidth, rotationA.metricsWidth);
        assertEquals(rotationA.displayHeight, rotationA.metricsHeight);
        assertEquals(rotationB.displayWidth, rotationB.metricsWidth);
        assertEquals(rotationB.displayHeight, rotationB.metricsHeight);

        if (skipOrientationCheck) {
            // All done if we are not doing orientation check.
            return;
        }
        final boolean beforePortrait = rotationA.displayWidth < rotationA.displayHeight;
        final boolean afterPortrait = rotationB.displayWidth < rotationB.displayHeight;
        assertFalse(beforePortrait == afterPortrait);

        final boolean beforeConfigPortrait = rotationA.widthDp < rotationA.heightDp;
        final boolean afterConfigPortrait = rotationB.widthDp < rotationB.heightDp;
        assertEquals(beforePortrait, beforeConfigPortrait);
        assertEquals(afterPortrait, afterConfigPortrait);

        assertEquals(rotationA.smallestWidthDp, rotationB.smallestWidthDp);
    }

    /**
     * Throws an AssertionError if fullscreenSizes has widths/heights (depending on aspect ratio)
     * that are smaller than the dockedSizes.
     */
    private static void assertSizesAreSane(SizeInfo fullscreenSizes, SizeInfo dockedSizes) {
        final boolean isHorizontalDivision =
                fullscreenSizes.displayHeight - dockedSizes.displayHeight >
                fullscreenSizes.displayWidth - dockedSizes.displayWidth;
        if (isHorizontalDivision) {
            assertThat(dockedSizes.displayHeight, lessThan(fullscreenSizes.displayHeight));
            assertThat(dockedSizes.heightDp, lessThan(fullscreenSizes.heightDp));
            assertThat(dockedSizes.metricsHeight, lessThan(fullscreenSizes.metricsHeight));
        } else {
            assertThat(dockedSizes.displayWidth, lessThan(fullscreenSizes.displayWidth));
            assertThat(dockedSizes.widthDp, lessThan(fullscreenSizes.widthDp));
            assertThat(dockedSizes.metricsWidth, lessThan(fullscreenSizes.metricsWidth));
        }
    }

    /**
     * Throws an AssertionError if sizes are different.
     */
    private static void assertSizesAreSame(SizeInfo firstSize, SizeInfo secondSize) {
        assertEquals(firstSize.widthDp, secondSize.widthDp);
        assertEquals(firstSize.heightDp, secondSize.heightDp);
        assertEquals(firstSize.displayWidth, secondSize.displayWidth);
        assertEquals(firstSize.displayHeight, secondSize.displayHeight);
        assertEquals(firstSize.metricsWidth, secondSize.metricsWidth);
        assertEquals(firstSize.metricsHeight, secondSize.metricsHeight);
        assertEquals(firstSize.smallestWidthDp, secondSize.smallestWidthDp);
    }

    private void waitForBroadcastActivityReady(int orientation) {
        mWmState.waitForActivityOrientation(BROADCAST_RECEIVER_ACTIVITY, orientation);
        mWmState.waitForActivityState(BROADCAST_RECEIVER_ACTIVITY, STATE_RESUMED);
    }

    /**
     * Test launching an activity which requests specific UI mode during creation.
     */
    @Test
    public void testLaunchWithUiModeChange() {
        // Launch activity that changes UI mode and handles this configuration change.
        launchActivity(NIGHT_MODE_ACTIVITY);
        mWmState.waitForActivityState(NIGHT_MODE_ACTIVITY, STATE_RESUMED);

        // Check if activity is launched successfully.
        mWmState.assertVisibility(NIGHT_MODE_ACTIVITY, true /* visible */);
        mWmState.assertFocusedActivity("Launched activity should be focused",
                NIGHT_MODE_ACTIVITY);
        mWmState.assertResumedActivity("Launched activity must be resumed", NIGHT_MODE_ACTIVITY);
    }

    @Test
    public void testAppConfigurationMatchesActivityInMultiWindow() throws Exception {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        final ActivitySession activitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(RESIZEABLE_ACTIVITY)
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN));
        putActivityInPrimarySplit(RESIZEABLE_ACTIVITY);
        SizeInfo dockedActivitySizes = getActivitySizeInfo(activitySession);
        SizeInfo applicationSizes = getAppSizeInfo(activitySession);
        assertSizesAreSame(dockedActivitySizes, applicationSizes);

        // Move the activity to fullscreen and check that the size was updated
        separateTestJournal();
        mTaskOrganizer.dismissSplitScreen(true /* primaryOnTop */);
        waitForOrFail("Activity and application configuration must match",
                () -> activityAndAppSizesMatch(activitySession));
        final SizeInfo fullscreenSizes = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);
        applicationSizes = getAppSizeInfo(activitySession);
        assertSizesAreSane(fullscreenSizes, dockedActivitySizes);
        assertSizesAreSame(fullscreenSizes, applicationSizes);

        // Move the activity to docked size again, check if the sizes were updated
        separateTestJournal();
        putActivityInPrimarySplit(RESIZEABLE_ACTIVITY);
        waitForOrFail("Activity and application configuration must match",
                () -> activityAndAppSizesMatch(activitySession));
        dockedActivitySizes = getActivitySizeInfo(activitySession);
        applicationSizes = getAppSizeInfo(activitySession);
        assertSizesAreSane(fullscreenSizes, dockedActivitySizes);
        assertSizesAreSame(dockedActivitySizes, applicationSizes);
    }

    @Test
    public void testAppConfigurationMatchesTopActivityInMultiWindow() throws Exception {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        // Launch initial activity in fullscreen and assert sizes
        final ActivitySession fullscreenActivitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(TEST_ACTIVITY)
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN));
        SizeInfo fullscreenActivitySizes = getActivitySizeInfo(fullscreenActivitySession);
        SizeInfo applicationSizes = getAppSizeInfo(fullscreenActivitySession);
        assertSizesAreSame(fullscreenActivitySizes, applicationSizes);

        // Launch second activity in split-screen and assert that sizes were updated
        separateTestJournal();
        final ActivitySession secondActivitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(RESIZEABLE_ACTIVITY)
                        .setNewTask(true)
                        .setMultipleTask(true));
        putActivityInPrimarySplit(RESIZEABLE_ACTIVITY);
        waitForOrFail("Activity and application configuration must match",
                () -> activityAndAppSizesMatch(secondActivitySession));
        SizeInfo dockedActivitySizes = getActivitySizeInfo(secondActivitySession);
        applicationSizes = getAppSizeInfo(secondActivitySession);
        assertSizesAreSame(dockedActivitySizes, applicationSizes);
        assertSizesAreSane(fullscreenActivitySizes, dockedActivitySizes);

        // Launch third activity in secondary split-screen and assert that sizes were updated
        separateTestJournal();
        final ActivitySession thirdActivitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(RESIZEABLE_ACTIVITY)
                        .setNewTask(true)
                        .setMultipleTask(true));
        putActivityInPrimarySplit(RESIZEABLE_ACTIVITY);
        waitForOrFail("Activity and application configuration must match",
                () -> activityAndAppSizesMatch(thirdActivitySession));
        SizeInfo secondarySplitActivitySizes = getActivitySizeInfo(thirdActivitySession);
        applicationSizes = getAppSizeInfo(thirdActivitySession);
        assertSizesAreSame(secondarySplitActivitySizes, applicationSizes);
        assertSizesAreSane(fullscreenActivitySizes, secondarySplitActivitySizes);
    }

    @Test
    public void testAppConfigurationMatchesActivityInFreeform() throws Exception {
        assumeTrue("Skipping test: no freeform support", supportsFreeform());

        // Launch activity in freeform and assert sizes
        final ActivitySession freeformActivitySession = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(TEST_ACTIVITY)
                        .setWindowingMode(WINDOWING_MODE_FREEFORM));
        SizeInfo freeformActivitySizes = getActivitySizeInfo(freeformActivitySession);
        SizeInfo applicationSizes = getAppSizeInfo(freeformActivitySession);
        assertSizesAreSame(freeformActivitySizes, applicationSizes);
    }

    private boolean activityAndAppSizesMatch(ActivitySession activitySession) {
        final SizeInfo activitySize = activitySession.getConfigInfo().sizeInfo;
        final SizeInfo appSize = activitySession.getAppConfigInfo().sizeInfo;
        return activitySize.equals(appSize);
    }

    private SizeInfo getActivitySizeInfo(ActivitySession activitySession) {
        return activitySession.getConfigInfo().sizeInfo;
    }

    private SizeInfo getAppSizeInfo(ActivitySession activitySession) {
        return activitySession.getAppConfigInfo().sizeInfo;
    }

    private void assumeNotIgnoringOrientation(ComponentName activityName) {
        assumeFalse("Skipping test: display area is ignoring orientation request",
                getWmState().isTaskDisplayAreaIgnoringOrientationRequest(activityName));
    }
}
