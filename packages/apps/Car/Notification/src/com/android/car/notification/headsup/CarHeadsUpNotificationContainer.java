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

package com.android.car.notification.headsup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;

import com.android.car.notification.CarNotificationTypeItem;
import com.android.car.notification.R;

import java.util.LinkedList;

/**
 * Container for displaying Heads Up Notifications.
 */
public abstract class CarHeadsUpNotificationContainer {
    private static final String TAG = "CarHUNContainer";
    private final LinkedList<HunImportance> mHunImportanceLinkedList = new LinkedList<>();
    private final ViewGroup mHunWindow;
    private final ViewGroup mHunContent;
    private final boolean mShowHunOnBottom;
    private final Context mContext;

    public CarHeadsUpNotificationContainer(Context context, WindowManager windowManager) {
        mContext = context;
        mShowHunOnBottom = context.getResources().getBoolean(
                R.bool.config_showHeadsUpNotificationOnBottom);
        mHunWindow = (ViewGroup) LayoutInflater.from(context).inflate(
                mShowHunOnBottom ? R.layout.headsup_container_bottom
                        : R.layout.headsup_container, /* root= */ null, /* attachToRoot= */ false);
        mHunContent = mHunWindow.findViewById(R.id.headsup_content);
        mHunWindow.setVisibility(View.INVISIBLE);
        windowManager.addView(mHunWindow, getWindowManagerLayoutParams());
    }

    /**
     * @return {@link WindowManager.LayoutParams} to be used when adding HUN Window to {@link
     * WindowManager}.
     */
    protected abstract WindowManager.LayoutParams getWindowManagerLayoutParams();

    protected Context getContext() {
        return mContext;
    }

    /**
     * Displays a given notification View to the user and inserts the view at Z-index according to
     * its {@link HunImportance},
     */
    public void displayNotification(View notificationView,
            CarNotificationTypeItem notificationTypeItem) {
        HunImportance hunImportance = getImportanceForCarNotificationTypeItem(notificationTypeItem);

        displayNotificationInner(notificationView, hunImportance);

        if (shouldShowHunPanel()) {
            getHunWindow().setVisibility(View.VISIBLE);
        }
    }

    private void displayNotificationInner(View notificationView, HunImportance hunImportance) {
        if (mHunImportanceLinkedList.isEmpty() || hunImportance.equals(HunImportance.EMERGENCY)) {
            mHunImportanceLinkedList.add(hunImportance);
            getHunContent().addView(notificationView);
            return;
        }

        int index = 0;
        for (; index < mHunImportanceLinkedList.size(); index++) {
            if (hunImportance.isLessImportantThan(mHunImportanceLinkedList.get(index))) break;
        }
        if (index < mHunImportanceLinkedList.size()) {
            mHunImportanceLinkedList.add(index, hunImportance);
            getHunContent().addView(notificationView, index);
            return;
        }

        mHunImportanceLinkedList.add(hunImportance);
        getHunContent().addView(notificationView);
    }

    /**
     * @return {@code true} if Hun panel should be set as visible after displaying HUN.
     */
    public boolean shouldShowHunPanel() {
        return !isVisible();
    }

    /**
     * Removes a given notification View from the container.
     */
    public void removeNotification(View notificationView) {
        if (getHunContent().getChildCount() == 0) return;

        int index = getHunContent().indexOfChild(notificationView);
        if (index == -1) return;

        getHunContent().removeViewAt(index);
        mHunImportanceLinkedList.remove(index);

        if (shouldHideHunPanel()) {
            getHunWindow().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * @return {@code true} if HUN panel should be set as invisible after removing a HUN.
     */
    public boolean shouldHideHunPanel() {
        return getHunContent().getChildCount() == 0;
    }

    /**
     * @return Whether or not the container is currently visible.
     */
    public final boolean isVisible() {
        return getHunWindow().getVisibility() == View.VISIBLE;
    }

    /**
     * @return HUN window.
     */
    protected final ViewGroup getHunWindow() {
        return mHunWindow;
    }

    /**
     * @return HUN content inside of window.
     */
    protected final ViewGroup getHunContent() {
        return mHunContent;
    }

    /**
     * @return {@code true} if HUN should be shown on bottom.
     */
    protected final boolean getShowHunOnBottom() {
        return mShowHunOnBottom;
    }

    private HunImportance getImportanceForCarNotificationTypeItem(
            CarNotificationTypeItem notificationTypeItem) {
        if (notificationTypeItem == CarNotificationTypeItem.EMERGENCY) {
            return HunImportance.EMERGENCY;
        } else if (notificationTypeItem == CarNotificationTypeItem.WARNING) {
            return HunImportance.WARNING;
        } else if (notificationTypeItem == CarNotificationTypeItem.NAVIGATION) {
            return HunImportance.NAVIGATION;
        } else if (notificationTypeItem == CarNotificationTypeItem.CALL) {
            return HunImportance.CALL;
        } else {
            return HunImportance.OTHER;
        }
    }

    @VisibleForTesting
    enum HunImportance {
        OTHER(/* level= */ 0),
        CALL(/* level= */ 1),
        NAVIGATION(/* level= */ 2),
        WARNING(/* level= */ 3),
        EMERGENCY(/* level= */ 4);

        private final Integer mLevel;

        HunImportance(int level) {
            this.mLevel = level;
        }

        boolean isMoreImportantThan(HunImportance other) {
            return this.mLevel > other.mLevel;
        }

        boolean isLessImportantThan(HunImportance other) {
            return this.mLevel < other.mLevel;
        }
    }
}
