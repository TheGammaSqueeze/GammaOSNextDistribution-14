/* eslint-disable */
import type {Controller} from '../rootcanal/configuration';

import type {ChipKind} from './common';

export const protobufPackage = 'netsim.model';

/** Radio Type used by netsim-grpc in testing module */
export enum PhyKind {
  /** NONE - Unknown Chip Kind */
  NONE = 'NONE',
  BLUETOOTH_CLASSIC = 'BLUETOOTH_CLASSIC',
  BLUETOOTH_LOW_ENERGY = 'BLUETOOTH_LOW_ENERGY',
  WIFI = 'WIFI',
  UWB = 'UWB',
  WIFI_RTT = 'WIFI_RTT',
  UNRECOGNIZED = 'UNRECOGNIZED',
}

/**
 * An explicit valued boolean.
 * This is to avoid having default values.
 */
export enum State {
  /** UNKNOWN - Default State */
  UNKNOWN = 'UNKNOWN',
  /** ON - True state */
  ON = 'ON',
  /** OFF - False state */
  OFF = 'OFF',
  UNRECOGNIZED = 'UNRECOGNIZED',
}

/**
 * A 3D position. A valid Position must have both x and y coordinates.
 * The position coordinates are in meters.
 */
export interface Position {
  /** positional value of x axis */
  x: number;
  /** positional value of y axis */
  y: number;
  /** positional value of z axis */
  z: number;
}

/**
 * A 3D orientation. A valid Orientation must have yaw, pitch, and roll.
 * The orientation values are in degrees.
 */
export interface Orientation {
  /** Rotational value around vertical axis. */
  yaw: number;
  /** Rotational value around side-to-side axis */
  pitch: number;
  /** Rotational value around front-to-back axis */
  roll: number;
}

/** Model of a Chip in netsim */
export interface Chip {
  /** Type of Radio (BT, WIFI, UWB) */
  kind: ChipKind;
  /** Chip Identifier */
  id: number;
  /** optional like "rear-right" */
  name: string;
  /** optional like Quorvo */
  manufacturer: string;
  /** optional like DW300 */
  productName: string;
  /** Dual mode of Bluetooth */
  bt?:|Chip_Bluetooth|undefined;
  /** Bluetooth Beacon Low Energy */
  bleBeacon?:|Chip_BleBeacon|undefined;
  /** UWB */
  uwb?:|Chip_Radio|undefined;
  /** WIFI */
  wifi?: Chip_Radio|undefined;
}

/** Radio state associated with the Chip */
export interface Chip_Radio {
  /** Boolean state of Radio */
  state: State;
  /** Maximum range of Radio */
  range: number;
  /** Transmitted packet counts */
  txCount: number;
  /** Received packet counts */
  rxCount: number;
}

/** Bluetooth has 2 radios */
export interface Chip_Bluetooth {
  /** BLE */
  lowEnergy:|Chip_Radio|undefined;
  /** Bluetooth Classic */
  classic:|Chip_Radio|undefined;
  /** BD_ADDR address */
  address: string;
  /** rootcanal Controller Properties */
  btProperties: Controller|undefined;
}

/**
 * BleBeacon has numerous configurable fields.
 * Address, AdvertiseSetting, AdvertiseData.
 */
export interface Chip_BleBeacon {
  /** Bluetooth Radio */
  bt:|Chip_Bluetooth|undefined;
  /** BD_ADDR address */
  address: string;
  /** Settings on how beacon functions */
  settings:|Chip_BleBeacon_AdvertiseSettings|undefined;
  /** Advertising Data */
  advData:|Chip_BleBeacon_AdvertiseData|undefined;
  /** Scan Response Data */
  scanResponse: Chip_BleBeacon_AdvertiseData|undefined;
}

/** Advertise Settigns dictate how the beacon functions on the netwwork. */
export interface Chip_BleBeacon_AdvertiseSettings {
  /** How often the beacon sends an advertising packet */
  advertiseMode?:|Chip_BleBeacon_AdvertiseSettings_AdvertiseMode|undefined;
  /** Numeric time interval between advertisements in ms. */
  milliseconds?:|number|undefined;
  /** Amount of power to send transmission */
  txPowerLevel?:|Chip_BleBeacon_AdvertiseSettings_AdvertiseTxPower|undefined;
  /** Numeric transmission power in dBm. Must be within [-127, 127]. */
  dbm?:|number|undefined;
  /** Whether the beacon will respond to scan requests. */
  scannable: boolean;
  /** Limit adveritising to a given amoutn of time. */
  timeout: number;
}

/**
 * How often the beacon sends an advertising packet
 *
 * Referenced From
 * packages/modules/Bluetooth/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java#151
 */
export enum Chip_BleBeacon_AdvertiseSettings_AdvertiseMode {
  /**
   * LOW_POWER - Perform Bluetooth LE advertising in low power mode. This is the
   * default and preferred advertising mode as it consumes the least power
   */
  LOW_POWER = 'LOW_POWER',
  /**
   * BALANCED - Perform Bluetooth LE advertising in balanced power mode. This is
   * balanced between advertising frequency and power consumption
   */
  BALANCED = 'BALANCED',
  /**
   * LOW_LATENCY - Perform Bluetooth LE advertising in low latency, high power
   * mode. This has the highest power consumption and should not be used for
   * continuous background advertising
   */
  LOW_LATENCY = 'LOW_LATENCY',
  UNRECOGNIZED = 'UNRECOGNIZED',
}

/**
 * Amount of power to send transmissions. Correlates with signal strength
 * and range. Inversely correlates with energy consumption.
 *
 * Referenced From
 * packages/modules/Bluetooth/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java#159
 */
export enum Chip_BleBeacon_AdvertiseSettings_AdvertiseTxPower {
  /**
   * ULTRA_LOW - Advertise using the lowest transmission (TX) power level. Low
   * transmission power can be used to restrict the visibility range of
   * advertising packets
   */
  ULTRA_LOW = 'ULTRA_LOW',
  /** LOW - Advertise using low TX power level. This is the default */
  LOW = 'LOW',
  /** MEDIUM - Advertise using medium TX power level */
  MEDIUM = 'MEDIUM',
  /**
   * HIGH - Advertise using high TX power level. This corresponds to largest
   * visibility range of the advertising packet
   */
  HIGH = 'HIGH',
  UNRECOGNIZED = 'UNRECOGNIZED',
}

/**
 * These parameters dictate which fields are included in advertisements or
 * scan responses sent by the beacon. Beacons in Betosim will support a
 * subset of the complete list of fields found in "Supplement to the
 * Bluetooth Core Specification"
 */
export interface Chip_BleBeacon_AdvertiseData {
  /** Whether the device name should be included in advertise packet. */
  includeDeviceName: boolean;
  /**
   * Whether the transmission power level should be included in the
   * advertise packet.
   */
  includeTxPowerLevel: boolean;
  /** Manufacturer specific data. */
  manufacturerData: Uint8Array;
  /** GATT services supported by the devices */
  services: Chip_BleBeacon_AdvertiseData_Service[];
}

/** GATT service proto */
export interface Chip_BleBeacon_AdvertiseData_Service {
  /** UUID of a Bluetooth GATT service for the beacon */
  uuid: string;
  /** Bytes of data associated with a GATT service provided by the device */
  data: Uint8Array;
}

/**
 * Protobuf for ChipCreate
 *
 * This is used specifically for CreateDevice
 */
export interface ChipCreate {
  /** Type of Radio (BT, WIFI, UWB) */
  kind: ChipKind;
  /** BD_ADDR address */
  address: string;
  /** optional like "rear-right" */
  name: string;
  /** optional like Quorvo */
  manufacturer: string;
  /** optional like DW300 */
  productName: string;
  /** BleBeaconCreate protobuf */
  bleBeacon?:|ChipCreate_BleBeaconCreate|undefined;
  /** optional rootcanal configuration for bluetooth chipsets. */
  btProperties: Controller|undefined;
}

/**
 * Protobuf for BleBeaconCreate
 * Beacon specific information during creation
 */
export interface ChipCreate_BleBeaconCreate {
  /** BD_ADDR address */
  address: string;
  /** Settings on how beacon functions */
  settings:|Chip_BleBeacon_AdvertiseSettings|undefined;
  /** Advertising Data */
  advData:|Chip_BleBeacon_AdvertiseData|undefined;
  /** Scan Response Data */
  scanResponse: Chip_BleBeacon_AdvertiseData|undefined;
}

/** Device model for netsim */
export interface Device {
  /** Device Identifier */
  id: number;
  /** Device name. Settable at creation */
  name: string;
  /** Visibility of device in the scene */
  visible: State;
  /** Position of Device */
  position:|Position|undefined;
  /** Orientation of Device */
  orientation:|Orientation|undefined;
  /** Chips in Device. Device can have multiple chips of the same kind. */
  chips: Chip[];
}

/**
 * Protobuf for DeviceCreate
 *
 * This is used specifically for CreateDevice
 */
export interface DeviceCreate {
  /** Device name. */
  name: string;
  /** Position of Device */
  position:|Position|undefined;
  /** Orientation of Device */
  orientation:|Orientation|undefined;
  /** Chips in Device */
  chips: ChipCreate[];
}

/** Scene model for netsim */
export interface Scene {
  /** List of devices in the scene. */
  devices: Device[];
}

/** Capture model for netsim */
export interface Capture {
  /** Capture Identifier (Same as Chip Identifier) */
  id: number;
  /** Type of Radio (BT, WIFI, UWB) */
  chipKind: ChipKind;
  /** device AVD name */
  deviceName: string;
  /** capture state */
  state: State;
  /** size of current capture */
  size: number;
  /** number of records in current capture */
  records: number;
  /**
   * Timestamp of the most recent start_capture
   * When "state" is set "ON", timestamp is updated.
   */
  timestamp:|Date|undefined;
  /**
   * True if capture for the chip is attached to netsim.
   * False if chip has been detached from netsim.
   */
  valid: boolean;
}
