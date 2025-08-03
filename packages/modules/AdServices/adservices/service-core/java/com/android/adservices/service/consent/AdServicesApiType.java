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

import android.app.adservices.consent.ConsentParcel;

import com.android.internal.annotations.VisibleForTesting;

/** Represents a PP API type. */
public enum AdServicesApiType {
    TOPICS,
    FLEDGE,
    MEASUREMENTS,
    ALL_API,
    UNKNOWN;

    // to support scenario when GA UX is on but the consent is server from PP API
    @VisibleForTesting static final String CONSENT_ALL = "CONSENT";

    @VisibleForTesting static final String CONSENT_ALL_APPSEARCH = "CONSENT-ALL";
    @VisibleForTesting static final String CONSENT_FLEDGE = "CONSENT-FLEDGE";
    @VisibleForTesting static final String CONSENT_MEASUREMENT = "CONSENT-MEASUREMENT";
    @VisibleForTesting static final String CONSENT_TOPICS = "CONSENT-TOPICS";


    /** Maps the {@link AdServicesApiType} to Consent API type integer. */
    public int toConsentApiType() {
        switch (this) {
            case TOPICS:
                return ConsentParcel.TOPICS;
            case FLEDGE:
                return ConsentParcel.FLEDGE;
            case MEASUREMENTS:
                return ConsentParcel.MEASUREMENT;
            case ALL_API:
                return ConsentParcel.ALL_API;
            default:
                return ConsentParcel.UNKNOWN;
        }
    }

    /**
     * Maps the {@link AdServicesApiType} to {@link String} which represents the key in the PP API
     * datastore.
     *
     * @return key which can be used to retrieved data for {@link AdServicesApiType} from PP API
     *     datastore.
     */
    public String toPpApiDatastoreKey() {
        switch (this) {
            case TOPICS:
                return CONSENT_TOPICS;
            case FLEDGE:
                return CONSENT_FLEDGE;
            case MEASUREMENTS:
                return CONSENT_MEASUREMENT;
            case ALL_API:
                return CONSENT_ALL;
            default:
                throw new IllegalStateException("AdServicesApiType doesn't exist.");
        }
    }

    /** Maps the {@link AdServicesApiType} to AppSearch storage Key. */
    public String toAppSearchKey() {
        switch (this) {
            case TOPICS:
                return CONSENT_TOPICS;
            case FLEDGE:
                return CONSENT_FLEDGE;
            case MEASUREMENTS:
                return CONSENT_MEASUREMENT;
            case ALL_API:
                return CONSENT_ALL_APPSEARCH;
            default:
                throw new IllegalStateException("AdServicesApiType doesn't exist.");
        }
    }

    /** Gets AdserviceApiType from datastore key. */
    public static AdServicesApiType fromPpApiDatastoreKey(String ppApiDatastoreKey) {
        for (AdServicesApiType apiType : AdServicesApiType.values()) {
            if (apiType.toPpApiDatastoreKey().equals(ppApiDatastoreKey)) {
                return apiType;
            }
        }
        return UNKNOWN;
    }
}
