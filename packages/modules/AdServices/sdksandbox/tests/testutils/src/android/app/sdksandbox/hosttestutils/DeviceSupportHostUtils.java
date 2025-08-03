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

package android.app.sdksandbox.hosttestutils;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PackageUtil;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

// TODO(b/322152671): deprecate for AdServicesHostSideSupportHelper
public class DeviceSupportHostUtils {
    private final BaseHostJUnit4Test mTest;

    // Used only for Go device checks, which rely on checking GMS Core and Play Store.
    public static final String GMS_CORE_PACKAGE = "com.google.android.gms";
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";

    public DeviceSupportHostUtils(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public boolean isSdkSandboxSupported() throws DeviceNotAvailableException {
        return !isWatch() && !isTv() && !isAutomotive() && !isGoDevice();
    }

    private boolean isWatch() throws DeviceNotAvailableException {
        return FeatureUtil.isWatch(mTest.getDevice());
    }

    private boolean isTv() throws DeviceNotAvailableException {
        return FeatureUtil.isTV(mTest.getDevice());
    }

    private boolean isAutomotive() throws DeviceNotAvailableException {
        return FeatureUtil.isAutomotive(mTest.getDevice());
    }

    private boolean hasGmsCore() throws DeviceNotAvailableException {
        return PackageUtil.exists(mTest.getDevice(), GMS_CORE_PACKAGE);
    }

    private boolean hasPlayStore() throws DeviceNotAvailableException {
        return PackageUtil.exists(mTest.getDevice(), PLAY_STORE_PACKAGE);
    }

    // Taken from vendor/xts/common/host-side/util/src/com/android/xts/common/util/GmsUtil.java
    private boolean isGoDevice() throws DeviceNotAvailableException {
        ITestDevice device = mTest.getDevice();
        return FeatureUtil.isLowRam(device)
                && hasGmsCore()
                && hasPlayStore()
                && !isWatch()
                && !isAutomotive()
                && !isTv();
    }
}
