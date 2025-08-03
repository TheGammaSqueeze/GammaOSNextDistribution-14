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

/**
 * Used to hold onto native OpenSSL references and run finalization on those objects using {@link
 * ReferenceManager}
 *
 * <p>Warning: This class is only suitable to hold references for non-critical native resources. If
 * the resource being referenced needs timely (eg: can not wait until finalize is called)
 * termination, then this is not a suitable class for it.
 *
 * <p>For such critical resources, try AutoCloseable or other patterns to ensure explicit
 * termination
 */
abstract class NativeRef {
    @VisibleForTesting static final long INVALID_ADDRESS = 0;

    private final long mAddress;
    private final ReferenceManager mReferenceManager;

    NativeRef(ReferenceManager referenceManager) {
        this.mReferenceManager = referenceManager;
        long address = referenceManager.getOrCreate();
        if (address == INVALID_ADDRESS) {
            throw new NullPointerException("address is invalid");
        }
        this.mAddress = address;
    }

    public long getAddress() {
        return mAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NativeRef)) {
            return false;
        }
        return ((NativeRef) o).mAddress == mAddress;
    }

    @Override
    public int hashCode() {
        return (int) (mAddress ^ (mAddress >>> 32));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() throws Throwable {
        try {
            if (mAddress != INVALID_ADDRESS) {
                mReferenceManager.doRelease(mAddress);
            }
        } finally {
            super.finalize();
        }
    }
}
