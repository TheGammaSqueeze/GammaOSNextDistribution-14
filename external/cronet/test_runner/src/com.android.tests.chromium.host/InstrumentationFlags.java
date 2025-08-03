/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tests.chromium.host;

public class InstrumentationFlags {
     static final String CHROMIUM_PACKAGE = "org.chromium.native_test";
     static final String NATIVE_TEST_ACTIVITY_KEY = String.format("%s.%s", CHROMIUM_PACKAGE,
             "NativeTestInstrumentationTestRunner.NativeTestActivity");
     static final String RUN_IN_SUBTHREAD_KEY = String.format("%s.%s", CHROMIUM_PACKAGE,
             "NativeTest.RunInSubThread");
     static final String NATIVE_UNIT_TEST_ACTIVITY_KEY = String.format("%s.%s",
             CHROMIUM_PACKAGE,
             "NativeUnitTestActivity");
     static final String COMMAND_LINE_FLAGS_KEY = String.format("%s.%s", CHROMIUM_PACKAGE,
             "NativeTest.CommandLineFlags");
     static final String EXTRA_SHARD_NANO_TIMEOUT_KEY = String.format("%s.%s",
             CHROMIUM_PACKAGE,
             "NativeTestInstrumentationTestRunner.ShardNanoTimeout");
     static final String DUMP_COVERAGE_KEY = String.format("%s.%s", CHROMIUM_PACKAGE,
             "NativeTestInstrumentationTestRunner.DumpCoverage");
     static final String LIBRARY_TO_LOAD_ACTIVITY_KEY = String.format("%s.%s",
             CHROMIUM_PACKAGE,
             "NativeTestInstrumentationTestRunner.LibraryUnderTest");
     static final String STDOUT_FILE_KEY = String.format("%s.%s", CHROMIUM_PACKAGE,
             "NativeTestInstrumentationTestRunner.StdoutFile");
     static final String TEST_RUNNER = String.format("%s/%s", CHROMIUM_PACKAGE,
             "org.chromium.build.gtest_apk.NativeTestInstrumentationTestRunner");
}
