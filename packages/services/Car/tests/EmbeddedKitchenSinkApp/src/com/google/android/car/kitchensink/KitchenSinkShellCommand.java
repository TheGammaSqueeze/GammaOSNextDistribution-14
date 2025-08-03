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
package com.google.android.car.kitchensink;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserManager;
import android.security.AttestedKeyPair;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.widget.Toast;

import com.google.android.car.kitchensink.drivemode.DriveModeSwitchController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@code KitchenSink}'s own {@code cmd} implementation.
 *
 * <p>Usage: {$code adb shell dumpsys activity
 * com.google.android.car.kitchensink/.KitchenSinkActivity cmd <CMD>}
 *
 * <p><p>Note</p>: this class is meant only for "global" commands (i.e., actions that could be
 * applied regardless of the current {@code KitchenSink} fragment), or for commands that don't have
 * an equivalent UI (for example, the key attestation ones). If you want to provide commands to
 * control the behavior of a fragment, you should implement {@code dump} on that fragment directly
 * (see
 * {@link com.google.android.car.kitchensink.VirtualDisplayFragment#dump(String,FileDescriptor,PrintWriter,String[])}
 * as an example);
 *
 * <p><p>Note</p>: you must launch {@code KitchenSink} first. Example: {@code
 * adb shell am start com.google.android.car.kitchensink/.KitchenSinkActivity}
 */
final class KitchenSinkShellCommand {

    private static final String TAG = "KitchenSinkCmd";

    private static final String CMD_HELP = "help";
    private static final String CMD_GET_DELEGATED_SCOPES = "get-delegated-scopes";
    private static final String CMD_IS_UNINSTALL_BLOCKED = "is-uninstall-blocked";
    private static final String CMD_SET_UNINSTALL_BLOCKED = "set-uninstall-blocked";
    private static final String CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR =
            "generate-device-attestation-key-pair";
    private static final String CMD_POST_NOTIFICATION = "post-notification";
    private static final String CMD_POST_TOAST = "post-toast";
    private static final String CMD_SET_DRIVE_MODE_SWITCH= "set-drive-mode-switch";

    private static final String ARG_VERBOSE = "-v";
    private static final String ARG_VERBOSE_FULL = "--verbose";
    private static final String ARG_USES_APP_CONTEXT = "--app-context";
    private static final String ARG_LONG_TOAST = "--long-toast";

    private final Context mContext;
    private final @Nullable DevicePolicyManager mDpm;
    private final IndentingPrintWriter mWriter;
    private final String[] mArgs;
    private final int mNotificationId;

    @Nullable // dynamically created on post() method
    private Handler mHandler;

    private int mNextArgIndex;

    KitchenSinkShellCommand(Context context, PrintWriter writer, String[] args, int id) {
        mContext = context;
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mWriter = new IndentingPrintWriter(writer);
        mArgs = args;
        mNotificationId = id;
    }

    void run() {
        if (mArgs.length == 0) {
            showHelp("Error: must pass an argument");
            return;
        }
        String cmd = mArgs[0];
        switch (cmd) {
            case CMD_HELP:
                showHelp("KitchenSink Command-Line Interface");
                break;
            case CMD_GET_DELEGATED_SCOPES:
                getDelegatedScopes();
                break;
            case CMD_IS_UNINSTALL_BLOCKED:
                isUninstallBlocked();
                break;
            case CMD_SET_UNINSTALL_BLOCKED:
                setUninstallBlocked();
                break;
            case CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR:
                generateDeviceAttestationKeyPair();
                break;
            case CMD_POST_NOTIFICATION:
                postNotification();
                break;
            case CMD_POST_TOAST:
                postToast();
                break;
            case CMD_SET_DRIVE_MODE_SWITCH:
                setDriveModeSwitch();
                break;
            default:
                showHelp("Invalid command: %s", cmd);
        }
    }

    private void showHelp(String headerMessage, Object... headerArgs) {
        if (headerMessage != null) {
            mWriter.printf(headerMessage, headerArgs);
            mWriter.print(". ");
        }
        mWriter.println("Available commands:\n");

        mWriter.increaseIndent();
        showCommandHelp("Shows this help message.",
                CMD_HELP);
        showCommandHelp("Lists delegated scopes set by the device admin.",
                CMD_GET_DELEGATED_SCOPES);
        showCommandHelp("Checks whether uninstalling the given app is blocked.",
                CMD_IS_UNINSTALL_BLOCKED, "<PKG>");
        showCommandHelp("Blocks / unblocks uninstalling the given app.",
                CMD_SET_UNINSTALL_BLOCKED, "<PKG>", "<true|false>");
        showCommandHelp("Generates a device attestation key.",
                CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR, "<ALIAS>", "[FLAGS]");
        showCommandHelp("Post Notification.",
                CMD_POST_NOTIFICATION, "<MESSAGE>");
        showCommandHelp("Post a Toast with the given message and options.",
                CMD_POST_TOAST, "[" + ARG_VERBOSE + "|" + ARG_VERBOSE_FULL + "]",
                "[" + ARG_USES_APP_CONTEXT + "]", "[" + ARG_LONG_TOAST + "]",
                "<MESSAGE>");
        showCommandHelp("Enables / Disables the DriveMode Switch in the System UI.",
                CMD_SET_DRIVE_MODE_SWITCH, "<true|false>");
        mWriter.decreaseIndent();
    }

    private void showCommandHelp(String description, String cmd, String... args) {
        mWriter.printf("%s", cmd);
        if (args != null) {
            for (String arg : args) {
                mWriter.printf(" %s", arg);
            }
        }
        mWriter.println(":");
        mWriter.increaseIndent();
        mWriter.printf("%s\n\n", description);
        mWriter.decreaseIndent();
    }

    private void getDelegatedScopes() {
        if (!supportDevicePolicyManagement()) return;

        List<String> scopes = mDpm.getDelegatedScopes(/* admin= */ null, mContext.getPackageName());
        printCollection("delegated scope", scopes);
    }

    private void isUninstallBlocked() {
        if (!supportDevicePolicyManagement()) return;

        String packageName = getNextArg();
        boolean isIt = mDpm.isUninstallBlocked(/* admin= */ null, packageName);
        mWriter.println(isIt);
    }

    private void setUninstallBlocked() {
        if (!supportDevicePolicyManagement()) return;

        String packageName = getNextArg();
        boolean blocked = getNextBooleanArg();

        Log.i(TAG, "Calling dpm.setUninstallBlocked(" + packageName + ", " + blocked + ")");
        mDpm.setUninstallBlocked(/* admin= */ null, packageName, blocked);
    }

    private void generateDeviceAttestationKeyPair() {
        if (!supportDevicePolicyManagement()) return;

        String alias = getNextArg();
        int flags = getNextOptionalIntArg(/* defaultValue= */ 0);
        // Cannot call dpm.generateKeyPair() on main thread
        warnAboutAsyncCall();
        post(() -> handleDeviceAttestationKeyPair(alias, flags));
    }

    private void handleDeviceAttestationKeyPair(String alias, int flags) {
        KeyGenParameterSpec keySpec = buildRsaKeySpecWithKeyAttestation(alias);
        String algorithm = "RSA";
        Log.i(TAG, "calling dpm.generateKeyPair(alg=" + algorithm + ", spec=" + keySpec
                + ", flags=" + flags + ")");
        AttestedKeyPair kp = mDpm.generateKeyPair(/* admin= */ null, algorithm, keySpec, flags);
        Log.i(TAG, "key: " + kp);
    }

    private void postNotification() {
        String message = getNextArg();
        String channelId = "importance_high";

        NotificationManager notificationMgr = mContext.getSystemService(NotificationManager.class);
        notificationMgr.createNotificationChannel(
                new NotificationChannel(channelId, "Importance High",
                        NotificationManager.IMPORTANCE_HIGH));
        Notification notification = new Notification
                .Builder(mContext, channelId)
                .setContentTitle("Car Emergency")
                .setContentText(message)
                .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                .setColor(mContext.getColor(android.R.color.holo_red_light))
                .setColorized(true)
                .setSmallIcon(R.drawable.car_ic_mode)
                .build();
        notificationMgr.notify(mNotificationId, notification);
        Log.i(TAG, "Post Notification: id=" + mNotificationId + ", message=" + message);
    }

    private void postToast() {
        boolean verbose = false;
        boolean usesAppContext = false;
        boolean longToast = false;
        String messageArg = null;
        String nextArg = null;

        while ((nextArg = getNextOptioanlArg()) != null) {
            switch (nextArg) {
                case ARG_VERBOSE:
                case ARG_VERBOSE_FULL:
                    verbose = true;
                    break;
                case ARG_USES_APP_CONTEXT:
                    usesAppContext = true;
                    break;
                case ARG_LONG_TOAST:
                    longToast = true;
                    break;
                default:
                    messageArg = nextArg;
            }
        }
        if (messageArg == null) {
            mWriter.println("Message is required");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        Context context = usesAppContext ? mContext.getApplicationContext() : mContext;
        if (verbose) {
            messageBuilder.append("user=").append(context.getUserId())
                    .append(", context=").append(context.getClass().getSimpleName())
                    .append(", contextDisplay=").append(context.getDisplayId())
                    .append(", userDisplay=").append(context.getSystemService(UserManager.class)
                            .getMainDisplayIdAssignedToUser())
                    .append(", length=").append(longToast ? "long" : "short")
                    .append(", message=");

        }
        String message = messageBuilder.append(messageArg).toString();
        Log.i(TAG, "Posting toast: " + message);
        Toast.makeText(context, message, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    private void setDriveModeSwitch() {
        boolean value = getNextBooleanArg();
        DriveModeSwitchController driveModeSwitchController = new DriveModeSwitchController(
                mContext
        );
        driveModeSwitchController.setDriveMode(value);
    }

    private void warnAboutAsyncCall() {
        mWriter.printf("Command will be executed asynchronally; use `adb logcat %s *:s` for result"
                + "\n", TAG);
    }

    private void post(Runnable r) {
        if (mHandler == null) {
            HandlerThread handlerThread = new HandlerThread("KitchenSinkShellCommandThread");
            Log.i(TAG, "Starting " + handlerThread);
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
        }
        Log.d(TAG, "posting runnable");
        mHandler.post(r);
    }

    private boolean supportDevicePolicyManagement() {
        if (mDpm == null) {
            mWriter.println("Device Policy Management not supported by device");
            return false;
        }
        return true;
    }

    private String getNextArgAndIncrementCounter() {
        return mArgs[++mNextArgIndex];
    }

    @Nullable
    private String getNextOptioanlArg() {
        if (++mNextArgIndex >= mArgs.length) {
            return null;
        }
        return mArgs[mNextArgIndex];
    }


    private String getNextArg() {
        try {
            return getNextArgAndIncrementCounter();
        } catch (Exception e) {
            Log.e(TAG, "getNextArg() failed", e);
            mWriter.println("Error: missing argument");
            mWriter.flush();
            throw new IllegalArgumentException(
                    "Missing argument. Args=" + Arrays.toString(mArgs), e);
        }
    }

    private int getNextOptionalIntArg(int defaultValue) {
        try {
            return Integer.parseInt(getNextArgAndIncrementCounter());
        } catch (Exception e) {
            Log.d(TAG, "Exception getting optional arg: " + e);
            return defaultValue;
        }
    }

    private boolean getNextBooleanArg() {
        String arg = getNextArg();
        return Boolean.parseBoolean(arg);
    }

    private void printCollection(String nameOnSingular, Collection<String> collection) {
        if (collection.isEmpty()) {
            mWriter.printf("No %ss\n", nameOnSingular);
            return;
        }
        int size = collection.size();
        mWriter.printf("%d %s%s:\n", size, nameOnSingular, size == 1 ? "" : "s");
        collection.forEach((s) -> mWriter.printf("  %s\n", s));
    }

    // Copied from CTS' KeyGenerationUtils
    private static KeyGenParameterSpec buildRsaKeySpecWithKeyAttestation(String alias) {
        return new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setIsStrongBoxBacked(false)
                        .setAttestationChallenge(new byte[] {
                                'a', 'b', 'c'
                        })
                        .build();
    }
}
