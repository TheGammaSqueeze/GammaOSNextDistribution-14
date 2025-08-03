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

package com.android.bedstead.remotedpc;

import android.content.ComponentName;

import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;

import org.jetbrains.annotations.Nullable;

/** {@link RemotePolicyManager} which represents an app sets as the DevicePolicyManagerRoleHolder */
public final class RemoteDevicePolicyManagerRoleHolder extends RemotePolicyManager {

    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    public static final TestApp sTestApp = sTestAppProvider.query()
            .wherePackageName().isEqualTo("com.android.DevicePolicyManagerRoleHolder")
            .get();

    public RemoteDevicePolicyManagerRoleHolder(TestApp testApp, UserReference user) {
        super(testApp, user);
    }

    @Nullable
    @Override
    public ComponentName componentName() {
        return null; // The role holder doesn't provide a component name
    }
}
