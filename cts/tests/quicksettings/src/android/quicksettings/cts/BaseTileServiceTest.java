/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.quicksettings.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.service.quicksettings.TileService;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.systemui.dump.nano.SystemUIProtoDump;
import com.android.systemui.qs.nano.QsTileState;
import com.android.systemui.util.nano.ComponentNameProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public abstract class BaseTileServiceTest {

    protected abstract String getTag();
    protected abstract String getComponentName();
    protected abstract String getTileServiceClassName();
    protected Context mContext;

    static final String PROTO_DUMP_COMMAND =
            "dumpsys statusbar QSTileHost --proto";

    // Time between checks for state we expect.
    protected static final long CHECK_DELAY = 250;
    // Number of times to check before failing. This is set so the maximum wait time is about 4s,
    // as some tests were observed to take around 3s.
    protected static final long CHECK_RETRIES = 15;
    // Timeout to wait for launcher
    protected static final long TIMEOUT = 8000;

    protected TileService mTileService;
    private Intent homeIntent;
    private String mLauncherPackage;

    @Before
    public void setUp() throws Exception {
        assumeTrue(TileService.isQuickSettingsSupported());
        CurrentTestState.reset();
        mContext = InstrumentationRegistry.getContext();
        homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        mLauncherPackage = mContext.getPackageManager().resolveActivity(homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;

        // Wait for home
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        device.wait(Until.hasObject(By.pkg(mLauncherPackage).depth(0)), TIMEOUT);
    }

    @After
    public void tearDown() throws Exception {
        expandSettings(false);
        toggleServiceAccess(getComponentName(), false);
        waitForTileAdded(false);
        CurrentTestState.reset();
    }

    protected void startTileService() throws Exception {
        toggleServiceAccess(getComponentName(), true);
        waitForTileAdded(true); // wait for service to be bound and added
    }

    protected void toggleServiceAccess(String componentName, boolean on) throws Exception {
        String command = " cmd statusbar " + (on ? "add-tile " : "remove-tile ")
                + componentName;

        executeShellCommand(command);
    }

    public String executeShellCommand(String command) throws IOException {
        Log.i(getTag(), "Shell command: " + command);
        try {
            return SystemUtil.runShellCommand(getInstrumentation(), command);
        } catch (IOException e) {
            //bubble it up
            Log.e(getTag(), "Error running shell command: " + command);
            throw new IOException(e);
        }
    }

    protected void expandSettings(boolean expand) throws Exception {
        executeShellCommand(" cmd statusbar " + (expand ? "expand-settings" : "collapse"));
        Thread.sleep(600); // wait for animation
    }

    protected void initializeAndListen() throws Exception {
        startTileService();
        expandSettings(true);
        waitForListening(true);
    }

    /**
     * Find a line containing {@code label} in {@code lines}.
     */
    protected String findLine(String[] lines, CharSequence label) {
        for (String line: lines) {
            if (line.contains(label)) {
                return line;
            }
        }
        return null;
    }

    protected final QsTileState findTileState() {
        QsTileState[] tiles = getTilesStates();
        for (int i = 0; i < tiles.length; i++) {
            QsTileState tile = tiles[i];
            if (tile.hasComponentName()) {
                ComponentNameProto componentName = tile.getComponentName();
                ComponentName c = ComponentName.createRelative(
                        componentName.packageName, componentName.className);
                if (c.flattenToString().equals(getComponentName())) {
                    return tile;
                }
            }
        }
        return null;
    }

    protected final void waitForTileAdded(boolean state) throws InterruptedException {
        int ct = 0;
        while (CurrentTestState.isTileAdded() != state && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertEquals(state, CurrentTestState.isTileAdded());
        if (state) {
            assertEquals(getTileServiceClassName(), CurrentTestState.getTileServiceClass());
        }
    }

    protected final void waitForListening(boolean state) throws InterruptedException {
        int ct = 0;
        while (CurrentTestState.isListening() != state && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertEquals(state, CurrentTestState.isListening());
        mTileService = CurrentTestState.getCurrentInstance();
    }

    private QsTileState[] getTilesStates() {
        try {
            return SystemUIProtoDump.parseFrom(getProtoDump()).tiles;
        } catch (InvalidProtocolBufferNanoException e) {
            throw new IllegalStateException("Couldn't parse proto dump", e);
        }
    }

    private byte[] getProtoDump() {
        try {
            ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(PROTO_DUMP_COMMAND);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
