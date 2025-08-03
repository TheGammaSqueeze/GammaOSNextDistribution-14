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

package com.android.bedstead.remotedpc;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;

/**
 * Version of {@link RemoteDpc} which returns the result of calling
 * {@link DevicePolicyManager#getParentProfileInstance(ComponentName)} when calling
 * {@link #devicePolicyManager()}.
 */
public final class RemoteDpcUsingParentInstance extends RemoteDpc {

    public RemoteDpcUsingParentInstance(RemoteDpc remoteDpc) {
        super(remoteDpc.testApp(), remoteDpc.devicePolicyController());
    }

    @Override
    public RemoteDevicePolicyManager devicePolicyManager() {
        return super.devicePolicyManager().getParentProfileInstance(componentName());
    }

    @Override
    public boolean isParentInstance() {
        return true;
    }
}
