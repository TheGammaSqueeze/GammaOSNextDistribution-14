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

package android.media.audio.cts;

import android.Manifest;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.audiopolicy.AudioVolumeGroup;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;

import java.util.List;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class VolumeInfoTest extends CtsAndroidTestCase {

    private static final String TAG = "VolumeInfoTest";
    private static final int MIN_VOL = 0;
    private static final int MAX_VOL = 100;
    private static final int SET_VOL = 77;

    /**
     * Verify marshalled VolumeInfo has the same information as the original when using
     * stream types.
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.VolumeInfo",
            "android.media.VolumeInfo.Builder"})
    public void testStreamTypeParcelableWriteToParcelCreate() throws Exception {
        // test VolumeInfo with stream type, no mute command
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                false /*has mute*/, true /*ignored*/);
        // test VolumeInfo with stream type, mute command set to mute
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                true /*has mute*/, true /*mute*/);
        // test VolumeInfo with stream type, mute command set to unmute
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                true /*has mute*/, false /*mute*/);
    }

    /**
     * Verify marshalled VolumeInfo has the same information as the original when using
     * AudioVolumeGroup.
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.VolumeInfo",
            "android.media.VolumeInfo.Builder"})
    public void testVolGroupParcelableWriteToParcelCreate() throws Exception {
        try {
            getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
            List<AudioVolumeGroup> groups = AudioManager.getAudioVolumeGroups();
            if (groups.isEmpty()) {
                Log.i(TAG, "no AudioVolumeGroup to use for testing VolumeInfo");
                return;
            }
            final AudioVolumeGroup group = groups.get(0);
            // test VolumeInfo with volume group, no mute command
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    false /*has mute*/, true /*ignored*/);
            // test VolumeInfo with volume group, mute command set to mute
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    true /*has mute*/, true /*mute*/);
            // test VolumeInfo with volume group, mute command set to unmute
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    true /*has mute*/, false /*mute*/);
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void exerciseParcelableWriteToParcelCreate(int streamType, AudioVolumeGroup group,
            boolean hasMute, boolean mute) throws Exception {
        final VolumeInfo.Builder srcVIB;
        if (group == null) {
            srcVIB = new VolumeInfo.Builder(streamType);
        } else {
            srcVIB = new VolumeInfo.Builder(group);
        }
        srcVIB.setMinVolumeIndex(MIN_VOL)
                .setMaxVolumeIndex(MAX_VOL)
                .setVolumeIndex(SET_VOL);
        if (hasMute) {
            srcVIB.setMuted(mute);
        }
        final VolumeInfo srcVI = srcVIB.build();
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcVI.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final VolumeInfo targetVI = VolumeInfo.CREATOR.createFromParcel(dstParcel);

        // test getters
        assertEquals(group == null, srcVI.hasStreamType());
        assertEquals(group != null, srcVI.hasVolumeGroup());
        assertEquals("Marshalled/restored hasStreamType doesn't match for " + srcVI,
                srcVI.hasStreamType(), targetVI.hasStreamType());
        if (group == null) { // using stream type
            assertEquals("Marshalled/restored stream type doesn't match for " + srcVI,
                    srcVI.getStreamType(), targetVI.getStreamType());
            assertFalse(srcVI.hasVolumeGroup());
        } else { // using AudioVolumeGroup
            assertEquals("Marshalled/restored volume group doesn't match for " + srcVI,
                    srcVI.getVolumeGroup(), targetVI.getVolumeGroup());
            assertFalse(srcVI.hasStreamType());
        }
        assertEquals("Marshalled/restored min volume index doesn't match for " + srcVI,
                srcVI.getMinVolumeIndex(), targetVI.getMinVolumeIndex());
        assertEquals("Marshalled/restored max volume index doesn't match for " + srcVI,
                srcVI.getMaxVolumeIndex(), targetVI.getMaxVolumeIndex());
        assertEquals("Marshalled/restored volume index doesn't match for " + srcVI,
                srcVI.getVolumeIndex(), targetVI.getVolumeIndex());
        assertEquals("Marshalled/restored has mute command doesn't match for " + srcVI,
                srcVI.hasMuteCommand(), targetVI.hasMuteCommand());
        if (hasMute) {
            assertEquals("set source mute command not as retrieved for " + srcVI,
                    mute, srcVI.isMuted());
            assertEquals("Marshalled/restored mute command doesn't match for " + srcVI,
                    srcVI.isMuted(), targetVI.isMuted());
        }

        // test equality
        assertEquals(srcVI, targetVI);
    }

    @ApiTest(apis = {"android.media.VolumeInfo",
            "android.media.VolumeInfo#getDefaultVolumeInfo"})
    public void testDefaultVolInfo() throws Exception {
        final VolumeInfo defaultVI = VolumeInfo.getDefaultVolumeInfo();
        assertNotNull(defaultVI);
        boolean hasStream = defaultVI.hasStreamType();
        assertEquals(!hasStream, defaultVI.hasVolumeGroup());
    }
}
