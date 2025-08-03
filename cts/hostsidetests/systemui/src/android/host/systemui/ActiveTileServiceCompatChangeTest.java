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

package android.host.systemui;

import com.android.tradefed.util.RunUtil;
import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.Set;

public class ActiveTileServiceCompatChangeTest extends CompatChangeGatingTestCase {

    // Constants for generating commands below.
    private static final String PACKAGE = "android.systemui.cts";

    // Commands used on the device.
    private static final String ADD_TILE = "cmd statusbar add-tile ";
    private static final String REM_TILE = "cmd statusbar remove-tile ";

    public static final String REQUEST_SUPPORTED = "cmd statusbar check-support";
    public static final String TEST_PREFIX = "TileTest_";

    // Time between checks for logs we expect.
    private static final long CHECK_DELAY = 500;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 30;

    private final String mService = "TestActiveTileService";
    private final String mComponent = PACKAGE + "/." + mService;

    private static final long REQUEST_LISTENING_MUST_MATCH_PACKAGE = 172251878L;

    private static final String EXTRA_BAD_PACKAGE = "android.systemui.cts.EXTRA_BAD_PACKAGE";
    private static final String ACTION_REQUEST_LISTENING =
            "android.sysui.testtile.REQUEST_LISTENING";

    private static final String REQUEST_LISTENING = "am broadcast -a " + ACTION_REQUEST_LISTENING
            + " " + PACKAGE;

    private static final String REQUEST_LISTENING_BAD = "am broadcast -a "
            + ACTION_REQUEST_LISTENING + " --ez " + EXTRA_BAD_PACKAGE + " true " + PACKAGE;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        clearLogcat();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (!supported()) return;
        remTile();
        // Try to wait for a onTileRemoved.
        waitFor("onTileRemoved");
    }

    public void testRequestListening_changeEnabled() throws Exception {
        runTest(true);
    }

    public void testRequestListening_changeDisabled() throws Exception {
        runTest(false);
    }

    public void testRequestListeningBadPackage_changeEnabled_SecurityException() throws Exception {
        if (!supported()) return;
        Set<Long> enabledSet = Set.of(REQUEST_LISTENING_MUST_MATCH_PACKAGE);
        Set<Long> disabledSet = Set.of();

        setCompatConfig(enabledSet, disabledSet, PACKAGE);

        addTile();
        waitFor("onDestroy");

        // Request the listening state but use a bad component name (not in the same package)
        getDevice().executeShellCommand(REQUEST_LISTENING_BAD);
        assertTrue(waitFor("SecurityException"));
        resetCompatChanges(Set.of(REQUEST_LISTENING_MUST_MATCH_PACKAGE), PACKAGE);
    }

    @Override
    protected void setCompatConfig(Set<Long> enabledChanges, Set<Long> disabledChanges,
            String packageName) throws DeviceNotAvailableException {
        for (Long enabledChange : enabledChanges) {
            runCommand("am compat enable --no-kill " + enabledChange + " " + packageName);
        }
        for (Long disabledChange : disabledChanges) {
            runCommand("am compat disable --no-kill" + disabledChange + " " + packageName);
        }
    }

    @Override
    protected void resetCompatChanges(Set<Long> changes, String packageName)
            throws DeviceNotAvailableException {
        for (Long change : changes) {
            runCommand("am compat reset --no-kill" + change + " " + packageName);
        }
    }

    private void runTest(boolean enabled) throws Exception {
        if (!supported()) return;
        Set<Long> enabledSet = enabled ? Set.of(REQUEST_LISTENING_MUST_MATCH_PACKAGE) : Set.of();
        Set<Long> disabledSet = enabled ? Set.of() : Set.of(REQUEST_LISTENING_MUST_MATCH_PACKAGE);

        setCompatConfig(enabledSet, disabledSet, PACKAGE);

        final long configId = getClass().getCanonicalName().hashCode();

        try {
            executeRequestListeningTest();
        } finally {
            resetCompatChanges(Set.of(REQUEST_LISTENING_MUST_MATCH_PACKAGE), PACKAGE);
        }
    }

    private void executeRequestListeningTest() throws Exception {
        addTile();
        waitFor("onDestroy");

        // Request the listening state and verify that it gets an onStartListening.
        getDevice().executeShellCommand(REQUEST_LISTENING);
        assertTrue(waitFor("requestListeningState"));
        assertTrue(waitFor("onStartListening"));
    }

    private void addTile() throws Exception {
        execute(ADD_TILE + mComponent);
    }

    private void remTile() throws Exception {
        execute(REM_TILE + mComponent);
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
