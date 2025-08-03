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

package com.android.adservices.service.adselection.encryption;

import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import java.security.spec.InvalidKeySpecException;
import java.util.List;

/** Interface to parse encryption key. */
public interface EncryptionKeyParser {

    /** Parses the storage encryption key into the wire encryption key. */
    AdSelectionEncryptionKey parseDbEncryptionKey(DBEncryptionKey dbEncryptionKey);

    /** Parses the HTTP response from key fetch server into the storage encryption key. */
    List<DBEncryptionKey> getDbEncryptionKeys(AdServicesHttpClientResponse response);

    /**
     * Parses the AdSelectionEncryptionKey into {@link
     * com.android.adservices.ohttp.ObliviousHttpKeyConfig}.
     */
    ObliviousHttpKeyConfig getObliviousHttpKeyConfig(AdSelectionEncryptionKey key)
            throws InvalidKeySpecException;
}
