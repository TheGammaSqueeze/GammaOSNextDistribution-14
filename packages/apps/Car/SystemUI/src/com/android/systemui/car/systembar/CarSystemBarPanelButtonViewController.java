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

package com.android.systemui.car.systembar;

import static com.android.systemui.car.systembar.CarSystemBarPanelButtonView.INVALID_RESOURCE_ID;

import android.os.Bundle;

import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/** Controller for the button view that anchors system bar panels. */
public class CarSystemBarPanelButtonViewController extends
        CarSystemBarElementController<CarSystemBarPanelButtonView> {
    private static final String KEY_IS_SELECTED = "key_is_selected";
    private final Provider<StatusIconPanelViewController.Builder> mStatusIconPanelBuilder;

    @AssistedInject
    protected CarSystemBarPanelButtonViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<StatusIconPanelViewController.Builder> statusIconPanelBuilder) {
        super(view, disableController, stateController);
        mStatusIconPanelBuilder = statusIconPanelBuilder;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    CarSystemBarPanelButtonViewController> {
    }

    @Override
    protected void onInit() {
        StatusIconPanelViewController.Builder builder = mStatusIconPanelBuilder.get();
        int panelLayoutRes = mView.getPanelContentLayout();
        int panelLayoutWidthRes = mView.getPanelWidth();
        Integer xOffset = mView.getXOffset();
        if (xOffset != null) {
            builder.setXOffset(xOffset);
        }
        Integer yOffset = mView.getYOffset();
        if (yOffset != null) {
            builder.setYOffset(yOffset);
        }
        Integer gravity = mView.getPanelGravity();
        if (gravity != null) {
            builder.setGravity(gravity);
        }
        Boolean disabledWhileDriving = mView.getDisabledWhileDriving();
        if (disabledWhileDriving != null) {
            builder.setDisabledWhileDriving(disabledWhileDriving);
        }
        Boolean showAsDropDown = mView.getShowAsDropDown();
        if (showAsDropDown != null) {
            builder.setShowAsDropDown(showAsDropDown);
        }

        if (panelLayoutRes != INVALID_RESOURCE_ID) {
            StatusIconPanelViewController panelController = builder.build(mView,
                    panelLayoutRes,
                    panelLayoutWidthRes);
            panelController.init();
        }
    }

    @Override
    protected boolean shouldRestoreState() {
        return true;
    }

    @Override
    protected Bundle getState(Bundle bundle) {
        bundle.putBoolean(KEY_IS_SELECTED, mView.isSelected());
        return bundle;
    }

    @Override
    protected void restoreState(Bundle bundle) {
        if (bundle.containsKey(KEY_IS_SELECTED)) {
            boolean selected = bundle.getBoolean(KEY_IS_SELECTED);
            if (selected != mView.isSelected()) {
                mView.callOnClick();
            }
        }
    }
}
