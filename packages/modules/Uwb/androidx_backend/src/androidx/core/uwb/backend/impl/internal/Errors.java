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

package androidx.core.uwb.backend.impl.internal;

import static android.uwb.RangingSession.Callback.REASON_BAD_PARAMETERS;
import static android.uwb.RangingSession.Callback.REASON_GENERIC_ERROR;
import static android.uwb.RangingSession.Callback.REASON_LOCAL_REQUEST;
import static android.uwb.RangingSession.Callback.REASON_MAX_RR_RETRY_REACHED;
import static android.uwb.RangingSession.Callback.REASON_MAX_SESSIONS_REACHED;
import static android.uwb.RangingSession.Callback.REASON_PROTOCOL_SPECIFIC_ERROR;
import static android.uwb.RangingSession.Callback.REASON_REMOTE_REQUEST;
import static android.uwb.RangingSession.Callback.REASON_SERVICE_CONNECTION_FAILURE;
import static android.uwb.RangingSession.Callback.REASON_SERVICE_DISCOVERY_FAILURE;
import static android.uwb.RangingSession.Callback.REASON_SE_INTERACTION_FAILURE;
import static android.uwb.RangingSession.Callback.REASON_SE_NOT_SUPPORTED;
import static android.uwb.RangingSession.Callback.REASON_SYSTEM_POLICY;

import java.util.Locale;

/** Error code to human readable string conversion */
public final class Errors {

    private Errors() {}

    /** Reason codes used in UWB session callback */
    public static final class RangingSession {

        private RangingSession() {}

        /** Convert error codes used in RangingSession callback to human readable string */
        public static String toString(int reason) {
            String msg;
            switch (reason) {
                case REASON_BAD_PARAMETERS:
                    msg = "REASON_BAD_PARAMETERS";
                    break;
                case REASON_GENERIC_ERROR:
                    msg = "REASON_GENERIC_ERROR";
                    break;
                case REASON_LOCAL_REQUEST:
                    msg = "REASON_LOCAL_REQUEST";
                    break;
                case REASON_MAX_RR_RETRY_REACHED:
                    msg = "REASON_MAX_RR_RETRY_REACHED";
                    break;
                case REASON_MAX_SESSIONS_REACHED:
                    msg = "REASON_MAX_SESSIONS_REACHED";
                    break;
                case REASON_PROTOCOL_SPECIFIC_ERROR:
                    msg = "REASON_PROTOCOL_SPECIFIC_ERROR";
                    break;
                case REASON_REMOTE_REQUEST:
                    msg = "REASON_REMOTE_REQUEST";
                    break;
                case REASON_SERVICE_CONNECTION_FAILURE:
                    msg = "REASON_SERVICE_CONNECTION_FAILURE";
                    break;
                case REASON_SERVICE_DISCOVERY_FAILURE:
                    msg = "REASON_SERVICE_DISCOVERY_FAILURE";
                    break;
                case REASON_SE_INTERACTION_FAILURE:
                    msg = "REASON_SE_INTERACTION_FAILURE";
                    break;
                case REASON_SE_NOT_SUPPORTED:
                    msg = "REASON_SE_NOT_SUPPORTED";
                    break;
                case REASON_SYSTEM_POLICY:
                    msg = "REASON_SYSTEM_POLICY";
                    break;
                default:
                    msg = "REASON_UNKNOWN";
            }
            return String.format(Locale.ENGLISH, "[%d]%s", reason, msg);
        }
    }
}
