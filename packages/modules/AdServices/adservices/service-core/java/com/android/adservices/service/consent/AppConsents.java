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

package com.android.adservices.service.consent;

import com.google.auto.value.AutoValue;

/***
 * Stores the opt-in/out value of each app during consent migration.
 */
@AutoValue
public abstract class AppConsents {
    /**
     * @return opt-in/out value for measurement app
     */
    public abstract boolean getMsmtConsent();

    /**
     * @return opt-in/out value for topics app
     */
    public abstract boolean getTopicsConsent();

    /**
     * @return opt-in/out value for fledge app
     */
    public abstract boolean getFledgeConsent();

    /**
     * @return generic builder.
     */
    public static AppConsents.Builder builder() {
        return new AutoValue_AppConsents.Builder();
    }

    /** Builder class for {@link AppConsents}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set opt-in/out value for measurement app */
        public abstract AppConsents.Builder setMsmtConsent(boolean value);

        /** Set opt-in/out value for topics app */
        public abstract AppConsents.Builder setTopicsConsent(boolean value);

        /** Set opt-in/out value for fledge app */
        public abstract AppConsents.Builder setFledgeConsent(boolean value);

        /** build for {@link AppConsents}. */
        public abstract AppConsents build();
    }
}
