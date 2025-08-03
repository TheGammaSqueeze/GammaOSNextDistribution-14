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

/**
 * Holds the 'info' field as required by HPKE setupBaseS operation according to OHTTP spec
 *
 * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#name-hpke-encapsulation
 */
@AutoValue
public abstract class RecipientKeyInfo {
    /** Get the bytes of the recipient key info */
    @SuppressWarnings("mutable")
    public abstract byte[] getBytes();

    /** Create a {@link RecipientKeyInfo} object with the given bytes */
    public static RecipientKeyInfo create(byte[] info) {
        return new AutoValue_RecipientKeyInfo(info);
    }
}
