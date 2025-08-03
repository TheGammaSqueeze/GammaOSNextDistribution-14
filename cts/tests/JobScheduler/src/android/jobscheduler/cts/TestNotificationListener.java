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

package android.jobscheduler.cts;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.compatibility.common.util.ScreenUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestNotificationListener extends NotificationListenerService {
    private static final String TAG = "TestNotificationListener";
    private static final long SLEEP_TIME_MS = 1000;

    private final ArrayList<String> mTestPackages = new ArrayList<>();

    public BlockingQueue<StatusBarNotification> mPosted = new ArrayBlockingQueue<>(10);
    public ArrayList<StatusBarNotification> mRemoved = new ArrayList<>();

    private static TestNotificationListener sNotificationListenerInstance = null;
    boolean mIsConnected;

    public static String getId() {
        return String.format("%s/%s", TestNotificationListener.class.getPackage().getName(),
                TestNotificationListener.class.getName());
    }

    public static ComponentName getComponentName() {
        return new ComponentName(TestNotificationListener.class.getPackage().getName(),
                TestNotificationListener.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "onListenerConnected #" + this.hashCode());
        sNotificationListenerInstance = this;
        mIsConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected #" + this.hashCode());
        sNotificationListenerInstance = null;
        mIsConnected = false;
    }

    public static TestNotificationListener getInstance() throws Exception {
        waitUntil("Notification listener not instantiated", 15 /* seconds */,
                () -> sNotificationListenerInstance != null);
        return sNotificationListenerInstance;
    }

    public void addTestPackage(String packageName) {
        mTestPackages.add(packageName);
    }

    public StatusBarNotification getFirstNotificationFromPackage(String packageName)
            throws Exception {
        final long maxWaitMs = 30_000L;
        final int numAttempts = (int) (maxWaitMs / SLEEP_TIME_MS) + 1;
        for (int i = 0; i < numAttempts; ++i) {
            StatusBarNotification sbn = mPosted.poll(SLEEP_TIME_MS, TimeUnit.MILLISECONDS);
            if (sbn != null && sbn.getPackageName().equals(packageName)) {
                return sbn;
            }
        }
        return null;
    }

    public static void toggleListenerAccess(Context context, boolean on) throws Exception {
        SystemUtil.runShellCommand("cmd notification"
                + " " + (on ? "allow_listener" : "disallow_listener")
                + " " + getId());

        final ComponentName listenerComponent = getComponentName();
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        assertEquals(listenerComponent + " has not been " + (on ? "allowed" : "disallowed"),
                on, nm.isNotificationListenerAccessGranted(listenerComponent));
    }

    public void resetData() {
        mPosted.clear();
        mRemoved.clear();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.v(TAG, "notification posted: " + sbn);
        if (!mTestPackages.contains(sbn.getPackageName())) {
            Log.d(TAG, "Ignoring notification from " + sbn.getPackageName()
                    + " because it's not in " + mTestPackages);
            return;
        }
        Log.v(TAG, "adding to added: " + sbn);
        mPosted.add(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.v(TAG, "notification removed: " + sbn);
        if (!mTestPackages.contains(sbn.getPackageName())) {
            return;
        }
        Log.v(TAG, "adding to removed: " + sbn);
        mRemoved.add(sbn);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
    }

    public static class NotificationHelper implements AutoCloseable {
        private final Context mContext;
        private final TestNotificationListener mNotificationListener;
        private final String mTestPackage;

        public NotificationHelper(Context context, String testPackage) throws Exception {
            mContext = context;
            TestNotificationListener.toggleListenerAccess(context, true);
            mNotificationListener = TestNotificationListener.getInstance();
            mNotificationListener.addTestPackage(testPackage);
            mTestPackage = testPackage;
            ScreenUtils.setScreenOn(true);
        }

        public void assertNotificationsRemoved() throws Exception {
            waitUntil("Notification wasn't removed", 15 /* seconds */,
                    () -> {
                        StatusBarNotification[] activeNotifications =
                                mNotificationListener.getActiveNotifications();
                        for (StatusBarNotification sbn : activeNotifications) {
                            if (sbn.getPackageName().equals(mTestPackage)) {
                                return false;
                            }
                        }
                        return true;
                    });
        }

        public void clickNotification() throws Exception {
            StatusBarNotification sbn =
                    mNotificationListener.getFirstNotificationFromPackage(mTestPackage);
            assertNotNull(sbn);
            sbn.getNotification().contentIntent.send();
        }

        public void close() throws Exception {
            TestNotificationListener.toggleListenerAccess(mContext, false);
        }

        public StatusBarNotification getNotification() throws Exception {
            return mNotificationListener.getFirstNotificationFromPackage(mTestPackage);
        }
    }
}
