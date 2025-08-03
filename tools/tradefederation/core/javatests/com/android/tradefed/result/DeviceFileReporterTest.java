/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.result;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.ArrayUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link DeviceFileReporter}. */
@RunWith(JUnit4.class)
public class DeviceFileReporterTest {
    private DeviceFileReporter dfr = null;
    @Mock ITestDevice mDevice;
    @Mock ITestInvocationListener mListener;

    // Used to control what ISS is returned
    private InputStreamSource mDfrIss = null;

    @SuppressWarnings("serial")
    private static class FakeFile extends File {
        private final String mName;
        private final long mSize;

        FakeFile(String name, long size) {
            super(name);
            mName = name;
            mSize = size;
        }

        @Override
        public String toString() {
            return mName;
        }

        @Override
        public long length() {
            return mSize;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mDevice.getSerialNumber()).thenReturn("serial");

        dfr =
                new DeviceFileReporter(mDevice, mListener) {
                    @Override
                    InputStreamSource createIssForFile(File file) {
                        return mDfrIss;
                    }
                };
    }

    @Test
    public void testSimple() throws Exception {
        final String result = "/data/tombstones/tombstone_00\r\n";
        final String filename = "/data/tombstones/tombstone_00";
        final String tombstone = "What do you want on your tombstone?";
        dfr.addPatterns("/data/tombstones/*");

        when(mDevice.executeShellCommand(Mockito.eq("ls /data/tombstones/*"))).thenReturn(result);
        // This gets passed verbatim to createIssForFile above
        when(mDevice.pullFile(Mockito.eq(filename)))
                .thenReturn(new FakeFile(filename, tombstone.length()));

        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());

        dfr.run();

        verify(mListener)
                .testLog(
                        Mockito.eq(filename), Mockito.eq(LogDataType.UNKNOWN), Mockito.eq(mDfrIss));
    }

    /** Files' paths should be trimmed to remove white spaces at the end of the lines. */
    @Test
    public void testTrim() throws Exception {
        // Result with trailing white spaces.
        final String result = "/data/tombstones/tombstone_00  \r\n";

        final String filename = "/data/tombstones/tombstone_00";
        final String tombstone = "What do you want on your tombstone?";
        dfr.addPatterns("/data/tombstones/*");

        when(mDevice.executeShellCommand(Mockito.eq("ls /data/tombstones/*"))).thenReturn(result);
        // This gets passed verbatim to createIssForFile above
        when(mDevice.pullFile(Mockito.eq(filename)))
                .thenReturn(new FakeFile(filename, tombstone.length()));

        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());

        List<String> filenames = dfr.run();
        assertEquals(filename, filenames.get(0));

        verify(mListener)
                .testLog(
                        Mockito.eq(filename), Mockito.eq(LogDataType.UNKNOWN), Mockito.eq(mDfrIss));
    }

    @Test
    public void testLine_containingSpace() throws Exception {
        final String filename = "/data/tombstones/tombstone_00";
        final String filename1 = "/data/tombstones/tomb1";
        final String filename2 = "/data/tombstones/tomb2";
        final String tombstone = "What do you want on your tombstone?";
        // Result with trailing white spaces.
        final String result = "/data/tombstones/tombstone_00  \r\n" + filename1 + "   " + filename2;
        dfr.addPatterns("/data/tombstones/*");

        when(mDevice.executeShellCommand(Mockito.eq("ls /data/tombstones/*"))).thenReturn(result);
        // This gets passed verbatim to createIssForFile above
        when(mDevice.pullFile(Mockito.eq(filename)))
                .thenReturn(new FakeFile(filename, tombstone.length()));
        when(mDevice.pullFile(Mockito.eq(filename1)))
                .thenReturn(new FakeFile(filename1, tombstone.length()));
        when(mDevice.pullFile(Mockito.eq(filename2)))
                .thenReturn(new FakeFile(filename2, tombstone.length()));

        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());

        List<String> filenames = dfr.run();
        assertEquals(filename, filenames.get(0));
        assertEquals(filename1, filenames.get(1));
        assertEquals(filename2, filenames.get(2));

        verify(mListener)
                .testLog(
                        Mockito.eq(filename), Mockito.eq(LogDataType.UNKNOWN), Mockito.eq(mDfrIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(filename1),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(mDfrIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(filename2),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(mDfrIss));
    }

    @Test
    public void testLineEnding_LF() throws Exception {
        final String[] filenames = {
            "/data/tombstones/tombstone_00",
            "/data/tombstones/tombstone_01",
            "/data/tombstones/tombstone_02",
            "/data/tombstones/tombstone_03",
            "/data/tombstones/tombstone_04"
        };
        String result = ArrayUtil.join("\n", (Object[]) filenames);
        final String tombstone = "What do you want on your tombstone?";
        dfr.addPatterns("/data/tombstones/*");

        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result);
        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());
        // This gets passed verbatim to createIssForFile above
        for (String filename : filenames) {
            when(mDevice.pullFile(Mockito.eq(filename)))
                    .thenReturn(new FakeFile(filename, tombstone.length()));
        }

        dfr.run();

        for (String filename : filenames) {
            verify(mListener)
                    .testLog(
                            Mockito.eq(filename),
                            Mockito.eq(LogDataType.UNKNOWN),
                            Mockito.eq(mDfrIss));
        }
    }

    @Test
    public void testLineEnding_CRLF() throws Exception {
        final String[] filenames = {
            "/data/tombstones/tombstone_00",
            "/data/tombstones/tombstone_01",
            "/data/tombstones/tombstone_02",
            "/data/tombstones/tombstone_03",
            "/data/tombstones/tombstone_04"
        };
        String result = ArrayUtil.join("\r\n", (Object[]) filenames);
        final String tombstone = "What do you want on your tombstone?";
        dfr.addPatterns("/data/tombstones/*");

        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result);
        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());
        // This gets passed verbatim to createIssForFile above
        for (String filename : filenames) {
            when(mDevice.pullFile(Mockito.eq(filename)))
                    .thenReturn(new FakeFile(filename, tombstone.length()));
        }

        dfr.run();

        for (String filename : filenames) {
            verify(mListener)
                    .testLog(
                            Mockito.eq(filename),
                            Mockito.eq(LogDataType.UNKNOWN),
                            Mockito.eq(mDfrIss));
        }
    }

    /**
     * Make sure that the Reporter behaves as expected when a file is matched by multiple patterns
     */
    @Test
    public void testRepeat_skip() throws Exception {
        final String result1 = "/data/files/file.png\r\n";
        final String result2 = "/data/files/file.png\r\n/data/files/file.xml\r\n";
        final String pngFilename = "/data/files/file.png";
        final String xmlFilename = "/data/files/file.xml";
        final Map<String, LogDataType> patMap = new HashMap<>(2);
        patMap.put("/data/files/*.png", LogDataType.PNG);
        patMap.put("/data/files/*", LogDataType.UNKNOWN);

        final String pngContents = "This is PNG data";
        final String xmlContents = "<!-- This is XML data -->";
        final InputStreamSource pngIss = new ByteArrayInputStreamSource(pngContents.getBytes());
        final InputStreamSource xmlIss = new ByteArrayInputStreamSource(xmlContents.getBytes());

        dfr =
                new DeviceFileReporter(mDevice, mListener) {
                    @Override
                    InputStreamSource createIssForFile(File file) {
                        if (file.toString().endsWith(".png")) {
                            return pngIss;
                        } else if (file.toString().endsWith(".xml")) {
                            return xmlIss;
                        }
                        return null;
                    }
                };
        dfr.addPatterns(patMap);
        dfr.setInferUnknownDataTypes(false);

        // Set file listing pulling, and reporting expectations
        // Expect that we go through the entire process for the PNG file, and then go through
        // the entire process again for the XML file
        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result1, result2);
        when(mDevice.pullFile(Mockito.eq(pngFilename)))
                .thenReturn(new FakeFile(pngFilename, pngContents.length()));
        when(mDevice.pullFile(Mockito.eq(xmlFilename)))
                .thenReturn(new FakeFile(xmlFilename, xmlContents.length()));

        dfr.run();

        verify(mListener)
                .testLog(Mockito.eq(pngFilename), Mockito.eq(LogDataType.PNG), Mockito.eq(pngIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(xmlFilename),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(xmlIss));
    }

    /**
     * Make sure that the Reporter behaves as expected when a file is matched by multiple patterns
     */
    @Test
    public void testRepeat_noSkip() throws Exception {
        final String result1 = "/data/files/file.png\r\n";
        final String result2 = "/data/files/file.png\r\n/data/files/file.xml\r\n";
        final String pngFilename = "/data/files/file.png";
        final String xmlFilename = "/data/files/file.xml";
        final Map<String, LogDataType> patMap = new HashMap<>(2);
        patMap.put("/data/files/*.png", LogDataType.PNG);
        patMap.put("/data/files/*", LogDataType.UNKNOWN);

        final String pngContents = "This is PNG data";
        final String xmlContents = "<!-- This is XML data -->";
        final InputStreamSource pngIss = new ByteArrayInputStreamSource(pngContents.getBytes());
        final InputStreamSource xmlIss = new ByteArrayInputStreamSource(xmlContents.getBytes());

        dfr =
                new DeviceFileReporter(mDevice, mListener) {
                    @Override
                    InputStreamSource createIssForFile(File file) {
                        if (file.toString().endsWith(".png")) {
                            return pngIss;
                        } else if (file.toString().endsWith(".xml")) {
                            return xmlIss;
                        }
                        return null;
                    }
                };
        dfr.addPatterns(patMap);
        dfr.setInferUnknownDataTypes(false);
        // this should cause us to see three pulls instead of two
        dfr.setSkipRepeatFiles(false);

        // Set file listing pulling, and reporting expectations
        // Expect that we go through the entire process for the PNG file, and then go through
        // the entire process again for the PNG file (again) and the XML file
        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result1);
        when(mDevice.pullFile(Mockito.eq(pngFilename)))
                .thenReturn(new FakeFile(pngFilename, pngContents.length()));

        // Note that the PNG file is picked up with the UNKNOWN data type this time
        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result2);
        when(mDevice.pullFile(Mockito.eq(pngFilename)))
                .thenReturn(new FakeFile(pngFilename, pngContents.length()));

        when(mDevice.pullFile(Mockito.eq(xmlFilename)))
                .thenReturn(new FakeFile(xmlFilename, xmlContents.length()));

        dfr.run();

        verify(mListener)
                .testLog(Mockito.eq(pngFilename), Mockito.eq(LogDataType.PNG), Mockito.eq(pngIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(pngFilename),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(pngIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(xmlFilename),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(xmlIss));
    }

    /**
     * Make sure that we correctly handle the case where a file doesn't exist while matching the
     * exact name.
     *
     * <p>This verifies a fix for a bug where we would mistakenly treat the "file.txt: No such file
     * or directory" message as a filename. This would happen when we tried to match an exact
     * filename that doesn't exist, rather than using a shell glob.
     */
    @Test
    public void testNoExist() throws Exception {
        final String file = "/data/traces.txt";
        final String result = file + ": No such file or directory\r\n";
        dfr.addPatterns(file);

        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result);

        dfr.run();
        // No pull attempt should happen
        verify(mDevice).executeShellCommand((String) Mockito.any());
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testTwoFiles() throws Exception {
        final String result = "/data/tombstones/tombstone_00\r\n/data/tombstones/tombstone_01\r\n";
        final String filename1 = "/data/tombstones/tombstone_00";
        final String filename2 = "/data/tombstones/tombstone_01";
        final String tombstone = "What do you want on your tombstone?";
        dfr.addPatterns("/data/tombstones/*");

        // Search the filesystem
        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result);

        // Log the first file
        // This gets passed verbatim to createIssForFile above
        when(mDevice.pullFile(Mockito.eq(filename1)))
                .thenReturn(new FakeFile(filename1, tombstone.length()));
        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());

        // Log the second file
        // This gets passed verbatim to createIssForFile above
        when(mDevice.pullFile(Mockito.eq(filename2)))
                .thenReturn(new FakeFile(filename2, tombstone.length()));

        dfr.run();

        verify(mListener)
                .testLog(
                        Mockito.eq(filename1),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(mDfrIss));
        verify(mListener)
                .testLog(
                        Mockito.eq(filename2),
                        Mockito.eq(LogDataType.UNKNOWN),
                        Mockito.eq(mDfrIss));
    }

    /** Make sure that data type inference works as expected */
    @Test
    public void testInferDataTypes() throws Exception {
        final String result =
                "/data/files/file.png\r\n/data/files/file.xml\r\n" + "/data/files/file.zip\r\n";
        final String[] filenames = {
            "/data/files/file.png", "/data/files/file.xml", "/data/files/file.zip"
        };
        final LogDataType[] expTypes = {LogDataType.PNG, LogDataType.XML, LogDataType.ZIP};
        dfr.addPatterns("/data/files/*");

        final String contents = "these are file contents";
        mDfrIss = new ByteArrayInputStreamSource(contents.getBytes());

        when(mDevice.executeShellCommand((String) Mockito.any())).thenReturn(result);
        // This gets passed verbatim to createIssForFile above
        for (int i = 0; i < filenames.length; ++i) {
            final String filename = filenames[i];
            final LogDataType expType = expTypes[i];
            when(mDevice.pullFile(Mockito.eq(filename)))
                    .thenReturn(new FakeFile(filename, contents.length()));
        }

        dfr.run();

        for (int i = 0; i < filenames.length; ++i) {
            final String filename = filenames[i];
            final LogDataType expType = expTypes[i];
            verify(mListener)
                    .testLog(Mockito.eq(filename), Mockito.eq(expType), Mockito.eq(mDfrIss));
        }
    }
}
