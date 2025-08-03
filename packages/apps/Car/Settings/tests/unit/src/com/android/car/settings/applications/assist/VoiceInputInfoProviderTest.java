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

package com.android.car.settings.applications.assist;


import static com.android.car.settings.applications.assist.VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG;
import static com.android.car.settings.applications.assist.VoiceInputInfoProvider.VOICE_RECOGNITION_SERVICE_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.voice.VoiceInteractionServiceInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class VoiceInputInfoProviderTest {

    private static final String TEST_PACKAGE = "test.package";
    private static final String TEST_CLASS = "Class1";
    private static final String TEST_RECOGNITION_SERVICE = "Recognition1";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private VoiceInputInfoProvider mVoiceInputInfoProvider;

    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getInteractionInfoList_hasElement() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE;
        resolveInfo.serviceInfo.name = TEST_CLASS;
        resolveInfo.serviceInfo.permission = Manifest.permission.BIND_VOICE_INTERACTION;

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfo);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.queryIntentServices(VOICE_INTERACTION_SERVICE_TAG,
                PackageManager.GET_META_DATA)).thenReturn(resolveInfoList);

        mVoiceInputInfoProvider = new TestVoiceInputInfoProvider(mContext);

        assertThat(mVoiceInputInfoProvider.getVoiceInteractionInfoList()).hasSize(1);
    }

    @Test
    public void getRecognitionInfoList_hasElement() {
        ResolveInfo otherInfo = new ResolveInfo();
        otherInfo.serviceInfo = new ServiceInfo();
        otherInfo.serviceInfo.packageName = TEST_PACKAGE;
        otherInfo.serviceInfo.name = TEST_RECOGNITION_SERVICE;

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(otherInfo);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.queryIntentServices(VOICE_RECOGNITION_SERVICE_TAG,
                PackageManager.GET_META_DATA)).thenReturn(resolveInfoList);

        mVoiceInputInfoProvider = new TestVoiceInputInfoProvider(mContext);

        assertThat(mVoiceInputInfoProvider.getVoiceRecognitionInfoList()).hasSize(1);
    }

    private static class TestVoiceInputInfoProvider extends VoiceInputInfoProvider {

        TestVoiceInputInfoProvider(Context context) {
            super(context);
        }

        @Override
        boolean hasParseError(VoiceInteractionServiceInfo voiceInteractionServiceInfo) {
            return false;
        }
    }
}
