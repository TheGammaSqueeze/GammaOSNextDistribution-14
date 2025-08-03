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

package com.android.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.app.Notification;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;
import android.testing.TestableResources;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.notification.template.BasicNotificationViewHolder;
import com.android.car.notification.template.CarNotificationBaseViewHolder;
import com.android.car.notification.template.GroupNotificationViewHolder;
import com.android.car.notification.template.GroupSummaryNotificationViewHolder;
import com.android.car.notification.template.InboxNotificationViewHolder;
import com.android.car.notification.template.MessageNotificationViewHolder;
import com.android.car.notification.template.ProgressNotificationViewHolder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarNotificationViewAdapterTest {

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

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };

    @Mock
    NotificationClickHandlerFactory mClickHandlerFactoryMock;
    @Mock
    PackageManager mPackageManager;
    @Mock
    CarUxRestrictions mCarUxRestrictionsMock;

    private AlertEntry mNotification1;
    private Notification.Builder mNotificationBuilder1;
    private CarNotificationViewAdapter mCarNotificationViewAdapter;
    private NotificationGroup mNotificationGroup1;
    private NotificationGroup mNotificationGroup2;
    private List<NotificationGroup> mNotificationGroupList1;

    @Before
    public void setupBaseActivityAndLayout() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        when(mClickHandlerFactoryMock.getClickHandler(any())).thenReturn(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    }
                });

        mNotificationBuilder1 = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);

        mNotification1 = new AlertEntry(new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME));

        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.packageName = PKG_1;
        when(mPackageManager.getApplicationInfoAsUser(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES), anyInt())).thenReturn(
                applicationInfo);
        when(mPackageManager.getApplicationInfo(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES))).thenReturn(applicationInfo);
        when(mPackageManager.getResourcesForApplication(applicationInfo)).thenReturn(
                mContext.getResources());
        mContext.setMockPackageManager(mPackageManager);

        mNotificationGroup1 = new NotificationGroup();
        mNotificationGroup1.addNotification(mNotification1);
        mNotificationGroup2 = new NotificationGroup();
        List<String> childTiles = new ArrayList<>();
        childTiles.add("child1");
        mNotificationGroup2.addNotification(mNotification1);
        mNotificationGroup2.setChildTitles(childTiles);
        mNotificationGroupList1 = new ArrayList<>();
        mNotificationGroupList1.add(mNotificationGroup1);
        mNotificationGroupList1.add(mNotificationGroup2);
    }

    @Test
    public void onCreateViewHolder_carWarningType_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_WARNING);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_groupType_shouldReturnObjectOfGroupNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.GROUP);

        assertThat(vh.getClass()).isEqualTo(GroupNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_groupSummaryType_shouldReturnObjectOfGroupSummaryNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.GROUP_SUMMARY);

        assertThat(vh.getClass()).isEqualTo(GroupSummaryNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_carInformation_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_carInfoInGroup_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_shouldReturnObjectOfMessageNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(MessageNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_message_shouldReturnObjectOfMessageNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.MESSAGE);

        assertThat(vh.getClass()).isEqualTo(MessageNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_progressInGroupType_shouldReturnObjectOfProgressNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.PROGRESS_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(ProgressNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_progressInGroup_shouldReturnObjectOfProgressNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.PROGRESS);

        assertThat(vh.getClass()).isEqualTo(ProgressNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_inboxInGroupType_shouldReturnObjectOfInboxNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.INBOX_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(InboxNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_inbox_shouldReturnObjectOfInboxNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.INBOX);

        assertThat(vh.getClass()).isEqualTo(InboxNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_basicInGroup_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.BASIC_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_basic_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext,
                /* isGroupNotificationAdapter= */ false,
                /* notificationItemController= */ null);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.BASIC);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onBindViewHolder_default_shouldThrowError() {
        initializeWithFactory();
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        assertThrows(IllegalArgumentException.class,
                () -> mCarNotificationViewAdapter.createViewHolder(null, 12345));
    }

    @Test
    public void onBindViewHolder_carWarning_shouldNotThrowError() {
        initializeWithFactory();
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.CAR_WARNING);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_carInformation_shouldNotThrowError() {
        initializeWithFactory();
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_basic_shouldNotThrowError() {
        initializeWithFactory();
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.BASIC);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_group_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_message_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageWithRestriction_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        when(mCarUxRestrictionsMock.getActiveRestrictions()).thenReturn(
                CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE);
        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageInGroup_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageInGroupWithRestriction_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        when(mCarUxRestrictionsMock.getActiveRestrictions()).thenReturn(
                CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE);
        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_progress_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.PROGRESS);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_progressInGroup_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.PROGRESS_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_inbox_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.INBOX);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_inboxInGroup_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.INBOX_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_groupSummary_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP_SUMMARY);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_carInfo_shouldNotThrowError() {
        initializeWithFactory();
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_basicInGroup_shouldNotThrowError() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.BASIC_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_group_shouldNotHideDismissButton() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
        assertThat(((CarNotificationBaseViewHolder) vh).shouldHideDismissButton()).isFalse();
    }

    @Test
    public void onBindViewHolder_groupSummary_shouldHideDismissButton() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP_SUMMARY);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
        assertThat(((CarNotificationBaseViewHolder) vh).shouldHideDismissButton()).isTrue();
    }

    @Test
    public void getItemViewType_shouldReturnGroup() {
        initializeWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.GROUP);
    }

    @Test
    public void getItemViewType_shouldReturnCarEmergency() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_CAR_EMERGENCY));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_EMERGENCY);
    }

    @Test
    public void getItemViewType_shouldReturnCarWarning() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_CAR_WARNING));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_WARNING);
    }

    @Test
    public void getItemViewType_shouldReturnCarInformation() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_CAR_INFORMATION));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_INFORMATION);
    }

    @Test
    public void getItemViewType_shouldReturnCarInformationInGroup() {
        initializeGroupAdapterWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_CAR_INFORMATION));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_INFORMATION_IN_GROUP);
    }

    @Test
    public void getItemViewType_shouldReturnMessageInGroup() {
        initializeGroupAdapterWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_MESSAGE));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.MESSAGE_IN_GROUP);
    }

    @Test
    public void getItemViewType_shouldReturnMessage() {
        initializeWithFactory();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                generateNotificationWithCategory(Notification.CATEGORY_MESSAGE));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.MESSAGE);
    }

    @Test
    public void getItemViewType_shouldReturnGroupSummary() {
        initializeWithFactory();

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(1);

        assertThat(itemViewType).isEqualTo(NotificationViewType.GROUP_SUMMARY);
    }

    @Test
    public void getItemViewType_shouldReturnBasic() {
        initializeWithFactory();

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(0);

        assertThat(itemViewType).isEqualTo(NotificationViewType.BASIC);
    }

    @Test
    public void getItemViewType_shouldReturnBasicInGroup() {
        initializeGroupAdapterWithFactory();

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(0);

        assertThat(itemViewType).isEqualTo(NotificationViewType.BASIC_IN_GROUP);
    }

    @Test
    public void getItemCount_shouldReturnTwo() {
        initializeGroupAdapterWithFactory();

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemCount = mCarNotificationViewAdapter.getItemCount();

        assertThat(itemCount).isEqualTo(2);
    }

    @Test
    public void setCarUxRestrictions_shouldSetCarUxRestrictions() {
        initializeGroupAdapterWithFactory();

        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        assertThat(mCarNotificationViewAdapter.getCarUxRestrictions()).isNotNull();
    }

    @Test
    public void setMaxItems_headerShouldBeFirstVisibleElement() {
        initializeGroupAdapterWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ true);

        mCarNotificationViewAdapter.setMaxItems(1);

        assertThat(mCarNotificationViewAdapter.getItemViewType(0)).isEqualTo(
                NotificationViewType.HEADER);
    }

    @Test
    public void setMaxItems_limitedMessageShouldBeLastVisibleElement() {
        initializeGroupAdapterWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ true);

        mCarNotificationViewAdapter.setMaxItems(1);

        assertThat(mCarNotificationViewAdapter.getItemViewType(
                mCarNotificationViewAdapter.getItemCount() - 1)).isEqualTo(
                mCarNotificationViewAdapter.getScrollingLimitedMessageViewType());
    }

    @Test
    public void setMaxItems_noHeaderAndFooter_getItemCount_shouldReturnTwo() {
        initializeGroupAdapterWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        mCarNotificationViewAdapter.setMaxItems(1);

        // Count should be two - one for the allotted notification and one for the limited message
        assertThat(mCarNotificationViewAdapter.getItemCount()).isEqualTo(2);
    }

    @Test
    public void setMaxItems_hasHeaderAndFooter_getItemCount_shouldReturnThree() {
        initializeGroupAdapterWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ true);

        mCarNotificationViewAdapter.setMaxItems(1);

        // Count should be three - one for the allotted notification, one for the limited message,
        // and one for the header
        assertThat(mCarNotificationViewAdapter.getItemCount()).isEqualTo(3);
    }

    @Test
    public void setMaxItems_hasHeaderAndFooter_hasSeenUnseen_getItemCount_shouldReturnFive() {
        initializeWithFactoryShowingRecentAndOlderHeaders();
        NotificationGroup seenGroup = new NotificationGroup();
        seenGroup.setSeen(true);
        seenGroup.addNotification(mNotification1);
        NotificationGroup unseenGroup = new NotificationGroup();
        unseenGroup.setSeen(false);
        unseenGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(seenGroup);
        mNotificationGroupList1.add(unseenGroup);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ true);

        mCarNotificationViewAdapter.setMaxItems(1);

        // Count should be three: one for the allotted notification, one for the limited message,
        // and one for the header
        assertThat(mCarNotificationViewAdapter.getItemCount()).isEqualTo(5);
    }

    @Test
    public void getViewPool_shouldReturnNotNull() {
        initializeWithFactory();

        assertThat(mCarNotificationViewAdapter.getViewPool()).isNotNull();
    }

    @Test
    public void getViewPool_shouldThrowError() {
        initializeGroupAdapterWithFactory();

        assertThrows(IllegalStateException.class,
                () -> mCarNotificationViewAdapter.getViewPool());
    }

    @Test
    public void getItemId_shouldReturnHashCode() {
        initializeWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(),
                /* isSeen= */ false, /* isExpanded= */ true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo(notificationGroup.getGroupKey().hashCode());
    }

    @Test
    public void getItemId_showRecentAndOldHeaders_shouldReturnHashCode() {
        initializeWithFactoryShowingRecentAndOlderHeaders();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setSeen(true);
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(),
                /* isSeen= */ false, /* isExpanded= */ true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo((notificationGroup.getGroupKey() + notificationGroup.isSeen())
                .hashCode());
    }

    @Test
    public void getItemId_showRecentAndOldHeaders_shouldReturnSingleNotificationHashCode() {
        initializeWithFactoryShowingRecentAndOlderHeaders();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setSeen(false);
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(),
                /* isSeen= */ false, /* isExpanded= */ true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo((notificationGroup.getSingleNotification().getKey()
                + notificationGroup.isSeen()).hashCode());
    }

    @Test
    public void getItemId_shouldReturnSingleNotificationHashCode() {
        initializeWithFactory();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(),
                /* isSeen= */ false, /* isExpanded= */ true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo(notificationGroup.getSingleNotification().getKey().hashCode());
    }

    @Test
    public void shouldRemoveGroupSummary_returnTrue_oneGroupWithSameGroupKeyIsPresent() {
        String group = "TEST_GROUP";
        initializeWithFactoryShowingRecentAndOlderHeaders();
        AlertEntry groupSummaryNotification = generateNotification(Notification.CATEGORY_MESSAGE,
                group, /* overrideGroupKey= */ null, /* isGroupSummary= */ true);
        NotificationGroup notificationGroup = generateGroupedNotification(group);
        notificationGroup.setGroupSummaryNotification(groupSummaryNotification);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        boolean result = mCarNotificationViewAdapter.shouldRemoveGroupSummary(
                groupSummaryNotification.getStatusBarNotification().getGroupKey());

        assertThat(result).isTrue();
    }

    @Test
    public void shouldRemoveGroupSummary_returnFalse_multipleGroupsWithSameGroupKeyArePresent() {
        String group = "TEST_GROUP";
        initializeWithFactoryShowingRecentAndOlderHeaders();
        AlertEntry groupSummaryNotification = generateNotification(Notification.CATEGORY_MESSAGE,
                group, /* overrideGroupKey= */ null, /* isGroupSummary= */ true);
        NotificationGroup notificationGroup1 = generateGroupedNotification(group);
        NotificationGroup notificationGroup2 = generateGroupedNotification(group);
        notificationGroup1.setGroupSummaryNotification(groupSummaryNotification);
        notificationGroup2.setGroupSummaryNotification(groupSummaryNotification);
        mNotificationGroupList1.add(notificationGroup1);
        mNotificationGroupList1.add(notificationGroup2);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        boolean result = mCarNotificationViewAdapter.shouldRemoveGroupSummary(
                groupSummaryNotification.getStatusBarNotification().getGroupKey());

        assertThat(result).isFalse();
    }


    private NotificationGroup generateGroupedNotification(String group) {
        NotificationGroup notificationGroup = new NotificationGroup();
        for (int i = 0; i < 5; i++) {
            notificationGroup.addNotification(generateNotification(Notification.CATEGORY_MESSAGE,
                    group, /* overrideGroupKey= */ null,
                    /* isGroupSummary= */ false));
        }
        return notificationGroup;
    }

    private AlertEntry generateNotificationWithCategory(String category) {
        return generateNotification(category, /* group= */ null, OVERRIDE_GROUP_KEY,
                /* isGroupSummary= */ false);
    }

    private AlertEntry generateNotification(String category, @Nullable String group,
            @Nullable String overrideGroupKey, boolean isGroupSummary) {
        Notification.Builder nb = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(category)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setGroupSummary(isGroupSummary);

        if (group != null) {
            nb.setGroup(group);
        }

        return new AlertEntry(new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, nb.build(), USER_HANDLE,
                overrideGroupKey, POST_TIME));
    }

    private void initializeWithFactoryShowingRecentAndOlderHeaders() {
        initializeWithFactory(/* isGroup= */ false, /* showRecentAndOldHeaders= */ true);
    }

    private void initializeGroupAdapterWithFactory() {
        initializeWithFactory(/* isGroup= */ true, /* showRecentAndOldHeaders= */ false);
    }

    private void initializeWithFactory() {
        initializeWithFactory(/* isGroup= */ false, /* showRecentAndOldHeaders= */ false);
    }

    private void initializeWithFactory(boolean isGroup, boolean showRecentAndOldHeaders) {
        TestableResources testableResources = mContext.getOrCreateTestableResources();
        testableResources.addOverride(R.bool.config_showRecentAndOldHeaders,
                /* value= */ showRecentAndOldHeaders);

        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, isGroup,
                /* notificationItemController= */ null);
        mCarNotificationViewAdapter.setClickHandlerFactory(mClickHandlerFactoryMock);
    }
}
