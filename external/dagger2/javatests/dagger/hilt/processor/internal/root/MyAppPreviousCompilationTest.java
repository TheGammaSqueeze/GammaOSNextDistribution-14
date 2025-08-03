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

import static com.google.common.truth.Truth.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compileWithKapt;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.javaSource;

import androidx.room.compiler.processing.util.DiagnosticMessage;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import javax.tools.Diagnostic.Kind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MyAppPreviousCompilationTest {

  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{true}, {false}});
  }

  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();

  private final boolean disableCrossCompilationRootValidation;

  public MyAppPreviousCompilationTest(boolean disableCrossCompilationRootValidation) {
    this.disableCrossCompilationRootValidation = disableCrossCompilationRootValidation;
  }

  private ImmutableMap<String, String> processorOptions() {
    return ImmutableMap.of(
        "dagger.hilt.disableCrossCompilationRootValidation",
        Boolean.toString(disableCrossCompilationRootValidation));
  }

  @Test
  public void testRootTest() {
    Source testRoot =
        javaSource(
            "test.TestRoot",
            "package test;",
            "",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "",
            "@HiltAndroidTest",
            "public class TestRoot {}");

    // TODO(danysantiago): Add KSP test once b/288966076 is resolved.
    // This test case should succeed independent of disableCrossCompilationRootValidation.
    compileWithKapt(
        ImmutableList.of(testRoot),
        processorOptions(),
        tempFolderRule,
        result -> assertThat(result.getSuccess()).isTrue());
  }

  @Test
  public void appRootTest() {
    Source appRoot =
        javaSource(
            "test.AppRoot",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public class AppRoot extends Hilt_AppRoot {}");

    // TODO(danysantiago): Add KSP test once b/288966076 is resolved.
    compileWithKapt(
        ImmutableList.of(appRoot),
        processorOptions(),
        tempFolderRule,
        result -> {
          if (disableCrossCompilationRootValidation) {
            assertThat(result.getSuccess()).isTrue();
          } else {
            List<DiagnosticMessage> errors = result.getDiagnostics().get(Kind.ERROR);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMsg())
                .contains(
                    "Cannot process new app roots when there are app roots from a "
                        + "previous compilation unit:"
                        + "\n    App roots in previous compilation unit: "
                        + "dagger.hilt.processor.internal.root.MyAppPreviousCompilation.MyApp"
                        + "\n    App roots in this compilation unit: test.AppRoot");
          }
        });
  }
}
