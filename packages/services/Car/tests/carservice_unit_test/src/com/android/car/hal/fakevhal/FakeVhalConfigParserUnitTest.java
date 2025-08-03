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

package com.android.car.hal.fakevhal;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.hardware.automotive.vehicle.AccessForVehicleProperty;
import android.hardware.automotive.vehicle.ChangeModeForVehicleProperty;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehicleAreaDoor;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehicleAreaWheel;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehicleSeatOccupancyState;
import android.hardware.automotive.vehicle.VehicleUnit;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class FakeVhalConfigParserUnitTest {
    private static final int DOOR_1_LEFT = VehicleAreaDoor.ROW_1_LEFT;
    private static final int SEAT_1_LEFT = VehicleAreaSeat.ROW_1_LEFT;
    private static final int WHEEL_FRONT_LEFT = VehicleAreaWheel.LEFT_FRONT;

    private FakeVhalConfigParser mFakeVhalConfigParser;

    @Before
    public void setUp() {
        mFakeVhalConfigParser = new FakeVhalConfigParser();
    }

    @Test
    public void testDefaultConfigFileIsEmpty() throws Exception {
        InputStream tempFileIS =
                new FileInputStream(createTempFileWithContent(/* fileContent= */ ""));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mFakeVhalConfigParser.parseJsonConfig(tempFileIS));

        assertThat(thrown).hasMessageThat().contains("This file does not contain a valid "
                + "JSONObject.");
    }

    @Test
    public void testCustomConfigFileNotExist() throws Exception {
        File tempFile = new File("NotExist.json");

        SparseArray<ConfigDeclaration> result = mFakeVhalConfigParser.parseJsonConfig(tempFile);

        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testCustomConfigFileIsEmpty() throws Exception {
        File tempFile = createTempFileWithContent(/* fileContent= */ "");

        SparseArray<ConfigDeclaration> result = mFakeVhalConfigParser.parseJsonConfig(tempFile);

        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testConfigFileHaveInvalidJsonObject() throws Exception {
        String fileContent = "This is a config file.";
        File tempFile = createTempFileWithContent(fileContent);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("This file does not contain a valid "
                + "JSONObject.");
    }

    @Test
    public void testConfigFileRootIsNotArray() throws Exception {
        String jsonString = "{\"properties\": 123}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("field value is not a valid JSONArray.");
    }

    @Test
    public void testConfigFileRootHasElementIsNotJsonObject() throws Exception {
        String jsonString = "{\"properties\": [{}, 123]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("properties array has an invalid JSON element"
                + " at index 1");
    }

    @Test
    public void testParseEachPropertyJsonObjectIsEmpty() throws Exception {
        String jsonString = "{\"properties\": [{}]}";
        JSONObject jsonObject = new JSONObject(jsonString);
        Object propertyObject = jsonObject.optJSONArray("properties").optJSONObject(0);
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("The JSONObject " + propertyObject
                + " is empty.");
    }

    @Test
    public void testParsePropertyIdWithIntValue() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123,"
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int propId = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(123).getConfig().prop;

        assertThat(propId).isEqualTo(123);
    }

    @Test
    public void testParsePropertyIdFieldNotExist() throws Exception {
        String jsonString = "{\"properties\": [{\"NotAPropIdFieldName\": 123}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains(" doesn't have propId. PropId is required.");
    }

    @Test
    public void testParsePropertyIdValueNull() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": NULL}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("property doesn't have a mapped value.");
    }

    @Test
    public void testParsePropertyIdWithWrongValueType() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 12.3f}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("property doesn't have a mapped int value.");
    }

    @Test
    public void testParsePropertyIdWithStringValue() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"VehicleProperty::INFO_FUEL_CAPACITY\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int propId = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(VehicleProperty.INFO_FUEL_CAPACITY).getConfig().prop;

        assertThat(propId).isEqualTo(VehicleProperty.INFO_FUEL_CAPACITY);
    }

    @Test
    public void testParsePropertyIdWithEmptyStringValue() throws Exception {
        String jsonString = "{\"properties\": " + "[{\"property\": \"\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("must in the form of "
                + "<EnumClassName>::<ConstantName>.");
    }

    @Test
    public void testParsePropertyIdWithIntStringValue() throws Exception {
        String jsonString = "{\"properties\": " + "[{\"property\": \"1234\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("must in the form of "
                + "<EnumClassName>::<ConstantName>.");
    }

    @Test
    public void testParsePropertyIdWithWrongStringFormat() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"VehicleProperty:INFO_FUEL_CAPACITY\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("must in the form of "
                + "<EnumClassName>::<ConstantName>.");
    }

    @Test
    public void testParsePropertyIdEnumClassNameNotExist() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"NotExistEnumClass::INFO_FUEL_CAPACITY\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("is not a valid class name.");
    }

    @Test
    public void testParsePropertyIdEnumClassFieldNameNotExist() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"VehicleProperty::NOT_EXIST\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("VehicleProperty doesn't have a constant field"
                + " with name NOT_EXIST");
    }

    @Test
    public void testParsePropertyIdEnumClassFieldTypeIsNotInt() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"IVehicle::INVALID_MEMORY_ID\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("Failed to get int value of interface "
                + "android.hardware.automotive.vehicle.IVehicle.INVALID_MEMORY_ID");
    }

    @Test
    public void testParsePropertyIdFromConstantsMap() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": \"Constants::DOOR_1_LEFT\","
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int propId = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(DOOR_1_LEFT).getConfig()
                .prop;

        assertThat(propId).isEqualTo(VehicleAreaDoor.ROW_1_LEFT);
    }

    @Test
    public void testParsePropertyIdFromConstantsMapWithWrongConstantsName() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": \"Constants::ROW_1_LEFT\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("ROW_1_LEFT is not a valid constant name.");
    }

    @Test
    public void testParseStringValueIsEmpty() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123, \"configString\": \"\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("configString doesn't have a mapped value.");
    }

    @Test
    public void testParseFloatValueIsEmptyString() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123, \"minSampleRate\": \"\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains(" must in the form of "
                + "<EnumClassName>::<ConstantName>.");
    }

    @Test
    public void testParseFloatValueIsString() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123, "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"minSampleRate\": \"VehicleUnit::FAHRENHEIT\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        float minSampleRate = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(123).getConfig()
                .minSampleRate;

        assertThat(minSampleRate).isEqualTo(49f);
    }

    @Test
    public void testParseFloatValueIsNull() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123, \"minSampleRate\": null}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("minSampleRate doesn't have a mapped float "
                + "value.");
    }

    @Test
    public void testParseFloatValueIsInt() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123,"
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"minSampleRate\": 456}]}";
        File tempFile = createTempFileWithContent(jsonString);

        float minSampleRate = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(123).getConfig()
                .minSampleRate;

        assertThat(minSampleRate).isEqualTo(456f);
    }

    @Test
    public void testParseAccessFieldOverride() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::INFO_VIN\", "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int access = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(VehicleProperty.INFO_VIN)
                .getConfig().access;

        assertThat(access).isEqualTo(VehiclePropertyAccess.READ_WRITE);
        assertThat(access).isNotEqualTo(AccessForVehicleProperty.values
                .get(VehicleProperty.INFO_VIN));
    }

    @Test
    public void testParseAccessFieldNotSpecifiedDefaultAccessValueExist() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::INFO_VIN\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int access = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(VehicleProperty.INFO_VIN)
                .getConfig().access;

        assertThat(access).isEqualTo(AccessForVehicleProperty.values.get(VehicleProperty.INFO_VIN));
    }

    @Test
    public void testParseAccessFieldNotSpecifiedDefaultAccessValueNotExist() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123,"
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("Access field is not set for this property");
    }

    @Test
    public void testParseChangeModeFieldOverride() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY\", "
                + "\"changeMode\": \"VehiclePropertyChangeMode::ON_CHANGE\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int changeMode = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(VehicleProperty.PERF_VEHICLE_SPEED_DISPLAY).getConfig().changeMode;

        assertThat(changeMode).isEqualTo(VehiclePropertyChangeMode.ON_CHANGE);
        assertThat(changeMode).isNotEqualTo(ChangeModeForVehicleProperty.values
                .get(VehicleProperty.PERF_VEHICLE_SPEED_DISPLAY));
    }

    @Test
    public void testParseChangeModeFieldNotSpecifiedDefaultChangeModeValueExist() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        int changeMode = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(VehicleProperty.PERF_VEHICLE_SPEED_DISPLAY).getConfig().changeMode;

        assertThat(changeMode).isEqualTo(ChangeModeForVehicleProperty.values
                .get(VehicleProperty.PERF_VEHICLE_SPEED_DISPLAY));
    }

    @Test
    public void testParseChangeModeFieldNotSpecifiedDefaultChangeModeValueNotExist()
            throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 123,"
                + "\"access\": \"VehiclePropertyAccess::READ\"}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("ChangeMode field is not set for this "
                + "property");
    }

    @Test
    public void testParseConfigArrayValueIsNotArray() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"configArray\": 123}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("configArray doesn't have a mapped JSONArray "
                + "value.");
    }

    @Test
    public void testParseConfigArrayValueWithNullElement() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"configArray\": "
                + "[123, null]}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("[123,null] doesn't have a mapped int value "
                + "at index 1");
    }

    @Test
    public void testParseDefaultValueIsNull() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": null}]}";
        File tempFile = createTempFileWithContent(jsonString);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration.getInitialValue()).isEqualTo(null);
    }

    @Test
    public void testParseDefaultValueIsEmpty() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": {}}]}";
        File tempFile = createTempFileWithContent(jsonString);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration.getInitialValue()).isEqualTo(null);
    }

    @Test
    public void testParseDefaultValueIntValuesIsNull() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": "
                + "{\"int32Values\": null}}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("Failed to parse the field name: int32Values "
                + "for defaultValueObject: {\"int32Values\":null}");
    }

    @Test
    public void testParseDefaultValueParseLongValues() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": "
                + "{\"int64Values\": [0, 100000, 200000]}}]}";
        File tempFile = createTempFileWithContent(jsonString);

        long[] longValues = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(286261504)
                .getInitialValue().int64Values;
        long[] expectLongValues = {0, 100000, 200000};
        assertThat(longValues).isEqualTo(expectLongValues);
    }

    @Test
    public void testParseDefaultValueFloat() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": "
                + "{\"floatValues\": [2.3, \"VehicleUnit::FAHRENHEIT\"]}}]}";
        File tempFile = createTempFileWithContent(jsonString);

        RawPropValues rawPropValues = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(286261504)
                .getInitialValue();
        RawPropValues expectRawPropertyValues = new RawPropValues();
        expectRawPropertyValues.floatValues = new float[]{2.3f, 49.0f};
        assertThat(rawPropValues).isEqualTo(expectRawPropertyValues);
    }

    @Test
    public void testParseDefaultValueIntValuesString() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"defaultValue\": "
                + "{\"int32Values\": [\"VehicleSeatOccupancyState::VACANT\"]}}]}";
        File tempFile = createTempFileWithContent(jsonString);
        RawPropValues expectRawPropertyValues = new RawPropValues();
        expectRawPropertyValues.int32Values = new int[]{VehicleSeatOccupancyState.VACANT};

        RawPropValues rawPropValues = mFakeVhalConfigParser.parseJsonConfig(tempFile).get(286261504)
                .getInitialValue();

        assertThat(rawPropValues).isEqualTo(expectRawPropertyValues);

    }

    @Test
    public void testParseAreaConfigValueIsNotArray() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": {}}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("areas doesn't have a mapped array value.");
    }

    @Test
    public void testParseAreaConfigValueWithNullElement() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": [null]}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("Unable to get a JSONObject element for areas "
                + "at index 0");
    }

    @Test
    public void testParseAreaConfigValueWithEmptyElement() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": [{}]}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("The JSONObject {} is empty.");
    }

    @Test
    public void testParseAreaConfigValueElementHasNoAreaId() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"minInt32Value\": 0}]}]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("{\"minInt32Value\":0} doesn't have areaId. "
                + "AreaId is required.");
    }

    @Test
    public void testParseAreaConfigValueHasNoAccessLevel() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 10, "
                + "\"defaultValue\": {\"int32Values\": [0]}}]}]}";
        File tempFile = createTempFileWithContent(jsonString);
        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = 286261504;
        vehiclePropConfig.access = AccessForVehicleProperty.values.get(286261504);
        VehicleAreaConfig vehicleAreaConfig = new VehicleAreaConfig();
        vehicleAreaConfig.access = AccessForVehicleProperty.values.get(286261504);
        vehicleAreaConfig.areaId = 1;
        vehicleAreaConfig.minInt32Value = 0;
        vehicleAreaConfig.maxInt32Value = 10;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig};
        RawPropValues areaRawPropValues = new RawPropValues();
        areaRawPropValues.int32Values = new int[]{0};
        SparseArray<RawPropValues> areaValuesByAreaId = new SparseArray<>();
        areaValuesByAreaId.put(vehicleAreaConfig.areaId, areaRawPropValues);
        ConfigDeclaration expectConfigDeclaration = new ConfigDeclaration(vehiclePropConfig,
                null, areaValuesByAreaId);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration).isEqualTo(expectConfigDeclaration);
    }

    @Test
    public void testParseAreaConfigValueHasAccessLevel() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"access\": 2, \"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 10, "
                + "\"defaultValue\": {\"int32Values\": [0]}}]}]}";
        File tempFile = createTempFileWithContent(jsonString);
        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = 286261504;
        vehiclePropConfig.access = AccessForVehicleProperty.values.get(286261504);
        VehicleAreaConfig vehicleAreaConfig = new VehicleAreaConfig();
        vehicleAreaConfig.access = 2;
        vehicleAreaConfig.areaId = 1;
        vehicleAreaConfig.minInt32Value = 0;
        vehicleAreaConfig.maxInt32Value = 10;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig};
        RawPropValues areaRawPropValues = new RawPropValues();
        areaRawPropValues.int32Values = new int[]{0};
        SparseArray<RawPropValues> areaValuesByAreaId = new SparseArray<>();
        areaValuesByAreaId.put(vehicleAreaConfig.areaId, areaRawPropValues);
        ConfigDeclaration expectConfigDeclaration = new ConfigDeclaration(vehiclePropConfig,
                null, areaValuesByAreaId);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration).isEqualTo(expectConfigDeclaration);
    }

    @Test
    public void testParseAreaConfig_PropertyConfigHasAccessLevel() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 10, "
                + "\"defaultValue\": {\"int32Values\": [0]}}], \"access\": 2}]}";
        File tempFile = createTempFileWithContent(jsonString);
        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = 286261504;
        vehiclePropConfig.access = 2;
        VehicleAreaConfig vehicleAreaConfig = new VehicleAreaConfig();
        vehicleAreaConfig.access = 2;
        vehicleAreaConfig.areaId = 1;
        vehicleAreaConfig.minInt32Value = 0;
        vehicleAreaConfig.maxInt32Value = 10;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig};
        RawPropValues areaRawPropValues = new RawPropValues();
        areaRawPropValues.int32Values = new int[]{0};
        SparseArray<RawPropValues> areaValuesByAreaId = new SparseArray<>();
        areaValuesByAreaId.put(vehicleAreaConfig.areaId, areaRawPropValues);
        ConfigDeclaration expectConfigDeclaration = new ConfigDeclaration(vehiclePropConfig,
                null, areaValuesByAreaId);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration).isEqualTo(expectConfigDeclaration);
    }

    @Test
    public void testParseAreaConfig_PropertyAndAreaConfigHasAccessLevel() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"access\": 3, \"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 10, "
                + "\"defaultValue\": {\"int32Values\": [0]}}], \"access\": 2}]}";
        File tempFile = createTempFileWithContent(jsonString);
        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = 286261504;
        vehiclePropConfig.access = 2;
        VehicleAreaConfig vehicleAreaConfig = new VehicleAreaConfig();
        vehicleAreaConfig.access = 3;
        vehicleAreaConfig.areaId = 1;
        vehicleAreaConfig.minInt32Value = 0;
        vehicleAreaConfig.maxInt32Value = 10;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig};
        RawPropValues areaRawPropValues = new RawPropValues();
        areaRawPropValues.int32Values = new int[]{0};
        SparseArray<RawPropValues> areaValuesByAreaId = new SparseArray<>();
        areaValuesByAreaId.put(vehicleAreaConfig.areaId, areaRawPropValues);
        ConfigDeclaration expectConfigDeclaration = new ConfigDeclaration(vehiclePropConfig,
                null, areaValuesByAreaId);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration).isEqualTo(expectConfigDeclaration);
    }

    @Test
    public void testParseAreaConfigWithNoValue() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": 286261504, \"areas\": "
                + "[{\"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 10, "
                + "\"defaultValue\": {\"int32Values\": [0]}}, {\"areaId\": 2}]}]}";
        File tempFile = createTempFileWithContent(jsonString);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(286261504);

        assertThat(configDeclaration.getInitialAreaValuesByAreaId().size()).isEqualTo(1);
        assertThat(configDeclaration.getInitialAreaValuesByAreaId().get(1).int32Values[0])
                .isEqualTo(0);
    }

    @Test
    public void testParseJsonConfig() throws Exception {
        // Create a JSON config object with all field values set.
        String jsonString = "{\"properties\": [{"
                + "             \"property\": "
                + "               \"VehicleProperty::WHEEL_TICK\","
                + "               \"defaultValue\": {"
                + "                 \"int64Values\": [0, 100000, 200000, 300000, 400000]"
                + "               },"
                + "               \"areas\": [{"
                + "                 \"defaultValue\": {"
                + "                   \"int32Values\": [\"VehicleSeatOccupancyState::VACANT\"],"
                + "                   \"floatValues\": [15000.0],"
                + "                   \"stringValue\": \"Toy Vehicle\""
                + "                 },"
                + "                 \"minInt32Value\": 0,"
                + "                 \"maxInt32Value\": 10,"
                + "                 \"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "                 \"areaId\": \"Constants::SEAT_1_LEFT\""
                + "               },{"
                + "                 \"defaultValue\": {"
                + "                   \"int32Values\": [0]"
                + "                 },"
                + "                   \"areaId\": \"Constants::SEAT_1_RIGHT\""
                + "               }],"
                + "            \"configArray\": [15, 50000, 50000, 50000, 50000],"
                + "            \"configString\": \"configString\","
                + "            \"maxSampleRate\": 10.0,"
                + "            \"minSampleRate\": 1.0,"
                + "            \"access\": \"VehiclePropertyAccess::READ\","
                + "            \"changeMode\": \"VehiclePropertyChangeMode::STATIC\""
                + "        }]}";
        File tempFile = createTempFileWithContent(jsonString);
        // Create prop config object
        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = VehicleProperty.WHEEL_TICK;
        vehiclePropConfig.access = VehiclePropertyAccess.READ;
        // Create area config object
        VehicleAreaConfig vehicleAreaConfig1 = new VehicleAreaConfig();
        vehicleAreaConfig1.areaId = VehicleAreaSeat.ROW_1_LEFT;
        vehicleAreaConfig1.minInt32Value = 0;
        vehicleAreaConfig1.maxInt32Value = 10;
        vehicleAreaConfig1.access = VehiclePropertyAccess.READ_WRITE;
        VehicleAreaConfig vehicleAreaConfig2 = new VehicleAreaConfig();
        vehicleAreaConfig2.areaId = VehicleAreaSeat.ROW_1_RIGHT;
        vehicleAreaConfig2.access = VehiclePropertyAccess.READ;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig1,
                vehicleAreaConfig2};
        vehiclePropConfig.configString = "configString";
        vehiclePropConfig.configArray = new int[]{15, 50000, 50000, 50000, 50000};
        vehiclePropConfig.minSampleRate = 1.0f;
        vehiclePropConfig.maxSampleRate = 10.0f;
        vehiclePropConfig.changeMode = VehiclePropertyChangeMode.STATIC;
        // Create default prop value object.
        RawPropValues defaultRawPropValues = new RawPropValues();
        defaultRawPropValues.int64Values = new long[]{0, 100000, 200000, 300000, 400000};
        // Create area prop value objects.
        RawPropValues areaRawPropValues1 = new RawPropValues();
        areaRawPropValues1.int32Values = new int[]{VehicleSeatOccupancyState.VACANT};
        areaRawPropValues1.floatValues = new float[]{15000.0f};
        areaRawPropValues1.stringValue = "Toy Vehicle";
        RawPropValues areaRawPropValues2 = new RawPropValues();
        areaRawPropValues2.int32Values = new int[]{0};
        // Create map from areaId to areaValue.
        SparseArray<RawPropValues> areaValuesByAreaId = new SparseArray<>();
        areaValuesByAreaId.put(vehicleAreaConfig1.areaId, areaRawPropValues1);
        areaValuesByAreaId.put(vehicleAreaConfig2.areaId, areaRawPropValues2);
        // Create expected ConfigDeclaration object.
        ConfigDeclaration expectConfigDeclaration = new ConfigDeclaration(vehiclePropConfig,
                defaultRawPropValues, areaValuesByAreaId);

        ConfigDeclaration configDeclaration = mFakeVhalConfigParser.parseJsonConfig(tempFile)
                .get(VehicleProperty.WHEEL_TICK);

        assertThat(expectConfigDeclaration.getConfig().changeMode).isEqualTo(0);
        assertThat(expectConfigDeclaration).isEqualTo(configDeclaration);
        assertThat(expectConfigDeclaration.hashCode()).isEqualTo(configDeclaration.hashCode());
        assertThat(expectConfigDeclaration.toString()).isEqualTo(configDeclaration.toString());
    }

    @Test
    public void testParseJsonConfigWithMultiErrors() throws Exception {
        String jsonString = "{\"properties\": ["
                + "              {"
                + "                  \"property\": 1234"
                + "              },"
                + "              null,"
                + "              {},"
                + "              {"
                + "                  \"defaultValue\": {"
                + "                      \"int64Values\": [0, 100000, 200000, 300000, 400000]"
                + "                  }"
                + "              },"
                + "              {"
                + "                  \"property\": \"NotExistEnumClass::INFO_FUEL_CAPACITY\""
                + "              },"
                + "              {"
                + "                  \"property\": 286261504,"
                + "                  \"configArray\": 123"
                + "              },"
                + "              {"
                + "                  \"property\": 286261504,"
                + "                  \"defaultValue\": {"
                + "                      \"int32Values\": null"
                + "                  }"
                + "              },"
                + "              {"
                + "                  \"property\": 286261504,"
                + "                  \"areas\": [{"
                + "                      \"minInt32Value\": 0"
                + "                  }]"
                + "              }"
                + "          ]}";
        File tempFile = createTempFileWithContent(jsonString);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mFakeVhalConfigParser.parseJsonConfig(tempFile));

        assertThat(thrown).hasMessageThat().contains("Access field is not set for this property:");
        assertThat(thrown).hasMessageThat().contains("ChangeMode field is not set for this "
                + "property:");
        assertThat(thrown).hasMessageThat().contains("Unable to parse JSON object:");
        assertThat(thrown).hasMessageThat().contains("at index 0");
        assertThat(thrown).hasMessageThat().contains("properties array has an invalid JSON element "
                + "at index 1");
        assertThat(thrown).hasMessageThat().contains("The JSONObject {} is empty.");
        assertThat(thrown).hasMessageThat().contains("at index 2");
        assertThat(thrown).hasMessageThat().contains("doesn't have propId. PropId is required.");
        assertThat(thrown).hasMessageThat().contains("at index 3");
        assertThat(thrown).hasMessageThat().contains("is not a valid class name.");
        assertThat(thrown).hasMessageThat().contains("at index 4");
        assertThat(thrown).hasMessageThat().contains("doesn't have a mapped JSONArray value.");
        assertThat(thrown).hasMessageThat().contains("at index 5");
        assertThat(thrown).hasMessageThat().contains("Failed to parse the field name:");
        assertThat(thrown).hasMessageThat().contains("at index 6");
        assertThat(thrown).hasMessageThat().contains("doesn't have areaId. AreaId is required.");
        assertThat(thrown).hasMessageThat().contains("at index 7");
    }

    @Test
    public void testParseDefaultConfigFile() throws Exception {
        InputStream inputStream = this.getClass().getClassLoader()
                .getResourceAsStream("DefaultProperties.json");

        SparseArray<ConfigDeclaration> result = mFakeVhalConfigParser.parseJsonConfig(inputStream);

        assertThat(result.get(VehicleProperty.INFO_FUEL_CAPACITY).getConfig().prop)
                .isEqualTo(VehicleProperty.INFO_FUEL_CAPACITY);
        assertThat(result.get(VehicleProperty.INFO_FUEL_CAPACITY).getInitialValue().floatValues[0])
                .isEqualTo(15000.0f);
        assertThat(result.get(VehicleProperty.PERF_VEHICLE_SPEED).getConfig().maxSampleRate)
                .isEqualTo(10.0f);
        assertThat(result.get(VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS).getConfig().configArray)
                .isEqualTo(new int[]{VehicleUnit.METER_PER_SEC, VehicleUnit.MILES_PER_HOUR,
                        VehicleUnit.KILOMETERS_PER_HOUR});
        assertThat(result.get(VehicleProperty.SEAT_OCCUPANCY).getInitialValue().int32Values[0])
                .isEqualTo(VehicleSeatOccupancyState.VACANT);
        assertThat(result.get(VehicleProperty.TIRE_PRESSURE).getConfig().areaConfigs[0].areaId)
                .isEqualTo(WHEEL_FRONT_LEFT);
    }

    private File createTempFileWithContent(String fileContent) throws Exception {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(tempFile);
        os.write(fileContent.getBytes());
        return tempFile;
    }
}