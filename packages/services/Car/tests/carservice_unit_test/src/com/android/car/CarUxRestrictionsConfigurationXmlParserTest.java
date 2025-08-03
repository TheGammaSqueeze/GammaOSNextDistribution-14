/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.CarOccupantZoneManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.content.Context;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarUxRestrictionsConfigurationXmlParserTest {

    private static final String UX_RESTRICTION_MODE_PASSENGER = "passenger";

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testParsingDefaultConfiguration() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfigurationXmlParser.parse(getContext(), R.xml.car_ux_restrictions_map);
    }

    @Test
    public void testParsingParameters() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_only_parameters).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(1, r.getMaxContentDepth());
        assertEquals(1, r.getMaxCumulativeContentItems());
        assertEquals(1, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingNonMovingState() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_non_moving_state).get(0);

        CarUxRestrictions parked = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertFalse(parked.isRequiresDistractionOptimization());

        CarUxRestrictions idling = config.getUxRestrictions(DRIVING_STATE_IDLING, 0f);
        assertTrue(idling.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, idling.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_NoSpeedRange() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_no_speed_range).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(r.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, r.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_SingleSpeedRange()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_single_speed_range).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(r.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, r.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_MultiSpeedRange()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_single_speed_range).get(0);

        CarUxRestrictions slow = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(slow.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, slow.getActiveRestrictions());

        CarUxRestrictions fast = config.getUxRestrictions(DRIVING_STATE_MOVING, 6f);
        assertTrue(fast.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, fast.getActiveRestrictions());
    }

    @Test
    public void testParsingPassengerState() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_passenger_mode).get(0);

        CarUxRestrictions moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(moving.isRequiresDistractionOptimization());

        CarUxRestrictions idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(idling.isRequiresDistractionOptimization());

        CarUxRestrictions parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(parked.isRequiresDistractionOptimization());
    }

    @Test
    public void testParsingMultipleModes() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_multi_mode).get(0);
        CarUxRestrictions moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, "mode1");
        assertFalse(moving.isRequiresDistractionOptimization());

        moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, "mode2");
        assertFalse(moving.isRequiresDistractionOptimization());

        CarUxRestrictions idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, "mode1");
        assertFalse(idling.isRequiresDistractionOptimization());

        idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, "mode2");
        assertFalse(idling.isRequiresDistractionOptimization());

        CarUxRestrictions parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, "mode1");
        assertFalse(parked.isRequiresDistractionOptimization());

        parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, "mode2");
        assertFalse(parked.isRequiresDistractionOptimization());

        parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, "mode3");
        assertFalse(parked.isRequiresDistractionOptimization());
    }

    @Test
    public void testParsingPassengerMode_ValuesInBaselineAreNotAffected()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_passenger_mode).get(0);

        CarUxRestrictions moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(moving.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, moving.getActiveRestrictions());

        CarUxRestrictions idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(idling.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, idling.getActiveRestrictions());

        CarUxRestrictions parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(parked.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, parked.getActiveRestrictions());
    }

    @Test
    public void testParsingMultipleConfigurations()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_multiple_display_ports);

        assertEquals(configs.size(), 2);

        // 1 and 2 are specified in test xml.
        Set<Integer> expected = new ArraySet<>();
        expected.add(1);
        expected.add(2);
        for (CarUxRestrictionsConfiguration config : configs) {
            assertTrue(expected.contains(config.getPhysicalPort()));
            assertThat(config.getOccupantZoneId()).isEqualTo(
                    CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID);
            assertThat(config.getDisplayType()).isEqualTo(
                    CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN);
        }
    }

    @Test
    public void testMultipleConfigurationsShareParameters()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_multiple_display_ports);

        for (CarUxRestrictionsConfiguration config : configs) {
            CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
            assertEquals(1, r.getMaxContentDepth());
            assertEquals(1, r.getMaxCumulativeContentItems());
            assertEquals(1, r.getMaxRestrictedStringLength());
        }
    }

    @Test
    public void testParsingConfigurationWithDisplayConfig()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_display_config);

        assertThat(configs).hasSize(1);
        CarUxRestrictionsConfiguration config = configs.get(0);
        // occupant zone id and display type are specified in the xml file.
        assertThat(config.getOccupantZoneId()).isEqualTo(1);
        assertThat(config.getDisplayType()).isEqualTo(1);
    }

    @Test
    public void testParsingConfigurationMultipleRestrictionParameters()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_multiple_restriction_parameters);

        assertThat(configs).hasSize(1);
        CarUxRestrictionsConfiguration config = configs.get(0);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(5, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingConfigurationMultipleRestrictionParametersLocalAndGlobal()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(getContext(),
                        R.xml.ux_restrictions_multiple_restriction_parameters_local_and_global);

        assertThat(configs).hasSize(2);

        CarUxRestrictionsConfiguration config = configs.get(1);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(1, r.getMaxContentDepth());
        assertEquals(1, r.getMaxCumulativeContentItems());
        assertEquals(1, r.getMaxRestrictedStringLength());


        config = configs.get(0);
        r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(5, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingConfigurationMultipleRestrictionParametersOnlyLocal()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(),
                        R.xml.ux_restrictions_multiple_restriction_parameters_only_local);

        assertThat(configs).hasSize(2);

        CarUxRestrictionsConfiguration config = configs.get(0);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(3, r.getMaxContentDepth());
        assertEquals(3, r.getMaxCumulativeContentItems());
        assertEquals(3, r.getMaxRestrictedStringLength());


        config = configs.get(1);
        r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(5, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingConfigurationMultipleRestrictionParametersMultipleDrivingState()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(getContext(),
                        R.xml.ux_restrictions_multiple_restriction_parameters_multiple_driving_state
                );

        assertThat(configs).hasSize(1);

        CarUxRestrictionsConfiguration config = configs.get(0);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(5, r.getMaxRestrictedStringLength());

        r = config.getUxRestrictions(DRIVING_STATE_IDLING, 0f);
        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(5, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingConfigurationIncorrectRestrictionParameters()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_incorrect_restriction_parameters);

        CarUxRestrictionsConfiguration config = configs.get(0);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);

        assertEquals(1, r.getMaxContentDepth());
        assertEquals(1, r.getMaxCumulativeContentItems());
        assertEquals(1, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingConfigurationNoStringRestrictionParameters()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_no_string_restriction_parameters);

        CarUxRestrictionsConfiguration config = configs.get(0);
        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);

        assertEquals(5, r.getMaxContentDepth());
        assertEquals(5, r.getMaxCumulativeContentItems());
        assertEquals(1, r.getMaxRestrictedStringLength());
    }
}
