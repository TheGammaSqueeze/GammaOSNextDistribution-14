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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class CarNotificationListenerTest {

    private static final int CURRENT_USER_ID = 10;
    private static final String TEST_KEY = "TEST_KEY";
    private static final String TEST_OLD_OVERRIDE_GROUP_KEY = "TEST_OLD_OVERRIDE_GROUP_KEY";
    private static final String TEST_NEW_OVERRIDE_GROUP_KEY = "TEST_NEW_OVERRIDE_GROUP_KEY";

    private final Object mLock = new Object();
    private CarNotificationListener mCarNotificationListener;
    private NotificationListenerService.RankingMap mRankingMap;
    @Mock
    private CarHeadsUpNotificationManager mCarHeadsUpNotificationManager;
    @Mock
    private Handler mHandler;
    @Mock
    private StatusBarNotification mStatusBarNotification;
    @Mock
    private NotificationDataManager mNotificationDataManager;
    @Mock
    private CarUxRestrictionManagerWrapper mCarUxRestrictionManagerWrapper;

    @Before
    public void setup() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        Context context = ApplicationProvider.getApplicationContext();
        mCarNotificationListener = spy(new CarNotificationListener());
        mCarNotificationListener.setHandler(mHandler);

        // wait for the listener to be connected before going further
        doAnswer(a -> {
            a.callRealMethod();
            synchronized (mLock) {
                mLock.notify();
            }
            return null;
        }).when(mCarNotificationListener).onListenerConnected();
        mCarNotificationListener.registerAsSystemService(context, mCarUxRestrictionManagerWrapper,
                mCarHeadsUpNotificationManager);
        synchronized (mLock) {
            while (!mCarNotificationListener.getIsListenerConnected()) {
                mLock.wait();
            }
        }
        mCarNotificationListener.setNotificationDataManager(mNotificationDataManager);

        createMockRankingMap(NotificationManager.IMPORTANCE_DEFAULT);

        when(mStatusBarNotification.getKey()).thenReturn(TEST_KEY);
        when(mStatusBarNotification.getOverrideGroupKey()).thenReturn(TEST_OLD_OVERRIDE_GROUP_KEY);
        when(mStatusBarNotification.getUser()).thenReturn(new UserHandle(CURRENT_USER_ID));
    }

    @After
    public void tearDown() throws RemoteException {
        if (mCarNotificationListener != null) {
            mCarNotificationListener.unregisterAsSystemService();
        }
    }

    @Test
    public void onNotificationPosted_isHun_notForCurrentUser_ignoresTheEvent() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_NULL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager, never()).addNewMessageNotification(any(AlertEntry.class));
        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onNotificationPosted_isNotHun_notForCurrentUser_ignoresTheEvent() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_NULL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager, never()).addNewMessageNotification(any(AlertEntry.class));
        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onNotificationPosted_isHun_isForCurrentUser_addsAlertEntryToDataManager() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager).addNewMessageNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isHun_isForCurrentUser_doesNotAddItToActiveNotifications() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        assertThat(mCarNotificationListener.getNotifications().containsKey(
                mStatusBarNotification.getKey())).isFalse();
    }

    @Test
    public void onNotificationPosted_isHun_isForAllUsers_addsAlertEntryToDataManager() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_ALL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager).addNewMessageNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isHun_isForAllUsers_doesNotAddItToActiveNotifications() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_ALL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        assertThat(mCarNotificationListener.getNotifications().containsKey(
                mStatusBarNotification.getKey())).isFalse();
    }

    @Test
    public void onNotificationPosted_isNotHun_isForCurrentUser_overrideGroupKeySet() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mStatusBarNotification).setOverrideGroupKey(eq(TEST_NEW_OVERRIDE_GROUP_KEY));
    }

    @Test
    public void onNotificationPosted_isNotHun_isForCurrentUser_addsAlertEntryToDataManager() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager).addNewMessageNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isNotHun_isForCurrentUser_addsItToActiveNotifications() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        assertThat(mCarNotificationListener.getNotifications().containsKey(
                mStatusBarNotification.getKey())).isTrue();
    }

    @Test
    public void onNotificationPosted_lowPriority_isForCurrentUser_untracksUnseenNotification() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        createMockRankingMap(NotificationManager.IMPORTANCE_LOW);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager, never()).addNewMessageNotification(any(AlertEntry.class));
        verify(mNotificationDataManager).untrackUnseenNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isNotHun_isForAllUsers_addsAlertEntryToDataManager() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_ALL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager).addNewMessageNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isNotHun_isForAllUsers_addsItToActiveNotifications() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_ALL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        assertThat(mCarNotificationListener.getNotifications().containsKey(
                mStatusBarNotification.getKey())).isTrue();
    }

    @Test
    public void onNotificationPosted_lowPriority_isForAllUsers_untracksUnseenNotification() {
        UserHandle userHandle = new UserHandle(UserHandle.USER_ALL);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(false);

        createMockRankingMap(NotificationManager.IMPORTANCE_LOW);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mNotificationDataManager, never()).addNewMessageNotification(any(AlertEntry.class));
        verify(mNotificationDataManager).untrackUnseenNotification(any(AlertEntry.class));
    }

    @Test
    public void onNotificationPosted_isHun_doesNotNotifyHandler() {
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        testingHeadsUpNotification(true);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onStateChange_hunDismissed_notifiesHandler() {
        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);

        mCarNotificationListener.onStateChange(alertEntry,
                CarHeadsUpNotificationManager.HeadsUpState.DISMISSED);

        verify(mHandler).sendMessage(any(Message.class));
    }

    @Test
    public void onStateChange_hunRemovedFromQueue_notifiesHandler() {
        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);

        mCarNotificationListener.onStateChange(alertEntry,
                CarHeadsUpNotificationManager.HeadsUpState.REMOVED_FROM_QUEUE);

        verify(mHandler).sendMessage(any(Message.class));
    }

    @Test
    public void onStateChange_hunRemovedBySender_doesNotNotifyHandler() {
        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);

        mCarNotificationListener.onStateChange(alertEntry,
                CarHeadsUpNotificationManager.HeadsUpState.REMOVED_BY_SENDER);

        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onStateChange_hunShown_doesNotNotifyHandler() {
        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);

        mCarNotificationListener.onStateChange(alertEntry,
                CarHeadsUpNotificationManager.HeadsUpState.SHOWN);

        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onStateChange_overrideGroupKeySet() {
        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);

        mCarNotificationListener.onStateChange(alertEntry,
                CarHeadsUpNotificationManager.HeadsUpState.DISMISSED);

        verify(mStatusBarNotification).setOverrideGroupKey(eq(TEST_NEW_OVERRIDE_GROUP_KEY));
    }

    @Test
    public void onNotificationPosted_isNotHun_isOngoing_notifiesHandler() {
        testingHeadsUpNotification(false);
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);

        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);

        verify(mHandler).sendMessage(any(Message.class));
    }

    @Test
    public void onNotificationRemoved_notificationPreviouslyAdded_removesNotification() {
        AlertEntry alertEntry = new AlertEntry(mStatusBarNotification);
        mCarNotificationListener.getNotifications().put(alertEntry.getKey(), alertEntry);
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);

        mCarNotificationListener.onNotificationRemoved(mStatusBarNotification);

        assertThat(mCarNotificationListener.getNotifications().containsKey(alertEntry.getKey()))
                .isFalse();
    }

    @Test
    public void onNotificationRankingUpdate_overrideGroupKeyUpdated_doesNotNotifyHandler() {
        testingHeadsUpNotification(false);
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        when(mStatusBarNotification.getOverrideGroupKey()).thenReturn(TEST_NEW_OVERRIDE_GROUP_KEY);
        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);
        reset(mHandler);

        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);

        verify(mHandler, never()).sendMessage(any(Message.class));
    }

    @Test
    public void onNotificationRankingUpdate_overrideGroupKeyUpdated_notifiesHandler() {
        testingHeadsUpNotification(false);
        UserHandle userHandle = new UserHandle(CURRENT_USER_ID);
        when(mStatusBarNotification.getUser()).thenReturn(userHandle);
        mCarNotificationListener.onNotificationPosted(mStatusBarNotification, mRankingMap);
        when(mStatusBarNotification.getOverrideGroupKey())
                .thenReturn(TEST_OLD_OVERRIDE_GROUP_KEY + "A");
        reset(mHandler);

        mCarNotificationListener.onNotificationRankingUpdate(mRankingMap);

        verify(mHandler).sendMessage(any(Message.class));
    }

    private void testingHeadsUpNotification(boolean isHeadsUpNotification) {
        Notification notification = new Notification();
        if (isHeadsUpNotification) {
            // Messages are always heads-up notifications.
            notification.category = Notification.CATEGORY_MESSAGE;
            when(mCarHeadsUpNotificationManager.maybeShowHeadsUp(any(), any(), any()))
                    .thenReturn(true);
        }

        when(mStatusBarNotification.getNotification()).thenReturn(notification);
    }

    private void createMockRankingMap(int importance) {
        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        ranking.populate(
                /* key= */ TEST_KEY,
                /* rank= */ 0,
                /* matchesInterruptionFilter= */ false,
                /* visibilityOverride= */ 0,
                /* suppressedVisualEffects= */ 0,
                /* importance= */ importance,
                /* explanation= */ null,
                TEST_NEW_OVERRIDE_GROUP_KEY,
                /* channel= */ null,
                /* overridePeople= */ new ArrayList<>(),
                /* snoozeCriteria= */ new ArrayList<>(),
                /* showBadge= */ false,
                /* userSentiment= */ 0,
                /* hidden= */ false,
                /* lastAudiblyAlertedMs= */ 0L,
                /* noisy= */ false,
                /* smartActions= */ new ArrayList<>(),
                /* smartReplies= */ new ArrayList<>(),
                /* canBubble= */ false,
                /* visuallyInterruptive= */ false,
                /* isConversation= */ false,
                /* shortcutInfo= */ null,
                /* rankingAdjustment= */ 0,
                /* isBubble= */ false,
                /* proposedImportance= */ 0,
                /* sensitiveContent= */ false
        );
        mRankingMap = new NotificationListenerService.RankingMap(
                new NotificationListenerService.Ranking[]{ranking});
    }
}
