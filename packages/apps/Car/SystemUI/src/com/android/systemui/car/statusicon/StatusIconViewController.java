/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.graphics.drawable.Drawable;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;

public abstract class StatusIconViewController extends
        CarSystemBarElementController<StatusIconView> {

    private final StatusIconData mStatusIconData = new StatusIconData();
    private final MutableLiveData<StatusIconData> mStatusIconLiveData =
            new MutableLiveData<>(mStatusIconData);
    private final Observer<StatusIconData> mObserver;

    protected StatusIconViewController(StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController) {
        super(view, disableController, stateController);
        mObserver = this::updateIconView;
    }

    public interface Factory<T extends StatusIconViewController> extends
            CarSystemBarElementController.Factory<StatusIconView, T> {
    }

    @Override
    @CallSuper
    protected void onViewAttached() {
        super.onViewAttached();
        mStatusIconLiveData.observeForever(mObserver);
    }

    @Override
    @CallSuper
    protected void onViewDetached() {
        super.onViewDetached();
        mStatusIconLiveData.removeObserver(mObserver);
    }

    @Override
    protected boolean shouldBeVisible() {
        StatusIconData data = mStatusIconLiveData.getValue();
        if (data == null) {
            return false;
        }
        return data.getIsIconVisible();
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
     * Returns the {@link Drawable} set to be displayed as the icon.
     */
    @VisibleForTesting
    public Drawable getIconDrawableToDisplay() {
        return mStatusIconData.getIconDrawable();
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
     * Provides observing views with the {@link StatusIconData} and causes them to update
     * themselves accordingly through {@link #updateIconView}.
     */
    protected void onStatusUpdated() {
        mStatusIconLiveData.setValue(mStatusIconData);
    }

    /**
     * Updates the icon view based on the current {@link StatusIconData}.
     */
    protected void updateIconView(StatusIconData data) {
        mView.setImageDrawable(data.getIconDrawable());
        mView.setContentDescription(data.getContentDescription());
        updateVisibility();
    }

    /**
     * Determines the icon to display via {@link #setIconDrawableToDisplay} and notifies observing
     * views by calling {@link #onStatusUpdated} at the end.
     */
    protected abstract void updateStatus();
}
