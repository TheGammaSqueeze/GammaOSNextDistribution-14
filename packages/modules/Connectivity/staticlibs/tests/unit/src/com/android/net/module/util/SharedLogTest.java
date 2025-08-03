/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.net.module.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SharedLogTest {
    private static final String TIMESTAMP_PATTERN = "\\d{2}:\\d{2}:\\d{2}";
    private static final String TIMESTAMP = "HH:MM:SS";
    private static final String TAG = "top";

    @Test
    public void testBasicOperation() {
        final SharedLog logTop = new SharedLog(TAG);
        assertTrue(TAG.equals(logTop.getTag()));

        logTop.mark("first post!");

        final SharedLog logLevel2a = logTop.forSubComponent("twoA");
        final SharedLog logLevel2b = logTop.forSubComponent("twoB");
        logLevel2b.e("2b or not 2b");
        logLevel2b.e("No exception", null);
        logLevel2b.e("Wait, here's one", new Exception("Test"));
        logLevel2a.w("second post?");

        final SharedLog logLevel3 = logLevel2a.forSubComponent("three");
        logTop.log("still logging");
        logLevel2b.e(new Exception("Got another exception"));
        logLevel3.i("3 >> 2");
        logLevel2a.mark("ok: last post");
        logTop.logf("finished!");

        final String[] expected = {
            " - MARK first post!",
            " - [twoB] ERROR 2b or not 2b",
            " - [twoB] ERROR No exception",
            // No stacktrace in shared log, only in logcat
            " - [twoB] ERROR Wait, here's one: Test",
            " - [twoA] WARN second post?",
            " - still logging",
            " - [twoB] ERROR java.lang.Exception: Got another exception",
            " - [twoA.three] 3 >> 2",
            " - [twoA] MARK ok: last post",
            " - finished!",
        };
        // Verify the logs are all there and in the correct order.
        assertDumpLogs(expected, logTop);

        // In fact, because they all share the same underlying LocalLog,
        // every subcomponent SharedLog's dump() is identical.
        assertDumpLogs(expected, logLevel2a);
        assertDumpLogs(expected, logLevel2b);
        assertDumpLogs(expected, logLevel3);
    }

    private static void assertDumpLogs(String[] expected, SharedLog log) {
        verifyLogLines(expected, dump(log));
        verifyLogLines(reverse(expected), reverseDump(log));
    }

    private static String dump(SharedLog log) {
        return getSharedLogString(pw -> log.dump(null /* fd */, pw, null /* args */));
    }

    private static String reverseDump(SharedLog log) {
        return getSharedLogString(pw -> log.reverseDump(pw));
    }

    private static String[] reverse(String[] ary) {
        final List<String> ls = new ArrayList<>(Arrays.asList(ary));
        Collections.reverse(ls);
        return ls.toArray(new String[ary.length]);
    }

    private static String getSharedLogString(Consumer<PrintWriter> functor) {
        final ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        final PrintWriter pw = new PrintWriter(ostream, true);
        functor.accept(pw);

        final String dumpOutput = ostream.toString();
        assertNotNull(dumpOutput);
        assertFalse("".equals(dumpOutput));
        return dumpOutput;
    }

    private static void verifyLogLines(String[] expected, String gottenLogs) {
        final String[] lines = gottenLogs.split("\n");
        assertEquals(expected.length, lines.length);

        for (int i = 0; i < expected.length; i++) {
            String got = lines[i];
            String want = expected[i];
            assertTrue(String.format("'%s' did not contain '%s'", got, want), got.endsWith(want));
            assertTrue(String.format("'%s' did not contain a %s timestamp", got, TIMESTAMP),
                    got.replaceFirst(TIMESTAMP_PATTERN, TIMESTAMP).contains(TIMESTAMP));
        }
    }
}
