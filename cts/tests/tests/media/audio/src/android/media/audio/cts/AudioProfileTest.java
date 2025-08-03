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

package android.media.audio.cts;

import android.media.AudioFormat;
import android.media.AudioProfile;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.util.Arrays;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AudioProfileTest extends CtsAndroidTestCase {
    // -----------------------------------------------------------------
    // AUDIOPROFILE TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Parcelable tests
    // ----------------------------------

    // Test case 1: call describeContents(), not used yet, but needs to be exercised
    public void testParcelableDescribeContents() throws Exception {
        final AudioProfile ap = new AudioProfile(AudioFormat.ENCODING_DTS,
                /* sampling rates */ new int[]{32000, 44100, 48000, 88200, 96000, 176400},
                /* channel masks */ new int[]{0x00000001, 0x00000008},
                /* channel index masks */ new int[]{0xa, 0x5},
                AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937);
        assertNotNull("Failure to create the AudioProfile", ap);
        assertEquals(0, ap.describeContents());
    }

    // Test case 2: create an instance, marshall it and create a new instance,
    //      check for equality, both by comparing fields, and with the equals(Object) method
    public void testParcelableWriteToParcelCreate() throws Exception {
        final AudioProfile srcProf = new AudioProfile(AudioFormat.ENCODING_DTS,
                /* sampling rates */ new int[]{32000, 44100, 48000, 88200, 96000, 176400},
                /* channel masks */ new int[]{0x00000001, 0x00000008},
                /* channel index masks */ new int[]{0xa, 0x5},
                AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937);
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcProf.writeToParcel(srcParcel, 0);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioProfile targetProf = AudioProfile.CREATOR.createFromParcel(dstParcel);
        assertEquals("Marshalled/restored format doesn't match",
                srcProf.getFormat(), targetProf.getFormat());
        assertTrue("Marshalled/restored channel masks don't match",
                Arrays.equals(srcProf.getChannelMasks(), targetProf.getChannelMasks()));
        assertTrue("Marshalled/restored channel index masks don't match",
                Arrays.equals(srcProf.getChannelIndexMasks(), targetProf.getChannelIndexMasks()));
        assertTrue("Marshalled/restored sample rates don't match",
                Arrays.equals(srcProf.getSampleRates(), targetProf.getSampleRates()));
        assertEquals("Marshalled/restored encapsulation type don't match",
                srcProf.getEncapsulationType(), targetProf.getEncapsulationType());
        assertTrue("Source and target AudioProfiles are not considered equal",
                srcProf.equals(targetProf));
    }
}
