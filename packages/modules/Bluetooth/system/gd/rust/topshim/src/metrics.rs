use crate::btif::{
    BtAclState, BtBondState, BtConnectionDirection, BtDeviceType, BtHciErrorCode, BtState,
    BtStatus, BtTransport, RawAddress,
};

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
mod ffi {
    unsafe extern "C++" {
        include!("gd/rust/topshim/common/type_alias.h");
        type RawAddress = crate::btif::RawAddress;
    }

    unsafe extern "C++" {
        include!("metrics/metrics_shim.h");

        fn adapter_state_changed(state: u32);
        fn bond_create_attempt(bt_addr: RawAddress, device_type: u32);
        fn bond_state_changed(
            bt_addr: RawAddress,
            device_type: u32,
            status: u32,
            bond_state: u32,
            fail_reason: i32,
        );
        fn device_info_report(
            bt_addr: RawAddress,
            device_type: u32,
            class_of_device: u32,
            appearance: u32,
            vendor_id: u32,
            vendor_id_src: u32,
            product_id: u32,
            version: u32,
        );
        fn profile_connection_state_changed(
            bt_addr: RawAddress,
            profile: u32,
            status: u32,
            state: u32,
        );
        fn acl_connect_attempt(addr: RawAddress, acl_state: u32);
        fn acl_connection_state_changed(
            bt_addr: RawAddress,
            transport: u32,
            status: u32,
            acl_state: u32,
            direction: u32,
            hci_reason: u32,
        );
        fn suspend_complete_state(state: u32);
    }
}

pub fn adapter_state_changed(state: BtState) {
    ffi::adapter_state_changed(state as u32);
}

pub fn bond_create_attempt(addr: RawAddress, device_type: BtDeviceType) {
    ffi::bond_create_attempt(addr, device_type as u32);
}

pub fn bond_state_changed(
    addr: RawAddress,
    device_type: BtDeviceType,
    status: BtStatus,
    bond_state: BtBondState,
    fail_reason: i32,
) {
    ffi::bond_state_changed(
        addr,
        device_type as u32,
        status as u32,
        bond_state as u32,
        fail_reason as i32,
    );
}

pub fn device_info_report(
    addr: RawAddress,
    device_type: BtDeviceType,
    class_of_device: u32,
    appearance: u16,
    vendor_id: u16,
    vendor_id_src: u8,
    product_id: u16,
    version: u16,
) {
    ffi::device_info_report(
        addr,
        device_type as u32,
        class_of_device as u32,
        appearance as u32,
        vendor_id as u32,
        vendor_id_src as u32,
        product_id as u32,
        version as u32,
    );
}

pub fn profile_connection_state_changed(
    addr: RawAddress,
    profile: u32,
    status: BtStatus,
    state: u32,
) {
    ffi::profile_connection_state_changed(addr, profile, status as u32, state);
}

pub fn acl_connect_attempt(addr: RawAddress, acl_state: BtAclState) {
    ffi::acl_connect_attempt(addr, acl_state as u32);
}

pub fn acl_connection_state_changed(
    addr: RawAddress,
    transport: BtTransport,
    status: BtStatus,
    acl_state: BtAclState,
    direction: BtConnectionDirection,
    hci_reason: BtHciErrorCode,
) {
    ffi::acl_connection_state_changed(
        addr,
        transport as u32,
        status as u32,
        acl_state as u32,
        direction as u32,
        hci_reason as u32,
    );
}

pub fn suspend_complete_state(state: u32) {
    ffi::suspend_complete_state(state);
}
