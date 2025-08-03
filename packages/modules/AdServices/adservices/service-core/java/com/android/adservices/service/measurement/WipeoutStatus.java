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

package com.android.adservices.service.measurement;

/** POJO for storing wipeout status */
public class WipeoutStatus {
    /** Enums are tied to the AdservicesMeasurementWipeoutStatus atom */
    public enum WipeoutType {
        UNKNOWN(0),
        UNINSTALL(1),
        CONSENT_FLIP(2),
        CLEAR_DATA(3),
        DELETE_REGISTRATIONS_API(4),
        PACKAGE_CHANGED_WIPEOUT_CAUSE(5), // deprecated in favor of DELETE_REGISTRATIONS_API
        ROLLBACK_WIPEOUT_CAUSE(6);

        private final int mValue;

        WipeoutType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    private WipeoutType mWipeoutType;

    public WipeoutStatus() {
        mWipeoutType = WipeoutType.UNKNOWN;
    }

    /** Get the type of wipeout that occurred. */
    public WipeoutType getWipeoutType() {
        return mWipeoutType;
    }

    /** Set the type of wipeout that occurred. */
    public void setWipeoutType(WipeoutType type) {
        mWipeoutType = type;
    }
}
