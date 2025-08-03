/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * A test suite for the BipImageProperties class
 */
@RunWith(AndroidJUnit4.class)
public class BipImagePropertiesTest {
    private static final String IMAGE_HANDLE = "123456789";
    private static final String FRIENDLY_NAME = "current-track.jpeg";
    private static final String VERSION = "1.0";
    private static final String XML_DOC_DECL =
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n";

    // An image-properties tag with all available attributes
    private static final String IMAGE_PROPERTIES =
            "<image-properties version=\"" + VERSION + "\" handle=\"" + IMAGE_HANDLE
            + "\" friendly-name=\"" + FRIENDLY_NAME + "\">\r\n";

    // An image-properties tag without an xml version - OUT OF SPEC / INVALID
    private static final String IMAGE_PROPERTIES_NO_VERSION =
            "<image-properties handle=\"" + IMAGE_HANDLE + "\" friendly-name=\""
            + FRIENDLY_NAME + "\">\r\n";

    // An image-properties tag without an image handle - OUT OF SPEC / INVALID
    private static final String IMAGE_PROPERTIES_NO_HANDLE =
            "<image-properties version=\"" + VERSION + "\" friendly-name=\"" + FRIENDLY_NAME
            + "\">\r\n";

    // An image-properties tag without an xml version - IN SPEC / VALID
    private static final String IMAGE_PROPERTIES_NO_FRIENDLY_NAME =
            "<image-properties version=\"" + VERSION + "\" handle=\"" + IMAGE_HANDLE + "\">\r\n";

    // A native format representing the unaltered image available. Has a basic pixel and size
    private static final String NATIVE_FORMAT =
            "  <native encoding=\"JPEG\" pixel=\"1280*1024\" size=\"1048576\" />\r\n";

    // A native format representation of the imaging thumbnail format
    private static final String NATIVE_THUMBNAIL_FORMAT =
            "  <native encoding=\"JPEG\" pixel=\"200*200\" />\r\n";

    // A variant format representing a static altered image type. Has a basic pixel and no size
    private static final String VARIANT_FIXED_FORMAT =
            "  <variant encoding=\"JPEG\" pixel=\"640*480\" />\r\n";

    // A variant format representing an range of sizes available. Has transformations and no size
    private static final String VARIANT_RANGE_FORMAT =
            "  <variant encoding=\"GIF\" pixel=\"80*60-640*175\" "
            + "transformation=\"stretch fill crop\" />\r\n";

    // A variant format representing a range of sizes within a fixed aspect ratio.
    private static final String VARIANT_FIXED_RANGE_FORMAT =
            "  <variant encoding=\"JPEG\" pixel=\"150**-600*120\" />\r\n";

    // A fixed variant format representation of the imaging thumbnail format
    private static final String VARIANT_FIXED_THUMBNAIL_FORMAT =
            "  <variant encoding=\"JPEG\" pixel=\"200*200\" />\r\n";

    // A resizable modifiable aspect ratio variant format containing the imaging thumbnail format
    private static final String VARIANT_RANGE_THUMBNAIL_FORMAT =
            "  <variant encoding=\"JPEG\" pixel=\"80*60-640*480\" />\r\n";

    // A resizable fixed variant format containing the imaging thumbnail format
    private static final String VARIANT_FIXED_RANGE_THUMBNAIL_FORMAT =
            "  <variant encoding=\"JPEG\" pixel=\"150**-600*600\" />\r\n";

    // Though not in the specification, we should be robust to attachments of various formats
    private static final String ATTACHMENT_1 =
            "  <attachment content-type=\"text/plain\" name=\"ABCD1234.txt\" size=\"5120\" />\r\n";
    private static final String ATTACHMENT_2 =
            "  <attachment content-type=\"audio/basic\" name=\"ABCD1234.wav\" size=\"102400\" "
            + "/>\r\n";

    private static final String IMAGE_PROPERTIES_END = "</image-properties>";

    private InputStream toUtf8Stream(String s) {
        try {
            return new ByteArrayInputStream(s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Test parsing image-properties with very simple information available.
     *
     * This is the most common type of object we will receive.
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" >
     *         <native encoding="JPEG" pixel="200*200" />
     *     </image-properties>";
     */
    @Test
    public void testParsePropertiesSimple() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES_NO_FRIENDLY_NAME
                + NATIVE_THUMBNAIL_FORMAT + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(null, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties with very rich information available.
     *
     * This information includes attachments, which are not allowed in AVRCP-BIP but completely
     * allowed in standard BIP.
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="200*200" />
     *         <variant encoding="JPEG" pixel="640*480" />
     *         <variant encoding="GIF" pixel="80*60-640*175" transformation="stretch fill crop" />
     *         <variant encoding="JPEG" pixel="150**-600*120" />
     *         <attachment content-type="text/plain" name="ABCD1234.txt" size="5120" />
     *         <attachment content-type="audio/basic" name="ABCD1234.wav" size="102400" />
     *     </image-properties>";
     */
    @Test
    public void testParsePropertiesRich() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + NATIVE_THUMBNAIL_FORMAT
                + VARIANT_FIXED_FORMAT + VARIANT_RANGE_FORMAT + VARIANT_FIXED_RANGE_FORMAT
                + ATTACHMENT_1 + ATTACHMENT_2 + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties without an image handle.
     *
     * This is out of spec, but should not crash. Instead, the individual attributes should be
     * available and serializing should return null.
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="200*200" />
     *     </image-properties>";
     */
    @Test
    public void testParseNoHandle() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES_NO_HANDLE + NATIVE_THUMBNAIL_FORMAT
                + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(null, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertFalse(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
        Assert.assertEquals(null, properties.serialize());
    }

    /**
     * Test parsing image-properties without a version.
     *
     * This is out of spec, but should not crash. Instead, the individual attributes should be
     * available and serializing should return null.
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties handle="123456789" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="200*200" />
     *     </image-properties>";
     */
    @Test
    public void testParseNoVersion() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES_NO_VERSION + NATIVE_THUMBNAIL_FORMAT
                + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(null, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertFalse(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
        Assert.assertEquals(null, properties.serialize());
    }

    /**
     * Test parsing image-properties without a friendly name.
     *
     * This is in spec, as friendly name isn't required.
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="200*200"/>
     *     </image-properties>";
     */
    @Test
    public void testParseNoFriendlyName() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES_NO_FRIENDLY_NAME
                + NATIVE_THUMBNAIL_FORMAT + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(null, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties with a fixed variant thumbnail format
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <variant encoding="JPEG" pixel="200*200" />
     *     </image-properties>";
     */
    @Test
    public void testParseFixedVariantThumbnailFormat() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + VARIANT_FIXED_THUMBNAIL_FORMAT
                + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties with a range variant thumbnail format
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <variant encoding="JPEG" pixel="80*60-640*480" />
     *     </image-properties>";
     */
    @Test
    public void testParseRangeVariantThumbnailFormat() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + VARIANT_RANGE_THUMBNAIL_FORMAT
                + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties with a fixed aspect ratio range variant thumbnail format
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <variant encoding="JPEG" pixel="80*60-640*480" />
     *     </image-properties>";
     */
    @Test
    public void testParseFixedRangeVariantThumbnailFormat() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + VARIANT_FIXED_RANGE_THUMBNAIL_FORMAT
                + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }

    /**
     * Test parsing image-properties without any thumbnail formats
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="1280*1024" size="1048576" />
     *         <variant encoding="JPEG" pixel="640*480" />
     *         <variant encoding="GIF" pixel="80*60-640*480" transformation="stretch fill crop" />
     *         <variant encoding="JPEG" pixel="150**-600*120" />
     *     </image-properties>";
     */
    @Test
    public void testParseNoThumbnailFormats() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + NATIVE_FORMAT + VARIANT_FIXED_FORMAT
                + VARIANT_RANGE_FORMAT + VARIANT_FIXED_RANGE_FORMAT + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertFalse(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
        Assert.assertEquals(null, properties.serialize());
    }

    /**
     * Test parsing image-properties without any formats
     *
     * Payload:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *     </image-properties>";
     */
    @Test
    public void testParseNoFormats() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertFalse(properties.isValid());
        Assert.assertEquals(null, properties.serialize());
    }

    /**
     * Test parsing an image-properties with no open tag
     */
    @Test (expected = ParseException.class)
    public void testParseMalformedNoOpen() {
        String xmlString = XML_DOC_DECL + NATIVE_FORMAT + IMAGE_PROPERTIES_END;
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
    }

    /**
     * Test parsing a malformed image-properties that just cuts out
     */
    @Test (expected = ParseException.class)
    public void testParseSimulateStreamEndedUnexpectedly() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + "<native encoding=\"JPE";
        InputStream stream = toUtf8Stream(xmlString);
        BipImageProperties properties = new BipImageProperties(stream);
    }

    /**
     * Test creating image-properties with very rich information available:
     *
     * Expected Payload created:
     *     <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
     *     <image-properties version="1.0" handle="123456789" friendly-name="current-track.jpeg">
     *         <native encoding="JPEG" pixel="200*200" />
     *         <variant encoding="JPEG" pixel="640*480" />
     *         <variant encoding="GIF" pixel="80*60-640*175" transformation="stretch fill crop" />
     *         <variant encoding="JPEG" pixel="150**-600*120" />
     *         <attachment content-type="text/plain" name="ABCD1234.txt" size="5120" />
     *         <attachment content-type="audio/basic" name="ABCD1234.wav" size="102400" />
     *     </image-properties>";
     */
    @Test
    public void testCreateProperties() {
        String xmlString = XML_DOC_DECL + IMAGE_PROPERTIES + NATIVE_THUMBNAIL_FORMAT
                + VARIANT_FIXED_FORMAT + VARIANT_RANGE_FORMAT + VARIANT_FIXED_RANGE_FORMAT
                + ATTACHMENT_1 + ATTACHMENT_2 + IMAGE_PROPERTIES_END;

        BipTransformation trans = new BipTransformation();
        trans.addTransformation(BipTransformation.STRETCH);
        trans.addTransformation(BipTransformation.CROP);
        trans.addTransformation(BipTransformation.FILL);

        BipImageProperties.Builder builder = new BipImageProperties.Builder();
        builder.setImageHandle(IMAGE_HANDLE);
        builder.setFriendlyName(FRIENDLY_NAME);
        builder.addNativeFormat(BipImageFormat.createNative(new BipEncoding(BipEncoding.JPEG, null),
                BipPixel.createFixed(200, 200), -1));

        builder.addVariantFormat(
                BipImageFormat.createVariant(
                    new BipEncoding(BipEncoding.JPEG, null),
                    BipPixel.createFixed(640, 480), -1, null));
        builder.addVariantFormat(
                BipImageFormat.createVariant(
                    new BipEncoding(BipEncoding.GIF, null),
                    BipPixel.createResizableModified(80, 60, 640, 175), -1, trans));
        builder.addVariantFormat(
                BipImageFormat.createVariant(
                    new BipEncoding(BipEncoding.JPEG, null),
                    BipPixel.createResizableFixed(150, 600, 120), -1, null));

        builder.addAttachment(
                new BipAttachmentFormat("text/plain", null, "ABCD1234.txt", 5120, null, null));
        builder.addAttachment(
                new BipAttachmentFormat("audio/basic", null, "ABCD1234.wav", 102400, null, null));

        BipImageProperties properties = builder.build();
        Assert.assertEquals(IMAGE_HANDLE, properties.getImageHandle());
        Assert.assertEquals(VERSION, properties.getVersion());
        Assert.assertEquals(FRIENDLY_NAME, properties.getFriendlyName());
        Assert.assertTrue(properties.isValid());
        Assert.assertEquals(xmlString, properties.toString());
    }
}
