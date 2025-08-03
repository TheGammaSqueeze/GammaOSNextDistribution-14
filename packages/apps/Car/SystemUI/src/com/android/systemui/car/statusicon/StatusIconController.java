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

package com.android.systemui.car.statusicon;

import android.annotation.DimenRes;
import android.annotation.LayoutRes;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.systemui.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class to extend to control views that display a certain status icon.
 */
public abstract class StatusIconController {
    public static final int PANEL_CONTENT_LAYOUT_NONE = -1;

    private final StatusIconData mStatusIconData = new StatusIconData();
    private final MutableLiveData<StatusIconData> mStatusIconLiveData =
            new MutableLiveData<>(mStatusIconData);
    private final Map<ImageView, Observer<StatusIconData>> mObserverMap = new HashMap<>();

    /**
     * Interface definition for a callback to be invoked when a status icon is updated.
     */
    public interface OnStatusUpdatedListener {
        /**
         * Reports that a status icon is updated.
         *
         * @param statusIconController controller to display a certain status icon.
         */
        void onStatusUpdated(StatusIconController statusIconController);
    }

    private OnStatusUpdatedListener mOnStatusUpdatedListener;

    public void setOnStatusUpdatedListener(OnStatusUpdatedListener l) {
        mOnStatusUpdatedListener = l;
    }

    /**
     * Registers an {@link ImageView} to contain the icon that this controller controls.
     */
    public final void registerIconView(ImageView view) {
        if (mObserverMap.containsKey(view)) return;

        Observer<StatusIconData> observer = statusIconData -> updateIconView(view, statusIconData);
        mObserverMap.put(view, observer);
        mStatusIconLiveData.observeForever(observer);
    }

    /**
     * Unregisters the observer for an {@link ImageView}.
     */
    public final void unregisterIconView(ImageView view) {
        Observer<StatusIconData> observer = mObserverMap.remove(view);
        if (observer != null) {
            mStatusIconLiveData.removeObserver(observer);
        }
    }

    /**
     * Returns the {@link Drawable} set to be displayed as the icon.
     */
    public Drawable getIconDrawableToDisplay() {
        return mStatusIconData.getIconDrawable();
    }

    /**
     * Sets the icon drawable to display.
     */
    protected final void setIconContentDescription(String str) {
        mStatusIconData.setContentDescription(str);
    }

    /**
     * Sets the icon drawable to display.
     */
    protected final void setIconDrawableToDisplay(Drawable drawable) {
        mStatusIconData.setIconDrawable(drawable);
    }

    /**
     * Sets the icon visibility.
     *
     * NOTE: Icons are visible by default.
     */
    protected final void setIconVisibility(boolean isVisible) {
        mStatusIconData.setIsIconVisible(isVisible);
    }

    /**
     * Lifecycle method executed when this controller is destroyed to clean up any references.
     */
    protected void onDestroy() {
    }

    /**
     * Provides observing views with the {@link StatusIconData} and causes them to update
     * themselves accordingly through {@link #updateIconView}.
     */
    protected void onStatusUpdated() {
        mStatusIconLiveData.setValue(mStatusIconData);
        if (mOnStatusUpdatedListener != null) {
            mOnStatusUpdatedListener.onStatusUpdated(this);
        }
    }

    /**
     * Updates the icon view based on the current {@link StatusIconData}.
     */
    protected void updateIconView(ImageView view, StatusIconData data) {
        view.setImageDrawable(data.getIconDrawable());
        view.setVisibility(data.getIsIconVisible() ? View.VISIBLE : View.GONE);
        view.setContentDescription(data.getContentDescription());
    }

    /**
     * Returns the resource id of the layout to be drawn inside the panel associated with this
     * status icon.
     *
     * By default, {@link #PANEL_CONTENT_LAYOUT_NONE} is returned and no panel will be attached to
     * the status icon.
     */
    @LayoutRes
    protected int getPanelContentLayout() {
        return PANEL_CONTENT_LAYOUT_NONE;
    }

    /**
     * Returns the resource id of the width for the panel associated with this status icon.
     */
    @DimenRes
    protected int getPanelWidth() {
        return R.dimen.car_status_icon_panel_default_width;
    }

    /**
     * Determines the icon to display via {@link #setIconDrawableToDisplay} and notifies observing
     * views by calling {@link #onStatusUpdated} at the end.
     */
    protected abstract void updateStatus();

    /**
     * Gets the Id for the View.
     */
    protected abstract int getId();

    @VisibleForTesting
    boolean isViewRegistered(ImageView view) {
        return mObserverMap.containsKey(view);
    }
}
