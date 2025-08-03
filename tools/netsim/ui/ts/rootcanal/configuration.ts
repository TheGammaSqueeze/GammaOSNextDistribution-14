/* eslint-disable */

export const protobufPackage = 'rootcanal.configuration';

export enum ControllerPreset {
  /** DEFAULT - Version 5.3, all features enabled, all quirks disabled. */
  DEFAULT = 'DEFAULT',
  /** LAIRD_BL654 - Official PTS dongle, Laird BL654. */
  LAIRD_BL654 = 'LAIRD_BL654',
  /** CSR_RCK_PTS_DONGLE - Official PTS dongle, CSR rck. */
  CSR_RCK_PTS_DONGLE = 'CSR_RCK_PTS_DONGLE',
  UNRECOGNIZED = 'UNRECOGNIZED',
}

export interface ControllerFeatures {
  leExtendedAdvertising: boolean;
  lePeriodicAdvertising: boolean;
  llPrivacy: boolean;
  le2mPhy: boolean;
  leCodedPhy: boolean;
  /**
   * Enable the support for both LL Connected Isochronous Stream Central
   * and LL Connected Isochronous Stream Peripheral.
   */
  leConnectedIsochronousStream: boolean;
}

export interface ControllerQuirks {
  /**
   * Randomly send ACL payloads before the Connection Complete event
   * is sent to the Host stack.
   */
  sendAclDataBeforeConnectionComplete: boolean;
  /** Configure a default value for the LE random address. */
  hasDefaultRandomAddress: boolean;
  /** Send an Hardware Error event if any command is called before HCI Reset. */
  hardwareErrorBeforeReset: boolean;
}

export interface VendorFeatures {
  /** Enable the support for the CSR vendor command. */
  csr: boolean;
  /**
   * Enable the support for Android vendor commands.
   * Note: not all required vendor commands are necessarily implemented
   * in RootCanal, unimplemented commands will return a Command Status or
   * Command Complete HCI event with the status Unsupported Opcode.
   */
  android: boolean;
}

export interface Controller {
  /**
   * Configure the controller preset. Presets come with a pre-selection
   * of features and quirks, but these can be overridden with the next fields.
   */
  preset: ControllerPreset;
  /** Configure support for controller features. */
  features:|ControllerFeatures|undefined;
  /**
   * Enable controller quirks.
   * Quirks are behaviors observed in real controllers that are not valid
   * according to the specification.
   */
  quirks:|ControllerQuirks|undefined;
  /**
   * Enable strict mode (defaults to enabled).
   * Activate assertion checks in RootCanal for missing RootCanal features
   * or Host stack misbehavior.
   */
  strict: boolean;
  /** Configure support for vendor features. */
  vendor: VendorFeatures|undefined;
}

export interface TcpServer {
  /**
   * Configure the TCP port on which the controller with this defined
   * configuration will be served.
   */
  tcpPort: number;
  /** Controller configuration for this port. */
  configuration: Controller|undefined;
}

export interface Configuration {
  tcpServer: TcpServer[];
}
