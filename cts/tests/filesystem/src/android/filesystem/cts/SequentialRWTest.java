/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.filesystem.cts;

import android.util.Log;
import android.mediapc.cts.common.Utils;
import android.mediapc.cts.common.PerformanceClassEvaluator;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class SequentialRWTest {
    private static final String TAG = "SequentialRWTest";

    private static final String DIR_SEQ_WR = "SEQ_WR";
    private static final String DIR_SEQ_UPDATE = "SEQ_UPDATE";
    private static final String DIR_SEQ_RD = "SEQ_RD";
    private static final String REPORT_LOG_NAME = "CtsFileSystemTestCases";
    private static final int BUFFER_SIZE = 100 * 1024 * 1024;

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        CarTestUtil.getInstance().setUp();
    }

    @After
    public void tearDown() throws Exception {
        CarTestUtil.getInstance().tearDown();
        FileUtil.removeFileOrDir(getContext(), DIR_SEQ_WR);
        FileUtil.removeFileOrDir(getContext(), DIR_SEQ_UPDATE);
        FileUtil.removeFileOrDir(getContext(), DIR_SEQ_RD);
    }

    @CddTest(requirements = {"8.2/H-1-1"})
    @Test
    public void testSingleSequentialWrite() throws Exception {
        final long fileSize = FileUtil.getFileSizeExceedingMemory(getContext(), BUFFER_SIZE);
        if (fileSize == 0) { // not enough space, give up
            return;
        }
        FileActivity.startFileActivity(getContext());
        final int numberOfFiles =(int)(fileSize / BUFFER_SIZE);
        String streamName = "test_single_sequential_write";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("files", numberOfFiles, ResultType.NEUTRAL, ResultUnit.COUNT);
        final byte[] data = FileUtil.generateRandomData(BUFFER_SIZE);
        final File[] files = FileUtil.createNewFiles(getContext(), DIR_SEQ_WR,
                numberOfFiles);
        double[] rdAmount = new double[numberOfFiles];
        double[] wrAmount = new double[numberOfFiles];
        double[] times = FileUtil.measureIO(numberOfFiles, rdAmount, wrAmount, new MeasureRun() {

            @Override
            public void run(int i) throws IOException {
                FileUtil.writeFile(files[i], data, false);
            }
        });
        double[] mbps = Stat.calcRatePerSecArray((double)BUFFER_SIZE / 1024 / 1024, times);
        report.addValues("write_throughput", mbps, ResultType.HIGHER_BETTER, ResultUnit.MBPS);
        report.addValues("write_amount", wrAmount, ResultType.NEUTRAL, ResultUnit.BYTE);
        Stat.StatResult stat = Stat.getStat(mbps);
        report.setSummary("write_throughput_average", stat.mAverage, ResultType.HIGHER_BETTER,
                ResultUnit.MBPS);
        Log.v(TAG, "sequential write " + stat.mAverage + " MBPS");
        report.submit(getInstrumentation());

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.FileSystemRequirement r8_2__H_1_1 = pce.addR8_2__H_1_1();
        PerformanceClassEvaluator.FileSystemRequirement r8_2__H_2_1 = pce.addR8_2__H_2_1();
        r8_2__H_1_1.setFilesystemIoRate(stat.mAverage);
        r8_2__H_2_1.setFilesystemIoRate(stat.mAverage);

        pce.submitAndCheck();
    }

    @Test
    public void testSingleSequentialUpdate() throws Exception {
        final long fileSize = FileUtil.getFileSizeExceedingMemory(getContext(), BUFFER_SIZE);
        if (fileSize == 0) { // not enough space, give up
            return;
        }
        FileActivity.startFileActivity(getContext());
        final int NUMBER_REPETITION = 3;
        String streamName = "test_single_sequential_update";
        FileUtil.doSequentialUpdateTest(getContext(), DIR_SEQ_UPDATE, fileSize, BUFFER_SIZE,
                NUMBER_REPETITION, REPORT_LOG_NAME, streamName);
    }

    @CddTest(requirements = {"8.2/H-1-3"})
    @Test
    public void testSingleSequentialRead() throws Exception {
        final long fileSize = FileUtil.getFileSizeExceedingMemory(getContext(), BUFFER_SIZE);
        if (fileSize == 0) { // not enough space, give up
            return;
        }
        FileActivity.startFileActivity(getContext());
        long start = System.currentTimeMillis();
        final File file = FileUtil.createNewFilledFile(getContext(),
                DIR_SEQ_RD, fileSize);
        long finish = System.currentTimeMillis();
        String streamName = "test_single_sequential_read";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("file_size", fileSize, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValue("write_throughput",
                Stat.calcRatePerSec((double)fileSize / 1024 / 1024, finish - start),
                ResultType.HIGHER_BETTER, ResultUnit.MBPS);

        final int NUMBER_READ = 10;

        final byte[] data = new byte[BUFFER_SIZE];
        double[] times = MeasureTime.measure(NUMBER_READ, new MeasureRun() {

            @Override
            public void run(int i) throws IOException {
                final FileInputStream in = new FileInputStream(file);
                long read = 0;
                while (read < fileSize) {
                    in.read(data);
                    read += BUFFER_SIZE;
                }
                in.close();
            }
        });
        double[] mbps = Stat.calcRatePerSecArray((double)fileSize / 1024 / 1024, times);
        report.addValues("read_throughput", mbps, ResultType.HIGHER_BETTER, ResultUnit.MBPS);
        Stat.StatResult stat = Stat.getStat(mbps);
        report.setSummary("read_throughput_average", stat.mAverage, ResultType.HIGHER_BETTER,
                ResultUnit.MBPS);
        Log.v(TAG, "sequential read " + stat.mAverage + " MBPS");
        report.submit(getInstrumentation());

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.FileSystemRequirement r8_2__H_1_3 = pce.addR8_2__H_1_3();
        PerformanceClassEvaluator.FileSystemRequirement r8_2__H_2_3 = pce.addR8_2__H_2_3();
        r8_2__H_1_3.setFilesystemIoRate(stat.mAverage);
        r8_2__H_2_3.setFilesystemIoRate(stat.mAverage);

        pce.submitAndCheck();
    }
}
