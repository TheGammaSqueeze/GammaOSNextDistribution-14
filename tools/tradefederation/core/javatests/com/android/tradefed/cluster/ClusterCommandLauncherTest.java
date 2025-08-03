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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.SubprocessTestResultsParser;
import com.android.tradefed.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link ClusterCommandLauncher}. */
@RunWith(JUnit4.class)
public class ClusterCommandLauncherTest {

    private static final String DEVICE_SERIAL = "device_serial";
    private static final String COMMAND = "host";
    private static final String EMPTY_CONF_CONTENT =
            "<configuration description=\"Empty Config\" />";

    private IRunUtil mMockRunUtil;
    private SubprocessTestResultsParser mMockSubprocessTestResultsParser;
    private TestInformation mMockTestInformation;
    private ITestInvocationListener mMockListener;
    private ITestDevice mMockTestDevice;
    private File mTfPath;
    private File mTfLibDir;
    private File mRootDir;
    private IConfiguration mConfiguration;
    private IInvocationContext mInvocationContext;
    private ClusterCommandLauncher mLauncher;
    private OptionSetter mOptionSetter;

    private File createTempDir(final String key) throws IOException {
        return FileUtil.createTempDir(this.getClass().getName() + "_" + key);
    }

    private String[] asMatchers(String... strs) {
        return Arrays.stream(strs).map(x -> Mockito.eq(x)).toArray(String[]::new);
    }

    @Before
    public void setUp() throws Exception {
        mMockRunUtil = Mockito.mock(IRunUtil.class);
        mMockSubprocessTestResultsParser = Mockito.mock(SubprocessTestResultsParser.class);
        mMockTestInformation = Mockito.mock(TestInformation.class);
        mMockListener = Mockito.mock(ITestInvocationListener.class);
        mMockTestDevice = Mockito.mock(ITestDevice.class);
        Mockito.doReturn(DEVICE_SERIAL).when(mMockTestDevice).getSerialNumber();

        mRootDir = createTempDir("RootDir");
        mTfPath = new File(mRootDir, "TfPath");
        mTfPath.mkdir();
        mTfLibDir = new File(mRootDir, "TfLibDir");
        mTfLibDir.mkdir();
        mConfiguration = new Configuration("name", "description");
        mConfiguration.getCommandOptions().setInvocationTimeout(10000L);
        mInvocationContext = new InvocationContext();
        mLauncher = Mockito.spy(ClusterCommandLauncher.class);
        mLauncher.setConfiguration(mConfiguration);
        mLauncher.setInvocationContext(mInvocationContext);
        mOptionSetter = new OptionSetter(mLauncher);
        mOptionSetter.setOptionValue("cluster:root-dir", mRootDir.getAbsolutePath());
        mOptionSetter.setOptionValue("cluster:env-var", "TF_WORK_DIR", mRootDir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mRootDir);
    }

    @Test
    public void testRun() throws DeviceNotAvailableException, ConfigurationException, IOException {
        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        mInvocationContext.addAllocatedDevice("bar", mMockTestDevice);
        final File tfJar = new File(mRootDir, "foo.jar");
        tfJar.createNewFile();
        final File extraJar = new File(mTfPath, "extra.jar");
        extraJar.createNewFile();
        final String tfPathValue =
                String.format(
                        "${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s",
                        tfJar.getName(), mTfPath.getName(), mTfLibDir.getName());
        final List<String> jars = new ArrayList<>();
        jars.add(tfJar.getAbsolutePath());
        jars.add(extraJar.getAbsolutePath());
        final String classpath = ArrayUtil.join(":", jars);
        mOptionSetter.setOptionValue("cluster:jvm-option", "-Xmx1g");
        mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", tfPathValue);
        mOptionSetter.setOptionValue("cluster:java-property", "FOO", "${TF_WORK_DIR}/foo");
        mOptionSetter.setOptionValue("cluster:command-line", COMMAND);
        final String expandedTfPathValue =
                String.format(
                        "%s:%s:%s",
                        tfJar.getAbsolutePath(),
                        mTfPath.getAbsolutePath(),
                        mTfLibDir.getAbsolutePath());
        final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        Mockito.<String[]>any()))
                .thenReturn(mockCommandResult);
        Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

        mLauncher.run(mMockTestInformation, mMockListener);

        Mockito.verify(mMockRunUtil, Mockito.times(2)).setWorkingDir(mRootDir);
        Mockito.verify(mMockRunUtil).unsetEnvVariable("TF_GLOBAL_CONFIG");
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_WORK_DIR", mRootDir.getAbsolutePath());
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_PATH", expandedTfPathValue);
        Mockito.verify(mMockRunUtil)
                .setEnvVariable("ANDROID_SERIALS", DEVICE_SERIAL + "," + DEVICE_SERIAL);
        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.eq(10000L),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(
                                new String[] {
                                    SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                                    "-cp",
                                    classpath,
                                    "-Xmx1g",
                                    "-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp",
                                    "-DFOO=" + mRootDir.getAbsolutePath() + "/foo",
                                    "com.android.tradefed.command.CommandRunner",
                                    COMMAND,
                                    "--serial",
                                    DEVICE_SERIAL,
                                    "--serial",
                                    DEVICE_SERIAL
                                }));
    }

    @Test
    public void testRun_withTFDeviceCount()
            throws DeviceNotAvailableException, ConfigurationException, IOException {
        mConfiguration = new Configuration("name", "description");
        mConfiguration.getCommandOptions().setInvocationTimeout(10000L);
        mConfiguration.getCommandOptions().setShardCount(1);
        mInvocationContext = new InvocationContext();
        mLauncher = Mockito.spy(ClusterCommandLauncher.class);
        mLauncher.setConfiguration(mConfiguration);
        mLauncher.setInvocationContext(mInvocationContext);
        mOptionSetter = new OptionSetter(mLauncher);
        mOptionSetter.setOptionValue("cluster:root-dir", mRootDir.getAbsolutePath());
        mOptionSetter.setOptionValue("cluster:env-var", "TF_WORK_DIR", mRootDir.getAbsolutePath());

        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        mInvocationContext.addAllocatedDevice("bar", mMockTestDevice);
        final File tfJar = new File(mRootDir, "foo.jar");
        tfJar.createNewFile();
        final File extraJar = new File(mTfPath, "extra.jar");
        extraJar.createNewFile();
        final String tfPathValue =
                String.format(
                        "${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s",
                        tfJar.getName(), mTfPath.getName(), mTfLibDir.getName());
        final List<String> jars = new ArrayList<>();
        jars.add(tfJar.getAbsolutePath());
        jars.add(extraJar.getAbsolutePath());
        final String classpath = ArrayUtil.join(":", jars);
        mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", tfPathValue);
        mOptionSetter.setOptionValue("cluster:java-property", "FOO", "${TF_WORK_DIR}/foo");
        mOptionSetter.setOptionValue("cluster:command-line", "--shard-count ${TF_DEVICE_COUNT}");
        final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        Mockito.<String[]>any()))
                .thenReturn(mockCommandResult);
        Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

        mLauncher.run(mMockTestInformation, mMockListener);

        Mockito.verify(mMockRunUtil).setEnvVariable("TF_DEVICE_COUNT", "2");
        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.eq(10000L),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(
                                new String[] {
                                    SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                                    "-cp",
                                    classpath,
                                    "-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp",
                                    "-DFOO=" + mRootDir.getAbsolutePath() + "/foo",
                                    "com.android.tradefed.command.CommandRunner",
                                    "--shard-count",
                                    "2",
                                    "--serial",
                                    DEVICE_SERIAL,
                                    "--serial",
                                    DEVICE_SERIAL
                                }));
    }

    @Test
    public void testRun_withSetupScripts()
            throws DeviceNotAvailableException, ConfigurationException, IOException {
        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        final File tfJar = new File(mTfPath, "foo.jar");
        tfJar.createNewFile();
        final String classpath = tfJar.getAbsolutePath();
        File scriptFile = new File(mRootDir, "script.py");
        scriptFile.createNewFile();
        scriptFile.setExecutable(false);
        mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", mTfPath.getAbsolutePath());
        mOptionSetter.setOptionValue("cluster:env-var", "FOO", "foo");
        mOptionSetter.setOptionValue("cluster:env-var", "BAR", "bar");
        mOptionSetter.setOptionValue("cluster:env-var", "ZZZ", "zzz");
        mOptionSetter.setOptionValue(
                "cluster:setup-script", scriptFile.getAbsolutePath() + " --args ${FOO}");
        mOptionSetter.setOptionValue("cluster:setup-script", "foo bar zzz");
        mOptionSetter.setOptionValue("cluster:setup-script", "${FOO} ${BAR} ${ZZZ}");
        mOptionSetter.setOptionValue("cluster:command-line", COMMAND);
        final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        Mockito.<String[]>any()))
                .thenReturn(mockCommandResult);
        Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

        mLauncher.run(mMockTestInformation, mMockListener);

        Mockito.verify(mMockRunUtil, Mockito.times(2)).setWorkingDir(mRootDir);
        Mockito.verify(mMockRunUtil).unsetEnvVariable("TF_GLOBAL_CONFIG");
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_WORK_DIR", mRootDir.getAbsolutePath());
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_PATH", mTfPath.getAbsolutePath());
        Mockito.verify(mMockRunUtil).setEnvVariable("BAR", "bar");
        Mockito.verify(mMockRunUtil).setEnvVariable("FOO", "foo");
        Mockito.verify(mMockRunUtil).setEnvVariable("ZZZ", "zzz");
        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(new String[] {scriptFile.getAbsolutePath(), "--args", "foo"}));
        assertTrue(scriptFile.canExecute());
        Mockito.verify(mMockRunUtil, Mockito.times(2))
                .runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(new String[] {"foo", "bar", "zzz"}));
        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.eq(10000L),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(
                                new String[] {
                                    SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                                    "-cp",
                                    classpath,
                                    "-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp",
                                    "com.android.tradefed.command.CommandRunner",
                                    COMMAND,
                                    "--serial",
                                    DEVICE_SERIAL
                                }));
    }

    @Test
    public void testRun_withUseSubprocessReporting()
            throws DeviceNotAvailableException, ConfigurationException, IOException {
        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        // We need to use the real TF path to allow ClusterCommandLauncher to find a config.
        final File tfPath =
                new File(
                        ClusterCommandLauncher.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath());
        File config = FileUtil.createTempFile("empty", ".xml");
        FileUtil.writeToFile(EMPTY_CONF_CONTENT, config);
        try {
            mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", tfPath.getAbsolutePath());
            mOptionSetter.setOptionValue("cluster:command-line", config.getAbsolutePath());
            mOptionSetter.setOptionValue("cluster:use-subprocess-reporting", "true");
            when(mMockSubprocessTestResultsParser.getSocketServerPort()).thenReturn(123);
            Mockito.when(
                            mLauncher.createSubprocessTestResultsParser(
                                    Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
                    .thenReturn(mMockSubprocessTestResultsParser);
            final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
            when(mMockRunUtil.runTimedCmdWithInput(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            Mockito.<File>any(),
                            Mockito.<File>any(),
                            Mockito.<String[]>any()))
                    .thenReturn(mockCommandResult);
            Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

            mLauncher.run(mMockTestInformation, mMockListener);

            String subprocessJar =
                    FileUtil.findFile(mRootDir, "subprocess-results-reporter.jar")
                            .getAbsolutePath();
            Mockito.verify(mMockRunUtil, Mockito.times(2)).setWorkingDir(mRootDir);
            Mockito.verify(mMockRunUtil).unsetEnvVariable("TF_GLOBAL_CONFIG");
            Mockito.verify(mMockRunUtil).setEnvVariable("TF_WORK_DIR", mRootDir.getAbsolutePath());
            Mockito.verify(mMockRunUtil).setEnvVariable("TF_PATH", tfPath.getAbsolutePath());
            Mockito.verify(mMockRunUtil).unsetEnvVariable("TF_GLOBAL_CONFIG");
            Mockito.verify(mMockRunUtil)
                    .runTimedCmdWithInput(
                            Mockito.eq(10000L),
                            Mockito.isNull(),
                            Mockito.<File>any(),
                            Mockito.<File>any(),
                            Mockito.eq(SystemUtil.getRunningJavaBinaryPath().getAbsolutePath()),
                            Mockito.eq("-cp"),
                            Mockito.contains(subprocessJar),
                            Mockito.eq("-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp"),
                            Mockito.eq("com.android.tradefed.command.CommandRunner"),
                            Mockito.eq(config.getAbsolutePath()),
                            Mockito.eq("--serial"),
                            Mockito.eq(DEVICE_SERIAL));
        } finally {
            FileUtil.deleteFile(config);
        }
    }

    @Test
    public void testRun_withJavaOptions()
            throws DeviceNotAvailableException, ConfigurationException, IOException {
        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        final File javaBinary = new File(mRootDir, "jdk/bin/java");
        new File(mRootDir, "jdk/bin").mkdirs();
        javaBinary.createNewFile();
        final File tfJar = new File(mRootDir, "tradefed.jar");
        tfJar.createNewFile();
        mOptionSetter.setOptionValue("cluster:env-var", "JAVA_HOME", "${TF_WORK_DIR}/jdk");
        mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", "${TF_WORK_DIR}/tradefed.jar");
        mOptionSetter.setOptionValue("cluster:jvm-option", "-jvmOption1");
        mOptionSetter.setOptionValue("cluster:jvm-option", "-jvmOption2");
        mOptionSetter.setOptionValue("cluster:jvm-option", "-jvmOption3");
        mOptionSetter.setOptionValue("cluster:java-property", "FOO", "${TF_WORK_DIR}/foo");
        mOptionSetter.setOptionValue("cluster:java-property", "BAR", "${TF_WORK_DIR}/bar");
        mOptionSetter.setOptionValue("cluster:java-property", "ZZZ", "${TF_WORK_DIR}/zzz");
        mOptionSetter.setOptionValue("cluster:command-line", COMMAND);
        final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        Mockito.<String[]>any()))
                .thenReturn(mockCommandResult);
        Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

        mLauncher.run(mMockTestInformation, mMockListener);

        Mockito.verify(mMockRunUtil, Mockito.times(2)).setWorkingDir(mRootDir);
        Mockito.verify(mMockRunUtil).unsetEnvVariable("TF_GLOBAL_CONFIG");
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_WORK_DIR", mRootDir.getAbsolutePath());
        Mockito.verify(mMockRunUtil).setEnvVariable("TF_PATH", tfJar.getAbsolutePath());
        Mockito.verify(mMockRunUtil).setEnvVariable("ANDROID_SERIALS", DEVICE_SERIAL);
        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.eq(10000L),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(
                                new String[] {
                                    javaBinary.getAbsolutePath(),
                                    "-cp",
                                    tfJar.getAbsolutePath(),
                                    "-jvmOption1",
                                    "-jvmOption2",
                                    "-jvmOption3",
                                    "-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp",
                                    "-DFOO=" + mRootDir.getAbsolutePath() + "/foo",
                                    "-DBAR=" + mRootDir.getAbsolutePath() + "/bar",
                                    "-DZZZ=" + mRootDir.getAbsolutePath() + "/zzz",
                                    "com.android.tradefed.command.CommandRunner",
                                    COMMAND,
                                    "--serial",
                                    DEVICE_SERIAL,
                                }));
    }

    @Test
    public void testRun_excludeFileInJavaClasspath()
            throws DeviceNotAvailableException, ConfigurationException, IOException {
        mInvocationContext.addAllocatedDevice("foo", mMockTestDevice);
        final File tfJar = new File(mRootDir, "tradefed.jar");
        tfJar.createNewFile();
        final File fooJar = new File(mTfPath, "foo.jar");
        fooJar.createNewFile();
        // Default excluded file
        final File artJar = new File(mTfPath, "art-run-test.jar");
        artJar.createNewFile();
        // Excluded with template path
        final File bazJar = new File(mTfPath, "baz.jar");
        bazJar.createNewFile();
        final String tfPathValue =
                String.format(
                        "${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s:${TF_WORK_DIR}/%s",
                        tfJar.getName(), mTfPath.getName(), mTfLibDir.getName());
        final String bazJarPath =
                String.format("${TF_WORK_DIR}/%s/%s", mTfPath.getName(), bazJar.getName());
        mOptionSetter.setOptionValue("cluster:env-var", "TF_PATH", tfPathValue);
        mOptionSetter.setOptionValue("cluster:exclude-file-in-java-classpath", bazJarPath);
        mOptionSetter.setOptionValue("cluster:command-line", COMMAND);
        final CommandResult mockCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmdWithInput(
                        Mockito.anyLong(),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        Mockito.<String[]>any()))
                .thenReturn(mockCommandResult);
        Mockito.when(mLauncher.getRunUtil()).thenReturn(mMockRunUtil);

        mLauncher.run(mMockTestInformation, mMockListener);

        Mockito.verify(mMockRunUtil)
                .runTimedCmdWithInput(
                        Mockito.eq(10000L),
                        Mockito.isNull(),
                        Mockito.<File>any(),
                        Mockito.<File>any(),
                        asMatchers(
                                SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                                "-cp",
                                tfJar.getAbsolutePath() + ":" + fooJar.getAbsolutePath(),
                                "-Djava.io.tmpdir=" + mRootDir.getAbsolutePath() + "/tmp",
                                "com.android.tradefed.command.CommandRunner",
                                COMMAND,
                                "--serial",
                                DEVICE_SERIAL));
    }
}
