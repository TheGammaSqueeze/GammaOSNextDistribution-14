// Copyright 2022, The Android Open Source Project
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

//! Fuzzes unsafe APIs of libkeystore2 module

#![no_main]

use keystore2::{legacy_blob::LegacyBlobLoader, utils::ui_opts_2_compat};
use keystore2_aaid::get_aaid;
use keystore2_apc_compat::ApcHal;
use keystore2_crypto::{
    aes_gcm_decrypt, aes_gcm_encrypt, ec_key_generate_key, ec_key_get0_public_key,
    ec_key_marshal_private_key, ec_key_parse_private_key, ec_point_oct_to_point,
    ec_point_point_to_oct, ecdh_compute_key, generate_random_data, hkdf_expand, hkdf_extract,
    hmac_sha256, parse_subject_from_certificate, Password, ZVec,
};
use keystore2_hal_names::get_hidl_instances;
use keystore2_selinux::{check_access, getpidcon, setcon, Backend, Context, KeystoreKeyBackend};
use libfuzzer_sys::{arbitrary::Arbitrary, fuzz_target};
use std::{ffi::CString, sync::Arc};

// Avoid allocating too much memory and crashing the fuzzer.
const MAX_SIZE_MODIFIER: usize = 1024;

/// CString does not contain any internal 0 bytes
fn get_valid_cstring_data(data: &[u8]) -> &[u8] {
    match data.iter().position(|&b| b == 0) {
        Some(idx) => &data[0..idx],
        None => data,
    }
}

#[derive(Arbitrary, Debug)]
enum FuzzCommand<'a> {
    DecodeAlias {
        string: String,
    },
    TryFrom {
        vector_data: Vec<u8>,
    },
    GenerateRandomData {
        size: usize,
    },
    HmacSha256 {
        key_hmac: &'a [u8],
        msg: &'a [u8],
    },
    AesGcmDecrypt {
        data: &'a [u8],
        iv: &'a [u8],
        tag: &'a [u8],
        key_aes_decrypt: &'a [u8],
    },
    AesGcmEecrypt {
        plaintext: &'a [u8],
        key_aes_encrypt: &'a [u8],
    },
    Password {
        pw: &'a [u8],
        salt: &'a [u8],
        key_length: usize,
    },
    HkdfExtract {
        hkdf_secret: &'a [u8],
        hkdf_salt: &'a [u8],
    },
    HkdfExpand {
        out_len: usize,
        hkdf_prk: &'a [u8],
        hkdf_info: &'a [u8],
    },
    PublicPrivateKey {
        ec_priv_buf: &'a [u8],
        ec_oct_buf: &'a [u8],
    },
    ParseSubjectFromCertificate {
        parse_buf: &'a [u8],
    },
    GetHidlInstances {
        hidl_package: &'a str,
        major_version: usize,
        minor_version: usize,
        hidl_interface_name: &'a str,
    },
    GetAaid {
        aaid_uid: u32,
    },
    Hal {
        opt: i32,
        prompt_text: &'a str,
        locale: &'a str,
        extra_data: &'a [u8],
    },
    Context {
        context: &'a str,
    },
    Backend {
        namespace: &'a str,
    },
    GetPidCon {
        pid: i32,
    },
    CheckAccess {
        source: &'a [u8],
        target: &'a [u8],
        tclass: &'a str,
        perm: &'a str,
    },
    SetCon {
        set_target: &'a [u8],
    },
}

fuzz_target!(|commands: Vec<FuzzCommand>| {
    for command in commands {
        match command {
            FuzzCommand::DecodeAlias { string } => {
                let _res = LegacyBlobLoader::decode_alias(&string);
            }
            FuzzCommand::TryFrom { vector_data } => {
                let _res = ZVec::try_from(vector_data);
            }
            FuzzCommand::GenerateRandomData { size } => {
                let _res = generate_random_data(size % MAX_SIZE_MODIFIER);
            }
            FuzzCommand::HmacSha256 { key_hmac, msg } => {
                let _res = hmac_sha256(key_hmac, msg);
            }
            FuzzCommand::AesGcmDecrypt { data, iv, tag, key_aes_decrypt } => {
                let _res = aes_gcm_decrypt(data, iv, tag, key_aes_decrypt);
            }
            FuzzCommand::AesGcmEecrypt { plaintext, key_aes_encrypt } => {
                let _res = aes_gcm_encrypt(plaintext, key_aes_encrypt);
            }
            FuzzCommand::Password { pw, salt, key_length } => {
                let _res =
                    Password::from(pw).derive_key_pbkdf2(salt, key_length % MAX_SIZE_MODIFIER);
            }
            FuzzCommand::HkdfExtract { hkdf_secret, hkdf_salt } => {
                let _res = hkdf_extract(hkdf_secret, hkdf_salt);
            }
            FuzzCommand::HkdfExpand { out_len, hkdf_prk, hkdf_info } => {
                let _res = hkdf_expand(out_len % MAX_SIZE_MODIFIER, hkdf_prk, hkdf_info);
            }
            FuzzCommand::PublicPrivateKey { ec_priv_buf, ec_oct_buf } => {
                let check_private_key = {
                    let mut check_private_key = ec_key_parse_private_key(ec_priv_buf);
                    if check_private_key.is_err() {
                        check_private_key = ec_key_generate_key();
                    };
                    check_private_key
                };
                let check_ecpoint = ec_point_oct_to_point(ec_oct_buf);
                if check_private_key.is_ok() {
                    let private_key = check_private_key.unwrap();
                    ec_key_get0_public_key(&private_key);
                    let _res = ec_key_marshal_private_key(&private_key);

                    if check_ecpoint.is_ok() {
                        let public_key = check_ecpoint.unwrap();
                        let _res = ec_point_point_to_oct(public_key.get_point());
                        let _res = ecdh_compute_key(public_key.get_point(), &private_key);
                    }
                }
            }
            FuzzCommand::ParseSubjectFromCertificate { parse_buf } => {
                let _res = parse_subject_from_certificate(parse_buf);
            }
            FuzzCommand::GetHidlInstances {
                hidl_package,
                major_version,
                minor_version,
                hidl_interface_name,
            } => {
                get_hidl_instances(hidl_package, major_version, minor_version, hidl_interface_name);
            }
            FuzzCommand::GetAaid { aaid_uid } => {
                let _res = get_aaid(aaid_uid);
            }
            FuzzCommand::Hal { opt, prompt_text, locale, extra_data } => {
                let hal = ApcHal::try_get_service();
                if hal.is_some() {
                    let hal = Arc::new(hal.unwrap());
                    let apc_compat_options = ui_opts_2_compat(opt);
                    let prompt_text =
                        std::str::from_utf8(get_valid_cstring_data(prompt_text.as_bytes()))
                            .unwrap();
                    let locale =
                        std::str::from_utf8(get_valid_cstring_data(locale.as_bytes())).unwrap();
                    let _res = hal.prompt_user_confirmation(
                        prompt_text,
                        extra_data,
                        locale,
                        apc_compat_options,
                        move |_, _, _| {},
                    );
                }
            }
            FuzzCommand::Context { context } => {
                let _res = Context::new(context);
            }
            FuzzCommand::Backend { namespace } => {
                let backend = KeystoreKeyBackend::new();
                if let Ok(backend) = backend {
                    let _res = backend.lookup(namespace);
                }
            }
            FuzzCommand::GetPidCon { pid } => {
                let _res = getpidcon(pid);
            }
            FuzzCommand::CheckAccess { source, target, tclass, perm } => {
                let source = get_valid_cstring_data(source);
                let target = get_valid_cstring_data(target);
                let _res = check_access(
                    &CString::new(source).unwrap(),
                    &CString::new(target).unwrap(),
                    tclass,
                    perm,
                );
            }
            FuzzCommand::SetCon { set_target } => {
                let _res = setcon(&CString::new(get_valid_cstring_data(set_target)).unwrap());
            }
        }
    }
});
