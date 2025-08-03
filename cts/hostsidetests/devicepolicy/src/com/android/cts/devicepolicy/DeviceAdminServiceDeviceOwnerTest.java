/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.devicepolicy;

import com.android.tradefed.log.LogUtil.CLog;

public final class DeviceAdminServiceDeviceOwnerTest extends BaseDeviceAdminServiceTest {

    @Override
    protected int getUserId() {
        return USER_OWNER;
    }

    @Override
    protected void installOwnerApp(String apk) throws Exception {
        CLog.i("Installing owner app %s...", apk);
        installDeviceOwnerApp(apk);
    }

    @Override
    protected void removeAdmin(String component) throws Exception {
        CLog.i("Removing admin %s...", component);
        removeDeviceOwnerAdmin(component);
    }

    @Override
    protected void setAsOwnerOrFail(String component) throws Exception {
        setDeviceOwnerOrFail(component, getUserId());
    }
}
