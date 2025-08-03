/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.notification.template;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Notification;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;
import android.view.LayoutInflater;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.notification.AlertEntry;
import com.android.car.notification.CarNotificationViewAdapter;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationGroup;
import com.android.car.notification.R;
import com.android.car.notification.utils.MockMessageNotificationBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class GroupNotificationViewHolderTest {
    private static final int GROUP_EXPANSION_INCREMENT_SIZE = 5;
    private static final String PKG = "package_1";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final long POST_TIME = 12345L;
    private static final UserHandle USER_HANDLE = new UserHandle(/* userId= */ 12);
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    private GroupNotificationViewHolder mGroupNotificationViewHolder;
    private RecyclerView mNotificationListView;
    private View mExpansionFooterView;
    @Mock
    private NotificationClickHandlerFactory mNotificationClickHandlerFactory;
    @Mock
    private CarNotificationViewAdapter mCarNotificationViewAdapter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_showNavigationHeadsup, GROUP_EXPANSION_INCREMENT_SIZE);
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_useLauncherIcon, /* value= */ true);
        View groupNotificationView = LayoutInflater.from(mContext)
                .inflate(R.layout.group_notification_template, /* root= */ null);
        mGroupNotificationViewHolder = new GroupNotificationViewHolder(groupNotificationView,
                mNotificationClickHandlerFactory);
        mExpansionFooterView = groupNotificationView.findViewById(R.id.expansion_footer);
        mNotificationListView = groupNotificationView.findViewById(R.id.notification_list);
    }

    @Test
    public void bind_22notifications_collapsed_1NotificationShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ false);

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(1);
    }

    @Test
    public void bind_22notifications_expanded1_5NotificationsShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ true);

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(5);
    }

    @Test
    public void bind_22notifications_expanded2_10NotificationsShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ true);

        mExpansionFooterView.performClick();

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(10);
    }

    @Test
    public void bind_22notifications_expanded3_15NotificationsShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ true);

        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(15);
    }

    @Test
    public void bind_22notifications_expanded4_20NotificationsShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ true);

        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(20);
    }

    @Test
    public void bind_22notifications_expanded5_22NotificationsShown() {
        mGroupNotificationViewHolder.bind(getNotificationGroup(/* size= */ 22),
                mCarNotificationViewAdapter, /* isExpanded= */ true);

        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();

        assertThat(mNotificationListView.getAdapter().getItemCount()).isEqualTo(22);
    }

    @Test
    public void bind_22notifications_expanded5ThenCollapse_setExpandedFalse() {
        NotificationGroup group = getNotificationGroup(/* size= */ 22);
        mGroupNotificationViewHolder
                .bind(group, mCarNotificationViewAdapter, /* isExpanded= */ true);

        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();
        mExpansionFooterView.performClick();

        verify(mCarNotificationViewAdapter)
                .setExpanded(group.getGroupKey(), group.isSeen(), /* isExpanded= */ false);
    }

    @Test
    public void resetNotifications_removeAllGroupedNotifications() {
        CarNotificationViewAdapter mockAdapter = mock(CarNotificationViewAdapter.class);
        NotificationGroup group = getNotificationGroup(/* size= */ 22);
        mGroupNotificationViewHolder.setAdapter(mockAdapter);
        mGroupNotificationViewHolder
                .bind(group, mCarNotificationViewAdapter, /* isExpanded= */ true);

        mGroupNotificationViewHolder.collapseGroup();

        // equal to 1 because the expected behaviour is to collapse the
        // GroupNotificationViewHolder by removing all notifications and
        // displaying to top notification or GroupNotificationSummary.
        verify(mockAdapter, times(1)).setNotifications(
                argThat(notificationGroupList -> notificationGroupList.size() == 1), eq(false));
    }

    private NotificationGroup getNotificationGroup(int size) {
        Notification groupSummaryNotification = new MockMessageNotificationBuilder(
                mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .build();
        AlertEntry groupSummaryAlertEntry = new AlertEntry(
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        groupSummaryNotification, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        NotificationGroup group = new NotificationGroup();
        group.setGroupSummaryNotification(groupSummaryAlertEntry);
        for (int i = 0; i < size; i++) {
            Notification notification = new MockMessageNotificationBuilder(
                    mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                    .setContentTitle(CONTENT_TITLE)
                    .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                    .build();
            AlertEntry alertEntry = new AlertEntry(
                    new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, INITIAL_PID,
                            notification, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
            group.addNotification(alertEntry);
        }
        return group;
    }
}
