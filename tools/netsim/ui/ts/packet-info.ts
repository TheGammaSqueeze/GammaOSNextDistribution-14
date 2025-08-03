import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

import {Device, Notifiable, SimulationInfo, simulationState,} from './device-observer.js';
import {Capture, State} from './netsim/model.js';

@customElement('ns-packet-info')
export class PacketInformation extends LitElement implements Notifiable {
  /**
   * List of captures currently on the netsim.
   */
  @property() captureData: Capture[] = [];

  /**
   * List of devices currently on the netsim.
   */
  @property() deviceData: Device[] = [];

  static styles = css`
    :host {
      display: flex;
      justify-content: center;
      align-items: flex-start;
      height: 100vh;
    }

    .panel {
      cursor: pointer;
      display: grid;
      place-content: center;
      color: black;
      font-size: 25px;
      font-family: 'Lato', sans-serif;
      border: 5px solid black;
      border-radius: 12px;
      margin: 10px;
      padding: 10px;
      background-color: #ffffff;
      max-width: max-content;
      float: left;
    }

    .title {
      font-weight: bold;
      text-transform: uppercase;
      text-align: center;
      margin-bottom: 10px;
    }

    .label {
      text-align: left;
    }

    .styled-table {
      border-collapse: collapse;
      margin: 25px 0;
      font-size: 20px;
      font-family: sans-serif;
      width: 100%;
      box-shadow: 0 0 20px rgba(0, 0, 0, 0.15);
    }

    .styled-table thead tr {
      background-color: #009879;
      color: #ffffff;
      text-align: left;
    }

    .styled-table th,
    .styled-table td {
      padding: 12px 15px;
      text-align: left;
    }

    .styled-table tbody tr {
      border-bottom: 1px solid #dddddd;
    }

    .styled-table tbody tr:nth-of-type(even) {
      background-color: #cac0c0;
    }

    input[type='button'] {
      height: 2rem;
      font-size: inherit;
    }

    input[type='checkbox'].switch_1 {
      font-size: 30px;
      -webkit-appearance: none;
      -moz-appearance: none;
      appearance: none;
      width: 3.5em;
      height: 1.5em;
      background: #ddd;
      border-radius: 3em;
      position: relative;
      cursor: pointer;
      outline: none;
      -webkit-transition: all 0.2s ease-in-out;
      transition: all 0.2s ease-in-out;
    }

    input[type='checkbox'].switch_1:checked {
      background: #0ebeff;
    }

    input[type='checkbox'].switch_1:after {
      position: absolute;
      content: '';
      width: 1.5em;
      height: 1.5em;
      border-radius: 50%;
      background: #fff;
      -webkit-box-shadow: 0 0 0.25em rgba(0, 0, 0, 0.3);
      box-shadow: 0 0 0.25em rgba(0, 0, 0, 0.3);
      -webkit-transform: scale(0.7);
      transform: scale(0.7);
      left: 0;
      -webkit-transition: all 0.2s ease-in-out;
      transition: all 0.2s ease-in-out;
    }

    input[type='checkbox'].switch_1:checked:after {
      left: calc(100% - 1.5em);
    }

    button {
      display: inline-block;
      padding: 12px 24px;
      background-color: #4CAF50;
      color: #FFFFFF;
      font-size: 18px;
      font-weight: bold;
      text-align: center;
      text-decoration: none;
      border: none;
      cursor: pointer;
      transition: background-color 0.3s ease;
    }

    button:hover {
      background-color: #45a049;
      transition: 0.5s;
    }
  `;

  connectedCallback() {
    super.connectedCallback();  // eslint-disable-line
    simulationState.registerObserver(this);
  }

  disconnectedCallback() {
    simulationState.removeObserver(this);
    super.disconnectedCallback();  // eslint-disable-line
  }

  onNotify(data: SimulationInfo) {
    this.captureData = data.captures;
    this.deviceData = data.devices;
    this.requestUpdate();
  }

  toggleCapture(capture: Capture) {
    let id = capture.id.toString();
    let state = capture.state === State.OFF ? '1' : '2';
    simulationState.patchCapture(id, state);
  }

  private handleGetChips(device: Device) {
    let btTable = html``;
    let uwbTable = html``;
    let wifiTable = html``;
    if ('chips' in device && device.chips) {
      for (const chip of device.chips) {
        if ('bt' in chip && chip.bt) {
          let bleTable = html``;
          let bclassicTable = html``;
          if ('lowEnergy' in chip.bt && chip.bt.lowEnergy) {
            bleTable = html`
              <tr>
                <td>BLE</td>
                <td>${chip.bt.lowEnergy.rxCount ?? 0}</td>
                <td>${chip.bt.lowEnergy.txCount ?? 0}</td>
              </tr>
            `;
          }
          if ('classic' in chip.bt && chip.bt.classic) {
            bclassicTable = html`
              <tr>
                <td>Bluetooth Classic</td>
                <td>${chip.bt.classic.rxCount ?? 0}</td>
                <td>${chip.bt.classic.txCount ?? 0}</td>
              </tr>
            `;
          }
          btTable = html`${bleTable} ${bclassicTable}`;
        }
        if ('uwb' in chip && chip.uwb) {
          uwbTable = html`
            <tr>
              <td>UWB</td>
              <td>${chip.uwb.rxCount ?? 0}</td>
              <td>${chip.uwb.txCount ?? 0}</td>
            </tr>
          `;
        }
        if ('wifi' in chip && chip.wifi) {
          wifiTable = html`
            <tr>
              <td>WIFI</td>
              <td>${chip.wifi.rxCount ?? 0}</td>
              <td>${chip.wifi.txCount ?? 0}</td>
            </tr>
          `;
        }
      }
    }
    return html`
      ${btTable}
      ${uwbTable}
      ${wifiTable}
    `;
  }

  private handleListCaptures(capture: Capture) {
    return html`
      <tr>
        <td>${capture.deviceName}</td>
        <td>${capture.chipKind}</td>
        <td>${capture.size}</td>
        <td>${capture.records}</td>
        <td>
        <input
                type="checkbox"
                class="switch_1"
                .checked=${capture.state === State.ON}
                @click=${() => {
      this.toggleCapture(capture);
    }}
              />
        </td>
        <td>
          <a
            href="./v1/captures/${capture.id}"
            target="_blank"
            type="application/vnd.tcpdump.pcap"
            ><button>Download</button></a
          >
        </td>
      </tr>
    `
  }

  render() {
    return html`
      <div class="panel">
        <div class="title">Packet Info</div>
        ${this.deviceData.map(device => html`
              <div class="label">${device.name}</div>
              <table class="styled-table">
                <tr>
                  <th>Radio</th>
                  <th>RX Count</th>
                  <th>TX Count</th>
                </tr>
                ${this.handleGetChips(device)}
              </table>
            `)}
      </div>
      <div class="panel">
        <div class="title">Packet Capture</div>
        <table class="styled-table">
          <tr>
            <th>Device Name</th>
            <th>Chip Kind</th>
            <th>Bytes</th>
            <th>Records</th>
            <th>Capture State</th>
            <th>Download Pcap</th>
          </tr>
          ${this.captureData.map(capture => this.handleListCaptures(capture))}
        </table>
      </div>
    `;
  }
}
