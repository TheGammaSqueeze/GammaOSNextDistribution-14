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

package com.android.adservices.ohttp;

import com.google.auto.value.AutoValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Contains the results of Ohttp encryption and the request context required for decryption */
@AutoValue
public abstract class ObliviousHttpRequest {

    @SuppressWarnings("mutable")
    abstract byte[] plainText();

    @SuppressWarnings("mutable")
    abstract byte[] cipherText();

    /** Returns the Oblivious HTTP request context that should be saved for decryption */
    public abstract ObliviousHttpRequestContext requestContext();

    /** Create a Oblivious HTTP Request object */
    public static ObliviousHttpRequest create(
            byte[] plainText, byte[] cipherText, ObliviousHttpRequestContext requestContext) {
        return new AutoValue_ObliviousHttpRequest(plainText, cipherText, requestContext);
    }

    /** Get the plain text that is encrypted */
    public byte[] getPlainText() {
        return Arrays.copyOf(plainText(), plainText().length);
    }

    /** Get the encrypted cipher text */
    public byte[] getCipherText() {
        return Arrays.copyOf(cipherText(), cipherText().length);
    }

    /**
     * Serialize according to OHTTP spec
     *
     * <p>concat(hdr, enc, ct) per
     * https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#name-encapsulation-of-requests
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(requestContext().keyConfig().serializeOhttpPayloadHeader());
        outputStream.write(requestContext().encapsulatedSharedSecret().getBytes());
        outputStream.write(cipherText());
        return outputStream.toByteArray();
    }
}
