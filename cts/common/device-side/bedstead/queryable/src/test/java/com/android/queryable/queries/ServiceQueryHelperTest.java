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
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import android.app.Service;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;
import com.android.queryable.info.ServiceInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class ServiceQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;

    private static final Class<? extends Service> CLASS_1 = Service.class;


    private static final String CLASS_1_CLASS_NAME = CLASS_1.getName();

    private static final IntentFilter INTENT_FILTER_WITH_ACTION = new IntentFilter("action");

    private static final android.content.pm.ServiceInfo FRAMEWORK_SERVICE_INFO_1 =
            createServiceInfo(CLASS_1_CLASS_NAME);
    private static final android.content.pm.ServiceInfo FRAMEWORK_SERVICE_INFO_2 =
            createServiceInfo("different.class.name");

    private static android.content.pm.ServiceInfo createServiceInfo(String name) {
        android.content.pm.ServiceInfo serviceInfo = new android.content.pm.ServiceInfo();
        serviceInfo.name = name;

        return serviceInfo;
    }

    private static final ServiceInfo CLASS_1_SERVICE_INFO =
            ServiceInfo.builder(FRAMEWORK_SERVICE_INFO_1).build();
    private static final ServiceInfo CLASS_2_SERVICE_INFO =
            ServiceInfo.builder(FRAMEWORK_SERVICE_INFO_2).build();
    private static final ServiceInfo INTENT_FILTER_SERVICE_INFO = ServiceInfo.builder()
            .serviceClass(CLASS_1_CLASS_NAME)
            .intentFilters(Set.of(INTENT_FILTER_WITH_ACTION))
            .build();


    @Test
    public void matches_noRestrictions_returnsTrue() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        assertThat(serviceQueryHelper.matches(CLASS_1_SERVICE_INFO)).isTrue();
    }

    @Test
    public void matches_serviceClass_doesMatch_returnsTrue() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.serviceClass().isSameClassAs(CLASS_1);

        assertThat(serviceQueryHelper.matches(CLASS_1_SERVICE_INFO)).isTrue();
    }

    @Test
    public void matches_serviceClass_doesNotMatch_returnsFalse() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.serviceClass().isSameClassAs(CLASS_1);

        assertThat(serviceQueryHelper.matches(CLASS_2_SERVICE_INFO)).isFalse();
    }

    @Test
    public void matches_intentFilters_matches_returnsTrue() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.intentFilters().contains(
                intentFilter().where().actions().contains("action")
        );

        assertThat(serviceQueryHelper.matches(INTENT_FILTER_SERVICE_INFO)).isTrue();
    }

    @Test
    public void matches_intentFilters_doesNotMatch_returnsFalse() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.intentFilters().contains(
                intentFilter().where().actions().doesNotContain("action")
        );

        assertThat(serviceQueryHelper.matches(INTENT_FILTER_SERVICE_INFO)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ServiceQueryHelper<Queryable> serviceQueryHelper = new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.serviceClass().className().isEqualTo("");
        serviceQueryHelper.intentFilters().contains(
                intentFilter().where().actions().doesNotContain("action")
        );

        assertParcelsCorrectly(ServiceQueryHelper.class, serviceQueryHelper);
    }

    @Test
    public void serviceQueryHelper_queries() {
        assertThat(ServiceQuery.service()
                .where().serviceClass().isSameClassAs(CLASS_1)
                .matches(CLASS_1_SERVICE_INFO)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        ServiceQueryHelper<Queryable> serviceQueryHelper =
                new ServiceQueryHelper<>(mQuery);

        assertThat(serviceQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasServiceClassQuery_returnsFalse() {
        ServiceQueryHelper<Queryable> serviceQueryHelper =
                new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.serviceClass().className().isEqualTo("");

        assertThat(serviceQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIntentFiltersQuery_returnsFalse() {
        ServiceQueryHelper<Queryable> serviceQueryHelper =
                new ServiceQueryHelper<>(mQuery);

        serviceQueryHelper.intentFilters().isEmpty();

        assertThat(serviceQueryHelper.isEmptyQuery()).isFalse();
    }
}
