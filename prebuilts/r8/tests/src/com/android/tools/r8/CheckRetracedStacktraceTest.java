/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.r8;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class CheckRetracedStacktraceTest {

  private static final String FRAME_PREFIX =
      "    at com.example.android.helloactivitywithr8.HelloActivityWithR8.";

  private List<String> getResourceLines(String resource) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(resource)) {
      return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
          .lines()
          .collect(Collectors.toList());
    }
  }

  private String onErrorDebugInfo() throws Exception {
    StringBuilder builder = new StringBuilder("\nAdditional debug info:\n");
    appendResourceContent(builder, "dexdump.txt");
    appendResourceContent(builder, "proguard_dictionary");
    appendResourceContent(builder, "stacktrace.txt");
    appendResourceContent(builder, "retraced-stacktrace.txt");
    return builder.toString();
  }

  private void appendResourceContent(StringBuilder builder, String resource) throws Exception {
    builder.append("==== ").append(resource).append('\n');
    getResourceLines("/" + resource).forEach(l -> builder.append(l).append('\n'));
  }

  @Test
  public void checkRawStacktrace() throws Exception {
    String errorInfo = onErrorDebugInfo();

    List<String> lines = getResourceLines("/stacktrace.txt");
    // In release builds a single frame is present, in debug builds two.
    Assert.assertTrue(errorInfo, 1 < lines.size() && lines.size() <= 3);
    Assert.assertEquals(errorInfo, "java.lang.RuntimeException: error", lines.get(0));
    // The frame lines "at line" is the qualified method and we don't check build hash
    // and PC to allow minor changes to the test app and compiler without breaking this test.
    for (int i = 1; i < lines.size(); i++) {
      String frameLine = lines.get(i);
      int sourceFileStart = frameLine.indexOf('(');
      int lineNumberSeparator = frameLine.indexOf(':');
      int lineNumberEnd = frameLine.lastIndexOf(')');
      int hashInfoSeparator = frameLine.lastIndexOf(' ', lineNumberSeparator);
      Assert.assertTrue(errorInfo, frameLine.startsWith(FRAME_PREFIX));
      Assert.assertEquals(
          errorInfo, "(go/retraceme", frameLine.substring(sourceFileStart, hashInfoSeparator));
      String lineNumberString = frameLine.substring(lineNumberSeparator + 1, lineNumberEnd);
      try {
        int lineNumber = Integer.parseInt(lineNumberString);
      } catch (NumberFormatException e) {
        Assert.fail("Invalid line number: " + lineNumberString + errorInfo);
      }
    }
  }

  @Test
  public void checkRetracedStacktrace() throws Exception {
    String errorInfo = onErrorDebugInfo();

    // Prefix is the qualified class on each line, suffix does not check line numbers to
    // allow minor changes to the test app without breaking this test.
    String suffix = "(HelloActivityWithR8.java";

    List<String> lines = getResourceLines("/retraced-stacktrace.txt");
    int expectedLines = 3;
    Assert.assertEquals(
        "Expected "
            + expectedLines
            + " lines, got: \n=====\n"
            + String.join("\n", lines)
            + "\n====="
            + errorInfo,
        expectedLines,
        lines.size());
    Assert.assertEquals(errorInfo, "java.lang.RuntimeException: error", lines.get(0));
    String line1 = lines.get(1);
    Assert.assertEquals(
        errorInfo, FRAME_PREFIX + "getView" + suffix, line1.substring(0, line1.indexOf(':')));
    String line2 = lines.get(2);
    Assert.assertEquals(
        errorInfo, FRAME_PREFIX + "onCreate" + suffix, line2.substring(0, line2.indexOf(':')));
  }
}
