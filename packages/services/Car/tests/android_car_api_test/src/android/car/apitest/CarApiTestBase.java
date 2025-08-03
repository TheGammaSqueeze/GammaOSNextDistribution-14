/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.apitest;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.TestUtils.BooleanSupplierWithThrow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.UiAutomation;
import android.car.Car;
import android.car.test.AbstractExpectableTestCase;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Base class for tests that don't need to connect to a {@link android.car.Car} object.
 *
 * <p>For tests that don't need a {@link android.car.Car} object, use
 * {@link CarLessApiTestBase} instead.
 */
public abstract class CarApiTestBase extends AbstractExpectableTestCase {

    private static final String TAG = CarApiTestBase.class.getSimpleName();

    protected static final long DEFAULT_WAIT_TIMEOUT_MS = 120_000;

    /**
     * Constant used to wait blindly, when there is no condition that can be checked.
     */
    private static final int SUSPEND_TIMEOUT_MS = 5_000;

    /**
     * How long to sleep (multiple times) while waiting for a condition.
     */
    private static final int SMALL_NAP_MS = 100;

    protected static final ReceiverTrackingContext sContext = new ReceiverTrackingContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

    private Car mCar;

    protected final DefaultServiceConnectionListener mConnectionListener =
            new DefaultServiceConnectionListener();

    // TODO(b/242350638): temporary hack to allow subclasses to disable checks - should be removed
    // when not needed anymore
    @Before
    public final void setFixturesAndConnectToCar() throws Exception {
        Log.d(TAG, "setFixturesAndConnectToCar() for " + getTestName());

        mCar = Car.createCar(getContext(), mConnectionListener);
        mCar.connect();
        mConnectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
    }

    @Before
    public final void dontStopUserOnSwitch() throws Exception {
        Log.d(TAG, "Calling am.setStopUserOnSwitch(false) for " + getTestName());
        getContext().getSystemService(ActivityManager.class)
                .setStopUserOnSwitch(ActivityManager.STOP_USER_ON_SWITCH_FALSE);
    }

    @After
    public final void disconnectCar() throws Exception {
        if (mCar == null) {
            Log.wtf(TAG, "no mCar on " + getTestName() + ".tearDown()");
            return;
        }
        mCar.disconnect();
    }

    @After
    public final void resetStopUserOnSwitch() throws Exception {
        Log.d(TAG, "Calling am.setStopUserOnSwitch(default) for " + getTestName());
        getContext().getSystemService(ActivityManager.class)
                .setStopUserOnSwitch(ActivityManager.STOP_USER_ON_SWITCH_DEFAULT);
    }

    @After
    public final void checkReceiversUnregisters() {
        Collection<String> receivers = sContext.getReceiversInfo();
        Log.d(TAG, "Checking if all receivers were unregistered.");

        assertWithMessage("Broadcast receivers that are not unregistered: %s", receivers)
                .that(receivers).isEmpty();
    }

    protected Car getCar() {
        return mCar;
    }

    protected final Context getContext() {
        return sContext;
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // error prone complains about returning <T>
    protected final <T> T getCarService(@NonNull String serviceName) {
        assertThat(serviceName).isNotNull();
        Object service = mCar.getCarManager(serviceName);
        assertWithMessage("Could not get service %s", serviceName).that(service).isNotNull();

        @SuppressWarnings("unchecked")
        T castService = (T) service;
        return castService;
    }

    protected static void assertMainThread() {
        assertThat(Looper.getMainLooper().isCurrentThread()).isTrue();
    }

    protected static final class DefaultServiceConnectionListener implements ServiceConnection {
        private final Semaphore mConnectionWait = new Semaphore(0);

        public void waitForConnection(long timeoutMs) throws InterruptedException {
            mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            assertMainThread();
            mConnectionWait.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assertMainThread();
            fail("Car service crashed");
        }
    }

    protected static void suspendToRamAndResume()
            throws Exception {
        Log.d(TAG, "Emulate suspend to RAM and resume");
        try {
            Log.d(TAG, "Disabling background users starting on garage mode");
            runShellCommand("cmd car_service set-start-bg-users-on-garage-mode false");

            PowerManager powerManager = sContext.getSystemService(PowerManager.class);
            // clear log
            runShellCommand("logcat -b all -c");
            // We use a simulated suspend because physically suspended devices cannot be woken up by
            // a shell command.
            runShellCommand("cmd car_service suspend --simulate --skip-garagemode "
                    + "--wakeup-after 3");
            // Check for suspend success
            waitUntil("screen is still on after suspend",
                    SUSPEND_TIMEOUT_MS, () -> !powerManager.isScreenOn());

            // The device will resume after 3 seconds.
            waitForLogcatMessage("logcat -b events", "car_user_svc_initial_user_info_req_complete: "
                    + InitialUserInfoRequestType.RESUME, 60_000);
        } catch (Exception e) {
            runShellCommand("cmd car_service set-start-bg-users-on-garage-mode true");
        }
    }

    /**
     * Wait for a particular logcat message.
     *
     * @param cmd is logcat command with buffer or filters
     * @param match is the string to be found in the log
     * @param timeoutMs for which call should wait for the match
     */
    protected static void waitForLogcatMessage(String cmd, String match, int timeoutMs) {
        Log.d(TAG, "waiting for logcat match: " + match);
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor output = automation.executeShellCommand(cmd);
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(match)) {
                    Log.d(TAG, "match found in "
                            + (SystemClock.elapsedRealtime() - startTime) + " ms");
                    break;
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeoutMs) {
                    fail("logcat message(%s) not found in %d ms", match, timeoutMs);
                }
            }
        } catch (IOException e) {
            fail("match (%s) was not found, IO exception: %s", match, e);
        }
    }

    protected static boolean waitUntil(String msg, long timeoutMs,
            BooleanSupplierWithThrow condition) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            try {
                if (condition.getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in waitUntil: " + msg);
                throw new RuntimeException(e);
            }
            SystemClock.sleep(SMALL_NAP_MS);
        } while (SystemClock.elapsedRealtime() < deadline);

        fail("%s after %d ms", msg, timeoutMs);
        return false;
    }

    // TODO(b/250914846): Clean this up once the investigation is done.
    // Same as waitUntil except for not failing the test.
    protected static boolean waitUntilNoFail(long timeoutMs,
            BooleanSupplierWithThrow condition) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            try {
                if (condition.getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in waitUntilNoFail");
                throw new RuntimeException(e);
            }
            SystemClock.sleep(SMALL_NAP_MS);
        } while (SystemClock.elapsedRealtime() < deadline);

        return false;
    }

    protected void requireNonUserBuild() {
        assumeFalse("Requires Shell commands that are not available on user builds", Build.IS_USER);
    }

    protected String getTestName() {
        // TODO(b/300964422): Add a way to get testName if possible.
        return "";
    }

    protected static void fail(String format, Object...args) {
        String message = String.format(format, args);
        Log.e(TAG, "test failed: " + message);
        org.junit.Assert.fail(message);
    }

    protected static String executeShellCommand(String commandFormat, Object... args)
            throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        return executeShellCommand(uiAutomation, commandFormat, args);
    }

    private static String executeShellCommand(UiAutomation uiAutomation, String commandFormat,
            Object... args) throws IOException {
        ParcelFileDescriptor stdout = uiAutomation.executeShellCommand(
                String.format(commandFormat, args));
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString("UTF-8");
        }
    }
}
