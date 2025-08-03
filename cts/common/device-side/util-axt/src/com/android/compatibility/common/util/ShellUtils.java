/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides Shell-based utilities such as running a command.
 */
public final class ShellUtils {

    private static final String TAG = "ShellHelper";

    private static final UserHelper sUserHelper = new UserHelper();

    /**
     * Runs a Shell command with a timeout, returning a trimmed response.
     */
    @NonNull
    public static String runShellCommandWithTimeout(@NonNull String command, long timeoutInSecond)
            throws TimeoutException {
        AtomicReference<Exception> exception = new AtomicReference<>(null);
        AtomicReference<String> result = new AtomicReference<>(null);

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                result.set(runShellCommand(command));
            } catch (Exception e) {
                exception.set(e);
            } finally {
                latch.countDown();
            }
        }).start();

        try {
            if (!latch.await(timeoutInSecond, TimeUnit.SECONDS)) {
                throw new TimeoutException("Command: '" + command + "' could not run in "
                        + timeoutInSecond + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (exception.get() != null) {
            throw new AndroidRuntimeException(exception.get());
        }

        return result.get();
    }

    /**
     * Runs a Shell command, returning a trimmed response.
     */
    @NonNull
    public static String runShellCommand(@NonNull String template, Object...args) {
        final String command = String.format(template, args);
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            final String result = SystemUtil
                    .runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
            return TextUtils.isEmpty(result) ? "" : result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    /**
     * Tap on the view center, it may change window focus.
     */
    public static void tap(View view) {
        final int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        final int x = (int) (xy[0] + (viewWidth / 2.0f));
        final int y = (int) (xy[1] + (viewHeight / 2.0f));

        runShellCommand("%s tap %d %d", sUserHelper.getInputCmd("touchscreen"), x, y);
    }

    private ShellUtils() {
        throw new UnsupportedOperationException("contain static methods only");
    }

    /**
     * Simulates input of key event.
     *
     * @param keyCode key event to fire.
     */
    public static void sendKeyEvent(String keyCode) {
        runShellCommand("%s %s", sUserHelper.getInputCmd("keyevent"), keyCode);
    }

    /**
     * Allows an app to draw overlaid windows.
     */
    public static void setOverlayPermissions(@NonNull String packageName, boolean allowed) {
        final String action = allowed ? "allow" : "ignore";
        runShellCommand("%s %s SYSTEM_ALERT_WINDOW %s",
                sUserHelper.getAppopsCmd("set"), packageName, action);
    }
}
