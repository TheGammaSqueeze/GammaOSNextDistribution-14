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
//! Trusty implementation of `RetrieveRpcArtifacts`. Currently, this supports
//! only IRPC V3.

use hwbcc::{get_bcc, sign_data, HwBccMode, SigningAlgorithm, HWBCC_MAX_RESP_PAYLOAD_LENGTH};
use hwkey::{Hwkey, KdfVersion};
use kmr_common::{crypto, rpc_err, vec_try, Error};
use kmr_ta::device::{
    CsrSigningAlgorithm, DiceInfo, PubDiceArtifacts, RetrieveRpcArtifacts, RpcV2Req,
};
use kmr_ta::rkp::serialize_cbor;
use kmr_wire::{cbor::value::Value, rpc};

// This matches the value of kMasterKeyDerivationData in
// trusty/user/app/keymaster/trusty_remote_provisioning_context.cpp
const HBK_KEY_DERIVATION_DATA: &'static [u8] = b"RemoteKeyProvisioningMasterKey";

pub struct TrustyRpc;

impl RetrieveRpcArtifacts for TrustyRpc {
    fn derive_bytes_from_hbk(
        &self,
        hkdf: &dyn crypto::Hkdf,
        context: &[u8],
        output_len: usize,
    ) -> Result<Vec<u8>, Error> {
        let hwkey_session =
            Hwkey::open().map_err(|e| rpc_err!(Failed, "failed to connect to Hwkey: {:?}", e))?;
        let mut key_buf = vec_try![0u8; output_len]?;
        hwkey_session
            .derive_key_req()
            .unique_key()
            .kdf(KdfVersion::Version(1))
            .derive(HBK_KEY_DERIVATION_DATA, key_buf.as_mut_slice())
            .map_err(|e| rpc_err!(Failed, "failed to derive hardware backed key: {:?}", e))?;
        hkdf.hkdf(&[], &key_buf, context, output_len)
    }

    fn get_dice_info<'a>(&self, _test_mode: rpc::TestMode) -> Result<DiceInfo, Error> {
        let mut bcc_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
        // Note: Test mode is ignored as this currently supports only IRPC V3.
        let bcc = get_bcc(HwBccMode::Release, &mut bcc_buf)
            .map_err(|e| rpc_err!(Failed, "failed to get DICE Info: {:?}", e))?;
        // Construct `UdsCerts` as an empty CBOR map
        let uds_certs_data = serialize_cbor(&Value::Map(Vec::new()))?;
        let pub_dice_artifacts =
            PubDiceArtifacts { uds_certs: uds_certs_data, dice_cert_chain: bcc.to_vec() };
        let dice_info = DiceInfo {
            pub_dice_artifacts,
            signing_algorithm: CsrSigningAlgorithm::EdDSA,
            rpc_v2_test_cdi_priv: None,
        };
        Ok(dice_info)
    }

    fn sign_data<'a>(
        &self,
        _ec: &dyn crypto::Ec,
        _data: &[u8],
        _rpc_v2: Option<RpcV2Req<'a>>,
    ) -> Result<Vec<u8>, Error> {
        // This is marked unimplemented because we override `sign_data_in_cose_sign1` below.
        Err(rpc_err!(Failed, "unimplemented"))
    }

    fn sign_data_in_cose_sign1<'a>(
        &self,
        _ec: &dyn crypto::Ec,
        signing_algorithm: &CsrSigningAlgorithm,
        payload: &[u8],
        aad: &[u8],
        _rpc_v2: Option<RpcV2Req<'a>>,
    ) -> Result<Vec<u8>, Error> {
        match signing_algorithm {
            CsrSigningAlgorithm::EdDSA => {}
            _ => {
                return Err(rpc_err!(
                    Failed,
                    "requested signing algorithm: {:?}, but only ED25519 is supported.",
                    signing_algorithm
                ));
            }
        }

        let mut cose_sign1_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
        // Note: Test mode is ignored as this currently supports only IRPC V3.
        let cose_sign1 = sign_data(
            HwBccMode::Release,
            SigningAlgorithm::ED25519,
            payload,
            aad,
            &mut cose_sign1_buf,
        )
        .map_err(|e| rpc_err!(Failed, "failed to get signed data: {:?}", e))?;
        Ok(cose_sign1.to_vec())
    }
}
