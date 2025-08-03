/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;

import com.android.systemui.dagger.SysUISingleton;

/**
 * CarSystemBarButtons can optionally have selection state that toggles certain visual indications
 * based on whether the active application on screen is associated with it. This is basically a
 * similar concept to a radio button group.
 *
 * This class controls the selection state of CarSystemBarButtons that have opted in to have such
 * selection state-dependent visual indications.
 */
@SysUISingleton
public class CarUiPortraitButtonSelectionStateController extends ButtonSelectionStateController {

    private CarUiPortraitAppGridButton mAppGridButton;
    private CarUiPortraitNotificationButton mNotificationButton;

    public CarUiPortraitButtonSelectionStateController(Context context) {
        super(context);
    }

    @Override
    protected ComponentName getTopActivity(
            ActivityTaskManager.RootTaskInfo validTaskInfo) {
        return validTaskInfo.topActivity;
    }

    @Override
    protected void addAllButtonsWithSelectionState(View v) {
        if (v instanceof CarUiPortraitAppGridButton) {
            mAppGridButton = (CarUiPortraitAppGridButton) v;
        } else if (v instanceof CarUiPortraitNotificationButton) {
            mNotificationButton = (CarUiPortraitNotificationButton) v;
        } else {
            super.addAllButtonsWithSelectionState(v);
        }
    }

    /** Updates the selected state (for AppGrid activity) of the app grid button */
    void setAppGridButtonSelected(boolean isSelected) {
        if (mAppGridButton != null) {
            mAppGridButton.setAppGridSelected(isSelected);
        }
    }

    /** Updates the selected state (for Recents activity) of the app grid button */
    void setRecentsButtonSelected(boolean isSelected) {
        if (mAppGridButton != null) {
            mAppGridButton.setRecentsSelected(isSelected);
        }
    }

    /** Updates the selected state of the notification button */
    void setNotificationButtonSelected(boolean isSelected) {
        if (mNotificationButton != null) {
            mNotificationButton.setSelected(isSelected);
        }
    }

}
