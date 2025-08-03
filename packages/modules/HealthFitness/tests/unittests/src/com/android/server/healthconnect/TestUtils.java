/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect;

import android.app.UiAutomation;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public final class TestUtils {
    public static final UserHandle TEST_USER = UserHandle.of(UserHandle.myUserId());

    public static void waitForTaskToFinishSuccessfully(Runnable task) throws TimeoutException {
        Instant startTime = Instant.now();
        while (true) {
            try {
                task.run();
                return;
            } catch (Exception e) {
                // ignore
            } finally {
                if (ChronoUnit.SECONDS.between(startTime, Instant.now()) > 3) {
                    throw new TimeoutException();
                }
            }
        }
    }

    static void waitForCondition(Predicate<Void> predicate, int timeoutSeconds)
            throws TimeoutException {
        Instant startTime = Instant.now();
        while (!predicate.test(null)) {
            if (ChronoUnit.SECONDS.between(startTime, Instant.now()) > timeoutSeconds) {
                throw new TimeoutException();
            }
        }
    }

    public static void waitForAllScheduledTasksToComplete() throws TimeoutException {
        waitForCondition(
                (unused) ->
                        (HealthConnectThreadScheduler.sInternalBackgroundExecutor.getTaskCount()
                                        == HealthConnectThreadScheduler.sInternalBackgroundExecutor
                                                .getCompletedTaskCount())
                                || (HealthConnectThreadScheduler.sControllerExecutor.getTaskCount()
                                        == HealthConnectThreadScheduler.sControllerExecutor
                                                .getCompletedTaskCount())
                                || (HealthConnectThreadScheduler.sBackgroundThreadExecutor
                                                .getTaskCount()
                                        == HealthConnectThreadScheduler.sBackgroundThreadExecutor
                                                .getCompletedTaskCount())
                                || (HealthConnectThreadScheduler.sForegroundExecutor.getTaskCount()
                                        == HealthConnectThreadScheduler.sForegroundExecutor
                                                .getCompletedTaskCount()),
                15);
    }

    public static long getInternalBackgroundExecutorTaskCount() {
        return HealthConnectThreadScheduler.sInternalBackgroundExecutor.getTaskCount();
    }

    /** Runs a {@link Runnable} adopting a subset of Shell's permissions. */
    public static void runWithShellPermissionIdentity(
            @NonNull Runnable runnable, String... permissions) {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
