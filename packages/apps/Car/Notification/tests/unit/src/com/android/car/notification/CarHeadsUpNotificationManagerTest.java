/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.notification.headsup.CarHeadsUpNotificationContainer;
import com.android.car.notification.utils.MockMessageNotificationBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class CarHeadsUpNotificationManagerTest {
    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CHANNEL_NAME = "CHANNEL_NAME";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final String EXTRA_BIG_TEXT_VALUE = "EXTRA_BIG_TEXT";
    private static final String EXTRA_SUMMARY_TEXT_VALUE = "EXTRA_SUMMARY_TEXT";
    private static final long POST_TIME = 12345L;
    private static final UserHandle USER_HANDLE = new UserHandle(/* userId= */ 12);
    private static final NotificationChannel CHANNEL = new NotificationChannel(CHANNEL_ID,
            CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };
    @Mock
    NotificationListenerService.RankingMap mRankingMapMock;
    @Mock
    NotificationListenerService.Ranking mRankingMock;
    @Mock
    NotificationClickHandlerFactory mClickHandlerFactory;
    @Mock
    NotificationDataManager mNotificationDataManager;
    @Mock
    PackageManager mPackageManager;
    @Mock
    CarHeadsUpNotificationContainer mCarHeadsUpNotificationContainer;
    @Mock
    CarHeadsUpNotificationQueue mCarHeadsUpNotificationQueue;
    @Mock
    KeyguardManager mKeyguardManager;
    @Mock
    Handler mHandlerMock;
    @Captor
    ArgumentCaptor<View> mViewCaptor;
    private CarHeadsUpNotificationManager mManager;
    private AlertEntry mAlertEntryMessageHeadsUp;
    private AlertEntry mAlertEntryNavigationHeadsUp;
    private AlertEntry mAlertEntryCallHeadsUp;
    private AlertEntry mAlertEntryInboxHeadsUp;
    private AlertEntry mAlertEntryWarningHeadsUp;
    private AlertEntry mAlertEntryEmergencyHeadsUp;
    private AlertEntry mAlertEntryCarInformationHeadsUp;
    private Map<String, AlertEntry> mActiveNotifications;
    private List<CarHeadsUpNotificationManager.HeadsUpState> mHeadsUpStates;


    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_showNavigationHeadsup, /* value= */ true);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(mPackageManager.getApplicationInfoAsUser(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES), anyInt())).thenReturn(
                applicationInfo);
        when(mPackageManager.getApplicationInfo(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES))).thenReturn(applicationInfo);
        when(mPackageManager.getResourcesForApplication(applicationInfo)).thenReturn(
                mContext.getResources());
        mContext.setMockPackageManager(mPackageManager);

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);
        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        when(mClickHandlerFactory.getClickHandler(any())).thenReturn(v -> {
        });

        when(mRankingMock.getChannel()).thenReturn(CHANNEL);
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        Notification mNotificationMessageHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setHasMessagingStyle(true)
                .setHasReplyAction(true)
                .setHasMarkAsRead(true)
                .build();
        Notification mNotificationNavigationHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build();
        Notification mNotificationCallHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CALL)
                .build();
        Notification mNotificationWarningHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_WARNING)
                .build();
        Notification mNotificationEmergencyHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                .build();
        Notification mNotificationCarInformationHeadsUp = new MockMessageNotificationBuilder(
                mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .build();

        Bundle bundle = new Bundle();
        bundle.putString(Notification.EXTRA_BIG_TEXT, EXTRA_BIG_TEXT_VALUE);
        bundle.putString(Notification.EXTRA_SUMMARY_TEXT, EXTRA_SUMMARY_TEXT_VALUE);
        Notification mNotificationBuilderInboxHeadsUp = new MockMessageNotificationBuilder(
                mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setExtras(bundle)
                .setHasMessagingStyle(true)
                .setHasReplyAction(true)
                .setHasMarkAsRead(true)
                .build();

        mAlertEntryMessageHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationMessageHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryNavigationHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_2, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationNavigationHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        mAlertEntryCallHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationCallHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryInboxHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationBuilderInboxHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        mAlertEntryWarningHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationWarningHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryEmergencyHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationEmergencyHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        mAlertEntryCarInformationHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationCarInformationHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));

        mActiveNotifications = new HashMap<>();
        mHeadsUpStates = new ArrayList<>();

        createCarHeadsUpNotificationManager();
    }

    @Test
    public void maybeShowHeadsUp_isNotImportant_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_DEFAULT);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryNavigationHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any(), any());
    }

    @Test
    public void maybeShowHeadsUp_isImportanceHigh_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryNavigationHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryNavigationHeadsUp,
                mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_categoryCarInformation_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryCarInformationHeadsUp,
                mRankingMapMock, mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any(), any());
    }

    @Test
    public void maybeShowHeadsUp_categoryMessage_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryMessageHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryMessageHeadsUp, mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_categoryCall_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryCallHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCallHeadsUp, mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_categoryNavigation_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryNavigationHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryNavigationHeadsUp,
                mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_inboxHeadsUp_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryInboxHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryInboxHeadsUp, mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_isSignedWithPlatformKey_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ true);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryCarInformationHeadsUp,
                mRankingMapMock, mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCarInformationHeadsUp,
                mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_isSystemApp_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ true, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryCarInformationHeadsUp,
                mRankingMapMock, mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCarInformationHeadsUp,
                mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_nonMutedNotification_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(false);
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryInboxHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryInboxHeadsUp, mRankingMapMock);
    }

    @Test
    public void maybeShowHeadsUp_mutedNotification_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(true);
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowHeadsUp(mAlertEntryInboxHeadsUp, mRankingMapMock,
                mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any(), any());
    }

    @Test
    public void getActiveHeadsUpNotifications_shouldReturnOne()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowHeadsUp(mAlertEntryNavigationHeadsUp, mRankingMapMock,
                mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(1);
    }

    @Test
    public void getActiveHeadsUpNotifications_diffNotifications_shouldReturnTwo()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowHeadsUp(mAlertEntryCallHeadsUp, mRankingMapMock, mActiveNotifications);
        mManager.maybeShowHeadsUp(mAlertEntryNavigationHeadsUp, mRankingMapMock,
                mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(2);
    }

    @Test
    public void getActiveHeadsUpNotifications_sameNotifications_shouldReturnOne()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowHeadsUp(mAlertEntryCallHeadsUp, mRankingMapMock, mActiveNotifications);
        mManager.maybeShowHeadsUp(mAlertEntryCallHeadsUp, mRankingMapMock, mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(1);
    }

    @Test
    public void isHeadsUpDismissible_ongoingCallNotificationWithFullScreenIntent_returnsFalse() {
        Notification.Builder notificationBuilder = new Notification.Builder(mContext, CHANNEL_ID)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setFullScreenIntent(mock(PendingIntent.class), /* highPriority= */ true);
        StatusBarNotification sbn = mock(StatusBarNotification.class);
        when(sbn.getNotification()).thenReturn(notificationBuilder.build());
        when(sbn.isOngoing()).thenReturn(true);
        AlertEntry alertEntry = new AlertEntry(sbn, /* postTime= */ 1000);

        boolean result = CarHeadsUpNotificationManager.isHeadsUpDismissible(alertEntry);

        assertThat(result).isFalse();
    }

    @Test
    public void notification_removedFromQueue_notifyListeners()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.registerHeadsUpNotificationStateChangeListener((alertEntry, headsUpState) -> {
            mHeadsUpStates.add(headsUpState);
        });
        CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback queueCallback =
                mManager.getCarHeadsUpNotificationQueueCallback();

        queueCallback.removedFromHeadsUpQueue(mAlertEntryMessageHeadsUp);

        assertThat(mHeadsUpStates.size()).isEqualTo(1);
        assertThat(mHeadsUpStates.get(0)).isEqualTo(
                CarHeadsUpNotificationManager.HeadsUpState.REMOVED_FROM_QUEUE);
    }

    @Test
    public void clearCache_viewsRemovedFromCarHeadsUpNotificationContainer() {
        CarHeadsUpNotificationContainer container = mManager.mHunContainer;
        HeadsUpEntry notification1 = createMockHeadsUpEntry("key1");
        HeadsUpEntry notification2 = createMockHeadsUpEntry("key2");
        mManager.addActiveHeadsUpNotification(notification1);
        mManager.addActiveHeadsUpNotification(notification2);

        mManager.clearCache();

        verify(container, times(2)).removeNotification(mViewCaptor.capture());
        assertThat(mViewCaptor.getAllValues().containsAll(List.of(new View[]{
                notification1.getNotificationView(), notification2.getNotificationView()})))
                .isTrue();
    }

    private void createCarHeadsUpNotificationManager() {
        createCarHeadsUpNotificationManager(mCarHeadsUpNotificationQueue);
    }

    private void createCarHeadsUpNotificationManager(
            @Nullable CarHeadsUpNotificationQueue carHeadsUpNotificationQueue) {
        mManager = new CarHeadsUpNotificationManager(mContext, mClickHandlerFactory,
                mCarHeadsUpNotificationContainer) {
            @Override
            protected NotificationListenerService.Ranking getRanking() {
                return mRankingMock;
            }
        };
        if (carHeadsUpNotificationQueue != null) {
            mManager.setCarHeadsUpNotificationQueue(carHeadsUpNotificationQueue);
        }
        mManager.setNotificationDataManager(mNotificationDataManager);
    }

    private void setPackageInfo(String packageName, boolean isSystem,
            boolean isSignedWithPlatformKey) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mock(PackageInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        packageInfo.packageName = packageName;
        when(applicationInfo.isPrivilegedApp()).thenReturn(isSystem);
        when(applicationInfo.isSystemApp()).thenReturn(isSystem);
        when(applicationInfo.isSignedWithPlatformKey()).thenReturn(isSignedWithPlatformKey);
        packageInfo.applicationInfo = applicationInfo;
        when(mPackageManager.getPackageInfoAsUser(eq(packageName), anyInt(), anyInt())).thenReturn(
                packageInfo);
    }

    private HeadsUpEntry createMockHeadsUpEntry(String key) {
        HeadsUpEntry headsUpEntry = mock(HeadsUpEntry.class);
        when(headsUpEntry.getKey()).thenReturn(key);
        when(headsUpEntry.getHandler()).thenReturn(mHandlerMock);
        View headsUpNotificationView = mock(View.class);
        when(headsUpEntry.getNotificationView()).thenReturn(headsUpNotificationView);
        return headsUpEntry;
    }
}