/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.TunerVersionChecker;
import android.media.tv.tuner.filter.AlpFilterConfiguration;
import android.media.tv.tuner.filter.AvSettings;
import android.media.tv.tuner.filter.DownloadSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.IpFilterConfiguration;
import android.media.tv.tuner.filter.MmtpFilterConfiguration;
import android.media.tv.tuner.filter.PesSettings;
import android.media.tv.tuner.filter.RecordSettings;
import android.media.tv.tuner.filter.SectionSettingsWithSectionBits;
import android.media.tv.tuner.filter.SectionSettingsWithTableInfo;
import android.media.tv.tuner.filter.TlvFilterConfiguration;
import android.media.tv.tuner.filter.TsFilterConfiguration;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerFilterTest {
    private static final String TAG = "MediaTunerFilterTest";

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TUNER);

    private Context mContext;
    private Tuner mTuner;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mTuner = new Tuner(mContext, null, 100);
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
    }

    @Test
    public void testAvSettings() throws Exception {
        AvSettings settings =
                AvSettings
                        .builder(Filter.TYPE_TS, true) // is Audio
                        .setPassthrough(false)
                        .setUseSecureMemory(false)
                        .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                        .build();

        assertFalse(settings.isPassthrough());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(settings.getAudioStreamType(), AvSettings.AUDIO_STREAM_TYPE_MPEG1);
        } else {
            assertEquals(settings.getAudioStreamType(), AvSettings.AUDIO_STREAM_TYPE_UNDEFINED);
        }
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(settings.useSecureMemory(), false);
        }

        settings = AvSettings
                .builder(Filter.TYPE_TS, false) // is Video
                .setPassthrough(false)
                .setUseSecureMemory(true)
                .setVideoStreamType(AvSettings.VIDEO_STREAM_TYPE_MPEG1)
                .build();

        assertFalse(settings.isPassthrough());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(settings.getVideoStreamType(), AvSettings.VIDEO_STREAM_TYPE_MPEG1);
        } else {
            assertEquals(settings.getVideoStreamType(), AvSettings.VIDEO_STREAM_TYPE_UNDEFINED);
        }
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(settings.useSecureMemory(), true);
        }
    }

    @Test
    public void testDownloadSettings() throws Exception {
        DownloadSettings settings =
                DownloadSettings
                        .builder(Filter.TYPE_MMTP)
                        .setDownloadId(2)
                        .build();

        assertEquals(2, settings.getDownloadId());
    }

    @Test
    public void testPesSettings() throws Exception {
        PesSettings settings =
                PesSettings
                        .builder(Filter.TYPE_TS)
                        .setStreamId(2)
                        .setRaw(true)
                        .build();

        assertEquals(2, settings.getStreamId());
        assertTrue(settings.isRaw());
    }

    @Test
    public void testRecordSettings() throws Exception {
        RecordSettings settings =
                RecordSettings
                        .builder(Filter.TYPE_TS)
                        .setTsIndexMask(
                                RecordSettings.TS_INDEX_FIRST_PACKET
                                        | RecordSettings.TS_INDEX_PRIVATE_DATA)
                        .setScIndexType(RecordSettings.INDEX_TYPE_SC)
                        .setScIndexMask(RecordSettings.SC_INDEX_B_SLICE)
                        .build();

        assertEquals(
                RecordSettings.TS_INDEX_FIRST_PACKET | RecordSettings.TS_INDEX_PRIVATE_DATA,
                settings.getTsIndexMask());
        assertEquals(RecordSettings.INDEX_TYPE_SC, settings.getScIndexType());
        assertEquals(RecordSettings.SC_INDEX_B_SLICE, settings.getScIndexMask());
    }

    @Test
    public void testSectionSettingsWithSectionBits() throws Exception {
        SectionSettingsWithSectionBits settings =
                SectionSettingsWithSectionBits
                        .builder(Filter.TYPE_TS)
                        .setCrcEnabled(true)
                        .setRepeat(false)
                        .setRaw(false)
                        .setFilter(new byte[]{2, 3, 4})
                        .setMask(new byte[]{7, 6, 5, 4})
                        .setMode(new byte[]{22, 55, 33})
                        .build();

        assertTrue(settings.isCrcEnabled());
        assertFalse(settings.isRepeat());
        assertFalse(settings.isRaw());
        Assert.assertArrayEquals(new byte[] {2, 3, 4}, settings.getFilterBytes());
        Assert.assertArrayEquals(new byte[] {7, 6, 5, 4}, settings.getMask());
        Assert.assertArrayEquals(new byte[] {22, 55, 33}, settings.getMode());
    }

    @Test
    public void testSectionSettingsWithTableInfo() throws Exception {
        SectionSettingsWithTableInfo settings =
                SectionSettingsWithTableInfo
                        .builder(Filter.TYPE_TS)
                        .setTableId(11)
                        .setVersion(2)
                        .setCrcEnabled(false)
                        .setRepeat(true)
                        .setRaw(true)
                        .build();

        assertEquals(11, settings.getTableId());
        assertEquals(2, settings.getVersion());
        assertFalse(settings.isCrcEnabled());
        assertTrue(settings.isRepeat());
        assertTrue(settings.isRaw());
    }

    @Test
    public void testMmtpSectionSettingsWithSectionBits() throws Exception {
        SectionSettingsWithSectionBits settings =
                SectionSettingsWithSectionBits.builder(Filter.TYPE_MMTP)
                        .setCrcEnabled(true)
                        .setBitWidthOfLengthField(16)
                        .setRepeat(false)
                        .setRaw(false)
                        .setFilter(new byte[] {2, 3, 4})
                        .setMask(new byte[] {7, 6, 5, 4})
                        .setMode(new byte[] {22, 55, 33})
                        .build();

        assertTrue(settings.isCrcEnabled());
        assertFalse(settings.isRepeat());
        assertFalse(settings.isRaw());
        assertEquals(settings.getLengthFieldBitWidth(), 16);
        Assert.assertArrayEquals(new byte[] {2, 3, 4}, settings.getFilterBytes());
        Assert.assertArrayEquals(new byte[] {7, 6, 5, 4}, settings.getMask());
        Assert.assertArrayEquals(new byte[] {22, 55, 33}, settings.getMode());
    }

    @Test
    public void testMMtpSectionSettingsWithTableInfo() throws Exception {
        SectionSettingsWithTableInfo settings =
                SectionSettingsWithTableInfo.builder(Filter.TYPE_MMTP)
                        .setTableId(11)
                        .setVersion(2)
                        .setCrcEnabled(true)
                        .setBitWidthOfLengthField(32)
                        .setRepeat(true)
                        .setRaw(true)
                        .build();

        assertEquals(11, settings.getTableId());
        assertEquals(2, settings.getVersion());
        assertTrue(settings.isCrcEnabled());
        assertEquals(settings.getLengthFieldBitWidth(), 32);
        assertTrue(settings.isRepeat());
        assertTrue(settings.isRaw());
    }

    @Test
    public void testAlpFilterConfiguration() throws Exception {
        AlpFilterConfiguration config =
                AlpFilterConfiguration
                        .builder()
                        .setPacketType(AlpFilterConfiguration.PACKET_TYPE_COMPRESSED)
                        .setLengthType(AlpFilterConfiguration.LENGTH_TYPE_WITH_ADDITIONAL_HEADER)
                        .setSettings(null)
                        .build();

        assertEquals(Filter.TYPE_ALP, config.getType());
        assertEquals(AlpFilterConfiguration.PACKET_TYPE_COMPRESSED, config.getPacketType());
        assertEquals(
                AlpFilterConfiguration.LENGTH_TYPE_WITH_ADDITIONAL_HEADER, config.getLengthType());
        assertEquals(null, config.getSettings());
    }

    @Test
    public void testIpFilterConfiguration() throws Exception {
        IpFilterConfiguration config =
                IpFilterConfiguration
                        .builder()
                        .setSrcIpAddress(new byte[]{(byte) 0xC0, (byte) 0xA8, 0, 1})
                        .setDstIpAddress(new byte[]{(byte) 0xC0, (byte) 0xA8, 3, 4})
                        .setSrcPort(33)
                        .setDstPort(23)
                        .setPassthrough(false)
                        .setSettings(null)
                        .setIpFilterContextId(1)
                        .build();

        assertEquals(Filter.TYPE_IP, config.getType());
        Assert.assertArrayEquals(
                new byte[] {(byte) 0xC0, (byte) 0xA8, 0, 1}, config.getSrcIpAddress());
        Assert.assertArrayEquals(
                new byte[] {(byte) 0xC0, (byte) 0xA8, 3, 4}, config.getDstIpAddress());
        assertEquals(33, config.getSrcPort());
        assertEquals(23, config.getDstPort());
        assertFalse(config.isPassthrough());
        assertEquals(null, config.getSettings());
        if (!TunerVersionChecker.checkHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1,
                TAG + ": testIpFilterConfiguration.setIpFilterContextId")) {
            assertEquals(IpFilterConfiguration.INVALID_IP_FILTER_CONTEXT_ID,
                    config.getIpFilterContextId());
        } else {
            assertEquals(1, config.getIpFilterContextId());
        }
    }

    @Test
    public void testMmtpFilterConfiguration() throws Exception {
        MmtpFilterConfiguration config =
                MmtpFilterConfiguration
                        .builder()
                        .setMmtpPacketId(3)
                        .setSettings(null)
                        .build();

        assertEquals(Filter.TYPE_MMTP, config.getType());
        assertEquals(3, config.getMmtpPacketId());
        assertEquals(null, config.getSettings());
    }

    @Test
    public void testTlvFilterConfiguration() throws Exception {
        TlvFilterConfiguration config =
                TlvFilterConfiguration
                        .builder()
                        .setPacketType(TlvFilterConfiguration.PACKET_TYPE_IPV4)
                        .setCompressedIpPacket(true)
                        .setPassthrough(false)
                        .setSettings(null)
                        .build();

        assertEquals(Filter.TYPE_TLV, config.getType());
        assertEquals(TlvFilterConfiguration.PACKET_TYPE_IPV4, config.getPacketType());
        assertTrue(config.isCompressedIpPacket());
        assertFalse(config.isPassthrough());
        assertEquals(null, config.getSettings());
    }

    @Test
    public void testTsFilterConfiguration() throws Exception {
        PesSettings settings =
                PesSettings
                        .builder(Filter.TYPE_TS)
                        .setStreamId(3)
                        .setRaw(false)
                        .build();

        TsFilterConfiguration config =
                TsFilterConfiguration
                        .builder()
                        .setTpid(521)
                        .setSettings(settings)
                        .build();

        assertEquals(Filter.TYPE_TS, config.getType());
        assertEquals(521, config.getTpid());

        assertTrue(config.getSettings() instanceof PesSettings);
        PesSettings pes = (PesSettings) config.getSettings();
        assertEquals(3, pes.getStreamId());
        assertFalse(pes.isRaw());
    }


    private boolean hasTuner() {
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }
}
