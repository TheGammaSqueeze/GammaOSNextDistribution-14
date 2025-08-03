import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

@customElement('netsim-app')
export class NetsimApp extends LitElement {
  /**
   * The view of the netsim app: main (map view), trace (packet trace view)
   */
  @property() viewMode: string = 'main';

  /**
   * The version of netsim.
   */
  @property() version: string = '';

  static styles = css`
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

    #bottom {
      position: relative;
      bottom: 0;
      left: 0;
      font-size: 20px;
    }
  `;

  constructor() {
    super();
    this.invokeGetVersion();
  }

  invokeGetVersion() {
    fetch('./version', {
      method: 'GET',
    })
        .then(response => response.json())
        .then(data => {
          this.version = data.version;
        })
        .catch(error => {
          // eslint-disable-next-line
          console.log('Cannot connect to netsim web server', error);
        });
  }

  connectedCallback() {
    super.connectedCallback();
    window.addEventListener('changeModeEvent', this.handleChangeModeEvent);
    window.addEventListener('reset-button-clicked', this.handleReset);
    window.addEventListener('bumble-button-clicked', this.handleBumbleHive);
  }

  disconnectedCallback() {
    window.removeEventListener('bumble-button-clicked', this.handleBumbleHive);
    window.removeEventListener('reset-button-clicked', this.handleReset);
    window.removeEventListener('changeModeEvent', this.handleChangeModeEvent);
    super.disconnectedCallback();
  }

  handleChangeModeEvent = (e: Event) => {
    const {detail} = (e as CustomEvent);
    this.viewMode = detail.mode;
  };

  handleReset() {
    fetch('./v1/devices', {
      method: 'PUT',
    }).catch(error => {
      console.log('Cannot connect to netsim web server:', error);
    })
  };

  handleBumbleHive() {
    window.open('https://google.github.io/bumble/hive/index.html', '_blank');
  }

  render() {
    let page = html``;
    if (this.viewMode === 'main') {
      page = html`
        <ns-customize-button eventName="map-button-clicked" class="primary" aria-label="Change background of the device map">Change Background</ns-customize-button>
        <ns-customize-button eventName="isometric-button-clicked" class="primary" aria-label="Toggle view of the device map">Toggle View</ns-customize-button>
        <ns-customize-button eventName="reset-button-clicked" class="primary" aria-label="Reset device information">Reset</ns-customize-button>
        <ns-customize-button eventName="bumble-button-clicked" class="primary" aria-label="Bumble Hive Webpage">Bumble Hive</ns-customize-button>
        <div class="container">
          <div class="contentA">
            <ns-device-map></ns-device-map>
            <ns-device-list></ns-device-list>
          </div>
          <div class="contentB">
            <ns-device-info></ns-device-info>
          </div>
        </div>
      `;
    } else if (this.viewMode === 'trace') {
      page = html`
        <ns-packet-info></ns-packet-info>
      `;
    } else if (this.viewMode === 'oslib') {
      page = html`
        <ns-license-info></ns-license-info>
      `;
    }
    return html`
      <div id="bottom">version: ${this.version}</div>
      <ns-navigation-bar></ns-navigation-bar>
      ${page}
    `;
  }
}
