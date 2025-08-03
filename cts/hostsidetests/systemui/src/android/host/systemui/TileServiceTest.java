/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.host.systemui;

import com.android.tradefed.util.RunUtil;

import com.android.compatibility.common.util.ApiTest;

/**
 * Tests the lifecycle of a TileService by adding/removing it and opening/closing
 * the notification/settings shade through adb commands.
 */
public class TileServiceTest extends BaseTileServiceTest {

    private static final String SERVICE = "TestTileService";

    public static final String ACTION_START_ACTIVITY =
            "android.sysui.testtile.action.START_ACTIVITY";

    public static final String ACTION_START_ACTIVITY_WITH_PENDING_INTENT =
            "android.sysui.testtile.action.START_ACTIVITY_WITH_PENDING_INTENT";

    public static final String SHELL_BROADCAST_COMMAND = "am broadcast -a ";

    public static final String START_ACTIVITY_AND_COLLAPSE =
            SHELL_BROADCAST_COMMAND + ACTION_START_ACTIVITY;

    public TileServiceTest() {
        super(SERVICE);
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#onTileAdded"})
    public void testAddTile() throws Exception {
        if (!supported()) return;
        addTile();
        // Verify that the service starts up and gets a onTileAdded callback.
        assertTrue(waitFor("onCreate"));
        assertTrue(waitFor("onTileAdded"));
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#onTileAdded",
            "android.service.quicksettings.TileService#onTileRemoved"})
    public void testRemoveTile() throws Exception {
        if (!supported()) return;
        addTile();
        // Verify that the service starts up and gets a onTileAdded callback.
        assertTrue(waitFor("onCreate"));
        assertTrue(waitFor("onTileAdded"));

        remTile();
        assertTrue(waitFor("onTileRemoved"));
    }
    @ApiTest(apis = {"android.service.quicksettings.TileService#onStartListening",
            "android.service.quicksettings.TileService#onStopListening"})
    public void testListeningSettings() throws Exception {
        if (!supported()) return;
        addTile();

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));
        // Collapse the shade and make sure the listening ends.
        collapse();
        assertTrue(waitFor("onStopListening"));
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#showDialog"})
    public void testCantAddDialog() throws Exception {
        if (!supported()) return;
        addTile();

        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Try to open a dialog, verify it doesn't happen.
        showDialog();
        assertTrue(waitFor("handleShowDialog"));
        assertTrue(waitFor("onWindowAddFailed"));
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#onClick"})
    public void testClick() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onClick"));

        // Verify the state that gets dumped during a click.
        // Device is expected to be unlocked and unsecure during CTS.
        // The unlock callback should be triggered immediately.
        assertTrue(waitFor("is_secure_false"));
        assertTrue(waitFor("is_locked_false"));
        assertTrue(waitFor("unlockAndRunRun"));
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#showDialog"})
    public void testClickAndShowDialog() throws Exception {
        if (!supported()) return;
        addTile();

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onClick"));

        // Try to open a dialog, verify it doesn't happen.
        showDialog();
        assertTrue(waitFor("handleShowDialog"));
        assertTrue(waitFor("onWindowFocusChanged_true"));
    }

    @ApiTest(apis = {
            "android.service.quicksettings.TileService#startActivityAndCollapse"})
    public void testStartActivityDoesNotLaunchPendingIntentWithoutClick() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Trigger the startActivityAndCollapse(pendingIntent) call and verify we are not
        // taken to a new activity
        getDevice().executeShellCommand(
                SHELL_BROADCAST_COMMAND + ACTION_START_ACTIVITY_WITH_PENDING_INTENT);
        assertFalse((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {"android.service.quicksettings.Tile#setActivityLaunchForClick"})
    public void testSetPendingIntentDoesNotStartActivityWithoutClick() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        setActivityForLaunch();

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Verify that the activity is not launched
        assertFalse((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {"android.service.quicksettings.Tile#setActivityLaunchForClick"})
    public void testSetPendingIntentStartsActivityWithClick() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Set the pending intent with a valid activity
        setActivityForLaunch();
        assertTrue(waitFor("handleSetPendingIntent"));

        // Click on the tile and verify the onClick is not called.
        clickTile();
        assertFalse(waitFor("onClick"));

        // Verify that the activity is launched
        assertTrue((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {"android.service.quicksettings.Tile#setActivityLaunchForClick"})
    public void testSetPendingIntentPersistsAfterShadeIsClosed() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Set the pending intent with a valid activity
        setActivityForLaunch();
        assertTrue(waitFor("handleSetPendingIntent"));
        RunUtil.getDefault().sleep(500);

        // Collapse the shade and make sure the listening ends.
        collapse();
        assertTrue(waitFor("onStopListening"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify the onClick is not called.
        clickTile();
        assertFalse(waitFor("onClick"));

        // Verify that the activity is launched
        assertTrue((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {"android.service.quicksettings.Tile#setActivityLaunchForClick"})
    public void testSetNullPendingIntentDoesNotStartActivity() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Set the pending intent with a valid activity
        setActivityForLaunch();
        assertTrue(waitFor("handleSetPendingIntent"));
        RunUtil.getDefault().sleep(500);

        // Set null or "delete" the current pending intent
        setNullPendingIntent();
        assertTrue(waitFor("handleSetPendingIntent"));
        RunUtil.getDefault().sleep(500);

        // Click on the tile and verify the onClick is called.
        clickTile();
        assertTrue(waitFor("onClick"));

        // Verify that the activity is not launched
        assertFalse((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {
            "android.service.quicksettings.TileService#startActivityAndCollapse"})
    public void testClickAndStartActivity() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onClick"));

        startActivityWithPendingIntent();
        assertTrue(waitFor("handleStartActivityWithPendingIntent"));

        // Verify that the activity is launched
        assertTrue(waitFor("TestActivity#onResume"));
    }
}
