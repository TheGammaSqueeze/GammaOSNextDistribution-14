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

package android.car.cts.powerpolicy;

import java.util.Objects;

public class ComponentPowerStateInfo {
    private final String mComponentName;
    private final boolean mPowerState;

    protected ComponentPowerStateInfo(String componentName, boolean powerState) {
        mComponentName = componentName;
        mPowerState = powerState;
    }

    public String getComponentName() {
        return mComponentName;
    }

    public boolean isPowerOn() {
        return mPowerState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentPowerStateInfo that = (ComponentPowerStateInfo) o;
        return (mPowerState == that.mPowerState)
                && mComponentName.equals(that.mComponentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPowerState, mComponentName);
    }
}
