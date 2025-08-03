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

import android.net.Uri;

import com.google.common.util.concurrent.FluentFuture;

/** Interface which provides methods to encrypt and decrypt bytes using OHTTP. */
public interface ObliviousHttpEncryptor {

    /** Encrypts the given bytes. */
    FluentFuture<byte[]> encryptBytes(
            byte[] plainText, long contextId, long keyFetchTimeoutMs, Uri coordinator);

    /** Decrypt given bytes. */
    byte[] decryptBytes(byte[] encryptedBytes, long storedContextId);
}
