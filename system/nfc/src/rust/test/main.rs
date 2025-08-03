// Copyright 2021, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Rootcanal HAL
//! This connects to "rootcanal" which provides a simulated
//! Nfc chip as well as a simulated environment.

use log::{debug, LevelFilter};
use logger::{self, Config};
use nfc_rnci::api::NciApi;

/// Result type
type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

/// The NFC response callback
pub fn nfc_callback(kind: u16, val: &[u8]) {
    debug!("Callback#{} -> {:?}", kind, val);
}

#[tokio::main]
async fn main() -> Result<()> {
    let set_tlvs: [u8; 10] = [3, 0xa1, 1, 0x1e, 0xa2, 1, 0x19, 0x80, 1, 0x01];
    let get_tlvs: [u8; 3] = [2, 0x52, 0x80];
    logger::init(Config::default().with_tag_on_device("lnfc").with_max_level(LevelFilter::Trace));

    let mut nci = NciApi::new();
    nci.nfc_enable(nfc_callback).await;
    nci.nfc_init().await?;
    let lmrts = nci.nfc_get_lmrt_size().await;
    debug!("LMRT size:{}", lmrts);
    let status = nci.nfc_set_config(&set_tlvs).await?;
    debug!("SET_CONFIG status:{}", status);
    let status = nci.nfc_get_config(&get_tlvs).await?;
    debug!("GET_CONFIG status:{}", status);
    nci.nfc_disable().await;
    nci.nfc_enable(nfc_callback).await;
    nci.nfc_init().await?;
    let status = nci.nfc_get_config(&get_tlvs).await?;
    debug!("GET_CONFIG status:{}", status);
    nci.nfc_disable().await;
    Ok(())
}
