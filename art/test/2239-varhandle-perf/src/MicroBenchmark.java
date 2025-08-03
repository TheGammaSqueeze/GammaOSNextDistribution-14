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

import java.io.PrintStream;

class MicroBenchmark extends BenchmarkBase {
  private static final int EXERCISE_ITERATIONS = 1000;

  MicroBenchmark() {
    super(null);
  }

  @Override
  public String getName() {
    return getClass().getSimpleName();
  }

  @Override
  public void exercise() throws Throwable {
    for (int i = 0; i < EXERCISE_ITERATIONS; ++i) {
      run();
    }
  }

  public int innerIterations() {
      return 1;
  }

  @Override
  public void report() {
    try {
      double microseconds = measure() / (EXERCISE_ITERATIONS * innerIterations());
      System.out.println(getName() + "(RunTimeRaw): " + microseconds + " us.");
    } catch (Throwable t) {
      System.err.println("Exception during the execution of " + getName());
      System.err.println(t);
      t.printStackTrace(new PrintStream(System.err));
      System.exit(1);
    }
  }
}
