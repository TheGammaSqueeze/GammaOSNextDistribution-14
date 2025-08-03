/*
 * Copyright (C) 2021 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.processor.internal.root;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class RootProcessorErrorsTest {

  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{true}, {false}});
  }

  private final boolean disableCrossCompilationRootValidation;

  public RootProcessorErrorsTest(boolean disableCrossCompilationRootValidation) {
    this.disableCrossCompilationRootValidation = disableCrossCompilationRootValidation;
  }

  private ImmutableMap<String, String> processorOptions() {
    return ImmutableMap.of(
        "dagger.hilt.disableCrossCompilationRootValidation",
        Boolean.toString(disableCrossCompilationRootValidation));
  }

  @Test
  public void multipleAppRootsTest() {
    Source appRoot1 =
        HiltCompilerTests.javaSource(
            "test.AppRoot1",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public class AppRoot1 extends Hilt_AppRoot1 {}");

    Source appRoot2 =
        HiltCompilerTests.javaSource(
            "test.AppRoot2",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public class AppRoot2 extends Hilt_AppRoot2 {}");

    HiltCompilerTests.hiltCompiler(appRoot1, appRoot2)
        .withProcessorOptions(processorOptions())
        .compile(
            subject -> {
              // This test case should fail independent of disableCrossCompilationRootValidation.
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Cannot process multiple app roots in the same compilation unit: "
                      + "test.AppRoot1, test.AppRoot2");
            });
  }

  @Test
  public void appRootWithTestRootTest() {
    Source appRoot =
        HiltCompilerTests.javaSource(
            "test.AppRoot",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public class AppRoot extends Hilt_AppRoot {}");

    Source testRoot =
        HiltCompilerTests.javaSource(
            "test.TestRoot",
            "package test;",
            "",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "",
            "@HiltAndroidTest",
            "public class TestRoot {}");

    HiltCompilerTests.hiltCompiler(appRoot, testRoot)
        .withProcessorOptions(processorOptions())
        .compile(
            subject -> {
              // This test case should fail independent of disableCrossCompilationRootValidation.
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Cannot process test roots and app roots in the same compilation unit:"
                      + "\n    App root in this compilation unit: test.AppRoot"
                      + "\n    Test roots in this compilation unit: test.TestRoot");
            });
  }
}
