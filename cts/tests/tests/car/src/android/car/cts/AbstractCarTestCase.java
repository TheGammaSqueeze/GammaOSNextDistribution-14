/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.car.Car;
import android.car.FuelType;
import android.car.PortLocationType;
import android.car.test.AbstractExpectableTestCase;
import android.car.test.PermissionsCheckerRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Base class for tests that don't need to connect to a {@link android.car.Car} object.
 *
 * <p>For tests that don't need a {@link android.car.Car} object, use
 * {@link AbstractCarLessTestCase} instead.
 */
abstract class AbstractCarTestCase extends AbstractExpectableTestCase {

    private static final String TAG = AbstractCarTestCase.class.getSimpleName();

    protected static final long DEFAULT_WAIT_TIMEOUT_MS = 1000;

    // Enums in FuelType
    protected static final List<Integer> EXPECTED_FUEL_TYPES =
            Arrays.asList(FuelType.UNKNOWN, FuelType.UNLEADED, FuelType.LEADED, FuelType.DIESEL_1,
                    FuelType.DIESEL_2, FuelType.BIODIESEL, FuelType.E85, FuelType.LPG, FuelType.CNG,
                    FuelType.LNG, FuelType.ELECTRIC, FuelType.HYDROGEN, FuelType.OTHER);
    // Enums in PortLocationType
    protected static final List<Integer> EXPECTED_PORT_LOCATIONS =
            Arrays.asList(PortLocationType.UNKNOWN, PortLocationType.FRONT_LEFT,
                    PortLocationType.FRONT_RIGHT, PortLocationType.REAR_RIGHT,
                    PortLocationType.REAR_LEFT, PortLocationType.FRONT, PortLocationType.REAR);

    // CheckFlagsRule rule needs to before PermissionsCheckerRule.
    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final PermissionsCheckerRule permissionsCheckerRule = new PermissionsCheckerRule();

    protected final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private final DefaultServiceConnectionListener mConnectionListener =
            new DefaultServiceConnectionListener();

    private Car mCar;

    protected void assertMainThread() {
        assertWithMessage("Looper.getMainLooper().isCurrentThread()")
                .that(Looper.getMainLooper().isCurrentThread()).isTrue();
    }

    @Before
    public final void createCar() throws Exception {
        mCar = Car.createCar(mContext);
    }

    @After
    public final void disconnectCar() throws Exception {
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    public void startUser(int userId) throws Exception {
        executeShellCommand("am start-user %d", userId);
    }

    protected synchronized Car getCar() {
        return mCar;
    }

    protected class DefaultServiceConnectionListener implements ServiceConnection {
        private final Semaphore mConnectionWait = new Semaphore(0);

        public void waitForConnection(long timeoutMs) throws InterruptedException {
            mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assertMainThread();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            assertMainThread();
            mConnectionWait.release();
        }
    }

    protected static String executeShellCommand(String commandFormat, Object... args)
            throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        return executeShellCommand(uiAutomation, commandFormat, args);
    }

    protected static String executeShellCommandWithPermission(String permission,
            String commandFormat, Object... args) throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String result;
        try {
            uiAutomation.adoptShellPermissionIdentity(permission);
            result = executeShellCommand(uiAutomation, commandFormat, args);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return result;
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
