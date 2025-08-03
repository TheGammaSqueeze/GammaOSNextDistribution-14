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

package com.android.systemui.car.systembar.element;

import android.app.StatusBarManager;
import android.os.Bundle;
import android.view.Display;
import android.view.View;

import androidx.annotation.CallSuper;

import com.android.systemui.util.ViewController;

public abstract class CarSystemBarElementController<V extends View & CarSystemBarElement>
        extends ViewController<V> {
    private final CarSystemBarElementStatusBarDisableController mElementDisableController;
    private final CarSystemBarElementStateController mElementStateController;
    private boolean mIsDisabledBySystemBarState;
    private boolean mDisableListenerRegistered = false;
    private boolean mStateControllerRegistered = false;
    private final CarSystemBarElementStatusBarDisableController.Listener mDisableListener =
            disabled -> {
                mIsDisabledBySystemBarState = disabled;
                updateVisibility();
            };

    protected CarSystemBarElementController(V view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController) {
        super(view);
        mElementDisableController = disableController;
        mElementStateController = stateController;
    }

    /**
     * Generic factory for CarSystemBarElementController - implementation classes should override
     * with types (without overriding the create method).
     * @param <V> view type that also implements CarSystemBarElement
     * @param <T> controller type that extends CarSystemBarElementController
     */
    public interface Factory<V extends View & CarSystemBarElement,
            T extends CarSystemBarElementController<V>> {
        /** Create instance of CarSystemBarElementController for CarSystemBarElement view */
        T create(V view);
    }

    @Override
    @CallSuper
    protected void onViewAttached() {
        if (shouldRegisterDisableListener()) {
            int displayId = mView.getDisplay() != null ? mView.getDisplay().getDisplayId()
                    : Display.INVALID_DISPLAY;
            if (displayId == Display.INVALID_DISPLAY) return;
            mElementDisableController.addListener(mDisableListener,
                    displayId, mView.getSystemBarDisableFlags(),
                    mView.getSystemBarDisable2Flags(), mView.disableForLockTaskModeLocked());
            mDisableListenerRegistered = true;
        }

        if (shouldRestoreState() && !mStateControllerRegistered) {
            mElementStateController.registerController(/* CarSystemBarElementController= */ this);
            mStateControllerRegistered = true;
        }
    }

    @Override
    @CallSuper
    protected void onViewDetached() {
        if (mDisableListenerRegistered) {
            mElementDisableController.removeListener(mDisableListener);
            mDisableListenerRegistered = false;
        }

        if (mStateControllerRegistered) {
            mElementStateController.unregisterController(/* CarSystemBarElementController= */ this);
            mStateControllerRegistered = false;
        }
    }

    /**
     * Updates the visibility of the view based on the system bar states and the state of
     * {@link #shouldBeVisible}.
     */
    protected final void updateVisibility() {
        boolean visible = shouldBeVisible() && !mIsDisabledBySystemBarState;
        mView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Return true if the element should currently be visible - this may be overruled by the
     * system bar disable state flags.
     */
    protected boolean shouldBeVisible() {
        return true;
    }

    /**
     * Return true if the element should be restore its state in the event that the hosting system
     * bar is restarted.
     */
    protected boolean shouldRestoreState() {
        return false;
    }

    /** Get state to save on system bar restart - values should be added to the provided bundle. */
    protected Bundle getState(Bundle bundle) {
        return bundle;
    }

    /** Restore controller state from the provided saved bundle. */
    protected void restoreState(Bundle bundle) {
    }

    /**
     * Attempts to return a unique id for this view instance by concatenating the root view id with
     * the current view id - per core android recommendations, is assumed that a view within a
     * single view hierarchy has a unique id. This logic may be overridden to provide a different
     * mechanism of providing unique ids should this not meet the needs of a specific configuration.
     */
    protected long getUniqueViewId() {
        if (mView.getId() <= 0) {
            return View.NO_ID;
        }
        if (mView.getRootView() == null || mView.getRootView().getId() <= 0) {
            return mView.getId();
        }
        // create unique id by concatenating the root id with the view id
        int length = (int) Math.floor(Math.log10(mView.getId())) + 1;
        return (long) Math.pow(10, length) * mView.getRootView().getId() + mView.getId();
    }

    private boolean shouldRegisterDisableListener() {
        if (mDisableListenerRegistered) return false;
        return mView.getSystemBarDisableFlags() != StatusBarManager.DISABLE_NONE
                || mView.getSystemBarDisable2Flags() != StatusBarManager.DISABLE2_NONE
                || mView.disableForLockTaskModeLocked();
    }
}
