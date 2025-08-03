import{__decorate as e}from"../node_modules/tslib/tslib.es6.js";import{html as i,css as t,LitElement as s}from"https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js";import{property as o,customElement as a}from"https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js";import{styleMap as d,live as l}from"https://cdn.jsdelivr.net/gh/lit/dist@2/all/lit-all.min.js";import{simulationState as n}from"./device-observer.js";import{State as c}from"./netsim/model.js";var r,v;!function(e){e.UNSPECIFIED="UNSPECIFIED",e.BLUETOOTH="BLUETOOTH",e.WIFI="WIFI",e.UWB="UWB",e.BLUETOOTH_BEACON="BLUETOOTH_BEACON",e.UNRECOGNIZED="UNRECOGNIZED"}(v||(v={})),i`
  <input type="checkbox" disabled />
  <span
    class="slider round"
    style=${d({opacity:"0.7"})}
  ></span>
`;let h=r=class extends s{constructor(){super(...arguments),this.yaw=0,this.pitch=0,this.roll=0,this.editMode=!1,this.posX=0,this.posY=0,this.posZ=0,this.holdRange=!1,this.getRadioCheckbox=(e,t)=>i`<label class="switch">
      <input
        id=${t}
        type="checkbox"
        .checked=${l(e.state===c.ON)}
        @click=${()=>{var i;null===(i=this.selectedDevice)||void 0===i||i.toggleChipState(e),this.patchRadio()}}
      />
      <span class="slider round"></span>
    </label> `}connectedCallback(){super.connectedCallback(),n.registerObserver(this)}disconnectedCallback(){n.removeObserver(this),super.disconnectedCallback()}onNotify(e){if(e.selectedId&&!1===this.editMode)for(const i of e.devices)if(i.name===e.selectedId){this.selectedDevice=i,this.holdRange||(this.yaw=i.orientation.yaw,this.pitch=i.orientation.pitch,this.roll=i.orientation.roll),this.posX=Math.floor(100*i.position.x),this.posY=Math.floor(100*i.position.y),this.posZ=Math.floor(100*i.position.z);break}}changeRange(e){var i;this.holdRange=!0,console.assert(null!==this.selectedDevice);const t=e.target,s=new CustomEvent("orientationEvent",{detail:{name:null===(i=this.selectedDevice)||void 0===i?void 0:i.name,type:t.id,value:t.value}});window.dispatchEvent(s),"yaw"===t.id?this.yaw=Number(t.value):"pitch"===t.id?this.pitch=Number(t.value):this.roll=Number(t.value)}patchOrientation(){this.holdRange=!1,console.assert(void 0!==this.selectedDevice),void 0!==this.selectedDevice&&(this.selectedDevice.orientation={yaw:this.yaw,pitch:this.pitch,roll:this.roll},n.patchDevice({device:{name:this.selectedDevice.name,orientation:this.selectedDevice.orientation}}))}patchRadio(){console.assert(void 0!==this.selectedDevice),void 0!==this.selectedDevice&&n.patchDevice({device:{name:this.selectedDevice.name,chips:this.selectedDevice.chips}})}handleEditForm(){this.editMode?(n.invokeGetDevice(),this.editMode=!1):this.editMode=!0}static checkPositionBound(e){return e>10?10:e<0?0:e}static checkOrientationBound(e){return e>90?90:e<-90?-90:e}handleSave(){if(console.assert(void 0!==this.selectedDevice),void 0===this.selectedDevice)return;const e=this.renderRoot.querySelectorAll('[id^="edit"]'),i={name:this.selectedDevice.name,position:this.selectedDevice.position,orientation:this.selectedDevice.orientation};e.forEach((e=>{const t=e;"editName"===t.id?i.name=t.value:t.id.startsWith("editPos")?Number.isNaN(Number(t.value))||(i.position[t.id.slice(7).toLowerCase()]=r.checkPositionBound(Number(t.value)/100)):t.id.startsWith("editOri")&&(Number.isNaN(Number(t.value))||(i.orientation[t.id.slice(7).toLowerCase()]=r.checkOrientationBound(Number(t.value))))})),this.selectedDevice.name=i.name,this.selectedDevice.position=i.position,this.selectedDevice.orientation=i.orientation,this.handleEditForm(),n.patchDevice({device:i})}handleGetBleBeacon(e){return i`${e.settings?i`<div class="setting">
        <div class="name">Settings</div>

        ${e.settings.advertiseMode?i`<div class="label">Advertise Mode:</div>
              <div class="info">
                ${null===(t=e.settings.advertiseMode)||void 0===t?void 0:t.replace("-"," ")}
              </div>`:i`<div class="label">Advertise Interval:</div>
              <div class="info">
                ${null===(s=e.settings.milliseconds)||void 0===s?void 0:s.toString().concat(" ms")}
              </div>`}
        ${e.settings.txPowerLevel?i`<div class="label">Transmit Power Level:</div>
              <div class="info">
                ${null===(o=e.settings.txPowerLevel)||void 0===o?void 0:o.replace("-"," ")}
              </div>`:i`<div class="label">Transmit Power:</div>
              <div class="info">
                ${null===(a=e.settings.dbm)||void 0===a?void 0:a.toString().concat(" dBm")}
              </div>`}

        <div class="label">Scannable:</div>
        <div class="info">${e.settings.scannable}</div>

        <div class="label">Timeout:</div>
        <div class="info">
          ${null===(d=e.settings.timeout)||void 0===d?void 0:d.toString().concat(" ms")}
        </div>
      </div>`:i``} ${e.advData?i`<div class="setting">
        <div class="name">Advertise Data</div>

        <div class="label">Include Device Name:</div>
        <div class="info">${e.advData.includeDeviceName}</div>

        <div class="label">Include Transmit Power:</div>
        <div class="info">${e.advData.includeTxPowerLevel}</div>

        ${e.advData.manufacturerData.length?i` <div class="label">Manufacturer Data Length:</div>
              <div class="info">
                ${e.advData.manufacturerData.length}
              </div>`:i``}
        ${e.advData.services.length?i` <div class="label">Number of Supported Services:</div>
              <div class="info">${e.advData.services.length}</div>`:i``}
      </div>`:i``}`;var t,s,o,a,d}getBluetoothRadioCheckboxes(e){let i,t;return e.lowEnergy&&e.lowEnergy.state&&(i=this.getRadioCheckbox(e.lowEnergy,"lowEnergy")),e.classic&&e.classic&&(t=this.getRadioCheckbox(e.classic,"classic")),[i,t]}handleGetChip(e,t){if(e.bleBeacon){let s={};return e.bleBeacon.bt&&([s["Bluetooth LE"],s["Bluetooth Classic"]]=this.getBluetoothRadioCheckboxes(e.bleBeacon.bt)),i`<div class="title">
          Chip ${t+1}: ${e.kind.replace("_"," ")}
        </div>
        <div class="setting">
          <div class="name">Name</div>
          <div class="info">${e.name}</div>
        </div>
        <div class="setting">
          ${Object.entries(s).length?i`<div class="name">Radios</div>`:i``}
          ${Object.entries(s).map((([e,t])=>i`<div class="label">${e}</div>
              <div class="info">${t}</div>`))}
        </div>
        ${this.handleGetBleBeacon(e.bleBeacon)}`}return""}handleGetChips(){if(!this.selectedDevice||!this.selectedDevice.chips)return i``;if(this.selectedDevice.chips.some((e=>(e=>e.kind===v.BLUETOOTH_BEACON&&e.bleBeacon)(e))))return i`${this.selectedDevice.chips.map(((e,i)=>this.handleGetChip(e,i)))}`;let e={};for(const i of this.selectedDevice.chips)i&&(i.bt&&([e["Bluetooth LE"],e["Bluetooth Classic"]]=this.getBluetoothRadioCheckboxes(i.bt)),i.wifi&&(e.WIFI=this.getRadioCheckbox(i.wifi,"wifi")),i.uwb&&(e.UWB=this.getRadioCheckbox(i.uwb,"uwb")));return Object.keys(e).length?i`<div class="setting">
        <div class="name">Radios</div>
        ${Object.entries(e).map((([e,t])=>i`<div class="label">${e}</div>
            <div class="info">${t}</div>`))}
      </div>`:i``}render(){return i`${this.selectedDevice?i`
          <div class="title">Device Info</div>
          <div class="setting">
            <div class="name">Name</div>
            <div class="info">${this.selectedDevice.name}</div>
          </div>
          <div class="setting">
            <div class="name">Position</div>
            <div class="label">X</div>
            <div
              class="info"
              style=${d({color:"red"})}
            >
              ${this.editMode?i`<input
                    type="text"
                    id="editPosX"
                    .value=${this.posX.toString()}
                  />`:i`${this.posX}`}
            </div>
            <div class="label">Y</div>
            <div
              class="info"
              style=${d({color:"green"})}
            >
              ${this.editMode?i`<input
                    type="text"
                    id="editPosY"
                    .value=${this.posY.toString()}
                  />`:i`${this.posY}`}
            </div>
            <div class="label">Z</div>
            <div
              class="info"
              style=${d({color:"blue"})}
            >
              ${this.editMode?i`<input
                    type="text"
                    id="editPosZ"
                    .value=${this.posZ.toString()}
                  />`:i`${this.posZ}`}
            </div>
          </div>
          <div class="setting">
            <div class="name">Orientation</div>
            <div class="label">Yaw</div>
            <div class="info">
              <input
                id="yaw"
                type="range"
                min="-90"
                max="90"
                .value=${this.yaw.toString()}
                .disabled=${this.editMode}
                @input=${this.changeRange}
                @change=${this.patchOrientation}
              />
              ${this.editMode?i`<input
                    type="text"
                    id="editOriYaw"
                    class="orientation"
                    .value=${this.yaw.toString()}
                  />`:i`<div class="text">(${this.yaw})</div>`}
            </div>
            <div class="label">Pitch</div>
            <div class="info">
              <input
                id="pitch"
                type="range"
                min="-90"
                max="90"
                .value=${this.pitch.toString()}
                .disabled=${this.editMode}
                @input=${this.changeRange}
                @change=${this.patchOrientation}
              />
              ${this.editMode?i`<input
                    type="text"
                    id="editOriPitch"
                    class="orientation"
                    .value=${this.pitch.toString()}
                  />`:i`<div class="text">(${this.pitch})</div>`}
            </div>
            <div class="label">Roll</div>
            <div class="info">
              <input
                id="roll"
                type="range"
                min="-90"
                max="90"
                .value=${this.roll.toString()}
                .disabled=${this.editMode}
                @input=${this.changeRange}
                @change=${this.patchOrientation}
              />
              ${this.editMode?i`<input
                    type="text"
                    id="editOriRoll"
                    class="orientation"
                    .value=${this.roll.toString()}
                  />`:i`<div class="text">(${this.roll})</div>`}
            </div>
          </div>
          <div class="setting">
            ${this.editMode?i`
                  <input type="button" value="Save" @click=${this.handleSave} />
                  <input
                    type="button"
                    value="Cancel"
                    @click=${this.handleEditForm}
                  />
                `:i`<input
                  type="button"
                  value="Edit"
                  @click=${this.handleEditForm}
                />`}
          </div>

          ${this.handleGetChips()}
        `:i`<div class="title">Device Info</div>`}`}};h.styles=t`
    :host {
      cursor: pointer;
      display: grid;
      place-content: center;
      color: white;
      font-size: 25px;
      font-family: 'Lato', sans-serif;
      border: 5px solid black;
      border-radius: 12px;
      padding: 10px;
      background-color: #9199a5;
      max-width: 600px;
    }

    .title {
      font-weight: bold;
      text-transform: uppercase;
      text-align: center;
      margin-bottom: 10px;
    }

    .setting {
      display: grid;
      grid-template-columns: auto auto;
      margin-top: 0px;
      margin-bottom: 30px;
      //border: 3px solid black;
      padding: 10px;
    }

    .setting .name {
      grid-column: 1 / span 2;
      text-transform: uppercase;
      text-align: left;
      margin-bottom: 10px;
      font-weight: bold;
    }

    .label {
      grid-column: 1;
      text-align: left;
    }

    .info {
      grid-column: 2;
      text-align: right;
      margin-bottom: 10px;
    }

    .switch {
      position: relative;
      float: right;
      width: 60px;
      height: 34px;
    }

    .switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: #ccc;
      -webkit-transition: 0.4s;
      transition: 0.4s;
    }

    .slider:before {
      position: absolute;
      content: '';
      height: 26px;
      width: 26px;
      left: 4px;
      bottom: 4px;
      background-color: white;
      -webkit-transition: 0.4s;
      transition: 0.4s;
    }

    input:checked + .slider {
      background-color: #2196f3;
    }

    input:focus + .slider {
      box-shadow: 0 0 1px #2196f3;
    }

    input:checked + .slider:before {
      -webkit-transform: translateX(26px);
      -ms-transform: translateX(26px);
      transform: translateX(26px);
    }

    /* Rounded sliders */
    .slider.round {
      border-radius: 34px;
    }

    .slider.round:before {
      border-radius: 50%;
    }

    .text {
      display: inline-block;
      position: relative;
      width: 50px;
    }

    input[type='range'] {
      width: 400px;
    }

    input[type='text'] {
      width: 50%;
      font-size: inherit;
      text-align: right;
      max-height: 25px;
    }

    input[type='text'].orientation {
      max-width: 50px;
    }

    input[type='button'] {
      display: inline;
      font-size: inherit;
      max-width: 200px;
    }
  `,e([o()],h.prototype,"selectedDevice",void 0),e([o({type:Number})],h.prototype,"yaw",void 0),e([o({type:Number})],h.prototype,"pitch",void 0),e([o({type:Number})],h.prototype,"roll",void 0),e([o({type:Boolean})],h.prototype,"editMode",void 0),e([o({type:Number})],h.prototype,"posX",void 0),e([o({type:Number})],h.prototype,"posY",void 0),e([o({type:Number})],h.prototype,"posZ",void 0),h=r=e([a("ns-device-info")],h);export{h as DeviceInformation};
