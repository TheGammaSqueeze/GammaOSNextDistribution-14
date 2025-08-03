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

package com.android.adservices.data.adselection.datahandlers;

import android.annotation.NonNull;
import android.net.Uri;

import com.google.auto.value.AutoValue;

/**
 * Data class representing the interaction event reporting data associated with an ad selection run.
 */
@AutoValue
public abstract class RegisteredAdInteraction {

    /** Interaction key associated with the event being reported. */
    @NonNull
    public abstract String getInteractionKey();

    /** Reporting Uri associated with the event being reported. */
    @NonNull
    public abstract Uri getInteractionReportingUri();

    /** Returns Builder for {@link RegisteredAdInteraction}. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_RegisteredAdInteraction.Builder();
    }

    /** Builder for RegisteredAdInteraction. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the interaction key associated with the event being reported. */
        public abstract Builder setInteractionKey(@NonNull String interactionKey);

        /** Sets the reporting Uri associated with the event being reported. */
        public abstract Builder setInteractionReportingUri(@NonNull Uri interactionReportingUri);

        /** Builds a {@link RegisteredAdInteraction}. */
        @NonNull
        public abstract RegisteredAdInteraction build();
    }
}
