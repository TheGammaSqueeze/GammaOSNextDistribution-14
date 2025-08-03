/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import com.google.common.net.HostAndPort;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;

/** Unit tests for {@link RemoteFileUtil}. */
@RunWith(JUnit4.class)
public class RemoteFileUtilTest {

    private TestDeviceOptions mOptions;
    private IRunUtil mMockRunUtil;

    @Before
    public void setUp() {
        mMockRunUtil = Mockito.mock(IRunUtil.class);
        mOptions = new TestDeviceOptions();
    }

    /** Test fetching a remote file via scp. */
    @Test
    public void testFetchRemoteFile() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        String remotePath = "/home/vsoc-01/cuttlefish_runtime/kernel.log";
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1:" + remotePath),
                        Mockito.any()))
                .thenReturn(res);

        File resFile = null;
        try {
            resFile =
                    RemoteFileUtil.fetchRemoteFile(
                            fakeInfo, mOptions, mMockRunUtil, 500L, remotePath);
            // The original remote name is used.
            assertTrue(resFile.getName().startsWith("kernel"));
        } finally {
            FileUtil.deleteFile(resFile);
        }
    }

    /** Test when fetching a remote file fails. */
    @Test
    public void testFetchRemoteFile_fail() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        String remotePath = "/home/vsoc-01/cuttlefish_runtime/kernel.log";
        CommandResult res = new CommandResult(CommandStatus.FAILED);
        res.setStderr("Failed to fetch file.");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1:" + remotePath),
                        Mockito.any()))
                .thenReturn(res);

        File resFile =
                RemoteFileUtil.fetchRemoteFile(fakeInfo, mOptions, mMockRunUtil, 500L, remotePath);
        assertNull(resFile);
    }

    /** Test pulling a directory from the remote hosts. */
    @Test
    public void testFetchRemoteDir() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        String remotePath = "/home/vsoc-01/cuttlefish_runtime/tombstones";
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("-r"),
                        Mockito.eq("root@127.0.0.1:" + remotePath),
                        Mockito.any()))
                .thenReturn(res);
        File resDir = null;
        try {
            resDir =
                    RemoteFileUtil.fetchRemoteDir(
                            fakeInfo, mOptions, mMockRunUtil, 500L, remotePath);
            // The original remote name is used.
            assertTrue(resDir.isDirectory());
        } finally {
            FileUtil.recursiveDelete(resDir);
        }
    }

    /** Test pushing a file to a remote instance via scp. */
    @Test
    public void testPushFileToRemote() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        String remotePath = "/home/vsoc-01/cuttlefish_runtime/kernel.log";
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        File localFile = FileUtil.createTempDir("test-remote-push-dir");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("-R"),
                        Mockito.eq(localFile.getAbsolutePath()),
                        Mockito.eq("root@127.0.0.1:" + remotePath)))
                .thenReturn(res);

        try {
            boolean result =
                    RemoteFileUtil.pushFileToRemote(
                            fakeInfo,
                            mOptions,
                            Arrays.asList("-R"),
                            mMockRunUtil,
                            500L,
                            remotePath,
                            localFile);
            assertTrue(result);
        } finally {
            FileUtil.recursiveDelete(localFile);
        }
    }

    /** Test pushing a file to a remote instance via scp when it fails */
    @Test
    public void testPushFileToRemote_fail() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        String remotePath = "/home/vsoc-01/cuttlefish_runtime/kernel.log";
        CommandResult res = new CommandResult(CommandStatus.FAILED);
        res.setStderr("failed to push to remote.");
        File localFile = FileUtil.createTempDir("test-remote-push-dir");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("-R"),
                        Mockito.eq(localFile.getAbsolutePath()),
                        Mockito.eq("root@127.0.0.1:" + remotePath)))
                .thenReturn(res);

        try {
            boolean result =
                    RemoteFileUtil.pushFileToRemote(
                            fakeInfo,
                            mOptions,
                            Arrays.asList("-R"),
                            mMockRunUtil,
                            500L,
                            remotePath,
                            localFile);
            assertFalse(result);
        } finally {
            FileUtil.recursiveDelete(localFile);
        }
    }
}
