/**
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

package com.android.telephony.imsmedia.tests;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.imsmedia.EvsParams;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EvsParamsTest {
    private static final byte CHANNEL_AWARE_MODE = 7;
    private static final byte CODEC_MODE_REQUEST = 15;
    private static final boolean USE_HEADER_FULL_ONLY = true;

    private EvsParams createEvsParams(
            final int evsBandwidth,
            final int evsMode,
            final byte channelAwareMode,
            final boolean useHeaderFullOnly,
            final byte codecModeRequest) {
        return new EvsParams.Builder()
                .setEvsbandwidth(evsBandwidth)
                .setEvsMode(evsMode)
                .setChannelAwareMode(channelAwareMode)
                .setHeaderFullOnly(useHeaderFullOnly)
                .setCodecModeRequest(codecModeRequest)
                .build();
    }

    @Test
    public void testConstructorAndGetters() {
        EvsParams evs = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        assertThat(evs.getEvsBandwidth()).isEqualTo(EvsParams.EVS_WIDE_BAND);
        assertThat(evs.getEvsMode()).isEqualTo(EvsParams.EVS_MODE_7);
        assertThat(evs.getChannelAwareMode()).isEqualTo(CHANNEL_AWARE_MODE);
        assertThat(evs.getUseHeaderFullOnly()).isEqualTo(USE_HEADER_FULL_ONLY);
        assertThat(evs.getCodecModeRequest()).isEqualTo(CODEC_MODE_REQUEST);
    }

    @Test
    public void testParcel() {
        EvsParams evs = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        Parcel parcel = Parcel.obtain();
        evs.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EvsParams parcelConfig = EvsParams.CREATOR.createFromParcel(parcel);
        assertThat(evs).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        EvsParams evs1 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        EvsParams evs2 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        assertThat(evs1).isEqualTo(evs2);
    }

    @Test
    public void testNotEqual() {
        EvsParams evs1 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        EvsParams evs2 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_6,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        assertThat(evs1).isNotEqualTo(evs2);

        EvsParams evs3 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                (byte) 8, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        assertThat(evs1).isNotEqualTo(evs3);

        EvsParams evs4 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, false, CODEC_MODE_REQUEST);

        assertThat(evs1).isNotEqualTo(evs4);

        EvsParams evs6 = createEvsParams(EvsParams.EVS_SUPER_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, CODEC_MODE_REQUEST);

        assertThat(evs1).isNotEqualTo(evs6);

        EvsParams evs7 = createEvsParams(EvsParams.EVS_WIDE_BAND, EvsParams.EVS_MODE_7,
                CHANNEL_AWARE_MODE, USE_HEADER_FULL_ONLY, (byte) 14);

        assertThat(evs1).isNotEqualTo(evs7);
    }
}
