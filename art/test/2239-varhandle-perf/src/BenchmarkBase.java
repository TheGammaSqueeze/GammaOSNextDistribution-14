/*
 * Copyright (C) 2014 The Android Open Source Project
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

public class BenchmarkBase {
  public final String name;

  // Empty constructor.
  public BenchmarkBase(String name) {
    this.name = name;
  }

  // The benchmark code.
  // This function is not used, if both [warmup] and [exercise] are overwritten.
  public void run() throws Throwable { }

  // Runs a short version of the benchmark. By default invokes [run] once.
  public void warmup() throws Throwable {
    run();
  }

  // Exercises the benchmark. By default invokes [run] 10 times.
  public void exercise() throws Throwable {
    for (int i = 0; i < 10; ++i) {
      run();
    }
  }

  // Not measured setup code executed prior to the benchmark runs.
  public void setup() throws Throwable { }

  // Not measures teardown code executed after the benchark runs.
  public void teardown() throws Throwable { }

  // Measures the score for this benchmark by executing it repeatedly until
  // time minimum has been reached.
  protected double measureFor(boolean doWarmup, long timeMinimum) throws Throwable {
    int iter = 0;
    long startTime = System.currentTimeMillis();
    long elapsed = 0;
    while (elapsed < timeMinimum) {
      if (doWarmup) {
        warmup();
      } else {
        exercise();
      }
      elapsed = System.currentTimeMillis() - startTime;
      iter++;
    }
    return 1000.0 * elapsed / iter;
  }

  // Measures the score for the benchmark and returns it.
  public double measure() throws Throwable {
    setup();
    // Warmup for at least 100ms. Discard result.
    measureFor(true, 100);
    // Run the benchmark for at least 1000ms.
    double result = measureFor(false, 1000);
    teardown();
    return result;
  }

  // Allow subclasses to override how the name is printed.
  public String getName() {
    return name;
  }

  public void report() throws Throwable {
    double score = measure();
    System.out.println(getName() + "(RunTime): " + score + " us.");
  }
}