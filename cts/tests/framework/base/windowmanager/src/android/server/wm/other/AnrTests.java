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
 * limitations under the License.
 */

package android.server.wm.other;

import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.app.Components.HOST_ACTIVITY;
import static android.server.wm.app.Components.UNRESPONSIVE_ACTIVITY;
import static android.server.wm.app.Components.UnresponsiveActivity;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_CREATE_DELAY_MS;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_KEYDOWN_DELAY_MS;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_MOTIONEVENT_DELAY_MS;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.BuildUtils;
import android.server.wm.WindowManagerState;
import android.server.wm.app.Components.RenderService;
import android.server.wm.settings.SettingsSession;
import android.util.EventLog;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Test scenarios that lead to "Application Not
 * Responding" (ANR) dialog being shown.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:AnrTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class AnrTests extends ActivityManagerTestBase {
    private static final String TAG = "AnrTests";
    private LogSeparator mLogSeparator;
    private SettingsSession<Integer> mHideDialogSetting;

    @Before
    public void setup() throws Exception {
        super.setUp();
        assumeTrue(mAtm.currentUiModeSupportsErrorDialogs(mContext));

        mLogSeparator = separateLogs(); // add a new separator for logs
        mHideDialogSetting = new SettingsSession<>(
                Settings.Global.getUriFor(Settings.Global.HIDE_ERROR_DIALOGS),
                Settings.Global::getInt, Settings.Global::putInt);
        mHideDialogSetting.set(0);
    }

    @After
    public void teardown() {
        if (mHideDialogSetting != null) mHideDialogSetting.close();
        stopTestPackage(UNRESPONSIVE_ACTIVITY.getPackageName());
        stopTestPackage(HOST_ACTIVITY.getPackageName());
    }

    @Test
    public void slowOnCreateWithKeyEventTriggersAnr() {
        startUnresponsiveActivity(EXTRA_ON_CREATE_DELAY_MS, false /* waitForCompletion */,
                UNRESPONSIVE_ACTIVITY);
        // wait for app to be focused
        mWmState.waitAndAssertAppFocus(UNRESPONSIVE_ACTIVITY.getPackageName(),
                2000 /* waitTime_ms */);
        // wait for input manager to get the new focus app. This sleep can be removed once we start
        // listening to input about the focused app.
        SystemClock.sleep(500);
        injectKey(KeyEvent.KEYCODE_A, false /* longpress */, false /* sync */);
        clickCloseAppOnAnrDialog(UNRESPONSIVE_ACTIVITY.getPackageName());
        assertEventLogsContainsAnr(UnresponsiveActivity.PROCESS_NAME);
    }

    @Test
    public void slowOnKeyEventHandleTriggersAnr() {
        startUnresponsiveActivity(EXTRA_ON_KEYDOWN_DELAY_MS, true /* waitForCompletion */,
                UNRESPONSIVE_ACTIVITY);
        injectKey(KeyEvent.KEYCODE_A, false /* longpress */, false /* sync */);
        clickCloseAppOnAnrDialog(UNRESPONSIVE_ACTIVITY.getPackageName());
        assertEventLogsContainsAnr(UnresponsiveActivity.PROCESS_NAME);
    }

    @Test
    public void slowOnTouchEventHandleTriggersAnr() {
        startUnresponsiveActivity(EXTRA_ON_MOTIONEVENT_DELAY_MS, true /* waitForCompletion */,
                UNRESPONSIVE_ACTIVITY);

        mWmState.computeState();
        // Tap on the UnresponsiveActivity
        final WindowManagerState.Task unresponsiveActivityTask =
                mWmState.getTaskByActivity(UNRESPONSIVE_ACTIVITY);
        mTouchHelper.tapOnTaskCenterAsync(unresponsiveActivityTask);
        clickCloseAppOnAnrDialog(UNRESPONSIVE_ACTIVITY.getPackageName());
        assertEventLogsContainsAnr(UnresponsiveActivity.PROCESS_NAME);
    }

    /**
     * Verify embedded windows can trigger ANR and the verify embedded app is blamed.
     */
    @Test
    @FlakyTest(bugId = 296860841)
    public void embeddedWindowTriggersAnr() {
        try (ActivityScenario<HostActivity> scenario =
                     ActivityScenario.launch(HostActivity.class)) {
            CountDownLatch[] latch = new CountDownLatch[1];
            scenario.onActivity(activity -> latch[0] = activity.mEmbeddedViewAttachedLatch);
            latch[0].await();
            mWmState.computeState();
            final WindowManagerState.Task hostActivityTask =
                    mWmState.getTaskByActivity(new ComponentName("android.server.wm.cts",
                            "android.server.wm.other.HostActivity"));
            mTouchHelper.tapOnTaskCenterAsync(hostActivityTask);
            clickCloseAppOnAnrDialog("android.server.wm.app");
        } catch (InterruptedException ignored) {
        }
        assertEventLogsContainsAnr(RenderService.PROCESS_NAME);
    }

    private void assertEventLogsContainsAnr(String processName) {
        final List<EventLog.Event> events = getEventLogsForComponents(mLogSeparator,
                android.util.EventLog.getTagCode("am_anr"));
        for (EventLog.Event event : events) {
            Object[] arr = (Object[]) event.getData();
            final String name = (String) arr[2];
            if (name.equals(processName)) {
                return;
            }
        }
        fail("Could not find anr kill event for " + processName);
    }

    private void clickCloseAppOnAnrDialog(String packageName) {
        // Find anr dialog and kill app
        final long timestamp = System.currentTimeMillis();
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 closeAppButton = uiDevice.wait(Until.findObject(By.res("android:id/aerr_close")),
                20000 * BuildUtils.HW_TIMEOUT_MULTIPLIER);
        if (closeAppButton == null) {
            fail("Could not find anr dialog");
            return;
        }
        closeAppButton.click();
        Log.d(TAG, "found permission dialog after searching all windows, clicked");
        /*
          We must wait for the app to be fully closed before exiting this test. This is because
          another test may again invoke 'am start' for the same activity.
          If the 1st process that got ANRd isn't killed by the time second 'am start' runs,
          the killing logic will apply to the newly launched 'am start' instance, and the second
          test will fail because the unresponsive activity will never be launched.
         */
        waitForNewExitReasonAfter(timestamp, packageName);
    }

    private void startUnresponsiveActivity(String delayTypeExtra, boolean waitForCompletion,
            ComponentName activity) {
        String flags = waitForCompletion ? " -W -n " : " -n ";
        String startCmd = "am start" + flags + activity.flattenToString() +
                " --ei " + delayTypeExtra + " 60000";
        executeShellCommand(startCmd);
    }

    private List<ApplicationExitInfo> getExitReasons(String packageName) {
        final List<ApplicationExitInfo>[] infos = new List[]{new ArrayList<>()};
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            ActivityManager am = (ActivityManager) instrumentation.getContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            infos[0] = am.getHistoricalProcessExitReasons(packageName, /*all pids*/0, /* no max*/0);
        });
        return infos[0];
    }
    private void waitForNewExitReasonAfter(long timestamp, String packageName) {
        PollingCheck.waitFor(() -> {
            List<ApplicationExitInfo> reasons = getExitReasons(packageName);
            return !reasons.isEmpty() && reasons.get(0).getTimestamp() >= timestamp;
        });
        List<ApplicationExitInfo> reasons = getExitReasons(packageName);
        assertTrue(reasons.get(0).getTimestamp() > timestamp);
        assertEquals(ApplicationExitInfo.REASON_ANR, reasons.get(0).getReason());
    }
}
