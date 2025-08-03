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

package com.android.helpers;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import java.io.IOException;

/**
 * Android unit test for {@link LyricMemProfilerHelper}
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.LyricMemProfilerHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class LyricMemProfilerHelperTest {
    private static final String TAG = LyricMemProfilerHelperTest.class.getSimpleName();

    private @Mock UiDevice mUiDevice;

    private int mMemInfoCmdCounter = 0;

    private int mDmabufCmdCounter = 0;

    private static final int MOCK_NATIVE_HEAP = 100;

    private static final int MOCK_TOTAL_PSS = 200;

    private static final int MOCK_PROVIDER_DMABUF = 500;

    private static final int MOCK_SERVER_DMABUF = 600;

    private static final int MOCK_APP_DMABUF = 700;

    private static final String[] TEST_PID_NAME_LIST = {
        "provider@", "cameraserver", "id.GoogleCamera"
    };
    private static final String[] TEST_METRIC_NAME_LIST = {
        "CameraProvider", "CameraServer", "CameraApp"
    };

    private String genMemInfoString(int nativeHeap, int totalPss) {
        return ".Native Heap:" + nativeHeap + " TOTAL PSS:" + totalPss + " .";
    }

    private String genDmabufString(
            int providerDmabuf,
            int serverDmabuf,
            int appDmabuf,
            int providerPid,
            int serverPid,
            int appPid) {
        String dmabufDumpString =
                " provider@2.7-se:"
                        + providerPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL          1752 kB          "
                        + providerDmabuf
                        + " kB\n"
                        + "----------------------\n"
                        + " cameraserver:"
                        + serverPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL        315376 kB        "
                        + serverDmabuf
                        + " kB\n"
                        + "----------------------\n"
                        + " id.GoogleCamera:"
                        + appPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL        347304 kB        "
                        + appDmabuf
                        + " kB\n"
                        + "----------------------\n"
                        + "dmabuf total: 1752 kB kernel_rss: 0 kB userspace_rss: 1752 kB"
                        + " userspace_pss: 1752 kB";

        return dmabufDumpString;
    }

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        // Return a fake pid for our fake processes and an empty string otherwise.
        doAnswer(
                        (inv) -> {
                            final int mockProviderPid = 1234;
                            final int mockServerPid = 4567;
                            final int mockAppPid = 8901;
                            String cmd = (String) inv.getArguments()[0];
                            if (cmd.startsWith("pgrep")) {
                                if (cmd.contains("provider@"))
                                    return Integer.toString(mockProviderPid);
                                if (cmd.contains("cameraserver"))
                                    return Integer.toString(mockServerPid);
                                return Integer.toString(mockAppPid);
                            } else if (cmd.startsWith("dumpsys meminfo")) {
                                mMemInfoCmdCounter++;
                                if (10 < mMemInfoCmdCounter && mMemInfoCmdCounter < 15) {
                                    return genMemInfoString(
                                            MOCK_NATIVE_HEAP + 50,
                                            MOCK_TOTAL_PSS + 50); // max value
                                } else {
                                    return genMemInfoString(MOCK_NATIVE_HEAP, MOCK_TOTAL_PSS);
                                }
                            } else if (cmd.startsWith("dmabuf_dump")) {
                                mDmabufCmdCounter++;
                                if (10 == mDmabufCmdCounter) {
                                    return genDmabufString(
                                            MOCK_PROVIDER_DMABUF + 50,
                                            MOCK_SERVER_DMABUF + 50,
                                            MOCK_APP_DMABUF + 50,
                                            mockProviderPid,
                                            mockServerPid,
                                            mockAppPid); // max value
                                } else {
                                    return genDmabufString(
                                            MOCK_PROVIDER_DMABUF,
                                            MOCK_SERVER_DMABUF,
                                            MOCK_APP_DMABUF,
                                            mockProviderPid,
                                            mockServerPid,
                                            mockAppPid);
                                }
                            }
                            return "";
                        })
                .when(mUiDevice)
                .executeShellCommand(any());
    }

    @Test
    @SuppressWarnings("VisibleForTests")
    public void testParsePid() {
        LyricMemProfilerHelper helper = new LyricMemProfilerHelper();
        helper.setProfileCameraProcName(TEST_PID_NAME_LIST);
        helper.setProfileMetricName(TEST_METRIC_NAME_LIST);
        String providerMemInfoString = helper.getMemInfoString("");
        String dmabufDumpString = helper.getDmabufDumpString();
        // memInfo and dmabufDump get empty string due to mCameraProviderPid is empty
        assertThat(providerMemInfoString).isEmpty();
        assertThat(dmabufDumpString).isEmpty();

        SystemClock.sleep(1000); // sleep 1 second to wait for camera provider initialize
        helper.setProfileCameraProcName(TEST_PID_NAME_LIST);
        helper.startCollecting();
        String[] memInfoStringList = new String[TEST_PID_NAME_LIST.length];
        dmabufDumpString = helper.getDmabufDumpString();
        int i;
        for (i = 0; i < memInfoStringList.length; i++) {
            memInfoStringList[i] = helper.getMemInfoString(helper.mCameraPidList[i]);
        }
        Map<String, Integer> metrics = helper.processOutput(memInfoStringList, dmabufDumpString);

        assertThat(metrics).containsKey("nativeHeap");
        assertThat(metrics).containsKey("totalPss");
        assertThat(metrics).containsKey("dmabuf");

        assertThat(metrics).containsKey("CameraProviderNativeHeap");
        assertThat(metrics).containsKey("CameraProviderTotalPss");
        assertThat(metrics).containsKey("CameraProviderDmabuf");

        assertThat(metrics).containsKey("CameraServerNativeHeap");
        assertThat(metrics).containsKey("CameraServerTotalPss");
        assertThat(metrics).containsKey("CameraServerDmabuf");

        assertThat(metrics).containsKey("CameraAppNativeHeap");
        assertThat(metrics).containsKey("CameraAppTotalPss");
        assertThat(metrics).containsKey("CameraAppDmabuf");

        assertThat(metrics.get("nativeHeap")).isGreaterThan(0);
        assertThat(metrics.get("totalPss")).isGreaterThan(0);

        assertThat(metrics.get("CameraProviderNativeHeap")).isGreaterThan(0);
        assertThat(metrics.get("CameraProviderTotalPss")).isGreaterThan(0);

        assertThat(metrics.get("CameraServerNativeHeap")).isGreaterThan(0);
        assertThat(metrics.get("CameraServerTotalPss")).isGreaterThan(0);

        assertThat(metrics.get("CameraAppNativeHeap")).isGreaterThan(0);
        assertThat(metrics.get("CameraAppTotalPss")).isGreaterThan(0);
    }

    @Test
    @SuppressWarnings("VisibleForTests")
    public void testProcessOutput() {
        LyricMemProfilerHelper helper = new LyricMemProfilerHelper();
        helper.setProfileCameraProcName(TEST_PID_NAME_LIST);
        helper.setProfileMetricName(TEST_METRIC_NAME_LIST);
        helper.startCollecting();
        String providerPid = helper.getProcPid("provider@");
        String serverPid = helper.getProcPid("cameraserver");
        String appPid = helper.getProcPid("id.GoogleCamera");

        String providerMemInfoString =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 2649336 Realtime: 3041976\n"
                        + "** MEMINFO in pid 14612 [android.hardwar] **\n"
                        + "App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:        0                              0\n"
                        + "         Native Heap:   377584                         377584\n"
                        + "                Code:    79008                         117044\n"
                        + "               Stack:     3364                           3364\n"
                        + "            Graphics:    47672                          47672\n"
                        + "       Private Other:    37188\n"
                        + "              System:     5307\n"
                        + "             Unknown:                                   39136\n"
                        + "\n"
                        + "           TOTAL PSS:   550123            TOTAL RSS:   584800      TOTAL"
                        + " SWAP (KB):        0";

        String serverMemInfoString =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 78463994 Realtime: 78463994\n"
                        + "** MEMINFO in pid 23081 [cameraserver] **\n"
                        + "App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:        0                              0\n"
                        + "         Native Heap:      584                            584\n"
                        + "                Code:     6860                          22764\n"
                        + "               Stack:      104                            104\n"
                        + "            Graphics:        0                              0\n"
                        + "       Private Other:      636\n"
                        + "              System:     1446\n"
                        + "             Unknown:                                    1468\n"
                        + "\n"
                        + "           TOTAL PSS:     9630            TOTAL RSS:    24920      TOTAL"
                        + " SWAP (KB):        0";

        String appMemInfoString =
                "Applications Memory Usage (in Kilobytes):\n"
                        + "Uptime: 78887501 Realtime: 78887501\n"
                        + "** MEMINFO in pid 20264 [com.google.android.GoogleCamera] **\n"
                        + "App Summary\n"
                        + "                       Pss(KB)                        Rss(KB)\n"
                        + "                        ------                         ------\n"
                        + "           Java Heap:     7724                          35404\n"
                        + "         Native Heap:    94780                          97908\n"
                        + "                Code:    14148                         152312\n"
                        + "               Stack:     1280                           1296\n"
                        + "            Graphics:    44472                          44472\n"
                        + "       Private Other:     7524\n"
                        + "              System:    25432\n"
                        + "             Unknown:                                   12616\n"
                        + "\n"
                        + "           TOTAL PSS:   195360            TOTAL RSS:   344008      TOTAL"
                        + " SWAP (KB):        0";

        String dmabufDumpString =
                " provider@2.7-se:"
                        + providerPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL          1752 kB          1552 kB\n"
                        + "----------------------\n"
                        + " id.GoogleCamera:"
                        + appPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL        347304 kB        105137 kB\n"
                        + "----------------------\n"
                        + " cameraserver:"
                        + serverPid
                        + "\n"
                        + "                  Name              Rss              Pss        "
                        + " nr_procs            Inode\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153387\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153388\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           153389\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           153390\n"
                        + "             <unknown>           576 kB           576 kB               "
                        + " 1           205579\n"
                        + "             <unknown>             8 kB             8 kB               "
                        + " 1           205580\n"
                        + "         PROCESS TOTAL        315376 kB        104349 kB\n"
                        + "----------------------\n"
                        + "dmabuf total: 630288 kB kernel_rss: 0 kB userspace_rss: 1410780 kB"
                        + " userspace_pss: 509763 kB";

        String[] memInfoStringList = {providerMemInfoString, serverMemInfoString, appMemInfoString};
        Map<String, Integer> metrics = helper.processOutput(memInfoStringList, dmabufDumpString);

        assertThat(metrics.get("nativeHeap")).isEqualTo(377584);
        assertThat(metrics.get("totalPss")).isEqualTo(550123);
        assertThat(metrics.get("dmabuf")).isEqualTo(1552);

        assertThat(metrics.get("CameraProviderNativeHeap")).isEqualTo(377584);
        assertThat(metrics.get("CameraProviderTotalPss")).isEqualTo(550123);
        assertThat(metrics.get("CameraProviderDmabuf")).isEqualTo(1552);

        assertThat(metrics.get("CameraServerNativeHeap")).isEqualTo(584);
        assertThat(metrics.get("CameraServerTotalPss")).isEqualTo(9630);
        assertThat(metrics.get("CameraServerDmabuf")).isEqualTo(104349);

        assertThat(metrics.get("CameraAppNativeHeap")).isEqualTo(94780);
        assertThat(metrics.get("CameraAppTotalPss")).isEqualTo(195360);
        assertThat(metrics.get("CameraAppDmabuf")).isEqualTo(105137);

        int totalDmabuf = 1552 + 104349 + 105137;
        assertThat(metrics.get("totalCameraDmabuf")).isEqualTo(totalDmabuf);
        int totalMemory = 550123 + 9630 + 195360 + totalDmabuf;
        assertThat(metrics.get("totalCameraMemory")).isEqualTo(totalMemory);
        String[] memInfoStringList2 = {"", "", ""};
        metrics = helper.processOutput(memInfoStringList2, "");
        assertThat(metrics.get("nativeHeap")).isEqualTo(0);
        assertThat(metrics.get("totalPss")).isEqualTo(0);
        assertThat(metrics.get("dmabuf")).isEqualTo(0);

        assertThat(metrics.get("CameraProviderNativeHeap")).isEqualTo(0);
        assertThat(metrics.get("CameraProviderTotalPss")).isEqualTo(0);
        assertThat(metrics.get("CameraProviderDmabuf")).isEqualTo(0);

        assertThat(metrics.get("CameraServerNativeHeap")).isEqualTo(0);
        assertThat(metrics.get("CameraServerTotalPss")).isEqualTo(0);
        assertThat(metrics.get("CameraServerDmabuf")).isEqualTo(0);

        assertThat(metrics.get("CameraAppNativeHeap")).isEqualTo(0);
        assertThat(metrics.get("CameraAppTotalPss")).isEqualTo(0);
        assertThat(metrics.get("CameraAppDmabuf")).isEqualTo(0);

        assertThat(metrics.get("totalCameraDmabuf")).isEqualTo(0);
        assertThat(metrics.get("totalCameraMemory")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("VisibleForTests")
    public void testProfilePeriod() {
        LyricMemProfilerHelper helper = new TestableLyricMemProfilerHelper();
        helper.setProfileCameraProcName(TEST_PID_NAME_LIST);
        helper.setProfileMetricName(TEST_METRIC_NAME_LIST);
        helper.setProfilePeriodMs(100);
        helper.startCollecting();
        SystemClock.sleep(2000);
        Map<String, Integer> metrics = helper.getMetrics();
        helper.stopCollecting();
        assertThat(metrics).containsKey("nativeHeap");
        assertThat(metrics).containsKey("totalPss");
        assertThat(metrics).containsKey("dmabuf");
        assertThat(metrics).containsKey("CameraProviderNativeHeap");
        assertThat(metrics).containsKey("CameraProviderTotalPss");
        assertThat(metrics).containsKey("CameraProviderDmabuf");
        assertThat(metrics).containsKey("CameraServerNativeHeap");
        assertThat(metrics).containsKey("CameraServerTotalPss");
        assertThat(metrics).containsKey("CameraServerDmabuf");
        assertThat(metrics).containsKey("CameraAppNativeHeap");
        assertThat(metrics).containsKey("CameraAppTotalPss");
        assertThat(metrics).containsKey("CameraAppDmabuf");
        assertThat(metrics).containsKey("totalCameraDmabuf");
        assertThat(metrics).containsKey("totalCameraMemory");

        assertThat(metrics).containsKey("maxNativeHeap");
        assertThat(metrics).containsKey("maxTotalPss");
        assertThat(metrics).containsKey("maxDmabuf");
        assertThat(metrics).containsKey("maxCameraProviderNativeHeap");
        assertThat(metrics).containsKey("maxCameraProviderTotalPss");
        assertThat(metrics).containsKey("maxCameraProviderDmabuf");
        assertThat(metrics).containsKey("maxCameraServerNativeHeap");
        assertThat(metrics).containsKey("maxCameraServerTotalPss");
        assertThat(metrics).containsKey("maxCameraServerDmabuf");
        assertThat(metrics).containsKey("maxCameraAppNativeHeap");
        assertThat(metrics).containsKey("maxCameraAppTotalPss");
        assertThat(metrics).containsKey("maxCameraAppDmabuf");
        assertThat(metrics).containsKey("maxTotalCameraDmabuf");
        assertThat(metrics).containsKey("maxTotalCameraMemory");

        assertThat(metrics.get("nativeHeap")).isLessThan(metrics.get("maxNativeHeap"));
        assertThat(metrics.get("totalPss")).isLessThan(metrics.get("maxTotalPss"));
        assertThat(metrics.get("dmabuf")).isLessThan(metrics.get("maxDmabuf"));

        assertThat(metrics.get("CameraProviderNativeHeap"))
                .isLessThan(metrics.get("maxCameraProviderNativeHeap"));
        assertThat(metrics.get("CameraProviderTotalPss"))
                .isLessThan(metrics.get("maxCameraProviderTotalPss"));
        assertThat(metrics.get("CameraProviderDmabuf"))
                .isLessThan(metrics.get("maxCameraProviderDmabuf"));

        assertThat(metrics.get("CameraServerNativeHeap"))
                .isLessThan(metrics.get("maxCameraServerNativeHeap"));
        assertThat(metrics.get("CameraServerTotalPss"))
                .isLessThan(metrics.get("maxCameraServerTotalPss"));
        assertThat(metrics.get("CameraServerDmabuf"))
                .isLessThan(metrics.get("maxCameraServerDmabuf"));

        assertThat(metrics.get("CameraAppNativeHeap"))
                .isLessThan(metrics.get("maxCameraAppNativeHeap"));
        assertThat(metrics.get("CameraAppTotalPss"))
                .isLessThan(metrics.get("maxCameraAppTotalPss"));
        assertThat(metrics.get("CameraAppDmabuf")).isLessThan(metrics.get("maxCameraAppDmabuf"));

        assertThat(metrics.get("totalCameraDmabuf"))
                .isLessThan(metrics.get("maxTotalCameraDmabuf"));
        assertThat(metrics.get("totalCameraMemory"))
                .isLessThan(metrics.get("maxTotalCameraMemory"));
    }

    @Test
    @SuppressWarnings("VisibleForTests")
    public void testProfilePeriodLessThanMin() {
        LyricMemProfilerHelper helper = new TestableLyricMemProfilerHelper();
        helper.setProfileCameraProcName(TEST_PID_NAME_LIST);
        helper.setProfileMetricName(TEST_METRIC_NAME_LIST);
        InOrder inOrder = inOrder(mUiDevice);
        helper.setProfilePeriodMs(50);
        helper.startCollecting();
        try {
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o provider@");
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o cameraserver");
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o id.GoogleCamera");
        } catch (IOException e) {
            Log.e(TAG, "Failed to execute Shell command");
        }
        SystemClock.sleep(3000);
        verifyNoMoreInteractions(mUiDevice);

        Map<String, Integer> metrics = helper.getMetrics();
        helper.stopCollecting();
        assertThat(metrics).containsKey("nativeHeap");
        assertThat(metrics).containsKey("totalPss");
        assertThat(metrics).containsKey("dmabuf");
        assertThat(metrics).containsKey("CameraProviderNativeHeap");
        assertThat(metrics).containsKey("CameraProviderTotalPss");
        assertThat(metrics).containsKey("CameraProviderDmabuf");
        assertThat(metrics).containsKey("CameraServerNativeHeap");
        assertThat(metrics).containsKey("CameraServerTotalPss");
        assertThat(metrics).containsKey("CameraServerDmabuf");
        assertThat(metrics).containsKey("CameraAppNativeHeap");
        assertThat(metrics).containsKey("CameraAppTotalPss");
        assertThat(metrics).containsKey("CameraAppDmabuf");
        assertThat(metrics).containsKey("totalCameraDmabuf");
        assertThat(metrics).containsKey("totalCameraMemory");

        assertThat(metrics).doesNotContainKey("maxNativeHeap");
        assertThat(metrics).doesNotContainKey("maxTotalPss");
        assertThat(metrics).doesNotContainKey("maxDmabuf");
        assertThat(metrics).doesNotContainKey("maxCameraProviderNativeHeap");
        assertThat(metrics).doesNotContainKey("maxCameraProviderTotalPss");
        assertThat(metrics).doesNotContainKey("maxCameraProviderDmabuf");
        assertThat(metrics).doesNotContainKey("maxCameraServerNativeHeap");
        assertThat(metrics).doesNotContainKey("maxCameraServerTotalPss");
        assertThat(metrics).doesNotContainKey("maxCameraServerDmabuf");
        assertThat(metrics).doesNotContainKey("maxCameraAppNativeHeap");
        assertThat(metrics).doesNotContainKey("maxCameraAppTotalPss");
        assertThat(metrics).doesNotContainKey("maxCameraAppDmabuf");
        assertThat(metrics).doesNotContainKey("maxTotalCameraDmabuf");
        assertThat(metrics).doesNotContainKey("maxTotalCameraMemory");

        assertThat(metrics.get("nativeHeap")).isEqualTo(MOCK_NATIVE_HEAP);
        assertThat(metrics.get("totalPss")).isEqualTo(MOCK_TOTAL_PSS);
        assertThat(metrics.get("dmabuf")).isEqualTo(MOCK_PROVIDER_DMABUF);
        assertThat(metrics.get("CameraProviderNativeHeap")).isEqualTo(MOCK_NATIVE_HEAP);
        assertThat(metrics.get("CameraProviderTotalPss")).isEqualTo(MOCK_TOTAL_PSS);
        assertThat(metrics.get("CameraProviderDmabuf")).isEqualTo(MOCK_PROVIDER_DMABUF);
        assertThat(metrics.get("CameraServerNativeHeap")).isEqualTo(MOCK_NATIVE_HEAP);
        assertThat(metrics.get("CameraServerTotalPss")).isEqualTo(MOCK_TOTAL_PSS);
        assertThat(metrics.get("CameraServerDmabuf")).isEqualTo(MOCK_SERVER_DMABUF);
        assertThat(metrics.get("CameraAppNativeHeap")).isEqualTo(MOCK_NATIVE_HEAP);
        assertThat(metrics.get("CameraAppTotalPss")).isEqualTo(MOCK_TOTAL_PSS);
        assertThat(metrics.get("CameraAppDmabuf")).isEqualTo(MOCK_APP_DMABUF);
        int totalCameraDmabuf = MOCK_PROVIDER_DMABUF + MOCK_SERVER_DMABUF + MOCK_APP_DMABUF;
        assertThat(metrics.get("totalCameraDmabuf")).isEqualTo(totalCameraDmabuf);
        int totalCameraMemory = totalCameraDmabuf + 3 * MOCK_TOTAL_PSS;
        assertThat(metrics.get("totalCameraMemory")).isEqualTo(totalCameraMemory);
    }

    @Test
    @SuppressWarnings("VisibleForTests")
    public void testSetNewPidName() {
        LyricMemProfilerHelper helper = new TestableLyricMemProfilerHelper();
        InOrder inOrder = inOrder(mUiDevice);
        final String newProviderPidName = "new.provider.name";
        final String newServerPidName = "new.server.name";
        final String newAppPidName = "new.app.name";
        final String[] newPidNameList = {newProviderPidName, newServerPidName, newAppPidName};
        final String[] newMetricNameList = {"CameraProvider", "CameraServer", "CameraApp"};
        helper.setProfileCameraProcName(newPidNameList);
        helper.setProfileMetricName(newMetricNameList);
        helper.startCollecting();
        try {
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o " + newProviderPidName);
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o " + newServerPidName);
            inOrder.verify(mUiDevice).executeShellCommand("pgrep -f -o " + newAppPidName);
        } catch (IOException e) {
            Log.e(TAG, "Failed to execute Shell command");
        }
    }

    private final class TestableLyricMemProfilerHelper extends LyricMemProfilerHelper {
        @Override
        protected UiDevice initUiDevice() {
            return mUiDevice;
        }
    }
}
