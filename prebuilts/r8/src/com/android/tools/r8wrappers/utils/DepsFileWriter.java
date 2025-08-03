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
package com.android.tools.r8wrappers.utils;

import com.android.tools.r8.InputDependencyGraphConsumer;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DepsFileWriter implements InputDependencyGraphConsumer {

  private final Path dependentFile;
  private final String dependencyOutput;
  private final Set<Path> dependencies = new HashSet<>();

  public DepsFileWriter(Path dependentFile, String dependencyOutput) {
    this.dependentFile = dependentFile;
    this.dependencyOutput = dependencyOutput;
  }

  @Override
  public void accept(Origin dependent, Path dependency) {
    dependencies.add(dependency);
  }

  @Override
  public void finished() {
    List<Path> sorted = new ArrayList<>(dependencies);
    sorted.sort(Path::compareTo);
    Path output = Paths.get(dependencyOutput);
    try (Writer writer =
        Files.newBufferedWriter(
            output,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(escape(dependentFile.toString()));
      writer.write(":");
      for (Path path : sorted) {
        writer.write(" ");
        writer.write(escape(path.toString()));
      }
      writer.write("\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String escape(String filepath) {
    return filepath.replace(" ", "\\ ");
  }
}
