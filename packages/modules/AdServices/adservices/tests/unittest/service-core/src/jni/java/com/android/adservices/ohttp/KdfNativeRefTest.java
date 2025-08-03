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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class KdfNativeRefTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private IOhttpJniWrapper mOhttpJniWrapper;
    private static final long TEST_ADDRESS = 10000;

    @Before
    public void setup() throws Throwable {
        when(mOhttpJniWrapper.hpkeKdfHkdfSha256()).thenReturn(TEST_ADDRESS);
    }

    @Test
    public void getKdftReference_callsCorrectJniMethod() throws Throwable {
        KdfNativeRef.getHpkeKdfHkdfSha256Reference(mOhttpJniWrapper);

        verify(mOhttpJniWrapper).hpkeKdfHkdfSha256();
    }

    @Test
    public void getAddress_returnsCorrectAddress() throws Throwable {
        KdfNativeRef ref = KdfNativeRef.getHpkeKdfHkdfSha256Reference(mOhttpJniWrapper);

        Assert.assertEquals(ref.getAddress(), TEST_ADDRESS);
    }

    @Test
    public void finalize_noJniMethodCalled() throws Throwable {
        KdfNativeRef ref = KdfNativeRef.getHpkeKdfHkdfSha256Reference(mOhttpJniWrapper);
        ref.finalize();

        verify(mOhttpJniWrapper).hpkeKdfHkdfSha256();
        verifyNoMoreInteractions(mOhttpJniWrapper);
    }
}
