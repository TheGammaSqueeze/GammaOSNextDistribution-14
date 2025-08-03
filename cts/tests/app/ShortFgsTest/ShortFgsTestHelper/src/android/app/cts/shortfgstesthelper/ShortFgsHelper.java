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
package android.app.cts.shortfgstesthelper;

import android.R.drawable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.Objects;
import java.util.function.Consumer;

public class ShortFgsHelper {
    private ShortFgsHelper() {
    }

    public static final String TAG = "ShortFgsTest";

    /**
     * Context that can be accessed globally. It's set by {@link MyApplication}.
     */
    public static Context sContext;

    /**
     * Package name of the main test. This is also the authority of CallProvider.
     */
    public static final String TEST_PACKAGE = "android.app.cts.shortfgstest";

    /**
     * Package name of this helper app.
     */
    public static final String HELPER_PACKAGE = "android.app.cts.shortfgstesthelper";

    /**
     * Second test package, it's used only to host a bound service that's bound by
     * {@link #HELPER_PACKAGE}.
     */
    public static final String HELPER_PACKAGE2 = "android.app.cts.shortfgstesthelper2";

    public static final String EXTRA_MESSAGE = "message";

    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + HELPER_PACKAGE;

    /** Component names for the services owned by the helper app. */
    public static ComponentName FGS0 = new ComponentName(HELPER_PACKAGE, Fgs0.class.getName());
    public static ComponentName FGS1 = new ComponentName(HELPER_PACKAGE, Fgs1.class.getName());
    public static ComponentName FGS2 = new ComponentName(HELPER_PACKAGE, Fgs2.class.getName());

    public static ComponentName BOUND_SERVICE = new ComponentName(
            HELPER_PACKAGE, BoundService.class.getName());

    /** Component names of {@link MyActivity}. */
    public static ComponentName ACTIVITY =
            new ComponentName(HELPER_PACKAGE, MyActivity.class.getName());

    /**
     * FGS0 in helper2
     */
    public static ComponentName FGS_B_0 = new ComponentName(HELPER_PACKAGE2, Fgs0.class.getName());

    /**
     * Bound service in helper2.
     */
    public static ComponentName BOUND_SERVICE_B = new ComponentName(
            HELPER_PACKAGE2, BoundService.class.getName());

    public static String ensureNotificationChannel() {
        sContext.getSystemService(NotificationManager.class)
                .createNotificationChannel(
                        new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                        NotificationManager.IMPORTANCE_DEFAULT));
        return NOTIFICATION_CHANNEL_ID;
    }

    public static Notification createNotification() {
        Notification.Builder builder =
                new Notification.Builder(sContext, ensureNotificationChannel())
                        .setContentTitle("Title: " + HELPER_PACKAGE)
                        .setSmallIcon(drawable.star_on);
        return builder.build();
    }

    /**
     * Set a {@link ShortFgsMessage} to an {@link Intent}.
     */
    public static Intent setMessage(Intent i, ShortFgsMessage message) {
        i.putExtra(EXTRA_MESSAGE, message);
        return i;
    }

    /**
     * Get a {@link ShortFgsMessage} from an {@link Intent}.
     */
    public static ShortFgsMessage getMessage(Intent i) {
        return Objects.requireNonNull(i.getParcelableExtra(EXTRA_MESSAGE, ShortFgsMessage.class));
    }

    /**
     * Sends a message back to the main test package.
     */
    public static void sendBackMessage(ShortFgsMessage m) {
        sendMessageToMainTest(m);
    }

    /**
     * Sends an "ack" message back to the main test package.
     */
    public static void sendBackAckMessage() {
        ShortFgsMessage m = new ShortFgsMessage();
        m.setAck(true);
        sendMessageToMainTest(m);
    }

    /**
     * Sends a class name and a method name back to the main test package.
     */
    public static void sendBackMethodName(Class<?> clazz, String methodName,
            Consumer<ShortFgsMessage> modifier) {
        ShortFgsMessage m = new ShortFgsMessage();
        m.setComponentName(new ComponentName(sContext, clazz.getName()));
        m.setMethodName(methodName);
        if (modifier != null) {
            modifier.accept(m);
        }
        sendBackMessage(m);
    }

    /**
     * Sends a class name and a method name back to the main test package.
     */
    public static void sendBackMethodName(Class<?> clazz, String methodName) {
        sendBackMethodName(clazz, methodName, (Consumer) null);
    }

    /**
     * Sends a class name and a method name back to the main test package.
     */
    public static void sendBackMethodName(Class<?> clazz, String methodName, ComponentName cn) {
        sendBackMethodName(clazz, methodName, (m) -> m.setComponentName(cn));
    }

    /**
     * Send a message to the main test app, via CallProvider.call().
     */
    public static ShortFgsMessage sendMessageToMainTest(ShortFgsMessage args) {
        final Bundle inBundle = new Bundle();
        inBundle.putParcelable(ShortFgsHelper.EXTRA_MESSAGE, args);

        final Bundle outBundle = sContext.getContentResolver().call(TEST_PACKAGE, "", "", inBundle);
        outBundle.setClassLoader(ShortFgsMessage.class.getClassLoader());

        return Objects.requireNonNull(
                outBundle.getParcelable(ShortFgsHelper.EXTRA_MESSAGE, ShortFgsMessage.class));
    }

    /**
     * Call CallProvider.call() to get the current test info.
     */
    public static ShortFgsMessage getCurrentTestInfo() {
        return sendMessageToMainTest(new ShortFgsMessage().setCallGetTestInfo(true));
    }

    public static String flattenComponentName(ComponentName cn) {
        if (cn == null) {
            return "(null)";
        }
        return cn.flattenToShortString();
    }
}
