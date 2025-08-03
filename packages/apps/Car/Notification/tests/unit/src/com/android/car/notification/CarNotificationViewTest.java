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
 * limitations under the License.
 */

package com.android.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.notification.template.GroupNotificationViewHolder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarNotificationViewTest {

    private static final String PKG_1 = "package_1";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);

    private CarNotificationView mCarNotificationView;

    @Rule
    public TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };

    @Mock
    private NotificationClickHandlerFactory mClickHandlerFactory;

    @Captor
    private ArgumentCaptor<List<NotificationGroup>> mDismissedNotificationsCaptor;
    @Captor
    private ArgumentCaptor<Intent> mIntentCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(mContext);
        FrameLayout frameLayout = new FrameLayout(mContext);
        LayoutInflater.from(mContext)
                .inflate(R.layout.test_car_notification_view_layout, frameLayout);
        mCarNotificationView = frameLayout.findViewById(R.id.notification_view);
        mCarNotificationView.setClickHandlerFactory(mClickHandlerFactory);
    }

    @Test
    public void onClickClearAllButton_callsFactoryClearNotificationsWithDismissibleNotifications() {
        Button clearAllButton = mCarNotificationView.findViewById(R.id.clear_all_button);
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        notifications.add(notDismissible);
        mCarNotificationView.setNotifications(notifications);

        clearAllButton.callOnClick();

        verify(mClickHandlerFactory).clearNotifications(mDismissedNotificationsCaptor.capture());
        assertThat(mDismissedNotificationsCaptor.getValue()).containsExactly(dismissible);
    }


    @Test
    public void onClickManageButton_actionNotificationSettings() {
        Button manageButton = mCarNotificationView.findViewById(R.id.manage_button);

        manageButton.callOnClick();

        verify(mContext).startActivityAsUser(mIntentCaptor.capture(), any());
        assertThat(mIntentCaptor.getValue().getAction())
                .isEqualTo(Settings.ACTION_NOTIFICATION_SETTINGS);
    }

    @Test
    public void onClickManageButton_categoryDefault() {
        Button manageButton = mCarNotificationView.findViewById(R.id.manage_button);

        manageButton.callOnClick();

        verify(mContext).startActivityAsUser(mIntentCaptor.capture(), any());
        assertThat(mIntentCaptor.getValue().getCategories())
                .containsExactly(Intent.CATEGORY_DEFAULT);
    }

    @Test
    public void onClickManageButton_flagsNewTaskAndMultipleTask() {
        Button manageButton = mCarNotificationView.findViewById(R.id.manage_button);

        manageButton.callOnClick();

        verify(mContext).startActivityAsUser(mIntentCaptor.capture(), any());
        assertThat(mIntentCaptor.getValue().getFlags())
                .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    @Test
    public void setNotifications_notEmpty_listViewIsVisible() {
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        notifications.add(notDismissible);

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.notifications).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void setNotifications_notEmpty_emptyNotificationTextViewIsGone() {
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        notifications.add(notDismissible);

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.empty_notification_text).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void setNotifications_notEmpty_manageButtonIsGone() {
        NotificationGroup dismissible =
                getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible =
                getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        notifications.add(notDismissible);

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.manage_button).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void setNotifications_empty_listViewStaysVisible() {
        List<NotificationGroup> notifications = new ArrayList<>();

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.notifications).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void setNotifications_empty_emptyNotificationTextViewIsVisible() {
        List<NotificationGroup> notifications = new ArrayList<>();

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.empty_notification_text).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void setNotifications_empty_manageButtonIsVisible() {
        List<NotificationGroup> notifications = new ArrayList<>();

        mCarNotificationView.setNotifications(notifications);

        assertThat(mCarNotificationView.findViewById(R.id.manage_button).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void removeNotification_notificationNotRemoved() {
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        mCarNotificationView.setNotifications(notifications);

        mCarNotificationView.removeNotification(notDismissible.getSingleNotification());

        assertThat(mCarNotificationView.getNotifications()).isEqualTo(notifications);
    }

    @Test
    public void removeNotification_notificationRemoved_wholeGroupRemoved() {
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        notifications.add(notDismissible);
        mCarNotificationView.setNotifications(notifications);

        mCarNotificationView.removeNotification(dismissible.getSingleNotification());

        assertThat(mCarNotificationView.getNotifications()).isEqualTo(List.of(notDismissible));
    }

    @Test
    public void removeNotification_notificationRemoved_partOfGroupRemoved() {
        NotificationGroup dismissible = getNotificationGroup(/* isDismissible= */ true);
        NotificationGroup notDismissible = getNotificationGroup(/* isDismissible= */ false);
        dismissible.addNotification(notDismissible.getSingleNotification());
        List<NotificationGroup> notifications = new ArrayList<>();
        notifications.add(dismissible);
        mCarNotificationView.setNotifications(notifications);

        mCarNotificationView.removeNotification(dismissible.getSingleNotification());

        assertThat(mCarNotificationView.getNotifications().toString())
                .isEqualTo(List.of(notDismissible).toString());
    }

    @Test
    public void resetState_collapseGroupForAllGroupsCalled() {
        CarNotificationViewAdapter mockAdapter = mock(CarNotificationViewAdapter.class);
        when(mockAdapter.getItemCount()).thenReturn(3);
        GroupNotificationViewHolder mockGroupNotificationViewHolder1 = mock(
                GroupNotificationViewHolder.class);
        GroupNotificationViewHolder mockGroupNotificationViewHolder2 = mock(
                GroupNotificationViewHolder.class);
        when(mockGroupNotificationViewHolder1.getItemViewType()).thenReturn(
                NotificationViewType.GROUP);
        when(mockGroupNotificationViewHolder2.getItemViewType()).thenReturn(
                NotificationViewType.GROUP);
        RecyclerView mockListView = mock(RecyclerView.class);
        when(mockListView.findViewHolderForAdapterPosition(0)).thenReturn(
                mock(RecyclerView.ViewHolder.class));
        when(mockListView.findViewHolderForAdapterPosition(1)).thenReturn(
                mockGroupNotificationViewHolder1);
        when(mockListView.findViewHolderForAdapterPosition(2)).thenReturn(
                mockGroupNotificationViewHolder2);
        mCarNotificationView.setAdapter(mockAdapter);
        mCarNotificationView.setListView(mockListView);

        mCarNotificationView.resetState();

        verify(mockAdapter, times(1)).collapseAllGroups();
        // only call resetNotifications on GroupNotificationViewHolders
        verify(mockGroupNotificationViewHolder1, times(1)).collapseGroup();
        verify(mockGroupNotificationViewHolder2, times(1)).collapseGroup();
    }

    private NotificationGroup getNotificationGroup(boolean isDismissible) {

        Notification.Builder builder = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE);
        AlertEntry notification = new AlertEntry(new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, builder.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME));

        if (!isDismissible) {
            notification.getNotification().flags =
                    notification.getNotification().flags | Notification.FLAG_ONGOING_EVENT;
            notification.getNotification().flags =
                    notification.getNotification().flags | Notification.FLAG_FOREGROUND_SERVICE;
        }

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(notification);

        return notificationGroup;
    }
}