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

import com.android.internal.annotations.VisibleForTesting;

/** Holds the reference to a HKDF message digest function */
public class HkdfMessageDigestNativeRef extends NativeRef {
    private HkdfMessageDigestNativeRef(ReferenceManager referenceManager) {
        super(referenceManager);
    }

    /** Returns a reference to the HKDF_SHA256 message digest (i.e., SHA256) */
    public static HkdfMessageDigestNativeRef getHkdfSha256MessageDigestReference() {
        return getHkdfSha256MessageDigestReference(OhttpJniWrapper.getInstance());
    }

    @VisibleForTesting
    static HkdfMessageDigestNativeRef getHkdfSha256MessageDigestReference(
            IOhttpJniWrapper ohttpJniWrapper) {
        ReferenceManager referenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return ohttpJniWrapper.hkdfSha256MessageDigest();
                    }

                    @Override
                    public void doRelease(long address) {
                        // do nothing;
                    }
                };
        return new HkdfMessageDigestNativeRef(referenceManager);
    }
}
