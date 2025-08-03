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

package com.android.helpers.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.MemLeaksHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.Map;

/**
 * Android Unit tests for {@link MemLeaksHelper}.
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.tests.MemLeaksHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class MemLeaksHelperTest {
    private @Spy MemLeaksHelper mMemLeaksHelper;

    @Before
    public void setUp() {
        mMemLeaksHelper = new MemLeaksHelper();
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test the parser works if the dump contains the correct unreachable memory bytes and
     * allocations on test level. Test good process name with matched process name, unreachable
     * memory and allocations. Collect all processes flag is TRUE.
     */
    @Test
    public void testGetMetricsNoIncrease() throws IOException {
        String memLeaksPidSampleOutput =
                "system  25905 410 13715708 78536 do_freezer_trap 0 S com.android.chrome";
        String memPreviousLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 94638627 Realtime: 102961738\n"
                    + "** MEMINFO in pid 25905 [com.android.chrome] **\n"
                    + "                   Pss  Private  Private  SwapPss      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     2024      812        4     1230     4476    19112    "
                    + " 4070     2209\n"
                    + "  Dalvik Heap     4229     1292        0       24    10160    10462    "
                    + " 2270     8192\n"
                    + " Dalvik Other     1208      528        4      326     2736\n"
                    + "        Stack      138      136        0      124      144\n"
                    + "       Ashmem       16        0        0        0      892\n"
                    + "    Other dev       16        0       16        0      288\n"
                    + "     .so mmap     5934      120      732        0    16864\n"
                    + "    .jar mmap      679        0        0        0    25676\n"
                    + "    .apk mmap     1947        0      136        0     8540\n"
                    + "    .dex mmap    10153       20    10096        0    10592\n"
                    + "    .oat mmap     4961        0     2976        0     8240\n"
                    + "    .art mmap     4999      364      104      114    14080\n"
                    + "   Other mmap       97        4        0        0      676\n"
                    + "      Unknown      305      132        0      184      716\n"
                    + "        TOTAL    38708     3408    14068     2002   104080    29574    "
                    + " 6340    10401\n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     1760                          24240\n"
                    + "         Native Heap:      812                           4476\n"
                    + "                Code:    14096                          69980\n"
                    + "               Stack:      136                            144\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:      672\n"
                    + "              System:    21232\n"
                    + "             Unknown:                                    5240\n"
                    + "           TOTAL PSS:    38708            TOTAL RSS:   104080       TOTAL"
                    + " SWAP PSS:     2002\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:        8           Activities:        0\n"
                    + "              Assets:       27        AssetManagers:        0\n"
                    + "       Local Binders:       15        Proxy Binders:       27\n"
                    + "       Parcel memory:        3         Parcel count:       14\n"
                    + "    Death Recipients:        0      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " SQL\n"
                    + "         MEMORY_USED:        0\n"
                    + "  PAGECACHE_OVERFLOW:        0          MALLOC_SIZE:        0\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  1632 bytes in 10 unreachable allocations\n"
                    + "  ABI: 'arm'\n"
                    + "\n"
                    + "  336 bytes unreachable at e31c1350\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e31c1350: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   e31c1360: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  144 bytes unreachable at e86c9430\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e86c9430: 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   e86c9440: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n";

        doReturn(memLeaksPidSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(matches(mMemLeaksHelper.ALL_PROCESS_CMD));

        doReturn(memPreviousLeaksSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 25905)));

        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertFalse(metrics.isEmpty());
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.chrome"));
        assertTrue(metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.chrome").equals(0L));
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.chrome"));
        assertTrue(metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.chrome").equals(0L));
    }

    /**
     * Test the parser works if the dump contains the correct unreachable memory bytes and
     * allocations on test level. Test good process name with matched process name, unreachable
     * memory and allocations. Collect all processes flag is TRUE.
     */
    @Test
    public void testGetMetricsHasIncrease() throws IOException {
        String memLeaksPidSampleOutput =
                "system  25905 410 13715708 78536 do_freezer_trap 0 S com.android.chrome";
        String memPreviousLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 94638627 Realtime: 102961738\n"
                    + "** MEMINFO in pid 25905 [com.android.chrome] **\n"
                    + "                   Pss  Private  Private  SwapPss      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     2024      812        4     1230     4476    19112    "
                    + " 4070     2209\n"
                    + "  Dalvik Heap     4229     1292        0       24    10160    10462    "
                    + " 2270     8192\n"
                    + " Dalvik Other     1208      528        4      326     2736\n"
                    + "        Stack      138      136        0      124      144\n"
                    + "       Ashmem       16        0        0        0      892\n"
                    + "    Other dev       16        0       16        0      288\n"
                    + "     .so mmap     5934      120      732        0    16864\n"
                    + "    .jar mmap      679        0        0        0    25676\n"
                    + "    .apk mmap     1947        0      136        0     8540\n"
                    + "    .dex mmap    10153       20    10096        0    10592\n"
                    + "    .oat mmap     4961        0     2976        0     8240\n"
                    + "    .art mmap     4999      364      104      114    14080\n"
                    + "   Other mmap       97        4        0        0      676\n"
                    + "      Unknown      305      132        0      184      716\n"
                    + "        TOTAL    38708     3408    14068     2002   104080    29574    "
                    + " 6340    10401\n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     1760                          24240\n"
                    + "         Native Heap:      812                           4476\n"
                    + "                Code:    14096                          69980\n"
                    + "               Stack:      136                            144\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:      672\n"
                    + "              System:    21232\n"
                    + "             Unknown:                                    5240\n"
                    + "           TOTAL PSS:    38708            TOTAL RSS:   104080       TOTAL"
                    + " SWAP PSS:     2002\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:        8           Activities:        0\n"
                    + "              Assets:       27        AssetManagers:        0\n"
                    + "       Local Binders:       15        Proxy Binders:       27\n"
                    + "       Parcel memory:        3         Parcel count:       14\n"
                    + "    Death Recipients:        0      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " SQL\n"
                    + "         MEMORY_USED:        0\n"
                    + "  PAGECACHE_OVERFLOW:        0          MALLOC_SIZE:        0\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  1632 bytes in 10 unreachable allocations\n"
                    + "  ABI: 'arm'\n"
                    + "\n"
                    + "  336 bytes unreachable at e31c1350\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e31c1350: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   e31c1360: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  144 bytes unreachable at e86c9430\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e86c9430: 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   e86c9440: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n";

        String memCurrentLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 94638627 Realtime: 102961738\n"
                    + "** MEMINFO in pid 25905 [com.android.chrome] **\n"
                    + "                   Pss  Private  Private  SwapPss      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     2024      812        4     1230     4476    19112    "
                    + " 4070     2209\n"
                    + "  Dalvik Heap     4229     1292        0       24    10160    10462    "
                    + " 2270     8192\n"
                    + " Dalvik Other     1208      528        4      326     2736\n"
                    + "        Stack      138      136        0      124      144\n"
                    + "       Ashmem       16        0        0        0      892\n"
                    + "    Other dev       16        0       16        0      288\n"
                    + "     .so mmap     5934      120      732        0    16864\n"
                    + "    .jar mmap      679        0        0        0    25676\n"
                    + "    .apk mmap     1947        0      136        0     8540\n"
                    + "    .dex mmap    10153       20    10096        0    10592\n"
                    + "    .oat mmap     4961        0     2976        0     8240\n"
                    + "    .art mmap     4999      364      104      114    14080\n"
                    + "   Other mmap       97        4        0        0      676\n"
                    + "      Unknown      305      132        0      184      716\n"
                    + "        TOTAL    38708     3408    14068     2002   104080    29574    "
                    + " 6340    10401\n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     1760                          24240\n"
                    + "         Native Heap:      812                           4476\n"
                    + "                Code:    14096                          69980\n"
                    + "               Stack:      136                            144\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:      672\n"
                    + "              System:    21232\n"
                    + "             Unknown:                                    5240\n"
                    + "           TOTAL PSS:    38708            TOTAL RSS:   104080       TOTAL"
                    + " SWAP PSS:     2002\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:        8           Activities:        0\n"
                    + "              Assets:       27        AssetManagers:        0\n"
                    + "       Local Binders:       15        Proxy Binders:       27\n"
                    + "       Parcel memory:        3         Parcel count:       14\n"
                    + "    Death Recipients:        0      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " SQL\n"
                    + "         MEMORY_USED:        0\n"
                    + "  PAGECACHE_OVERFLOW:        0          MALLOC_SIZE:        0\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  2632 bytes in 20 unreachable allocations\n"
                    + "  ABI: 'arm'\n"
                    + "\n"
                    + "  336 bytes unreachable at e31c1350\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e31c1350: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   e31c1360: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  144 bytes unreachable at e86c9430\n"
                    + "   first 20 bytes of contents:\n"
                    + "   e86c9430: 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   e86c9440: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n";

        doReturn(memLeaksPidSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(matches(mMemLeaksHelper.ALL_PROCESS_CMD));

        doReturn(memPreviousLeaksSampleOutput, memCurrentLeaksSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 25905)));

        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertFalse(metrics.isEmpty());
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.chrome"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.chrome").equals(1000L));
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.chrome"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.chrome").equals(10L));
    }

    /**
     * Test the parser works if the dump does not contain the unreachable memory bytes and
     * allocations. Test good process name with matched process name but missing unreachable memory
     * and allocations. Collect all processes flag is TRUE.
     */
    @Test
    public void testNoUnreachableMemory() throws IOException {
        String memLeaksPidSampleOutput =
                "root 31966   2  0  0 worker_thread   0  S  com.google.android.ims\n";
        String memLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 1137766417 Realtime: 1146089528\n"
                        + "\n"
                        + "** MEMINFO in pid 31966 [com.google.android.ims] **\n"
                        + "                   Pss  Private  Private  SwapPss      Rss     Heap   "
                        + "  Heap     Heap\n"
                        + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                        + " Alloc     Free\n"
                        + "                ------   ------   ------   ------   ------   ------   "
                        + "------   ------\n"
                        + "  Native Heap     1221     1176        0     3280     4732    13400   "
                        + "  5592     2877\n"
                        + "  Dalvik Heap     2942     2788        0     1072    11960    11244   "
                        + "  3052     8192\n"
                        + " Dalvik Other      923      604        0     1336     4068            "
                        + "               \n"
                        + "        Stack      364      364        0      428      376            "
                        + "               \n"
                        + "       Ashmem       15        0        0        0      892            "
                        + "               \n"
                        + "    Other dev       68        0       68        0      364            "
                        + "               \n"
                        + "     .so mmap      631      144        0        0    31308            "
                        + "               \n"
                        + "    .jar mmap      659        0        0        0    29404            "
                        + "               \n"
                        + "    .apk mmap     7026        0     6976        0     8400            "
                        + "               \n"
                        + "    .dex mmap      375       12      356        0     1020            "
                        + "               \n"
                        + "    .oat mmap      237        0        0        0    11792            "
                        + "               \n"
                        + "    .art mmap      720      412        4      224    18772            "
                        + "               \n"
                        + "   Other mmap       23        8        0        0     1244            "
                        + "               \n"
                        + "      Unknown      103       96        0      240     1012            "
                        + "               \n"
                        + "        TOTAL    21887     5604     7404     6580   125344    24644   "
                        + "  8644    11069\n"
                        + " \n"
                        + " App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:     3204                          30732\n"
                        + "         Native Heap:     1176                           4732\n"
                        + "                Code:     7492                          82580\n"
                        + "               Stack:      364                            376\n"
                        + "            Graphics:        0                              0\n"
                        + "       Private Other:      772\n"
                        + "              System:     8879\n"
                        + "             Unknown:                                    6924\n"
                        + " \n"
                        + "           TOTAL PSS:    21887            TOTAL RSS:   125344       "
                        + "TOTAL SWAP PSS:     6580\n"
                        + " \n"
                        + " Objects\n"
                        + "               Views:        0         ViewRootImpl:        0\n"
                        + "         AppContexts:        4           Activities:        0\n"
                        + "              Assets:       24        AssetManagers:        0\n"
                        + "       Local Binders:       37        Proxy Binders:       53\n"
                        + "       Parcel memory:       49         Parcel count:       90\n"
                        + "    Death Recipients:       10      OpenSSL Sockets:        0\n"
                        + "            WebViews:        0\n"
                        + " \n"
                        + " SQL\n"
                        + "         MEMORY_USED:      164\n"
                        + "  PAGECACHE_OVERFLOW:       37          MALLOC_SIZE:       46\n";

        doReturn(memLeaksPidSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(matches(mMemLeaksHelper.ALL_PROCESS_CMD));
        doReturn(memLeaksSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 31966)));

        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertFalse(metrics.isEmpty());
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.ims"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.ims").equals(0L));
        assertTrue(
                metrics.containsKey(mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.ims"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.ims")
                        .equals(0L));
    }

    /**
     * Test the parser works if the dump does not contain the unreachable memory bytes and
     * allocations. Test good process name but missing in output, which is identified as non-java
     * process. We skip non-java process. Collect all processes flag is TRUE.
     */
    @Test
    public void testNoProcessName() throws IOException {
        String memLeaksPidSampleOutput =
                "root          31966     2       0      0 worker_thread       0 S"
                        + " com.google.android.ims\n";
        String memLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 433368844 Realtime: 441691956\n"
                        + "                   Pss  Private  Private     Swap      Rss     Heap   "
                        + "  Heap     Heap\n"
                        + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                        + " Alloc     Free\n"
                        + "                ------   ------   ------   ------   ------   ------   "
                        + "------   ------\n"
                        + "  Native Heap        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + "  Dalvik Heap        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + "      Unknown        0        0        0        0        0            "
                        + "               \n"
                        + "        TOTAL        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + " \n"
                        + " App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:        0                              0\n"
                        + "         Native Heap:        0                              0\n"
                        + "                Code:        0                              0\n"
                        + "               Stack:        0                              0\n"
                        + "            Graphics:        0                              0\n"
                        + "       Private Other:        0\n"
                        + "              System:        0\n"
                        + "             Unknown:                                       0\n"
                        + " \n"
                        + "           TOTAL PSS:        0            TOTAL RSS:        0      "
                        + "TOTAL SWAP (KB):        0\n";

        doReturn(memLeaksPidSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(matches(mMemLeaksHelper.ALL_PROCESS_CMD));
        doReturn(memLeaksSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 31966)));

        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertTrue(metrics.isEmpty());
    }

    /**
     * Test the parser works if the process name enclosed in []. Test enclosed process name. Collect
     * all processes flag is TRUE.
     */
    @Test
    public void testEnclosedProcessName() throws IOException {
        String memLeaksPidSampleOutput =
                "root          8616     2       0      0 worker_thread       0 I [dio/dm-46]\n";
        String memLeaksSampleOutput =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 433368844 Realtime: 441691956\n"
                        + "                   Pss  Private  Private     Swap      Rss     Heap   "
                        + "  Heap     Heap\n"
                        + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                        + " Alloc     Free\n"
                        + "                ------   ------   ------   ------   ------   ------   "
                        + "------   ------\n"
                        + "  Native Heap        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + "  Dalvik Heap        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + "      Unknown        0        0        0        0        0            "
                        + "               \n"
                        + "        TOTAL        0        0        0        0        0        0   "
                        + "     0        0\n"
                        + " \n"
                        + " App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:        0                              0\n"
                        + "         Native Heap:        0                              0\n"
                        + "                Code:        0                              0\n"
                        + "               Stack:        0                              0\n"
                        + "            Graphics:        0                              0\n"
                        + "       Private Other:        0\n"
                        + "              System:        0\n"
                        + "             Unknown:                                       0\n"
                        + " \n"
                        + "           TOTAL PSS:        0            TOTAL RSS:        0      "
                        + "TOTAL SWAP (KB):        0\n";

        doReturn(memLeaksPidSampleOutput)
                .when(mMemLeaksHelper)
                .executeShellCommand(matches(mMemLeaksHelper.ALL_PROCESS_CMD));

        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        // Skip process names enclosed in "[]"
        assertTrue(metrics.isEmpty());
        verify(mMemLeaksHelper, never())
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 8616)));
    }

    /**
     * Test the parser works if the dump contains the correct unreachable memory bytes and
     * allocations. Test good process name with matched process name, unreachable memory and
     * allocations. Collect all processes flag is FALSE and given process names are not empty.
     */
    @Test
    public void testByGivenNamesDiffOff() throws IOException {
        boolean diffOnFlag = false;
        boolean collectAllflag = false;
        String[] procNames =
                new String[] {
                    "com.android.systemui",
                    "com.google.android.apps.scone",
                    "com.google.android.googlequicksearchbox:search"
                };
        String memLeaksPidofSampleOutput1 = "2041\n";
        String memLeaksSampleOutput1 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3116837476 Realtime: 3125160587\n"
                    + "\n"
                    + "** MEMINFO in pid 2041 [com.android.systemui] **\n"
                    + "                   Pss  Private  Private     Swap      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap   112730   112688        0        0   116024   145792  "
                    + " 107632    29137\n"
                    + "  Dalvik Heap    69063    68908        0        0    77540   158551   "
                    + " 60247    98304\n"
                    + " Dalvik Other     5773     4980        0        0     8940                 "
                    + "          \n"
                    + "        Stack     2404     2404        0        0     2416                 "
                    + "          \n"
                    + "       Ashmem      288      148       24        0     7792                 "
                    + "          \n"
                    + "    Other dev       29        0       24        0      384                 "
                    + "          \n"
                    + "     .so mmap     6503      432       12        0    73060                 "
                    + "          \n"
                    + "    .jar mmap     2076        0        0        0    39848                 "
                    + "          \n"
                    + "    .apk mmap    25368       48    12620        0    53836                 "
                    + "          \n"
                    + "    .ttf mmap      813        0      136        0     1604                 "
                    + "          \n"
                    + "    .dex mmap    12541       32        0        0    25696                 "
                    + "          \n"
                    + "    .oat mmap      571        0        0        0    14556                 "
                    + "          \n"
                    + "    .art mmap     3072     2788        0        0    18980                 "
                    + "          \n"
                    + "   Other mmap      279        8      196        0     1936                 "
                    + "          \n"
                    + "   EGL mtrack    54924    54924        0        0    54924                 "
                    + "          \n"
                    + "    GL mtrack    17052    17052        0        0    17052                 "
                    + "          \n"
                    + "      Unknown      937      932        0        0     1588                 "
                    + "          \n"
                    + "        TOTAL   314423   265344    13012        0   516176   304343  "
                    + " 167879   127441\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:    71696                          96520\n"
                    + "         Native Heap:   112688                         116024\n"
                    + "                Code:    13300                         210224\n"
                    + "               Stack:     2404                           2416\n"
                    + "            Graphics:    71976                          71976\n"
                    + "       Private Other:     6292\n"
                    + "              System:    36067\n"
                    + "             Unknown:                                   19016\n"
                    + " \n"
                    + "           TOTAL PSS:   314423            TOTAL RSS:   516176      TOTAL"
                    + " SWAP (KB):        0\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:    21781         ViewRootImpl:        9\n"
                    + "         AppContexts:      514           Activities:        0\n"
                    + "              Assets:       43        AssetManagers:        0\n"
                    + "       Local Binders:      439        Proxy Binders:      151\n"
                    + "       Parcel memory:      167         Parcel count:      275\n"
                    + "    Death Recipients:       25      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:        0\n"
                    + "  PAGECACHE_OVERFLOW:        0          MALLOC_SIZE:        0\n"
                    + " \n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  752 bytes in 5 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  320 bytes unreachable at 7a253d0df0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a253d0df0: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   7a253d0e00: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  24 bytes unreachable at 7985437010\n"
                    + "   referencing 24 unreachable bytes in 1 allocation\n"
                    + "   contents:\n"
                    + "   7985437010: 00 00 00 00 00 00 00 00 00 9c 45 85 79 00 00 b4"
                    + " ..........E.y...\n"
                    + "   7985437020: 83 00 00 00 72 79 5f 66                         ....ry_f\n"
                    + "\n"
                    + "  24 bytes unreachable at 7985459c00\n"
                    + "   referencing 24 unreachable bytes in 1 allocation\n"
                    + "   contents:\n"
                    + "   7985459c00: 10 70 43 85 79 00 00 b4 00 00 00 00 00 00 00 00"
                    + " .pC.y...........\n"
                    + "   7985459c10: ae 00 00 00 00 00 00 00                         ........\n"
                    + "\n";

        String memLeaksPidofSampleOutput2 = "4293\n";
        String memLeaksSampleOutput2 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3120462758 Realtime: 3128785870\n"
                    + "\n"
                    + "** MEMINFO in pid 4293 [com.google.android.apps.scone] **\n"
                    + "                   Pss  Private  Private     Swap      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     3283     3216        0        0     6928    12888    "
                    + " 5254     2274\n"
                    + "  Dalvik Heap     2591     2404        0        0    11308    11492    "
                    + " 3300     8192\n"
                    + " Dalvik Other     2346     1816        0        0     5520                 "
                    + "          \n"
                    + "        Stack      824      824        0        0      836                 "
                    + "          \n"
                    + "       Ashmem       19        0        0        0      892                 "
                    + "          \n"
                    + "    Other dev       20        0       20        0      316                 "
                    + "          \n"
                    + "     .so mmap      732      160        0        0    29384                 "
                    + "          \n"
                    + "    .jar mmap      999        0        0        0    32052                 "
                    + "          \n"
                    + "    .apk mmap     2228        0     2176        0     3432                 "
                    + "          \n"
                    + "    .dex mmap       78       12       56        0      720                 "
                    + "          \n"
                    + "    .oat mmap      290        0        0        0    11792                 "
                    + "          \n"
                    + "    .art mmap     1180      800        0        0    18980                 "
                    + "          \n"
                    + "   Other mmap       30        8        4        0     1192                 "
                    + "          \n"
                    + "      Unknown      382      372        0        0     1264                 "
                    + "          \n"
                    + "        TOTAL    15002     9612     2256        0   124616    24380    "
                    + " 8554    10466\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     3204                          30288\n"
                    + "         Native Heap:     3216                           6928\n"
                    + "                Code:     2404                          78440\n"
                    + "               Stack:      824                            836\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:     2220\n"
                    + "              System:     3134\n"
                    + "             Unknown:                                    8124\n"
                    + " \n"
                    + "           TOTAL PSS:    15002            TOTAL RSS:   124616      TOTAL"
                    + " SWAP (KB):        0\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:       11           Activities:        0\n"
                    + "              Assets:       26        AssetManagers:        0\n"
                    + "       Local Binders:       65        Proxy Binders:       53\n"
                    + "       Parcel memory:       15         Parcel count:       61\n"
                    + "    Death Recipients:       10      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:       84\n"
                    + "  PAGECACHE_OVERFLOW:       25          MALLOC_SIZE:       46\n"
                    + " \n"
                    + " DATABASES\n"
                    + "      pgsz     dbsz   Lookaside(b)          cache  Dbname\n"
                    + "         4       20             35         6/74/3 "
                    + " /data/user_de/0/com.google.android.apps.scone/databases/com.google.modemservice\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  1016 bytes in 3 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  632 bytes unreachable at 7a553d3c90\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a553d3c90: cc ae aa 04 00 00 00 00 88 13 e8 07 00 00 00 00"
                    + " ................\n"
                    + "   7a553d3ca0: 9e 9f 15 08 00 00 00 00 2c bb 17 08 00 00 00 00"
                    + " ........,.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n";

        String memLeaksPidofSampleOutput3 = "8683\n";
        String memLeaksSampleOutput3 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3120310343 Realtime: 3128633455\n"
                    + "\n"
                    + "** MEMINFO in pid 8683 [com.google.android.googlequicksearchbox:search]"
                    + " **\n"
                    + "                   Pss  Private  Private  SwapPss      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     1157     1100        0    13156     4492    29452   "
                    + " 13397    11635\n"
                    + "  Dalvik Heap      927      728       20    26388     9348    34761   "
                    + " 17381    17380\n"
                    + " Dalvik Other      323      232        0     3744     2880                 "
                    + "          \n"
                    + "        Stack      168      168        0     1456      180                 "
                    + "          \n"
                    + "       Ashmem       19        0        0        0      904                 "
                    + "          \n"
                    + "    Other dev      368        0      368        0      692                 "
                    + "          \n"
                    + "     .so mmap     1397      176        8        0    35808                 "
                    + "          \n"
                    + "    .jar mmap     1310        0        0        0    35444                 "
                    + "          \n"
                    + "    .apk mmap    70273      548    31280        0   121536                 "
                    + "          \n"
                    + "    .ttf mmap       27        0        0        0      124                 "
                    + "          \n"
                    + "    .dex mmap    40634       56    40336        0    41512                 "
                    + "          \n"
                    + "    .oat mmap      462        0        0        0    13780                 "
                    + "          \n"
                    + "    .art mmap     1751     1224      156    15636    19200                 "
                    + "          \n"
                    + "   Other mmap     1683        8     1348        0     3592                 "
                    + "          \n"
                    + "      Unknown      202      192        0      968     1048                 "
                    + "          \n"
                    + "        TOTAL   182049     4432    73516    61348   290540    64213   "
                    + " 30778    29015\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     2108                          28548\n"
                    + "         Native Heap:     1100                           4492\n"
                    + "                Code:    72404                         248392\n"
                    + "               Stack:      168                            180\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:     2168\n"
                    + "              System:   104101\n"
                    + "             Unknown:                                    8928\n"
                    + " \n"
                    + "           TOTAL PSS:   182049            TOTAL RSS:   290540       TOTAL"
                    + " SWAP PSS:    61348\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:      184           Activities:        0\n"
                    + "              Assets:       31        AssetManagers:        0\n"
                    + "       Local Binders:      153        Proxy Binders:       94\n"
                    + "       Parcel memory:      122         Parcel count:      775\n"
                    + "    Death Recipients:        5      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:     1097\n"
                    + "  PAGECACHE_OVERFLOW:      336          MALLOC_SIZE:      188\n"
                    + " \n"
                    + " DATABASES\n"
                    + "      pgsz     dbsz   Lookaside(b)          cache  Dbname\n"
                    + "         4       96            105   2697/1253/25 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/no_backup/androidx.work.workdb\n"
                    + "         4        8                         0/0/0    (attached) temp\n"
                    + "         4       96             60       113/29/6 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/no_backup/androidx.work.workdb"
                    + " (2)\n"
                    + "         4       24             36         2/24/4 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/pseudonymous_room_notifications.db\n"
                    + "         4        8                         0/0/0    (attached) temp\n"
                    + "         4      112             82        18/46/5 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db\n"
                    + "         4      112             30        34/44/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (3)\n"
                    + "         4      112             30        37/43/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (4)\n"
                    + "         4      112             42        72/31/3 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (2)\n"
                    + "         4       36             39       87/102/4 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db\n"
                    + "         4       36             24        22/38/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db"
                    + " (2)\n"
                    + "         4       36             36        62/28/3 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db"
                    + " (1)\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  720 bytes in 3 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  336 bytes unreachable at 7a25414c30\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a25414c30: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   7a25414c40: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "j";

        doReturn(memLeaksPidofSampleOutput1)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.PIDOF_CMD, "com.android.systemui")));
        doReturn(memLeaksSampleOutput1)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 2041)));

        doReturn(memLeaksPidofSampleOutput2)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(
                                String.format(
                                        mMemLeaksHelper.PIDOF_CMD,
                                        "com.google.android.apps.scone")));
        doReturn(memLeaksSampleOutput2)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 4293)));

        doReturn(memLeaksPidofSampleOutput3)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(
                                String.format(
                                        mMemLeaksHelper.PIDOF_CMD,
                                        "com.google.android.googlequicksearchbox:search")));
        doReturn(memLeaksSampleOutput3)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 8683)));

        mMemLeaksHelper.setUp(diffOnFlag, collectAllflag, procNames);
        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertTrue(metrics.size() == 6);

        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.systemui"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.systemui").equals(752L));
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.systemui"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.systemui").equals(5L));

        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.apps.scone"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.apps.scone")
                        .equals(1016L));
        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.apps.scone"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.apps.scone")
                        .equals(3L));

        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_MEM_BYTES
                                + "com.google.android.googlequicksearchbox:search"));
        assertTrue(
                metrics.get(
                                mMemLeaksHelper.PROC_MEM_BYTES
                                        + "com.google.android.googlequicksearchbox:search")
                        .equals(720L));
        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_ALLOCATIONS
                                + "com.google.android.googlequicksearchbox:search"));
        assertTrue(
                metrics.get(
                                mMemLeaksHelper.PROC_ALLOCATIONS
                                        + "com.google.android.googlequicksearchbox:search")
                        .equals(3L));
    }

    /**
     * Test the parser works if the dump contains the correct unreachable memory bytes and
     * allocations. Test good process name with matched process name, unreachable memory and
     * allocations. Collect all processes flag is FALSE and given process names are not empty.
     */
    @Test
    public void testByGivenNamesDiffOn() throws IOException {
        boolean diffOnFlag = true;
        boolean collectAllflag = false;
        String[] procNames =
                new String[] {
                    "com.android.systemui",
                    "com.google.android.apps.scone",
                    "com.google.android.googlequicksearchbox:search"
                };
        String memLeaksPidofSampleOutput1 = "2041\n";
        String memLeaksSampleOutput1 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3116837476 Realtime: 3125160587\n"
                    + "\n"
                    + "** MEMINFO in pid 2041 [com.android.systemui] **\n"
                    + "                   Pss  Private  Private     Swap      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap   112730   112688        0        0   116024   145792  "
                    + " 107632    29137\n"
                    + "  Dalvik Heap    69063    68908        0        0    77540   158551   "
                    + " 60247    98304\n"
                    + " Dalvik Other     5773     4980        0        0     8940                 "
                    + "          \n"
                    + "        Stack     2404     2404        0        0     2416                 "
                    + "          \n"
                    + "       Ashmem      288      148       24        0     7792                 "
                    + "          \n"
                    + "    Other dev       29        0       24        0      384                 "
                    + "          \n"
                    + "     .so mmap     6503      432       12        0    73060                 "
                    + "          \n"
                    + "    .jar mmap     2076        0        0        0    39848                 "
                    + "          \n"
                    + "    .apk mmap    25368       48    12620        0    53836                 "
                    + "          \n"
                    + "    .ttf mmap      813        0      136        0     1604                 "
                    + "          \n"
                    + "    .dex mmap    12541       32        0        0    25696                 "
                    + "          \n"
                    + "    .oat mmap      571        0        0        0    14556                 "
                    + "          \n"
                    + "    .art mmap     3072     2788        0        0    18980                 "
                    + "          \n"
                    + "   Other mmap      279        8      196        0     1936                 "
                    + "          \n"
                    + "   EGL mtrack    54924    54924        0        0    54924                 "
                    + "          \n"
                    + "    GL mtrack    17052    17052        0        0    17052                 "
                    + "          \n"
                    + "      Unknown      937      932        0        0     1588                 "
                    + "          \n"
                    + "        TOTAL   314423   265344    13012        0   516176   304343  "
                    + " 167879   127441\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:    71696                          96520\n"
                    + "         Native Heap:   112688                         116024\n"
                    + "                Code:    13300                         210224\n"
                    + "               Stack:     2404                           2416\n"
                    + "            Graphics:    71976                          71976\n"
                    + "       Private Other:     6292\n"
                    + "              System:    36067\n"
                    + "             Unknown:                                   19016\n"
                    + " \n"
                    + "           TOTAL PSS:   314423            TOTAL RSS:   516176      TOTAL"
                    + " SWAP (KB):        0\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:    21781         ViewRootImpl:        9\n"
                    + "         AppContexts:      514           Activities:        0\n"
                    + "              Assets:       43        AssetManagers:        0\n"
                    + "       Local Binders:      439        Proxy Binders:      151\n"
                    + "       Parcel memory:      167         Parcel count:      275\n"
                    + "    Death Recipients:       25      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:        0\n"
                    + "  PAGECACHE_OVERFLOW:        0          MALLOC_SIZE:        0\n"
                    + " \n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  752 bytes in 5 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  320 bytes unreachable at 7a253d0df0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a253d0df0: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   7a253d0e00: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  24 bytes unreachable at 7985437010\n"
                    + "   referencing 24 unreachable bytes in 1 allocation\n"
                    + "   contents:\n"
                    + "   7985437010: 00 00 00 00 00 00 00 00 00 9c 45 85 79 00 00 b4"
                    + " ..........E.y...\n"
                    + "   7985437020: 83 00 00 00 72 79 5f 66                         ....ry_f\n"
                    + "\n"
                    + "  24 bytes unreachable at 7985459c00\n"
                    + "   referencing 24 unreachable bytes in 1 allocation\n"
                    + "   contents:\n"
                    + "   7985459c00: 10 70 43 85 79 00 00 b4 00 00 00 00 00 00 00 00"
                    + " .pC.y...........\n"
                    + "   7985459c10: ae 00 00 00 00 00 00 00                         ........\n"
                    + "\n";

        String memLeaksPidofSampleOutput2 = "4293\n";
        String memLeaksSampleOutput2 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3120462758 Realtime: 3128785870\n"
                    + "\n"
                    + "** MEMINFO in pid 4293 [com.google.android.apps.scone] **\n"
                    + "                   Pss  Private  Private     Swap      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     3283     3216        0        0     6928    12888    "
                    + " 5254     2274\n"
                    + "  Dalvik Heap     2591     2404        0        0    11308    11492    "
                    + " 3300     8192\n"
                    + " Dalvik Other     2346     1816        0        0     5520                 "
                    + "          \n"
                    + "        Stack      824      824        0        0      836                 "
                    + "          \n"
                    + "       Ashmem       19        0        0        0      892                 "
                    + "          \n"
                    + "    Other dev       20        0       20        0      316                 "
                    + "          \n"
                    + "     .so mmap      732      160        0        0    29384                 "
                    + "          \n"
                    + "    .jar mmap      999        0        0        0    32052                 "
                    + "          \n"
                    + "    .apk mmap     2228        0     2176        0     3432                 "
                    + "          \n"
                    + "    .dex mmap       78       12       56        0      720                 "
                    + "          \n"
                    + "    .oat mmap      290        0        0        0    11792                 "
                    + "          \n"
                    + "    .art mmap     1180      800        0        0    18980                 "
                    + "          \n"
                    + "   Other mmap       30        8        4        0     1192                 "
                    + "          \n"
                    + "      Unknown      382      372        0        0     1264                 "
                    + "          \n"
                    + "        TOTAL    15002     9612     2256        0   124616    24380    "
                    + " 8554    10466\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     3204                          30288\n"
                    + "         Native Heap:     3216                           6928\n"
                    + "                Code:     2404                          78440\n"
                    + "               Stack:      824                            836\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:     2220\n"
                    + "              System:     3134\n"
                    + "             Unknown:                                    8124\n"
                    + " \n"
                    + "           TOTAL PSS:    15002            TOTAL RSS:   124616      TOTAL"
                    + " SWAP (KB):        0\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:       11           Activities:        0\n"
                    + "              Assets:       26        AssetManagers:        0\n"
                    + "       Local Binders:       65        Proxy Binders:       53\n"
                    + "       Parcel memory:       15         Parcel count:       61\n"
                    + "    Death Recipients:       10      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:       84\n"
                    + "  PAGECACHE_OVERFLOW:       25          MALLOC_SIZE:       46\n"
                    + " \n"
                    + " DATABASES\n"
                    + "      pgsz     dbsz   Lookaside(b)          cache  Dbname\n"
                    + "         4       20             35         6/74/3 "
                    + " /data/user_de/0/com.google.android.apps.scone/databases/com.google.modemservice\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  1016 bytes in 3 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  632 bytes unreachable at 7a553d3c90\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a553d3c90: cc ae aa 04 00 00 00 00 88 13 e8 07 00 00 00 00"
                    + " ................\n"
                    + "   7a553d3ca0: 9e 9f 15 08 00 00 00 00 2c bb 17 08 00 00 00 00"
                    + " ........,.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n";

        String memLeaksPidofSampleOutput3 = "8683\n";
        String memLeaksSampleOutput3 =
                "Applications Memory Usage (in Kilobytes):\n"
                    + "Uptime: 3120310343 Realtime: 3128633455\n"
                    + "\n"
                    + "** MEMINFO in pid 8683 [com.google.android.googlequicksearchbox:search]"
                    + " **\n"
                    + "                   Pss  Private  Private  SwapPss      Rss     Heap    "
                    + " Heap     Heap\n"
                    + "                 Total    Dirty    Clean    Dirty    Total     Size   "
                    + " Alloc     Free\n"
                    + "                ------   ------   ------   ------   ------   ------  "
                    + " ------   ------\n"
                    + "  Native Heap     1157     1100        0    13156     4492    29452   "
                    + " 13397    11635\n"
                    + "  Dalvik Heap      927      728       20    26388     9348    34761   "
                    + " 17381    17380\n"
                    + " Dalvik Other      323      232        0     3744     2880                 "
                    + "          \n"
                    + "        Stack      168      168        0     1456      180                 "
                    + "          \n"
                    + "       Ashmem       19        0        0        0      904                 "
                    + "          \n"
                    + "    Other dev      368        0      368        0      692                 "
                    + "          \n"
                    + "     .so mmap     1397      176        8        0    35808                 "
                    + "          \n"
                    + "    .jar mmap     1310        0        0        0    35444                 "
                    + "          \n"
                    + "    .apk mmap    70273      548    31280        0   121536                 "
                    + "          \n"
                    + "    .ttf mmap       27        0        0        0      124                 "
                    + "          \n"
                    + "    .dex mmap    40634       56    40336        0    41512                 "
                    + "          \n"
                    + "    .oat mmap      462        0        0        0    13780                 "
                    + "          \n"
                    + "    .art mmap     1751     1224      156    15636    19200                 "
                    + "          \n"
                    + "   Other mmap     1683        8     1348        0     3592                 "
                    + "          \n"
                    + "      Unknown      202      192        0      968     1048                 "
                    + "          \n"
                    + "        TOTAL   182049     4432    73516    61348   290540    64213   "
                    + " 30778    29015\n"
                    + " \n"
                    + " App Summary\n"
                    + "                       Pss(KB)                        Rss(KB)\n"
                    + "                        ------                         ------\n"
                    + "           Java Heap:     2108                          28548\n"
                    + "         Native Heap:     1100                           4492\n"
                    + "                Code:    72404                         248392\n"
                    + "               Stack:      168                            180\n"
                    + "            Graphics:        0                              0\n"
                    + "       Private Other:     2168\n"
                    + "              System:   104101\n"
                    + "             Unknown:                                    8928\n"
                    + " \n"
                    + "           TOTAL PSS:   182049            TOTAL RSS:   290540       TOTAL"
                    + " SWAP PSS:    61348\n"
                    + " \n"
                    + " Objects\n"
                    + "               Views:        0         ViewRootImpl:        0\n"
                    + "         AppContexts:      184           Activities:        0\n"
                    + "              Assets:       31        AssetManagers:        0\n"
                    + "       Local Binders:      153        Proxy Binders:       94\n"
                    + "       Parcel memory:      122         Parcel count:      775\n"
                    + "    Death Recipients:        5      OpenSSL Sockets:        0\n"
                    + "            WebViews:        0\n"
                    + " \n"
                    + " SQL\n"
                    + "         MEMORY_USED:     1097\n"
                    + "  PAGECACHE_OVERFLOW:      336          MALLOC_SIZE:      188\n"
                    + " \n"
                    + " DATABASES\n"
                    + "      pgsz     dbsz   Lookaside(b)          cache  Dbname\n"
                    + "         4       96            105   2697/1253/25 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/no_backup/androidx.work.workdb\n"
                    + "         4        8                         0/0/0    (attached) temp\n"
                    + "         4       96             60       113/29/6 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/no_backup/androidx.work.workdb"
                    + " (2)\n"
                    + "         4       24             36         2/24/4 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/pseudonymous_room_notifications.db\n"
                    + "         4        8                         0/0/0    (attached) temp\n"
                    + "         4      112             82        18/46/5 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db\n"
                    + "         4      112             30        34/44/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (3)\n"
                    + "         4      112             30        37/43/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (4)\n"
                    + "         4      112             42        72/31/3 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/app_si/opa_content_store/content_store.db"
                    + " (2)\n"
                    + "         4       36             39       87/102/4 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db\n"
                    + "         4       36             24        22/38/2 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db"
                    + " (2)\n"
                    + "         4       36             36        62/28/3 "
                    + " /data/user/0/com.google.android.googlequicksearchbox/databases/portable_geller_.db"
                    + " (1)\n"
                    + " \n"
                    + " Unreachable memory\n"
                    + "  720 bytes in 3 unreachable allocations\n"
                    + "  ABI: 'arm64'\n"
                    + "\n"
                    + "  336 bytes unreachable at 7a25414c30\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a25414c30: 88 13 e8 07 00 00 00 00 9e 9f 15 08 00 00 00 00"
                    + " ................\n"
                    + "   7a25414c40: 2c bb 17 08 00 00 00 00 23 8a 89 08 00 00 00 00"
                    + " ,.......#.......\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540adf0\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540adf0: 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540ae00: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "  192 bytes unreachable at 7a0540c830\n"
                    + "   first 20 bytes of contents:\n"
                    + "   7a0540c830: 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "   7a0540c840: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                    + " ................\n"
                    + "\n"
                    + "j";

        doReturn(memLeaksPidofSampleOutput1)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.PIDOF_CMD, "com.android.systemui")));
        doReturn(memLeaksSampleOutput1)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 2041)));

        doReturn(memLeaksPidofSampleOutput2)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(
                                String.format(
                                        mMemLeaksHelper.PIDOF_CMD,
                                        "com.google.android.apps.scone")));
        doReturn(memLeaksSampleOutput2)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 4293)));

        doReturn(memLeaksPidofSampleOutput3)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(
                                String.format(
                                        mMemLeaksHelper.PIDOF_CMD,
                                        "com.google.android.googlequicksearchbox:search")));
        doReturn(memLeaksSampleOutput3)
                .when(mMemLeaksHelper)
                .executeShellCommand(
                        matches(String.format(mMemLeaksHelper.DUMPSYS_MEMIFNO_CMD, 8683)));

        mMemLeaksHelper.setUp(diffOnFlag, collectAllflag, procNames);
        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();

        assertTrue(metrics.size() == 6);

        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.systemui"));
        assertTrue(metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.android.systemui").equals(0L));
        assertTrue(metrics.containsKey(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.systemui"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.android.systemui").equals(0L));

        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.apps.scone"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_MEM_BYTES + "com.google.android.apps.scone")
                        .equals(0L));
        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.apps.scone"));
        assertTrue(
                metrics.get(mMemLeaksHelper.PROC_ALLOCATIONS + "com.google.android.apps.scone")
                        .equals(0L));

        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_MEM_BYTES
                                + "com.google.android.googlequicksearchbox:search"));
        assertTrue(
                metrics.get(
                                mMemLeaksHelper.PROC_MEM_BYTES
                                        + "com.google.android.googlequicksearchbox:search")
                        .equals(0L));
        assertTrue(
                metrics.containsKey(
                        mMemLeaksHelper.PROC_ALLOCATIONS
                                + "com.google.android.googlequicksearchbox:search"));
        assertTrue(
                metrics.get(
                                mMemLeaksHelper.PROC_ALLOCATIONS
                                        + "com.google.android.googlequicksearchbox:search")
                        .equals(0L));
    }

    /**
     * Test the parser works if the dump contains the correct unreachable memory bytes and
     * allocations. Test good process name with matched process name, unreachable memory and
     * allocations. Collect all processes flag is FALSE and given process name is empty.
     */
    @Test
    public void testByGivenEmptyNames() throws IOException {
        boolean diffOnFlag = true;
        boolean collectAllflag = false;
        String[] procNames = new String[] {};

        mMemLeaksHelper.setUp(diffOnFlag, collectAllflag, procNames);
        assertTrue(mMemLeaksHelper.startCollecting());
        Map<String, Long> metrics = mMemLeaksHelper.getMetrics();
        assertTrue(metrics.isEmpty());
    }
}
