/* eslint-disable */

export const protobufPackage = 'netsim.common';

/** Radio Type (BT, WIFI, UWB, BLEBEACON) */
export enum ChipKind {
  /** UNSPECIFIED - Default to UNSPECIFIED */
  UNSPECIFIED = 'UNSPECIFIED',
  BLUETOOTH = 'BLUETOOTH',
  WIFI = 'WIFI',
  UWB = 'UWB',
  /** BLUETOOTH_BEACON - Built-in Bluetooth Beacon for netsim. */
  BLUETOOTH_BEACON = 'BLUETOOTH_BEACON',
  UNRECOGNIZED = 'UNRECOGNIZED',
}
