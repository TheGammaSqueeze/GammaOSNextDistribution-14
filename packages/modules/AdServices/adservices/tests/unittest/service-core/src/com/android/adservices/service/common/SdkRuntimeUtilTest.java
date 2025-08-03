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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.os.Process;

import com.android.adservices.service.common.compat.ProcessCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

public class SdkRuntimeUtilTest {
    MockitoSession mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Process.class)
                        .mockStatic(ProcessCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testCallingUidIsNotSdkSandbox_returnParameterUid() {
        int uid = 400;
        ExtendedMockito.doReturn(false).when(() -> ProcessCompatUtils.isSdkSandboxUid(uid));
        assertThat(SdkRuntimeUtil.getCallingAppUid(uid)).isEqualTo(uid);
    }

    @Test
    public void testCallingUidIsSdkSandbox_returnAppUid() {
        assumeTrue(SdkLevel.isAtLeastT());

        int sdkUid = 400;
        int appUid = 200;
        ExtendedMockito.doReturn(true).when(() -> ProcessCompatUtils.isSdkSandboxUid(sdkUid));
        ExtendedMockito.doReturn(appUid).when(() -> Process.getAppUidForSdkSandboxUid(sdkUid));
        assertThat(SdkRuntimeUtil.getCallingAppUid(sdkUid)).isEqualTo(appUid);
    }
}
