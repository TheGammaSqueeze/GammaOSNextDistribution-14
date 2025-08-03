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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NativeRefTest {

    private static final long VALID_REFERENCE = 100000;
    private boolean mDoReleaseCalled;
    private ReferenceManager mReferenceManager;

    @Before
    public void setup() {
        mDoReleaseCalled = false;
        mReferenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return VALID_REFERENCE;
                    }

                    @Override
                    public void doRelease(long address) {
                        mDoReleaseCalled = true;
                    }
                };
    }

    @Test
    public void setReferenceToZero_throwsError() {
        ReferenceManager referenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return NativeRef.INVALID_ADDRESS;
                    }

                    @Override
                    public void doRelease(long address) {}
                };

        Assert.assertThrows(NullPointerException.class, () -> new TestNativeRef(referenceManager));
    }

    @Test
    public void getAddress_returnsAddress() {
        NativeRef nativeRef = new TestNativeRef(mReferenceManager);

        Assert.assertEquals(nativeRef.getAddress(), VALID_REFERENCE);
    }

    @Test
    public void finalize_doReleaseCalled() throws Throwable {
        NativeRef nativeRef = new TestNativeRef(mReferenceManager);
        nativeRef.finalize();

        Assert.assertTrue(mDoReleaseCalled);
    }

    private static class TestNativeRef extends NativeRef {
        TestNativeRef(ReferenceManager referenceManager) {
            super(referenceManager);
        }
    }
}
