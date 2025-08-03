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

package com.android.car.oem;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.car.oem.IOemCarAudioDuckingService;
import android.car.oem.OemCarAudioVolumeRequest;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioAttributes;

import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public final class CarOemAudioDuckingProxyServiceTest extends AbstractExtendedMockitoTestCase {

    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAV_AUIDIO_ATTRIBUTE = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();

    private static final List<AudioAttributes> TEST_AUDIO_ATTRIBUTES_LIST =
            List.of(TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NAV_AUIDIO_ATTRIBUTE);

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;

    private CarOemProxyServiceHelper mCarOemProxyServiceHelper;

    private final TestOemCarAudioDuckingService mTestOemCarService =
            new TestOemCarAudioDuckingService();

    @Before
    public void setUp() throws Exception {
        when(mContext.getResources()).thenReturn(mResources);
        mockCallTimeout(/* timeoutMs= */ 5000);
        mCarOemProxyServiceHelper = new CarOemProxyServiceHelper(mContext);
    }

    @Test
    public void testFeatureDisabled() throws Exception {
        CarOemAudioDuckingProxyService carOemProxyService =
                new CarOemAudioDuckingProxyService(mCarOemProxyServiceHelper, mTestOemCarService);

        List<AudioAttributes> duckedAttributes =
                carOemProxyService.evaluateAttributesToDuck(
                        new OemCarAudioVolumeRequest.Builder(PRIMARY_AUDIO_ZONE).build());

        assertWithMessage("Ducked audio attributes").that(duckedAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NAV_AUIDIO_ATTRIBUTE);
    }

    private static final class TestOemCarAudioDuckingService extends
            IOemCarAudioDuckingService.Stub {

        @Override
        @NonNull
        public List<AudioAttributes> evaluateAttributesToDuck(
                @NonNull OemCarAudioVolumeRequest requestInfo) {
            return TEST_AUDIO_ATTRIBUTES_LIST;
        }
    }

    private void mockCallTimeout(int timeoutMs) {
        when(mResources.getInteger(R.integer.config_oemCarService_regularCall_timeout_ms))
                .thenReturn(timeoutMs);
        when(mResources.getInteger(R.integer.config_oemCarService_crashCall_timeout_ms))
                .thenReturn(timeoutMs);
    }
}
