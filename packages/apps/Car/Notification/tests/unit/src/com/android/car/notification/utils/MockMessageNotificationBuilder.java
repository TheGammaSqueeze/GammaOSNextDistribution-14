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

package com.android.car.notification.utils;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

import static androidx.core.app.NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ;
import static androidx.core.app.NotificationCompat.Action.SEMANTIC_ACTION_MUTE;
import static androidx.core.app.NotificationCompat.Action.SEMANTIC_ACTION_REPLY;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;

import java.time.Instant;

/**
 * Builds {@link Notification}s for testing.
 */
public class MockMessageNotificationBuilder {
    private static final String SENDER_NAME = "senderName";
    private static final String USER_NAME = "userName";
    private static final String TITLE_MARK_AS_READ = "Mark As Read";
    private static final String TITLE_REPLY = "Reply";
    private static final String TITLE_MUTE= "Mute";
    private static final String CONTENT_TITLE_PREFIX = "2 new messages from ";
    private static final String CONTENT_SUBJECT = "subject";
    private static final String OLD_MESSAGE = "old message";
    private static final String FIRST_MESSAGE = "first message";
    private static final String SECOND_MESSAGE = "second message";

    private final Context mContext;
    private final Icon mActionIcon;
    private final Person mSender;
    private final Person mUser;
    private final String mChannelId;
    private final int mSmallIconId;
    @Nullable
    private Message mPostSpecificMessage = null;
    @Nullable
    private Bundle mExtras = null;
    private boolean mHasMessagingStyle = false;
    private boolean mIsOldMessage = false;
    private boolean mHasReplyAction = false;
    private boolean mReplyActionIsMocked = false;
    private boolean mMuteActionIsMocked = false;
    private boolean mMarkAsReadActionIsMocked = false;
    private boolean mPendingIntentIsMocked = false;
    private boolean mHasReplyWrongSemanticAction = false;
    private boolean mHasMarkAsRead = false;
    private boolean mHasMute = false;
    private boolean mUseInvisibleAction = false;
    private boolean mShowsUI = false;
    private Instant mConnectionTime = Instant.now();
    private String mContentTitle = "";
    private String mCategory = "";
    private String mContentText = "";
    private Action mReplyAction = null;
    private Action mMuteAction = null;
    private Action mMarkAsReadAction = null;
    private PendingIntent mPendingIntent = null;

    public MockMessageNotificationBuilder(Context context, String channelId, int smallIconId) {
        mContext = context;
        mChannelId = channelId;
        mSmallIconId = smallIconId;
        mActionIcon = Icon.createWithResource(mContext, /* resId= */ 1);
        mSender = (new Person.Builder()).setName(SENDER_NAME).build();
        mUser = (new Person.Builder()).setName(USER_NAME).build();
    }

    public Notification build() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.mContext,
                mChannelId).setSmallIcon(mSmallIconId).setContentText(
                CONTENT_SUBJECT);

        if (mExtras != null) {
            builder.setExtras(mExtras);
        }

        if (!mContentTitle.isEmpty()) {
            builder.setContentTitle(mContentTitle);
        }

        if (!mContentText.isEmpty()) {
            builder.setContentText(mContentText);
        }

        if (!mCategory.isEmpty()) {
            builder.setCategory(mCategory);
        }

        if (mHasMessagingStyle) {
            builder.setStyle(buildStyle());
        }

        if (mPendingIntentIsMocked) {
            mPendingIntent = mock(PendingIntent.class);
            when(mPendingIntent.describeContents()).thenReturn(0);
        } else {
            mPendingIntent = PendingIntent.getService(mContext, /* requestCode= */ 101,
                    new Intent(mContext, MockService.class), FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
        }

        if (mHasReplyAction) {
            if (mReplyActionIsMocked) {
                mReplyAction = getMockReplyAction(
                        mHasReplyWrongSemanticAction ? Action.SEMANTIC_ACTION_NONE
                                : SEMANTIC_ACTION_REPLY);
            } else {
                mReplyAction = getNonMockReplyAction(
                        mHasReplyWrongSemanticAction ? Action.SEMANTIC_ACTION_NONE
                                : SEMANTIC_ACTION_REPLY);
            }

            if (mUseInvisibleAction) {
                builder.addInvisibleAction(mReplyAction);
            } else {
                builder.addAction(mReplyAction);
            }
        }

        if (mHasMarkAsRead) {
            if (mMarkAsReadActionIsMocked) {
                mMarkAsReadAction = getMockMarkAsReadAction();
            } else {
                mMarkAsReadAction = getNonMockMarkAsReadAction();
            }

            if (mUseInvisibleAction) {
                builder.addInvisibleAction(mMarkAsReadAction);
            } else {
                builder.addAction(mMarkAsReadAction);
            }
        }

        if (mHasMute) {
            if (mMuteActionIsMocked) {
                mMuteAction = getMockMuteAction();
            } else {
                mMuteAction = getNonMockMuteAction();
            }

            if (mUseInvisibleAction) {
                builder.addInvisibleAction(mMuteAction);
            } else {
                builder.addAction(mMuteAction);
            }
        }

        return builder.build();
    }

    public MockMessageNotificationBuilder setHasMessagingStyle(boolean hasMessagingStyle) {
        mHasMessagingStyle = hasMessagingStyle;
        return this;
    }

    public MockMessageNotificationBuilder setOldMessage(boolean oldMessage) {
        mIsOldMessage = oldMessage;
        return this;
    }

    public MockMessageNotificationBuilder setHasReplyAction(boolean hasReplyAction) {
        mHasReplyAction = hasReplyAction;
        return this;
    }

    public MockMessageNotificationBuilder setReplyActionIsMocked(boolean replyActionIsMocked) {
        mReplyActionIsMocked = replyActionIsMocked;
        return this;
    }

    public MockMessageNotificationBuilder setMarkAsReadActionIsMocked(
            boolean markAsReadActionIsMocked) {
        mMarkAsReadActionIsMocked = markAsReadActionIsMocked;
        return this;
    }

    public MockMessageNotificationBuilder setMuteActionIsMocked(
            boolean muteActionIsMocked) {
        mMuteActionIsMocked = muteActionIsMocked;
        return this;
    }

    public MockMessageNotificationBuilder setPendingIntentIsMocked(boolean pendingIntentIsMocked) {
        mPendingIntentIsMocked = pendingIntentIsMocked;
        return this;
    }

    public MockMessageNotificationBuilder setHasReplyWrongSemanticAction(
            boolean hasReplyWrongSemanticAction) {
        mHasReplyWrongSemanticAction = hasReplyWrongSemanticAction;
        return this;
    }

    public MockMessageNotificationBuilder setHasMarkAsRead(boolean hasMarkAsRead) {
        mHasMarkAsRead = hasMarkAsRead;
        return this;
    }

    public MockMessageNotificationBuilder setHasMuteAction(boolean hasMute) {
        mHasMute = hasMute;
        return this;
    }

    public MockMessageNotificationBuilder setUseInvisibleAction(boolean useInvisibleAction) {
        mUseInvisibleAction = useInvisibleAction;
        return this;
    }

    public MockMessageNotificationBuilder setShowsUI(boolean showsUI) {
        mShowsUI = showsUI;
        return this;
    }

    public MockMessageNotificationBuilder setConnectionTime(Instant connectionTime) {
        mConnectionTime = connectionTime;
        return this;
    }

    public MockMessageNotificationBuilder setPostSpecificMessage(
            MessagingStyle.Message postSpecificMessage) {
        mPostSpecificMessage = postSpecificMessage;
        return this;
    }

    public MockMessageNotificationBuilder setContentTitle(String contentTitle) {
        mContentTitle = contentTitle;
        return this;
    }

    public MockMessageNotificationBuilder setCategory(String category) {
        mCategory = category;
        return this;
    }

    public MockMessageNotificationBuilder setContentText(String contentText) {
        mContentText = contentText;
        return this;
    }

    public MockMessageNotificationBuilder setExtras(@Nullable Bundle extras) {
        mExtras = extras;
        return this;
    }

    public Action getReplyAction() {
        return mReplyAction;
    }

    public Action getMuteAction() {
        return mMuteAction;
    }

    public Action getMarkAsReadAction() {
        return mMarkAsReadAction;
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    private MessagingStyle buildStyle() {
        MessagingStyle builder = new MessagingStyle(mUser);
        long connectionTimeMs = mConnectionTime.toEpochMilli();
        if (mPostSpecificMessage != null) {
            return builder.addMessage(mPostSpecificMessage);
        } else if (mIsOldMessage) {
            return builder.addMessage(OLD_MESSAGE, /* timestamp= */ connectionTimeMs - 100,
                    mSender);
        } else {
            return builder.addMessage(FIRST_MESSAGE, /* timestamp= */ connectionTimeMs + 100,
                    mSender).addMessage(SECOND_MESSAGE, /* timestamp= */ connectionTimeMs + 100,
                    mSender);
        }
    }

    private Action getNonMockReplyAction(int semanticAction) {
        return (new Action.Builder(IconCompat.createFromIcon(mActionIcon), TITLE_REPLY,
                mPendingIntent)).addRemoteInput(
                new RemoteInput.Builder(mChannelId).build()).setSemanticAction(
                semanticAction).setShowsUserInterface(mShowsUI).build();
    }

    private Action getMockReplyAction(int semanticAction) {
        IconCompat iconCompat = IconCompat.createFromIcon(mActionIcon);
        Action action = mock(Action.class);
        when(action.actionIntent).thenReturn(mPendingIntent);
        when(action.title).thenReturn(TITLE_REPLY);
        when(action.icon).thenReturn(iconCompat.getResId());
        when(action.getActionIntent()).thenReturn(mPendingIntent);
        when(action.getAllowGeneratedReplies()).thenReturn(true);
        when(action.getDataOnlyRemoteInputs()).thenReturn(null);
        when(action.getExtras()).thenReturn(new Bundle());
        when(action.getRemoteInputs()).thenReturn(new RemoteInput[]{new RemoteInput.Builder(
                mChannelId).build()});
        when(action.getSemanticAction()).thenReturn(semanticAction);
        when(action.getShowsUserInterface()).thenReturn(mShowsUI);
        when(action.getTitle()).thenReturn(TITLE_REPLY);
        when(action.isContextual()).thenReturn(false);
        when(action.getIconCompat()).thenReturn(iconCompat);
        return action;
    }

    private Action getNonMockMarkAsReadAction() {
        return (new Action.Builder(IconCompat.createFromIcon(mActionIcon),
                TITLE_MARK_AS_READ, mPendingIntent)).addRemoteInput(
                new RemoteInput.Builder(mChannelId).build()).setSemanticAction(
                SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build();
    }

    private Action getMockMarkAsReadAction() {
        IconCompat iconCompat = IconCompat.createFromIcon(mActionIcon);
        Action action = mock(Action.class);
        when(action.actionIntent).thenReturn(mPendingIntent);
        when(action.title).thenReturn(TITLE_MARK_AS_READ);
        when(action.icon).thenReturn(iconCompat.getResId());
        when(action.getActionIntent()).thenReturn(mPendingIntent);
        when(action.getAllowGeneratedReplies()).thenReturn(true);
        when(action.getDataOnlyRemoteInputs()).thenReturn(null);
        when(action.getExtras()).thenReturn(new Bundle());
        when(action.getRemoteInputs()).thenReturn(new RemoteInput[]{new RemoteInput.Builder(
                mChannelId).build()});
        when(action.getSemanticAction()).thenReturn(SEMANTIC_ACTION_MARK_AS_READ);
        when(action.getShowsUserInterface()).thenReturn(false);
        when(action.getTitle()).thenReturn(TITLE_MARK_AS_READ);
        when(action.isContextual()).thenReturn(false);
        when(action.getIconCompat()).thenReturn(iconCompat);
        return action;
    }

    private Action getNonMockMuteAction() {
        return (new Action.Builder(IconCompat.createFromIcon(mActionIcon),
                TITLE_MUTE, mPendingIntent)).addRemoteInput(
                new RemoteInput.Builder(mChannelId).build()).setSemanticAction(
                SEMANTIC_ACTION_MUTE).setShowsUserInterface(false).build();
    }

    private Action getMockMuteAction() {
        IconCompat iconCompat = IconCompat.createFromIcon(mActionIcon);
        Action action = mock(Action.class);
        when(action.actionIntent).thenReturn(mPendingIntent);
        when(action.title).thenReturn(TITLE_MUTE);
        when(action.icon).thenReturn(iconCompat.getResId());
        when(action.getActionIntent()).thenReturn(mPendingIntent);
        when(action.getAllowGeneratedReplies()).thenReturn(true);
        when(action.getDataOnlyRemoteInputs()).thenReturn(null);
        when(action.getExtras()).thenReturn(new Bundle());
        when(action.getRemoteInputs()).thenReturn(new RemoteInput[]{new RemoteInput.Builder(
                mChannelId).build()});
        when(action.getSemanticAction()).thenReturn(SEMANTIC_ACTION_MUTE);
        when(action.getShowsUserInterface()).thenReturn(false);
        when(action.getTitle()).thenReturn(TITLE_MUTE);
        when(action.isContextual()).thenReturn(false);
        when(action.getIconCompat()).thenReturn(iconCompat);
        return action;
    }

    private static class MockService extends Service {
        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}