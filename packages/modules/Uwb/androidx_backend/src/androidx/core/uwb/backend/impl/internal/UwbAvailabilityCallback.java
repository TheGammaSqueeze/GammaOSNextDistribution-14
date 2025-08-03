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

package androidx.core.uwb.backend.impl.internal;

import androidx.annotation.IntDef;

/** Callback for UWB availability change events. */
public interface UwbAvailabilityCallback {
    void onUwbAvailabilityChanged(boolean isUwbAvailable, int reason);

    /** Reason why UWB state changed */
    @IntDef({
            /* The state has changed because of an unknown reason */
            REASON_UNKNOWN,

            /* The state has changed because UWB is turned on/off */
            REASON_SYSTEM_POLICY,

            /*
             * The state has changed either because no country code has been configured or due to
             *  UWB being
             * unavailable as a result of regulatory constraints.
             */
            REASON_COUNTRY_CODE_ERROR,
    })
    @interface UwbStateChangeReason {
    }

    int REASON_UNKNOWN = 0;
    int REASON_SYSTEM_POLICY = 1;
    int REASON_COUNTRY_CODE_ERROR = 2;
}

