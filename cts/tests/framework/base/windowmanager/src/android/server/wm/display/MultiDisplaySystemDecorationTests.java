/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.display;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.server.wm.BarTestUtils.assumeHasBars;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.HOME_ACTIVITY;
import static android.server.wm.app.Components.SECONDARY_HOME_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_HOME_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_SECONDARY_HOME_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TEST_LIVE_WALLPAPER_SERVICE;
import static android.server.wm.app.Components.TestLiveWallpaperKeys.COMPONENT;
import static android.server.wm.app.Components.TestLiveWallpaperKeys.ENGINE_DISPLAY_ID;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.platform.test.annotations.Presubmit;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.TestJournalProvider;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.DisplayContent;
import android.server.wm.WindowManagerState.WindowState;

import com.android.compatibility.common.util.TestUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:MultiDisplaySystemDecorationTests
 *
 * This tests that verify the following should not be run for OEM device verification:
 * Wallpaper added if display supports system decorations (and not added otherwise)
 * Navigation bar is added if display supports system decorations (and not added otherwise)
 * Secondary Home is shown if display supports system decorations (and not shown otherwise)
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplaySystemDecorationTests extends MultiDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsSystemDecorsOnSecondaryDisplays());
    }

    // Wallpaper related tests
    /**
     * Test WallpaperService.Engine#getDisplayContext can work on secondary display.
     */
    @Test
    public void testWallpaperGetDisplayContext() throws Exception {
        assumeTrue(supportsLiveWallpaper());

        final ChangeWallpaperSession wallpaperSession = createManagedChangeWallpaperSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();

        TestJournalContainer.start();

        final DisplayContent newDisplay = virtualDisplaySession
                .setSimulateDisplay(true).setShowSystemDecorations(true).createDisplay();

        wallpaperSession.setWallpaperComponent(TEST_LIVE_WALLPAPER_SERVICE);
        final String TARGET_ENGINE_DISPLAY_ID = ENGINE_DISPLAY_ID + newDisplay.mId;
        final TestJournalProvider.TestJournal journal = TestJournalContainer.get(COMPONENT);
        TestUtils.waitUntil("Waiting for wallpaper engine bounded", 5 /* timeoutSecond */,
                () -> journal.extras.getBoolean(TARGET_ENGINE_DISPLAY_ID));
    }

    /**
     * Tests that wallpaper shows on secondary displays.
     */
    @Test
    public void testWallpaperShowOnSecondaryDisplays()  {
        assumeTrue(supportsWallpaper());

        final ChangeWallpaperSession wallpaperSession = createManagedChangeWallpaperSession();

        final DisplayContent untrustedDisplay = createManagedExternalDisplaySession()
                .setPublicDisplay(true).setShowSystemDecorations(true).createVirtualDisplay();

        final DisplayContent decoredSystemDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).setShowSystemDecorations(true).createDisplay();

        final Bitmap tmpWallpaper = wallpaperSession.getTestBitmap();
        wallpaperSession.setImageWallpaper(tmpWallpaper);

        assertTrue("Wallpaper must be displayed on system owned display with system decor flag",
                mWmState.waitForWithAmState(
                        state -> isWallpaperOnDisplay(state, decoredSystemDisplay.mId),
                        "wallpaper window to show"));

        assertFalse("Wallpaper must not be displayed on the untrusted display",
                isWallpaperOnDisplay(mWmState, untrustedDisplay.mId));
    }

    private boolean isWallpaperOnDisplay(WindowManagerState windowManagerState, int displayId) {
        return windowManagerState.getMatchingWindowType(TYPE_WALLPAPER).stream().anyMatch(
                w -> w.getDisplayId() == displayId);
    }

    // Navigation bar related tests
    // TODO(115978725): add runtime sys decor change test once we can do this.
    /**
     * Test that navigation bar should show on display with system decoration.
     */
    @Test
    public void testNavBarShowingOnDisplayWithDecor() {
        assumeHasBars();
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).setShowSystemDecorations(true).createDisplay();

        mWmState.waitAndAssertNavBarShownOnDisplay(newDisplay.mId);
    }

    /**
     * Test that navigation bar should not show on display without system decoration.
     */
    @Test
    public void testNavBarNotShowingOnDisplayWithoutDecor() {
        assumeHasBars();
        // Wait for system decoration showing and record current nav states.
        mWmState.waitForHomeActivityVisible();
        final List<WindowState> expected = mWmState.getAllNavigationBarStates();

        createManagedVirtualDisplaySession().setSimulateDisplay(true)
                .setShowSystemDecorations(false).createDisplay();

        waitAndAssertNavBarStatesAreTheSame(expected);
    }

    /**
     * Test that navigation bar should not show on private display even if the display
     * supports system decoration.
     */
    @Test
    public void testNavBarNotShowingOnPrivateDisplay() {
        assumeHasBars();
        // Wait for system decoration showing and record current nav states.
        mWmState.waitForHomeActivityVisible();
        final List<WindowState> expected = mWmState.getAllNavigationBarStates();

        createManagedExternalDisplaySession().setPublicDisplay(false)
                .setShowSystemDecorations(true).createVirtualDisplay();

        waitAndAssertNavBarStatesAreTheSame(expected);
    }

    private void waitAndAssertNavBarStatesAreTheSame(List<WindowState> expected) {
        // This is used to verify that we have nav bars shown on the same displays
        // as before the test.
        //
        // The strategy is:
        // Once a display with system ui decor support is created and a nav bar shows on the
        // display, go back to verify whether the nav bar states are unchanged to verify that no nav
        // bars were added to a display that was added before executing this method that shouldn't
        // have nav bars (i.e. private or without system ui decor).
        try (final VirtualDisplaySession secondDisplaySession = new VirtualDisplaySession()) {
            final DisplayContent supportsSysDecorDisplay = secondDisplaySession
                    .setSimulateDisplay(true).setShowSystemDecorations(true).createDisplay();
            mWmState.waitAndAssertNavBarShownOnDisplay(supportsSysDecorDisplay.mId);
            // This display has finished his task. Just close it.
        }

        mWmState.computeState();
        final List<WindowState> result = mWmState.getAllNavigationBarStates();

        assertEquals("The number of nav bars should be the same", expected.size(), result.size());

        mWmState.getDisplays().forEach(displayContent -> {
            List<WindowState> navWindows = expected.stream().filter(ws ->
                    ws.getDisplayId() == displayContent.mId)
                    .collect(Collectors.toList());

            mWmState.waitAndAssertNavBarShownOnDisplay(displayContent.mId, navWindows.size());
        });
    }

    // Secondary Home related tests
    /**
     * Tests launching a home activity on virtual display without system decoration support.
     */
    @Test
    public void testLaunchHomeActivityOnSecondaryDisplayWithoutDecorations() {
        createManagedHomeActivitySession(SECONDARY_HOME_ACTIVITY);

        // Create new virtual display without system decoration support.
        final DisplayContent newDisplay = createManagedExternalDisplaySession()
                .createVirtualDisplay();

        // Secondary home activity can't be launched on the display without system decoration
        // support.
        assertEquals(
                "No stacks on newly launched virtual display", 0, newDisplay.getRootTasks().size());
    }

    /** Tests launching a home activity on untrusted virtual display. */
    @Test
    public void testLaunchHomeActivityOnUntrustedVirtualSecondaryDisplay() {
        createManagedHomeActivitySession(SECONDARY_HOME_ACTIVITY);

        // Create new virtual display with system decoration support flag.
        final DisplayContent newDisplay = createManagedExternalDisplaySession()
                .setPublicDisplay(true).setShowSystemDecorations(true).createVirtualDisplay();

        // Secondary home activity can't be launched on the untrusted virtual display.
        assertEquals("No stacks on untrusted virtual display", 0, newDisplay.getRootTasks().size());
    }

    /**
     * Tests launching a single instance home activity on virtual display with system decoration
     * support.
     */
    @Test
    public void testLaunchSingleHomeActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(SINGLE_HOME_ACTIVITY);

        // If default home doesn't support multi-instance, default secondary home activity
        // should be automatically launched on the new display.
        assertSecondaryHomeResumedOnNewDisplay(getDefaultSecondaryHomeComponent());
    }

    /**
     * Tests launching a single instance home activity with SECONDARY_HOME on virtual display with
     * system decoration support.
     */
    @Test
    public void testLaunchSingleSecondaryHomeActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(SINGLE_SECONDARY_HOME_ACTIVITY);

        // If provided secondary home doesn't support multi-instance, default secondary home
        // activity should be automatically launched on the new display.
        assertSecondaryHomeResumedOnNewDisplay(getDefaultSecondaryHomeComponent());
    }

    /**
     * Tests sending a secondary home intent to a virtual display with system decoration support.
     * The currently configured secondary home activity should be resumed.
     */
    @Test
    public void testSendSecondaryHomeIntentActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(SINGLE_SECONDARY_HOME_ACTIVITY);

        // Create new simulated display with system decoration support.
        final DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setShowSystemDecorations(true)
                .createDisplay();
        assertSecondaryHomeResumedOnDisplay(getDefaultSecondaryHomeComponent(), display.mId);

        // Launch a random activity on the display.
        final VirtualDisplayLauncher virtualLauncher =
                mObjectTracker.manage(new VirtualDisplayLauncher());
        virtualLauncher.launchActivityOnDisplay(TEST_ACTIVITY, display);
        waitAndAssertActivityStateOnDisplay(TEST_ACTIVITY, STATE_RESUMED, display.mId,
                "Top activity must be on secondary display");

        // Send a SECONDARY_HOME intent to that display and check that the home activity is resumed.
        sendHomeIntentToDisplay(Intent.CATEGORY_SECONDARY_HOME, display.mId);
        assertSecondaryHomeResumedOnDisplay(getDefaultSecondaryHomeComponent(), display.mId);
    }

    /**
     * Tests sending a primary home intent to a virtual display with system decoration support.
     * The currently configured secondary home activity should be resumed because the display does
     * not support primary home.
     */
    @Test
    public void testSendPrimaryHomeIntentActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(SINGLE_SECONDARY_HOME_ACTIVITY);

        // Create new simulated display with system decoration support.
        final DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setShowSystemDecorations(true)
                .createDisplay();
        assertSecondaryHomeResumedOnDisplay(getDefaultSecondaryHomeComponent(), display.mId);

        // Launch a random activity on the display.
        final VirtualDisplayLauncher virtualLauncher =
                mObjectTracker.manage(new VirtualDisplayLauncher());
        virtualLauncher.launchActivityOnDisplay(TEST_ACTIVITY, display);
        waitAndAssertActivityStateOnDisplay(TEST_ACTIVITY, STATE_RESUMED, display.mId,
                "Top activity must be on secondary display");

        // Send a HOME intent to that display and check that the home activity is resumed.
        // The secondary home activity should be resumed because the target display does not support
        // primary home but it supports secondary home.
        sendHomeIntentToDisplay(Intent.CATEGORY_HOME, display.mId);
        assertSecondaryHomeResumedOnDisplay(getDefaultSecondaryHomeComponent(), display.mId);
    }

    /**
     * Tests launching a multi-instance home activity on virtual display with system decoration
     * support.
     */
    @Test
    public void testLaunchHomeActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(HOME_ACTIVITY);

        // If default home doesn't have SECONDARY_HOME category, default secondary home
        // activity should be automatically launched on the new display.
        assertSecondaryHomeResumedOnNewDisplay(getDefaultSecondaryHomeComponent());
    }

    /**
     * Tests launching a multi-instance home activity with SECONDARY_HOME on virtual display with
     * system decoration support.
     */
    @Test
    public void testLaunchSecondaryHomeActivityOnDisplayWithDecorations() {
        createManagedHomeActivitySession(SECONDARY_HOME_ACTIVITY);
        boolean useSystemProvidedLauncher = mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_useSystemProvidedLauncherForSecondary",
                        "bool", "android"));

        if (useSystemProvidedLauncher) {
            // Default secondary home activity should be automatically launched on the new display
            // if forced by the config.
            assertSecondaryHomeResumedOnNewDisplay(getDefaultSecondaryHomeComponent());
        } else {
            // Provided secondary home activity should be automatically launched on the new display.
            assertSecondaryHomeResumedOnNewDisplay(SECONDARY_HOME_ACTIVITY);
        }
    }

    private void sendHomeIntentToDisplay(String category, int displayId) {
        Intent homeIntent = createHomeIntent(category);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        mContext.startActivity(homeIntent, options.toBundle());
    }

    private void assertSecondaryHomeResumedOnNewDisplay(ComponentName homeComponentName) {
        // Create new simulated display with system decoration support.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setShowSystemDecorations(true)
                .createDisplay();

        assertSecondaryHomeResumedOnDisplay(homeComponentName, newDisplay.mId);
    }

    private void assertSecondaryHomeResumedOnDisplay(ComponentName homeComponentName,
            int displayId) {
        waitAndAssertActivityStateOnDisplay(homeComponentName, STATE_RESUMED,
                displayId, "Activity launched on secondary display must be resumed");

        tapOnDisplayCenter(displayId);
        assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                mWmState.getFrontRootTaskActivityType(displayId));
    }
}
