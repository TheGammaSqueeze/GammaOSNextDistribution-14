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

package com.android.car.vms;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

public class VmsProviderInfoStoreTest {
    private static final byte[] MOCK_INFO_1 = new byte[]{2, 3, 5, 7, 11, 13, 17};
    private static final byte[] SAME_MOCK_INFO_1 = new byte[]{2, 3, 5, 7, 11, 13, 17};
    private static final byte[] MOCK_INFO_2 = new byte[]{2, 3, 5, 7, 11, 13, 17, 19};

    private VmsProviderInfoStore mProviderInfoStore;

    @Before
    public void setUp() throws Exception {
        mProviderInfoStore = new VmsProviderInfoStore();
    }

    @Test
    public void testSingleInfo() {
        int id = mProviderInfoStore.getProviderId(MOCK_INFO_1);
        assertThat(id).isEqualTo(1);
        assertThat(mProviderInfoStore.getProviderInfo(id)).isEqualTo(MOCK_INFO_1);
    }

    @Test
    public void testSingleInfo_NoSuchId() {
        assertThat(mProviderInfoStore.getProviderInfo(12345)).isNull();
    }

    @Test
    public void testTwoInfos() {
        int id1 = mProviderInfoStore.getProviderId(MOCK_INFO_1);
        int id2 = mProviderInfoStore.getProviderId(MOCK_INFO_2);
        assertThat(id1).isEqualTo(1);
        assertThat(id2).isEqualTo(2);
        assertThat(mProviderInfoStore.getProviderInfo(id1)).isEqualTo(MOCK_INFO_1);
        assertThat(mProviderInfoStore.getProviderInfo(id2)).isEqualTo(MOCK_INFO_2);
    }

    @Test
    public void testSingleInfoInsertedTwice() {
        int id = mProviderInfoStore.getProviderId(MOCK_INFO_1);
        assertThat(id).isEqualTo(1);

        int sameId = mProviderInfoStore.getProviderId(SAME_MOCK_INFO_1);
        assertThat(id).isEqualTo(sameId);
    }
}
