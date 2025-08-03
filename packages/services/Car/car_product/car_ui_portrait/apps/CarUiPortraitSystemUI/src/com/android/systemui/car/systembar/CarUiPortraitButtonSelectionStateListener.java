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

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_LAUNCHER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

class CarUiPortraitButtonSelectionStateListener extends ButtonSelectionStateListener {

    private CarUiPortraitButtonSelectionStateController mPortraitButtonStateController;
    private boolean mIsAppGridVisible;
    private boolean mIsNotificationVisible;
    private boolean mIsRecentsVisible;

    CarUiPortraitButtonSelectionStateListener(Context context,
            ButtonSelectionStateController carSystemButtonController) {
        super(carSystemButtonController);
        if (mButtonSelectionStateController
                instanceof CarUiPortraitButtonSelectionStateController) {
            mPortraitButtonStateController =
                    (CarUiPortraitButtonSelectionStateController) carSystemButtonController;
        }

        BroadcastReceiver displayAreaVisibilityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mPortraitButtonStateController == null) {
                    return;
                }
                if (intent.hasExtra(INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE)) {
                    mIsAppGridVisible = intent.getBooleanExtra(
                            INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE, false);
                    mPortraitButtonStateController.setAppGridButtonSelected(mIsAppGridVisible);
                } else if (intent.hasExtra(INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE)) {
                    mIsNotificationVisible = intent.getBooleanExtra(
                            INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE, false);
                    mPortraitButtonStateController.setNotificationButtonSelected(
                            mIsNotificationVisible);
                } else if (intent.hasExtra(INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE)) {
                    mIsRecentsVisible = intent.getBooleanExtra(
                            INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE, false);
                    mPortraitButtonStateController.setRecentsButtonSelected(mIsRecentsVisible);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(REQUEST_FROM_LAUNCHER);
        context.registerReceiverForAllUsers(displayAreaVisibilityReceiver,
                filter, null, null, Context.RECEIVER_EXPORTED);
    }
}
