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
package com.android.car.settings.enterprise;

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.enterprise.CallbackTranslator.Callback;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;

/**
 * Base class for async controllers.
 */
abstract class BaseAsyncPreferenceController<P extends Preference, R,
        T extends CallbackTranslator<R>> extends BaseEnterprisePrivacyPreferenceController<P> {

    private R mResult;

    @Nullable
    private T mCallbackTranslator;

    @Nullable
    private Integer mAvailabilityStatus;

    protected BaseAsyncPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions,
            @Nullable ApplicationFeatureProvider applicationFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                /* enterprisePrivacyFeatureProvider= */ null, applicationFeatureProvider);
    }

    /**
     * Calls the method that will lazy-load the counter.
     */
    protected abstract void lazyLoad(T callbackHolder);

    /**
     * Creates a translator between this class Callback and {@code SettingsLib}.
     * @param callback
     * @return
     */
    protected abstract T newCallbackTranslator(Callback<R> callback);

    /**
     * Checks if the result of the callback should make the controller available.
     */
    protected abstract boolean isAvailable(R result);

    /**
     * Gets the result returned by the callback.
     */
    protected R getResult() {
        return mResult;
    }

    @Override
    protected final int getDefaultAvailabilityStatus() {
        if (mAvailabilityStatus != null) {
            // Already calculated
            mLogger.d("getAvailabilityStatus(): returning cached result " + mAvailabilityStatus);
            return mAvailabilityStatus;
        }

        if (mCallbackTranslator != null) {
            mLogger.d("getAvailabilityStatus(): already waiting for callback...");
        } else {
            mLogger.d("getAvailabilityStatus(): lazy-loading number of apps");
            mCallbackTranslator = newCallbackTranslator((result) -> onLazyLoaded(result));
            lazyLoad(mCallbackTranslator);
        }

        // Calculating the number of apps can takes a bit of time, so we always return
        // CONDITIONALLY_UNAVAILABLE, so the actual visibility will be set when the result is called
        // back (on onLazyLoaded()).
        return CONDITIONALLY_UNAVAILABLE;
    }

    private void onLazyLoaded(R result) {
        mResult = result;
        boolean available = isAvailable(result);
        mAvailabilityStatus = available
                ? AVAILABLE
                : DISABLED_FOR_PROFILE;
        mLogger.d("onLazyLoaded(): result=" + result + ", available=" + available);

        if (available)  {
            refreshUi();
        }
    }
}
