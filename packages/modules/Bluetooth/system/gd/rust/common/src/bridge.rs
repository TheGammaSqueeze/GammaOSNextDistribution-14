//! Merged bridge

pub use crate::parameter_provider::*;

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "bluetooth::fake_bluetooth_keystore")]
/// ffi extern module
pub mod ffi {
    extern "Rust" {
        // ParameterProvider
        type ParameterProvider;
    }

    unsafe extern "C++" {
        include!("keystore/fake_bt_keystore.h");

        /// BluetoothKeystoreInterface
        type BluetoothKeystoreInterface;
        /// Construct a new BluetoothKeystoreInterface
        fn new_bt_keystore_interface() -> UniquePtr<BluetoothKeystoreInterface>;
    }

    impl UniquePtr<BluetoothKeystoreInterface> {}
}
