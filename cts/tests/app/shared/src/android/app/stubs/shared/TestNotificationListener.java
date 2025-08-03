/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.ConditionVariable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class TestNotificationListener extends NotificationListenerService {
    public static final String TAG = "TestNotificationListener";
    public static final String PKG = "android.app.stubs";
    private static final long CONNECTION_TIMEOUT_MS = 1000;

    private ArrayList<String> mTestPackages = new ArrayList<>();

    public ArrayList<StatusBarNotification> mPosted = new ArrayList<>();
    public ArrayList<StatusBarNotification> mRemoved = new ArrayList<>();
    public Map<String, Integer> mRemovedReasons = new HashMap<>();
    public RankingMap mRankingMap;
    public Map<String, Boolean> mIntercepted = new HashMap<>();

    private CountDownLatch mPostedLatch = null;
    private CountDownLatch mRankingUpdateLatch = null;
    private CountDownLatch mRemovedLatch = null;

    /**
     * This controls whether there is a listener connected or not. Depending on the method, if the
     * caller tries to use a listener after it has disconnected, NMS can throw a SecurityException.
     *
     * There is no race between onListenerConnected() and onListenerDisconnected() because they are
     * called in the same thread. The value that getInstance() sees is guaranteed to be the value
     * that was set by onListenerConnected() because of the happens-before established by the
     * condition variable.
     */
    private static final ConditionVariable INSTANCE_AVAILABLE = new ConditionVariable(false);
    private static TestNotificationListener sNotificationListenerInstance = null;
    boolean isConnected;

    @Override
    public void onCreate() {
        super.onCreate();
        mTestPackages.add(PKG);
    }

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "onListenerConnected() called");
        super.onListenerConnected();
        sNotificationListenerInstance = this;
        INSTANCE_AVAILABLE.open();
        isConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected() called");
        INSTANCE_AVAILABLE.close();
        sNotificationListenerInstance = null;
        isConnected = false;
    }

    public static TestNotificationListener getInstance() {
        if (INSTANCE_AVAILABLE.block(CONNECTION_TIMEOUT_MS)) {
            return sNotificationListenerInstance;
        }
        return null;
    }

    public void resetData() {
        Log.d(TAG, "resetData() called");
        mPosted.clear();
        mRemovedReasons.clear();
        mRemoved.clear();
        mIntercepted.clear();
    }

    public void addTestPackage(String packageName) {
        mTestPackages.add(packageName);
    }

    public void removeTestPackage(String packageName) {
        mTestPackages.remove(packageName);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !mTestPackages.contains(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationPosted: skipping handling sbn=" + sbn + " testPackages="
                    + listToString(mTestPackages));
            return;
        } else {
            Log.d(TAG, "onNotificationPosted: sbn=" + sbn + " testPackages=" + listToString(
                    mTestPackages));
        }
        mPosted.add(sbn);
        maybeUpdateLatch(mPostedLatch);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (sbn == null || !mTestPackages.contains(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationPosted: skipping handling sbn=" + sbn + " testPackages="
                    + listToString(mTestPackages));
            return;
        } else {
            Log.d(TAG, "onNotificationPosted: sbn=" + sbn + " testPackages=" + listToString(
                    mTestPackages));
        }
        mRankingMap = rankingMap;
        updateInterceptedRecords(rankingMap);
        mPosted.add(sbn);
        maybeUpdateLatch(mPostedLatch);
    }

    public void maybeUpdateLatch(CountDownLatch latch) {
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !mTestPackages.contains(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationRemoved: skipping handling sbn=" + sbn + " testPackages="
                    + listToString(mTestPackages));
            return;
        } else {
            Log.d(TAG, "onNotificationRemoved: sbn=" + sbn
                    + " testPackages=" + listToString(mTestPackages));
        }
        mPosted.remove(sbn);
        mRemovedReasons.put(sbn.getKey(), -1);
        mRemoved.add(sbn);
        maybeUpdateLatch(mRemovedLatch);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        if (sbn == null || !mTestPackages.contains(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationRemoved: skipping handling sbn=" + sbn + " testPackages="
                    + listToString(mTestPackages));
            return;
        } else {
            Log.d(TAG, "onNotificationRemoved: sbn=" + sbn + " reason=" + reason
                    + " testPackages=" + listToString(mTestPackages));
        }
        mRankingMap = rankingMap;
        updateInterceptedRecords(rankingMap);
        mPosted.remove(sbn);
        mRemovedReasons.put(sbn.getKey(), reason);
        mRemoved.add(sbn);
        maybeUpdateLatch(mRemovedLatch);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        Log.d(TAG, "onNotificationRankingUpdate() called rankingMap=[" + rankingMap + "]");
        mRankingMap = rankingMap;
        updateInterceptedRecords(rankingMap);
    }

    // update the local cache of intercepted records based on the given ranking map; should be run
    // every time the listener gets updated ranking map info
    private void updateInterceptedRecords(RankingMap rankingMap) {
        maybeUpdateLatch(mRankingUpdateLatch);
        for (String key : rankingMap.getOrderedKeys()) {
            Ranking rank = new Ranking();
            if (rankingMap.getRanking(key, rank)) {
                // matchesInterruptionFilter is true if the notification can bypass and false if
                // blocked so the "is intercepted" boolean is the opposite of that.
                mIntercepted.put(key, !rank.matchesInterruptionFilter());
            }
        }
    }

    public CountDownLatch setPostedCountDown(int countDownNumber) {
        mPostedLatch = new CountDownLatch(countDownNumber);
        return mPostedLatch;
    }

    public CountDownLatch setRankingUpdateCountDown(int countDownNumber) {
        mRankingUpdateLatch = new CountDownLatch(countDownNumber);
        return mRankingUpdateLatch;
    }

    public CountDownLatch setRemovedCountDown(int countDownNumber) {
        mRemovedLatch = new CountDownLatch(countDownNumber);
        return mRemovedLatch;
    }

    @Override
    public String toString() {
        return "TestNotificationListener{"
                + "mTestPackages=[" + listToString(mTestPackages)
                + "], mPosted=[" + listToString(mPosted)
                + ", mRemoved=[" + listToString(mRemovedReasons.values())
                + "]}";
    }

    private String listToString(Collection<?> list) {
        return list.stream().map(Object::toString).collect(Collectors.joining(","));
    }
}
