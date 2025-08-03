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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.notifications;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.example.sampleleanbacklauncher.R;

/**
 * View for a dismissible notification displayed in the notifications side panel.
 * Handles swapping of background in RTL layouts.
 * Handles dismiss button focus animation.
 */
public class NotificationPanelDismissibleItemView extends NotificationPanelItemView {
    private View mDismissButton;
    private TextView mDismissText;
    private int mViewFocusTranslationX;
    private int mDismissTranslationX;
    private boolean mIsRtl;

    public NotificationPanelDismissibleItemView(Context context) {
        super(context);
        initializeTranslationValues();
    }

    public NotificationPanelDismissibleItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeTranslationValues();
    }

    private void initializeTranslationValues() {
        mIsRtl = (getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_RTL);

        mViewFocusTranslationX = getResources().getDimensionPixelSize(
                R.dimen.notification_panel_item_show_button_translate_x);
        mDismissTranslationX = getResources().getDimensionPixelSize(
                R.dimen.notification_panel_item_dismiss_translate_x);


        if (mIsRtl) {
            mViewFocusTranslationX *= -1;
            mDismissTranslationX *= -1;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is to set the focus search to the default behavior, so that on dismissible views
        // the user can navigate left/right to the dismiss button.
        mMainContentText.setNextFocusLeftId(NO_ID);
        mMainContentText.setNextFocusRightId(NO_ID);

        mDismissButton = findViewById(R.id.dismiss_button);
        mDismissText = findViewById(R.id.dismiss_text);

        if (mIsRtl) {
            mMainContentText.setBackgroundResource(R.drawable.notification_background_left);
            mDismissButton.setBackgroundResource(R.drawable.notification_background_right);
        } else {
            mMainContentText.setBackgroundResource(R.drawable.notification_background_right);
            mDismissButton.setBackgroundResource(R.drawable.notification_background_left);
        }

        final AnimatorSet dismissAnimator = new AnimatorSet();
        ObjectAnimator containerSlide = ObjectAnimator.ofFloat(mMainContentText, View.TRANSLATION_X,
                mViewFocusTranslationX, mDismissTranslationX);
        ObjectAnimator dismissButtonSlide = ObjectAnimator.ofFloat(mDismissButton,
                View.TRANSLATION_X, mViewFocusTranslationX, mDismissTranslationX);

        dismissAnimator.playTogether(dismissButtonSlide, containerSlide);

        dismissAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                collapseText();
                mDismissText.setVisibility(GONE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDismissButton.setVisibility(INVISIBLE);
                mMainContentText.setVisibility(INVISIBLE);
                setBackgroundColor(getContext().getColor(R.color.notification_selection_color));
                NotificationsUtils.dismissNotification(getContext(), mNotificationKey);
            }
        });

        final AnimatorSet gainFocus = new AnimatorSet();
        ObjectAnimator containerSlideOut = ObjectAnimator.ofFloat(mMainContentText,
                View.TRANSLATION_X, 0, mViewFocusTranslationX);
        ObjectAnimator dismissButtonFocusGain = ObjectAnimator.ofFloat(mDismissButton,
                View.TRANSLATION_X, 0, mViewFocusTranslationX);
        gainFocus.playTogether(dismissButtonFocusGain, containerSlideOut);

        final AnimatorSet loseFocus = new AnimatorSet();
        ObjectAnimator containerSlideIn = ObjectAnimator.ofFloat(mMainContentText,
                View.TRANSLATION_X, mViewFocusTranslationX, 0);
        ObjectAnimator dismissButtonFocusLost = ObjectAnimator.ofFloat(mDismissButton,
                View.TRANSLATION_X, mViewFocusTranslationX, 0);
        loseFocus.playTogether(dismissButtonFocusLost, containerSlideIn);
        loseFocus.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDismissText.setVisibility(GONE);
            }
        });

        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNotificationKey != null) {
                    dismissAnimator.start();
                }
            }
        });
        mDismissButton.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    // Slide the views to the side and show the dismiss text
                    mDismissText.setVisibility(VISIBLE);
                    gainFocus.start();
                } else {
                    // Slide the views back to their original positions and hide the dismiss text
                    loseFocus.start();
                }
            }
        });
    }

    @Override
    public void setNotification(TvNotification notif) {
        super.setNotification(notif);
        mDismissText.setText(notif.getDismissButtonLabel());
        mDismissButton.setVisibility(VISIBLE);
    }
}

