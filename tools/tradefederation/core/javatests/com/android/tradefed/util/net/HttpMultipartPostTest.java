/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tradefed.util.net;

import static org.mockito.Mockito.when;

import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/** Unit tests for {@link HttpMultipartPost}. */
@RunWith(JUnit4.class)
public class HttpMultipartPostTest {

    private File mFile;
    @Mock IHttpHelper mHelper;

    private static final String CRLF = "\r\n";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFile = File.createTempFile("http-multipart-test", null);
        PrintWriter writer = new PrintWriter(new FileOutputStream(mFile));
        writer.write("Test data");
        writer.close();
    }

    @Test
    public void testSendRequest() throws IOException, DataSizeException {
        String expectedContents =
                "--xXxXx"
                        + CRLF
                        + "Content-Disposition: form-data; name=\"param1\""
                        + CRLF
                        + CRLF
                        + "value1"
                        + CRLF
                        + "--xXxXx"
                        + CRLF
                        + "Content-Disposition: form-data; name=\"param2\";filename=\""
                        + mFile.getAbsolutePath()
                        + "\""
                        + CRLF
                        + "Content-Type: text/plain"
                        + CRLF
                        + CRLF
                        + "Test data"
                        + CRLF
                        + "--xXxXx--"
                        + CRLF
                        + CRLF;
        when(mHelper.doPostWithRetry(
                        "www.example.com", expectedContents, "multipart/form-data;boundary=xXxXx"))
                .thenReturn("OK");

        HttpMultipartPost testPost = new HttpMultipartPost("www.example.com", mHelper);
        testPost.addParameter("param1", "value1");
        testPost.addTextFile("param2", mFile);

        testPost.send();
    }

    @After
    public void tearDown() throws Exception {
        mFile.delete();
    }
}
