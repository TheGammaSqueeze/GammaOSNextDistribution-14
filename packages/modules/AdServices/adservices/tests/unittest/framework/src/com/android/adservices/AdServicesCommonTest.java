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

package com.android.adservices;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SmallTest
public class AdServicesCommonTest {
    private ResolveInfo mResolveInfo1, mResolveInfo2, mResolveInfo3, mResolveInfo4;
    private ServiceInfo mServiceInfo1, mServiceInfo2;
    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.android.ext.services";
    private static final String ADSERVICES_PACKAGE_NAME_NOT_SUFFIX = "com.android.adservices.api.x";

    @Before
    public void setUp() {
        mServiceInfo1 = new ServiceInfo();
        mServiceInfo1.packageName = ADSERVICES_PACKAGE_NAME;
        mResolveInfo1 = new ResolveInfo();
        mResolveInfo1.serviceInfo = mServiceInfo1;

        mServiceInfo2 = new ServiceInfo();
        mServiceInfo2.packageName = ADEXTSERVICES_PACKAGE_NAME;
        mResolveInfo2 = new ResolveInfo();
        mResolveInfo2.serviceInfo = mServiceInfo2;

        mResolveInfo3 = new ResolveInfo();
        ServiceInfo serviceInfo3 = new ServiceInfo();
        serviceInfo3.packageName = "foobar";
        mResolveInfo3.serviceInfo = serviceInfo3;

        mResolveInfo4 = new ResolveInfo();
        ServiceInfo serviceInfo4 = new ServiceInfo();
        serviceInfo4.packageName = ADSERVICES_PACKAGE_NAME_NOT_SUFFIX;
        mResolveInfo4.serviceInfo = serviceInfo4;
    }

    @Test
    public void testResolveAdServicesService_empty() {
        assertThat(AdServicesCommon.resolveAdServicesService(List.of(), "")).isNull();
    }

    @Test
    public void testResolveAdServicesService_moreThan2() {
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo2, mResolveInfo3), ""))
                .isNull();
    }

    @Test
    public void testResolveAdServicesService_single() {
        assertThat(AdServicesCommon.resolveAdServicesService(List.of(mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);

        assertThat(AdServicesCommon.resolveAdServicesService(List.of(mResolveInfo2), ""))
                .isEqualTo(mServiceInfo2);
    }

    @Test
    public void testResolveAdServicesService() {
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo2), ""))
                .isEqualTo(mServiceInfo1);
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo2, mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo4), ""))
                .isEqualTo(mServiceInfo1);
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo4, mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo2, mResolveInfo4), ""))
                .isNull();
    }
}
