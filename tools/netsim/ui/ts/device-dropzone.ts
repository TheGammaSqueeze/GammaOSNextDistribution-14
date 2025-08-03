import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

import {DeviceDragZone} from './device-dragzone.js';
import {simulationState} from './device-observer.js';

@customElement('ns-device-dropzone')
export class DeviceDropZone extends LitElement {
  @property({type: String, attribute: 'serial'}) serial: string = '';

  @property({type: String, attribute: 'type'}) type: string = '';

  constructor() {
    super();
    this.addEventListener('drop', this.handleDrop);
    this.addEventListener('drag', this.handleDragOver);
    this.addEventListener('dragenter', DeviceDropZone.handleDragEnter);
    this.addEventListener('dragleave', DeviceDropZone.handleDragLeave);
    this.addEventListener('dragover', this.handleDragOver);
  }

  static handleDragEnter(ev: DragEvent) {
    ev.preventDefault();
  }

  static handleDragLeave(ev: DragEvent) {
    ev.preventDefault();
  }

  slottedDropZone() {
    // Returns the #dropzone div inside the slotted children, where devices are
    // stored. note: needs better checking when not the first element.
    const slot = this.shadowRoot?.querySelector('slot');
    return slot?.assignedElements({flatten: true})[0];
  }

  handleDrop(ev: DragEvent) {
    ev.preventDefault();
    const dropzone = this.slottedDropZone();
    if (dropzone) {
      const draggedElement = DeviceDragZone.dragged as HTMLElement;
      if (ev.dataTransfer?.effectAllowed === 'move') {
        draggedElement.parentNode?.removeChild(draggedElement);
        draggedElement.style.opacity = '';
        dropzone.appendChild(draggedElement);
      } else {
        // copy
        dropzone.appendChild(draggedElement.cloneNode(true));
      }
      const dropped = dropzone.lastChild as HTMLElement;
      if (dropped) {
        const rect = (dropzone as HTMLElement).getBoundingClientRect();
        dropped.setAttribute('action', 'move');
        dropped.style.position = 'absolute';
        dropped.style.left = `${ev.clientX - rect.left}px`;
        dropped.style.top = `${ev.clientY - rect.top}px`;
        dropped.style.opacity = `1.0`;
        // Patch the position of a dropped element
        let id = dropped.getElementsByTagName('ns-cube-sprite')
                     .item(0)
                     ?.getAttribute('id');
        if (id === undefined) {
          id = dropped.getElementsByTagName('ns-pyramid-sprite')
                   .item(0)
                   ?.getAttribute('id');
        }
        if (id === undefined || id === null) {
          id = '';
        }
        simulationState.handleDrop(
            id, (ev.clientX - rect.left) / 100, (ev.clientY - rect.top) / 100);
      }
    }
  }

  handleDragOver(ev: DragEvent) {
    ev.preventDefault();
    this.slottedDropZone();
  }

  render() {
    return html`<slot></slot>`;
  }
}
