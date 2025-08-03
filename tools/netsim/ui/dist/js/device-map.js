import{__decorate as e}from"../node_modules/tslib/tslib.es6.js";import{css as i,LitElement as t,html as o}from"https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js";import{property as n,customElement as r}from"https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js";import{styleMap as s}from"https://cdn.jsdelivr.net/gh/lit/dist@2/all/lit-all.min.js";import{simulationState as a}from"./device-observer.js";let d=class extends t{constructor(){super(...arguments),this.deviceData=[],this.imageIdx=0,this.numImages=3,this.isometric=!1,this.onChangeMap=()=>{this.imageIdx=(this.imageIdx+1)%this.numImages},this.handleIsometricView=()=>{this.isometric=!this.isometric}}connectedCallback(){super.connectedCallback(),a.registerObserver(this),window.addEventListener("map-button-clicked",this.onChangeMap),window.addEventListener("isometric-button-clicked",this.handleIsometricView)}disconnectedCallback(){window.removeEventListener("isometric-button-clicked",this.handleIsometricView),window.removeEventListener("map-button-clicked",this.onChangeMap),a.removeObserver(this),super.disconnectedCallback()}onNotify(e){this.deviceData=e.devices,this.requestUpdate()}checkBle(e){var i;return void 0!==(null===(i=e.chips.at(0))||void 0===i?void 0:i.bleBeacon)}render(){const e=["red","orange","yellow","green","blue","indigo","purple"],i=this.isometric?"perspective(200rem) rotateX(60deg) rotateY(0deg) rotateZ(0deg) scale3d(0.8,0.8,0.8); top: 250px":"none; top: 0px;";return o`
      <ns-device-dropzone role="widget" tabindex="0" aria-label="Device map">
        <div id="dropzone" class="box pattern${this.imageIdx}">
          ${this.deviceData.map(((i,t)=>o`
              ${this.checkBle(i)?o`
                    <ns-device-dragzone
                      .action=${"move"}
                      style=${s({position:"absolute",left:100*i.position.x+"px",top:100*i.position.y+"px"})}
                    >
                      <ns-pyramid-sprite
                        id=${i.name}
                        .color=${e[t%e.length]}
                        .size=${"30px"}
                        .controls=${!0}
                        yaw=${i.orientation.yaw}
                        pitch=${i.orientation.pitch}
                        roll=${i.orientation.roll}
                        posZ=${100*i.position.z}
                        role="widget"
                        tabindex="1"
                        aria-label="${i.name} on Device Map, Position: ${Math.round(100*i.position.x)}, ${Math.round(100*i.position.y)}, ${Math.round(100*i.position.z)}, Orientation: yaw: ${i.orientation.yaw}, pitch: ${i.orientation.pitch}, roll: ${i.orientation.roll}"
                        aria-live="polite"
                      ></ns-pyramid-sprite>
                    </ns-device-dragzone>
                  `:o`
                  <ns-device-dragzone
                    .action=${"move"}
                    style=${s({position:"absolute",left:100*i.position.x+"px",top:100*i.position.y+"px"})}
                  >
                    <ns-cube-sprite
                      id=${i.name}
                      .color=${e[t%e.length]}
                      .size=${"30px"}
                      .controls=${!0}
                      yaw=${i.orientation.yaw}
                      pitch=${i.orientation.pitch}
                      roll=${i.orientation.roll}
                      posZ=${100*i.position.z}
                      role="widget"
                      tabindex="1"
                      aria-label="${i.name} on Device Map, Position: ${Math.round(100*i.position.x)}, ${Math.round(100*i.position.y)}, ${Math.round(100*i.position.z)}, Orientation: yaw: ${i.orientation.yaw}, pitch: ${i.orientation.pitch}, roll: ${i.orientation.roll}"
                      aria-live="polite"
                    ></ns-cube-sprite>
                  </ns-device-dragzone>
                `}
            `))}
        </div>
        <style>
          #dropzone {
            transform: ${i};
          }
        </style>
      </ns-device-dropzone>
    `}};d.styles=i`
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
  `,e([n()],d.prototype,"deviceData",void 0),e([n()],d.prototype,"imageIdx",void 0),e([n()],d.prototype,"numImages",void 0),e([n({type:Boolean,reflect:!0})],d.prototype,"isometric",void 0),d=e([r("ns-device-map")],d);export{d as DeviceMap};
