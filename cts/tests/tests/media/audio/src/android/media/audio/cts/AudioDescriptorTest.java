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

import android.media.AudioDescriptor;
import android.media.AudioProfile;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.util.Arrays;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AudioDescriptorTest extends CtsAndroidTestCase {
    // -----------------------------------------------------------------
    // AUDIODESCRIPTOR TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Parcelable tests
    // ----------------------------------

    // Test case 1: call describeContents(), not used yet, but needs to be exercised
    public void testParcelableDescribeContents() throws Exception {
        final AudioDescriptor ad = new AudioDescriptor(AudioDescriptor.STANDARD_EDID,
                AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937, new byte[]{0x0F, 0x18, 0x4A});
        assertNotNull("Failure to create the AudioDescriptor", ad);
        assertEquals(0, ad.describeContents());
    }

    // Test case 2: create an instance, marshall it and create a new instance,
    //      check for equality, both by comparing fields, and with the equals(Object) method
    public void testParcelableWriteToParcelCreate() throws Exception {
        final AudioDescriptor srcDescr = new AudioDescriptor(AudioDescriptor.STANDARD_EDID,
                AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937, new byte[]{0x0F, 0x18, 0x4A});
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcDescr.writeToParcel(srcParcel, 0);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioDescriptor targetDescr = AudioDescriptor.CREATOR.createFromParcel(dstParcel);
        assertEquals("Marshalled/restored standard doesn't match",
                srcDescr.getStandard(), targetDescr.getStandard());
        assertTrue("Marshalled/restored descriptor doesn't match",
                Arrays.equals(srcDescr.getDescriptor(), targetDescr.getDescriptor()));
        assertEquals("Marshalled/restored encapsulation type don't match",
                srcDescr.getEncapsulationType(), targetDescr.getEncapsulationType());
        assertTrue("Source and target AudioDescriptors are not considered equal",
                srcDescr.equals(targetDescr));
    }
}
