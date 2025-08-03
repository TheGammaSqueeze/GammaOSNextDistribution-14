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

package com.android.managedprovisioning.common;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class HomogenousBiMapTest {

    private final static Integer KEY = 1;
    private final static Integer VALUE = 2;
    private final static Integer OTHER_KEY = 3;
    private final static Integer OTHER_VALUE = 4;
    private HomogenousBiMap<Object> mBiMap;

    @Before
    public void setUp() {
        mBiMap = new HomogenousBiMap<>();
        mBiMap.put(KEY, VALUE);
    }

    @Test
    public void put_works() {
        assertThat(mBiMap.getForwardMap().get(KEY)).isEqualTo(VALUE);
    }

    @Test
    public void getForwardMap_works() {
        assertThat(mBiMap.getForwardMap().get(KEY)).isEqualTo(VALUE);
    }

    @Test
    public void getReverseMap_works() {
        assertThat(mBiMap.getReverseMap().get(VALUE)).isEqualTo(KEY);
    }

    @Test
    public void clear_works() {
        mBiMap.clear();

        assertThat(mBiMap.getForwardMap()).isEmpty();
        assertThat(mBiMap.getReverseMap()).isEmpty();
    }

    @Test
    public void equals_equalMaps_works() {
        HomogenousBiMap<Integer> copy = new HomogenousBiMap<>();
        copy.put(KEY, VALUE);

        assertThat(copy).isEqualTo(mBiMap);
    }

    @Test
    public void equals_differentMaps_works() {
        HomogenousBiMap<Integer> copy = new HomogenousBiMap<>();
        copy.put(OTHER_KEY, OTHER_VALUE);

        assertThat(copy).isNotEqualTo(mBiMap);
    }

    @Test
    public void hashCode_equalMaps_works() {
        HomogenousBiMap<Integer> copy = new HomogenousBiMap<>();
        copy.put(KEY, VALUE);

        assertThat(copy.hashCode()).isEqualTo(mBiMap.hashCode());
    }

    @Test
    public void hashCode_differentMaps_works() {
        HomogenousBiMap<Integer> copy = new HomogenousBiMap<>();
        copy.put(OTHER_KEY, OTHER_VALUE);

        assertThat(copy.hashCode()).isNotEqualTo(mBiMap.hashCode());
    }
}
