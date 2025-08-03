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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/** Unit tests for {@link PushFilePreparer} */
@RunWith(JUnit4.class)
public class PushFilePreparerTest {

    private static final String HOST_TESTCASES = "host/testcases";

    private PushFilePreparer mPreparer = null;
    @Mock ITestDevice mMockDevice;
    private OptionSetter mOptionSetter = null;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        mPreparer = new PushFilePreparer();
        mOptionSetter = new OptionSetter(mPreparer);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    /** When there's nothing to be done, expect no exception to be thrown */
    @Test
    public void testNoop() throws Exception {

        mPreparer.setUp(mTestInfo);
    }

    @Test
    public void testLocalNoExist() throws Exception {
        mOptionSetter.setOptionValue("push-file", "/noexist", "/data/");
        mOptionSetter.setOptionValue("post-push", "ls /");

        try {
            mTestInfo.getContext().addDeviceBuildInfo("device", new BuildInfo());
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testRemoteNoExist() throws Exception {
        mOptionSetter.setOptionValue("push-file", "/bin/sh", "/noexist/");
        mOptionSetter.setOptionValue("post-push", "ls /");
        // expect a pushFile() call and return false (failed)
        when(mMockDevice.pushFile((File) Mockito.any(), Mockito.eq("/noexist/")))
                .thenReturn(Boolean.FALSE);

        try {
            mTestInfo.getContext().addDeviceBuildInfo("device", new BuildInfo());
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test pushing a file to remote dir. The 'push' contract allows to push the file to a named
     * directory.
     */
    @Test
    public void testPushFile_RemoteIsDir() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.createNewFile();
            info.setFile("perf_test", testFile, "v1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/");
            // expect a pushFile() to be done with the appended file name.
            when(mMockDevice.pushFile(Mockito.eq(testFile), Mockito.eq("/data/local/tmp/")))
                    .thenReturn(Boolean.TRUE);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /** Pushing the same file to two different locations is working. */
    @Test
    public void testPushFile_duplicateKey() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.createNewFile();
            info.setFile("perf_test", testFile, "v1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/perf_test1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/perf_test2");
            // expect a pushFile() to be done with the appended file name.
            when(mMockDevice.pushFile(
                            Mockito.eq(testFile), Mockito.eq("/data/local/tmp/perf_test1")))
                    .thenReturn(Boolean.TRUE);
            when(mMockDevice.pushFile(
                            Mockito.eq(testFile), Mockito.eq("/data/local/tmp/perf_test2")))
                    .thenReturn(Boolean.TRUE);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /** Test pushing a directory to an existing remote directory. */
    @Test
    public void testPushDir_RemoteIsDir() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.mkdir();
            info.setFile("perf_test", testFile, "v1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/");
            when(mMockDevice.doesFileExist("/data/local/tmp/")).thenReturn(true);
            when(mMockDevice.isDirectory("/data/local/tmp/")).thenReturn(true);
            // expect a pushFile() to be done with the appended file name.
            when(mMockDevice.pushDir(
                            Mockito.eq(testFile), Mockito.eq("/data/local/tmp/"), Mockito.any()))
                    .thenReturn(Boolean.TRUE);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /** Test when we attempt to push a directory but the receiving location is an existing file. */
    @Test
    public void testPushDir_RemoteIsFile() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.mkdir();
            info.setFile("perf_test", testFile, "v1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/file");
            when(mMockDevice.doesFileExist("/data/local/tmp/file")).thenReturn(true);
            when(mMockDevice.isDirectory("/data/local/tmp/file")).thenReturn(false);

            mTestInfo.getContext().addDeviceBuildInfo("device", info);
            try {
                mPreparer.setUp(mTestInfo);
                fail("Should have thrown an exception.");
            } catch (TargetSetupError expected) {
                // Expected
            }
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /**
     * Test pushing a file to remote dir. If there are multiple files push to the same place, the
     * latest win.
     */
    @Test
    public void testRemotePush_override() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.createNewFile();
            File testFile2 = new File(testsDir, "perf_test2");
            testFile2.createNewFile();
            info.setFile("perf_test", testFile, "v1");
            info.setFile("perf_test2", testFile2, "v1");
            mOptionSetter.setOptionValue("push-file", "perf_test", "/data/local/tmp/perf_test");
            mOptionSetter.setOptionValue("push-file", "perf_test2", "/data/local/tmp/perf_test");
            when(mMockDevice.isDirectory(Mockito.any())).thenReturn(false);
            // the latest config win.
            when(mMockDevice.pushFile(
                            Mockito.eq(testFile2), Mockito.eq("/data/local/tmp/perf_test")))
                    .thenReturn(Boolean.TRUE);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /**
     * Test pushing a file to remote dir. If both push and push-file push to the same remote file,
     * the push-file win.
     */
    @Test
    public void testPushFileAndPush_override() throws Exception {
        BuildInfo info = new BuildInfo();
        File testsDir = FileUtil.createTempDir("tests_dir");
        try {
            File testFile = new File(testsDir, "perf_test");
            testFile.createNewFile();
            File testFile2 = new File(testsDir, "perf_test2");
            testFile2.createNewFile();
            info.setFile("perf_test", testFile, "v1");
            info.setFile("perf_test2", testFile2, "v1");

            mOptionSetter.setOptionValue("push-file", "perf_test2", "/data/local/tmp/perf_test");
            mOptionSetter.setOptionValue("push", "perf_test->/data/local/tmp/perf_test");
            when(mMockDevice.isDirectory(Mockito.any())).thenReturn(false);
            // the latest config win.
            when(mMockDevice.pushFile(
                            Mockito.eq(testFile2), Mockito.eq("/data/local/tmp/perf_test")))
                    .thenReturn(Boolean.TRUE);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testWarnOnFailure() throws Exception {
        mOptionSetter.setOptionValue("push", "/bin/sh->/noexist/");
        mOptionSetter.setOptionValue("post-push", "ls /");
        mOptionSetter.setOptionValue("abort-on-push-failure", "false");

        // expect a pushFile() call and return false (failed)
        when(mMockDevice.pushFile((File) Mockito.any(), Mockito.eq("/noexist/")))
                .thenReturn(Boolean.FALSE);
        // Because we're only warning, the post-push command should be run despite the push failures
        when(mMockDevice.executeShellCommand(Mockito.eq("ls /"))).thenReturn("");

        mTestInfo.getContext().addDeviceBuildInfo("device", new BuildInfo());
        // Don't expect any exceptions to be thrown
        mPreparer.setUp(mTestInfo);
    }

    /**
     * Test {@link PushFilePreparer#resolveRelativeFilePath(IBuildInfo, String)} do not search
     * additional tests directory if the given build if is not of IBuildInfo type.
     */
    @Test
    public void testResolveRelativeFilePath_noDeviceBuildInfo() {
        IBuildInfo buildInfo = mock(IBuildInfo.class);
        String fileName = "source_file";
        when(buildInfo.getFile(fileName)).thenReturn(null);

        assertNull(mPreparer.resolveRelativeFilePath(buildInfo, fileName));
        InOrder inOrder = Mockito.inOrder(buildInfo);
        inOrder.verify(buildInfo).getFile(fileName);
    }

    /**
     * Test {@link PushFilePreparer#resolveRelativeFilePath(IBuildInfo, String)} can locate a source
     * file existed in tests directory of a device build.
     */
    @Test
    public void testResolveRelativeFilePath_withDeviceBuildInfo() throws Exception {
        IDeviceBuildInfo buildInfo = mock(IDeviceBuildInfo.class);
        String fileName = "source_file";

        File testsDir = null;
        try {
            testsDir = FileUtil.createTempDir("tests_dir");
            File hostTestCasesDir = FileUtil.getFileForPath(testsDir, HOST_TESTCASES);
            FileUtil.mkdirsRWX(hostTestCasesDir);
            File sourceFile = FileUtil.createTempFile(fileName, null, hostTestCasesDir);

            fileName = sourceFile.getName();
            when(buildInfo.getFile(fileName)).thenReturn(null);
            when(buildInfo.getTestsDir()).thenReturn(testsDir);
            when(buildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);

            assertEquals(
                    sourceFile.getAbsolutePath(),
                    mPreparer.resolveRelativeFilePath(buildInfo, fileName).getAbsolutePath());
            InOrder inOrder = Mockito.inOrder(buildInfo);
            inOrder.verify(buildInfo).getFile(fileName);
            inOrder.verify(buildInfo).getTestsDir();
            inOrder.verify(buildInfo).getFile(BuildInfoFileKey.TARGET_LINKED_DIR);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /**
     * Test {@link PushFilePreparer#resolveRelativeFilePath(IBuildInfo, String)} can locate a source
     * file existed in a remote zip of a device build.
     */
    @Test
    public void testResolveRelativeFilePath_withDeviceBuildInfo_remoteZip() throws Exception {
        IDeviceBuildInfo buildInfo = mock(IDeviceBuildInfo.class);
        String fileName = "source_file";

        File testsDir = null;
        try {
            testsDir = FileUtil.createTempDir("tests_dir");
            File hostTestCasesDir = FileUtil.getFileForPath(testsDir, HOST_TESTCASES);
            FileUtil.mkdirsRWX(hostTestCasesDir);
            File sourceFile = FileUtil.createTempFile(fileName, null, hostTestCasesDir);

            // Change the file name so direct file search will return null.
            fileName = sourceFile.getName() + "-2";
            when(buildInfo.getFile(fileName)).thenReturn(null);
            when(buildInfo.getTestsDir()).thenReturn(testsDir);
            when(buildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(buildInfo.stageRemoteFile(Mockito.eq(fileName), Mockito.eq(testsDir)))
                    .thenReturn(sourceFile);

            assertEquals(
                    sourceFile.getAbsolutePath(),
                    mPreparer.resolveRelativeFilePath(buildInfo, fileName).getAbsolutePath());
            InOrder inOrder = Mockito.inOrder(buildInfo);
            inOrder.verify(buildInfo).getFile(fileName);
            inOrder.verify(buildInfo).getTestsDir();
            inOrder.verify(buildInfo).getFile(BuildInfoFileKey.TARGET_LINKED_DIR);
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /**
     * If a folder is found match it first and push it while filtering the abi that are not
     * considered.
     */
    @Test
    public void testPush_abiDirectory_noBitness() throws Exception {
        mOptionSetter.setOptionValue("push", "debugger->/data/local/tmp/debugger");
        mPreparer.setAbi(new Abi("x86", "32"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File debuggerFile = new File(tmpFolder, "target/testcases/debugger/x86/debugger");
            FileUtil.mkdirsRWX(debuggerFile);

            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/debugger")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/debugger\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/debugger")),
                            Mockito.eq("/data/local/tmp/debugger"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_abiDirectory_noBitness_withModule() throws Exception {
        mOptionSetter.setOptionValue("push", "folder->/data/local/tmp/folder");
        mPreparer.setAbi(new Abi("x86", "32"));
        mPreparer.setInvocationContext(createModuleWithName("debugger"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File debuggerFile = new File(tmpFolder, "target/testcases/debugger/x86/folder");
            FileUtil.mkdirsRWX(debuggerFile);

            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/folder")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/folder\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/debugger/x86/folder")),
                            Mockito.eq("/data/local/tmp/folder"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /** Test when pushing a directory and the subfolders are abi marked. */
    @Test
    public void testPush_abiDirectory() throws Exception {
        mOptionSetter.setOptionValue("push", "debugger->/data/local/tmp/debugger");
        mPreparer.setAbi(new Abi("x86", "32"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File debuggerFile = new File(tmpFolder, "target/testcases/debugger/x86/debugger32");
            FileUtil.mkdirsRWX(debuggerFile);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/debugger")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/debugger\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/debugger")),
                            Mockito.eq("/data/local/tmp/debugger"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /** Test that if a module name exists we attempt to search it first. */
    @Test
    public void testPush_moduleName_dirs() throws Exception {
        mOptionSetter.setOptionValue("push", "lib64->/data/local/tmp/lib");
        mPreparer.setAbi(new Abi("x86_64", "64"));

        mPreparer.setInvocationContext(createModuleWithName("debugger"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/lib64");
            FileUtil.mkdirsRWX(beforeName);
            File libX86File = new File(tmpFolder, "target/testcases/debugger/x86_64/lib64");
            FileUtil.mkdirsRWX(libX86File);
            File otherLib = new File(tmpFolder, "target/testcases/random/x86_64/lib64");
            FileUtil.mkdirsRWX(otherLib);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/lib")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/lib\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(
                                    new File(tmpFolder, "target/testcases/debugger/x86_64/lib64")),
                            Mockito.eq("/data/local/tmp/lib"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86_64"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_moduleName_files() throws Exception {
        mOptionSetter.setOptionValue("push", "file->/data/local/tmp/file");
        mPreparer.setAbi(new Abi("x86_64", "64"));

        mPreparer.setInvocationContext(createModuleWithName("aaaaa"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/file");
            FileUtil.mkdirsRWX(beforeName.getParentFile());
            beforeName.createNewFile();
            File libX86File = new File(tmpFolder, "target/testcases/debugger/x86_64/file");
            FileUtil.mkdirsRWX(libX86File.getParentFile());
            libX86File.createNewFile();
            File otherLib = new File(tmpFolder, "target/testcases/random/x86_64/file");
            FileUtil.mkdirsRWX(otherLib.getParentFile());
            otherLib.createNewFile();
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.pushFile(
                            Mockito.eq(new File(tmpFolder, "target/testcases/aaaaa/x86_64/file")),
                            Mockito.eq("/data/local/tmp/file")))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /** Test that if multiple files exists, push the one with matching ABI. */
    @Test
    public void testPush_moduleName_files_abi_32bit() throws Exception {
        mOptionSetter.setOptionValue("push", "file->/data/local/tmp/file");
        mPreparer.setAbi(new Abi("x86", "32"));

        mPreparer.setInvocationContext(createModuleWithName("aaaaa"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/file");
            FileUtil.mkdirsRWX(beforeName.getParentFile());
            beforeName.createNewFile();
            File x86File = new File(tmpFolder, "target/testcases/aaaaa/x86/file");
            FileUtil.mkdirsRWX(x86File.getParentFile());
            x86File.createNewFile();
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.pushFile(
                            Mockito.eq(new File(tmpFolder, "target/testcases/aaaaa/x86/file")),
                            Mockito.eq("/data/local/tmp/file")))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /** Test that if multiple files exists, push the one with matching ABI. */
    @Test
    public void testPush_moduleName_files_abi_64bit() throws Exception {
        mOptionSetter.setOptionValue("push", "file->/data/local/tmp/file");
        mPreparer.setAbi(new Abi("x86_64", "64"));

        mPreparer.setInvocationContext(createModuleWithName("aaaaa"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/file");
            FileUtil.mkdirsRWX(beforeName.getParentFile());
            beforeName.createNewFile();
            File x86File = new File(tmpFolder, "target/testcases/aaaaa/x86/file");
            FileUtil.mkdirsRWX(x86File.getParentFile());
            x86File.createNewFile();
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.pushFile(
                            Mockito.eq(new File(tmpFolder, "target/testcases/aaaaa/x86_64/file")),
                            Mockito.eq("/data/local/tmp/file")))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /**
     * Test that if multiple files exists after delayed partial download, push the one with matching
     * ABI.
     */
    @Test
    public void testPush_moduleName_files_abi_delayedDownload() throws Exception {
        mOptionSetter.setOptionValue("push", "file->/data/local/tmp/file");
        mPreparer.setAbi(new Abi("x86", "32"));

        mPreparer.setInvocationContext(createModuleWithName("aaaaa"));
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        IDeviceBuildInfo info =
                new DeviceBuildInfo() {
                    @Override
                    public File stageRemoteFile(String fileName, File workingDir) {
                        try {
                            File file64 =
                                    new File(tmpFolder, "target/testcases/aaaaa/x86_64/file");
                            FileUtil.mkdirsRWX(file64.getParentFile());
                            file64.createNewFile();
                            File file32 = new File(tmpFolder, "target/testcases/aaaaa/x86/file");
                            FileUtil.mkdirsRWX(file32.getParentFile());
                            file32.createNewFile();
                            // Return the file with mismatched ABI.
                            return file64;
                        } catch (IOException e) {
                            return null;
                        }
                    }
                };
        try {
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.pushFile(
                            Mockito.eq(new File(tmpFolder, "target/testcases/aaaaa/x86/file")),
                            Mockito.eq("/data/local/tmp/file")))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_moduleName_ignored() throws Exception {
        mOptionSetter.setOptionValue("push", "lib64->/data/local/tmp/lib");
        mPreparer.setAbi(new Abi("x86_64", "64"));

        mPreparer.setInvocationContext(createModuleWithName("debugger"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/lib64");
            FileUtil.mkdirsRWX(beforeName);
            File libX86File = new File(tmpFolder, "target/testcases/debugger/x86_64/minilib64");
            FileUtil.mkdirsRWX(libX86File);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/lib")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/lib\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            // Use the only dir matching the file, regardless of the module if the module doesn't
            // contain the file.
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/aaaaa/x86_64/lib64")),
                            Mockito.eq("/data/local/tmp/lib"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86_64"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_moduleName_multiAbi() throws Exception {
        mOptionSetter.setOptionValue("push", "lib64->/data/local/tmp/lib");
        mPreparer.setAbi(new Abi("x86", "32"));

        mPreparer.setInvocationContext(createModuleWithName("debugger"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/aaaaa/x86_64/lib64");
            FileUtil.mkdirsRWX(beforeName);
            File libX86File = new File(tmpFolder, "target/testcases/debugger/lib64");
            FileUtil.mkdirsRWX(libX86File);
            File otherLib = new File(tmpFolder, "target/testcases/debugger/lib32");
            FileUtil.mkdirsRWX(otherLib);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/lib")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/lib\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/debugger/lib64")),
                            Mockito.eq("/data/local/tmp/lib"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_moduleName_multiabi_files() throws Exception {
        mOptionSetter.setOptionValue("push", "debugger->/data/local/tmp/debugger");
        mPreparer.setAbi(new Abi("x86", "32"));

        mPreparer.setInvocationContext(createModuleWithName("debugger"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "target/testcases/debugger/x86/debugger");
            FileUtil.mkdirsRWX(beforeName.getParentFile());
            beforeName.createNewFile();
            File libX86File = new File(tmpFolder, "target/testcases/debugger/x86_64/debugger");
            FileUtil.mkdirsRWX(libX86File.getParentFile());
            libX86File.createNewFile();
            File otherLib = new File(tmpFolder, "target/testcases/debugger/arm/debugger");
            FileUtil.mkdirsRWX(otherLib.getParentFile());
            otherLib.createNewFile();
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");

            when(mMockDevice.doesFileExist("/data/local/tmp/debugger")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/debugger\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/debugger")),
                            Mockito.eq("/data/local/tmp/debugger"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /**
     * Ensure that in case we don't find the module directory. We fallback to top level match first
     * and not first found.
     */
    @Test
    public void testPush_moduleName_noMatch() throws Exception {
        mOptionSetter.setOptionValue("push", "lib64->/data/local/tmp/lib");
        mPreparer.setAbi(new Abi("x86_64", "64"));

        mPreparer.setInvocationContext(createModuleWithName("CtsBionicTestCases"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName = new File(tmpFolder, "lib64");
            FileUtil.mkdirsRWX(beforeName);
            File libX86File = new File(tmpFolder, "DATA/lib64");
            FileUtil.mkdirsRWX(libX86File);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/lib")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/lib\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "lib64")),
                            Mockito.eq("/data/local/tmp/lib"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86_64"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    /**
     * Test that if a binary name is repeating in another directory that is searched first we don't
     * use it and do prioritize the module name directory.
     */
    @Test
    public void testPush_moduleName_repeating_name() throws Exception {
        mOptionSetter.setOptionValue(
                "push",
                "propertyinfoserializer_tests->/data/local/tmp/propertyinfoserializer_tests");
        mPreparer.setAbi(new Abi("x86", "32"));

        mPreparer.setInvocationContext(createModuleWithName("propertyinfoserializer_tests"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File beforeName =
                    new File(
                            tmpFolder,
                            "target/testcases/propertyinfoserializer_tests/x86/"
                                    + "propertyinfoserializer_tests");
            FileUtil.mkdirsRWX(beforeName.getParentFile());
            beforeName.createNewFile();
            File libArmFile =
                    new File(
                            tmpFolder,
                            "target/testcases/propertyinfoserializer_tests/arm/"
                                    + "propertyinfoserializer_tests");
            FileUtil.mkdirsRWX(libArmFile.getParentFile());
            libArmFile.createNewFile();
            File otherModule =
                    new File(
                            tmpFolder,
                            "target/testcases/propertyinfoserializer_tests.vendor/arm/"
                                    + "propertyinfoserializer_tests");
            FileUtil.mkdirsRWX(otherModule.getParentFile());
            otherModule.createNewFile();
            File otherModule2 =
                    new File(
                            tmpFolder,
                            "target/testcases/propertyinfoserializer_tests.vendor/x86/"
                                    + "propertyinfoserializer_tests");
            FileUtil.mkdirsRWX(otherModule2.getParentFile());
            otherModule2.createNewFile();
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");

            when(mMockDevice.doesFileExist("/data/local/tmp/propertyinfoserializer_tests"))
                    .thenReturn(false);
            when(mMockDevice.executeShellCommand(
                            "mkdir -p \"/data/local/tmp/propertyinfoserializer_tests\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(
                                    new File(
                                            tmpFolder,
                                            "target/testcases/propertyinfoserializer_tests")),
                            Mockito.eq("/data/local/tmp/propertyinfoserializer_tests"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("x86"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    @Test
    public void testPush_moduleName_multiAbi_repeatedName() throws Exception {
        mOptionSetter.setOptionValue("push", "lib64->/data/local/tmp/lib");
        mPreparer.setAbi(new Abi("arm64-v8a", "64"));

        mPreparer.setInvocationContext(createModuleWithName("bionic"));
        IDeviceBuildInfo info = new DeviceBuildInfo();
        File tmpFolder = FileUtil.createTempDir("push-file-tests-dir");
        try {
            File libX86File =
                    new File(
                            tmpFolder,
                            "target/testcases/bionic/lib64/bionic-loader-test-libs/dt_runpath_y/lib64/arm64/");
            FileUtil.mkdirsRWX(libX86File);
            info.setFile(BuildInfoFileKey.TESTDIR_IMAGE, tmpFolder, "v1");
            when(mMockDevice.doesFileExist("/data/local/tmp/lib")).thenReturn(false);
            when(mMockDevice.executeShellCommand("mkdir -p \"/data/local/tmp/lib\""))
                    .thenReturn("");
            ArgumentCaptor<Set<String>> capture = ArgumentCaptor.forClass(Set.class);
            when(mMockDevice.pushDir(
                            Mockito.eq(new File(tmpFolder, "target/testcases/bionic/lib64")),
                            Mockito.eq("/data/local/tmp/lib"),
                            capture.capture()))
                    .thenReturn(true);
            mTestInfo.getContext().addDeviceBuildInfo("device", info);

            mPreparer.setUp(mTestInfo);

            // The x86 folder was not filtered
            Set<String> capValue = capture.getValue();
            assertFalse(capValue.contains("arm64"));
        } finally {
            FileUtil.recursiveDelete(tmpFolder);
        }
    }

    private IInvocationContext createModuleWithName(String name) {
        IInvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, name);
        return moduleContext;
    }
}
