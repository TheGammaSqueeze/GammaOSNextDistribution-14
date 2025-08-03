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

package androidx.core.uwb.backend.impl.internal;

import static java.util.Objects.requireNonNull;

import android.os.Build.VERSION_CODES;
import android.uwb.UwbManager;

import androidx.annotation.RequiresApi;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.util.concurrent.Executor;

/** Represents a UWB ranging controlee. */
@RequiresApi(api = VERSION_CODES.S)
public class RangingControlee extends RangingDevice {

    RangingControlee(UwbManager manager, Executor executor,
            OpAsyncCallbackRunner<Boolean> opAsyncCallbackRunner, UwbFeatureFlags uwbFeatureFlags) {
        super(manager, executor, opAsyncCallbackRunner, uwbFeatureFlags);
    }

    @Override
    protected FiraOpenSessionParams getOpenSessionParams() {
        requireNonNull(mRangingParameters);
        return ConfigurationManager.createOpenSessionParams(
                FiraParams.RANGING_DEVICE_TYPE_CONTROLEE, getLocalAddress(), mRangingParameters,
                mUwbFeatureFlags);
    }

    @Override
    protected int hashSessionId(RangingParameters rangingParameters) {
        UwbAddress controllerAddress = rangingParameters.getPeerAddresses().get(0);
        return calculateHashedSessionId(controllerAddress, rangingParameters.getComplexChannel());
    }
}
