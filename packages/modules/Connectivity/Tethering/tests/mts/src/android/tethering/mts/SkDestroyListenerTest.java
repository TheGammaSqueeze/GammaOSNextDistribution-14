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

package android.tethering.mts;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_STREAM;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.TrafficStats;
import android.os.Build;
import android.os.Process;
import android.system.Os;
import android.util.Pair;

import com.android.net.module.util.BpfDump;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class SkDestroyListenerTest {
    private static final int COOKIE_TAG = 0x1234abcd;
    private static final int SOCKET_COUNT = 100;
    private static final int SOCKET_CLOSE_WAIT_MS = 200;
    private static final String LINE_DELIMITER = "\\n";
    private static final String DUMP_COMMAND = "dumpsys netstats --bpfRawMap --cookieTagMap";

    private Map<CookieTagMapKey, CookieTagMapValue> parseBpfRawMap(final String dump) {
        final Map<CookieTagMapKey, CookieTagMapValue> map = new HashMap<>();
        for (final String line: dump.split(LINE_DELIMITER)) {
            final Pair<CookieTagMapKey, CookieTagMapValue> keyValue =
                    BpfDump.fromBase64EncodedString(CookieTagMapKey.class,
                            CookieTagMapValue.class, line.trim());
            map.put(keyValue.first, keyValue.second);
        }
        return map;
    }

    private int countTaggedSocket() {
        final String dump = runShellCommandOrThrow(DUMP_COMMAND);
        final Map<CookieTagMapKey, CookieTagMapValue> cookieTagMap = parseBpfRawMap(dump);
        int count = 0;
        for (final CookieTagMapValue value: cookieTagMap.values()) {
            if (value.tag == COOKIE_TAG && value.uid == Process.myUid()) {
                count++;
            }
        }
        return count;
    }

    private boolean noTaggedSocket() {
        return countTaggedSocket() == 0;
    }

    private void doTestSkDestroyListener(final int family, final int type) throws Exception {
        assertTrue("There are tagged sockets before test", noTaggedSocket());

        TrafficStats.setThreadStatsTag(COOKIE_TAG);
        final List<FileDescriptor> fds = new ArrayList<>();
        for (int i = 0; i < SOCKET_COUNT; i++) {
            fds.add(Os.socket(family, type, 0 /* protocol */));
        }
        TrafficStats.clearThreadStatsTag();
        assertEquals("Number of tagged socket does not match after creating sockets",
                SOCKET_COUNT, countTaggedSocket());

        for (final FileDescriptor fd: fds) {
            Os.close(fd);
        }
        // Wait a bit for skDestroyListener to handle all the netlink messages.
        Thread.sleep(SOCKET_CLOSE_WAIT_MS);
        assertTrue("There are tagged sockets after closing sockets", noTaggedSocket());
    }

    @Test
    public void testSkDestroyListener() throws Exception {
        doTestSkDestroyListener(AF_INET, SOCK_STREAM);
        doTestSkDestroyListener(AF_INET, SOCK_DGRAM);
        doTestSkDestroyListener(AF_INET6, SOCK_STREAM);
        doTestSkDestroyListener(AF_INET6, SOCK_DGRAM);
    }
}
