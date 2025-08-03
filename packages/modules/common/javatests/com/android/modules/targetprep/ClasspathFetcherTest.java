/*
 * Copyright (C) 2022  The Android Open Source Project
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

package com.android.modules.targetprep;

import static com.android.modules.targetprep.ClasspathFetcher.DEVICE_JAR_ARTIFACTS_TAG;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import com.android.modules.proto.ClasspathClasses.ClasspathClassesDump;
import com.android.modules.proto.ClasspathClasses.ClasspathEntry;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class ClasspathFetcherTest {

    private static final String SERIAL = "SERIAL";

    @Mock IDeviceBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockTestDevice;

    private TestInformation mTestInfo;

    private String mBootclasspathJarNames = "";
    private String mSystemServerclasspathJarNames = "";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockTestDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockTestDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(false);
        when(mMockTestDevice.executeShellV2Command(eq("echo $BOOTCLASSPATH"))).then(
            invocation -> {
                    return successfulCommandResult(mBootclasspathJarNames, "");
                }
            );
        when(mMockTestDevice.executeShellV2Command(eq("echo $SYSTEMSERVERCLASSPATH"))).then(
            invocation -> {
                    return successfulCommandResult(mSystemServerclasspathJarNames, "");
                }
            );
        when(mMockTestDevice.pullFile(anyString())).then(
            invocation -> {
                final String path = invocation.getArgument(0);
                final File tempFile = File.createTempFile(path, null);

                try (InputStream is =
                        ClasspathFetcherTest.class.getClassLoader().getResourceAsStream(path)) {
                    Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return tempFile;
            }
        );
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockTestDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testSingleArtifactFetcher() throws Exception {
        mBootclasspathJarNames = "LibraryA.jar";
        mSystemServerclasspathJarNames = "LibraryB.jar";
        final ClasspathFetcher fetcher = new ClasspathFetcher();
        fetcher.setUp(mTestInfo);
        assertThat(mTestInfo.properties().containsKey(DEVICE_JAR_ARTIFACTS_TAG)).isTrue();
        fetcher.tearDown(mTestInfo, null);
        assertThat(mTestInfo.properties().containsKey(DEVICE_JAR_ARTIFACTS_TAG)).isFalse();
    }

    @Test
    public void testMultipleArtifactFetchers() throws Exception {
        mBootclasspathJarNames = "LibraryA.jar";
        mSystemServerclasspathJarNames = "LibraryB.jar";
        final ClasspathFetcher fetcher1 = new ClasspathFetcher();
        final ClasspathFetcher fetcher2 = new ClasspathFetcher();

        fetcher1.setUp(mTestInfo);
        fetcher2.setUp(mTestInfo);
        assertThat(mTestInfo.properties().containsKey(DEVICE_JAR_ARTIFACTS_TAG)).isTrue();
        fetcher2.tearDown(mTestInfo, null);
        assertThat(mTestInfo.properties().containsKey(DEVICE_JAR_ARTIFACTS_TAG)).isTrue();
        fetcher1.tearDown(mTestInfo, null);
        assertThat(mTestInfo.properties().containsKey(DEVICE_JAR_ARTIFACTS_TAG)).isFalse();
    }

    @Test
    public void testFetchCorrectBcpClasses() throws Exception {
        mBootclasspathJarNames = "LibraryA.jar";
        mSystemServerclasspathJarNames = "LibraryB.jar";
        final ClasspathFetcher fetcher = new ClasspathFetcher();

        try {
            fetcher.setUp(mTestInfo);

            final File bcpProto = new File(mTestInfo.properties().get(DEVICE_JAR_ARTIFACTS_TAG),
                    ClasspathFetcher.BCP_CLASSES_FILE);
            assertThat(bcpProto.exists()).isTrue();
            ClasspathClassesDump dump =
                    ClasspathClassesDump.parseFrom(new FileInputStream(bcpProto));
            List<ClasspathEntry> entries = dump.getEntriesList();
            assertThat(entries.size()).isEqualTo(1);
            ClasspathEntry entry = entries.get(0);
            assertThat(entry.hasJar()).isTrue();
            assertThat(entry.getJar().getPath()).isEqualTo("LibraryA.jar");
            assertThat(entry.getClassesList().size()).isEqualTo(1);
            assertThat(entry.getClassesList().get(0))
                    .isEqualTo("Lcom/android/modules/targetprep/android/A;");
        } finally {
            fetcher.tearDown(mTestInfo, null);
        }
    }

    @Test
    public void testFetchCorrectSscpClasses() throws Exception {
        mBootclasspathJarNames = "LibraryA.jar";
        mSystemServerclasspathJarNames = "LibraryB.jar";
        final ClasspathFetcher fetcher = new ClasspathFetcher();

        try {
            fetcher.setUp(mTestInfo);

            final File sscpProto = new File(mTestInfo.properties().get(DEVICE_JAR_ARTIFACTS_TAG),
                    ClasspathFetcher.SSCP_CLASSES_FILE);
            assertThat(sscpProto.exists()).isTrue();
            ClasspathClassesDump dump =
                    ClasspathClassesDump.parseFrom(new FileInputStream(sscpProto));
            List<ClasspathEntry> entries = dump.getEntriesList();
            assertThat(entries.size()).isEqualTo(1);
            ClasspathEntry entry = entries.get(0);
            assertThat(entry.hasJar()).isTrue();
            assertThat(entry.getJar().getPath()).isEqualTo("LibraryB.jar");
            assertThat(entry.getClassesList().size()).isEqualTo(1);
            assertThat(entry.getClassesList().get(0))
                .isEqualTo("Lcom/android/modules/targetprep/android/B;");
        } finally {
            fetcher.tearDown(mTestInfo, null);
        }
    }

    private static CommandResult successfulCommandResult(String stdout, String stderr) {
        final CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setExitCode(0);
        result.setStdout(stdout);
        result.setStderr(stderr);
        return result;
    }

}
