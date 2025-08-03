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
package com.android.tradefed.cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/** Unit tests for {@link TestOutputUploader}. */
@RunWith(JUnit4.class)
public class TestOutputUploaderTest {

    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();

    private IRunUtil mMockRunUtil;
    private TestOutputUploader mTestOutputUploader;
    private File mOutputFile;

    @Before
    public void setUp() throws IOException {
        mMockRunUtil = mock(IRunUtil.class);
        when(mMockRunUtil.runTimedCmdRetry(
                        Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        mTestOutputUploader =
                new TestOutputUploader() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mOutputFile = tmpDir.newFile("test_file");
        Files.writeString(mOutputFile.toPath(), "hello world");
    }

    @Test
    public void testUploadFile_fileProtocol_rootPath() throws IOException {
        File destDir = tmpDir.newFolder();
        String uploadUrl = "file://" + destDir.getAbsolutePath();
        mTestOutputUploader.setUploadUrl(uploadUrl);

        String outputFileUrl = mTestOutputUploader.uploadFile(mOutputFile, null);
        assertEquals(uploadUrl + "/test_file", outputFileUrl);
        File uploadedFile = new File(new URL(outputFileUrl).getPath());
        assertTrue(uploadedFile.exists());
        assertTrue(FileUtil.compareFileContents(mOutputFile, uploadedFile));
    }

    @Test
    public void testUploadFile_fileProtocol_withDestPath() throws IOException {
        File destDir = tmpDir.newFolder();
        String uploadUrl = "file://" + destDir.getAbsolutePath();
        mTestOutputUploader.setUploadUrl(uploadUrl);

        String outputFileUrl = mTestOutputUploader.uploadFile(mOutputFile, "sub_dir");
        assertEquals(uploadUrl + "/sub_dir/test_file", outputFileUrl);
        File uploadedFile = new File(new URL(outputFileUrl).getPath());
        assertTrue(uploadedFile.exists());
        assertTrue(FileUtil.compareFileContents(mOutputFile, uploadedFile));
    }

    @Test
    public void testUploadFile_httpProtocol_rootPath() throws IOException {
        mTestOutputUploader.setUploadUrl("http://test.hostname.com/");

        String outputFileUrl = mTestOutputUploader.uploadFile(mOutputFile, null);
        assertEquals("http://test.hostname.com/test_file", outputFileUrl);
        verify(mMockRunUtil)
                .runTimedCmdRetry(
                        TestOutputUploader.UPLOAD_TIMEOUT_MS,
                        TestOutputUploader.RETRY_INTERVAL_MS,
                        TestOutputUploader.MAX_RETRY_COUNT,
                        "curl",
                        "--request",
                        "POST",
                        "--form",
                        "file=@" + mOutputFile.getAbsolutePath(),
                        "--fail",
                        "--location",
                        outputFileUrl);
    }

    @Test
    public void testUploadFile_httpProtocol_withDestPath() throws IOException {
        mTestOutputUploader.setUploadUrl("http://test.hostname.com/");
        String outputFileUrl = mTestOutputUploader.uploadFile(mOutputFile, "sub_dir");
        assertEquals("http://test.hostname.com/sub_dir/test_file", outputFileUrl);
    }

    @Test
    public void testUploadFile_httpProtocol_encoded() throws IOException {
        mOutputFile = tmpDir.newFile("\"_ _#");
        mTestOutputUploader.setUploadUrl("http://test.hostname.com/");
        String outputFileUrl = mTestOutputUploader.uploadFile(mOutputFile, "/{_%_}/");
        assertEquals("http://test.hostname.com/%7B_%25_%7D/%22_%20_%23", outputFileUrl);
    }
}
