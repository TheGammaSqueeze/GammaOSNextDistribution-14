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

use super::*;
use ::test::{assert, assert_eq, assert_ne};

::test::init!();

const HWCRYPTO_UNITTEST_DERIVED_KEYBOX_ID: &'static [u8] =
    b"com.android.trusty.hwcrypto.unittest.derived_key32\0";
const HWCRYPTO_UNITTEST_KEYBOX_ID: &'static [u8] = b"com.android.trusty.hwcrypto.unittest.key32\0";
const RPMB_STORAGE_AUTH_KEY_ID: &'static [u8] = b"com.android.trusty.storage_auth.rpmb\0";
const HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID: &'static [u8] =
    b"com.android.trusty.hwcrypto.unittest.opaque_handle\0";

#[cfg(feature = "hwcrypto-unittest")]
const HWCRYPTO_UNITTEST_OPAQUE_HANDLE_NOACCESS_ID: &'static [u8] =
    b"com.android.trusty.hwcrypto.unittest.opaque_handle_noaccess\0";

const UNITTEST_KEYSLOT: &'static [u8] = b"unittestkeyslotunittestkeyslotun";
const UNITTEST_DERIVED_KEYSLOT: &'static [u8] = b"unittestderivedkeyslotunittestde";

const NONSENSE_DATA_32B: &'static [u8] = b"thirtytwo-bytes-of-nonsense-data";

const KEY_SIZE: usize = 32;

fn keys_are_sufficiently_distinct(key1: &[u8], key2: &[u8]) -> bool {
    let (sk, lk) = if key1.len() < key2.len() { (key1, key2) } else { (key2, key1) };
    let differing_bytes = sk.iter().zip(lk).filter(|&(s, l)| s ^ l != 0).count();
    sk.len() - differing_bytes <= 4
}

#[test]
fn test_hwkey_derive_repeatable_versioned() {
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    // derive key once
    let buf = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { kdf_version, os_rollback_version } = hwkey_session
        .derive_key_req()
        .unique_key()
        .kdf(KdfVersion::Best)
        .os_rollback_version(OsRollbackVersion::Version(0))
        .rollback_version_source(RollbackVersionSource::CommittedVersion)
        .derive(NONSENSE_DATA_32B, buf)
        .expect("could not derive key");
    assert_ne!(kdf_version, KdfVersion::Best);

    // derive key again
    let buf2 = &mut [0u8; KEY_SIZE as usize];
    let _ = hwkey_session
        .derive_key_req()
        .unique_key()
        .kdf(kdf_version)
        .os_rollback_version(os_rollback_version)
        .rollback_version_source(RollbackVersionSource::CommittedVersion)
        .derive(NONSENSE_DATA_32B, buf2)
        .expect("could not derive key");

    // ensure they are the same
    assert_eq!(buf, buf2);
    assert_ne!(buf, NONSENSE_DATA_32B);

    // ensure that we don't derive the same key if deriving a shared key
    buf2.fill(0);
    let _ = hwkey_session
        .derive_key_req()
        .shared_key()
        .kdf(kdf_version)
        .os_rollback_version(os_rollback_version)
        .rollback_version_source(RollbackVersionSource::CommittedVersion)
        .derive(NONSENSE_DATA_32B, buf2)
        .expect("could not derive key");
    assert_ne!(buf, buf2);

    assert!(keys_are_sufficiently_distinct(buf, buf2));

    // ensure that we don't derive the same key if deriving
    // a device-unique key that specifies the running version
    // as the rollback version source
    buf2.fill(0);
    let DeriveResult { os_rollback_version, .. } = hwkey_session
        .derive_key_req()
        .unique_key()
        .kdf(kdf_version)
        .os_rollback_version(os_rollback_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .derive(NONSENSE_DATA_32B, buf2)
        .expect("could not derive key");

    match os_rollback_version {
        OsRollbackVersion::Version(v) if v > 0 => {
            assert_ne!(buf, buf2);
            assert!(keys_are_sufficiently_distinct(buf, buf2))
        }
        OsRollbackVersion::Version(_) => {
            assert_eq!(buf, buf2);
        }
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }
}

#[test]
fn test_hwkey_derive_different_default() {
    const SRC_DATA2: &'static [u8] = b"thirtytwo-byt3s-of-nons3ns3-data";

    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    // derive key once
    let buf1 = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { kdf_version, .. } = hwkey_session
        .derive_key_req()
        .derive(NONSENSE_DATA_32B, buf1)
        .expect("could not derive key");

    assert_ne!(kdf_version, KdfVersion::Best);

    // derive key again, with different source data
    let buf2 = &mut [0u8; KEY_SIZE as usize];
    let _ = hwkey_session.derive_key_req().derive(SRC_DATA2, buf2).expect("could not derive key");

    // ensure they are not the same
    assert_ne!(buf1, buf2);
    assert_ne!(buf1, NONSENSE_DATA_32B);
    assert_ne!(buf2, SRC_DATA2);
    assert!(keys_are_sufficiently_distinct(buf1, buf2));
}

#[test]
fn test_hwkey_derive_different_specified() {
    const SRC_DATA2: &'static [u8] = b"thirtytwo-byt3s-of-nons3ns3-data";

    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    // derive key once
    let buf1 = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { kdf_version, os_rollback_version } = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .unique_key()
        .derive(NONSENSE_DATA_32B, buf1)
        .expect("could not derive key");

    assert_ne!(kdf_version, KdfVersion::Best);

    let buf2 = &mut [0u8; KEY_SIZE as usize];
    // derive with the same input but an older OS version
    match os_rollback_version {
        OsRollbackVersion::Version(n) if n >= 1 => {
            let _ = hwkey_session
                .derive_key_req()
                .kdf(kdf_version)
                .rollback_version_source(RollbackVersionSource::RunningVersion)
                .os_rollback_version(OsRollbackVersion::Version(n - 1))
                .unique_key()
                .derive(NONSENSE_DATA_32B, buf2)
                .expect("could not derive key");

            assert_ne!(buf1, buf2);
            assert_ne!(buf1, NONSENSE_DATA_32B);
            assert_ne!(buf2, SRC_DATA2);
            assert!(keys_are_sufficiently_distinct(buf1, buf2));
        }
        OsRollbackVersion::Version(_) => (),
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }

    // derive key again, with different source data
    buf2.fill(0);
    let _ = hwkey_session
        .derive_key_req()
        .kdf(kdf_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .unique_key()
        .derive(SRC_DATA2, buf2)
        .expect("could not derive key");

    // ensure they are not the same
    assert_ne!(buf1, buf2);
    assert_ne!(buf1, NONSENSE_DATA_32B);
    assert_ne!(buf2, SRC_DATA2);
    assert!(keys_are_sufficiently_distinct(buf1, buf2));

    // derive a shared key from the same input and ensure different
    let buf_shared = &mut [0u8; KEY_SIZE as usize];
    let _ = hwkey_session
        .derive_key_req()
        .kdf(kdf_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(os_rollback_version)
        .shared_key()
        .derive(NONSENSE_DATA_32B, buf_shared)
        .expect("could not derive key");

    // ensure they are not the same
    assert_ne!(buf1, buf_shared);
    assert_ne!(buf2, buf_shared);
    assert_ne!(buf_shared, NONSENSE_DATA_32B);
    assert!(keys_are_sufficiently_distinct(buf1, buf_shared));
    assert!(keys_are_sufficiently_distinct(buf2, buf_shared));

    // shared key, different input
    let buf_shared2 = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { os_rollback_version, kdf_version } = hwkey_session
        .derive_key_req()
        .kdf(kdf_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .shared_key()
        .derive(SRC_DATA2, buf_shared2)
        .expect("could not derive key");

    // ensure they are not the same
    assert_ne!(buf1, buf_shared2);
    assert_ne!(buf2, buf_shared2);
    assert_ne!(buf_shared, buf_shared2);
    assert_ne!(buf_shared2, NONSENSE_DATA_32B);
    assert!(keys_are_sufficiently_distinct(buf1, buf_shared2));
    assert!(keys_are_sufficiently_distinct(buf2, buf_shared2));
    assert!(keys_are_sufficiently_distinct(buf_shared, buf_shared2));

    // derive with the same input but an older OS version
    match os_rollback_version {
        OsRollbackVersion::Version(n) if n >= 1 => {
            let _ = hwkey_session
                .derive_key_req()
                .kdf(kdf_version)
                .rollback_version_source(RollbackVersionSource::RunningVersion)
                .os_rollback_version(OsRollbackVersion::Version(n - 1))
                .shared_key()
                .derive(NONSENSE_DATA_32B, buf_shared2)
                .expect("could not derive key");

            // ensure they are not the same
            assert_ne!(buf1, buf_shared2);
            assert_ne!(buf2, buf_shared2);
            assert_ne!(buf_shared, buf_shared2);
            assert_ne!(buf_shared2, NONSENSE_DATA_32B);
            assert!(keys_are_sufficiently_distinct(buf1, buf_shared2));
            assert!(keys_are_sufficiently_distinct(buf2, buf_shared2));
            assert!(keys_are_sufficiently_distinct(buf_shared, buf_shared2));
        }
        OsRollbackVersion::Version(_) => (),
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }
}

#[test]
fn test_hwkey_derive_different_version_source() {
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    // derive with current committed version
    let buf1 = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { kdf_version, os_rollback_version } = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::CommittedVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .unique_key()
        .derive(NONSENSE_DATA_32B, buf1)
        .expect("could not derive key");

    assert_ne!(kdf_version, KdfVersion::Best);

    // derive with same input and rollback version, different version source
    let buf2 = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { os_rollback_version, .. } = hwkey_session
        .derive_key_req()
        .kdf(kdf_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(os_rollback_version)
        .unique_key()
        .derive(NONSENSE_DATA_32B, buf2)
        .expect("could not derive key");

    match os_rollback_version {
        OsRollbackVersion::Version(n) if n > 0 => {
            assert_ne!(buf1, buf2);
            assert_ne!(buf1, NONSENSE_DATA_32B);
            assert!(keys_are_sufficiently_distinct(buf1, buf2));
        }
        OsRollbackVersion::Version(_) => {
            assert_eq!(buf1, buf2);
        }
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }

    // derive shared key with 0 committed version
    let buf_shared = &mut [0u8; KEY_SIZE as usize];
    let DeriveResult { kdf_version, .. } = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::CommittedVersion)
        .os_rollback_version(OsRollbackVersion::Version(0))
        .shared_key()
        .derive(NONSENSE_DATA_32B, buf_shared)
        .expect("could not derive key");

    assert_ne!(buf1, buf_shared);
    assert_ne!(buf2, buf_shared);
    assert_ne!(buf_shared, NONSENSE_DATA_32B);
    assert!(keys_are_sufficiently_distinct(buf1, buf_shared));
    assert!(keys_are_sufficiently_distinct(buf2, buf_shared));

    // derive shared key with 0 running version
    let buf_shared2 = &mut [0u8; KEY_SIZE as usize];
    let _ = hwkey_session
        .derive_key_req()
        .kdf(kdf_version)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Version(0))
        .shared_key()
        .derive(NONSENSE_DATA_32B, buf_shared2)
        .expect("could not derive key");

    assert_ne!(buf1, buf_shared2);
    assert_ne!(buf2, buf_shared2);
    assert_ne!(buf_shared, buf_shared2);
    assert_ne!(buf_shared2, NONSENSE_DATA_32B);
    assert!(keys_are_sufficiently_distinct(buf1, buf_shared2));
    assert!(keys_are_sufficiently_distinct(buf2, buf_shared2));
    assert!(keys_are_sufficiently_distinct(buf_shared, buf_shared2));
}

#[test]
fn test_hwkey_derive_null_context() {
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    let buf1 = &mut [0u8; KEY_SIZE as usize];
    let _ = hwkey_session
        .derive_key_req()
        .derive(&[], buf1)
        .expect_err("able to derive with empty context");
}

#[test]
fn test_hwkey_derive_newer_versions() {
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf1 = &mut [0u8; KEY_SIZE as usize];

    let os_rollback_version = hwkey_session
        .query_current_os_version(RollbackVersionSource::RunningVersion)
        .expect("could not query version");

    match os_rollback_version {
        OsRollbackVersion::Version(n) => {
            // request a newer version
            let _ = hwkey_session
                .derive_key_req()
                .kdf(KdfVersion::Best)
                .rollback_version_source(RollbackVersionSource::RunningVersion)
                .os_rollback_version(OsRollbackVersion::Version(n + 1))
                .derive(NONSENSE_DATA_32B, buf1)
                .expect_err("versioned derive with too new running version");
        }
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }

    // query committed version
    let os_rollback_version = hwkey_session
        .query_current_os_version(RollbackVersionSource::CommittedVersion)
        .expect("could not query version");

    match os_rollback_version {
        OsRollbackVersion::Version(n) => {
            // request a newer version
            let _ = hwkey_session
                .derive_key_req()
                .kdf(KdfVersion::Best)
                .rollback_version_source(RollbackVersionSource::CommittedVersion)
                .os_rollback_version(OsRollbackVersion::Version(n + 1))
                .derive(NONSENSE_DATA_32B, buf1)
                .expect_err("versioned derive with too new running version");

            // try a very large version
            let _ = hwkey_session
                .derive_key_req()
                .kdf(KdfVersion::Best)
                .rollback_version_source(RollbackVersionSource::CommittedVersion)
                .os_rollback_version(OsRollbackVersion::Version(u32::MAX))
                .derive(NONSENSE_DATA_32B, buf1)
                .expect_err("versioned derive with far too large version");
        }
        _ => {
            assert_ne!(os_rollback_version, OsRollbackVersion::Current);
        }
    }
}

#[test]
fn test_hwkey_derive_large_payload() {
    const HEADER_SIZE: usize = mem::size_of::<hwkey_derive_versioned_msg>();
    const MAX_PAYLOAD_LEN: usize = HWKEY_MAX_MSG_SIZE as usize - HEADER_SIZE;

    // A payload length that should be supported everywhere, copied from the C
    // tests. If you increase this, make sure that all hwkey implementations
    // support a larger payload.
    const PAYLOAD_LEN: usize = 128;

    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let ctx = &[0u8; MAX_PAYLOAD_LEN + 1 as usize];
    let buf1 = &mut [0u8; MAX_PAYLOAD_LEN + 1 as usize];

    let _ = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .derive(&ctx[..PAYLOAD_LEN], &mut buf1[..PAYLOAD_LEN])
        .expect("versioned derive with large context and key");

    let err = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .derive(ctx, &mut buf1[..MAX_PAYLOAD_LEN as usize - 1])
        .expect_err("versioned derive with too large context");

    assert_eq!(err, HwkeyError::BadLen);

    let err = hwkey_session
        .derive_key_req()
        .kdf(KdfVersion::Best)
        .rollback_version_source(RollbackVersionSource::RunningVersion)
        .os_rollback_version(OsRollbackVersion::Current)
        .derive(&ctx[..MAX_PAYLOAD_LEN as usize - 1], buf1)
        .expect_err("versioned derive with too large key");

    assert_eq!(err, HwkeyError::BadLen);
}

#[test]
fn test_query_current_os_version() {
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    let os_rollback_version = hwkey_session
        .query_current_os_version(RollbackVersionSource::RunningVersion)
        .expect("could not query version");
    assert_ne!(os_rollback_version, OsRollbackVersion::Current);

    let os_rollback_version = hwkey_session
        .query_current_os_version(RollbackVersionSource::CommittedVersion)
        .expect("could not query version");
    assert_ne!(os_rollback_version, OsRollbackVersion::Current);
}

#[test]
fn test_get_keyslot_storage_auth() {
    let keyslot = CStr::from_bytes_with_nul(RPMB_STORAGE_AUTH_KEY_ID).unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; KEY_SIZE as usize];
    let err = hwkey_session
        .get_keyslot_data(keyslot, buf)
        .expect_err("auth key accessible when it shouldn't be");
    assert_eq!(err, HwkeyError::NotFound);
}

#[test]
fn test_get_keybox() {
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_KEYBOX_ID).unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; KEY_SIZE as usize];
    let keyslot_res = hwkey_session.get_keyslot_data(keyslot, buf);
    if cfg!(feature = "hwcrypto-unittest") {
        assert_eq!(UNITTEST_KEYSLOT, keyslot_res.expect("could not get keyslot data"))
    } else {
        assert!(keyslot_res.is_err());
    }
}

#[test]
fn test_get_derived_keybox() {
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_DERIVED_KEYBOX_ID).unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; KEY_SIZE as usize];
    let keyslot_res = hwkey_session.get_keyslot_data(keyslot, buf);
    if cfg!(feature = "hwcrypto-unittest") {
        assert_eq!(UNITTEST_DERIVED_KEYSLOT, keyslot_res.expect("could not get keyslot data"))
    } else {
        assert!(keyslot_res.is_err());
    }
}

#[test]
fn test_get_opaque_handle() {
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID).unwrap();
    const HWKEY_OPAQUE_HANDLE_MAX_SIZE: usize = 128;
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let keyslot_res = hwkey_session.get_keyslot_data(keyslot, buf);
    if cfg!(feature = "hwcrypto-unittest") {
        assert!(
            keyslot_res.expect("could not retrieve keyslot data").len()
                <= HWKEY_OPAQUE_HANDLE_MAX_SIZE
        )
    } else {
        assert!(keyslot_res.is_err());
    }
}

#[test]
#[cfg(feature = "hwcrypto-unittest")]
fn test_get_opaque_key() {
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID).unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let opaque_handle =
        hwkey_session.get_keyslot_data(keyslot, buf).expect("could not retrieve keyslot data");
    assert!(opaque_handle.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

    let key_buf = &mut [0u8; KEY_SIZE as usize];
    let keyslot_data = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
        .expect("could not retrieve keyslot data");
    assert_eq!(UNITTEST_KEYSLOT, keyslot_data)
}

#[test]
#[cfg(feature = "hwcrypto-unittest")]
fn test_get_multiple_opaque_keys() {
    let handle_buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let handle: &[u8];
    let no_access_handle_buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let no_access_handle: &[u8];
    {
        // close hwkey session when scope ends
        let hwkey_session = Hwkey::open().expect("could not open hwkey session");

        // get handle of opaque key
        let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID).unwrap();
        handle = hwkey_session
            .get_keyslot_data(keyslot, handle_buf)
            .expect("could not retrieve keyslot data");
        assert!(handle.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

        // get handle of opaque key that there is no access to
        let keyslot =
            CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_NOACCESS_ID).unwrap();
        no_access_handle = hwkey_session
            .get_keyslot_data(keyslot, no_access_handle_buf)
            .expect("could not retrieve keyslot data");
        assert!(no_access_handle.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

        // the handles should be different
        assert_ne!(handle, no_access_handle);

        // test the key belonging to the key slot
        let key_buf = &mut [0u8; KEY_SIZE as usize];
        let handle_keyslot_data = hwkey_session
            .get_keyslot_data(CStr::from_bytes_with_nul(handle).unwrap(), key_buf)
            .expect("could not retrieve keyslot data");
        assert_eq!(UNITTEST_KEYSLOT, handle_keyslot_data);

        // test no access
        let key_buf = &mut [0u8; KEY_SIZE as usize];
        let err = hwkey_session
            .get_keyslot_data(CStr::from_bytes_with_nul(no_access_handle).unwrap(), key_buf)
            .expect_err("key accessible when it shouldn't be");
        assert_eq!(err, HwkeyError::NotFound);
    }

    // session has closed following end of scope above, open a new session
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    // ensure that the tokens have been dropped and cleared
    let key_buf = &mut [0u8; KEY_SIZE as usize];
    let err = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(handle).unwrap(), key_buf)
        .expect_err("key accessible when it shouldn't be");
    assert_eq!(err, HwkeyError::NotFound);

    let key_buf = &mut [0u8; KEY_SIZE as usize];
    let err = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(no_access_handle).unwrap(), key_buf)
        .expect_err("key accessible when it shouldn't be");
    assert_eq!(err, HwkeyError::NotFound);
}

#[test]
#[cfg(feature = "hwcrypto-unittest")]
fn test_get_opaque_handle_multiple_sessions() {
    const HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID2: &'static [u8] =
        b"com.android.trusty.hwcrypto.unittest.opaque_handle2\0";
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID).unwrap();
    let key_buf = &mut [0u8; KEY_SIZE as usize];
    let buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let buf2 = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let opaque_handle;
    let opaque_handle2;

    {
        // scope of first hwkey session
        let hwkey_session = Hwkey::open().expect("could not open hwkey session");
        opaque_handle = hwkey_session
            .get_keyslot_data(keyslot, buf)
            .expect("get hwcrypto-unittest opaque keybox");
        assert!(opaque_handle.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

        {
            // scope of second hwkey session
            let hwkey_session2 = Hwkey::open().expect("could not open hwkey session");
            let _ = hwkey_session2
                .get_keyslot_data(keyslot, buf2)
                .expect_err("retrieve same handle twice");

            let keyslot2 = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID2).unwrap();
            opaque_handle2 = hwkey_session2
                .get_keyslot_data(keyslot2, buf2)
                .expect("get hwcrypto-unittest opaque keybox");
            assert!(opaque_handle2.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

            // fetch the keys via the first session
            let keyslot_data = hwkey_session
                .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
                .expect("could not retrieve keyslot data");
            assert_eq!(UNITTEST_KEYSLOT, keyslot_data);

            key_buf.fill(0);
            let keyslot_data = hwkey_session
                .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle2).unwrap(), key_buf)
                .expect("could not retrieve keyslot data");
            assert_eq!(UNITTEST_KEYSLOT, keyslot_data);

            // fetch the same key via the second session
            key_buf.fill(0);
            let keyslot_data = hwkey_session2
                .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
                .expect("could not retrieve keyslot data");
            assert_eq!(UNITTEST_KEYSLOT, keyslot_data);

            key_buf.fill(0);
            let keyslot_data = hwkey_session2
                .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle2).unwrap(), key_buf)
                .expect("could not retrieve keyslot data");
            assert_eq!(UNITTEST_KEYSLOT, keyslot_data);
        } // end of second hwkey session scope

        // second session is now closed, make sure the first session
        // handle is still valid while the second is invalid

        let _ = hwkey_session
            .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
            .expect("first session handle wasn't valid");

        let _ = hwkey_session
            .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle2).unwrap(), key_buf)
            .expect_err("second session handle was still valid");
    } // end of first hwkey session scope

    // disconnect the original session which retrieved the handle, open a new one
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");

    let _ = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
        .expect_err("first session handle was still valid");

    let _ = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle2).unwrap(), key_buf)
        .expect_err("second session handle was still valid");
}

#[test]
#[cfg(feature = "hwcrypto-unittest")]
fn test_try_empty_opaque_handle() {
    let keyslot = CStr::from_bytes_with_nul(b"\0").unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let _ = hwkey_session
        .get_keyslot_data(keyslot, buf)
        .expect_err("retrieving a key with an empty access token succeeded");
}

#[test]
#[cfg(feature = "hwcrypto-unittest")]
fn test_get_opaque_derived_key() {
    const HWCRYPTO_UNITTEST_OPAQUE_DERIVED_ID: &'static [u8] =
        b"com.android.trusty.hwcrypto.unittest.opaque_derived\0";
    let keyslot = CStr::from_bytes_with_nul(HWCRYPTO_UNITTEST_OPAQUE_DERIVED_ID).unwrap();
    let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    let buf = &mut [0u8; HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize];
    let opaque_handle =
        hwkey_session.get_keyslot_data(keyslot, buf).expect("could not retrieve keyslot data");
    assert!(opaque_handle.len() <= HWKEY_OPAQUE_HANDLE_MAX_SIZE as usize);

    let key_buf = &mut [0u8; KEY_SIZE as usize];
    let keyslot_data = hwkey_session
        .get_keyslot_data(CStr::from_bytes_with_nul(opaque_handle).unwrap(), key_buf)
        .expect("could not retrieve keyslot data");
    assert_eq!(UNITTEST_DERIVED_KEYSLOT, keyslot_data)
}
