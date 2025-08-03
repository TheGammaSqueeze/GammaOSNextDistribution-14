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

import android.app.Notification;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.assist.client.CarAssistUtils;
import com.android.car.notification.AlertEntry;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationDataManager;
import com.android.car.notification.PreprocessingManager;
import com.android.car.notification.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification actions view that contains the buttons that fire actions.
 */
public class CarNotificationActionsView extends LinearLayout implements
        PreprocessingManager.CallStateListener {
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String TAG = "CarNotificationActionsView";

    // Maximum 3 actions
    // https://developer.android.com/reference/android/app/Notification.Builder.html#addAction
    @VisibleForTesting
    static final int MAX_NUM_ACTIONS = 3;
    @VisibleForTesting
    static final int FIRST_MESSAGE_ACTION_BUTTON_INDEX = 0;
    @VisibleForTesting
    static final int SECOND_MESSAGE_ACTION_BUTTON_INDEX = 1;
    @VisibleForTesting
    static final int THIRD_MESSAGE_ACTION_BUTTON_INDEX = 2;

    private final List<CarNotificationActionButton> mActionButtons = new ArrayList<>();
    private final Context mContext;
    private final CarAssistUtils mCarAssistUtils;
    private final Drawable mActionButtonBackground;
    private final Drawable mCallButtonBackground;
    private final Drawable mDeclineButtonBackground;
    @ColorInt
    private final int mCallButtonTextColor;
    @ColorInt
    private final int mDeclineButtonTextColor;
    private final Drawable mUnmuteButtonBackground;
    private final String mReplyButtonText;
    private final String mPlayButtonText;
    private final String mMuteText;
    private final String mUnmuteText;
    @ColorInt
    private final int mUnmuteTextColor;
    private final boolean mEnableDirectReply;
    private final boolean mEnablePlay;

    @VisibleForTesting
    final Drawable mPlayButtonDrawable;
    @VisibleForTesting
    final Drawable mReplyButtonDrawable;
    @VisibleForTesting
    final Drawable mMuteButtonDrawable;
    @VisibleForTesting
    final Drawable mUnmuteButtonDrawable;


    private NotificationDataManager mNotificationDataManager;
    private NotificationClickHandlerFactory mNotificationClickHandlerFactory;
    private AlertEntry mAlertEntry;
    private boolean mIsCategoryCall;
    private boolean mIsInCall;

    public CarNotificationActionsView(Context context) {
        this(context, /* attrs= */ null);
    }

    public CarNotificationActionsView(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public CarNotificationActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public CarNotificationActionsView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr, defStyleRes, new CarAssistUtils(context));
    }

    @VisibleForTesting
    CarNotificationActionsView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes, @NonNull CarAssistUtils carAssistUtils) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mContext = context;
        mCarAssistUtils = carAssistUtils;
        mNotificationDataManager = NotificationDataManager.getInstance();
        mActionButtonBackground = mContext.getDrawable(R.drawable.action_button_background);
        mCallButtonBackground = mContext.getDrawable(R.drawable.call_action_button_background);
        mCallButtonBackground.setColorFilter(
                new PorterDuffColorFilter(mContext.getColor(R.color.call_accept_button),
                        PorterDuff.Mode.SRC_IN));
        mDeclineButtonBackground = mContext.getDrawable(R.drawable.call_action_button_background);
        mDeclineButtonBackground.setColorFilter(
                new PorterDuffColorFilter(mContext.getColor(R.color.call_decline_button),
                        PorterDuff.Mode.SRC_IN));
        mCallButtonTextColor = mContext.getColor(R.color.call_accept_button_text);
        mDeclineButtonTextColor = mContext.getColor(R.color.call_decline_button_text);
        mUnmuteButtonBackground = mContext.getDrawable(R.drawable.call_action_button_background);
        mUnmuteButtonBackground.setColorFilter(
                new PorterDuffColorFilter(mContext.getColor(R.color.unmute_button),
                        PorterDuff.Mode.SRC_IN));
        mPlayButtonText = mContext.getString(R.string.assist_action_play_label);
        mReplyButtonText = mContext.getString(R.string.assist_action_reply_label);
        mMuteText = mContext.getString(R.string.action_mute_short);
        mUnmuteText = mContext.getString(R.string.action_unmute_short);
        mPlayButtonDrawable = mContext.getDrawable(R.drawable.ic_play_arrow);
        mReplyButtonDrawable = mContext.getDrawable(R.drawable.ic_reply);
        mMuteButtonDrawable = mContext.getDrawable(R.drawable.ic_mute);
        mUnmuteButtonDrawable = mContext.getDrawable(R.drawable.ic_unmute);
        mEnablePlay =
                mContext.getResources().getBoolean(R.bool.config_enableMessageNotificationPlay);
        mEnableDirectReply = mContext.getResources()
                .getBoolean(R.bool.config_enableMessageNotificationDirectReply);
        mUnmuteTextColor = mContext.getColor(R.color.dark_icon_tint);
        init(attrs);
    }

    @VisibleForTesting
    void setNotificationDataManager(NotificationDataManager notificationDataManager) {
        mNotificationDataManager = notificationDataManager;
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray attributes =
                    mContext.obtainStyledAttributes(attrs, R.styleable.CarNotificationActionsView);
            mIsCategoryCall =
                    attributes.getBoolean(R.styleable.CarNotificationActionsView_categoryCall,
                            /* defaultValue= */ false);
            attributes.recycle();
        }

        inflate(mContext, R.layout.car_notification_actions_view, /* root= */ this);
    }

    /**
     * Binds the notification action buttons.
     *
     * @param clickHandlerFactory factory class used to generate {@link OnClickListener}s.
     * @param alertEntry          the notification that contains the actions.
     */
    public void bind(NotificationClickHandlerFactory clickHandlerFactory, AlertEntry alertEntry) {
        Notification notification = alertEntry.getNotification();
        Notification.Action[] actions = notification.actions;
        if (actions == null || actions.length == 0) {
            setVisibility(View.GONE);
            return;
        }

        PreprocessingManager.getInstance(mContext).addCallStateListener(this);

        mNotificationClickHandlerFactory = clickHandlerFactory;
        mAlertEntry = alertEntry;

        setVisibility(View.VISIBLE);

        if (CarAssistUtils.isCarCompatibleMessagingNotification(
                alertEntry.getStatusBarNotification())) {
            boolean canPlayMessage = mEnablePlay && mCarAssistUtils.hasActiveAssistant()
                    || mCarAssistUtils.isFallbackAssistantEnabled();
            boolean canReplyMessage = mEnableDirectReply && mCarAssistUtils.hasActiveAssistant()
                    && clickHandlerFactory.getReplyAction(alertEntry.getNotification()) != null;
            if (canPlayMessage) {
                createPlayButton(clickHandlerFactory, alertEntry);
            }
            if (canReplyMessage) {
                createReplyButton(clickHandlerFactory, alertEntry);
            }
            createMuteButton(clickHandlerFactory, alertEntry, canReplyMessage);
            return;
        }

        Context packageContext = alertEntry.getStatusBarNotification().getPackageContext(mContext);
        int length = Math.min(actions.length, MAX_NUM_ACTIONS);
        for (int i = 0; i < length; i++) {
            Notification.Action action = actions[i];
            CarNotificationActionButton button = mActionButtons.get(i);
            button.setVisibility(View.VISIBLE);
            // clear spannables and only use the text
            button.setText(action.title.toString());

            if (action.actionIntent != null) {
                button.setOnClickListener(clickHandlerFactory.getActionClickHandler(alertEntry, i));
            }

            Icon icon = action.getIcon();
            if (icon != null) {
                icon.loadDrawableAsync(packageContext, button::setImageDrawable, getAsyncHandler());
            }
        }

        if (mIsCategoryCall) {
            mActionButtons.get(0).setBackground(mCallButtonBackground);
            mActionButtons.get(1).setBackground(mDeclineButtonBackground);
            mActionButtons.get(0).setTextColor(mCallButtonTextColor);
            mActionButtons.get(1).setTextColor(mDeclineButtonTextColor);
        }
    }

    /**
     * Resets the notification actions empty for recycling.
     */
    public void reset() {
        resetButtons();
        PreprocessingManager.getInstance(getContext()).removeCallStateListener(this);
        mAlertEntry = null;
        mNotificationClickHandlerFactory = null;
    }

    private void resetButtons() {
        for (CarNotificationActionButton button : mActionButtons) {
            button.setVisibility(View.GONE);
            button.setText(null);
            button.setImageDrawable(null);
            button.setOnClickListener(null);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mActionButtons.add(findViewById(R.id.action_1));
        mActionButtons.add(findViewById(R.id.action_2));
        mActionButtons.add(findViewById(R.id.action_3));
    }

    @VisibleForTesting
    List<CarNotificationActionButton> getActionButtons() {
        return mActionButtons;
    }

    @VisibleForTesting
    void setCategoryIsCall(boolean isCall) {
        mIsCategoryCall = isCall;
    }

    /**
     * The Play button triggers the assistant to read the message aloud, optionally prompting the
     * user to reply to the message afterwards.
     */
    private void createPlayButton(NotificationClickHandlerFactory clickHandlerFactory,
            AlertEntry alertEntry) {
        if (mIsInCall) return;

        CarNotificationActionButton button = mActionButtons.get(FIRST_MESSAGE_ACTION_BUTTON_INDEX);
        button.setText(mPlayButtonText);
        button.setImageDrawable(mPlayButtonDrawable);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(
                clickHandlerFactory.getPlayClickHandler(alertEntry));
    }

    /**
     * The Reply button triggers the assistant to read the message aloud, optionally prompting the
     * user to reply to the message afterwards.
     */
    private void createReplyButton(NotificationClickHandlerFactory clickHandlerFactory,
            AlertEntry alertEntry) {
        if (mIsInCall) return;
        int index = SECOND_MESSAGE_ACTION_BUTTON_INDEX;

        CarNotificationActionButton button = mActionButtons.get(index);
        button.setText(mReplyButtonText);
        button.setImageDrawable(mReplyButtonDrawable);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(
                clickHandlerFactory.getReplyClickHandler(alertEntry));
    }

    /**
     * The Mute button allows users to toggle whether or not incoming notification with the same
     * statusBarNotification key will be shown with a HUN and trigger a notification sound.
     */
    private void createMuteButton(NotificationClickHandlerFactory clickHandlerFactory,
            AlertEntry alertEntry, boolean canReply) {
        int index = THIRD_MESSAGE_ACTION_BUTTON_INDEX;
        if (!canReply) index = SECOND_MESSAGE_ACTION_BUTTON_INDEX;
        if (mIsInCall) index = FIRST_MESSAGE_ACTION_BUTTON_INDEX;

        CarNotificationActionButton button = mActionButtons.get(index);
        setMuteStatus(button, mNotificationDataManager.isMessageNotificationMuted(alertEntry));
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(
                clickHandlerFactory.getMuteClickHandler(button, alertEntry, this::setMuteStatus));
    }

    private void setMuteStatus(CarNotificationActionButton button, boolean isMuted) {
        button.setText(isMuted ? mUnmuteText : mMuteText);
        button.setTextColor(isMuted ? mUnmuteTextColor : button.getDefaultTextColor());
        button.setImageDrawable(isMuted ? mUnmuteButtonDrawable : mMuteButtonDrawable);
        button.setBackground(isMuted ? mUnmuteButtonBackground : mActionButtonBackground);
    }

    /** Implementation of {@link PreprocessingManager.CallStateListener} **/
    @Override
    public void onCallStateChanged(boolean isInCall) {
        if (mIsInCall == isInCall) {
            return;
        }

        mIsInCall = isInCall;

        if (mNotificationClickHandlerFactory == null || mAlertEntry == null) {
            return;
        }

        if (DEBUG) {
            if (isInCall) {
                Log.d(TAG, "Call state activated: " + mAlertEntry);
            } else {
                Log.d(TAG, "Call state deactivated: " + mAlertEntry);
            }
        }

        int focusedButtonIndex = getFocusedButtonIndex();
        resetButtons();
        bind(mNotificationClickHandlerFactory, mAlertEntry);

        // If not in touch mode and action button had focus, then have original or preceding button
        // request focus.
        if (!isInTouchMode() && focusedButtonIndex != -1) {
            for (int i = focusedButtonIndex; i != -1; i--) {
                CarNotificationActionButton button = getActionButtons().get(i);
                if (button.getVisibility() == View.VISIBLE) {
                    button.requestFocus();
                    return;
                }
            }
        }
    }

    private int getFocusedButtonIndex() {
        for (int i = FIRST_MESSAGE_ACTION_BUTTON_INDEX; i <= THIRD_MESSAGE_ACTION_BUTTON_INDEX;
                i++) {
            boolean hasFocus = getActionButtons().get(i).hasFocus();
            if (hasFocus) {
                return i;
            }
        }
        return -1;
    }

    /** Will be overwritten by test to return a mock Handler **/
    @VisibleForTesting
    Handler getAsyncHandler() {
        return Handler.createAsync(Looper.myLooper());
    }
}
