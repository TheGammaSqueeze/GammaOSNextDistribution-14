/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.process;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;
import static com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner.TRUSTED_PARTNER_APPS_SIP;
import static com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner.UNKNOWN_APPS_SIP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class SharedIsolatedProcessRunnerTest {

    private static final SharedIsolatedProcessRunner sSipRunner =
            SharedIsolatedProcessRunner.getInstance();

    private static final String TRUSTED_APP_NAME = "trusted_app_name";
    @Mock
    private Flags mFlags;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(FlagsFactory.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();

        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(TRUSTED_APP_NAME).when(mFlags).getStableFlag(KEY_TRUSTED_PARTNER_APPS_LIST);
    }

    @Test
    public void testGetSipInstanceName_artImageLoadingOptimizationEnabled() {
        doReturn(true).when(mFlags)
                .getStableFlag(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);
        assertThat(sSipRunner.getSipInstanceName(TRUSTED_APP_NAME))
                .isEqualTo(TRUSTED_PARTNER_APPS_SIP + "_disable_art_image_");
    }

    @Test
    public void testGetSipInstanceName_trustedApp() {
        doReturn(false).when(mFlags)
                .getStableFlag(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);
        assertThat(sSipRunner.getSipInstanceName(TRUSTED_APP_NAME))
                .isEqualTo(TRUSTED_PARTNER_APPS_SIP);
    }

    @Test
    public void testGetSipInstanceName_unknownApp() {
        doReturn(false).when(mFlags)
                .getStableFlag(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);
        assertThat(sSipRunner.getSipInstanceName("unknown_app_name"))
                .isEqualTo(UNKNOWN_APPS_SIP);
    }
}
