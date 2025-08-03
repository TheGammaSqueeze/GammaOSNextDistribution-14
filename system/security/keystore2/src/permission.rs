// Copyright 2020, The Android Open Source Project
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

//! This crate provides access control primitives for Keystore 2.0.
//! It provides high level functions for checking permissions in the keystore2 and keystore2_key
//! SELinux classes based on the keystore2_selinux backend.
//! It also provides KeystorePerm and KeyPerm as convenience wrappers for the SELinux permission
//! defined by keystore2 and keystore2_key respectively.

use crate::error::Error as KsError;
use crate::error::ResponseCode;
use crate::ks_err;
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, KeyPermission::KeyPermission,
};
use anyhow::Context as AnyhowContext;
use keystore2_selinux as selinux;
use lazy_static::lazy_static;
use selinux::{implement_class, Backend, ClassPermission};
use std::cmp::PartialEq;
use std::convert::From;
use std::ffi::CStr;

// Replace getcon with a mock in the test situation
#[cfg(not(test))]
use selinux::getcon;
#[cfg(test)]
use tests::test_getcon as getcon;

lazy_static! {
    // Panicking here is allowed because keystore cannot function without this backend
    // and it would happen early and indicate a gross misconfiguration of the device.
    static ref KEYSTORE2_KEY_LABEL_BACKEND: selinux::KeystoreKeyBackend =
            selinux::KeystoreKeyBackend::new().unwrap();
}

fn lookup_keystore2_key_context(namespace: i64) -> anyhow::Result<selinux::Context> {
    KEYSTORE2_KEY_LABEL_BACKEND.lookup(&namespace.to_string())
}

implement_class!(
    /// KeyPerm provides a convenient abstraction from the SELinux class `keystore2_key`.
    /// At the same time it maps `KeyPermissions` from the Keystore 2.0 AIDL Grant interface to
    /// the SELinux permissions.
    #[repr(i32)]
    #[selinux(class_name = keystore2_key)]
    #[derive(Clone, Copy, Debug, PartialEq, Eq)]
    pub enum KeyPerm {
        /// Checked when convert_storage_key_to_ephemeral is called.
        #[selinux(name = convert_storage_key_to_ephemeral)]
        ConvertStorageKeyToEphemeral = KeyPermission::CONVERT_STORAGE_KEY_TO_EPHEMERAL.0,
        /// Checked when the caller tries do delete a key.
        #[selinux(name = delete)]
        Delete = KeyPermission::DELETE.0,
        /// Checked when the caller tries to use a unique id.
        #[selinux(name = gen_unique_id)]
        GenUniqueId = KeyPermission::GEN_UNIQUE_ID.0,
        /// Checked when the caller tries to load a key.
        #[selinux(name = get_info)]
        GetInfo = KeyPermission::GET_INFO.0,
        /// Checked when the caller attempts to grant a key to another uid.
        /// Also used for gating key migration attempts.
        #[selinux(name = grant)]
        Grant = KeyPermission::GRANT.0,
        /// Checked when the caller attempts to use Domain::BLOB.
        #[selinux(name = manage_blob)]
        ManageBlob = KeyPermission::MANAGE_BLOB.0,
        /// Checked when the caller tries to create a key which implies rebinding
        /// an alias to the new key.
        #[selinux(name = rebind)]
        Rebind = KeyPermission::REBIND.0,
        /// Checked when the caller attempts to create a forced operation.
        #[selinux(name = req_forced_op)]
        ReqForcedOp = KeyPermission::REQ_FORCED_OP.0,
        /// Checked when the caller attempts to update public key artifacts.
        #[selinux(name = update)]
        Update = KeyPermission::UPDATE.0,
        /// Checked when the caller attempts to use a private or public key.
        #[selinux(name = use)]
        Use = KeyPermission::USE.0,
        /// Does nothing, and is not checked. For use of device identifiers,
        /// the caller must hold the READ_PRIVILEGED_PHONE_STATE Android
        /// permission.
        #[selinux(name = use_dev_id)]
        UseDevId = KeyPermission::USE_DEV_ID.0,
    }
);

implement_class!(
    /// KeystorePerm provides a convenient abstraction from the SELinux class `keystore2`.
    /// Using the implement_permission macro we get the same features as `KeyPerm`.
    #[selinux(class_name = keystore2)]
    #[derive(Clone, Copy, Debug, PartialEq, Eq)]
    pub enum KeystorePerm {
        /// Checked when a new auth token is installed.
        #[selinux(name = add_auth)]
        AddAuth,
        /// Checked when an app is uninstalled or wiped.
        #[selinux(name = clear_ns)]
        ClearNs,
        /// Checked when Keystore 2.0 is asked to list a namespace that the caller
        /// does not have the get_info permission for.
        #[selinux(name = list)]
        List,
        /// Checked when Keystore 2.0 gets locked.
        #[selinux(name = lock)]
        Lock,
        /// Checked when Keystore 2.0 shall be reset.
        #[selinux(name = reset)]
        Reset,
        /// Checked when Keystore 2.0 shall be unlocked.
        #[selinux(name = unlock)]
        Unlock,
        /// Checked when user is added or removed.
        #[selinux(name = change_user)]
        ChangeUser,
        /// Checked when password of the user is changed.
        #[selinux(name = change_password)]
        ChangePassword,
        /// Checked when a UID is cleared.
        #[selinux(name = clear_uid)]
        ClearUID,
        /// Checked when Credstore calls IKeystoreAuthorization to obtain auth tokens.
        #[selinux(name = get_auth_token)]
        GetAuthToken,
        /// Checked when earlyBootEnded() is called.
        #[selinux(name = early_boot_ended)]
        EarlyBootEnded,
        /// Checked when IKeystoreMaintenance::onDeviceOffBody is called.
        #[selinux(name = report_off_body)]
        ReportOffBody,
        /// Checked when IkeystoreMetrics::pullMetrics is called.
        #[selinux(name = pull_metrics)]
        PullMetrics,
        /// Checked when IKeystoreMaintenance::deleteAllKeys is called.
        #[selinux(name = delete_all_keys)]
        DeleteAllKeys,
        /// Checked on calls to IRemotelyProvisionedKeyPool::getAttestationKey
        #[selinux(name = get_attestation_key)]
        GetAttestationKey,
        /// Checked on IKeystoreAuthorization::getLastAuthTime() is called.
        #[selinux(name = get_last_auth_time)]
        GetLastAuthTime,
    }
);

/// Represents a set of `KeyPerm` permissions.
/// `IntoIterator` is implemented for this struct allowing the iteration through all the
/// permissions in the set.
/// It also implements a function `includes(self, other)` that checks if the permissions
/// in `other` are included in `self`.
///
/// KeyPermSet can be created with the macro `key_perm_set![]`.
///
/// ## Example
/// ```
/// let perms1 = key_perm_set![KeyPerm::Use, KeyPerm::ManageBlob, KeyPerm::Grant];
/// let perms2 = key_perm_set![KeyPerm::Use, KeyPerm::ManageBlob];
///
/// assert!(perms1.includes(perms2))
/// assert!(!perms2.includes(perms1))
///
/// let i = perms1.into_iter();
/// // iteration in ascending order of the permission's numeric representation.
/// assert_eq(Some(KeyPerm::ManageBlob), i.next());
/// assert_eq(Some(KeyPerm::Grant), i.next());
/// assert_eq(Some(KeyPerm::Use), i.next());
/// assert_eq(None, i.next());
/// ```
#[derive(Copy, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct KeyPermSet(pub i32);

mod perm {
    use super::*;

    pub struct IntoIter {
        vec: KeyPermSet,
        pos: u8,
    }

    impl IntoIter {
        pub fn new(v: KeyPermSet) -> Self {
            Self { vec: v, pos: 0 }
        }
    }

    impl std::iter::Iterator for IntoIter {
        type Item = KeyPerm;

        fn next(&mut self) -> Option<Self::Item> {
            loop {
                if self.pos == 32 {
                    return None;
                }
                let p = self.vec.0 & (1 << self.pos);
                self.pos += 1;
                if p != 0 {
                    return Some(KeyPerm::from(p));
                }
            }
        }
    }
}

impl From<KeyPerm> for KeyPermSet {
    fn from(p: KeyPerm) -> Self {
        Self(p as i32)
    }
}

/// allow conversion from the AIDL wire type i32 to a permission set.
impl From<i32> for KeyPermSet {
    fn from(p: i32) -> Self {
        Self(p)
    }
}

impl From<KeyPermSet> for i32 {
    fn from(p: KeyPermSet) -> i32 {
        p.0
    }
}

impl KeyPermSet {
    /// Returns true iff this permission set has all of the permissions that are in `other`.
    pub fn includes<T: Into<KeyPermSet>>(&self, other: T) -> bool {
        let o: KeyPermSet = other.into();
        (self.0 & o.0) == o.0
    }
}

/// This macro can be used to create a `KeyPermSet` from a list of `KeyPerm` values.
///
/// ## Example
/// ```
/// let v = key_perm_set![Perm::delete(), Perm::manage_blob()];
/// ```
#[macro_export]
macro_rules! key_perm_set {
    () => { KeyPermSet(0) };
    ($head:expr $(, $tail:expr)* $(,)?) => {
        KeyPermSet($head as i32 $(| $tail as i32)*)
    };
}

impl IntoIterator for KeyPermSet {
    type Item = KeyPerm;
    type IntoIter = perm::IntoIter;

    fn into_iter(self) -> Self::IntoIter {
        Self::IntoIter::new(self)
    }
}

/// Uses `selinux::check_permission` to check if the given caller context `caller_cxt` may access
/// the given permision `perm` of the `keystore2` security class.
pub fn check_keystore_permission(caller_ctx: &CStr, perm: KeystorePerm) -> anyhow::Result<()> {
    let target_context = getcon().context("check_keystore_permission: getcon failed.")?;
    selinux::check_permission(caller_ctx, &target_context, perm)
}

/// Uses `selinux::check_permission` to check if the given caller context `caller_cxt` has
/// all the permissions indicated in `access_vec` for the target domain indicated by the key
/// descriptor `key` in the security class `keystore2_key`.
///
/// Also checks if the caller has the grant permission for the given target domain.
///
/// Attempts to grant the grant permission are always denied.
///
/// The only viable target domains are
///  * `Domain::APP` in which case u:r:keystore:s0 is used as target context and
///  * `Domain::SELINUX` in which case the `key.nspace` parameter is looked up in
///                      SELinux keystore key backend, and the result is used
///                      as target context.
pub fn check_grant_permission(
    caller_ctx: &CStr,
    access_vec: KeyPermSet,
    key: &KeyDescriptor,
) -> anyhow::Result<()> {
    let target_context = match key.domain {
        Domain::APP => getcon().context("check_grant_permission: getcon failed.")?,
        Domain::SELINUX => lookup_keystore2_key_context(key.nspace)
            .context("check_grant_permission: Domain::SELINUX: Failed to lookup namespace.")?,
        _ => return Err(KsError::sys()).context(format!("Cannot grant {:?}.", key.domain)),
    };

    selinux::check_permission(caller_ctx, &target_context, KeyPerm::Grant)
        .context("Grant permission is required when granting.")?;

    if access_vec.includes(KeyPerm::Grant) {
        return Err(selinux::Error::perm()).context("Grant permission cannot be granted.");
    }

    for p in access_vec.into_iter() {
        selinux::check_permission(caller_ctx, &target_context, p).context(ks_err!(
            "check_permission failed. \
            The caller may have tried to grant a permission that they don't possess. {:?}",
            p
        ))?
    }
    Ok(())
}

/// Uses `selinux::check_permission` to check if the given caller context `caller_cxt`
/// has the permissions indicated by `perm` for the target domain indicated by the key
/// descriptor `key` in the security class `keystore2_key`.
///
/// The behavior differs slightly depending on the selected target domain:
///  * `Domain::APP` u:r:keystore:s0 is used as target context.
///  * `Domain::SELINUX` `key.nspace` parameter is looked up in the SELinux keystore key
///                      backend, and the result is used as target context.
///  * `Domain::BLOB` Same as SELinux but the "manage_blob" permission is always checked additionally
///                   to the one supplied in `perm`.
///  * `Domain::GRANT` Does not use selinux::check_permission. Instead the `access_vector`
///                    parameter is queried for permission, which must be supplied in this case.
///
/// ## Return values.
///  * Ok(()) If the requested permissions were granted.
///  * Err(selinux::Error::perm()) If the requested permissions were denied.
///  * Err(KsError::sys()) This error is produced if `Domain::GRANT` is selected but no `access_vec`
///                      was supplied. It is also produced if `Domain::KEY_ID` was selected, and
///                      on various unexpected backend failures.
pub fn check_key_permission(
    caller_uid: u32,
    caller_ctx: &CStr,
    perm: KeyPerm,
    key: &KeyDescriptor,
    access_vector: &Option<KeyPermSet>,
) -> anyhow::Result<()> {
    // If an access vector was supplied, the key is either accessed by GRANT or by KEY_ID.
    // In the former case, key.domain was set to GRANT and we check the failure cases
    // further below. If the access is requested by KEY_ID, key.domain would have been
    // resolved to APP or SELINUX depending on where the key actually resides.
    // Either way we can return here immediately if the access vector covers the requested
    // permission. If it does not, we can still check if the caller has access by means of
    // ownership.
    if let Some(access_vector) = access_vector {
        if access_vector.includes(perm) {
            return Ok(());
        }
    }

    let target_context = match key.domain {
        // apps get the default keystore context
        Domain::APP => {
            if caller_uid as i64 != key.nspace {
                return Err(selinux::Error::perm())
                    .context("Trying to access key without ownership.");
            }
            getcon().context(ks_err!("getcon failed."))?
        }
        Domain::SELINUX => lookup_keystore2_key_context(key.nspace)
            .context(ks_err!("Domain::SELINUX: Failed to lookup namespace."))?,
        Domain::GRANT => {
            match access_vector {
                Some(_) => {
                    return Err(selinux::Error::perm())
                        .context(format!("\"{}\" not granted", perm.name()));
                }
                None => {
                    // If DOMAIN_GRANT was selected an access vector must be supplied.
                    return Err(KsError::sys()).context(ks_err!(
                        "Cannot check permission for Domain::GRANT without access vector.",
                    ));
                }
            }
        }
        Domain::KEY_ID => {
            // We should never be called with `Domain::KEY_ID. The database
            // lookup should have converted this into one of `Domain::APP`
            // or `Domain::SELINUX`.
            return Err(KsError::sys())
                .context(ks_err!("Cannot check permission for Domain::KEY_ID.",));
        }
        Domain::BLOB => {
            let tctx = lookup_keystore2_key_context(key.nspace)
                .context(ks_err!("Domain::BLOB: Failed to lookup namespace."))?;
            // If DOMAIN_KEY_BLOB was specified, we check for the "manage_blob"
            // permission in addition to the requested permission.
            selinux::check_permission(caller_ctx, &tctx, KeyPerm::ManageBlob)?;

            tctx
        }
        _ => {
            return Err(KsError::Rc(ResponseCode::INVALID_ARGUMENT))
                .context(format!("Unknown domain value: \"{:?}\".", key.domain))
        }
    };

    selinux::check_permission(caller_ctx, &target_context, perm)
}

#[cfg(test)]
mod tests {
    use super::*;
    use anyhow::anyhow;
    use anyhow::Result;
    use keystore2_selinux::*;

    const ALL_PERMS: KeyPermSet = key_perm_set![
        KeyPerm::ManageBlob,
        KeyPerm::Delete,
        KeyPerm::UseDevId,
        KeyPerm::ReqForcedOp,
        KeyPerm::GenUniqueId,
        KeyPerm::Grant,
        KeyPerm::GetInfo,
        KeyPerm::Rebind,
        KeyPerm::Update,
        KeyPerm::Use,
        KeyPerm::ConvertStorageKeyToEphemeral,
    ];

    const SYSTEM_SERVER_PERMISSIONS_NO_GRANT: KeyPermSet = key_perm_set![
        KeyPerm::Delete,
        KeyPerm::UseDevId,
        // No KeyPerm::Grant
        KeyPerm::GetInfo,
        KeyPerm::Rebind,
        KeyPerm::Update,
        KeyPerm::Use,
    ];

    const NOT_GRANT_PERMS: KeyPermSet = key_perm_set![
        KeyPerm::ManageBlob,
        KeyPerm::Delete,
        KeyPerm::UseDevId,
        KeyPerm::ReqForcedOp,
        KeyPerm::GenUniqueId,
        // No KeyPerm::Grant
        KeyPerm::GetInfo,
        KeyPerm::Rebind,
        KeyPerm::Update,
        KeyPerm::Use,
        KeyPerm::ConvertStorageKeyToEphemeral,
    ];

    const UNPRIV_PERMS: KeyPermSet = key_perm_set![
        KeyPerm::Delete,
        KeyPerm::GetInfo,
        KeyPerm::Rebind,
        KeyPerm::Update,
        KeyPerm::Use,
    ];

    /// The su_key namespace as defined in su.te and keystore_key_contexts of the
    /// SePolicy (system/sepolicy).
    const SU_KEY_NAMESPACE: i32 = 0;
    /// The shell_key namespace as defined in shell.te and keystore_key_contexts of the
    /// SePolicy (system/sepolicy).
    const SHELL_KEY_NAMESPACE: i32 = 1;

    pub fn test_getcon() -> Result<Context> {
        Context::new("u:object_r:keystore:s0")
    }

    // This macro evaluates the given expression and checks that
    // a) evaluated to Result::Err() and that
    // b) the wrapped error is selinux::Error::perm() (permission denied).
    // We use a macro here because a function would mask which invocation caused the failure.
    //
    // TODO b/164121720 Replace this macro with a function when `track_caller` is available.
    macro_rules! assert_perm_failed {
        ($test_function:expr) => {
            let result = $test_function;
            assert!(result.is_err(), "Permission check should have failed.");
            assert_eq!(
                Some(&selinux::Error::perm()),
                result.err().unwrap().root_cause().downcast_ref::<selinux::Error>()
            );
        };
    }

    fn check_context() -> Result<(selinux::Context, i32, bool)> {
        // Calling the non mocked selinux::getcon here intended.
        let context = selinux::getcon()?;
        match context.to_str().unwrap() {
            "u:r:su:s0" => Ok((context, SU_KEY_NAMESPACE, true)),
            "u:r:shell:s0" => Ok((context, SHELL_KEY_NAMESPACE, false)),
            c => Err(anyhow!(format!(
                "This test must be run as \"su\" or \"shell\". Current context: \"{}\"",
                c
            ))),
        }
    }

    #[test]
    fn check_keystore_permission_test() -> Result<()> {
        let system_server_ctx = Context::new("u:r:system_server:s0")?;
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::AddAuth).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::ClearNs).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::Lock).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::Reset).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::Unlock).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::ChangeUser).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::ChangePassword).is_ok());
        assert!(check_keystore_permission(&system_server_ctx, KeystorePerm::ClearUID).is_ok());
        let shell_ctx = Context::new("u:r:shell:s0")?;
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::AddAuth));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::ClearNs));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::List));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::Lock));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::Reset));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::Unlock));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::ChangeUser));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::ChangePassword));
        assert_perm_failed!(check_keystore_permission(&shell_ctx, KeystorePerm::ClearUID));
        Ok(())
    }

    #[test]
    fn check_grant_permission_app() -> Result<()> {
        let system_server_ctx = Context::new("u:r:system_server:s0")?;
        let shell_ctx = Context::new("u:r:shell:s0")?;
        let key = KeyDescriptor { domain: Domain::APP, nspace: 0, alias: None, blob: None };
        check_grant_permission(&system_server_ctx, SYSTEM_SERVER_PERMISSIONS_NO_GRANT, &key)
            .expect("Grant permission check failed.");

        // attempts to grant the grant permission must always fail even when privileged.
        assert_perm_failed!(check_grant_permission(
            &system_server_ctx,
            KeyPerm::Grant.into(),
            &key
        ));
        // unprivileged grant attempts always fail. shell does not have the grant permission.
        assert_perm_failed!(check_grant_permission(&shell_ctx, UNPRIV_PERMS, &key));
        Ok(())
    }

    #[test]
    fn check_grant_permission_selinux() -> Result<()> {
        let (sctx, namespace, is_su) = check_context()?;
        let key = KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: namespace as i64,
            alias: None,
            blob: None,
        };
        if is_su {
            assert!(check_grant_permission(&sctx, NOT_GRANT_PERMS, &key).is_ok());
            // attempts to grant the grant permission must always fail even when privileged.
            assert_perm_failed!(check_grant_permission(&sctx, KeyPerm::Grant.into(), &key));
        } else {
            // unprivileged grant attempts always fail. shell does not have the grant permission.
            assert_perm_failed!(check_grant_permission(&sctx, UNPRIV_PERMS, &key));
        }
        Ok(())
    }

    #[test]
    fn check_key_permission_domain_grant() -> Result<()> {
        let key = KeyDescriptor { domain: Domain::GRANT, nspace: 0, alias: None, blob: None };

        assert_perm_failed!(check_key_permission(
            0,
            &selinux::Context::new("ignored").unwrap(),
            KeyPerm::Grant,
            &key,
            &Some(UNPRIV_PERMS)
        ));

        check_key_permission(
            0,
            &selinux::Context::new("ignored").unwrap(),
            KeyPerm::Use,
            &key,
            &Some(ALL_PERMS),
        )
    }

    #[test]
    fn check_key_permission_domain_app() -> Result<()> {
        let system_server_ctx = Context::new("u:r:system_server:s0")?;
        let shell_ctx = Context::new("u:r:shell:s0")?;
        let gmscore_app = Context::new("u:r:gmscore_app:s0")?;

        let key = KeyDescriptor { domain: Domain::APP, nspace: 0, alias: None, blob: None };

        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::Use, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::Delete, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::GetInfo, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::Rebind, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::Update, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::Grant, &key, &None).is_ok());
        assert!(check_key_permission(0, &system_server_ctx, KeyPerm::UseDevId, &key, &None).is_ok());
        assert!(check_key_permission(0, &gmscore_app, KeyPerm::GenUniqueId, &key, &None).is_ok());

        assert!(check_key_permission(0, &shell_ctx, KeyPerm::Use, &key, &None).is_ok());
        assert!(check_key_permission(0, &shell_ctx, KeyPerm::Delete, &key, &None).is_ok());
        assert!(check_key_permission(0, &shell_ctx, KeyPerm::GetInfo, &key, &None).is_ok());
        assert!(check_key_permission(0, &shell_ctx, KeyPerm::Rebind, &key, &None).is_ok());
        assert!(check_key_permission(0, &shell_ctx, KeyPerm::Update, &key, &None).is_ok());
        assert_perm_failed!(check_key_permission(0, &shell_ctx, KeyPerm::Grant, &key, &None));
        assert_perm_failed!(check_key_permission(0, &shell_ctx, KeyPerm::ReqForcedOp, &key, &None));
        assert_perm_failed!(check_key_permission(0, &shell_ctx, KeyPerm::ManageBlob, &key, &None));
        assert_perm_failed!(check_key_permission(0, &shell_ctx, KeyPerm::UseDevId, &key, &None));
        assert_perm_failed!(check_key_permission(0, &shell_ctx, KeyPerm::GenUniqueId, &key, &None));

        // Also make sure that the permission fails if the caller is not the owner.
        assert_perm_failed!(check_key_permission(
            1, // the owner is 0
            &system_server_ctx,
            KeyPerm::Use,
            &key,
            &None
        ));
        // Unless there was a grant.
        assert!(check_key_permission(
            1,
            &system_server_ctx,
            KeyPerm::Use,
            &key,
            &Some(key_perm_set![KeyPerm::Use])
        )
        .is_ok());
        // But fail if the grant did not cover the requested permission.
        assert_perm_failed!(check_key_permission(
            1,
            &system_server_ctx,
            KeyPerm::Use,
            &key,
            &Some(key_perm_set![KeyPerm::GetInfo])
        ));

        Ok(())
    }

    #[test]
    fn check_key_permission_domain_selinux() -> Result<()> {
        let (sctx, namespace, is_su) = check_context()?;
        let key = KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: namespace as i64,
            alias: None,
            blob: None,
        };

        assert!(check_key_permission(0, &sctx, KeyPerm::Use, &key, &None).is_ok());
        assert!(check_key_permission(0, &sctx, KeyPerm::Delete, &key, &None).is_ok());
        assert!(check_key_permission(0, &sctx, KeyPerm::GetInfo, &key, &None).is_ok());
        assert!(check_key_permission(0, &sctx, KeyPerm::Rebind, &key, &None).is_ok());
        assert!(check_key_permission(0, &sctx, KeyPerm::Update, &key, &None).is_ok());

        if is_su {
            assert!(check_key_permission(0, &sctx, KeyPerm::Grant, &key, &None).is_ok());
            assert!(check_key_permission(0, &sctx, KeyPerm::ManageBlob, &key, &None).is_ok());
            assert!(check_key_permission(0, &sctx, KeyPerm::UseDevId, &key, &None).is_ok());
            assert!(check_key_permission(0, &sctx, KeyPerm::GenUniqueId, &key, &None).is_ok());
            assert!(check_key_permission(0, &sctx, KeyPerm::ReqForcedOp, &key, &None).is_ok());
        } else {
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::Grant, &key, &None));
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::ReqForcedOp, &key, &None));
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::ManageBlob, &key, &None));
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::UseDevId, &key, &None));
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::GenUniqueId, &key, &None));
        }
        Ok(())
    }

    #[test]
    fn check_key_permission_domain_blob() -> Result<()> {
        let (sctx, namespace, is_su) = check_context()?;
        let key = KeyDescriptor {
            domain: Domain::BLOB,
            nspace: namespace as i64,
            alias: None,
            blob: None,
        };

        if is_su {
            check_key_permission(0, &sctx, KeyPerm::Use, &key, &None)
        } else {
            assert_perm_failed!(check_key_permission(0, &sctx, KeyPerm::Use, &key, &None));
            Ok(())
        }
    }

    #[test]
    fn check_key_permission_domain_key_id() -> Result<()> {
        let key = KeyDescriptor { domain: Domain::KEY_ID, nspace: 0, alias: None, blob: None };

        assert_eq!(
            Some(&KsError::sys()),
            check_key_permission(
                0,
                &selinux::Context::new("ignored").unwrap(),
                KeyPerm::Use,
                &key,
                &None
            )
            .err()
            .unwrap()
            .root_cause()
            .downcast_ref::<KsError>()
        );
        Ok(())
    }

    #[test]
    fn key_perm_set_all_test() {
        let v = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::UseDevId,
            KeyPerm::ReqForcedOp,
            KeyPerm::GenUniqueId,
            KeyPerm::Grant,
            KeyPerm::GetInfo,
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use // Test if the macro accepts missing comma at the end of the list.
        ];
        let mut i = v.into_iter();
        assert_eq!(i.next().unwrap().name(), "delete");
        assert_eq!(i.next().unwrap().name(), "gen_unique_id");
        assert_eq!(i.next().unwrap().name(), "get_info");
        assert_eq!(i.next().unwrap().name(), "grant");
        assert_eq!(i.next().unwrap().name(), "manage_blob");
        assert_eq!(i.next().unwrap().name(), "rebind");
        assert_eq!(i.next().unwrap().name(), "req_forced_op");
        assert_eq!(i.next().unwrap().name(), "update");
        assert_eq!(i.next().unwrap().name(), "use");
        assert_eq!(i.next().unwrap().name(), "use_dev_id");
        assert_eq!(None, i.next());
    }
    #[test]
    fn key_perm_set_sparse_test() {
        let v = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::ReqForcedOp,
            KeyPerm::GenUniqueId,
            KeyPerm::Update,
            KeyPerm::Use, // Test if macro accepts the comma at the end of the list.
        ];
        let mut i = v.into_iter();
        assert_eq!(i.next().unwrap().name(), "gen_unique_id");
        assert_eq!(i.next().unwrap().name(), "manage_blob");
        assert_eq!(i.next().unwrap().name(), "req_forced_op");
        assert_eq!(i.next().unwrap().name(), "update");
        assert_eq!(i.next().unwrap().name(), "use");
        assert_eq!(None, i.next());
    }
    #[test]
    fn key_perm_set_empty_test() {
        let v = key_perm_set![];
        let mut i = v.into_iter();
        assert_eq!(None, i.next());
    }
    #[test]
    fn key_perm_set_include_subset_test() {
        let v1 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::UseDevId,
            KeyPerm::ReqForcedOp,
            KeyPerm::GenUniqueId,
            KeyPerm::Grant,
            KeyPerm::GetInfo,
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        let v2 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        assert!(v1.includes(v2));
        assert!(!v2.includes(v1));
    }
    #[test]
    fn key_perm_set_include_equal_test() {
        let v1 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        let v2 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        assert!(v1.includes(v2));
        assert!(v2.includes(v1));
    }
    #[test]
    fn key_perm_set_include_overlap_test() {
        let v1 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::Grant, // only in v1
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        let v2 = key_perm_set![
            KeyPerm::ManageBlob,
            KeyPerm::Delete,
            KeyPerm::ReqForcedOp, // only in v2
            KeyPerm::Rebind,
            KeyPerm::Update,
            KeyPerm::Use,
        ];
        assert!(!v1.includes(v2));
        assert!(!v2.includes(v1));
    }
    #[test]
    fn key_perm_set_include_no_overlap_test() {
        let v1 = key_perm_set![KeyPerm::ManageBlob, KeyPerm::Delete, KeyPerm::Grant,];
        let v2 =
            key_perm_set![KeyPerm::ReqForcedOp, KeyPerm::Rebind, KeyPerm::Update, KeyPerm::Use,];
        assert!(!v1.includes(v2));
        assert!(!v2.includes(v1));
    }
}
