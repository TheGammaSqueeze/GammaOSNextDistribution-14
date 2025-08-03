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
import com.android.queryable.info.ActivityInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class QueryableActivityInfoHashSetTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ActivityInfo EXPORTED_ACTIVITY_INFO_1 = ActivityInfo
            .builder().activityClass("activityInfo1").exported(true).build();
    private static final ActivityInfo EXPORTED_ACTIVITY_INFO_2 = ActivityInfo
            .builder().activityClass("activityInfo2").exported(true).build();
    private static final ActivityInfo NOT_EXPORTED_ACTIVITY_INFO = ActivityInfo
            .builder().activityClass("activityInfo3").exported(false).build();

    @Test
    public void get_containsMatch_returnsOneMatch() {
        QueryableActivityInfoHashSet set =
                QueryableActivityInfoHashSet.of(
                        EXPORTED_ACTIVITY_INFO_1, EXPORTED_ACTIVITY_INFO_2,
                        NOT_EXPORTED_ACTIVITY_INFO);

        assertThat(set.query()
                .where().activityClass().className().isEqualTo(EXPORTED_ACTIVITY_INFO_1.className())
                .get()
        ).isEqualTo(EXPORTED_ACTIVITY_INFO_1);
    }

    @Test
    public void get_containsMultipleMatches_returnsOneMatch() {
        QueryableActivityInfoHashSet set =
                QueryableActivityInfoHashSet.of(
                        EXPORTED_ACTIVITY_INFO_1, EXPORTED_ACTIVITY_INFO_2,
                        NOT_EXPORTED_ACTIVITY_INFO);

        assertThat(set.query()
                .where().exported().isTrue()
                .get()
        ).isNotNull();
    }

    @Test
    public void get_containsNoMatches_returnsNull() {
        QueryableActivityInfoHashSet set =
                QueryableActivityInfoHashSet.of(
                        EXPORTED_ACTIVITY_INFO_1, EXPORTED_ACTIVITY_INFO_2);

        assertThat(set.query()
                .where().exported().isFalse()
                .get()
        ).isNull();
    }

    @Test
    public void filter_filters() {
        QueryableActivityInfoHashSet set =
                QueryableActivityInfoHashSet.of(
                        EXPORTED_ACTIVITY_INFO_1, EXPORTED_ACTIVITY_INFO_2,
                        NOT_EXPORTED_ACTIVITY_INFO);

        assertThat(set.query()
                .where().exported().isTrue()
                .filter()
        ).containsExactly(EXPORTED_ACTIVITY_INFO_1, EXPORTED_ACTIVITY_INFO_2);
    }
}
