/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Main entrypoint for KeyMint/Rust trusted application (TA) on Trusty.

use keymint::{
    AttestationIds, CertSignInfo, SharedSddManager, TrustyKeys, TrustyMonotonicClock, TrustyRng,
    TrustyRpc, TrustySecureDeletionSecretManager,
};

#[cfg(feature = "with_hwwsk_support")]
use keymint::{TrustyAes, TrustyStorageKeyWrapper};

use kmr_common::crypto;
use kmr_crypto_boring::{
    aes::BoringAes, aes_cmac::BoringAesCmac, des::BoringDes, ec::BoringEc, eq::BoringEq,
    hmac::BoringHmac, rsa::BoringRsa, sha256::BoringSha256,
};
use kmr_ta::{HardwareInfo, RpcInfo, RpcInfoV3};
use log::info;

fn log_formatter(record: &log::Record) -> String {
    // line number should be present, so keeping it simple by just returning a 0.
    let line = record.line().unwrap_or(0);
    let file = record.file().unwrap_or("unknown file");
    format!("{}: {}:{} {}\n", record.level(), file, line, record.args())
}

fn main() {
    let config = trusty_log::TrustyLoggerConfig::default()
        .with_min_level(log::Level::Info)
        .format(&log_formatter);
    trusty_log::init_with_config(config);

    info!("Hello from Keymint Rust!");

    let hw_info = HardwareInfo {
        version_number: 3,
        security_level: kmr_common::wire::keymint::SecurityLevel::TrustedEnvironment,
        impl_name: "TEE KeyMint in Rust",
        author_name: "Google",
        unique_id: "TEE KeyMint TA",
    };

    let rpc_info_v3 = RpcInfoV3 {
        author_name: "Google",
        unique_id: "TEE KeyMint TA",
        fused: false,
        supported_num_of_keys_in_csr: kmr_wire::rpc::MINIMUM_SUPPORTED_KEYS_IN_CSR,
    };

    let rng = TrustyRng::default();
    let clock = TrustyMonotonicClock;
    #[cfg(feature = "with_hwwsk_support")]
    let aes = TrustyAes::default();
    #[cfg(not(feature = "with_hwwsk_support"))]
    let aes = BoringAes;
    let imp = crypto::Implementation {
        rng: Box::new(rng),
        clock: Some(Box::new(clock)),
        compare: Box::new(BoringEq),
        aes: Box::new(aes),
        des: Box::new(BoringDes),
        hmac: Box::new(BoringHmac),
        rsa: Box::<BoringRsa>::default(),
        ec: Box::<BoringEc>::default(),
        ckdf: Box::new(BoringAesCmac),
        hkdf: Box::new(BoringHmac),
        sha256: Box::new(BoringSha256),
    };
    let sdd_mgr = TrustySecureDeletionSecretManager::new();
    let shared_sdd_mgr = SharedSddManager::new(sdd_mgr);
    let legacy_sdd_mgr = shared_sdd_mgr.clone();
    let legacy_key = keymint::TrustyLegacyKeyBlobHandler {
        aes: Box::new(BoringAes),
        hkdf: Box::new(BoringHmac),
        sdd_mgr: Some(Box::new(legacy_sdd_mgr)),
        keys: Box::new(TrustyKeys),
    };
    let dev = kmr_ta::device::Implementation {
        keys: Box::new(TrustyKeys),
        sign_info: Box::new(CertSignInfo),
        attest_ids: Some(Box::new(AttestationIds)),
        sdd_mgr: Some(Box::new(shared_sdd_mgr)),
        bootloader: Box::new(kmr_ta::device::BootloaderDone),
        #[cfg(feature = "with_hwwsk_support")]
        sk_wrapper: Some(Box::new(TrustyStorageKeyWrapper)),
        #[cfg(not(feature = "with_hwwsk_support"))]
        sk_wrapper: None,
        tup: Box::new(kmr_ta::device::TrustedPresenceUnsupported),
        legacy_key: Some(Box::new(legacy_key)),
        rpc: Box::new(TrustyRpc),
    };
    keymint::handle_port_connections(hw_info, RpcInfo::V3(rpc_info_v3), imp, dev)
        .expect("handle_port_connections returned an error");
}
