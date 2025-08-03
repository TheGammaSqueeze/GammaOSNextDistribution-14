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

package com.android.systemui.car.qc;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.CallSuper;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

public class QCFooterViewController extends CarSystemBarElementController<QCFooterView> {
    private static final String TAG = QCFooterButtonController.class.getSimpleName();

    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener mListener =
            carUxRestrictions -> mView.setEnabled(
                    !carUxRestrictions.isRequiresDistractionOptimization());

    @AssistedInject
    protected QCFooterViewController(@Assisted QCFooterView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController, Context context,
            UserTracker userTracker) {
        super(view, disableController, stateController);
        mContext = context;
        mUserTracker = userTracker;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<QCFooterView, QCFooterViewController> {}

    @Override
    protected void onInit() {
        Intent intent = mView.getOnClickIntent();
        if (intent == null) return;
        mView.setOnClickListener(v -> {
            mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                    mUserTracker.getUserHandle());
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(mContext.getDisplayId());
                mContext.startActivityAsUser(intent, options.toBundle(),
                        mUserTracker.getUserHandle());
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch intent", e);
            }
        });
    }

    @Override
    @CallSuper
    protected void onViewAttached() {
        super.onViewAttached();
        if (mView.isDisableWhileDriving()) {
            CarUxRestrictionsUtil.getInstance(mContext).register(mListener);
        }
    }

    @Override
    @CallSuper
    protected void onViewDetached() {
        super.onViewDetached();
        if (mView.isDisableWhileDriving()) {
            CarUxRestrictionsUtil.getInstance(mContext).unregister(mListener);
        }
    }
}
