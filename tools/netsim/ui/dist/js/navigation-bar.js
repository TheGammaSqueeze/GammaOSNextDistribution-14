import{__decorate as e}from"../node_modules/tslib/tslib.es6.js";import{css as i,LitElement as a,html as o}from"https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js";import{customElement as t}from"https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js";let n=class extends a{connectedCallback(){super.connectedCallback()}disconnectedCallback(){super.disconnectedCallback()}handleClick(e){let i="main";"nav-trace-section"===e.target.id?i="trace":"nav-os-library-section"===e.target.id&&(i="oslib"),window.dispatchEvent(new CustomEvent("changeModeEvent",{detail:{mode:i}}))}alertMissingLink(){window.alert("This link is currently under construction")}render(){return o`
      <nav>
        <div id="nav-logo-section" class="nav-section">
          <a>
            <div id="nav-logo-pic" class="logo" @click=${this.handleClick} role="tab" tabindex="0" aria-label="Netsim Logo, change view mode to scene view"></div>
          </a>
          <p>netsim</p>
        </div>
        <div id="nav-link-section" class="nav-section">
          <a href="javascript:void(0)" @click=${this.alertMissingLink} rel="noopener noreferrer"
            >ABOUT</a
          >
          <a href="javascript:void(0)" id="nav-trace-section" @click=${this.handleClick} role="tab" aria-label="Packet Trace, change view mode to packet trace view"
            >PACKET TRACE</a
          >
          <a href="javascript:void(0)" id="nav-os-library-section" @click=${this.handleClick} role = "tab" aria-label="Open Source Libraries, change view mode to open source libraries view"
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
    `}};n.styles=i`
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
  `,n=e([t("ns-navigation-bar")],n);export{n as NavigationBar};
