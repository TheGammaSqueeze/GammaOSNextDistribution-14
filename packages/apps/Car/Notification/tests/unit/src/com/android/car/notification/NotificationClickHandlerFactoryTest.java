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

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationStats;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.assist.client.CarAssistUtils;
import com.android.car.notification.template.CarNotificationActionButton;
import com.android.car.notification.utils.MockMessageNotificationBuilder;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.NotificationVisibility;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NotificationClickHandlerFactoryTest {
    private static final String PKG_1 = "package_1";
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

    private Context mContext;
    private NotificationClickHandlerFactory mNotificationClickHandlerFactory;
    private AlertEntry mAlertEntry1;
    private AlertEntry mAlertEntry2;
    private AlertEntry mAlertEntryMessageHeadsUp;
    private AlertEntry mAlertEntryMessageWithMuteAction;
    private PendingIntent mReplyActionPendingIntent;
    private PendingIntent mMuteActionPendingIntent;

    @Mock
    private IStatusBarService mBarService;
    @Mock
    private NotificationClickHandlerFactory.OnNotificationClickListener mListener1;
    @Mock
    private NotificationClickHandlerFactory.OnNotificationClickListener mListener2;
    @Mock
    private View mView;
    @Mock
    private StatusBarNotification mStatusBarNotification1;
    @Mock
    private StatusBarNotification mStatusBarNotification2;
    @Mock
    private UserHandle mUser1;
    @Mock
    private UserHandle mUser2;
    @Mock
    private PendingIntent mActionIntent;
    @Mock
    private RemoteInput mRemoteInput;
    @Mock
    private CarAssistUtils mCarAssistUtils;
    @Mock
    private NotificationClickHandlerFactory.MuteStatusSetter mMuteStatusSetter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mNotificationClickHandlerFactory = new NotificationClickHandlerFactory(mBarService);
        Notification notification1 = new Notification();
        Notification notification2 = new Notification();
        notification1.contentIntent = PendingIntent.getForegroundService(
                mContext, /* requestCode= */ 0, new Intent(), FLAG_IMMUTABLE);
        notification2.contentIntent = PendingIntent.getForegroundService(
                mContext, /* requestCode= */ 0, new Intent(), FLAG_IMMUTABLE);
        when(mStatusBarNotification1.getNotification()).thenReturn(notification1);
        when(mStatusBarNotification2.getNotification()).thenReturn(notification2);
        when(mStatusBarNotification1.getKey()).thenReturn("TEST_KEY_1");
        when(mStatusBarNotification2.getKey()).thenReturn("TEST_KEY_2");
        when(mStatusBarNotification1.getUser()).thenReturn(mUser1);
        when(mStatusBarNotification2.getUser()).thenReturn(mUser2);
        mAlertEntry1 = new AlertEntry(mStatusBarNotification1);
        mAlertEntry2 = new AlertEntry(mStatusBarNotification2);

        MockMessageNotificationBuilder mockNotificationBuilder_messageHeadsUp =
                new MockMessageNotificationBuilder(mContext,
                        CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                        .setContentTitle(CONTENT_TITLE)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .setHasMessagingStyle(true)
                        .setHasReplyAction(true)
                        .setPendingIntentIsMocked(true)
                        .setHasMarkAsRead(true);
        MockMessageNotificationBuilder mockNotificationBuilder_messageHeadsUpWithMute =
                new MockMessageNotificationBuilder(mContext,
                        CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                        .setContentTitle(CONTENT_TITLE)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .setHasMessagingStyle(true)
                        .setHasReplyAction(true)
                        .setPendingIntentIsMocked(true)
                        .setHasMarkAsRead(true)
                        .setHasMuteAction(true);
        mAlertEntryMessageHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mockNotificationBuilder_messageHeadsUp.build(), USER_HANDLE,
                        OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryMessageWithMuteAction = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mockNotificationBuilder_messageHeadsUpWithMute
                                .build(), USER_HANDLE,
                        OVERRIDE_GROUP_KEY, POST_TIME));
        mReplyActionPendingIntent = mockNotificationBuilder_messageHeadsUp.getPendingIntent();
        mMuteActionPendingIntent =
                mockNotificationBuilder_messageHeadsUpWithMute.getPendingIntent();

        when(mView.getContext()).thenReturn(mContext);
        mNotificationClickHandlerFactory.setCarAssistUtils(mCarAssistUtils);
    }

    @After
    public void tearDown() {
        NotificationDataManager.refreshInstance();
    }

    @Test
    public void onClickClickHandler_noIntent_returnsImmediately() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        // overriding the non-null value assigned in setup.
        mAlertEntry1.getNotification().contentIntent = null;
        mAlertEntry1.getNotification().fullScreenIntent = null;

        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1, never()).onNotificationClicked(anyInt(), any(AlertEntry.class));
        try {
            verify(mBarService, never())
                    .onNotificationClick(anyString(), any(NotificationVisibility.class));
        } catch (RemoteException ex) {
            // ignore
        }
    }

    @Test
    public void onClickClickHandler_intentExists_callsBarServiceNotificationClicked() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);

        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        try {
            verify(mBarService)
                    .onNotificationClick(anyString(), any(NotificationVisibility.class));
        } catch (RemoteException ex) {
            // ignore
        }
    }

    @Test
    public void onClickClickHandler_intentExists_invokesRegisteredClickListeners() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);

        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(anyInt(), any(AlertEntry.class));
    }

    @Test
    public void onClickClickHandler_shouldAutoCancel_callsBarServiceOnNotificationClear() {
        mAlertEntry1.getStatusBarNotification().getNotification().flags =
                mStatusBarNotification1.getNotification().flags | Notification.FLAG_AUTO_CANCEL;
        UserHandle user = new UserHandle( /* handle= */ 0);
        when(mStatusBarNotification1.getUser()).thenReturn(user);
        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);
        NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                mAlertEntry1.getKey(), /* rank= */ -1, /* count= */ -1, /* visible= */ true);

        try {
            verify(mBarService).onNotificationClear(
                    mAlertEntry1.getStatusBarNotification().getPackageName(),
                    mAlertEntry1.getStatusBarNotification().getUser().getIdentifier(),
                    mAlertEntry1.getKey(),
                    NotificationStats.DISMISSAL_SHADE,
                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                    notificationVisibility);
        } catch (RemoteException ex) {
            // ignore
        }

    }

    @Test
    public void onClickActionClickHandler_isReplyAction_sendsPendingIntent() {
        // Have to prepare looper since Toast will be shown in test.
        Looper.prepare();

        mNotificationClickHandlerFactory.getActionClickHandler(
                mAlertEntryMessageHeadsUp, /* index= */
                0).onClick(mView);
        try {
            verify(mReplyActionPendingIntent).sendAndReturnResult(any(Context.class), eq(0),
                    any(Intent.class), isNull(), isNull(), isNull(), any(Bundle.class));
        } catch (PendingIntent.CanceledException e) {
            // ignore
        }
    }

    @Test
    public void onClickActionClickHandler_isReplyAction_doesNotInvokeRegisteredClickListeners() {
        // Have to prepare looper since Toast will be shown in test.
        Looper.prepare();

        mNotificationClickHandlerFactory.getActionClickHandler(
                mAlertEntryMessageHeadsUp, /* index= */
                0).onClick(mView);

        verify(mListener1, never()).onNotificationClicked(anyInt(), any(AlertEntry.class));
    }

    @Test
    public void onClickActionClickHandler_notCarCompatibleMessage_sendsPendingIntent() {
        PendingIntent pendingIntent = PendingIntent.getForegroundService(
                mContext, /* requestCode= */0,
                new Intent(), FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action
                .Builder(/* icon= */ null, "ACTION_TITLE", pendingIntent)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .addRemoteInput(mRemoteInput)
                .build();
        action.actionIntent = mActionIntent;
        Notification.Action[] actions = {action};
        mStatusBarNotification1.getNotification().actions = actions;

        mNotificationClickHandlerFactory.getActionClickHandler(mAlertEntry1, /* index= */
                0).onClick(mView);
        try {
            verify(mActionIntent).sendAndReturnResult(isNull(), eq(0), isNull(), isNull(),
                    isNull(), isNull(), any(Bundle.class));
        } catch (PendingIntent.CanceledException e) {
            // ignore
        }
    }

    @Test
    public void onClickActionClickHandler_notCarCompatibleMessage_invokesRegisteredListeners() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        PendingIntent pendingIntent = PendingIntent.getForegroundService(
                mContext, /* requestCode= */0,
                new Intent(), FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action
                .Builder(/* icon= */ null, "ACTION_TITLE", pendingIntent)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .addRemoteInput(mRemoteInput)
                .build();
        action.actionIntent = mActionIntent;
        Notification.Action[] actions = {action};
        mStatusBarNotification1.getNotification().actions = actions;

        mNotificationClickHandlerFactory.getActionClickHandler(mAlertEntry1, /* index= */
                0).onClick(mView);

        verify(mListener1).onNotificationClicked(anyInt(), any(AlertEntry.class));
    }

    @Test
    public void onClickPlayClickHandler_notCarCompatibleMessage_returnsImmediately() {
        mNotificationClickHandlerFactory.getPlayClickHandler(mAlertEntry1).onClick(mView);

        verify(mCarAssistUtils, never()).requestAssistantVoiceAction(any(), any(), any());
    }

    @Test
    public void onClickPlayClickHandler_isCarCompatibleMessage_requestsAssistantVoiceAction() {
        mNotificationClickHandlerFactory.getPlayClickHandler(mAlertEntryMessageHeadsUp).onClick(
                mView);

        verify(mCarAssistUtils).requestAssistantVoiceAction(any(), any(), any());
    }

    @Test
    public void onClickMuteClickHandler_togglesMute() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageHeadsUp);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);

        // first make sure it is not muted by default
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageHeadsUp)).isFalse();

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageHeadsUp, mMuteStatusSetter).onClick(mView);

        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageHeadsUp)).isTrue();

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageHeadsUp, mMuteStatusSetter).onClick(mView);

        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageHeadsUp)).isFalse();
    }

    @Test
    public void onClickMuteClickHandler_mutePendingIntent_notificationDataManagerUnchanged() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageWithMuteAction);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);

        // first make sure it is not muted by default
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageWithMuteAction)).isFalse();

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageWithMuteAction, mMuteStatusSetter).onClick(mView);

        // verify that notification data manager does not handle muting the notification
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageWithMuteAction)).isFalse();
    }

    @Test
    public void onClickMuteClickHandler_mutePendingIntent_dismissesNotification() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageWithMuteAction);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);
        NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                mAlertEntryMessageWithMuteAction.getKey(),
                /* rank= */ -1,
                /* count= */ -1,
                /* visible= */ true);

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageWithMuteAction, mMuteStatusSetter).onClick(mView);

        // verify notification is dismissed
        try {
            verify(mBarService).onNotificationClear(
                    mAlertEntryMessageWithMuteAction.getStatusBarNotification().getPackageName(),
                    mAlertEntryMessageWithMuteAction
                            .getStatusBarNotification().getUser().getIdentifier(),
                    mAlertEntryMessageWithMuteAction.getStatusBarNotification().getKey(),
                    NotificationStats.DISMISSAL_SHADE,
                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                    notificationVisibility);
        } catch (RemoteException e) {
            // ignore.
        }
    }

    @Test
    public void onClickMuteClickHandler_mutePendingIntent_firesPendingIntent() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageWithMuteAction);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);

        // first make sure it is not muted by default
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageWithMuteAction)).isFalse();

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageWithMuteAction, mMuteStatusSetter).onClick(mView);
        try {
            verify(mMuteActionPendingIntent).send();
        } catch (PendingIntent.CanceledException e) {
            // ignore
        }
    }

    @Test
    public void onClickMuteClickHandler_isMuted_muteStatusSetTrue() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageHeadsUp);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);
        // first make sure it is not muted by default
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageHeadsUp)).isFalse();

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageHeadsUp, mMuteStatusSetter).onClick(mView);

        verify(mMuteStatusSetter).setMuteStatus(button, /* isMuted= */ true);
    }

    @Test
    public void onClickMuteClickHandler_isUnmuted_muteStatusSetFalse() {
        NotificationDataManager notificationDataManager = NotificationDataManager.getInstance();
        notificationDataManager.addNewMessageNotification(mAlertEntryMessageHeadsUp);
        CarNotificationActionButton button = new CarNotificationActionButton(mContext);
        // first make sure it is not muted by default
        assertThat(notificationDataManager.isMessageNotificationMuted(
                mAlertEntryMessageHeadsUp)).isFalse();
        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageHeadsUp, mMuteStatusSetter).onClick(mView);
        verify(mMuteStatusSetter).setMuteStatus(button, /* isMuted= */ true);

        mNotificationClickHandlerFactory.getMuteClickHandler(button,
                mAlertEntryMessageHeadsUp, mMuteStatusSetter).onClick(mView);

        verify(mMuteStatusSetter).setMuteStatus(button, /* isMuted= */ false);
    }

    @Test
    public void onNotificationClicked_twoListenersRegistered_invokesTwoHandlerCallbacks() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_oneListenersUnregistered_invokesRegisteredCallback() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        mNotificationClickHandlerFactory.unregisterClickListener(mListener2);

        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2, never()).onNotificationClicked(ActivityManager.START_SUCCESS,
                mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_duplicateListenersRegistered_invokesCallbackOnce() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_twoAlertEntriesSubscribe_passesTheRightAlertEntry() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        View.OnClickListener clickListener1 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1);
        View.OnClickListener clickListener2 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry2);

        clickListener2.onClick(mView);

        verify(mListener1, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);
    }

    @Test
    public void onNotificationClicked_multipleListenersAndAes_passesTheRightAeToAllListeners() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        View.OnClickListener clickListener1 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1);
        View.OnClickListener clickListener2 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry2);

        clickListener2.onClick(mView);

        verify(mListener1, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);

        verify(mListener2, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);
    }

    @Test
    public void onClearNotifications_groupNotificationWithSummary_clearsSummary() {
        List<NotificationGroup> notificationsToClear = new ArrayList<>();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mAlertEntry1);
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationsToClear.add(notificationGroup);
        NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                mAlertEntry1.getKey(),
                /* rank= */ -1,
                /* count= */ -1,
                /* visible= */ true);

        mNotificationClickHandlerFactory.clearNotifications(notificationsToClear);

        try {
            verify(mBarService).onNotificationClear(
                    mAlertEntry1.getStatusBarNotification().getPackageName(),
                    mAlertEntry1.getStatusBarNotification().getUser().getIdentifier(),
                    mAlertEntry1.getStatusBarNotification().getKey(),
                    NotificationStats.DISMISSAL_SHADE,
                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                    notificationVisibility);
        } catch (RemoteException e) {
            // ignore.
        }
    }

    @Test
    public void onClearNotifications_groupNotificationWithSummary_clearsAllChildren() {
        List<NotificationGroup> notificationsToClear = new ArrayList<>();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mAlertEntry1);
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationsToClear.add(notificationGroup);
        NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                mAlertEntry2.getKey(),
                /* rank= */ -1,
                /* count= */ -1,
                /* visible= */ true);

        mNotificationClickHandlerFactory.clearNotifications(notificationsToClear);

        try {
            verify(mBarService, times(3)).onNotificationClear(
                    mAlertEntry2.getStatusBarNotification().getPackageName(),
                    mAlertEntry2.getStatusBarNotification().getUser().getIdentifier(),
                    mAlertEntry2.getStatusBarNotification().getKey(),
                    NotificationStats.DISMISSAL_SHADE,
                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                    notificationVisibility);
        } catch (RemoteException e) {
            // ignore.
        }
    }

    @Test
    public void onClearNotifications_groupNotificationWithoutSummary_clearsAllChildren() {
        List<NotificationGroup> notificationsToClear = new ArrayList<>();
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationGroup.addNotification(mAlertEntry2);
        notificationsToClear.add(notificationGroup);
        NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                mAlertEntry2.getKey(),
                /* rank= */ -1,
                /* count= */ -1,
                /* visible= */ true);

        mNotificationClickHandlerFactory.clearNotifications(notificationsToClear);

        try {
            verify(mBarService, times(3)).onNotificationClear(
                    mAlertEntry2.getStatusBarNotification().getPackageName(),
                    mAlertEntry2.getStatusBarNotification().getUser().getIdentifier(),
                    mAlertEntry2.getStatusBarNotification().getKey(),
                    NotificationStats.DISMISSAL_SHADE,
                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                    notificationVisibility);
        } catch (RemoteException e) {
            // ignore.
        }
    }
}
