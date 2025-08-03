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
package com.android.car.notification.template;

import static android.app.Notification.EXTRA_IS_GROUP_CONVERSATION;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.Person;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.app.NotificationCompat.MessagingStyle;

import com.android.car.notification.AlertEntry;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.PreprocessingManager;
import com.android.car.notification.R;

import java.util.List;

/**
 * Messaging notification template that displays a messaging notification and a voice reply button.
 */
public class MessageNotificationViewHolder extends CarNotificationBaseViewHolder {
    private static final String TAG = "MessageNotificationViewHolder";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String SENDER_TITLE_SEPARATOR = " • ";
    private static final String SENDER_BODY_SEPARATOR = ": ";
    private static final String NEW_LINE = "\n";

    private final CarNotificationBodyView mBodyView;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final PreprocessingManager mPreprocessingManager;
    private final String mNewMessageText;
    private final String mSeeMoreText;
    private final String mEllipsizedSuffix;
    private final int mMaxMessageCount;
    private final int mMaxLineCount;
    private final int mAdditionalCharCountAfterExpansion;
    private final Drawable mGroupIcon;
    private final boolean mUseCustomColorForMessageNotificationCountTextButton;
    private final float mDisabledCountTextButtonAlpha;
    @ColorInt
    private final int mCountTextColor;

    private final NotificationClickHandlerFactory mClickHandlerFactory;

    public MessageNotificationViewHolder(
            View view, NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mHeaderView = view.findViewById(R.id.notification_header);
        mActionsView = view.findViewById(R.id.notification_actions);
        mBodyView = view.findViewById(R.id.notification_body);

        mNewMessageText = getContext().getString(R.string.restricted_hun_message_content);
        mSeeMoreText = getContext().getString(R.string.see_more_message);
        mEllipsizedSuffix = getContext().getString(R.string.ellipsized_string);
        mMaxMessageCount =
                getContext().getResources().getInteger(R.integer.config_maxNumberOfMessagesInPanel);
        mMaxLineCount = getContext().getResources().getInteger(
                R.integer.config_maxNumberOfMessageLinesInPanel);
        mAdditionalCharCountAfterExpansion = getContext().getResources().getInteger(
                R.integer.config_additionalCharactersToShowInSingleMessageExpandedNotification);
        mGroupIcon = getContext().getDrawable(R.drawable.ic_group);
        mUseCustomColorForMessageNotificationCountTextButton =
                getContext().getResources().getBoolean(
                        R.bool.config_useCustomColorForMessageNotificationCountTextButton);
        mCountTextColor = getContext().getResources().getColor(R.color.count_text);
        mDisabledCountTextButtonAlpha = getContext().getResources().getFloat(
                R.dimen.config_disabledCountTextButtonAlpha);

        mClickHandlerFactory = clickHandlerFactory;
        mPreprocessingManager = PreprocessingManager.getInstance(getContext());
    }

    /**
     * Binds a {@link AlertEntry} to a messaging car notification template without
     * UX restriction.
     */
    @Override
    public void bind(AlertEntry alertEntry, boolean isInGroup,
            boolean isHeadsUp, boolean isSeen) {
        super.bind(alertEntry, isInGroup, isHeadsUp, isSeen);
        bindBody(alertEntry, isInGroup, /* isRestricted= */ false, isHeadsUp);
        mHeaderView.bind(alertEntry, isInGroup);
        mActionsView.bind(mClickHandlerFactory, alertEntry);
    }

    /**
     * Binds a {@link AlertEntry} to a messaging car notification template with
     * UX restriction.
     */
    public void bindRestricted(AlertEntry alertEntry, boolean isInGroup, boolean isHeadsUp,
            boolean isSeen) {
        super.bind(alertEntry, isInGroup, isHeadsUp, isSeen);
        bindBody(alertEntry, isInGroup, /* isRestricted= */ true, isHeadsUp);
        mHeaderView.bind(alertEntry, isInGroup);

        mActionsView.bind(mClickHandlerFactory, alertEntry);
    }

    /**
     * Private method that binds the data to the view.
     */
    private void bindBody(AlertEntry alertEntry, boolean isInGroup, boolean isRestricted,
            boolean isHeadsUp) {
        if (DEBUG) {
            if (isInGroup) {
                Log.d(TAG, "Is part of notification group: " + alertEntry);
            } else {
                Log.d(TAG, "Is not part of notification group: " + alertEntry);
            }
            if (isRestricted) {
                Log.d(TAG, "Has driver restrictions: " + alertEntry);
            } else {
                Log.d(TAG, "Doesn't have driver restrictions: " + alertEntry);
            }
            if (isHeadsUp) {
                Log.d(TAG, "Is a heads-up notification: " + alertEntry);
            } else {
                Log.d(TAG, "Is not a heads-up notification: " + alertEntry);
            }
        }

        if (mUseCustomColorForMessageNotificationCountTextButton) {
            mBodyView.setCountTextColor(mCountTextColor);
        } else {
            mBodyView.setCountTextColor(getAccentColor());
        }

        mBodyView.setCountTextAlpha(isRestricted ? mDisabledCountTextButtonAlpha : /* alpha= */ 1);

        Notification notification = alertEntry.getNotification();
        StatusBarNotification sbn = alertEntry.getStatusBarNotification();
        Bundle extras = notification.extras;
        CharSequence messageText;
        CharSequence conversationTitle;
        Icon avatar = null;
        Integer messageCount;
        CharSequence senderName = null;
        Notification.MessagingStyle.Message latestMessage = null;

        MessagingStyle messagingStyle =
                MessagingStyle.extractMessagingStyleFromNotification(notification);

        boolean isGroupConversation =
                ((messagingStyle != null && messagingStyle.isGroupConversation())
                        || extras.getBoolean(EXTRA_IS_GROUP_CONVERSATION));
        if (DEBUG) {
            if (isGroupConversation) {
                Log.d(TAG, "Is a group conversation: " + alertEntry);
            } else {
                Log.d(TAG, "Is not a group conversation: " + alertEntry);
            }
        }

        boolean messageStyleFlag = false;
        List<Notification.MessagingStyle.Message> messages = null;
        Parcelable[] messagesData = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messagesData != null) {
            messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messagesData);
            if (messages != null && !messages.isEmpty()) {
                if (DEBUG) {
                    Log.d(TAG, "App did use messaging style: " + alertEntry);
                }
                messageStyleFlag = true;

                // Use the latest message
                latestMessage = messages.get(messages.size() - 1);
                Person sender = latestMessage.getSenderPerson();
                if (sender != null) {
                    avatar = sender.getIcon();
                }
                senderName = (sender != null ? sender.getName() : latestMessage.getSender());
            } else {
                // App did not use messaging style; fall back to standard fields
                if (DEBUG) {
                    Log.d(TAG, "App did not use messaging style; fall back to standard "
                            + "fields: " + alertEntry);
                }
            }
        }


        messageCount = getMessageCount(messages, notification.number);
        messageText = getMessageText(latestMessage, isRestricted, isHeadsUp, isGroupConversation,
                senderName, messageCount, extras);
        conversationTitle = getConversationTitle(messagingStyle, isHeadsUp, isGroupConversation,
                senderName, extras);

        if (avatar == null) {
            avatar = notification.getLargeIcon();
        }

        Long when;
        if (notification.showsTime()) {
            when = notification.when;
        } else {
            when = null;
        }

        Drawable groupIcon;
        if (isGroupConversation) {
            groupIcon = mGroupIcon;
        } else {
            groupIcon = null;
        }

        int unshownCount = messageCount - 1;
        String unshownCountText = null;
        if (!isRestricted && !isHeadsUp && messageStyleFlag) {
            if (unshownCount > 0) {
                unshownCountText = getContext().getResources().getQuantityString(
                        R.plurals.restricted_numbered_message_content, unshownCount, unshownCount);
            } else if (messageText.toString().endsWith(mEllipsizedSuffix)) {
                unshownCountText = mSeeMoreText;
            }

            View.OnClickListener listener =
                    getCountViewOnClickListener(unshownCount, messages, isGroupConversation,
                            sbn, conversationTitle, avatar, groupIcon, when);
            mBodyView.setCountOnClickListener(listener);
        }
        mBodyView.bind(conversationTitle, messageText,
                sbn, avatar, groupIcon, unshownCountText, when);
    }

    private CharSequence getMessageText(Notification.MessagingStyle.Message message,
            boolean isRestricted, boolean isHeadsUp, boolean isGroupConversation,
            CharSequence senderName, int messageCount, Bundle extras) {
        CharSequence messageText = null;

        if (message != null) {
            if (DEBUG) {
                Log.d(TAG, "Message style message text used.");
            }

            messageText = message.getText();

            if (!isHeadsUp && isGroupConversation) {
                // If conversation is a group conversation and notification is not a HUN,
                // then prepend sender's name to title.
                messageText = senderName + SENDER_BODY_SEPARATOR + messageText;
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "Standard field message text used.");
            }

            messageText = extras.getCharSequence(Notification.EXTRA_TEXT);
        }

        if (isRestricted) {
            if (isHeadsUp || messageCount == 1) {
                messageText = mNewMessageText;
            } else {
                messageText = getContext().getResources().getQuantityString(
                        R.plurals.restricted_numbered_message_content, messageCount, messageCount);
            }
        }

        if (!TextUtils.isEmpty(messageText)) {
            messageText = mPreprocessingManager.trimText(messageText);
        }

        return messageText;
    }

    private CharSequence getConversationTitle(MessagingStyle messagingStyle, boolean isHeadsUp,
            boolean isGroupConversation, CharSequence senderName, Bundle extras) {
        CharSequence conversationTitle = null;

        if (messagingStyle != null) {
            conversationTitle = messagingStyle.getConversationTitle();
        }

        if (isGroupConversation && conversationTitle != null && isHeadsUp) {
            // If conversation title has been set, conversation is a group conversation
            // and notification is a HUN, then prepend sender's name to title.
            conversationTitle = senderName + SENDER_TITLE_SEPARATOR + conversationTitle;
        } else if (conversationTitle == null) {
            if (DEBUG) {
                Log.d(TAG, "Conversation title not set.");
            }
            // If conversation title has not been set, set it as sender's name.
            conversationTitle = senderName;
        }

        if (TextUtils.isEmpty(conversationTitle)) {
            if (DEBUG) {
                Log.d(TAG, "Standard field conversation title used.");
            }
            conversationTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
        }

        return conversationTitle;
    }

    private int getMessageCount(List<Notification.MessagingStyle.Message> messages, int numEvents) {
        Integer messageCount = null;

        if (messages != null) {
            messageCount = messages.size();
        } else {
            messageCount = numEvents;
            if (messageCount == 0) {
                // A notification should at least represent 1 message
                messageCount = 1;
            }
        }

        return messageCount;
    }

    @Override
    void reset() {
        super.reset();
        mBodyView.reset();
        mHeaderView.reset();
        mActionsView.reset();
    }

    private View.OnClickListener getCountViewOnClickListener(int unshownCount,
            @Nullable List<Notification.MessagingStyle.Message> messages,
            boolean isGroupConversation, StatusBarNotification sbn, CharSequence title,
            @Nullable Icon avatar, @Nullable Drawable groupIcon, @Nullable Long when) {
        String finalMessage;
        if (unshownCount > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = messages.size() - 1; i >= messages.size() - 1 - mMaxMessageCount && i >= 0;
                    i--) {
                if (i != messages.size() - 1) {
                    builder.append(NEW_LINE);
                    builder.append(NEW_LINE);
                }
                unshownCount--;
                Notification.MessagingStyle.Message message = messages.get(i);
                Person sender = message.getSenderPerson();
                CharSequence senderName =
                        (sender != null ? sender.getName() : message.getSender());
                if (isGroupConversation) {
                    builder.append(senderName + SENDER_BODY_SEPARATOR + message.getText());
                } else {
                    builder.append(message.getText());
                }
                if (builder.toString().split(NEW_LINE).length >= mMaxLineCount) {
                    break;
                }
            }

            finalMessage = builder.toString();
        } else {
            StringBuilder builder = new StringBuilder();
            Notification.MessagingStyle.Message message = messages.get(messages.size() - 1);
            Person sender = message.getSenderPerson();
            CharSequence senderName =
                    (sender != null ? sender.getName() : message.getSender());
            if (isGroupConversation) {
                builder.append(senderName + SENDER_BODY_SEPARATOR + message.getText());
            } else {
                builder.append(message.getText());
            }
            String messageStr = builder.toString();

            int maxCharCountAfterExpansion;
            if (mPreprocessingManager.getMaximumStringLength() == Integer.MAX_VALUE) {
                maxCharCountAfterExpansion = Integer.MAX_VALUE;
            } else {
                // We are exceeding UXRE maximum string length limit only when expanding the long
                // message notification. This neither applies for collapsed single message
                // notifications nor applies for UXRE updates that are handled by `isRestricted`
                // being {@code true}.
                maxCharCountAfterExpansion = mPreprocessingManager.getMaximumStringLength()
                        + mAdditionalCharCountAfterExpansion - mEllipsizedSuffix.length();
            }

            if (messageStr.length() > maxCharCountAfterExpansion) {
                messageStr = messageStr.substring(0, maxCharCountAfterExpansion - 1)
                        + mEllipsizedSuffix;
            }
            finalMessage = messageStr;
        }

        int finalUnshownCount = unshownCount;

        return view -> {
            String unshownCountText;
            if (finalUnshownCount <= 0) {
                unshownCountText = null;
            } else {
                unshownCountText = getContext().getResources().getQuantityString(
                        R.plurals.message_unshown_count, finalUnshownCount, finalUnshownCount);
            }

            mBodyView.bind(title, finalMessage, sbn, avatar, groupIcon,
                    unshownCountText, when);
            mBodyView.setContentMaxLines(mMaxLineCount);
            mBodyView.setCountOnClickListener(null);
            mBodyView.setCountTextAlpha(mDisabledCountTextButtonAlpha);
        };
    }
}
