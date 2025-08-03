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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.property.CarPropertyManager;
import android.hardware.automotive.vehicle.FuelType;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehicleAreaDoor;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehicleAreaWindow;
import android.hardware.automotive.vehicle.VehicleOilLevel;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.SparseArray;

import com.android.car.IVehicleDeathRecipient;
import com.android.car.VehicleStub;
import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.VehicleStub.GetVehicleStubAsyncResult;
import com.android.car.VehicleStub.SetVehicleStubAsyncResult;
import com.android.car.VehicleStub.SubscriptionClient;
import com.android.car.VehicleStub.VehicleStubCallbackInterface;
import com.android.car.hal.AidlHalPropConfig;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.property.CarPropertyErrorCodes;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class FakeVehicleStubUnitTest {

    private static final int DOOR_1_LEFT = VehicleAreaDoor.ROW_1_LEFT;
    private static final int WINDOW_1_LEFT = VehicleAreaWindow.ROW_1_LEFT;
    private static final int SEAT_1_LEFT = VehicleAreaSeat.ROW_1_LEFT;
    private static final int SEAT_1_RIGHT = VehicleAreaSeat.ROW_1_RIGHT;
    private static final int HVAC_LEFT = SEAT_1_LEFT | VehicleAreaSeat.ROW_2_LEFT
            | VehicleAreaSeat.ROW_2_CENTER;
    private static final int HVAC_ALL = HVAC_LEFT | SEAT_1_RIGHT | VehicleAreaSeat.ROW_2_RIGHT;
    private static final String PROPERTY_CONFIG_STRING_ON_CHANGE = "{\"property\":"
            + "\"VehicleProperty::ENGINE_OIL_LEVEL\","
            + "\"defaultValue\": {\"int32Values\": [\"VehicleOilLevel::NORMAL\"]},"
            + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
            + "\"changeMode\": \"VehiclePropertyChangeMode::ON_CHANGE\"}";
    private static final String PROPERTY_CONFIG_STRING_CONTINUOUS = "{\"property\":"
            + "\"VehicleProperty::FUEL_LEVEL\","
            + "\"defaultValue\": {\"floatValues\": [100]},"
            + "\"access\": \"VehiclePropertyAccess::READ\","
            + "\"changeMode\": \"VehiclePropertyChangeMode::CONTINUOUS\","
            + "\"maxSampleRate\": 100.0,"
            + "\"minSampleRate\": 1.0}";
    private static final String PROPERTY_CONFIG_STRING_CONTINUOUS_2 = "{\"property\":"
            + "\"VehicleProperty::EV_BATTERY_LEVEL\","
            + "\"defaultValue\": {\"floatValues\": [100]},"
            + "\"access\": \"VehiclePropertyAccess::READ\","
            + "\"changeMode\": \"VehiclePropertyChangeMode::CONTINUOUS\","
            + "\"maxSampleRate\": 100.0,"
            + "\"minSampleRate\": 1.0}";
    private static final String PROPERTY_CONFIG_STRING_STATIC = "{\"property\":"
            + "\"VehicleProperty::INFO_FUEL_TYPE\","
            + "\"defaultValue\": {\"int32Values\": [\"FuelType::FUEL_TYPE_UNLEADED\"]},"
            + "\"access\": \"VehiclePropertyAccess::READ\","
            + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\"}";
    private static final String PROPERTY_CONFIG_STRING_HVAC_POWER_ON = "{\"property\":"
            + "\"VehicleProperty::HVAC_POWER_ON\","
            + "\"defaultValue\": {\"int32Values\": [1]},"
            + "\"areas\": [{\"areaId\": \"Constants::HVAC_ALL\"}],"
            + "\"configArray\": [\"VehicleProperty::HVAC_FAN_SPEED\","
            + "\"VehicleProperty::HVAC_FAN_DIRECTION\"]}";
    private static final String PROPERTY_CONFIG_STRING_HVAC_POWER_OFF = "{\"property\":"
            + "\"VehicleProperty::HVAC_POWER_ON\","
            + "\"defaultValue\": {\"int32Values\": [0]},"
            + "\"areas\": [{\"areaId\": \"Constants::HVAC_ALL\"}],"
            + "\"configArray\": [\"VehicleProperty::HVAC_FAN_SPEED\","
            + "\"VehicleProperty::HVAC_FAN_DIRECTION\"]}";
    private static final String PROPERTY_CONFIG_STRING_HVAC_FAN_SPEED = "{\"property\":"
            + "\"VehicleProperty::HVAC_FAN_SPEED\","
            + "\"defaultValue\": {\"int32Values\": [3]},"
            + "\"areas\": [{"
            + "\"areaId\": \"Constants::HVAC_ALL\","
            + "\"minInt32Value\": 1,"
            + "\"maxInt32Value\": 7}]}";
    private static final int DEFAULT_TIMEOUT = 1000;
    @Captor
    private ArgumentCaptor<List<GetVehicleStubAsyncResult>> mGetVehicleStubAsyncResultCaptor;
    @Captor
    private ArgumentCaptor<List<SetVehicleStubAsyncResult>> mSetVehicleStubAsyncResultCaptor;

    @Mock
    private VehicleStub mMockRealVehicleStub;
    @Mock
    private FakeVhalConfigParser mParser;
    @Mock
    private VehicleStubCallbackInterface mCallback;

    @Rule
    public final Expect expect = Expect.create();

    @Before
    public void setup() throws Exception {
        when(mMockRealVehicleStub.isValid()).thenReturn(true);
        when(mMockRealVehicleStub.getAllPropConfigs()).thenReturn(new HalPropConfig[0]);
    }

    @Test
    public void testGetAllPropConfigsWithoutCustomConfig() throws Exception {
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        HalPropConfig[] allPropConfig = fakeVehicleStub.getAllPropConfigs();
        HalPropConfig propConfigInfo = getPropConfigByPropId(allPropConfig,
                VehicleProperty.INFO_MAKE);
        HalPropConfig propConfigWindow = getPropConfigByPropId(allPropConfig,
                VehicleProperty.WINDOW_POS);

        expect.that(fakeVehicleStub.isValid()).isTrue();
        expect.that(propConfigInfo.getPropId()).isEqualTo(VehicleProperty.INFO_MAKE);
        expect.that(propConfigWindow.getAreaConfigs()[0].getAreaId())
                .isEqualTo(VehicleAreaWindow.ROW_1_LEFT);
        expect.that(propConfigWindow.getAreaConfigs()[0].getMaxInt32Value()).isEqualTo(10);
    }

    @Test
    public void testGetAllPropConfigsWithCustomConfigHasExistingPropId() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::TIRE_PRESSURE\","
                + "\"defaultValue\": {\"floatValues\": [200.0]}, \"maxSampleRate\": 2.5}]}";
        List<File> customFileList = createFilenameList(jsonString);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        HalPropConfig[] allPropConfig = fakeVehicleStub.getAllPropConfigs();
        HalPropConfig propConfig = getPropConfigByPropId(allPropConfig,
                VehicleProperty.TIRE_PRESSURE);

        expect.that(propConfig.getPropId()).isEqualTo(VehicleProperty.TIRE_PRESSURE);
        expect.that(propConfig.getMaxSampleRate()).isEqualTo(2.5f);
    }

    @Test
    public void testGetAllPropConfigsWithCustomConfigHasNonExistingPropId() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": 123,"
                + "\"defaultValue\": {\"floatValues\": [200.0]}, "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"maxSampleRate\": 5.0}]}";
        List<File> customFileList = createFilenameList(jsonString);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        HalPropConfig[] allPropConfig = fakeVehicleStub.getAllPropConfigs();
        HalPropConfig propConfig = getPropConfigByPropId(allPropConfig, 123);

        expect.that(propConfig.getPropId()).isEqualTo(123);
        expect.that(propConfig.getMaxSampleRate()).isEqualTo(5.0f);
        expect.that(allPropConfig.length).isEqualTo(new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>()).getAllPropConfigs().length + 1);
    }

    @Test
    public void testGetAllPropConfigsWithSpecialProp() throws Exception {
        // Access permission of VHAL_HEARTBEAT has been overridden by the mocked return result of
        // AidlVehicleStub.getAllPropConfigs().
        when(mMockRealVehicleStub.getAllPropConfigs()).thenReturn(new HalPropConfig[]{
                new AidlHalPropConfig(createConfig(VehicleProperty.VHAL_HEARTBEAT,
                        /* sampleRate= */ 0, VehiclePropertyAccess.READ_WRITE, /* areaId= */ 0))});
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        HalPropConfig propConfig = getPropConfigByPropId(fakeVehicleStub.getAllPropConfigs(),
                VehicleProperty.VHAL_HEARTBEAT);

        verify(mMockRealVehicleStub, atLeastOnce()).getAllPropConfigs();
        expect.that(propConfig.getAccess()).isEqualTo(VehiclePropertyAccess.READ_WRITE);
    }

    @Test
    public void testParseConfigFilesThrowException() throws Exception {
        when(mParser.parseJsonConfig(any(InputStream.class))).thenThrow(
                new IllegalArgumentException("This file does not contain a valid JSONObject."));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new FakeVehicleStub(mMockRealVehicleStub, mParser, new ArrayList<>()));

        expect.that(thrown).hasMessageThat().contains("This file does not contain a valid "
                + "JSONObject.");
    }

    @Test
    public void testNoCustomFile() throws Exception {
        SparseArray<ConfigDeclaration> defaultParseResult = createParseResult(
                /* propId= */ 123, /* maxSampleRate= */ 10.0f,
                /* access= */ VehiclePropertyAccess.NONE, /* areaId= */ 0);
        when(mParser.parseJsonConfig(any(InputStream.class))).thenReturn(defaultParseResult);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub, mParser,
                new ArrayList<>());
        HalPropConfig[] allPropConfig = fakeVehicleStub.getAllPropConfigs();

        expect.that(allPropConfig.length).isEqualTo(defaultParseResult.size());
    }

    @Test
    public void testCustomFileParseFailure() throws Exception {
        // Parse this custom config file will throw an IllegalArgumentException with message
        // "properties field value is not a valid JSONArray."
        String jsonString = "{\"property\": 123,"
                + "\"defaultValue\": {\"floatValues\": [200.0]}, "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"maxSampleRate\": 5.0}";
        List<File> customFileList = createFilenameList(jsonString);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        expect.that(fakeVehicleStub.isFakeModeEnabled()).isEqualTo(true);
    }

    @Test
    public void testCustomFilesOneParseSuccessOneParseFailure() throws Exception {
        String validJsonString = "{\"properties\":["
                + "{\"property\": \"VehicleProperty::INFO_FUEL_TYPE\","
                + "\"defaultValue\": {\"floatValues\": [200.0]}, "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"maxSampleRate\": 5.0}]}";
        List<File> customFileList = createFilenameList(validJsonString);
        // Parse this custom config file will throw an IllegalArgumentException with message
        // "properties field value is not a valid JSONArray."
        String invalidJsonString = "{\"property\": 123,"
                + "\"defaultValue\": {\"floatValues\": [200.0]}, "
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::STATIC\","
                + "\"maxSampleRate\": 5.0}";
        customFileList.addAll(createFilenameList(invalidJsonString));
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_TYPE, /* areaId= */ 0);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        expect.that(fakeVehicleStub.isFakeModeEnabled()).isEqualTo(true);
        expect.that(fakeVehicleStub.get(requestPropValue).getPropId())
                .isEqualTo(VehicleProperty.INFO_FUEL_TYPE);
        expect.that(fakeVehicleStub.get(requestPropValue).getFloatValue(0)).isEqualTo(200);
    }

    @Test
    public void testGetMethodPropIdNotSupported() throws Exception {
        // Mock config files parsing results to be empty.
        when(mParser.parseJsonConfig(any(InputStream.class))).thenReturn(new SparseArray<>());
        when(mParser.parseJsonConfig(any(File.class))).thenReturn(new SparseArray<>());
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_TYPE, /* areaId= */ 0);
        // Create a FakeVehicleStub instance.
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub, mParser,
                new ArrayList<>());

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));


        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The propId: "
                + VehicleProperty.INFO_FUEL_TYPE + " is not supported.");
    }

    @Test
    public void testGetMethodNoReadPermission() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::ANDROID_EPOCH_TIME\","
                + "\"access\": \"VehiclePropertyAccess::WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.ANDROID_EPOCH_TIME, /* areaId= */ 0);
        // Create a FakeVehicleStub instance.
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.ACCESS_DENIED);
        expect.that(thrown).hasMessageThat().contains("doesn't have read permission");
    }

    @Test
    public void testGetMethodPropIdIsGlobalHasNoDefaultValueAreaIdIgnored() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::INFO_FUEL_CAPACITY\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_CAPACITY, /* areaId= */ 123);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        // For global properties, if default value is not defined, will throw
        // ServiceSpecificException with StatusCode.NOT_AVAILABLE.
        expect.that(thrown.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
        expect.that(thrown).hasMessageThat().contains("propId: "
                + VehicleProperty.INFO_FUEL_CAPACITY + " has no property value.");
    }

    @Test
    public void testGetMethodGlobalPropWithAreaIdNotZero() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]},"
                + "\"areas\": [{\"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 100}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 1);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        // For global properties, if the supported area config array doesn't include 0,
        // a ServiceSpecificException with StatusCode.INVALID_ARG.
        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The areaId: 0 is not supported.");
    }

    @Test
    public void testGetMethodPropIdIsGlobalHasDefaultValueAreaIdIgnored() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::INFO_FUEL_TYPE\","
                + "\"defaultValue\": {\"int32Values\": [\"FuelType::FUEL_TYPE_UNLEADED\"]}}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_TYPE, /* areaId= */ 123);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        // For global properties, if default value exists, will return the default value.
        expect.that(propValue.getAreaId()).isEqualTo(0);
        expect.that(propValue.getInt32ValuesSize()).isEqualTo(1);
        expect.that(propValue.getInt32Value(0)).isEqualTo(FuelType.FUEL_TYPE_UNLEADED);
    }

    @Test
    public void testGetMethodPropIdHasAreasHasNoDefaultValueHasNoAreaValue() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::WINDOW_POS\","
                + "\"areas\": [{\"areaId\": \"Constants::WINDOW_1_LEFT\", \"minInt32Value\": 0,"
                + "\"maxInt32Value\": 10}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.WINDOW_POS, /* areaId= */ WINDOW_1_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        // For properties have areas, if neither default prop value nor area value exists, then
        // throw a ServiceSpecificException of StatusCode.NOT_AVAILABLE.
        expect.that(thrown.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
        expect.that(thrown).hasMessageThat().contains("propId: "
                + VehicleProperty.WINDOW_POS + ", areaId: " + WINDOW_1_LEFT + " has no property "
                + "value");
    }

    @Test
    public void testGetMethodPropIdHasAreasHasDefaultValueHasNoAreaValue() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::WINDOW_POS\","
                + "\"defaultValue\": {\"int32Values\": [0]},\"areas\": [{\"areaId\":"
                + "\"Constants::WINDOW_1_LEFT\", \"minInt32Value\": 0, \"maxInt32Value\": 10}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.WINDOW_POS, /* areaId= */ WINDOW_1_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        // For properties have areas, expect that default prop value will override area value when
        // default prop value is set but the area value is not defined.
        expect.that(propValue.getAreaId()).isEqualTo(WINDOW_1_LEFT);
        expect.that(propValue.getInt32ValuesSize()).isEqualTo(1);
        expect.that(propValue.getInt32Value(0)).isEqualTo(0);
    }

    @Test
    public void testGetMethodPropIdHasAreasHasAreaValue() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::DOOR_LOCK\","
                + "\"areas\": [{\"defaultValue\": {\"int32Values\": [1]}, \"areaId\":"
                + "\"Constants::DOOR_1_LEFT\"}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(VehicleProperty.DOOR_LOCK, DOOR_1_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        // For properties have areas, get method returns the area value if it exists.
        expect.that(propValue.getPropId()).isEqualTo(VehicleProperty.DOOR_LOCK);
        expect.that(propValue.getInt32Value(0)).isEqualTo(1);
    }

    @Test
    public void testGetMethodPropIdHasAreasAreaIdNotSupport() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::SEAT_BELT_BUCKLED\","
                + "\"defaultValue\": {\"int32Values\": [0]},"
                + "\"areas\": [{\"areaId\": \"Constants::SEAT_1_LEFT\"}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.SEAT_BELT_BUCKLED, /* areaId= */ 0);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("areaId: 0 is not supported");
    }

    @Test
    public void testGetMethodForHvacProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_ON
                + ", {\"property\": \"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"defaultValue\": {\"int32Values\": [3]},"
                + "\"areas\": [{"
                + "\"areaId\": \"Constants::HVAC_ALL\","
                + "\"minInt32Value\": 1,"
                + "\"maxInt32Value\": 7}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ HVAC_ALL);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        expect.that(propValue.getPropId()).isEqualTo(VehicleProperty.HVAC_FAN_SPEED);
        expect.that(propValue.getInt32Value(0)).isEqualTo(3);
    }

    @Test
    public void testGetMethodForHvacPropNotAvailable() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_OFF + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ SEAT_1_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
        expect.that(thrown).hasMessageThat().contains("HVAC_POWER_ON is off. PropId: 356517120 "
                + "is not available.");
    }

    @Test
    public void testGetMethodForHvacPropMatchedBitOrAreaId() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_ON
                + ", {\"property\": \"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"defaultValue\": {\"int32Values\": [3]},"
                + "\"areas\": [{"
                + "\"areaId\": \"Constants::HVAC_LEFT\","
                + "\"minInt32Value\": 1,"
                + "\"maxInt32Value\": 7}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ HVAC_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        expect.that(propValue.getPropId()).isEqualTo(VehicleProperty.HVAC_FAN_SPEED);
        expect.that(propValue.getInt32Value(0)).isEqualTo(3);
    }

    @Test
    public void testGetMethodForHvacPropNoMatchedAreaId() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::HVAC_POWER_ON\","
                + "\"defaultValue\": {\"int32Values\": [1]},"
                + "\"areas\": [{\"areaId\": \"Constants::HVAC_RIGHT\"}],"
                + "\"configArray\": [\"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"VehicleProperty::HVAC_FAN_DIRECTION\"]}"
                + ", {\"property\": \"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"defaultValue\": {\"int32Values\": [3]},"
                + "\"areas\": [{"
                + "\"areaId\": \"Constants::HVAC_LEFT\","
                + "\"minInt32Value\": 1,"
                + "\"maxInt32Value\": 7}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ HVAC_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("This areaId: " + HVAC_LEFT + " doesn't match"
                + " any supported areaIds in HVAC_POWER_ON");
    }

    @Test
    public void testGetMethodForHvacPropWithMoreDefaultValues() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::HVAC_POWER_ON\","
                + "\"defaultValue\": {\"int32Values\": [1, 0]},"
                + "\"areas\": [{\"areaId\": \"Constants::HVAC_ALL\"}],"
                + "\"configArray\": [\"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"VehicleProperty::HVAC_FAN_DIRECTION\"]}"
                + ", {\"property\": \"VehicleProperty::HVAC_FAN_SPEED\","
                + "\"defaultValue\": {\"int32Values\": [3]},"
                + "\"areas\": [{"
                + "\"areaId\": \"Constants::HVAC_ALL\","
                + "\"minInt32Value\": 1,"
                + "\"maxInt32Value\": 7}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ HVAC_ALL);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        HalPropValue propValue = fakeVehicleStub.get(requestPropValue);

        expect.that(propValue.getInt32Value(0)).isEqualTo(3);
    }

    @Test
    public void testGetAsyncMethodForSingleValue() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": \"VehicleProperty::INFO_FUEL_TYPE\","
                + "\"defaultValue\": {\"int32Values\": [\"FuelType::FUEL_TYPE_UNLEADED\"]}}]}";
        List<File> customFileList = createFilenameList(jsonString);
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_TYPE, /* areaId= */ 123);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        AsyncGetSetRequest getRequest = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);

        fakeVehicleStub.getAsync(List.of(getRequest), mCallback);

        List<GetVehicleStubAsyncResult> getAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult asyncResult = getAsyncResult.get(0);
        HalPropValue propValue = asyncResult.getHalPropValue();
        expect.that(asyncResult.getServiceRequestId()).isEqualTo(0);
        // For global properties, if default value exists, will return the default value.
        expect.that(propValue.getAreaId()).isEqualTo(0);
        expect.that(propValue.getInt32ValuesSize()).isEqualTo(1);
        expect.that(propValue.getInt32Value(0)).isEqualTo(FuelType.FUEL_TYPE_UNLEADED);
    }

    @Test
    public void testGetAsyncMethodForSingleValueNoReadPermissionError() throws Exception {
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::ANDROID_EPOCH_TIME\","
                + "\"access\": \"VehiclePropertyAccess::WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.ANDROID_EPOCH_TIME, /* areaId= */ 0);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        AsyncGetSetRequest getRequest = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);

        fakeVehicleStub.getAsync(List.of(getRequest), mCallback);

        List<GetVehicleStubAsyncResult> getAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult asyncResult = getAsyncResult.get(0);
        HalPropValue propValue = asyncResult.getHalPropValue();
        expect.that(propValue).isNull();
        expect.that(asyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(asyncResult.getErrorCode())
            .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetAsyncForPropNotAvailable() throws Exception {
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_OFF + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ SEAT_1_LEFT);
        AsyncGetSetRequest getRequest = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        fakeVehicleStub.getAsync(List.of(getRequest), mCallback);

        List<GetVehicleStubAsyncResult> getAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult asyncResult = getAsyncResult.get(0);
        expect.that(asyncResult.getHalPropValue()).isNull();
        expect.that(asyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(asyncResult.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncForSpecialPropInternalError() throws Exception {
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(VehicleProperty.VHAL_HEARTBEAT, 0);
        AsyncGetSetRequest getRequest = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());
        when(mMockRealVehicleStub.get(requestPropValue)).thenThrow(RemoteException.class);

        fakeVehicleStub.getAsync(List.of(getRequest), mCallback);

        List<GetVehicleStubAsyncResult> getAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult asyncResult = getAsyncResult.get(0);
        expect.that(asyncResult.getHalPropValue()).isNull();
        expect.that(asyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(asyncResult.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetAsyncForMultipleGetRequestsSuccessAndError() throws Exception {
        String jsonString = "{\"properties\": "
                + "[{\"property\": \"VehicleProperty::INFO_FUEL_TYPE\","
                + "\"defaultValue\": {\"int32Values\": [\"FuelType::FUEL_TYPE_UNLEADED\"]}}, "
                + "{\"property\": \"VehicleProperty::ANDROID_EPOCH_TIME\","
                + "\"access\": \"VehiclePropertyAccess::WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        HalPropValue requestPropValue1 = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.ANDROID_EPOCH_TIME, /* areaId= */ 0);
        HalPropValue requestPropValue2 = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.INFO_FUEL_TYPE, /* areaId= */ 123);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        AsyncGetSetRequest getRequest1 = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue1, DEFAULT_TIMEOUT);
        AsyncGetSetRequest getRequest2 = new AsyncGetSetRequest(/* serviceRequestId= */ 1,
                requestPropValue2, DEFAULT_TIMEOUT);

        fakeVehicleStub.getAsync(List.of(getRequest1, getRequest2), mCallback);

        List<GetVehicleStubAsyncResult> getAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult asyncResult1 = getAsyncResult.get(0);
        HalPropValue propValue1 = asyncResult1.getHalPropValue();
        expect.that(asyncResult1.getServiceRequestId()).isEqualTo(0);
        expect.that(propValue1).isNull();
        expect.that(asyncResult1.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        GetVehicleStubAsyncResult asyncResult2 = getAsyncResult.get(1);
        HalPropValue propValue2 = asyncResult2.getHalPropValue();
        expect.that(asyncResult2.getServiceRequestId()).isEqualTo(1);
        expect.that(propValue2.getAreaId()).isEqualTo(0);
        expect.that(propValue2.getInt32ValuesSize()).isEqualTo(1);
        expect.that(propValue2.getInt32Value(0)).isEqualTo(FuelType.FUEL_TYPE_UNLEADED);
    }

    @Test
    public void testSetMethodPropConfigNotExist() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::SEAT_BELT_BUCKLED\","
                + "\"defaultValue\": {\"int32Values\": [0]},"
                + "\"areas\": [{\"areaId\": \"Constants::SEAT_1_LEFT\"}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAdil= */ true)
                .build(/* propId= */ 123456, /* areaId= */ 0);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.set(requestPropValue));

        expect.that(thrown).hasMessageThat().contains("The propId: 123456 is not supported.");
    }

    @Test
    public void testSetMethodPropDefaultValueExist() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::INFO_FUEL_CAPACITY\","
                + "\"defaultValue\": {\"floatValues\": [100.0]},"
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.floatValues = new float[]{10};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.INFO_FUEL_CAPACITY, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        HalPropValue oldPropValue = fakeVehicleStub.get(requestPropValue);
        fakeVehicleStub.set(requestPropValue);
        HalPropValue updatedPropValue = fakeVehicleStub.get(requestPropValue);

        expect.that(updatedPropValue.getFloatValue(0)).isEqualTo(10.0f);
        expect.that(updatedPropValue.getFloatValue(0)).isNotEqualTo(oldPropValue.getFloatValue(0));
        expect.that(updatedPropValue.getTimestamp()).isNotEqualTo(oldPropValue.getTimestamp());
    }

    @Test
    public void testSetMethodPropDefaultValueNotExist() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::INFO_FUEL_CAPACITY\","
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{32};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.INFO_FUEL_CAPACITY, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        expect.that(thrown).hasMessageThat().contains("has no property value");

        fakeVehicleStub.set(requestPropValue);
        HalPropValue updatedPropValue = fakeVehicleStub.get(requestPropValue);

        expect.that(updatedPropValue.getInt32Value(0)).isEqualTo(32);
    }

    @Test
    public void testSetMethodValueOutOfRange() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::SEAT_BELT_HEIGHT_POS\","
                + "\"defaultValue\": {\"int32Values\": [10]},"
                + "\"areas\": [{\"areaId\": \"Constants::SEAT_1_LEFT\","
                + "\"minInt32Value\": 0, \"maxInt32Value\": 10}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{15};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.SEAT_BELT_HEIGHT_POS, /* areaId= */ SEAT_1_LEFT,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.set(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The set value is outside the range");
    }

    @Test
    public void testSetMethodGlobalPropWithAreaIdSetValueOutOfRange() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]},"
                + "\"areas\": [{\"areaId\": 0, \"minInt32Value\": 0, \"maxInt32Value\": 100}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{120};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.set(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The set value is outside the range");
    }

    @Test
    public void testSetMethodGlobalPropWithNoAreaIdSetValueOutOfRange() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]}}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{120};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        fakeVehicleStub.set(requestPropValue);
        HalPropValue updatedPropValue = fakeVehicleStub.get(requestPropValue);

        expect.that(updatedPropValue.getInt32Value(0)).isEqualTo(120);
    }

    @Test
    public void testSetMethodGlobalPropWithAreaIdNotZero() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]},"
                + "\"areas\": [{\"areaId\": 1, \"minInt32Value\": 0, \"maxInt32Value\": 100}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{120};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 1,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.get(requestPropValue));

        // For global properties, if the supported area config array doesn't include 0,
        // a ServiceSpecificException with StatusCode.INVALID_ARG.
        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The areaId: 0 is not supported.");
    }

    @Test
    public void testSetMethodAreaConfigHasNoLimit() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::SEAT_BELT_BUCKLED\","
                + "\"defaultValue\": {\"int32Values\": [0]},"
                + "\"areas\": [{\"areaId\": \"Constants::SEAT_1_LEFT\"}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{32};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.SEAT_BELT_BUCKLED, /* areaId= */ SEAT_1_LEFT,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);

        HalPropValue oldPropValue = fakeVehicleStub.get(requestPropValue);
        fakeVehicleStub.set(requestPropValue);
        HalPropValue updatedPropValue = fakeVehicleStub.get(requestPropValue);

        expect.that(oldPropValue.getInt32Value(0)).isEqualTo(0);
        expect.that(updatedPropValue.getInt32Value(0)).isEqualTo(32);
    }

    @Test
    public void testSetMethodForHvacProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_ON
                + "," + PROPERTY_CONFIG_STRING_HVAC_FAN_SPEED + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{5};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ HVAC_ALL,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        HalPropValue oldPropValue = fakeVehicleStub.get(requestPropValue);
        fakeVehicleStub.set(requestPropValue);
        HalPropValue updatedPropValue = fakeVehicleStub.get(requestPropValue);

        expect.that(oldPropValue.getInt32Value(0)).isEqualTo(3);
        expect.that(updatedPropValue.getInt32Value(0)).isEqualTo(5);
    }

    @Test
    public void testSetMethodForHvacPropNotAvailable() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_HVAC_POWER_OFF + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(/* propId= */ VehicleProperty.HVAC_FAN_SPEED, /* areaId= */ SEAT_1_LEFT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> fakeVehicleStub.set(requestPropValue));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
        expect.that(thrown).hasMessageThat().contains("HVAC_POWER_ON is off. PropId: 356517120 "
                + "is not available.");
    }

    @Test
    public void testSetAsyncForSingleProp() throws Exception {
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::INFO_FUEL_CAPACITY\","
                + "\"defaultValue\": {\"floatValues\": [100.0]},"
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.floatValues = new float[]{10};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.INFO_FUEL_CAPACITY, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        AsyncGetSetRequest request = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        fakeVehicleStub.getAsync(List.of(request), mCallback);
        List<GetVehicleStubAsyncResult> getOldAsyncResult = captureOnGetAsyncResults();
        HalPropValue oldPropValue = getOldAsyncResult.get(0).getHalPropValue();
        reset(mCallback);

        fakeVehicleStub.setAsync(List.of(request), mCallback);
        List<SetVehicleStubAsyncResult> setAsyncResult = captureOnSetAsyncResults();

        reset(mCallback);
        fakeVehicleStub.getAsync(List.of(request), mCallback);
        List<GetVehicleStubAsyncResult> getNewAsyncResult = captureOnGetAsyncResults();
        GetVehicleStubAsyncResult newAsyncGetResult = getNewAsyncResult.get(0);
        SetVehicleStubAsyncResult setVehicleStubAsyncResult = setAsyncResult.get(0);
        HalPropValue updatedPropValue = newAsyncGetResult.getHalPropValue();
        expect.that(setVehicleStubAsyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(setVehicleStubAsyncResult.getServiceRequestId())
                .isEqualTo(newAsyncGetResult.getServiceRequestId());
        expect.that(setVehicleStubAsyncResult.getErrorCode())
                .isEqualTo(CarPropertyErrorCodes.STATUS_OK);
        expect.that(updatedPropValue.getFloatValue(0)).isEqualTo(10.0f);
        expect.that(updatedPropValue.getFloatValue(0)).isNotEqualTo(oldPropValue.getFloatValue(0));
        expect.that(updatedPropValue.getTimestamp()).isNotEqualTo(oldPropValue.getTimestamp());
    }

    @Test
    public void testSetAsyncGlobalPropWithAreaIdSetValueOutOfRange() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]},"
                + "\"areas\": [{\"areaId\": 0, \"minInt32Value\": 0, \"maxInt32Value\": 100}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{105};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        AsyncGetSetRequest request = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        fakeVehicleStub.setAsync(List.of(request), mCallback);

        List<SetVehicleStubAsyncResult> setAsyncResult = captureOnSetAsyncResults();
        SetVehicleStubAsyncResult setVehicleStubAsyncResult = setAsyncResult.get(0);
        expect.that(setVehicleStubAsyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(setVehicleStubAsyncResult.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testSetAsyncForSpecialPropRemoteException() throws Exception {
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.floatValues = new float[]{10};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.SWITCH_USER, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        doThrow(new RemoteException()).when(mMockRealVehicleStub).set(requestPropValue);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());
        AsyncGetSetRequest request = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue, DEFAULT_TIMEOUT);

        fakeVehicleStub.setAsync(List.of(request), mCallback);

        List<SetVehicleStubAsyncResult> setAsyncResult = captureOnSetAsyncResults();
        SetVehicleStubAsyncResult setVehicleStubAsyncResult = setAsyncResult.get(0);
        expect.that(setVehicleStubAsyncResult.getServiceRequestId()).isEqualTo(0);
        expect.that(setVehicleStubAsyncResult.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        verify(mMockRealVehicleStub).set(requestPropValue);
    }

    @Test
    public void testSetAsyncOnMultipleRequests() throws Exception {
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::DISPLAY_BRIGHTNESS\","
                + "\"defaultValue\": {\"int32Values\": [100]},"
                + "\"areas\": [{\"areaId\": 0, \"minInt32Value\": 0, \"maxInt32Value\": 100}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        RawPropValues rawPropValues1 = new RawPropValues();
        rawPropValues1.floatValues = new float[]{10};
        HalPropValue requestPropValue1 = buildHalPropValue(
                /* propId= */ VehicleProperty.SWITCH_USER, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues1);
        doThrow(new RemoteException()).when(mMockRealVehicleStub).set(requestPropValue1);
        AsyncGetSetRequest request1 = new AsyncGetSetRequest(/* serviceRequestId= */ 0,
                requestPropValue1, DEFAULT_TIMEOUT);
        RawPropValues rawPropValues2 = new RawPropValues();
        rawPropValues2.int32Values = new int[]{105};
        HalPropValue requestPropValue2 = buildHalPropValue(
                /* propId= */ VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues2);
        AsyncGetSetRequest request2 = new AsyncGetSetRequest(/* serviceRequestId= */ 1,
                requestPropValue2, DEFAULT_TIMEOUT);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        fakeVehicleStub.setAsync(List.of(request1, request2), mCallback);
        List<SetVehicleStubAsyncResult> setAsyncResult = captureOnSetAsyncResults();
        SetVehicleStubAsyncResult setVehicleStubAsyncResult1 = setAsyncResult.get(0);
        expect.that(setVehicleStubAsyncResult1.getServiceRequestId()).isEqualTo(0);
        expect.that(setVehicleStubAsyncResult1.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        verify(mMockRealVehicleStub).set(requestPropValue1);
        SetVehicleStubAsyncResult setVehicleStubAsyncResult2 = setAsyncResult.get(1);
        expect.that(setVehicleStubAsyncResult2.getServiceRequestId()).isEqualTo(1);
        expect.that(setVehicleStubAsyncResult2.getErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testSubscribeOnChangePropOneClientSubscribeOnePropManyTime() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_ON_CHANGE + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options array.
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.ENGINE_OIL_LEVEL;
        option.sampleRate = 0.5f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        // Create set value
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{VehicleOilLevel.LOW};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.ENGINE_OIL_LEVEL, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options);
            client.subscribe(options);
            client.subscribe(options);
            fakeVehicleStub.set(requestPropValue);

            verify(callback, times(1)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.ENGINE_OIL_LEVEL);
        }
    }

    @Test
    public void testSubscribeOnChangePropOneClientSubscribeTwoProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_ON_CHANGE + ","
                + "{\"property\": \"VehicleProperty::NIGHT_MODE\","
                + "\"defaultValue\": {\"int32Values\": [0]},"
                + "\"access\": \"VehiclePropertyAccess::READ_WRITE\","
                + "\"changeMode\": \"VehiclePropertyChangeMode::ON_CHANGE\"}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option1 = new SubscribeOptions();
        option1.propId = VehicleProperty.ENGINE_OIL_LEVEL;
        option1.sampleRate = 0.5f;
        SubscribeOptions option2 = new SubscribeOptions();
        option2.propId = VehicleProperty.NIGHT_MODE;
        option2.sampleRate = 0.5f;
        SubscribeOptions[] options = new SubscribeOptions[]{option1, option2};
        // Create set value
        RawPropValues rawPropValues1 = new RawPropValues();
        rawPropValues1.int32Values = new int[]{VehicleOilLevel.LOW};
        HalPropValue requestPropValue1 = buildHalPropValue(
                /* propId= */ VehicleProperty.ENGINE_OIL_LEVEL, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues1);
        RawPropValues rawPropValues2 = new RawPropValues();
        rawPropValues2.int32Values = new int[]{0};
        HalPropValue requestPropValue2 = buildHalPropValue(
                /* propId= */ VehicleProperty.NIGHT_MODE, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues2);
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options);
            fakeVehicleStub.set(requestPropValue1);
            fakeVehicleStub.set(requestPropValue2);

            verify(callback, times(2)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.ENGINE_OIL_LEVEL);
            client.unsubscribe(VehicleProperty.NIGHT_MODE);
        }
    }

    @Test
    public void testSubscribeAreaPropertyWithEmptyOptionsAreaIds() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\": "
                + "\"VehicleProperty::SEAT_HEADREST_ANGLE_POS\","
                + "\"defaultValue\": {\"int32Values\": [0]},"
                + "\"areas\":["
                + "{\"areaId\": \"Constants::SEAT_1_LEFT\", \"minInt32Value\": -10,"
                + "\"maxInt32Value\": 10},"
                + "{\"areaId\": \"Constants::SEAT_1_RIGHT\",\"minInt32Value\": -10,"
                + "\"maxInt32Value\": 10}]}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options array.
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.SEAT_HEADREST_ANGLE_POS;
        // option.areaId array is null.
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        // Create set value
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{10};
        HalPropValue requestPropValue1 = buildHalPropValue(
                /* propId= */ VehicleProperty.SEAT_HEADREST_ANGLE_POS, SEAT_1_LEFT,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        HalPropValue requestPropValue2 = buildHalPropValue(
                /* propId= */ VehicleProperty.SEAT_HEADREST_ANGLE_POS, SEAT_1_RIGHT,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        client.subscribe(options);
        fakeVehicleStub.set(requestPropValue1);
        fakeVehicleStub.set(requestPropValue2);

        verify(callback, times(2)).onPropertyEvent(any(ArrayList.class));
    }

    @Test
    public void testSubscribeOnChangePropTwoClientSubscribeSameProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_ON_CHANGE + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.ENGINE_OIL_LEVEL;
        option.sampleRate = 2f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        // Create set value
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{VehicleOilLevel.LOW};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.ENGINE_OIL_LEVEL, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        VehicleHalCallback callback1 = mock(VehicleHalCallback.class);
        VehicleHalCallback callback2 = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client1 = fakeVehicleStub.newSubscriptionClient(callback1);
        VehicleStub.SubscriptionClient client2 = fakeVehicleStub.newSubscriptionClient(callback2);
        try {
            client1.subscribe(options);
            client2.subscribe(options);
            fakeVehicleStub.set(requestPropValue);

            verify(callback1, times(1)).onPropertyEvent(any(ArrayList.class));
            verify(callback2, times(1)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client1.unsubscribe(VehicleProperty.ENGINE_OIL_LEVEL);
            client2.unsubscribe(VehicleProperty.ENGINE_OIL_LEVEL);
        }
    }

    @Test
    public void testSubscribeContinuousProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.FUEL_LEVEL;
        option.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options);

            verify(callback, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        }
    }

    @Test
    public void testSubscribeContinuousPropDifferentRate() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option1 = new SubscribeOptions();
        option1.propId = VehicleProperty.FUEL_LEVEL;
        option1.sampleRate = 100f;
        SubscribeOptions[] options1 = new SubscribeOptions[]{option1};
        SubscribeOptions option2 = new SubscribeOptions();
        option2.propId = VehicleProperty.FUEL_LEVEL;
        option2.sampleRate = 50f;
        SubscribeOptions[] options2 = new SubscribeOptions[]{option2};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options1);

            verify(callback, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
            clearInvocations(callback);

            client.subscribe(options2);

            verify(callback, timeout(200).atLeast(5)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        }
    }

    @Test
    public void testSubscribeContinuousPropRateTooLarge() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.FUEL_LEVEL;
        option.sampleRate = 200f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};

        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options);

            verify(callback, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        }
    }

    @Test
    public void testSubscribeContinuousPropRateTooSmall() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.FUEL_LEVEL;
        option.sampleRate = -50f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        try {
            client.subscribe(options);

            verify(callback, timeout(100).atLeast(1)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        }
    }

    @Test
    public void testSubscribeNonSupportPropID() throws Exception {
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = 123;
        option.sampleRate = 0f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> client.subscribe(options));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The propId: 123 is not supported.");
    }

    @Test
    public void testSubscribeNonSupportAreaId() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [{\"property\":"
                + "\"VehicleProperty::SEAT_BELT_BUCKLED\","
                + "\"defaultValue\": {\"int32Values\": [0]}}]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.SEAT_BELT_BUCKLED;
        option.areaIds = new int[]{SEAT_1_LEFT};
        option.sampleRate = 1f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        ServiceSpecificException thrown;
        try {
            thrown = assertThrows(ServiceSpecificException.class, () -> client.subscribe(options));
        } finally {
            client.unsubscribe(VehicleProperty.SEAT_BELT_BUCKLED);
        }

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The areaId: " + SEAT_1_LEFT
                + " is not supported.");
    }

    @Test
    public void testSubscribeStaticPropThrowException() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_STATIC + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.INFO_FUEL_CAPACITY;
        option.sampleRate = 0f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> client.subscribe(options));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("Static property cannot be subscribed.");
    }

    @Test
    public void testUnsubscribePropIdNotSupport() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> client.unsubscribe(1234));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("The propId: 1234 is not supported");
    }

    @Test
    public void testUnsubscribeStaticProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_STATIC + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        ServiceSpecificException thrown = assertThrows(ServiceSpecificException.class,
                () -> client.unsubscribe(VehicleProperty.INFO_FUEL_TYPE));

        expect.that(thrown.errorCode).isEqualTo(StatusCode.INVALID_ARG);
        expect.that(thrown).hasMessageThat().contains("Static property cannot be unsubscribed.");
    }

    @Test
    public void testUnsubscribeOnChangeProp() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_ON_CHANGE + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options array.
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.ENGINE_OIL_LEVEL;
        option.sampleRate = 1f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        // Create set value
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.int32Values = new int[]{VehicleOilLevel.LOW};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.ENGINE_OIL_LEVEL, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);
        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        client.subscribe(options);
        fakeVehicleStub.set(requestPropValue);
        verify(callback, times(1)).onPropertyEvent(any(ArrayList.class));

        client.unsubscribe(VehicleProperty.ENGINE_OIL_LEVEL);
        clearInvocations(callback);
        fakeVehicleStub.set(requestPropValue);

        // Because in the FakeVehicleStub.set method, we copy the subscription client set from the
        // lock to a local variable and use it outside the lock to call callbacks. After
        // unsubscription, there is still slight chance the callback might be used once.
        verify(callback, atMost(1)).onPropertyEvent(any(ArrayList.class));
    }

    @Test
    public void testUnsubscribeContinuousPropOneClient() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.FUEL_LEVEL;
        option.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        client.subscribe(options);

        verify(callback, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));

        client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        clearInvocations(callback);

        verify(callback, after(200).atMost(1)).onPropertyEvent(any(ArrayList.class));
    }

    @Test
    public void testUnsubscribeContinuousPropTwoClient() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.FUEL_LEVEL;
        option.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback1 = mock(VehicleHalCallback.class);
        VehicleHalCallback callback2 = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client1 = fakeVehicleStub.newSubscriptionClient(callback1);
        VehicleStub.SubscriptionClient client2 = fakeVehicleStub.newSubscriptionClient(callback2);
        try {
            client1.subscribe(options);
            client2.subscribe(options);

            verify(callback1, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
            verify(callback2, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));

            client1.unsubscribe(VehicleProperty.FUEL_LEVEL);
            clearInvocations(callback1);
            clearInvocations(callback2);

            verify(callback1, after(200).atMost(1)).onPropertyEvent(any(ArrayList.class));
            verify(callback2, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
        } finally {
            client2.unsubscribe(VehicleProperty.FUEL_LEVEL);
        }
    }

    @Test
    public void testUnsubscribeContinuousPropOneClientTwoSubscription() throws Exception {
        // Create a custom config file.
        String jsonString = "{\"properties\": [" + PROPERTY_CONFIG_STRING_CONTINUOUS + ", "
                + PROPERTY_CONFIG_STRING_CONTINUOUS_2 + "]}";
        List<File> customFileList = createFilenameList(jsonString);
        // Create subscribe options
        SubscribeOptions option1 = new SubscribeOptions();
        option1.propId = VehicleProperty.FUEL_LEVEL;
        option1.sampleRate = 100f;
        SubscribeOptions option2 = new SubscribeOptions();
        option2.propId = VehicleProperty.EV_BATTERY_LEVEL;
        option2.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option1, option2};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), customFileList);

        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);
        client.subscribe(options);

        verify(callback, timeout(100).atLeast(10)).onPropertyEvent(any(ArrayList.class));

        client.unsubscribe(VehicleProperty.FUEL_LEVEL);
        clearInvocations(callback);

        verify(callback, timeout(100).atLeast(5)).onPropertyEvent(any(ArrayList.class));
    }

    @Test
    public void testGetValueForSpecialProp() throws Exception {
        // Create a request prop value.
        HalPropValue requestPropValue = new HalPropValueBuilder(/* isAidl= */ true)
                .build(VehicleProperty.VHAL_HEARTBEAT, 0);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        fakeVehicleStub.get(requestPropValue);

        verify(mMockRealVehicleStub).get(requestPropValue);
    }

    @Test
    public void testSetValueForSpecialProp() throws Exception {
        // Create a request prop value.
        RawPropValues rawPropValues = new RawPropValues();
        rawPropValues.floatValues = new float[]{10};
        HalPropValue requestPropValue = buildHalPropValue(
                /* propId= */ VehicleProperty.SWITCH_USER, /* areaId= */ 0,
                SystemClock.elapsedRealtimeNanos(), rawPropValues);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        fakeVehicleStub.set(requestPropValue);

        verify(mMockRealVehicleStub).set(requestPropValue);
    }

    @Test
    public void testSubscribeSpecialProp() throws Exception {
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.VHAL_HEARTBEAT;
        option.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());
        SubscriptionClient realClient = mock(SubscriptionClient.class);
        when(mMockRealVehicleStub.newSubscriptionClient(callback)).thenReturn(realClient);
        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);

        doNothing().when(realClient).subscribe(options);
        client.subscribe(options);

        verify(realClient).subscribe(options);
    }

    @Test
    public void testUnsubscribeSpecialProp() throws Exception {
        // Create subscribe options
        SubscribeOptions option = new SubscribeOptions();
        option.propId = VehicleProperty.VHAL_HEARTBEAT;
        option.sampleRate = 100f;
        SubscribeOptions[] options = new SubscribeOptions[]{option};
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        FakeVehicleStub fakeVehicleStub =  new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());
        SubscriptionClient realClient = mock(SubscriptionClient.class);
        when(mMockRealVehicleStub.newSubscriptionClient(callback)).thenReturn(realClient);
        VehicleStub.SubscriptionClient client = fakeVehicleStub.newSubscriptionClient(callback);

        doNothing().when(realClient).subscribe(options);
        client.subscribe(options);

        verify(realClient).subscribe(options);

        doNothing().when(realClient).unsubscribe(VehicleProperty.VHAL_HEARTBEAT);
        client.unsubscribe(VehicleProperty.VHAL_HEARTBEAT);

        verify(realClient).unsubscribe(VehicleProperty.VHAL_HEARTBEAT);
    }

    @Test
    public void testLinkToDeath() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        fakeVehicleStub.linkToDeath(recipient);

        verify(mMockRealVehicleStub).linkToDeath(recipient);
    }

    @Test
    public void testUnLinkToDeath() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        fakeVehicleStub.unlinkToDeath(recipient);

        verify(mMockRealVehicleStub).unlinkToDeath(recipient);
    }

    @Test
    public void testDump() throws Exception {
        FileDescriptor fd = mock(FileDescriptor.class);
        FakeVehicleStub fakeVehicleStub = new FakeVehicleStub(mMockRealVehicleStub,
                new FakeVhalConfigParser(), new ArrayList<>());

        fakeVehicleStub.dump(fd, new ArrayList<>());

        verify(mMockRealVehicleStub).dump(eq(fd), eq(new ArrayList<>()));
    }

    private HalPropValue buildHalPropValue(int propId, int areaId, long timestamp,
            RawPropValues rawPropValues) {
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = propId;
        propValue.areaId = areaId;
        propValue.timestamp = timestamp;
        propValue.value = rawPropValues;
        return new HalPropValueBuilder(/* isAidl= */ true).build(propValue);
    }

    private SparseArray<ConfigDeclaration> createParseResult(int propId, float maxSampleRate,
            int access, int areaId) {
        SparseArray<ConfigDeclaration> parseResult = new SparseArray<>();
        ConfigDeclaration propConfig = new ConfigDeclaration(
                createConfig(propId, maxSampleRate, access, areaId), null, new SparseArray<>());
        parseResult.put(propId, propConfig);
        return parseResult;
    }

    private HalPropConfig getPropConfigByPropId(HalPropConfig[] propConfigs, int propId) {
        for (int i = 0; i < propConfigs.length; i++) {
            if (propConfigs[i].getPropId() == propId) {
                return propConfigs[i];
            }
        }
        return null;
    }

    private VehiclePropConfig createConfig(int propId, float sampleRate, int access, int areaId) {
        VehiclePropConfig propConfigValue = new VehiclePropConfig();
        propConfigValue.prop = propId;
        propConfigValue.access = access;
        VehicleAreaConfig vehicleAreaConfig = new VehicleAreaConfig();
        vehicleAreaConfig.areaId = areaId;
        propConfigValue.areaConfigs = new VehicleAreaConfig[]{vehicleAreaConfig};
        propConfigValue.maxSampleRate = sampleRate;
        return propConfigValue;
    }

    private List<File> createFilenameList(String fileContent) throws Exception {
        List<File> customFileList = new ArrayList<>();
        File tempFile = File.createTempFile("custom_config_", ".json");
        tempFile.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(tempFile)) {
            os.write(fileContent.getBytes());
            customFileList.add(tempFile);
        }
        return customFileList;
    }

    private List<GetVehicleStubAsyncResult> captureOnGetAsyncResults() {
        verify(mCallback, timeout(DEFAULT_TIMEOUT)).onGetAsyncResults(
                mGetVehicleStubAsyncResultCaptor.capture());
        return mGetVehicleStubAsyncResultCaptor.getValue();
    }

    private List<SetVehicleStubAsyncResult> captureOnSetAsyncResults() {
        verify(mCallback, timeout(DEFAULT_TIMEOUT)).onSetAsyncResults(
                mSetVehicleStubAsyncResultCaptor.capture());
        return mSetVehicleStubAsyncResultCaptor.getValue();
    }
}
