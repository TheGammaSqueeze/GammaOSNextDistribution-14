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
import android.car.media.CarVolumeGroupInfo;
import android.car.oem.IOemCarAudioVolumeService;
import android.car.oem.OemCarAudioVolumeRequest;
import android.car.oem.OemCarVolumeChangeInfo;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;

import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class CarOemAudioVolumeProxyServiceTest extends AbstractExtendedMockitoTestCase {

    private static final CarVolumeGroupInfo TEST_CAR_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("Group name", PRIMARY_AUDIO_ZONE,
            /* id= */ 0).setMaxVolumeGainIndex(9_000).setMinVolumeGainIndex(0)
                    .setMaxActivationVolumeGainIndex(8_000).setMinActivationVolumeGainIndex(100)
                    .build();

    private static final OemCarVolumeChangeInfo TEST_CAR_VOLUME_CHANGE_INFO =
            new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ true)
                    .setChangedVolumeGroup(TEST_CAR_VOLUME_INFO).build();

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;

    private CarOemProxyServiceHelper mCarOemProxyServiceHelper;

    private final TestOemCarAudioVolumeService mTestOemCarService =
            new TestOemCarAudioVolumeService();

    @Before
    public void setUp() throws Exception {
        when(mContext.getResources()).thenReturn(mResources);
        mockCallTimeout(/* timeoutMs= */ 5000);
        mCarOemProxyServiceHelper = new CarOemProxyServiceHelper(mContext);
    }

    @Test
    public void getSuggestedGroupForVolumeChange() throws Exception {
        CarOemAudioVolumeProxyService carOemProxyService =
                new CarOemAudioVolumeProxyService(mCarOemProxyServiceHelper, mTestOemCarService);

        OemCarVolumeChangeInfo info = carOemProxyService.getSuggestedGroupForVolumeChange(
                new OemCarAudioVolumeRequest.Builder(PRIMARY_AUDIO_ZONE).build(),
                AudioManager.ADJUST_RAISE);

        assertWithMessage("Volume group change").that(info)
                .isEqualTo(TEST_CAR_VOLUME_CHANGE_INFO);
    }

    private static final class TestOemCarAudioVolumeService extends
            IOemCarAudioVolumeService.Stub {

        @Override
        @NonNull
        public OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
                @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment) {
            return TEST_CAR_VOLUME_CHANGE_INFO;
        }
    }

    private void mockCallTimeout(int timeoutMs) {
        when(mResources.getInteger(R.integer.config_oemCarService_regularCall_timeout_ms))
                .thenReturn(timeoutMs);
        when(mResources.getInteger(R.integer.config_oemCarService_crashCall_timeout_ms))
                .thenReturn(timeoutMs);
    }
}
