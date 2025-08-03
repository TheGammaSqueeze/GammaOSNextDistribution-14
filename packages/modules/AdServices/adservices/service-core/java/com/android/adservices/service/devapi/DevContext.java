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

package com.android.adservices.service.devapi;

import android.annotation.Nullable;

import com.google.auto.value.AutoValue;

/**
 * Instances of this class are used to hold information required by the developer features supported
 * by the AdSelection and CustomAudience API. An instance of this class is created while serving
 * each client call and will be used by underlying services to understand if they need to activate
 * development features and to provide information useful for those features.
 */
@AutoValue
public abstract class DevContext {
    /**
     * @return true if the developer options are enabled for this service call.
     */
    public abstract boolean getDevOptionsEnabled();

    /**
     * @return The package name for the calling app or NULL if the dev options are not enabled.
     */
    @Nullable
    public abstract String getCallingAppPackageName();

    /**
     * @return Generic builder
     */
    public static DevContext.Builder builder() {
        return new AutoValue_DevContext.Builder();
    }

    /**
     * @return An instance of {@link DevContext} with developer options disabled.
     */
    public static DevContext createForDevOptionsDisabled() {
        return DevContext.builder().setDevOptionsEnabled(false).build();
    }

    /** The Builder for {@link DevContext} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the value for the dev options enabled flag */
        public abstract DevContext.Builder setDevOptionsEnabled(boolean flag);

        /** Sets the value for the calling app package */
        public abstract DevContext.Builder setCallingAppPackageName(@Nullable String value);

        /** Build an AdBiddingOutcome object. */
        public abstract DevContext build();
    }
}
