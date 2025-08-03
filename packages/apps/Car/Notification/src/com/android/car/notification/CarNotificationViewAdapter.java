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
import android.app.Notification;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.template.CarNotificationBaseViewHolder;
import com.android.car.notification.template.CarNotificationFooterViewHolder;
import com.android.car.notification.template.CarNotificationHeaderViewHolder;
import com.android.car.notification.template.CarNotificationOlderViewHolder;
import com.android.car.notification.template.CarNotificationRecentsViewHolder;
import com.android.car.notification.template.GroupNotificationViewHolder;
import com.android.car.notification.template.GroupSummaryNotificationViewHolder;
import com.android.car.notification.template.MessageNotificationViewHolder;
import com.android.car.ui.recyclerview.ContentLimitingAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Notification data adapter that binds a notification to the corresponding view.
 */
public class CarNotificationViewAdapter extends ContentLimitingAdapter<RecyclerView.ViewHolder>
        implements PreprocessingManager.CallStateListener {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "CarNotificationAdapter";
    private static final int ID_HEADER = 0;
    private static final int ID_RECENT_HEADER = 1;
    private static final int ID_OLDER_HEADER = 2;
    private static final int ID_FOOTER = 3;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final int mMaxNumberGroupChildrenShown;
    private final boolean mIsGroupNotificationAdapter;
    private final boolean mShowRecentsAndOlderHeaders;

    // book keeping expanded notification groups
    private final List<ExpandedNotification> mExpandedNotifications = new ArrayList<>();
    private final CarNotificationItemController mNotificationItemController;

    private List<NotificationGroup> mNotifications = new ArrayList<>();
    private Map<String, Integer> mGroupKeyToCountMap = new HashMap<>();
    private LinearLayoutManager mLayoutManager;
    private RecyclerView.RecycledViewPool mViewPool;
    private CarUxRestrictions mCarUxRestrictions;
    private NotificationClickHandlerFactory mClickHandlerFactory;
    private NotificationDataManager mNotificationDataManager;
    private boolean mIsInCall;
    private boolean mHasHeaderAndFooter;
    private boolean mHasUnseenNotifications;
    private boolean mHasSeenNotifications;
    private int mMaxItems = ContentLimitingAdapter.UNLIMITED;

    /**
     * Constructor for a notification adapter.
     * Can be used both by the root notification list view, or a grouped notification view.
     *
     * @param context the context for resources and inflating views
     * @param isGroupNotificationAdapter true if this adapter is used by a grouped notification view
     * @param notificationItemController shared logic to control notification items.
     */
    public CarNotificationViewAdapter(Context context, boolean isGroupNotificationAdapter,
            @Nullable CarNotificationItemController notificationItemController) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mMaxNumberGroupChildrenShown =
                mContext.getResources().getInteger(R.integer.max_group_children_number);
        mShowRecentsAndOlderHeaders =
                mContext.getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);
        mIsGroupNotificationAdapter = isGroupNotificationAdapter;
        mNotificationItemController = notificationItemController;
        mNotificationDataManager = NotificationDataManager.getInstance();
        setHasStableIds(true);
        if (!mIsGroupNotificationAdapter) {
            mViewPool = new RecyclerView.RecycledViewPool();
        }

        PreprocessingManager.getInstance(context).addCallStateListener(this::onCallStateChanged);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mLayoutManager = null;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolderImpl(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder viewHolder;
        View view;
        switch (viewType) {
            case NotificationViewType.HEADER:
                view = mInflater.inflate(R.layout.notification_header_template, parent, false);
                viewHolder = new CarNotificationHeaderViewHolder(mContext, view,
                        mNotificationItemController, mClickHandlerFactory);
                break;
            case NotificationViewType.FOOTER:
                view = mInflater.inflate(R.layout.notification_footer_template, parent, false);
                viewHolder = new CarNotificationFooterViewHolder(mContext, view,
                        mNotificationItemController, mClickHandlerFactory);
                break;
            case NotificationViewType.RECENTS:
                view = mInflater.inflate(R.layout.notification_recents_template, parent, false);
                viewHolder = new CarNotificationRecentsViewHolder(mContext, view,
                        mNotificationItemController);
                break;
            case NotificationViewType.OLDER:
                view = mInflater.inflate(R.layout.notification_older_template, parent, false);
                viewHolder = new CarNotificationOlderViewHolder(mContext, view,
                        mNotificationItemController);
                break;
            default:
                CarNotificationTypeItem carNotificationTypeItem = CarNotificationTypeItem.of(
                        viewType);
                view = mInflater.inflate(
                        carNotificationTypeItem.getNotificationCenterTemplate(), parent, false);
                viewHolder = carNotificationTypeItem.getViewHolder(view, mClickHandlerFactory);
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolderImpl(RecyclerView.ViewHolder holder, int position) {
        NotificationGroup notificationGroup = mNotifications.get(position);

        int viewType = holder.getItemViewType();
        switch (viewType) {
            case NotificationViewType.HEADER:
                ((CarNotificationHeaderViewHolder) holder).bind(hasNotifications());
                return;
            case NotificationViewType.FOOTER:
                ((CarNotificationFooterViewHolder) holder).bind(hasNotifications(),
                        mHasSeenNotifications);
                return;
            case NotificationViewType.RECENTS:
                ((CarNotificationRecentsViewHolder) holder).bind(mHasUnseenNotifications);
                return;
            case NotificationViewType.OLDER:
                ((CarNotificationOlderViewHolder) holder)
                        .bind(mHasSeenNotifications, !mHasUnseenNotifications);
                return;
            case NotificationViewType.GROUP:
                ((GroupNotificationViewHolder) holder)
                        .bind(notificationGroup, this, /* isExpanded= */
                                isExpanded(notificationGroup.getGroupKey(),
                                        notificationGroup.isSeen()));
                return;
            case NotificationViewType.GROUP_SUMMARY:
                ((CarNotificationBaseViewHolder) holder).setHideDismissButton(true);
                ((GroupSummaryNotificationViewHolder) holder).bind(notificationGroup);
                return;
        }

        CarNotificationTypeItem carNotificationTypeItem = CarNotificationTypeItem.of(viewType);
        AlertEntry alertEntry = notificationGroup.getSingleNotification();

        if (shouldRestrictMessagePreview() && (viewType == NotificationViewType.MESSAGE
                || viewType == NotificationViewType.MESSAGE_IN_GROUP)) {
            ((MessageNotificationViewHolder) holder).bindRestricted(alertEntry, /* isInGroup= */
                    false, /* isHeadsUp= */ false, notificationGroup.isSeen());
        } else {
            carNotificationTypeItem.bind(alertEntry, false, (CarNotificationBaseViewHolder) holder,
                    notificationGroup.isSeen());
        }
    }

    @Override
    public int getItemViewTypeImpl(int position) {
        NotificationGroup notificationGroup = mNotifications.get(position);
        if (notificationGroup.isHeader()) {
            return NotificationViewType.HEADER;
        }

        if (notificationGroup.isFooter()) {
            return NotificationViewType.FOOTER;
        }

        if (notificationGroup.isRecentsHeader()) {
            return NotificationViewType.RECENTS;
        }

        if (notificationGroup.isOlderHeader()) {
            return NotificationViewType.OLDER;
        }

        ExpandedNotification expandedNotification =
                new ExpandedNotification(notificationGroup.getGroupKey(),
                        notificationGroup.isSeen());
        if (notificationGroup.isGroup()) {
            return NotificationViewType.GROUP;
        } else if (mExpandedNotifications.contains(expandedNotification)) {
            // when there are 2 notifications left in the expanded notification and one of them is
            // removed at that time the item type changes from group to normal and hence the
            // notification should be removed from expanded notifications.
            setExpanded(expandedNotification.getKey(), expandedNotification.isExpanded(),
                    /* isExpanded= */ false);
        }

        Notification notification =
                notificationGroup.getSingleNotification().getNotification();
        Bundle extras = notification.extras;

        String category = notification.category;
        if (category != null) {
            switch (category) {
                case Notification.CATEGORY_CALL:
                    return NotificationViewType.CALL;
                case Notification.CATEGORY_CAR_EMERGENCY:
                    return NotificationViewType.CAR_EMERGENCY;
                case Notification.CATEGORY_CAR_WARNING:
                    return NotificationViewType.CAR_WARNING;
                case Notification.CATEGORY_CAR_INFORMATION:
                    return mIsGroupNotificationAdapter
                            ? NotificationViewType.CAR_INFORMATION_IN_GROUP
                            : NotificationViewType.CAR_INFORMATION;
                case Notification.CATEGORY_MESSAGE:
                    return mIsGroupNotificationAdapter
                            ? NotificationViewType.MESSAGE_IN_GROUP : NotificationViewType.MESSAGE;
                default:
                    break;
            }
        }

        // progress
        int progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX);
        boolean isIndeterminate = extras.getBoolean(
                Notification.EXTRA_PROGRESS_INDETERMINATE);
        boolean hasValidProgress = isIndeterminate || progressMax != 0;
        boolean isProgress = extras.containsKey(Notification.EXTRA_PROGRESS)
                && extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
                && hasValidProgress
                && !notification.hasCompletedProgress();
        if (isProgress) {
            return mIsGroupNotificationAdapter
                    ? NotificationViewType.PROGRESS_IN_GROUP : NotificationViewType.PROGRESS;
        }

        // inbox
        boolean isInbox = extras.containsKey(Notification.EXTRA_TITLE_BIG)
                && extras.containsKey(Notification.EXTRA_SUMMARY_TEXT);
        if (isInbox) {
            return mIsGroupNotificationAdapter
                    ? NotificationViewType.INBOX_IN_GROUP : NotificationViewType.INBOX;
        }

        // group summary
        boolean isGroupSummary = notificationGroup.getChildTitles() != null;
        if (isGroupSummary) {
            return NotificationViewType.GROUP_SUMMARY;
        }

        // the big text and big picture styles are fallen back to basic template in car
        // i.e. setting the big text and big picture does not have an effect
        boolean isBigText = extras.containsKey(Notification.EXTRA_BIG_TEXT);
        if (isBigText) {
            Log.i(TAG, "Big text style is not supported as a car notification");
        }
        boolean isBigPicture = extras.containsKey(Notification.EXTRA_PICTURE);
        if (isBigPicture) {
            Log.i(TAG, "Big picture style is not supported as a car notification");
        }

        // basic, big text, big picture
        return mIsGroupNotificationAdapter
                ? NotificationViewType.BASIC_IN_GROUP : NotificationViewType.BASIC;
    }

    @Override
    public int getUnrestrictedItemCount() {
        return mNotifications.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        if (maxItems == ContentLimitingAdapter.UNLIMITED
                || (!mHasHeaderAndFooter && !mHasUnseenNotifications && !mHasSeenNotifications)) {
            mMaxItems = maxItems;
        } else {
            // Adding to max limit of notifications for each header so that they do not count
            // towards limit.
            // Footer is not accounted for since it as the end of the list and it doesn't affect the
            // limit of notifications above it.
            mMaxItems = maxItems;
            if (mHasHeaderAndFooter) {
                mMaxItems++;
            }
            if (mHasSeenNotifications) {
                mMaxItems++;
            }
            if (mHasUnseenNotifications) {
                mMaxItems++;
            }
        }
        super.setMaxItems(mMaxItems);
    }

    @Override
    protected int getScrollToPositionWhenRestricted() {
        if (mLayoutManager == null) {
            return -1;
        }
        int firstItem = mLayoutManager.findFirstVisibleItemPosition();
        if (firstItem >= getItemCount() - 1) {
            return getItemCount() - 1;
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        NotificationGroup notificationGroup = mNotifications.get(position);
        if (notificationGroup.isHeader()) {
            return ID_HEADER;
        }
        if (mShowRecentsAndOlderHeaders && !mIsGroupNotificationAdapter) {
            if (notificationGroup.isRecentsHeader()) {
                return ID_RECENT_HEADER;
            }
            if (notificationGroup.isOlderHeader()) {
                return ID_OLDER_HEADER;
            }
            if (notificationGroup.isFooter()) {
                return ID_FOOTER;
            }
        }
        if (notificationGroup.isFooter()) {
            // We can use recent header's ID when it isn't being used.
            return ID_RECENT_HEADER;
        }

        String key = notificationGroup.isGroup()
                ? notificationGroup.getGroupKey()
                : notificationGroup.getSingleNotification().getKey();

        if (mShowRecentsAndOlderHeaders) {
            key += notificationGroup.isSeen();
        }

        return key.hashCode();
    }

    /**
     * Set the expansion state of a group notification given its group key.
     *
     * @param groupKey the unique identifier of a {@link NotificationGroup}
     * @param isSeen whether the {@link NotificationGroup} has been seen by the user
     * @param isExpanded whether the group notification should be expanded.
     */
    public void setExpanded(String groupKey, boolean isSeen, boolean isExpanded) {
        if (isExpanded(groupKey, isSeen) == isExpanded) {
            return;
        }

        ExpandedNotification expandedNotification = new ExpandedNotification(groupKey, isSeen);
        if (isExpanded) {
            mExpandedNotifications.add(expandedNotification);
        } else {
            mExpandedNotifications.remove(expandedNotification);
        }
        if (DEBUG) {
            Log.d(TAG, "Expanded notification statuses: " + mExpandedNotifications);
        }
    }

    /**
     * Collapses all expanded groups.
     */
    public void collapseAllGroups() {
        if (!mExpandedNotifications.isEmpty()) {
            mExpandedNotifications.clear();
        }
    }

    /**
     * Returns whether the notification is expanded given its group key and it's seen status.
     *
     * @param groupKey the unique identifier of a {@link NotificationGroup}
     * @param isSeen whether the {@link NotificationGroup} has been seen by the user
     */
    boolean isExpanded(String groupKey, boolean isSeen) {
        ExpandedNotification expandedNotification = new ExpandedNotification(groupKey, isSeen);
        return mExpandedNotifications.contains(expandedNotification);
    }

    /**
     * Gets the current {@link CarUxRestrictions}.
     */
    public CarUxRestrictions getCarUxRestrictions() {
        return mCarUxRestrictions;
    }

    /**
     * Updates notifications and update views.
     *
     * @param setRecyclerViewListHeadersAndFooters sets the header and footer on the entire list of
     * items within the recycler view. This is NOT the header/footer for the grouped notifications.
     */
    public void setNotifications(List<NotificationGroup> notifications,
            boolean setRecyclerViewListHeadersAndFooters) {
        mGroupKeyToCountMap.clear();
        notifications.forEach(notificationGroup -> {
            if ((mGroupKeyToCountMap.computeIfPresent(notificationGroup.getGroupKey(),
                    (key, currentValue) -> currentValue + 1)) == null) {
                mGroupKeyToCountMap.put(notificationGroup.getGroupKey(), 1);
            }
        });

        if (mShowRecentsAndOlderHeaders && !mIsGroupNotificationAdapter) {
            List<NotificationGroup> seenNotifications = new ArrayList<>();
            List<NotificationGroup> unseenNotifications = new ArrayList<>();
            notifications.forEach(notificationGroup -> {
                if (notificationGroup.isSeen()) {
                    seenNotifications.add(new NotificationGroup(notificationGroup));
                } else {
                    unseenNotifications.add(new NotificationGroup(notificationGroup));
                }
            });
            setSeenAndUnseenNotifications(unseenNotifications, seenNotifications,
                    setRecyclerViewListHeadersAndFooters);
            return;
        }

        List<NotificationGroup> notificationGroupList = notifications.stream()
                .map(notificationGroup -> new NotificationGroup(notificationGroup))
                .collect(Collectors.toList());

        if (setRecyclerViewListHeadersAndFooters) {
            // add header as the first item of the list.
            notificationGroupList.add(0, createNotificationHeader());
            // add footer as the last item of the list.
            notificationGroupList.add(createNotificationFooter());
            mHasHeaderAndFooter = true;
        } else {
            mHasHeaderAndFooter = false;
        }

        CarNotificationDiff notificationDiff =
                new CarNotificationDiff(mContext, mNotifications, notificationGroupList, mMaxItems);
        notificationDiff.setShowRecentsAndOlderHeaders(false);
        DiffUtil.DiffResult diffResult =
                DiffUtil.calculateDiff(notificationDiff, /* detectMoves= */ false);
        mNotifications = notificationGroupList;
        if (DEBUG) {
            Log.d(TAG, "Updated adapter view holders: " + mNotifications);
        }
        updateUnderlyingDataChanged(getUnrestrictedItemCount(), /* newAnchorIndex= */ 0);
        diffResult.dispatchUpdatesTo(this);
    }

    private void setSeenAndUnseenNotifications(List<NotificationGroup> unseenNotifications,
            List<NotificationGroup> seenNotifications,
            boolean setRecyclerViewListHeadersAndFooters) {
        if (DEBUG) {
            Log.d(TAG, "Seen notifications: " + seenNotifications);
            Log.d(TAG, "Unseen notifications: " + unseenNotifications);
        }

        List<NotificationGroup> notificationGroupList;
        if (unseenNotifications.isEmpty()) {
            mHasUnseenNotifications = false;

            notificationGroupList = new ArrayList<>();
        } else {
            mHasUnseenNotifications = true;

            notificationGroupList = new ArrayList<>(unseenNotifications);
            if (setRecyclerViewListHeadersAndFooters) {
                // Add recents header as the first item of the list.
                notificationGroupList.add(/* index= */ 0, createRecentsHeader());
            }
        }

        if (seenNotifications.isEmpty()) {
            mHasSeenNotifications = false;
        } else {
            mHasSeenNotifications = true;

            if (setRecyclerViewListHeadersAndFooters) {
                // Append older header to the list.
                notificationGroupList.add(createOlderHeader());
            }
            notificationGroupList.addAll(seenNotifications);
        }

        if (setRecyclerViewListHeadersAndFooters) {
            // Add header as the first item of the list.
            notificationGroupList.add(0, createNotificationHeader());
            // Add footer as the last item of the list.
            notificationGroupList.add(createNotificationFooter());
            mHasHeaderAndFooter = true;
        } else {
            mHasHeaderAndFooter = false;
        }

        CarNotificationDiff notificationDiff =
                new CarNotificationDiff(mContext, mNotifications, notificationGroupList, mMaxItems);
        notificationDiff.setShowRecentsAndOlderHeaders(true);
        DiffUtil.DiffResult diffResult =
                DiffUtil.calculateDiff(notificationDiff, /* detectMoves= */ false);
        mNotifications = notificationGroupList;
        if (DEBUG) {
            Log.d(TAG, "Updated adapter view holders: " + mNotifications);
        }
        updateUnderlyingDataChanged(getUnrestrictedItemCount(), /* newAnchorIndex= */ 0);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Returns {@code true} if notifications are present in adapter.
     *
     * Group notification list doesn't have any headers, hence, if there are any notifications
     * present the size will be more than zero.
     *
     * Non-group notification list has header and footer by default. Therefore the min number of
     * items in the adapter will always be two. If there are any notifications present the size will
     * be more than two.
     *
     * When recent and older headers are enabled, each header will be accounted for when checking
     * for the presence of notifications.
     */
    public boolean hasNotifications() {
        int numberOfHeaders;
        if (mIsGroupNotificationAdapter) {
            numberOfHeaders = 0;
        } else {
            numberOfHeaders = 2;

            if (mHasSeenNotifications) {
                numberOfHeaders++;
            }

            if (mHasUnseenNotifications) {
                numberOfHeaders++;
            }
        }

        return getItemCount() > numberOfHeaders;
    }

    private NotificationGroup createNotificationHeader() {
        NotificationGroup notificationGroupWithHeader = new NotificationGroup();
        notificationGroupWithHeader.setHeader(true);
        notificationGroupWithHeader.setGroupKey("notification_header");
        return notificationGroupWithHeader;
    }

    private NotificationGroup createNotificationFooter() {
        NotificationGroup notificationGroupWithFooter = new NotificationGroup();
        notificationGroupWithFooter.setFooter(true);
        notificationGroupWithFooter.setGroupKey("notification_footer");
        return notificationGroupWithFooter;
    }

    private NotificationGroup createRecentsHeader() {
        NotificationGroup notificationGroupWithRecents = new NotificationGroup();
        notificationGroupWithRecents.setRecentsHeader(true);
        notificationGroupWithRecents.setGroupKey("notification_recents");
        notificationGroupWithRecents.setSeen(false);
        return notificationGroupWithRecents;
    }

    private NotificationGroup createOlderHeader() {
        NotificationGroup notificationGroupWithOlder = new NotificationGroup();
        notificationGroupWithOlder.setOlderHeader(true);
        notificationGroupWithOlder.setGroupKey("notification_older");
        notificationGroupWithOlder.setSeen(true);
        return notificationGroupWithOlder;
    }

    /** Implementation of {@link PreprocessingManager.CallStateListener} **/
    @Override
    public void onCallStateChanged(boolean isInCall) {
        if (isInCall != mIsInCall) {
            mIsInCall = isInCall;
            notifyDataSetChanged();
        }
    }

    /**
     * Sets the current {@link CarUxRestrictions}.
     */
    public void setCarUxRestrictions(CarUxRestrictions carUxRestrictions) {
        Log.d(TAG, "setCarUxRestrictions");
        mCarUxRestrictions = carUxRestrictions;
        notifyDataSetChanged();
    }

    /**
     * Helper method that determines whether a notification is a messaging notification and
     * should have restricted content (no message preview).
     */
    private boolean shouldRestrictMessagePreview() {
        return mCarUxRestrictions != null && (mCarUxRestrictions.getActiveRestrictions()
                & CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE) != 0;
    }

    /**
     * Get root recycler view's view pool so that the child recycler view can share the same
     * view pool with the parent.
     */
    public RecyclerView.RecycledViewPool getViewPool() {
        if (mIsGroupNotificationAdapter) {
            // currently only support one level of expansion.
            throw new IllegalStateException("CarNotificationViewAdapter is a child adapter; "
                    + "its view pool should not be reused.");
        }
        return mViewPool;
    }

    /**
     * Returns {@code true} if there are multiple groups with the same {@code groupKey}.
     */
    public boolean shouldRemoveGroupSummary(String groupKey) {
        return mGroupKeyToCountMap.getOrDefault(groupKey, /* defaultValue= */ 0) <= 1;
    }

    /**
     * Sets the NotificationClickHandlerFactory that allows for a hook to run a block off code
     * when  the notification is clicked. This is useful to dismiss a screen after
     * a notification list clicked.
     */
    public void setClickHandlerFactory(NotificationClickHandlerFactory clickHandlerFactory) {
        mClickHandlerFactory = clickHandlerFactory;
    }

    /**
     * Set notification groups as seen.
     *
     * @param start Initial adapter position of the notification groups.
     * @param end Final adapter position of the notification groups.
     */
    void setVisibleNotificationsAsSeen(int start, int end) {
        if (mNotificationDataManager == null || mIsGroupNotificationAdapter) {
            return;
        }

        start = Math.max(start, 0);
        end = Math.min(end, mNotifications.size() - 1);

        List<AlertEntry> notifications = new ArrayList();
        for (int i = start; i <= end; i++) {
            NotificationGroup group = mNotifications.get(i);
            AlertEntry groupSummary = group.getGroupSummaryNotification();
            if (groupSummary != null) {
                notifications.add(groupSummary);
            }

            notifications.addAll(group.getChildNotifications());
        }

        mNotificationDataManager.setVisibleNotificationsAsSeen(notifications);
    }

    @Override
    public int getConfigurationId() {
        return R.id.notification_list_uxr_config;
    }

    private static class ExpandedNotification {
        private String mKey;
        private boolean mIsExpanded;

        ExpandedNotification(String key, boolean isExpanded) {
            mKey = key;
            mIsExpanded = isExpanded;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ExpandedNotification)) {
                return false;
            }
            ExpandedNotification other = (ExpandedNotification) obj;

            return mKey.equals(other.getKey()) && mIsExpanded == other.isExpanded();
        }

        public String getKey() {
            return mKey;
        }

        public boolean isExpanded() {
            return mIsExpanded;
        }
    }
}
