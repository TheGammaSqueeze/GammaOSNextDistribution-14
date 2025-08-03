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

package com.android.tradefed.device.metric;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;

/** Unit tests for {@link ShowmapPullerMetricCollector}. */
@RunWith(JUnit4.class)
public class ShowmapPullerMetricCollectorTest {

    private ShowmapPullerMetricCollector mShowmapMetricCollector;
    @Mock private ITestInvocationListener mMockListener;
    @Mock private ITestDevice mMockDevice;
    private IInvocationContext mContext;
    private File mTmpFile;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(TestDeviceState.ONLINE).when(mMockDevice).getDeviceState();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("default", mMockDevice);
        mShowmapMetricCollector = Mockito.spy(new ShowmapPullerMetricCollector());
        mShowmapMetricCollector.init(mContext, mMockListener);
        mTmpFile = File.createTempFile("showmap_granular", "");
        when(mMockDevice.getCurrentUser()).thenReturn(0);
    }

    @After
    public void tearDown() throws Exception {
        mTmpFile.delete();
    }

    @Test
    public void test16ColumnHeaders() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        setter.setOptionValue("showmap-process-name", "netd");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb flags "
                                + " object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------------------------------",
                        "     128        4        0        0        4        0        0        0   "
                                + "     0         0         0         0        0        0    rw-"
                                + " /dev/__properties__/properties_serial",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------------------------------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);

        Assert.assertEquals(16, currentMetrics.size());
        Assert.assertEquals(
                1,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void test17ColumnHeaders() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        setter.setOptionValue("showmap-process-name", "netd");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "     128        4        0        0        4        0        0        0   "
                            + "     0         0         0         0        0        0      0    rw-"
                            + " /dev/__properties__/properties_serial",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);

        Assert.assertEquals(16, currentMetrics.size());
        Assert.assertEquals(
                1,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void testOneObjectTwiceFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90    100   110  120  130 140 15 rw- obj1",
                        "11 21 31 41 51 61 71 81 91  101 111 121  131 141 15 r-- obj1",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);
        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(16, currentMetrics.size());
        Assert.assertEquals(
                1,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void testTwoObjectOnceFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90    100   110  120  130 140 15 rw- obj1",
                        "11 21 31 41 51 61 71 81 91  101 111 121  131 141 15 r-- obj2",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(30, currentMetrics.size());
        Assert.assertEquals(
                2,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void testThreeObjectOnceFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90  100 110 120  130 140 15 rw- obj1",
                        "11 21 31 41 51 61 71 81 91  101 111 121  131 141 15 r-- obj2",
                        "87 32 96 87 11 05 23 48 100 000 121 1 13 1991 15 rwx obj3./apex",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(44, currentMetrics.size());
        Assert.assertEquals(
                3,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void testTwoProcessesFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        setter.setOptionValue("showmap-process-name", "netd");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90    100   110  120  130 140 15 rw- obj1",
                        "-------- -------- --------",
                        "   >>> netd (7038) <<<   ",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "100 2021 3033 4092 500 6 7  8 9 100 110 120 130 140 15 rw- obj123",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(
                        mMockDevice.pullFile(
                                Mockito.eq("/sdcard/test_results/showmap.txt"), Mockito.eq(0)))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(31, currentMetrics.size());
        Assert.assertEquals(
                1,
                currentMetrics
                        .get("showmap_granular_system_server_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
        Assert.assertEquals(
                1,
                currentMetrics
                        .get("showmap_granular_netd_total_object_count")
                        .getMeasurements()
                        .getSingleInt());
    }

    @Test
    public void testNoProcessMatchFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "watchdog");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90    100   110  120  130 140 15 rw- obj1",
                        "-------- -------- --------",
                        "   >>> netd (7038) <<<   ",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "100 2021 3033 4092 500 6 7  8 9 100 110 120 130 140 15 rw- obj123",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(mMockDevice.pullFile(Mockito.eq("/sdcard/test_results/showmap.txt")))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(1, currentMetrics.size());
        Assert.assertEquals(
                null, currentMetrics.get("showmap_granular_system_server_total_object_count"));
    }

    @Test
    public void testNoProcessFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70 80 90    100   110  120  130 140 15 rw- obj1",
                        "-------- -------- --------",
                        "   >>> netd (7038) <<<   ",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "100 2021 3033 4092 500 6 7  8 9 100 110 120 130 140 15 rw- obj123",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(mMockDevice.pullFile(Mockito.eq("/sdcard/test_results/showmap.txt")))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(1, currentMetrics.size());
        Assert.assertEquals(
                null, currentMetrics.get("showmap_granular_system_server_total_object_count"));
    }

    @Test
    public void testErrorFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        setter.setOptionValue("showmap-process-name", "system_server");
        setter.setOptionValue("showmap-process-name", "netd");
        FileWriter writer = new FileWriter(mTmpFile);
        String log =
                String.join(
                        "\n",
                        ">>> system_server (6910) <<<",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "10 20 30 40 50 60 70    100   110  120  130 140 15 rw- obj1",
                        "100 2021 3033 4092 500 6 7  8 9 100 110 120 130 140 15 rw- obj123",
                        "-------- -------- --------",
                        "   >>> netd (7038) <<<   ",
                        " virtual                     shared   shared  private  private            "
                                + "       Anon      Shmem     File       Shared   Private",
                        "    size      RSS      PSS    clean    dirty    clean    dirty     swap "
                                + " swapPSS HugePages PmdMapped PmdMapped  Hugetlb  Hugetlb locked"
                                + " flags  object",
                        "-------- -------- -------- -------- -------- -------- -------- --------"
                                + " -------- --------- --------- --------- -------- -------- ------"
                                + " ------ ------------------------------",
                        "zzz abc 4%d -md 5,g --c 0sd  asd 9# 1*0 1! 1ew qqq 14: 15. rw- obj123",
                        "-------- -------- --------");
        writer.write(log);
        writer.close();
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(mMockDevice.pullFile(Mockito.eq("/sdcard/test_results/showmap.txt")))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(1, currentMetrics.size());
        Assert.assertEquals(
                null, currentMetrics.get("showmap_granular_system_server_total_object_count"));
    }

    @Test
    public void testEmptyFileFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(mMockDevice.pullFile(Mockito.eq("/sdcard/test_results/showmap.txt")))
                .thenReturn(mTmpFile);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(1, currentMetrics.size());
        Assert.assertEquals(
                null, currentMetrics.get("showmap_granular_system_server_total_object_count"));
    }

    @Test
    public void testNullFileFlow() throws Exception {
        OptionSetter setter = new OptionSetter(mShowmapMetricCollector);
        setter.setOptionValue("collect-on-run-ended-only", "false");
        setter.setOptionValue("pull-pattern-keys", "showmap_output_file");
        TestDescription testDesc = new TestDescription("xyz", "abc");
        mShowmapMetricCollector.testStarted(testDesc);

        HashMap<String, Metric> currentMetrics = new HashMap<>();
        currentMetrics.put(
                "showmap_output_file",
                TfMetricProtoUtil.stringToMetric("/sdcard/test_results/showmap.txt"));
        Mockito.when(mMockDevice.pullFile(Mockito.eq("/sdcard/test_results/showmap.txt")))
                .thenReturn(null);

        mShowmapMetricCollector.testEnded(testDesc, currentMetrics);
        Assert.assertEquals(1, currentMetrics.size());
        Assert.assertEquals(
                null, currentMetrics.get("showmap_granular_system_server_total_object_count"));
    }
}
