/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.ipsec.test.ike.ike3gpp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;

import org.junit.Before;
import org.junit.Test;

public class Ike3gppExtensionTest {
    private static final byte PDU_SESSION_ID = (byte) 0x01;

    private Ike3gppDataListener mMockIke3gppDataListener;
    private Ike3gppParams mIke3gppParams;

    @Before
    public void setUp() {
        mMockIke3gppDataListener = mock(Ike3gppDataListener.class);
        mIke3gppParams = new Ike3gppParams.Builder().build();
    }

    @Test
    public void testIke3gppExtensionConstructor() {
        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(mIke3gppParams, mMockIke3gppDataListener);

        assertEquals(mMockIke3gppDataListener, ike3gppExtension.getIke3gppDataListener());
        assertEquals(mIke3gppParams, ike3gppExtension.getIke3gppParams());
    }

    @Test(expected = NullPointerException.class)
    public void testIke3gppExtensionConstructorInvalidCallback() {
        Ike3gppExtension ike3gppExtension = new Ike3gppExtension(mIke3gppParams, null);
    }

    @Test(expected = NullPointerException.class)
    public void testIke3gppExtensionConstructorInvalidParams() {
        Ike3gppExtension ike3gppExtension = new Ike3gppExtension(null, mMockIke3gppDataListener);
    }

    @Test
    public void testEquals() {
        Ike3gppExtension extensionA =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build(),
                        mMockIke3gppDataListener);

        Ike3gppExtension extensionB =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build(),
                        mMockIke3gppDataListener);

        assertEquals(extensionA, extensionB);
    }

    @Test
    public void testNotEquals() {
        Ike3gppExtension extensionA =
                new Ike3gppExtension(new Ike3gppParams.Builder().build(), mMockIke3gppDataListener);

        Ike3gppExtension extensionB =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build(),
                        mMockIke3gppDataListener);

        assertNotEquals(extensionA, extensionB);
    }
}
