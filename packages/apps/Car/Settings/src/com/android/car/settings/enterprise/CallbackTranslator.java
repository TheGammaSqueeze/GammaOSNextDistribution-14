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

import com.android.car.settingslib.applications.ApplicationFeatureProvider.ListOfAppsCallback;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.NumberOfAppsCallback;
import com.android.car.settingslib.applications.UserAppInfo;

import java.util.List;

/**
 * Intermediate object that keeps the "real" callback used by the {@code SettingsLib} APIs.
 *
 * <p>This is needed because they use different classes (like and ) that are not related.
 *
 * @param <R> callback result type
 */
// TODO(b/208511815): this clas is only needed because NumberOfAppsCallback and ListOfAppsCallback
// are not related - ideally, they should extend a common interface instead
abstract class CallbackTranslator<R> {

    public final Callback<R> callback;

    CallbackTranslator(Callback<R> callback) {
        this.callback = callback;
    }

    interface Callback<R> {
        void onResult(R result);
    }

    static final class AppsCounterCallbackTranslator extends CallbackTranslator<Integer>
            implements NumberOfAppsCallback {

        AppsCounterCallbackTranslator(Callback<Integer> callback) {
            super(callback);
        }

        @Override
        public void onNumberOfAppsResult(int num) {
            callback.onResult(num);
        }
    }

    static final class AppsListCallbackTranslator extends CallbackTranslator<List<UserAppInfo>>
            implements ListOfAppsCallback {

        AppsListCallbackTranslator(Callback<List<UserAppInfo>> callback) {
            super(callback);
        }

        @Override
        public void onListOfAppsResult(List<UserAppInfo> result) {
            callback.onResult(result);
        }
    }
}
