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

package com.android.car.notification;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TaskStackListener;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;

import androidx.annotation.AnyThread;

import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Queue for throttling heads up notifications.
 */
public class CarHeadsUpNotificationQueue implements
        CarHeadsUpNotificationManager.OnHeadsUpNotificationStateChange {
    private static final String TAG = CarHeadsUpNotificationQueue.class.getSimpleName();
    private static final String NOTIFICATION_CHANNEL_ID = "HUN_QUEUE_CHANNEL_ID";
    private static final int NOTIFICATION_ID = 2000;
    static final String CATEGORY_HUN_QUEUE_INTERNAL = "HUN_QUEUE_INTERNAL";

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final PriorityQueue<String> mPriorityQueue;
    private final ActivityTaskManager mActivityTaskManager;
    private final CarHeadsUpNotificationQueueCallback mQueueCallback;
    private final TaskStackListener mTaskStackListener;
    private final ScheduledExecutorService mScheduledExecutorService;
    private final long mNotificationExpirationTimeFromQueueWhenDriving;
    private final long mNotificationExpirationTimeFromQueueWhenParked;
    private final boolean mExpireHeadsUpWhileDriving;
    private final boolean mExpireHeadsUpWhileParked;
    private final int mHeadsUpDelayDuration;
    private final boolean mDismissHeadsUpWhenNotificationCenterOpens;
    private final String mNotificationTitleInParkState;
    private final String mNotificationTitleInDriveState;
    private final String mNotificationDescription;
    private final Set<String> mNotificationCategoriesForImmediateShow;
    private final Set<String> mPackagesToThrottleHeadsUp;
    private final Map<String, AlertEntry> mKeyToAlertEntryMap;
    private final Set<Integer> mThrottledDisplays;
    private NotificationListenerService.RankingMap mRankingMap;
    private Clock mClock;
    @VisibleForTesting
    ScheduledFuture<?> mScheduledFuture;
    private boolean mIsActiveUxRestriction;
    private boolean mIsOngoingHeadsUpFlush;
    @VisibleForTesting
    boolean mAreNotificationsExpired;
    @VisibleForTesting
    boolean mCancelInternalNotificationOnStateChange;

    public CarHeadsUpNotificationQueue(Context context, ActivityTaskManager activityTaskManager,
            NotificationManager notificationManager,
            ScheduledExecutorService scheduledExecutorService,
            CarHeadsUpNotificationQueueCallback queuePopCallback) {
        mContext = context;
        mActivityTaskManager = activityTaskManager;
        mQueueCallback = queuePopCallback;
        mNotificationManager = notificationManager;
        mKeyToAlertEntryMap = new HashMap<>();
        mThrottledDisplays = new HashSet<>();

        mExpireHeadsUpWhileDriving = context.getResources().getBoolean(
                R.bool.config_expireHeadsUpWhenDriving);
        mExpireHeadsUpWhileParked = context.getResources().getBoolean(
                R.bool.config_expireHeadsUpWhenParked);
        mDismissHeadsUpWhenNotificationCenterOpens = context.getResources().getBoolean(
                R.bool.config_dismissHeadsUpWhenNotificationCenterOpens);
        mNotificationExpirationTimeFromQueueWhenDriving = context.getResources().getInteger(
                R.integer.headsup_queue_expire_driving_duration_ms);
        mNotificationExpirationTimeFromQueueWhenParked = context.getResources().getInteger(
                R.integer.headsup_queue_expire_parked_duration_ms);
        mHeadsUpDelayDuration = mContext.getResources().getInteger(
                R.integer.headsup_delay_duration);
        mNotificationCategoriesForImmediateShow = Set.of(context.getResources().getStringArray(
                R.array.headsup_category_immediate_show));
        mPackagesToThrottleHeadsUp = Set.of(context.getResources().getStringArray(
                R.array.headsup_throttled_foreground_packages));
        String notificationChannelName = context.getResources().getString(
                R.string.hun_suppression_channel_name);
        mNotificationTitleInParkState = context.getResources().getString(
                R.string.hun_suppression_notification_title_park);
        mNotificationTitleInDriveState = context.getResources().getString(
                R.string.hun_suppression_notification_title_drive);
        mNotificationDescription = context.getResources().getString(
                R.string.hun_suppression_notification_description);

        mPriorityQueue = new PriorityQueue<>(
                new PrioritisedNotifications(context.getResources().getStringArray(
                        R.array.headsup_category_priority), mKeyToAlertEntryMap));

        mClock = Clock.systemUTC();

        mTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                    throws RemoteException {
                super.onTaskMovedToFront(taskInfo);
                if (taskInfo.baseActivity == null) {
                    return;
                }
                if (mPackagesToThrottleHeadsUp.contains(taskInfo.baseActivity.getPackageName())) {
                    mThrottledDisplays.add(taskInfo.displayAreaFeatureId);
                    return;
                }

                if (mThrottledDisplays.remove(taskInfo.displayAreaFeatureId)) {
                    scheduleCallback(mHeadsUpDelayDuration);
                }

            }
        };
        mActivityTaskManager.registerTaskStackListener(mTaskStackListener);

        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, notificationChannelName,
                NotificationManager.IMPORTANCE_HIGH));

        mScheduledExecutorService = scheduledExecutorService;
    }

    /**
     * Adds an {@link AlertEntry} into the queue.
     */
    public void addToQueue(AlertEntry alertEntry,
            NotificationListenerService.RankingMap rankingMap) {
        mRankingMap = rankingMap;
        if (isCategoryImmediateShow(alertEntry.getNotification().category)) {
            mQueueCallback.getActiveHeadsUpNotifications().forEach(mQueueCallback::dismissHeadsUp);
            mQueueCallback.showAsHeadsUp(alertEntry, rankingMap);
            return;
        }
        boolean headsUpExistsInQueue = mKeyToAlertEntryMap.containsKey(alertEntry.getKey());
        mKeyToAlertEntryMap.put(alertEntry.getKey(), alertEntry);
        if (!headsUpExistsInQueue) {
            mPriorityQueue.add(alertEntry.getKey());
        }
        scheduleCallback(/* delay= */ 0);
    }

    /**
     * Removes the {@link AlertEntry} from the queue if present.
     */
    public boolean removeFromQueue(AlertEntry alertEntry) {
        mKeyToAlertEntryMap.remove(alertEntry.getKey());
        return mPriorityQueue.remove(alertEntry.getKey());
    }

    /**
     * Removes all notifications from the queue and optionally dismisses the active HUNs.
     * Active HUN is not dismissed if it is not dismissible.
     */
    public void releaseQueue() {
        mIsOngoingHeadsUpFlush = true;

        if (mDismissHeadsUpWhenNotificationCenterOpens) {
            mQueueCallback.getActiveHeadsUpNotifications().stream()
                    .filter(CarHeadsUpNotificationManager::isHeadsUpDismissible)
                    .forEach(mQueueCallback::dismissHeadsUp);
        }
        while (!mPriorityQueue.isEmpty()) {
            String key = mPriorityQueue.poll();
            if (mKeyToAlertEntryMap.containsKey(key)) {
                mQueueCallback.removedFromHeadsUpQueue(mKeyToAlertEntryMap.get(key));
                mKeyToAlertEntryMap.remove(key);
            }
        }
        mIsOngoingHeadsUpFlush = false;
    }

    @VisibleForTesting
    void scheduleCallback(long delay) {
        if (!canShowHeadsUp()) {
            return;
        }

        if (mScheduledFuture != null && !mScheduledFuture.isDone()) {
            long delayLeft = mScheduledFuture.getDelay(TimeUnit.MILLISECONDS);
            if (delay < delayLeft) {
                return;
            }
            mScheduledFuture.cancel(/* mayInterruptIfRunning= */ true);

        }
        mScheduledFuture = mScheduledExecutorService.schedule(this::triggerCallback,
                delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Triggers {@code CarHeadsUpNotificationQueueCallback.showAsHeadsUp} on non expired HUN and
     * {@code CarHeadsUpNotificationQueueCallback.removedFromHeadsUpQueue} for expired HUN if
     * there are no active HUNs.
     */
    @VisibleForTesting
    void triggerCallback() {
        if (!canShowHeadsUp()) {
            return;
        }

        AlertEntry alertEntry;
        do {
            if (mPriorityQueue.isEmpty()) {
                if (mAreNotificationsExpired) {
                    mAreNotificationsExpired = false;
                    mNotificationManager.notifyAsUser(TAG, NOTIFICATION_ID,
                            getUserNotificationForExpiredHun(),
                            UserHandle.of(NotificationUtils.getCurrentUser(mContext)));
                    mCancelInternalNotificationOnStateChange = true;
                }
                return;
            }
            String key = mPriorityQueue.poll();
            alertEntry = mKeyToAlertEntryMap.get(key);
            mKeyToAlertEntryMap.remove(key);

            if (alertEntry == null) {
                continue;
            }

            long timeElapsed = mClock.millis() - alertEntry.getPostTime();
            boolean isExpired = (mIsActiveUxRestriction && mExpireHeadsUpWhileDriving
                    && mNotificationExpirationTimeFromQueueWhenDriving < timeElapsed) || (
                    !mIsActiveUxRestriction && mExpireHeadsUpWhileParked
                            && mNotificationExpirationTimeFromQueueWhenParked < timeElapsed);

            if (isExpired && !CATEGORY_HUN_QUEUE_INTERNAL.equals(
                    alertEntry.getNotification().category)) {
                mAreNotificationsExpired = true;
                mQueueCallback.removedFromHeadsUpQueue(alertEntry);
                alertEntry = null;
            }
        } while (alertEntry == null);
        mQueueCallback.showAsHeadsUp(alertEntry, mRankingMap);
    }

    private boolean canShowHeadsUp() {
        return mQueueCallback.getActiveHeadsUpNotifications().isEmpty()
                && mThrottledDisplays.isEmpty()
                && !mIsOngoingHeadsUpFlush
                && !mPriorityQueue.isEmpty();
    }

    /**
     * Returns {@code true} if the {@code category} should be shown immediately.
     */
    private boolean isCategoryImmediateShow(@Nullable String category) {
        return category != null && mNotificationCategoriesForImmediateShow.contains(category);
    }

    @VisibleForTesting
    Notification getUserNotificationForExpiredHun() {
        return new Notification
                .Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setCategory(CATEGORY_HUN_QUEUE_INTERNAL)
                .setContentTitle(mIsActiveUxRestriction ? mNotificationTitleInDriveState
                        : mNotificationTitleInParkState)
                .setContentText(mNotificationDescription)
                .setSmallIcon(R.drawable.car_ui_icon_settings)
                .build();
    }

    @Override
    public void onStateChange(AlertEntry alertEntry,
            CarHeadsUpNotificationManager.HeadsUpState headsUpState) {
        if (headsUpState == CarHeadsUpNotificationManager.HeadsUpState.SHOWN
                || headsUpState == CarHeadsUpNotificationManager.HeadsUpState.REMOVED_FROM_QUEUE) {
            return;
        }
        if (mCancelInternalNotificationOnStateChange && TextUtils.equals(
                alertEntry.getNotification().category, CATEGORY_HUN_QUEUE_INTERNAL)) {
            mCancelInternalNotificationOnStateChange = false;
            mAreNotificationsExpired = false;
            mNotificationManager.cancelAsUser(TAG, NOTIFICATION_ID,
                    UserHandle.of(NotificationUtils.getCurrentUser(mContext)));
        }
        scheduleCallback(mHeadsUpDelayDuration);
    }

    /**
     * Called when distraction optimisation state changes.
     * {@link CarUxRestrictionsManager} can be used to get this state.
     */
    public void setActiveUxRestriction(boolean isActiveUxRestriction) {
        mIsActiveUxRestriction = isActiveUxRestriction;
    }

    /**
     * Unregisters all listeners.
     */
    public void unregisterListeners() {
        mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
    }

    /**
     * Clears all local cached variables and cancels scheduled executor tasks.
     */
    public void clearCache() {
        mPriorityQueue.clear();
        mKeyToAlertEntryMap.clear();
        mThrottledDisplays.clear();
        if (mScheduledFuture != null) {
            mScheduledFuture.cancel(/* mayInterruptIfRunning= */ true);
        }
    }

    /**
     * Callback to communicate status of HUN.
     */
    public interface CarHeadsUpNotificationQueueCallback {
        /**
         * Show the AlertEntry as HUN.
         */
        @AnyThread
        void showAsHeadsUp(AlertEntry alertEntry,
                NotificationListenerService.RankingMap rankingMap);

        /**
         * AlertEntry removed from the queue without being shown as HUN.
         */
        void removedFromHeadsUpQueue(AlertEntry alertEntry);

        /**
         * Dismiss the active HUN.
         *
         * @param alertEntry the {@link AlertEntry} to be dismissed if present as active HUN
         */
        void dismissHeadsUp(@Nullable AlertEntry alertEntry);

        /**
         * @return list of active HUNs.
         */
        List<AlertEntry> getActiveHeadsUpNotifications();
    }

    /**
     * Used to assign priority for {@link AlertEntry} based on category and postTime.
     */
    private static class PrioritisedNotifications implements Comparator<String> {
        private final String[] mNotificationsCategoryInPriorityOrder;
        private final Map<String, AlertEntry> mKeyToAlertEntryMap;

        PrioritisedNotifications(String[] notificationsCategoryInPriorityOrder,
                Map<String, AlertEntry> mapKeyToAlertEntry) {
            mNotificationsCategoryInPriorityOrder = notificationsCategoryInPriorityOrder;
            mKeyToAlertEntryMap = mapKeyToAlertEntry;
        }

        public int compare(String aKey, String bKey) {
            AlertEntry a = mKeyToAlertEntryMap.get(aKey);
            AlertEntry b = mKeyToAlertEntryMap.get(bKey);
            if (a == null || b == null) {
                return 0;
            }

            String categoryA = a.getNotification().category;
            String categoryB = b.getNotification().category;

            if (CATEGORY_HUN_QUEUE_INTERNAL.equals(categoryA)) {
                return 1;
            }
            if (CATEGORY_HUN_QUEUE_INTERNAL.equals(categoryB)) {
                return -1;
            }

            int priorityA = -1;
            int priorityB = -1;

            for (int i = 0; i < mNotificationsCategoryInPriorityOrder.length; i++) {
                if (mNotificationsCategoryInPriorityOrder[i].equals(categoryA)) {
                    priorityA = i;
                }
                if (mNotificationsCategoryInPriorityOrder[i].equals(categoryB)) {
                    priorityB = i;
                }
            }
            if (priorityA != priorityB) {
                return Integer.compare(priorityA, priorityB);
            } else {
                return Long.compare(a.getPostTime(), b.getPostTime());
            }
        }
    }

    @VisibleForTesting
    PriorityQueue<String> getPriorityQueue() {
        return mPriorityQueue;
    }

    @VisibleForTesting
    void addToPriorityQueue(AlertEntry alertEntry) {
        mKeyToAlertEntryMap.put(alertEntry.getKey(), alertEntry);
        mPriorityQueue.add(alertEntry.getKey());
    }

    @VisibleForTesting
    void setClock(Clock clock) {
        mClock = clock;
    }
}
