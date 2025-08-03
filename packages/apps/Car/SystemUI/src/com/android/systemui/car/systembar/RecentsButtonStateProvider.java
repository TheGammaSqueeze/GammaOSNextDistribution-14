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

package com.android.systemui.car.systembar;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.hardware.input.InputManager;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Used to add Recents state functionality to a {@link CarSystemBarButton}
 * Long click functionality is updated to send KeyEvent instead of regular intents when Recents is
 * inactive.
 * click functionality is updated to send KeyEvent when Recents is active.
 * TaskStackChangeListener helps to toggle the local long clicked state which further helps
 * determine the appropriate icon and alpha to show.
 */
public class RecentsButtonStateProvider {
    private final InputManager mInputManager;
    private final ComponentName mRecentsComponentName;
    private final CarSystemBarButton mCarSystemBarButton;
    private final boolean mIsRecentsEntryPointEnabled;
    private TaskStackChangeListener mTaskStackChangeListener;
    private boolean mIsRecentsActive;

    public RecentsButtonStateProvider(Context context, CarSystemBarButton carSystemBarButton) {
        mCarSystemBarButton = carSystemBarButton;
        mInputManager = context.getSystemService(InputManager.class);
        mRecentsComponentName = ComponentName.unflattenFromString(context.getString(
                com.android.internal.R.string.config_recentsComponentName));
        mIsRecentsEntryPointEnabled = context.getResources()
                .getBoolean(R.bool.config_enableRecentsEntryPoint);
        initialiseListener();
    }

    protected void initialiseListener() {
        mTaskStackChangeListener = new TaskStackChangeListener() {
            @Override
            public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
                if (mRecentsComponentName == null) {
                    return;
                }
                ComponentName topComponent =
                        taskInfo.topActivity != null ? taskInfo.topActivity
                                : taskInfo.baseIntent.getComponent();
                if (topComponent != null && mRecentsComponentName.getClassName().equals(
                        topComponent.getClassName())) {
                    mIsRecentsActive = true;
                    return;
                }
                mIsRecentsActive = false;
            }
        };
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackChangeListener);
    }

    /**
     * Adds OnLongClickListener to the {@link CarSystemBarButton} to toggle Recents.
     *
     * @param defaultSetUpIntents default function to be called for non Recents functionality.
     * @see CarSystemBarButton#setUpIntents(TypedArray)
     */
    public void setUpIntents(TypedArray typedArray, Consumer<TypedArray> defaultSetUpIntents) {
        if (defaultSetUpIntents != null) {
            defaultSetUpIntents.accept(typedArray);
        }
        mCarSystemBarButton.setOnLongClickListener(v -> {
            if (mIsRecentsActive) {
                return false;
            }
            return toggleRecents();
        });
    }

    /**
     * Adds OnClickListener to the {@link CarSystemBarButton} to toggle Recents.
     *
     * @param defaultGetButtonClickListener default function to be called for non Recents
     *                                      functionality.
     * @see CarSystemBarButton#getButtonClickListener(Intent)
     */
    public View.OnClickListener getButtonClickListener(Intent toSend,
            Function<Intent, View.OnClickListener> defaultGetButtonClickListener) {
        return v -> {
            if (mIsRecentsActive) {
                toggleRecents();
                return;
            }
            if (defaultGetButtonClickListener == null) {
                return;
            }
            View.OnClickListener onClickListener = defaultGetButtonClickListener.apply(toSend);
            if (onClickListener == null) {
                return;
            }
            onClickListener.onClick(v);
        };
    }


    /**
     * Updates the {@code icon}'s drawable to Recents icon when Recents is active.
     *
     * @param defaultUpdateImage default function to be called for non Recents functionality.
     * @see CarSystemBarButton#updateImage(AlphaOptimizedImageView)
     */
    public void updateImage(AlphaOptimizedImageView icon,
            Consumer<AlphaOptimizedImageView> defaultUpdateImage) {
        if (mIsRecentsActive) {
            icon.setImageResource(R.drawable.car_ic_recents);
            return;
        }
        if (defaultUpdateImage == null) {
            return;
        }
        defaultUpdateImage.accept(icon);
    }

    /**
     * Updates the {@code icon}'s alpha to selected alpha when Recents is active.
     *
     * @param defaultRefreshIconAlpha default function to be called for non Recents functionality.
     * @see CarSystemBarButton#refreshIconAlpha(AlphaOptimizedImageView)
     */
    public void refreshIconAlpha(AlphaOptimizedImageView icon,
            Consumer<AlphaOptimizedImageView> defaultRefreshIconAlpha) {
        if (mIsRecentsActive) {
            icon.setAlpha(mCarSystemBarButton.getSelectedAlpha());
            return;
        }
        if (defaultRefreshIconAlpha == null) {
            return;
        }
        defaultRefreshIconAlpha.accept(icon);
    }

    /**
     * Sets if the Recents activity is in foreground.
     */
    public void setIsRecentsActive(boolean isRecentsActive) {
        mIsRecentsActive = isRecentsActive;
    }

    /**
     * Gets if the Recents activity is in foreground.
     */
    public boolean getIsRecentsActive() {
        return mIsRecentsActive;
    }

    /**
     * Opens/closes the Recents Activity.
     */
    protected boolean toggleRecents() {
        return mIsRecentsEntryPointEnabled && mInputManager.injectInputEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_APP_SWITCH),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    @VisibleForTesting
    TaskStackChangeListener getTaskStackChangeListener() {
        return mTaskStackChangeListener;
    }
}
