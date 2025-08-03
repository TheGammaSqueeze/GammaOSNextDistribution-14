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

//! Helper wrapper around RKPD interface.

use android_security_rkp_aidl::aidl::android::security::rkp::{
    IGetKeyCallback::BnGetKeyCallback, IGetKeyCallback::ErrorCode::ErrorCode as GetKeyErrorCode,
    IGetKeyCallback::IGetKeyCallback, IGetRegistrationCallback::BnGetRegistrationCallback,
    IGetRegistrationCallback::IGetRegistrationCallback, IRegistration::IRegistration,
    IRemoteProvisioning::IRemoteProvisioning,
    IStoreUpgradedKeyCallback::BnStoreUpgradedKeyCallback,
    IStoreUpgradedKeyCallback::IStoreUpgradedKeyCallback,
    RemotelyProvisionedKey::RemotelyProvisionedKey,
};
use anyhow::{Context, Result};
use binder::{BinderFeatures, Interface, StatusCode, Strong};
use message_macro::source_location_msg;
use std::sync::Mutex;
use std::time::Duration;
use tokio::sync::oneshot;
use tokio::time::timeout;

// Normally, we block indefinitely when making calls outside of keystore and rely on watchdog to
// report deadlocks. However, RKPD is mainline updatable. Also, calls to RKPD may wait on network
// for certificates. So, we err on the side of caution and timeout instead.
static RKPD_TIMEOUT: Duration = Duration::from_secs(10);

fn tokio_rt() -> tokio::runtime::Runtime {
    tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap()
}

/// Errors occurred during the interaction with RKPD.
#[derive(Debug, Clone, Copy, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    /// An RKPD request gets cancelled.
    #[error("An RKPD request gets cancelled")]
    RequestCancelled,

    /// Failed to get registration.
    #[error("Failed to get registration")]
    GetRegistrationFailed,

    /// Failed to get key.
    #[error("Failed to get key: {0:?}")]
    GetKeyFailed(GetKeyErrorCode),

    /// Failed to store upgraded key.
    #[error("Failed to store upgraded key")]
    StoreUpgradedKeyFailed,

    /// Retryable timeout when waiting for a callback.
    #[error("Retryable timeout when waiting for a callback")]
    RetryableTimeout,

    /// Timeout when waiting for a callback.
    #[error("Timeout when waiting for a callback")]
    Timeout,

    /// Wraps a Binder status code.
    #[error("Binder transaction error {0:?}")]
    BinderTransaction(StatusCode),
}

impl From<StatusCode> for Error {
    fn from(s: StatusCode) -> Self {
        Self::BinderTransaction(s)
    }
}

/// Thread-safe channel for sending a value once and only once. If a value has
/// already been send, subsequent calls to send will noop.
struct SafeSender<T> {
    inner: Mutex<Option<oneshot::Sender<T>>>,
}

impl<T> SafeSender<T> {
    fn new(sender: oneshot::Sender<T>) -> Self {
        Self { inner: Mutex::new(Some(sender)) }
    }

    fn send(&self, value: T) {
        if let Some(inner) = self.inner.lock().unwrap().take() {
            // It's possible for the corresponding receiver to time out and be dropped. In this
            // case send() will fail. This error is not actionable though, so only log the error.
            if inner.send(value).is_err() {
                log::error!("SafeSender::send() failed");
            }
        }
    }
}

struct GetRegistrationCallback {
    registration_tx: SafeSender<Result<binder::Strong<dyn IRegistration>>>,
}

impl GetRegistrationCallback {
    pub fn new_native_binder(
        registration_tx: oneshot::Sender<Result<binder::Strong<dyn IRegistration>>>,
    ) -> Strong<dyn IGetRegistrationCallback> {
        let result: Self =
            GetRegistrationCallback { registration_tx: SafeSender::new(registration_tx) };
        BnGetRegistrationCallback::new_binder(result, BinderFeatures::default())
    }
}

impl Interface for GetRegistrationCallback {}

impl IGetRegistrationCallback for GetRegistrationCallback {
    fn onSuccess(&self, registration: &Strong<dyn IRegistration>) -> binder::Result<()> {
        self.registration_tx.send(Ok(registration.clone()));
        Ok(())
    }
    fn onCancel(&self) -> binder::Result<()> {
        log::warn!("IGetRegistrationCallback cancelled");
        self.registration_tx.send(
            Err(Error::RequestCancelled)
                .context(source_location_msg!("GetRegistrationCallback cancelled.")),
        );
        Ok(())
    }
    fn onError(&self, description: &str) -> binder::Result<()> {
        log::error!("IGetRegistrationCallback failed: '{description}'");
        self.registration_tx.send(
            Err(Error::GetRegistrationFailed)
                .context(source_location_msg!("GetRegistrationCallback failed: {:?}", description)),
        );
        Ok(())
    }
}

/// Make a new connection to a IRegistration service.
async fn get_rkpd_registration(rpc_name: &str) -> Result<binder::Strong<dyn IRegistration>> {
    let remote_provisioning: Strong<dyn IRemoteProvisioning> =
        binder::get_interface("remote_provisioning")
            .map_err(Error::from)
            .context(source_location_msg!("Trying to connect to IRemoteProvisioning service."))?;

    let (tx, rx) = oneshot::channel();
    let cb = GetRegistrationCallback::new_native_binder(tx);

    remote_provisioning
        .getRegistration(rpc_name, &cb)
        .context(source_location_msg!("Trying to get registration."))?;

    match timeout(RKPD_TIMEOUT, rx).await {
        Err(e) => Err(Error::Timeout).context(source_location_msg!("Waiting for RKPD: {:?}", e)),
        Ok(v) => v.unwrap(),
    }
}

struct GetKeyCallback {
    key_tx: SafeSender<Result<RemotelyProvisionedKey>>,
}

impl GetKeyCallback {
    pub fn new_native_binder(
        key_tx: oneshot::Sender<Result<RemotelyProvisionedKey>>,
    ) -> Strong<dyn IGetKeyCallback> {
        let result: Self = GetKeyCallback { key_tx: SafeSender::new(key_tx) };
        BnGetKeyCallback::new_binder(result, BinderFeatures::default())
    }
}

impl Interface for GetKeyCallback {}

impl IGetKeyCallback for GetKeyCallback {
    fn onSuccess(&self, key: &RemotelyProvisionedKey) -> binder::Result<()> {
        self.key_tx.send(Ok(RemotelyProvisionedKey {
            keyBlob: key.keyBlob.clone(),
            encodedCertChain: key.encodedCertChain.clone(),
        }));
        Ok(())
    }
    fn onCancel(&self) -> binder::Result<()> {
        log::warn!("IGetKeyCallback cancelled");
        self.key_tx.send(
            Err(Error::RequestCancelled).context(source_location_msg!("GetKeyCallback cancelled.")),
        );
        Ok(())
    }
    fn onError(&self, error: GetKeyErrorCode, description: &str) -> binder::Result<()> {
        log::error!("IGetKeyCallback failed: {description}");
        self.key_tx.send(Err(Error::GetKeyFailed(error)).context(source_location_msg!(
            "GetKeyCallback failed: {:?} {:?}",
            error,
            description
        )));
        Ok(())
    }
}

async fn get_rkpd_attestation_key_from_registration_async(
    registration: &Strong<dyn IRegistration>,
    caller_uid: u32,
) -> Result<RemotelyProvisionedKey> {
    let (tx, rx) = oneshot::channel();
    let cb = GetKeyCallback::new_native_binder(tx);

    registration
        .getKey(caller_uid.try_into().unwrap(), &cb)
        .context(source_location_msg!("Trying to get key."))?;

    match timeout(RKPD_TIMEOUT, rx).await {
        Err(e) => {
            // Make a best effort attempt to cancel the timed out request.
            if let Err(e) = registration.cancelGetKey(&cb) {
                log::error!("IRegistration::cancelGetKey failed: {:?}", e);
            }
            Err(Error::RetryableTimeout)
                .context(source_location_msg!("Waiting for RKPD key timed out: {:?}", e))
        }
        Ok(v) => v.unwrap(),
    }
}

async fn get_rkpd_attestation_key_async(
    rpc_name: &str,
    caller_uid: u32,
) -> Result<RemotelyProvisionedKey> {
    let registration = get_rkpd_registration(rpc_name)
        .await
        .context(source_location_msg!("Trying to get to IRegistration service."))?;
    get_rkpd_attestation_key_from_registration_async(&registration, caller_uid).await
}

struct StoreUpgradedKeyCallback {
    completer: SafeSender<Result<()>>,
}

impl StoreUpgradedKeyCallback {
    pub fn new_native_binder(
        completer: oneshot::Sender<Result<()>>,
    ) -> Strong<dyn IStoreUpgradedKeyCallback> {
        let result: Self = StoreUpgradedKeyCallback { completer: SafeSender::new(completer) };
        BnStoreUpgradedKeyCallback::new_binder(result, BinderFeatures::default())
    }
}

impl Interface for StoreUpgradedKeyCallback {}

impl IStoreUpgradedKeyCallback for StoreUpgradedKeyCallback {
    fn onSuccess(&self) -> binder::Result<()> {
        self.completer.send(Ok(()));
        Ok(())
    }

    fn onError(&self, error: &str) -> binder::Result<()> {
        log::error!("IStoreUpgradedKeyCallback failed: {error}");
        self.completer.send(
            Err(Error::StoreUpgradedKeyFailed)
                .context(source_location_msg!("Failed to store upgraded key: {:?}", error)),
        );
        Ok(())
    }
}

async fn store_rkpd_attestation_key_with_registration_async(
    registration: &Strong<dyn IRegistration>,
    key_blob: &[u8],
    upgraded_blob: &[u8],
) -> Result<()> {
    let (tx, rx) = oneshot::channel();
    let cb = StoreUpgradedKeyCallback::new_native_binder(tx);

    registration
        .storeUpgradedKeyAsync(key_blob, upgraded_blob, &cb)
        .context(source_location_msg!("Failed to store upgraded blob with RKPD."))?;

    match timeout(RKPD_TIMEOUT, rx).await {
        Err(e) => Err(Error::Timeout)
            .context(source_location_msg!("Waiting for RKPD to complete storing key: {:?}", e)),
        Ok(v) => v.unwrap(),
    }
}

async fn store_rkpd_attestation_key_async(
    rpc_name: &str,
    key_blob: &[u8],
    upgraded_blob: &[u8],
) -> Result<()> {
    let registration = get_rkpd_registration(rpc_name)
        .await
        .context(source_location_msg!("Trying to get to IRegistration service."))?;
    store_rkpd_attestation_key_with_registration_async(&registration, key_blob, upgraded_blob).await
}

/// Get attestation key from RKPD.
pub fn get_rkpd_attestation_key(rpc_name: &str, caller_uid: u32) -> Result<RemotelyProvisionedKey> {
    tokio_rt().block_on(get_rkpd_attestation_key_async(rpc_name, caller_uid))
}

/// Store attestation key in RKPD.
pub fn store_rkpd_attestation_key(
    rpc_name: &str,
    key_blob: &[u8],
    upgraded_blob: &[u8],
) -> Result<()> {
    tokio_rt().block_on(store_rkpd_attestation_key_async(rpc_name, key_blob, upgraded_blob))
}

#[cfg(test)]
mod tests {
    use super::*;
    use android_security_rkp_aidl::aidl::android::security::rkp::IRegistration::BnRegistration;
    use std::sync::atomic::{AtomicU32, Ordering};
    use std::sync::{Arc, Mutex};

    const DEFAULT_RPC_SERVICE_NAME: &str =
        "android.hardware.security.keymint.IRemotelyProvisionedComponent/default";

    struct MockRegistrationValues {
        key: RemotelyProvisionedKey,
        latency: Option<Duration>,
        thread_join_handles: Vec<Option<std::thread::JoinHandle<()>>>,
    }

    struct MockRegistration(Arc<Mutex<MockRegistrationValues>>);

    impl MockRegistration {
        pub fn new_native_binder(
            key: &RemotelyProvisionedKey,
            latency: Option<Duration>,
        ) -> Strong<dyn IRegistration> {
            let result = Self(Arc::new(Mutex::new(MockRegistrationValues {
                key: RemotelyProvisionedKey {
                    keyBlob: key.keyBlob.clone(),
                    encodedCertChain: key.encodedCertChain.clone(),
                },
                latency,
                thread_join_handles: Vec::new(),
            })));
            BnRegistration::new_binder(result, BinderFeatures::default())
        }
    }

    impl Drop for MockRegistration {
        fn drop(&mut self) {
            let mut values = self.0.lock().unwrap();
            for handle in values.thread_join_handles.iter_mut() {
                // These are test threads. So, no need to worry too much about error handling.
                handle.take().unwrap().join().unwrap();
            }
        }
    }

    impl Interface for MockRegistration {}

    impl IRegistration for MockRegistration {
        fn getKey(&self, _: i32, cb: &Strong<dyn IGetKeyCallback>) -> binder::Result<()> {
            let mut values = self.0.lock().unwrap();
            let key = RemotelyProvisionedKey {
                keyBlob: values.key.keyBlob.clone(),
                encodedCertChain: values.key.encodedCertChain.clone(),
            };
            let latency = values.latency;
            let get_key_cb = cb.clone();

            // Need a separate thread to trigger timeout in the caller.
            let join_handle = std::thread::spawn(move || {
                if let Some(duration) = latency {
                    std::thread::sleep(duration);
                }
                get_key_cb.onSuccess(&key).unwrap();
            });
            values.thread_join_handles.push(Some(join_handle));
            Ok(())
        }

        fn cancelGetKey(&self, _: &Strong<dyn IGetKeyCallback>) -> binder::Result<()> {
            Ok(())
        }

        fn storeUpgradedKeyAsync(
            &self,
            _: &[u8],
            _: &[u8],
            cb: &Strong<dyn IStoreUpgradedKeyCallback>,
        ) -> binder::Result<()> {
            // We are primarily concerned with timing out correctly. Storing the key in this mock
            // registration isn't particularly interesting, so skip that part.
            let values = self.0.lock().unwrap();
            let store_cb = cb.clone();
            let latency = values.latency;

            std::thread::spawn(move || {
                if let Some(duration) = latency {
                    std::thread::sleep(duration);
                }
                store_cb.onSuccess().unwrap();
            });
            Ok(())
        }
    }

    fn get_mock_registration(
        key: &RemotelyProvisionedKey,
        latency: Option<Duration>,
    ) -> Result<binder::Strong<dyn IRegistration>> {
        let (tx, rx) = oneshot::channel();
        let cb = GetRegistrationCallback::new_native_binder(tx);
        let mock_registration = MockRegistration::new_native_binder(key, latency);

        assert!(cb.onSuccess(&mock_registration).is_ok());
        tokio_rt().block_on(rx).unwrap()
    }

    // Using the same key ID makes test cases race with each other. So, we use separate key IDs for
    // different test cases.
    fn get_next_key_id() -> u32 {
        static ID: AtomicU32 = AtomicU32::new(0);
        ID.fetch_add(1, Ordering::Relaxed)
    }

    #[test]
    fn test_get_registration_cb_success() {
        let key: RemotelyProvisionedKey = Default::default();
        let registration = get_mock_registration(&key, /*latency=*/ None);
        assert!(registration.is_ok());
    }

    #[test]
    fn test_get_registration_cb_cancel() {
        let (tx, rx) = oneshot::channel();
        let cb = GetRegistrationCallback::new_native_binder(tx);
        assert!(cb.onCancel().is_ok());

        let result = tokio_rt().block_on(rx).unwrap();
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::RequestCancelled);
    }

    #[test]
    fn test_get_registration_cb_error() {
        let (tx, rx) = oneshot::channel();
        let cb = GetRegistrationCallback::new_native_binder(tx);
        assert!(cb.onError("error").is_ok());

        let result = tokio_rt().block_on(rx).unwrap();
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::GetRegistrationFailed);
    }

    #[test]
    fn test_get_key_cb_success() {
        let mock_key =
            RemotelyProvisionedKey { keyBlob: vec![1, 2, 3], encodedCertChain: vec![4, 5, 6] };
        let (tx, rx) = oneshot::channel();
        let cb = GetKeyCallback::new_native_binder(tx);
        assert!(cb.onSuccess(&mock_key).is_ok());

        let key = tokio_rt().block_on(rx).unwrap().unwrap();
        assert_eq!(key, mock_key);
    }

    #[test]
    fn test_get_key_cb_cancel() {
        let (tx, rx) = oneshot::channel();
        let cb = GetKeyCallback::new_native_binder(tx);
        assert!(cb.onCancel().is_ok());

        let result = tokio_rt().block_on(rx).unwrap();
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::RequestCancelled);
    }

    #[test]
    fn test_get_key_cb_error() {
        for get_key_error in GetKeyErrorCode::enum_values() {
            let (tx, rx) = oneshot::channel();
            let cb = GetKeyCallback::new_native_binder(tx);
            assert!(cb.onError(get_key_error, "error").is_ok());

            let result = tokio_rt().block_on(rx).unwrap();
            assert_eq!(
                result.unwrap_err().downcast::<Error>().unwrap(),
                Error::GetKeyFailed(get_key_error),
            );
        }
    }

    #[test]
    fn test_store_upgraded_cb_success() {
        let (tx, rx) = oneshot::channel();
        let cb = StoreUpgradedKeyCallback::new_native_binder(tx);
        assert!(cb.onSuccess().is_ok());

        tokio_rt().block_on(rx).unwrap().unwrap();
    }

    #[test]
    fn test_store_upgraded_key_cb_error() {
        let (tx, rx) = oneshot::channel();
        let cb = StoreUpgradedKeyCallback::new_native_binder(tx);
        assert!(cb.onError("oh no! it failed").is_ok());

        let result = tokio_rt().block_on(rx).unwrap();
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::StoreUpgradedKeyFailed);
    }

    #[test]
    fn test_get_mock_key_success() {
        let mock_key =
            RemotelyProvisionedKey { keyBlob: vec![1, 2, 3], encodedCertChain: vec![4, 5, 6] };
        let registration = get_mock_registration(&mock_key, /*latency=*/ None).unwrap();

        let key = tokio_rt()
            .block_on(get_rkpd_attestation_key_from_registration_async(&registration, 0))
            .unwrap();
        assert_eq!(key, mock_key);
    }

    #[test]
    fn test_get_mock_key_timeout() {
        let mock_key =
            RemotelyProvisionedKey { keyBlob: vec![1, 2, 3], encodedCertChain: vec![4, 5, 6] };
        let latency = RKPD_TIMEOUT + Duration::from_secs(1);
        let registration = get_mock_registration(&mock_key, Some(latency)).unwrap();

        let result =
            tokio_rt().block_on(get_rkpd_attestation_key_from_registration_async(&registration, 0));
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::RetryableTimeout);
    }

    #[test]
    fn test_store_mock_key_success() {
        let mock_key =
            RemotelyProvisionedKey { keyBlob: vec![1, 2, 3], encodedCertChain: vec![4, 5, 6] };
        let registration = get_mock_registration(&mock_key, /*latency=*/ None).unwrap();
        tokio_rt()
            .block_on(store_rkpd_attestation_key_with_registration_async(&registration, &[], &[]))
            .unwrap();
    }

    #[test]
    fn test_store_mock_key_timeout() {
        let mock_key =
            RemotelyProvisionedKey { keyBlob: vec![1, 2, 3], encodedCertChain: vec![4, 5, 6] };
        let latency = RKPD_TIMEOUT + Duration::from_secs(1);
        let registration = get_mock_registration(&mock_key, Some(latency)).unwrap();

        let result = tokio_rt().block_on(store_rkpd_attestation_key_with_registration_async(
            &registration,
            &[],
            &[],
        ));
        assert_eq!(result.unwrap_err().downcast::<Error>().unwrap(), Error::Timeout);
    }

    #[test]
    fn test_get_rkpd_attestation_key() {
        binder::ProcessState::start_thread_pool();
        let key_id = get_next_key_id();
        let key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();
        assert!(!key.keyBlob.is_empty());
        assert!(!key.encodedCertChain.is_empty());
    }

    #[test]
    fn test_get_rkpd_attestation_key_same_caller() {
        binder::ProcessState::start_thread_pool();
        let key_id = get_next_key_id();

        // Multiple calls should return the same key.
        let first_key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();
        let second_key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();

        assert_eq!(first_key.keyBlob, second_key.keyBlob);
        assert_eq!(first_key.encodedCertChain, second_key.encodedCertChain);
    }

    #[test]
    fn test_get_rkpd_attestation_key_different_caller() {
        binder::ProcessState::start_thread_pool();
        let first_key_id = get_next_key_id();
        let second_key_id = get_next_key_id();

        // Different callers should be getting different keys.
        let first_key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, first_key_id).unwrap();
        let second_key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, second_key_id).unwrap();

        assert_ne!(first_key.keyBlob, second_key.keyBlob);
        assert_ne!(first_key.encodedCertChain, second_key.encodedCertChain);
    }

    #[test]
    // Couple of things to note:
    // 1. This test must never run with UID of keystore. Otherwise, it can mess up keys stored by
    //    keystore.
    // 2. Storing and reading the stored key is prone to race condition. So, we only do this in one
    //    test case.
    fn test_store_rkpd_attestation_key() {
        binder::ProcessState::start_thread_pool();
        let key_id = get_next_key_id();
        let key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();
        let new_blob: [u8; 8] = rand::random();

        assert!(
            store_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, &key.keyBlob, &new_blob).is_ok()
        );

        let new_key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();

        // Restore original key so that we don't leave RKPD with invalid blobs.
        assert!(
            store_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, &new_blob, &key.keyBlob).is_ok()
        );
        assert_eq!(new_key.keyBlob, new_blob);
    }

    #[test]
    fn test_stress_get_rkpd_attestation_key() {
        binder::ProcessState::start_thread_pool();
        let key_id = get_next_key_id();
        let mut threads = vec![];
        const NTHREADS: u32 = 10;
        const NCALLS: u32 = 1000;

        for _ in 0..NTHREADS {
            threads.push(std::thread::spawn(move || {
                for _ in 0..NCALLS {
                    let key = get_rkpd_attestation_key(DEFAULT_RPC_SERVICE_NAME, key_id).unwrap();
                    assert!(!key.keyBlob.is_empty());
                    assert!(!key.encodedCertChain.is_empty());
                }
            }));
        }

        for t in threads {
            assert!(t.join().is_ok());
        }
    }
}
