/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.media.mediatranscoding.cts;

import static org.testng.Assert.assertThrows;

import android.content.res.XmlResourceParser;
import android.media.ApplicationMediaCapabilities;
import android.media.MediaFeature;
import android.media.MediaFormat;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Xml;

import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Presubmit
@AppModeFull(reason = "Instant apps cannot access the SD card")
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class ApplicationMediaCapabilitiesTest extends AndroidTestCase {
    private static final String TAG = "ApplicationMediaCapabilitiesTest";

    public void testSetSupportHevc() throws Exception {
        ApplicationMediaCapabilities capability =
                new ApplicationMediaCapabilities.Builder().addSupportedVideoMimeType(
                        MediaFormat.MIMETYPE_VIDEO_HEVC).build();
        assertTrue(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
    }

    public void testSetUnsupportVideoMime() throws Exception {
        ApplicationMediaCapabilities capability =
                new ApplicationMediaCapabilities.Builder().addUnsupportedVideoMimeType(
                        MediaFormat.MIMETYPE_VIDEO_HEVC).build();
        assertFalse(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
    }

    public void testSetSupportHdr() throws Exception {
        ApplicationMediaCapabilities capability =
                new ApplicationMediaCapabilities.Builder().addSupportedHdrType(
                        MediaFeature.HdrType.HDR10_PLUS).addSupportedVideoMimeType(
                        MediaFormat.MIMETYPE_VIDEO_HEVC).build();
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10_PLUS));
    }

    public void testSetUnsupportHdr() throws Exception {
        ApplicationMediaCapabilities capability =
                new ApplicationMediaCapabilities.Builder().addUnsupportedHdrType(
                        MediaFeature.HdrType.HDR10_PLUS).addSupportedVideoMimeType(
                        MediaFormat.MIMETYPE_VIDEO_HEVC).build();
        assertFalse(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10_PLUS));
    }

    // Test supports HDR without supporting hevc, expect exception.
    public void testSupportHdrWithoutSupportHevc() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            ApplicationMediaCapabilities capability =
                    new ApplicationMediaCapabilities.Builder().addSupportedHdrType(
                            MediaFeature.HdrType.HDR10_PLUS).build();
        });
    }


    // Test builder with all supports are set to true.
    public void testBuilder() throws Exception {
        ApplicationMediaCapabilities capability =
                new ApplicationMediaCapabilities.Builder().addSupportedVideoMimeType(
                        MediaFormat.MIMETYPE_VIDEO_HEVC).addSupportedHdrType(
                        MediaFeature.HdrType.HDR10_PLUS).build();
        assertTrue(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10_PLUS));
    }

    //   Test read the application's xml from res/xml folder using the XmlResourceParser.
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="HDR10" supported="false"/>
    public void testReadMediaCapabilitiesXml() throws Exception {
        XmlResourceParser parser = mContext.getResources().getXml(R.xml.mediacapabilities);
        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertFalse(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10));
        assertTrue(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
    }

    //   Test read the xml from assets folder using the InputStream.
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="VP9" supported="false"/>
    //    <format android:name="HDR10" supported="false"/>
    public void testReadFromCorrectXmlWithInputStreamInAssets() throws Exception {
        InputStream xmlIs = mContext.getAssets().open("MediaCapabilities.xml");
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(xmlIs, StandardCharsets.UTF_8.name());

        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertFalse(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10));
        assertTrue(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
        List<String> supportedVideoMimetypes = capability.getSupportedVideoMimeTypes();
        assertTrue(supportedVideoMimetypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC));
        List<String> unsupportedVideoMimetypes = capability.getUnsupportedVideoMimeTypes();
        assertTrue(unsupportedVideoMimetypes.contains(MediaFormat.MIMETYPE_VIDEO_VP9));
        List<String> unsupportedHdr = capability.getUnsupportedHdrTypes();
        assertTrue(unsupportedHdr.contains(MediaFeature.HdrType.HDR10));
    }

    //   Test read the application's xml from assets folder using the XmlResourceParser.
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="HDR10" supported="true"/>
    //    <format android:name="HDR10Plus" supported="true"/>
    //    <format android:name="Dolby-Vision" supported="true"/>
    //    <format android:name="HLG" supported="true"/>
    public void testReadMediaCapabilitiesXmlWithSupportAllHdr() throws Exception {
        InputStream xmlIs = mContext.getAssets().open("SupportAllHdr.xml");
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(xmlIs, StandardCharsets.UTF_8.name());

        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10));
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10_PLUS));
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.DOLBY_VISION));
        assertTrue(capability.isHdrTypeSupported(MediaFeature.HdrType.HLG));
        assertTrue(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
        List<String> supportedHdrTypes = capability.getSupportedHdrTypes();
        assertTrue(supportedHdrTypes.contains(MediaFeature.HdrType.HDR10));
        assertTrue(supportedHdrTypes.contains(MediaFeature.HdrType.HDR10_PLUS));
        assertTrue(supportedHdrTypes.contains(MediaFeature.HdrType.DOLBY_VISION));
        assertTrue(supportedHdrTypes.contains(MediaFeature.HdrType.HLG));
        assertEquals(4, supportedHdrTypes.size());
    }

    // Test parsing invalid xml with wrong tag expect UnsupportedOperationException
    // MediaCapability does not match MediaCapabilities at the end which will lead to
    // exception with "Ill-formatted xml file"
    // <MediaCapability xmlns:android="http://schemas.android.com/apk/res/android">
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="HDR10" supported="true"/>
    // </MediaCapabilities>
    public void testReadFromWrongMediaCapabilityXml() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("WrongMediaCapabilityTag.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test invalid xml with wrong tag expect UnsupportedOperationException
    // MediaCapability is wrong tag.
    // <MediaCapability xmlns:android="http://schemas.android.com/apk/res/android">
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="HDR10" supported="true"/>
    // </MediaCapability>
    public void testReadFromWrongMediaCapabilityXml2() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("WrongMediaCapabilityTag2.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test invalid attribute value of "support" with true->yes expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //    <format android:name="HEVC" supported="yes"/>
    //    <format android:name="HDR10" supported="false"/>
    // </media-capabilities>
    public void testReadFromXmlWithWrongBoolean() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("WrongBooleanValue.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test parsing capabilities that support HDR10 but not support HEVC.
    // Expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //    <format android:name="HDR10" supported="true"/>
    // </media-capabilities>
    public void testReadXmlSupportHdrWithoutSupportHevc() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("SupportHdrWithoutHevc.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test parsing capabilities that has conflicted supported value.
    // Expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //     <format android:name="HEVC" supported="true"/>
    //     <format android:name="HEVC" supported="false"/>
    // </media-capabilities>
    public void testReadXmlConflictSupportedValue() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("ConflictSupportedValue.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test parsing capabilities that has empty format.
    // Expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //     <format/>
    // </media-capabilities>
    public void testReadXmlWithEmptyFormat() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("EmptyFormat.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test parsing capabilities that has empty format.
    // Expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //     <format android:name="HEVC"/>
    // </media-capabilities>
    public void testReadXmlFormatWithoutSupported() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("FormatWithoutSupported.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test parsing capabilities that has supported without the format name.
    // Expect UnsupportedOperationException
    // <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
    //     <format supported="true"/>
    // </media-capabilities>
    public void testReadXmlSupportedWithoutFormat() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            InputStream xmlIs = mContext.getAssets().open("SupportedWithoutFormat.xml");
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
            ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                    parser);
        });
    }

    // Test unspecified codec type.
    // AV1 is not declare in the XML which leads to isFormatSpecified return false.
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="VP9" supported="false"/>
    //    <format android:name="HDR10" supported="false"/>
    public void testUnspecifiedCodecMimetype() throws Exception {
        InputStream xmlIs = mContext.getAssets().open("MediaCapabilities.xml");
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertFalse(capability.isFormatSpecified(MediaFormat.MIMETYPE_VIDEO_AV1));
        assertFalse(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_VP9));
    }

    // Test unsupported codec type.
    //    <format android:name="HEVC" supported="false"/>
    public void testUnsupportedCodecMimetype() throws Exception {
        InputStream xmlIs = mContext.getAssets().open("MediaCapabilitiesUnsupportedHevc.xml");
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertTrue(capability.isFormatSpecified(MediaFormat.MIMETYPE_VIDEO_HEVC));
        assertFalse(capability.isVideoMimeTypeSupported(MediaFormat.MIMETYPE_VIDEO_HEVC));
    }

    // Test unspecified and unsupported hdr type.
    // DOLBY_VISION is not declare in the XML which leads to isFormatSpecified return false.
    //    <format android:name="HEVC" supported="true"/>
    //    <format android:name="HDR10" supported="false"/>
    public void testUnsupportedHdrtype() throws Exception {
        InputStream xmlIs = mContext.getAssets().open("MediaCapabilities.xml");
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(xmlIs, StandardCharsets.UTF_8.name());
        ApplicationMediaCapabilities capability = ApplicationMediaCapabilities.createFromXml(
                parser);
        assertFalse(capability.isFormatSpecified(MediaFeature.HdrType.DOLBY_VISION));
        assertFalse(capability.isHdrTypeSupported(MediaFeature.HdrType.DOLBY_VISION));
        assertTrue(capability.isFormatSpecified(MediaFeature.HdrType.HDR10));
        assertFalse(capability.isHdrTypeSupported(MediaFeature.HdrType.HDR10));
    }
}
