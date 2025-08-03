/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.car.occupantawareness.DriverMonitoringDetection;
import android.car.occupantawareness.GazeDetection;
import android.car.occupantawareness.OccupantAwarenessDetection;
import android.car.occupantawareness.SystemStatusEvent;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.occupant_awareness.ConfidenceLevel;
import android.hardware.automotive.occupant_awareness.IOccupantAwareness;
import android.hardware.automotive.occupant_awareness.OccupantAwarenessStatus;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public final class OccupantAwarenessUtilsTest extends AbstractExpectableTestCase {
    private static final byte INVALID_OCCUPANT_AWARENESS_STATUS = -1;
    private static final int INVALID_IOCCUPANT_AWARENESS = -1;

    @Test
    public void testConvertToStatusEvent_returnsSystemStatusReady() {
        SystemStatusEvent event =
                OccupantAwarenessUtils.convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                OccupantAwarenessStatus.READY);

        assertThat(event.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_READY);
    }

    @Test
    public void testConvertToStatusEvent_returnsSystemStatusNotSupported() {
        SystemStatusEvent event =
                OccupantAwarenessUtils.convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                OccupantAwarenessStatus.NOT_SUPPORTED);

        assertThat(event.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_NOT_SUPPORTED);
    }

    @Test
    public void testConvertToStatusEvent_returnsSystemStatusNotReady() {
        SystemStatusEvent event =
                OccupantAwarenessUtils.convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                OccupantAwarenessStatus.NOT_INITIALIZED);

        assertThat(event.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_NOT_READY);
    }

    @Test
    public void testConvertToStatusEvent_returnsSystemStatusSystemFailure() {
        SystemStatusEvent event =
                OccupantAwarenessUtils.convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                OccupantAwarenessStatus.FAILURE);

        assertThat(event.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_SYSTEM_FAILURE);
    }

    @Test
    public void testConvertToStatusEvent_returnsSystemStatusNotReadyWithInvalidAwarenessStatus() {
        SystemStatusEvent event =
                OccupantAwarenessUtils.convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                INVALID_OCCUPANT_AWARENESS_STATUS);

        assertThat(event.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_NOT_READY);
    }

    @Test
    public void testConvertToStatusEvent_returnsDetectionTypeNone() {
        SystemStatusEvent event = OccupantAwarenessUtils
                .convertToStatusEvent(IOccupantAwareness.CAP_NONE,
                OccupantAwarenessStatus.READY);

        assertThat(event.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testConvertToStatusEvent_returnsDetectionTypePresence() {
        SystemStatusEvent event = OccupantAwarenessUtils
                .convertToStatusEvent(IOccupantAwareness.CAP_PRESENCE_DETECTION,
                OccupantAwarenessStatus.READY);

        assertThat(event.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_PRESENCE);
    }

    @Test
    public void testConvertToStatusEvent_returnsDetectionTypeGaze() {
        SystemStatusEvent event = OccupantAwarenessUtils
                .convertToStatusEvent(IOccupantAwareness.CAP_GAZE_DETECTION,
                OccupantAwarenessStatus.READY);

        assertThat(event.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_GAZE);
    }

    @Test
    public void testConvertToStatusEvent_returnsDetectionTypeMonitoring() {
        SystemStatusEvent event = OccupantAwarenessUtils
                .convertToStatusEvent(IOccupantAwareness.CAP_DRIVER_MONITORING_DETECTION,
                OccupantAwarenessStatus.READY);

        assertThat(event.detectionType)
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_DRIVER_MONITORING);
    }

    @Test
    public void testConvertToStatusEvent_returnsDetectionTypeNoneWithInvalidIOccupantAwareness() {
        SystemStatusEvent event = OccupantAwarenessUtils
                .convertToStatusEvent(INVALID_IOCCUPANT_AWARENESS, OccupantAwarenessStatus.READY);

        assertThat(event.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testConvertToConfidenceScore_returnsConfidenceLevelMax() {
        assertThat(OccupantAwarenessUtils.convertToConfidenceScore(
                android.hardware.automotive.occupant_awareness.ConfidenceLevel.MAX))
                .isEqualTo(OccupantAwarenessDetection.CONFIDENCE_LEVEL_MAX);
    }

    @Test
    public void testConvertToConfidenceScore_returnsConfidenceLevelHigh() {
        assertThat(OccupantAwarenessUtils.convertToConfidenceScore(
                android.hardware.automotive.occupant_awareness.ConfidenceLevel.HIGH))
                .isEqualTo(OccupantAwarenessDetection.CONFIDENCE_LEVEL_HIGH);
    }

    @Test
    public void testConvertToConfidenceScore_returnsConfidenceLevelLow() {
        assertThat(OccupantAwarenessUtils.convertToConfidenceScore(
                android.hardware.automotive.occupant_awareness.ConfidenceLevel.LOW))
                .isEqualTo(OccupantAwarenessDetection.CONFIDENCE_LEVEL_LOW);
    }

    @Test
    public void testConvertToConfidenceScore_returnsDefaultConfidenceLevel() {
        assertThat(OccupantAwarenessUtils.convertToConfidenceScore(
                android.hardware.automotive.occupant_awareness.ConfidenceLevel.NONE))
                .isEqualTo(OccupantAwarenessDetection.CONFIDENCE_LEVEL_NONE);
    }

    @Test
    public void testConvertToPoint3D() {
        assertThat(OccupantAwarenessUtils.convertToPoint3D(new double[] {1, 2, 3})).isNotNull();
    }

    @Test
    public void testConvertToPoint3D_returnsNullWithNullVector() {
        assertThat(OccupantAwarenessUtils.convertToPoint3D(null)).isNull();
    }

    @Test
    public void testConvertToPoint3D_returnsNullWithEmptyVector() {
        assertThat(OccupantAwarenessUtils.convertToPoint3D(new double[0])).isNull();
    }

    @Test
    public void testConvertToPoint3D_returnsNullWithUnderflowVector() {
        assertThat(OccupantAwarenessUtils.convertToPoint3D(new double[] {1, 2})).isNull();
    }

    @Test
    public void testConvertToPoint3D_returnsNullWithOverflowVector() {
        assertThat(OccupantAwarenessUtils.convertToPoint3D(new double[] {1, 2, 3, 4})).isNull();
    }

    @Test
    public void testConvertTimestamp() {
        long someInputTimestamp = 10_000;
        assertThat(OccupantAwarenessUtils.convertTimestamp(someInputTimestamp))
                .isEqualTo(someInputTimestamp);
    }

    @Test
    public void testConvertToRole_returnsNoneWithRoleInvalid() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.INVALID))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_NONE);
    }

    @Test
    public void testConvertToRole_returnsNoneWithRoleUnknown() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.UNKNOWN))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_NONE);
    }

    @Test
    public void testConvertToRole_returnsAllOccupants() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ALL_OCCUPANTS))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ALL_OCCUPANTS);
    }

    @Test
    public void testConvertToRole_returnsDriver() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.DRIVER))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_DRIVER);
    }

    @Test
    public void testConvertToRole_returnsFrontPassenger() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.FRONT_PASSENGER))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_FRONT_PASSENGER);
    }

    @Test
    public void testConvertToRole_returnsRow2PassengerLeft() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_2_PASSENGER_LEFT))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_LEFT);
    }

    @Test
    public void testConvertToRole_returnsRow2PassengerCenter() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_2_PASSENGER_CENTER))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_CENTER);
    }

    @Test
    public void testConvertToRole_returnsRow2PassengerRight() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_2_PASSENGER_RIGHT))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_RIGHT);
    }

    @Test
    public void testConvertToRole_returnsRow3PassengerLeft() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_3_PASSENGER_LEFT))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_3_PASSENGER_LEFT);
    }

    @Test
    public void testConvertToRole_returnsRow3PassengerCenter() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_3_PASSENGER_CENTER))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_3_PASSENGER_CENTER);
    }

    @Test
    public void testConvertToRole_returnsRow3PassengerRight() {
        assertThat(OccupantAwarenessUtils.convertToRole(
                android.hardware.automotive.occupant_awareness.Role.ROW_3_PASSENGER_RIGHT))
                .isEqualTo(OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_3_PASSENGER_RIGHT);
    }

    @Test
    public void testConvertToGazeDetection_returnsProperConfidenceLevel() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();
        // assign an any ConfidenceLevel, such as LOW
        inputDetection.gazeConfidence = ConfidenceLevel.LOW;
        int expected = OccupantAwarenessDetection.CONFIDENCE_LEVEL_LOW;

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.confidenceLevel).isEqualTo(expected);
    }

    @Test
    public void testConvertToGazeDetection_returnsNullLeftAndRightEyePositions() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.leftEyePosition).isNull();
        assertThat(outputDetection.rightEyePosition).isNull();
    }


    @Test
    public void testConvertToGazeDetection_returnsNonNullLeftAndRightEyePositions() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();
        inputDetection.headPosition = new double[] {1.0, 2.0, 3.0};

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.leftEyePosition).isNotNull();
        assertThat(outputDetection.rightEyePosition).isNotNull();
    }

    @Test
    public void testConvertToGazeDetection_returnsNullHeadAndGazeAngleUnitVectors() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.headAngleUnitVector).isNull();
        assertThat(outputDetection.gazeAngleUnitVector).isNull();
    }

    @Test
    public void testConvertToGazeDetection_returnsNonNullHeadAngleUnitVector() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();
        inputDetection.headAngleUnitVector = new double[] {1.0, 2.0, 3.0};

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.headAngleUnitVector).isNotNull();
    }

    @Test
    public void testConvertToGazeDetection_returnsNonNullGazeAngleUnitVector() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();
        inputDetection.gazeAngleUnitVector = new double[] {1.0, 2.0, 3.0};

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.gazeAngleUnitVector).isNotNull();
    }

    @Test
    public void testConvertToDriverMonitoringDetection() {
        byte anyConfidenceScore = ConfidenceLevel.LOW;
        boolean anyIsLookingOnRoad = false;
        long anyGazeDurationMillis = 100;
        android.hardware.automotive.occupant_awareness.DriverMonitoringDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.DriverMonitoringDetection();
        inputDetection.confidenceScore = anyConfidenceScore;
        inputDetection.isLookingOnRoad = anyIsLookingOnRoad;
        inputDetection.gazeDurationMillis = anyGazeDurationMillis;
        int expectedConfidenceLevel =
                OccupantAwarenessUtils.convertToConfidenceScore(anyConfidenceScore);

        DriverMonitoringDetection outputDetection =
                OccupantAwarenessUtils.convertToDriverMonitoringDetection(inputDetection);

        mExpect.that(outputDetection.confidenceLevel).isEqualTo(expectedConfidenceLevel);
        mExpect.that(outputDetection.isLookingOnRoad).isEqualTo(anyIsLookingOnRoad);
        mExpect.that(outputDetection.gazeDurationMillis).isEqualTo(anyGazeDurationMillis);
    }
}
