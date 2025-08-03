/*
 * Copyright (C) 2007 The Android Open Source Project
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

/**
 * Exercise Object.wait(), comparing results against wall clock time.
 */
public class Main {
    /* delays, in milliseconds */
    private final static long[] DELAYS = {
        200, 500, 1000, 2000, 3500, 8000
    };
    // This test is inherently prone to failures through scheduling delays and spurious wakeups.
    // We try it repeatedly, and check that failures are "rare enough".
    // Currently we go for success on the first try or 2 out of 3.
    private final static int NUM_TRIES = 3;
    private final static int MAX_FAILURES = 1;
    private final static int NANOS_PER_MILLI = 1_000_000;

    // Allow a random scheduling delay of up to 400 msecs.  That value is empirically determined
    // from failure logs, though we do get occasional violations.
    // We seem to get very occasional very long delays on host, perhaps due to getting paged out.
    private final static int MAX_SCHED_MILLIS = 400;

    public static void main(String[] args) {
        boolean timing = (args.length >= 1) && args[0].equals("--timing");
        doit(timing);
    }

    public static void doit(boolean timing) {
        Object sleepy = new Object();

        synchronized (sleepy) {
            try {
                sleepy.wait(-500);
                System.out.println("HEY: didn't throw on negative arg");
            } catch (IllegalArgumentException iae) {
                System.out.println("Caught expected exception on neg arg");
            } catch (InterruptedException ie) {
                ie.printStackTrace(System.out);
            }

            for (long delay : DELAYS) {
                System.out.println("Waiting for " + delay + "ms...");
                long min = delay - 1;
                long max = delay + MAX_SCHED_MILLIS;

                int num_failures = 0;
                long elapsed_to_report = 0;
                boolean showTime = timing;
                for (int i = 0; i < (timing ? 1 : NUM_TRIES); ++i) {
                    final long start = System.nanoTime();
                    try {
                        sleepy.wait(delay);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace(System.out);
                    }
                    final long end = System.nanoTime();

                    long elapsed = (end - start + NANOS_PER_MILLI / 2) / NANOS_PER_MILLI;

                    if (timing) {
                        elapsed_to_report = elapsed;
                    } else {
                        if (elapsed < min || elapsed > max) {
                            ++ num_failures;
                            elapsed_to_report = elapsed;
                        } else if (i == 0) {
                            // Save time if we immediately succeeded.
                            break;
                        }
                    }
                }
                if (num_failures > MAX_FAILURES) {
                    System.out.println("Failed " + num_failures + " times out of "
                            + NUM_TRIES + " tries.");
                    showTime = true;
                    if (elapsed_to_report < min) {
                        // This can legitimately happen due to premature wake-ups.
                        // This seems rare and unexpected enough in practice that we should
                        // still report if it occurs repeatedly.
                        System.out.println("  Elapsed time was too short");
                    } else if (elapsed_to_report > max) {
                        System.out.println("  Elapsed time was too long: "
                             + "elapsed = " + elapsed_to_report + " max = " + max);
                    }
                }
                if (showTime) {
                    System.out.println("  Wall clock elapsed "
                            + elapsed_to_report + "ms");
                }
            }
        }
    }
}
