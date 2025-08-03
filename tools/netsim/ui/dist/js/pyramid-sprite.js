import{__decorate as t}from"../node_modules/tslib/tslib.es6.js";import{css as e,LitElement as o,html as i}from"https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js";import{property as r,customElement as s}from"https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js";import{simulationState as a}from"./device-observer.js";let d=class extends o{constructor(){super(...arguments),this.yaw=-15,this.pitch=-15,this.roll=0,this.posZ=0,this.color=e`red`,this.size=e`30px`,this.controls=!1,this.highlighted=!1,this.handleOrientationEvent=t=>{const{detail:e}=t;e.name===this.id&&this.controls&&("yaw"===e.type?this.yaw=e.value:"pitch"===e.type?this.pitch=e.value:this.roll=e.value)}}connectedCallback(){super.connectedCallback(),a.registerObserver(this),window.addEventListener("orientationEvent",this.handleOrientationEvent)}disconnectedCallback(){window.removeEventListener("orientationEvent",this.handleOrientationEvent),a.removeObserver(this),super.disconnectedCallback()}onNotify(t){this.highlighted=t.selectedId===this.id;for(const e of t.devices)if(e.name===this.id)return void(this.posZ=100*e.position.z)}render(){return i`
      <style>
        :host {
          font-size: ${this.size};
          --color: ${this.color};
          --yaw: ${this.yaw};
          --pitch: ${this.pitch};
          --roll: ${this.roll};
          --posZ: ${this.controls?this.posZ:0};
        }
        .pyramid > div {
          outline: ${this.highlighted&&this.controls?e`dashed`:e``};
        }
      </style>
      <div class="pyramid">
        <div class="face"></div>
        <div class="face"></div>
        <div class="face"></div>
        <div class="face"></div>
      </div>
      ${this.controls?i`
            <div class="line"></div>
            <div class="base"></div>
          `:i``}
    `}};d.styles=e`
    :host {
      /** all sizes are relative to font-size **/
      display: block;
      min-height: 1.5em;
      min-width: 1.5em;
      width: 1em;
      /*  overflow: hidden; */
      transform-origin: center;
      // TODO(b/294574192): Uncommenting below causes significant render lag
      // transform-style: preserve-3d;
      transform: translateZ(calc(var(--posZ) * 1px));
      cursor: move;
    }
    .pyramid {
      transform-style: preserve-3d;
      transform: rotateX(calc(var(--yaw) * 1deg))
        rotateY(calc(var(--pitch) * 1deg)) rotateZ(calc(var(--roll) * 1deg));
      position: absolute;
      left: 0.25em;
      bottom: 0.25em;
      width: 1em;
      height: 1em;
    }
    .pyramid > div {
      background-color: var(--color);
      position: absolute;
      width: 100%;
      height: 100%;
      clip-path: polygon(50% 0, 100% 100%, 0 100%);
      box-shadow: 0 0 0.25em #000 inset;
      opacity: 0.7;
    }
    .pyramid > div:nth-child(1) {
      transform: rotateX(30deg);
    }
    .pyramid > div:nth-child(2) {
      transform: translate3d(0.25em, 0, -0.25em) rotateY(90deg) rotateX(30deg);
    }
    .pyramid > div:nth-child(3) {
      transform: translate3d(0, 0, -0.5em) rotateY(180deg) rotateX(30deg);
    }
    .pyramid > div:nth-child(4) {
      transform: translate3d(-0.25em, 0, -0.25em) rotateY(270deg) rotateX(30deg);
    }

    .line {
      position: absolute;
      border-bottom: 5px dashed;
      width: calc(var(--posZ) * 1px);
      top: 50%;
      left: 50%;
      transform: rotateY(90deg) rotateX(90deg);
      transform-origin: left;
    }

    .base {
      position: absolute;
      border: 5px solid;
      border-radius: 50%;
      background-color: black;
      height: 5px;
      width: 5px;
      top: 50%;
      left: 50%;
      transform: translate3d(-50%, -50%, calc(var(--posZ) * -1px));
    }
  `,t([r({type:Number})],d.prototype,"yaw",void 0),t([r({type:Number})],d.prototype,"pitch",void 0),t([r({type:Number})],d.prototype,"roll",void 0),t([r({type:Number})],d.prototype,"posZ",void 0),t([r({type:e,attribute:"color"})],d.prototype,"color",void 0),t([r({type:e,attribute:"size"})],d.prototype,"size",void 0),t([r({type:Boolean})],d.prototype,"controls",void 0),t([r({type:Boolean})],d.prototype,"highlighted",void 0),d=t([s("ns-pyramid-sprite")],d);export{d as PyramidSprite};
