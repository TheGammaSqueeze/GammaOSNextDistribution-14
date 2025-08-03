/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.stubs.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.PendingIntent.CanceledException;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.base.Objects;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class NotificationHelper {

    private static final String TAG = NotificationHelper.class.getSimpleName();
    public static final long SHORT_WAIT_TIME = 100;
    public static final long MAX_WAIT_TIME = 2000;

    public enum SEARCH_TYPE {
        /**
         * Search for the notification only within the posted app. This returns enqueued
         * as well as posted notifications, so use with caution.
         */
        APP,
        /**
         * Search for the notification across all apps. Makes a binder call from the NLS to
         * check currently posted notifications for all apps, which means it can return
         * notifications the NLS hasn't been informed about yet.
         */
        LISTENER,
        /**
         * Search for the notification across all apps. Looks only in the list of notifications
         * that the listener has been informed about via onNotificationPosted.
         */
        POSTED,
        /**
         * Search for the notification across all apps. Looks only in the list of notifications
         * that the listener has been informed about via onNotificationRemoved.
         */
        REMOVED,
        /**
         * Search for the notification across all apps. Looks only in the list of notifications
         * that are snoozed by the system
         */
        SNOOZED,
    }

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private TestNotificationListener mNotificationListener;
    private TestNotificationAssistant mAssistant;

    public NotificationHelper(Context context) {
        mContext = context;
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
    }

    public void clickNotification(int notificationId, boolean searchAll) throws CanceledException {
        findPostedNotification(null, notificationId,
                searchAll ? SEARCH_TYPE.LISTENER : SEARCH_TYPE.APP)
                .getNotification().contentIntent.send();
    }

    public StatusBarNotification findPostedNotification(String tag, int id,
            SEARCH_TYPE searchType) {
        // notification posting is asynchronous so it may take a few hundred ms to appear.
        // we will check for it for up to MAX_WAIT_TIME ms before giving up.
        for (long totalWait = 0; totalWait < MAX_WAIT_TIME; totalWait += SHORT_WAIT_TIME) {
            StatusBarNotification n = findNotificationNoWait(tag, id, searchType);
            if (n != null) {
                return n;
            }
            try {
                Thread.sleep(SHORT_WAIT_TIME);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return findNotificationNoWait(null, id, searchType);
    }

    /**
     * Returns true if the notification cannot be found. Polls for the notification to account for
     * delays in posting
     */
    public boolean isNotificationGone(int id, SEARCH_TYPE searchType) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to MAX_WAIT_TIME ms before giving up.
        boolean found = false;
        for (long totalWait = 0; totalWait < MAX_WAIT_TIME; totalWait += SHORT_WAIT_TIME) {
            // Need reset flag.
            found = false;
            for (StatusBarNotification sbn : getNotifications(searchType)) {
                Log.d(TAG, "Found " + sbn.getKey());
                if (sbn.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
            try {
                Thread.sleep(SHORT_WAIT_TIME);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return !found;
    }

    /**
     * Checks whether the NLS has received a removal event for this notification
     */
    public boolean isNotificationGone(String key) {
        for (long totalWait = 0; totalWait < MAX_WAIT_TIME; totalWait += SHORT_WAIT_TIME) {
            if (mNotificationListener.mRemovedReasons.containsKey(key)) {
                return true;
            }
            try {
                Thread.sleep(SHORT_WAIT_TIME);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return false;
    }

    private StatusBarNotification findNotificationNoWait(String tag, int id,
            SEARCH_TYPE searchType) {
        for (StatusBarNotification sbn : getNotifications(searchType)) {
            if (sbn.getId() == id && Objects.equal(sbn.getTag(), tag)) {
                return sbn;
            }
        }
        return null;
    }

    private ArrayList<StatusBarNotification> getNotifications(SEARCH_TYPE searchType) {
        switch (searchType) {
            case APP:
                return new ArrayList<>(
                        Arrays.asList(mNotificationManager.getActiveNotifications()));
            case POSTED:
                return new ArrayList(mNotificationListener.mPosted);
            case REMOVED:
                return new ArrayList<>(mNotificationListener.mRemoved);
            case SNOOZED:
                return new ArrayList<>(
                        Arrays.asList(mNotificationListener.getSnoozedNotifications()));
            case LISTENER:
            default:
                return new ArrayList<>(
                        Arrays.asList(mNotificationListener.getActiveNotifications()));
        }
    }

    public TestNotificationListener enableListener(String pkg) throws IOException {
        String command = " cmd notification allow_listener "
                + pkg + "/" + TestNotificationListener.class.getName();
        runCommand(command, InstrumentationRegistry.getInstrumentation());
        mNotificationListener = TestNotificationListener.getInstance();
        if (mNotificationListener != null) {
            mNotificationListener.addTestPackage(pkg);
        }
        return mNotificationListener;
    }

    public void disableListener(String pkg) throws IOException {
        final ComponentName component =
                new ComponentName(pkg, TestNotificationListener.class.getName());
        String command = " cmd notification disallow_listener " + component.flattenToString();

        runCommand(command, InstrumentationRegistry.getInstrumentation());

        final NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        assertEquals(component + " has incorrect listener access",
                false, nm.isNotificationListenerAccessGranted(component));
    }

    public TestNotificationAssistant enableAssistant(String pkg) throws IOException {
        final ComponentName component =
                new ComponentName(pkg, TestNotificationAssistant.class.getName());

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE",
                        "android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE");
        mNotificationManager.setNotificationAssistantAccessGranted(component, true);

        assertTrue(component + " has not been allowed",
                mNotificationManager.isNotificationAssistantAccessGranted(component));
        assertEquals(component, mNotificationManager.getAllowedNotificationAssistant());

        mAssistant = TestNotificationAssistant.getInstance();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
        return mAssistant;
    }

    public void disableAssistant(String pkg) throws IOException {
        final ComponentName component =
                new ComponentName(pkg, TestNotificationAssistant.class.getName());

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE",
                        "android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE");
        mNotificationManager.setNotificationAssistantAccessGranted(component, false);

        assertTrue(component + " has not been disallowed",
                !mNotificationManager.isNotificationAssistantAccessGranted(component));
        assertNotEquals(component, mNotificationManager.getAllowedNotificationAssistant());

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void runCommand(String command, Instrumentation instrumentation)
            throws IOException {
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        // Execute command
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) {
                    // discard output
                }
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        }
    }
}
