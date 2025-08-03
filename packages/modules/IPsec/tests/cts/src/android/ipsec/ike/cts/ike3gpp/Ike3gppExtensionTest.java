/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.ipsec.ike.ike3gpp;

import static android.net.ipsec.ike.ike3gpp.Ike3gppDataListenerTest.TestIke3gppDataListener;

import static org.junit.Assert.assertEquals;

import android.net.ipsec.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;

import org.junit.Before;
import org.junit.Test;

public class Ike3gppExtensionTest {
    private Ike3gppParams mParams;
    private Ike3gppDataListener mDataListener;
    private Ike3gppExtension mIke3gppExtension;

    @Before
    public void setUp() {
        mParams = new Ike3gppParams.Builder().build();
        mDataListener = new TestIke3gppDataListener();

        mIke3gppExtension = new Ike3gppExtension(mParams, mDataListener);
    }

    @Test
    public void testGetIke3gppParams() {
        assertEquals(mParams, mIke3gppExtension.getIke3gppParams());
    }

    @Test
    public void testGetIke3gppDataListener() {
        assertEquals(mDataListener, mIke3gppExtension.getIke3gppDataListener());
    }
}
