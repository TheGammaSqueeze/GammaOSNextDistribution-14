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

package com.android.queryable.queries;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;
import com.android.queryable.info.ActivityInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class ActivityQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;

    private static final Class<? extends Activity> CLASS_1 = Activity.class;

    private static final String CLASS_1_CLASS_NAME = CLASS_1.getName();

    private static final IntentFilter INTENT_FILTER_WITH_ACTION = new IntentFilter("action");
    private static final android.content.pm.ActivityInfo FRAMEWORK_ACTIVITY_INFO_1 =
            createActivityInfo(CLASS_1_CLASS_NAME, /* exported= */ false);
    private static final android.content.pm.ActivityInfo FRAMEWORK_ACTIVITY_INFO_2 =
            createActivityInfo("different.class.name", /* exported= */ false);
    private static final android.content.pm.ActivityInfo EXPORTED_FRAMEWORK_ACTIVITY_INFO =
            createActivityInfo(CLASS_1_CLASS_NAME, /* exported= */ true);

    private static android.content.pm.ActivityInfo createActivityInfo(String name, boolean exported) {
        android.content.pm.ActivityInfo activityInfo = new android.content.pm.ActivityInfo();
        activityInfo.name = name;
        activityInfo.exported = exported;

        return activityInfo;
    }

    private static final ActivityInfo CLASS_1_ACTIVITY_INFO = ActivityInfo.builder(FRAMEWORK_ACTIVITY_INFO_1).build();
    private static final ActivityInfo EXPORTED_ACTIVITY_INFO = ActivityInfo.builder(EXPORTED_FRAMEWORK_ACTIVITY_INFO).build();
    private static final ActivityInfo CLASS_2_ACTIVITY_INFO = ActivityInfo.builder(FRAMEWORK_ACTIVITY_INFO_2).build();
    private static final ActivityInfo INTENT_FILTER_ACTIVITY_INFO = ActivityInfo.builder()
            .activityClass(CLASS_1_CLASS_NAME)
            .intentFilters(Set.of(INTENT_FILTER_WITH_ACTION))
            .build();

    @Test
    public void matches_noRestrictions_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_activityClass_doesMatch_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.activityClass().isSameClassAs(CLASS_1);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_activityClass_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.activityClass().isSameClassAs(CLASS_1);

        assertThat(activityQueryHelper.matches(CLASS_2_ACTIVITY_INFO)).isFalse();
    }

    @Test
    public void matches_exported_matches_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.exported().isTrue();

        assertThat(activityQueryHelper.matches(EXPORTED_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_exported_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.exported().isFalse();

        assertThat(activityQueryHelper.matches(EXPORTED_ACTIVITY_INFO)).isFalse();
    }

    @Test
    public void matches_intentFilters_matches_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.intentFilters().contains(
                intentFilter().where().actions().contains("action")
        );

        assertThat(activityQueryHelper.matches(INTENT_FILTER_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_intentFilters_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.intentFilters().doesNotContain(
                intentFilter().where().actions().contains("action")
        );

        assertThat(activityQueryHelper.matches(INTENT_FILTER_ACTIVITY_INFO)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper
                .activityClass().className().isEqualTo("");
        activityQueryHelper
                .intentFilters().doesNotContain(
                        intentFilter().where().actions().contains("action")
        );
        activityQueryHelper.exported().isTrue();

        assertParcelsCorrectly(ActivityQueryHelper.class, activityQueryHelper);
    }

    @Test
    public void activityQueryBase_queries() {
        assertThat(activity().where().exported().isTrue().matches(EXPORTED_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        assertThat(activityQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasActivityClass_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.activityClass().className().isNotNull();

        assertThat(activityQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIntentFilters_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper = new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.intentFilters().isNotEmpty();

        assertThat(activityQueryHelper.isEmptyQuery()).isFalse();
    }
}
