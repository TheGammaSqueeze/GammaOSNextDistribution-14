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

//! This module implements IKeystoreAuthorization AIDL interface.

use crate::error::anyhow_error_to_cstring;
use crate::error::Error as KeystoreError;
use crate::globals::{DB, ENFORCEMENTS, LEGACY_IMPORTER, SUPER_KEY};
use crate::ks_err;
use crate::permission::KeystorePerm;
use crate::utils::{check_keystore_permission, watchdog as wd};
use aconfig_android_hardware_biometrics_rust;
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    HardwareAuthToken::HardwareAuthToken, HardwareAuthenticatorType::HardwareAuthenticatorType,
};
use android_security_authorization::aidl::android::security::authorization::{
    AuthorizationTokens::AuthorizationTokens, IKeystoreAuthorization::BnKeystoreAuthorization,
    IKeystoreAuthorization::IKeystoreAuthorization, ResponseCode::ResponseCode,
};
use android_security_authorization::binder::{
    BinderFeatures, ExceptionCode, Interface, Result as BinderResult, Status as BinderStatus,
    Strong,
};
use android_system_keystore2::aidl::android::system::keystore2::ResponseCode::ResponseCode as KsResponseCode;
use anyhow::{Context, Result};
use keystore2_crypto::Password;
use keystore2_selinux as selinux;
use std::ffi::CString;

/// This is the Authorization error type, it wraps binder exceptions and the
/// Authorization ResponseCode
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    /// Wraps an IKeystoreAuthorization response code as defined by
    /// android.security.authorization AIDL interface specification.
    #[error("Error::Rc({0:?})")]
    Rc(ResponseCode),
    /// Wraps a Binder exception code other than a service specific exception.
    #[error("Binder exception code {0:?}, {1:?}")]
    Binder(ExceptionCode, i32),
}

/// This function should be used by authorization service calls to translate error conditions
/// into service specific exceptions.
///
/// All error conditions get logged by this function.
///
/// `Error::Rc(x)` variants get mapped onto a service specific error code of `x`.
/// Certain response codes may be returned from keystore/ResponseCode.aidl by the keystore2 modules,
/// which are then converted to the corresponding response codes of android.security.authorization
/// AIDL interface specification.
///
/// `selinux::Error::perm()` is mapped on `ResponseCode::PERMISSION_DENIED`.
///
/// All non `Error` error conditions get mapped onto ResponseCode::SYSTEM_ERROR`.
///
/// `handle_ok` will be called if `result` is `Ok(value)` where `value` will be passed
/// as argument to `handle_ok`. `handle_ok` must generate a `BinderResult<T>`, but it
/// typically returns Ok(value).
pub fn map_or_log_err<T, U, F>(result: Result<U>, handle_ok: F) -> BinderResult<T>
where
    F: FnOnce(U) -> BinderResult<T>,
{
    result.map_or_else(
        |e| {
            log::error!("{:#?}", e);
            let root_cause = e.root_cause();
            if let Some(KeystoreError::Rc(ks_rcode)) = root_cause.downcast_ref::<KeystoreError>() {
                let rc = match *ks_rcode {
                    // Although currently keystore2/ResponseCode.aidl and
                    // authorization/ResponseCode.aidl share the same integer values for the
                    // common response codes, this may deviate in the future, hence the
                    // conversion here.
                    KsResponseCode::SYSTEM_ERROR => ResponseCode::SYSTEM_ERROR.0,
                    KsResponseCode::KEY_NOT_FOUND => ResponseCode::KEY_NOT_FOUND.0,
                    KsResponseCode::VALUE_CORRUPTED => ResponseCode::VALUE_CORRUPTED.0,
                    KsResponseCode::INVALID_ARGUMENT => ResponseCode::INVALID_ARGUMENT.0,
                    // If the code paths of IKeystoreAuthorization aidl's methods happen to return
                    // other error codes from KsResponseCode in the future, they should be converted
                    // as well.
                    _ => ResponseCode::SYSTEM_ERROR.0,
                };
                return Err(BinderStatus::new_service_specific_error(
                    rc,
                    anyhow_error_to_cstring(&e).as_deref(),
                ));
            }
            let rc = match root_cause.downcast_ref::<Error>() {
                Some(Error::Rc(rcode)) => rcode.0,
                Some(Error::Binder(_, _)) => ResponseCode::SYSTEM_ERROR.0,
                None => match root_cause.downcast_ref::<selinux::Error>() {
                    Some(selinux::Error::PermissionDenied) => ResponseCode::PERMISSION_DENIED.0,
                    _ => ResponseCode::SYSTEM_ERROR.0,
                },
            };
            Err(BinderStatus::new_service_specific_error(
                rc,
                anyhow_error_to_cstring(&e).as_deref(),
            ))
        },
        handle_ok,
    )
}

/// This struct is defined to implement the aforementioned AIDL interface.
/// As of now, it is an empty struct.
pub struct AuthorizationManager;

impl AuthorizationManager {
    /// Create a new instance of Keystore Authorization service.
    pub fn new_native_binder() -> Result<Strong<dyn IKeystoreAuthorization>> {
        Ok(BnKeystoreAuthorization::new_binder(
            Self,
            BinderFeatures { set_requesting_sid: true, ..BinderFeatures::default() },
        ))
    }

    fn add_auth_token(&self, auth_token: &HardwareAuthToken) -> Result<()> {
        // Check keystore permission.
        check_keystore_permission(KeystorePerm::AddAuth).context(ks_err!())?;

        log::info!(
            "add_auth_token(challenge={}, userId={}, authId={}, authType={:#x}, timestamp={}ms)",
            auth_token.challenge,
            auth_token.userId,
            auth_token.authenticatorId,
            auth_token.authenticatorType.0,
            auth_token.timestamp.milliSeconds,
        );

        ENFORCEMENTS.add_auth_token(auth_token.clone());
        Ok(())
    }

    fn on_device_unlocked(&self, user_id: i32, password: Option<Password>) -> Result<()> {
        log::info!(
            "on_device_unlocked(user_id={}, password.is_some()={})",
            user_id,
            password.is_some(),
        );
        check_keystore_permission(KeystorePerm::Unlock).context(ks_err!("Unlock."))?;
        ENFORCEMENTS.set_device_locked(user_id, false);

        let mut skm = SUPER_KEY.write().unwrap();
        if let Some(password) = password {
            DB.with(|db| {
                skm.unlock_user(&mut db.borrow_mut(), &LEGACY_IMPORTER, user_id as u32, &password)
            })
            .context(ks_err!("Unlock with password."))
        } else {
            DB.with(|db| skm.try_unlock_user_with_biometric(&mut db.borrow_mut(), user_id as u32))
                .context(ks_err!("try_unlock_user_with_biometric failed"))
        }
    }

    fn on_device_locked(
        &self,
        user_id: i32,
        unlocking_sids: &[i64],
        mut weak_unlock_enabled: bool,
    ) -> Result<()> {
        log::info!(
            "on_device_locked(user_id={}, unlocking_sids={:?}, weak_unlock_enabled={})",
            user_id,
            unlocking_sids,
            weak_unlock_enabled
        );
        if !android_security_flags::fix_unlocked_device_required_keys_v2() {
            weak_unlock_enabled = false;
        }
        check_keystore_permission(KeystorePerm::Lock).context(ks_err!("Lock"))?;
        ENFORCEMENTS.set_device_locked(user_id, true);
        let mut skm = SUPER_KEY.write().unwrap();
        DB.with(|db| {
            skm.lock_unlocked_device_required_keys(
                &mut db.borrow_mut(),
                user_id as u32,
                unlocking_sids,
                weak_unlock_enabled,
            );
        });
        Ok(())
    }

    fn on_weak_unlock_methods_expired(&self, user_id: i32) -> Result<()> {
        log::info!("on_weak_unlock_methods_expired(user_id={})", user_id);
        if !android_security_flags::fix_unlocked_device_required_keys_v2() {
            return Ok(());
        }
        check_keystore_permission(KeystorePerm::Lock).context(ks_err!("Lock"))?;
        SUPER_KEY.write().unwrap().wipe_plaintext_unlocked_device_required_keys(user_id as u32);
        Ok(())
    }

    fn on_non_lskf_unlock_methods_expired(&self, user_id: i32) -> Result<()> {
        log::info!("on_non_lskf_unlock_methods_expired(user_id={})", user_id);
        if !android_security_flags::fix_unlocked_device_required_keys_v2() {
            return Ok(());
        }
        check_keystore_permission(KeystorePerm::Lock).context(ks_err!("Lock"))?;
        SUPER_KEY.write().unwrap().wipe_all_unlocked_device_required_keys(user_id as u32);
        Ok(())
    }

    fn get_auth_tokens_for_credstore(
        &self,
        challenge: i64,
        secure_user_id: i64,
        auth_token_max_age_millis: i64,
    ) -> Result<AuthorizationTokens> {
        // Check permission. Function should return if this failed. Therefore having '?' at the end
        // is very important.
        check_keystore_permission(KeystorePerm::GetAuthToken).context(ks_err!("GetAuthToken"))?;

        // If the challenge is zero, return error
        if challenge == 0 {
            return Err(Error::Rc(ResponseCode::INVALID_ARGUMENT))
                .context(ks_err!("Challenge can not be zero."));
        }
        // Obtain the auth token and the timestamp token from the enforcement module.
        let (auth_token, ts_token) =
            ENFORCEMENTS.get_auth_tokens(challenge, secure_user_id, auth_token_max_age_millis)?;
        Ok(AuthorizationTokens { authToken: auth_token, timestampToken: ts_token })
    }

    fn get_last_auth_time(
        &self,
        secure_user_id: i64,
        auth_types: &[HardwareAuthenticatorType],
    ) -> Result<i64> {
        // Check keystore permission.
        check_keystore_permission(KeystorePerm::GetLastAuthTime).context(ks_err!())?;

        let mut max_time: i64 = -1;
        for auth_type in auth_types.iter() {
            if let Some(time) = ENFORCEMENTS.get_last_auth_time(secure_user_id, *auth_type) {
                if time.milliseconds() > max_time {
                    max_time = time.milliseconds();
                }
            }
        }

        if max_time >= 0 {
            Ok(max_time)
        } else {
            Err(Error::Rc(ResponseCode::NO_AUTH_TOKEN_FOUND))
                .context(ks_err!("No auth token found"))
        }
    }
}

impl Interface for AuthorizationManager {}

impl IKeystoreAuthorization for AuthorizationManager {
    fn addAuthToken(&self, auth_token: &HardwareAuthToken) -> BinderResult<()> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::addAuthToken", 500);
        map_or_log_err(self.add_auth_token(auth_token), Ok)
    }

    fn onDeviceUnlocked(&self, user_id: i32, password: Option<&[u8]>) -> BinderResult<()> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::onDeviceUnlocked", 500);
        map_or_log_err(self.on_device_unlocked(user_id, password.map(|pw| pw.into())), Ok)
    }

    fn onDeviceLocked(
        &self,
        user_id: i32,
        unlocking_sids: &[i64],
        weak_unlock_enabled: bool,
    ) -> BinderResult<()> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::onDeviceLocked", 500);
        map_or_log_err(self.on_device_locked(user_id, unlocking_sids, weak_unlock_enabled), Ok)
    }

    fn onWeakUnlockMethodsExpired(&self, user_id: i32) -> BinderResult<()> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::onWeakUnlockMethodsExpired", 500);
        map_or_log_err(self.on_weak_unlock_methods_expired(user_id), Ok)
    }

    fn onNonLskfUnlockMethodsExpired(&self, user_id: i32) -> BinderResult<()> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::onNonLskfUnlockMethodsExpired", 500);
        map_or_log_err(self.on_non_lskf_unlock_methods_expired(user_id), Ok)
    }

    fn getAuthTokensForCredStore(
        &self,
        challenge: i64,
        secure_user_id: i64,
        auth_token_max_age_millis: i64,
    ) -> binder::Result<AuthorizationTokens> {
        let _wp = wd::watch_millis("IKeystoreAuthorization::getAuthTokensForCredStore", 500);
        map_or_log_err(
            self.get_auth_tokens_for_credstore(
                challenge,
                secure_user_id,
                auth_token_max_age_millis,
            ),
            Ok,
        )
    }

    fn getLastAuthTime(
        &self,
        secure_user_id: i64,
        auth_types: &[HardwareAuthenticatorType],
    ) -> binder::Result<i64> {
        if aconfig_android_hardware_biometrics_rust::last_authentication_time() {
            map_or_log_err(self.get_last_auth_time(secure_user_id, auth_types), Ok)
        } else {
            Err(BinderStatus::new_service_specific_error(
                ResponseCode::PERMISSION_DENIED.0,
                Some(CString::new("Feature is not enabled.").unwrap().as_c_str()),
            ))
        }
    }
}
