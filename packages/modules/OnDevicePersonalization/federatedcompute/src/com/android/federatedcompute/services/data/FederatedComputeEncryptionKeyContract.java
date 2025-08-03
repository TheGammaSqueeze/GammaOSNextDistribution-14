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

package com.android.federatedcompute.services.data;

public final class FederatedComputeEncryptionKeyContract {
    public static final String ENCRYPTION_KEY_TABLE = "encryption_keys";

    private FederatedComputeEncryptionKeyContract() {}

    public static final class FederatedComputeEncryptionColumns {
        private FederatedComputeEncryptionColumns() {}

        /**
         * A unique identifier of the key, in thd form of UUID. FCP server uses key_identifier to
         * get private key.
         */
        public static final String KEY_IDENTIFIER = "key_identifier";

        /** The public key base64 encoded. */
        public static final String PUBLIC_KEY = "public_key";

        /**
         * The type of the key in @link {com.android.federatedcompute.services.data.fbs.KeyType}
         * Currently only encryption key is allowed.
         */
        public static final String KEY_TYPE = "key_type";

        /** Creation instant of the key in the database in milliseconds. */
        public static final String CREATION_TIME = "creation_time";

        /** Expiry time of the key in milliseconds. */
        public static final String EXPIRY_TIME = "expiry_time";
    }
}
