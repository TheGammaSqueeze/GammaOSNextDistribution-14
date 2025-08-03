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

package com.android.car.telemetry;

import static com.android.car.telemetry.ResultStore.BUNDLE_KEY_ID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.car.telemetry.TelemetryProto;
import android.content.Context;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import com.android.car.telemetry.MetricsReportProto.MetricsReportList;
import com.android.car.telemetry.util.IoUtils;
import com.android.car.telemetry.util.MetricsReportProtoUtils;
import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class ResultStoreTest {
    private static final PersistableBundle TEST_INTERIM_BUNDLE = new PersistableBundle();
    private static final PersistableBundle TEST_METRICS_REPORT_BUNDLE = new PersistableBundle();
    private static final PersistableBundle TEST_PUBLISHER_BUNDLE = new PersistableBundle();
    private static final TelemetryProto.TelemetryError TEST_TELEMETRY_ERROR =
            TelemetryProto.TelemetryError.newBuilder().setMessage("test error").build();

    private File mTestRootDir;
    private File mTestInterimResultDir;
    private File mTestErrorResultDir;
    private File mTestMetricsReportDir;
    private File mTestPublisherDataDir;
    private ResultStore mResultStore;

    @Mock
    private Context mMockContext;

    @Before
    public void setUp() throws Exception {
        MockContentResolver mockContentResolver = new MockContentResolver();
        mockContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mMockContext.getContentResolver()).thenReturn(mockContentResolver);
        TEST_INTERIM_BUNDLE.putString("test key", "interim value");
        TEST_METRICS_REPORT_BUNDLE.putDouble("pi", 3.14159);
        TEST_METRICS_REPORT_BUNDLE.putString("test key", "metrics report");
        TEST_PUBLISHER_BUNDLE.putString("test key", "publisher provided string");

        mTestRootDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mTestInterimResultDir = new File(mTestRootDir, ResultStore.INTERIM_RESULT_DIR);
        mTestErrorResultDir = new File(mTestRootDir, ResultStore.ERROR_RESULT_DIR);
        mTestMetricsReportDir = new File(mTestRootDir, ResultStore.FINAL_RESULT_DIR);
        mTestPublisherDataDir = new File(mTestRootDir, ResultStore.PUBLISHER_STORAGE_DIR);

        mResultStore = createResultStore();
    }

    private ResultStore createResultStore() {
        return new ResultStore(mMockContext, mTestRootDir);
    }

    @Test
    public void testConstructor_shouldCreateResultsFolder() {
        // constructor is called in setUp()
        assertThat(mTestInterimResultDir.exists()).isTrue();
        assertThat(mTestMetricsReportDir.exists()).isTrue();
        assertThat(mTestErrorResultDir.exists()).isTrue();
        assertThat(mTestPublisherDataDir.exists()).isTrue();
    }

    @Test
    public void testConstructor_shouldLoadInterimResultsIntoMemory() throws Exception {
        String testInterimFileName = "test_file_1";
        writeBundleToFile(mTestInterimResultDir, testInterimFileName, TEST_INTERIM_BUNDLE);

        mResultStore = createResultStore();

        // should compare value instead of reference
        assertThat(mResultStore.getInterimResult(testInterimFileName).toString())
                .isEqualTo(TEST_INTERIM_BUNDLE.toString());
    }

    @Test
    public void testFlushToDisk_shouldRemoveStaleData() throws Exception {
        File staleTestFile1 = new File(mTestInterimResultDir, "stale_test_file_1");
        File staleTestFile2 = new File(mTestMetricsReportDir, "stale_test_file_2");
        File activeTestFile3 = new File(mTestInterimResultDir, "active_test_file_3");
        writeBundleToFile(staleTestFile1, TEST_INTERIM_BUNDLE);
        IoUtils.writeProto(
                staleTestFile2,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));
        writeBundleToFile(activeTestFile3, TEST_INTERIM_BUNDLE);
        long currTimeMs = System.currentTimeMillis();
        staleTestFile1.setLastModified(0L); // stale
        staleTestFile2.setLastModified(
                currTimeMs - TimeUnit.MILLISECONDS.convert(31, TimeUnit.DAYS)); // stale
        activeTestFile3.setLastModified(
                currTimeMs - TimeUnit.MILLISECONDS.convert(29, TimeUnit.DAYS)); // active

        mResultStore.flushToDisk();

        assertThat(staleTestFile1.exists()).isFalse();
        assertThat(staleTestFile2.exists()).isFalse();
        assertThat(activeTestFile3.exists()).isTrue();
    }

    @Test
    public void testPutInterimResult_shouldNotWriteToDisk() {
        String metricsConfigName = "my_metrics_config";

        mResultStore.putInterimResult(metricsConfigName, TEST_INTERIM_BUNDLE);

        assertThat(mTestInterimResultDir.list()).asList().doesNotContain(metricsConfigName);
        assertThat(mResultStore.getInterimResult(metricsConfigName)).isNotNull();
    }

    @Test
    public void testPutInterimResultAndFlushToDisk_shouldReplaceExistingFile() throws Exception {
        String newKey = "new key";
        String newValue = "new value";
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);
        TEST_INTERIM_BUNDLE.putString(newKey, newValue);

        mResultStore.putInterimResult(metricsConfigName, TEST_INTERIM_BUNDLE);
        mResultStore.flushToDisk();

        PersistableBundle bundle = readBundleFromFile(mTestInterimResultDir, metricsConfigName);
        assertThat(bundle.getString(newKey)).isEqualTo(newValue);
        assertThat(bundle.toString()).isEqualTo(TEST_INTERIM_BUNDLE.toString());
    }

    @Test
    public void testPutInterimResultAndFlushToDisk_shouldWriteDirtyResultsOnly() throws Exception {
        File fileFoo = new File(mTestInterimResultDir, "foo");
        File fileBar = new File(mTestInterimResultDir, "bar");
        writeBundleToFile(fileFoo, TEST_INTERIM_BUNDLE);
        writeBundleToFile(fileBar, TEST_INTERIM_BUNDLE);
        mResultStore = createResultStore(); // re-load data
        PersistableBundle newData = new PersistableBundle();
        newData.putDouble("pi", 3.1415926);

        mResultStore.putInterimResult("bar", newData); // make bar dirty
        fileFoo.delete(); // delete the clean file from the file system
        mResultStore.flushToDisk(); // write dirty data

        // foo is a clean file that should not be written in flushToDisk()
        assertThat(fileFoo.exists()).isFalse();
        assertThat(readBundleFromFile(fileBar).toString()).isEqualTo(newData.toString());
    }

    @Test
    public void testGetInterimResult() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);
        mResultStore = createResultStore(); // reload data

        PersistableBundle data = mResultStore.getInterimResult(metricsConfigName);

        assertThat(data.toString()).isEqualTo(TEST_INTERIM_BUNDLE.toString());
    }

    @Test
    public void testGetMetricsReports_whenNoData_shouldReceiveNull() {
        String metricsConfigName = "my_metrics_config";

        MetricsReportList reportList = mResultStore.getMetricsReports(metricsConfigName, true);

        assertThat(reportList).isNull();
    }

    @Test
    public void testGetMetricsReports_whenDataCorrupt_shouldReceiveNull() throws Exception {
        String metricsConfigName = "my_metrics_config";
        Files.write(new File(mTestMetricsReportDir, metricsConfigName).toPath(),
                "not a bundle".getBytes(StandardCharsets.UTF_8));

        MetricsReportList reportList = mResultStore.getMetricsReports(metricsConfigName, true);

        assertThat(reportList).isNull();
    }

    @Test
    public void testGetMetricsReports_whenMultipleReports_shouldReceiveCorrectList()
            throws Exception {
        String metricsConfigName = "my_metrics_config";
        IoUtils.writeProto(
                mTestMetricsReportDir,
                metricsConfigName,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));

        MetricsReportList reportList = mResultStore.getMetricsReports(metricsConfigName, false);

        assertThat(reportList.getReportCount()).isEqualTo(2);
        // should compare value instead of reference
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 0).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 1).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
    }

    @Test
    public void testGetMetricsReports_whenDeleteFlagTrue_shouldDeleteData() throws Exception {
        String testFinalFileName = "my_metrics_config";
        IoUtils.writeProto(
                mTestMetricsReportDir,
                testFinalFileName,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));

        MetricsReportList reportList = mResultStore.getMetricsReports(testFinalFileName, true);

        assertThat(reportList.getReportCount()).isEqualTo(2);
        // should compare value instead of reference
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 0).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 1).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
        assertThat(new File(mTestMetricsReportDir, testFinalFileName).exists()).isFalse();
    }

    @Test
    public void testGetAllMetricsReports_shouldReturnMapWithBundle() throws Exception {
        String configName = "my_config";
        // 2 reports for my_config
        IoUtils.writeProto(
                mTestMetricsReportDir,
                configName,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));
        // 1 report for my_second_config
        String configName2 = "my_second_config";
        PersistableBundle expectedReportForConfig2 = new PersistableBundle();
        expectedReportForConfig2.putDouble("pi", 3.14);
        mResultStore.putMetricsReport(configName2, expectedReportForConfig2, false);

        Map<String, MetricsReportList> allReports = mResultStore.getAllMetricsReports();

        assertThat(allReports.keySet()).containsExactly(configName, configName2);
        // should get 2 reports for my_config
        MetricsReportList reportsForConfig1 = allReports.get(configName);
        assertThat(reportsForConfig1.getReportCount()).isEqualTo(2);
        assertThat(MetricsReportProtoUtils.getBundle(reportsForConfig1, 0).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
        assertThat(MetricsReportProtoUtils.getBundle(reportsForConfig1, 1).toString())
                .isEqualTo(TEST_METRICS_REPORT_BUNDLE.toString());
        // should get 1 report for my_second_config
        MetricsReportList reportsForConfig2 = allReports.get(configName2);
        assertThat(reportsForConfig2.getReportCount()).isEqualTo(1);
        assertThat(MetricsReportProtoUtils.getBundle(reportsForConfig2, 0).toString())
                .isEqualTo(expectedReportForConfig2.toString());
    }

    @Test
    public void testGetAllMetricsReports_whenNoData_shouldReceiveEmptyMap() throws Exception {
        assertThat(mResultStore.getAllMetricsReports()).isEmpty();
    }

    @Test
    public void testGetAllMetricsReports_whenDataCorrupt_shouldReceiveEmptyMap() throws Exception {
        Files.write(new File(mTestMetricsReportDir, "my_metrics_config").toPath(),
                "not a bundle".getBytes(StandardCharsets.UTF_8));

        assertThat(mResultStore.getAllMetricsReports()).isEmpty();
    }

    @Test
    public void testGetErrorResult_whenNoError_shouldReceiveNull() {
        String metricsConfigName = "my_metrics_config";

        TelemetryProto.TelemetryError error = mResultStore.getErrorResult(metricsConfigName, true);

        assertThat(error).isNull();
    }

    @Test
    public void testGetErrorResult_shouldReceiveError() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeErrorToFile(metricsConfigName, TEST_TELEMETRY_ERROR);

        TelemetryProto.TelemetryError error = mResultStore.getErrorResult(metricsConfigName, true);

        assertThat(error).isEqualTo(TEST_TELEMETRY_ERROR);
    }

    @Test
    public void testGetErrorResults_whenNoError_shouldReceiveEmptyMap() {
        assertThat(mResultStore.getAllErrorResults()).isEmpty();
    }

    @Test
    public void testGetErrorResults_shouldReceiveErrors() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeErrorToFile(metricsConfigName, TEST_TELEMETRY_ERROR);

        assertThat(mResultStore.getAllErrorResults().get("my_metrics_config"))
                .isEqualTo(TEST_TELEMETRY_ERROR);
    }

    @Test
    public void testPutMetricsReport_shouldNotWriteToDisk() {
        String metricsConfigName = "my_metrics_config";

        mResultStore.putMetricsReport(metricsConfigName, TEST_METRICS_REPORT_BUNDLE, false);
        mResultStore.putMetricsReport(metricsConfigName, TEST_METRICS_REPORT_BUNDLE, false);

        assertThat(mTestMetricsReportDir.list()).isEmpty();
        assertThat(mResultStore.getMetricsReports(metricsConfigName, false).getReportCount())
                .isEqualTo(2);
    }

    @Test
    public void testPutMetricsReportAndFlushToDisk_shouldWriteToDisk() {
        String metricsConfigName = "my_metrics_config";

        mResultStore.putMetricsReport(
                metricsConfigName, TEST_METRICS_REPORT_BUNDLE.deepCopy(), false);
        mResultStore.putMetricsReport(
                metricsConfigName, TEST_METRICS_REPORT_BUNDLE.deepCopy(), false);
        mResultStore.flushToDisk();

        assertThat(new File(mTestMetricsReportDir, metricsConfigName).exists()).isTrue();
        MetricsReportList reportList = mResultStore.getMetricsReports(metricsConfigName, false);
        assertThat(reportList.getReportCount()).isEqualTo(2);
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 0).keySet())
                .containsAtLeastElementsIn(TEST_METRICS_REPORT_BUNDLE.keySet().toArray());
        assertThat(MetricsReportProtoUtils.getBundle(reportList, 1).keySet())
                .containsAtLeastElementsIn(TEST_METRICS_REPORT_BUNDLE.keySet().toArray());
    }

    @Test
    public void testPutMetricsReport_whenLastReport_shouldBuildCorrectReportContainer()
            throws Exception {
        String metricsConfigName = "my_metrics_config";
        File reportListFile = new File(mTestMetricsReportDir, metricsConfigName);

        mResultStore.putMetricsReport(metricsConfigName, TEST_METRICS_REPORT_BUNDLE, false);
        mResultStore.putMetricsReport(metricsConfigName, TEST_METRICS_REPORT_BUNDLE, true);
        mResultStore.flushToDisk();

        MetricsReportList reportList = MetricsReportList.parseFrom(
                Files.readAllBytes(reportListFile.toPath()));
        assertThat(reportList.getReportCount()).isEqualTo(2);
        assertThat(reportList.getReport(0).getIsLastReport()).isFalse();
        assertThat(reportList.getReport(1).getIsLastReport()).isTrue();
    }

    @Test
    public void testPutMetricsReport_shouldAnnotateReport() {
        String metricsConfigName = "my_metrics_config";

        mResultStore.putMetricsReport(
                metricsConfigName, TEST_METRICS_REPORT_BUNDLE.deepCopy(), false);
        mResultStore.putMetricsReport(
                metricsConfigName, TEST_METRICS_REPORT_BUNDLE.deepCopy(), false);

        MetricsReportList reportList = mResultStore.getMetricsReports(metricsConfigName, false);
        PersistableBundle report1 = MetricsReportProtoUtils.getBundle(reportList, 0);
        assertThat(report1.getInt(BUNDLE_KEY_ID)).isEqualTo(1);
        PersistableBundle report2 = MetricsReportProtoUtils.getBundle(reportList, 1);
        assertThat(report2.getInt(BUNDLE_KEY_ID)).isEqualTo(2);
    }

    @Test
    public void testPutErrorResult_shouldNotWriteToDisk() {
        String metricsConfigName = "my_metrics_config";

        mResultStore.putErrorResult(metricsConfigName, TEST_TELEMETRY_ERROR);

        assertThat(mTestErrorResultDir.list()).asList().doesNotContain(metricsConfigName);
        assertThat(mResultStore.getErrorResult(metricsConfigName, false)).isNotNull();
    }

    @Test
    public void testPutErrorResultAndFlushToDisk_shouldWriteErrorAndRemoveInterimResultFile()
            throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);

        mResultStore.putErrorResult(metricsConfigName, TEST_TELEMETRY_ERROR);
        mResultStore.flushToDisk();

        assertThat(new File(mTestInterimResultDir, metricsConfigName).exists()).isFalse();
        assertThat(new File(mTestErrorResultDir, metricsConfigName).exists()).isTrue();
    }

    @Test
    public void testRemoveInterimResult() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);
        String configName2 = "my_second_config";
        mResultStore.putInterimResult(configName2, TEST_INTERIM_BUNDLE);

        mResultStore.removeInterimResult(metricsConfigName);

        assertThat(new File(mTestInterimResultDir, metricsConfigName).exists()).isFalse();
        assertThat(mResultStore.getInterimResult(metricsConfigName)).isNull();
    }

    @Test
    public void testRemoveFinalReports() throws Exception {
        String metricsConfigName = "my_metrics_config";
        IoUtils.writeProto(
                mTestMetricsReportDir,
                metricsConfigName,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));

        mResultStore.removeMetricsReports(metricsConfigName);

        assertThat(new File(mTestInterimResultDir, metricsConfigName).exists()).isFalse();
        assertThat(mResultStore.getMetricsReports(metricsConfigName, false)).isNull();
    }

    @Test
    public void testRemoveErrorResult() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeErrorToFile(metricsConfigName, TEST_TELEMETRY_ERROR);

        mResultStore.removeErrorResult(metricsConfigName);

        assertThat(new File(mTestErrorResultDir, metricsConfigName).exists()).isFalse();
        assertThat(mResultStore.getErrorResult(metricsConfigName, false)).isNull();
    }

    @Test
    public void testRemovePublisherData() throws Exception {
        String publisherName = "publisher 1";
        writeBundleToFile(mTestPublisherDataDir, publisherName, TEST_PUBLISHER_BUNDLE);
        mResultStore = createResultStore(); // reload data

        mResultStore.removePublisherData(publisherName);

        assertThat(mResultStore.getPublisherData(publisherName, true)).isNull();
    }

    @Test
    public void testRemoveResult() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);
        IoUtils.writeProto(
                mTestMetricsReportDir,
                metricsConfigName,
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));
        writeErrorToFile(metricsConfigName, TEST_TELEMETRY_ERROR);

        mResultStore.removeResult(metricsConfigName);

        assertThat(new File(mTestInterimResultDir, metricsConfigName).exists()).isFalse();
        assertThat(new File(mTestMetricsReportDir, metricsConfigName).exists()).isFalse();
        assertThat(new File(mTestErrorResultDir, metricsConfigName).exists()).isFalse();
    }

    @Test
    public void testRemoveAllResults() {
        mResultStore.putInterimResult("config 1", TEST_INTERIM_BUNDLE);
        mResultStore.putMetricsReport("config 2", TEST_METRICS_REPORT_BUNDLE, false);
        mResultStore.putErrorResult("config 3", TEST_TELEMETRY_ERROR);
        mResultStore.putPublisherData("publisher 1", TEST_PUBLISHER_BUNDLE);
        mResultStore.flushToDisk();

        mResultStore.removeAllResults();

        assertThat(mTestInterimResultDir.listFiles()).isEmpty();
        assertThat(mTestMetricsReportDir.listFiles()).isEmpty();
        assertThat(mTestErrorResultDir.listFiles()).isEmpty();
        assertThat(mTestPublisherDataDir.listFiles()).isEmpty();
    }

    @Test
    public void testGetFinishedMetricsConfigNames() throws Exception {
        mResultStore.putInterimResult("name0", TEST_INTERIM_BUNDLE);
        mResultStore.putMetricsReport("name1", TEST_METRICS_REPORT_BUNDLE, false);
        mResultStore.putErrorResult("name2", TEST_TELEMETRY_ERROR);
        IoUtils.writeProto(
                mTestMetricsReportDir,
                "name3",
                MetricsReportProtoUtils.buildMetricsReportList(
                        TEST_METRICS_REPORT_BUNDLE, TEST_METRICS_REPORT_BUNDLE));
        writeErrorToFile("name4", TEST_TELEMETRY_ERROR);

        Set<String> names = mResultStore.getFinishedMetricsConfigNames();

        assertThat(names).containsExactly("name1", "name2", "name3", "name4");
    }

    private void writeErrorToFile(String fileName, TelemetryProto.TelemetryError error)
            throws Exception {
        Files.write(new File(mTestErrorResultDir, fileName).toPath(), error.toByteArray());
    }

    private void writeBundleToFile(
            File dir, String fileName, PersistableBundle persistableBundle) throws Exception {
        writeBundleToFile(new File(dir, fileName), persistableBundle);
    }

    /**
     * Writes a persistable bundle to the result directory with the given directory and file name,
     * and verifies that it was successfully written.
     */
    private void writeBundleToFile(
            File file, PersistableBundle persistableBundle) throws Exception {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            persistableBundle.writeToStream(byteArrayOutputStream);
            Files.write(file.toPath(), byteArrayOutputStream.toByteArray());
        }
        assertWithMessage("bundle is not written to the result directory")
                .that(file.exists()).isTrue();
    }

    private PersistableBundle readBundleFromFile(File dir, String fileName) throws Exception {
        return readBundleFromFile(new File(dir, fileName));
    }

    /** Reads a persistable bundle from the given path. */
    private PersistableBundle readBundleFromFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return PersistableBundle.readFromStream(fis);
        }
    }
}
