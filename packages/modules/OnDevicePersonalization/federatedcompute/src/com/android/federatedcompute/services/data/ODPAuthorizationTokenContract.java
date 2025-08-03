/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.data;

public final class ODPAuthorizationTokenContract {
    public static final String ODP_AUTHORIZATION_TOKEN_TABLE = "odp_authorization_tokens";

    private ODPAuthorizationTokenContract() {}

    public static final class ODPAuthorizationTokenColumns {
        private ODPAuthorizationTokenColumns() {}

        /**
         * An identifier for different ODP adopters (e.g, server address, calling package name,
         * etc).
         */
        public static final String OWNER_IDENTIFIER = "owner_identifier";

        /**
         * The authorization token received from the server.
         */
        public static final String AUTHORIZATION_TOKEN = "authorization_token";

        /** Create time of the authorization token in the database in milliseconds. */
        public static final String CREATION_TIME = "creation_time";

        /** Expiry time of the authorization token in milliseconds. */
        public static final String EXPIRY_TIME = "expiry_time";
    }
}
