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

package com.android.sts.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.os.Handler;
import android.os.HandlerThread;

import com.android.compatibility.common.util.UserSettings;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class SystemUtil {
    private static final String TAG = "SystemUtil";
    public static final long DEFAULT_MAX_POLL_TIME_MS = 30_000L;
    public static final long DEFAULT_POLL_TIME_MS = 100L;

    /**
     * Set the value of a device setting and set it back to old value upon closing.
     *
     * @param instrumentation {@link Instrumentation} instance, obtained from a test running in
     *     instrumentation framework
     * @param namespace "system", "secure", or "global"
     * @param key setting key to set
     * @param value setting value to set to
     * @return AutoCloseable that resets the setting back to existing value upon closing.
     */
    public static AutoCloseable withSetting(
            Instrumentation instrumentation,
            final String namespace,
            final String key,
            String value) {
        UserSettings userSettings = new UserSettings(UserSettings.Namespace.of(namespace));
        String getSettingRes = userSettings.get(key);
        final Optional<String> oldSetting = Optional.ofNullable(getSettingRes);
        userSettings.set(key, value);

        String getSettingCurrent = userSettings.get(key);
        Optional<String> currSetting = Optional.ofNullable(getSettingCurrent);
        assumeThat(
                String.format("Could not set %s:%s to %s", namespace, key, value),
                currSetting.isPresent() ? currSetting.get().trim() : null,
                equalTo(value));

        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                if (!oldSetting.isPresent()) {
                    userSettings.delete(key);
                } else {
                    String oldValue = oldSetting.get().trim();
                    userSettings.set(key, oldValue);
                    String failMsg =
                            String.format("could not reset '%s' back to '%s'", key, oldValue);
                    String getSettingCurrent = userSettings.get(key);
                    Optional<String> currSetting = Optional.ofNullable(getSettingCurrent);
                    assumeThat(
                            failMsg,
                            currSetting.isPresent() ? currSetting.get().trim() : null,
                            equalTo(oldValue));
                }
            }
        };
    }

    /**
     * Poll on a condition supplied by the user.
     *
     * @param waitCondition returns true when the polling condition is met, false otherwise.
     * @return boolean value of {@code waitCondition}.
     * @throws IllegalArgumentException when {@code pollingTime} is not a positive ineteger and is
     *     not less than {@code maxPollingTime}.
     * @throws InterruptedException if the current thread is interrupted.
     */
    public static boolean poll(BooleanSupplier waitCondition)
            throws IllegalArgumentException, InterruptedException {
        return poll(waitCondition, DEFAULT_POLL_TIME_MS, DEFAULT_MAX_POLL_TIME_MS);
    }

    /**
     * Poll on a condition supplied by the user.
     *
     * @param waitCondition returns true when the polling condition is met, false otherwise.
     * @param pollingTime wait between successive calls to fetch value of {@code waitCondition} in
     *     milliseconds
     * @param maxPollingTime maximum waiting time before return.
     * @return boolean value of {@code waitCondition}.
     * @throws IllegalArgumentException when {@code pollingTime} is not a positive ineteger and is
     *     not less than {@code maxPollingTime}.
     * @throws InterruptedException if the current thread is interrupted.
     */
    public static boolean poll(BooleanSupplier waitCondition, long pollingTime, long maxPollingTime)
            throws IllegalArgumentException, InterruptedException {
        // The value of pollingTime should be a positive integer
        if (pollingTime <= 0) {
            throw new IllegalArgumentException("pollingTime should be a positive integer");
        }

        // The value of pollingTime should be less than maxPollingTime
        if (pollingTime >= maxPollingTime) {
            throw new IllegalArgumentException("pollingTime should be less than maxPollingTime");
        }

        // Use handlerThread to run task in a separate thread.
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        final Semaphore semaphore = new Semaphore(0);
        final long startTime = System.currentTimeMillis();
        do {
            // Check for the status.
            if (waitCondition.getAsBoolean()) {
                return true;
            }

            // Wait before checking status again.
            handler.postDelayed(() -> semaphore.release(), pollingTime);
            assumeTrue(semaphore.tryAcquire(maxPollingTime, TimeUnit.MILLISECONDS));
        } while (System.currentTimeMillis() - startTime <= maxPollingTime);
        return waitCondition.getAsBoolean();
    }
}
