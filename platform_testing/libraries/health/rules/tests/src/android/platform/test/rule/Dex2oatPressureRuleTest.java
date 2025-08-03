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
package android.platform.test.rule;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static java.util.stream.Collectors.toList;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Unit tests for {@link Dex2oatPressureRule}. */
@RunWith(JUnit4.class)
public class Dex2oatPressureRuleTest {
    @Rule public ExpectedException expectedException = ExpectedException.none();

    private static final Description TEST_DESCRIPTION =
            Description.createTestDescription("Class", "method");

    // The rule under test.
    private Dex2oatPressureRule rule;

    private AtomicBoolean dex2oatIsRunning = new AtomicBoolean();

    @Before
    public void setUp() {
        rule = spy(new Dex2oatPressureRule());
        // A bit of a hack for testing -- the success and duration of a compilation will be coded
        // into the package name. e.g., "success.3000" will mean a successful compilation after 3000
        // ms, and "failure.1000" will mean that the compilation reports failure after 1000 ms.
        // Arbiturary strings will be appended after the duration number to simulate different
        // package names (e.g. "success.1000.some_package").
        doAnswer(
                        invocation -> {
                            dex2oatIsRunning.set(true);
                            String pkg = invocation.getArgument(0);
                            String status = pkg.split("\\.")[0];
                            long duration =
                                    TimeUnit.MILLISECONDS.toMillis(
                                            Long.parseLong(pkg.split("\\.")[1]));
                            SystemClock.sleep(duration);
                            dex2oatIsRunning.set(false);
                            return status;
                        })
                .when(rule)
                .runCompileCommand(any(String.class), any(String.class));
    }

    @Test
    public void testInvokesCompilation() throws Throwable {
        String packageName = "success.20";
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, packageName,
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(Arrays.asList(packageName));
        fakeWhetherDex2oatIsRunningCheck(0);
        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
        verify(rule, times(1)).runCompileCommand(packageName, Dex2oatPressureRule.SPEED_FILTER);
    }

    @Test
    public void testInvokesCompilationCyclically() throws Throwable {
        List<String> packages =
                Arrays.asList("success.10.pkg1", "success.20.pkg2", "success.30.pkg3");
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", packages),
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(packages);
        fakeWhetherDex2oatIsRunningCheck(0);
        rule.apply(createTestStatement(150L), TEST_DESCRIPTION).evaluate();
        ArgumentCaptor<String> compiledPackages = ArgumentCaptor.forClass(String.class);
        verify(rule, atLeastOnce())
                .runCompileCommand(compiledPackages.capture(), any(String.class));
        for (int i = 0; i < compiledPackages.getAllValues().size(); i++) {
            assertThat(compiledPackages.getAllValues().get(i))
                    .isEqualTo(packages.get(i % packages.size()));
        }
    }

    @Test
    public void testSchedulesCompilationUntilTestEnds() throws Throwable {
        List<String> packages =
                Arrays.asList("success.100.pkg1", "success.200.pkg2", "success.300.pkg3");
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", packages),
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(packages);
        fakeWhetherDex2oatIsRunningCheck(0);

        // Stub the stopDex2oat() method to check if there is a compile command running at the end
        // of the test, before asking the rule to stop kicking off dex2oat. There is a tiny chance
        // for a race condition (the check might fall between the kicking off of two compile
        // commands) but given the ~100ms timing granularity this is unlikely to be an issue.
        doAnswer(
                        invocation -> {
                            assertThat(dex2oatIsRunning.get()).isTrue();
                            invocation.callRealMethod();
                            return null;
                        })
                .when(rule)
                .stopDex2oat();
        // Time the test statement to end around the middle of compiling the second package during
        // the second round of compilation.
        rule.apply(createTestStatement(800L), TEST_DESCRIPTION).evaluate();
        assertThat(dex2oatIsRunning.get()).isFalse();
    }

    @Test
    public void testNoAdditionalCompilationIsTriggeredAfterTestFinishes() throws Throwable {
        List<String> packages = Arrays.asList("success.100.pkg1", "success.200.pkg2");
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", packages),
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(packages);
        fakeWhetherDex2oatIsRunningCheck(0);

        // Stub the stopDex2oat() method to do a verification on the packages compiled. No other
        // packages should be compiled after that.
        ArgumentCaptor<String> compiledPackagesWhenTestEnded =
                ArgumentCaptor.forClass(String.class);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            verify(rule, atLeastOnce())
                                    .runCompileCommand(
                                            compiledPackagesWhenTestEnded.capture(),
                                            any(String.class));
                            return null;
                        })
                .when(rule)
                .stopDex2oat();

        rule.apply(createTestStatement(600L), TEST_DESCRIPTION).evaluate();
        ArgumentCaptor<String> compiledPackagesWhenRuleReturns =
                ArgumentCaptor.forClass(String.class);
        verify(rule, atLeastOnce())
                .runCompileCommand(compiledPackagesWhenRuleReturns.capture(), any(String.class));
        assertThat(compiledPackagesWhenRuleReturns.getAllValues())
                .isEqualTo(compiledPackagesWhenTestEnded.getAllValues());
    }

    @Test
    public void testFailedCompilationDoesNotBlockOtherCompilations() throws Throwable {
        List<String> packages = Arrays.asList("failure.10", "success.10");
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", packages),
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(packages);
        fakeWhetherDex2oatIsRunningCheck(0);
        rule.apply(createTestStatement(50L), TEST_DESCRIPTION).evaluate();
        for (String pkg : packages) {
            verify(rule, atLeastOnce()).runCompileCommand(eq(pkg), any(String.class));
        }
    }

    @Test
    public void testWaitsUntilDex2oatIsRunningBeforeTest() throws Throwable {
        String packageName = "success.150";
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, packageName,
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(Arrays.asList(packageName));
        int checksBeforeDex2oatIsRunning = 5;
        fakeWhetherDex2oatIsRunningCheck(checksBeforeDex2oatIsRunning);
        Statement testStatement =
                new Statement() {
                    @Override
                    public void evaluate() {
                        // The check should have been performed unsuccessful count + 1 times.
                        verify(rule, times(checksBeforeDex2oatIsRunning + 1)).dex2oatIsRunning();
                        SystemClock.sleep(100L);
                    }
                };
        rule.apply(testStatement, TEST_DESCRIPTION).evaluate();
    }

    @Test
    public void testCancelsAndThrowsIfDex2oatIsNotRunningAfterTimeout() throws Throwable {
        expectedException.expectMessage("dex2oat still isn't running");

        String packageName = "success.150";
        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, packageName,
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(Arrays.asList(packageName));
        fakeWhetherDex2oatIsRunningCheck(-1);

        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();

        verify(rule, times(1)).stopDex2oat();
    }

    @Test
    public void testValidCompilationFilters() throws Throwable {
        String packageName = "success.20";
        stubInstalledPackages(Arrays.asList(packageName));
        fakeWhetherDex2oatIsRunningCheck(0);
        for (String filter : Dex2oatPressureRule.SUPPORTED_FILTERS_LIST) {
            stubInstrumentationArgs(
                    Dex2oatPressureRule.PACKAGES_OPTION,
                    packageName,
                    Dex2oatPressureRule.COMPILATION_FILTER_OPTION,
                    filter,
                    Dex2oatPressureRule.ENABLE_OPTION,
                    String.valueOf(true));
            rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
            verify(rule, atLeastOnce()).runCompileCommand(any(String.class), eq(filter));
        }
    }

    @Test
    public void testInvalidCompilationFilterThrows() throws Throwable {
        expectedException.expectMessage("Invalid compilation filter");

        String packageName = "success.20";
        stubInstrumentationArgs(
                Dex2oatPressureRule.PACKAGES_OPTION,
                packageName,
                Dex2oatPressureRule.COMPILATION_FILTER_OPTION,
                "not a filter",
                Dex2oatPressureRule.ENABLE_OPTION,
                String.valueOf(true));
        stubInstalledPackages(Arrays.asList(packageName));
        fakeWhetherDex2oatIsRunningCheck(0);
        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
    }

    @Test
    public void testInvalidPackageThrows() throws Throwable {
        List<String> packages = Arrays.asList("success.20.exists", "success.20.nonexistent");
        expectedException.expectMessage(packages.get(1));
        expectedException.expectMessage("not installed");

        stubInstrumentationArgs(Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", packages),
                                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        stubInstalledPackages(Arrays.asList(packages.get(0)));
        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
    }

    @Test
    public void testDisable() throws Throwable {
        String packageName = "success.10";
        stubInstrumentationArgs(
                Dex2oatPressureRule.PACKAGES_OPTION,
                packageName,
                Dex2oatPressureRule.ENABLE_OPTION,
                String.valueOf(false));
        stubInstalledPackages(Arrays.asList(packageName));
        rule.apply(createTestStatement(50L), TEST_DESCRIPTION).evaluate();
        verify(rule, never()).runCompileCommand(any(String.class), any(String.class));
    }

    @Test
    public void testMultipleTestsParsePackageNamesIndependently() throws Throwable {
        List<String> test1PackageNames = Arrays.asList("success.10.test1", "success.20.test1");
        List<String> test2PackageNames = Arrays.asList("success.10.test2", "success.20.test2");
        stubInstalledPackages(
                Stream.of(test1PackageNames, test2PackageNames)
                        .flatMap(l -> l.stream())
                        .collect(toList()));
        fakeWhetherDex2oatIsRunningCheck(0);
        // Run the first test.
        stubInstrumentationArgs(
                Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", test1PackageNames),
                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
        ArgumentCaptor<String> compiledInTest1 = ArgumentCaptor.forClass(String.class);
        verify(rule, atLeastOnce()).runCompileCommand(compiledInTest1.capture(), any(String.class));
        // Run the second test.
        stubInstrumentationArgs(
                Dex2oatPressureRule.PACKAGES_OPTION, String.join(",", test2PackageNames),
                Dex2oatPressureRule.ENABLE_OPTION, String.valueOf(true));
        rule.apply(createTestStatement(10L), TEST_DESCRIPTION).evaluate();
        ArgumentCaptor<String> compiledInBothTests = ArgumentCaptor.forClass(String.class);
        verify(rule, atLeastOnce())
                .runCompileCommand(compiledInBothTests.capture(), any(String.class));
        // Check that compiledInTest1 only has packages for test 1.
        assertThat(compiledInTest1.getAllValues()).isNotEmpty();
        assertThat(compiledInTest1.getAllValues().stream().allMatch(p -> p.endsWith("test1")))
                .isTrue();
        // Check that packages compiled in test2 only has packages for test 2.
        List<String> compiledInTest2 =
                compiledInBothTests
                        .getAllValues()
                        .subList(
                                compiledInTest1.getAllValues().size(),
                                compiledInBothTests.getAllValues().size());
        assertThat(compiledInTest2).isNotEmpty();
        assertThat(compiledInTest2.stream().allMatch(p -> p.endsWith("test2"))).isTrue();
    }

    @Test
    public void testCompilationCommandCompiles() throws Throwable {
        boolean foundDex2oatPid = false;
        Dex2oatPressureRule realRule = new Dex2oatPressureRule();
        SimpleDateFormat logcatTimestampFormatter = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String startLogcatAt =
                logcatTimestampFormatter.format(new Date(System.currentTimeMillis()));

        // Run the real compilation method on the package under test (which is guaranteed to always
        // exist on the device), which does not interfere with the currently running test.
        String packageName = InstrumentationRegistry.getContext().getPackageName();
        realRule.runCompileCommand(packageName, Dex2oatPressureRule.SPEED_FILTER);

        // Dump the logcat since before the compile command, and check if there are logs about
        // compiling the test package.
        File logcatFile = File.createTempFile("logcat", ".txt");
        logcatFile.deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(Arrays.asList("logcat", "-t", startLogcatAt));
        pb.redirectOutput(logcatFile);
        Process proc = pb.start();
        proc.waitFor();

        List<String> logcat = Files.readAllLines(logcatFile.toPath());
        assertWithMessage(
                        String.format(
                                "Expected a logcat line that mentioned dex2oat, the package to "
                                        + "compile and the compilation filter, but got:\n%s",
                                String.join("\n", logcat)))
                .that(
                        logcat.stream()
                                .map(
                                        line ->
                                                line.contains("dex2oat")
                                                        && line.contains(packageName)
                                                        && line.contains(
                                                                Dex2oatPressureRule.SPEED_FILTER))
                                .collect(toList()))
                .contains(true);
    }

    private Statement createTestStatement(long testDurationMs) {
        return new Statement() {
            @Override
            public void evaluate() {
                SystemClock.sleep(TimeUnit.MILLISECONDS.toMillis(testDurationMs));
            }
        };
    }

    /**
     * Stub instrumentation args for the rule under test. The arguments should be an alternating
     * list of keys and values.
     */
    private void stubInstrumentationArgs(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Please supply keys and values in pairs.");
        }
        Bundle args = new Bundle();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            args.putString(keysAndValues[i], keysAndValues[i + 1]);
        }
        doReturn(args).when(rule).getArguments();
    }

    /**
     * Fake the check to whether dex2oat is running. Argument: the number of attempts before the
     * check returns a positive result, or -1 for never being successful.
     */
    private void fakeWhetherDex2oatIsRunningCheck(int failedAttempts) {
        List<Boolean> returnValues = new ArrayList<>();
        if (failedAttempts == -1) {
            returnValues.add(false);
        } else {
            returnValues.addAll(Collections.nCopies(failedAttempts, false));
            returnValues.add(true);
        }
        doReturn(returnValues.get(0), returnValues.subList(1, returnValues.size()).toArray())
                .when(rule)
                .dex2oatIsRunning();
    }

    private void stubInstalledPackages(Collection<String> packages) {
        doReturn(new HashSet(packages)).when(rule).getInstalledPackages();
    }
}
