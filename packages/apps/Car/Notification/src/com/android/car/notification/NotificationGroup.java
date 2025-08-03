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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure representing a notification card in car.
 * A notification group can hold either:
 * <ol>
 * <li>One notification with no group summary notification</li>
 * <li>One group summary notification with no child notifications</li>
 * <li>A group of notifications with a group summary notification</li>
 * </ol>
 */
public class NotificationGroup {
    private static final String TAG = "NotificationGroup";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final List<AlertEntry> mNotifications = new ArrayList<>();

    @Nullable
    private List<String> mChildTitles;
    @Nullable
    private AlertEntry mGroupSummaryNotification;
    private String mGroupKey;
    private boolean mIsHeader;
    private boolean mIsFooter;
    private boolean mIsRecentsHeader;
    private boolean mIsOlderHeader;
    private boolean mIsSeen;

    public NotificationGroup() {
    }

    public NotificationGroup(AlertEntry alertEntry) {
        addNotification(alertEntry);
    }

    public NotificationGroup(NotificationGroup group) {
        setGroupKey(group.getGroupKey());
        if (group.getGroupSummaryNotification() != null) {
            setGroupSummaryNotification(group.getGroupSummaryNotification());
        }
        for (AlertEntry alertEntry : group.getChildNotifications()) {
            addNotification(alertEntry);
        }
        setChildTitles(group.getChildTitles());
        setFooter(group.isFooter());
        setHeader(group.isHeader());
        setOlderHeader(group.isOlderHeader());
        setRecentsHeader(group.isRecentsHeader());
        setSeen(group.isSeen());
    }

    /**
     * Add child notification.
     *
     * New notification must have the same group key as other notifications in group.
     */
    public void addNotification(AlertEntry alertEntry) {
        assertSameGroupKey(alertEntry.getStatusBarNotification().getGroupKey());
        mNotifications.add(alertEntry);
    }

    /**
     * Removes child notification.
     *
     * @return {@code true} if notification was removed
     */
    public boolean removeNotification(AlertEntry alertEntry) {
        for (int i = 0; i < mNotifications.size(); i++) {
            if (mNotifications.get(i).getKey().equals(alertEntry.getKey())) {
                mNotifications.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Set group summary notification.
     *
     * Group summary must have the same group key as other notifications in group.
     */
    public void setGroupSummaryNotification(AlertEntry groupSummaryNotification) {
        assertSameGroupKey(groupSummaryNotification.getStatusBarNotification().getGroupKey());
        mGroupSummaryNotification = groupSummaryNotification;
    }

    void setGroupKey(@NonNull String groupKey) {
        mGroupKey = groupKey;
    }

    /**
     * Returns the group key of this notification group.
     *
     * <p> {@code null} will be returned if the group key has not been set yet.
     */
    @Nullable
    public String getGroupKey() {
        return mGroupKey;
    }

    /**
     * Returns the count of how many child notifications (excluding the group summary notification)
     * this notification group has.
     */
    public int getChildCount() {
        return mNotifications.size();
    }

    /**
     * Returns true when it has a group summary notification and >1 child notifications
     */
    public boolean isGroup() {
        return mGroupSummaryNotification != null && getChildCount() > 1;
    }

    /**
     * Return true if this group is a header, footer, recents header or older header.
     */
    public boolean isHeaderOrFooter() {
        return isHeader() || isFooter() || isOlderHeader() || isRecentsHeader();
    }

    /**
     * Return true if the header is set to be displayed.
     */
    public boolean isHeader() {
        return mIsHeader;
    }

    /**
     * Set this to true if a header needs to be displayed with a title and a clear all button.
     */
    public void setHeader(boolean header) {
        mIsHeader = header;
    }

    /**
     * Return true if the header is set to be displayed.
     */
    public boolean isFooter() {
        return mIsFooter;
    }

    /**
     * Set this to true if a footer needs to be displayed with a clear all button.
     */
    public void setFooter(boolean footer) {
        mIsFooter = footer;
    }

    /**
     * Return true if the recents header is set to be displayed.
     */
    public boolean isRecentsHeader() {
        return mIsRecentsHeader;
    }

    /**
     * Set this to true if a header is a recents header.
     */
    public void setRecentsHeader(boolean isRecentsHeader) {
        mIsRecentsHeader = isRecentsHeader;
    }

    /**
     * Return true if the older notifications header is set to be displayed.
     */
    public boolean isOlderHeader() {
        return mIsOlderHeader;
    }

    /**
     * Set this to true if a header is a older notifications header.
     */
    public void setOlderHeader(boolean isOlderHeader) {
        mIsOlderHeader = isOlderHeader;
    }

    /**
     * Return true if the notification group has been seen.
     */
    public boolean isSeen() {
        return mIsSeen;
    }

    /**
     * Set this to true if the notification group has been seen.
     */
    public void setSeen(boolean isSeen) {
        mIsSeen = isSeen;
    }

    /**
     * Returns true if this group is not a header or footer and all of the notifications it holds
     * are dismissible by user action.
     */
    public boolean isDismissible() {
        if (mIsHeader || mIsFooter) {
            return false;
        }

        for (AlertEntry notification : mNotifications) {
            boolean isForeground =
                    (notification.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE)
                            != 0;
            if (isForeground || notification.getStatusBarNotification().isOngoing()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the list of the child notifications.
     */
    public List<AlertEntry> getChildNotifications() {
        return mNotifications;
    }

    /**
     * Returns the group summary notification.
     */
    @Nullable
    public AlertEntry getGroupSummaryNotification() {
        return mGroupSummaryNotification;
    }

    /**
     * Sets the list of child notification titles.
     */
    public void setChildTitles(List<String> childTitles) {
        mChildTitles = childTitles;
    }

    /**
     * Returns the list of child notification titles.
     */
    @Nullable
    public List<String> getChildTitles() {
        return mChildTitles;
    }

    /**
     * Generates the list of the child notification titles for a group summary notification.
     */
    public List<String> generateChildTitles() {
        List<String> titles = new ArrayList<>();

        for (AlertEntry notification : mNotifications) {
            Bundle extras = notification.getNotification().extras;
            if (extras.containsKey(Notification.EXTRA_TITLE)) {
                titles.add(extras.getString(Notification.EXTRA_TITLE));
            } else if (extras.containsKey(Notification.EXTRA_TITLE_BIG)) {
                titles.add(extras.getString(Notification.EXTRA_TITLE_BIG));
            } else if (extras.containsKey(Notification.EXTRA_MESSAGES)) {
                List<Notification.MessagingStyle.Message> messages =
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                                extras.getParcelableArray(Notification.EXTRA_MESSAGES));
                Notification.MessagingStyle.Message lastMessage = messages.get(messages.size() - 1);
                titles.add(lastMessage.getSenderPerson().getName().toString());
            } else if (extras.containsKey(Notification.EXTRA_SUB_TEXT)) {
                titles.add(extras.getString(Notification.EXTRA_SUB_TEXT));
            }
        }

        return titles;
    }

    /**
     * Returns a single notification that represents this NotificationGroup:
     *
     * <p> If the NotificationGroup is a valid grouped notification or has no child notifications,
     * the group summary notification is returned.
     *
     * <p> If the NotificationGroup has only 1 child notification,
     * or has more than 1 child notifications without a valid group summary,
     * the first child notification is returned.
     *
     * @return the notification that represents this NotificationGroup
     */
    public AlertEntry getSingleNotification() {
        if (isGroup() || getChildCount() == 0) {
            return getGroupSummaryNotification();
        } else {
            return mNotifications.get(0);
        }
    }

    AlertEntry getNotificationForSorting() {
        if (mGroupSummaryNotification != null) {
            return getGroupSummaryNotification();
        }
        return getSingleNotification();
    }

    private void assertSameGroupKey(String groupKey) {
        if (mGroupKey == null) {
            setGroupKey(groupKey);
        } else if (!mGroupKey.equals(groupKey)) {
            updateGroupKeyOrThrowError(groupKey);
        }
    }

    /**
     * If {@link mGroupKey} and the passed groupKey doesn't match, then compare the passed groupKey
     * with the groupKey of a single notification to decide whether to update or throw an error.
     */
    private void updateGroupKeyOrThrowError(String groupKey) {
        if (getSingleNotification() != null
                && getSingleNotification().getStatusBarNotification() != null) {
            String singleGroupKey =
                    getSingleNotification().getStatusBarNotification().getGroupKey();
            if (TextUtils.equals(groupKey, singleGroupKey)) {
                if (DEBUG) {
                    Log.d(TAG, "The current groupKey is: " + mGroupKey + ", and it will be"
                            + "updated to: " + singleGroupKey);
                }
                setGroupKey(singleGroupKey);
            } else {
                throw new IllegalStateException(
                        "Group key mismatch when adding a notification to a group. "
                                + "mGroupKey: " + mGroupKey + "; groupKey:" + groupKey);
            }
        }
    }

    @Override
    public String toString() {
        return mGroupKey + ": " + mNotifications.toString();
    }
}
