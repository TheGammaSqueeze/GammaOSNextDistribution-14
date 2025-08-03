/*
 * Copyright 2021 The Android Open Source Project
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

package android.media.drmframework.cts;

import android.media.DrmInitData;

import android.content.res.AssetFileDescriptor;
import android.media.DrmInitData;
import android.media.MediaExtractor;
import android.media.cts.TestMediaDataSource;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;

@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaDrmExtractorTest extends AndroidTestCase {
    private static final String TAG = "MediaDrmExtractorTest";
    private static final UUID UUID_WIDEVINE = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final UUID UUID_PLAYREADY = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);
    private static boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);

    static final String mInpPrefix = WorkDir.getMediaDirString();
    private MediaExtractor mExtractor;

@Override
    protected void setUp() throws Exception {
        super.setUp();
        mExtractor = new MediaExtractor();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mExtractor.release();
    }

    protected AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    protected TestMediaDataSource getDataSourceFor(final String res) throws Exception {
        AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
        return TestMediaDataSource.fromAssetFd(afd);
    }

    protected TestMediaDataSource setDataSource(final String res) throws Exception {
        TestMediaDataSource ds = getDataSourceFor(res);
        mExtractor.setDataSource(ds);
        return ds;
    }

    public void testGetDrmInitData() throws Exception {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        Preconditions.assertTestFileExists(mInpPrefix + "psshtest.mp4");
        setDataSource("psshtest.mp4");
        DrmInitData drmInitData = mExtractor.getDrmInitData();
        assertEquals(drmInitData.getSchemeInitDataCount(), 2);
        assertEquals(drmInitData.getSchemeInitDataAt(0).uuid, UUID_WIDEVINE);
        assertEquals(drmInitData.get(UUID_WIDEVINE), drmInitData.getSchemeInitDataAt(0));
        assertEquals(drmInitData.getSchemeInitDataAt(1).uuid, UUID_PLAYREADY);
        assertEquals(drmInitData.get(UUID_PLAYREADY), drmInitData.getSchemeInitDataAt(1));
    }
}
