/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.ims.AudioCodecAttributes;
import android.telephony.ims.ImsStreamMediaProfile;
import android.util.Range;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImsStreamMediaProfileTest {

    @Test
    public void testParcelUnparcel() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsStreamMediaProfile data = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_FULL);

        ImsStreamMediaProfile unparceledData = (ImsStreamMediaProfile) parcelUnparcel(data);

        assertEquals(data.getAudioDirection(), unparceledData.getAudioDirection());
        assertEquals(data.getAudioQuality(), unparceledData.getAudioQuality());
        assertEquals(data.getRttMode(), unparceledData.getRttMode());
        assertEquals(data.getVideoDirection(), unparceledData.getVideoDirection());
        assertEquals(data.getVideoQuality(), unparceledData.getVideoQuality());

        assertNull(unparceledData.getAudioCodecAttributes());
    }

    @Test
    public void testParcelUnparcelForAudioCodecAttributes() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsStreamMediaProfile data = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_FULL);
        data.setAudioCodecAttributes(
                new AudioCodecAttributes(5.0f, new Range<Float>(1.0f, 10.0f), 15.0f,
                        new Range<Float>(10.0f, 20.0f)));

        ImsStreamMediaProfile unparceledData = (ImsStreamMediaProfile) parcelUnparcel(data);

        assertEquals(data.getAudioDirection(), unparceledData.getAudioDirection());
        assertEquals(data.getAudioQuality(), unparceledData.getAudioQuality());
        assertEquals(data.getRttMode(), unparceledData.getRttMode());
        assertEquals(data.getVideoDirection(), unparceledData.getVideoDirection());
        assertEquals(data.getVideoQuality(), unparceledData.getVideoQuality());

        assertEquals(data.getAudioCodecAttributes().getBitrateKbps(),
                unparceledData.getAudioCodecAttributes().getBitrateKbps(), 0.1);
        assertEquals(data.getAudioCodecAttributes().getBitrateRangeKbps().getLower(),
                unparceledData.getAudioCodecAttributes().getBitrateRangeKbps().getLower(), 0.1);
        assertEquals(data.getAudioCodecAttributes().getBitrateRangeKbps().getUpper(),
                unparceledData.getAudioCodecAttributes().getBitrateRangeKbps().getUpper(), 0.1);
        assertEquals(data.getAudioCodecAttributes().getBandwidthKhz(),
                unparceledData.getAudioCodecAttributes().getBandwidthKhz(), 0.1);
        assertEquals(data.getAudioCodecAttributes().getBandwidthRangeKhz().getLower(),
                unparceledData.getAudioCodecAttributes().getBandwidthRangeKhz().getLower(), 0.1);
        assertEquals(data.getAudioCodecAttributes().getBandwidthRangeKhz().getUpper(),
                unparceledData.getAudioCodecAttributes().getBandwidthRangeKhz().getUpper(), 0.1);
    }

    @Test
    public void testCopyFrom() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsStreamMediaProfile data = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_FULL);

        ImsStreamMediaProfile copiedData = new ImsStreamMediaProfile(0, 0, 0, 0, 0);
        copiedData.copyFrom(data);

        assertEquals(data.getAudioDirection(), copiedData.getAudioDirection());
        assertEquals(data.getAudioQuality(), copiedData.getAudioQuality());
        assertEquals(data.getRttMode(), copiedData.getRttMode());
        assertEquals(data.getVideoDirection(), copiedData.getVideoDirection());
        assertEquals(data.getVideoQuality(), copiedData.getVideoQuality());
    }

    @Test
    public void testSetRttMode() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsStreamMediaProfile data = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_FULL);
        assertTrue(data.isRttCall());

        data.setRttMode(ImsStreamMediaProfile.RTT_MODE_DISABLED);
        assertFalse(data.isRttCall());
    }

    @Test
    public void testReceivingRttAudio() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsStreamMediaProfile data = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_FULL);

        data.setReceivingRttAudio(true);

        ImsStreamMediaProfile unparceled = (ImsStreamMediaProfile) parcelUnparcel(data);

        assertTrue(unparceled.isReceivingRttAudio());
    }

    public Object parcelUnparcel(Object dataObj) {
        ImsStreamMediaProfile data = (ImsStreamMediaProfile) dataObj;
        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ImsStreamMediaProfile unparceledData =
                ImsStreamMediaProfile.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return unparceledData;
    }
}
