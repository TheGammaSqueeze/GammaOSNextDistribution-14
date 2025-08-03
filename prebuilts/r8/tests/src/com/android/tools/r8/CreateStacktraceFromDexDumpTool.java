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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CreateStacktraceFromDexDumpTool {

  private static final String CLASS = "com.example.android.helloactivitywithr8.HelloActivityWithR8";
  private static final String SOURCE_FILE_IDX = "source_file_idx";
  private static final String INVOKE_VIRTUAL = ": invoke-virtual";
  private static final String INVOKE_DIRECT = ": invoke-direct";
  private static final String METHOD_ON_CREATE = "onCreate";
  private static final String METHOD_GET_VIEW = "getView";
  private static final String METHOD_GET_LAYOUT_INFLATER = "getLayoutInflater";
  private static final String R8_MARKER_PREFIX = "~~R8{";

  private final List<String> inputLines;
  private final List<String> outputLines = new ArrayList<>();
  private final String sourceFile;

  private CreateStacktraceFromDexDumpTool(List<String> lines) {
    this.inputLines = lines;
    sourceFile = getSourceFile(lines);
    outputLines.add("java.lang.RuntimeException: error");
  }

  // Find the source file line.
  private static String getSourceFile(List<String> lines) {
    for (String line : lines) {
      if (line.contains(SOURCE_FILE_IDX)) {
        // Read <source-file> from line:
        //   source_file_idx   : <idx> (<source-file>)
        int start = line.indexOf('(');
        if (start > 0) {
          int end = line.indexOf(')', start);
          if (end > 0) {
            return line.substring(start + 1, end);
          }
        }
      }
    }
    return "NoSourceFile";
  }

  private static String skipUntil(Iterator<String> iterator, Predicate<String> fn) {
    while (iterator.hasNext()) {
      String next = iterator.next();
      if (fn.test(next)) {
        return next;
      }
    }
    return null;
  }

  private static String skipUntilInMethod(Iterator<String> iterator, Predicate<String> fn) {
    String line = skipUntil(iterator, fn.or(l -> isMethodHeader(l)));
    return line == null || isMethodHeader(line) ? null : line;
  }

  private static boolean isMethodHeader(String line) {
    return line.endsWith("'") && line.contains("name");
  }

  private boolean isDebug() {
    String marker = skipUntil(inputLines.iterator(), l -> l.contains(R8_MARKER_PREFIX));
    return marker != null && marker.contains("\"debug\"");
  }

  private static String mapPcInLineNumberTable(
      Iterator<String> iterator, int invokePcValue, String invokePcString) {
    Map<Integer, String> lineTable = new HashMap<>();
    String lineTableEntry;
    do {
      lineTableEntry = skipUntilInMethod(iterator, line -> line.contains(" line="));
      if (lineTableEntry != null) {
        // Read a line table mapping entry:
        // 0x<addr> line=<linenumber>
        String stripped = lineTableEntry.strip();
        int split = stripped.indexOf(" line=");
        if (split > 0 && stripped.startsWith("0x")) {
          try {
            int pc = Integer.parseInt(stripped.substring(2, split), 16);
            lineTable.put(pc, stripped.substring(split + " line=".length()));
          } catch (NumberFormatException e) {
            return "InvalidLineTablePc";
          }
        }
      }
    } while (lineTableEntry != null);
    // If there is no line number table return the PC as the line.
    if (lineTable.isEmpty()) {
      return invokePcString;
    }
    String lineNumber = lineTable.get(invokePcValue);
    if (lineNumber != null) {
      return lineNumber;
    }
    return "PcNotInLineNumberTable";
  }

  private void addLineFor(String methodName, String invokeType, String invokedMethod) {
    Iterator<String> iterator = inputLines.iterator();
    // Find the method entry.
    if (skipUntil(iterator, line -> line.endsWith("'" + methodName + "'") && isMethodHeader(line))
        == null) {
      outputLines.add("MethodNotFound: " + methodName);
      return;
    }
    // Find the code section.
    if (skipUntilInMethod(iterator, line -> line.contains("insns size")) == null) {
      outputLines.add("InstructionsNotFound: " + methodName);
      return;
    }
    // Find the invoke instruction.
    String invokeLine =
        skipUntilInMethod(
            iterator, line -> line.contains(invokeType) && line.contains(invokedMethod));
    if (invokeLine == null) {
      outputLines.add(
          "InvokeNotFound: " + methodName + " calling " + invokeType + " " + invokedMethod);
      return;
    }
    String invokePcString = "NoPcInfo";
    int invokePcValue = -1;
    // Read <pc> from line:
    // <addr>: <bytes> |<pc>: <invoke-type> {vX}, <type-desc>.<method-name>:<method-desc>;
    int end = invokeLine.indexOf(invokeType);
    if (end > 0) {
      int start = invokeLine.lastIndexOf('|', end);
      if (start > 0) {
        String pcString = invokeLine.substring(start + 1, end);
        try {
          int pc = Integer.parseInt(pcString, 16);
          invokePcValue = pc;
          invokePcString = "" + pc;
        } catch (NumberFormatException e) {
          invokePcString = "PcParseError";
        }
      }
    }
    String lineNumber = mapPcInLineNumberTable(iterator, invokePcValue, invokePcString);
    outputLines.add(
        String.format("    at %s.%s(%s:%s)", CLASS, methodName, sourceFile, lineNumber));
  }

  public static void main(String[] args) throws Exception {
    Path dexdumpPath = Paths.get(args[0]);
    Path outputPath = Paths.get(args[1]);
    List<String> inputLines = Files.readAllLines(dexdumpPath);
    CreateStacktraceFromDexDumpTool tool = new CreateStacktraceFromDexDumpTool(inputLines);
    if (tool.isDebug()) {
      // In debug builds onCreate calls getView which calls getLayoutInflater.
      tool.addLineFor(METHOD_GET_VIEW, INVOKE_VIRTUAL, METHOD_GET_LAYOUT_INFLATER);
      tool.addLineFor(METHOD_ON_CREATE, INVOKE_DIRECT, METHOD_GET_VIEW);
    } else {
      // In release builds getView is inlined away.
      tool.addLineFor(METHOD_ON_CREATE, INVOKE_VIRTUAL, METHOD_GET_LAYOUT_INFLATER);
    }
    Files.write(
        outputPath,
        tool.outputLines,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
