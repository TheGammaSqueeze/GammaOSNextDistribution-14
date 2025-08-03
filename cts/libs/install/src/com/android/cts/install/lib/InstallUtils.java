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

package com.android.cts.install.lib;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Utilities to facilitate installation in tests.
 */
public class InstallUtils {
    private static final int NUM_MAX_POLLS = 5;
    private static final int POLL_WAIT_TIME_MILLIS = 200;
    private static final long GET_UIAUTOMATION_TIMEOUT_MS = 60000;
    private static final String CLASS_NAME_PROCESS_BROADCAST =
            "com.android.cts.install.lib.testapp.ProcessBroadcast";

    private static UiAutomation getUiAutomation() {
        final long start = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - start < GET_UIAUTOMATION_TIMEOUT_MS) {
            UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            if (ui != null) {
                return ui;
            }
        }
        throw new AssertionError("Failed to get UiAutomation");
    }

    /**
     * Adopts the given shell permissions.
     */
    public static void adoptShellPermissionIdentity(String... permissions) {
        getUiAutomation().adoptShellPermissionIdentity(permissions);
    }

    /**
     * Drops all shell permissions.
     */
    public static void dropShellPermissionIdentity() {
        getUiAutomation().dropShellPermissionIdentity();
    }
    /**
     * Returns the version of the given package installed on device.
     * Returns -1 if the package is not currently installed.
     */
    public static long getInstalledVersion(String packageName) {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();
        try {
            if (SdkLevel.isAtLeastT()) {
                PackageInfo info = pm.getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));
                return info.getLongVersionCode();
            } else {
                PackageInfo info = pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
                return info.getLongVersionCode();
            }
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * Returns the info for the given package name.
     */
    public static PackageInfo getPackageInfo(String packageName) {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();
        try {
            if (SdkLevel.isAtLeastT()) {
                return pm.getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));
            } else {
                return pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the PackageInstaller instance of the current {@code Context}
     */
    public static PackageInstaller getPackageInstaller() {
        return InstrumentationRegistry.getTargetContext().getPackageManager().getPackageInstaller();
    }

    /**
     * Returns an existing session to actively perform work.
     * {@see PackageInstaller#openSession}
     */
    public static PackageInstaller.Session openPackageInstallerSession(int sessionId)
            throws IOException {
        return getPackageInstaller().openSession(sessionId);
    }

    /**
     * Asserts that {@code result} intent has a success status.
     */
    public static void assertStatusSuccess(Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent intent = result.getParcelableExtra(Intent.EXTRA_INTENT);
            throw new AssertionError("PENDING USER ACTION: " + intent);
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            throw new AssertionError(message == null ? "UNKNOWN FAILURE" : message);
        }
    }

    /**
     * Asserts that {@code result} intent has a failure status.
     */
    public static void assertStatusFailure(Intent result) {
        // Pass SUCCESS as default to ensure that this doesn't accidentally pass
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_SUCCESS);
        switch (status) {
            case -2: // PackageInstaller.STATUS_PENDING_STREAMING
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                throw new AssertionError("PENDING " + status);
            case PackageInstaller.STATUS_SUCCESS:
                throw new AssertionError("INCORRECT SUCCESS ");
            case PackageInstaller.STATUS_FAILURE:
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
            case PackageInstaller.STATUS_FAILURE_ABORTED:
            case PackageInstaller.STATUS_FAILURE_INVALID:
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_STORAGE:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            case PackageInstaller.STATUS_FAILURE_TIMEOUT:
                break;
            default:
                String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                throw new AssertionError(message == null ? "UNKNOWN STATUS" : message);
        }
    }

    /**
     * Commits {@link Install} but expects to fail.
     *
     * @param expectedThrowableClass class or superclass of the expected throwable.
     *
     */
    public static void commitExpectingFailure(Class expectedThrowableClass,
            String expectedFailMessage, Install install) {
        assertThrows(expectedThrowableClass, expectedFailMessage, () -> install.commit());
    }

    /**
     * Mutates {@code installFlags} field of {@code params} by adding {@code
     * additionalInstallFlags} to it.
     */
    @VisibleForTesting
    public static void mutateInstallFlags(PackageInstaller.SessionParams params,
            int additionalInstallFlags) {
        final Class<?> clazz = params.getClass();
        Field installFlagsField;
        try {
            installFlagsField = clazz.getDeclaredField("installFlags");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Unable to reflect over SessionParams.installFlags", e);
        }

        try {
            int flags = installFlagsField.getInt(params);
            flags |= additionalInstallFlags;
            installFlagsField.setAccessible(true);
            installFlagsField.setInt(params, flags);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Unable to reflect over SessionParams.installFlags", e);
        }
    }

    private static final String NO_RESPONSE = "NO RESPONSE";

    /**
     * Calls into the test app to process user data.
     * Asserts if the user data could not be processed or was version
     * incompatible with the previously processed user data.
     */
    public static void processUserData(String packageName) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, CLASS_NAME_PROCESS_BROADCAST));
        intent.setAction("PROCESS_USER_DATA");
        Context context = InstrumentationRegistry.getTargetContext();

        HandlerThread handlerThread = new HandlerThread("RollbackTestHandlerThread");
        handlerThread.start();

        // It can sometimes take a while after rollback before the app will
        // receive this broadcast, so try a few times in a loop.
        String result = NO_RESPONSE;
        for (int i = 0; i < NUM_MAX_POLLS; ++i) {
            BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
            context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (getResultCode() == 1) {
                        resultQueue.add("OK");
                    } else {
                        // If the test app doesn't receive the broadcast or
                        // fails to set the result data, then getResultData
                        // here returns the initial NO_RESPONSE data passed to
                        // the sendOrderedBroadcast call.
                        resultQueue.add(getResultData());
                    }
                }
            }, new Handler(handlerThread.getLooper()), 0, NO_RESPONSE, null);

            try {
                result = resultQueue.take();
                if (!result.equals(NO_RESPONSE)) {
                    break;
                }
                Thread.sleep(POLL_WAIT_TIME_MILLIS);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        assertThat(result).isEqualTo("OK");
    }

    /**
     * Retrieves the app's user data version from userdata.txt.
     * @return -1 if userdata.txt doesn't exist or -2 if the app doesn't handle the broadcast which
     * could happen when the app crashes or doesn't start at all.
     */
    public static int getUserDataVersion(String packageName) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, CLASS_NAME_PROCESS_BROADCAST));
        intent.setAction("GET_USER_DATA_VERSION");
        Context context = InstrumentationRegistry.getTargetContext();

        HandlerThread handlerThread = new HandlerThread("RollbackTestHandlerThread");
        handlerThread.start();

        // The response code returned when the broadcast is not received by the app or when the app
        // crashes during handling the broadcast. We will retry when this code is returned.
        final int noResponse = -2;
        // It can sometimes take a while after rollback before the app will
        // receive this broadcast, so try a few times in a loop.
        BlockingQueue<Integer> resultQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < NUM_MAX_POLLS; ++i) {
            context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    resultQueue.add(getResultCode());
                }
            }, new Handler(handlerThread.getLooper()), noResponse, null, null);

            try {
                int userDataVersion = resultQueue.take();
                if (userDataVersion != noResponse) {
                    return userDataVersion;
                }
                Thread.sleep(POLL_WAIT_TIME_MILLIS);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        return noResponse;
    }

    private static void sendBroadcastAndWait(Intent intent) throws Exception {
        var handlerThread = new HandlerThread("TestHandlerThread");
        handlerThread.start();

        var latch = new CountDownLatch(1);
        var context = InstrumentationRegistry.getTargetContext();
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, new Handler(handlerThread.getLooper()), 0, null, null);
        latch.await();
    }

    /**
     * Tells the app to request audio focus.
     */
    public static void requestAudioFocus(String packageName) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, CLASS_NAME_PROCESS_BROADCAST));
        intent.setAction("REQUEST_AUDIO_FOCUS");
        sendBroadcastAndWait(intent);
    }

    /**
     * Tells the app to abandon audio focus.
     */
    public static void abandonAudioFocus(String packageName) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, CLASS_NAME_PROCESS_BROADCAST));
        intent.setAction("ABANDON_AUDIO_FOCUS");
        sendBroadcastAndWait(intent);
    }

    /**
     * Checks whether the given package is installed on /system and was not updated.
     */
    static boolean isSystemAppWithoutUpdate(String packageName) {
        PackageInfo pi = getPackageInfo(packageName);
        if (pi == null) {
            return false;
        } else {
            return ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    && ((pi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0);
        }
    }

    /**
     * Checks whether a given package is installed for only the given user, from a list of users.
     * @param packageName the package to check
     * @param userIdToCheck the user id of the user to check
     * @param userIds a list of user ids to check
     * @return {@code true} if the package is only installed for the given user,
     *         {@code false} otherwise.
     */
    public static boolean isOnlyInstalledForUser(String packageName, int userIdToCheck,
            List<Integer> userIds) {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();
        for (int userId: userIds) {
            List<PackageInfo> installedPackages;
            if (userId != userIdToCheck) {
                installedPackages = pm.getInstalledPackagesAsUser(
                        PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX),
                        userId);
                for (PackageInfo pi : installedPackages) {
                    if (pi.packageName.equals(packageName)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the session by session Id, or null if no session is found.
     */
    public static PackageInstaller.SessionInfo getStagedSessionInfo(int sessionId) {
        PackageInstaller packageInstaller = getPackageInstaller();
        for (PackageInstaller.SessionInfo session : packageInstaller.getStagedSessions()) {
            if (session.getSessionId() == sessionId) {
                return session;
            }
        }
        return null;
    }

    /**
     * Assert that the given staged session is abandoned. The method assumes that the given session
     * is staged.
     * @param sessionId of the staged session
     */
    public static void assertStagedSessionIsAbandoned(int sessionId) {
        assertThat(getStagedSessionInfo(sessionId)).isNull();
    }

    /**
     * A functional interface representing an operation that takes no arguments,
     * returns no arguments and might throw a {@link Throwable} of any kind.
     */
    @FunctionalInterface
    private interface Operation {
        /**
         * This is the method that gets called for any object that implements this interface.
         */
        void run() throws Throwable;
    }

    /**
     * Runs {@link Operation} and expects a {@link Throwable} of the given class to be thrown.
     *
     * @param expectedThrowableClass class or superclass of the expected throwable.
     */
    private static void assertThrows(Class expectedThrowableClass, String expectedFailMessage,
            Operation operation) {
        try {
            operation.run();
        } catch (Throwable expected) {
            assertThat(expectedThrowableClass.isAssignableFrom(expected.getClass())).isTrue();
            assertThat(expected.getMessage()).containsMatch(expectedFailMessage);
            return;
        }
        fail("Operation was expected to fail!");
    }
}
