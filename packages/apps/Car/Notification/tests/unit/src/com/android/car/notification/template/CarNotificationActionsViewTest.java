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

package com.android.car.notification.template;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.assist.client.CarAssistUtils;
import com.android.car.notification.AlertEntry;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationDataManager;
import com.android.car.notification.R;
import com.android.car.notification.utils.MockMessageNotificationBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CarNotificationActionsViewTest {
    private static final String PKG = "package";
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
    private static final String TEST_KEY = "TEST_KEY";
    private static final String ACTION_TITLE = "ACTION_TITLE";
    private static final View.OnClickListener CLICK_LISTENER = (v) -> {
    };

    private CarNotificationActionsView mCarNotificationActionsView;
    private Notification.Action mAction;
    private Context mContext;
    private AlertEntry mMessageCategoryAlertEntry;
    private Icon mIcon;
    @Mock
    private StatusBarNotification mStatusBarNotification;
    @Mock
    private NotificationClickHandlerFactory mNotificationClickHandlerFactory;
    @Mock
    private NotificationDataManager mNotificationDataManager;
    @Mock
    private CarAssistUtils mCarAssistUtils;
    @Mock
    private Context mMockContext;
    @Mock
    private Handler mHandler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
        mIcon = spy(Icon.createWithResource(mContext, R.drawable.icon));
        doNothing().when(mIcon).loadDrawableAsync(any(Context.class),
                nullable(Icon.OnDrawableLoadedListener.class), nullable(Handler.class));
        initAction(/* icon= */ null);

        MockMessageNotificationBuilder messageNotificationBuilder =
                new MockMessageNotificationBuilder(mContext,
                        CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                        .setContentTitle(CONTENT_TITLE)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .setHasMessagingStyle(true)
                        .setHasReplyAction(true)
                        .setHasMuteAction(true)
                        .setHasMarkAsRead(true);
        mMessageCategoryAlertEntry = new AlertEntry(
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        messageNotificationBuilder.build(), USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        when(mStatusBarNotification.getKey()).thenReturn(TEST_KEY);

        when(mNotificationClickHandlerFactory
                .getPlayClickHandler(any(AlertEntry.class)))
                .thenReturn(CLICK_LISTENER);
        when(mNotificationClickHandlerFactory
                .getReplyClickHandler(any(AlertEntry.class)))
                .thenReturn(CLICK_LISTENER);
        when(mNotificationClickHandlerFactory
                .getMuteClickHandler(any(CarNotificationActionButton.class), any(AlertEntry.class),
                        any(NotificationClickHandlerFactory.MuteStatusSetter.class)))
                .thenReturn(CLICK_LISTENER);
        when(mNotificationClickHandlerFactory
                .getActionClickHandler(any(AlertEntry.class), anyInt()))
                .thenReturn(CLICK_LISTENER);
        when(mNotificationClickHandlerFactory.getReplyAction(any(Notification.class)))
                .thenReturn(messageNotificationBuilder.getReplyAction());

        when(mCarAssistUtils.hasActiveAssistant()).thenReturn(true);
        when(mCarAssistUtils.isFallbackAssistantEnabled()).thenReturn(false);
    }

    @Test
    public void onFinishInflate_addsMaxNumberOfActionButtons() {
        finishInflateWithIsCall(/* isCall= */ false);

        assertThat(mCarNotificationActionsView.getActionButtons().size())
                .isEqualTo(CarNotificationActionsView.MAX_NUM_ACTIONS);
    }

    @Test
    public void onBind_noAction_doesNotCreateButtons() {
        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));

        for (int i = 0; i < mCarNotificationActionsView.getActionButtons().size(); i++) {
            CarNotificationActionButton button =
                    mCarNotificationActionsView.getActionButtons().get(i);
            assertThat(button.getVisibility()).isNotEqualTo(View.VISIBLE);
            assertThat(button.hasOnClickListeners()).isFalse();
        }
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_playButtonIsVisible() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_playButtonHasClickListener() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.hasOnClickListeners()).isTrue();
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_playButtonShowsPlayLabel() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.getText())
                .isEqualTo(mContext.getString(R.string.assist_action_play_label));
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_playButtonShowsPlayDrawable() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.getDrawable())
                .isEqualTo(mCarNotificationActionsView.mPlayButtonDrawable);
    }

    @Test
    public void onBind_carCompatibleMessage_noAssistantNoFallback_playButtonIsHidden() {
        when(mCarAssistUtils.hasActiveAssistant()).thenReturn(false);
        when(mCarAssistUtils.isFallbackAssistantEnabled()).thenReturn(false);

        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(
                mNotificationClickHandlerFactory, mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.getVisibility()).isNotEqualTo(View.VISIBLE);
    }

    @Test
    public void onBind_carCompatibleMessage_noAssistantWithFallback_playButtonIsVisible() {
        when(mCarAssistUtils.hasActiveAssistant()).thenReturn(false);
        when(mCarAssistUtils.isFallbackAssistantEnabled()).thenReturn(true);

        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(
                mNotificationClickHandlerFactory, mMessageCategoryAlertEntry);
        CarNotificationActionButton playButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.FIRST_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(playButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(playButton.getText())
                .isEqualTo(mContext.getString(R.string.assist_action_play_label));
        assertThat(playButton.hasOnClickListeners()).isTrue();
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_replyButtonIsVisible() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton replyButton = mCarNotificationActionsView.getActionButtons()
                .get(CarNotificationActionsView.SECOND_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(replyButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_replyButtonHasClickListener() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton replyButton = mCarNotificationActionsView.getActionButtons()
                .get(CarNotificationActionsView.SECOND_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(replyButton.hasOnClickListeners()).isTrue();
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_replyButtonShowsReplyLabel() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton replyButton = mCarNotificationActionsView.getActionButtons()
                .get(CarNotificationActionsView.SECOND_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(replyButton.getText())
                .isEqualTo(mContext.getString(R.string.assist_action_reply_label));
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_replyButtonShowsReplyDrawable() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton replyButton = mCarNotificationActionsView.getActionButtons()
                .get(CarNotificationActionsView.SECOND_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(replyButton.getDrawable())
                .isEqualTo(mCarNotificationActionsView.mReplyButtonDrawable);
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_muteButtonIsVisible() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton muteButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.THIRD_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(muteButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_muteButtonHasClickListener() {
        finishInflateWithIsCall(/* isCall= */ false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton muteButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.THIRD_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(muteButton.hasOnClickListeners()).isTrue();
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_muted_muteButtonShowsUnmuteLabel() {
        finishInflateWithIsCall(/* isCall= */ false);
        messageIsMuted(true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton muteButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.THIRD_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(muteButton.getText())
                .isEqualTo(mContext.getString(R.string.action_unmute_short));
    }

    @Test
    public void onBind_actionExists_isCarCompatibleMessage_unmuted_muteButtonShowsMuteLabel() {
        finishInflateWithIsCall(/* isCall= */ false);
        messageIsMuted(false);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                mMessageCategoryAlertEntry);
        CarNotificationActionButton muteButton = mCarNotificationActionsView.getActionButtons().get(
                CarNotificationActionsView.THIRD_MESSAGE_ACTION_BUTTON_INDEX);

        assertThat(muteButton.getText())
                .isEqualTo(mContext.getString(R.string.action_mute_short));
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_buttonIsVisible() {
        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton button = mCarNotificationActionsView.getActionButtons().get(0);

        assertThat(button.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_buttonShowsActionTitle() {
        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton button = mCarNotificationActionsView.getActionButtons().get(0);

        assertThat(button.getText()).isEqualTo(ACTION_TITLE);
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_hasIntent_buttonHasClickListener() {
        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton button = mCarNotificationActionsView.getActionButtons().get(0);

        assertThat(button.hasOnClickListeners()).isTrue();
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_hasNoIntent_buttonHasNoClickListener() {
        // Override mAction with an Action without a pending intent.
        mAction = new Notification.Action
                .Builder(/* icon= */ null, ACTION_TITLE, /* pendingIntent= */ null)
                .build();

        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton button = mCarNotificationActionsView.getActionButtons().get(0);

        assertThat(button.hasOnClickListeners()).isFalse();
    }

    @Test
    public void onBind_actionCountExceedsMaximum_notCarCompatibleMessage_doesNotThrowError() {
        finishInflateWithIsCall(/* isCall= */ false);

        int numberOverMaximum = CarNotificationActionsView.MAX_NUM_ACTIONS + 10;
        Notification.Action[] actions = new Notification.Action[numberOverMaximum];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = mAction;
        }
        Notification notification = new Notification();
        notification.actions = actions;
        when(mStatusBarNotification.getNotification()).thenReturn(notification);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_isCall_firstButtonHasBackground() {
        finishInflateWithIsCall(/* isCall= */ true);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton firstButton =
                mCarNotificationActionsView.getActionButtons().get(0);

        assertThat(firstButton.getBackground()).isNotNull();
    }

    @Test
    public void onBind_actionExists_notCarCompatibleMessage_isCall_secondButtonHasBackground() {
        finishInflateWithIsCall(/* isCall= */ true);
        statusBarNotificationHasActions(/* hasActions= */ true);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));
        CarNotificationActionButton secondButton =
                mCarNotificationActionsView.getActionButtons().get(1);

        assertThat(secondButton.getBackground()).isNotNull();
    }

    @Test
    public void onBind_actionWithCustomIconExists_iconLoadedUsingSenderPackage() {
        initAction(mIcon);
        finishInflateWithIsCall(/* isCall= */ false);
        statusBarNotificationHasActions(/* hasActions= */ true);
        when(mStatusBarNotification.getPackageContext(nullable(Context.class))).thenReturn(
                mMockContext);

        mCarNotificationActionsView.bind(mNotificationClickHandlerFactory,
                new AlertEntry(mStatusBarNotification));

        verify(mIcon).loadDrawableAsync(eq(mMockContext),
                nullable(Icon.OnDrawableLoadedListener.class), nullable(Handler.class));
    }

    private void finishInflateWithIsCall(boolean isCall) {
        mCarNotificationActionsView = new CarNotificationActionsView(
                mContext,
                /* attributeSet= */ null,
                /* defStyleAttr= */ 0,
                /* defStyleRes= */ 0,
                mCarAssistUtils) {
            @Override
            Handler getAsyncHandler() {
                return mHandler;
            }
        };
        mCarNotificationActionsView.setCategoryIsCall(isCall);
        mCarNotificationActionsView.onFinishInflate();
        mCarNotificationActionsView.setNotificationDataManager(mNotificationDataManager);
    }

    private void initAction(Icon icon) {
        PendingIntent pendingIntent = PendingIntent.getForegroundService(
                mContext, /* requestCode= */ 0, new Intent(), FLAG_IMMUTABLE);
        mAction = new Notification.Action
                .Builder(icon, ACTION_TITLE, pendingIntent).build();
    }

    private void statusBarNotificationHasActions(boolean hasActions) {
        Notification notification = new Notification();
        Notification.Action[] actions = {mAction};
        notification.actions = hasActions ? actions : null;
        when(mStatusBarNotification.getNotification()).thenReturn(notification);
    }

    private void messageIsMuted(boolean isMuted) {
        when(mNotificationDataManager.isMessageNotificationMuted(any(AlertEntry.class)))
                .thenReturn(isMuted);
    }
}
