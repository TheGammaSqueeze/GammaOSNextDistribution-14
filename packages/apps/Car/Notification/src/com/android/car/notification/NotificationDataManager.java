/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.notification;

import android.app.NotificationManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.car.assist.client.CarAssistUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the additional state of notifications. This class is not thread safe and should
 * only be called from the main thread.
 */
public class NotificationDataManager {
    /**
     * Interface for listeners that want to register for receiving updates to the notification
     * unseen count.
     */
    public interface OnUnseenCountUpdateListener {
        /**
         * Called when unseen notification count is changed.
         */
        void onUnseenCountUpdate();
    }

    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String TAG = "NotificationDataManager";

    private static NotificationDataManager sInstance;

    /**
     * Map that contains the key of all message notifications, mapped to whether or not the key's
     * notification should be muted.
     *
     * Muted notifications should show an "Unmute" button on their notification and should not
     * trigger the HUN when new notifications arrive with the same key. Unmuted should show a "Mute"
     * button on their notification and should trigger the HUN. Both should update the notification
     * in the Notification Center.
     */
    private final ConcurrentHashMap<String, Boolean> mMessageNotificationToMuteStateMap =
            new ConcurrentHashMap<>();

    /**
     * Map that contains the key of all unseen notifications.
     */
    private final ConcurrentHashMap<String, Boolean> mUnseenNotificationMap =
            new ConcurrentHashMap<>();

    /**
     * List of notifications that are visible to the user.
     */
    private final ConcurrentHashMap.KeySetView<AlertEntry, Boolean> mVisibleNotifications =
            ConcurrentHashMap.newKeySet();

    private OnUnseenCountUpdateListener mOnUnseenCountUpdateListener;

    /**
     * @return the {@link NotificationDataManager} singleton
     */
    public static NotificationDataManager getInstance() {
        if (sInstance == null) {
            sInstance = new NotificationDataManager();
        }
        return sInstance;
    }

    @VisibleForTesting
    static void refreshInstance() {
        sInstance = null;
    }

    private NotificationDataManager() {
        clearAll();
    }

    /**
     * Sets listener for unseen notification count change event.
     *
     * @param listener UnseenCountUpdateListener
     */
    public void setOnUnseenCountUpdateListener(OnUnseenCountUpdateListener listener) {
        mOnUnseenCountUpdateListener = listener;
    }

    void addNewMessageNotification(AlertEntry alertEntry) {
        if (CarAssistUtils.isCarCompatibleMessagingNotification(
                alertEntry.getStatusBarNotification())) {
            mMessageNotificationToMuteStateMap
                    .putIfAbsent(alertEntry.getKey(), /* muteState= */ false);

            if (mUnseenNotificationMap.containsKey(alertEntry.getKey())) {
                mUnseenNotificationMap.put(alertEntry.getKey(), true);
                mVisibleNotifications.add(alertEntry);

                notifyUnseenCountUpdateListeners();
            }
        }
    }

    void untrackUnseenNotification(AlertEntry alertEntry) {
        if (mUnseenNotificationMap.containsKey(alertEntry.getKey())) {
            mUnseenNotificationMap.remove(alertEntry.getKey());
            notifyUnseenCountUpdateListeners();
        }
    }

    void updateUnseenNotificationGroups(List<NotificationGroup> notificationGroups) {
        List<AlertEntry> alertEntries = new ArrayList<>();

        notificationGroups.forEach(group -> {
            if (group.getGroupSummaryNotification() != null) {
                alertEntries.add(group.getGroupSummaryNotification());
            }
            alertEntries.addAll(group.getChildNotifications());
        });

        updateUnseenAlertEntries(alertEntries);
    }

    void updateUnseenAlertEntries(List<AlertEntry> alertEntries) {
        Set<String> currentNotificationKeys = new HashSet<>();

        Collections.addAll(currentNotificationKeys,
                mUnseenNotificationMap.keySet().toArray(new String[0]));

        for (AlertEntry alertEntry : alertEntries) {
            // add new notifications
            mUnseenNotificationMap.putIfAbsent(alertEntry.getKey(), true);

            // sbn exists in both sets.
            currentNotificationKeys.remove(alertEntry.getKey());
        }

        // These keys were removed from notificationGroups. Remove from mUnseenNotificationMap.
        for (String notificationKey : currentNotificationKeys) {
            mUnseenNotificationMap.remove(notificationKey);
        }

        notifyUnseenCountUpdateListeners();
    }

    boolean isNotificationSeen(AlertEntry alertEntry) {
        return !mUnseenNotificationMap.getOrDefault(alertEntry.getKey(), false);
    }

    /**
     * Returns the mute state of the notification, or false if notification does not have a mute
     * state. Only message notifications can be muted.
     **/
    public boolean isMessageNotificationMuted(AlertEntry alertEntry) {
        if (!mMessageNotificationToMuteStateMap.containsKey(alertEntry.getKey())) {
            addNewMessageNotification(alertEntry);
        }

        return mMessageNotificationToMuteStateMap.getOrDefault(alertEntry.getKey(), false);
    }

    /**
     * If {@param sbn} is a messaging notification, this function will toggle its mute state. This
     * state determines whether or not a HUN will be shown on future updates to the notification.
     * It also determines the title of the notification's "Mute" button.
     **/
    public void toggleMute(AlertEntry alertEntry) {
        if (CarAssistUtils.isCarCompatibleMessagingNotification(
                alertEntry.getStatusBarNotification())) {
            String sbnKey = alertEntry.getKey();
            Boolean currentMute = mMessageNotificationToMuteStateMap.get(sbnKey);
            if (currentMute != null) {
                mMessageNotificationToMuteStateMap.put(sbnKey, !currentMute);
            } else {
                Log.e(TAG, "Msg notification was not initially added to the mute state map: "
                        + alertEntry.getKey());
            }
        }
    }

    /**
     * Clear unseen and mute notification state information.
     */
    public void clearAll() {
        mMessageNotificationToMuteStateMap.clear();
        mUnseenNotificationMap.clear();
        mVisibleNotifications.clear();
        notifyUnseenCountUpdateListeners();
    }

    /**
     * Uses the {@code alertEntries} to reset the visible notifications and marks them as seen.
     *
     * @param alertEntries List of {@link AlertEntry} that are currently visible to be marked seen.
     */
    void setVisibleNotificationsAsSeen(List<AlertEntry> alertEntries) {
        mVisibleNotifications.clear();
        for (AlertEntry alertEntry : alertEntries) {
            if (mUnseenNotificationMap.containsKey(alertEntry.getKey())) {
                mUnseenNotificationMap.put(alertEntry.getKey(), false);
                mVisibleNotifications.add(alertEntry);
            }
        }
        notifyUnseenCountUpdateListeners();
    }

    /**
     * @param alertEntry {@link AlertEntry} to be marked seen and notify listeners.
     */
    void setNotificationAsSeen(AlertEntry alertEntry) {
        mUnseenNotificationMap.put(alertEntry.getKey(), false);
        notifyUnseenCountUpdateListeners();
    }

    /**
     * Returns unseen notification count for higher than low importance notifications.
     */
    public int getNonLowImportanceUnseenNotificationCount(
            NotificationListenerService.RankingMap rankingMap) {
        final int[] unseenCount = {0};
        mUnseenNotificationMap.forEach((key, val) -> {
            if (val) {
                NotificationListenerService.Ranking ranking =
                        new NotificationListenerService.Ranking();
                rankingMap.getRanking(key, ranking);
                if (ranking.getImportance() > NotificationManager.IMPORTANCE_LOW) {
                    unseenCount[0]++;
                }
            }
        });
        if (DEBUG) {
            Log.d(TAG, "Unseen notification map: " + mUnseenNotificationMap);
        }
        return unseenCount[0];
    }

    /**
     * Returns a collection containing all notifications the user should be seeing right now.
     */
    public List<AlertEntry> getVisibleNotifications() {
        return new ArrayList<>(mVisibleNotifications);
    }

    /**
     * Returns seen notifications.
     */
    public String[] getSeenNotifications() {
        return mUnseenNotificationMap.entrySet()
                .stream()
                // Seen notifications have value set to false
                .filter(map -> !map.getValue())
                .map(map -> map.getKey())
                .toArray(String[]::new);
    }

    private void notifyUnseenCountUpdateListeners() {
        if (mOnUnseenCountUpdateListener == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Unseen notifications cleared");
        }
        mOnUnseenCountUpdateListener.onUnseenCountUpdate();
    }
}
