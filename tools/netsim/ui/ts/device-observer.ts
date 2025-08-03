import {Capture, Chip, Chip_Radio, Device as ProtoDevice, State,} from './netsim/model.js';

// URL for netsim
const DEVICES_URL = './v1/devices';
const CAPTURES_URL = './v1/captures';

/**
 * Interface for a method in notifying the subscribed observers.
 * Subscribed observers must implement this interface.
 */
export interface Notifiable {
  onNotify(data: {}): void;
}

/**
 * Modularization of Device.
 * Contains getters and setters for properties in Device interface.
 */
export class Device {
  device: ProtoDevice;

  constructor(device: ProtoDevice) {
    this.device = device;
  }

  get name(): string {
    return this.device.name;
  }

  set name(value: string) {
    this.device.name = value;
  }

  get position(): {x: number; y: number; z: number} {
    const result = {x: 0, y: 0, z: 0};
    if ('position' in this.device && this.device.position &&
        typeof this.device.position === 'object') {
      if ('x' in this.device.position &&
          typeof this.device.position.x === 'number') {
        result.x = this.device.position.x;
      }
      if ('y' in this.device.position &&
          typeof this.device.position.y === 'number') {
        result.y = this.device.position.y;
      }
      if ('z' in this.device.position &&
          typeof this.device.position.z === 'number') {
        result.z = this.device.position.z;
      }
    }
    return result;
  }

  set position(pos: {x: number; y: number; z: number}) {
    this.device.position = pos;
  }

  get orientation(): {yaw: number; pitch: number; roll: number} {
    const result = {yaw: 0, pitch: 0, roll: 0};
    if ('orientation' in this.device && this.device.orientation &&
        typeof this.device.orientation === 'object') {
      if ('yaw' in this.device.orientation &&
          typeof this.device.orientation.yaw === 'number') {
        result.yaw = this.device.orientation.yaw;
      }
      if ('pitch' in this.device.orientation &&
          typeof this.device.orientation.pitch === 'number') {
        result.pitch = this.device.orientation.pitch;
      }
      if ('roll' in this.device.orientation &&
          typeof this.device.orientation.roll === 'number') {
        result.roll = this.device.orientation.roll;
      }
    }
    return result;
  }

  set orientation(ori: {yaw: number; pitch: number; roll: number}) {
    this.device.orientation = ori;
  }

  // TODO modularize getters and setters for Chip Interface
  get chips(): Chip[] {
    return this.device.chips ?? [];
  }

  // TODO modularize getters and setters for Chip Interface
  set chips(value: Chip[]) {
    this.device.chips = value;
  }

  get visible(): boolean {
    return this.device.visible === State.ON ? true : false;
  }

  set visible(value: boolean) {
    this.device.visible = value ? State.ON : State.OFF;
  }

  toggleChipState(radio: Chip_Radio) {
    radio.state = radio.state === State.ON ? State.OFF : State.ON;
  }

  toggleCapture(device: Device, chip: Chip) {
    if ('capture' in chip && chip.capture) {
      chip.capture = chip.capture === State.ON ? State.OFF : State.ON;
      simulationState.patchDevice({
        device: {
          name: device.name,
          chips: device.chips,
        },
      });
    }
  }
}

/**
 * The most recent state of the simulation.
 * Subscribed observers must refer to this info and patch accordingly.
 */
export interface SimulationInfo {
  devices: Device[];
  captures: Capture[];
  selectedId: string;
  dimension: {x: number; y: number; z: number};
  lastModified: string;
}

interface Observable {
  registerObserver(elem: Notifiable): void;
  removeObserver(elem: Notifiable): void;
}

class SimulationState implements Observable {
  private observers: Notifiable[] = [];

  private simulationInfo: SimulationInfo = {
    devices: [],
    captures: [],
    selectedId: '',
    dimension: {x: 10, y: 10, z: 0},
    lastModified: '',
  };

  constructor() {
    // initial GET
    this.invokeGetDevice();
    this.invokeListCaptures();
  }

  async invokeGetDevice() {
    await fetch(DEVICES_URL, {
      method: 'GET',
    })
        .then(response => response.json())
        .then(data => {
          this.fetchDevice(data.devices);
          this.updateLastModified(data.lastModified);
        })
        .catch(error => {
          // eslint-disable-next-line
          console.log('Cannot connect to netsim web server', error);
        });
  }

  async invokeListCaptures() {
    await fetch(CAPTURES_URL, {
      method: 'GET',
    })
        .then(response => response.json())
        .then(data => {
          this.simulationInfo.captures = data.captures;
          this.notifyObservers();
        })
        .catch(error => {
          console.log('Cannot connect to netsim web server', error);
        });
  }

  fetchDevice(devices?: ProtoDevice[]) {
    this.simulationInfo.devices = [];
    if (devices) {
      this.simulationInfo.devices = devices.map(device => new Device(device));
    }
    this.notifyObservers();
  }

  getLastModified() {
    return this.simulationInfo.lastModified;
  }

  updateLastModified(timestamp: string) {
    this.simulationInfo.lastModified = timestamp;
  }

  patchSelected(id: string) {
    this.simulationInfo.selectedId = id;
    this.notifyObservers();
  }

  handleDrop(id: string, x: number, y: number) {
    for (const device of this.simulationInfo.devices) {
      if (id === device.name) {
        device.position = {x, y, z: device.position.z};
        this.patchDevice({
          device: {
            name: device.name,
            position: device.position,
          },
        });
        break;
      }
    }
  }

  patchCapture(id: string, state: string) {
    fetch(CAPTURES_URL + '/' + id, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'text/plain',
        'Content-Length': state.length.toString(),
      },
      body: state,
    });
    this.notifyObservers();
  }

  patchDevice(obj: object) {
    const jsonBody = JSON.stringify(obj);
    fetch(DEVICES_URL, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': jsonBody.length.toString(),
      },
      body: jsonBody,
    })
        .then(response => response.json())
        .catch(error => {
          // eslint-disable-next-line
          console.error('Error:', error);
        });
    this.notifyObservers();
  }

  registerObserver(elem: Notifiable) {
    this.observers.push(elem);
    elem.onNotify(this.simulationInfo);
  }

  removeObserver(elem: Notifiable) {
    const index = this.observers.indexOf(elem);
    this.observers.splice(index, 1);
  }

  notifyObservers() {
    for (const observer of this.observers) {
      observer.onNotify(this.simulationInfo);
    }
  }

  getDeviceList() {
    return this.simulationInfo.devices;
  }
}

/** Subscribed observers must register itself to the simulationState */
export const simulationState = new SimulationState();

async function subscribeCaptures() {
  const delay = (ms: number) => new Promise(res => setTimeout(res, ms));
  while (true) {
    await simulationState.invokeListCaptures();
    await simulationState.invokeGetDevice();
    await delay(1000);
  }
}

async function subscribeDevices() {
  await simulationState.invokeGetDevice();
  while (true) {
    const jsonBody = JSON.stringify({
      lastModified: simulationState.getLastModified(),
    });
    await fetch(DEVICES_URL, {
      method: 'SUBSCRIBE',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': jsonBody.length.toString(),
      },
      body: jsonBody,
    })
        .then(response => response.json())
        .then(data => {
          simulationState.fetchDevice(data.devices);
          simulationState.updateLastModified(data.lastModified);
        })
        .catch(error => {
          // eslint-disable-next-line
          console.log('Cannot connect to netsim web server', error);
        });
  }
}

subscribeCaptures();
subscribeDevices();
