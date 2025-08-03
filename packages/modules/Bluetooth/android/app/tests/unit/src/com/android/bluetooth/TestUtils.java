/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.test.TestLooper;
import android.service.media.MediaBrowserService;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.uiautomator.UiDevice;

import com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.gatt.GattService;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.util.MockUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * A set of methods useful in Bluetooth instrumentation tests
 */
public class TestUtils {
    private static final int SERVICE_TOGGLE_TIMEOUT_MS = 1000;    // 1s

    private static String sSystemScreenOffTimeout = "10000";

    private static final String TAG = "BluetoothTestUtils";

    /**
     * Utility method to replace obj.fieldName with newValue where obj is of type c
     *
     * @param c         type of obj
     * @param fieldName field name to be replaced
     * @param obj       instance of type c whose fieldName is to be replaced, null for static fields
     * @param newValue  object used to replace fieldName
     * @return the old value of fieldName that got replaced, caller is responsible for restoring
     * it back to obj
     * @throws NoSuchFieldException   when fieldName is not found in type c
     * @throws IllegalAccessException when fieldName cannot be accessed in type c
     */
    public static Object replaceField(final Class c, final String fieldName, final Object obj,
            final Object newValue) throws NoSuchFieldException, IllegalAccessException {
        Field field = c.getDeclaredField(fieldName);
        field.setAccessible(true);

        Object oldValue = field.get(obj);
        field.set(obj, newValue);
        return oldValue;
    }

    /**
     * Set the return value of {@link AdapterService#getAdapterService()} to a test specified value
     *
     * @param adapterService the designated {@link AdapterService} in test, must not be null, can be
     *     mocked or spied
     */
    public static void setAdapterService(AdapterService adapterService) {
        Assert.assertNull("AdapterService.getAdapterService() must be null before setting another"
                + " AdapterService", AdapterService.getAdapterService());
        Assert.assertNotNull("Adapter service should not be null", adapterService);
        // We cannot mock AdapterService.getAdapterService() with Mockito.
        // Hence we need to set AdapterService.sAdapterService field.
        AdapterService.setAdapterService(adapterService);
    }

    /**
     * Clear the return value of {@link AdapterService#getAdapterService()} to null
     *
     * @param adapterService the {@link AdapterService} used when calling {@link
     *     TestUtils#setAdapterService(AdapterService)}
     */
    public static void clearAdapterService(AdapterService adapterService) {
        Assert.assertSame("AdapterService.getAdapterService() must return the same object as the"
                        + " supplied adapterService in this method", adapterService,
                AdapterService.getAdapterService());
        Assert.assertNotNull("Adapter service should not be null", adapterService);
        AdapterService.clearAdapterService(adapterService);
    }

    /** Helper function to mock getSystemService calls */
    public static <T> void mockGetSystemService(
            Context ctx, String serviceName, Class<T> serviceClass, T mockService) {
        when(ctx.getSystemService(eq(serviceName))).thenReturn(mockService);
        when(ctx.getSystemServiceName(eq(serviceClass))).thenReturn(serviceName);
    }

    /** Helper function to mock getSystemService calls */
    public static <T> T mockGetSystemService(
            Context ctx, String serviceName, Class<T> serviceClass) {
        T mockedService = mock(serviceClass);
        mockGetSystemService(ctx, serviceName, serviceClass, mockedService);
        return mockedService;
    }

    /**
     * Create a test device.
     *
     * @param bluetoothAdapter the Bluetooth adapter to use
     * @param id               the test device ID. It must be an integer in the interval [0, 0xFF].
     * @return {@link BluetoothDevice} test device for the device ID
     */
    public static BluetoothDevice getTestDevice(BluetoothAdapter bluetoothAdapter, int id) {
        Assert.assertTrue(id <= 0xFF);
        Assert.assertNotNull(bluetoothAdapter);
        BluetoothDevice testDevice =
                bluetoothAdapter.getRemoteDevice(String.format("00:01:02:03:04:%02X", id));
        Assert.assertNotNull(testDevice);
        return testDevice;
    }

    public static Resources getTestApplicationResources(Context context) {
        try {
            return context.getPackageManager().getResourcesForApplication(
                    "com.android.bluetooth.tests");
        } catch (PackageManager.NameNotFoundException e) {
            assertWithMessage("Setup Failure: Unable to get test application resources"
                    + e.toString()).fail();
            return null;
        }
    }

    /**
     * Wait and verify that an intent has been received.
     *
     * @param timeoutMs the time (in milliseconds) to wait for the intent
     * @param queue     the queue for the intent
     * @return the received intent
     */
    public static Intent waitForIntent(int timeoutMs, BlockingQueue<Intent> queue) {
        try {
            Intent intent = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            Assert.assertNotNull(intent);
            return intent;
        } catch (InterruptedException e) {
            Assert.fail("Cannot obtain an Intent from the queue: " + e.getMessage());
        }
        return null;
    }

    /**
     * Wait and verify that no intent has been received.
     *
     * @param timeoutMs the time (in milliseconds) to wait and verify no intent
     *                  has been received
     * @param queue     the queue for the intent
     * @return the received intent. Should be null under normal circumstances
     */
    public static Intent waitForNoIntent(int timeoutMs, BlockingQueue<Intent> queue) {
        try {
            Intent intent = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            Assert.assertNull(intent);
            return intent;
        } catch (InterruptedException e) {
            Assert.fail("Cannot obtain an Intent from the queue: " + e.getMessage());
        }
        return null;
    }

    /**
     * Wait for looper to finish its current task and all tasks schedule before this
     *
     * @param looper looper of interest
     */
    public static void waitForLooperToFinishScheduledTask(Looper looper) {
        runOnLooperSync(looper, () -> {
            // do nothing, just need to make sure looper finishes current task
        });
    }

    /**
     * Dispatch all the message on the Loopper and check that the `what` is expected
     *
     * @param looper looper to execute the message from
     * @param what list of Messages.what that are expected to be run by the handler
     */
    public static void syncHandler(TestLooper looper, int... what) {
        IntStream.of(what)
                .forEach(
                        w -> {
                            Message msg = looper.nextMessage();
                            assertWithMessage("Expecting [" + w + "] instead of null Msg")
                                    .that(msg)
                                    .isNotNull();
                            assertWithMessage("Not the expected Message:\n" + msg)
                                    .that(msg.what)
                                    .isEqualTo(w);
                            Log.d(TAG, "Processing message: " + msg);
                            msg.getTarget().dispatchMessage(msg);
                        });
    }

    /**
     * Wait for looper to become idle
     *
     * @param looper looper of interest
     */
    public static void waitForLooperToBeIdle(Looper looper) {
        class Idler implements MessageQueue.IdleHandler {
            private boolean mIdle = false;

            @Override
            public boolean queueIdle() {
                synchronized (this) {
                    mIdle = true;
                    notifyAll();
                }
                return false;
            }

            public synchronized void waitForIdle() {
                while (!mIdle) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        Idler idle = new Idler();
        looper.getQueue().addIdleHandler(idle);
        // Ensure we are not Idle to begin with so the idle handler will run
        waitForLooperToFinishScheduledTask(looper);
        idle.waitForIdle();
    }

    /**
     * Run synchronously a runnable action on a looper.
     * The method will return after the action has been execution to completion.
     *
     * Example:
     * <pre>
     * {@code
     * TestUtils.runOnMainSync(new Runnable() {
     *       public void run() {
     *           Assert.assertTrue(mA2dpService.stop());
     *       }
     *   });
     * }
     * </pre>
     *
     * @param looper the looper used to run the action
     * @param action the action to run
     */
    public static void runOnLooperSync(Looper looper, Runnable action) {
        if (Looper.myLooper() == looper) {
            // requested thread is the same as the current thread. call directly.
            action.run();
        } else {
            Handler handler = new Handler(looper);
            SyncRunnable sr = new SyncRunnable(action);
            handler.post(sr);
            sr.waitForComplete();
        }
    }

    /**
     * Read Bluetooth adapter configuration from the filesystem
     *
     * @return A {@link HashMap} of Bluetooth configs in the format:
     * section -> key1 -> value1
     * -> key2 -> value2
     * Assume no empty section name, no duplicate keys in the same section
     */
    public static HashMap<String, HashMap<String, String>> readAdapterConfig() {
        HashMap<String, HashMap<String, String>> adapterConfig = new HashMap<>();
        try (BufferedReader reader =
                     new BufferedReader(new FileReader("/data/misc/bluedroid/bt_config.conf"))) {
            String section = "";
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[")) {
                    if (line.charAt(line.length() - 1) != ']') {
                        Log.e(TAG, "readAdapterConfig: config line is not correct: " + line);
                        return null;
                    }
                    section = line.substring(1, line.length() - 1);
                    adapterConfig.put(section, new HashMap<>());
                } else {
                    String[] keyValue = line.split("=");
                    adapterConfig.get(section).put(keyValue[0].trim(),
                            keyValue.length == 1 ? "" : keyValue[1].trim());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "readAdapterConfig: Exception while reading the config" + e);
            return null;
        }
        return adapterConfig;
    }

    /**
     * Prepare the intent to start bluetooth browser media service.
     *
     * @return intent with the appropriate component & action set.
     */
    public static Intent prepareIntentToStartBluetoothBrowserMediaService() {
        final Intent intent = new Intent(InstrumentationRegistry.getTargetContext(),
                BluetoothMediaBrowserService.class);
        intent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        return intent;
    }

    public static void setUpUiTest() throws Exception {
        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        // Disable animation
        device.executeShellCommand("settings put global window_animation_scale 0.0");
        device.executeShellCommand("settings put global transition_animation_scale 0.0");
        device.executeShellCommand("settings put global animator_duration_scale 0.0");

        // change device screen_off_timeout to 5 minutes
        sSystemScreenOffTimeout =
                device.executeShellCommand("settings get system screen_off_timeout");
        device.executeShellCommand("settings put system screen_off_timeout 300000");

        // Turn on screen and unlock
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");

        // Back to home screen, in case some dialog/activity is in front
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();
    }

    public static void tearDownUiTest() throws Exception {
        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        device.executeShellCommand("wm dismiss-keyguard");

        // Re-enable animation
        device.executeShellCommand("settings put global window_animation_scale 1.0");
        device.executeShellCommand("settings put global transition_animation_scale 1.0");
        device.executeShellCommand("settings put global animator_duration_scale 1.0");

        // restore screen_off_timeout
        device.executeShellCommand("settings put system screen_off_timeout "
                + sSystemScreenOffTimeout);
    }

    public static class RetryTestRule implements TestRule {
        private int retryCount = 5;

        public RetryTestRule() {
            this(5);
        }

        public RetryTestRule(int retryCount) {
            this.retryCount = retryCount;
        }

        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Throwable caughtThrowable = null;

                    // implement retry logic here
                    for (int i = 0; i < retryCount; i++) {
                        try {
                            base.evaluate();
                            return;
                        } catch (Throwable t) {
                            caughtThrowable = t;
                            Log.e(
                                    TAG,
                                    description.getDisplayName() + ": run " + (i + 1) + " failed",
                                    t);
                        }
                    }
                    Log.e(
                            TAG,
                            description.getDisplayName()
                                    + ": giving up after "
                                    + retryCount
                                    + " failures");
                    throw caughtThrowable;
                }
            };
        }
    }

    /**
     * Helper class used to run synchronously a runnable action on a looper.
     */
    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private volatile boolean mComplete = false;

        SyncRunnable(Runnable target) {
            mTarget = target;
        }

        @Override
        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }
}
