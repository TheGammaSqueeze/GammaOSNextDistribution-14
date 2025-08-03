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

package com.android.bedstead.testapp;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.ReceiverInfo;
import com.android.queryable.info.ServiceInfo;

import java.util.HashSet;
import java.util.Set;

/** Details about a queryable test app. */
class TestAppDetails {
    TestappProtos.AndroidApp mApp;
    int mResourceIdentifier;
    final Bundle mMetadata = new Bundle();
    final Set<String> mPermissions = new HashSet<>();
    final Set<ActivityInfo> mActivities = new HashSet<>();
    final Set<ServiceInfo> mServices = new HashSet<>();
    final Set<ReceiverInfo> mReceivers = new HashSet<>();

    /**
     * Gets the shared user ID of the test app, or {@code Null} if none.
     */
    @Nullable
    public String sharedUserId() {
        if (mApp.getSharedUserId().isEmpty()) {
            return null;
        }

        return mApp.getSharedUserId();
    }

    /**
     * Gets the label of the test app, or {@code Null} if none.
     */
    @Nullable
    public String label() {
        if (mApp.getLabel().isEmpty()) {
            return null;
        }

        return mApp.getLabel();
    }

    public boolean crossProfile() {
        return mApp.getCrossProfile();
    }

    @Override
    public String toString() {
        return "TestAppDetails{"
                + "mApp=" + mApp
                + ", mResourceIdentifier=" + mResourceIdentifier
                + ", mMetadata=" + mMetadata
                + ", mPermissions=" + mPermissions
                + ", mActivities=" + mActivities
                + ", mServices=" + mServices
                + ", mReceivers=" + mReceivers
                + '}';
    }
}
