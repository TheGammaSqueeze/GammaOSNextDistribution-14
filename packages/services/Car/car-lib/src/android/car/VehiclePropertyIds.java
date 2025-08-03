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

package android.car;

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.property.VehiclePropertyIdDebugUtils.isDefined;
import static com.android.car.internal.property.VehiclePropertyIdDebugUtils.toName;

import android.annotation.FlaggedApi;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AutomaticEmergencyBrakingState;
import android.car.hardware.property.BlindSpotWarningState;
import android.car.hardware.property.CruiseControlCommand;
import android.car.hardware.property.CruiseControlState;
import android.car.hardware.property.CruiseControlType;
import android.car.hardware.property.DriverDistractionState;
import android.car.hardware.property.DriverDistractionWarning;
import android.car.hardware.property.DriverDrowsinessAttentionState;
import android.car.hardware.property.DriverDrowsinessAttentionWarning;
import android.car.hardware.property.EmergencyLaneKeepAssistState;
import android.car.hardware.property.ErrorState;
import android.car.hardware.property.EvChargeState;
import android.car.hardware.property.EvRegenerativeBrakingState;
import android.car.hardware.property.EvStoppingMode;
import android.car.hardware.property.ForwardCollisionWarningState;
import android.car.hardware.property.HandsOnDetectionDriverState;
import android.car.hardware.property.HandsOnDetectionWarning;
import android.car.hardware.property.LaneCenteringAssistCommand;
import android.car.hardware.property.LaneCenteringAssistState;
import android.car.hardware.property.LaneDepartureWarningState;
import android.car.hardware.property.LaneKeepAssistState;
import android.car.hardware.property.LocationCharacterization;
import android.car.hardware.property.TrailerState;
import android.car.hardware.property.VehicleElectronicTollCollectionCardStatus;
import android.car.hardware.property.VehicleElectronicTollCollectionCardType;
import android.car.hardware.property.VehicleLightState;
import android.car.hardware.property.VehicleLightSwitch;
import android.car.hardware.property.VehicleOilLevel;
import android.car.hardware.property.VehicleTurnSignal;
import android.car.hardware.property.WindshieldWipersState;
import android.car.hardware.property.WindshieldWipersSwitch;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

/**
 * List of vehicle property IDs.
 *
 * <p> Property IDs are used with the {@link android.car.hardware.property.CarPropertyManager} APIs
 * (e.g. {@link android.car.hardware.property.CarPropertyManager#getProperty(int, int)} or {@link
 * android.car.hardware.property.CarPropertyManager#setProperty(Class, int, int, Object)}).
 */
public final class VehiclePropertyIds {

    /**
     * Undefined property.
     *
     * <p>This property should never be used/will never be supported.
     */
    public static final int INVALID = 0;
    /**
     * VIN of vehicle
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code String} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_IDENTIFICATION" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_IDENTIFICATION)
    public static final int INFO_VIN = 286261504;
    /**
     * Manufacturer of vehicle.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code String} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_MAKE = 286261505;
    /**
     * Model of vehicle.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code String} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_MODEL = 286261506;
    /**
     * Model year of vehicle in YYYY format based on Gregorian calendar.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_MODEL_YEAR = 289407235;
    /**
     * Fuel capacity of the vehicle in milliliters.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_FUEL_CAPACITY = 291504388;
    /**
     * List the {@link FuelType}s the vehicle may use.
     *
     * <p>{@link FuelType#ELECTRIC} will only be included if the vehicle is plug in rechargeable.
     * Note that for this reason, even though {@link FuelType#ELECTRIC} is not listed as a fuel
     * type, other EV properties such as {@link #INFO_EV_BATTERY_CAPACITY} can still be supported on
     * the vehicle.
     *
     * <p>For example:
     *  <p>FHEVs (Fully Hybrid Electric Vehicles) will not include {@link FuelType#ELECTRIC} in its
     *  {@code Integer[]} value. So {@code INFO_FUEL_TYPE} will be populated as such:
     *  { {@link FuelType#UNLEADED} }.
     *  <p>On the other hand, PHEVs (Partially Hybrid Electric Vehicles) are plug in rechargeable,
     *  and hence will include {@link FuelType#ELECTRIC} in {@code INFO_FUEL_TYPE}'s {@code
     *  Integer[]} value. So {@code INFO_FUEL_TYPE} will be populated as such:
     *  { {@link FuelType#UNLEADED}, {@link FuelType#ELECTRIC} }.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link FuelType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_FUEL_TYPE = 289472773;
    /**
     * Nominal battery capacity for EV or hybrid vehicle.
     *
     * <p>Returns the nominal battery capacity in {@link android.car.VehicleUnit#WATT_HOUR}, if EV
     * or hybrid. This is the battery capacity when the vehicle is new. This value might be
     * different from {@link #EV_CURRENT_BATTERY_CAPACITY} because {@link
     * #EV_CURRENT_BATTERY_CAPACITY} returns the real-time battery capacity taking into account
     * factors such as battery aging and temperature dependency.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_EV_BATTERY_CAPACITY = 291504390;
    /**
     * List of {@link android.car.hardware.property.EvChargingConnectorType}s this vehicle may use.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link EvConnectorType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_EV_CONNECTOR_TYPE = 289472775;
    /**
     * {@link PortLocationType} for the fuel door location.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link PortLocationType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_FUEL_DOOR_LOCATION = 289407240;
    /**
     * {@link PortLocationType} for the EV port location.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link PortLocationType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_EV_PORT_LOCATION = 289407241;
    /**
     * List {@link PortLocationType}s for Multiple EV port locations.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link PortLocationType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_MULTI_EV_PORT_LOCATIONS = 289472780;
    /**
     * Driver's seat location.
     *
     * <p>The only area ID listed in {@link android.car.hardware.CarPropertyConfig#getAreaIds} for
     * {@code INFO_DRIVER_SEAT} will be {@code 0}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleAreaSeat}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_DRIVER_SEAT = 356516106;
    /**
     * Vehicle's exterior dimensions in millimeters.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Exterior dimensions defined in the {@link CarPropertyValue#getValue()} {@code Integer[]}:
     * <ul>
     *  <li>Integer[0] = height
     *  <li>Integer[1] = length
     *  <li>Integer[2] = width
     *  <li>Integer[3] = width including mirrors
     *  <li>Integer[4] = wheel base
     *  <li>Integer[5] = track width front
     *  <li>Integer[6] = track width rear
     *  <li>Integer[7] = curb to curb turning diameter
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int INFO_EXTERIOR_DIMENSIONS = 289472779;
    /**
     * Current odometer value of the vehicle in kilometers.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_MILEAGE" to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_MILEAGE)
    public static final int PERF_ODOMETER = 291504644;
    /**
     * Speed of the vehicle in meters per second.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>When the vehicle is moving forward, {@code PERF_VEHICLE_SPEED} is positive and negative
     * when the vehicle is moving backward. Also, this value is independent of gear value ({@link
     * #CURRENT_GEAR} or {@link #GEAR_SELECTION}). For example, if {@link #GEAR_SELECTION} is
     * {@link VehicleGear#GEAR_NEUTRAL}, {@code PERF_VEHICLE_SPEED} is positive when the vehicle is
     * moving forward, negative when moving backward, and zero when not moving.
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_SPEED} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_SPEED)
    public static final int PERF_VEHICLE_SPEED = 291504647;
    /**
     * Speed of the vehicle in meters per second for displays.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Some cars display a slightly slower speed than the actual speed. This is
     * usually displayed on the speedometer.
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_SPEED} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_SPEED)
    public static final int PERF_VEHICLE_SPEED_DISPLAY = 291504648;
    /**
     * Front bicycle model steering angle for vehicle in degrees.
     *
     * <p>Left is negative.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_STEERING" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_READ_STEERING_STATE)
    public static final int PERF_STEERING_ANGLE = 291504649;
    /**
     * Rear bicycle model steering angle for vehicle in degrees.
     *
     * <p>Left is negative.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_STEERING" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_READ_STEERING_STATE)
    public static final int PERF_REAR_STEERING_ANGLE = 291504656;
    /**
     * Temperature of engine coolant in celsius.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_ENGINE_DETAILED" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
    public static final int ENGINE_COOLANT_TEMP = 291504897;
    /**
     * Engine oil level.
     *
     * <p>Returns the status of the oil level for the vehicle. See {@code VehicleOilLevel} for
     * possible values for {@code ENGINE_OIL_LEVEL}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_ENGINE_DETAILED" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleOilLevel}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_CAR_ENGINE_DETAILED))
    public static final int ENGINE_OIL_LEVEL = 289407747;
    /**
     * Temperature of engine oil in celsius.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_ENGINE_DETAILED" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
    public static final int ENGINE_OIL_TEMP = 291504900;
    /**
     * Engine rpm.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_ENGINE_DETAILED" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
    public static final int ENGINE_RPM = 291504901;
    /**
     * Represents feature for engine idle automatic stop.
     *
     * <p>If true, the vehicle may automatically shut off the engine when it is not needed and then
     * automatically restart it when needed.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CAR_ENGINE_DETAILED} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
    public static final int ENGINE_IDLE_AUTO_STOP_ENABLED = 287310624;
    /**
     * Impact detected.
     *
     * <p>Bit flag property to relay information on whether an impact has occurred on a particular
     * side of the vehicle as described through the {@link
     * android.car.hardware.property.ImpactSensorLocation} enum. As a bit flag property, this
     * property can be set to multiple ORed together values of the enum when necessary.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which bit flags
     * from {@link android.car.hardware.property.ImpactSensorLocation} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_IMPACT_SENSORS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.ImpactSensorLocation}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_IMPACT_SENSORS))
    public static final int IMPACT_DETECTED = 289407792;
    /**
     * Reports wheel ticks.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Long[]} property type
     * </ul>
     *
     * <p>The first element in the array is a reset count.  A reset indicates
     * previous tick counts are not comparable with this and future ones.  Some
     * sort of discontinuity in tick counting has occurred.
     *
     * <p>The next four elements represent ticks for individual wheels in the
     * following order: front left, front right, rear right, rear left.  All
     * tick counts are cumulative.  Tick counts increment when the vehicle
     * moves forward, and decrement when vehicles moves in reverse.  The ticks
     * should be reset to 0 when the vehicle is started by the user.
     *
     * <ul>
     *  <li>Long[0] = reset count
     *  <li>Long[1] = front left ticks
     *  <li>Long[2] = front right ticks
     *  <li>Long[3] = rear right ticks
     *  <li>Long[4] = rear left ticks
     * </ul>
     *
     * <p>configArray is used to indicate the micrometers-per-wheel-tick values and
     * which wheels are supported. Each micrometers-per-wheel-tick value is static (i.e. will not
     * update based on wheel's status) and a best approximation. For example, if a vehicle has
     * multiple rim/tire size options, the micrometers-per-wheel-tick values are set to those for
     * the typically expected rim/tire size. configArray is set as follows:
     *
     * <ul>
     *  <li>configArray[0], bits [0:3] = supported wheels. Uses {@link VehicleAreaWheel}. For
     *  example, if all wheels are supported, then configArray[0] = {@link
     *  VehicleAreaWheel#WHEEL_LEFT_FRONT} | {@link VehicleAreaWheel#WHEEL_RIGHT_FRONT} | {@link
     *  VehicleAreaWheel#WHEEL_LEFT_REAR} | {@link VehicleAreaWheel#WHEEL_RIGHT_REAR}
     *  <li>configArray[1] = micrometers per front left wheel tick
     *  <li>configArray[2] = micrometers per front right wheel tick
     *  <li>configArray[3] = micrometers per rear right wheel tick
     *  <li>configArray[4] = micrometers per rear left wheel tick
     * </ul>
     *
     * <p>NOTE:  If a wheel is not supported, its value is always 0.
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_SPEED} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_SPEED)
    public static final int WHEEL_TICK = 290521862;
    /**
     * Fuel remaining in the vehicle in milliliters.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY)
    public static final int FUEL_LEVEL = 291504903;
    /**
     * Fuel door open.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_ENERGY_PORTS} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_ENERGY_PORTS"to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY_PORTS" to
     *  write property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY_PORTS,
            Car.PERMISSION_CONTROL_ENERGY_PORTS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ENERGY_PORTS))
    public static final int FUEL_DOOR_OPEN = 287310600;
    /**
     * EV battery level.
     *
     * <p>Returns the current battery level in {@link android.car.VehicleUnit#WATT_HOUR}, if EV or
     * hybrid. This value will not exceed {@link #EV_CURRENT_BATTERY_CAPACITY}. To calculate the
     * battery percentage, use:
     * ({@link #EV_BATTERY_LEVEL}/{@link #EV_CURRENT_BATTERY_CAPACITY})*100.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY)
    public static final int EV_BATTERY_LEVEL = 291504905;
    /**
     * Current battery capacity for EV or hybrid vehicle.
     *
     * <p>Returns the actual value of battery capacity in {@link android.car.VehicleUnit#WATT_HOUR},
     * if EV or hybrid. This property captures the real-time battery capacity taking into account
     * factors such as battery aging and temperature dependency. Therefore, this value might be
     * different from {@link #INFO_EV_BATTERY_CAPACITY} because {@link #INFO_EV_BATTERY_CAPACITY}
     * returns the nominal battery capacity from when the vehicle was new.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY)
    public static final int EV_CURRENT_BATTERY_CAPACITY = 291504909;
    /**
     * EV charge port open.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_ENERGY_PORTS} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_ENERGY_PORTS"to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY_PORTS" to
     *  write property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY_PORTS,
            Car.PERMISSION_CONTROL_ENERGY_PORTS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ENERGY_PORTS))
    public static final int EV_CHARGE_PORT_OPEN = 287310602;
    /**
     * EV charge port connected.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_ENERGY_PORTS} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY_PORTS)
    public static final int EV_CHARGE_PORT_CONNECTED = 287310603;
    /**
     * EV instantaneous charge rate in milliwatts.
     *
     * <p>Positive rate indicates battery is being charged, and Negative rate indicates battery
     * being discharged.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY)
    public static final int EV_BATTERY_INSTANTANEOUS_CHARGE_RATE = 291504908;
    /**
     * Range remaining in meters.
     *
     * <p>Range remaining accounts for all energy sources in a vehicle.  For example, a hybrid car's
     * range will be the sum of the ranges based on fuel and battery.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} or Signature|Privileged permission
     *  "android.car.permission.ADJUST_RANGE_REMAINING" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.ADJUST_RANGE_REMAINING" to write
     *  property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY,
            Car.PERMISSION_ADJUST_RANGE_REMAINING}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_ADJUST_RANGE_REMAINING))
    public static final int RANGE_REMAINING = 291504904;
    /**
     * EV battery average temperature
     *
     * <p>Exposes the temperature of the battery in an EV. If multiple batteries exist in the EV, or
     * multiple temperature sensors exist, this property will be set to a meaningful weighted
     * average that best represents the overall temperature of the battery system.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_ENERGY))
    public static final int EV_BATTERY_AVERAGE_TEMPERATURE = 291504910;
    /**
     * Tire pressure in kilopascals.
     *
     * <p>For each area ID listed in {@link android.car.hardware.CarPropertyConfig#getAreaIds}, the
     * corresponding {@link android.car.hardware.property.AreaIdConfig#getMinValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the OEM recommended tire
     * pressure range for that tire.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WHEEL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_TIRES" to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_TIRES)
    public static final int TIRE_PRESSURE = 392168201;
    /**
     * Critically low tire pressure.
     *
     * <p>For each area ID listed in {@link android.car.hardware.CarPropertyConfig#getAreaIds}, the
     * corresponding {@code CRITICALLY_LOW_TIRE_PRESSURE} will be less than or equal the {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} of {@link #TIRE_PRESSURE} for the
     * same area ID.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WHEEL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_TIRES" to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_TIRES)
    public static final int CRITICALLY_LOW_TIRE_PRESSURE = 392168202;
    /**
     * Currently selected gear by user.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p> See {@link VehicleGear} for gear value enum.
     *
     * <p>configArray represents the list of supported gears for the vehicle. For example,
     * configArray for an EV vehicle is set as follows:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_PARK}
     *  <li>configArray[2] = {@link VehicleGear#GEAR_DRIVE}
     * </ul>
     *
     * <p>Example automatic transmission configArray:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_NEUTRAL}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[2] = {@link VehicleGear#GEAR_PARK}
     *  <li>configArray[3] = {@link VehicleGear#GEAR_DRIVE}
     *  <li>configArray[4] = {@link VehicleGear#GEAR_FIRST}
     *  <li>configArray[5] = {@link VehicleGear#GEAR_SECOND}
     *  <li>...
     * </ul>
     *
     * <p>Example manual transmission configArray:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_NEUTRAL}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[4] = {@link VehicleGear#GEAR_FIRST}
     *  <li>configArray[5] = {@link VehicleGear#GEAR_SECOND}
     *  <li>...
     * </ul>
     *
     * <p>Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     *
     * @data_enum {@link VehicleGear}
     */
    @RequiresPermission(Car.PERMISSION_POWERTRAIN)
    public static final int GEAR_SELECTION = 289408000;
    /**
     * Vehicle transmission's current {@link VehicleGear}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>{@code CURRENT_GEAR}'s value may not match that of {@link
     * VehiclePropertyIds#GEAR_SELECTION}. For example, if the {@link
     * VehiclePropertyIds#GEAR_SELECTION} is {@link VehicleGear#GEAR_DRIVE} in a vehicle with an
     * automatic transmission, the {@code CURRENT_GEAR} will be one of {@link
     * VehicleGear#GEAR_FIRST}, {@link VehicleGear#GEAR_SECOND}, etc, which reflects the actual gear
     * the transmission is currently running in.
     *
     * <p>configArray represents the list of supported {@link VehicleGear}s for {@code
     * CURRENT_GEAR}. For example, the configArray for an EV vehicle is set as follows:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_PARK}
     *  <li>configArray[2] = {@link VehicleGear#GEAR_DRIVE}
     * </ul>
     *
     * <p>Example automatic transmission configArray:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_NEUTRAL}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[2] = {@link VehicleGear#GEAR_PARK}
     *  <li>configArray[4] = {@link VehicleGear#GEAR_FIRST}
     *  <li>configArray[5] = {@link VehicleGear#GEAR_SECOND}
     *  <li>...
     * </ul>
     *
     * <p>Example manual transmission configArray:
     *
     * <ul>
     *  <li>configArray[0] = {@link VehicleGear#GEAR_NEUTRAL}
     *  <li>configArray[1] = {@link VehicleGear#GEAR_REVERSE}
     *  <li>configArray[4] = {@link VehicleGear#GEAR_FIRST}
     *  <li>configArray[5] = {@link VehicleGear#GEAR_SECOND}
     *  <li>...
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleGear}
     */
    @RequiresPermission(Car.PERMISSION_POWERTRAIN)
    public static final int CURRENT_GEAR = 289408001;
    /**
     * Parking brake state.
     *
     * <p>{@code PARKING_BRAKE_ON} is true indicates that the car's parking brake is currently
     * engaged. False implies that the car's parking brake is currently disengaged.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_POWERTRAIN)
    public static final int PARKING_BRAKE_ON = 287310850;
    /**
     * Auto-apply parking brake.
     *
     * <p>{@code PARKING_BRAKE_AUTO_APPLY} is true indicates that the car's automatic parking brake
     * feature is currently enabled. False indicates that the car's automatic parking brake feature
     * is currently disabled.
     *
     * <p>This property is often confused with {@link #PARKING_BRAKE_ON}. The difference is that
     * {@link #PARKING_BRAKE_ON} describes whether the actual parking brake is currently on/off,
     * whereas {@code PARKING_BRAKE_AUTO_APPLY} describes whether the feature of automatic parking
     * brake is enabled/disabled, and does not describe the current state of the actual parking
     * brake.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_POWERTRAIN)
    public static final int PARKING_BRAKE_AUTO_APPLY = 287310851;
    /**
     * Regenerative braking level of a electronic vehicle.
     *
     * <p>Returns the current regenerative braking level. Larger values mean more energy regenerated
     * from braking while smaller values mean less energy regenerated from braking. 0 means no
     * regenerative braking. See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()}
     * and {@link android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of
     * possible values.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_POWERTRAIN" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_POWERTRAIN" to write
     *  property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_POWERTRAIN,
            Car.PERMISSION_CONTROL_POWERTRAIN}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_POWERTRAIN))
    public static final int EV_BRAKE_REGENERATION_LEVEL = 289408012;
    /**
     * Represents property for the current stopping mode of the vehicle.
     *
     * <p>For the global area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} obtained from {@link
     * android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum values from
     * {@code EvStoppingMode} are supported. {@code EvStoppingMode} may be extended to include more
     * states in the future.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_POWERTRAIN" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_POWERTRAIN" to write
     *  property.
     * </ul>
     *
     * @data_enum {@link EvStoppingMode}
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_POWERTRAIN,
            Car.PERMISSION_CONTROL_POWERTRAIN}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_POWERTRAIN))
    public static final int EV_STOPPING_MODE = 289408013;
    /**
     * Warning for fuel low level.
     *
     * <p>{@code FUEL_LEVEL_LOW} corresponds to the low fuel warning on the dashboard. Once {@code
     * FUEL_LEVEL_LOW} is set, it should not be cleared until more fuel is added to the vehicle.
     * This property may take into account all fuel sources for a vehicle - for example:
     * <ul>
     *  <li>For a gas powered vehicle, this property is based solely on gas level.
     *  <li>For a battery powered vehicle, this property is based solely on battery level.
     *  <li>For a hybrid vehicle, this property may be based on the combination of gas and
     *  battery levels, at the OEM's discretion.
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_ENERGY)
    public static final int FUEL_LEVEL_LOW = 287310853;
    /**
     * Night mode.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>True indicates that the night mode sensor has detected that the car cabin environment has
     * low light.
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_EXTERIOR_ENVIRONMENT} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
    public static final int NIGHT_MODE = 287310855;
    /**
     * State of the vehicles turn signals
     *
     * <p>See {@code VehicleTurnSignal} for possible values for {@code TURN_SIGNAL_STATE}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleTurnSignal}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int TURN_SIGNAL_STATE = 289408008;
    /**
     * Vehicle's ignition state.
     *
     * <p>See {@link VehicleIgnitionState} for possible values for {@code IGNITION_STATE}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_POWERTRAIN} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleIgnitionState}
     */
    @RequiresPermission(Car.PERMISSION_POWERTRAIN)
    public static final int IGNITION_STATE = 289408009;
    /**
     * ABS is active.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_DYNAMICS_STATE" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
    public static final int ABS_ACTIVE = 287310858;
    /**
     * Traction Control is active.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_DYNAMICS_STATE" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
    public static final int TRACTION_CONTROL_ACTIVE = 287310859;
    /**
     * Enable or disable Electronic Stability Control (ESC).
     *
     * <p>Returns true if ESC is enabled and false if ESC is disabled. When ESC is enabled, a system
     * in the vehicle should be controlling the tires during instances with high risk of skidding to
     * actively prevent the same from happening.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CAR_DYNAMICS_STATE} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_DYNAMICS_STATE} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_DYNAMICS_STATE} to
     *  write property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_CAR_DYNAMICS_STATE,
            Car.PERMISSION_CONTROL_CAR_DYNAMICS_STATE}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_CAR_DYNAMICS_STATE))
    public static final int ELECTRONIC_STABILITY_CONTROL_ENABLED = 287310862;
    /**
     * Electronic Stability Control (ESC) state.
     *
     * <p>Returns the current state of ESC. This property will always return a valid state defined
     * in {@link android.car.hardware.property.ElectronicStabilityControlState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.ElectronicStabilityControlState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CAR_DYNAMICS_STATE} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.ElectronicStabilityControlState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_CAR_DYNAMICS_STATE))
    public static final int ELECTRONIC_STABILITY_CONTROL_STATE = 289408015;
    /**
     * Fan speed setting.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_FAN_SPEED = 356517120;
    /**
     * Fan direction setting.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     *
     * @data_enum {@link CarHvacFanDirection}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_FAN_DIRECTION = 356517121;
    /**
     * HVAC current temperature in celsius.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_TEMPERATURE_CURRENT = 358614274;
    /**
     * HVAC, target temperature set.
     *
     * <p>The {@code configArray} is used to indicate the valid values for HVAC in Fahrenheit and
     * Celsius. Android might use it in the HVAC app UI.
     *
     * <p>The {@code configArray} is set as follows:
     * <ul>
     *      <li>{@code configArray[0]} is [the lower bound of the supported temperature in Celsius]
     *      * 10.
     *      <li>{@code configArray[1]} is [the upper bound of the supported temperature in Celsius]
     *      * 10.
     *      <li>{@code configArray[2]} is [the increment in Celsius] * 10.
     *      <li>{@code configArray[3]} is
     *      [the lower bound of the supported temperature in Fahrenheit] * 10.
     *      <li>{@code configArray[4]} is
     *      [the upper bound of the supported temperature in Fahrenheit] * 10.
     *      <li>{@code configArray[5]} is [the increment in Fahrenheit] * 10.
     * </ul>
     *
     * <p>For example, if the vehicle supports temperature values as:
     * <pre>
     * [16.0, 16.5, 17.0 ,..., 28.0] in Celsius
     * [60.5, 61.5, 62.5 ,..., 85.5] in Fahrenheit
     * </pre>
     *
     * <p>The {@code configArray} should be:
     * <pre>
     * configArray = {160, 280, 5, 605, 855, 10}.
     * </pre>
     *
     * <p>If the vehicle supports {@link VehiclePropertyIds#HVAC_TEMPERATURE_VALUE_SUGGESTION},
     * the application can use that property to get the suggested value before setting
     * {@code HVAC_TEMPERATURE_SET}. Otherwise, the application may choose the
     * value in {@code configArray} of {@code HVAC_TEMPERATURE_SET} by itself.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_TEMPERATURE_SET = 358614275;
    /**
     * Suggested values for setting HVAC temperature.
     *
     * <p>Implement the property to help applications understand the closest supported temperature
     * value in Celsius or Fahrenheit.
     * <ul>
     *      <li>{@code floatValues[0]} is the requested value that an application wants to set a
     *      temperature to.
     *      <li>{@code floatValues[1]} is the unit for {@code floatValues[0]}. It should be one of
     *      ({@link VehicleUnit#CELSIUS}, {@link VehicleUnit#FAHRENHEIT}).
     *      <li>{@code floatValues[2]} is the value OEMs suggested in CELSIUS. This value is not
     *      included in the request.
     *      <li>{@code floatValues[3]} is the value OEMs suggested in FAHRENHEIT. This value is not
     *      included in the request.
     * </ul>
     *
     * <p>An application calls
     * {@link android.car.hardware.property.CarPropertyManager#setProperty(Class, int, int, Object)}
     * with the requested value and unit for the value. OEMs need to return the suggested values
     * in {@code floatValues[2]} and {@code floatValues[3]} by
     * {@link android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback}.
     *
     * <p>For example, when a user uses the voice assistant to set HVAC temperature to 66.2 in
     * Fahrenheit.
     *
     * <p>First, an application will set this property with the value
     * [66.2, {@link VehicleUnit#FAHRENHEIT}, 0, 0]. If OEMs suggest to set 19.0 in Celsius
     * or 66.5 in Fahrenheit for user's request, then car must generate a callback with property
     * value [66.2, {@link VehicleUnit#FAHRENHEIT}, 19.0, 66.5]. After the voice assistant
     * gets the callback, it will inform the user and set HVAC temperature to the suggested value.
     *
     * <p>Another example, an application receives 21 Celsius as the current temperature value by
     * querying {@link VehiclePropertyIds#HVAC_TEMPERATURE_SET}. But the application wants to know
     * what value is displayed on the car's UI in Fahrenheit.
     *
     * <p>For this, the application sets the property to
     * [21, {@link VehicleUnit#CELSIUS}, 0, 0]. If the suggested value by the OEM for 21
     * Celsius is 70 Fahrenheit, then car must generate a callback with property value
     * [21, {@link VehicleUnit#CELSIUS}, 21.0, 70.0]. In this case, the application can know
     * that the value is 70.0 Fahrenheit in the car’s UI.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_TEMPERATURE_VALUE_SUGGESTION = 291570965;
    /**
     * On/off defrost for designated window.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_DEFROSTER = 320865540;
    /**
     * On/off AC for designated areaId.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_AC_ON = 354419973;
    /**
     * On/off max AC.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_MAX_AC_ON = 354419974;
    /**
     * On/off max defrost.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_MAX_DEFROST_ON = 354419975;
    /**
     * Recirculation on/off.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_RECIRC_ON = 354419976;
    /**
     * Enable temperature coupling between areas.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_DUAL_ON = 354419977;
    /**
     * On/off automatic mode.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_AUTO_ON = 354419978;
    /**
     * Seat heating/cooling.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_SEAT_TEMPERATURE = 356517131;
    /**
     * Side Mirror Heat.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_SIDE_MIRROR_HEAT = 339739916;
    /**
     * Steering Wheel Heating/Cooling.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_STEERING_WHEEL_HEAT = 289408269;
    /**
     * Temperature units for display.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} or Signature|Privileged
     *  permission "android.car.permission.CONTROL_CAR_CLIMATE" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to write
     *  property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_DISPLAY_UNITS,
            Car.PERMISSION_CONTROL_CAR_CLIMATE}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE))
    public static final int HVAC_TEMPERATURE_DISPLAY_UNITS = 289408270;
    /**
     * Actual fan speed.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_ACTUAL_FAN_SPEED_RPM = 356517135;
    /**
     * Represents global power state for HVAC.
     *
     * <p>Setting this property to false MAY mark some properties that control individual HVAC
     * features/subsystems to UNAVAILABLE state. Setting this property to true MAY mark some
     * properties that control individual HVAC features/subsystems to AVAILABLE state (unless
     * any/all of them are UNAVAILABLE on their own individual merits).
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_POWER_ON = 354419984;
    /**
     * Fan Positions Available.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link CarHvacFanDirection}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_FAN_DIRECTION_AVAILABLE = 356582673;
    /**
     * Automatic recirculation on/off.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_AUTO_RECIRC_ON = 354419986;
    /**
     * Seat ventilation.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    public static final int HVAC_SEAT_VENTILATION = 356517139;
    /**
     * ELECTRIC DEFROSTER.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_CLIMATE} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
    @SystemApi
    public static final int HVAC_ELECTRIC_DEFROSTER_ON = 320865556;
    /**
     * Distance units for display.
     *
     * <p>Indicates which units the car is using to display distances to the user.
     *
     * <p>configArray represents the list of supported units for {@code
     * DISTANCE_DISPLAY_UNITS}. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = {@link VehicleUnit#METER}
     *  <li>configArray[1] = {@link VehicleUnit#KILOMETER}
     *  <li>configArray[2] = {@link VehicleUnit#MILE}
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int DISTANCE_DISPLAY_UNITS = 289408512;
    /**
     * Fuel volume units for display.
     *
     * <p>Indicates which units the car is using to display fuel volume to the user.
     *
     * <p>configArray represents the list of supported units for {@code
     * FUEL_VOLUME_DISPLAY_UNITS}. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = {@link VehicleUnit#LITER}
     *  <li>configArray[1] = {@link VehicleUnit#US_GALLON}
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int FUEL_VOLUME_DISPLAY_UNITS = 289408513;
    /**
     * Tire pressure units for display.
     *
     * <p>Indicates which units the car is using to display tire pressure to the user.
     *
     * <p>configArray represents the list of supported units for {@code
     * TIRE_PRESSURE_DISPLAY_UNITS}. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = {@link VehicleUnit#KILOPASCAL}
     *  <li>configArray[1] = {@link VehicleUnit#PSI}
     *  <li>configArray[2] = {@link VehicleUnit#BAR}
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int TIRE_PRESSURE_DISPLAY_UNITS = 289408514;
    /**
     * EV battery units for display.
     *
     * <p>Indicates which units the vehicle is using to display EV battery information to the user.
     *
     * <p>configArray represents the list of supported units for {@code
     * EV_BATTERY_DISPLAY_UNITS}. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = {@link VehicleUnit#WATT_HOUR}
     *  <li>configArray[1] = {@link VehicleUnit#AMPERE_HOURS}
     *  <li>configArray[2] = {@link VehicleUnit#KILOWATT_HOUR}
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int EV_BATTERY_DISPLAY_UNITS = 289408515;
    /**
     * Speed units for display.
     *
     * <p>Indicates type of units the vehicle is using to display speed to user.
     *
     * <p>configArray represents the list of supported units for {@code
     * VEHICLE_SPEED_DISPLAY_UNITS}. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = {@link VehicleUnit#METER_PER_SEC}
     *  <li>configArray[1] = {@link VehicleUnit#MILES_PER_HOUR}
     *  <li>configArray[2] = {@link VehicleUnit#KILOMETERS_PER_HOUR}
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     *
     * @data_enum {@link VehicleUnit}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int VEHICLE_SPEED_DISPLAY_UNITS = 289408516;
    /**
     * Fuel consumption units for display.
     *
     * <p>Indicates type of units the car is using to display fuel consumption information to user.
     *
     * <p>{@code true} indicates units are distance over volume such as MPG.
     *
     * <p>{@code false} indicates units are volume over distance such as L/100KM.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read property.
     *  <li>Normal permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} and Signature|Privileged
     *  permission "android.car.permission.CAR_VENDOR_EXTENSION" to write property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DISPLAY_UNITS))
    @RequiresPermission.Write(@RequiresPermission(allOf = {Car.PERMISSION_CONTROL_DISPLAY_UNITS,
            Car.PERMISSION_VENDOR_EXTENSION}))
    public static final int FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME = 287311364;
    /**
     * Outside temperature in celsius.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_EXTERIOR_ENVIRONMENT} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
    public static final int ENV_OUTSIDE_TEMPERATURE = 291505923;
    /**
     * Property to control power state of application processor.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.hardware.power.CarPowerManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public static final int AP_POWER_STATE_REQ = 289475072;
    /**
     * Property to report power state of application processor.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.hardware.power.CarPowerManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public static final int AP_POWER_STATE_REPORT = 289475073;
    /**
     * Property to report bootup reason for the current power on.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.hardware.power.CarPowerManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public static final int AP_POWER_BOOTUP_REASON = 289409538;
    /**
     * Property to represent brightness of the display.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.hardware.power.CarPowerManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public static final int DISPLAY_BRIGHTNESS = 289409539;
    /**
     * Property to represent brightness of the displays which are controlled separately.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.hardware.power.CarPowerManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public static final int PER_DISPLAY_BRIGHTNESS = 289475076;
    /**
     * Valet mode enabled
     *
     * <p>This property allows the user to enable/disable valet mode in their vehicle. Valet mode is
     * a privacy and security setting that prevents an untrusted driver to access more private areas
     * in the vehicle, such as the glove box or the trunk(s).
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_VALET_MODE} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_VALET_MODE} to read property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_VALET_MODE} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(
            anyOf = {Car.PERMISSION_READ_VALET_MODE, Car.PERMISSION_CONTROL_VALET_MODE}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_VALET_MODE))
    public static final int VALET_MODE_ENABLED = 287312389;
    /**
     * Head up display (HUD) enabled
     *
     * <p>This property allows the user to turn on/off the HUD for their seat.
     *
     * <p>Each HUD in the vehicle will be assigned to the seat that is intended to use it. For
     * example, if there is a single HUD in the vehicle that is used by the driver so that they no
     * longer need to continuously look at the instrument cluster, then this property will be
     * defined with a single area ID that is equal to the driver's seat area ID.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_HEAD_UP_DISPLAY_STATUS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_HEAD_UP_DISPLAY} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_HEAD_UP_DISPLAY} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(
            anyOf = {Car.PERMISSION_READ_HEAD_UP_DISPLAY_STATUS,
                    Car.PERMISSION_CONTROL_HEAD_UP_DISPLAY}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_HEAD_UP_DISPLAY))
    public static final int HEAD_UP_DISPLAY_ENABLED = 354421254;
    /**
     * Property to feed H/W input events to android.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.input.CarInputManager} instead.
     *
     * @to_be_deprecated
     */
    public static final int HW_KEY_INPUT = 289475088;
    /**
     * Door position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the door's
     * position when closed. This value will be 0
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the door's
     * position when fully open.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the closed and fully open positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_DOOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_DOORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
    public static final int DOOR_POS = 373295872;
    /**
     * Door move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the door is opening and negative values mean the door is closing.
     * Larger integers, either positive or negative, indicate a faster speed. Once the door reaches
     * the positional limit, the value resets to 0. When this property's value is 0, that means
     * there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_DOOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_DOORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
    public static final int DOOR_MOVE = 373295873;
    /**
     * Door lock.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_DOOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_DOORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
    public static final int DOOR_LOCK = 371198722;
    /**
     * Door child lock feature enabled.
     *
     * <p>Returns true if the door child lock feature is enabled and false if it is disabled.
     * If enabled, the door is unable to be opened from the inside.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_DOOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_DOORS} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
    public static final int DOOR_CHILD_LOCK_ENABLED = 371198723;
    /**
     * Mirror Z Position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the mirror's
     * position when tilted completely downwards.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the mirror's
     * position when tilted completely upwards.
     * <p>0 indicates the mirror is not tilted in either direction.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the completely downwards and completely upwards positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_Z_POS = 339741504;
    /**
     * Mirror Z Move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the mirror is tilting up and negative values mean the mirror is
     * tilting down. Larger integers, either positive or negative, indicate a faster speed. Once the
     * mirror reaches the positional limit, the value resets to 0. When this property's value is 0,
     * that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_Z_MOVE = 339741505;
    /**
     * Mirror Y Position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the mirror's
     * position when tilted completely to the left.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the mirror's
     * position when tilted completely to the right.
     * <p>0 indicates the mirror is not tilted in either direction.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the extreme left and extreme right positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_Y_POS = 339741506;
    /**
     * Mirror Y Move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the mirror is tilting to the right and negative values mean the
     * support is tilting to the left. Larger integers, either positive or negative, indicate a
     * faster speed. Once the mirror reaches the positional limit, the value resets to 0. When this
     * property's value is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_Y_MOVE = 339741507;
    /**
     * Mirror Lock.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_LOCK = 287312708;
    /**
     * Mirror Fold.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_MIRRORS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_FOLD = 287312709;
    /**
     * Represents property for the Mirror Auto Fold feature.
     *
     * <p>This property is true when the feature for automatically folding the vehicle's mirrors
     * (for example, when the mirrors fold inward automatically when one exits and locks the
     * vehicle) is enabled.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_MIRRORS} to read and
     *  write property.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_AUTO_FOLD_ENABLED = 337644358;
    /**
     * Represents property for the Mirror Auto Tilt feature.
     *
     * <p>This property is true when the feature for automatically tilting the vehicle's mirrors
     * (for example, when the mirrors tilt downward automatically when one reverses the vehicle) is
     * enabled.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_MIRRORS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
    public static final int MIRROR_AUTO_TILT_ENABLED = 337644359;
    /**
     * Property that represents the current position of the glove box door.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the glove box's
     * position when closed. This value will be 0.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the glove box's
     * position when fully open.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the closed and fully open positions.
     *
     * <p>The supported area IDs match the seat(s) by which the glove box is intended to be used
     * (e.g.) if the front right dashboard has a glove box embedded in it, then the area ID should
     * be {@link VehicleAreaSeat#SEAT_ROW_1_RIGHT}).
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_GLOVE_BOX} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
    public static final int GLOVE_BOX_DOOR_POS = 356518896;

    /**
     * Lock or unlock the glove box.
     *
     * <p>If {@code true}, the glove box is locked. If {@code false}, the glove box is unlocked.
     *
     * <p>The supported area IDs match the seat(s) by which the glove box is intended to be used
     * (e.g. if the front right dashboard has a glove box embedded in it, then the area ID will be
     * {@link android.car.VehicleAreaSeat#SEAT_ROW_1_RIGHT}).
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_GLOVE_BOX} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
    public static final int GLOVE_BOX_LOCKED = 354421745;

    /**
     * Seat memory select.
     *
     * <p>This parameter selects the memory preset to use to select the seat position. The {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} is always 0, and the {@link
     * android.car.hardware.property.AreaIdConfig#getMaxValue()} determines the number of seat
     * positions available (i.e. numSeatPresets - 1).
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Property is not readable.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to write
     *  property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_MEMORY_SELECT = 356518784;
    /**
     * Seat memory set.
     *
     * <p>This setting allows the user to save the current seat position settings into the selected
     * preset slot. The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} for each
     * seat position must match the {@link android.car.hardware.property.AreaIdConfig#getMaxValue()}
     * for {@link #SEAT_MEMORY_SELECT}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Property is not readable.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to write
     *  property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_MEMORY_SET = 356518785;
    /**
     * Seatbelt buckled.
     *
     * <p>True indicates belt is buckled.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BELT_BUCKLED = 354421634;
    /**
     * Seatbelt height position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat belt
     * shoulder anchor's lowest position.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat belt
     * shoulder anchor's highest position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the lowest and highest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BELT_HEIGHT_POS = 356518787;
    /**
     * Seatbelt height move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat belt's shoulder anchor is moving up and negative values mean
     * the seat belt's shoulder anchor is moving down. Larger integers, either positive or negative,
     * indicate a faster speed. Once the seat belt's shoulder anchor reaches the positional limit,
     * the value resets to 0. When this property's value is 0, that means there is no movement
     * currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BELT_HEIGHT_MOVE = 356518788;
    /**
     * Seat fore/aft position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat's
     * rearward-most linear position.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat's
     * forward-most linear position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_FORE_AFT_POS = 356518789;
    /**
     * Seat fore/aft move.
     *
     * <p>This property moves the entire seat forward/backward in the direction it's facing.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat is moving forward and negative values mean the seat is
     * moving backward. Larger integers, either positive or negative, indicate a faster speed. Once
     * the seat reaches the positional limit, the value resets to 0. When this property's value is
     * 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_FORE_AFT_MOVE = 356518790;
    /**
     * Seat backrest angle 1 position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * backrest's full recline position w.r.t the actuator at the bottom of the seat (see {@link
     * #SEAT_BACKREST_ANGLE_1_MOVE} for additional details).
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * backrest's most upright/forward position w.r.t the actuator at the bottom of the seat (see
     * {@link #SEAT_BACKREST_ANGLE_1_MOVE} for additional details).
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BACKREST_ANGLE_1_POS = 356518791;
    /**
     * Seat backrest angle 1 move.
     *
     * <p>This property moves the seat backrest along the axis of rotation defined by the actuator
     * closest to the bottom of the seat. This is the actuator that moves the seat upright/forward
     * or into recline as seen in most conventional vehicles.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat is angling forward and negative values mean the seat is
     * reclining backward. Larger integers, either positive or negative, indicate a faster speed.
     * Once the seat reaches the positional limit, the value resets to 0. When this property's value
     * is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BACKREST_ANGLE_1_MOVE = 356518792;
    /**
     * Seat backrest angle 2 position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * backrest's full recline position w.r.t the next actuator in the backrest from the one at the
     * bottom of the seat (see {@link #SEAT_BACKREST_ANGLE_2_MOVE} for more details).
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * backrest's most upright/forward position w.r.t the next actuator in the backrest from the one
     * at the bottom of the seat (see {@link #SEAT_BACKREST_ANGLE_2_MOVE} for more details).
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BACKREST_ANGLE_2_POS = 356518793;
    /**
     * Seat backrest angle 2 move.
     *
     * <p>This property will be implemented if there exists an additional actuator in the seat
     * backrest besides the one at the very bottom of the backrest, which is covered by {@link
     * #SEAT_BACKREST_ANGLE_1_MOVE}. This property will move the backrest along the axis of rotation
     * defined by this additional actuator. It can be assumed that this actuator is closer to the
     * headrest than the one defined by {@link #SEAT_BACKREST_ANGLE_1_MOVE}.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat is angling forward and negative values mean the seat is
     * reclining backward. Larger integers, either positive or negative, indicate a faster speed.
     * Once the seat reaches the positional limit, the value resets to 0. When this property's value
     * is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_BACKREST_ANGLE_2_MOVE = 356518794;
    /**
     * Seat height position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat's
     * lowest position.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat's
     * highest position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the lowest and highest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEIGHT_POS = 356518795;
    /**
     * Seat height move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat is moving up and negative values mean the seat is moving
     * down. Larger integers, either positive or negative, indicate a faster speed. Once the seat
     * reaches the positional limit, the value resets to 0. When this property's value is 0, that
     * means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEIGHT_MOVE = 356518796;
    /**
     * Seat depth position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat's
     * shallowest position. This corresponds to the smallest distance between the front edge of the
     * seat and the seat backrest.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat's
     * deepest position. This corresponds to the largest distance between the front edge of the seat
     * and the seat backrest.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the shallowest and deepest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_DEPTH_POS = 356518797;
    /**
     * Seat depth move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the distance between the seat's front edge and the back of the seat
     * is increasing, thus making the seat deeper. Negative values mean the distance between the
     * seat's front edge and the back of the seat is decreasing, thus making the seat shallower.
     * Larger integers, either positive or negative, indicate a faster speed. Once the seat depth
     * reaches its limit, the value resets to 0. When this property's value is 0, that means there
     * is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_DEPTH_MOVE = 356518798;
    /**
     * Seat tilt position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * bottom's lowest angular position. This corresponds to the seat's front edge at its lowest
     * possible position relative to the rear end of the seat.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * bottom's highest angular position. This corresponds to the seat's front edge at its highest
     * possible position relative to the rear end of the seat.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the lowest and highest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_TILT_POS = 356518799;
    /**
     * Seat tilt move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the seat cushion is tilting upward such that the seat cushion's front
     * edge is higher than the rear end of the seat cushion. Negative values mean the seat cushion
     * is tilting downward such that the seat cushion's front edge is lower than the rear end of the
     * seat cushion. Larger integers, either positive or negative, indicate a faster speed. Once the
     * seat cushion reaches the positional limit, the value resets to 0. When this property's value
     * is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_TILT_MOVE = 356518800;
    /**
     * Lumber fore/aft position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat lumbar
     * support's rearward most position (i.e. least supportive position).
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat lumbar
     * support's forward most position (i.e. most supportive position).
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_FORE_AFT_POS = 356518801;
    /**
     * Lumbar fore/aft move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the lumbar support is moving forward towards the front of the seat
     * and negative values mean the lumbar support is moving backward away from the front of the
     * seat. Larger integers, either positive or negative, indicate a faster speed. Once the lumbar
     * support reaches the positional limit, the value resets to 0. When this property's value is 0,
     * that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_FORE_AFT_MOVE = 356518802;
    /**
     * Lumbar side support position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat lumbar
     * side support's thinnest position (i.e most support).
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat lumbar
     * side support's widest position (i.e least support).
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the thinnest and widest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_SIDE_SUPPORT_POS = 356518803;
    /**
     * Lumbar side support move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the lumbar side support is getting wider (i.e. less support) and
     * negative values mean the lumbar side support is getting thinner (i.e. more support). Larger
     * integers, either positive or negative, indicate a faster speed. Once the lumbar side support
     * reaches the positional limit, the value resets to 0. When this property's value is 0, that
     * means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_SIDE_SUPPORT_MOVE = 356518804;

    /**
     * Headrest height position.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * @deprecated because it is defined as type {@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL},
     * which means all seats use the same value. Use {@link #SEAT_HEADREST_HEIGHT_POS_V2} instead
     * which fixes this issue by being defined as type
     * {@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}.
     */
    @Deprecated
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_HEIGHT_POS = 289409941;

    /**
     * Headrest height position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * headrest's shortest position.
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * headrest's tallest position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the shortest and tallest positions.
     *
     * <p>{@link android.car.hardware.CarPropertyConfig#getAreaIds()} specifies which seats are
     * supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_HEIGHT_POS_V2 = 356518820;

    /**
     * Headrest height move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the headrest is moving up and negative values mean the headrest is
     * moving down. Larger integers, either positive or negative, indicate a faster speed. Once the
     * headrest reaches the positional limit, the value resets to 0. When this property's value is
     * 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_HEIGHT_MOVE = 356518806;
    /**
     * Headrest angle position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * headrest's full recline position.
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * headrest's most upright/forward position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_ANGLE_POS = 356518807;
    /**
     * Headrest angle move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the headrest is moving upright/forward and negative values mean the
     * headrest is reclining. Larger integers, either positive or negative, indicate a faster speed.
     * Once the headrest reaches the positional limit, the value resets to 0. When this property's
     * value is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_ANGLE_MOVE = 356518808;
    /**
     * Headrest fore/aft position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * headrest's rearward-most linear position.
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * headrest's forward-most linear position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the forward most and rearward most positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_FORE_AFT_POS = 356518809;
    /**
     * Headrest fore/aft move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the headrest is moving forward towards the front of the seat and
     * negative values mean the headrest is moving backward away from the front of the seat. Larger
     * integers, either positive or negative, indicate a faster speed. Once the headrest reaches the
     * positional limit, the value resets to 0. When this property's value is 0, that means there is
     * no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  and write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_HEADREST_FORE_AFT_MOVE = 356518810;
    /**
     * Represents property for state of the footwell lights.
     *
     * <p>{@code SEAT_FOOTWELL_LIGHTS_STATE} reflects the current state of the lights at any point
     * in time. This is different from the function of {@link #SEAT_FOOTWELL_LIGHTS_SWITCH} which
     * represents the position of the switch controlling the lights. Therefore, {@code
     * SEAT_FOOTWELL_LIGHTS_STATE} may not match the value of {@link #SEAT_FOOTWELL_LIGHTS_SWITCH}
     * (e.g. {@link #SEAT_FOOTWELL_LIGHTS_SWITCH}={@code VehicleLightSwitch#STATE_AUTOMATIC} and
     * {@code SEAT_FOOTWELL_LIGHTS_STATE}={@code VehicleLightState#STATE_ON}).
     *
     * <p>This property will only be implemented if {@code SEAT_FOOTWELL_LIGHTS_STATE}'s value may
     * be different from that of {@link #CABIN_LIGHTS_STATE}.
     *
     * <p>For each supported area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} obtained from {@link
     * android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum values from
     * {@code VehicleLightState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_INTERIOR_LIGHTS" to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
    public static final int SEAT_FOOTWELL_LIGHTS_STATE = 356518811;
    /**
     * Represents property for switch of the footwell lights.
     *
     * <p>{@code SEAT_FOOTWELL_LIGHTS_SWITCH} represents the position of the switch controlling the
     * lights. This is different from the function of {@link #SEAT_FOOTWELL_LIGHTS_STATE} which
     * reflects the current state of the lights at any point in time. Therefore, {@code
     * SEAT_FOOTWELL_LIGHTS_SWITCH} may not match the value of {@link #SEAT_FOOTWELL_LIGHTS_STATE}
     * (e.g. {@code SEAT_FOOTWELL_LIGHTS_SWITCH}={@code VehicleLightSwitch#STATE_AUTOMATIC} and
     * {@link #SEAT_FOOTWELL_LIGHTS_STATE}={@code VehicleLightState#STATE_ON}).
     *
     * <p>This property will only be implemented if {@code SEAT_FOOTWELL_LIGHTS_SWITCH}'s value may
     * be different from that of {@link #CABIN_LIGHTS_SWITCH}.
     *
     * <p>For each supported area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} obtained from {@link
     * android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum values from
     * {@code VehicleLightSwitch} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
    public static final int SEAT_FOOTWELL_LIGHTS_SWITCH = 356518812;
    /**
     * Represents property for Seat easy access feature.
     *
     * <p>If true, the seat will automatically adjust to make it easier for the occupant to enter
     * and exit the vehicle. Each area ID maps to the seat that the user is trying to enter/exit
     * with the help of the easy access feature.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_EASY_ACCESS_ENABLED = 354421661;
    /**
     * Represents feature to enable/disable a seat's ability to deploy airbag(s) when triggered
     * (e.g. by a crash).
     *
     * <p>If true, it means the seat's airbags are enabled, and if triggered (e.g. by a crash), they
     * will deploy. If false, it means the seat's airbags are disabled, and they will not deploy
     * under any circumstance. This property does not indicate if the airbags are deployed or not.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_AIRBAGS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS)
    public static final int SEAT_AIRBAG_ENABLED = 354421662;
    /**
     * State of deployment for seat airbags.
     *
     * <p>Bit flag property to relay information on which airbags have been deployed in the vehicle
     * at each seat, vs which ones are currently still armed. When SEAT_AIRBAG_ENABLED is set to
     * false at a particular areaId, this property will be UNAVAILABLE at that areaId.
     *
     * <p>Enums apply to each seat, not the global vehicle. For example,
     * {@link android.car.hardware.property.VehicleAirbagLocation#CURTAIN} at the driver seat areaId
     * represents whether the driver side curtain airbag has been deployed. Multiple bit flags can
     * be set to indicate that multiple different airbags have been deployed for the seat.
     *
     * <p>For each seat area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.VehicleAirbagLocation} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_AIRBAGS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.VehicleAirbagLocation}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_CAR_AIRBAGS))
    public static final int SEAT_AIRBAGS_DEPLOYED = 356518821;
    /**
     * Represents property for seat’s hipside (bottom cushion’s side) support position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * hipside support's thinnest position (i.e. most support).
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * hipside support's widest position (i.e. least support).
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the thinnest and widest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_CUSHION_SIDE_SUPPORT_POS = 356518815;
    /**
     * Represents property for movement direction and speed of seat cushion side support.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values means the seat cushion side support is growing wider (i.e. less support)
     * and negative values means the seat cushion side support is growing thinner (i.e. more
     * support). Larger integers, either positive or negative, indicate a faster speed. Once the
     * seat cushion side support reaches the positional limit, the value resets to 0. When this
     * property's value is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_CUSHION_SIDE_SUPPORT_MOVE = 356518816;
    /**
     * Represents property for seat’s lumbar support vertical position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the seat
     * lumbar support's lowest position.
     * <p>The {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat
     * lumbar support's highest position.
     *
     * <p>All integers between the min and max values are supported and indicate a transition state
     * between the lowest and highest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_VERTICAL_POS = 356518817;
    /**
     * Represents property for vertical movement direction and speed of seat lumbar support.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the lumbar support is moving up and negative values mean the lumbar
     * support is moving down. Larger integers, either positive or negative, indicate a faster
     * speed. Once the lumbar support reaches the positional limit, the value resets to 0. When this
     * property's value is 0, that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_LUMBAR_VERTICAL_MOVE = 356518818;
    /**
     * Represents property that indicates the current walk-in position of the seat.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the normal seat
     * position.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the seat's
     * position in full walk-in mode.
     *
     * <p>All integers in between the min and max values are supported and indicate a transition
     * state between the normal and walk-in positions. The area IDs match the seats that actually
     * move when the walk-in feature activates.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_CAR_SEATS} to read and
     *  write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_WALK_IN_POS = 356518819;
    /**
     * Seat belt pretensioner deployed.
     *
     * <p>Property to relay information on whether the seat belt pretensioner has been deployed for
     * a particular seat due to a collision. This is different from the regular seat belt tightening
     * system that continuously adds tension to the seat belts so that they fit snugly around the
     * person sitting in the seat, nor is it the seat belt retractor system that locks the seat belt
     * in place during sudden brakes or when the user jerks the seat belt.
     *
     * <p>If this property is dependant on the state of other properties, and if those properties
     * are currently in the state that doesn't support this property, reading this property will
     * throw {@link android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_SEAT_BELTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_CAR_SEAT_BELTS))
    public static final int SEAT_BELT_PRETENSIONER_DEPLOYED = 354421670;
    /**
     * Seat Occupancy.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_SEATS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleSeatOccupancyState}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
    public static final int SEAT_OCCUPANCY = 356518832;
    /**
     * Window Position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the window's
     * position when closed/fully open out of plane. If the window cannot open out of plane, then
     * {@link android.car.hardware.property.AreaIdConfig#getMinValue()} is the position of the
     * window when fully closed and must be 0. If the window can open out of plane, {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the window is fully open
     * in its position out of plane and will be a negative value.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the window's
     * position when fully open.
     *
     * <p>All integers in between the min and max values are supported and indicate a transition
     * state between the closed and fully open positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_WINDOWS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
    public static final int WINDOW_POS = 322964416;
    /**
     * Window Move.
     *
     * <p>This property is not in any particular unit but in a specified range of relative movement
     * speeds.
     *
     * <p>Positive values mean the window is either closing from its out of plane position (if such
     * a position is supported by the window), or is opening in plane. Negative values mean the
     * window is closing in plane, or opening in its out of plane position (if the position is
     * supported). Larger integers, either positive or negative, indicate a faster speed. Once the
     * window reaches the positional limit, the value resets to 0. When this property's value is 0,
     * that means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_WINDOWS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
    public static final int WINDOW_MOVE = 322964417;
    /**
     * Window Lock.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_WINDOWS" to read and
     *  write property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
    public static final int WINDOW_LOCK = 320867268;

    /**
     * Windshield wipers period (milliseconds).
     *
     * <p>Returns the instantaneous time period for 1 full cycle of the windshield wipers in {@link
     * android.car.VehicleUnit#MILLI_SECS}. A full cycle is defined as a wiper moving from and
     * returning to its rest position. The {@link
     * android.car.hardware.property.AreaIdConfig#getMaxValue()} specifies the longest wiper period.
     * The {@link android.car.hardware.property.AreaIdConfig#getMinValue()} is always 0. When an
     * intermittent wiper setting is selected, this property value will be set to 0 during the
     * "pause" phase of the intermittent wiping.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_WINDSHIELD_WIPERS} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS))
    public static final int WINDSHIELD_WIPERS_PERIOD = 322964421;

    /**
     * Windshield wipers state.
     *
     * <p>Returns the current state of the windshield wipers. The value of {@code
     * WINDSHIELD_WIPERS_STATE} may not match the value of {@link #WINDSHIELD_WIPERS_SWITCH}. (e.g.
     * {@code #WINDSHIELD_WIPERS_STATE} = {@link
     * android.car.hardware.property.WindshieldWipersState#ON} and {@link
     * #WINDSHIELD_WIPERS_SWITCH} = {@link
     * android.car.hardware.property.WindshieldWipersSwitch#AUTO}).
     *
     * <p>If {@code #WINDSHIELD_WIPERS_STATE} = {@link
     * android.car.hardware.property.WindshieldWipersState#ON} and {@link #WINDSHIELD_WIPERS_PERIOD}
     * is implemented, then {@link #WINDSHIELD_WIPERS_PERIOD} will reflect the time period of 1
     * full cycle of the wipers.
     *
     * <p>For each supported area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.WindshieldWipersState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_WINDSHIELD_WIPERS} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link WindshieldWipersState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS))
    public static final int WINDSHIELD_WIPERS_STATE = 322964422;

    /**
     * Windshield wipers switch.
     *
     * <p>Represents the position of the switch controlling the windshield wipers. The value of
     * {@code WINDSHIELD_WIPERS_SWITCH} may not match the value of {@link #WINDSHIELD_WIPERS_STATE}
     * (e.g. {@code WINDSHIELD_WIPERS_SWITCH} = {@link
     * android.car.hardware.property.WindshieldWipersSwitch#AUTO} and {@link
     * #WINDSHIELD_WIPERS_STATE} = WindshieldWipersState#ON).
     *
     * <p>For each supported area ID, the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which values
     * from {@link android.car.hardware.property.WindshieldWipersSwitch} are supported.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_WINDSHIELD_WIPERS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_WINDSHIELD_WIPERS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_WINDSHIELD_WIPERS} to
     *  write property.
     * </ul>
     *
     * @data_enum {@link WindshieldWipersSwitch}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_WINDSHIELD_WIPERS,
            Car.PERMISSION_CONTROL_WINDSHIELD_WIPERS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_WINDSHIELD_WIPERS))
    public static final int WINDSHIELD_WIPERS_SWITCH = 322964423;

    /**
     * Steering wheel depth position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the steering
     * wheel's position when closest to the driver.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the steering
     * wheel's position when farthest from the driver.
     *
     * <p>All integers in between the min and max values are supported and indicate a transition
     * state between the closest and farthest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_DEPTH_POS = 289410016;
    /**
     * Steering wheel depth movement.
     *
     * <p>Returns the speed and direction, either towards or away from the driver, that the
     * steering wheel is moving in. This property is not in any particular unit but in a specified
     * range of relative movement speeds.
     *
     * <p>Positive values mean the steering wheel is moving away from the driver and negative values
     * mean the steering wheel is moving towards the driver. Larger integers, either positive or
     * negative, indicate a faster speed. Once the steering wheel reaches the positional limit, the
     * value resets to 0. When this property's value is 0, that means there is no movement currently
     * occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_DEPTH_MOVE = 289410017;
    /**
     * Steering wheel height position.
     *
     * <p>This property is not in any particular unit but in a specified range of relative
     * positions.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} indicates the steering
     * wheel's lowest position.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} indicates the steering
     * wheel's highest position.
     *
     * <p>All integers in between the min and max values are supported and indicate a transition
     * state between the lowest and highest positions.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_HEIGHT_POS = 289410018;
    /**
     * Steering wheel height movement.
     *
     * <p>Returns the speed and direction, either upwards or downwards, that the steering wheel is
     * moving in. This property is not in any particular unit but in a specified range of relative
     * movement speeds.
     *
     * <p>Positive values mean moving upwards and negative values mean moving downwards. Larger
     * integers, either positive or negative, indicate a faster speed. Once the steering wheel
     * reaches the positional limit, the value resets to 0. When this property's value is 0, that
     * means there is no movement currently occurring.
     *
     * <p>See {@link android.car.hardware.property.AreaIdConfig#getMaxValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMinValue()} for the range of possible speeds.
     * All integers between min and max value are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_HEIGHT_MOVE = 289410019;
    /**
     * Steering wheel theft lock feature enabled.
     *
     * <p>Returns true if the steering wheel theft lock feature is enabled and false if it is
     * disabled. If enabled, the steering wheel will lock automatically to prevent theft in
     * certain situations.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_THEFT_LOCK_ENABLED = 287312868;
    /**
     * Steering wheel locked.
     *
     * <p>Returns true if the steering wheel is locked. If locked, the steering wheel’s position is
     * not changeable.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_LOCKED = 287312869;
    /**
     * Steering wheel easy access feature enabled.
     *
     * <p>Returns true if the steering wheel easy access feature is enabled and false if it is
     * disabled. If enabled, the driver’s steering wheel will automatically adjust to make it easier
     * for the driver to enter and exit the vehicle.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_STEERING_WHEEL} to read
     *  and write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
    public static final int STEERING_WHEEL_EASY_ACCESS_ENABLED = 287312870;
    /**
     * Vehicle Maps Service (VMS) message.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.vms.VmsClientManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(anyOf = {Car.PERMISSION_VMS_PUBLISHER, Car.PERMISSION_VMS_SUBSCRIBER})
    public static final int VEHICLE_MAP_SERVICE = 299895808;
    /**
     * Characterization of inputs used for computing location.
     *
     * <p>This property indicates what (if any) data and sensor inputs are considered by the system
     * when computing the vehicle's location that is shared with Android through {@link
     * android.location.LocationManager#GPS_PROVIDER}.
     *
     * <p>The value returned is a collection of bit flags. The bit flags are defined in {@link
     * LocationCharacterization}. The value will also include exactly
     * one of {@link LocationCharacterization#DEAD_RECKONED} or {@link
     * LocationCharacterization#RAW_GNSS_ONLY} among its collection of
     * bit flags.
     *
     * <p>When this property is not supported, it is assumed that no additional sensor inputs are
     * fused into the GNSS updates provided through {@link
     * android.location.LocationManager#GPS_PROVIDER}. That is unless otherwise specified through
     * other {@link android.location.LocationManager} APIs.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link android.Manifest.permission#ACCESS_FINE_LOCATION} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_flag {@link LocationCharacterization}
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public static final int LOCATION_CHARACTERIZATION = 289410064;

    /**
     * Static data for the position of each ultrasonic sensor installed on the vehicle.
     *
     * <p>Each individual sensor is identified by its unique {@link AreaIdConfig#getAreaId()} and
     * returns the sensor's position formatated as [x, y, z] where:
     *
     * <ul>
     *  <li>x is the position of the sensor along the x-axis relative to the origin of the Android
     *  Automotive sensor coordinate frame in millimeters.
     *  <li>y is the position of the sensor along the y-axis relative to the origin of the Android
     *  Automotive sensor coordinate frame in millimeters.
     *  <li>z is the position of the sensor along the z-axis relative to the origin of the Android
     *  Automotive sensor coordinate frame in millimeters.
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_POSITION = 406916128;

    /**
     * Static data for the orientation of each ultrasonic sensor installed on the vehicle.
     *
     * <p>Each individual sensor is identified by its {@link AreaIdConfig#getAreaId()} and returns
     * the sensor's orientation formatted as [qw, qx, qy, qz] where:
     *
     * <ul>
     *  <li>qw is the quaternion coefficient w within the quaterinion (w + xi + yj + zk) describing
     *  the rotation of the sensor relative to the Android Automotive sensor coordinate frame.
     *  <li>qx is the quaternion coefficient x within the quaterinion (w + xi + yj + zk) describing
     *  the rotation of the sensor relative to the Android Automotive sensor coordinate frame.
     *  <li>qy is the quaternion coefficient y within the quaterinion (w + xi + yj + zk) describing
     *  the rotation of the sensor relative to the Android Automotive sensor coordinate frame.
     *  <li>qz is the quaternion coefficient z within the quaterinion (w + xi + yj + zk) describing
     *  the rotation of the sensor relative to the Android Automotive sensor coordinate frame.
     * </ul>
     *
     * <p>This assumes each sensor uses the same axes conventions as Android Automotive.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Float[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_ORIENTATION = 409013281;

    /**
     * Static data for the field of view of each ultrasonic sensor in degrees.
     *
     * <p>Each individual sensor is identified by its {@link AreaIdConfig#getAreaId()} and returns
     * the sensor's field of view formatted as [horizontal, vertical] where:
     *
     * <ul>
     *  <li>horizontal is the horizontal field of view for the specified ultrasonic sensor in
     *  degrees.
     *  <li>vertical is the vertical field of view for the associated specified ultrasonic sensor in
     *  degrees.
     * </ul>
     *
     * <p>This assumes each sensor uses the same axes conventions as Android Automotive.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_FIELD_OF_VIEW = 406916130;

    /**
     * Static data for the detection range of each ultrasonic sensor in millimeters.
     *
     * <p>Each individual sensor is identified by its {@link AreaIdConfig#getAreaId()} and returns
     * the sensor's detection range formatted as [minimum, maximum] where:
     *
     * <ul>
     *  <li>minimum is the minimum range detectable by the ultrasonic sensor in millimeters.
     *  <li>maximum is the maximum range detectable by the ultrasonic sensor in millimeters.
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_DETECTION_RANGE = 406916131;

    /**
     * Static data for the supported ranges of each ultrasonic sensor in millimeters.
     *
     * <p>For ultrasonic sensors that only support readings within a specific range. For example, if
     * an ultrasonic sensor detects an object at 700mm, but can only report that an object has been
     * detected between 500mm and 1000mm.
     *
     * <p>Each individual sensor is identified by its {@link AreaIdConfig#getAreaId()} and returns
     * the sensor's supported ranges formatted as [range_min_1, range_max_1, range_min_2,
     * range_max_2, ...] where:
     *
     * <ul>
     *  <li>range_min_1 is the minimum of one supported range by the specified sensor in
     *  millimeters, inclusive.
     *  <li>range_max_1 is the maximum of one supported range by the specified sensor in
     *  millimeters, inclusive.
     *  <li> range_min_2 is the minimum of another supported range by the specified sensor in
     *  millimeters, inclusive.
     *  <li> range_max_2 is the maximum of another supported range by the specified sensor in
     *  millimeters, inclusive.
     * </ul>
     *
     * <p>For example, if an ultrasonic sensor supports the ranges 150mm to  499mm, 500mm to 999mm,
     * and 1000mm to 1500mm, then the property should be set to:
     * <ul>
     *  <li>value[0] = 150
     *  <li>value[1] = 499
     *  <li>value[2] = 500
     *  <li>value[3] = 999
     *  <li>value[4] = 1000
     *  <li>value[5] = 1500
     * </ul>
     *
     * <p>If this property is not defined, all the values within the
     * {@link #ULTRASONICS_SENSOR_DETECTION_RANGE} for the specified sensor are assumed to be
     * supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_SUPPORTED_RANGES = 406916132;

    /**
     * The distance reading of the nearest detected object per sensor in millimeters.
     *
     * <p>Each individual sensor is identified by its {@link AreaIdConfig#getAreaId()} and returns
     * the sensor's measured distance formatted as [distance, distance_error] where:
     *
     * <ul>
     *  <li>distance is the measured distance of the nearest object in millimeters. If only a range
     *  is supported, this value must be set to the minimum supported distance in the detected range
     *  as specified in {@link #ULTRASONICS_SENSOR_SUPPORTED_RANGES}.
     *  <li>distance_error is the error of the measured distance value in millimeters.
     * </ul>
     *
     * <p>If no object is detected, an empty vector will be returned. If distance_error is not
     * available then an array of only the measured distance will be returned.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_VENDOR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ULTRASONICS_SENSOR_DATA} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ULTRASONICS_SENSOR_DATA))
    public static final int ULTRASONICS_SENSOR_MEASURED_DISTANCE = 406916133;

    /**
     * OBD2 Live Sensor Data.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.diagnostic.CarDiagnosticManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL)
    public static final int OBD2_LIVE_FRAME = 299896064;
    /**
     * OBD2 Freeze Frame Sensor Data.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.diagnostic.CarDiagnosticManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL)
    public static final int OBD2_FREEZE_FRAME = 299896065;
    /**
     * OBD2 Freeze Frame Information.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.diagnostic.CarDiagnosticManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL)
    public static final int OBD2_FREEZE_FRAME_INFO = 299896066;
    /**
     * OBD2 Freeze Frame Clear.
     *
     * <p>Not exposed through {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>This property is not supported.
     *
     * use {@link android.car.diagnostic.CarDiagnosticManager} instead.
     *
     * @to_be_deprecated
     */
    @RequiresPermission(Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR)
    public static final int OBD2_FREEZE_FRAME_CLEAR = 299896067;
    /**
     * Headlights State.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int HEADLIGHTS_STATE = 289410560;
    /**
     * High beam lights state.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int HIGH_BEAM_LIGHTS_STATE = 289410561;
    /**
     * Fog light state.
     *
     * <p>If the car has both front and rear fog lights:
     * <ul>
     *  <li>If front and rear fog lights can only be controlled together: {@code FOG_LIGHTS_STATE}
     *  provides the state of fog lights.
     *  <li>If front and rear fog lights can only be controlled independently: {@link
     *  #FRONT_FOG_LIGHTS_STATE} and {@link #REAR_FOG_LIGHTS_STATE} provide the state of front, rear
     *  fog lights respectively.
     * </ul>
     *
     * <p>If the car has only front fog lights:
     * <ul>
     *  <li>Only one of {@code FOG_LIGHTS_STATE} or {@link #FRONT_FOG_LIGHTS_STATE} will be
     *  implemented in the car. The implemented property provides the state of the front fog lights.
     * </ul>
     *
     * <p>If the car has only rear fog lights:
     * <ul>
     *  <li>Only one of {@code FOG_LIGHTS_STATE} or {@link #REAR_FOG_LIGHTS_STATE} will be
     *  implemented in the car. The implemented property provides the state of the rear fog lights.
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int FOG_LIGHTS_STATE = 289410562;
    /**
     * Hazard light status.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int HAZARD_LIGHTS_STATE = 289410563;
    /**
     * Headlight switch.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int HEADLIGHTS_SWITCH = 289410576;
    /**
     * High beam light switch.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int HIGH_BEAM_LIGHTS_SWITCH = 289410577;
    /**
     * Fog light switch.
     *
     * <p>If the car has both front and rear fog lights:
     * <ul>
     *  <li>If front and rear fog lights can only be controlled together: {@code FOG_LIGHTS_SWITCH}
     *  should be used to change the fog lights state.
     *  <li>If front and rear fog lights can only be controlled independently: {@link
     *  #FRONT_FOG_LIGHTS_SWITCH} and {@link #REAR_FOG_LIGHTS_SWITCH} should be used to change the
     *  front and rear fog lights state respectively.
     * </ul>
     *
     * <p>If the car has only front fog lights:
     * <ul>
     *  <li>Only one of {@code FOG_LIGHTS_SWITCH} or {@link #FRONT_FOG_LIGHTS_SWITCH} will be
     *  implemented in the car. The implemented property should be used to change the front fog
     *  lights state.
     * </ul>
     *
     * <p>If the car has only rear fog lights:
     * <ul>
     *  <li>Only one of {@code FOG_LIGHTS_SWITCH} or {@link #REAR_FOG_LIGHTS_SWITCH} will be
     *  implemented in the car. The implemented property should be used to change the rear fog
     *  lights state.
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int FOG_LIGHTS_SWITCH = 289410578;
    /**
     * Hazard light switch.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int HAZARD_LIGHTS_SWITCH = 289410579;
    /**
     * Cabin lights.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_INTERIOR_LIGHTS" to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
    public static final int CABIN_LIGHTS_STATE = 289410817;
    /**
     * Cabin lights switch.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
    public static final int CABIN_LIGHTS_SWITCH = 289410818;
    /**
     * Reading lights.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_INTERIOR_LIGHTS" to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
    public static final int READING_LIGHTS_STATE = 356519683;
    /**
     * Reading lights switch.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
    public static final int READING_LIGHTS_SWITCH = 356519684;

    /**
     * Steering wheel lights state.
     *
     * <p>Returns the current state of the steering wheel lights. This is different from {@link
     * #STEERING_WHEEL_LIGHTS_SWITCH} which represents the position of the switch controlling
     * the lights. Therefore, {@code STEERING_WHEEL_LIGHTS_STATE} may not match the value of
     * {@link #STEERING_WHEEL_LIGHTS_SWITCH} (e.g. {@link #STEERING_WHEEL_LIGHTS_SWITCH}={@code
     * VehicleLightSwitch#STATE_AUTOMATIC} and {@code STEERING_WHEEL_LIGHTS_STATE}={@code
     * VehicleLightState#STATE_ON}).
     *
     * <p>This property will only be implemented if {@code STEERING_WHEEL_LIGHTS_STATE}'s value may
     * be different from that of {@link #CABIN_LIGHTS_STATE}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} obtained from {@link
     * android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum values from
     * {@code VehicleLightState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.READ_CAR_INTERIOR_LIGHTS" to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS))
    public static final int STEERING_WHEEL_LIGHTS_STATE = 289410828;

    /**
     * Steering wheel lights switch.
     *
     * <p>Returns the position of the switch controlling the steering wheel lights. This is
     * different from {@link #STEERING_WHEEL_LIGHTS_STATE} which represents the current state of the
     * steering wheel lights. Therefore, {@code STEERING_WHEEL_LIGHTS_SWITCH} may not match the
     * value of {@link #STEERING_WHEEL_LIGHTS_STATE} (e.g. {@code STEERING_WHEEL_LIGHTS_SWITCH}=
     * {@code VehicleLightSwitch#STATE_AUTOMATIC} and {@link #STEERING_WHEEL_LIGHTS_STATE}={@code
     * VehicleLightState#STATE_ON}).
     *
     * <p>This property will only be implemented if {@code STEERING_WHEEL_LIGHTS_SWITCH}'s value may
     * be different from that of {@link #CABIN_LIGHTS_SWITCH}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} obtained from {@link
     * android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum values from
     * {@code VehicleLightSwitch} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
    public static final int STEERING_WHEEL_LIGHTS_SWITCH = 289410829;

    /**
     * Property to get the initial settings for multi-user management (such as initial user).
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int INITIAL_USER_INFO = 299896583;

    /**
     * Property to switch user for multi-user management.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int SWITCH_USER = 299896584;

    /**
     * Property to create a new user for multi-user management.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int CREATE_USER = 299896585;

    /**
     * Property to remove a new user for multi-user management.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int REMOVE_USER = 299896586;

    /**
     * Property to get / set the user authentication types associated with an Android user.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int USER_IDENTIFICATION_ASSOCIATION = 299896587;

    /**
     * Property for VHAL to apply power policy.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code String} property type
     * </ul>
     *
     * @hide
     */
    public static final int POWER_POLICY_REQ = 286265121;

    /**
     * Property for VHAL to set the default power policies per power status transition.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code String} property type
     * </ul>
     *
     * @hide
     */
    public static final int POWER_POLICY_GROUP_REQ = 286265122;

    /**
     * Property to report a new current power policy to VHAL.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code String} property type
     * </ul>
     *
     * @hide
     */
    public static final int CURRENT_POWER_POLICY = 286265123;

    /**
     * Property to report that car watchdog is alive.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Long} property type
     * </ul>
     *
     * @hide
     */
    public static final int WATCHDOG_ALIVE = 290459441;

    /**
     * Property to report a process terminated by car watchdog.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int WATCHDOG_TERMINATED_PROCESS = 299896626;

    /**
     * Property to signal a heartbeat from VHAL.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Long} property type
     * </ul>
     *
     * @hide
     */
    public static final int VHAL_HEARTBEAT = 290459443;

    /**
     * Property to start the ClusterUI in cluster display.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_SWITCH_UI = 289410868;

    /**
     * Property to change the state of the cluster display.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_DISPLAY_STATE = 289476405;

    /**
     * Property to reports the current display and ClusterUI statue.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_REPORT_STATE = 299896630;

    /**
     * Property to request to change the cluster display state to show some ClusterUI.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_REQUEST_DISPLAY = 289410871;

    /**
     * Property to inform the current navigation state.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code bytes[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_NAVIGATION_STATE = 292556600;

    /**
     * Property to send the heartbeat signal to ClusterOS.
     *
     * <p>Doesn't require permission because it's not exposed through
     * {@link android.car.hardware.property.CarPropertyManager}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Object[]} property type
     * </ul>
     *
     * @hide
     */
    public static final int CLUSTER_HEARTBEAT = 299896651;

    /**
     * Current date and time, encoded as Unix time.
     *
     * <p>This value denotes the number of milliseconds that have elapsed since 1/1/1970 UTC.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Long} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Property is not readable.
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EPOCH_TIME" to write
     *  property.
     * </ul>
     */
    @RequiresPermission(Car.PERMISSION_CAR_EPOCH_TIME)
    public static final int EPOCH_TIME = 290457094;

    /**
     * Electronic Toll Collection card type.
     *
     * <p>This property indicates the type of ETC(Electronic Toll Collection) card in the vehicle.
     * If the head unit is aware of an ETC card attached to the vehicle, this property should return
     * the type of card attached; otherwise, this property should be UNAVAILABLE. The property value
     * should be one of {@link VehicleElectronicTollCollectionCardType}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleElectronicTollCollectionCardType}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int ELECTRONIC_TOLL_COLLECTION_CARD_TYPE = 289410873;

    /**
     * Electronic Toll Collection card status.
     *
     * <p>This property indicates the status of ETC(Electronic Toll Collection) card in the vehicle.
     * If the head unit is aware of an ETC card attached to the vehicle, ETC_CARD_STATUS gives that
     * status of the card; otherwise, this property should be UNAVAILABLE. The property value should
     * be one of {@link VehicleElectronicTollCollectionCardStatus}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleElectronicTollCollectionCardStatus}
     */
    @RequiresPermission(Car.PERMISSION_CAR_INFO)
    public static final int ELECTRONIC_TOLL_COLLECTION_CARD_STATUS = 289410874;

    /**
     * Front fog lights state.
     *
     * <p>Please refer to the documentation on {@link #FOG_LIGHTS_STATE} for more information.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int FRONT_FOG_LIGHTS_STATE = 289410875;

    /**
     * Front fog lights switch.
     *
     * <p>Please refer to the documentation on {@link #FOG_LIGHTS_SWITCH} for more information.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int FRONT_FOG_LIGHTS_SWITCH = 289410876;

    /**
     * Rear fog lights state.
     *
     * <p>Please refer to the documentation on {@link #FOG_LIGHTS_STATE} for more information.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_EXTERIOR_LIGHTS" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link VehicleLightState}
     */
    @RequiresPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
    public static final int REAR_FOG_LIGHTS_STATE = 289410877;

    /**
     * Rear fog lights switch.
     *
     * <p>Please refer to the documentation on {@link #FOG_LIGHTS_SWITCH} for more information.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS" to
     *  read and write property.
     * </ul>
     *
     * @data_enum {@link VehicleLightSwitch}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
    public static final int REAR_FOG_LIGHTS_SWITCH = 289410878;

    /**
     * EV charge current draw limit.
     *
     * <p>Indicates the maximum current draw threshold for charging set by the user. {@code
     * configArray[0]} contains the max current draw allowed by the vehicle in Amperes.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_ENERGY" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY" to write
     *  property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY,
            Car.PERMISSION_CONTROL_CAR_ENERGY}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_CAR_ENERGY))
    public static final int EV_CHARGE_CURRENT_DRAW_LIMIT = 291508031;

    /**
     * EV charge percent limit.
     *
     * <p>Indicates the maximum charge percent threshold set by the user. Returns a float value
     * from 0 to 100.
     *
     * <p>configArray is optional. If it is populated, it represents the valid charge percent limit
     * values for the vehicle. Here is an example configArray:
     * <ul>
     *  <li>configArray[0] = 20
     *  <li>configArray[1] = 40
     *  <li>configArray[2] = 60
     *  <li>configArray[3] = 80
     * </ul>
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} or Signature|Privileged permission
     *  "android.car.permission.CONTROL_CAR_ENERGY" to read property.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY" to write
     *  property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY,
            Car.PERMISSION_CONTROL_CAR_ENERGY}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_CAR_ENERGY))
    public static final int EV_CHARGE_PERCENT_LIMIT = 291508032;

    /**
     * Charging state of the car.
     *
     * <p>Returns the current charging state of the car. See
     * {@link android.car.hardware.property.EvChargeState} for possible values for
     * {@code EV_CHARGE_STATE}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link EvChargeState}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_ENERGY))
    public static final int EV_CHARGE_STATE = 289410881;

    /**
     * Start or stop charging the EV battery.
     *
     * <p>The setting that the user wants. Setting this property to true starts the battery charging
     * and setting to false stops charging.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY" or
     *  dangerous permission {@link Car#PERMISSION_ENERGY} to read.
     *  <li>Signature|Privileged permission "android.car.permission.CONTROL_CAR_ENERGY" to write
     *  property.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_ENERGY,
            Car.PERMISSION_CONTROL_CAR_ENERGY}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_CAR_ENERGY))
    public static final int EV_CHARGE_SWITCH = 287313730;

    /**
     * Estimated charge time remaining in seconds.
     *
     * <p>Returns 0 if the vehicle is not charging.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_ENERGY))
    public static final int EV_CHARGE_TIME_REMAINING = 289410883;

    /**
     * Regenerative braking or one-pedal drive state of the car.
     *
     * <p>Returns the current state associated with the regenerative braking
     * setting in the car. See
     * {@link android.car.hardware.property.EvRegenerativeBrakingState} for possible values for
     * {@code EV_REGENERATIVE_BRAKING_STATE}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Dangerous permission {@link Car#PERMISSION_ENERGY} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link EvRegenerativeBrakingState}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_ENERGY))
    public static final int EV_REGENERATIVE_BRAKING_STATE = 289410884;

    /**
     * Vehicle’s curb weight.
     *
     * <p>Returns the vehicle's curb weight in kilograms. configArray[0] specifies the vehicle’s
     * gross weight in kilograms.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.PRIVILEGED_CAR_INFO" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO))
    public static final int VEHICLE_CURB_WEIGHT = 289410886;

     /**
     * Indicates if there is a trailer present or not.
     *
     * <p>Returns the trailer state of the car. See {@code TrailerState} for possible values for
     * {@code TRAILER_PRESENT}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.PRIVILEGED_CAR_INFO" to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link TrailerState}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO))
    public static final int TRAILER_PRESENT = 289410885;

    /**
     * EU's General security regulation compliance requirement.
     *
     * <p>Returns whether general security regulation compliance is required, if
     * so, what type of requirement. See {@link GsrComplianceType} for possible enums.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Normal permission {@link Car#PERMISSION_CAR_INFO} to read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link GsrComplianceType}
     */
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_CAR_INFO))
    public static final int GENERAL_SAFETY_REGULATION_COMPLIANCE = 289410887;

    /**
     * Current state of vehicle autonomy.
     *
     * <p>Defines the level of autonomy currently engaged in the vehicle from the SAE standard
     * levels 0-5, with 0 representing no autonomy and 5 representing full driving automation. These
     * levels are defined in accordance with the standards set in the
     * <a href="https://www.sae.org/standards/content/j3016_202104/">J3016_202104 revision</a> of
     * the SAE automation level taxonomy and its clarification for international audiences
     * <a href="https://www.sae.org/blog/sae-j3016-update">here</a>.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.VehicleAutonomousState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission "android.car.permission.CAR_DRIVING_STATE" to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.VehicleAutonomousState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_CAR_DRIVING_STATE))
    public static final int VEHICLE_DRIVING_AUTOMATION_CURRENT_LEVEL = 289410892;

    /**
     * Enable or disable Automatic Emergency Braking (AEB).
     *
     * <p>Returns true if AEB is enabled and false if AEB is disabled. When AEB is enabled, the ADAS
     * system in the vehicle should be turned on and monitoring to avoid potential collisions. This
     * property applies for higher speed applications only. For enabling low speed automatic
     * emergency braking, {@link LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_ENABLED} will be used.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int AUTOMATIC_EMERGENCY_BRAKING_ENABLED = 287313920;

    /**
     * Automatic Emergency Braking (AEB) state.
     *
     * <p>Returns the current state of AEB. This property will always return a valid state defined
     * in {@link android.car.hardware.property.AutomaticEmergencyBrakingState} or {@link
     * android.car.hardware.property.ErrorState}. This property should apply for higher speed
     * applications only. For representing the state of the low speed automatic emergency braking
     * system, {@link LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE} should be used.
     *
     * <p>If AEB includes forward collision warnings before activating the brakes, those warnings
     * will be surfaced through the Forward Collision Warning (FCW) properties.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.AutomaticEmergencyBrakingState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link AutomaticEmergencyBrakingState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int AUTOMATIC_EMERGENCY_BRAKING_STATE = 289411073;

    /**
     * Enable or disable Forward Collision Warning (FCW).
     *
     * <p>Returns true if FCW is enabled and false if FCW is disabled. When FCW is enabled, the ADAS
     * system in the vehicle should be turned on and monitoring for potential collisions.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int FORWARD_COLLISION_WARNING_ENABLED = 287313922;

    /**
     * Forward Collision Warning State (FCW) state.
     *
     * <p>Returns the current state of FCW. This property will always return a valid state defined
     * in {@link android.car.hardware.property.ForwardCollisionWarningState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.ForwardCollisionWarningState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link ForwardCollisionWarningState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int FORWARD_COLLISION_WARNING_STATE = 289411075;

    /**
     * Enable and disable Blind Spot Warning (BSW).
     *
     * <p>Returns true if BSW is enabled and false if BSW is disabled. When BSW is enabled, the ADAS
     * system in the vehicle should be turned on and monitoring for objects in the vehicle’s blind
     * spots.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int BLIND_SPOT_WARNING_ENABLED = 287313924;

    /**
     * Blind Spot Warning (BSW) state.
     *
     * <p>Returns the current state of BSW. This property will always return a valid state defined
     * in {@link android.car.hardware.property.BlindSpotWarningState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.BlindSpotWarningState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link BlindSpotWarningState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int BLIND_SPOT_WARNING_STATE = 339742725;

    /**
     * Enable or disable Lane Departure Warning (LDW).
     *
     * <p>Returns true if LDW is enabled and false if LDW is disabled. When LDW is enabled, the ADAS
     * system in the vehicle should be turned on and monitoring if the vehicle is approaching or
     * crossing lane lines, in which case a warning will be given.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int LANE_DEPARTURE_WARNING_ENABLED = 287313926;

    /**
     * Lane Departure Warning (LDW) state.
     *
     * <p>Returns the current state of LDW. This property will always return a valid state defined
     * in {@link android.car.hardware.property.LaneDepartureWarningState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.LaneDepartureWarningState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link LaneDepartureWarningState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int LANE_DEPARTURE_WARNING_STATE = 289411079;

    /**
     * Enable or disable Lane Keep Assist (LKA).
     *
     * <p>Returns true if LKA is enabled and false if LKA is disabled. When LKA is enabled, the ADAS
     * system in the vehicle should be turned on and monitoring if the driver unintentionally drifts
     * toward or over the lane marking. If an unintentional lane departure is detected, the system
     * applies steering control to return the vehicle into the current lane.
     *
     * <p>This is different from Lane Centering Assist (LCA) which, when activated, applies
     * continuous steering control to keep the vehicle centered in the current lane.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int LANE_KEEP_ASSIST_ENABLED = 287313928;

    /**
     * Lane Keep Assist (LKA) state.
     *
     * <p>Returns the current state of LKA. This property will always return a valid state defined
     * in {@link android.car.hardware.property.LaneKeepAssistState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If LKA includes lane departure warnings before applying steering corrections, those
     * warnings will be surfaced through {@link #LANE_DEPARTURE_WARNING_STATE}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.LaneKeepAssistState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link LaneKeepAssistState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int LANE_KEEP_ASSIST_STATE = 289411081;

    /**
     * Enable or disable Lane Centering Assist (LCA).
     *
     * <p>Returns true if LCA is enabled and false if LCA is disabled. When LCA is enabled, the ADAS
     * system in the vehicle should be turned on and waiting for an activation signal from the
     * driver. Once the feature is activated, the ADAS system should be steering the vehicle to keep
     * it centered in its current lane.
     *
     * <p>This is different from Lane Keep Assist (LKA) which monitors if the driver unintentionally
     * drifts toward or over the lane marking. If an unintentional lane departure is detected, the
     * system applies steering control to return the vehicle into the current lane.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int LANE_CENTERING_ASSIST_ENABLED = 287313930;

    /**
     * Lane Centering Assist (LCA) commands.
     *
     * <p>Commands to activate and suspend LCA. They are only valid when {@link
     * #LANE_CENTERING_ASSIST_ENABLED} = {@code true}. Otherwise, these commands will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>When the command {@link android.car.hardware.property.LaneCenteringAssistCommand#ACTIVATE}
     * is sent, {@link #LANE_CENTERING_ASSIST_STATE} will be set to {@link
     * android.car.hardware.property.LaneCenteringAssistState#ACTIVATION_REQUESTED}. When the
     * command {@link android.car.hardware.property.LaneCenteringAssistCommand#ACTIVATE} succeeds,
     * {@link #LANE_CENTERING_ASSIST_STATE} will be set to {@link
     * android.car.hardware.property.LaneCenteringAssistState#ACTIVATED}. When the command {@link
     * android.car.hardware.property.LaneCenteringAssistCommand#DEACTIVATE} succeeds, {@link
     * #LANE_CENTERING_ASSIST_STATE} will be set to {@link
     * android.car.hardware.property.LaneCenteringAssistState#ENABLED}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which enum
     * values from {@link android.car.hardware.property.LaneCenteringAssistCommand} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Property is not readable.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to write
     *  property.
     * </ul>
     *
     * @data_enum {@link LaneCenteringAssistCommand}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_STATES))
    public static final int LANE_CENTERING_ASSIST_COMMAND = 289411083;

    /**
     * Lane Centering Assist (LCA) state.
     *
     * <p>Returns the current state of LCA. This property will always return a valid state defined
     * in {@link android.car.hardware.property.LaneCenteringAssistState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If LCA includes lane departure warnings, those warnings will be surfaced through the Lane
     * Departure Warning (LDW) properties.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.LaneCenteringAssistState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link LaneCenteringAssistState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int LANE_CENTERING_ASSIST_STATE = 289411084;

    /**
     * Enable or disable Emergency Lane Keep Assist (ELKA).
     *
     * <p>Return true if ELKA is enabled and false if ELKA is disabled. When ELKA is enabled, the
     * ADAS system in the vehicle should be on and monitoring for unsafe lane changes by the driver.
     * When an unsafe maneuver is detected, ELKA alerts the driver and applies steering corrections
     * to keep the vehicle in its original lane.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int EMERGENCY_LANE_KEEP_ASSIST_ENABLED = 287313933;

    /**
     * Emergency Lane Keep Assist (ELKA) state.
     *
     * <p>Returns the current state of ELKA. Generally, this property should return a valid state
     * defined in the {@link android.car.hardware.property.EmergencyLaneKeepAssistState} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.EmergencyLaneKeepAssistState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link EmergencyLaneKeepAssistState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int EMERGENCY_LANE_KEEP_ASSIST_STATE = 289411086;

    /**
     * Enable or disable Cruise Control (CC).
     *
     * <p>Return true if CC is enabled and false if CC is disabled. This property is shared by all
     * forms of {@link android.car.hardware.property.CruiseControlType}).
     *
     * <p>When CC is enabled, the ADAS system in the vehicle should be turned on and responding to
     * commands.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int CRUISE_CONTROL_ENABLED = 287313935;

    /**
     * Current type of Cruise Control (CC).
     *
     * <p>When {@link #CRUISE_CONTROL_ENABLED} is true, this property returns the type of CC that is
     * currently enabled (for example, standard CC, adaptive CC, etc.). Generally, this property
     * should return a valid state defined in the {@link
     * android.car.hardware.property.CruiseControlType} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.CruiseControlType} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * Trying to write {@link android.car.hardware.property.CruiseControlType#OTHER} or an
     * {@link android.car.hardware.property.ErrorState} to this property will throw an {@code
     * IllegalArgumentException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to write
     *  property.
     * </ul>
     *
     * @data_enum {@link CruiseControlType}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_STATES,
            Car.PERMISSION_CONTROL_ADAS_STATES}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_STATES))
    public static final int CRUISE_CONTROL_TYPE = 289411088;

    /**
     * Current state of Cruise Control (CC).
     *
     * <p>This property returns the state of CC. Generally, this property should return a valid
     * state defined in the {@link android.car.hardware.property.CruiseControlState} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.CruiseControlState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link CruiseControlState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int CRUISE_CONTROL_STATE = 289411089;

    /**
     * Write Cruise Control (CC) commands.
     *
     * <p>See {@link android.car.hardware.property.CruiseControlCommand} for the details about
     * each supported command.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.CruiseControlCommand} are supported.
     *
     * <p>When this property is unavailable (for example when {@link #CRUISE_CONTROL_ENABLED} is
     * false), writing this property will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Property is not readable.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to write
     *  property.
     * </ul>
     *
     * @data_enum {@link CruiseControlCommand}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_STATES))
    public static final int CRUISE_CONTROL_COMMAND = 289411090;

    /**
     * Current target speed for Cruise Control (CC) in meters per second.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} and {@link
     * android.car.hardware.property.AreaIdConfig#getMaxValue()} return the min and max target
     * speed values respectively. These values will be non-negative.
     *
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMinValue()} represents the lower
     * bound of the target speed.
     * <p>{@link android.car.hardware.property.AreaIdConfig#getMaxValue()} represents the upper
     * bound of the target speed.
     *
     * <p>When this property is unavailable (for example when {@link #CRUISE_CONTROL_ENABLED} is
     * false), reading this property will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Float} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int CRUISE_CONTROL_TARGET_SPEED = 291508243;

    /**
     * Current target time gap for Adaptive Cruise Control (ACC) or Predictive Cruise Control in
     * milliseconds.
     *
     * <p>This property should specify the target time gap to a leading vehicle. This gap is defined
     * as the time to travel the distance between the leading vehicle's rear-most point to the ACC
     * vehicle's front-most point. The actual time gap from a leading vehicle can be above or below
     * this value.
     *
     * <p>The possible values to set for the target time gap should be specified in {@code
     * configArray} in ascending order. All values must be positive. If the property is writable,
     * all values must be writable.
     *
     * <p>When this property is unavailable (for example when {@link #CRUISE_CONTROL_ENABLED} is
     * false), reading or writing this property will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_STATES} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_STATES,
            Car.PERMISSION_CONTROL_ADAS_STATES}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_STATES))
    public static final int ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP = 289411092;

    /**
     * Measured distance from leading vehicle when using Adaptive Cruise Control (ACC) or Predictive
     * Cruise Control in millimeters.
     *
     * <p>Returns the measured distance in meters from the lead vehicle for ACC between the
     * rear-most point of the leading vehicle and the front-most point of the ACC vehicle.
     *
     * <p>{@link CarPropertyConfig#getMinValue(int)} returns 0.
     * <p>{@link CarPropertyConfig#getMaxValue(int)} returns the maximum range the distance sensor
     * can support. This value will be non-negative.
     *
     * <p>When no lead vehicle is detected (that is, when there is no leading vehicle or the leading
     * vehicle is too far away for the sensor to detect), this property will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>When this property is unavailable (for example when {@link #CRUISE_CONTROL_ENABLED} is
     * false), reading this property will throw a {@link
     * android.car.hardware.property.PropertyNotAvailableException}.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE = 289411093;

    /**
     * Enable or disable Hands On Detection (HOD).
     *
     * <p>Return true if HOD is enabled and false if HOD is disabled. When HOD is enabled, a system
     * inside the vehicle should be monitoring the presence of the driver's hands on the steering
     * wheel and send a warning if it detects that the driver's hands are no longer on the steering
     * wheel.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_SETTINGS}
     *  or Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to read property.
     *  <li>Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to write property.
     * </ul>
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {
            Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
    public static final int HANDS_ON_DETECTION_ENABLED = 287313942;

    /**
     * Hands On Detection (HOD) driver state.
     *
     * <p>Returns whether the driver's hands are on the steering wheel. Generally, this property
     * should return a valid state defined in the {@link
     * android.car.hardware.property.HandsOnDetectionDriverState} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If the vehicle is sending a warning to the user because the driver's hands have been off
     * the steering wheel for too long, the warning should be surfaced through
     * {@link #HANDS_ON_DETECTION_WARNING}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.HandsOnDetectionDriverState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link HandsOnDetectionDriverState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int HANDS_ON_DETECTION_DRIVER_STATE = 289411095;
    /**
     * Hands On Detection (HOD) warning.
     *
     * <p>Returns whether a warning is being sent to the driver for having their hands off the wheel
     * for too long a duration.
     *
     * <p>Generally, this property should return a valid state defined in the {@link
     * android.car.hardware.property.HandsOnDetectionWarning} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through an {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.HandsOnDetectionWarning} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link HandsOnDetectionWarning}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int HANDS_ON_DETECTION_WARNING = 289411096;

    /**
     * Enable or disable driver drowsiness and attention monitoring.
     *
     * <p>Set true to enable driver drowsiness and attention monitoring and false to disable driver
     * drowsiness and attention monitoring. When driver drowsiness and attention monitoring is
     * enabled, a system inside the vehicle will monitor the drowsiness and attention level of the
     * driver and warn the driver if needed.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_SETTINGS}
     *  or Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to read property.
     *  <li>Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to write property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {
            Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
    public static final int DRIVER_DROWSINESS_ATTENTION_SYSTEM_ENABLED = 287313945;

    /**
     * Driver drowsiness and attention level state.
     *
     * <p>Returns the current detected state of driver drowiness and attention level based on the
     * Karolinska Sleepiness Scale (KSS).
     *
     * <p>Generally, this property should return a valid state defined in the {@link
     * android.car.hardware.property.DriverDrowsinessAttentionState} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If the vehicle is sending a warning to the user because the driver is too drowsy, the
     * warning should be surfaced through {@link #DRIVER_DROWSINESS_ATTENTION_WARNING}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.DriverDrowsinessAttentionState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link DriverDrowsinessAttentionState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int DRIVER_DROWSINESS_ATTENTION_STATE = 289411098;

    /**
     * Enable or disable driver drowsiness and attention warnings.
     *
     * <p>Set true to enable driver drowsiness and attention warnings and false to disable driver
     * drowsiness and attention warnings. When driver drowsiness and attention warnings are enabled,
     * the driver drowsiness and attention monitoring system inside the vehicle should warn the
     * driver when it detects the driver is drowsy or not attentive.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_SETTINGS}
     *  or Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to read property.
     *  <li>Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to write property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {
            Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
    public static final int DRIVER_DROWSINESS_ATTENTION_WARNING_ENABLED = 287313947;

    /**
     * Driver drowsiness and attention warning.
     *
     * <p>Returns whether a warning is being sent to the driver for being drowsy or not attentive.
     *
     * <p>Generally, this property should return a valid state defined in the {@link
     * android.car.hardware.property.DriverDrowsinessAttentionWarning} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through an {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.DriverDrowsinessAttentionWarning} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link DriverDrowsinessAttentionWarning}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int DRIVER_DROWSINESS_ATTENTION_WARNING = 289411100;

   /**
     * Enable or disable driver distraction monitoring.
     *
     * <p>Set true to enable driver distraction monitoring and false to disable driver distraction
     * monitoring. When driver distraction monitoring is enabled, a system inside the vehicle should
     * be monitoring the distraction level of the driver and warn the driver if needed.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_SETTINGS}
     *  or Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to read property.
     *  <li>Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to write property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {
            Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
    public static final int DRIVER_DISTRACTION_SYSTEM_ENABLED = 287313949;

    /**
     * Driver distraction state.
     *
     * <p>Returns the current detected driver distraction state.
     *
     * <p>Generally, this property should return a valid state defined in the {@link
     * android.car.hardware.property.DriverDistractionState} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If the vehicle is sending a warning to the user because the driver is too distracted, the
     * warning should be surfaced through {@link #DRIVER_DISTRACTION_WARNING}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.DriverDistractionState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link DriverDistractionState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int DRIVER_DISTRACTION_STATE = 289411102;

    /**
     * Enable or disable driver distraction warnings.
     *
     * <p>Set true to enable driver distraction warnings and false to disable driver distraction
     * warnings. When driver distraction warnings are enabled, the driver distraction monitoring
     * system inside the vehicle should warn the driver when it detects the driver is distracted.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_SETTINGS}
     *  or Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to read property.
     *  <li>Signature|Privileged permission {@link
     *  Car#PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS} to write property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {
            Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(
            Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
    public static final int DRIVER_DISTRACTION_WARNING_ENABLED = 287313951;

    /**
     * Driver distraction warning.
     *
     * <p>Returns whether a warning is being sent to the driver for being distracted.
     *
     * <p>Generally, this property should return a valid state defined in the {@link
     * android.car.hardware.property.DriverDistractionWarning} or {@link
     * android.car.hardware.property.ErrorState}. For example, if the feature is not available due
     * to some temporary state, that information should be conveyed through an {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.DriverDistractionWarning} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_DRIVER_MONITORING_STATES} to
     *  read property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link DriverDistractionWarning}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES))
    public static final int DRIVER_DISTRACTION_WARNING = 289411104;
    /**
     * Enable or disable Low Speed Collision Warning.
     *
     * <p>Returns true if low speed collision warning is enabled and false if low speed collision
     * warning is disabled. When low speed collision warning is enabled, the ADAS system in the
     * vehicle will warn the driver of potential collisions at low speeds. This property is
     * different from the pre-existing {@link VehiclePropertyIds#FORWARD_COLLISION_WARNING_ENABLED},
     * which applies to higher speed applications only. If the vehicle doesn't have a separate
     * collision detection system for low speed environments, this property will not be implemented.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int LOW_SPEED_COLLISION_WARNING_ENABLED = 287313953;

    /**
     * Low Speed Collision Warning State state.
     *
     * <p>Returns the current state of Low Speed Collision Warning. This property will always return
     * a valid state defined in {@link android.car.hardware.property.LowSpeedCollisionWarningState}
     * or {@link android.car.hardware.property.ErrorState}. This property is different from the
     * pre-existing {@link VehiclePropertyIds#FORWARD_COLLISION_WARNING_ENABLED}, which applies to
     * higher speed applications only. If the vehicle doesn't have a separate collision detection
     * system for low speed environments, this property will not be implemented.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.LowSpeedCollisionWarningState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.LowSpeedCollisionWarningState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int LOW_SPEED_COLLISION_WARNING_STATE = 289411106;

    /**
     * Enable or disable Cross Traffic Monitoring.
     *
     * <p>Returns true if Cross Traffic Monitoring is enabled and false if Cross Traffic Monitoring
     * is disabled. When Cross Traffic Monitoring is enabled, the ADAS system in the vehicle should
     * be turned on and monitoring for potential sideways collisions.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int CROSS_TRAFFIC_MONITORING_ENABLED = 287313955;

    /**
     * Cross Traffic Monitoring Warning state.
     *
     * <p>Returns the current state of Cross Traffic Monitoring Warning. This property will always
     * return a valid state defined in {@link
     * android.car.hardware.property.CrossTrafficMonitoringWarningState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.CrossTrafficMonitoringWarningState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.CrossTrafficMonitoringWarningState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int CROSS_TRAFFIC_MONITORING_WARNING_STATE = 289411108;

    /**
     * Enable or disable Low Speed Automatic Emergency Braking.
     *
     * <p>Returns true if Low Speed Automatic Emergency Braking is enabled or false if Low Speed
     * Automatic Emergency Braking is disabled. When Low Speed Automatic Emergency Braking is
     * enabled, the ADAS system in the vehicle will be turned on and monitoring to avoid potential
     * collisions in low speed conditions. This property is different from the pre-existing
     * AUTOMATIC_EMERGENCY_BRAKING_ENABLED, which should apply to higher speed applications only. If
     * the vehicle doesn't have a separate collision avoidance system for low speed environments,
     * this property will not be implemented.
     *
     * <p>This property is defined as read_write, but OEMs have the option to implement it as read
     * only.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE} or
     *  {@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Boolean} property type
     * </ul>
     *
     * <p>Required Permissions:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_SETTINGS} or
     *  Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to read
     *  property.
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_CONTROL_ADAS_SETTINGS} to write
     *  property.
     * </ul>
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(anyOf = {Car.PERMISSION_READ_ADAS_SETTINGS,
            Car.PERMISSION_CONTROL_ADAS_SETTINGS}))
    @RequiresPermission.Write(@RequiresPermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS))
    public static final int LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_ENABLED = 287313957;

    /**
     * Low Speed Automatic Emergency Braking state.
     *
     * <p>Returns the current state of Low Speed Automatic Emergency Braking. This property will
     * always return a valid state defined in {@link
     * android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState} or {@link
     * android.car.hardware.property.ErrorState}.
     *
     * <p>If Low Speed Automatic Emergency Braking includes collision warnings before activating the
     * brakes, those warnings will be surfaced through use of {@link
     * android.car.VehiclePropertyIds#LOW_SPEED_COLLISION_WARNING_ENABLED} and {@link
     * android.car.VehiclePropertyIds#LOW_SPEED_COLLISION_WARNING_STATE}.
     *
     * <p>For the global area ID (0), the {@link
     * android.car.hardware.property.AreaIdConfig#getSupportedEnumValues()} array obtained from
     * {@link android.car.hardware.CarPropertyConfig#getAreaIdConfig(int)} specifies which states
     * from {@link android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState} and {@link
     * android.car.hardware.property.ErrorState} are supported.
     *
     * <p>Property Config:
     * <ul>
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}
     *  <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}
     *  <li>{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}
     *  <li>{@code Integer} property type
     * </ul>
     *
     * <p>Required Permission:
     * <ul>
     *  <li>Signature|Privileged permission {@link Car#PERMISSION_READ_ADAS_STATES} to read
     *  property.
     *  <li>Property is not writable.
     * </ul>
     *
     * @data_enum {@link android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState}
     * @data_enum {@link ErrorState}
     *
     * @hide
     */
    @FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    @SystemApi
    @RequiresPermission.Read(@RequiresPermission(Car.PERMISSION_READ_ADAS_STATES))
    public static final int LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE = 289411110;

    /**
     * @deprecated to prevent others from instantiating this class
     */
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public VehiclePropertyIds() {
    }

    /**
     * Gets a user-friendly representation of a property.
     */
    public static String toString(int propertyId) {
        return isDefined(propertyId) ? toName(propertyId) : "0x" + Integer.toHexString(propertyId);
    }
}
