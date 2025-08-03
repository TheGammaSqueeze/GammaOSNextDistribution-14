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

package com.android.car.telemetry.util;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;

import com.android.car.telemetry.MetricsReportProto.MetricsReportList;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetricsReportProtoUtilsTest {

    @Test
    public void testConversion() {
        PersistableBundle expected = new PersistableBundle();
        expected.putLong("timestamp", System.currentTimeMillis());

        byte[] bytes = MetricsReportProtoUtils.getBytes(expected);
        PersistableBundle bundle = MetricsReportProtoUtils.getBundle(ByteString.copyFrom(bytes));

        assertThat(bundle.toString()).isEqualTo(expected.toString());
    }

    @Test
    public void testBuildMetricsReportListAndGetBundle() {
        PersistableBundle expected = new PersistableBundle();
        expected.putString("test", "test");

        MetricsReportList reportList =
                MetricsReportProtoUtils.buildMetricsReportList(expected, expected);
        assertThat(reportList.getReportCount()).isEqualTo(2);
        PersistableBundle report1 = MetricsReportProtoUtils.getBundle(reportList, 0);
        PersistableBundle report2 = MetricsReportProtoUtils.getBundle(reportList, 1);

        assertThat(report1.toString()).isEqualTo(expected.toString());
        assertThat(report2.toString()).isEqualTo(expected.toString());
    }
}
