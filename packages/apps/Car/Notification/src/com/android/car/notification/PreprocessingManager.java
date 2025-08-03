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

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationManager;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.car.notification.template.MessageNotificationViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Manager that filters, groups and ranks the notifications in the notification center.
 *
 * <p> Note that heads-up notifications have a different filtering mechanism and is managed by
 * {@link CarHeadsUpNotificationManager}.
 */
public class PreprocessingManager {

    /** Listener that will be notified when a call state changes. **/
    public interface CallStateListener {
        /**
         * @param isInCall is true when user is currently in a call.
         */
        void onCallStateChanged(boolean isInCall);
    }

    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "PreprocessingManager";

    private final String mEllipsizedSuffix;
    private final Context mContext;
    private final boolean mShowRecentsAndOlderHeaders;
    private final boolean mUseLauncherIcon;
    private final int mMinimumGroupingThreshold;

    private static PreprocessingManager sInstance;

    private int mMaxStringLength = Integer.MAX_VALUE;
    private Map<String, AlertEntry> mOldNotifications;
    private List<NotificationGroup> mOldProcessedNotifications;
    private NotificationListenerService.RankingMap mOldRankingMap;
    private NotificationDataManager mNotificationDataManager;

    private boolean mIsInCall;
    private List<CallStateListener> mCallStateListeners = new ArrayList<>();

    @VisibleForTesting
    final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mIsInCall = TelephonyManager.EXTRA_STATE_OFFHOOK
                        .equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE));
                for (CallStateListener listener : mCallStateListeners) {
                    listener.onCallStateChanged(mIsInCall);
                }
            }
        }
    };

    private PreprocessingManager(Context context) {
        mEllipsizedSuffix = context.getString(R.string.ellipsized_string);
        mContext = context;
        mNotificationDataManager = NotificationDataManager.getInstance();

        Resources resources = mContext.getResources();
        mShowRecentsAndOlderHeaders = resources.getBoolean(R.bool.config_showRecentAndOldHeaders);
        mUseLauncherIcon = resources.getBoolean(R.bool.config_useLauncherIcon);
        mMinimumGroupingThreshold = resources.getInteger(R.integer.config_minimumGroupingThreshold);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(mIntentReceiver, filter);
    }

    public static PreprocessingManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PreprocessingManager(context);
        }
        return sInstance;
    }

    @VisibleForTesting
    static void refreshInstance() {
        sInstance = null;
    }

    @VisibleForTesting
    void setNotificationDataManager(NotificationDataManager notificationDataManager) {
        mNotificationDataManager = notificationDataManager;
    }

    /**
     * Initialize the data when the UI becomes foreground.
     */
    public void init(Map<String, AlertEntry> notifications, RankingMap rankingMap) {
        mOldNotifications = notifications;
        mOldRankingMap = rankingMap;
        mOldProcessedNotifications =
                process(/* showLessImportantNotifications = */ false, notifications, rankingMap);
    }

    /**
     * Process the given notifications. In order for DiffUtil to work, the adapter needs a new
     * data object each time it updates, therefore wrapping the return value in a new list.
     *
     * @param showLessImportantNotifications whether less important notifications should be shown.
     * @param notifications the list of notifications to be processed.
     * @param rankingMap the ranking map for the notifications.
     * @return the processed notifications in a new list.
     */
    public List<NotificationGroup> process(boolean showLessImportantNotifications,
            Map<String, AlertEntry> notifications, RankingMap rankingMap) {
        return new ArrayList<>(
                rank(group(optimizeForDriving(
                                filter(showLessImportantNotifications,
                                        new ArrayList<>(notifications.values()),
                                        rankingMap))),
                        rankingMap));
    }

    /**
     * Create a new list of notifications based on existing list.
     *
     * @param showLessImportantNotifications whether less important notifications should be shown.
     * @param newRankingMap the latest ranking map for the notifications.
     * @return the new notification group list that should be shown to the user.
     */
    public List<NotificationGroup> updateNotifications(
            boolean showLessImportantNotifications,
            AlertEntry alertEntry,
            int updateType,
            RankingMap newRankingMap) {

        switch (updateType) {
            case CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED:
                // removal of a notification is the same as a normal preprocessing
                mOldNotifications.remove(alertEntry.getKey());
                mOldProcessedNotifications =
                        process(showLessImportantNotifications, mOldNotifications, mOldRankingMap);
                break;
            case CarNotificationListener.NOTIFY_NOTIFICATION_POSTED:
                AlertEntry notification = optimizeForDriving(alertEntry);
                boolean isUpdate = mOldNotifications.containsKey(notification.getKey());
                mOldNotifications.put(notification.getKey(), notification);
                // insert a new notification into the list
                mOldProcessedNotifications = new ArrayList<>(
                        additionalGroupAndRank((alertEntry), newRankingMap, isUpdate));
                break;
        }

        return mOldProcessedNotifications;
    }

    /** Add {@link CallStateListener} in order to be notified when call state is changed. **/
    public void addCallStateListener(CallStateListener listener) {
        if (mCallStateListeners.contains(listener)) return;
        mCallStateListeners.add(listener);
        listener.onCallStateChanged(mIsInCall);
    }

    /** Remove {@link CallStateListener} to stop getting notified when call state is changed. **/
    public void removeCallStateListener(CallStateListener listener) {
        mCallStateListeners.remove(listener);
    }

    /**
     * Returns true if the current {@link AlertEntry} should be filtered out and not
     * added to the list.
     */
    boolean shouldFilter(AlertEntry alertEntry, RankingMap rankingMap) {
        return isLessImportantForegroundNotification(alertEntry, rankingMap)
                || isMediaOrNavigationNotification(alertEntry);
    }

    /**
     * Filter a list of {@link AlertEntry}s according to OEM's configurations.
     */
    @VisibleForTesting
    protected List<AlertEntry> filter(
            boolean showLessImportantNotifications,
            List<AlertEntry> notifications,
            RankingMap rankingMap) {
        // remove notifications that should be filtered.
        if (!showLessImportantNotifications) {
            notifications.removeIf(alertEntry -> shouldFilter(alertEntry, rankingMap));
        }

        // Call notifications should not be shown in the panel.
        // Since they're shown as persistent HUNs, and notifications are not added to the panel
        // until after they're dismissed as HUNs, it does not make sense to have them in the panel,
        // and sequencing could cause them to be removed before being added here.
        notifications.removeIf(alertEntry -> Notification.CATEGORY_CALL.equals(
                alertEntry.getNotification().category));

        // HUN suppression notifications should not be shown in the panel.
        notifications.removeIf(alertEntry -> CarHeadsUpNotificationQueue.CATEGORY_HUN_QUEUE_INTERNAL
                .equals(alertEntry.getNotification().category));

        if (DEBUG) {
            Log.d(TAG, "Filtered notifications: " + notifications);
        }

        return notifications;
    }

    private boolean isLessImportantForegroundNotification(AlertEntry alertEntry,
            RankingMap rankingMap) {
        boolean isForeground =
                (alertEntry.getNotification().flags
                        & Notification.FLAG_FOREGROUND_SERVICE) != 0;

        if (!isForeground) {
            Log.d(TAG, alertEntry + " is not a foreground notification.");
            return false;
        }

        int importance = 0;
        NotificationListenerService.Ranking ranking =
                new NotificationListenerService.Ranking();
        if (rankingMap.getRanking(alertEntry.getKey(), ranking)) {
            importance = ranking.getImportance();
        }

        if (DEBUG) {
            if (importance < NotificationManager.IMPORTANCE_DEFAULT) {
                Log.d(TAG, alertEntry + " importance is insufficient to show in notification "
                        + "center");
            } else {
                Log.d(TAG, alertEntry + " importance is sufficient to show in notification "
                        + "center");
            }

            if (NotificationUtils.isSystemPrivilegedOrPlatformKey(mContext, alertEntry)) {
                Log.d(TAG, alertEntry + " application is system privileged or signed with "
                        + "platform key");
            } else {
                Log.d(TAG, alertEntry + " application is neither system privileged nor signed "
                        + "with platform key");
            }
        }

        return importance < NotificationManager.IMPORTANCE_DEFAULT
                && NotificationUtils.isSystemPrivilegedOrPlatformKey(mContext, alertEntry);
    }

    private boolean isMediaOrNavigationNotification(AlertEntry alertEntry) {
        Notification notification = alertEntry.getNotification();
        boolean mediaOrNav = notification.isMediaNotification()
                || Notification.CATEGORY_NAVIGATION.equals(notification.category);
        if (DEBUG) {
            Log.d(TAG, alertEntry + " category: " + notification.category);
        }
        return mediaOrNav;
    }

    /**
     * Process a list of {@link AlertEntry}s to be driving optimized.
     *
     * <p> Note that the string length limit is always respected regardless of whether distraction
     * optimization is required.
     */
    private List<AlertEntry> optimizeForDriving(List<AlertEntry> notifications) {
        notifications.forEach(notification -> notification = optimizeForDriving(notification));
        return notifications;
    }

    /**
     * Helper method that optimize a single {@link AlertEntry} for driving.
     *
     * <p> Currently only trimming texts that have visual effects in car. Operation is done on
     * the original notification object passed in; no new object is created.
     *
     * <p> Note that message notifications are not trimmed, so that messages are preserved for
     * assistant read-out. Instead, {@link MessageNotificationViewHolder} will be responsible
     * for the presentation-level text truncation.
     */
    AlertEntry optimizeForDriving(AlertEntry alertEntry) {
        if (Notification.CATEGORY_MESSAGE.equals(alertEntry.getNotification().category)) {
            return alertEntry;
        }

        Bundle extras = alertEntry.getNotification().extras;
        for (String key : extras.keySet()) {
            switch (key) {
                case Notification.EXTRA_TITLE:
                case Notification.EXTRA_TEXT:
                case Notification.EXTRA_TITLE_BIG:
                case Notification.EXTRA_SUMMARY_TEXT:
                    CharSequence value = extras.getCharSequence(key);
                    extras.putCharSequence(key, trimText(value));
                default:
                    continue;
            }
        }
        return alertEntry;
    }

    /**
     * Helper method that takes a string and trims the length to the maximum character allowed
     * by the {@link CarUxRestrictionsManager}.
     */
    @Nullable
    public CharSequence trimText(@Nullable CharSequence text) {
        if (TextUtils.isEmpty(text) || text.length() < mMaxStringLength) {
            return text;
        }
        int maxLength = mMaxStringLength - mEllipsizedSuffix.length();
        return text.toString().substring(0, maxLength) + mEllipsizedSuffix;
    }

    /**
     * @return the maximum numbers of characters allowed by the {@link CarUxRestrictionsManager}
     */
    public int getMaximumStringLength() {
        return mMaxStringLength;
    }

    /**
     * Group notifications that have the same group key.
     *
     * <p> Automatically generated group summaries that contains no child notifications are removed.
     * This can happen if a notification group only contains less important notifications that are
     * filtered out in the previous {@link #filter} step.
     *
     * <p> A group of child notifications without a summary notification will not be grouped.
     *
     * @param list list of ungrouped {@link AlertEntry}s.
     * @return list of grouped notifications as {@link NotificationGroup}s.
     */
    @VisibleForTesting
    List<NotificationGroup> group(List<AlertEntry> list) {
        SortedMap<String, NotificationGroup> groupedNotifications = new TreeMap<>();

        // First pass: group all notifications according to their groupKey.
        for (int i = 0; i < list.size(); i++) {
            AlertEntry alertEntry = list.get(i);
            Notification notification = alertEntry.getNotification();

            String groupKey;
            if (Notification.CATEGORY_CALL.equals(notification.category)) {
                // DO NOT group CATEGORY_CALL.
                groupKey = UUID.randomUUID().toString();
            } else {
                groupKey = alertEntry.getStatusBarNotification().getGroupKey();
            }

            if (groupKey == null) {
                // set a random group key since a TreeMap does not allow null keys
                groupKey = UUID.randomUUID().toString();
            }

            if (!groupedNotifications.containsKey(groupKey)) {
                NotificationGroup notificationGroup = new NotificationGroup();
                groupedNotifications.put(groupKey, notificationGroup);
            }
            if (notification.isGroupSummary()) {
                groupedNotifications.get(groupKey)
                        .setGroupSummaryNotification(alertEntry);
            } else {
                groupedNotifications.get(groupKey).addNotification(alertEntry);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "(First pass) Grouped notifications according to groupKey: "
                    + groupedNotifications);
        }

        // Second pass: remove automatically generated group summary if it contains no child
        // notifications. This can happen if a notification group only contains less important
        // notifications that are filtered out in the previous filter step.
        List<NotificationGroup> groupList = new ArrayList<>(groupedNotifications.values());
        groupList.removeIf(
                notificationGroup -> {
                    AlertEntry summaryNotification =
                            notificationGroup.getGroupSummaryNotification();
                    return notificationGroup.getChildCount() == 0
                            && summaryNotification != null
                            && summaryNotification.getStatusBarNotification().getOverrideGroupKey()
                            != null;
                });
        if (DEBUG) {
            Log.d(TAG, "(Second pass) Remove automatically generated group summaries: "
                    + groupList);
        }

        if (mShowRecentsAndOlderHeaders) {
            mNotificationDataManager.updateUnseenNotificationGroups(groupList);
        }


        // Third Pass: If a notification group has seen and unseen notifications, we need to split
        // up the group into its seen and unseen constituents.
        List<NotificationGroup> tempGroupList = new ArrayList<>();
        groupList.forEach(notificationGroup -> {
            AlertEntry groupSummary = notificationGroup.getGroupSummaryNotification();
            if (groupSummary == null || !mShowRecentsAndOlderHeaders) {
                boolean isNotificationSeen = mNotificationDataManager
                        .isNotificationSeen(notificationGroup.getSingleNotification());
                notificationGroup.setSeen(isNotificationSeen);
                tempGroupList.add(notificationGroup);
                return;
            }

            NotificationGroup seenNotificationGroup = new NotificationGroup();
            seenNotificationGroup.setSeen(true);
            seenNotificationGroup.setGroupSummaryNotification(groupSummary);
            NotificationGroup unseenNotificationGroup = new NotificationGroup();
            unseenNotificationGroup.setGroupSummaryNotification(groupSummary);
            unseenNotificationGroup.setSeen(false);

            notificationGroup.getChildNotifications().forEach(alertEntry -> {
                if (mNotificationDataManager.isNotificationSeen(alertEntry)) {
                    seenNotificationGroup.addNotification(alertEntry);
                } else {
                    unseenNotificationGroup.addNotification(alertEntry);
                }
            });
            tempGroupList.add(unseenNotificationGroup);
            tempGroupList.add(seenNotificationGroup);
        });
        groupList.clear();
        groupList.addAll(tempGroupList);
        if (DEBUG) {
            Log.d(TAG, "(Third pass) Split notification groups by seen and unseen: "
                    + groupList);
        }

        List<NotificationGroup> validGroupList = new ArrayList<>();
        if (mUseLauncherIcon) {
            // Fourth pass: since we do not use group summaries when using launcher icon, we can
            // restore groups into individual notifications that do not meet grouping threshold.
            groupList.forEach(
                    group -> {
                        if (group.getChildCount() < mMinimumGroupingThreshold) {
                            group.getChildNotifications().forEach(
                                    notification -> {
                                        NotificationGroup newGroup = new NotificationGroup();
                                        newGroup.addNotification(notification);
                                        newGroup.setSeen(group.isSeen());
                                        validGroupList.add(newGroup);
                                    });
                        } else {
                            validGroupList.add(group);
                        }
                    });
        } else {
            // Fourth pass: a notification group without a group summary or a notification group
            // that do not meet grouping threshold should be restored back into individual
            // notifications.
            groupList.forEach(
                    group -> {
                        boolean groupWithNoGroupSummary = group.getChildCount() > 1
                                && group.getGroupSummaryNotification() == null;
                        boolean groupWithGroupSummaryButNotEnoughNotifs =
                                group.getChildCount() < mMinimumGroupingThreshold
                                        && group.getGroupSummaryNotification() != null;
                        if (groupWithNoGroupSummary || groupWithGroupSummaryButNotEnoughNotifs) {
                            group.getChildNotifications().forEach(
                                    notification -> {
                                        NotificationGroup newGroup = new NotificationGroup();
                                        newGroup.addNotification(notification);
                                        newGroup.setSeen(group.isSeen());
                                        validGroupList.add(newGroup);
                                    });
                        } else {
                            validGroupList.add(group);
                        }
                    });
        }
        if (DEBUG) {
            if (mUseLauncherIcon) {
                Log.d(TAG, "(Fourth pass) Split notification groups that do not meet minimum "
                        + "grouping threshold of " + mMinimumGroupingThreshold + " : "
                        + validGroupList);
            } else {
                Log.d(TAG, "(Fourth pass) Restore notifications without group summaries and do"
                        + " not meet minimum grouping threshold of " + mMinimumGroupingThreshold
                        + " : " + validGroupList);
            }
        }


        // Fifth Pass: group notifications with no child notifications should be removed.
        validGroupList.removeIf(notificationGroup ->
                notificationGroup.getChildNotifications().isEmpty());
        if (DEBUG) {
            Log.d(TAG, "(Fifth pass) Group notifications without child notifications "
                    + "are removed: " + validGroupList);
        }

        // Sixth pass: if a notification is a group notification, update the timestamp if one of
        // the children notifications shows a timestamp.
        validGroupList.forEach(group -> {
            if (!group.isGroup()) {
                return;
            }

            AlertEntry groupSummaryNotification = group.getGroupSummaryNotification();
            boolean showWhen = false;
            long greatestTimestamp = 0;
            for (AlertEntry notification : group.getChildNotifications()) {
                if (notification.getNotification().showsTime()) {
                    showWhen = true;
                    greatestTimestamp = Math.max(greatestTimestamp,
                            notification.getNotification().when);
                }
            }

            if (showWhen) {
                groupSummaryNotification.getNotification().extras.putBoolean(
                        Notification.EXTRA_SHOW_WHEN, true);
                groupSummaryNotification.getNotification().when = greatestTimestamp;
            }
        });
        if (DEBUG) {
            Log.d(TAG, "Grouped notifications: " + validGroupList);
        }

        return validGroupList;
    }

    /**
     * Add new NotificationGroup to an existing list of NotificationGroups. The group will be
     * placed above next highest ranked notification without changing the ordering of the full list.
     *
     * @param newNotification the {@link AlertEntry} that should be added to the list.
     * @return list of grouped notifications as {@link NotificationGroup}s.
     */
    @VisibleForTesting
    protected List<NotificationGroup> additionalGroupAndRank(AlertEntry newNotification,
            RankingMap newRankingMap, boolean isUpdate) {
        Notification notification = newNotification.getNotification();
        NotificationGroup newGroup = new NotificationGroup();

        // The newGroup should appear in the recent section so mark the group as not seen. Since the
        // panel is open, mark the notification as seen in the data manager so when panel is closed
        // and reopened, it is set as seen.
        newGroup.setSeen(false);
        mNotificationDataManager.setNotificationAsSeen(newNotification);

        if (notification.isGroupSummary()) {
            // If child notifications already exist, update group summary
            for (NotificationGroup oldGroup : mOldProcessedNotifications) {
                if (hasSameGroupKey(oldGroup.getSingleNotification(), newNotification)) {
                    oldGroup.setGroupSummaryNotification(newNotification);
                    return mOldProcessedNotifications;
                }
            }
            // If child notifications do not exist, insert the summary as a new notification
            newGroup.setGroupSummaryNotification(newNotification);
            insertRankedNotification(newGroup, newRankingMap);
            return mOldProcessedNotifications;
        }

        // To keep track of indexes of unseen Notifications with the same group key
        Set<Integer> indexOfUnseenGroupsWithSameGroupKey = new HashSet<>();
        Set<NotificationGroup> emptySeenGroupsToBeRemoved = new HashSet<>();

        // Check if notification with same group key exists. The notification could be:
        // 1. present in a seen group and is an update:
        //      remove the notification from the seen group.
        //      next step will add this notification to the newGroup which is unseen.
        //      Also remove the seen group if there are no more children
        // 2. present in an unseen group with no children (i.e. group summary).
        // 3. present in an unseen group.
        for (int i = 0; i < mOldProcessedNotifications.size(); i++) {
            NotificationGroup oldGroup = mOldProcessedNotifications.get(i);

            if (!TextUtils.equals(oldGroup.getGroupKey(),
                    newNotification.getStatusBarNotification().getGroupKey())) {
                continue;
            }

            if (mShowRecentsAndOlderHeaders && oldGroup.isSeen()) {
                if (isUpdate) {
                    boolean isRemoved = oldGroup.removeNotification(newNotification);
                    if (isRemoved) {
                        mOldProcessedNotifications.set(i, oldGroup);
                        if (oldGroup.getChildCount() == 0) {
                            emptySeenGroupsToBeRemoved.add(oldGroup);
                        }
                    }
                }
                continue;
            }

            indexOfUnseenGroupsWithSameGroupKey.add(i);

            // If a group already exist with no children
            if (oldGroup.getChildCount() == 0) {
                // A group with no children is a standalone group summary
                NotificationGroup group = oldGroup;
                if (isUpdate) {
                    // Replace the standalone group summary
                    group = newGroup;
                }
                group.addNotification(newNotification);
                mOldProcessedNotifications.set(i, group);
                return mOldProcessedNotifications;
            }

            // Group with same group key exist with multiple children
            // For update, replace the old notification with the updated notification
            // else add the new notification to the existing group if it's notification
            // count is greater than the minimum threshold.
            if (isUpdate) {
                oldGroup.removeNotification(newNotification);
            }
            if (isUpdate || oldGroup.getChildCount() >= mMinimumGroupingThreshold) {
                oldGroup.addNotification(newNotification);
                mOldProcessedNotifications.set(i, oldGroup);
                return mOldProcessedNotifications;
            }
        }

        mOldProcessedNotifications.removeAll(emptySeenGroupsToBeRemoved);

        // Not an update to an existing group and no groups with same group key and
        // child count > minimum grouping threshold or child count == 0 exist in the list.
        AlertEntry groupSummaryNotification = findGroupSummaryNotification(
                newNotification.getStatusBarNotification().getGroupKey());
        // If the number of unseen notifications (+1 to account for new notification being
        // added) with same group key is greater than the minimum grouping threshold
        if (((indexOfUnseenGroupsWithSameGroupKey.size() + 1) >= mMinimumGroupingThreshold)
                && groupSummaryNotification != null) {
            // Remove all individual groups and add all notifications with the same group key
            // to the new group
            List<NotificationGroup> otherProcessedNotifications = new ArrayList<>();
            for (int i = 0; i < mOldProcessedNotifications.size(); i++) {
                NotificationGroup notificationGroup = mOldProcessedNotifications.get(i);
                if (indexOfUnseenGroupsWithSameGroupKey.contains(i)) {
                    // Group has the same group key
                    for (AlertEntry alertEntry : notificationGroup.getChildNotifications()) {
                        newGroup.addNotification(alertEntry);
                    }
                } else {
                    otherProcessedNotifications.add(notificationGroup);
                }
            }
            mOldProcessedNotifications = otherProcessedNotifications;
            mNotificationDataManager.setNotificationAsSeen(groupSummaryNotification);
            newGroup.setGroupSummaryNotification(groupSummaryNotification);
        }

        // notification should be added to the new unseen group
        newGroup.addNotification(newNotification);
        insertRankedNotification(newGroup, newRankingMap);
        return mOldProcessedNotifications;
    }

    /**
     * Finds Group Summary Notification with the same group key from {@code mOldNotifications}.
     */
    @Nullable
    private AlertEntry findGroupSummaryNotification(String groupKey) {
        for (AlertEntry alertEntry : mOldNotifications.values()) {
            if (alertEntry.getNotification().isGroupSummary() && TextUtils.equals(
                    alertEntry.getStatusBarNotification().getGroupKey(), groupKey)) {
                return alertEntry;
            }
        }
        return null;
    }

    // When adding a new notification we want to add it before the next highest ranked without
    // changing existing order
    private void insertRankedNotification(NotificationGroup group, RankingMap newRankingMap) {
        NotificationListenerService.Ranking newRanking = new NotificationListenerService.Ranking();
        newRankingMap.getRanking(group.getNotificationForSorting().getKey(), newRanking);

        for (int i = 0; i < mOldProcessedNotifications.size(); i++) {
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            newRankingMap.getRanking(mOldProcessedNotifications.get(
                    i).getNotificationForSorting().getKey(), ranking);
            if (mShowRecentsAndOlderHeaders && group.isSeen()
                    && !mOldProcessedNotifications.get(i).isSeen()) {
                mOldProcessedNotifications.add(i, group);
                return;
            }

            if (newRanking.getRank() < ranking.getRank()) {
                mOldProcessedNotifications.add(i, group);
                return;
            }
        }

        // If it's not higher ranked than any existing notifications then just add at end
        mOldProcessedNotifications.add(group);
    }

    private boolean hasSameGroupKey(AlertEntry notification1, AlertEntry notification2) {
        return TextUtils.equals(notification1.getStatusBarNotification().getGroupKey(),
                notification2.getStatusBarNotification().getGroupKey());
    }

    /**
     * Rank notifications according to the ranking key supplied by the notification.
     */
    @VisibleForTesting
    protected List<NotificationGroup> rank(List<NotificationGroup> notifications,
            RankingMap rankingMap) {

        Collections.sort(notifications, new NotificationComparator(rankingMap));

        // Rank within each group
        notifications.forEach(notificationGroup -> {
            if (notificationGroup.isGroup()) {
                Collections.sort(
                        notificationGroup.getChildNotifications(),
                        new InGroupComparator(rankingMap));
            }
        });
        return notifications;
    }

    @VisibleForTesting
    protected Map<String, AlertEntry> getOldNotifications() {
        return mOldNotifications;
    }

    public void setCarUxRestrictionManagerWrapper(CarUxRestrictionManagerWrapper manager) {
        try {
            if (manager == null || manager.getCurrentCarUxRestrictions() == null) {
                return;
            }
            mMaxStringLength =
                    manager.getCurrentCarUxRestrictions().getMaxRestrictedStringLength();
        } catch (RuntimeException e) {
            mMaxStringLength = Integer.MAX_VALUE;
            Log.e(TAG, "Failed to get UxRestrictions thus running unrestricted", e);
        }
    }

    @VisibleForTesting
    List<NotificationGroup> getOldProcessedNotifications() {
        return mOldProcessedNotifications;
    }

    /**
     * Comparator that sorts within the notification group by the sort key. If a sort key is not
     * supplied, sort by the global ranking order.
     */
    private static class InGroupComparator implements Comparator<AlertEntry> {
        private final RankingMap mRankingMap;

        InGroupComparator(RankingMap rankingMap) {
            mRankingMap = rankingMap;
        }

        @Override
        public int compare(AlertEntry left, AlertEntry right) {
            if (left.getNotification().getSortKey() != null
                    && right.getNotification().getSortKey() != null) {
                return left.getNotification().getSortKey().compareTo(
                        right.getNotification().getSortKey());
            }

            NotificationListenerService.Ranking leftRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(left.getKey(), leftRanking);

            NotificationListenerService.Ranking rightRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(right.getKey(), rightRanking);

            return leftRanking.getRank() - rightRanking.getRank();
        }
    }

    /**
     * Comparator that sorts the notification groups by their representative notification's rank.
     */
    private class NotificationComparator implements Comparator<NotificationGroup> {
        private final NotificationListenerService.RankingMap mRankingMap;

        NotificationComparator(NotificationListenerService.RankingMap rankingMap) {
            mRankingMap = rankingMap;
        }

        @Override
        public int compare(NotificationGroup left, NotificationGroup right) {
            if (mShowRecentsAndOlderHeaders) {
                if (left.isSeen() && !right.isSeen()) {
                    return -1;
                } else if (!left.isSeen() && right.isSeen()) {
                    return 1;
                }
            }

            NotificationListenerService.Ranking leftRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(left.getNotificationForSorting().getKey(), leftRanking);

            NotificationListenerService.Ranking rightRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(right.getNotificationForSorting().getKey(), rightRanking);

            return leftRanking.getRank() - rightRanking.getRank();
        }
    }
}
