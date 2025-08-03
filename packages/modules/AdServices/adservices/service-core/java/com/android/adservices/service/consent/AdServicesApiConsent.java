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

package com.android.adservices.service.consent;

/** Represents a consent. */
public class AdServicesApiConsent {
    /**
     * Provides given consent as {@link AdServicesApiConsent}.
     */
    public static final AdServicesApiConsent GIVEN = new AdServicesApiConsent(true);
    /**
     * Provides revoked consent as {@link AdServicesApiConsent}.
     */
    public static final AdServicesApiConsent REVOKED = new AdServicesApiConsent(false);
    private final boolean mGiven;

    AdServicesApiConsent(boolean given) {
        this.mGiven = given;
    }

    /**
     * Provides {@link AdServicesApiConsent}.
     *
     * @param isGiven value based on which {@link AdServicesApiConsent} is instantiated.
     * @return {@link AdServicesApiConsent} instantiated based on provided parameter.
     */
    public static AdServicesApiConsent getConsent(Boolean isGiven) {
        if (isGiven == null) {
            return REVOKED;
        }
        return new AdServicesApiConsent(isGiven);
    }

    public boolean isGiven() {
        return mGiven;
    }
}
