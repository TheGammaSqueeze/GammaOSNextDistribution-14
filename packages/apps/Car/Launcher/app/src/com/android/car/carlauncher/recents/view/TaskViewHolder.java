/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.recents.view;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.carlauncher.R;

/**
 * Builds onto BaseViewHolder by adding functionality of disabled state and adding click and touch
 * listeners.
 */
public class TaskViewHolder extends BaseTaskViewHolder {
    private final ImageView mThumbnailImageView;
    private final ImageView mIconImageView;
    private final ImageView mDismissButton;
    private final float mDisabledStateAlpha;
    private ViewTreeObserver.OnTouchModeChangeListener mOnTouchModeChangeListener;

    public TaskViewHolder(@NonNull View itemView) {
        super(itemView);
        mThumbnailImageView = itemView.findViewById(R.id.task_thumbnail);
        mIconImageView = itemView.findViewById(R.id.task_icon);
        mDismissButton = itemView.findViewById(R.id.task_dismiss_button);
        mDisabledStateAlpha = itemView.getResources().getFloat(
                R.dimen.disabled_recent_task_alpha);
    }

    public void bind(@Nullable Drawable icon, @Nullable Bitmap thumbnail, boolean isDisabled,
            @Nullable View.OnClickListener openTaskClickListener,
            @Nullable View.OnClickListener dismissTaskClickListener,
            @Nullable View.OnTouchListener taskTouchListener) {
        super.bind(icon, thumbnail);

        setDisabledAlpha(mThumbnailImageView, isDisabled);
        setDisabledAlpha(mIconImageView, isDisabled);

        setListeners(mThumbnailImageView, taskTouchListener, openTaskClickListener);
        setListeners(mIconImageView, taskTouchListener, openTaskClickListener);

        mDismissButton.setOnClickListener(dismissTaskClickListener);
    }

    /**
     * Callback for when the vh is attached to the window.
     * Used to set the state for Dismiss Button depending on the touch mode. This should be done
     * after the view is attached to the window else {@link View#isInTouchMode} would always return
     * the default value.
     */
    public void attachedToWindow() {
        setDismissButtonVisibility(itemView.isInTouchMode());
        mOnTouchModeChangeListener = this::setDismissButtonVisibility;
        itemView.getViewTreeObserver().addOnTouchModeChangeListener(mOnTouchModeChangeListener);
    }

    /**
     * Callback for when the vh is detached from the window.
     */
    public void detachedFromWindow() {
        itemView.getViewTreeObserver().removeOnTouchModeChangeListener(mOnTouchModeChangeListener);
    }

    private void setListeners(View view, View.OnTouchListener onTouchListener,
            View.OnClickListener onClickListener) {
        view.setOnTouchListener(onTouchListener);
        view.setOnClickListener(onClickListener);
    }

    private void setDisabledAlpha(View view, boolean isDisabled) {
        if (isDisabled) {
            view.setAlpha(mDisabledStateAlpha);
        } else {
            view.setAlpha(1f);
        }
    }

    private void setDismissButtonVisibility(boolean isInTouchMode) {
        if (mDismissButton == null) {
            return;
        }
        mDismissButton.setVisibility(isInTouchMode ? View.GONE : View.VISIBLE);
    }
}
