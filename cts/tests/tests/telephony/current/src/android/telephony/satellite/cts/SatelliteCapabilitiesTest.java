/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteManager;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SatelliteCapabilitiesTest {

    private static final boolean IS_POINTING_REQUIRED = true;
    private static final int MAX_BYTES_PER_OUTGOING_DATAGRAM = 10;
    private static final AntennaDirection ANTENNA_DIRECTION =
            new AntennaDirection(1, 1, 1);
    private static final AntennaPosition ANTENNA_POSITION = new AntennaPosition(ANTENNA_DIRECTION,
            SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
    private static final Set<Integer> SUPPORTED_RADIO_TECHNOLOGIES;
    static {
        SUPPORTED_RADIO_TECHNOLOGIES = new HashSet<>();
        SUPPORTED_RADIO_TECHNOLOGIES.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN);
        SUPPORTED_RADIO_TECHNOLOGIES.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN);
    }
    private static final Map<Integer, AntennaPosition> ANTENNA_POSITION_MAP;
    static {
        ANTENNA_POSITION_MAP = new HashMap<>();
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_OPENED, ANTENNA_POSITION);
    }

    @Test
    public void testConstructorsAndGetters() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(satelliteCapabilities.getSupportedRadioTechnologies())
                .isEqualTo(SUPPORTED_RADIO_TECHNOLOGIES);
        assertThat(satelliteCapabilities.isPointingRequired()).isEqualTo(IS_POINTING_REQUIRED);
        assertThat(satelliteCapabilities.getMaxBytesPerOutgoingDatagram())
                .isEqualTo(MAX_BYTES_PER_OUTGOING_DATAGRAM);
        assertThat(satelliteCapabilities.getAntennaPositionMap()).isEqualTo(ANTENNA_POSITION_MAP);
    }

    @Test
    public void testEquals() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        SatelliteCapabilities equalsSatelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(satelliteCapabilities).isEqualTo(equalsSatelliteCapabilities);
    }

    @Test
    public void testNotEquals() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        Set<Integer> radioTechnologies = new HashSet<>();
        radioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN);
        SatelliteCapabilities notEqualsSatelliteCapabilities = new SatelliteCapabilities(
                radioTechnologies, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);
        assertThat(satelliteCapabilities).isNotEqualTo(notEqualsSatelliteCapabilities);

        satelliteCapabilities = new SatelliteCapabilities(SUPPORTED_RADIO_TECHNOLOGIES,
                false, MAX_BYTES_PER_OUTGOING_DATAGRAM, ANTENNA_POSITION_MAP);
        assertThat(satelliteCapabilities).isNotEqualTo(notEqualsSatelliteCapabilities);

        satelliteCapabilities = new SatelliteCapabilities(SUPPORTED_RADIO_TECHNOLOGIES,
                IS_POINTING_REQUIRED, 100, ANTENNA_POSITION_MAP);
        assertThat(satelliteCapabilities).isNotEqualTo(notEqualsSatelliteCapabilities);

        Map<Integer, AntennaPosition> antennaPositionMap = new HashMap<>();
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT);
        antennaPositionMap.put(SatelliteManager.DISPLAY_MODE_CLOSED, antennaPosition);
        assertThat(satelliteCapabilities).isNotEqualTo(notEqualsSatelliteCapabilities);
    }

    @Test
    public void testParcel() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        Parcel parcel = Parcel.obtain();
        satelliteCapabilities.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SatelliteCapabilities fromParcel = SatelliteCapabilities.CREATOR.createFromParcel(parcel);
        assertThat(satelliteCapabilities).isEqualTo(fromParcel);
    }

    @Test
    public void testGetSupportedRadioTechnologies() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(SUPPORTED_RADIO_TECHNOLOGIES)
                .isEqualTo(satelliteCapabilities.getSupportedRadioTechnologies());
    }

    @Test
    public void testIsPointingRequired() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(IS_POINTING_REQUIRED).isEqualTo(satelliteCapabilities.isPointingRequired());
    }

    @Test
    public void testGetMaxBytesPerOutgoingDatagram() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(MAX_BYTES_PER_OUTGOING_DATAGRAM)
                .isEqualTo(satelliteCapabilities.getMaxBytesPerOutgoingDatagram());
    }

    @Test
    public void testGetAntennaPositionMap() {
        SatelliteCapabilities satelliteCapabilities = new SatelliteCapabilities(
                SUPPORTED_RADIO_TECHNOLOGIES, IS_POINTING_REQUIRED, MAX_BYTES_PER_OUTGOING_DATAGRAM,
                ANTENNA_POSITION_MAP);

        assertThat(ANTENNA_POSITION_MAP).isEqualTo(satelliteCapabilities.getAntennaPositionMap());
    }
}
