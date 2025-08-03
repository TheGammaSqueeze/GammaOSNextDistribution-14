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

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.AccessForVehicleProperty;
import android.hardware.automotive.vehicle.ChangeModeForVehicleProperty;
import android.hardware.automotive.vehicle.EvStoppingMode;
import android.hardware.automotive.vehicle.PortLocationType;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.TestVendorProperty;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehicleAreaDoor;
import android.hardware.automotive.vehicle.VehicleAreaMirror;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehicleAreaWheel;
import android.hardware.automotive.vehicle.VehicleAreaWindow;
import android.hardware.automotive.vehicle.VehicleHvacFanDirection;
import android.hardware.automotive.vehicle.VehicleLightState;
import android.hardware.automotive.vehicle.VehicleLightSwitch;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.util.Pair;
import android.util.SparseArray;

import com.android.car.CarLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A JSON parser class to get configs and values from JSON config files.
 */
public final class FakeVhalConfigParser {

    private static final String TAG = CarLog.tagFor(FakeVhalConfigParser.class);
    private static final String ENUM_CLASS_DIRECTORY = "android.hardware.automotive.vehicle.";
    private static final String JSON_FIELD_NAME_ROOT = "properties";
    private static final String JSON_FIELD_NAME_PROPERTY_ID = "property";
    private static final String JSON_FIELD_NAME_DEFAULT_VALUE = "defaultValue";
    private static final String JSON_FIELD_NAME_AREAS = "areas";
    private static final String JSON_FIELD_NAME_CONFIG_ARRAY = "configArray";
    private static final String JSON_FIELD_NAME_CONFIG_STRING = "configString";
    private static final String JSON_FIELD_NAME_MIN_SAMPLE_RATE = "minSampleRate";
    private static final String JSON_FIELD_NAME_MAX_SAMPLE_RATE = "maxSampleRate";
    private static final String JSON_FIELD_NAME_AREA_ID = "areaId";
    private static final String JSON_FIELD_NAME_INT32_VALUES = "int32Values";
    private static final String JSON_FIELD_NAME_INT64_VALUES = "int64Values";
    private static final String JSON_FIELD_NAME_FLOAT_VALUES = "floatValues";
    private static final String JSON_FIELD_NAME_STRING_VALUE = "stringValue";
    private static final String JSON_FIELD_NAME_MIN_INT32_VALUE = "minInt32Value";
    private static final String JSON_FIELD_NAME_MAX_INT32_VALUE = "maxInt32Value";
    private static final String JSON_FIELD_NAME_MIN_FLOAT_VALUE = "minFloatValue";
    private static final String JSON_FIELD_NAME_MAX_FLOAT_VALUE = "maxFloatValue";
    private static final String JSON_FIELD_NAME_ACCESS = "access";
    private static final String JSON_FIELD_NAME_CHANGE_MODE = "changeMode";
    private static final String JSON_FIELD_NAME_COMMENT = "comment";
    // Following values are defined in PropertyUtils.h file
    // (hardware/interfaces/automotive/vehicle/aidl/impl/utils/common/include/PropertyUtils.h).
    private static final int DOOR_1_RIGHT = VehicleAreaDoor.ROW_1_RIGHT;
    private static final int DOOR_1_LEFT = VehicleAreaDoor.ROW_1_LEFT;
    private static final int DOOR_2_RIGHT = VehicleAreaDoor.ROW_2_RIGHT;
    private static final int DOOR_2_LEFT = VehicleAreaDoor.ROW_2_LEFT;
    private static final int DOOR_REAR = VehicleAreaDoor.REAR;
    private static final int WINDOW_1_LEFT = VehicleAreaWindow.ROW_1_LEFT;
    private static final int WINDOW_1_RIGHT = VehicleAreaWindow.ROW_1_RIGHT;
    private static final int WINDOW_2_LEFT = VehicleAreaWindow.ROW_2_LEFT;
    private static final int WINDOW_2_RIGHT = VehicleAreaWindow.ROW_2_RIGHT;
    private static final int WINDOW_ROOF_TOP_1 = VehicleAreaWindow.ROOF_TOP_1;
    private static final int SEAT_1_RIGHT = VehicleAreaSeat.ROW_1_RIGHT;
    private static final int SEAT_1_LEFT = VehicleAreaSeat.ROW_1_LEFT;
    private static final int SEAT_2_RIGHT = VehicleAreaSeat.ROW_2_RIGHT;
    private static final int SEAT_2_LEFT = VehicleAreaSeat.ROW_2_LEFT;
    private static final int SEAT_2_CENTER = VehicleAreaSeat.ROW_2_CENTER;
    private static final int WHEEL_REAR_RIGHT = VehicleAreaWheel.RIGHT_REAR;
    private static final int WHEEL_REAR_LEFT = VehicleAreaWheel.LEFT_REAR;
    private static final int WHEEL_FRONT_RIGHT = VehicleAreaWheel.RIGHT_FRONT;
    private static final int WHEEL_FRONT_LEFT = VehicleAreaWheel.LEFT_FRONT;
    private static final int CHARGE_PORT_FRONT_LEFT = PortLocationType.FRONT_LEFT;
    private static final int CHARGE_PORT_REAR_LEFT = PortLocationType.REAR_LEFT;
    private static final int FAN_DIRECTION_UNKNOWN = VehicleHvacFanDirection.UNKNOWN;
    private static final int FAN_DIRECTION_FLOOR = VehicleHvacFanDirection.FLOOR;
    private static final int FAN_DIRECTION_FACE = VehicleHvacFanDirection.FACE;
    private static final int FAN_DIRECTION_DEFROST = VehicleHvacFanDirection.DEFROST;
    private static final int FUEL_DOOR_REAR_LEFT = PortLocationType.REAR_LEFT;
    // TODO(b/241984846) Keep SEAT_2_CENTER from HVAC_LEFT here. May have a new design to handle
    //  HVAC zone ids.
    private static final int HVAC_LEFT = SEAT_1_LEFT | SEAT_2_LEFT | SEAT_2_CENTER;
    private static final int HVAC_RIGHT = SEAT_1_RIGHT | SEAT_2_RIGHT;
    private static final int HVAC_ALL = HVAC_LEFT | HVAC_RIGHT;
    private static final int LIGHT_STATE_ON = VehicleLightState.ON;
    private static final int LIGHT_STATE_OFF = VehicleLightState.OFF;
    private static final int LIGHT_SWITCH_ON = VehicleLightSwitch.ON;
    private static final int LIGHT_SWITCH_OFF = VehicleLightSwitch.OFF;
    private static final int LIGHT_SWITCH_AUTO = VehicleLightSwitch.AUTOMATIC;
    private static final int EV_STOPPING_MODE_CREEP = EvStoppingMode.CREEP;
    private static final int EV_STOPPING_MODE_ROLL = EvStoppingMode.ROLL;
    private static final int EV_STOPPING_MODE_HOLD = EvStoppingMode.HOLD;
    private static final int MIRROR_DRIVER_LEFT_RIGHT = VehicleAreaMirror.DRIVER_LEFT
                                | VehicleAreaMirror.DRIVER_RIGHT;

    private static final Map<String, Integer> CONSTANTS_BY_NAME = Map.ofEntries(
            Map.entry("DOOR_1_RIGHT", DOOR_1_RIGHT),
            Map.entry("DOOR_1_LEFT", DOOR_1_LEFT),
            Map.entry("DOOR_2_RIGHT", DOOR_2_RIGHT),
            Map.entry("DOOR_2_LEFT", DOOR_2_LEFT),
            Map.entry("DOOR_REAR", DOOR_REAR),
            Map.entry("HVAC_ALL", HVAC_ALL),
            Map.entry("HVAC_LEFT", HVAC_LEFT),
            Map.entry("HVAC_RIGHT", HVAC_RIGHT),
            Map.entry("VENDOR_EXTENSION_INT_PROPERTY",
                    TestVendorProperty.VENDOR_EXTENSION_INT_PROPERTY),
            Map.entry("VENDOR_EXTENSION_BOOLEAN_PROPERTY",
                    TestVendorProperty.VENDOR_EXTENSION_BOOLEAN_PROPERTY),
            Map.entry("VENDOR_EXTENSION_STRING_PROPERTY",
                    TestVendorProperty.VENDOR_EXTENSION_STRING_PROPERTY),
            Map.entry("VENDOR_EXTENSION_FLOAT_PROPERTY",
                    TestVendorProperty.VENDOR_EXTENSION_FLOAT_PROPERTY),
            Map.entry("WINDOW_1_LEFT", WINDOW_1_LEFT),
            Map.entry("WINDOW_1_RIGHT", WINDOW_1_RIGHT),
            Map.entry("WINDOW_2_LEFT", WINDOW_2_LEFT),
            Map.entry("WINDOW_2_RIGHT", WINDOW_2_RIGHT),
            Map.entry("WINDOW_ROOF_TOP_1", WINDOW_ROOF_TOP_1),
            Map.entry("WINDOW_1_RIGHT_2_LEFT_2_RIGHT", WINDOW_1_RIGHT | WINDOW_2_LEFT
                    | WINDOW_2_RIGHT),
            Map.entry("SEAT_1_LEFT", SEAT_1_LEFT),
            Map.entry("SEAT_1_RIGHT", SEAT_1_RIGHT),
            Map.entry("SEAT_2_LEFT", SEAT_2_LEFT),
            Map.entry("SEAT_2_RIGHT", SEAT_2_RIGHT),
            Map.entry("SEAT_2_CENTER", SEAT_2_CENTER),
            Map.entry("SEAT_2_LEFT_2_RIGHT_2_CENTER", SEAT_2_LEFT | SEAT_2_RIGHT | SEAT_2_CENTER),
            Map.entry("WHEEL_REAR_RIGHT", WHEEL_REAR_RIGHT),
            Map.entry("WHEEL_REAR_LEFT", WHEEL_REAR_LEFT),
            Map.entry("WHEEL_FRONT_RIGHT", WHEEL_FRONT_RIGHT),
            Map.entry("WHEEL_FRONT_LEFT", WHEEL_FRONT_LEFT),
            Map.entry("CHARGE_PORT_FRONT_LEFT", CHARGE_PORT_FRONT_LEFT),
            Map.entry("CHARGE_PORT_REAR_LEFT", CHARGE_PORT_REAR_LEFT),
            Map.entry("FAN_DIRECTION_UNKNOWN", FAN_DIRECTION_UNKNOWN),
            Map.entry("FAN_DIRECTION_FLOOR", FAN_DIRECTION_FLOOR),
            Map.entry("FAN_DIRECTION_FACE", FAN_DIRECTION_FACE),
            Map.entry("FAN_DIRECTION_DEFROST", FAN_DIRECTION_DEFROST),
            Map.entry("FAN_DIRECTION_FACE_FLOOR", FAN_DIRECTION_FACE | FAN_DIRECTION_FLOOR),
            Map.entry("FAN_DIRECTION_FACE_DEFROST", FAN_DIRECTION_FACE | FAN_DIRECTION_DEFROST),
            Map.entry("FAN_DIRECTION_FLOOR_DEFROST", FAN_DIRECTION_FLOOR | FAN_DIRECTION_DEFROST),
            Map.entry("FAN_DIRECTION_FLOOR_DEFROST_FACE", FAN_DIRECTION_FLOOR
                    | FAN_DIRECTION_DEFROST | FAN_DIRECTION_FACE),
            Map.entry("FUEL_DOOR_REAR_LEFT", FUEL_DOOR_REAR_LEFT),
            Map.entry("LIGHT_STATE_ON", LIGHT_STATE_ON),
            Map.entry("LIGHT_STATE_OFF", LIGHT_STATE_OFF),
            Map.entry("LIGHT_SWITCH_ON", LIGHT_SWITCH_ON),
            Map.entry("LIGHT_SWITCH_OFF", LIGHT_SWITCH_OFF),
            Map.entry("LIGHT_SWITCH_AUTO", LIGHT_SWITCH_AUTO),
            Map.entry("EV_STOPPING_MODE_CREEP", EV_STOPPING_MODE_CREEP),
            Map.entry("EV_STOPPING_MODE_ROLL", EV_STOPPING_MODE_ROLL),
            Map.entry("EV_STOPPING_MODE_HOLD", EV_STOPPING_MODE_HOLD),
            Map.entry("MIRROR_DRIVER_LEFT_RIGHT", MIRROR_DRIVER_LEFT_RIGHT),
            Map.entry("ECHO_REVERSE_BYTES", TestVendorProperty.ECHO_REVERSE_BYTES),
            Map.entry("VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING",
                    TestVendorProperty.VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING),
            Map.entry("kMixedTypePropertyForTest", TestVendorProperty.MIXED_TYPE_PROPERTY_FOR_TEST),
            Map.entry("VENDOR_CLUSTER_NAVIGATION_STATE",
                    TestVendorProperty.VENDOR_CLUSTER_NAVIGATION_STATE),
            Map.entry("VENDOR_CLUSTER_REQUEST_DISPLAY",
                    TestVendorProperty.VENDOR_CLUSTER_REQUEST_DISPLAY),
            Map.entry("VENDOR_CLUSTER_SWITCH_UI", TestVendorProperty.VENDOR_CLUSTER_SWITCH_UI),
            Map.entry("VENDOR_CLUSTER_DISPLAY_STATE",
                    TestVendorProperty.VENDOR_CLUSTER_DISPLAY_STATE),
            Map.entry("VENDOR_CLUSTER_REPORT_STATE",
                    TestVendorProperty.VENDOR_CLUSTER_REPORT_STATE),
            Map.entry("PLACEHOLDER_PROPERTY_INT", TestVendorProperty.PLACEHOLDER_PROPERTY_INT),
            Map.entry("PLACEHOLDER_PROPERTY_FLOAT", TestVendorProperty.PLACEHOLDER_PROPERTY_FLOAT),
            Map.entry("PLACEHOLDER_PROPERTY_BOOLEAN",
                    TestVendorProperty.PLACEHOLDER_PROPERTY_BOOLEAN),
            Map.entry("PLACEHOLDER_PROPERTY_STRING",
                    TestVendorProperty.PLACEHOLDER_PROPERTY_STRING)
    );

    /**
     * Reads custom config files and parses the JSON root object whose field name is "properties".
     *
     * @param customConfigFile The custom config JSON file to parse from.
     * @return a list of {@link ConfigDeclaration} storing configs and values for each property.
     * @throws IOException if unable to read the config file.
     * @throws IllegalArgumentException if file is invalid JSON or when a JSONException is caught.
     */
    public SparseArray<ConfigDeclaration> parseJsonConfig(File customConfigFile) throws
            IOException, IllegalArgumentException {
        // Check if config file exists.
        if (!isFileValid(customConfigFile)) {
            Slogf.w(TAG, "Custom config file: %s is not a valid file.", customConfigFile.getPath());
            return new SparseArray<>(/* initialCapacity= */ 0);
        }

        FileInputStream customConfigFileStream = new FileInputStream(customConfigFile);
        if (customConfigFileStream.available() == 0) {
            Slogf.w(TAG, "Custom config file: %s is empty.", customConfigFile.getPath());
            return new SparseArray<>(/* initialCapacity= */ 0);
        }
        return parseJsonConfig(customConfigFileStream);
    }

    /**
     * Reads default config file from java resources which is in the type of input stream.
     *
     * @param configInputStream The {@link InputStream} to parse from.
     * @return a list of {@link ConfigDeclaration} storing configs and values for each property.
     * @throws IOException if unable to read the config file.
     * @throws IllegalArgumentException if file is invalid JSON or when a JSONException is caught.
     */
    public SparseArray<ConfigDeclaration> parseJsonConfig(InputStream configInputStream)
            throws IOException {
        String configString = new String(configInputStream.readAllBytes());

        // Parse JSON root object.
        JSONObject configJsonObject;
        JSONArray configJsonArray;
        try {
            configJsonObject = new JSONObject(configString);
        } catch (JSONException e) {
            throw new IllegalArgumentException("This file does not contain a valid JSONObject.", e);
        }
        try {
            configJsonArray = configJsonObject.getJSONArray(JSON_FIELD_NAME_ROOT);
        } catch (JSONException e) {
            throw new IllegalArgumentException(JSON_FIELD_NAME_ROOT + " field value is not a valid "
                + "JSONArray.", e);
        }

        SparseArray<ConfigDeclaration> allPropConfigs = new SparseArray<>();
        List<String> errors = new ArrayList<>();
        // Parse each property.
        for (int i = 0; i < configJsonArray.length(); i++) {
            JSONObject propertyObject = configJsonArray.optJSONObject(i);
            if (propertyObject == null) {
                errors.add(JSON_FIELD_NAME_ROOT + " array has an invalid JSON element at index "
                        + i);
                continue;
            }
            ConfigDeclaration propConfig = parseEachProperty(propertyObject, errors);
            if (propConfig == null) {
                errors.add("Unable to parse JSON object: " + propertyObject + " at index " + i);
                if (allPropConfigs.size() != 0) {
                    errors.add("Last successfully parsed property Id: "
                            + allPropConfigs.valueAt(allPropConfigs.size() - 1).getConfig().prop);
                }
                continue;
            }
            allPropConfigs.put(propConfig.getConfig().prop, propConfig);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
        return allPropConfigs;
    }

    /**
     * Parses each property for its configs and values.
     *
     * @param propertyObject A JSONObject which stores all configs and values of a property.
     * @param errors A list to keep all errors.
     * @return a {@link ConfigDeclaration} instance, null if failed to parse.
     */
    @Nullable
    private ConfigDeclaration parseEachProperty(JSONObject propertyObject, List<String> errors) {
        int initialErrorCount = errors.size();
        List<String> fieldNames = getFieldNames(propertyObject);

        if (fieldNames == null) {
            errors.add("The JSONObject " + propertyObject + " is empty.");
            return null;
        }

        VehiclePropConfig vehiclePropConfig = new VehiclePropConfig();
        vehiclePropConfig.prop = VehicleProperty.INVALID;
        boolean isAccessSet = false;
        boolean isChangeModeSet = false;
        boolean isAreasSet = false;
        RawPropValues rawPropValues = null;
        JSONArray areas = null;

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            switch (fieldName) {
                case JSON_FIELD_NAME_PROPERTY_ID:
                    vehiclePropConfig.prop = parseIntValue(propertyObject, fieldName, errors);
                    break;
                case JSON_FIELD_NAME_CONFIG_STRING:
                    vehiclePropConfig.configString = parseStringValue(propertyObject, fieldName,
                        errors);
                    break;
                case JSON_FIELD_NAME_MIN_SAMPLE_RATE:
                    vehiclePropConfig.minSampleRate = parseFloatValue(propertyObject, fieldName,
                        errors);
                    break;
                case JSON_FIELD_NAME_MAX_SAMPLE_RATE:
                    vehiclePropConfig.maxSampleRate = parseFloatValue(propertyObject, fieldName,
                        errors);
                    break;
                case JSON_FIELD_NAME_ACCESS:
                    vehiclePropConfig.access = parseIntValue(propertyObject, fieldName, errors);
                    isAccessSet = true;
                    break;
                case JSON_FIELD_NAME_CHANGE_MODE:
                    vehiclePropConfig.changeMode = parseIntValue(propertyObject, fieldName, errors);
                    isChangeModeSet = true;
                    break;
                case JSON_FIELD_NAME_CONFIG_ARRAY:
                    JSONArray configArray = propertyObject.optJSONArray(fieldName);
                    if (configArray == null) {
                        errors.add(fieldName + " doesn't have a mapped JSONArray value.");
                        continue;
                    }
                    vehiclePropConfig.configArray = parseIntArrayValue(configArray, errors);
                    break;
                case JSON_FIELD_NAME_DEFAULT_VALUE:
                    JSONObject defaultValueObject = propertyObject.optJSONObject(fieldName);
                    if (defaultValueObject == null) {
                        Slogf.w(TAG, "%s doesn't have a mapped value.", fieldName);
                        continue;
                    }
                    rawPropValues = parseDefaultValue(defaultValueObject, errors);
                    break;
                case JSON_FIELD_NAME_AREAS:
                    areas = propertyObject.optJSONArray(fieldName);
                    isAreasSet = true;
                    break;
                case JSON_FIELD_NAME_COMMENT:
                    // The "comment" field is used for comment in the config files and is ignored
                    // by the parser.
                    break;
                default:
                    Slogf.i(TAG, "%s is an unknown field name. It didn't get parsed.", fieldName);
            }
        }

        if (vehiclePropConfig.prop == VehicleProperty.INVALID) {
            errors.add(propertyObject + " doesn't have propId. PropId is required.");
            return null;
        }

        if (!isAccessSet) {
            if (AccessForVehicleProperty.values.containsKey(vehiclePropConfig.prop)) {
                vehiclePropConfig.access = AccessForVehicleProperty.values
                        .get(vehiclePropConfig.prop);
            } else {
                errors.add("Access field is not set for this property: " + propertyObject);
            }
        }

        if (!isChangeModeSet) {
            if (ChangeModeForVehicleProperty.values.containsKey(vehiclePropConfig.prop)) {
                vehiclePropConfig.changeMode = ChangeModeForVehicleProperty.values
                        .get(vehiclePropConfig.prop);
            } else {
                errors.add("ChangeMode field is not set for this property: " + propertyObject);
            }
        }

        List<VehicleAreaConfig> areaConfigs = new ArrayList<>();
        SparseArray<RawPropValues> defaultValuesByAreaId = new SparseArray<>();

        if (isAreasSet) {
            if (areas == null) {
                errors.add(JSON_FIELD_NAME_AREAS + " doesn't have a mapped array value.");
            } else {
                for (int j = 0; j < areas.length(); j++) {
                    JSONObject areaObject = areas.optJSONObject(j);
                    if (areaObject == null) {
                        errors.add("Unable to get a JSONObject element for "
                                + JSON_FIELD_NAME_AREAS + " at index " + j);
                        continue;
                    }
                    Pair<VehicleAreaConfig, RawPropValues> result =
                            parseAreaConfig(areaObject, vehiclePropConfig.access, errors);
                    if (result != null) {
                        areaConfigs.add(result.first);
                        if (result.second != null) {
                            defaultValuesByAreaId.put(result.first.areaId, result.second);
                        }
                    }
                }
                vehiclePropConfig.areaConfigs = areaConfigs.toArray(
                        new VehicleAreaConfig[areaConfigs.size()]);
            }
        }

        if (vehiclePropConfig.areaConfigs == null || vehiclePropConfig.areaConfigs.length == 0) {
            VehicleAreaConfig areaConfig = new VehicleAreaConfig();
            areaConfig.areaId = 0;
            areaConfig.access = vehiclePropConfig.access;
            areaConfig.supportVariableUpdateRate = true;
            vehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{areaConfig};
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }

        return new ConfigDeclaration(vehiclePropConfig, rawPropValues, defaultValuesByAreaId);
    }

    /**
     * Parses area JSON config object.
     *
     * @param areaObject A JSONObject of field name "areas".
     * @param errors The list to store all errors.
     * @return a pair of configs and values for one area, null if failed to parse.
     */
    @Nullable
    private Pair<VehicleAreaConfig, RawPropValues> parseAreaConfig(JSONObject areaObject,
            int defaultAccessMode, List<String> errors) {
        int initialErrorCount = errors.size();
        List<String> fieldNames = getFieldNames(areaObject);

        if (fieldNames == null) {
            errors.add("The JSONObject " + areaObject + " is empty.");
            return null;
        }

        VehicleAreaConfig areaConfig = new VehicleAreaConfig();
        RawPropValues defaultValue = null;
        boolean hasAreaId = false;
        boolean isAccessSet = false;

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            switch (fieldName) {
                case JSON_FIELD_NAME_ACCESS:
                    areaConfig.access = parseIntValue(areaObject, fieldName, errors);
                    isAccessSet = true;
                    break;
                case JSON_FIELD_NAME_AREA_ID:
                    areaConfig.areaId = parseIntValue(areaObject, fieldName, errors);
                    hasAreaId = true;
                    break;
                case JSON_FIELD_NAME_MIN_INT32_VALUE:
                    areaConfig.minInt32Value = parseIntValue(areaObject, fieldName, errors);
                    break;
                case JSON_FIELD_NAME_MAX_INT32_VALUE:
                    areaConfig.maxInt32Value = parseIntValue(areaObject, fieldName, errors);
                    break;
                case JSON_FIELD_NAME_MIN_FLOAT_VALUE:
                    areaConfig.minFloatValue = parseFloatValue(areaObject, fieldName, errors);
                    break;
                case JSON_FIELD_NAME_MAX_FLOAT_VALUE:
                    areaConfig.maxFloatValue = parseFloatValue(areaObject, fieldName, errors);
                    break;
                case JSON_FIELD_NAME_DEFAULT_VALUE:
                    defaultValue = parseDefaultValue(areaObject.optJSONObject(fieldName), errors);
                    break;
                default:
                    Slogf.i(TAG, "%s is an unknown field name. It didn't get parsed.", fieldName);
            }
        }

        if (!hasAreaId) {
            errors.add(areaObject + " doesn't have areaId. AreaId is required.");
            return null;
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }

        if (!isAccessSet) {
            areaConfig.access = defaultAccessMode;
        }

        return Pair.create(areaConfig, defaultValue);
    }

    /**
     * Parses the "defaultValue" field of a property object and area property object.
     *
     * @param defaultValue The defaultValue JSONObject to be parsed.
     * @param errors The list to store all errors.
     * @return a {@link RawPropValues} object which stores defaultValue, null if failed to parse.
     */
    @Nullable
    private RawPropValues parseDefaultValue(JSONObject defaultValue, List<String> errors) {
        int initialErrorCount = errors.size();
        List<String> fieldNames = getFieldNames(defaultValue);

        if (fieldNames == null) {
            Slogf.w(TAG, "The JSONObject %s is empty.", defaultValue.toString());
            return null;
        }

        RawPropValues rawPropValues = new RawPropValues();

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            switch (fieldName) {
                case JSON_FIELD_NAME_INT32_VALUES: {
                    JSONArray int32Values = defaultValue.optJSONArray(fieldName);
                    if (int32Values == null) {
                        errors.add("Failed to parse the field name: " + fieldName + " for "
                                + "defaultValueObject: " + defaultValue);
                        continue;
                    }
                    rawPropValues.int32Values = parseIntArrayValue(int32Values, errors);
                    break;
                }
                case JSON_FIELD_NAME_INT64_VALUES: {
                    JSONArray int64Values = defaultValue.optJSONArray(fieldName);
                    if (int64Values == null) {
                        errors.add("Failed to parse the field name: " + fieldName + " for "
                                + "defaultValueObject: " + defaultValue);
                        continue;
                    }
                    rawPropValues.int64Values = parseLongArrayValue(int64Values, errors);
                    break;
                }
                case JSON_FIELD_NAME_FLOAT_VALUES: {
                    JSONArray floatValues = defaultValue.optJSONArray(fieldName);
                    if (floatValues == null) {
                        errors.add("Failed to parse the field name: " + fieldName + " for "
                                + "defaultValueObject: " + defaultValue);
                        continue;
                    }
                    rawPropValues.floatValues = parseFloatArrayValue(floatValues, errors);
                    break;
                }
                case JSON_FIELD_NAME_STRING_VALUE: {
                    rawPropValues.stringValue = parseStringValue(defaultValue, fieldName, errors);
                    break;
                }
                default:
                    Slogf.i(TAG, "%s is an unknown field name. It didn't get parsed.", fieldName);
            }
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }
        return rawPropValues;
    }

    /**
     * Parses String Json value.
     *
     * @param parentObject The JSONObject will be parsed.
     * @param fieldName Field name of JSON object name/value mapping.
     * @param errors The list to store all errors.
     * @return a string of parsed value, null if failed to parse.
     */
    @Nullable
    private String parseStringValue(JSONObject parentObject, String fieldName,
            List<String> errors) {
        String value = parentObject.optString(fieldName);
        if (Objects.equals(value, "")) {
            errors.add(fieldName + " doesn't have a mapped value.");
            return null;
        }
        return value;
    }

    /**
     * Parses int Json value.
     *
     * @param parentObject The JSONObject will be parsed.
     * @param fieldName Field name of JSON object name/value mapping.
     * @param errors The list to store all errors.
     * @return a value as int, 0 if failed to parse.
     */
    private int parseIntValue(JSONObject parentObject, String fieldName, List<String> errors) {
        if (isString(parentObject, fieldName)) {
            String constantValue;
            try {
                constantValue = parentObject.getString(fieldName);
                return parseConstantValue(constantValue, errors);
            } catch (JSONException e) {
                errors.add(fieldName + " doesn't have a mapped string value. " + e.getMessage());
                return 0;
            }
        }
        Object value = parentObject.opt(fieldName);
        if (value != JSONObject.NULL) {
            if (value.getClass() == Integer.class) {
                return parentObject.optInt(fieldName);
            }
            errors.add(fieldName + " doesn't have a mapped int value.");
            return 0;
        }
        errors.add(fieldName + " doesn't have a mapped value.");
        return 0;
    }

    /**
     * Parses float Json value.
     *
     * @param parentObject The JSONObject will be parsed.
     * @param fieldName Field name of JSON object name/value mapping.
     * @param errors The list to store all errors.
     * @return the parsed value as float, {@code 0f} if failed to parse.
     */
    private float parseFloatValue(JSONObject parentObject, String fieldName, List<String> errors) {
        if (isString(parentObject, fieldName)) {
            String constantValue;
            try {
                constantValue = parentObject.getString(fieldName);
            } catch (JSONException e) {
                errors.add(fieldName + " doesn't have a mapped string value. " + e.getMessage());
                return 0f;
            }
            return (float) parseConstantValue(constantValue, errors);
        }
        try {
            return (float) parentObject.getDouble(fieldName);
        } catch (JSONException e) {
            errors.add(fieldName + " doesn't have a mapped float value. " + e.getMessage());
            return 0f;
        }
    }

    /**
     * Parses enum class constant.
     *
     * @param stringValue The constant string to be parsed.
     * @param errors A list to keep all errors.
     * @return the int value of an enum constant, 0 if the constant format is invalid.
     */
    private int parseConstantValue(String stringValue, List<String> errors) {
        String[] propIdStrings = stringValue.split("::");
        if (propIdStrings.length != 2 || propIdStrings[0].isEmpty() || propIdStrings[1].isEmpty()) {
            errors.add(stringValue + " must in the form of <EnumClassName>::<ConstantName>.");
            return 0;
        }
        String enumClassName = ENUM_CLASS_DIRECTORY + propIdStrings[0];
        String constantName = propIdStrings[1];

        if (Objects.equals(propIdStrings[0], "Constants")) {
            if (CONSTANTS_BY_NAME.containsKey(constantName)) {
                return CONSTANTS_BY_NAME.get(constantName);
            }
            errors.add(constantName + " is not a valid constant name.");
            return 0;
        }

        Class enumClass;
        try {
            enumClass = Class.forName(enumClassName);
        } catch (ClassNotFoundException e) {
            errors.add(enumClassName + " is not a valid class name. " + e.getMessage());
            return 0;
        }
        Field[] fields = enumClass.getDeclaredFields();
        for (Field field : fields) {
            if (constantName.equals(field.getName())) {
                try {
                    return field.getInt(enumClass);
                } catch (Exception e) {
                    errors.add("Failed to get int value of " + enumClass + "." + constantName
                            + " " + e.getMessage());
                    return 0;
                }
            }
        }
        errors.add(enumClass + " doesn't have a constant field with name " + constantName);
        return 0;
    }

    /**
     * Parses int values in a {@link JSONArray}.
     *
     * @param values The JSON array to be parsed.
     * @param errors The list to store all errors.
     * @return an int array of default values, null if failed to parse.
     */
    @Nullable
    private int[] parseIntArrayValue(JSONArray values, List<String> errors) {
        int initialErrorCount = errors.size();
        int[] valueArray = new int[values.length()];

        for (int i = 0; i < values.length(); i++) {
            if (isString(values, i)) {
                String stringValue = values.optString(i);
                valueArray[i] = parseConstantValue(stringValue, errors);
            } else {
                try {
                    valueArray[i] = values.getInt(i);
                } catch (JSONException e) {
                    errors.add(values + " doesn't have a mapped int value at index " + i + " "
                            + e.getMessage());
                }
            }
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }

        return valueArray;
    }

    /**
     * Parses long values in a {@link JSONArray}.
     *
     * @param values The JSON array to be parsed.
     * @param errors The list to store all errors.
     * @return a long array of default values, null if failed to parse.
     */
    @Nullable
    private long[] parseLongArrayValue(JSONArray values, List<String> errors) {
        int initialErrorCount = errors.size();
        long[] valueArray = new long[values.length()];

        for (int i = 0; i < values.length(); i++) {
            if (isString(values, i)) {
                String stringValue = values.optString(i);
                valueArray[i] = parseConstantValue(stringValue, errors);
            } else {
                try {
                    valueArray[i] = values.getLong(i);
                } catch (JSONException e) {
                    errors.add(values + " doesn't have a mapped long value at index " + i + " "
                            + e.getMessage());
                }
            }
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }

        return valueArray;
    }

    /**
     * Parses float values in a {@link JSONArray}.
     *
     * @param values The JSON array to be parsed.
     * @param errors The list to store all errors.
     * @return a float array of default value, null if failed to parse.
     */
    @Nullable
    private float[] parseFloatArrayValue(JSONArray values, List<String> errors) {
        int initialErrorCount = errors.size();
        float[] valueArray = new float[values.length()];

        for (int i = 0; i < values.length(); i++) {
            if (isString(values, i)) {
                String stringValue = values.optString(i);
                valueArray[i] = (float) parseConstantValue(stringValue, errors);
            } else {
                try {
                    valueArray[i] = (float) values.getDouble(i);
                } catch (JSONException e) {
                    errors.add(values + " doesn't have a mapped float value at index " + i + " "
                            + e.getMessage());
                }
            }
        }

        if (errors.size() > initialErrorCount) {
            return null;
        }
        return valueArray;
    }

    /**
     * Checks if parentObject contains field and the field is a String.
     *
     * @param parentObject The JSONObject containing the field.
     * @param fieldName The name for the JSON field.
     * @return {@code true} if parent object contains this field and the value is string.
     */
    private boolean isString(JSONObject parentObject, String fieldName) {
        return parentObject.opt(fieldName) != JSONObject.NULL && parentObject.opt(fieldName)
                .getClass() == String.class;
    }

    /**
     * Checks if the JSON array contains the index and the element at the index is a String.
     *
     * @param jsonArray The JSON array to be checked.
     * @param index The index of the JSON array element which will be checked.
     * @return {@code true} if the JSON array has value at index and the value is string.
     */
    private boolean isString(JSONArray jsonArray, int index) {
        return jsonArray.opt(index) != JSONObject.NULL && jsonArray.opt(index).getClass()
                == String.class;
    }

    /**
     * Gets all field names of a {@link JSONObject}.
     *
     * @param jsonObject The JSON object to read field names from.
     * @return a list of all the field names of an JSONObject, null if the object is empty.
     */
    @Nullable
    private List<String> getFieldNames(JSONObject jsonObject) {
        JSONArray names = jsonObject.names();

        if (names == null) {
            return null;
        }

        List<String> fieldNames = new ArrayList<>();
        for (int i = 0; i < names.length(); i++) {
            String fieldName = names.optString(i);
            if (fieldName != null) {
                fieldNames.add(fieldName);
            }
        }
        return fieldNames;
    }

    /**
     * Checks if config file exists and has read permission.
     *
     * @param configFile Either default or custom config JSON file.
     * @return A boolean value to determine if config file is valid.
     */
    private boolean isFileValid(File configFile) {
        return configFile.exists() && configFile.isFile();
    }

    /**
     * Converts system property to vendor property.
     *
     * @param property The property going to be converted.
     * @return an int represents vendor property.
     */
    private static int toVendorProperty(int property) {
        return (property & VehiclePropertyGroup.MASK) | VehiclePropertyGroup.VENDOR;
    }
}
