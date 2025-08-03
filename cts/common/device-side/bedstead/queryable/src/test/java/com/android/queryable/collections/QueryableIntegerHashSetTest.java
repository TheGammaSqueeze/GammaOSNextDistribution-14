/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.queryable.collections;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public class QueryableIntegerHashSetTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void get_containsMatch_returnsOneMatch() {
        QueryableIntegerHashSet set = QueryableIntegerHashSet.of(1, 2, 3, 4);

        assertThat(set.query()
                .where().isGreaterThan(1)
                .where().isLessThan(3)
                .get()
        ).isEqualTo(2);
    }

    @Test
    public void get_containsMultipleMatches_returnsOneMatch() {
        QueryableIntegerHashSet set = QueryableIntegerHashSet.of(1, 2, 3, 4);

        assertThat(set.query()
                .where().isGreaterThan(1)
                .where().isLessThan(4)
                .get()
        ).isNotNull();
    }

    @Test
    public void get_containsNoMatches_returnsNull() {
        QueryableIntegerHashSet set = QueryableIntegerHashSet.of(1, 2, 3, 4);

        assertThat(set.query()
                .where().isGreaterThan(1)
                .where().isLessThan(1)
                .get()
        ).isNull();
    }

    @Test
    public void filter_filters() {
        QueryableIntegerHashSet set = QueryableIntegerHashSet.of(1, 2, 3, 4);

        assertThat(set.query()
                .where().isGreaterThan(1)
                .where().isLessThan(4)
                .filter()
        ).containsExactly(2, 3);
    }
}
