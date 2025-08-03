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

package android.host.systemui;

import static android.host.systemui.TileServiceTest.START_ACTIVITY_AND_COLLAPSE;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.compatibility.common.util.ApiTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.util.RunUtil;

import java.util.Set;

public class CompatChangesTileServiceTest extends CompatChangeGatingTestCase {

    // Constants for generating commands below.
    private static final String PACKAGE = "android.systemui.cts";

    // Commands used on the device.
    private static final String ADD_TILE = "cmd statusbar add-tile ";
    private static final String REM_TILE = "cmd statusbar remove-tile ";
    private static final String OPEN_SETTINGS = "cmd statusbar expand-settings";

    public static final String REQUEST_SUPPORTED = "cmd statusbar check-support";
    public static final String TEST_PREFIX = "TileTest_";

    // Time between checks for logs we expect.
    private static final long CHECK_DELAY = 500;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 30;

    private final String mService = "TestTileService";
    private final String mComponent = PACKAGE + "/." + mService;

    private static final long START_ACTIVITY_NEEDS_PENDING_INTENT = 241766793L;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        clearLogcat();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        remTile();
        // Try to wait for a onTileRemoved.
        waitFor("onTileRemoved");

        resetCompatChanges(Set.of(START_ACTIVITY_NEEDS_PENDING_INTENT), PACKAGE);
        RunUtil.getDefault().sleep(100);
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#startActivityAndCollapse(Intent)"})
    public void testStartActivityWithIntent_requiresPendingIntent_ThrowsException()
            throws Exception {
        if (!supported()) return;
        Set<Long> enabledSet = Set.of(START_ACTIVITY_NEEDS_PENDING_INTENT);
        Set<Long> disabledSet = Set.of();

        setCompatConfig(enabledSet, disabledSet, PACKAGE);
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Trigger the startActivityAndCollapse call and verify calling the method throws
        // an UnsupportedOperationException.
        getDevice().executeShellCommand(START_ACTIVITY_AND_COLLAPSE);
        assertTrue(waitFor("handleStartActivity"));
        assertTrue(waitFor("UnsupportedOperationException"));

        // Verify that the activity is not launched
        assertFalse((waitFor("TestActivity#onResume")));
    }

    @ApiTest(apis = {"android.service.quicksettings.TileService#startActivityAndCollapse(Intent)"})
    public void testStartActivityWithIntent_doesNotRequirePendingIntent_lauchesSuccessfully()
            throws Exception {
        if (!supported()) return;
        Set<Long> enabledSet = Set.of();
        Set<Long> disabledSet = Set.of(START_ACTIVITY_NEEDS_PENDING_INTENT);

        setCompatConfig(enabledSet, disabledSet, PACKAGE);
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Trigger the startActivityAndCollapse call and verify calling the method does not throw
        // an UnsupportedOperationException.
        getDevice().executeShellCommand(START_ACTIVITY_AND_COLLAPSE);
        assertTrue(waitFor("handleStartActivity"));

        // Verify that the activity is launched
        assertTrue((waitFor("TestActivity#onResume")));
    }

    private void addTile() throws Exception {
        execute(ADD_TILE + mComponent);
    }

    private void remTile() throws Exception {
        execute(REM_TILE + mComponent);
    }

    protected void openSettings() throws Exception {
        execute(OPEN_SETTINGS);
    }

    private void execute(String cmd) throws Exception {
        getDevice().executeShellCommand(cmd);
        // All of the status bar commands tend to have animations associated
        // everything seems to be happier if you give them time to finish.
        RunUtil.getDefault().sleep(100);
    }

    protected boolean waitFor(String str) throws DeviceNotAvailableException, InterruptedException {
        final String searchStr = TEST_PREFIX + str;
        int ct = 0;
        while (!hasLog(searchStr) && (ct++ < CHECK_RETRIES)) {
            RunUtil.getDefault().sleep(CHECK_DELAY);
        }
        return hasLog(searchStr);
    }

    protected boolean hasLog(String str) throws DeviceNotAvailableException {
        String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d", mService + ":I",
                "*:S");
        return logs.contains(str);
    }

    private void clearLogcat() throws DeviceNotAvailableException {
        getDevice().executeAdbCommand("logcat", "-c");
    }

    protected boolean supported() throws DeviceNotAvailableException {
        return supportedHardware() && supportedSoftware();
    }

    private boolean supportedSoftware() throws DeviceNotAvailableException {
        String supported = getDevice().executeShellCommand(REQUEST_SUPPORTED);
        return Boolean.parseBoolean(supported.trim());
    }

    private boolean supportedHardware() throws DeviceNotAvailableException {
        String features = getDevice().executeShellCommand("pm list features");
        return !features.contains("android.hardware.type.television")
                && !features.contains("android.hardware.type.watch");
    }
}
