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
package com.android.car.notification;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.notification.headsup.CarHeadsUpNotificationAppContainer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NotificationListenerService that fetches all notifications from system.
 */
public class CarNotificationListener extends NotificationListenerService implements
        CarHeadsUpNotificationManager.OnHeadsUpNotificationStateChange {
    private static final String TAG = "CarNotificationListener";
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    static final String ACTION_LOCAL_BINDING = "local_binding";
    static final int NOTIFY_NOTIFICATION_POSTED = 1;
    static final int NOTIFY_NOTIFICATION_REMOVED = 2;
    static final int NOTIFY_RANKING_UPDATED = 3;
    /** Temporary {@link Ranking} object that serves as a reused value holder */
    final private Ranking mTemporaryRanking = new Ranking();

    private Handler mHandler;
    private RankingMap mRankingMap;
    private CarHeadsUpNotificationManager mHeadsUpManager;
    private NotificationDataManager mNotificationDataManager;
    private boolean mIsNotificationPanelVisible;
    private boolean mIsListenerConnected;

    /**
     * Map that contains all the active notifications that are not currently HUN. These
     * notifications may or may not be visible to the user if they get filtered out. The only time
     * these will be removed from the map is when the {@llink NotificationListenerService} calls the
     * onNotificationRemoved method. New notifications will be added to this map if the notification
     * is posted as a non-HUN or when a HUN's state is changed to non-HUN.
     */
    private ConcurrentMap<String, AlertEntry> mActiveNotifications = new ConcurrentHashMap<>();

    /**
     * Call this if to register this service as a system service and connect to HUN. This is useful
     * if the notification service is being used as a lib instead of a standalone app. The
     * standalone app version has a manifest entry that will have the same effect.
     *
     * @param context Context required for registering the service.
     * @param carUxRestrictionManagerWrapper will have the heads up manager registered with it.
     * @param carHeadsUpNotificationManager HUN controller.
     */
    public void registerAsSystemService(Context context,
            CarUxRestrictionManagerWrapper carUxRestrictionManagerWrapper,
            CarHeadsUpNotificationManager carHeadsUpNotificationManager) {
        try {
            mNotificationDataManager = NotificationDataManager.getInstance();
            registerAsSystemService(context,
                    new ComponentName(context.getPackageName(), getClass().getCanonicalName()),
                    NotificationUtils.getCurrentUser(context));
            mHeadsUpManager = carHeadsUpNotificationManager;
            mHeadsUpManager.registerHeadsUpNotificationStateChangeListener(this);
            carUxRestrictionManagerWrapper.setCarHeadsUpNotificationManager(
                    carHeadsUpNotificationManager);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register notification listener", e);
        }
    }

    @VisibleForTesting
    void setNotificationDataManager(NotificationDataManager notificationDataManager) {
        mNotificationDataManager = notificationDataManager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationDataManager = NotificationDataManager.getInstance();
        NotificationApplication app = (NotificationApplication) getApplication();

        mHeadsUpManager = new CarHeadsUpNotificationManager(/* context= */ this,
                app.getClickHandlerFactory(), new CarHeadsUpNotificationAppContainer(this));
        mHeadsUpManager.registerHeadsUpNotificationStateChangeListener(this);
        app.getCarUxRestrictionWrapper().setCarHeadsUpNotificationManager(mHeadsUpManager);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHeadsUpManager.unregisterListeners();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return ACTION_LOCAL_BINDING.equals(intent.getAction())
                ? new LocalBinder() : super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (sbn == null) {
            Log.e(TAG, "onNotificationPosted: StatusBarNotification is null");
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onNotificationPosted: " + sbn);
            Log.d(TAG, "Is incoming notification a group summary?: "
                    + sbn.getNotification().isGroupSummary());
        }
        if (!isNotificationForCurrentUser(sbn)) {
            if (DEBUG) {
                Log.d(TAG, "Notification is not for current user: " + sbn);
                Log.d(TAG, "Notification user: " + sbn.getUser().getIdentifier());
                Log.d(TAG, "Current user: " + NotificationUtils.getCurrentUser(getContext()));
            }
            return;
        }
        AlertEntry alertEntry = new AlertEntry(sbn);
        onNotificationRankingUpdate(rankingMap);
        notifyNotificationPosted(alertEntry);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) {
            Log.e(TAG, "onNotificationRemoved: StatusBarNotification is null");
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onNotificationRemoved: " + sbn);
        }

        if (!isNotificationForCurrentUser(sbn)) {
            if (DEBUG) {
                Log.d(TAG, "Notification is not for current user: " + sbn);
                Log.d(TAG, "Notification user: " + sbn.getUser().getIdentifier());
                Log.d(TAG, "Current user: " + NotificationUtils.getCurrentUser(getContext()));
            }
            return;
        }

        AlertEntry alertEntry = mActiveNotifications.get(sbn.getKey());

        if (alertEntry != null) {
            mActiveNotifications.remove(alertEntry.getKey());
        } else {
            // HUN notifications are not tracked in mActiveNotifications but still need to be
            // removed
            alertEntry = new AlertEntry(sbn);
        }

        removeNotification(alertEntry);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        mRankingMap = rankingMap;
        boolean overrideGroupKeyUpdated = false;
        for (AlertEntry alertEntry : mActiveNotifications.values()) {
            if (updateOverrideGroupKey(alertEntry)) {
                overrideGroupKeyUpdated = true;
            }
        }
        if (overrideGroupKeyUpdated) {
            sendNotificationEventToHandler(/* alertEntry= */ null, NOTIFY_RANKING_UPDATED);
        }
    }

    private boolean updateOverrideGroupKey(AlertEntry alertEntry) {
        if (!mRankingMap.getRanking(alertEntry.getKey(), mTemporaryRanking)) {
            if (DEBUG) {
                Log.d(TAG, "OverrideGroupKey not applied: " + alertEntry);
            }
            return false;
        }

        String oldOverrideGroupKey =
                alertEntry.getStatusBarNotification().getOverrideGroupKey();
        String newOverrideGroupKey = getOverrideGroupKey(alertEntry.getKey());
        if (Objects.equals(oldOverrideGroupKey, newOverrideGroupKey)) {
            return false;
        }
        alertEntry.getStatusBarNotification().setOverrideGroupKey(newOverrideGroupKey);
        return true;
    }

    /**
     * Get the override group key of a {@link AlertEntry} given its key.
     */
    @Nullable
    private String getOverrideGroupKey(String key) {
        if (mRankingMap != null) {
            mRankingMap.getRanking(key, mTemporaryRanking);
            return mTemporaryRanking.getOverrideGroupKey();
        }
        return null;
    }

    /**
     * Get all active notifications that are not heads-up notifications.
     *
     * @return a map of all active notifications with key being the notification key.
     */
    Map<String, AlertEntry> getNotifications() {
        return mActiveNotifications.entrySet().stream()
                .filter(x -> (isNotificationForCurrentUser(
                        x.getValue().getStatusBarNotification())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public RankingMap getCurrentRanking() {
        return mRankingMap;
    }

    @Override
    public void onListenerConnected() {
        mActiveNotifications = Stream.of(getActiveNotifications()).collect(
                Collectors.toConcurrentMap(StatusBarNotification::getKey, AlertEntry::new));
        mRankingMap = super.getCurrentRanking();
        mIsListenerConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        mIsListenerConnected = false;
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    /**
     * Clears all local cached variables.
     * Note: This is a blocking call so should not execute any long-running or time-consuming tasks
     * like storing cache.
     */
    public void clearCache() {
        mHeadsUpManager.clearCache();
        mNotificationDataManager.clearAll();
        mActiveNotifications.clear();
    }

    /**
     * Called when Notification Panel's visibility changes.
     */
    public void onVisibilityChanged(boolean isVisible) {
        mIsNotificationPanelVisible = isVisible;
        if (mIsNotificationPanelVisible) {
            mHeadsUpManager.releaseQueue();
        }
    }


    private void notifyNotificationPosted(AlertEntry alertEntry) {
        if (isNotificationHigherThanLowImportance(alertEntry)) {
            mNotificationDataManager.addNewMessageNotification(alertEntry);
        } else {
            mNotificationDataManager.untrackUnseenNotification(alertEntry);
        }

        boolean isShowingHeadsUp = false;
        if (!mIsNotificationPanelVisible
                || !CarHeadsUpNotificationManager.isHeadsUpDismissible(alertEntry)) {
            isShowingHeadsUp = mHeadsUpManager.maybeShowHeadsUp(alertEntry, getCurrentRanking(),
                    mActiveNotifications);
        }
        if (DEBUG) {
            Log.d(TAG, "Is " + alertEntry + " shown as HUN?: " + isShowingHeadsUp);
        }
        if (!isShowingHeadsUp) {
            updateOverrideGroupKey(alertEntry);
            postNewNotification(alertEntry);
        }
    }

    private boolean isNotificationForCurrentUser(StatusBarNotification sbn) {
        // Notifications should only be shown for the current user and the the notifications from
        // the system when CarNotification is running as SystemUI component.
        return (sbn.getUser().getIdentifier() == NotificationUtils.getCurrentUser(getContext())
                || sbn.getUser().getIdentifier() == UserHandle.USER_ALL);
    }

    @Override
    public void onStateChange(AlertEntry alertEntry,
            CarHeadsUpNotificationManager.HeadsUpState headsUpState) {
        if (headsUpState == CarHeadsUpNotificationManager.HeadsUpState.DISMISSED
                || headsUpState == CarHeadsUpNotificationManager.HeadsUpState.REMOVED_FROM_QUEUE) {
            updateOverrideGroupKey(alertEntry);
            postNewNotification(alertEntry);
        }
    }

    class LocalBinder extends Binder {
        public CarNotificationListener getService() {
            return CarNotificationListener.this;
        }
    }

    private void postNewNotification(AlertEntry alertEntry) {
        mActiveNotifications.put(alertEntry.getKey(), alertEntry);
        sendNotificationEventToHandler(alertEntry, NOTIFY_NOTIFICATION_POSTED);
    }

    private void removeNotification(AlertEntry alertEntry) {
        mHeadsUpManager.maybeRemoveHeadsUp(alertEntry);
        sendNotificationEventToHandler(alertEntry, NOTIFY_NOTIFICATION_REMOVED);
    }

    private void sendNotificationEventToHandler(AlertEntry alertEntry, int eventType) {
        if (mHandler == null) {
            return;
        }
        Message msg = Message.obtain(mHandler);
        msg.what = eventType;
        msg.obj = alertEntry;
        mHandler.sendMessage(msg);
    }

    private boolean isNotificationHigherThanLowImportance(AlertEntry alertEntry) {
        Ranking ranking = new NotificationListenerService.Ranking();
        mRankingMap.getRanking(alertEntry.getKey(), ranking);
        return ranking.getImportance() > NotificationManager.IMPORTANCE_LOW;
    }

    @VisibleForTesting
    boolean getIsListenerConnected() {
        return mIsListenerConnected;
    }
}
