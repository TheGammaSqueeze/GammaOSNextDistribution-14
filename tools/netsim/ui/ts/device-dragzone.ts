import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

import {simulationState} from './device-observer.js';

@customElement('ns-device-dragzone')
export class DeviceDragZone extends LitElement {
  static dragged: EventTarget|null;

  @property({type: String, attribute: 'action'}) action = 'move';

  constructor() {
    super();
    this.addEventListener('dragstart', this.handleDragStart);
    this.addEventListener('dragend', this.handleDragEnd);
    this.addEventListener('click', this.handleSelect);
  }

  connectedCallback() {
    this.draggable = true;
  }

  handleDragStart(ev: DragEvent) {
    this.style.opacity = '0.4';
    if (ev.dataTransfer && ev.target) {
      DeviceDragZone.dragged = ev.target;
      // eslint-disable-next-line no-param-reassign
      ev.dataTransfer.effectAllowed = this.action === 'move' ? 'move' : 'copy';
    }
  }

  handleDragEnd() {
    this.style.opacity = '1';
    DeviceDragZone.dragged = null;
  }

  // Allow the information panel to figure what has been selected.
  handleSelect(ev: Event) {
    this.style.opacity = '1';
    if (ev.target) {
      simulationState.patchSelected((ev.target as Element).id);
      // We can add a feature for visually showing a selected object (i.e.
      // bolded borders)
    }
  }

  render() {
    return html` <slot></slot> `;
  }
}
