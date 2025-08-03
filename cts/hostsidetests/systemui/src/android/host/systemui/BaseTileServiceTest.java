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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;

public class BaseTileServiceTest extends DeviceTestCase {
    // Constants for generating commands below.
    protected static final String PACKAGE = "android.systemui.cts";
    private static final String ACTION_SHOW_DIALOG = "android.sysui.testtile.action.SHOW_DIALOG";
    private static final String ACTION_START_ACTIVITY_WITH_PENDING_INTENT =
            "android.sysui.testtile.action.START_ACTIVITY_WITH_PENDING_INTENT";
    public static final String ACTION_SET_PENDING_INTENT =
            "android.sysui.testtile.action.SET_PENDING_INTENT";
    public static final String ACTION_SET_NULL_PENDING_INTENT =
            "android.sysui.testtile.action.SET_NULL_PENDING_INTENT";

    // Commands used on the device.
    private static final String ADD_TILE = "cmd statusbar add-tile ";
    private static final String REM_TILE = "cmd statusbar remove-tile ";
    private static final String CLICK_TILE = "cmd statusbar click-tile ";

    private static final String OPEN_NOTIFICATIONS = "cmd statusbar expand-notifications";
    private static final String OPEN_SETTINGS = "cmd statusbar expand-settings";
    private static final String COLLAPSE = "cmd statusbar collapse";

    private static final String SHELL_BROADCAST_COMMAND = "am broadcast -a ";
    private static final String SHOW_DIALOG = SHELL_BROADCAST_COMMAND + ACTION_SHOW_DIALOG;
    private static final String START_ACTIVITY_WITH_PENDING_INTENT =
            SHELL_BROADCAST_COMMAND + ACTION_START_ACTIVITY_WITH_PENDING_INTENT;
    private static final String SET_PENDING_INTENT =
            SHELL_BROADCAST_COMMAND + ACTION_SET_PENDING_INTENT;
    private static final String SET_NULL_PENDING_INTENT =
            SHELL_BROADCAST_COMMAND + ACTION_SET_NULL_PENDING_INTENT;

    public static final String REQUEST_SUPPORTED = "cmd statusbar check-support";
    public static final String TEST_PREFIX = "TileTest_";

    // Time between checks for logs we expect.
    private static final long CHECK_DELAY = 500;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 30;

    private final String mService;
    private final String mComponent;

    public BaseTileServiceTest() {
        this("");
    }

    public BaseTileServiceTest(String service) {
        mService = service;
        mComponent = PACKAGE + "/." + mService;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        clearLogcat();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (!supported()) return;
        collapse();
        remTile();
        // Try to wait for a onTileRemoved.
        waitFor("onTileRemoved");
    }

    protected void showDialog() throws Exception {
        execute(SHOW_DIALOG);
    }

    protected void startActivityWithPendingIntent() throws Exception {
        execute(START_ACTIVITY_WITH_PENDING_INTENT);
    }

    protected void setActivityForLaunch() throws Exception {
        execute(SET_PENDING_INTENT);
    }

    protected void setNullPendingIntent() throws Exception {
        execute(SET_NULL_PENDING_INTENT);
    }

    protected void addTile() throws Exception {
        execute(ADD_TILE + mComponent);
    }

    protected void remTile() throws Exception {
        execute(REM_TILE + mComponent);
    }

    protected void clickTile() throws Exception {
        execute(CLICK_TILE + mComponent);
    }

    protected void openNotifications() throws Exception {
        execute(OPEN_NOTIFICATIONS);
    }

    protected void openSettings() throws Exception {
        execute(OPEN_SETTINGS);
    }

    protected void collapse() throws Exception {
        execute(COLLAPSE);
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

    protected final void clearLogcat() throws DeviceNotAvailableException {
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
        return !features.contains("android.hardware.type.television") &&
                !features.contains("android.hardware.type.watch");
    }
}
