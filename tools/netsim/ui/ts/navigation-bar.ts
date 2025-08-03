import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';

@customElement('ns-navigation-bar')
export class NavigationBar extends LitElement {
  static styles = css`
    :host {
      --border-color: rgb(255, 255, 255, 0.1);
      --background-color: #747474;
    }

    .logo {
      background-image: url(./assets/netsim-logo.svg);
      background-repeat: no-repeat;
      margin-left: 25%;
      width: 50px;
      height: 50px;
    }
    
    nav {
      display: flex;
      width: 100%;
      border-bottom: 1px solid var(--border-color);
      background-color: var(--background-color);
      margin-bottom: 10px;
    }

    nav > .nav-section {
      padding: 3rem 2rem;
      display: flex;
      gap: 1rem;
      border-left: 1px solid var(--border-color);
      align-items: center;
      justify-content: center;
    }

    #nav-logo-section {
      justify-content: flex-start;
      flex-basis: calc(100% / 4);
    }

    #nav-link-section {
      flex-basis: calc(100% / 2);
      gap: 6rem;
    }

    #nav-contact-section {
      flex-grow: 1;
    }

    a {
      text-decoration: none;
    }

    a:hover {
      cursor: pointer;
    }

    h1,
    h2,
    h3,
    a,
    p,
    span {
      font-family: 'Lato';
      font-weight: bold;
      color: white;
      font-size: 25px;
    }
  `;

  connectedCallback() {
    super.connectedCallback();  // eslint-disable-line
  }

  disconnectedCallback() {
    super.disconnectedCallback();  // eslint-disable-line
  }

  private handleClick(ev: Event) {
    let mode = 'main';
    if ((ev.target as HTMLElement).id === 'nav-trace-section') {
      mode = 'trace';
    } else if ((ev.target as HTMLElement).id === 'nav-os-library-section') {
      mode = 'oslib';
    }
    window.dispatchEvent(new CustomEvent('changeModeEvent', {detail: {mode}}));
  }

  private alertMissingLink() {
    window.alert('This link is currently under construction');
  }

  render() {
    return html`
      <nav>
        <div id="nav-logo-section" class="nav-section">
          <a>
            <div id="nav-logo-pic" class="logo" @click=${
        this.handleClick} role="tab" tabindex="0" aria-label="Netsim Logo, change view mode to scene view"></div>
          </a>
          <p>netsim</p>
        </div>
        <div id="nav-link-section" class="nav-section">
          <a href="javascript:void(0)" @click=${
        this.alertMissingLink} rel="noopener noreferrer"
            >ABOUT</a
          >
          <a href="javascript:void(0)" id="nav-trace-section" @click=${
        this.handleClick} role="tab" aria-label="Packet Trace, change view mode to packet trace view"
            >PACKET TRACE</a
          >
          <a href="javascript:void(0)" id="nav-os-library-section" @click=${
        this.handleClick} role = "tab" aria-label="Open Source Libraries, change view mode to open source libraries view"
            >OPEN SOURCE LIBRARIES</a
          >
        </div>
        <div id="nav-contact-section" class="nav-section">
          <a
            href="javascript:void(0)"
            @click=${this.alertMissingLink}
            rel="noopener noreferrer"
            >DOCUMENTATION</a
          >
        </div>
      </nav>
    `;
  }
}
