import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';

import {Device, Notifiable, SimulationInfo, simulationState,} from './device-observer.js';

@customElement('ns-device-map')
export class DeviceMap extends LitElement implements Notifiable {
  /**
   * List of devices currently on the netsim.
   */
  @property() deviceData: Device[] = [];

  /**
   * Index of the background image displayed.
   */
  @property() imageIdx = 0;

  /**
   * Number of images available for the background.
   */
  @property() numImages = 3;

  @property({type: Boolean, reflect: true}) isometric: boolean = false;

  connectedCallback() {
    super.connectedCallback();  // eslint-disable-line
    simulationState.registerObserver(this);
    window.addEventListener('map-button-clicked', this.onChangeMap);
    window.addEventListener(
        'isometric-button-clicked', this.handleIsometricView);
  }

  disconnectedCallback() {
    window.removeEventListener(
        'isometric-button-clicked', this.handleIsometricView);
    window.removeEventListener('map-button-clicked', this.onChangeMap);
    simulationState.removeObserver(this);
    super.disconnectedCallback();  // eslint-disable-line
  }

  static styles = css`
    #dropzone {
      margin-left: 200px;
      margin-right: 200px;
      transition: transform 2s, top 2s;
      transform-style: preserve-3d;
    }

    .box {
      position: relative;
      width: 1000px; //40vw;
      height: 1000px; //40vh;
      border: solid 1px rgb(198, 210, 255);
      margin: 2.5em auto;
    }

    .pattern0 {
      background-image: url(./assets/grid-background.svg);
    }

    .pattern1 {
      background-image: url(./assets/polar-background.svg);
      background-size: 1150px 1150px;
      background-position: center;
    }

    .pattern2 {
      background-image: url(./assets/hexagonal-background.png);
      background-size: 1175px 1175px;
      background-position: center;
    }

    .container {
      display: flex;
      width: 100%;
    }

    .contentA {
      flex: 2;
    }

    .contentB {
      flex: 2;
    }

    ns-device-dragzone {
      transform-style: inherit;
    }
  `;

  onNotify(data: SimulationInfo): void {
    this.deviceData = data.devices;
    this.requestUpdate();
  }

  private onChangeMap = () => {
    this.imageIdx = (this.imageIdx + 1) % this.numImages;
  };

  private handleIsometricView = () => {
    this.isometric = !this.isometric;
  };

  checkBle(device: Device):
      boolean{return device.chips.at(0)?.bleBeacon !== undefined}

  render() {
    const rainbow = [
      'red',
      'orange',
      'yellow',
      'green',
      'blue',
      'indigo',
      'purple',
    ];
    const viewStyle = this.isometric ?
        `perspective(200rem) rotateX(60deg) rotateY(0deg) rotateZ(0deg) scale3d(0.8,0.8,0.8); top: 250px` :
        'none; top: 0px;';

    return html`
      <ns-device-dropzone role="widget" tabindex="0" aria-label="Device map">
        <div id="dropzone" class="box pattern${this.imageIdx}">
          ${
        this.deviceData.map(
            (device, idx) => html`
              ${
                true ?  // TODO manage device.visible in Web UI
                    this.checkBle(device) ?
                    html`
                    <ns-device-dragzone
                      .action=${'move'}
                      style=${styleMap({
                      position: 'absolute',
                      left: `${device.position.x * 100}px`,
                      top: `${device.position.y * 100}px`,
                    })}
                    >
                      <ns-pyramid-sprite
                        id=${device.name}
                        .color=${rainbow[idx % rainbow.length]}
                        .size=${'30px'}
                        .controls=${true}
                        yaw=${device.orientation.yaw}
                        pitch=${device.orientation.pitch}
                        roll=${device.orientation.roll}
                        posZ=${device.position.z * 100}
                        role="widget"
                        tabindex="1"
                        aria-label="${device.name} on Device Map, Position: ${
                        Math.round(device.position.x * 100)}, ${
                        Math.round(device.position.y * 100)}, ${
                        Math.round(
                            device.position.z * 100)}, Orientation: yaw: ${
                        device.orientation.yaw}, pitch: ${
                        device.orientation.pitch}, roll: ${
                        device.orientation.roll}"
                        aria-live="polite"
                      ></ns-pyramid-sprite>
                    </ns-device-dragzone>
                  ` :
                    html`
                  <ns-device-dragzone
                    .action=${'move'}
                    style=${styleMap({
                      position: 'absolute',
                      left: `${device.position.x * 100}px`,
                      top: `${device.position.y * 100}px`,
                    })}
                  >
                    <ns-cube-sprite
                      id=${device.name}
                      .color=${rainbow[idx % rainbow.length]}
                      .size=${'30px'}
                      .controls=${true}
                      yaw=${device.orientation.yaw}
                      pitch=${device.orientation.pitch}
                      roll=${device.orientation.roll}
                      posZ=${device.position.z * 100}
                      role="widget"
                      tabindex="1"
                      aria-label="${device.name} on Device Map, Position: ${
                        Math.round(device.position.x * 100)}, ${
                        Math.round(device.position.y * 100)}, ${
                        Math.round(
                            device.position.z * 100)}, Orientation: yaw: ${
                        device.orientation.yaw}, pitch: ${
                        device.orientation.pitch}, roll: ${
                        device.orientation.roll}"
                      aria-live="polite"
                    ></ns-cube-sprite>
                  </ns-device-dragzone>
                ` :
                    html``}
            `)}
        </div>
        <style>
          #dropzone {
            transform: ${viewStyle};
          }
        </style>
      </ns-device-dropzone>
    `;
  }
}
