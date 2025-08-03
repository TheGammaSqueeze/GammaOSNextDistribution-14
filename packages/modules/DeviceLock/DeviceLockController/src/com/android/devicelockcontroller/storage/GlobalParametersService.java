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

package com.android.devicelockcontroller.storage;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * A class exposing Global Parameters as a service.
 */
public final class GlobalParametersService extends Service {
    private static final String TAG = "GlobalParametersService";

    private Context mContext;

    private final IGlobalParametersService.Stub mBinder =
            new IGlobalParametersService.Stub() {
                @Override
                public void clear() {
                    GlobalParameters.clear(mContext);
                }

                @Override
                public void dump() {
                    GlobalParameters.dump(mContext);
                }

                @Override
                public boolean isProvisionReady() {
                    return GlobalParameters.isProvisionReady(mContext);
                }

                @Override
                public void setProvisionReady(boolean isProvisionReady) {
                    GlobalParameters.setProvisionReady(mContext, isProvisionReady);
                }

                @Override
                public String getRegisteredDeviceId() {
                    return GlobalParameters.getRegisteredDeviceId(mContext);
                }

                @Override
                public void setRegisteredDeviceId(String registeredDeviceId) {
                    GlobalParameters.setRegisteredDeviceId(mContext, registeredDeviceId);
                }

                @Override
                public boolean isProvisionForced() {
                    return GlobalParameters.isProvisionForced(mContext);
                }

                @Override
                public void setProvisionForced(boolean isForced) {
                    GlobalParameters.setProvisionForced(mContext, isForced);
                }

                @Override
                public int getDeviceState() {
                    return GlobalParameters.getDeviceState(mContext);
                }

                @Override
                public void setDeviceState(@DeviceState int state) {
                    GlobalParameters.setDeviceState(mContext, state);
                }

                @Override
                public @FinalizationState int getFinalizationState() {
                    return GlobalParameters.getFinalizationState(mContext);
                }

                @Override
                public void setFinalizationState(@FinalizationState int state) {
                    GlobalParameters.setFinalizationState(mContext, state);
                }

                @Override
                @DeviceProvisionState
                public int getLastReceivedProvisionState() {
                    return GlobalParameters.getLastReceivedProvisionState(mContext);
                }

                @Override
                public void setLastReceivedProvisionState(
                        @DeviceProvisionState int provisionState) {
                    GlobalParameters.setLastReceivedProvisionState(mContext, provisionState);
                }
            };

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");
        mContext = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
