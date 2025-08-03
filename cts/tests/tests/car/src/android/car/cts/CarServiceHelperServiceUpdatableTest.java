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

package android.car.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

import android.app.UiAutomation;
import android.car.Car;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class CarServiceHelperServiceUpdatableTest extends AbstractCarTestCase {

    private static final String TAG = CarServiceHelperServiceUpdatableTest.class.getSimpleName();
    private static final int TIMEOUT_MS = 60_000;
    private static final int WAIT_TIME_MS = 1_000;

    @Before
    public void clearLogcat() throws Exception {
        SystemUtil.runShellCommand("logcat -b all -c");
    }

    @Test
    @ApiTest(apis = {"com.android.internal.car.CarServiceHelperService#dump"})
    public void testCarServiceHelperServiceDump() throws Exception {
        assumeSystemServerDumpSupported();

        assertWithMessage("System server dumper")
                .that(executeShellCommand("dumpsys system_server_dumper --list"))
                .contains("CarServiceHelper");
    }

    @Test
    @ApiTest(apis = {
            "com.android.internal.car.CarServiceHelperServiceUpdatable#dump"
    })
    public void testCarServiceHelperServiceDump_carServiceProxy() throws Exception {
        assumeSystemServerDumpSupported();

        assertWithMessage("CarServiceHelperService dump")
                .that(executeShellCommand("dumpsys system_server_dumper --name CarServiceHelper"))
                .contains("CarServiceProxy");
    }

    @Test
    @ApiTest(apis = {
            "com.android.internal.car.CarServiceHelperServiceUpdatable#dump"
    })
    public void testCarServiceHelperServiceDump_serviceStacks() throws Exception {
        assumeSystemServerDumpSupported();

        assertWithMessage("CarServiceHelperService dump")
                .that(dumpCarServiceHelper("--dump-service-stacks"))
                .contains("dumpServiceStacks ANR file path=/data/anr/anr_");
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testSendUserLifecycleEventAndOnUserCreated_supportedVersion() throws Exception {
        testSendUserLifecycleEventAndOnUserCreated(/*onSupportedVersion=*/ true);
    }

    private void testSendUserLifecycleEventAndOnUserCreated(boolean onSupportedVersion)
            throws Exception {
        // Add listener to check if user started
        CarUserManager carUserManager = (CarUserManager) getCar()
                .getCarManager(Car.CAR_USER_SERVICE);
        LifecycleListener listener = new LifecycleListener();
        carUserManager.addListener(Runnable::run, listener);

        NewUserResponse response = null;
        UserManager userManager = null;
        try {
            // CreateUser
            userManager = mContext.getSystemService(UserManager.class);
            response = userManager.createUser(new NewUserRequest.Builder().build());
            assertThat(response.isSuccessful()).isTrue();

            int userId = response.getUser().getIdentifier();
            if (onSupportedVersion) {
                listener.assertEventReceived(
                        userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
                // check the dump stack
                assertUserLifecycleEventLogged(
                        CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED, userId);
            } else {
                listener.assertEventNotReceived(
                        userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
            }
        } finally {
            // Clean up the user that was previously created.
            userManager.removeUser(response.getUser());
            carUserManager.removeListener(listener);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testSendUserLifecycleEventAndOnUserRemoved_supportedVersion() throws Exception {
        testSendUserLifecycleEventAndOnUserRemoved(/*onSupportedVersion=*/ true);
    }

    private static void assumeSystemServerDumpSupported() throws IOException {
        assumeThat("System_server_dumper not implemented.",
                executeShellCommand("service check system_server_dumper"),
                containsStringIgnoringCase("system_server_dumper: found"));
    }

    private void testSendUserLifecycleEventAndOnUserRemoved(boolean onSupportedVersion)
            throws Exception {
        // Add listener to check if user started
        CarUserManager carUserManager = (CarUserManager) getCar()
                .getCarManager(Car.CAR_USER_SERVICE);
        LifecycleListener listener = new LifecycleListener();
        carUserManager.addListener(Runnable::run, listener);

        NewUserResponse response = null;
        UserManager userManager = null;
        boolean userRemoved = false;
        try {
            // CreateUser
            userManager = mContext.getSystemService(UserManager.class);
            response = userManager.createUser(new NewUserRequest.Builder().build());
            assertThat(response.isSuccessful()).isTrue();

            int userId = response.getUser().getIdentifier();
            startUser(userId);
            listener.assertEventReceived(userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING);
            // check the dump stack
            assertUserLifecycleEventLogged(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING,
                    userId);

            // TestOnUserRemoved call
            userRemoved = userManager.removeUser(response.getUser());

            if (onSupportedVersion) {
                listener.assertEventReceived(
                        userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED);
                // check the dump stack
                assertUserLifecycleEventLogged(
                        CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED, userId);
            } else {
                listener.assertEventNotReceived(
                        userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED);
            }
        } finally {
            if (!userRemoved && response != null && response.isSuccessful()) {
                userManager.removeUser(response.getUser());
            }
            carUserManager.removeListener(listener);
        }
    }

    private void assertUserLifecycleEventLogged(int eventType, int userId) throws Exception {
        assertUserLifecycleEventLogged(eventType, UserHandle.USER_NULL, userId);
    }

    private void assertUserLifecycleEventLogged(int eventType, int fromUserId, int toUserId)
            throws Exception {
        // check for the logcat
        // TODO(b/210874444): Use logcat helper from
        // cts/tests/tests/car_builtin/src/android/car/cts/builtin/util/LogcatHelper.java
        String match = String.format("car_service_on_user_lifecycle: [%d,%d,%d]", eventType,
                fromUserId, toUserId);
        long timeout = 60_000;
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String command = "logcat -b events";
        ParcelFileDescriptor output = automation.executeShellCommand(command);
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(match)) {
                    return;
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    fail("match '" + match + "' was not found, Timeout: " + timeout + " ms");
                }
            }
        } catch (IOException e) {
            fail("match '" + match + "' was not found, IO exception: " + e);
        }

    }

    private String dumpCarServiceHelper(String...args) throws IOException {
        StringBuilder cmd = new StringBuilder(
                "dumpsys system_server_dumper --name CarServiceHelper");
        for (String arg : args) {
            cmd.append(' ').append(arg);
        }
        return executeShellCommand(cmd.toString());
    }

    // TODO(214100537): Improve listener by removing sleep.
    private final class LifecycleListener implements UserLifecycleListener {

        private final List<UserLifecycleEvent> mEvents =
                new ArrayList<CarUserManager.UserLifecycleEvent>();

        private final Object mLock = new Object();

        @Override
        public void onEvent(UserLifecycleEvent event) {
            Log.d(TAG, "Event received: " + event);
            synchronized (mLock) {
                mEvents.add(event);
            }
        }

        public void assertEventReceived(int userId, int eventType)
                throws InterruptedException {
            long startTime = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - startTime < TIMEOUT_MS) {
                boolean result = checkEvent(userId, eventType);
                if (result) return;
                Thread.sleep(WAIT_TIME_MS);
            }

            fail("Event" + eventType + " was not received within timeoutMs: " + TIMEOUT_MS);
        }


        public void assertEventNotReceived(int userId, int eventType)
                throws InterruptedException {
            long startTime = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - startTime < TIMEOUT_MS) {
                boolean result = checkEvent(userId, eventType);
                if (result) {
                    fail("Event" + eventType
                            + " was not expected but was received within timeoutMs: " + TIMEOUT_MS);
                }
                Thread.sleep(WAIT_TIME_MS);
            }
        }

        private boolean checkEvent(int userId, int eventType) {
            synchronized (mLock) {
                for (int i = 0; i < mEvents.size(); i++) {
                    if (mEvents.get(i).getUserHandle().getIdentifier() == userId
                            && mEvents.get(i).getEventType() == eventType) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
