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
package com.android.tradefed.testtype.rust;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Base class of RustBinaryHostTest and RustBinaryTest */
@OptionClass(alias = "rust-test")
public abstract class RustTestBase implements IRemoteTest, ITestFilterReceiver, IAbiReceiver {
    protected class EnvPair {
        public String key;
        public String val;

        public EnvPair(String key, String val) {
            this.key = key;
            this.val = val;
        }
    }

    protected class Invocation {
        public String[] command;
        public ArrayList<EnvPair> env;
        public File workingDir;

        public Invocation(String[] command, ArrayList<EnvPair> env, File workingDir) {
            this.command = command;
            this.env = env;
            this.workingDir = workingDir;
        }
    }

    @Option(
            name = "native-test-flag",
            description = "Option string to be passed to the binary when running")
    private List<String> mTestOptions = new ArrayList<>();

    @Option(
            name = "test-timeout",
            description = "Timeout for a single test file to terminate.",
            isTimeVal = true)
    protected long mTestTimeout = 60 * 1000L; // milliseconds

    @Option(
            name = "is-benchmark",
            description =
                    "Set to true if module is a benchmark. Module is treated as test by default.")
    private boolean mIsBenchmark = false;

    @Option(name = "include-filter", description = "A substr filter of test case names to run.")
    private Set<String> mIncludeFilters = new LinkedHashSet<>();

    @Option(name = "exclude-filter", description = "A substr filter of test case names to skip.")
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    @Option(
            name = "ld-library-path",
            description = "LD_LIBRARY_PATH value to include in the Rust test execution command.")
    private String mLdLibraryPath = null;

    @Option(
            name = "ld-library-path-32",
            description =
                    "LD_LIBRARY_PATH value to include in the Rust test execution command "
                            + "for 32-bit tests. If both `--ld-library-path` and "
                            + "`--ld-library-path-32` are set, only the latter is honored "
                            + "for 32-bit tests.")
    private String mLdLibraryPath32 = null;

    @Option(
            name = "ld-library-path-64",
            description =
                    "LD_LIBRARY_PATH value to include in the Rust test execution command "
                            + "for 64-bit tests. If both `--ld-library-path` and "
                            + "`--ld-library-path-64` are set, only the latter is honored "
                            + "for 64-bit tests.")
    private String mLdLibraryPath64 = null;

    private IAbi mAbi;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    // A wrapper that can be redefined in unit tests to create a (mocked) result parser.
    @VisibleForTesting
    IShellOutputReceiver createParser(ITestInvocationListener listener, String runName) {
        if (!mIsBenchmark) {
            return new RustTestResultParser(listener, runName);
        } else {
            return new RustBenchmarkResultParser(listener, runName);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    /** Find test case names in testList and add them into foundTests. */
    protected void collectTestLines(String[] testList, Set<String> foundTests) {
        // Rust test --list returns "testName: test" for each test.
        // In case of criterion benchmarks it's "benchName: bench".
        final String tag = mIsBenchmark ? ": bench" : ": test";
        int counter = 0;
        for (String line : testList) {
            if (line.endsWith(tag)) {
                counter++;
                foundTests.add(line);
            }
        }
        CLog.d("Found %d tests", counter);
    }

    /** Convert TestBinaryName#TestCaseName to TestCaseName for Rust Test. */
    private String cleanFilter(String filter) {
        return filter.replaceFirst(".*#", "");
    }

    private List<String> getListOfIncludeFilters() {
        if (mIncludeFilters.isEmpty()) {
            // Run test only once without any include filter.
            return new ArrayList<String>(List.of(""));
        }
        return new ArrayList<String>(mIncludeFilters);
    }

    private void addFiltersToArgs(List<String> args, String filter) {
        if (mIsBenchmark) {
            CLog.w("b/294857452 -- filters are not yet supported for rust benchmarks");
            return;
        }
        if (!"".equals(filter)) {
            args.add(cleanFilter(filter));
        }
        args.add("--exact");
        for (String s : mExcludeFilters) {
            args.add("--skip");
            args.add(cleanFilter(s));
        }
    }

    private String ldLibraryPath() {
        if (mLdLibraryPath32 != null && "32".equals(getAbi().getBitness())) {
            return mLdLibraryPath32;
        } else if (mLdLibraryPath64 != null && "64".equals(getAbi().getBitness())) {
            return mLdLibraryPath64;
        } else if (mLdLibraryPath != null) {
            return mLdLibraryPath;
        } else {
            return null;
        }
    }

    protected List<Invocation> generateInvocations(File target) {
        File workingDir = target.getParentFile();

        ArrayList<String> commandTemplate = new ArrayList<>();
        commandTemplate.add(target.getAbsolutePath());
        commandTemplate.addAll(mTestOptions);

        // Criterion does not support these options.
        if (!mIsBenchmark) {
            commandTemplate.add("-Zunstable-options");
            commandTemplate.add("--report-time");
        }

        // Pass parameter to criterion so it performs the benchmarking.
        if (mIsBenchmark) {
            commandTemplate.add("--bench");
            commandTemplate.add("--color");
            commandTemplate.add("never");
        }

        ArrayList<Invocation> out = new ArrayList<>();

        for (String filter : getListOfIncludeFilters()) {
            ArrayList<String> command = new ArrayList<>(commandTemplate);
            addFiltersToArgs(command, filter);
            ArrayList<EnvPair> env = new ArrayList<>();
            env.add(new EnvPair("RUST_BACKTRACE", "full"));
            if (ldLibraryPath() != null) {
                env.add(new EnvPair("LD_LIBRARY_PATH", ldLibraryPath()));
            }
            out.add(new Invocation(command.toArray(new String[0]), env, workingDir));
        }

        return out;
    }
}
