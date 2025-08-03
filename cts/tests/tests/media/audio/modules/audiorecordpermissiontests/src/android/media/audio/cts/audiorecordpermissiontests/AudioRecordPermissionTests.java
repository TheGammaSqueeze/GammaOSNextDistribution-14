/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests;

import static android.media.audio.cts.audiorecordpermissiontests.common.ActionsKt.*;
import static com.android.media.mediatestutils.TestUtils.getFutureForIntent;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class AudioRecordPermissionTests {
    // Keep in sync with test apps
    static final String API_34_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi34";
    // Behavior changes with targetSdk >= 34, so test both cases
    static final String API_33_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi33";
    static final String API_34_NO_CAP_PACKAGE =
            "android.media.audio.cts.CtsRecordServiceApi34NoCap";

    static final String SERVICE_NAME = ".RecordService";

    static final int FUTURE_WAIT_SECS = 15;
    static final int FALSE_NEG_SECS = 10;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    private String mStartedActivityPackage = null;
    private String mStartedServicePackage = null;

    @Before
    public void setup() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE));
    }

    @After
    public void teardown() throws Exception {
        // Clean up any left-over activities, services
        if (mStartedActivityPackage != null) {
            stopActivity(mStartedActivityPackage);
        }
        if (mStartedServicePackage != null) {
            stopService(mStartedServicePackage);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToBackground_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        startBackgroundServiceRecording(TEST_PACKAGE);
        // Prime future that the stream is silenced
        final var future =
                getFutureForIntent(mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToSleep_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        startBackgroundServiceRecording(TEST_PACKAGE);
        // Prime future that the stream is silenced
        final var future =
                getFutureForIntent(mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        try {
            // Move out of TOP to TOP_SLEEPING
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_SLEEP");
            // Future completes when silenced. If not, timeout and throw
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } finally {
            // Wait for unsilence after return to TOP
            final var receiveFuture = getFutureForIntent(
                    mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_AUDIO);
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_WAKEUP");
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithMicCapabilities_isNotSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a fgs with mic caps
        startActivity(TEST_PACKAGE);
        startForegroundServiceRecording(TEST_PACKAGE);
        // Prime future that the stream is silenced
        final var future =
                getFutureForIntent(mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithoutMicCapabilities_isSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_NO_CAP_PACKAGE;
        // Start an activity, then start recording in a fgs WITHOUT mic caps
        startActivity(TEST_PACKAGE);
        startForegroundServiceRecording(TEST_PACKAGE);
        // Prime future that the stream is silenced
        final var future =
                getFutureForIntent(mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future is completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testIfTargetPre34_MovingFromTopToBackground_isNotSilenced() throws Exception {
        final var TEST_PACKAGE = API_33_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        startBackgroundServiceRecording(TEST_PACKAGE);
        // Prime future that the stream is silenced
        final var future =
                getFutureForIntent(mContext, TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
    }

    private void startActivity(String packageName) throws Exception {
        final var future = getFutureForIntent(mContext, packageName + ACTION_ACTIVITY_STARTED);
        final var intent =
                new Intent(Intent.ACTION_MAIN)
                        .setClassName(packageName, packageName + ".SimpleActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getTargetContext().startActivity(intent);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mStartedActivityPackage = packageName;
    }

    private void stopActivity(String packageName) throws Exception {
        final var future = getFutureForIntent(mContext, packageName + ACTION_ACTIVITY_FINISHED);
        mContext.sendBroadcast(
                new Intent(packageName + ACTION_ACTIVITY_DO_FINISH).setPackage(packageName));
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mStartedActivityPackage = null;
    }

    private void startForegroundServiceRecording(String packageName) throws Exception {
        final var future = getFutureForIntent(mContext, packageName + ACTION_BEGAN_RECEIVE_AUDIO);
        amShellCommand(
                "start-foreground-service", packageName, ACTION_START_RECORD, " --ez "
                + EXTRA_IS_FOREGROUND + " true");
        try {
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // TODO(b/297259825) we never started recording unsilenced, due to avd sometimes
            // providing only silenced mic data.
            assumeTrue("AVD mic data may be silenced, preventing this test from working", false);
        }
        mStartedServicePackage = packageName;
    }

    private void startBackgroundServiceRecording(String packageName) throws Exception {
        final var future = getFutureForIntent(mContext, packageName + ACTION_BEGAN_RECEIVE_AUDIO);
        amShellCommand("startservice", packageName, ACTION_START_RECORD);
        try {
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // TODO(b/297259825) we never started recording unsilenced, due to avd sometimes
            // providing only silenced mic data.
            assumeTrue("AVD mic data may be silenced, preventing this test from working", false);
        }
        mStartedServicePackage = packageName;
    }

    private void stopService(String packageName) throws Exception {
        final var future = getFutureForIntent(mContext, packageName + ACTION_FINISH_TEARDOWN);
        amShellCommand("startservice", packageName, ACTION_STOP_RECORD);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mStartedServicePackage = null;
        // Just in case
        mInstrumentation
                .getTargetContext()
                .stopService(new Intent().setClassName(packageName, packageName + SERVICE_NAME));
    }

    private void amShellCommand(String command, String packageName, String action)
            throws Exception {
        amShellCommand(command, packageName, action, "");
    }

    private void amShellCommand(String command, String packageName, String action, String extra)
            throws Exception {
        SystemUtil.runShellCommand(
                mInstrumentation,
                "am "
                        + command
                        + " -n "
                        + ComponentName.createRelative(packageName, SERVICE_NAME)
                                .flattenToShortString()
                        + " -a "
                        + packageName
                        + action
                        + extra);
    }
}
