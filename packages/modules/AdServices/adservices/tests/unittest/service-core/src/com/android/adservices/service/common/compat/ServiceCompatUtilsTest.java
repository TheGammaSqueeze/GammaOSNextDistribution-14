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

package com.android.adservices.service.common.compat;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

public class ServiceCompatUtilsTest {
    private static final String EXT_PACKAGE_NAME = "com.google.android.ext.services";
    private static final String NON_EXT_PACKAGE_NAME = "com.example.package";

    private MockitoSession mMockitoSession;

    @Mock Context mMockContext;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testShouldDisableJob_S() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        assertThat(ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(mMockContext)).isFalse();
        verify(mMockContext, never()).getPackageName();
    }

    @Test
    public void testShouldDisableJob_T_NonExtPackageName() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(NON_EXT_PACKAGE_NAME).when(mMockContext).getPackageName();
        assertThat(ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(mMockContext)).isFalse();
    }

    @Test
    public void testShouldDisableJob_T_ExtPackageName() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(EXT_PACKAGE_NAME).when(mMockContext).getPackageName();
        assertThat(ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(mMockContext)).isTrue();
    }

    @Test
    public void testShouldDisableJob_T_ExtPackageNameButNotSuffix() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(EXT_PACKAGE_NAME + ".more").when(mMockContext).getPackageName();
        assertThat(ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(mMockContext)).isFalse();
    }
}
