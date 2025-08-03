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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Build;

import com.android.compatibility.common.util.DeviceReportLog;

import org.junit.Test;

public class RequirementTest {
    public static class TestReq extends Requirement {
        private TestReq(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setMeasurement1(int measure) {
            this.<Integer>setMeasuredValue("test_measurement_1", measure);
        }

        public static TestReq create() {
            RequiredMeasurement<Integer> measurement1 = RequiredMeasurement
                .<Integer>builder()
                .setId("test_measurement_1")
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(30, 200)
                .addRequiredValue(31, 300)
                .addRequiredValue(32, 400)
                .build();

            return new TestReq("TestReq", measurement1);
        }
    }

    public static class TestReqWith2Measures extends Requirement {
        private TestReqWith2Measures(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setMeasurement1(int measure) {
            this.<Integer>setMeasuredValue("test_measurement_1", measure);
        }

        public void setMeasurement2(int measure) {
            this.<Integer>setMeasuredValue("test_measurement_2", measure);
        }

        public static TestReqWith2Measures create() {
            RequiredMeasurement<Integer> measurement1 = RequiredMeasurement
                .<Integer>builder()
                .setId("test_measurement_1")
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(30, 200)
                .addRequiredValue(31, 300)
                .addRequiredValue(32, 400)
                .build();
            RequiredMeasurement<Integer> measurement2 = RequiredMeasurement
                .<Integer>builder()
                .setId("test_measurement_2")
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(30, 200)
                .addRequiredValue(31, 300)
                .addRequiredValue(32, 400)
                .build();

            return new TestReqWith2Measures("TestReqWith2Measures", measurement1, measurement2);
        }
    }

    @Test
    public void computePerformanceClass_0() {
        TestReq testReq = TestReq.create();
        int pc;

        testReq.setMeasurement1(100);
        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(0);
    }

    @Test
    public void computePerformanceClass_30() {
        TestReq testReq = TestReq.create();
        int pc;

        testReq.setMeasurement1(200);
        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(30);
    }

    @Test
    public void computePerformanceClass_31() {
       TestReq testReq = TestReq.create();
        int pc;

        testReq.setMeasurement1(300);
        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(31);
    }

    @Test
    public void computePerformanceClass_32() {
        TestReq testReq = TestReq.create();
        int pc;

        testReq.setMeasurement1(400);
        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(32);
    }

    @Test
    public void computePerformanceClass_PicksLower() {
        TestReqWith2Measures testReq = TestReqWith2Measures.create();
        int pc;

        // measure1 meets 32, but measure2 only meets 30
        testReq.setMeasurement1(401);
        testReq.setMeasurement2(201);

        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(30);
    }

    @Test
    public void checkPerformanceClass_justBelow() {
        TestReq testReq = TestReq.create();
        boolean perfClassMet;

        // setting measurements to meet pc 31
        testReq.setMeasurement1(300);

        perfClassMet = testReq.checkPerformanceClass(32);
        assertThat(perfClassMet).isEqualTo(false);
    }

    @Test
    public void checkPerformanceClass_justAt() {
        TestReq testReq = TestReq.create();
        boolean perfClassMet;

        // setting measurements to meet pc 31
        testReq.setMeasurement1(300);

        perfClassMet = testReq.checkPerformanceClass(31);
        assertThat(perfClassMet).isEqualTo(true);
    }

    @Test
    public void checkPerformanceClass_justAbove() {
        TestReq testReq = TestReq.create();
        boolean perfClassMet;

        // setting measurements to meet pc 31
        testReq.setMeasurement1(301);

        perfClassMet = testReq.checkPerformanceClass(30);
        assertThat(perfClassMet).isEqualTo(true);
    }

    @Test
    public void checkPerformanceClass_OutOfRange() {
        TestReq testReq = TestReq.create();
        boolean perfClassMet;

        // setting measurements to meet pc 31
        testReq.setMeasurement1(300);

        // performance class 33 not handled by testReq, so expected result is true
        perfClassMet = testReq.checkPerformanceClass(33);
        assertThat(perfClassMet).isEqualTo(true);
    }

    @Test
    public void checkPerformanceClass_UnsetMeasurement() {
        TestReq testReq = TestReq.create();

        assertThrows(
            IllegalStateException.class,
            () -> testReq.checkPerformanceClass(31));
    }

    @Test
    public void writeLogAndCheck_UnsetMeasurement() {
        TestReq testReq = TestReq.create();
        DeviceReportLog testLog = new DeviceReportLog("test", "test");

        assertThrows(
            IllegalStateException.class,
            () -> testReq.writeLogAndCheck(testLog, "writeLogAndCheck_UnsetMeasurement"));
    }
}
