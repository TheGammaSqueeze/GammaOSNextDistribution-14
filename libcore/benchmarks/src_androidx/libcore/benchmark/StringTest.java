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

package libcore.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringTest {
  @Rule
  public BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Test
  public void stringRepeat_art_x10() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      "hello, world!".repeat(10);
    }
  }

  @Test
  public void timeStringRepeat_art_x100k() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      "hello, world!".repeat(100_000);
    }
  }

  @Test
  public void timeStringRepeat_java_x10() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      repeatStringBuilder("hello, world!", 10);
    }
  }

  @Test
  public void timeStringRepeat_java_x100k() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      repeatStringBuilder("hello, world!", 100_000);
    }
  }

  @Test
  public void stringRepeat_art_x1m_singleChar() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      "h".repeat(1_000_000);
    }
  }

  @Test
  public void stringRepeat_java_x1m_singleChar() {
    final BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      repeatStringBuilder("h", 1_000_000);
    }
  }

  private static String repeatStringBuilder(String s, int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(s);
    }
    return builder.toString();
  }
}
