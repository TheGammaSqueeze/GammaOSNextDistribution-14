import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';

@customElement('ns-license-info')
export class PacketInformation extends LitElement {
  static styles = css`
    .panel {
      cursor: pointer;
      display: grid;
      place-content: center;
      color: black;
      font-size: 30px;
      font-family: 'Lato', sans-serif;
      border: 5px solid black;
      border-radius: 12px;
      padding: 10px;
      background-color: #ffffff;
      max-width: max-content;
      float: left;
    }

    .title {
      font-weight: bold;
      text-align: center;
      margin-bottom: 10px;
    }

    .copyright {
      text-align: center;
      font-size: 25px;
      margin-bottom: 30px;
    }

    .licensetext {
      font-size: 20px;
      margin-bottom: 10px;
    }
  `;

  render() {
    return html`
      <div class="panel" role="text" tabindex="0">
        <div class="title">Lit-HTML</div>
        <div class="copyright">
            Copyright (c) 2017 Google LLC. All rights reserved.
        </div>
        <div class="licensetext">
            BSD 3-Clause License<br><br>

            Copyright (c) 2017 Google LLC. All rights reserved.<br><br>

            Redistribution and use in source and binary forms, with or without<br>
            modification, are permitted provided that the following conditions are met:<br><br>

            1. Redistributions of source code must retain the above copyright notice, this<br>
            list of conditions and the following disclaimer.<br><br>

            2. Redistributions in binary form must reproduce the above copyright notice,<br>
            this list of conditions and the following disclaimer in the documentation<br>
            and/or other materials provided with the distribution.<br><br>

            3. Neither the name of the copyright holder nor the names of its<br>
            contributors may be used to endorse or promote products derived from<br>
            this software without specific prior written permission.<br><br>

            THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"<br>
            AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE<br>
            IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE<br>
            DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE<br>
            FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL<br>
            DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR<br>
            SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER<br>
            CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,<br>
            OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE<br>
            OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
        </div>
      </div>
    `;
  }
}
