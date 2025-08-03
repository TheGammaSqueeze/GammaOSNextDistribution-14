import{__decorate as e}from"../node_modules/tslib/tslib.es6.js";import{css as t,LitElement as n,html as i}from"https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js";import{property as o,customElement as s}from"https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js";let c=class extends n{constructor(){super(),this.viewMode="main",this.version="",this.handleChangeModeEvent=e=>{const{detail:t}=e;this.viewMode=t.mode},this.invokeGetVersion()}invokeGetVersion(){fetch("./version",{method:"GET"}).then((e=>e.json())).then((e=>{this.version=e.version})).catch((e=>{console.log("Cannot connect to netsim web server",e)}))}connectedCallback(){super.connectedCallback(),window.addEventListener("changeModeEvent",this.handleChangeModeEvent),window.addEventListener("reset-button-clicked",this.handleReset),window.addEventListener("bumble-button-clicked",this.handleBumbleHive)}disconnectedCallback(){window.removeEventListener("bumble-button-clicked",this.handleBumbleHive),window.removeEventListener("reset-button-clicked",this.handleReset),window.removeEventListener("changeModeEvent",this.handleChangeModeEvent),super.disconnectedCallback()}handleReset(){fetch("./v1/devices",{method:"PUT"}).catch((e=>{console.log("Cannot connect to netsim web server:",e)}))}handleBumbleHive(){window.open("https://google.github.io/bumble/hive/index.html","_blank")}render(){let e=i``;return"main"===this.viewMode?e=i`
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
      `:"trace"===this.viewMode?e=i`
        <ns-packet-info></ns-packet-info>
      `:"oslib"===this.viewMode&&(e=i`
        <ns-license-info></ns-license-info>
      `),i`
      <div id="bottom">version: ${this.version}</div>
      <ns-navigation-bar></ns-navigation-bar>
      ${e}
    `}};c.styles=t`
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
  `,e([o()],c.prototype,"viewMode",void 0),e([o()],c.prototype,"version",void 0),c=e([s("netsim-app")],c);export{c as NetsimApp};
