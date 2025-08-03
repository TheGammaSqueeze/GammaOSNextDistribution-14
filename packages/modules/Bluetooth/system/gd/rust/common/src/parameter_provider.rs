use crate::bridge::ffi::BluetoothKeystoreInterface;
use crate::init_flags::AID_BLUETOOTH;
use cxx::UniquePtr;
use nix::unistd::getuid;
use std::marker::Send;
use std::ptr::null_mut;
use std::string::String;
use std::sync::Mutex;

/// A single ParameterProvider
pub struct ParameterProvider {
    configuration_file_path: String,
    btsnoop_log_file_path: String,
    btsnooz_log_file_path: String,
    common_criteria_mode: bool,
    common_criteria_config_compare_result: i32,
    bt_keystore_interface: *mut BluetoothKeystoreInterface,
    lock: Mutex<i32>,
}

// SAFETY: Nothing about `BluetoothKeystoreInterface` is bound to a specific thread, and all other
// fields are `Send`.
unsafe impl Send for ParameterProvider {}

// SAFETY: Nothing about `BluetoothKeystoreInterface` is bound to a specific thread.
unsafe impl Send for BluetoothKeystoreInterface {}

impl ParameterProvider {
    /// Construct a new ParameterProvider
    pub fn new(prefix: String) -> Self {
        Self {
            configuration_file_path: prefix.clone() + "bluedroid/bt_config.conf",
            btsnoop_log_file_path: prefix.clone() + "bluetooth/logs/btsnoop_hci.log",
            btsnooz_log_file_path: prefix + "bluetooth/logs/btsnooz_hci.log",
            common_criteria_mode: false,
            common_criteria_config_compare_result: 0b11,
            bt_keystore_interface: null_mut(),
            lock: Mutex::new(0),
        }
    }

    /// Get the config file path
    pub async fn config_file_path(&self) -> String {
        let guard = self.lock.lock().unwrap();
        let path = &self.configuration_file_path;
        drop(guard);
        path.to_string()
    }

    /// Set the config file path
    pub async fn override_config_file_path(&mut self, path: &str) {
        let guard = self.lock.lock().unwrap();
        self.configuration_file_path = path.to_string();
        drop(guard);
    }

    /// Get the snoop file path
    pub async fn snoop_log_file_path(&mut self) -> String {
        let guard = self.lock.lock().unwrap();
        let path = &self.btsnoop_log_file_path;
        drop(guard);
        path.to_string()
    }

    /// Set the snoop file path
    pub async fn override_snoop_file_path(&mut self, path: &str) {
        let guard = self.lock.lock().unwrap();
        self.btsnoop_log_file_path = path.to_string();
        drop(guard);
    }

    /// Get the snooz log file path
    pub async fn snooz_log_file_path(&mut self) -> String {
        let guard = self.lock.lock().unwrap();
        let path = &self.btsnooz_log_file_path;
        drop(guard);
        path.to_string()
    }

    /// Set the snooz file path
    pub async fn override_snooz_file_path(&mut self, path: &str) {
        let guard = self.lock.lock().unwrap();
        self.btsnooz_log_file_path = path.to_string();
        drop(guard);
    }

    /// Get the bluetooth keystore interface
    pub async fn get_bt_keystore_interface(&mut self) -> *mut BluetoothKeystoreInterface {
        let guard = self.lock.lock().unwrap();
        let result = &self.bt_keystore_interface;
        drop(guard);
        *result
    }

    /// Set the bluetooth keystore interface
    pub async fn set_bt_keystore_interface(
        &mut self,
        by_keystore: UniquePtr<BluetoothKeystoreInterface>,
    ) {
        let guard = self.lock.lock().unwrap();
        self.bt_keystore_interface = by_keystore.into_raw();
        drop(guard);
    }

    /// Get the common criteria mode
    pub async fn is_common_criteria_mode(&self) -> bool {
        let guard = self.lock.lock().unwrap();
        let enable = &self.common_criteria_mode;
        drop(guard);
        return (getuid().as_raw() == *(*AID_BLUETOOTH).lock().unwrap()) && *enable;
    }

    /// Set the common criteria mode
    pub async fn set_common_criteria_mode(&mut self, enable: bool) {
        let guard = self.lock.lock().unwrap();
        self.common_criteria_mode = enable;
        drop(guard);
    }

    /// Get the common criteria config compare result
    pub async fn get_common_criteria_config_compare_result(&self) -> i32 {
        let guard = self.lock.lock().unwrap();
        let result = &self.common_criteria_config_compare_result;
        drop(guard);
        *result
    }

    /// Set the common criteria config compare result
    pub async fn set_common_criteria_config_compare_result(&mut self, result: i32) {
        let guard = self.lock.lock().unwrap();
        self.common_criteria_config_compare_result = result;
        drop(guard);
    }
}

#[cfg(test)]
mod tests {
    use crate::bridge::ffi;
    use crate::bridge::ffi::BluetoothKeystoreInterface;
    use crate::bridge::ParameterProvider;
    use crate::init_flags::AID_BLUETOOTH;
    use crate::init_flags::MISC;
    use nix::unistd::getuid;
    use std::assert_eq;
    use std::i64;
    use std::ptr::null_mut;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;
    use tokio::sync::Mutex;

    #[tokio::test]
    async fn test_config_file_path() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: String = (*param_provider.lock().await).config_file_path().await;
        assert_eq!(origin, "/data/misc/bluedroid/bt_config.conf");
        let choice = Arc::new(Mutex::new("0"));

        for i in 1..3 {
            let select = Arc::clone(&choice);
            let param_provider_cur = Arc::clone(&param_provider);

            tokio::spawn(async move {
                let mut c = select.lock().await;
                if i == 1 {
                    *c = "1";
                    param_provider_cur.lock().await.override_config_file_path(&c.to_string()).await;
                } else {
                    *c = "2";
                    param_provider_cur.lock().await.override_config_file_path(&c.to_string()).await;
                }
            })
            .await
            .unwrap();
        }

        let current: String = (*param_provider.lock().await).config_file_path().await;
        assert_eq!(*choice.lock().await, &current);
        (*param_provider.lock().await).override_config_file_path(&origin).await;
        let now: String = (*param_provider.lock().await).config_file_path().await;
        assert_eq!(now, "/data/misc/bluedroid/bt_config.conf");
    }

    #[tokio::test]
    async fn test_snoop_log_file_path() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: String = (*param_provider.lock().await).snoop_log_file_path().await;
        assert_eq!(origin, "/data/misc/bluetooth/logs/btsnoop_hci.log");
        let choice = Arc::new(Mutex::new("0"));

        for i in 1..3 {
            let select = Arc::clone(&choice);
            let param_provider_cur = Arc::clone(&param_provider);

            tokio::spawn(async move {
                let mut c = select.lock().await;
                if i == 1 {
                    *c = "1";
                    param_provider_cur.lock().await.override_snoop_file_path(&c.to_string()).await;
                } else {
                    *c = "2";
                    param_provider_cur.lock().await.override_snoop_file_path(&c.to_string()).await;
                }
            })
            .await
            .unwrap();
        }

        let current: String = (*param_provider.lock().await).snoop_log_file_path().await;
        assert_eq!(*choice.lock().await, &current);
        (*param_provider.lock().await).override_snoop_file_path(&origin).await;
        let now: String = (*param_provider.lock().await).snoop_log_file_path().await;
        assert_eq!(now, "/data/misc/bluetooth/logs/btsnoop_hci.log");
    }

    #[tokio::test]
    async fn test_snooz_log_file_path() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: String = (*param_provider.lock().await).snooz_log_file_path().await;
        assert_eq!(origin, "/data/misc/bluetooth/logs/btsnooz_hci.log");
        let choice = Arc::new(Mutex::new("0"));

        for i in 1..3 {
            let select = Arc::clone(&choice);
            let param_provider_cur = Arc::clone(&param_provider);

            tokio::spawn(async move {
                let mut c = select.lock().await;
                if i == 1 {
                    *c = "1";
                    param_provider_cur.lock().await.override_snooz_file_path(&c.to_string()).await;
                } else {
                    *c = "2";
                    param_provider_cur.lock().await.override_snooz_file_path(&c.to_string()).await;
                }
            })
            .await
            .unwrap();
        }

        let current: String = (*param_provider.lock().await).snooz_log_file_path().await;
        assert_eq!(*choice.lock().await, &current);
        (*param_provider.lock().await).override_snooz_file_path(&origin).await;
        let now: String = (*param_provider.lock().await).snooz_log_file_path().await;
        assert_eq!(now, "/data/misc/bluetooth/logs/btsnooz_hci.log");
    }

    // TODO(b/290018030): Remove this and add proper safety comments.
    #[allow(clippy::undocumented_unsafe_blocks)]
    #[ignore]
    #[tokio::test]
    async fn test_bt_keystore_interface() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: *mut BluetoothKeystoreInterface =
            (*param_provider.lock().await).get_bt_keystore_interface().await;
        assert_eq!(origin, null_mut());

        let choice_ptr1: cxx::UniquePtr<ffi::BluetoothKeystoreInterface> =
            ffi::new_bt_keystore_interface();
        let address1 = Arc::new(Mutex::new(format!("{:p}", &choice_ptr1)));

        let choice_ptr2: cxx::UniquePtr<ffi::BluetoothKeystoreInterface> =
            ffi::new_bt_keystore_interface();
        let address2 = Arc::new(Mutex::new(format!("{:p}", &choice_ptr2)));

        let choice = Arc::new(Mutex::new("0"));

        for i in 1..3 {
            let select = Arc::clone(&choice);
            let param_provider_cur = Arc::clone(&param_provider);
            let addr1 = Arc::clone(&address1);
            let addr2 = Arc::clone(&address2);

            tokio::spawn(async move {
                let mut c = select.lock().await;
                let mut a1 = addr1.lock().await;
                let mut a2 = addr2.lock().await;

                if i == 1 {
                    let ptr1: cxx::UniquePtr<ffi::BluetoothKeystoreInterface> =
                        ffi::new_bt_keystore_interface();
                    *c = "1";
                    *a1 = format!("{:p}", &ptr1);
                    param_provider_cur.lock().await.set_bt_keystore_interface(ptr1).await;
                } else {
                    let ptr2: cxx::UniquePtr<ffi::BluetoothKeystoreInterface> =
                        ffi::new_bt_keystore_interface();
                    *c = "2";
                    *a2 = format!("{:p}", &ptr2);
                    param_provider_cur.lock().await.set_bt_keystore_interface(ptr2).await;
                }
            })
            .await
            .unwrap();
        }

        let current = (*param_provider.lock().await).get_bt_keystore_interface().await;
        let reality = current as i64;
        // TODO: Fix unsoundness. `address1` contains a string representation of a pointer to a
        // `cxx::UniquePtr` on the stack; either `choice_ptr1` (which is still in scope, so it is
        // valid) or to `ptr1` (which is in another task which may have finished, so may not be
        // valid). If it were valid, then transmuting the `cxx::UniquePtr` to an `i64` is probably
        // fine, though a dubious thing to do.
        let mut answer = unsafe { *(hex_to_dec(&address1.lock().await) as *const i64) };
        if *choice.lock().await == "2" {
            // TODO: Fix unsoundness. `address2` contains a string representation of a pointer to a
            // `cxx::UniquePtr` on the stack; either `choice_ptr2` (which is still in scope, so it
            // is valid) or to `ptr2` (which is in another task which may have finished, so may not
            // be valid). If it were valid, then transmuting the `cxx::UniquePtr` to an `i64` is
            // probably fine, though a dubious thing to do.
            answer = unsafe { *(hex_to_dec(&address2.lock().await) as *const i64) };
        }
        assert_eq!(reality, answer);
    }

    #[tokio::test]
    async fn test_common_criteria_mode() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: bool = (*param_provider.lock().await).is_common_criteria_mode().await;
        assert!(!origin);

        let choice = Arc::new(AtomicBool::new(false));

        let init_bt: u32 = *(*AID_BLUETOOTH).lock().unwrap();
        *(*AID_BLUETOOTH).lock().unwrap() = getuid().as_raw();

        for i in 1..3 {
            let select = Arc::clone(&choice);
            let param_provider_cur = Arc::clone(&param_provider);

            tokio::spawn(async move {
                if i == 1 {
                    select.store(true, Ordering::Relaxed);
                    param_provider_cur.lock().await.set_common_criteria_mode(true).await;
                } else {
                    select.store(false, Ordering::Relaxed);
                    param_provider_cur.lock().await.set_common_criteria_mode(false).await;
                }
            })
            .await
            .unwrap();
        }

        let current: bool = (*param_provider.lock().await).is_common_criteria_mode().await;
        assert_eq!(choice.load(Ordering::SeqCst), current);
        (*param_provider.lock().await).set_common_criteria_mode(origin).await;
        let now: bool = (*param_provider.lock().await).is_common_criteria_mode().await;
        assert!(!now);
        *(*AID_BLUETOOTH).lock().unwrap() = init_bt;
    }

    #[tokio::test]
    async fn test_common_criteria_config_compare_result() {
        let prefix: String = (*(*MISC).lock().unwrap()).clone();
        let param_provider = Arc::new(Mutex::new(ParameterProvider::new(prefix)));
        let origin: i32 =
            (*param_provider.lock().await).get_common_criteria_config_compare_result().await;
        assert_eq!(origin, 0b11);
        let counter = Arc::new(Mutex::new(0));

        for i in 1..3 {
            let select = Arc::clone(&counter);
            let param_provider_cur = Arc::clone(&param_provider);

            tokio::spawn(async move {
                let mut c = select.lock().await;
                if i == 1 {
                    *c = 1;
                    param_provider_cur
                        .lock()
                        .await
                        .set_common_criteria_config_compare_result(*c)
                        .await;
                } else {
                    *c = 2;
                    param_provider_cur
                        .lock()
                        .await
                        .set_common_criteria_config_compare_result(*c)
                        .await;
                }
            })
            .await
            .unwrap();
        }

        let current: i32 =
            (*param_provider.lock().await).get_common_criteria_config_compare_result().await;
        assert_eq!(*counter.lock().await, current);
        (*param_provider.lock().await).set_common_criteria_config_compare_result(origin).await;
        let now: i32 =
            (*param_provider.lock().await).get_common_criteria_config_compare_result().await;
        assert_eq!(now, 0b11);
    }

    /// Converts a hex string to an integer
    fn hex_to_dec(origin: &str) -> usize {
        let origin = origin.trim_start_matches("0x");
        usize::from_str_radix(origin, 16).unwrap()
    }
}
