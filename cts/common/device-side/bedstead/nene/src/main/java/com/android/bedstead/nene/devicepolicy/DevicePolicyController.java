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

package com.android.bedstead.nene.devicepolicy;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;

import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;

import java.util.Objects;

/**
 * A reference to either a Device Owner or a Profile Owner.
 */
public abstract class DevicePolicyController implements AutoCloseable {

    protected final UserReference mUser;
    protected final Package mPackage;
    protected final ComponentName mComponentName;

    DevicePolicyController(UserReference user, Package pkg, ComponentName componentName) {
        if (user == null || pkg == null || componentName == null) {
            throw new NullPointerException();
        }

        mUser = user;
        mPackage = pkg;
        mComponentName = componentName;
    }

    /**
     * Get the {@link UserReference} which this device policy controller is installed into.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * Get the {@link Package} of the device policy controller.
     */
    public Package pkg() {
        return mPackage;
    }

    /**
     * Get the {@link ComponentName} of the {@link DeviceAdminReceiver} for this device policy
     * controller.
     */
    public ComponentName componentName() {
        return mComponentName;
    }

    /**
     * Remove this device policy controller.
     */
    public abstract void remove();

    @Override
    public int hashCode() {
        return Objects.hashCode(mUser)
                + Objects.hashCode(mPackage)
                + Objects.hashCode(mComponentName);
    }

    /** See {@link #remove}. */
    @Override
    public void close() {
        remove();
    }
}
