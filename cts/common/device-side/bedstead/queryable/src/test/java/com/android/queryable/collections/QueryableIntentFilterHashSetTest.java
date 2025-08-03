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


import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public class QueryableIntentFilterHashSetTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String CATEGORY_1 = "Category1";
    private static final String CATEGORY_2 = "Category2";
    private static final String ACTION_1 = "action1";
    private static final String ACTION_2 = "action2";
    private static final String ACTION_3 = "action3";

    private static final IntentFilter CATEGORY_1_INTENT_FILTER_1 =
            createIntentFilter(ACTION_1, CATEGORY_1);
    private static final IntentFilter CATEGORY_1_INTENT_FILTER_2 =
            createIntentFilter(ACTION_2, CATEGORY_1);
    private static final IntentFilter CATEGORY_2_INTENT_FILTER =
            createIntentFilter(ACTION_3, CATEGORY_2);

    private static IntentFilter createIntentFilter(String action, String category) {
        IntentFilter i = new IntentFilter(action);
        i.addCategory(category);
        return i;
    }

    @Test
    public void get_containsMatch_returnsOneMatch() {
        QueryableIntentFilterHashSet set =
                QueryableIntentFilterHashSet.of(
                        CATEGORY_1_INTENT_FILTER_1, CATEGORY_1_INTENT_FILTER_2,
                        CATEGORY_2_INTENT_FILTER);

        assertThat(set.query()
                .where().actions().contains(ACTION_1)
                .get()
        ).isEqualTo(CATEGORY_1_INTENT_FILTER_1);
    }

    @Test
    public void get_containsMultipleMatches_returnsOneMatch() {
        QueryableIntentFilterHashSet set =
                QueryableIntentFilterHashSet.of(
                        CATEGORY_1_INTENT_FILTER_1, CATEGORY_1_INTENT_FILTER_2,
                        CATEGORY_2_INTENT_FILTER);

        assertThat(set.query()
                .where().categories().contains(CATEGORY_1)
                .get()
        ).isNotNull();
    }

    @Test
    public void get_containsNoMatches_returnsNull() {
        QueryableIntentFilterHashSet set =
                QueryableIntentFilterHashSet.of(
                        CATEGORY_1_INTENT_FILTER_1, CATEGORY_1_INTENT_FILTER_2);

        assertThat(set.query()
                .where().categories().contains(CATEGORY_2)
                .get()
        ).isNull();
    }

    @Test
    public void filter_filters() {
        QueryableIntentFilterHashSet set =
                QueryableIntentFilterHashSet.of(
                        CATEGORY_1_INTENT_FILTER_1, CATEGORY_1_INTENT_FILTER_2,
                        CATEGORY_2_INTENT_FILTER);

        assertThat(set.query()
                .where().categories().contains(CATEGORY_1)
                .filter()
        ).containsExactly(CATEGORY_1_INTENT_FILTER_1, CATEGORY_1_INTENT_FILTER_2);
    }
}
