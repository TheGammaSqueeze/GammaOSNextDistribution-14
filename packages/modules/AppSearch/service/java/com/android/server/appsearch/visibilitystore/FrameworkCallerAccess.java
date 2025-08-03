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

package com.android.server.appsearch.visibilitystore;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.aidl.AppSearchAttributionSource;
import android.content.AttributionSource;

import com.android.server.appsearch.external.localstorage.visibilitystore.CallerAccess;

import java.util.Objects;

/**
 * Contains attributes of an API caller relevant to its access via visibility store.
 *
 * @hide
 */
public class FrameworkCallerAccess extends CallerAccess {
    private final AppSearchAttributionSource mAttributionSource;
    private final boolean mCallerHasSystemAccess;
    // A caller requiring enterprise access will not have default access to its own package schemas
    // or to package schemas visible to system. The caller will only have access to the enterprise
    // schemas for which it passes the required permissions checks.
    private final boolean mIsForEnterprise;

    /**
     * Constructs a new {@link CallerAccess}.
     *
     * @param callerAttributionSource The permission identity of the caller
     * @param callerHasSystemAccess Whether {@code callingPackageName} has access to schema types
     *     marked visible to system via {@link
     *     android.app.appsearch.SetSchemaRequest.Builder#setSchemaTypeDisplayedBySystem}.
     * @param isForEnterprise Whether the caller requires enterprise access.
     */
    public FrameworkCallerAccess(
            @NonNull AppSearchAttributionSource callerAttributionSource,
            boolean callerHasSystemAccess, boolean isForEnterprise) {
        super(Objects.requireNonNull(callerAttributionSource.getPackageName()));
        mAttributionSource = callerAttributionSource;
        mCallerHasSystemAccess = callerHasSystemAccess;
        mIsForEnterprise = isForEnterprise;
    }

    /** Returns the permission identity {@link AttributionSource} of the caller. */
    @NonNull
    public AppSearchAttributionSource getCallingAttributionSource() {
        return mAttributionSource;
    }

    /**
     * Returns whether {@code callingPackageName} has access to schema types marked visible to
     * system via {@link
     * android.app.appsearch.SetSchemaRequest.Builder#setSchemaTypeDisplayedBySystem}.
     */
    public boolean doesCallerHaveSystemAccess() {
        return mCallerHasSystemAccess;
    }

    /** Returns whether the caller requires enterprise access. */
    public boolean isForEnterprise() {
        return mIsForEnterprise;
    }

    @Override
    public boolean doesCallerHaveSelfAccess() {
        // Don't check self access if we need to check enterprise access, since the caller will not
        // have access to data in its own package if it doesn't pass the enterprise checks
        return !mIsForEnterprise;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameworkCallerAccess)) return false;
        FrameworkCallerAccess that = (FrameworkCallerAccess) o;
        return super.equals(o)
                && mCallerHasSystemAccess == that.mCallerHasSystemAccess
                && Objects.equals(mAttributionSource, that.mAttributionSource)
                && mIsForEnterprise == that.mIsForEnterprise;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mAttributionSource, mCallerHasSystemAccess,
                mIsForEnterprise);
    }
}
