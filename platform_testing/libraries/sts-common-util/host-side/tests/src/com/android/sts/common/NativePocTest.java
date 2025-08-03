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

package com.android.sts.common;

import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link NativePoc}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class NativePocTest extends BaseHostJUnit4Test {
    private static final IAbi ABI_ARM32 = new Abi("armeabi-v7a", "32");
    private static final IAbi ABI_ARM64 = new Abi("aarch64", "64");
    private static final String POC_NAME = "poc_name";
    private static final String TEST_RESOURCE = "nativepoc.res";
    private static final String REMOTE_POC_FILE = NativePoc.TMP_PATH + POC_NAME;
    private static final CommandResult TIMEOUT_RESULT = new CommandResult(CommandStatus.TIMED_OUT);
    private static final CommandResult SUCCESS_RESULT = new CommandResult(CommandStatus.SUCCESS);
    private static final CommandResult VULN_RESULT = new CommandResult(CommandStatus.FAILED);

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public ExpectedException exceptionRule = ExpectedException.none();
    @Mock private ITestDevice device;

    private BaseHostJUnit4Test testCase;
    private Path testCasesDir;
    private BuildInfo buildInfo;
    private String tmpDir;
    private File localPocFile32;
    private File localPocFile64;

    @BeforeClass
    public static void setupClass() throws Exception {
        VULN_RESULT.setExitCode(113);
        VULN_RESULT.setStderr("stderr");
        VULN_RESULT.setStdout("stdout");
    }

    @Before
    public void setup() throws Exception {
        tmpDir = Files.createTempDirectory("").toFile().getAbsolutePath();
        testCasesDir = Paths.get(tmpDir, "android-sts-host-util-test", "testcases");
        Files.createDirectories(testCasesDir);
        localPocFile32 = new File(testCasesDir.toFile(), POC_NAME + "_sts32");
        localPocFile32.createNewFile();
        localPocFile64 = new File(testCasesDir.toFile(), POC_NAME + "_sts64");
        localPocFile64.createNewFile();

        buildInfo = new BuildInfo("0", "");
        buildInfo.addBuildAttribute("ROOT_DIR", tmpDir);
        buildInfo.addBuildAttribute("SUITE_NAME", "sts-host-util-test");

        InvocationContext iContext = new InvocationContext();
        iContext.addAllocatedDevice("device1", device);
        iContext.addDeviceBuildInfo("device1", buildInfo);

        testCase = new BaseHostJUnit4Test() {};
        testCase.setTestInformation(
                TestInformation.newBuilder().setInvocationContext(iContext).build());
        testCase.setAbi(ABI_ARM32); // Default to 32bit machine. Re-set this if testing 64

        when(device.executeShellV2Command(startsWith("chmod "))).thenReturn(SUCCESS_RESULT);
        when(device.executeShellV2Command(startsWith("test "))).thenReturn(SUCCESS_RESULT);
    }

    @After
    public void cleanup() throws Exception {
        RunUtil.getDefault().runTimedCmd(0, "rm", "-r", tmpDir);
    }

    @Test
    public void testBasicPocExecution() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(SUCCESS_RESULT);

        NativePoc.builder().pocName(POC_NAME).build().run(testCase);

        verifyPocCorrectlyPushed(localPocFile32);
        verify(device)
                .executeShellV2Command(
                        contains(POC_NAME),
                        eq(NativePoc.DEFAULT_POC_TIMEOUT_SECONDS),
                        eq(TimeUnit.SECONDS),
                        eq(0));
        verify(device).deleteFile(REMOTE_POC_FILE);
        verifyNoMoreInteractions(device);
    }

    @Test
    public void testPocExecutionWithArgsAndEnvVarsAndTimeout() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(SUCCESS_RESULT);

        NativePoc.builder()
                .envVars(ImmutableMap.of("VAR1", "val1", "LD_LIBRARY_PATH", "/val2"))
                .pocName(POC_NAME)
                .args("arg1", "arg2")
                .useDefaultLdLibraryPath(true)
                .timeoutSeconds(100)
                .build()
                .run(testCase);

        verifyPocCorrectlyPushed(localPocFile32);
        verify(device)
                .executeShellV2Command(
                        contains(
                                "VAR1='val1' LD_LIBRARY_PATH='/val2:/system/lib64:/system/lib' ./"
                                        + POC_NAME
                                        + " arg1 arg2"),
                        eq(100L),
                        eq(TimeUnit.SECONDS),
                        eq(0));
        verify(device).deleteFile(REMOTE_POC_FILE);
        verifyNoMoreInteractions(device);
    }

    @Test
    public void testPocTimeout() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(TIMEOUT_RESULT);
        exceptionRule.expect(AssumptionViolatedException.class);
        exceptionRule.expectMessage(containsString("PoC timed out"));
        NativePoc.builder().pocName(POC_NAME).timeoutSeconds(100).build().run(testCase);
    }

    @Test
    public void testPocExecutionWithResourceAndAfter() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(SUCCESS_RESULT);

        NativePoc.builder()
                .pocName(POC_NAME)
                .resources(TEST_RESOURCE)
                .resourcePushLocation("/tmp")
                .after(res -> testCase.getDevice().executeShellV2Command("echo EXTRA AFTER CMD"))
                .build()
                .run(testCase);

        verify(device).pushFile(any(), eq("/tmp/" + TEST_RESOURCE));
        verifyPocCorrectlyPushed(localPocFile32);
        verify(device)
                .executeShellV2Command(
                        contains(POC_NAME),
                        eq(NativePoc.DEFAULT_POC_TIMEOUT_SECONDS),
                        eq(TimeUnit.SECONDS),
                        eq(0));
        verify(device).executeShellV2Command("echo EXTRA AFTER CMD");
        verify(device).deleteFile(REMOTE_POC_FILE);
        verify(device).deleteFile("/tmp/" + TEST_RESOURCE);
        verifyNoMoreInteractions(device);
    }

    @Test
    public void test64bit() throws Exception {
        testCase.setAbi(ABI_ARM64);
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(SUCCESS_RESULT);

        NativePoc.builder().pocName(POC_NAME).build().run(testCase);
        verifyPocCorrectlyPushed(localPocFile64);
    }

    @Test
    public void testBadBitness() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(SUCCESS_RESULT);

        exceptionRule.expect(AssumptionViolatedException.class);
        NativePoc.builder().pocName(POC_NAME).only64().build().run(testCase);
    }

    @Test
    public void testAsserter() throws Exception {
        when(device.executeShellV2Command(anyString(), anyLong(), any(), anyInt()))
                .thenReturn(VULN_RESULT);

        exceptionRule.expect(AssertionError.class);
        exceptionRule.expectMessage(containsString("113"));

        NativePoc.builder()
                .pocName(POC_NAME)
                .asserter(NativePocStatusAsserter.assertNotVulnerableExitCode())
                .build()
                .run(testCase);

        verifyPocCorrectlyPushed(localPocFile32);
        verify(device)
                .executeShellV2Command(
                        contains(POC_NAME),
                        eq(NativePoc.DEFAULT_POC_TIMEOUT_SECONDS),
                        eq(TimeUnit.SECONDS),
                        eq(0));
        verify(device).deleteFile(REMOTE_POC_FILE);
        verifyNoMoreInteractions(device);
    }

    private void verifyPocCorrectlyPushed(File poc) throws Exception {
        verify(device).pushFile(poc, REMOTE_POC_FILE);
        verify(device).executeShellV2Command("chmod 777 '/data/local/tmp/" + POC_NAME + "'");
        verify(device).executeShellV2Command("test -r '/data/local/tmp/" + POC_NAME + "'");
        verify(device).executeShellV2Command("test -w '/data/local/tmp/" + POC_NAME + "'");
        verify(device).executeShellV2Command("test -x '/data/local/tmp/" + POC_NAME + "'");
    }
}
