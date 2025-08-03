#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_VEHICLE_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_VEHICLE_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class VehiclePropertyType : int32_t;
enum class VehicleArea : int32_t;
enum class VehiclePropertyGroup : int32_t;
enum class VehicleProperty : int32_t;
enum class VehicleVendorPermission : int32_t;
enum class VehicleSeatOccupancyState : int32_t;
enum class VehicleLightState : int32_t;
enum class VehicleLightSwitch : int32_t;
enum class EvConnectorType : int32_t;
enum class PortLocationType : int32_t;
enum class FuelType : int32_t;
enum class VehicleHvacFanDirection : int32_t;
enum class VehicleOilLevel : int32_t;
enum class VehicleApPowerStateConfigFlag : int32_t;
enum class VehicleApPowerStateReq : int32_t;
enum class VehicleApPowerStateReqIndex : int32_t;
enum class VehicleApPowerStateShutdownParam : int32_t;
enum class VehicleApPowerStateReport : int32_t;
enum class VehicleHwKeyInputAction : int32_t;
enum class VehicleDisplay : int32_t;
enum class VehicleUnit : int32_t;
enum class VehiclePropertyChangeMode : int32_t;
enum class VehiclePropertyAccess : int32_t;
enum class VehiclePropertyStatus : int32_t;
enum class VehicleGear : int32_t;
enum class VehicleAreaSeat : int32_t;
enum class VehicleAreaWindow : int32_t;
enum class VehicleAreaDoor : int32_t;
enum class VehicleAreaMirror : int32_t;
enum class VehicleTurnSignal : int32_t;
struct VehicleAreaConfig;
struct VehiclePropConfig;
struct VehiclePropValue;
enum class VehicleIgnitionState : int32_t;
enum class SubscribeFlags : int32_t;
struct SubscribeOptions;
enum class StatusCode : int32_t;
enum class VehicleAreaWheel : int32_t;
enum class Obd2FuelSystemStatus : int32_t;
enum class Obd2IgnitionMonitorKind : int32_t;
enum class Obd2CommonIgnitionMonitors : int32_t;
enum class Obd2SparkIgnitionMonitors : int32_t;
enum class Obd2CompressionIgnitionMonitors : int32_t;
enum class Obd2SecondaryAirStatus : int32_t;
enum class Obd2FuelType : int32_t;
enum class DiagnosticIntegerSensorIndex : int32_t;
enum class DiagnosticFloatSensorIndex : int32_t;
enum class VmsMessageType : int32_t;
enum class VmsBaseMessageIntegerValuesIndex : int32_t;
enum class VmsStartSessionMessageIntegerValuesIndex : int32_t;
enum class VmsMessageWithLayerIntegerValuesIndex : int32_t;
enum class VmsMessageWithLayerAndPublisherIdIntegerValuesIndex : int32_t;
enum class VmsOfferingMessageIntegerValuesIndex : int32_t;
enum class VmsSubscriptionsStateIntegerValuesIndex : int32_t;
enum class VmsAvailabilityStateIntegerValuesIndex : int32_t;
enum class VmsPublisherInformationIntegerValuesIndex : int32_t;
enum class UserFlags : int32_t;
struct UserInfo;
struct UsersInfo;
enum class InitialUserInfoRequestType : int32_t;
struct InitialUserInfoRequest;
enum class InitialUserInfoResponseAction : int32_t;
struct InitialUserInfoResponse;
enum class SwitchUserMessageType : int32_t;
struct SwitchUserRequest;
enum class SwitchUserStatus : int32_t;
struct SwitchUserResponse;
struct CreateUserRequest;
enum class CreateUserStatus : int32_t;
struct CreateUserResponse;
struct RemoveUserRequest;
enum class UserIdentificationAssociationType : int32_t;
enum class UserIdentificationAssociationValue : int32_t;
enum class UserIdentificationAssociationSetValue : int32_t;
struct UserIdentificationGetRequest;
struct UserIdentificationSetRequest;
struct UserIdentificationResponse;
struct UserIdentificationAssociation;
struct UserIdentificationSetAssociation;
enum class RotaryInputType : int32_t;

// Order of inner types was changed for forward reference support.

/**
 * Enumerates supported data type for VehicleProperty.
 *
 * Used to create property ID in VehicleProperty enum.
 */
enum class VehiclePropertyType : int32_t {
    STRING = 1048576 /* 0x00100000 */,
    BOOLEAN = 2097152 /* 0x00200000 */,
    INT32 = 4194304 /* 0x00400000 */,
    INT32_VEC = 4259840 /* 0x00410000 */,
    INT64 = 5242880 /* 0x00500000 */,
    INT64_VEC = 5308416 /* 0x00510000 */,
    FLOAT = 6291456 /* 0x00600000 */,
    FLOAT_VEC = 6356992 /* 0x00610000 */,
    BYTES = 7340032 /* 0x00700000 */,
    /**
     * Any combination of scalar or vector types. The exact format must be
     * provided in the description of the property.
     *
     * For vendor MIXED type properties, configArray needs to be formatted in this
     * structure.
     * configArray[0], 1 indicates the property has a String value
     * configArray[1], 1 indicates the property has a Boolean value .
     * configArray[2], 1 indicates the property has an Integer value.
     * configArray[3], the number indicates the size of Integer[] in the property.
     * configArray[4], 1 indicates the property has a Long value.
     * configArray[5], the number indicates the size of Long[] in the property.
     * configArray[6], 1 indicates the property has a Float value.
     * configArray[7], the number indicates the size of Float[] in the property.
     * configArray[8], the number indicates the size of byte[] in the property.
     * For example:
     * {@code configArray = {1, 1, 1, 3, 0, 0, 0, 0, 0}} indicates the property has
     * a String value, a Boolean value, an Integer value and an array with 3 integers.
     */
    MIXED = 14680064 /* 0x00e00000 */,
    MASK = 16711680 /* 0x00ff0000 */,
};

/**
 * Vehicle Areas
 * Used to construct property IDs in the VehicleProperty enum.
 *
 * Some properties may be associated with particular vehicle areas. For
 * example, VehicleProperty:DOOR_LOCK property must be associated with
 * particular door, thus this property must be marked with
 * VehicleArea:DOOR flag.
 *
 * Other properties may not be associated with particular vehicle area.
 * These kinds of properties must have VehicleArea:GLOBAL flag.
 *
 * [Definition] Area: An area represents a unique element of an AreaType.
 *   For instance, if AreaType is WINDOW, then an area may be FRONT_WINDSHIELD.
 *
 * [Definition] AreaID: An AreaID is a combination of one or more areas,
 *   and is represented using a bitmask of Area enums. Different AreaTypes may
 *   not be mixed in a single AreaID. For instance, a window area cannot be
 *   combined with a seat area in an AreaID.
 *
 * Rules for mapping a zoned property to AreaIDs:
 *  - A property must be mapped to an array of AreaIDs that are impacted when
 *    the property value changes.
 *  - Each element in the array must represent an AreaID, in which the
 *    property value can only be changed together in all the areas within
 *    the AreaID and never independently. That is, when the property value
 *    changes in one of the areas in an AreaID in the array, then it must
 *    automatically change in all other areas in the AreaID.
 *  - The property value must be independently controllable in any two
 *    different AreaIDs in the array.
 *  - An area must only appear once in the array of AreaIDs. That is, an
 *    area must only be part of a single AreaID in the array.
 *
 * [Definition] Global Property: A property that applies to the entire car
 *   and is not associated with a specific area. For example, FUEL_LEVEL,
 *   HVAC_STEERING_WHEEL_HEAT.
 *
 * Rules for mapping a global property to AreaIDs:
 *  - A global property must not be mapped to AreaIDs.
 */
enum class VehicleArea : int32_t {
    GLOBAL = 16777216 /* 0x01000000 */,
    /**
     * WINDOW maps to enum VehicleAreaWindow
     */
    WINDOW = 50331648 /* 0x03000000 */,
    /**
     * MIRROR maps to enum VehicleAreaMirror
     */
    MIRROR = 67108864 /* 0x04000000 */,
    /**
     * SEAT maps to enum VehicleAreaSeat
     */
    SEAT = 83886080 /* 0x05000000 */,
    /**
     * DOOR maps to enum VehicleAreaDoor
     */
    DOOR = 100663296 /* 0x06000000 */,
    /**
     * WHEEL maps to enum VehicleAreaWheel
     */
    WHEEL = 117440512 /* 0x07000000 */,
    MASK = 251658240 /* 0x0f000000 */,
};

/**
 * Enumerates property groups.
 *
 * Used to create property ID in VehicleProperty enum.
 */
enum class VehiclePropertyGroup : int32_t {
    /**
     * Properties declared in AOSP must use this flag.
     */
    SYSTEM = 268435456 /* 0x10000000 */,
    /**
     * Properties declared by vendors must use this flag.
     */
    VENDOR = 536870912 /* 0x20000000 */,
    MASK = -268435456 /* 0xf0000000 */,
};

/**
 * Declares all vehicle properties. VehicleProperty has a bitwise structure.
 * Each property must have:
 *  - a unique id from range 0x0100 - 0xffff
 *  - associated data type using VehiclePropertyType
 *  - property group (VehiclePropertyGroup)
 *  - vehicle area (VehicleArea)
 *
 * Vendors are allowed to extend this enum with their own properties. In this
 * case they must use VehiclePropertyGroup:VENDOR flag when the property is
 * declared.
 *
 * When a property's status field is not set to AVAILABLE:
 *  - IVehicle#set may return StatusCode::NOT_AVAILABLE.
 *  - IVehicle#get is not guaranteed to work.
 *
 * Properties set to values out of range must be ignored and no action taken
 * in response to such ill formed requests.
 */
enum class VehicleProperty : int32_t {
    /**
     * Undefined property.
     */
    INVALID = 0 /* 0x00000000 */,
    /**
     * VIN of vehicle
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    INFO_VIN = 286261504 /* (0x0100 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:STRING | VehicleArea:GLOBAL) */,
    /**
     * Manufacturer of vehicle
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    INFO_MAKE = 286261505 /* (0x0101 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:STRING | VehicleArea:GLOBAL) */,
    /**
     * Model of vehicle
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    INFO_MODEL = 286261506 /* (0x0102 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:STRING | VehicleArea:GLOBAL) */,
    /**
     * Model year of vehicle.
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:YEAR
     */
    INFO_MODEL_YEAR = 289407235 /* (0x0103 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Fuel capacity of the vehicle in milliliters
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:MILLILITER
     */
    INFO_FUEL_CAPACITY = 291504388 /* (0x0104 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * List of fuels the vehicle may use
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @data_enum FuelType
     */
    INFO_FUEL_TYPE = 289472773 /* (0x0105 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Battery capacity of the vehicle, if EV or hybrid.  This is the nominal
     * battery capacity when the vehicle is new.
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:WH
     */
    INFO_EV_BATTERY_CAPACITY = 291504390 /* (0x0106 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * List of connectors this EV may use
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @data_enum EvConnectorType
     * @access VehiclePropertyAccess:READ
     */
    INFO_EV_CONNECTOR_TYPE = 289472775 /* (0x0107 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Fuel door location
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @data_enum PortLocationType
     * @access VehiclePropertyAccess:READ
     */
    INFO_FUEL_DOOR_LOCATION = 289407240 /* (0x0108 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * EV port location
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @data_enum PortLocationType
     */
    INFO_EV_PORT_LOCATION = 289407241 /* (0x0109 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Driver's seat location
     * VHAL implementations must ignore the areaId. Use VehicleArea:GLOBAL.
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @data_enum VehicleAreaSeat
     * @access VehiclePropertyAccess:READ
     */
    INFO_DRIVER_SEAT = 356516106 /* (0x010A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Exterior dimensions of vehicle.
     *
     *  int32Values[0] = height
     *  int32Values[1] = length
     *  int32Values[2] = width
     *  int32Values[3] = width including mirrors
     *  int32Values[4] = wheel base
     *  int32Values[5] = track width front
     *  int32Values[6] = track width rear
     *  int32Values[7] = curb to curb turning radius
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:MILLIMETER
     */
    INFO_EXTERIOR_DIMENSIONS = 289472779 /* (0x010B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Multiple EV port locations
     *
     * Implement this property if the vehicle has multiple EV ports.
     * Port locations are defined in PortLocationType.
     * For example, a car has one port in front left and one port in rear left:
     *   int32Values[0] = PortLocationType::FRONT_LEFT
     *   int32Values[0] = PortLocationType::REAR_LEFT
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @data_enum PortLocationType
     */
    INFO_MULTI_EV_PORT_LOCATIONS = 289472780 /* (0x010C | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Current odometer value of the vehicle
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:KILOMETER
     */
    PERF_ODOMETER = 291504644 /* (0x0204 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Speed of the vehicle
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:METER_PER_SEC
     */
    PERF_VEHICLE_SPEED = 291504647 /* (0x0207 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Speed of the vehicle for displays
     *
     * Some cars display a slightly slower speed than the actual speed.  This is
     * usually displayed on the speedometer.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:METER_PER_SEC
     */
    PERF_VEHICLE_SPEED_DISPLAY = 291504648 /* (0x0208 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Front bicycle model steering angle for vehicle
     *
     * Angle is in degrees.  Left is negative.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:DEGREES
     */
    PERF_STEERING_ANGLE = 291504649 /* (0x0209 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Rear bicycle model steering angle for vehicle
     *
     * Angle is in degrees.  Left is negative.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:DEGREES
     */
    PERF_REAR_STEERING_ANGLE = 291504656 /* (0x0210 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Temperature of engine coolant
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:CELSIUS
     */
    ENGINE_COOLANT_TEMP = 291504897 /* (0x0301 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Engine oil level
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleOilLevel
     */
    ENGINE_OIL_LEVEL = 289407747 /* (0x0303 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Temperature of engine oil
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:CELSIUS
     */
    ENGINE_OIL_TEMP = 291504900 /* (0x0304 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Engine rpm
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:RPM
     */
    ENGINE_RPM = 291504901 /* (0x0305 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Reports wheel ticks
     *
     * The first element in the vector is a reset count.  A reset indicates
     * previous tick counts are not comparable with this and future ones.  Some
     * sort of discontinuity in tick counting has occurred.
     *
     * The next four elements represent ticks for individual wheels in the
     * following order: front left, front right, rear right, rear left.  All
     * tick counts are cumulative.  Tick counts increment when the vehicle
     * moves forward, and decrement when vehicles moves in reverse.  The ticks
     * should be reset to 0 when the vehicle is started by the user.
     *
     *  int64Values[0] = reset count
     *  int64Values[1] = front left ticks
     *  int64Values[2] = front right ticks
     *  int64Values[3] = rear right ticks
     *  int64Values[4] = rear left ticks
     *
     * configArray is used to indicate the micrometers-per-wheel-tick value and
     * which wheels are supported.  configArray is set as follows:
     *
     *  configArray[0], bits [0:3] = supported wheels.  Uses enum Wheel.
     *  configArray[1] = micrometers per front left wheel tick
     *  configArray[2] = micrometers per front right wheel tick
     *  configArray[3] = micrometers per rear right wheel tick
     *  configArray[4] = micrometers per rear left wheel tick
     *
     * NOTE:  If a wheel is not supported, its value shall always be set to 0.
     *
     * VehiclePropValue.timestamp must be correctly filled in.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     */
    WHEEL_TICK = 290521862 /* (0x0306 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT64_VEC | VehicleArea:GLOBAL) */,
    /**
     * Fuel remaining in the the vehicle, in milliliters
     *
     * Value may not exceed INFO_FUEL_CAPACITY
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:MILLILITER
     */
    FUEL_LEVEL = 291504903 /* (0x0307 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Fuel door open
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    FUEL_DOOR_OPEN = 287310600 /* (0x0308 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * EV battery level in WH, if EV or hybrid
     *
     * Value may not exceed INFO_EV_BATTERY_CAPACITY
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:WH
     */
    EV_BATTERY_LEVEL = 291504905 /* (0x0309 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * EV charge port open
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    EV_CHARGE_PORT_OPEN = 287310602 /* (0x030A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * EV charge port connected
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    EV_CHARGE_PORT_CONNECTED = 287310603 /* (0x030B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * EV instantaneous charge rate in milliwatts
     *
     * Positive value indicates battery is being charged.
     * Negative value indicates battery being discharged.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:MW
     */
    EV_BATTERY_INSTANTANEOUS_CHARGE_RATE = 291504908 /* (0x030C | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Range remaining
     *
     * Meters remaining of fuel and charge.  Range remaining shall account for
     * all energy sources in a vehicle.  For example, a hybrid car's range will
     * be the sum of the ranges based on fuel and battery.
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ_WRITE
     * @unit VehicleUnit:METER
     */
    RANGE_REMAINING = 291504904 /* (0x0308 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Tire pressure
     *
     * Each tires is identified by its areaConfig.areaId config and their
     * minFloatValue/maxFloatValue are used to store OEM recommended pressure
     * range.
     * The Min value in the areaConfig data represents the lower bound of
     * the recommended tire pressure.
     * The Max value in the areaConfig data represents the upper bound of
     * the recommended tire pressure.
     * For example:
     * The following areaConfig indicates the recommended tire pressure
     * of left_front tire is from 200.0 KILOPASCAL to 240.0 KILOPASCAL.
     * .areaConfigs = {
     *      VehicleAreaConfig {
     *          .areaId = VehicleAreaWheel::LEFT_FRONT,
     *          .minFloatValue = 200.0,
     *          .maxFloatValue = 240.0,
     *      }
     * },
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:KILOPASCAL
     */
    TIRE_PRESSURE = 392168201 /* (0x0309 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:WHEEL) */,
    /**
     * Currently selected gear
     *
     * This is the gear selected by the user.
     *
     * Values in the config data must represent the list of supported gears
     * for this vehicle.  For example, config data for an automatic transmission
     * must contain {GEAR_NEUTRAL, GEAR_REVERSE, GEAR_PARK, GEAR_DRIVE,
     * GEAR_1, GEAR_2,...} and for manual transmission the list must be
     * {GEAR_NEUTRAL, GEAR_REVERSE, GEAR_1, GEAR_2,...}
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleGear
     */
    GEAR_SELECTION = 289408000 /* (0x0400 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Current gear. In non-manual case, selected gear may not
     * match the current gear. For example, if the selected gear is GEAR_DRIVE,
     * the current gear will be one of GEAR_1, GEAR_2 etc, which reflects
     * the actual gear the transmission is currently running in.
     *
     * Values in the config data must represent the list of supported gears
     * for this vehicle.  For example, config data for an automatic transmission
     * must contain {GEAR_NEUTRAL, GEAR_REVERSE, GEAR_PARK, GEAR_1, GEAR_2,...}
     * and for manual transmission the list must be
     * {GEAR_NEUTRAL, GEAR_REVERSE, GEAR_1, GEAR_2,...}. This list need not be the
     * same as that of the supported gears reported in GEAR_SELECTION.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleGear
     */
    CURRENT_GEAR = 289408001 /* (0x0401 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Parking brake state.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    PARKING_BRAKE_ON = 287310850 /* (0x0402 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Auto-apply parking brake.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    PARKING_BRAKE_AUTO_APPLY = 287310851 /* (0x0403 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Warning for fuel low level.
     *
     * This property corresponds to the low fuel warning on the dashboard.
     * Once FUEL_LEVEL_LOW is set, it should not be cleared until more fuel is
     * added to the vehicle.  This property may take into account all fuel
     * sources for a vehicle - for example:
     *
     *   For a gas powered vehicle, this property is based soley on gas level.
     *   For a battery powered vehicle, this property is based solely on battery level.
     *   For a hybrid vehicle, this property may be based on the combination of gas and battery
     *      levels, at the OEM's discretion.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    FUEL_LEVEL_LOW = 287310853 /* (0x0405 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Night mode
     *
     * True indicates that the night mode sensor has detected that the car cabin environment has
     * low light. The platform could use this, for example, to enable appropriate UI for
     * better viewing in dark or low light environments.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    NIGHT_MODE = 287310855 /* (0x0407 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * State of the vehicles turn signals
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleTurnSignal
     */
    TURN_SIGNAL_STATE = 289408008 /* (0x0408 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Represents ignition state
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleIgnitionState
     */
    IGNITION_STATE = 289408009 /* (0x0409 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * ABS is active
     *
     * Set to true when ABS is active.  Reset to false when ABS is off.  This
     * property may be intermittently set (pulsing) based on the real-time
     * state of the ABS system.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    ABS_ACTIVE = 287310858 /* (0x040A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Traction Control is active
     *
     * Set to true when traction control (TC) is active.  Reset to false when
     * TC is off.  This property may be intermittently set (pulsing) based on
     * the real-time state of the TC system.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    TRACTION_CONTROL_ACTIVE = 287310859 /* (0x040B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /*
     * HVAC Properties
     *
     * Additional rules for mapping a zoned HVAC property (except
     * HVAC_MAX_DEFROST_ON) to AreaIDs:
     *  - Every seat in VehicleAreaSeat that is available in the car, must be
     *    part of an AreaID in the AreaID array.
     *
     * Example 1: A car has two front seats (ROW_1_LEFT, ROW_1_RIGHT) and three
     *  back seats (ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT). There are two
     *  temperature control units -- driver side and passenger side.
     *   - A valid mapping set of AreaIDs for HVAC_TEMPERATURE_SET would be a
     *     two element array:
     *      - ROW_1_LEFT  | ROW_2_LEFT
     *      - ROW_1_RIGHT | ROW_2_CENTER | ROW_2_RIGHT
     *   - An alternative mapping for the same hardware configuration would be:
     *      - ROW_1_LEFT  | ROW_2_CENTER | ROW_2_LEFT
     *      - ROW_1_RIGHT | ROW_2_RIGHT
     *  The temperature controllers are assigned to the seats which they
     *  "most influence", but every seat must be included exactly once. The
     *  assignment of the center rear seat to the left or right AreaID may seem
     *  arbitrary, but the inclusion of every seat in exactly one AreaID ensures
     *  that the seats in the car are all expressed and that a "reasonable" way
     *  to affect each seat is available.
     *
     * Example 2: A car has three seat rows with two seats in the front row (ROW_1_LEFT,
     *  ROW_1_RIGHT) and three seats in the second (ROW_2_LEFT, ROW_2_CENTER,
     *  ROW_2_RIGHT) and third rows (ROW_3_LEFT, ROW_3_CENTER, ROW_3_RIGHT). There
     *  are three temperature control units -- driver side, passenger side, and rear.
     *   - A reasonable way to map HVAC_TEMPERATURE_SET to AreaIDs is a three
     *     element array:
     *     - ROW_1_LEFT
     *     - ROW_1_RIGHT
     *     - ROW_2_LEFT | ROW_2_CENTER | ROW_2_RIGHT | ROW_3_LEFT | ROW_3_CENTER | ROW_3_RIGHT
     *
     *
     * Fan speed setting
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_FAN_SPEED = 356517120 /* (0x0500 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Fan direction setting
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleHvacFanDirection
     */
    HVAC_FAN_DIRECTION = 356517121 /* (0x0501 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * HVAC current temperature.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:CELSIUS
     */
    HVAC_TEMPERATURE_CURRENT = 358614274 /* (0x0502 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:SEAT) */,
    /**
     * HVAC, target temperature set.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @unit VehicleUnit:CELSIUS
     */
    HVAC_TEMPERATURE_SET = 358614275 /* (0x0503 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:SEAT) */,
    /**
     * Fan-based defrost for designated window.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_DEFROSTER = 320865540 /* (0x0504 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:WINDOW) */,
    /**
     * On/off AC for designated areaId
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @config_flags Supported areaIds
     */
    HVAC_AC_ON = 354419973 /* (0x0505 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * On/off max AC
     *
     * When MAX AC is on, the ECU may adjust the vent position, fan speed,
     * temperature, etc as necessary to cool the vehicle as quickly as possible.
     * Any parameters modified as a side effect of turning on/off the MAX AC
     * parameter shall generate onPropertyEvent() callbacks to the VHAL.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_MAX_AC_ON = 354419974 /* (0x0506 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * On/off max defrost
     *
     * When MAX DEFROST is on, the ECU may adjust the vent position, fan speed,
     * temperature, etc as necessary to defrost the windows as quickly as
     * possible.  Any parameters modified as a side effect of turning on/off
     * the MAX DEFROST parameter shall generate onPropertyEvent() callbacks to
     * the VHAL.
     * The AreaIDs for HVAC_MAX_DEFROST_ON indicate MAX DEFROST can be controlled
     * in the area.
     * For example:
     * areaConfig.areaId = {ROW_1_LEFT | ROW_1_RIGHT} indicates HVAC_MAX_DEFROST_ON
     * only can be controlled for the front rows.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_MAX_DEFROST_ON = 354419975 /* (0x0507 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Recirculation on/off
     *
     * Controls the supply of exterior air to the cabin.  Recirc “on” means the
     * majority of the airflow into the cabin is originating in the cabin.
     * Recirc “off” means the majority of the airflow into the cabin is coming
     * from outside the car.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_RECIRC_ON = 354419976 /* (0x0508 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Enable temperature coupling between areas.
     *
     * The AreaIDs for HVAC_DUAL_ON property shall contain a combination of
     * HVAC_TEMPERATURE_SET AreaIDs that can be coupled together. If
     * HVAC_TEMPERATURE_SET is mapped to AreaIDs [a_1, a_2, ..., a_n], and if
     * HVAC_DUAL_ON can be enabled to couple a_i and a_j, then HVAC_DUAL_ON
     * property must be mapped to [a_i | a_j]. Further, if a_k and a_l can also
     * be coupled together separately then HVAC_DUAL_ON must be mapped to
     * [a_i | a_j, a_k | a_l].
     *
     * Example: A car has two front seats (ROW_1_LEFT, ROW_1_RIGHT) and three
     *  back seats (ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT). There are two
     *  temperature control units -- driver side and passenger side -- which can
     *  be optionally synchronized. This may be expressed in the AreaIDs this way:
     *  - HVAC_TEMPERATURE_SET->[ROW_1_LEFT | ROW_2_LEFT, ROW_1_RIGHT | ROW_2_CENTER | ROW_2_RIGHT]
     *  - HVAC_DUAL_ON->[ROW_1_LEFT | ROW_2_LEFT | ROW_1_RIGHT | ROW_2_CENTER | ROW_2_RIGHT]
     *
     * When the property is enabled, the ECU must synchronize the temperature
     * for the affected areas. Any parameters modified as a side effect
     * of turning on/off the DUAL_ON parameter shall generate
     * onPropertyEvent() callbacks to the VHAL. In addition, if setting
     * a temperature (i.e. driver's temperature) changes another temperature
     * (i.e. front passenger's temperature), then the appropriate
     * onPropertyEvent() callbacks must be generated.  If a user changes a
     * temperature that breaks the coupling (e.g. setting the passenger
     * temperature independently) then the VHAL must send the appropriate
     * onPropertyEvent() callbacks (i.e. HVAC_DUAL_ON = false,
     * HVAC_TEMPERATURE_SET[AreaID] = xxx, etc).
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_DUAL_ON = 354419977 /* (0x0509 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * On/off automatic mode
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_AUTO_ON = 354419978 /* (0x050A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Seat heating/cooling
     *
     * Negative values indicate cooling.
     * 0 indicates off.
     * Positive values indicate heating.
     *
     * Some vehicles may have multiple levels of heating and cooling. The
     * min/max range defines the allowable range and number of steps in each
     * direction.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_SEAT_TEMPERATURE = 356517131 /* (0x050B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Side Mirror Heat
     *
     * Increasing values denote higher heating levels for side mirrors.
     * The Max value in the config data represents the highest heating level.
     * The Min value in the config data MUST be zero and indicates no heating.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_SIDE_MIRROR_HEAT = 339739916 /* (0x050C | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:MIRROR) */,
    /**
     * Steering Wheel Heating/Cooling
     *
     * Sets the amount of heating/cooling for the steering wheel
     * config data Min and Max MUST be set appropriately.
     * Positive value indicates heating.
     * Negative value indicates cooling.
     * 0 indicates temperature control is off.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_STEERING_WHEEL_HEAT = 289408269 /* (0x050D | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Temperature units for display
     *
     * Indicates whether the vehicle is displaying temperature to the user as
     * Celsius or Fahrenheit.
     * VehiclePropConfig.configArray is used to indicate the supported temperature display units.
     * For example: configArray[0] = CELSIUS
     *              configArray[1] = FAHRENHEIT
     *
     * This parameter MAY be used for displaying any HVAC temperature in the system.
     * Values must be one of VehicleUnit::CELSIUS or VehicleUnit::FAHRENHEIT
     * Note that internally, all temperatures are represented in floating point Celsius.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleUnit
     */
    HVAC_TEMPERATURE_DISPLAY_UNITS = 289408270 /* (0x050E | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Actual fan speed
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    HVAC_ACTUAL_FAN_SPEED_RPM = 356517135 /* (0x050F | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Represents global power state for HVAC. Setting this property to false
     * MAY mark some properties that control individual HVAC features/subsystems
     * to UNAVAILABLE state. Setting this property to true MAY mark some
     * properties that control individual HVAC features/subsystems to AVAILABLE
     * state (unless any/all of them are UNAVAILABLE on their own individual
     * merits).
     *
     * [Definition] HvacPower_DependentProperties: Properties that need HVAC to be
     *   powered on in order to enable their functionality. For example, in some cars,
     *   in order to turn on the AC, HVAC must be powered on first.
     *
     * HvacPower_DependentProperties list must be set in the
     * VehiclePropConfig.configArray. HvacPower_DependentProperties must only contain
     * properties that are associated with VehicleArea:SEAT. Properties that are not
     * associated with VehicleArea:SEAT, for example, HVAC_DEFROSTER, must never
     * depend on HVAC_POWER_ON property and must never be part of
     * HvacPower_DependentProperties list.
     *
     * AreaID mapping for HVAC_POWER_ON property must contain all AreaIDs that
     * HvacPower_DependentProperties are mapped to.
     *
     * Example 1: A car has two front seats (ROW_1_LEFT, ROW_1_RIGHT) and three back
     *  seats (ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT). If the HVAC features (AC,
     *  Temperature etc.) throughout the car are dependent on a single HVAC power
     *  controller then HVAC_POWER_ON must be mapped to
     *  [ROW_1_LEFT | ROW_1_RIGHT | ROW_2_LEFT | ROW_2_CENTER | ROW_2_RIGHT].
     *
     * Example 2: A car has two seats in the front row (ROW_1_LEFT, ROW_1_RIGHT) and
     *   three seats in the second (ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT) and third
     *   rows (ROW_3_LEFT, ROW_3_CENTER, ROW_3_RIGHT). If the car has temperature
     *   controllers in the front row which can operate entirely independently of
     *   temperature controllers in the back of the vehicle, then HVAC_POWER_ON
     *   must be mapped to a two element array:
     *   - ROW_1_LEFT | ROW_1_RIGHT
     *   - ROW_2_LEFT | ROW_2_CENTER | ROW_2_RIGHT | ROW_3_LEFT | ROW_3_CENTER | ROW_3_RIGHT
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_POWER_ON = 354419984 /* (0x0510 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Fan Positions Available
     *
     * This is a bit mask of fan positions available for the zone.  Each
     * available fan direction is denoted by a separate entry in the vector.  A
     * fan direction may have multiple bits from vehicle_hvac_fan_direction set.
     * For instance, a typical car may have the following fan positions:
     *   - FAN_DIRECTION_FACE (0x1)
     *   - FAN_DIRECTION_FLOOR (0x2)
     *   - FAN_DIRECTION_FACE | FAN_DIRECTION_FLOOR (0x3)
     *   - FAN_DIRECTION_DEFROST (0x4)
     *   - FAN_DIRECTION_FLOOR | FAN_DIRECTION_DEFROST (0x6)
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleHvacFanDirection
     */
    HVAC_FAN_DIRECTION_AVAILABLE = 356582673 /* (0x0511 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:SEAT) */,
    /**
     * Automatic recirculation on/off
     *
     * When automatic recirculation is ON, the HVAC system may automatically
     * switch to recirculation mode if the vehicle detects poor incoming air
     * quality.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_AUTO_RECIRC_ON = 354419986 /* (0x0512 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Seat ventilation
     *
     * 0 indicates off.
     * Positive values indicates ventilation level.
     *
     * Used by HVAC apps and Assistant to enable, change, or read state of seat
     * ventilation.  This is different than seating cooling. It can be on at the
     * same time as cooling, or not.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_SEAT_VENTILATION = 356517139 /* (0x0513 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Electric defrosters' status
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    HVAC_ELECTRIC_DEFROSTER_ON = 320865556 /* (0x0514 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:WINDOW) */,
    /**
     * Distance units for display
     *
     * Indicates which units the car is using to display distances to the user. Eg. Mile, Meter
     * Kilometer.
     *
     * Distance units are defined in VehicleUnit.
     * VehiclePropConfig.configArray is used to indicate the supported distance display units.
     * For example: configArray[0] = METER
     *              configArray[1] = KILOMETER
     *              configArray[2] = MILE
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleUnit
     */
    DISTANCE_DISPLAY_UNITS = 289408512 /* (0x0600 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Fuel volume units for display
     *
     * Indicates which units the car is using to display fuel volume to the user. Eg. Liter or
     * Gallon.
     *
     * VehiclePropConfig.configArray is used to indicate the supported fuel volume display units.
     * Volume units are defined in VehicleUnit.
     * For example: configArray[0] = LITER
     *              configArray[1] = GALLON
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleUnit
     */
    FUEL_VOLUME_DISPLAY_UNITS = 289408513 /* (0x0601 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Tire pressure units for display
     *
     * Indicates which units the car is using to display tire pressure to the user. Eg. PSI, Bar or
     * Kilopascal.
     *
     * VehiclePropConfig.configArray is used to indicate the supported pressure display units.
     * Pressure units are defined in VehicleUnit.
     * For example: configArray[0] = KILOPASCAL
     *              configArray[1] = PSI
     *              configArray[2] = BAR
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleUnit
     */
    TIRE_PRESSURE_DISPLAY_UNITS = 289408514 /* (0x0602 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * EV battery units for display
     *
     * Indicates which units the car is using to display EV battery information to the user. Eg.
     * watt-hours(Wh), kilowatt-hours(kWh) or ampere-hours(Ah).
     *
     * VehiclePropConfig.configArray is used to indicate the supported electrical energy units.
     * Electrical energy units are defined in VehicleUnit.
     * For example: configArray[0] = WATT_HOUR
     *              configArray[1] = AMPERE_HOURS
     *              configArray[2] = KILOWATT_HOUR
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleUnit
     */
    EV_BATTERY_DISPLAY_UNITS = 289408515 /* (0x0603 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Fuel consumption units for display
     *
     * Indicates type of units the car is using to display fuel consumption information to user
     * True indicates units are distance over volume such as MPG.
     * False indicates units are volume over distance such as L/100KM.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME = 287311364 /* (0x0604 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Speed units for display
     *
     * Indicates type of units the car is using to display speed to user. Eg. m/s, km/h, or mph.
     *
     * VehiclePropConfig.configArray is used to indicate the supported speed display units.
     * Pressure units are defined in VehicleUnit.
     * For example: configArray[0] = METER_PER_SEC
     *              configArray[1] = MILES_PER_HOUR
     *              configArray[2] = KILOMETERS_PER_HOUR
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    VEHICLE_SPEED_DISPLAY_UNITS = 289408517 /* (0x0605 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Outside temperature
     *
     * @change_mode VehiclePropertyChangeMode:CONTINUOUS
     * @access VehiclePropertyAccess:READ
     * @unit VehicleUnit:CELSIUS
     */
    ENV_OUTSIDE_TEMPERATURE = 291505923 /* (0x0703 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:FLOAT | VehicleArea:GLOBAL) */,
    /**
     * Property to control power state of application processor
     *
     * It is assumed that AP's power state is controlled by a separate power
     * controller.
     *
     * For configuration information, VehiclePropConfig.configArray can have bit flag combining
     * values in VehicleApPowerStateConfigFlag.
     *
     *   int32Values[0] : VehicleApPowerStateReq enum value
     *   int32Values[1] : additional parameter relevant for each state,
     *                    0 if not used.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    AP_POWER_STATE_REQ = 289475072 /* (0x0A00 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Property to report power state of application processor
     *
     * It is assumed that AP's power state is controller by separate power
     * controller.
     *
     *   int32Values[0] : VehicleApPowerStateReport enum value
     *   int32Values[1] : Time in ms to wake up, if necessary.  Otherwise 0.
     *
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    AP_POWER_STATE_REPORT = 289475073 /* (0x0A01 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Property to report bootup reason for the current power on. This is a
     * static property that will not change for the whole duration until power
     * off. For example, even if user presses power on button after automatic
     * power on with door unlock, bootup reason must stay with
     * VehicleApPowerBootupReason#USER_UNLOCK.
     *
     * int32Values[0] must be VehicleApPowerBootupReason.
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    AP_POWER_BOOTUP_REASON = 289409538 /* (0x0A02 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Property to represent brightness of the display. Some cars have single
     * control for the brightness of all displays and this property is to share
     * change in that control.
     *
     * If this is writable, android side can set this value when user changes
     * display brightness from Settings. If this is read only, user may still
     * change display brightness from Settings, but that must not be reflected
     * to other displays.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    DISPLAY_BRIGHTNESS = 289409539 /* (0x0A03 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Property to feed H/W input events to android
     *
     * int32Values[0] : action defined by VehicleHwKeyInputAction
     * int32Values[1] : key code, must use standard android key code
     * int32Values[2] : target display defined in VehicleDisplay. Events not
     *                  tied to specific display must be sent to
     *                  VehicleDisplay#MAIN.
     * int32Values[3] : [optional] Number of ticks. The value must be equal or
     *                  greater than 1. When omitted, Android will default to 1.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @config_flags
     */
    HW_KEY_INPUT = 289475088 /* (0x0A10 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Property to feed H/W rotary events to android
     *
     * int32Values[0] : RotaryInputType identifying which rotary knob rotated
     * int32Values[1] : number of detents (clicks), positive for clockwise,
     *                  negative for counterclockwise
     * int32Values[2] : target display defined in VehicleDisplay. Events not
     *                  tied to specific display must be sent to
     *                  VehicleDisplay#MAIN.
     * int32values[3 .. 3 + abs(number of detents) - 2]:
     *                  nanosecond deltas between pairs of consecutive detents,
     *                  if the number of detents is > 1 or < -1
     *
     * VehiclePropValue.timestamp: when the rotation occurred. If the number of
     *                             detents is > 1 or < -1, this is when the
     *                             first detent of rotation occurred.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @data_enum RotaryInputType
     * @access VehiclePropertyAccess:READ
     */
    HW_ROTARY_INPUT = 289475104 /* (0x0A20 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32_VEC | VehicleArea:GLOBAL) */,
    /**
     * Most Car Cabin properties have both a POSition and MOVE parameter.  These
     * are used to control the various movements for seats, doors, and windows
     * in a vehicle.
     *
     * A POS parameter allows the user to set the absolution position.  For
     * instance, for a door, 0 indicates fully closed and max value indicates
     * fully open.  Thus, a value halfway between min and max must indicate
     * the door is halfway open.
     *
     * A MOVE parameter moves the device in a particular direction.  The sign
     * indicates direction, and the magnitude indicates speed (if multiple
     * speeds are available).  For a door, a move of -1 will close the door, and
     * a move of +1 will open it.  Once a door reaches the limit of open/close,
     * the door should automatically stop moving.  The user must NOT need to
     * send a MOVE(0) command to stop the door at the end of its range.
     *
     *
     * Door position
     *
     * This is an integer in case a door may be set to a particular position.
     * Max value indicates fully open, min value (0) indicates fully closed.
     *
     * Some vehicles (minivans) can open the door electronically.  Hence, the
     * ability to write this property.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    DOOR_POS = 373295872 /* (0x0B00 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:DOOR) */,
    /**
     * Door move
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    DOOR_MOVE = 373295873 /* (0x0B01 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:DOOR) */,
    /**
     * Door lock
     *
     * 'true' indicates door is locked
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    DOOR_LOCK = 371198722 /* (0x0B02 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:DOOR) */,
    /**
     * Mirror Z Position
     *
     * Positive value indicates tilt upwards, negative value is downwards
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_Z_POS = 339741504 /* (0x0B40 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:MIRROR) */,
    /**
     * Mirror Z Move
     *
     * Positive value indicates tilt upwards, negative value is downwards
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_Z_MOVE = 339741505 /* (0x0B41 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:MIRROR) */,
    /**
     * Mirror Y Position
     *
     * Positive value indicate tilt right, negative value is left
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_Y_POS = 339741506 /* (0x0B42 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:MIRROR) */,
    /**
     * Mirror Y Move
     *
     * Positive value indicate tilt right, negative value is left
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_Y_MOVE = 339741507 /* (0x0B43 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:MIRROR) */,
    /**
     * Mirror Lock
     *
     * True indicates mirror positions are locked and not changeable
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_LOCK = 287312708 /* (0x0B44 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Mirror Fold
     *
     * True indicates mirrors are folded
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    MIRROR_FOLD = 287312709 /* (0x0B45 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Seat memory select
     *
     * This parameter selects the memory preset to use to select the seat
     * position. The minValue is always 0, and the maxValue determines the
     * number of seat positions available.
     *
     * For instance, if the driver's seat has 3 memory presets, the maxValue
     * will be 3. When the user wants to select a preset, the desired preset
     * number (1, 2, or 3) is set.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:WRITE
     */
    SEAT_MEMORY_SELECT = 356518784 /* (0x0B80 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat memory set
     *
     * This setting allows the user to save the current seat position settings
     * into the selected preset slot.  The maxValue for each seat position
     * must match the maxValue for SEAT_MEMORY_SELECT.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:WRITE
     */
    SEAT_MEMORY_SET = 356518785 /* (0x0B81 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seatbelt buckled
     *
     * True indicates belt is buckled.
     *
     * Write access indicates automatic seat buckling capabilities.  There are
     * no known cars at this time, but you never know...
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BELT_BUCKLED = 354421634 /* (0x0B82 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:SEAT) */,
    /**
     * Seatbelt height position
     *
     * Adjusts the shoulder belt anchor point.
     * Max value indicates highest position
     * Min value indicates lowest position
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BELT_HEIGHT_POS = 356518787 /* (0x0B83 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seatbelt height move
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BELT_HEIGHT_MOVE = 356518788 /* (0x0B84 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat fore/aft position
     *
     * Sets the seat position forward (closer to steering wheel) and backwards.
     * Max value indicates closest to wheel, min value indicates most rearward
     * position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_FORE_AFT_POS = 356518789 /* (0x0B85 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat fore/aft move
     *
     * Moves the seat position forward and aft.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_FORE_AFT_MOVE = 356518790 /* (0x0B86 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat backrest angle 1 position
     *
     * Backrest angle 1 is the actuator closest to the bottom of the seat.
     * Max value indicates angling forward towards the steering wheel.
     * Min value indicates full recline.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BACKREST_ANGLE_1_POS = 356518791 /* (0x0B87 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat backrest angle 1 move
     *
     * Moves the backrest forward or recline.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BACKREST_ANGLE_1_MOVE = 356518792 /* (0x0B88 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat backrest angle 2 position
     *
     * Backrest angle 2 is the next actuator up from the bottom of the seat.
     * Max value indicates angling forward towards the steering wheel.
     * Min value indicates full recline.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BACKREST_ANGLE_2_POS = 356518793 /* (0x0B89 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat backrest angle 2 move
     *
     * Moves the backrest forward or recline.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_BACKREST_ANGLE_2_MOVE = 356518794 /* (0x0B8A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat height position
     *
     * Sets the seat height.
     * Max value indicates highest position.
     * Min value indicates lowest position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEIGHT_POS = 356518795 /* (0x0B8B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat height move
     *
     * Moves the seat height.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEIGHT_MOVE = 356518796 /* (0x0B8C | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat depth position
     *
     * Sets the seat depth, distance from back rest to front edge of seat.
     * Max value indicates longest depth position.
     * Min value indicates shortest position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_DEPTH_POS = 356518797 /* (0x0B8D | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat depth move
     *
     * Adjusts the seat depth.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_DEPTH_MOVE = 356518798 /* (0x0B8E | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat tilt position
     *
     * Sets the seat tilt.
     * Max value indicates front edge of seat higher than back edge.
     * Min value indicates front edge of seat lower than back edge.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_TILT_POS = 356518799 /* (0x0B8F | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat tilt move
     *
     * Tilts the seat.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_TILT_MOVE = 356518800 /* (0x0B90 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Lumber fore/aft position
     *
     * Pushes the lumbar support forward and backwards
     * Max value indicates most forward position.
     * Min value indicates most rearward position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_LUMBAR_FORE_AFT_POS = 356518801 /* (0x0B91 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Lumbar fore/aft move
     *
     * Adjusts the lumbar support.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_LUMBAR_FORE_AFT_MOVE = 356518802 /* (0x0B92 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Lumbar side support position
     *
     * Sets the amount of lateral lumbar support.
     * Max value indicates widest lumbar setting (i.e. least support)
     * Min value indicates thinnest lumbar setting.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_LUMBAR_SIDE_SUPPORT_POS = 356518803 /* (0x0B93 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Lumbar side support move
     *
     * Adjusts the amount of lateral lumbar support.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_LUMBAR_SIDE_SUPPORT_MOVE = 356518804 /* (0x0B94 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Headrest height position
     *
     * Sets the headrest height.
     * Max value indicates tallest setting.
     * Min value indicates shortest setting.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_HEIGHT_POS = 289409941 /* (0x0B95 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Headrest height move
     *
     * Moves the headrest up and down.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_HEIGHT_MOVE = 356518806 /* (0x0B96 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Headrest angle position
     *
     * Sets the angle of the headrest.
     * Max value indicates most upright angle.
     * Min value indicates shallowest headrest angle.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_ANGLE_POS = 356518807 /* (0x0B97 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Headrest angle move
     *
     * Adjusts the angle of the headrest
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_ANGLE_MOVE = 356518808 /* (0x0B98 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Headrest fore/aft position
     *
     * Adjusts the headrest forwards and backwards.
     * Max value indicates position closest to front of car.
     * Min value indicates position closest to rear of car.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_FORE_AFT_POS = 356518809 /* (0x0B99 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Headrest fore/aft move
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SEAT_HEADREST_FORE_AFT_MOVE = 356518810 /* (0x0B9A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Seat Occupancy
     *
     * Indicates whether a particular seat is occupied or not, to the best of the car's ability
     * to determine. Valid values are from the VehicleSeatOccupancyState enum.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleSeatOccupancyState
     */
    SEAT_OCCUPANCY = 356518832 /* (0x0BB0 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Window Position
     *
     * Min = window up / closed
     * Max = window down / open
     *
     * For a window that may open out of plane (i.e. vent mode of sunroof) this
     * parameter will work with negative values as follows:
     *  Max = sunroof completely open
     *  0 = sunroof closed.
     *  Min = sunroof vent completely open
     *
     *  Note that in this mode, 0 indicates the window is closed.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    WINDOW_POS = 322964416 /* (0x0BC0 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:WINDOW) */,
    /**
     * Window Move
     *
     * Max = Open the window as fast as possible
     * Min = Close the window as fast as possible
     * Magnitude denotes relative speed.  I.e. +2 is faster than +1 in closing
     * the window.
     *
     * For a window that may open out of plane (i.e. vent mode of sunroof) this
     * parameter will work as follows:
     *
     * If sunroof is open:
     *   Max = open the sunroof further, automatically stop when fully open.
     *   Min = close the sunroof, automatically stop when sunroof is closed.
     *
     * If vent is open:
     *   Max = close the vent, automatically stop when vent is closed.
     *   Min = open the vent further, automatically stop when vent is fully open.
     *
     * If sunroof is in the closed position:
     *   Max = open the sunroof, automatically stop when sunroof is fully open.
     *   Min = open the vent, automatically stop when vent is fully open.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    WINDOW_MOVE = 322964417 /* (0x0BC1 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:WINDOW) */,
    /**
     * Window Lock
     *
     * True indicates windows are locked and can't be moved.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    WINDOW_LOCK = 320867268 /* (0x0BC4 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:WINDOW) */,
    /**
     * Vehicle Maps Service (VMS) message
     *
     * This property uses MIXED data to communicate vms messages.
     *
     * Its contents are to be interpreted as follows:
     * the indices defined in VmsMessageIntegerValuesIndex are to be used to
     * read from int32Values;
     * bytes is a serialized VMS message as defined in the vms protocol
     * which is opaque to the framework;
     *
     * IVehicle#get must always return StatusCode::NOT_AVAILABLE.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    VEHICLE_MAP_SERVICE = 299895808 /* (0x0C00 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * OBD2 Live Sensor Data
     *
     * Reports a snapshot of the current (live) values of the OBD2 sensors available.
     *
     * The configArray is set as follows:
     *   configArray[0] = number of vendor-specific integer-valued sensors
     *   configArray[1] = number of vendor-specific float-valued sensors
     *
     * The values of this property are to be interpreted as in the following example.
     * Considering a configArray = {2,3}
     * int32Values must be a vector containing Obd2IntegerSensorIndex.LAST_SYSTEM_INDEX + 2
     * elements (that is, 33 elements);
     * floatValues must be a vector containing Obd2FloatSensorIndex.LAST_SYSTEM_INDEX + 3
     * elements (that is, 73 elements);
     *
     * It is possible for each frame to contain a different subset of sensor values, both system
     * provided sensors, and vendor-specific ones. In order to support that, the bytes element
     * of the property value is used as a bitmask,.
     *
     * bytes must have a sufficient number of bytes to represent the total number of possible
     * sensors (in this case, 14 bytes to represent 106 possible values); it is to be read as
     * a contiguous bitmask such that each bit indicates the presence or absence of a sensor
     * from the frame, starting with as many bits as the size of int32Values, immediately
     * followed by as many bits as the size of floatValues.
     *
     * For example, should bytes[0] = 0x4C (0b01001100) it would mean that:
     *   int32Values[0 and 1] are not valid sensor values
     *   int32Values[2 and 3] are valid sensor values
     *   int32Values[4 and 5] are not valid sensor values
     *   int32Values[6] is a valid sensor value
     *   int32Values[7] is not a valid sensor value
     * Should bytes[5] = 0x61 (0b01100001) it would mean that:
     *   int32Values[32] is a valid sensor value
     *   floatValues[0 thru 3] are not valid sensor values
     *   floatValues[4 and 5] are valid sensor values
     *   floatValues[6] is not a valid sensor value
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    OBD2_LIVE_FRAME = 299896064 /* (0x0D00 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * OBD2 Freeze Frame Sensor Data
     *
     * Reports a snapshot of the value of the OBD2 sensors available at the time that a fault
     * occurred and was detected.
     *
     * A configArray must be provided with the same meaning as defined for OBD2_LIVE_FRAME.
     *
     * The values of this property are to be interpreted in a similar fashion as those for
     * OBD2_LIVE_FRAME, with the exception that the stringValue field may contain a non-empty
     * diagnostic troubleshooting code (DTC).
     *
     * A IVehicle#get request of this property must provide a value for int64Values[0].
     * This will be interpreted as the timestamp of the freeze frame to retrieve. A list of
     * timestamps can be obtained by a IVehicle#get of OBD2_FREEZE_FRAME_INFO.
     *
     * Should no freeze frame be available at the given timestamp, a response of NOT_AVAILABLE
     * must be returned by the implementation. Because vehicles may have limited storage for
     * freeze frames, it is possible for a frame request to respond with NOT_AVAILABLE even if
     * the associated timestamp has been recently obtained via OBD2_FREEZE_FRAME_INFO.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    OBD2_FREEZE_FRAME = 299896065 /* (0x0D01 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * OBD2 Freeze Frame Information
     *
     * This property describes the current freeze frames stored in vehicle
     * memory and available for retrieval via OBD2_FREEZE_FRAME.
     *
     * The values are to be interpreted as follows:
     * each element of int64Values must be the timestamp at which a a fault code
     * has been detected and the corresponding freeze frame stored, and each
     * such element can be used as the key to OBD2_FREEZE_FRAME to retrieve
     * the corresponding freeze frame.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     */
    OBD2_FREEZE_FRAME_INFO = 299896066 /* (0x0D02 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * OBD2 Freeze Frame Clear
     *
     * This property allows deletion of any of the freeze frames stored in
     * vehicle memory, as described by OBD2_FREEZE_FRAME_INFO.
     *
     * The configArray is set as follows:
     *  configArray[0] = 1 if the implementation is able to clear individual freeze frames
     *                   by timestamp, 0 otherwise
     *
     * IVehicle#set of this property is to be interpreted as follows:
     *   if int64Values contains no elements, then all frames stored must be cleared;
     *   if int64Values contains one or more elements, then frames at the timestamps
     *   stored in int64Values must be cleared, and the others not cleared. Should the
     *   vehicle not support selective clearing of freeze frames, this latter mode must
     *   return NOT_AVAILABLE.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:WRITE
     */
    OBD2_FREEZE_FRAME_CLEAR = 299896067 /* (0x0D03 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * Headlights State
     *
     * Return the current state of headlights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    HEADLIGHTS_STATE = 289410560 /* (0x0E00 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * High beam lights state
     *
     * Return the current state of high beam lights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    HIGH_BEAM_LIGHTS_STATE = 289410561 /* (0x0E01 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Fog light state
     *
     * Return the current state of fog lights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    FOG_LIGHTS_STATE = 289410562 /* (0x0E02 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Hazard light status
     *
     * Return the current status of hazard lights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    HAZARD_LIGHTS_STATE = 289410563 /* (0x0E03 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Headlight switch
     *
     * The setting that the user wants.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    HEADLIGHTS_SWITCH = 289410576 /* (0x0E10 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * High beam light switch
     *
     * The setting that the user wants.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    HIGH_BEAM_LIGHTS_SWITCH = 289410577 /* (0x0E11 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Fog light switch
     *
     * The setting that the user wants.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    FOG_LIGHTS_SWITCH = 289410578 /* (0x0E12 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Hazard light switch
     *
     * The setting that the user wants.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    HAZARD_LIGHTS_SWITCH = 289410579 /* (0x0E13 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Cabin lights
     *
     * Return current status of cabin lights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    CABIN_LIGHTS_STATE = 289410817 /* (0x0F01 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Cabin lights switch
     *
     * The position of the physical switch which controls the cabin lights.
     * This might be different than the CABIN_LIGHTS_STATE if the lights are on because a door
     * is open or because of a voice command.
     * For example, while the switch is in the "off" or "automatic" position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    CABIN_LIGHTS_SWITCH = 289410818 /* (0x0F02 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:GLOBAL) */,
    /**
     * Reading lights
     *
     * Return current status of reading lights.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ
     * @data_enum VehicleLightState
     */
    READING_LIGHTS_STATE = 356519683 /* (0x0F03 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Reading lights switch
     *
     * The position of the physical switch which controls the reading lights.
     * This might be different than the READING_LIGHTS_STATE if the lights are on because a door
     * is open or because of a voice command.
     * For example, while the switch is in the "off" or "automatic" position.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     * @data_enum VehicleLightSwitch
     */
    READING_LIGHTS_SWITCH = 356519684 /* (0x0F04 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:INT32 | VehicleArea:SEAT) */,
    /**
     * Support customize permissions for vendor properties
     *
     * Implement this property if vehicle hal support customize vendor permissions feature.
     * VehiclePropConfig.configArray is used to indicate vendor properties and permissions
     * which selected for this vendor property. The permission must be one of enum in
     * VehicleVendorPermission.
     * The configArray is set as follows:
     *      configArray[n] = propId : property ID for the vendor property
     *      configArray[n+1] = one of enums in VehicleVendorPermission. It indicates the permission
     *      for reading value of the property.
     *      configArray[n+2] = one of enums in VehicleVendorPermission. It indicates the permission
     *      for writing value of the property.
     *
     * For example:
     * configArray = {
     *      vendor_prop_1, PERMISSION_VENDOR_SEAT_READ, PERMISSION_VENDOR_SEAT_WRITE,
     *      vendor_prop_2, PERMISSION_VENDOR_INFO, PERMISSION_NOT_ACCESSIBLE,
     * }
     * If vendor properties are not in this array, they will have the default vendor permission.
     * If vendor chose PERMISSION_NOT_ACCESSIBLE, android will not have access to the property. In
     * the example, Android can not write value for vendor_prop_2.
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    SUPPORT_CUSTOMIZE_VENDOR_PERMISSION = 287313669 /* (0x0F05 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:BOOLEAN | VehicleArea:GLOBAL) */,
    /**
     * Allow disabling optional featurs from vhal.
     *
     * This property reports optional features that should be disabled.
     * All allowed optional features for the system is declared in Car service overlay,
     * config_allowed_optional_car_features.
     * This property allows disabling features defined in the overlay. Without this property,
     * all the features declared in the overlay will be enabled.
     *
     * Value read should include all features disabled with ',' separation.
     * ex) "com.android.car.user.CarUserNoticeService,storage_monitoring"
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:READ
     */
    DISABLED_OPTIONAL_FEATURES = 286265094 /* (0x0F06 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:STRING | VehicleArea:GLOBAL) */,
    /**
     * Defines the initial Android user to be used during initialization.
     *
     * This property is called by the Android system when it initializes and it lets the HAL
     * define which Android user should be started.
     *
     * This request is made by setting a VehiclePropValue (defined by InitialUserInfoRequest),
     * and the HAL must respond with a property change event (defined by InitialUserInfoResponse).
     * If the HAL doesn't respond after some time (defined by the Android system), the Android
     * system will proceed as if HAL returned a response of action
     * InitialUserInfoResponseAction:DEFAULT.
     *
     * For example, on first boot, the request could be:
     *
     * int32[0]: 42  // request id (arbitrary number set by Android system)
     * int32[1]: 1   // InitialUserInfoRequestType::FIRST_BOOT
     * int32[2]: 0   // id of current user (usersInfo.currentUser.userId)
     * int32[3]: 1   // flag of current user (usersInfo.currentUser.flags = SYSTEM)
     * int32[4]: 1   // number of existing users (usersInfo.numberUsers);
     * int32[5]: 0   // user #0  (usersInfo.existingUsers[0].userId)
     * int32[6]: 1   // flags of user #0  (usersInfo.existingUsers[0].flags)
     *
     * And if the HAL want to respond with the creation of an admin user called "Owner", the
     * response would be:
     *
     * int32[0]: 42      // must match the request id from the request
     * int32[1]:  2      // action = InitialUserInfoResponseAction::CREATE
     * int32[2]: -10000  // userToSwitchOrCreate.userId (not used as user will be created)
     * int32[3]:  8      // userToSwitchOrCreate.flags = ADMIN
     * string: "||Owner" // userLocales + separator + userNameToCreate
     *
     * Notice the string value represents multiple values, separated by ||. The first value is the
     * (optional) system locales for the user to be created (in this case, it's empty, meaning it
     * will use Android's default value), while the second value is the (also optional) name of the
     * to user to be created (when the type of response is InitialUserInfoResponseAction:CREATE).
     * For example, to create the same "Owner" user with "en-US" and "pt-BR" locales, the string
     * value of the response would be "en-US,pt-BR||Owner". As such, neither the locale nor the
     * name can have || on it, although a single | is fine.
     *
     * NOTE: if the HAL doesn't support user management, then it should not define this property,
     * which in turn would disable the other user-related properties (for example, the Android
     * system would never issue them and user-related requests from the HAL layer would be ignored
     * by the Android System). But if it supports user management, then it must support all core
     * user-related properties (INITIAL_USER_INFO, SWITCH_USER, CREATE_USER, and REMOVE_USER).
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    INITIAL_USER_INFO = 299896583 /* (0x0F07 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * Defines a request to switch the foreground Android user.
     *
     * This property is used primarily by the Android System to inform the HAL that the
     * current foreground Android user is switching, but it could also be used by the HAL to request
     * the Android system to switch users - the
     *
     * When the request is made by Android, it sets a VehiclePropValue and the HAL must responde
     * with a property change event; when the HAL is making the request, it must also do it through
     * a property change event (the main difference is that the request id will be positive in the
     * former case, and negative in the latter; the SwitchUserMessageType will also be different).
     *
     * The format of both request is defined by SwitchUserRequest and the format of the response
     * (when needed) is defined by SwitchUserResponse. How the HAL (or Android System) should
     * proceed depends on the message type (which is defined by the SwitchUserMessageType
     * parameter), as defined below.
     *
     * 1.LEGACY_ANDROID_SWITCH
     * -----------------------
     *
     * Called by the Android System to indicate the Android user is about to change, when the change
     * request was made in a way that is not integrated with the HAL (for example, through
     * adb shell am switch-user).
     *
     * The HAL can switch its internal user once it receives this request, but it doesn't need to
     * reply back to the Android System. If its internal user cannot be changed for some reason,
     * then it must wait for the SWITCH_USER(type=ANDROID_POST_SWITCH) call to recover
     * (for example, it could issue a SWITCH_USER(type=VEHICLE_REQUEST) to switch back to
     * the previous user), but ideally it should never fail (as switching back could result in a
     * confusing experience for the end user).
     *
     * For example, if the system have users (0, 10, 11) and it's switching from 0 to 11 (where none
     * of them have any special flag), the request would be:
     *
     * int32[0]:  42  // request id
     * int32[1]:  1   // SwitchUserMessageType::LEGACY_ANDROID_SWITCH
     * int32[2]:  11  // target user id
     * int32[3]:  0   // target user flags (none)
     * int32[4]:  10  // current user
     * int32[5]:  0   // current user flags (none)
     * int32[6]:  3   // number of users
     * int32[7]:  0   // user #0 (Android user id 0)
     * int32[8]:  0   // flags of user #0 (none)
     * int32[9]:  10  // user #1 (Android user id 10)
     * int32[10]: 0   // flags of user #1 (none)
     * int32[11]: 11  // user #2 (Android user id 11)
     * int32[12]: 0   // flags of user #2 (none)
     *
     * 2.ANDROID_SWITCH
     * ----------------
     * Called by the Android System to indicate the Android user is about to change, but Android
     * will wait for the HAL's response (up to some time) before proceeding.
     *
     * The HAL must switch its internal user once it receives this request, then respond back to
     * Android with a SWITCH_USER(type=VEHICLE_RESPONSE) indicating whether its internal
     * user was switched or not (through the SwitchUserStatus enum).
     *
     * For example, if Android has users (0, 10, 11) and it's switching from 10 to 11 (where
     * none of them have any special flag), the request would be:
     *
     * int32[0]:  42  // request id
     * int32[1]:  2   // SwitchUserMessageType::ANDROID_SWITCH
     * int32[2]:  11  // target user id
     * int32[3]:  0   // target user flags (none)
     * int32[4]:  10  // current user
     * int32[5]:  0   // current user flags (none)
     * int32[6]:  3   // number of users
     * int32[7]:  0   // 1st user (user 0)
     * int32[8]:  1   // 1st user flags (SYSTEM)
     * int32[9]:  10  // 2nd user (user 10)
     * int32[10]: 0   // 2nd user flags (none)
     * int32[11]: 11  // 3rd user (user 11)
     * int32[12]: 0   // 3rd user flags (none)
     *
     * If the request succeeded, the HAL must update the propery with:
     *
     * int32[0]: 42  // request id
     * int32[1]: 3   // messageType = SwitchUserMessageType::VEHICLE_RESPONSE
     * int32[2]: 1   // status = SwitchUserStatus::SUCCESS
     *
     * But if it failed, the response would be something like:
     *
     * int32[0]: 42   // request id
     * int32[1]: 3    // messageType = SwitchUserMessageType::VEHICLE_RESPONSE
     * int32[2]: 2    // status = SwitchUserStatus::FAILURE
     * string: "108-D'OH!" // OEM-spefic error message
     *
     * 3.VEHICLE_RESPONSE
     * ------------------
     * Called by the HAL to indicate whether a request of type ANDROID_SWITCH should proceed or
     * abort - see the ANDROID_SWITCH section above for more info.
     *
     * 4.VEHICLE_REQUEST
     * ------------------
     * Called by the HAL to request that the current foreground Android user is switched.
     *
     * This is useful in situations where Android started as one user, but the vehicle identified
     * the driver as another user. For example, user A unlocked the car using the key fob of user B;
     * the INITIAL_USER_INFO request returned user B, but then a face recognition subsubsystem
     * identified the user as A.
     *
     * The HAL makes this request by a property change event (passing a negative request id), and
     * the Android system will response by issue an ANDROID_POST_SWITCH call which the same
     * request id.
     *
     * For example, if the current foreground Android user is 10 and the HAL asked it to switch to
     * 11, the request would be:
     *
     * int32[0]: -108  // request id
     * int32[1]: 4     // messageType = SwitchUserMessageType::VEHICLE_REQUEST
     * int32[2]: 11    // Android user id
     *
     * If the request succeeded and Android has 3 users (0, 10, 11), the response would be:
     *
     * int32[0]: -108 // request id
     * int32[1]:  5   // messageType = SwitchUserMessageType::ANDROID_POST_SWITCH
     * int32[2]:  11  // target user id
     * int32[3]:  0   // target user id flags (none)
     * int32[4]:  11  // current user
     * int32[5]:  0   // current user flags (none)
     * int32[6]:  3   // number of users
     * int32[7]:  0   // 1st user (user 0)
     * int32[8]:  0   // 1st user flags (none)
     * int32[9]:  10  // 2nd user (user 10)
     * int32[10]: 4   // 2nd user flags (none)
     * int32[11]: 11  // 3rd user (user 11)
     * int32[12]: 3   // 3rd user flags (none)
     *
     * Notice that both the current and target user ids are the same - if the request failed, then
     * they would be different (i.e, target user would be 11, but current user would still be 10).
     *
     * 5.ANDROID_POST_SWITCH
     * ---------------------
     * Called by the Android System after a request to switch a user was made.
     *
     * This property is called after switch requests of any type (i.e., LEGACY_ANDROID_SWITCH,
     * ANDROID_SWITCH, or VEHICLE_REQUEST) and can be used to determine if the request succeeded or
     * failed:
     *
     * 1. When it succeeded, it's called when the Android user is in the unlocked state and the
     *    value of the current and target users ids in the response are the same. This would be
     *    equivalent to receiving an Intent.ACTION_USER_UNLOCKED in an Android app.
     * 2. When it failed it's called right away and the value of the current and target users ids
     *    in the response are different (as the current user didn't change to the target).
     * 3. If a new switch request is made before the HAL responded to the previous one or before
     *    the user was unlocked, then the ANDROID_POST_SWITCH request is not made. For example,
     *    the driver could accidentally switch to the wrong user which has lock credentials, then
     *    switch to the right one before entering the credentials.
     *
     * The HAL can update its internal state once it receives this request, but it doesn't need to
     * reply back to the Android System.
     *
     * Request: the first N values as defined by INITIAL_USER_INFO (where the request-specific
     * value at index 1 is SwitchUserMessageType::ANDROID_POST_SWITCH), then 2 more values for the
     * target user id (i.e., the Android user id that was requested to be switched to) and its flags
     * (as defined by  UserFlags).
     *
     * Response: none.
     *
     * Example: see VEHICLE_REQUEST section above.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    SWITCH_USER = 299896584 /* (0x0F08 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * Called by the Android System after an Android user was created.
     *
     * The HAL can use this property to create its equivalent user.
     *
     * This is an async request: Android makes the request by setting a VehiclePropValue, and HAL
     * must respond with a property change indicating whether the request succeeded or failed. If
     * it failed, the Android system will remove the user.
     *
     * The format of the request is defined by CreateUserRequest and the format of the response by
     * CreateUserResponse.
     *
     * For example, if system had 2 users (0 and 10) and a 3rd one (which is an ephemeral guest) was
     * created, the request would be:
     *
     * int32[0]: 42  // request id
     * int32[1]: 11  // Android id of the created user
     * int32[2]: 3   // Android flags (ephemeral guest) of the created user
     * int32[3]: 10  // current user
     * int32[4]: 0   // current user flags (none)
     * int32[5]: 3   // number of users
     * int32[6]: 0   // 1st user (user 0)
     * int32[7]: 0   // 1st user flags (none)
     * int32[8]: 10  // 2nd user (user 10)
     * int32[9]: 0   // 2nd user flags (none)
     * int32[19]: 11 // 3rd user (user 11)
     * int32[11]: 3  // 3rd user flags (ephemeral guest)
     * string: "ElGuesto" // name of the new user
     *
     * Then if the request succeeded, the HAL would return:
     *
     * int32[0]: 42  // request id
     * int32[1]: 1   // CreateUserStatus::SUCCESS
     *
     * But if it failed:
     *
     * int32[0]: 42  // request id
     * int32[1]: 2   // CreateUserStatus::FAILURE
     * string: "D'OH!" // The meaning is a blackbox - it's passed to the caller (like Settings UI),
     *                 // which in turn can take the proper action.
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    CREATE_USER = 299896585 /* (0x0F09 | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * Called by the Android System after an Android user was removed.
     *
     * The HAL can use this property to remove its equivalent user.
     *
     * This is write-only call - the Android System is not expecting a reply from the HAL. Hence,
     * this request should not fail - if the equivalent HAL user cannot be removed, then HAL should
     * mark it as inactive or recover in some other way.
     *
     * The request is made by setting the VehiclePropValue with the contents defined by
     * RemoveUserRequest.
     *
     * For example, if system had 3 users (0, 10, and 11) and user 11 was removed, the request
     * would be:
     *
     * int32[0]: 42  // request id
     * int32[1]: 11  // (Android user id of the removed user)
     * int32[2]: 0   // (Android user flags of the removed user)
     * int32[3]: 10  // current user
     * int32[4]: 0   // current user flags (none)
     * int32[5]: 2   // number of users
     * int32[6]: 0   // 1st user (user 0)
     * int32[7]: 0   // 1st user flags (none)
     * int32[8]: 10  // 2nd user (user 10)
     * int32[9]: 0   // 2nd user flags (none)
     *
     * @change_mode VehiclePropertyChangeMode:STATIC
     * @access VehiclePropertyAccess:WRITE
     */
    REMOVE_USER = 299896586 /* (0x0F0A | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
    /**
     * Property used to associate (or query the association) the current user with vehicle-specific
     * identification mechanisms (such as key FOB).
     *
     * This is an optional user management property - the OEM could still support user management
     * without defining it. In fact, this property could be used without supporting the core
     * user-related functions described on INITIAL_USER_INFO.
     *
     * To query the association, the Android system gets the property, passing a VehiclePropValue
     * containing the types of associations are being queried, as defined by
     * UserIdentificationGetRequest. The HAL must return right away, returning a VehiclePropValue
     * with a UserIdentificationResponse. Notice that user identification should have already
     * happened while system is booting up and the VHAL implementation should only return the
     * already identified association (like the key FOB used to unlock the car), instead of starting
     * a new association from the get call.
     *
     * To associate types, the Android system sets the property, passing a VehiclePropValue
     * containing the types and values of associations being set, as defined by the
     * UserIdentificationSetRequest. The HAL will then use a property change event (whose
     * VehiclePropValue is defined by UserIdentificationResponse) indicating the current status of
     * the types after the request.
     *
     * For example, to query if the current user (10) is associated with the FOB that unlocked the
     * car and a custom mechanism provided by the OEM, the request would be:
     *
     * int32[0]: 42  // request id
     * int32[1]: 10  (Android user id)
     * int32[2]: 0   (Android user flags)
     * int32[3]: 2   (number of types queried)
     * int32[4]: 1   (1st type queried, UserIdentificationAssociationType::KEY_FOB)
     * int32[5]: 101 (2nd type queried, UserIdentificationAssociationType::CUSTOM_1)
     *
     * If the user is associated with the FOB but not with the custom mechanism, the response would
     * be:
     *
     * int32[0]: 42  // request id
     * int32[1]: 2   (number of associations in the response)
     * int32[2]: 1   (1st type: UserIdentificationAssociationType::KEY_FOB)
     * int32[3]: 2   (1st value: UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER)
     * int32[4]: 101 (2st type: UserIdentificationAssociationType::CUSTOM_1)
     * int32[5]: 4   (2nd value: UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER)
     *
     * Then to associate the user with the custom mechanism, a set request would be made:
     *
     * int32[0]: 43  // request id
     * int32[1]: 10  (Android user id)
     * int32[2]: 0   (Android user flags)
     * int32[3]: 1   (number of associations being set)
     * int32[4]: 101 (1st type: UserIdentificationAssociationType::CUSTOM_1)
     * int32[5]: 1   (1st value: UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER)
     *
     * If the request succeeded, the response would be simply:
     *
     * int32[0]: 43  // request id
     * int32[1]: 1   (number of associations in the response)
     * int32[2]: 101 (1st type: UserIdentificationAssociationType::CUSTOM_1)
     * int32[3]: 1   (1st value: UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER)
     *
     * Notice that the set request adds associations, but doesn't remove the existing ones. In the
     * example above, the end state would be 2 associations (FOB and CUSTOM_1). If we wanted to
     * associate the user with just CUSTOM_1 but not FOB, then the request should have been:
     *
     * int32[0]: 43  // request id
     * int32[1]: 10  (Android user id)
     * int32[2]: 2   (number of types set)
     * int32[3]: 1   (1st type: UserIdentificationAssociationType::KEY_FOB)
     * int32[4]: 2   (1st value: UserIdentificationAssociationValue::DISASSOCIATE_CURRENT_USER)
     * int32[5]: 101 (2nd type: UserIdentificationAssociationType::CUSTOM_1)
     * int32[6]: 1   (2nd value: UserIdentificationAssociationValue::ASSOCIATE_CURRENT_USER)
     *
     * @change_mode VehiclePropertyChangeMode:ON_CHANGE
     * @access VehiclePropertyAccess:READ_WRITE
     */
    USER_IDENTIFICATION_ASSOCIATION = 299896587 /* (0x0F0B | VehiclePropertyGroup:SYSTEM | VehiclePropertyType:MIXED | VehicleArea:GLOBAL) */,
};

/**
 * Used by SUPPORT_CUSTOMIZE_VENDOR_PERMISSION to indicate the permission of vendor properties.
 */
enum class VehicleVendorPermission : int32_t {
    PERMISSION_DEFAULT = 0 /* 0x00000000 */,
    PERMISSION_SET_VENDOR_CATEGORY_WINDOW = 1 /* 0X00000001 */,
    PERMISSION_GET_VENDOR_CATEGORY_WINDOW = 2 /* 0x00000002 */,
    PERMISSION_SET_VENDOR_CATEGORY_DOOR = 3 /* 0x00000003 */,
    PERMISSION_GET_VENDOR_CATEGORY_DOOR = 4 /* 0x00000004 */,
    PERMISSION_SET_VENDOR_CATEGORY_SEAT = 5 /* 0x00000005 */,
    PERMISSION_GET_VENDOR_CATEGORY_SEAT = 6 /* 0x00000006 */,
    PERMISSION_SET_VENDOR_CATEGORY_MIRROR = 7 /* 0x00000007 */,
    PERMISSION_GET_VENDOR_CATEGORY_MIRROR = 8 /* 0x00000008 */,
    PERMISSION_SET_VENDOR_CATEGORY_INFO = 9 /* 0x00000009 */,
    PERMISSION_GET_VENDOR_CATEGORY_INFO = 10 /* 0x0000000A */,
    PERMISSION_SET_VENDOR_CATEGORY_ENGINE = 11 /* 0x0000000B */,
    PERMISSION_GET_VENDOR_CATEGORY_ENGINE = 12 /* 0x0000000C */,
    PERMISSION_SET_VENDOR_CATEGORY_HVAC = 13 /* 0x0000000D */,
    PERMISSION_GET_VENDOR_CATEGORY_HVAC = 14 /* 0x0000000E */,
    PERMISSION_SET_VENDOR_CATEGORY_LIGHT = 15 /* 0x0000000F */,
    PERMISSION_GET_VENDOR_CATEGORY_LIGHT = 16 /* 0x00000010 */,
    PERMISSION_SET_VENDOR_CATEGORY_1 = 65536 /* 0x00010000 */,
    PERMISSION_GET_VENDOR_CATEGORY_1 = 69632 /* 0x00011000 */,
    PERMISSION_SET_VENDOR_CATEGORY_2 = 131072 /* 0x00020000 */,
    PERMISSION_GET_VENDOR_CATEGORY_2 = 135168 /* 0x00021000 */,
    PERMISSION_SET_VENDOR_CATEGORY_3 = 196608 /* 0x00030000 */,
    PERMISSION_GET_VENDOR_CATEGORY_3 = 200704 /* 0x00031000 */,
    PERMISSION_SET_VENDOR_CATEGORY_4 = 262144 /* 0x00040000 */,
    PERMISSION_GET_VENDOR_CATEGORY_4 = 266240 /* 0x00041000 */,
    PERMISSION_SET_VENDOR_CATEGORY_5 = 327680 /* 0x00050000 */,
    PERMISSION_GET_VENDOR_CATEGORY_5 = 331776 /* 0x00051000 */,
    PERMISSION_SET_VENDOR_CATEGORY_6 = 393216 /* 0x00060000 */,
    PERMISSION_GET_VENDOR_CATEGORY_6 = 397312 /* 0x00061000 */,
    PERMISSION_SET_VENDOR_CATEGORY_7 = 458752 /* 0x00070000 */,
    PERMISSION_GET_VENDOR_CATEGORY_7 = 462848 /* 0x00071000 */,
    PERMISSION_SET_VENDOR_CATEGORY_8 = 524288 /* 0x00080000 */,
    PERMISSION_GET_VENDOR_CATEGORY_8 = 528384 /* 0x00081000 */,
    PERMISSION_SET_VENDOR_CATEGORY_9 = 589824 /* 0x00090000 */,
    PERMISSION_GET_VENDOR_CATEGORY_9 = 593920 /* 0x00091000 */,
    PERMISSION_SET_VENDOR_CATEGORY_10 = 655360 /* 0x000A0000 */,
    PERMISSION_GET_VENDOR_CATEGORY_10 = 659456 /* 0x000A1000 */,
    PERMISSION_NOT_ACCESSIBLE = -268435456 /* 0xF0000000 */,
};

/**
 * Used by seat occupancy to enumerate the current occupancy state of the seat.
 */
enum class VehicleSeatOccupancyState : int32_t {
    UNKNOWN = 0,
    VACANT = 1,
    OCCUPIED = 2,
};

/**
 * Used by lights state properties to enumerate the current state of the lights.
 *
 * Most XXX_LIGHTS_STATE properties will only report ON and OFF states.  Only
 * the HEADLIGHTS_STATE property will report DAYTIME_RUNNING.
 */
enum class VehicleLightState : int32_t {
    OFF = 0,
    ON = 1,
    DAYTIME_RUNNING = 2,
};

/**
 * Used by lights switch properties to enumerate user selected switch setting.
 *
 * XXX_LIGHTS_SWITCH properties report the switch settings that the user
 * selects.  The switch setting may be decoupled from the state reported if the
 * user selects AUTOMATIC.
 */
enum class VehicleLightSwitch : int32_t {
    OFF = 0,
    ON = 1,
    /**
     * Daytime running lights mode.  Most cars automatically use DRL but some
     * cars allow the user to activate them manually.
     */
    DAYTIME_RUNNING = 2,
    /**
     * Allows the vehicle ECU to set the lights automatically
     */
    AUTOMATIC = 256 /* 0x100 */,
};

/**
 * Used by INFO_EV_CONNECTOR_TYPE to enumerate the type of connectors
 * available to charge the vehicle.
 */
enum class EvConnectorType : int32_t {
    /**
     * Default type if the vehicle does not know or report the EV connector
     * type.
     */
    UNKNOWN = 0,
    IEC_TYPE_1_AC = 1,
    IEC_TYPE_2_AC = 2,
    IEC_TYPE_3_AC = 3,
    IEC_TYPE_4_DC = 4,
    IEC_TYPE_1_CCS_DC = 5,
    IEC_TYPE_2_CCS_DC = 6,
    TESLA_ROADSTER = 7,
    TESLA_HPWC = 8,
    TESLA_SUPERCHARGER = 9,
    GBT_AC = 10,
    GBT_DC = 11,
    /**
     * Connector type to use when no other types apply. Before using this
     * value, work with Google to see if the EvConnectorType enum can be
     * extended with an appropriate value.
     */
    OTHER = 101,
};

/**
 * Used by INFO_FUEL_DOOR_LOCATION/INFO_CHARGE_PORT_LOCATION to enumerate fuel door or
 * ev port location.
 */
enum class PortLocationType : int32_t {
    /**
     * Default type if the vehicle does not know or report the Fuel door
     * and ev port location.
     */
    UNKNOWN = 0,
    FRONT_LEFT = 1,
    FRONT_RIGHT = 2,
    REAR_RIGHT = 3,
    REAR_LEFT = 4,
    FRONT = 5,
    REAR = 6,
};

/**
 * Used by INFO_FUEL_TYPE to enumerate the type of fuels this vehicle uses.
 * Consistent with projection protocol.
 */
enum class FuelType : int32_t {
    /**
     * Fuel type to use if the HU does not know on which types of fuel the vehicle
     * runs. The use of this value is generally discouraged outside of aftermarket units.
     */
    FUEL_TYPE_UNKNOWN = 0,
    /**
     * Unleaded gasoline
     */
    FUEL_TYPE_UNLEADED = 1,
    /**
     * Leaded gasoline
     */
    FUEL_TYPE_LEADED = 2,
    /**
     * Diesel #1
     */
    FUEL_TYPE_DIESEL_1 = 3,
    /**
     * Diesel #2
     */
    FUEL_TYPE_DIESEL_2 = 4,
    /**
     * Biodiesel
     */
    FUEL_TYPE_BIODIESEL = 5,
    /**
     * 85% ethanol/gasoline blend
     */
    FUEL_TYPE_E85 = 6,
    /**
     * Liquified petroleum gas
     */
    FUEL_TYPE_LPG = 7,
    /**
     * Compressed natural gas
     */
    FUEL_TYPE_CNG = 8,
    /**
     * Liquified natural gas
     */
    FUEL_TYPE_LNG = 9,
    /**
     * Electric
     */
    FUEL_TYPE_ELECTRIC = 10,
    /**
     * Hydrogen fuel cell
     */
    FUEL_TYPE_HYDROGEN = 11,
    /**
     * Fuel type to use when no other types apply. Before using this value, work with
     * Google to see if the FuelType enum can be extended with an appropriate value.
     */
    FUEL_TYPE_OTHER = 12,
};

/**
 * Bit flags for fan direction
 */
enum class VehicleHvacFanDirection : int32_t {
    UNKNOWN = 0 /* 0x0 */,
    FACE = 1 /* 0x1 */,
    FLOOR = 2 /* 0x2 */,
    /**
     * FACE_AND_FLOOR = FACE | FLOOR
     */
    FACE_AND_FLOOR = 3 /* 0x3 */,
    DEFROST = 4 /* 0x4 */,
    /**
     * DEFROST_AND_FLOOR = DEFROST | FLOOR
     */
    DEFROST_AND_FLOOR = 6 /* 0x06 */,
};

enum class VehicleOilLevel : int32_t {
    /**
     * Oil level values
     */
    CRITICALLY_LOW = 0,
    LOW = 1,
    NORMAL = 2,
    HIGH = 3,
    ERROR = 4,
};

enum class VehicleApPowerStateConfigFlag : int32_t {
    /**
     * AP can enter deep sleep state. If not set, AP will always shutdown from
     * VehicleApPowerState#SHUTDOWN_PREPARE power state.
     */
    ENABLE_DEEP_SLEEP_FLAG = 1 /* 0x1 */,
    /**
     * The power controller can power on AP from off state after timeout
     * specified in VehicleApPowerSet VEHICLE_AP_POWER_SET_SHUTDOWN_READY message.
     */
    CONFIG_SUPPORT_TIMER_POWER_ON_FLAG = 2 /* 0x2 */,
};

enum class VehicleApPowerStateReq : int32_t {
    /**
     * This requests Android to enter its normal operating state.
     * This may be sent after the AP has reported
     * VehicleApPowerStateReport#DEEP_SLEEP_EXIT,
     * VehicleApPowerStateReport#SHUTDOWN_CANCELLED, or
     * VehicleApPowerStateReport#WAIT_FOR_VHAL.
     */
    ON = 0,
    /**
     * The power controller issues this request to shutdown the system.
     * This may be sent after the AP has reported
     * VehicleApPowerStateReport#DEEP_SLEEP_EXIT,
     * VehicleApPowerStateReport#ON,
     * VehicleApPowerStateReport#SHUTDOWN_CANCELLED,
     * VehicleApPowerStateReport#SHUTDOWN_POSTPONE,
     * VehicleApPowerStateReport#SHUTDOWN_PREPARE, or
     * VehicleApPowerStateReport#WAIT_FOR_VHAL.
     *
     * int32Values[1] : One of VehicleApPowerStateShutdownParam.
     *                  This parameter indicates if the AP should shut
     *                  down fully or sleep. This parameter also
     *                  indicates if the shutdown should be immediate
     *                  or if it can be postponed. If the shutdown can
     *                  be postponed, AP requests postponing by sending
     *                  VehicleApPowerStateReport#SHUTDOWN_POSTPONE.
     */
    SHUTDOWN_PREPARE = 1,
    /**
     * Cancel the shutdown.
     * This may be sent after the AP has reported
     * VehicleApPowerStateReport#SHUTDOWN_POSTPONE or
     * VehicleApPowerStateReport#SHUTDOWN_PREPARE.
     * After receiving this request, the AP will report
     * VehicleApPowerStateReport#WAIT_FOR_VHAL in preparation to going ON.
     */
    CANCEL_SHUTDOWN = 2,
    /**
     * Completes the shutdown process.
     * This may be sent after the AP has reported
     * VehicleApPowerStateReport#DEEP_SLEEP_ENTRY or
     * VehicleApPowerStateReport#SHUTDOWN_START. The AP will not report new
     * state information after receiving this request.
     */
    FINISHED = 3,
};

/**
 * Index in int32Values for VehicleProperty#AP_POWER_STATE_REQ property.
 */
enum class VehicleApPowerStateReqIndex : int32_t {
    STATE = 0,
    ADDITIONAL = 1,
};

enum class VehicleApPowerStateShutdownParam : int32_t {
    /**
     * AP must shutdown immediately. Postponing is not allowed.
     */
    SHUTDOWN_IMMEDIATELY = 1,
    /**
     * AP can enter deep sleep instead of shutting down completely.
     */
    CAN_SLEEP = 2,
    /**
     * AP can only shutdown with postponing allowed.
     */
    SHUTDOWN_ONLY = 3,
    /**
     * AP may enter deep sleep, but must either sleep or shut down immediately.
     * Postponing is not allowed.
     */
    SLEEP_IMMEDIATELY = 4,
};

enum class VehicleApPowerStateReport : int32_t {
    /**
     * The device has booted. CarService has initialized and is ready to accept commands
     * from VHAL. The user is not logged in, and vendor apps and services are expected to
     * control the display and audio.
     * After reporting this state, AP will accept VehicleApPowerStateReq#ON or
     * VehicleApPowerStateReq#SHUTDOWN_PREPARE. Other power state requests are ignored.
     */
    WAIT_FOR_VHAL = 1 /* 0x1 */,
    /**
     * AP is ready to suspend.
     * The AP will not send any more state reports after this.
     * After reporting this state, AP will accept VehicleApPowerStateReq#FINISHED.
     * Other power state requests are ignored.
     *
     * int32Values[1]: Time to turn AP back on, in seconds. Power controller should turn on
     *                 AP after the specified time has elapsed, so AP can run tasks like
     *                 update. If this value is 0, no wake up is requested. The power
     *                 controller may not necessarily support timed wake-up.
     */
    DEEP_SLEEP_ENTRY = 2 /* 0x2 */,
    /**
     * AP is exiting from deep sleep state.
     * After reporting this state, AP will accept VehicleApPowerStateReq#ON or
     * VehicleApPowerStateReq#SHUTDOWN_PREPARE. Other power state requests are ignored.
     */
    DEEP_SLEEP_EXIT = 3 /* 0x3 */,
    /**
     * AP sends this message repeatedly while cleanup and idle tasks execute.
     * After reporting this state, AP will accept VehicleApPowerStateReq#SHUTDOWN_PREPARE
     * requesting immediate shutdown or VehicleApPowerStateReq#CANCEL_SHUTDOWN. Other
     * power state requests are ignored.
     *
     * int32Values[1]: Time to postpone shutdown in ms. Maximum value is
     *                 5000 ms.
     *                 If AP needs more time, it will send another SHUTDOWN_POSTPONE
     *                 message before the previous one expires.
     */
    SHUTDOWN_POSTPONE = 4 /* 0x4 */,
    /**
     * AP is ready to shutdown.
     * The AP will not send any more state reports after this.
     * After reporting this state, AP will accept VehicleApPowerStateReq#FINISHED.
     * Other power state requests are ignored.
     *
     * int32Values[1]: Time to turn AP back on, in seconds. Power controller should turn on
     *                 AP after the specified time has elapsed so AP can run tasks like
     *                 update. If this value is 0, no wake up is specified. The power
     *                 controller may not necessarily support timed wake-up.
     */
    SHUTDOWN_START = 5 /* 0x5 */,
    /**
     * AP is entering its normal operating state.
     * After reporting this state, AP will accept VehicleApPowerStateReq#SHUTDOWN_PREPARE.
     * Other power state requests are ignored.
     */
    ON = 6 /* 0x6 */,
    /**
     * AP is preparing to shut down. In this state, Garage Mode is active and idle
     * tasks are allowed to run.
     * After reporting this state, AP will accept VehicleApPowerStateReq#SHUTDOWN_PREPARE
     * requesting immediate shutdown or VehicleApPowerStateReq#CANCEL_SHUTDOWN. Other
     * power state requests are ignored.
     */
    SHUTDOWN_PREPARE = 7 /* 0x7 */,
    /**
     * AP has stopped preparing to shut down.
     * After reporting this state, AP will accept VehicleApPowerStateReq#ON or
     * VehicleApPowerStateReq#SHUTDOWN_PREPARE. Other power state requests are ignored.
     */
    SHUTDOWN_CANCELLED = 8 /* 0x8 */,
};

enum class VehicleHwKeyInputAction : int32_t {
    /**
     * Key down
     */
    ACTION_DOWN = 0,
    /**
     * Key up
     */
    ACTION_UP = 1,
};

enum class VehicleDisplay : int32_t {
    /**
     * The primary Android display (for example, center console)
     */
    MAIN = 0,
    INSTRUMENT_CLUSTER = 1,
};

/**
 * Units used for int or float type with no attached enum types.
 */
enum class VehicleUnit : int32_t {
    SHOULD_NOT_USE = 0 /* 0x000 */,
    METER_PER_SEC = 1 /* 0x01 */,
    RPM = 2 /* 0x02 */,
    HERTZ = 3 /* 0x03 */,
    PERCENTILE = 16 /* 0x10 */,
    MILLIMETER = 32 /* 0x20 */,
    METER = 33 /* 0x21 */,
    KILOMETER = 35 /* 0x23 */,
    MILE = 36 /* 0x24 */,
    CELSIUS = 48 /* 0x30 */,
    FAHRENHEIT = 49 /* 0x31 */,
    KELVIN = 50 /* 0x32 */,
    MILLILITER = 64 /* 0x40 */,
    LITER = 65 /* 0x41 */,
    /**
     * deprecated. Use US_GALLON instead.
     */
    GALLON = 66 /* 0x42 */,
    US_GALLON = 66 /* 0x42 */,
    IMPERIAL_GALLON = 67 /* 0x43 */,
    NANO_SECS = 80 /* 0x50 */,
    SECS = 83 /* 0x53 */,
    YEAR = 89 /* 0x59 */,
    WATT_HOUR = 96 /* 0x60 */,
    MILLIAMPERE = 97 /* 0x61 */,
    MILLIVOLT = 98 /* 0x62 */,
    MILLIWATTS = 99 /* 0x63 */,
    AMPERE_HOURS = 100 /* 0x64 */,
    KILOWATT_HOUR = 101 /* 0x65 */,
    KILOPASCAL = 112 /* 0x70 */,
    PSI = 113 /* 0x71 */,
    BAR = 114 /* 0x72 */,
    DEGREES = 128 /* 0x80 */,
    MILES_PER_HOUR = 144 /* 0x90 */,
    KILOMETERS_PER_HOUR = 145 /* 0x91 */,
};

/**
 * This describes how value of property can change.
 */
enum class VehiclePropertyChangeMode : int32_t {
    /**
     * Property of this type must never be changed. Subscription is not supported
     * for these properties.
     */
    STATIC = 0 /* 0x00 */,
    /**
     * Properties of this type must report when there is a change.
     * IVehicle#get call must return the current value.
     * Set operation for this property is assumed to be asynchronous. When the
     * property is read (using IVehicle#get) after IVehicle#set, it may still
     * return old value until underlying H/W backing this property has actually
     * changed the state. Once state is changed, the property must dispatch
     * changed value as event.
     */
    ON_CHANGE = 1 /* 0x01 */,
    /**
     * Properties of this type change continuously and require a fixed rate of
     * sampling to retrieve the data.  Implementers may choose to send extra
     * notifications on significant value changes.
     */
    CONTINUOUS = 2 /* 0x02 */,
};

/**
 * Property config defines the capabilities of it. User of the API
 * must first get the property config to understand the output from get()
 * commands and also to ensure that set() or events commands are in sync with
 * the expected output.
 */
enum class VehiclePropertyAccess : int32_t {
    NONE = 0 /* 0x00 */,
    READ = 1 /* 0x01 */,
    WRITE = 2 /* 0x02 */,
    READ_WRITE = 3 /* 0x03 */,
};

/**
 * Property status is a dynamic value that may change based on the vehicle state.
 */
enum class VehiclePropertyStatus : int32_t {
    /**
     * Property is available and behaving normally
     */
    AVAILABLE = 0 /* 0x00 */,
    /**
     * A property in this state is not available for reading and writing.  This
     * is a transient state that depends on the availability of the underlying
     * implementation (e.g. hardware or driver). It MUST NOT be used to
     * represent features that this vehicle is always incapable of.  A get() of
     * a property in this state MAY return an undefined value, but MUST
     * correctly describe its status as UNAVAILABLE A set() of a property in
     * this state MAY return NOT_AVAILABLE. The HAL implementation MUST ignore
     * the value of the status field when writing a property value coming from
     * Android.
     */
    UNAVAILABLE = 1 /* 0x01 */,
    /**
     * There is an error with this property.
     */
    ERROR = 2 /* 0x02 */,
};

/**
 * Various gears which can be selected by user and chosen in system.
 */
enum class VehicleGear : int32_t {
    GEAR_UNKNOWN = 0 /* 0x0000 */,
    GEAR_NEUTRAL = 1 /* 0x0001 */,
    GEAR_REVERSE = 2 /* 0x0002 */,
    GEAR_PARK = 4 /* 0x0004 */,
    GEAR_DRIVE = 8 /* 0x0008 */,
    GEAR_1 = 16 /* 0x0010 */,
    GEAR_2 = 32 /* 0x0020 */,
    GEAR_3 = 64 /* 0x0040 */,
    GEAR_4 = 128 /* 0x0080 */,
    GEAR_5 = 256 /* 0x0100 */,
    GEAR_6 = 512 /* 0x0200 */,
    GEAR_7 = 1024 /* 0x0400 */,
    GEAR_8 = 2048 /* 0x0800 */,
    GEAR_9 = 4096 /* 0x1000 */,
};

/**
 * Various Seats in the car.
 */
enum class VehicleAreaSeat : int32_t {
    ROW_1_LEFT = 1 /* 0x0001 */,
    ROW_1_CENTER = 2 /* 0x0002 */,
    ROW_1_RIGHT = 4 /* 0x0004 */,
    ROW_2_LEFT = 16 /* 0x0010 */,
    ROW_2_CENTER = 32 /* 0x0020 */,
    ROW_2_RIGHT = 64 /* 0x0040 */,
    ROW_3_LEFT = 256 /* 0x0100 */,
    ROW_3_CENTER = 512 /* 0x0200 */,
    ROW_3_RIGHT = 1024 /* 0x0400 */,
};

/**
 * Various windshields/windows in the car.
 */
enum class VehicleAreaWindow : int32_t {
    FRONT_WINDSHIELD = 1 /* 0x00000001 */,
    REAR_WINDSHIELD = 2 /* 0x00000002 */,
    ROW_1_LEFT = 16 /* 0x00000010 */,
    ROW_1_RIGHT = 64 /* 0x00000040 */,
    ROW_2_LEFT = 256 /* 0x00000100 */,
    ROW_2_RIGHT = 1024 /* 0x00000400 */,
    ROW_3_LEFT = 4096 /* 0x00001000 */,
    ROW_3_RIGHT = 16384 /* 0x00004000 */,
    ROOF_TOP_1 = 65536 /* 0x00010000 */,
    ROOF_TOP_2 = 131072 /* 0x00020000 */,
};

enum class VehicleAreaDoor : int32_t {
    ROW_1_LEFT = 1 /* 0x00000001 */,
    ROW_1_RIGHT = 4 /* 0x00000004 */,
    ROW_2_LEFT = 16 /* 0x00000010 */,
    ROW_2_RIGHT = 64 /* 0x00000040 */,
    ROW_3_LEFT = 256 /* 0x00000100 */,
    ROW_3_RIGHT = 1024 /* 0x00000400 */,
    HOOD = 268435456 /* 0x10000000 */,
    REAR = 536870912 /* 0x20000000 */,
};

enum class VehicleAreaMirror : int32_t {
    DRIVER_LEFT = 1 /* 0x00000001 */,
    DRIVER_RIGHT = 2 /* 0x00000002 */,
    DRIVER_CENTER = 4 /* 0x00000004 */,
};

enum class VehicleTurnSignal : int32_t {
    NONE = 0 /* 0x00 */,
    RIGHT = 1 /* 0x01 */,
    LEFT = 2 /* 0x02 */,
};

struct VehicleAreaConfig final {
    /**
     * Area id is ignored for VehiclePropertyGroup:GLOBAL properties.
     */
    int32_t areaId __attribute__ ((aligned(4)));
    /**
     * If the property has @data_enum, leave the range to zero.
     *
     * Range will be ignored in the following cases:
     *    - The VehiclePropertyType is not INT32, INT64 or FLOAT.
     *    - Both of min value and max value are zero.
     */
    int32_t minInt32Value __attribute__ ((aligned(4)));
    int32_t maxInt32Value __attribute__ ((aligned(4)));
    int64_t minInt64Value __attribute__ ((aligned(8)));
    int64_t maxInt64Value __attribute__ ((aligned(8)));
    float minFloatValue __attribute__ ((aligned(4)));
    float maxFloatValue __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, areaId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, minInt32Value) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, maxInt32Value) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, minInt64Value) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, maxInt64Value) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, minFloatValue) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig, maxFloatValue) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig) == 8, "wrong alignment");

struct VehiclePropConfig final {
    /**
     * Property identifier
     */
    int32_t prop __attribute__ ((aligned(4)));
    /**
     * Defines if the property is read or write or both.
     */
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess access __attribute__ ((aligned(4)));
    /**
     * Defines the change mode of the property.
     */
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode changeMode __attribute__ ((aligned(4)));
    /**
     * Contains per-area configuration.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig> areaConfigs __attribute__ ((aligned(8)));
    /**
     * Contains additional configuration parameters
     */
    ::android::hardware::hidl_vec<int32_t> configArray __attribute__ ((aligned(8)));
    /**
     * Some properties may require additional information passed over this
     * string. Most properties do not need to set this.
     */
    ::android::hardware::hidl_string configString __attribute__ ((aligned(8)));
    /**
     * Min sample rate in Hz.
     * Must be defined for VehiclePropertyChangeMode::CONTINUOUS
     */
    float minSampleRate __attribute__ ((aligned(4)));
    /**
     * Must be defined for VehiclePropertyChangeMode::CONTINUOUS
     * Max sample rate in Hz.
     */
    float maxSampleRate __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, prop) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, access) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, changeMode) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, areaConfigs) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, configArray) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, configString) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, minSampleRate) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig, maxSampleRate) == 68, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig) == 72, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig) == 8, "wrong alignment");

/**
 * Encapsulates the property name and the associated value. It
 * is used across various API calls to set values, get values or to register for
 * events.
 */
struct VehiclePropValue final {
    // Forward declaration for forward reference support:
    struct RawValue;

    /**
     * Contains value for a single property. Depending on property data type of
     * this property (VehiclePropetyType) one field of this structure must be filled in.
     */
    struct RawValue final {
        /**
         * This is used for properties of types VehiclePropertyType#INT
         * and VehiclePropertyType#INT_VEC
         */
        ::android::hardware::hidl_vec<int32_t> int32Values __attribute__ ((aligned(8)));
        /**
         * This is used for properties of types VehiclePropertyType#FLOAT
         * and VehiclePropertyType#FLOAT_VEC
         */
        ::android::hardware::hidl_vec<float> floatValues __attribute__ ((aligned(8)));
        /**
         * This is used for properties of type VehiclePropertyType#INT64
         */
        ::android::hardware::hidl_vec<int64_t> int64Values __attribute__ ((aligned(8)));
        /**
         * This is used for properties of type VehiclePropertyType#BYTES
         */
        ::android::hardware::hidl_vec<uint8_t> bytes __attribute__ ((aligned(8)));
        /**
         * This is used for properties of type VehiclePropertyType#STRING
         */
        ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue, int32Values) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue, floatValues) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue, int64Values) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue, bytes) == 48, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue, stringValue) == 64, "wrong offset");
    static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue) == 80, "wrong size");
    static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue) == 8, "wrong alignment");

    /**
     * Time is elapsed nanoseconds since boot
     */
    int64_t timestamp __attribute__ ((aligned(8)));
    /**
     * Area type(s) for non-global property it must be one of the value from
     * VehicleArea* enums or 0 for global properties.
     */
    int32_t areaId __attribute__ ((aligned(4)));
    /**
     * Property identifier
     */
    int32_t prop __attribute__ ((aligned(4)));
    /**
     * Status of the property
     */
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus status __attribute__ ((aligned(4)));
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue, timestamp) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue, areaId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue, prop) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue, status) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue, value) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue) == 104, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::VehiclePropValue) == 8, "wrong alignment");

enum class VehicleIgnitionState : int32_t {
    UNDEFINED = 0,
    /**
     * Steering wheel is locked
     */
    LOCK = 1,
    /**
     * Steering wheel is not locked, engine and all accessories are OFF. If
     * car can be in LOCK and OFF state at the same time than HAL must report
     * LOCK state.
     */
    OFF = 2 /* ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState.LOCK implicitly + 1 */,
    /**
     * Typically in this state accessories become available (e.g. radio).
     * Instrument cluster and engine are turned off
     */
    ACC = 3 /* ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState.OFF implicitly + 1 */,
    /**
     * Ignition is in state ON. Accessories and instrument cluster available,
     * engine might be running or ready to be started.
     */
    ON = 4 /* ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState.ACC implicitly + 1 */,
    /**
     * Typically in this state engine is starting (cranking).
     */
    START = 5 /* ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState.ON implicitly + 1 */,
};

enum class SubscribeFlags : int32_t {
    UNDEFINED = 0 /* 0x0 */,
    /**
     * Subscribe to event that was originated in vehicle HAL
     * (most likely this event came from the vehicle itself).
     */
    EVENTS_FROM_CAR = 1 /* 0x1 */,
    /**
     * Use this flag to subscribe on events when IVehicle#set(...) was called by
     * vehicle HAL's client (e.g. Car Service).
     */
    EVENTS_FROM_ANDROID = 2 /* 0x2 */,
};

/**
 * Encapsulates information about subscription to vehicle property events.
 */
struct SubscribeOptions final {
    /**
     * Property to subscribe
     */
    int32_t propId __attribute__ ((aligned(4)));
    /**
     * Sample rate in Hz.
     *
     * Must be provided for properties with
     * VehiclePropertyChangeMode::CONTINUOUS. The value must be within
     * VehiclePropConfig#minSamplingRate .. VehiclePropConfig#maxSamplingRate
     * for a given property.
     * This value indicates how many updates per second client wants to receive.
     */
    float sampleRate __attribute__ ((aligned(4)));
    /**
     * Flags that indicate to which event sources to listen.
     */
    ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SubscribeOptions, propId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SubscribeOptions, sampleRate) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SubscribeOptions, flags) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::SubscribeOptions) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::SubscribeOptions) == 4, "wrong alignment");

/**
 * Error codes used in vehicle HAL interface.
 */
enum class StatusCode : int32_t {
    OK = 0,
    /**
     * Try again.
     */
    TRY_AGAIN = 1,
    /**
     * Invalid argument provided.
     */
    INVALID_ARG = 2,
    /**
     * This code must be returned when device that associated with the vehicle
     * property is not available. For example, when client tries to set HVAC
     * temperature when the whole HVAC unit is turned OFF.
     */
    NOT_AVAILABLE = 3,
    /**
     * Access denied
     */
    ACCESS_DENIED = 4,
    /**
     * Something unexpected has happened in Vehicle HAL
     */
    INTERNAL_ERROR = 5,
};

enum class VehicleAreaWheel : int32_t {
    UNKNOWN = 0 /* 0x0 */,
    LEFT_FRONT = 1 /* 0x1 */,
    RIGHT_FRONT = 2 /* 0x2 */,
    LEFT_REAR = 4 /* 0x4 */,
    RIGHT_REAR = 8 /* 0x8 */,
};

/**
 * The status of the vehicle's fuel system.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2FuelSystemStatus : int32_t {
    OPEN_INSUFFICIENT_ENGINE_TEMPERATURE = 1,
    CLOSED_LOOP = 2,
    OPEN_ENGINE_LOAD_OR_DECELERATION = 4,
    OPEN_SYSTEM_FAILURE = 8,
    CLOSED_LOOP_BUT_FEEDBACK_FAULT = 16,
};

/**
 * Defines which ignition monitors are available to be read.
 */
enum class Obd2IgnitionMonitorKind : int32_t {
    SPARK = 0,
    COMPRESSION = 1,
};

/**
 * Ignition monitors common to both SPARK and COMPRESSION.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2CommonIgnitionMonitors : int32_t {
    COMPONENTS_AVAILABLE = 1 /* 0x1 << 0 */,
    COMPONENTS_INCOMPLETE = 2 /* 0x1 << 1 */,
    FUEL_SYSTEM_AVAILABLE = 4 /* 0x1 << 2 */,
    FUEL_SYSTEM_INCOMPLETE = 8 /* 0x1 << 3 */,
    MISFIRE_AVAILABLE = 16 /* 0x1 << 4 */,
    MISFIRE_INCOMPLETE = 32 /* 0x1 << 5 */,
};

/**
 * Ignition monitors available for SPARK vehicles.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2SparkIgnitionMonitors : int32_t {
    COMPONENTS_AVAILABLE = 1 /* 0x1 << 0 */,
    COMPONENTS_INCOMPLETE = 2 /* 0x1 << 1 */,
    FUEL_SYSTEM_AVAILABLE = 4 /* 0x1 << 2 */,
    FUEL_SYSTEM_INCOMPLETE = 8 /* 0x1 << 3 */,
    MISFIRE_AVAILABLE = 16 /* 0x1 << 4 */,
    MISFIRE_INCOMPLETE = 32 /* 0x1 << 5 */,
    EGR_AVAILABLE = 64 /* 0x1 << 6 */,
    EGR_INCOMPLETE = 128 /* 0x1 << 7 */,
    OXYGEN_SENSOR_HEATER_AVAILABLE = 256 /* 0x1 << 8 */,
    OXYGEN_SENSOR_HEATER_INCOMPLETE = 512 /* 0x1 << 9 */,
    OXYGEN_SENSOR_AVAILABLE = 1024 /* 0x1 << 10 */,
    OXYGEN_SENSOR_INCOMPLETE = 2048 /* 0x1 << 11 */,
    AC_REFRIGERANT_AVAILABLE = 4096 /* 0x1 << 12 */,
    AC_REFRIGERANT_INCOMPLETE = 8192 /* 0x1 << 13 */,
    SECONDARY_AIR_SYSTEM_AVAILABLE = 16384 /* 0x1 << 14 */,
    SECONDARY_AIR_SYSTEM_INCOMPLETE = 32768 /* 0x1 << 15 */,
    EVAPORATIVE_SYSTEM_AVAILABLE = 65536 /* 0x1 << 16 */,
    EVAPORATIVE_SYSTEM_INCOMPLETE = 131072 /* 0x1 << 17 */,
    HEATED_CATALYST_AVAILABLE = 262144 /* 0x1 << 18 */,
    HEATED_CATALYST_INCOMPLETE = 524288 /* 0x1 << 19 */,
    CATALYST_AVAILABLE = 1048576 /* 0x1 << 20 */,
    CATALYST_INCOMPLETE = 2097152 /* 0x1 << 21 */,
};

/**
 * Ignition monitors only available for COMPRESSION vehicles.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2CompressionIgnitionMonitors : int32_t {
    COMPONENTS_AVAILABLE = 1 /* 0x1 << 0 */,
    COMPONENTS_INCOMPLETE = 2 /* 0x1 << 1 */,
    FUEL_SYSTEM_AVAILABLE = 4 /* 0x1 << 2 */,
    FUEL_SYSTEM_INCOMPLETE = 8 /* 0x1 << 3 */,
    MISFIRE_AVAILABLE = 16 /* 0x1 << 4 */,
    MISFIRE_INCOMPLETE = 32 /* 0x1 << 5 */,
    EGR_OR_VVT_AVAILABLE = 64 /* 0x1 << 6 */,
    EGR_OR_VVT_INCOMPLETE = 128 /* 0x1 << 7 */,
    PM_FILTER_AVAILABLE = 256 /* 0x1 << 8 */,
    PM_FILTER_INCOMPLETE = 512 /* 0x1 << 9 */,
    EXHAUST_GAS_SENSOR_AVAILABLE = 1024 /* 0x1 << 10 */,
    EXHAUST_GAS_SENSOR_INCOMPLETE = 2048 /* 0x1 << 11 */,
    BOOST_PRESSURE_AVAILABLE = 4096 /* 0x1 << 12 */,
    BOOST_PRESSURE_INCOMPLETE = 8192 /* 0x1 << 13 */,
    NOx_SCR_AVAILABLE = 16384 /* 0x1 << 14 */,
    NOx_SCR_INCOMPLETE = 32768 /* 0x1 << 15 */,
    NMHC_CATALYST_AVAILABLE = 65536 /* 0x1 << 16 */,
    NMHC_CATALYST_INCOMPLETE = 131072 /* 0x1 << 17 */,
};

/**
 * The status of the vehicle's secondary air system.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2SecondaryAirStatus : int32_t {
    UPSTREAM = 1,
    DOWNSTREAM_OF_CATALYCIC_CONVERTER = 2,
    FROM_OUTSIDE_OR_OFF = 4,
    PUMP_ON_FOR_DIAGNOSTICS = 8,
};

/**
 * The fuel type(s) supported by a vehicle.
 * These values come from the SAE J1979 standard.
 */
enum class Obd2FuelType : int32_t {
    NOT_AVAILABLE = 0,
    GASOLINE = 1,
    METHANOL = 2,
    ETHANOL = 3,
    DIESEL = 4,
    LPG = 5,
    CNG = 6,
    PROPANE = 7,
    ELECTRIC = 8,
    BIFUEL_RUNNING_GASOLINE = 9,
    BIFUEL_RUNNING_METHANOL = 10,
    BIFUEL_RUNNING_ETHANOL = 11,
    BIFUEL_RUNNING_LPG = 12,
    BIFUEL_RUNNING_CNG = 13,
    BIFUEL_RUNNING_PROPANE = 14,
    BIFUEL_RUNNING_ELECTRIC = 15,
    BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION = 16,
    HYBRID_GASOLINE = 17,
    HYBRID_ETHANOL = 18,
    HYBRID_DIESEL = 19,
    HYBRID_ELECTRIC = 20,
    HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION = 21,
    HYBRID_REGENERATIVE = 22,
    BIFUEL_RUNNING_DIESEL = 23,
};

/**
 * This enum provides the canonical mapping for sensor properties that have an integer value.
 * The ordering of the values is taken from the OBD2 specification.
 * Some of the properties are represented as an integer mapping to another enum. In those cases
 * expect a comment by the property definition describing the enum to look at for the mapping.
 * Any value greater than the last reserved index is available to vendors to map their extensions.
 * While these values do not directly map to SAE J1979 PIDs, an equivalence is listed next
 * to each one to aid implementors.
 */
enum class DiagnosticIntegerSensorIndex : int32_t {
    /**
     * refer to FuelSystemStatus for a description of this value.
     */
    FUEL_SYSTEM_STATUS = 0,
    /*
     * PID 0x03
     */
    MALFUNCTION_INDICATOR_LIGHT_ON = 1,
    /*
     * PID 0x01
     *
     *
     * refer to IgnitionMonitorKind for a description of this value.
     */
    IGNITION_MONITORS_SUPPORTED = 2,
    /*
     * PID 0x01
     *
     *
     * The value of this sensor is a bitmask that specifies whether ignition-specific
     * tests are available and whether they are complete. The semantics of the individual
     * bits in this value are given by, respectively, SparkIgnitionMonitors and
     * CompressionIgnitionMonitors depending on the value of IGNITION_MONITORS_SUPPORTED.
     */
    IGNITION_SPECIFIC_MONITORS = 3,
    /*
     * PID 0x01
     */
    INTAKE_AIR_TEMPERATURE = 4,
    /*
     * PID 0x0F
     *
     *
     * refer to SecondaryAirStatus for a description of this value.
     */
    COMMANDED_SECONDARY_AIR_STATUS = 5,
    /*
     * PID 0x12
     */
    NUM_OXYGEN_SENSORS_PRESENT = 6,
    /*
     * PID 0x13
     */
    RUNTIME_SINCE_ENGINE_START = 7,
    /*
     * PID 0x1F
     */
    DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON = 8,
    /*
     * PID 0x21
     */
    WARMUPS_SINCE_CODES_CLEARED = 9,
    /*
     * PID 0x30
     */
    DISTANCE_TRAVELED_SINCE_CODES_CLEARED = 10,
    /*
     * PID 0x31
     */
    ABSOLUTE_BAROMETRIC_PRESSURE = 11,
    /*
     * PID 0x33
     */
    CONTROL_MODULE_VOLTAGE = 12,
    /*
     * PID 0x42
     */
    AMBIENT_AIR_TEMPERATURE = 13,
    /*
     * PID 0x46
     */
    TIME_WITH_MALFUNCTION_LIGHT_ON = 14,
    /*
     * PID 0x4D
     */
    TIME_SINCE_TROUBLE_CODES_CLEARED = 15,
    /*
     * PID 0x4E
     */
    MAX_FUEL_AIR_EQUIVALENCE_RATIO = 16,
    /*
     * PID 0x4F
     */
    MAX_OXYGEN_SENSOR_VOLTAGE = 17,
    /*
     * PID 0x4F
     */
    MAX_OXYGEN_SENSOR_CURRENT = 18,
    /*
     * PID 0x4F
     */
    MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 19,
    /*
     * PID 0x4F
     */
    MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR = 20,
    /*
     * PID 0x50
     *
     *
     * refer to FuelType for a description of this value.
     */
    FUEL_TYPE = 21,
    /*
     * PID 0x51
     */
    FUEL_RAIL_ABSOLUTE_PRESSURE = 22,
    /*
     * PID 0x59
     */
    ENGINE_OIL_TEMPERATURE = 23,
    /*
     * PID 0x5C
     */
    DRIVER_DEMAND_PERCENT_TORQUE = 24,
    /*
     * PID 0x61
     */
    ENGINE_ACTUAL_PERCENT_TORQUE = 25,
    /*
     * PID 0x62
     */
    ENGINE_REFERENCE_PERCENT_TORQUE = 26,
    /*
     * PID 0x63
     */
    ENGINE_PERCENT_TORQUE_DATA_IDLE = 27,
    /*
     * PID 0x64
     */
    ENGINE_PERCENT_TORQUE_DATA_POINT1 = 28,
    /*
     * PID 0x64
     */
    ENGINE_PERCENT_TORQUE_DATA_POINT2 = 29,
    /*
     * PID 0x64
     */
    ENGINE_PERCENT_TORQUE_DATA_POINT3 = 30,
    /*
     * PID 0x64
     */
    ENGINE_PERCENT_TORQUE_DATA_POINT4 = 31,
    /*
     * PID 0x64
     */
    LAST_SYSTEM_INDEX = 31 /* ENGINE_PERCENT_TORQUE_DATA_POINT4 */,
};

/**
 * This enum provides the canonical mapping for sensor properties that have a floating-point value.
 * The ordering of the values is taken from the OBD2 specification.
 * Any value greater than the last reserved index is available to vendors to map their extensions.
 * While these values do not directly map to SAE J1979 PIDs, an equivalence is listed next
 * to each one to aid implementors.
 */
enum class DiagnosticFloatSensorIndex : int32_t {
    CALCULATED_ENGINE_LOAD = 0,
    /*
     * PID 0x04
     */
    ENGINE_COOLANT_TEMPERATURE = 1,
    /*
     * PID 0x05
     */
    SHORT_TERM_FUEL_TRIM_BANK1 = 2,
    /*
     * PID 0x06
     */
    LONG_TERM_FUEL_TRIM_BANK1 = 3,
    /*
     * PID 0x07
     */
    SHORT_TERM_FUEL_TRIM_BANK2 = 4,
    /*
     * PID 0x08
     */
    LONG_TERM_FUEL_TRIM_BANK2 = 5,
    /*
     * PID 0x09
     */
    FUEL_PRESSURE = 6,
    /*
     * PID 0x0A
     */
    INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 7,
    /*
     * PID 0x0B
     */
    ENGINE_RPM = 8,
    /*
     * PID 0x0C
     */
    VEHICLE_SPEED = 9,
    /*
     * PID 0x0D
     */
    TIMING_ADVANCE = 10,
    /*
     * PID 0x0E
     */
    MAF_AIR_FLOW_RATE = 11,
    /*
     * PID 0x10
     */
    THROTTLE_POSITION = 12,
    /*
     * PID 0x11
     */
    OXYGEN_SENSOR1_VOLTAGE = 13,
    /*
     * PID 0x14
     */
    OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM = 14,
    /*
     * PID 0x14
     */
    OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO = 15,
    /*
     * PID 0x24
     */
    OXYGEN_SENSOR2_VOLTAGE = 16,
    /*
     * PID 0x15
     */
    OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM = 17,
    /*
     * PID 0x15
     */
    OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO = 18,
    /*
     * PID 0x25
     */
    OXYGEN_SENSOR3_VOLTAGE = 19,
    /*
     * PID 0x16
     */
    OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM = 20,
    /*
     * PID 0x16
     */
    OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO = 21,
    /*
     * PID 0x26
     */
    OXYGEN_SENSOR4_VOLTAGE = 22,
    /*
     * PID 0x17
     */
    OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM = 23,
    /*
     * PID 0x17
     */
    OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO = 24,
    /*
     * PID 0x27
     */
    OXYGEN_SENSOR5_VOLTAGE = 25,
    /*
     * PID 0x18
     */
    OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM = 26,
    /*
     * PID 0x18
     */
    OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO = 27,
    /*
     * PID 0x28
     */
    OXYGEN_SENSOR6_VOLTAGE = 28,
    /*
     * PID 0x19
     */
    OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM = 29,
    /*
     * PID 0x19
     */
    OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO = 30,
    /*
     * PID 0x29
     */
    OXYGEN_SENSOR7_VOLTAGE = 31,
    /*
     * PID 0x1A
     */
    OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM = 32,
    /*
     * PID 0x1A
     */
    OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO = 33,
    /*
     * PID 0x2A
     */
    OXYGEN_SENSOR8_VOLTAGE = 34,
    /*
     * PID 0x1B
     */
    OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM = 35,
    /*
     * PID 0x1B
     */
    OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO = 36,
    /*
     * PID 0x2B
     */
    FUEL_RAIL_PRESSURE = 37,
    /*
     * PID 0x22
     */
    FUEL_RAIL_GAUGE_PRESSURE = 38,
    /*
     * PID 0x23
     */
    COMMANDED_EXHAUST_GAS_RECIRCULATION = 39,
    /*
     * PID 0x2C
     */
    EXHAUST_GAS_RECIRCULATION_ERROR = 40,
    /*
     * PID 0x2D
     */
    COMMANDED_EVAPORATIVE_PURGE = 41,
    /*
     * PID 0x2E
     */
    FUEL_TANK_LEVEL_INPUT = 42,
    /*
     * PID 0x2F
     */
    EVAPORATION_SYSTEM_VAPOR_PRESSURE = 43,
    /*
     * PID 0x32
     */
    CATALYST_TEMPERATURE_BANK1_SENSOR1 = 44,
    /*
     * PID 0x3C
     */
    CATALYST_TEMPERATURE_BANK2_SENSOR1 = 45,
    /*
     * PID 0x3D
     */
    CATALYST_TEMPERATURE_BANK1_SENSOR2 = 46,
    /*
     * PID 0x3E
     */
    CATALYST_TEMPERATURE_BANK2_SENSOR2 = 47,
    /*
     * PID 0x3F
     */
    ABSOLUTE_LOAD_VALUE = 48,
    /*
     * PID 0x43
     */
    FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO = 49,
    /*
     * PID 0x44
     */
    RELATIVE_THROTTLE_POSITION = 50,
    /*
     * PID 0x45
     */
    ABSOLUTE_THROTTLE_POSITION_B = 51,
    /*
     * PID 0x47
     */
    ABSOLUTE_THROTTLE_POSITION_C = 52,
    /*
     * PID 0x48
     */
    ACCELERATOR_PEDAL_POSITION_D = 53,
    /*
     * PID 0x49
     */
    ACCELERATOR_PEDAL_POSITION_E = 54,
    /*
     * PID 0x4A
     */
    ACCELERATOR_PEDAL_POSITION_F = 55,
    /*
     * PID 0x4B
     */
    COMMANDED_THROTTLE_ACTUATOR = 56,
    /*
     * PID 0x4C
     */
    ETHANOL_FUEL_PERCENTAGE = 57,
    /*
     * PID 0x52
     */
    ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE = 58,
    /*
     * PID 0x53
     */
    SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 59,
    /*
     * PID 0x55
     */
    SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 60,
    /*
     * PID 0x57
     */
    SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 61,
    /*
     * PID 0x55
     */
    SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 62,
    /*
     * PID 0x57
     */
    LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 63,
    /*
     * PID 0x56
     */
    LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 64,
    /*
     * PID 0x58
     */
    LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 65,
    /*
     * PID 0x56
     */
    LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 66,
    /*
     * PID 0x58
     */
    RELATIVE_ACCELERATOR_PEDAL_POSITION = 67,
    /*
     * PID 0x5A
     */
    HYBRID_BATTERY_PACK_REMAINING_LIFE = 68,
    /*
     * PID 0x5B
     */
    FUEL_INJECTION_TIMING = 69,
    /*
     * PID 0x5D
     */
    ENGINE_FUEL_RATE = 70,
    /*
     * PID 0x5E
     */
    LAST_SYSTEM_INDEX = 70 /* ENGINE_FUEL_RATE */,
};

/**
 * This enum lists the types of supported VMS messages. It is used as the first
 * integer in the vehicle property integers array and determines how the rest of
 * the message is decoded.
 */
enum class VmsMessageType : int32_t {
    /**
     * A request from the subscribers to the VMS service to subscribe to a layer.
     *
     * This message type uses enum VmsMessageWithLayerIntegerValuesIndex.
     */
    SUBSCRIBE = 1,
    /**
     * A request from the subscribers to the VMS service to subscribe to a layer from a specific publisher.
     *
     * This message type uses enum VmsMessageWithLayerAndPublisherIdIntegerValuesIndex.
     */
    SUBSCRIBE_TO_PUBLISHER = 2,
    /**
     * A request from the subscribers to the VMS service to unsubscribes from a layer.
     *
     * This message type uses enum VmsMessageWithLayerIntegerValuesIndex.
     */
    UNSUBSCRIBE = 3,
    /**
     * A request from the subscribers to the VMS service to unsubscribes from a layer from a specific publisher.
     *
     * This message type uses enum VmsMessageWithLayerAndPublisherIdIntegerValuesIndex.
     */
    UNSUBSCRIBE_TO_PUBLISHER = 4,
    /**
     * Information from the publishers to the VMS service about the layers which the client can publish.
     *
     * This message type uses enum VmsOfferingMessageIntegerValuesIndex.
     */
    OFFERING = 5,
    /**
     * A request from the subscribers to the VMS service to get the available layers.
     *
     * This message type uses enum VmsBaseMessageIntegerValuesIndex.
     */
    AVAILABILITY_REQUEST = 6,
    /**
     * A request from the publishers to the VMS service to get the layers with subscribers.
     *
     * This message type uses enum VmsBaseMessageIntegerValuesIndex.
     */
    SUBSCRIPTIONS_REQUEST = 7,
    /**
     * A response from the VMS service to the subscribers to a VmsMessageType.AVAILABILITY_REQUEST
     *
     * This message type uses enum VmsAvailabilityStateIntegerValuesIndex.
     */
    AVAILABILITY_RESPONSE = 8,
    /**
     * A notification from the VMS service to the subscribers on a change in the available layers.
     *
     * This message type uses enum VmsAvailabilityStateIntegerValuesIndex.
     */
    AVAILABILITY_CHANGE = 9,
    /**
     * A response from the VMS service to the publishers to a VmsMessageType.SUBSCRIPTIONS_REQUEST
     *
     * This message type uses enum VmsSubscriptionsStateIntegerValuesIndex.
     */
    SUBSCRIPTIONS_RESPONSE = 10,
    /**
     * A notification from the VMS service to the publishers on a change in the layers with subscribers.
     *
     * This message type uses enum VmsSubscriptionsStateIntegerValuesIndex.
     */
    SUBSCRIPTIONS_CHANGE = 11,
    /**
     * A message from the VMS service to the subscribers or from the publishers to the VMS service
     * with a serialized VMS data packet as defined in the VMS protocol.
     *
     * This message type uses enum VmsMessageWithLayerAndPublisherIdIntegerValuesIndex.
     */
    DATA = 12,
    /**
     * A request from the publishers to the VMS service to get a Publisher ID for a serialized VMS
     * provider description packet as defined in the VMS protocol.
     *
     * This message type uses enum VmsBaseMessageIntegerValuesIndex.
     */
    PUBLISHER_ID_REQUEST = 13,
    /**
     * A response from the VMS service to the publisher that contains a provider description packet
     * and the publisher ID assigned to it.
     *
     * This message type uses enum VmsPublisherInformationIntegerValuesIndex.
     */
    PUBLISHER_ID_RESPONSE = 14,
    /**
     * A request from the subscribers to the VMS service to get information for a Publisher ID.
     *
     * This message type uses enum VmsPublisherInformationIntegerValuesIndex.
     */
    PUBLISHER_INFORMATION_REQUEST = 15,
    /**
     * A response from the VMS service to the subscribers that contains a provider description packet
     * and the publisher ID assigned to it.
     *
     * This message type uses enum VmsPublisherInformationIntegerValuesIndex.
     */
    PUBLISHER_INFORMATION_RESPONSE = 16,
    /**
     * A notification indicating that the sender has been reset.
     *
     * The receiving party must reset its internal state and respond to the
     * sender with a START_SESSION message as acknowledgement.
     *
     * This message type uses enum VmsStartSessionMessageIntegerValuesIndex.
     */
    START_SESSION = 17,
    LAST_VMS_MESSAGE_TYPE = 17 /* START_SESSION */,
};

/**
 * Every VMS message starts with the type of the message from the VmsMessageType enum.
 * Messages with no parameters such as VmsMessageType.AVAILABILITY_REQUEST,
 * VmsMessageType.SUBSCRIPTIONS_REQUEST and VmsMessageType.DATA are also based on this enum.
 */
enum class VmsBaseMessageIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
};

/*
 * Handshake data sent as part of a VmsMessageType.START_SESSION message.
 *
 * A new session is initiated by sending a START_SESSION message with the
 * sender's identifier populated and the receiver's identifier set to -1.
 *
 * Identifier values are independently generated, but must be non-negative, and
 * increase monotonically between reboots.
 *
 * Upon receiving a START_SESSION with a mis-matching identifier, the receiver
 * must clear any cached VMS offering or subscription state and acknowledge the
 * new session by responding with a START_SESSION message that populates both
 * identifier fields.
 *
 * Any VMS messages received between initiation and completion of the handshake
 * must be discarded.
 */
enum class VmsStartSessionMessageIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    /*
     * Identifier field for the Android system service.
     */
    SERVICE_ID = 1,
    /*
     * Identifier field for the HAL client process.
     */
    CLIENT_ID = 2,
};

/*
 * A VMS message with a layer is sent as part of a VmsMessageType.SUBSCRIBE or
 * VmsMessageType.UNSUBSCRIBE messages.
 *
 * The layer type is defined in the VMS protocol, and the subtype and version are
 * controlled by the implementer of the publisher.
 */
enum class VmsMessageWithLayerIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    LAYER_TYPE = 1,
    LAYER_SUBTYPE = 2,
    LAYER_VERSION = 3,
};

/*
 * A VMS message with a layer and publisher ID is sent as part of a
 * VmsMessageType.SUBSCRIBE_TO_PUBLISHER, VmsMessageType.UNSUBSCRIBE_TO_PUBLISHER messages and
 * VmsMessageType.DATA .
 */
enum class VmsMessageWithLayerAndPublisherIdIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    LAYER_TYPE = 1,
    LAYER_SUBTYPE = 2,
    LAYER_VERSION = 3,
    PUBLISHER_ID = 4,
};

/*
 * An offering can be sent by publishers as part of VmsMessageType.OFFERING in order to
 * advertise which layers they can publish and under which constraints: e.g., I can publish Layer X
 * if someone else will publish Layer Y.
 * The offering contains the publisher ID which was assigned to the publisher by the VMS service.
 * A single offering is represented as:
 * - Layer type
 * - Layer subtype
 * - Layer version
 * - Number of dependencies (N)
 * - N x (Layer type, Layer subtype, Layer version)
 */
enum class VmsOfferingMessageIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    PUBLISHER_ID = 1,
    NUMBER_OF_OFFERS = 2,
    OFFERING_START = 3,
};

/**
 * A subscriptions state is sent to the publishers in response to a change in the subscriptions
 * as part of a VmsMessageType.SUBSCRIPTIONS_CHANGE, or in response to a
 * VmsMessageType.SUBSCRIPTIONS_REQUEST message as part of VmsMessageType.SUBSCRIPTIONS_RESPONSE.
 * The VMS service issues monotonically increasing sequence numbers, and in case a subscriber receives
 * a smaller sequnce number it should ignore the message.
 * The subscriptions are sent as a list of layers followed by a list of associated layers:
 * {Sequence number, N, M, N x layer, M x associated layer}
 * A subscribed layer is represented as three integers:
 * - Layer type
 * - Layer subtype
 * - Layer version
 * A subscribed associated layer is a layer with a list of publisher IDs. It is represented as:
 * - Layer type
 * - Layer subtype
 * - Layer version
 * - Number of publisher IDs (N)
 * - N x publisher ID
 */
enum class VmsSubscriptionsStateIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    SEQUENCE_NUMBER = 1,
    NUMBER_OF_LAYERS = 2,
    NUMBER_OF_ASSOCIATED_LAYERS = 3,
    SUBSCRIPTIONS_START = 4,
};

/**
 * An availability state is sent to the subscribers in response to a change in the available
 * layers as part of a VmsMessageType.AVAILABILITY_CHANGE message, or in response to a
 * VmsMessageType.AVAILABILITY_REQUEST message as part of a VmsMessageType.AVAILABILITY_RESPONSE.
 * The VMS service issues monotonically increasing sequence numbers, and in case a subscriber receives
 * a smaller sequnce number, it should ignore the message.
 * An available associated layer is a layer with a list of publisher IDs:
 * - Layer type
 * - Layer subtype
 * - Layer version
 * - Number of publisher IDs (N)
 * - N x publisher ID
 */
enum class VmsAvailabilityStateIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    SEQUENCE_NUMBER = 1,
    NUMBER_OF_ASSOCIATED_LAYERS = 2,
    LAYERS_START = 3,
};

/*
 * Publishers send the VMS service their information and assigned in response a publisher ID.
 * Subscribers can request the publisher information for a publisher ID they received in other messages.
 */
enum class VmsPublisherInformationIntegerValuesIndex : int32_t {
    /*
     * The message type as enumerated by VmsMessageType enum.
     */
    MESSAGE_TYPE = 0,
    PUBLISHER_ID = 1,
};

/**
 * Id of an Android user.
 *
 * Must be > 0 for valid ids, or -10000 (which is the same as Android.UserHandle.USER_NULL) when
 * it's not used.
 */
typedef int32_t UserId;

/**
 * Flags used to define the characteristics of an Android user.
 */
enum class UserFlags : int32_t {
    /**
     * No flags.
     */
    NONE = 0 /* 0x0 */,
    /**
     * System user.
     * On automotive, that user is always running, although never on foreground (except during
     * boot or exceptional circumstances).
     */
    SYSTEM = 1 /* 0x01 */,
    /**
     * Guest users have restrictions.
     */
    GUEST = 2 /* 0x02 */,
    /**
     * Ephemeral users have non-persistent state.
     */
    EPHEMERAL = 4 /* 0x04 */,
    /**
     * Admin users have additional privileges such as permission to create other users.
     */
    ADMIN = 8 /* 0x08 */,
    /**
     * Disabled users are marked for deletion.
     */
    DISABLED = 16 /* 0x10 */,
    /**
     * Profile user is a profile of another user.
     */
    PROFILE = 32 /* 0x20 */,
};

/**
 * Information about a specific Android user.
 */
struct UserInfo final {
    int32_t userId __attribute__ ((aligned(4)));
    ::android::hardware::automotive::vehicle::V2_0::UserFlags flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserInfo, userId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserInfo, flags) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserInfo) == 8, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserInfo) == 4, "wrong alignment");

/**
 * Information about all Android users.
 *
 * NOTE: this struct is not used in the HAL properties directly, it's part of other structs, which
 * in turn are converted to a VehiclePropValue.RawValue through libraries provided by the default
 * Vehicle HAL implementation.
 */
struct UsersInfo final {
    /**
     * The current foreground user.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo currentUser __attribute__ ((aligned(4)));
    /**
     * Number of existing users; includes the current user, recently removed users (with DISABLED
     * flag), and profile users (with PROFILE flag).
     */
    int32_t numberUsers __attribute__ ((aligned(4)));
    /**
     * List of existing users; includes the current user, recently removed users (with DISABLED
     * flag), and profile users (with PROFILE flag).
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::vehicle::V2_0::UserInfo> existingUsers __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UsersInfo, currentUser) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UsersInfo, numberUsers) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UsersInfo, existingUsers) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UsersInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UsersInfo) == 8, "wrong alignment");

/**
 * Id of a request related to user management.
 *
 * This id can be used by the Android system to map responses sent by the HAL, and vice-versa.
 *
 * For requests originated by Android, the value is positive (> 0), while for requests originated by
 * the HAL it must be negative (< 0).
 */
typedef int32_t UserRequestId;

/**
 * Defines when a INITIAL_USER_INFO request was made.
 */
enum class InitialUserInfoRequestType : int32_t {
    /**
     * At the first time Android was booted (or after a factory reset).
     */
    FIRST_BOOT = 1,
    /**
     * At the first time Android was booted after the system was updated.
     */
    FIRST_BOOT_AFTER_OTA = 2,
    /**
     * When Android was booted "from scratch".
     */
    COLD_BOOT = 3,
    /**
     * When Android was resumed after the system was suspended to memory.
     */
    RESUME = 4,
};

/**
 * Defines the format of a INITIAL_USER_INFO request made by the Android system.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct InitialUserInfoRequest final {
    /**
     * Arbitrary id used to map the HAL response to the request.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Type of request.
     */
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType requestType __attribute__ ((aligned(4)));
    /**
     * Information about the current state of the Android system.
     */
    ::android::hardware::automotive::vehicle::V2_0::UsersInfo usersInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest, requestType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest, usersInfo) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest) == 8, "wrong alignment");

/**
 * Defines which action the Android system should take in an INITIAL_USER_INFO request.
 */
enum class InitialUserInfoResponseAction : int32_t {
    /**
     * Let the Android System decide what to do.
     *
     * For example, it might create a new user on first boot, and switch to the last
     * active user afterwards.
     */
    DEFAULT = 0,
    /**
     * Switch to an existing Android user.
     */
    SWITCH = 1,
    /**
     * Create a new Android user (and switch to it).
     */
    CREATE = 2,
};

/**
 * Defines the format of a HAL response to a INITIAL_USER_INFO request.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct InitialUserInfoResponse final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * which action the Android system should take.
     */
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction action __attribute__ ((aligned(4)));
    /**
     * Information about the user that should be switched to or created.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo userToSwitchOrCreate __attribute__ ((aligned(4)));
    /**
     * System locales of the initial user (value will be passed as-is to
     * android.provider.Settings.System.SYSTEM_LOCALES)
     */
    ::android::hardware::hidl_string userLocales __attribute__ ((aligned(8)));
    /**
     * Name of the user that should be created.
     */
    ::android::hardware::hidl_string userNameToCreate __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse, action) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse, userToSwitchOrCreate) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse, userLocales) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse, userNameToCreate) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse) == 48, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse) == 8, "wrong alignment");

/**
 * Defines the reason a SWITCH_USER call was made.
 *
 * The meaning of each constant is explained in that property.
 */
enum class SwitchUserMessageType : int32_t {
    LEGACY_ANDROID_SWITCH = 1,
    ANDROID_SWITCH = 2,
    VEHICLE_RESPONSE = 3,
    VEHICLE_REQUEST = 4,
    ANDROID_POST_SWITCH = 5,
};

/**
 * Defines the format of a SWITCH_USER property.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct SwitchUserRequest final {
    /**
     * Arbitrary id used to map the response to the request.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Type of message.
     */
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType messageType __attribute__ ((aligned(4)));
    /**
     * Information about the Android user being switched to.
     *
     * Only the user id (but not the flags) should be set when the request is made by HAL.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo targetUser __attribute__ ((aligned(4)));
    /**
     * Information about the current state of the Android system.
     *
     * Should not be set when the request is made by HAL.
     */
    ::android::hardware::automotive::vehicle::V2_0::UsersInfo usersInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest, messageType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest, targetUser) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest, usersInfo) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest) == 48, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest) == 8, "wrong alignment");

/**
 * Status of the response to a SwitchUserRequest.
 */
enum class SwitchUserStatus : int32_t {
    /**
     * The request succeeded and the HAL user was switched.
     */
    SUCCESS = 1,
    /**
     * The request failed and the HAL user remained the same.
     */
    FAILURE = 2,
};

/**
 * Defines the result of a SwitchUserRequest.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct SwitchUserResponse final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Type of message.
     */
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType messageType __attribute__ ((aligned(4)));
    /**
     * Status of the request.
     */
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus status __attribute__ ((aligned(4)));
    /**
     * HAL-specific error message.
     *
     * This argument is optional, and when defined, it's passed "as-is" to the caller. It could be
     * used to show custom error messages to the end user.
     */
    ::android::hardware::hidl_string errorMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse, messageType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse, status) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse, errorMessage) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse) == 32, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse) == 8, "wrong alignment");

/**
 * Defines the format of a CREATE_USER property.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct CreateUserRequest final {
    /**
     * Arbitrary id used to map the response to the request.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Basic information about Android user that was created.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo newUserInfo __attribute__ ((aligned(4)));
    /**
     * Name of the new Android user.
     */
    ::android::hardware::hidl_string newUserName __attribute__ ((aligned(8)));
    /**
     * Information about the current state of the Android system.
     */
    ::android::hardware::automotive::vehicle::V2_0::UsersInfo usersInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest, newUserInfo) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest, newUserName) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest, usersInfo) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest) == 64, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::CreateUserRequest) == 8, "wrong alignment");

/**
 * Status of the response to a CreateUserRequest.
 */
enum class CreateUserStatus : int32_t {
    /**
     * The request succeeded (for example, HAL created a new internal user, or associated the
     * Android user to an existing internal user).
     */
    SUCCESS = 1,
    /**
     * The request failed (and Android will remove the Android user).
     */
    FAILURE = 2,
};

/**
 * Defines the result of a CreateUserRequest.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct CreateUserResponse final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Status of the request.
     */
    ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus status __attribute__ ((aligned(4)));
    /**
     * HAL-specific error message.
     *
     * This argument is optional, and when defined, it's passed "as-is" to the caller. It could be
     * used to show custom error messages to the end user.
     */
    ::android::hardware::hidl_string errorMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserResponse, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserResponse, status) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::CreateUserResponse, errorMessage) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::CreateUserResponse) == 24, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::CreateUserResponse) == 8, "wrong alignment");

/**
 * Defines the format of a REMOVE_USER property.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct RemoveUserRequest final {
    /**
     * Arbitrary id used to map the response to the request.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Information about the Android user that was removed.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo removedUserInfo __attribute__ ((aligned(4)));
    /**
     * Information about the current state of the Android system.
     */
    ::android::hardware::automotive::vehicle::V2_0::UsersInfo usersInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest, removedUserInfo) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest, usersInfo) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest) == 48, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest) == 8, "wrong alignment");

/**
 * Types of mechanisms used to identify an Android user.
 *
 * See USER_IDENTIFICATION_ASSOCIATION for more details and example.
 */
enum class UserIdentificationAssociationType : int32_t {
    /**
     * Key used to unlock the car.
     */
    KEY_FOB = 1,
    /**
     * Custom mechanism defined by the OEM.
     */
    CUSTOM_1 = 101,
    /**
     * Custom mechanism defined by the OEM.
     */
    CUSTOM_2 = 102,
    /**
     * Custom mechanism defined by the OEM.
     */
    CUSTOM_3 = 103,
    /**
     * Custom mechanism defined by the OEM.
     */
    CUSTOM_4 = 104,
};

/**
 * Whether a UserIdentificationAssociationType is associate with an Android user.
 */
enum class UserIdentificationAssociationValue : int32_t {
    /**
     * Used when the status of an association could not be determined.
     *
     * For example, in a set() request, it would indicate a failure to set the given type.
     */
    UNKNOWN = 1,
    /**
     * The identification type is associated with the current foreground Android user.
     */
    ASSOCIATED_CURRENT_USER = 2,
    /**
     * The identification type is associated with another Android user.
     */
    ASSOCIATED_ANOTHER_USER = 3,
    /**
     * The identification type is not associated with any Android user.
     */
    NOT_ASSOCIATED_ANY_USER = 4,
};

/**
 * Used to set a UserIdentificationAssociationType with an Android user.
 */
enum class UserIdentificationAssociationSetValue : int32_t {
    /**
     * Associate the identification type with the current foreground Android user.
     */
    ASSOCIATE_CURRENT_USER = 1,
    /**
     * Disassociate the identification type from the current foreground Android user.
     */
    DISASSOCIATE_CURRENT_USER = 2,
    /**
     * Disassociate the identification type from all Android users.
     */
    DISASSOCIATE_ALL_USERS = 3,
};

/**
 * Defines the format of a get() call to USER_IDENTIFICATION_ASSOCIATION.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct UserIdentificationGetRequest final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Information about the current foreground Android user.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo userInfo __attribute__ ((aligned(4)));
    /**
     * Number of association being queried.
     */
    int32_t numberAssociationTypes __attribute__ ((aligned(4)));
    /**
     * Types of association being queried.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType> associationTypes __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest, userInfo) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest, numberAssociationTypes) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest, associationTypes) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest) == 32, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest) == 8, "wrong alignment");

/**
 * Defines the format of a set() call to USER_IDENTIFICATION_ASSOCIATION.
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct UserIdentificationSetRequest final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Information about the current foreground Android user.
     */
    ::android::hardware::automotive::vehicle::V2_0::UserInfo userInfo __attribute__ ((aligned(4)));
    /**
     * Number of association being set.
     */
    int32_t numberAssociations __attribute__ ((aligned(4)));
    /**
     * Associations being set.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation> associations __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest, userInfo) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest, numberAssociations) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest, associations) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest) == 32, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest) == 8, "wrong alignment");

/**
 * Defines the result of a USER_IDENTIFICATION_ASSOCIATION - both for get() and set().
 *
 * NOTE: this struct is not used in the HAL properties directly, it must be converted to
 * VehiclePropValue.RawValue through libraries provided by the default Vehicle HAL implementation.
 */
struct UserIdentificationResponse final {
    /**
     * Id of the request being responded.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * Number of associations being returned.
     */
    int32_t numberAssociation __attribute__ ((aligned(4)));
    /**
     * Values associated with the user.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation> associations __attribute__ ((aligned(8)));
    /**
     * HAL-specific error message.
     *
     * This argument is optional, and when defined, it's passed "as-is" to the caller. It could be
     * used to show custom error messages to the end user.
     */
    ::android::hardware::hidl_string errorMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse, numberAssociation) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse, associations) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse, errorMessage) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse) == 8, "wrong alignment");

/**
 * Helper struct used when getting a user/identification association type.
 */
struct UserIdentificationAssociation final {
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType type __attribute__ ((aligned(4)));
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue value __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation, value) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation) == 8, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation) == 4, "wrong alignment");

/**
 * Helper struct used when setting a user/identification association type.
 */
struct UserIdentificationSetAssociation final {
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType type __attribute__ ((aligned(4)));
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue value __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation, value) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation) == 8, "wrong size");
static_assert(__alignof(::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation) == 4, "wrong alignment");

/**
 * A rotary control which can rotate without limits. These controls use HW_ROTARY_INPUT to report
 * relative clockwise or counterclockwise motion. They have no absolute position.
 */
enum class RotaryInputType : int32_t {
    /**
     * Main rotary control, typically in the center console, used to navigate the user interface.
     */
    ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION = 0,
    /**
     * Volume control for adjusting audio volume.
     */
    ROTARY_INPUT_TYPE_AUDIO_VOLUME = 1,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleArea o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleArea o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleArea lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleArea lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleArea lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleArea lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleArea e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleProperty o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleProperty o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleProperty e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleLightState o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleLightState o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightState e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::EvConnectorType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::EvConnectorType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType lhs, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType lhs, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::EvConnectorType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::PortLocationType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::PortLocationType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::PortLocationType lhs, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::PortLocationType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::PortLocationType lhs, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::PortLocationType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::PortLocationType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::FuelType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::FuelType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::FuelType lhs, const ::android::hardware::automotive::vehicle::V2_0::FuelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::FuelType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::FuelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::FuelType lhs, const ::android::hardware::automotive::vehicle::V2_0::FuelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::FuelType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::FuelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::FuelType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::FuelType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleUnit o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleUnit o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleUnit e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleGear o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleGear o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleGear lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleGear lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleGear lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleGear lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleGear e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::StatusCode o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::StatusCode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::StatusCode lhs, const ::android::hardware::automotive::vehicle::V2_0::StatusCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::StatusCode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::StatusCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::StatusCode lhs, const ::android::hardware::automotive::vehicle::V2_0::StatusCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::StatusCode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::StatusCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::StatusCode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::StatusCode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserFlags o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserFlags o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserFlags lhs, const ::android::hardware::automotive::vehicle::V2_0::UserFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserFlags rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserFlags lhs, const ::android::hardware::automotive::vehicle::V2_0::UserFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserFlags rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserFlags e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserFlags e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserInfo& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserInfo& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& rhs);

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& o);
static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& rhs);
static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::RotaryInputType o);
static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::RotaryInputType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType lhs, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType lhs, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::vehicle::V2_0::RotaryInputType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::STRING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::STRING)) {
        os += (first ? "" : " | ");
        os += "STRING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::STRING;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BOOLEAN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BOOLEAN)) {
        os += (first ? "" : " | ");
        os += "BOOLEAN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BOOLEAN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32)) {
        os += (first ? "" : " | ");
        os += "INT32";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32_VEC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32_VEC)) {
        os += (first ? "" : " | ");
        os += "INT32_VEC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32_VEC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64)) {
        os += (first ? "" : " | ");
        os += "INT64";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64_VEC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64_VEC)) {
        os += (first ? "" : " | ");
        os += "INT64_VEC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64_VEC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT)) {
        os += (first ? "" : " | ");
        os += "FLOAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT_VEC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT_VEC)) {
        os += (first ? "" : " | ");
        os += "FLOAT_VEC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT_VEC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BYTES) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BYTES)) {
        os += (first ? "" : " | ");
        os += "BYTES";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BYTES;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MIXED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MIXED)) {
        os += (first ? "" : " | ");
        os += "MIXED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MIXED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MASK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MASK)) {
        os += (first ? "" : " | ");
        os += "MASK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MASK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::STRING) {
        return "STRING";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BOOLEAN) {
        return "BOOLEAN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32) {
        return "INT32";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32_VEC) {
        return "INT32_VEC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64) {
        return "INT64";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64_VEC) {
        return "INT64_VEC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT) {
        return "FLOAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT_VEC) {
        return "FLOAT_VEC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BYTES) {
        return "BYTES";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MIXED) {
        return "MIXED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MASK) {
        return "MASK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleArea>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleArea> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::GLOBAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::GLOBAL)) {
        os += (first ? "" : " | ");
        os += "GLOBAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::GLOBAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WINDOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::WINDOW)) {
        os += (first ? "" : " | ");
        os += "WINDOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WINDOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MIRROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::MIRROR)) {
        os += (first ? "" : " | ");
        os += "MIRROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MIRROR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::SEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::SEAT)) {
        os += (first ? "" : " | ");
        os += "SEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::SEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::DOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::DOOR)) {
        os += (first ? "" : " | ");
        os += "DOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::DOOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WHEEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::WHEEL)) {
        os += (first ? "" : " | ");
        os += "WHEEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WHEEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MASK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleArea::MASK)) {
        os += (first ? "" : " | ");
        os += "MASK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MASK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleArea o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::GLOBAL) {
        return "GLOBAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WINDOW) {
        return "WINDOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MIRROR) {
        return "MIRROR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::SEAT) {
        return "SEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::DOOR) {
        return "DOOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WHEEL) {
        return "WHEEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MASK) {
        return "MASK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleArea o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::SYSTEM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::SYSTEM)) {
        os += (first ? "" : " | ");
        os += "SYSTEM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::SYSTEM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::VENDOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::VENDOR)) {
        os += (first ? "" : " | ");
        os += "VENDOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::VENDOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::MASK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::MASK)) {
        os += (first ? "" : " | ");
        os += "MASK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::MASK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::SYSTEM) {
        return "SYSTEM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::VENDOR) {
        return "VENDOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::MASK) {
        return "MASK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleProperty>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleProperty> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INVALID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INVALID;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_VIN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_VIN)) {
        os += (first ? "" : " | ");
        os += "INFO_VIN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_VIN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MAKE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MAKE)) {
        os += (first ? "" : " | ");
        os += "INFO_MAKE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MAKE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL)) {
        os += (first ? "" : " | ");
        os += "INFO_MODEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL_YEAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL_YEAR)) {
        os += (first ? "" : " | ");
        os += "INFO_MODEL_YEAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL_YEAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_CAPACITY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_CAPACITY)) {
        os += (first ? "" : " | ");
        os += "INFO_FUEL_CAPACITY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_CAPACITY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_TYPE)) {
        os += (first ? "" : " | ");
        os += "INFO_FUEL_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_BATTERY_CAPACITY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_BATTERY_CAPACITY)) {
        os += (first ? "" : " | ");
        os += "INFO_EV_BATTERY_CAPACITY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_BATTERY_CAPACITY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_CONNECTOR_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_CONNECTOR_TYPE)) {
        os += (first ? "" : " | ");
        os += "INFO_EV_CONNECTOR_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_CONNECTOR_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_DOOR_LOCATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_DOOR_LOCATION)) {
        os += (first ? "" : " | ");
        os += "INFO_FUEL_DOOR_LOCATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_DOOR_LOCATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_PORT_LOCATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_PORT_LOCATION)) {
        os += (first ? "" : " | ");
        os += "INFO_EV_PORT_LOCATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_PORT_LOCATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_DRIVER_SEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_DRIVER_SEAT)) {
        os += (first ? "" : " | ");
        os += "INFO_DRIVER_SEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_DRIVER_SEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EXTERIOR_DIMENSIONS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EXTERIOR_DIMENSIONS)) {
        os += (first ? "" : " | ");
        os += "INFO_EXTERIOR_DIMENSIONS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EXTERIOR_DIMENSIONS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MULTI_EV_PORT_LOCATIONS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MULTI_EV_PORT_LOCATIONS)) {
        os += (first ? "" : " | ");
        os += "INFO_MULTI_EV_PORT_LOCATIONS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MULTI_EV_PORT_LOCATIONS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_ODOMETER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_ODOMETER)) {
        os += (first ? "" : " | ");
        os += "PERF_ODOMETER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_ODOMETER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED)) {
        os += (first ? "" : " | ");
        os += "PERF_VEHICLE_SPEED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY)) {
        os += (first ? "" : " | ");
        os += "PERF_VEHICLE_SPEED_DISPLAY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_STEERING_ANGLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_STEERING_ANGLE)) {
        os += (first ? "" : " | ");
        os += "PERF_STEERING_ANGLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_STEERING_ANGLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_REAR_STEERING_ANGLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_REAR_STEERING_ANGLE)) {
        os += (first ? "" : " | ");
        os += "PERF_REAR_STEERING_ANGLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_REAR_STEERING_ANGLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_COOLANT_TEMP) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_COOLANT_TEMP)) {
        os += (first ? "" : " | ");
        os += "ENGINE_COOLANT_TEMP";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_COOLANT_TEMP;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_LEVEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ENGINE_OIL_LEVEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_LEVEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_TEMP) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_TEMP)) {
        os += (first ? "" : " | ");
        os += "ENGINE_OIL_TEMP";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_TEMP;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_RPM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_RPM)) {
        os += (first ? "" : " | ");
        os += "ENGINE_RPM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_RPM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WHEEL_TICK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WHEEL_TICK)) {
        os += (first ? "" : " | ");
        os += "WHEEL_TICK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WHEEL_TICK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL)) {
        os += (first ? "" : " | ");
        os += "FUEL_LEVEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_DOOR_OPEN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_DOOR_OPEN)) {
        os += (first ? "" : " | ");
        os += "FUEL_DOOR_OPEN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_DOOR_OPEN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_LEVEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_LEVEL)) {
        os += (first ? "" : " | ");
        os += "EV_BATTERY_LEVEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_LEVEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_OPEN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_OPEN)) {
        os += (first ? "" : " | ");
        os += "EV_CHARGE_PORT_OPEN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_OPEN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_CONNECTED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_CONNECTED)) {
        os += (first ? "" : " | ");
        os += "EV_CHARGE_PORT_CONNECTED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_CONNECTED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_INSTANTANEOUS_CHARGE_RATE)) {
        os += (first ? "" : " | ");
        os += "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_INSTANTANEOUS_CHARGE_RATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::RANGE_REMAINING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::RANGE_REMAINING)) {
        os += (first ? "" : " | ");
        os += "RANGE_REMAINING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::RANGE_REMAINING;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "TIRE_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::GEAR_SELECTION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::GEAR_SELECTION)) {
        os += (first ? "" : " | ");
        os += "GEAR_SELECTION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::GEAR_SELECTION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CURRENT_GEAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CURRENT_GEAR)) {
        os += (first ? "" : " | ");
        os += "CURRENT_GEAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CURRENT_GEAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_ON)) {
        os += (first ? "" : " | ");
        os += "PARKING_BRAKE_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_AUTO_APPLY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_AUTO_APPLY)) {
        os += (first ? "" : " | ");
        os += "PARKING_BRAKE_AUTO_APPLY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_AUTO_APPLY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL_LOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL_LOW)) {
        os += (first ? "" : " | ");
        os += "FUEL_LEVEL_LOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL_LOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::NIGHT_MODE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::NIGHT_MODE)) {
        os += (first ? "" : " | ");
        os += "NIGHT_MODE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::NIGHT_MODE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TURN_SIGNAL_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TURN_SIGNAL_STATE)) {
        os += (first ? "" : " | ");
        os += "TURN_SIGNAL_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TURN_SIGNAL_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::IGNITION_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::IGNITION_STATE)) {
        os += (first ? "" : " | ");
        os += "IGNITION_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::IGNITION_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ABS_ACTIVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ABS_ACTIVE)) {
        os += (first ? "" : " | ");
        os += "ABS_ACTIVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ABS_ACTIVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TRACTION_CONTROL_ACTIVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TRACTION_CONTROL_ACTIVE)) {
        os += (first ? "" : " | ");
        os += "TRACTION_CONTROL_ACTIVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TRACTION_CONTROL_ACTIVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_SPEED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_SPEED)) {
        os += (first ? "" : " | ");
        os += "HVAC_FAN_SPEED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_SPEED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION)) {
        os += (first ? "" : " | ");
        os += "HVAC_FAN_DIRECTION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_CURRENT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_CURRENT)) {
        os += (first ? "" : " | ");
        os += "HVAC_TEMPERATURE_CURRENT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_CURRENT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_SET) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_SET)) {
        os += (first ? "" : " | ");
        os += "HVAC_TEMPERATURE_SET";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_SET;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DEFROSTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DEFROSTER)) {
        os += (first ? "" : " | ");
        os += "HVAC_DEFROSTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DEFROSTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AC_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AC_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_AC_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AC_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_AC_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_AC_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_MAX_AC_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_AC_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_DEFROST_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_DEFROST_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_MAX_DEFROST_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_DEFROST_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_RECIRC_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_RECIRC_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_RECIRC_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_RECIRC_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DUAL_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DUAL_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_DUAL_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DUAL_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_AUTO_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "HVAC_SEAT_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SIDE_MIRROR_HEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SIDE_MIRROR_HEAT)) {
        os += (first ? "" : " | ");
        os += "HVAC_SIDE_MIRROR_HEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SIDE_MIRROR_HEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_STEERING_WHEEL_HEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_STEERING_WHEEL_HEAT)) {
        os += (first ? "" : " | ");
        os += "HVAC_STEERING_WHEEL_HEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_STEERING_WHEEL_HEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "HVAC_TEMPERATURE_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ACTUAL_FAN_SPEED_RPM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ACTUAL_FAN_SPEED_RPM)) {
        os += (first ? "" : " | ");
        os += "HVAC_ACTUAL_FAN_SPEED_RPM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ACTUAL_FAN_SPEED_RPM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_POWER_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_POWER_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_POWER_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_POWER_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "HVAC_FAN_DIRECTION_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_RECIRC_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_RECIRC_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_AUTO_RECIRC_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_RECIRC_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_VENTILATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_VENTILATION)) {
        os += (first ? "" : " | ");
        os += "HVAC_SEAT_VENTILATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_VENTILATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON)) {
        os += (first ? "" : " | ");
        os += "HVAC_ELECTRIC_DEFROSTER_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISTANCE_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISTANCE_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "DISTANCE_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISTANCE_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_VOLUME_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_VOLUME_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "FUEL_VOLUME_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_VOLUME_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "TIRE_PRESSURE_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "EV_BATTERY_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME)) {
        os += (first ? "" : " | ");
        os += "FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_SPEED_DISPLAY_UNITS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_SPEED_DISPLAY_UNITS)) {
        os += (first ? "" : " | ");
        os += "VEHICLE_SPEED_DISPLAY_UNITS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_SPEED_DISPLAY_UNITS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENV_OUTSIDE_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENV_OUTSIDE_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ENV_OUTSIDE_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENV_OUTSIDE_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REQ) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REQ)) {
        os += (first ? "" : " | ");
        os += "AP_POWER_STATE_REQ";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REQ;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REPORT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REPORT)) {
        os += (first ? "" : " | ");
        os += "AP_POWER_STATE_REPORT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REPORT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_BOOTUP_REASON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_BOOTUP_REASON)) {
        os += (first ? "" : " | ");
        os += "AP_POWER_BOOTUP_REASON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_BOOTUP_REASON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISPLAY_BRIGHTNESS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISPLAY_BRIGHTNESS)) {
        os += (first ? "" : " | ");
        os += "DISPLAY_BRIGHTNESS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISPLAY_BRIGHTNESS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_KEY_INPUT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_KEY_INPUT)) {
        os += (first ? "" : " | ");
        os += "HW_KEY_INPUT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_KEY_INPUT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_ROTARY_INPUT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_ROTARY_INPUT)) {
        os += (first ? "" : " | ");
        os += "HW_ROTARY_INPUT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_ROTARY_INPUT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_POS)) {
        os += (first ? "" : " | ");
        os += "DOOR_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_MOVE)) {
        os += (first ? "" : " | ");
        os += "DOOR_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_LOCK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_LOCK)) {
        os += (first ? "" : " | ");
        os += "DOOR_LOCK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_LOCK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_POS)) {
        os += (first ? "" : " | ");
        os += "MIRROR_Z_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_MOVE)) {
        os += (first ? "" : " | ");
        os += "MIRROR_Z_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_POS)) {
        os += (first ? "" : " | ");
        os += "MIRROR_Y_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_MOVE)) {
        os += (first ? "" : " | ");
        os += "MIRROR_Y_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_LOCK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_LOCK)) {
        os += (first ? "" : " | ");
        os += "MIRROR_LOCK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_LOCK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_FOLD) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_FOLD)) {
        os += (first ? "" : " | ");
        os += "MIRROR_FOLD";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_FOLD;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SELECT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SELECT)) {
        os += (first ? "" : " | ");
        os += "SEAT_MEMORY_SELECT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SELECT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SET) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SET)) {
        os += (first ? "" : " | ");
        os += "SEAT_MEMORY_SET";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SET;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_BUCKLED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_BUCKLED)) {
        os += (first ? "" : " | ");
        os += "SEAT_BELT_BUCKLED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_BUCKLED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_BELT_HEIGHT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_BELT_HEIGHT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_FORE_AFT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_FORE_AFT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_BACKREST_ANGLE_1_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_BACKREST_ANGLE_1_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_BACKREST_ANGLE_2_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_BACKREST_ANGLE_2_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEIGHT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEIGHT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_DEPTH_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_DEPTH_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_TILT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_TILT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_LUMBAR_FORE_AFT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_LUMBAR_FORE_AFT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_LUMBAR_SIDE_SUPPORT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_LUMBAR_SIDE_SUPPORT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_HEIGHT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_HEIGHT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_ANGLE_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_ANGLE_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_POS)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_FORE_AFT_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_MOVE)) {
        os += (first ? "" : " | ");
        os += "SEAT_HEADREST_FORE_AFT_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_OCCUPANCY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_OCCUPANCY)) {
        os += (first ? "" : " | ");
        os += "SEAT_OCCUPANCY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_OCCUPANCY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_POS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_POS)) {
        os += (first ? "" : " | ");
        os += "WINDOW_POS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_POS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_MOVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_MOVE)) {
        os += (first ? "" : " | ");
        os += "WINDOW_MOVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_MOVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_LOCK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_LOCK)) {
        os += (first ? "" : " | ");
        os += "WINDOW_LOCK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_LOCK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_MAP_SERVICE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_MAP_SERVICE)) {
        os += (first ? "" : " | ");
        os += "VEHICLE_MAP_SERVICE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_MAP_SERVICE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_LIVE_FRAME) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_LIVE_FRAME)) {
        os += (first ? "" : " | ");
        os += "OBD2_LIVE_FRAME";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_LIVE_FRAME;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME)) {
        os += (first ? "" : " | ");
        os += "OBD2_FREEZE_FRAME";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_INFO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_INFO)) {
        os += (first ? "" : " | ");
        os += "OBD2_FREEZE_FRAME_INFO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_INFO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_CLEAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_CLEAR)) {
        os += (first ? "" : " | ");
        os += "OBD2_FREEZE_FRAME_CLEAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_CLEAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "HEADLIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "HIGH_BEAM_LIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "FOG_LIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "HAZARD_LIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "HEADLIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "HIGH_BEAM_LIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "FOG_LIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "HAZARD_LIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "CABIN_LIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "CABIN_LIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_STATE)) {
        os += (first ? "" : " | ");
        os += "READING_LIGHTS_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_SWITCH)) {
        os += (first ? "" : " | ");
        os += "READING_LIGHTS_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SUPPORT_CUSTOMIZE_VENDOR_PERMISSION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SUPPORT_CUSTOMIZE_VENDOR_PERMISSION)) {
        os += (first ? "" : " | ");
        os += "SUPPORT_CUSTOMIZE_VENDOR_PERMISSION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SUPPORT_CUSTOMIZE_VENDOR_PERMISSION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISABLED_OPTIONAL_FEATURES) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISABLED_OPTIONAL_FEATURES)) {
        os += (first ? "" : " | ");
        os += "DISABLED_OPTIONAL_FEATURES";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISABLED_OPTIONAL_FEATURES;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INITIAL_USER_INFO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INITIAL_USER_INFO)) {
        os += (first ? "" : " | ");
        os += "INITIAL_USER_INFO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INITIAL_USER_INFO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SWITCH_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SWITCH_USER)) {
        os += (first ? "" : " | ");
        os += "SWITCH_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SWITCH_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CREATE_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CREATE_USER)) {
        os += (first ? "" : " | ");
        os += "CREATE_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CREATE_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::REMOVE_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::REMOVE_USER)) {
        os += (first ? "" : " | ");
        os += "REMOVE_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::REMOVE_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::USER_IDENTIFICATION_ASSOCIATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleProperty::USER_IDENTIFICATION_ASSOCIATION)) {
        os += (first ? "" : " | ");
        os += "USER_IDENTIFICATION_ASSOCIATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::USER_IDENTIFICATION_ASSOCIATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleProperty o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_VIN) {
        return "INFO_VIN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MAKE) {
        return "INFO_MAKE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL) {
        return "INFO_MODEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL_YEAR) {
        return "INFO_MODEL_YEAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_CAPACITY) {
        return "INFO_FUEL_CAPACITY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_TYPE) {
        return "INFO_FUEL_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_BATTERY_CAPACITY) {
        return "INFO_EV_BATTERY_CAPACITY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_CONNECTOR_TYPE) {
        return "INFO_EV_CONNECTOR_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_DOOR_LOCATION) {
        return "INFO_FUEL_DOOR_LOCATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_PORT_LOCATION) {
        return "INFO_EV_PORT_LOCATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_DRIVER_SEAT) {
        return "INFO_DRIVER_SEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EXTERIOR_DIMENSIONS) {
        return "INFO_EXTERIOR_DIMENSIONS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MULTI_EV_PORT_LOCATIONS) {
        return "INFO_MULTI_EV_PORT_LOCATIONS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_ODOMETER) {
        return "PERF_ODOMETER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED) {
        return "PERF_VEHICLE_SPEED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY) {
        return "PERF_VEHICLE_SPEED_DISPLAY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_STEERING_ANGLE) {
        return "PERF_STEERING_ANGLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_REAR_STEERING_ANGLE) {
        return "PERF_REAR_STEERING_ANGLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_COOLANT_TEMP) {
        return "ENGINE_COOLANT_TEMP";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_LEVEL) {
        return "ENGINE_OIL_LEVEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_TEMP) {
        return "ENGINE_OIL_TEMP";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_RPM) {
        return "ENGINE_RPM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WHEEL_TICK) {
        return "WHEEL_TICK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL) {
        return "FUEL_LEVEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_DOOR_OPEN) {
        return "FUEL_DOOR_OPEN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_LEVEL) {
        return "EV_BATTERY_LEVEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_OPEN) {
        return "EV_CHARGE_PORT_OPEN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_CONNECTED) {
        return "EV_CHARGE_PORT_CONNECTED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) {
        return "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::RANGE_REMAINING) {
        return "RANGE_REMAINING";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE) {
        return "TIRE_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::GEAR_SELECTION) {
        return "GEAR_SELECTION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CURRENT_GEAR) {
        return "CURRENT_GEAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_ON) {
        return "PARKING_BRAKE_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_AUTO_APPLY) {
        return "PARKING_BRAKE_AUTO_APPLY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL_LOW) {
        return "FUEL_LEVEL_LOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::NIGHT_MODE) {
        return "NIGHT_MODE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TURN_SIGNAL_STATE) {
        return "TURN_SIGNAL_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::IGNITION_STATE) {
        return "IGNITION_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ABS_ACTIVE) {
        return "ABS_ACTIVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TRACTION_CONTROL_ACTIVE) {
        return "TRACTION_CONTROL_ACTIVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_SPEED) {
        return "HVAC_FAN_SPEED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION) {
        return "HVAC_FAN_DIRECTION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_CURRENT) {
        return "HVAC_TEMPERATURE_CURRENT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_SET) {
        return "HVAC_TEMPERATURE_SET";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DEFROSTER) {
        return "HVAC_DEFROSTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AC_ON) {
        return "HVAC_AC_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_AC_ON) {
        return "HVAC_MAX_AC_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_DEFROST_ON) {
        return "HVAC_MAX_DEFROST_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_RECIRC_ON) {
        return "HVAC_RECIRC_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DUAL_ON) {
        return "HVAC_DUAL_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_ON) {
        return "HVAC_AUTO_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_TEMPERATURE) {
        return "HVAC_SEAT_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SIDE_MIRROR_HEAT) {
        return "HVAC_SIDE_MIRROR_HEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_STEERING_WHEEL_HEAT) {
        return "HVAC_STEERING_WHEEL_HEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_DISPLAY_UNITS) {
        return "HVAC_TEMPERATURE_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ACTUAL_FAN_SPEED_RPM) {
        return "HVAC_ACTUAL_FAN_SPEED_RPM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_POWER_ON) {
        return "HVAC_POWER_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION_AVAILABLE) {
        return "HVAC_FAN_DIRECTION_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_RECIRC_ON) {
        return "HVAC_AUTO_RECIRC_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_VENTILATION) {
        return "HVAC_SEAT_VENTILATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON) {
        return "HVAC_ELECTRIC_DEFROSTER_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISTANCE_DISPLAY_UNITS) {
        return "DISTANCE_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_VOLUME_DISPLAY_UNITS) {
        return "FUEL_VOLUME_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE_DISPLAY_UNITS) {
        return "TIRE_PRESSURE_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_DISPLAY_UNITS) {
        return "EV_BATTERY_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME) {
        return "FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_SPEED_DISPLAY_UNITS) {
        return "VEHICLE_SPEED_DISPLAY_UNITS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENV_OUTSIDE_TEMPERATURE) {
        return "ENV_OUTSIDE_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REQ) {
        return "AP_POWER_STATE_REQ";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REPORT) {
        return "AP_POWER_STATE_REPORT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_BOOTUP_REASON) {
        return "AP_POWER_BOOTUP_REASON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISPLAY_BRIGHTNESS) {
        return "DISPLAY_BRIGHTNESS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_KEY_INPUT) {
        return "HW_KEY_INPUT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_ROTARY_INPUT) {
        return "HW_ROTARY_INPUT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_POS) {
        return "DOOR_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_MOVE) {
        return "DOOR_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_LOCK) {
        return "DOOR_LOCK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_POS) {
        return "MIRROR_Z_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_MOVE) {
        return "MIRROR_Z_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_POS) {
        return "MIRROR_Y_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_MOVE) {
        return "MIRROR_Y_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_LOCK) {
        return "MIRROR_LOCK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_FOLD) {
        return "MIRROR_FOLD";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SELECT) {
        return "SEAT_MEMORY_SELECT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SET) {
        return "SEAT_MEMORY_SET";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_BUCKLED) {
        return "SEAT_BELT_BUCKLED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_POS) {
        return "SEAT_BELT_HEIGHT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_MOVE) {
        return "SEAT_BELT_HEIGHT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_POS) {
        return "SEAT_FORE_AFT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_MOVE) {
        return "SEAT_FORE_AFT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_POS) {
        return "SEAT_BACKREST_ANGLE_1_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_MOVE) {
        return "SEAT_BACKREST_ANGLE_1_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_POS) {
        return "SEAT_BACKREST_ANGLE_2_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_MOVE) {
        return "SEAT_BACKREST_ANGLE_2_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_POS) {
        return "SEAT_HEIGHT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_MOVE) {
        return "SEAT_HEIGHT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_POS) {
        return "SEAT_DEPTH_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_MOVE) {
        return "SEAT_DEPTH_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_POS) {
        return "SEAT_TILT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_MOVE) {
        return "SEAT_TILT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_POS) {
        return "SEAT_LUMBAR_FORE_AFT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_MOVE) {
        return "SEAT_LUMBAR_FORE_AFT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_POS) {
        return "SEAT_LUMBAR_SIDE_SUPPORT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_MOVE) {
        return "SEAT_LUMBAR_SIDE_SUPPORT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_POS) {
        return "SEAT_HEADREST_HEIGHT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_MOVE) {
        return "SEAT_HEADREST_HEIGHT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_POS) {
        return "SEAT_HEADREST_ANGLE_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_MOVE) {
        return "SEAT_HEADREST_ANGLE_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_POS) {
        return "SEAT_HEADREST_FORE_AFT_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_MOVE) {
        return "SEAT_HEADREST_FORE_AFT_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_OCCUPANCY) {
        return "SEAT_OCCUPANCY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_POS) {
        return "WINDOW_POS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_MOVE) {
        return "WINDOW_MOVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_LOCK) {
        return "WINDOW_LOCK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_MAP_SERVICE) {
        return "VEHICLE_MAP_SERVICE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_LIVE_FRAME) {
        return "OBD2_LIVE_FRAME";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME) {
        return "OBD2_FREEZE_FRAME";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_INFO) {
        return "OBD2_FREEZE_FRAME_INFO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_CLEAR) {
        return "OBD2_FREEZE_FRAME_CLEAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_STATE) {
        return "HEADLIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_STATE) {
        return "HIGH_BEAM_LIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_STATE) {
        return "FOG_LIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_STATE) {
        return "HAZARD_LIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_SWITCH) {
        return "HEADLIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_SWITCH) {
        return "HIGH_BEAM_LIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_SWITCH) {
        return "FOG_LIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_SWITCH) {
        return "HAZARD_LIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_STATE) {
        return "CABIN_LIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_SWITCH) {
        return "CABIN_LIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_STATE) {
        return "READING_LIGHTS_STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_SWITCH) {
        return "READING_LIGHTS_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SUPPORT_CUSTOMIZE_VENDOR_PERMISSION) {
        return "SUPPORT_CUSTOMIZE_VENDOR_PERMISSION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISABLED_OPTIONAL_FEATURES) {
        return "DISABLED_OPTIONAL_FEATURES";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INITIAL_USER_INFO) {
        return "INITIAL_USER_INFO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SWITCH_USER) {
        return "SWITCH_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CREATE_USER) {
        return "CREATE_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::REMOVE_USER) {
        return "REMOVE_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::USER_IDENTIFICATION_ASSOCIATION) {
        return "USER_IDENTIFICATION_ASSOCIATION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleProperty o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_DEFAULT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_DEFAULT)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_DEFAULT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_DEFAULT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_WINDOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_WINDOW)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_WINDOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_WINDOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_WINDOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_WINDOW)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_WINDOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_WINDOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_DOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_DOOR)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_DOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_DOOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_DOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_DOOR)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_DOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_DOOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_SEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_SEAT)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_SEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_SEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_SEAT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_MIRROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_MIRROR)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_MIRROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_MIRROR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_MIRROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_MIRROR)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_MIRROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_MIRROR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_INFO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_INFO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_ENGINE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_ENGINE)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_ENGINE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_ENGINE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_ENGINE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_ENGINE)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_ENGINE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_ENGINE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_HVAC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_HVAC)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_HVAC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_HVAC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_HVAC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_HVAC)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_HVAC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_HVAC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_LIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_LIGHT)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_LIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_LIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_LIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_LIGHT)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_LIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_LIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_1)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_1)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_2)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_2)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_3)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_3)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_4)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_4)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_5) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_5)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_5";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_5;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_5) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_5)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_5";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_5;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_6) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_6)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_6";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_6;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_6) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_6)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_6";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_6;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_7) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_7)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_7";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_7;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_7) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_7)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_7";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_7;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_8) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_8)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_8";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_8;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_8) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_8)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_8";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_8;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_9) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_9)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_9";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_9;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_9) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_9)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_9";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_9;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_10) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_10)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_SET_VENDOR_CATEGORY_10";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_10;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_10) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_10)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_GET_VENDOR_CATEGORY_10";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_10;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_NOT_ACCESSIBLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_DEFAULT) {
        return "PERMISSION_DEFAULT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_WINDOW) {
        return "PERMISSION_SET_VENDOR_CATEGORY_WINDOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_WINDOW) {
        return "PERMISSION_GET_VENDOR_CATEGORY_WINDOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_DOOR) {
        return "PERMISSION_SET_VENDOR_CATEGORY_DOOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_DOOR) {
        return "PERMISSION_GET_VENDOR_CATEGORY_DOOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_SEAT) {
        return "PERMISSION_SET_VENDOR_CATEGORY_SEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT) {
        return "PERMISSION_GET_VENDOR_CATEGORY_SEAT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_MIRROR) {
        return "PERMISSION_SET_VENDOR_CATEGORY_MIRROR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_MIRROR) {
        return "PERMISSION_GET_VENDOR_CATEGORY_MIRROR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO) {
        return "PERMISSION_SET_VENDOR_CATEGORY_INFO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO) {
        return "PERMISSION_GET_VENDOR_CATEGORY_INFO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_ENGINE) {
        return "PERMISSION_SET_VENDOR_CATEGORY_ENGINE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_ENGINE) {
        return "PERMISSION_GET_VENDOR_CATEGORY_ENGINE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_HVAC) {
        return "PERMISSION_SET_VENDOR_CATEGORY_HVAC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_HVAC) {
        return "PERMISSION_GET_VENDOR_CATEGORY_HVAC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_LIGHT) {
        return "PERMISSION_SET_VENDOR_CATEGORY_LIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_LIGHT) {
        return "PERMISSION_GET_VENDOR_CATEGORY_LIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_1) {
        return "PERMISSION_SET_VENDOR_CATEGORY_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_1) {
        return "PERMISSION_GET_VENDOR_CATEGORY_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_2) {
        return "PERMISSION_SET_VENDOR_CATEGORY_2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_2) {
        return "PERMISSION_GET_VENDOR_CATEGORY_2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_3) {
        return "PERMISSION_SET_VENDOR_CATEGORY_3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_3) {
        return "PERMISSION_GET_VENDOR_CATEGORY_3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_4) {
        return "PERMISSION_SET_VENDOR_CATEGORY_4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_4) {
        return "PERMISSION_GET_VENDOR_CATEGORY_4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_5) {
        return "PERMISSION_SET_VENDOR_CATEGORY_5";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_5) {
        return "PERMISSION_GET_VENDOR_CATEGORY_5";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_6) {
        return "PERMISSION_SET_VENDOR_CATEGORY_6";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_6) {
        return "PERMISSION_GET_VENDOR_CATEGORY_6";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_7) {
        return "PERMISSION_SET_VENDOR_CATEGORY_7";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_7) {
        return "PERMISSION_GET_VENDOR_CATEGORY_7";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_8) {
        return "PERMISSION_SET_VENDOR_CATEGORY_8";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_8) {
        return "PERMISSION_GET_VENDOR_CATEGORY_8";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_9) {
        return "PERMISSION_SET_VENDOR_CATEGORY_9";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_9) {
        return "PERMISSION_GET_VENDOR_CATEGORY_9";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_10) {
        return "PERMISSION_SET_VENDOR_CATEGORY_10";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_10) {
        return "PERMISSION_GET_VENDOR_CATEGORY_10";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE) {
        return "PERMISSION_NOT_ACCESSIBLE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::VACANT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::VACANT)) {
        os += (first ? "" : " | ");
        os += "VACANT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::VACANT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::OCCUPIED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::OCCUPIED)) {
        os += (first ? "" : " | ");
        os += "OCCUPIED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::OCCUPIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::VACANT) {
        return "VACANT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::OCCUPIED) {
        return "OCCUPIED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleLightState>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleLightState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::OFF) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightState::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::OFF;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightState::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::DAYTIME_RUNNING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightState::DAYTIME_RUNNING)) {
        os += (first ? "" : " | ");
        os += "DAYTIME_RUNNING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::DAYTIME_RUNNING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleLightState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::ON) {
        return "ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::DAYTIME_RUNNING) {
        return "DAYTIME_RUNNING";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleLightState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::OFF) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::OFF;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::DAYTIME_RUNNING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::DAYTIME_RUNNING)) {
        os += (first ? "" : " | ");
        os += "DAYTIME_RUNNING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::DAYTIME_RUNNING;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::AUTOMATIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::AUTOMATIC)) {
        os += (first ? "" : " | ");
        os += "AUTOMATIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::AUTOMATIC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::ON) {
        return "ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::DAYTIME_RUNNING) {
        return "DAYTIME_RUNNING";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::AUTOMATIC) {
        return "AUTOMATIC";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::EvConnectorType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::EvConnectorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_AC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_AC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_1_AC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_AC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_AC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_AC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_2_AC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_AC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_3_AC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_3_AC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_3_AC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_3_AC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_4_DC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_4_DC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_4_DC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_4_DC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_CCS_DC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_CCS_DC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_1_CCS_DC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_CCS_DC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_CCS_DC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_CCS_DC)) {
        os += (first ? "" : " | ");
        os += "IEC_TYPE_2_CCS_DC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_CCS_DC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_ROADSTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_ROADSTER)) {
        os += (first ? "" : " | ");
        os += "TESLA_ROADSTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_ROADSTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_HPWC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_HPWC)) {
        os += (first ? "" : " | ");
        os += "TESLA_HPWC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_HPWC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_SUPERCHARGER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_SUPERCHARGER)) {
        os += (first ? "" : " | ");
        os += "TESLA_SUPERCHARGER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_SUPERCHARGER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_AC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_AC)) {
        os += (first ? "" : " | ");
        os += "GBT_AC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_AC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_DC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_DC)) {
        os += (first ? "" : " | ");
        os += "GBT_DC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_DC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::OTHER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::EvConnectorType::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::OTHER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::EvConnectorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_AC) {
        return "IEC_TYPE_1_AC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_AC) {
        return "IEC_TYPE_2_AC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_3_AC) {
        return "IEC_TYPE_3_AC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_4_DC) {
        return "IEC_TYPE_4_DC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_CCS_DC) {
        return "IEC_TYPE_1_CCS_DC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_CCS_DC) {
        return "IEC_TYPE_2_CCS_DC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_ROADSTER) {
        return "TESLA_ROADSTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_HPWC) {
        return "TESLA_HPWC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_SUPERCHARGER) {
        return "TESLA_SUPERCHARGER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_AC) {
        return "GBT_AC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_DC) {
        return "GBT_DC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::OTHER) {
        return "OTHER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::EvConnectorType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::PortLocationType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::PortLocationType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_LEFT)) {
        os += (first ? "" : " | ");
        os += "FRONT_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_RIGHT)) {
        os += (first ? "" : " | ");
        os += "FRONT_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_RIGHT)) {
        os += (first ? "" : " | ");
        os += "REAR_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_LEFT)) {
        os += (first ? "" : " | ");
        os += "REAR_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT)) {
        os += (first ? "" : " | ");
        os += "FRONT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR)) {
        os += (first ? "" : " | ");
        os += "REAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::PortLocationType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_LEFT) {
        return "FRONT_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_RIGHT) {
        return "FRONT_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_RIGHT) {
        return "REAR_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_LEFT) {
        return "REAR_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT) {
        return "FRONT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR) {
        return "REAR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::PortLocationType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::FuelType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::FuelType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNLEADED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNLEADED)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_UNLEADED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNLEADED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LEADED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LEADED)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_LEADED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LEADED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_1)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_DIESEL_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_2)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_DIESEL_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_BIODIESEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_BIODIESEL)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_BIODIESEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_BIODIESEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_E85) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_E85)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_E85";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_E85;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LPG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LPG)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_LPG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LPG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_CNG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_CNG)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_CNG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_CNG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LNG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LNG)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_LNG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LNG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_ELECTRIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_ELECTRIC)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_ELECTRIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_ELECTRIC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_HYDROGEN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_HYDROGEN)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_HYDROGEN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_HYDROGEN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_OTHER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_OTHER)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE_OTHER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_OTHER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::FuelType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNKNOWN) {
        return "FUEL_TYPE_UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNLEADED) {
        return "FUEL_TYPE_UNLEADED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LEADED) {
        return "FUEL_TYPE_LEADED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_1) {
        return "FUEL_TYPE_DIESEL_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_2) {
        return "FUEL_TYPE_DIESEL_2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_BIODIESEL) {
        return "FUEL_TYPE_BIODIESEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_E85) {
        return "FUEL_TYPE_E85";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LPG) {
        return "FUEL_TYPE_LPG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_CNG) {
        return "FUEL_TYPE_CNG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LNG) {
        return "FUEL_TYPE_LNG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_ELECTRIC) {
        return "FUEL_TYPE_ELECTRIC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_HYDROGEN) {
        return "FUEL_TYPE_HYDROGEN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_OTHER) {
        return "FUEL_TYPE_OTHER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::FuelType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE)) {
        os += (first ? "" : " | ");
        os += "FACE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FLOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FLOOR)) {
        os += (first ? "" : " | ");
        os += "FLOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FLOOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE_AND_FLOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE_AND_FLOOR)) {
        os += (first ? "" : " | ");
        os += "FACE_AND_FLOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE_AND_FLOOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST)) {
        os += (first ? "" : " | ");
        os += "DEFROST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST_AND_FLOOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST_AND_FLOOR)) {
        os += (first ? "" : " | ");
        os += "DEFROST_AND_FLOOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST_AND_FLOOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE) {
        return "FACE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FLOOR) {
        return "FLOOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE_AND_FLOOR) {
        return "FACE_AND_FLOOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST) {
        return "DEFROST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST_AND_FLOOR) {
        return "DEFROST_AND_FLOOR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::CRITICALLY_LOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::CRITICALLY_LOW)) {
        os += (first ? "" : " | ");
        os += "CRITICALLY_LOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::CRITICALLY_LOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::LOW) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::LOW)) {
        os += (first ? "" : " | ");
        os += "LOW";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::LOW;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::NORMAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::NORMAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::HIGH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::HIGH)) {
        os += (first ? "" : " | ");
        os += "HIGH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::HIGH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::ERROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::CRITICALLY_LOW) {
        return "CRITICALLY_LOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::LOW) {
        return "LOW";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::HIGH) {
        return "HIGH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::ENABLE_DEEP_SLEEP_FLAG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::ENABLE_DEEP_SLEEP_FLAG)) {
        os += (first ? "" : " | ");
        os += "ENABLE_DEEP_SLEEP_FLAG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::ENABLE_DEEP_SLEEP_FLAG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::CONFIG_SUPPORT_TIMER_POWER_ON_FLAG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::CONFIG_SUPPORT_TIMER_POWER_ON_FLAG)) {
        os += (first ? "" : " | ");
        os += "CONFIG_SUPPORT_TIMER_POWER_ON_FLAG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::CONFIG_SUPPORT_TIMER_POWER_ON_FLAG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::ENABLE_DEEP_SLEEP_FLAG) {
        return "ENABLE_DEEP_SLEEP_FLAG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::CONFIG_SUPPORT_TIMER_POWER_ON_FLAG) {
        return "CONFIG_SUPPORT_TIMER_POWER_ON_FLAG";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::SHUTDOWN_PREPARE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::SHUTDOWN_PREPARE)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_PREPARE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::SHUTDOWN_PREPARE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::CANCEL_SHUTDOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::CANCEL_SHUTDOWN)) {
        os += (first ? "" : " | ");
        os += "CANCEL_SHUTDOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::CANCEL_SHUTDOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::FINISHED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::FINISHED)) {
        os += (first ? "" : " | ");
        os += "FINISHED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::FINISHED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::ON) {
        return "ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::SHUTDOWN_PREPARE) {
        return "SHUTDOWN_PREPARE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::CANCEL_SHUTDOWN) {
        return "CANCEL_SHUTDOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::FINISHED) {
        return "FINISHED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::STATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::STATE)) {
        os += (first ? "" : " | ");
        os += "STATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::STATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::ADDITIONAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::ADDITIONAL)) {
        os += (first ? "" : " | ");
        os += "ADDITIONAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::ADDITIONAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::STATE) {
        return "STATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::ADDITIONAL) {
        return "ADDITIONAL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_IMMEDIATELY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_IMMEDIATELY)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_IMMEDIATELY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_IMMEDIATELY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::CAN_SLEEP) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::CAN_SLEEP)) {
        os += (first ? "" : " | ");
        os += "CAN_SLEEP";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::CAN_SLEEP;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_ONLY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_ONLY)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_ONLY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_ONLY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SLEEP_IMMEDIATELY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SLEEP_IMMEDIATELY)) {
        os += (first ? "" : " | ");
        os += "SLEEP_IMMEDIATELY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SLEEP_IMMEDIATELY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_IMMEDIATELY) {
        return "SHUTDOWN_IMMEDIATELY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::CAN_SLEEP) {
        return "CAN_SLEEP";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_ONLY) {
        return "SHUTDOWN_ONLY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SLEEP_IMMEDIATELY) {
        return "SLEEP_IMMEDIATELY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::WAIT_FOR_VHAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::WAIT_FOR_VHAL)) {
        os += (first ? "" : " | ");
        os += "WAIT_FOR_VHAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::WAIT_FOR_VHAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_ENTRY) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_ENTRY)) {
        os += (first ? "" : " | ");
        os += "DEEP_SLEEP_ENTRY";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_ENTRY;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_EXIT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_EXIT)) {
        os += (first ? "" : " | ");
        os += "DEEP_SLEEP_EXIT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_EXIT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_POSTPONE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_POSTPONE)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_POSTPONE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_POSTPONE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_START)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_START;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_PREPARE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_PREPARE)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_PREPARE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_PREPARE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_CANCELLED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_CANCELLED)) {
        os += (first ? "" : " | ");
        os += "SHUTDOWN_CANCELLED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_CANCELLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::WAIT_FOR_VHAL) {
        return "WAIT_FOR_VHAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_ENTRY) {
        return "DEEP_SLEEP_ENTRY";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_EXIT) {
        return "DEEP_SLEEP_EXIT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_POSTPONE) {
        return "SHUTDOWN_POSTPONE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_START) {
        return "SHUTDOWN_START";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::ON) {
        return "ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_PREPARE) {
        return "SHUTDOWN_PREPARE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_CANCELLED) {
        return "SHUTDOWN_CANCELLED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_DOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_DOWN)) {
        os += (first ? "" : " | ");
        os += "ACTION_DOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_DOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_UP) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_UP)) {
        os += (first ? "" : " | ");
        os += "ACTION_UP";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_UP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_DOWN) {
        return "ACTION_DOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_UP) {
        return "ACTION_UP";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleDisplay>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleDisplay> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::MAIN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::MAIN)) {
        os += (first ? "" : " | ");
        os += "MAIN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::MAIN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::INSTRUMENT_CLUSTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::INSTRUMENT_CLUSTER)) {
        os += (first ? "" : " | ");
        os += "INSTRUMENT_CLUSTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::INSTRUMENT_CLUSTER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::MAIN) {
        return "MAIN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::INSTRUMENT_CLUSTER) {
        return "INSTRUMENT_CLUSTER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleDisplay o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleUnit>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleUnit> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SHOULD_NOT_USE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SHOULD_NOT_USE)) {
        os += (first ? "" : " | ");
        os += "SHOULD_NOT_USE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SHOULD_NOT_USE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER_PER_SEC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER_PER_SEC)) {
        os += (first ? "" : " | ");
        os += "METER_PER_SEC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER_PER_SEC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::RPM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::RPM)) {
        os += (first ? "" : " | ");
        os += "RPM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::RPM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::HERTZ) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::HERTZ)) {
        os += (first ? "" : " | ");
        os += "HERTZ";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::HERTZ;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PERCENTILE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PERCENTILE)) {
        os += (first ? "" : " | ");
        os += "PERCENTILE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PERCENTILE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIMETER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIMETER)) {
        os += (first ? "" : " | ");
        os += "MILLIMETER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIMETER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER)) {
        os += (first ? "" : " | ");
        os += "METER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETER)) {
        os += (first ? "" : " | ");
        os += "KILOMETER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILE)) {
        os += (first ? "" : " | ");
        os += "MILE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::CELSIUS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::CELSIUS)) {
        os += (first ? "" : " | ");
        os += "CELSIUS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::CELSIUS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::FAHRENHEIT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::FAHRENHEIT)) {
        os += (first ? "" : " | ");
        os += "FAHRENHEIT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::FAHRENHEIT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KELVIN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KELVIN)) {
        os += (first ? "" : " | ");
        os += "KELVIN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KELVIN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLILITER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLILITER)) {
        os += (first ? "" : " | ");
        os += "MILLILITER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLILITER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::LITER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::LITER)) {
        os += (first ? "" : " | ");
        os += "LITER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::LITER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::GALLON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::GALLON)) {
        os += (first ? "" : " | ");
        os += "GALLON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::GALLON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::US_GALLON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::US_GALLON)) {
        os += (first ? "" : " | ");
        os += "US_GALLON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::US_GALLON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::IMPERIAL_GALLON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::IMPERIAL_GALLON)) {
        os += (first ? "" : " | ");
        os += "IMPERIAL_GALLON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::IMPERIAL_GALLON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::NANO_SECS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::NANO_SECS)) {
        os += (first ? "" : " | ");
        os += "NANO_SECS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::NANO_SECS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SECS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SECS)) {
        os += (first ? "" : " | ");
        os += "SECS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SECS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::YEAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::YEAR)) {
        os += (first ? "" : " | ");
        os += "YEAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::YEAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::WATT_HOUR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::WATT_HOUR)) {
        os += (first ? "" : " | ");
        os += "WATT_HOUR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::WATT_HOUR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIAMPERE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIAMPERE)) {
        os += (first ? "" : " | ");
        os += "MILLIAMPERE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIAMPERE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIVOLT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIVOLT)) {
        os += (first ? "" : " | ");
        os += "MILLIVOLT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIVOLT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIWATTS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIWATTS)) {
        os += (first ? "" : " | ");
        os += "MILLIWATTS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIWATTS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::AMPERE_HOURS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::AMPERE_HOURS)) {
        os += (first ? "" : " | ");
        os += "AMPERE_HOURS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::AMPERE_HOURS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOWATT_HOUR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOWATT_HOUR)) {
        os += (first ? "" : " | ");
        os += "KILOWATT_HOUR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOWATT_HOUR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOPASCAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOPASCAL)) {
        os += (first ? "" : " | ");
        os += "KILOPASCAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOPASCAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PSI) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PSI)) {
        os += (first ? "" : " | ");
        os += "PSI";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PSI;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::BAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::BAR)) {
        os += (first ? "" : " | ");
        os += "BAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::BAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::DEGREES) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::DEGREES)) {
        os += (first ? "" : " | ");
        os += "DEGREES";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::DEGREES;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILES_PER_HOUR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILES_PER_HOUR)) {
        os += (first ? "" : " | ");
        os += "MILES_PER_HOUR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILES_PER_HOUR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETERS_PER_HOUR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETERS_PER_HOUR)) {
        os += (first ? "" : " | ");
        os += "KILOMETERS_PER_HOUR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETERS_PER_HOUR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleUnit o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SHOULD_NOT_USE) {
        return "SHOULD_NOT_USE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER_PER_SEC) {
        return "METER_PER_SEC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::RPM) {
        return "RPM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::HERTZ) {
        return "HERTZ";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PERCENTILE) {
        return "PERCENTILE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIMETER) {
        return "MILLIMETER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER) {
        return "METER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETER) {
        return "KILOMETER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILE) {
        return "MILE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::CELSIUS) {
        return "CELSIUS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::FAHRENHEIT) {
        return "FAHRENHEIT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KELVIN) {
        return "KELVIN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLILITER) {
        return "MILLILITER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::LITER) {
        return "LITER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::GALLON) {
        return "GALLON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::US_GALLON) {
        return "US_GALLON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::IMPERIAL_GALLON) {
        return "IMPERIAL_GALLON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::NANO_SECS) {
        return "NANO_SECS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SECS) {
        return "SECS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::YEAR) {
        return "YEAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::WATT_HOUR) {
        return "WATT_HOUR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIAMPERE) {
        return "MILLIAMPERE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIVOLT) {
        return "MILLIVOLT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIWATTS) {
        return "MILLIWATTS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::AMPERE_HOURS) {
        return "AMPERE_HOURS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOWATT_HOUR) {
        return "KILOWATT_HOUR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOPASCAL) {
        return "KILOPASCAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PSI) {
        return "PSI";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::BAR) {
        return "BAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::DEGREES) {
        return "DEGREES";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILES_PER_HOUR) {
        return "MILES_PER_HOUR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETERS_PER_HOUR) {
        return "KILOMETERS_PER_HOUR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleUnit o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::STATIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::STATIC)) {
        os += (first ? "" : " | ");
        os += "STATIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::STATIC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::ON_CHANGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::ON_CHANGE)) {
        os += (first ? "" : " | ");
        os += "ON_CHANGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::ON_CHANGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::CONTINUOUS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::CONTINUOUS)) {
        os += (first ? "" : " | ");
        os += "CONTINUOUS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::CONTINUOUS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::STATIC) {
        return "STATIC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::ON_CHANGE) {
        return "ON_CHANGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::CONTINUOUS) {
        return "CONTINUOUS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::NONE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::NONE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ)) {
        os += (first ? "" : " | ");
        os += "READ";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::WRITE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::WRITE)) {
        os += (first ? "" : " | ");
        os += "WRITE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::WRITE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ_WRITE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ_WRITE)) {
        os += (first ? "" : " | ");
        os += "READ_WRITE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ_WRITE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ) {
        return "READ";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::WRITE) {
        return "WRITE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ_WRITE) {
        return "READ_WRITE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::UNAVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::UNAVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::ERROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::AVAILABLE) {
        return "AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::UNAVAILABLE) {
        return "UNAVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleGear>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleGear> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "GEAR_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_NEUTRAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_NEUTRAL)) {
        os += (first ? "" : " | ");
        os += "GEAR_NEUTRAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_NEUTRAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_REVERSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_REVERSE)) {
        os += (first ? "" : " | ");
        os += "GEAR_REVERSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_REVERSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_PARK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_PARK)) {
        os += (first ? "" : " | ");
        os += "GEAR_PARK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_PARK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_DRIVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_DRIVE)) {
        os += (first ? "" : " | ");
        os += "GEAR_DRIVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_DRIVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_1)) {
        os += (first ? "" : " | ");
        os += "GEAR_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_2)) {
        os += (first ? "" : " | ");
        os += "GEAR_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_3)) {
        os += (first ? "" : " | ");
        os += "GEAR_3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_4)) {
        os += (first ? "" : " | ");
        os += "GEAR_4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_5) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_5)) {
        os += (first ? "" : " | ");
        os += "GEAR_5";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_5;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_6) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_6)) {
        os += (first ? "" : " | ");
        os += "GEAR_6";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_6;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_7) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_7)) {
        os += (first ? "" : " | ");
        os += "GEAR_7";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_7;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_8) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_8)) {
        os += (first ? "" : " | ");
        os += "GEAR_8";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_8;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_9) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_9)) {
        os += (first ? "" : " | ");
        os += "GEAR_9";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_9;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleGear o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_UNKNOWN) {
        return "GEAR_UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_NEUTRAL) {
        return "GEAR_NEUTRAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_REVERSE) {
        return "GEAR_REVERSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_PARK) {
        return "GEAR_PARK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_DRIVE) {
        return "GEAR_DRIVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_1) {
        return "GEAR_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_2) {
        return "GEAR_2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_3) {
        return "GEAR_3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_4) {
        return "GEAR_4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_5) {
        return "GEAR_5";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_6) {
        return "GEAR_6";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_7) {
        return "GEAR_7";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_8) {
        return "GEAR_8";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_9) {
        return "GEAR_9";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleGear o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_CENTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_CENTER)) {
        os += (first ? "" : " | ");
        os += "ROW_1_CENTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_CENTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_CENTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_CENTER)) {
        os += (first ? "" : " | ");
        os += "ROW_2_CENTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_CENTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_CENTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_CENTER)) {
        os += (first ? "" : " | ");
        os += "ROW_3_CENTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_CENTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_RIGHT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_LEFT) {
        return "ROW_1_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_CENTER) {
        return "ROW_1_CENTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_RIGHT) {
        return "ROW_1_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_LEFT) {
        return "ROW_2_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_CENTER) {
        return "ROW_2_CENTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_RIGHT) {
        return "ROW_2_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_LEFT) {
        return "ROW_3_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_CENTER) {
        return "ROW_3_CENTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_RIGHT) {
        return "ROW_3_RIGHT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::FRONT_WINDSHIELD) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::FRONT_WINDSHIELD)) {
        os += (first ? "" : " | ");
        os += "FRONT_WINDSHIELD";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::FRONT_WINDSHIELD;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::REAR_WINDSHIELD) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::REAR_WINDSHIELD)) {
        os += (first ? "" : " | ");
        os += "REAR_WINDSHIELD";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::REAR_WINDSHIELD;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_1)) {
        os += (first ? "" : " | ");
        os += "ROOF_TOP_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_2)) {
        os += (first ? "" : " | ");
        os += "ROOF_TOP_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_2;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::FRONT_WINDSHIELD) {
        return "FRONT_WINDSHIELD";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::REAR_WINDSHIELD) {
        return "REAR_WINDSHIELD";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_LEFT) {
        return "ROW_1_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_RIGHT) {
        return "ROW_1_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_LEFT) {
        return "ROW_2_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_RIGHT) {
        return "ROW_2_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_LEFT) {
        return "ROW_3_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_RIGHT) {
        return "ROW_3_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_1) {
        return "ROOF_TOP_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_2) {
        return "ROOF_TOP_2";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_1_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_2_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_LEFT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_RIGHT)) {
        os += (first ? "" : " | ");
        os += "ROW_3_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::HOOD) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::HOOD)) {
        os += (first ? "" : " | ");
        os += "HOOD";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::HOOD;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::REAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::REAR)) {
        os += (first ? "" : " | ");
        os += "REAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::REAR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_LEFT) {
        return "ROW_1_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_RIGHT) {
        return "ROW_1_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_LEFT) {
        return "ROW_2_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_RIGHT) {
        return "ROW_2_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_LEFT) {
        return "ROW_3_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_RIGHT) {
        return "ROW_3_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::HOOD) {
        return "HOOD";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::REAR) {
        return "REAR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_LEFT)) {
        os += (first ? "" : " | ");
        os += "DRIVER_LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_LEFT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_RIGHT)) {
        os += (first ? "" : " | ");
        os += "DRIVER_RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_CENTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_CENTER)) {
        os += (first ? "" : " | ");
        os += "DRIVER_CENTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_CENTER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_LEFT) {
        return "DRIVER_LEFT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_RIGHT) {
        return "DRIVER_RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_CENTER) {
        return "DRIVER_CENTER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::NONE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::NONE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::RIGHT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::RIGHT)) {
        os += (first ? "" : " | ");
        os += "RIGHT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::RIGHT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::LEFT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::LEFT)) {
        os += (first ? "" : " | ");
        os += "LEFT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::LEFT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::RIGHT) {
        return "RIGHT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::LEFT) {
        return "LEFT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".areaId = ";
    os += ::android::hardware::toString(o.areaId);
    os += ", .minInt32Value = ";
    os += ::android::hardware::toString(o.minInt32Value);
    os += ", .maxInt32Value = ";
    os += ::android::hardware::toString(o.maxInt32Value);
    os += ", .minInt64Value = ";
    os += ::android::hardware::toString(o.minInt64Value);
    os += ", .maxInt64Value = ";
    os += ::android::hardware::toString(o.maxInt64Value);
    os += ", .minFloatValue = ";
    os += ::android::hardware::toString(o.minFloatValue);
    os += ", .maxFloatValue = ";
    os += ::android::hardware::toString(o.maxFloatValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& rhs) {
    if (lhs.areaId != rhs.areaId) {
        return false;
    }
    if (lhs.minInt32Value != rhs.minInt32Value) {
        return false;
    }
    if (lhs.maxInt32Value != rhs.maxInt32Value) {
        return false;
    }
    if (lhs.minInt64Value != rhs.minInt64Value) {
        return false;
    }
    if (lhs.maxInt64Value != rhs.maxInt64Value) {
        return false;
    }
    if (lhs.minFloatValue != rhs.minFloatValue) {
        return false;
    }
    if (lhs.maxFloatValue != rhs.maxFloatValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".prop = ";
    os += ::android::hardware::toString(o.prop);
    os += ", .access = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.access);
    os += ", .changeMode = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.changeMode);
    os += ", .areaConfigs = ";
    os += ::android::hardware::toString(o.areaConfigs);
    os += ", .configArray = ";
    os += ::android::hardware::toString(o.configArray);
    os += ", .configString = ";
    os += ::android::hardware::toString(o.configString);
    os += ", .minSampleRate = ";
    os += ::android::hardware::toString(o.minSampleRate);
    os += ", .maxSampleRate = ";
    os += ::android::hardware::toString(o.maxSampleRate);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& rhs) {
    if (lhs.prop != rhs.prop) {
        return false;
    }
    if (lhs.access != rhs.access) {
        return false;
    }
    if (lhs.changeMode != rhs.changeMode) {
        return false;
    }
    if (lhs.areaConfigs != rhs.areaConfigs) {
        return false;
    }
    if (lhs.configArray != rhs.configArray) {
        return false;
    }
    if (lhs.configString != rhs.configString) {
        return false;
    }
    if (lhs.minSampleRate != rhs.minSampleRate) {
        return false;
    }
    if (lhs.maxSampleRate != rhs.maxSampleRate) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".int32Values = ";
    os += ::android::hardware::toString(o.int32Values);
    os += ", .floatValues = ";
    os += ::android::hardware::toString(o.floatValues);
    os += ", .int64Values = ";
    os += ::android::hardware::toString(o.int64Values);
    os += ", .bytes = ";
    os += ::android::hardware::toString(o.bytes);
    os += ", .stringValue = ";
    os += ::android::hardware::toString(o.stringValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& rhs) {
    if (lhs.int32Values != rhs.int32Values) {
        return false;
    }
    if (lhs.floatValues != rhs.floatValues) {
        return false;
    }
    if (lhs.int64Values != rhs.int64Values) {
        return false;
    }
    if (lhs.bytes != rhs.bytes) {
        return false;
    }
    if (lhs.stringValue != rhs.stringValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue::RawValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .areaId = ";
    os += ::android::hardware::toString(o.areaId);
    os += ", .prop = ";
    os += ::android::hardware::toString(o.prop);
    os += ", .status = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.status);
    os += ", .value = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& rhs) {
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    if (lhs.areaId != rhs.areaId) {
        return false;
    }
    if (lhs.prop != rhs.prop) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& lhs, const ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::UNDEFINED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::UNDEFINED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::LOCK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::LOCK)) {
        os += (first ? "" : " | ");
        os += "LOCK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::LOCK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::OFF) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::OFF;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ACC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ACC)) {
        os += (first ? "" : " | ");
        os += "ACC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ACC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::START)) {
        os += (first ? "" : " | ");
        os += "START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::LOCK) {
        return "LOCK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ACC) {
        return "ACC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ON) {
        return "ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::START) {
        return "START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::SubscribeFlags>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::SubscribeFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::UNDEFINED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::UNDEFINED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_CAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_CAR)) {
        os += (first ? "" : " | ");
        os += "EVENTS_FROM_CAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_CAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_ANDROID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_ANDROID)) {
        os += (first ? "" : " | ");
        os += "EVENTS_FROM_ANDROID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_ANDROID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_CAR) {
        return "EVENTS_FROM_CAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_ANDROID) {
        return "EVENTS_FROM_ANDROID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SubscribeFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".propId = ";
    os += ::android::hardware::toString(o.propId);
    os += ", .sampleRate = ";
    os += ::android::hardware::toString(o.sampleRate);
    os += ", .flags = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& rhs) {
    if (lhs.propId != rhs.propId) {
        return false;
    }
    if (lhs.sampleRate != rhs.sampleRate) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& lhs, const ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::StatusCode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::StatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::OK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::OK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::TRY_AGAIN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::TRY_AGAIN)) {
        os += (first ? "" : " | ");
        os += "TRY_AGAIN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::TRY_AGAIN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::INVALID_ARG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::INVALID_ARG)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::INVALID_ARG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::NOT_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::ACCESS_DENIED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::ACCESS_DENIED)) {
        os += (first ? "" : " | ");
        os += "ACCESS_DENIED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::ACCESS_DENIED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::StatusCode::INTERNAL_ERROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::StatusCode::INTERNAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::StatusCode::INTERNAL_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::StatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::OK) {
        return "OK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::TRY_AGAIN) {
        return "TRY_AGAIN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::INVALID_ARG) {
        return "INVALID_ARG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::NOT_AVAILABLE) {
        return "NOT_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::ACCESS_DENIED) {
        return "ACCESS_DENIED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::StatusCode::INTERNAL_ERROR) {
        return "INTERNAL_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::StatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_FRONT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_FRONT)) {
        os += (first ? "" : " | ");
        os += "LEFT_FRONT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_FRONT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_FRONT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_FRONT)) {
        os += (first ? "" : " | ");
        os += "RIGHT_FRONT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_FRONT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_REAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_REAR)) {
        os += (first ? "" : " | ");
        os += "LEFT_REAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_REAR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_REAR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_REAR)) {
        os += (first ? "" : " | ");
        os += "RIGHT_REAR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_REAR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_FRONT) {
        return "LEFT_FRONT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_FRONT) {
        return "RIGHT_FRONT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_REAR) {
        return "LEFT_REAR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_REAR) {
        return "RIGHT_REAR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_INSUFFICIENT_ENGINE_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_INSUFFICIENT_ENGINE_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "OPEN_INSUFFICIENT_ENGINE_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_INSUFFICIENT_ENGINE_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP)) {
        os += (first ? "" : " | ");
        os += "CLOSED_LOOP";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_ENGINE_LOAD_OR_DECELERATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_ENGINE_LOAD_OR_DECELERATION)) {
        os += (first ? "" : " | ");
        os += "OPEN_ENGINE_LOAD_OR_DECELERATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_ENGINE_LOAD_OR_DECELERATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_SYSTEM_FAILURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_SYSTEM_FAILURE)) {
        os += (first ? "" : " | ");
        os += "OPEN_SYSTEM_FAILURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_SYSTEM_FAILURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP_BUT_FEEDBACK_FAULT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP_BUT_FEEDBACK_FAULT)) {
        os += (first ? "" : " | ");
        os += "CLOSED_LOOP_BUT_FEEDBACK_FAULT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP_BUT_FEEDBACK_FAULT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_INSUFFICIENT_ENGINE_TEMPERATURE) {
        return "OPEN_INSUFFICIENT_ENGINE_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP) {
        return "CLOSED_LOOP";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_ENGINE_LOAD_OR_DECELERATION) {
        return "OPEN_ENGINE_LOAD_OR_DECELERATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_SYSTEM_FAILURE) {
        return "OPEN_SYSTEM_FAILURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP_BUT_FEEDBACK_FAULT) {
        return "CLOSED_LOOP_BUT_FEEDBACK_FAULT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::SPARK) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::SPARK)) {
        os += (first ? "" : " | ");
        os += "SPARK";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::SPARK;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::COMPRESSION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::COMPRESSION)) {
        os += (first ? "" : " | ");
        os += "COMPRESSION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::COMPRESSION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::SPARK) {
        return "SPARK";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::COMPRESSION) {
        return "COMPRESSION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_INCOMPLETE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE) {
        return "COMPONENTS_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_INCOMPLETE) {
        return "COMPONENTS_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) {
        return "FUEL_SYSTEM_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) {
        return "FUEL_SYSTEM_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE) {
        return "MISFIRE_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_INCOMPLETE) {
        return "MISFIRE_INCOMPLETE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "EGR_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "EGR_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR_HEATER_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR_HEATER_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "AC_REFRIGERANT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "AC_REFRIGERANT_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "SECONDARY_AIR_SYSTEM_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "SECONDARY_AIR_SYSTEM_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "EVAPORATIVE_SYSTEM_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "EVAPORATIVE_SYSTEM_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "HEATED_CATALYST_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "HEATED_CATALYST_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "CATALYST_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "CATALYST_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_INCOMPLETE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_AVAILABLE) {
        return "COMPONENTS_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_INCOMPLETE) {
        return "COMPONENTS_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) {
        return "FUEL_SYSTEM_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) {
        return "FUEL_SYSTEM_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_AVAILABLE) {
        return "MISFIRE_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_INCOMPLETE) {
        return "MISFIRE_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_AVAILABLE) {
        return "EGR_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_INCOMPLETE) {
        return "EGR_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_AVAILABLE) {
        return "OXYGEN_SENSOR_HEATER_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_INCOMPLETE) {
        return "OXYGEN_SENSOR_HEATER_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_AVAILABLE) {
        return "OXYGEN_SENSOR_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_INCOMPLETE) {
        return "OXYGEN_SENSOR_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE) {
        return "AC_REFRIGERANT_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_INCOMPLETE) {
        return "AC_REFRIGERANT_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_AVAILABLE) {
        return "SECONDARY_AIR_SYSTEM_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_INCOMPLETE) {
        return "SECONDARY_AIR_SYSTEM_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE) {
        return "EVAPORATIVE_SYSTEM_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_INCOMPLETE) {
        return "EVAPORATIVE_SYSTEM_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_AVAILABLE) {
        return "HEATED_CATALYST_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_INCOMPLETE) {
        return "HEATED_CATALYST_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_AVAILABLE) {
        return "CATALYST_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_INCOMPLETE) {
        return "CATALYST_INCOMPLETE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "COMPONENTS_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "MISFIRE_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "EGR_OR_VVT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "EGR_OR_VVT_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "PM_FILTER_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "PM_FILTER_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "EXHAUST_GAS_SENSOR_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "EXHAUST_GAS_SENSOR_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "BOOST_PRESSURE_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "BOOST_PRESSURE_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NOx_SCR_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "NOx_SCR_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_INCOMPLETE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NMHC_CATALYST_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_INCOMPLETE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "NMHC_CATALYST_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_INCOMPLETE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_AVAILABLE) {
        return "COMPONENTS_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_INCOMPLETE) {
        return "COMPONENTS_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_AVAILABLE) {
        return "FUEL_SYSTEM_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE) {
        return "FUEL_SYSTEM_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_AVAILABLE) {
        return "MISFIRE_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_INCOMPLETE) {
        return "MISFIRE_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_AVAILABLE) {
        return "EGR_OR_VVT_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_INCOMPLETE) {
        return "EGR_OR_VVT_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_AVAILABLE) {
        return "PM_FILTER_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_INCOMPLETE) {
        return "PM_FILTER_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_AVAILABLE) {
        return "EXHAUST_GAS_SENSOR_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_INCOMPLETE) {
        return "EXHAUST_GAS_SENSOR_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_AVAILABLE) {
        return "BOOST_PRESSURE_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_INCOMPLETE) {
        return "BOOST_PRESSURE_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_AVAILABLE) {
        return "NOx_SCR_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_INCOMPLETE) {
        return "NOx_SCR_INCOMPLETE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_AVAILABLE) {
        return "NMHC_CATALYST_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_INCOMPLETE) {
        return "NMHC_CATALYST_INCOMPLETE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::UPSTREAM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::UPSTREAM)) {
        os += (first ? "" : " | ");
        os += "UPSTREAM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::UPSTREAM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::DOWNSTREAM_OF_CATALYCIC_CONVERTER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::DOWNSTREAM_OF_CATALYCIC_CONVERTER)) {
        os += (first ? "" : " | ");
        os += "DOWNSTREAM_OF_CATALYCIC_CONVERTER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::DOWNSTREAM_OF_CATALYCIC_CONVERTER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF)) {
        os += (first ? "" : " | ");
        os += "FROM_OUTSIDE_OR_OFF";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::PUMP_ON_FOR_DIAGNOSTICS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::PUMP_ON_FOR_DIAGNOSTICS)) {
        os += (first ? "" : " | ");
        os += "PUMP_ON_FOR_DIAGNOSTICS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::PUMP_ON_FOR_DIAGNOSTICS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::UPSTREAM) {
        return "UPSTREAM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::DOWNSTREAM_OF_CATALYCIC_CONVERTER) {
        return "DOWNSTREAM_OF_CATALYCIC_CONVERTER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF) {
        return "FROM_OUTSIDE_OR_OFF";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::PUMP_ON_FOR_DIAGNOSTICS) {
        return "PUMP_ON_FOR_DIAGNOSTICS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::Obd2FuelType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::Obd2FuelType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::NOT_AVAILABLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::GASOLINE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::GASOLINE)) {
        os += (first ? "" : " | ");
        os += "GASOLINE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::GASOLINE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::METHANOL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::METHANOL)) {
        os += (first ? "" : " | ");
        os += "METHANOL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::METHANOL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ETHANOL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ETHANOL)) {
        os += (first ? "" : " | ");
        os += "ETHANOL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ETHANOL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::DIESEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::DIESEL)) {
        os += (first ? "" : " | ");
        os += "DIESEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::DIESEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::LPG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::LPG)) {
        os += (first ? "" : " | ");
        os += "LPG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::LPG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::CNG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::CNG)) {
        os += (first ? "" : " | ");
        os += "CNG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::CNG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::PROPANE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::PROPANE)) {
        os += (first ? "" : " | ");
        os += "PROPANE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::PROPANE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ELECTRIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ELECTRIC)) {
        os += (first ? "" : " | ");
        os += "ELECTRIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ELECTRIC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_GASOLINE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_GASOLINE)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_GASOLINE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_GASOLINE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_METHANOL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_METHANOL)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_METHANOL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_METHANOL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ETHANOL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ETHANOL)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_ETHANOL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ETHANOL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_LPG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_LPG)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_LPG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_LPG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_CNG) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_CNG)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_CNG";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_CNG;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_PROPANE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_PROPANE)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_PROPANE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_PROPANE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_ELECTRIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_GASOLINE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_GASOLINE)) {
        os += (first ? "" : " | ");
        os += "HYBRID_GASOLINE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_GASOLINE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ETHANOL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ETHANOL)) {
        os += (first ? "" : " | ");
        os += "HYBRID_ETHANOL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ETHANOL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_DIESEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_DIESEL)) {
        os += (first ? "" : " | ");
        os += "HYBRID_DIESEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_DIESEL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ELECTRIC) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ELECTRIC)) {
        os += (first ? "" : " | ");
        os += "HYBRID_ELECTRIC";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ELECTRIC;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION)) {
        os += (first ? "" : " | ");
        os += "HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_REGENERATIVE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_REGENERATIVE)) {
        os += (first ? "" : " | ");
        os += "HYBRID_REGENERATIVE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_REGENERATIVE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_DIESEL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_DIESEL)) {
        os += (first ? "" : " | ");
        os += "BIFUEL_RUNNING_DIESEL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_DIESEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::NOT_AVAILABLE) {
        return "NOT_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::GASOLINE) {
        return "GASOLINE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::METHANOL) {
        return "METHANOL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ETHANOL) {
        return "ETHANOL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::DIESEL) {
        return "DIESEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::LPG) {
        return "LPG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::CNG) {
        return "CNG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::PROPANE) {
        return "PROPANE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ELECTRIC) {
        return "ELECTRIC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_GASOLINE) {
        return "BIFUEL_RUNNING_GASOLINE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_METHANOL) {
        return "BIFUEL_RUNNING_METHANOL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ETHANOL) {
        return "BIFUEL_RUNNING_ETHANOL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_LPG) {
        return "BIFUEL_RUNNING_LPG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_CNG) {
        return "BIFUEL_RUNNING_CNG";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_PROPANE) {
        return "BIFUEL_RUNNING_PROPANE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC) {
        return "BIFUEL_RUNNING_ELECTRIC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION) {
        return "BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_GASOLINE) {
        return "HYBRID_GASOLINE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ETHANOL) {
        return "HYBRID_ETHANOL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_DIESEL) {
        return "HYBRID_DIESEL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ELECTRIC) {
        return "HYBRID_ELECTRIC";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION) {
        return "HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_REGENERATIVE) {
        return "HYBRID_REGENERATIVE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_DIESEL) {
        return "BIFUEL_RUNNING_DIESEL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::Obd2FuelType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS)) {
        os += (first ? "" : " | ");
        os += "FUEL_SYSTEM_STATUS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "MALFUNCTION_INDICATOR_LIGHT_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "IGNITION_MONITORS_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS)) {
        os += (first ? "" : " | ");
        os += "IGNITION_SPECIFIC_MONITORS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "INTAKE_AIR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS)) {
        os += (first ? "" : " | ");
        os += "COMMANDED_SECONDARY_AIR_STATUS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT)) {
        os += (first ? "" : " | ");
        os += "NUM_OXYGEN_SENSORS_PRESENT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START)) {
        os += (first ? "" : " | ");
        os += "RUNTIME_SINCE_ENGINE_START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED)) {
        os += (first ? "" : " | ");
        os += "WARMUPS_SINCE_CODES_CLEARED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED)) {
        os += (first ? "" : " | ");
        os += "DISTANCE_TRAVELED_SINCE_CODES_CLEARED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_BAROMETRIC_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "CONTROL_MODULE_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "AMBIENT_AIR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_WITH_MALFUNCTION_LIGHT_ON) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_WITH_MALFUNCTION_LIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "TIME_WITH_MALFUNCTION_LIGHT_ON";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_WITH_MALFUNCTION_LIGHT_ON;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_SINCE_TROUBLE_CODES_CLEARED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_SINCE_TROUBLE_CODES_CLEARED)) {
        os += (first ? "" : " | ");
        os += "TIME_SINCE_TROUBLE_CODES_CLEARED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_SINCE_TROUBLE_CODES_CLEARED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "MAX_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "MAX_OXYGEN_SENSOR_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_CURRENT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_CURRENT)) {
        os += (first ? "" : " | ");
        os += "MAX_OXYGEN_SENSOR_CURRENT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_CURRENT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR)) {
        os += (first ? "" : " | ");
        os += "MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_TYPE)) {
        os += (first ? "" : " | ");
        os += "FUEL_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_RAIL_ABSOLUTE_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_RAIL_ABSOLUTE_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "FUEL_RAIL_ABSOLUTE_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_RAIL_ABSOLUTE_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_OIL_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_OIL_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_OIL_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_OIL_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DRIVER_DEMAND_PERCENT_TORQUE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DRIVER_DEMAND_PERCENT_TORQUE)) {
        os += (first ? "" : " | ");
        os += "DRIVER_DEMAND_PERCENT_TORQUE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DRIVER_DEMAND_PERCENT_TORQUE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_ACTUAL_PERCENT_TORQUE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_ACTUAL_PERCENT_TORQUE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_ACTUAL_PERCENT_TORQUE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_ACTUAL_PERCENT_TORQUE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_REFERENCE_PERCENT_TORQUE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_REFERENCE_PERCENT_TORQUE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_REFERENCE_PERCENT_TORQUE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_REFERENCE_PERCENT_TORQUE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_IDLE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_IDLE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_PERCENT_TORQUE_DATA_IDLE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_IDLE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT1)) {
        os += (first ? "" : " | ");
        os += "ENGINE_PERCENT_TORQUE_DATA_POINT1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT2)) {
        os += (first ? "" : " | ");
        os += "ENGINE_PERCENT_TORQUE_DATA_POINT2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT3)) {
        os += (first ? "" : " | ");
        os += "ENGINE_PERCENT_TORQUE_DATA_POINT3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT4)) {
        os += (first ? "" : " | ");
        os += "ENGINE_PERCENT_TORQUE_DATA_POINT4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX)) {
        os += (first ? "" : " | ");
        os += "LAST_SYSTEM_INDEX";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS) {
        return "FUEL_SYSTEM_STATUS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON) {
        return "MALFUNCTION_INDICATOR_LIGHT_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED) {
        return "IGNITION_MONITORS_SUPPORTED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS) {
        return "IGNITION_SPECIFIC_MONITORS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE) {
        return "INTAKE_AIR_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS) {
        return "COMMANDED_SECONDARY_AIR_STATUS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT) {
        return "NUM_OXYGEN_SENSORS_PRESENT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START) {
        return "RUNTIME_SINCE_ENGINE_START";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON) {
        return "DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED) {
        return "WARMUPS_SINCE_CODES_CLEARED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED) {
        return "DISTANCE_TRAVELED_SINCE_CODES_CLEARED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE) {
        return "ABSOLUTE_BAROMETRIC_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE) {
        return "CONTROL_MODULE_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE) {
        return "AMBIENT_AIR_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_WITH_MALFUNCTION_LIGHT_ON) {
        return "TIME_WITH_MALFUNCTION_LIGHT_ON";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_SINCE_TROUBLE_CODES_CLEARED) {
        return "TIME_SINCE_TROUBLE_CODES_CLEARED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "MAX_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_VOLTAGE) {
        return "MAX_OXYGEN_SENSOR_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_CURRENT) {
        return "MAX_OXYGEN_SENSOR_CURRENT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE) {
        return "MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR) {
        return "MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_TYPE) {
        return "FUEL_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_RAIL_ABSOLUTE_PRESSURE) {
        return "FUEL_RAIL_ABSOLUTE_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_OIL_TEMPERATURE) {
        return "ENGINE_OIL_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DRIVER_DEMAND_PERCENT_TORQUE) {
        return "DRIVER_DEMAND_PERCENT_TORQUE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_ACTUAL_PERCENT_TORQUE) {
        return "ENGINE_ACTUAL_PERCENT_TORQUE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_REFERENCE_PERCENT_TORQUE) {
        return "ENGINE_REFERENCE_PERCENT_TORQUE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_IDLE) {
        return "ENGINE_PERCENT_TORQUE_DATA_IDLE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT1) {
        return "ENGINE_PERCENT_TORQUE_DATA_POINT1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT2) {
        return "ENGINE_PERCENT_TORQUE_DATA_POINT2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT3) {
        return "ENGINE_PERCENT_TORQUE_DATA_POINT3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT4) {
        return "ENGINE_PERCENT_TORQUE_DATA_POINT4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX) {
        return "LAST_SYSTEM_INDEX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD)) {
        os += (first ? "" : " | ");
        os += "CALCULATED_ENGINE_LOAD";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_COOLANT_TEMPERATURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_COOLANT_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_COOLANT_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_COOLANT_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_FUEL_TRIM_BANK1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_FUEL_TRIM_BANK1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_FUEL_TRIM_BANK2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_FUEL_TRIM_BANK2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "FUEL_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "INTAKE_MANIFOLD_ABSOLUTE_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_RPM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_RPM)) {
        os += (first ? "" : " | ");
        os += "ENGINE_RPM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_RPM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::VEHICLE_SPEED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::VEHICLE_SPEED)) {
        os += (first ? "" : " | ");
        os += "VEHICLE_SPEED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::VEHICLE_SPEED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::TIMING_ADVANCE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::TIMING_ADVANCE)) {
        os += (first ? "" : " | ");
        os += "TIMING_ADVANCE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::TIMING_ADVANCE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::MAF_AIR_FLOW_RATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::MAF_AIR_FLOW_RATE)) {
        os += (first ? "" : " | ");
        os += "MAF_AIR_FLOW_RATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::MAF_AIR_FLOW_RATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::THROTTLE_POSITION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::THROTTLE_POSITION)) {
        os += (first ? "" : " | ");
        os += "THROTTLE_POSITION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::THROTTLE_POSITION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR1_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR2_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR3_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR4_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR5_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR6_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR7_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_VOLTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR8_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_VOLTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "FUEL_RAIL_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_GAUGE_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_GAUGE_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "FUEL_RAIL_GAUGE_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_GAUGE_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EXHAUST_GAS_RECIRCULATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EXHAUST_GAS_RECIRCULATION)) {
        os += (first ? "" : " | ");
        os += "COMMANDED_EXHAUST_GAS_RECIRCULATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EXHAUST_GAS_RECIRCULATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EXHAUST_GAS_RECIRCULATION_ERROR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EXHAUST_GAS_RECIRCULATION_ERROR)) {
        os += (first ? "" : " | ");
        os += "EXHAUST_GAS_RECIRCULATION_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EXHAUST_GAS_RECIRCULATION_ERROR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EVAPORATIVE_PURGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EVAPORATIVE_PURGE)) {
        os += (first ? "" : " | ");
        os += "COMMANDED_EVAPORATIVE_PURGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EVAPORATIVE_PURGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT)) {
        os += (first ? "" : " | ");
        os += "FUEL_TANK_LEVEL_INPUT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "EVAPORATION_SYSTEM_VAPOR_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1)) {
        os += (first ? "" : " | ");
        os += "CATALYST_TEMPERATURE_BANK1_SENSOR1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR1)) {
        os += (first ? "" : " | ");
        os += "CATALYST_TEMPERATURE_BANK2_SENSOR1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR2)) {
        os += (first ? "" : " | ");
        os += "CATALYST_TEMPERATURE_BANK1_SENSOR2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR2)) {
        os += (first ? "" : " | ");
        os += "CATALYST_TEMPERATURE_BANK2_SENSOR2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_LOAD_VALUE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_LOAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_LOAD_VALUE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_LOAD_VALUE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO)) {
        os += (first ? "" : " | ");
        os += "FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_THROTTLE_POSITION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_THROTTLE_POSITION_B";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_C) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_C)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_THROTTLE_POSITION_C";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_C;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D)) {
        os += (first ? "" : " | ");
        os += "ACCELERATOR_PEDAL_POSITION_D";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E)) {
        os += (first ? "" : " | ");
        os += "ACCELERATOR_PEDAL_POSITION_E";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_F) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_F)) {
        os += (first ? "" : " | ");
        os += "ACCELERATOR_PEDAL_POSITION_F";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_F;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR)) {
        os += (first ? "" : " | ");
        os += "COMMANDED_THROTTLE_ACTUATOR";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ETHANOL_FUEL_PERCENTAGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ETHANOL_FUEL_PERCENTAGE)) {
        os += (first ? "" : " | ");
        os += "ETHANOL_FUEL_PERCENTAGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ETHANOL_FUEL_PERCENTAGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4)) {
        os += (first ? "" : " | ");
        os += "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4)) {
        os += (first ? "" : " | ");
        os += "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_ACCELERATOR_PEDAL_POSITION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_ACCELERATOR_PEDAL_POSITION)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_ACCELERATOR_PEDAL_POSITION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_ACCELERATOR_PEDAL_POSITION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::HYBRID_BATTERY_PACK_REMAINING_LIFE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::HYBRID_BATTERY_PACK_REMAINING_LIFE)) {
        os += (first ? "" : " | ");
        os += "HYBRID_BATTERY_PACK_REMAINING_LIFE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::HYBRID_BATTERY_PACK_REMAINING_LIFE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_INJECTION_TIMING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_INJECTION_TIMING)) {
        os += (first ? "" : " | ");
        os += "FUEL_INJECTION_TIMING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_INJECTION_TIMING;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_FUEL_RATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_FUEL_RATE)) {
        os += (first ? "" : " | ");
        os += "ENGINE_FUEL_RATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_FUEL_RATE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX)) {
        os += (first ? "" : " | ");
        os += "LAST_SYSTEM_INDEX";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD) {
        return "CALCULATED_ENGINE_LOAD";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_COOLANT_TEMPERATURE) {
        return "ENGINE_COOLANT_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1) {
        return "SHORT_TERM_FUEL_TRIM_BANK1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1) {
        return "LONG_TERM_FUEL_TRIM_BANK1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2) {
        return "SHORT_TERM_FUEL_TRIM_BANK2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2) {
        return "LONG_TERM_FUEL_TRIM_BANK2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_PRESSURE) {
        return "FUEL_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE) {
        return "INTAKE_MANIFOLD_ABSOLUTE_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_RPM) {
        return "ENGINE_RPM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::VEHICLE_SPEED) {
        return "VEHICLE_SPEED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::TIMING_ADVANCE) {
        return "TIMING_ADVANCE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::MAF_AIR_FLOW_RATE) {
        return "MAF_AIR_FLOW_RATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::THROTTLE_POSITION) {
        return "THROTTLE_POSITION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE) {
        return "OXYGEN_SENSOR1_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_VOLTAGE) {
        return "OXYGEN_SENSOR2_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_VOLTAGE) {
        return "OXYGEN_SENSOR3_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_VOLTAGE) {
        return "OXYGEN_SENSOR4_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_VOLTAGE) {
        return "OXYGEN_SENSOR5_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_VOLTAGE) {
        return "OXYGEN_SENSOR6_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_VOLTAGE) {
        return "OXYGEN_SENSOR7_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_VOLTAGE) {
        return "OXYGEN_SENSOR8_VOLTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM) {
        return "OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO) {
        return "OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_PRESSURE) {
        return "FUEL_RAIL_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_GAUGE_PRESSURE) {
        return "FUEL_RAIL_GAUGE_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EXHAUST_GAS_RECIRCULATION) {
        return "COMMANDED_EXHAUST_GAS_RECIRCULATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EXHAUST_GAS_RECIRCULATION_ERROR) {
        return "EXHAUST_GAS_RECIRCULATION_ERROR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EVAPORATIVE_PURGE) {
        return "COMMANDED_EVAPORATIVE_PURGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT) {
        return "FUEL_TANK_LEVEL_INPUT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE) {
        return "EVAPORATION_SYSTEM_VAPOR_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1) {
        return "CATALYST_TEMPERATURE_BANK1_SENSOR1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR1) {
        return "CATALYST_TEMPERATURE_BANK2_SENSOR1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR2) {
        return "CATALYST_TEMPERATURE_BANK1_SENSOR2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR2) {
        return "CATALYST_TEMPERATURE_BANK2_SENSOR2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_LOAD_VALUE) {
        return "ABSOLUTE_LOAD_VALUE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO) {
        return "FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION) {
        return "RELATIVE_THROTTLE_POSITION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B) {
        return "ABSOLUTE_THROTTLE_POSITION_B";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_C) {
        return "ABSOLUTE_THROTTLE_POSITION_C";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D) {
        return "ACCELERATOR_PEDAL_POSITION_D";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E) {
        return "ACCELERATOR_PEDAL_POSITION_E";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_F) {
        return "ACCELERATOR_PEDAL_POSITION_F";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR) {
        return "COMMANDED_THROTTLE_ACTUATOR";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ETHANOL_FUEL_PERCENTAGE) {
        return "ETHANOL_FUEL_PERCENTAGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE) {
        return "ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1) {
        return "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2) {
        return "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3) {
        return "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4) {
        return "SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1) {
        return "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2) {
        return "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3) {
        return "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4) {
        return "LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_ACCELERATOR_PEDAL_POSITION) {
        return "RELATIVE_ACCELERATOR_PEDAL_POSITION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::HYBRID_BATTERY_PACK_REMAINING_LIFE) {
        return "HYBRID_BATTERY_PACK_REMAINING_LIFE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_INJECTION_TIMING) {
        return "FUEL_INJECTION_TIMING";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_FUEL_RATE) {
        return "ENGINE_FUEL_RATE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX) {
        return "LAST_SYSTEM_INDEX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsMessageType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsMessageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIBE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE_TO_PUBLISHER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE_TO_PUBLISHER)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIBE_TO_PUBLISHER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE_TO_PUBLISHER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE)) {
        os += (first ? "" : " | ");
        os += "UNSUBSCRIBE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE_TO_PUBLISHER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE_TO_PUBLISHER)) {
        os += (first ? "" : " | ");
        os += "UNSUBSCRIBE_TO_PUBLISHER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE_TO_PUBLISHER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::OFFERING) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::OFFERING)) {
        os += (first ? "" : " | ");
        os += "OFFERING";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::OFFERING;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_REQUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_REQUEST)) {
        os += (first ? "" : " | ");
        os += "AVAILABILITY_REQUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_REQUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_REQUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_REQUEST)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIPTIONS_REQUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_REQUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_RESPONSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "AVAILABILITY_RESPONSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_RESPONSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_CHANGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_CHANGE)) {
        os += (first ? "" : " | ");
        os += "AVAILABILITY_CHANGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_CHANGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_RESPONSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIPTIONS_RESPONSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_RESPONSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_CHANGE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_CHANGE)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIPTIONS_CHANGE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_CHANGE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::DATA) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::DATA)) {
        os += (first ? "" : " | ");
        os += "DATA";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::DATA;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_REQUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_REQUEST)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_ID_REQUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_REQUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_RESPONSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_ID_RESPONSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_RESPONSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_REQUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_REQUEST)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_INFORMATION_REQUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_REQUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_RESPONSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_INFORMATION_RESPONSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_RESPONSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::START_SESSION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::START_SESSION)) {
        os += (first ? "" : " | ");
        os += "START_SESSION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::START_SESSION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::LAST_VMS_MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageType::LAST_VMS_MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "LAST_VMS_MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::LAST_VMS_MESSAGE_TYPE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE) {
        return "SUBSCRIBE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE_TO_PUBLISHER) {
        return "SUBSCRIBE_TO_PUBLISHER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE) {
        return "UNSUBSCRIBE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE_TO_PUBLISHER) {
        return "UNSUBSCRIBE_TO_PUBLISHER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::OFFERING) {
        return "OFFERING";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_REQUEST) {
        return "AVAILABILITY_REQUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_REQUEST) {
        return "SUBSCRIPTIONS_REQUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_RESPONSE) {
        return "AVAILABILITY_RESPONSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_CHANGE) {
        return "AVAILABILITY_CHANGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_RESPONSE) {
        return "SUBSCRIPTIONS_RESPONSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_CHANGE) {
        return "SUBSCRIPTIONS_CHANGE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::DATA) {
        return "DATA";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_REQUEST) {
        return "PUBLISHER_ID_REQUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_RESPONSE) {
        return "PUBLISHER_ID_RESPONSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_REQUEST) {
        return "PUBLISHER_INFORMATION_REQUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_RESPONSE) {
        return "PUBLISHER_INFORMATION_RESPONSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::START_SESSION) {
        return "START_SESSION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::LAST_VMS_MESSAGE_TYPE) {
        return "LAST_VMS_MESSAGE_TYPE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex::MESSAGE_TYPE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::SERVICE_ID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::SERVICE_ID)) {
        os += (first ? "" : " | ");
        os += "SERVICE_ID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::SERVICE_ID;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::CLIENT_ID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::CLIENT_ID)) {
        os += (first ? "" : " | ");
        os += "CLIENT_ID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::CLIENT_ID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::SERVICE_ID) {
        return "SERVICE_ID";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::CLIENT_ID) {
        return "CLIENT_ID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_TYPE)) {
        os += (first ? "" : " | ");
        os += "LAYER_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_SUBTYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_SUBTYPE)) {
        os += (first ? "" : " | ");
        os += "LAYER_SUBTYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_SUBTYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_VERSION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_VERSION)) {
        os += (first ? "" : " | ");
        os += "LAYER_VERSION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_VERSION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_TYPE) {
        return "LAYER_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_SUBTYPE) {
        return "LAYER_SUBTYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_VERSION) {
        return "LAYER_VERSION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_TYPE)) {
        os += (first ? "" : " | ");
        os += "LAYER_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_SUBTYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_SUBTYPE)) {
        os += (first ? "" : " | ");
        os += "LAYER_SUBTYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_SUBTYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_VERSION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_VERSION)) {
        os += (first ? "" : " | ");
        os += "LAYER_VERSION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_VERSION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::PUBLISHER_ID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::PUBLISHER_ID)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_ID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::PUBLISHER_ID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_TYPE) {
        return "LAYER_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_SUBTYPE) {
        return "LAYER_SUBTYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_VERSION) {
        return "LAYER_VERSION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::PUBLISHER_ID) {
        return "PUBLISHER_ID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::PUBLISHER_ID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::PUBLISHER_ID)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_ID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::PUBLISHER_ID;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::NUMBER_OF_OFFERS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::NUMBER_OF_OFFERS)) {
        os += (first ? "" : " | ");
        os += "NUMBER_OF_OFFERS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::NUMBER_OF_OFFERS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::OFFERING_START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::OFFERING_START)) {
        os += (first ? "" : " | ");
        os += "OFFERING_START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::OFFERING_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::PUBLISHER_ID) {
        return "PUBLISHER_ID";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::NUMBER_OF_OFFERS) {
        return "NUMBER_OF_OFFERS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::OFFERING_START) {
        return "OFFERING_START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SEQUENCE_NUMBER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SEQUENCE_NUMBER)) {
        os += (first ? "" : " | ");
        os += "SEQUENCE_NUMBER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SEQUENCE_NUMBER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_LAYERS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_LAYERS)) {
        os += (first ? "" : " | ");
        os += "NUMBER_OF_LAYERS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_LAYERS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS)) {
        os += (first ? "" : " | ");
        os += "NUMBER_OF_ASSOCIATED_LAYERS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SUBSCRIPTIONS_START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SUBSCRIPTIONS_START)) {
        os += (first ? "" : " | ");
        os += "SUBSCRIPTIONS_START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SUBSCRIPTIONS_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SEQUENCE_NUMBER) {
        return "SEQUENCE_NUMBER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_LAYERS) {
        return "NUMBER_OF_LAYERS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS) {
        return "NUMBER_OF_ASSOCIATED_LAYERS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SUBSCRIPTIONS_START) {
        return "SUBSCRIPTIONS_START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::SEQUENCE_NUMBER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::SEQUENCE_NUMBER)) {
        os += (first ? "" : " | ");
        os += "SEQUENCE_NUMBER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::SEQUENCE_NUMBER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS)) {
        os += (first ? "" : " | ");
        os += "NUMBER_OF_ASSOCIATED_LAYERS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::LAYERS_START) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::LAYERS_START)) {
        os += (first ? "" : " | ");
        os += "LAYERS_START";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::LAYERS_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::SEQUENCE_NUMBER) {
        return "SEQUENCE_NUMBER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS) {
        return "NUMBER_OF_ASSOCIATED_LAYERS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::LAYERS_START) {
        return "LAYERS_START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::MESSAGE_TYPE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::MESSAGE_TYPE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::MESSAGE_TYPE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::PUBLISHER_ID) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::PUBLISHER_ID)) {
        os += (first ? "" : " | ");
        os += "PUBLISHER_ID";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::PUBLISHER_ID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::MESSAGE_TYPE) {
        return "MESSAGE_TYPE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::PUBLISHER_ID) {
        return "PUBLISHER_ID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::UserFlags>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::UserFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::NONE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::NONE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::SYSTEM) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::SYSTEM)) {
        os += (first ? "" : " | ");
        os += "SYSTEM";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::SYSTEM;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::GUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::GUEST)) {
        os += (first ? "" : " | ");
        os += "GUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::GUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::EPHEMERAL) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::EPHEMERAL)) {
        os += (first ? "" : " | ");
        os += "EPHEMERAL";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::EPHEMERAL;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::ADMIN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::ADMIN)) {
        os += (first ? "" : " | ");
        os += "ADMIN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::ADMIN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::DISABLED) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::DISABLED;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserFlags::PROFILE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserFlags::PROFILE)) {
        os += (first ? "" : " | ");
        os += "PROFILE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserFlags::PROFILE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::SYSTEM) {
        return "SYSTEM";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::GUEST) {
        return "GUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::EPHEMERAL) {
        return "EPHEMERAL";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::ADMIN) {
        return "ADMIN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserFlags::PROFILE) {
        return "PROFILE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".userId = ";
    os += ::android::hardware::toString(o.userId);
    os += ", .flags = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserInfo& rhs) {
    if (lhs.userId != rhs.userId) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".currentUser = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.currentUser);
    os += ", .numberUsers = ";
    os += ::android::hardware::toString(o.numberUsers);
    os += ", .existingUsers = ";
    os += ::android::hardware::toString(o.existingUsers);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& rhs) {
    if (lhs.currentUser != rhs.currentUser) {
        return false;
    }
    if (lhs.numberUsers != rhs.numberUsers) {
        return false;
    }
    if (lhs.existingUsers != rhs.existingUsers) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& lhs, const ::android::hardware::automotive::vehicle::V2_0::UsersInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT)) {
        os += (first ? "" : " | ");
        os += "FIRST_BOOT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT_AFTER_OTA) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT_AFTER_OTA)) {
        os += (first ? "" : " | ");
        os += "FIRST_BOOT_AFTER_OTA";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT_AFTER_OTA;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::COLD_BOOT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::COLD_BOOT)) {
        os += (first ? "" : " | ");
        os += "COLD_BOOT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::COLD_BOOT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::RESUME) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::RESUME)) {
        os += (first ? "" : " | ");
        os += "RESUME";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::RESUME;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT) {
        return "FIRST_BOOT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT_AFTER_OTA) {
        return "FIRST_BOOT_AFTER_OTA";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::COLD_BOOT) {
        return "COLD_BOOT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::RESUME) {
        return "RESUME";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .requestType = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.requestType);
    os += ", .usersInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.usersInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.requestType != rhs.requestType) {
        return false;
    }
    if (lhs.usersInfo != rhs.usersInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::DEFAULT) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::DEFAULT;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::SWITCH)) {
        os += (first ? "" : " | ");
        os += "SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::CREATE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::CREATE)) {
        os += (first ? "" : " | ");
        os += "CREATE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::CREATE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::SWITCH) {
        return "SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::CREATE) {
        return "CREATE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .action = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.action);
    os += ", .userToSwitchOrCreate = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.userToSwitchOrCreate);
    os += ", .userLocales = ";
    os += ::android::hardware::toString(o.userLocales);
    os += ", .userNameToCreate = ";
    os += ::android::hardware::toString(o.userNameToCreate);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.action != rhs.action) {
        return false;
    }
    if (lhs.userToSwitchOrCreate != rhs.userToSwitchOrCreate) {
        return false;
    }
    if (lhs.userLocales != rhs.userLocales) {
        return false;
    }
    if (lhs.userNameToCreate != rhs.userNameToCreate) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponse& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::LEGACY_ANDROID_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::LEGACY_ANDROID_SWITCH)) {
        os += (first ? "" : " | ");
        os += "LEGACY_ANDROID_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::LEGACY_ANDROID_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_SWITCH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_SWITCH;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_RESPONSE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "VEHICLE_RESPONSE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_RESPONSE;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_REQUEST) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_REQUEST)) {
        os += (first ? "" : " | ");
        os += "VEHICLE_REQUEST";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_REQUEST;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_POST_SWITCH) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_POST_SWITCH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_POST_SWITCH";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_POST_SWITCH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::LEGACY_ANDROID_SWITCH) {
        return "LEGACY_ANDROID_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_SWITCH) {
        return "ANDROID_SWITCH";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_RESPONSE) {
        return "VEHICLE_RESPONSE";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_REQUEST) {
        return "VEHICLE_REQUEST";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_POST_SWITCH) {
        return "ANDROID_POST_SWITCH";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .messageType = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.messageType);
    os += ", .targetUser = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.targetUser);
    os += ", .usersInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.usersInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.messageType != rhs.messageType) {
        return false;
    }
    if (lhs.targetUser != rhs.targetUser) {
        return false;
    }
    if (lhs.usersInfo != rhs.usersInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::SUCCESS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::SUCCESS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::FAILURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::FAILURE) {
        return "FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .messageType = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.messageType);
    os += ", .status = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.status);
    os += ", .errorMessage = ";
    os += ::android::hardware::toString(o.errorMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.messageType != rhs.messageType) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.errorMessage != rhs.errorMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::SwitchUserResponse& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .newUserInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.newUserInfo);
    os += ", .newUserName = ";
    os += ::android::hardware::toString(o.newUserName);
    os += ", .usersInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.usersInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.newUserInfo != rhs.newUserInfo) {
        return false;
    }
    if (lhs.newUserName != rhs.newUserName) {
        return false;
    }
    if (lhs.usersInfo != rhs.usersInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::CreateUserStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::CreateUserStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::SUCCESS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::SUCCESS;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::FAILURE) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::FAILURE) {
        return "FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::CreateUserStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .status = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.status);
    os += ", .errorMessage = ";
    os += ::android::hardware::toString(o.errorMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.errorMessage != rhs.errorMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::CreateUserResponse& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .removedUserInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.removedUserInfo);
    os += ", .usersInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.usersInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.removedUserInfo != rhs.removedUserInfo) {
        return false;
    }
    if (lhs.usersInfo != rhs.usersInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::RemoveUserRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::KEY_FOB) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::KEY_FOB)) {
        os += (first ? "" : " | ");
        os += "KEY_FOB";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::KEY_FOB;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_1) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_1)) {
        os += (first ? "" : " | ");
        os += "CUSTOM_1";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_1;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_2) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_2)) {
        os += (first ? "" : " | ");
        os += "CUSTOM_2";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_2;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_3) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_3)) {
        os += (first ? "" : " | ");
        os += "CUSTOM_3";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_3;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_4) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_4)) {
        os += (first ? "" : " | ");
        os += "CUSTOM_4";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_4;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::KEY_FOB) {
        return "KEY_FOB";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_1) {
        return "CUSTOM_1";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_2) {
        return "CUSTOM_2";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_3) {
        return "CUSTOM_3";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_4) {
        return "CUSTOM_4";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::UNKNOWN) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::UNKNOWN;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATED_CURRENT_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_ANOTHER_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_ANOTHER_USER)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATED_ANOTHER_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_ANOTHER_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER)) {
        os += (first ? "" : " | ");
        os += "NOT_ASSOCIATED_ANY_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER) {
        return "ASSOCIATED_CURRENT_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_ANOTHER_USER) {
        return "ASSOCIATED_ANOTHER_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER) {
        return "NOT_ASSOCIATED_ANY_USER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATE_CURRENT_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_CURRENT_USER) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_CURRENT_USER)) {
        os += (first ? "" : " | ");
        os += "DISASSOCIATE_CURRENT_USER";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_CURRENT_USER;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_ALL_USERS) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_ALL_USERS)) {
        os += (first ? "" : " | ");
        os += "DISASSOCIATE_ALL_USERS";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_ALL_USERS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER) {
        return "ASSOCIATE_CURRENT_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_CURRENT_USER) {
        return "DISASSOCIATE_CURRENT_USER";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_ALL_USERS) {
        return "DISASSOCIATE_ALL_USERS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .userInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.userInfo);
    os += ", .numberAssociationTypes = ";
    os += ::android::hardware::toString(o.numberAssociationTypes);
    os += ", .associationTypes = ";
    os += ::android::hardware::toString(o.associationTypes);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.userInfo != rhs.userInfo) {
        return false;
    }
    if (lhs.numberAssociationTypes != rhs.numberAssociationTypes) {
        return false;
    }
    if (lhs.associationTypes != rhs.associationTypes) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationGetRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .userInfo = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.userInfo);
    os += ", .numberAssociations = ";
    os += ::android::hardware::toString(o.numberAssociations);
    os += ", .associations = ";
    os += ::android::hardware::toString(o.associations);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.userInfo != rhs.userInfo) {
        return false;
    }
    if (lhs.numberAssociations != rhs.numberAssociations) {
        return false;
    }
    if (lhs.associations != rhs.associations) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .numberAssociation = ";
    os += ::android::hardware::toString(o.numberAssociation);
    os += ", .associations = ";
    os += ::android::hardware::toString(o.associations);
    os += ", .errorMessage = ";
    os += ::android::hardware::toString(o.errorMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.numberAssociation != rhs.numberAssociation) {
        return false;
    }
    if (lhs.associations != rhs.associations) {
        return false;
    }
    if (lhs.errorMessage != rhs.errorMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationResponse& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.type);
    os += ", .value = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.type);
    os += ", .value = ";
    os += ::android::hardware::automotive::vehicle::V2_0::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& lhs, const ::android::hardware::automotive::vehicle::V2_0::UserIdentificationSetAssociation& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::vehicle::V2_0::RotaryInputType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::vehicle::V2_0::RotaryInputType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION)) {
        os += (first ? "" : " | ");
        os += "ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION;
    }
    if ((o & ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_AUDIO_VOLUME) == static_cast<int32_t>(::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_AUDIO_VOLUME)) {
        os += (first ? "" : " | ");
        os += "ROTARY_INPUT_TYPE_AUDIO_VOLUME";
        first = false;
        flipped |= ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_AUDIO_VOLUME;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::vehicle::V2_0::RotaryInputType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION) {
        return "ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION";
    }
    if (o == ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_AUDIO_VOLUME) {
        return "ROTARY_INPUT_TYPE_AUDIO_VOLUME";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::vehicle::V2_0::RotaryInputType o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType, 11> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType> = {
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::STRING,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BOOLEAN,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT32_VEC,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::INT64_VEC,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::FLOAT_VEC,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::BYTES,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MIXED,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyType::MASK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleArea, 7> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleArea> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::GLOBAL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WINDOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MIRROR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::SEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::DOOR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::WHEEL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleArea::MASK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup> = {
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::SYSTEM,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::VENDOR,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyGroup::MASK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleProperty, 140> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleProperty> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INVALID,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_VIN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MAKE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MODEL_YEAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_CAPACITY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_BATTERY_CAPACITY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_CONNECTOR_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_FUEL_DOOR_LOCATION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EV_PORT_LOCATION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_DRIVER_SEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_EXTERIOR_DIMENSIONS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INFO_MULTI_EV_PORT_LOCATIONS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_ODOMETER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_VEHICLE_SPEED_DISPLAY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_STEERING_ANGLE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PERF_REAR_STEERING_ANGLE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_COOLANT_TEMP,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_LEVEL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_OIL_TEMP,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENGINE_RPM,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WHEEL_TICK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_DOOR_OPEN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_LEVEL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_OPEN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_CHARGE_PORT_CONNECTED,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::RANGE_REMAINING,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::GEAR_SELECTION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CURRENT_GEAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::PARKING_BRAKE_AUTO_APPLY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_LEVEL_LOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::NIGHT_MODE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TURN_SIGNAL_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::IGNITION_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ABS_ACTIVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TRACTION_CONTROL_ACTIVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_SPEED,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_CURRENT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_SET,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DEFROSTER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AC_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_AC_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_MAX_DEFROST_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_RECIRC_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_DUAL_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SIDE_MIRROR_HEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_STEERING_WHEEL_HEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_TEMPERATURE_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ACTUAL_FAN_SPEED_RPM,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_POWER_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_FAN_DIRECTION_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_AUTO_RECIRC_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_SEAT_VENTILATION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISTANCE_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_VOLUME_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::TIRE_PRESSURE_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::EV_BATTERY_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_SPEED_DISPLAY_UNITS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::ENV_OUTSIDE_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REQ,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_STATE_REPORT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::AP_POWER_BOOTUP_REASON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISPLAY_BRIGHTNESS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_KEY_INPUT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HW_ROTARY_INPUT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DOOR_LOCK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Z_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_Y_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_LOCK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::MIRROR_FOLD,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SELECT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_MEMORY_SET,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_BUCKLED,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BELT_HEIGHT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_FORE_AFT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_1_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_BACKREST_ANGLE_2_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEIGHT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_DEPTH_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_TILT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_FORE_AFT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_HEIGHT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_ANGLE_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_HEADREST_FORE_AFT_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SEAT_OCCUPANCY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_POS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_MOVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::WINDOW_LOCK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::VEHICLE_MAP_SERVICE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_LIVE_FRAME,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_INFO,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::OBD2_FREEZE_FRAME_CLEAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HEADLIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HIGH_BEAM_LIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::FOG_LIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::HAZARD_LIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CABIN_LIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::READING_LIGHTS_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SUPPORT_CUSTOMIZE_VENDOR_PERMISSION,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::DISABLED_OPTIONAL_FEATURES,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::INITIAL_USER_INFO,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::SWITCH_USER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::CREATE_USER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::REMOVE_USER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleProperty::USER_IDENTIFICATION_ASSOCIATION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission, 38> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_DEFAULT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_WINDOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_WINDOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_DOOR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_DOOR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_SEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_MIRROR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_MIRROR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_ENGINE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_ENGINE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_HVAC,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_HVAC,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_LIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_LIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_1,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_1,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_2,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_2,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_3,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_3,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_4,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_4,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_5,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_5,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_6,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_6,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_7,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_7,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_8,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_8,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_9,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_9,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_10,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_10,
    ::android::hardware::automotive::vehicle::V2_0::VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::VACANT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleSeatOccupancyState::OCCUPIED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleLightState, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleLightState> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::OFF,
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightState::DAYTIME_RUNNING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::OFF,
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::DAYTIME_RUNNING,
    ::android::hardware::automotive::vehicle::V2_0::VehicleLightSwitch::AUTOMATIC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::EvConnectorType, 13> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::EvConnectorType> = {
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_AC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_AC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_3_AC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_4_DC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_1_CCS_DC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::IEC_TYPE_2_CCS_DC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_ROADSTER,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_HPWC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::TESLA_SUPERCHARGER,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_AC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::GBT_DC,
    ::android::hardware::automotive::vehicle::V2_0::EvConnectorType::OTHER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::PortLocationType, 7> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::PortLocationType> = {
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::FRONT,
    ::android::hardware::automotive::vehicle::V2_0::PortLocationType::REAR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::FuelType, 13> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::FuelType> = {
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_UNLEADED,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LEADED,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_1,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_DIESEL_2,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_BIODIESEL,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_E85,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LPG,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_CNG,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_LNG,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_ELECTRIC,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_HYDROGEN,
    ::android::hardware::automotive::vehicle::V2_0::FuelType::FUEL_TYPE_OTHER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection, 6> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FLOOR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::FACE_AND_FLOOR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHvacFanDirection::DEFROST_AND_FLOOR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::CRITICALLY_LOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::LOW,
    ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::NORMAL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::HIGH,
    ::android::hardware::automotive::vehicle::V2_0::VehicleOilLevel::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::ENABLE_DEEP_SLEEP_FLAG,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateConfigFlag::CONFIG_SUPPORT_TIMER_POWER_ON_FLAG,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::SHUTDOWN_PREPARE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::CANCEL_SHUTDOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReq::FINISHED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::STATE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReqIndex::ADDITIONAL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_IMMEDIATELY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::CAN_SLEEP,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SHUTDOWN_ONLY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateShutdownParam::SLEEP_IMMEDIATELY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport, 8> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::WAIT_FOR_VHAL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_ENTRY,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::DEEP_SLEEP_EXIT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_POSTPONE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_START,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_PREPARE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport::SHUTDOWN_CANCELLED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_DOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleHwKeyInputAction::ACTION_UP,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleDisplay, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleDisplay> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::MAIN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleDisplay::INSTRUMENT_CLUSTER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleUnit, 32> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleUnit> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SHOULD_NOT_USE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER_PER_SEC,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::RPM,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::HERTZ,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PERCENTILE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIMETER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::METER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::CELSIUS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::FAHRENHEIT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KELVIN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLILITER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::LITER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::GALLON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::US_GALLON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::IMPERIAL_GALLON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::NANO_SECS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::SECS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::YEAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::WATT_HOUR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIAMPERE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIVOLT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILLIWATTS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::AMPERE_HOURS,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOWATT_HOUR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOPASCAL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::PSI,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::BAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::DEGREES,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::MILES_PER_HOUR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleUnit::KILOMETERS_PER_HOUR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode> = {
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::STATIC,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::ON_CHANGE,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyChangeMode::CONTINUOUS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess> = {
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::NONE,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::WRITE,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyAccess::READ_WRITE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus> = {
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::UNAVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleGear, 14> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleGear> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_NEUTRAL,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_REVERSE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_PARK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_DRIVE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_1,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_2,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_3,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_4,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_5,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_6,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_7,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_8,
    ::android::hardware::automotive::vehicle::V2_0::VehicleGear::GEAR_9,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat, 9> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_CENTER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_1_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_CENTER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_2_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_CENTER,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaSeat::ROW_3_RIGHT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow, 10> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::FRONT_WINDSHIELD,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::REAR_WINDSHIELD,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_1_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_2_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROW_3_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_1,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWindow::ROOF_TOP_2,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor, 8> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_1_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_2_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::ROW_3_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::HOOD,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaDoor::REAR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_LEFT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaMirror::DRIVER_CENTER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::NONE,
    ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::RIGHT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleTurnSignal::LEFT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState, 6> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::UNDEFINED,
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::LOCK,
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::OFF,
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ACC,
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::ON,
    ::android::hardware::automotive::vehicle::V2_0::VehicleIgnitionState::START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::SubscribeFlags, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::SubscribeFlags> = {
    ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::UNDEFINED,
    ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_CAR,
    ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags::EVENTS_FROM_ANDROID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::StatusCode, 6> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::StatusCode> = {
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::OK,
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::TRY_AGAIN,
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::INVALID_ARG,
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::NOT_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::ACCESS_DENIED,
    ::android::hardware::automotive::vehicle::V2_0::StatusCode::INTERNAL_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel> = {
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_FRONT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_FRONT,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::LEFT_REAR,
    ::android::hardware::automotive::vehicle::V2_0::VehicleAreaWheel::RIGHT_REAR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_INSUFFICIENT_ENGINE_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_ENGINE_LOAD_OR_DECELERATION,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::OPEN_SYSTEM_FAILURE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelSystemStatus::CLOSED_LOOP_BUT_FEEDBACK_FAULT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::SPARK,
    ::android::hardware::automotive::vehicle::V2_0::Obd2IgnitionMonitorKind::COMPRESSION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors, 6> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::COMPONENTS_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CommonIgnitionMonitors::MISFIRE_INCOMPLETE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors, 22> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::COMPONENTS_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::MISFIRE_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EGR_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_HEATER_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::OXYGEN_SENSOR_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::AC_REFRIGERANT_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::SECONDARY_AIR_SYSTEM_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::HEATED_CATALYST_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SparkIgnitionMonitors::CATALYST_INCOMPLETE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors, 18> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::COMPONENTS_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::FUEL_SYSTEM_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::MISFIRE_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EGR_OR_VVT_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::PM_FILTER_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::EXHAUST_GAS_SENSOR_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::BOOST_PRESSURE_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NOx_SCR_INCOMPLETE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2CompressionIgnitionMonitors::NMHC_CATALYST_INCOMPLETE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::UPSTREAM,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::DOWNSTREAM_OF_CATALYCIC_CONVERTER,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF,
    ::android::hardware::automotive::vehicle::V2_0::Obd2SecondaryAirStatus::PUMP_ON_FOR_DIAGNOSTICS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::Obd2FuelType, 24> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::Obd2FuelType> = {
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::NOT_AVAILABLE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::GASOLINE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::METHANOL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ETHANOL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::DIESEL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::LPG,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::CNG,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::PROPANE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::ELECTRIC,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_GASOLINE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_METHANOL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ETHANOL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_LPG,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_CNG,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_PROPANE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_GASOLINE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ETHANOL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_DIESEL,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_ELECTRIC,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::HYBRID_REGENERATIVE,
    ::android::hardware::automotive::vehicle::V2_0::Obd2FuelType::BIFUEL_RUNNING_DIESEL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex, 33> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_WITH_MALFUNCTION_LIGHT_ON,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::TIME_SINCE_TROUBLE_CODES_CLEARED,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_OXYGEN_SENSOR_CURRENT,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::FUEL_RAIL_ABSOLUTE_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_OIL_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::DRIVER_DEMAND_PERCENT_TORQUE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_ACTUAL_PERCENT_TORQUE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_REFERENCE_PERCENT_TORQUE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_IDLE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT3,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::ENGINE_PERCENT_TORQUE_DATA_POINT4,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex, 72> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_COOLANT_TEMPERATURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_RPM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::VEHICLE_SPEED,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::TIMING_ADVANCE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::MAF_AIR_FLOW_RATE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::THROTTLE_POSITION,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_VOLTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_RAIL_GAUGE_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EXHAUST_GAS_RECIRCULATION,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EXHAUST_GAS_RECIRCULATION_ERROR,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_EVAPORATIVE_PURGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK2_SENSOR2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_LOAD_VALUE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_C,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_F,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ETHANOL_FUEL_PERCENTAGE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::RELATIVE_ACCELERATOR_PEDAL_POSITION,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::HYBRID_BATTERY_PACK_REMAINING_LIFE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::FUEL_INJECTION_TIMING,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::ENGINE_FUEL_RATE,
    ::android::hardware::automotive::vehicle::V2_0::DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsMessageType, 18> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsMessageType> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIBE_TO_PUBLISHER,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::UNSUBSCRIBE_TO_PUBLISHER,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::OFFERING,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_REQUEST,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_REQUEST,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_RESPONSE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::AVAILABILITY_CHANGE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_RESPONSE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::SUBSCRIPTIONS_CHANGE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::DATA,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_REQUEST,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_ID_RESPONSE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_REQUEST,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::PUBLISHER_INFORMATION_RESPONSE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::START_SESSION,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageType::LAST_VMS_MESSAGE_TYPE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex, 1> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsBaseMessageIntegerValuesIndex::MESSAGE_TYPE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::SERVICE_ID,
    ::android::hardware::automotive::vehicle::V2_0::VmsStartSessionMessageIntegerValuesIndex::CLIENT_ID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_SUBTYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerIntegerValuesIndex::LAYER_VERSION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_SUBTYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::LAYER_VERSION,
    ::android::hardware::automotive::vehicle::V2_0::VmsMessageWithLayerAndPublisherIdIntegerValuesIndex::PUBLISHER_ID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::PUBLISHER_ID,
    ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::NUMBER_OF_OFFERS,
    ::android::hardware::automotive::vehicle::V2_0::VmsOfferingMessageIntegerValuesIndex::OFFERING_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SEQUENCE_NUMBER,
    ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_LAYERS,
    ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS,
    ::android::hardware::automotive::vehicle::V2_0::VmsSubscriptionsStateIntegerValuesIndex::SUBSCRIPTIONS_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::SEQUENCE_NUMBER,
    ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::NUMBER_OF_ASSOCIATED_LAYERS,
    ::android::hardware::automotive::vehicle::V2_0::VmsAvailabilityStateIntegerValuesIndex::LAYERS_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex> = {
    ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::MESSAGE_TYPE,
    ::android::hardware::automotive::vehicle::V2_0::VmsPublisherInformationIntegerValuesIndex::PUBLISHER_ID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::UserFlags, 7> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::UserFlags> = {
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::NONE,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::SYSTEM,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::GUEST,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::EPHEMERAL,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::ADMIN,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::DISABLED,
    ::android::hardware::automotive::vehicle::V2_0::UserFlags::PROFILE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType> = {
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT,
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::FIRST_BOOT_AFTER_OTA,
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::COLD_BOOT,
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoRequestType::RESUME,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction> = {
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::DEFAULT,
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::InitialUserInfoResponseAction::CREATE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType> = {
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::LEGACY_ANDROID_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_SWITCH,
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_RESPONSE,
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::VEHICLE_REQUEST,
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserMessageType::ANDROID_POST_SWITCH,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus> = {
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::SUCCESS,
    ::android::hardware::automotive::vehicle::V2_0::SwitchUserStatus::FAILURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::CreateUserStatus, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::CreateUserStatus> = {
    ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::SUCCESS,
    ::android::hardware::automotive::vehicle::V2_0::CreateUserStatus::FAILURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType, 5> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType> = {
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::KEY_FOB,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_1,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_2,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_3,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationType::CUSTOM_4,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue, 4> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue> = {
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::UNKNOWN,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_CURRENT_USER,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::ASSOCIATED_ANOTHER_USER,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationValue::NOT_ASSOCIATED_ANY_USER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue, 3> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue> = {
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::ASSOCIATE_CURRENT_USER,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_CURRENT_USER,
    ::android::hardware::automotive::vehicle::V2_0::UserIdentificationAssociationSetValue::DISASSOCIATE_ALL_USERS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::automotive::vehicle::V2_0::RotaryInputType, 2> hidl_enum_values<::android::hardware::automotive::vehicle::V2_0::RotaryInputType> = {
    ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION,
    ::android::hardware::automotive::vehicle::V2_0::RotaryInputType::ROTARY_INPUT_TYPE_AUDIO_VOLUME,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_VEHICLE_V2_0_TYPES_H
