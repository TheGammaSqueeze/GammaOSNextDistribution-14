use crate::bindings::root as bindings;
use crate::btif::{BluetoothInterface, BtStatus, RawAddress, SupportedProfiles, ToggleableProfile};
use crate::ccall;
use crate::profiles::hid_host::bindings::bthh_interface_t;
use crate::topstack::get_dispatchers;
use crate::utils::LTCheckedPtrMut;

use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::sync::{Arc, Mutex};
use topshim_macros::{cb_variant, profile_enabled_or};

use log::warn;

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthhConnectionState {
    Connected = 0,
    Connecting,
    Disconnected,
    Disconnecting,
    Unknown = 0xff,
}

impl From<bindings::bthh_connection_state_t> for BthhConnectionState {
    fn from(item: bindings::bthh_connection_state_t) -> Self {
        BthhConnectionState::from_u32(item).unwrap_or_else(|| BthhConnectionState::Unknown)
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthhStatus {
    Ok = 0,
    HsHidNotReady,
    HsInvalidRptId,
    HsTransNotSpt,
    HsInvalidParam,
    HsError,
    Error,
    ErrSdp,
    ErrProto,
    ErrDbFull,
    ErrTodUnspt,
    ErrNoRes,
    ErrAuthFailed,
    ErrHdl,

    Unknown,
}

impl From<bindings::bthh_status_t> for BthhStatus {
    fn from(item: bindings::bthh_status_t) -> Self {
        BthhStatus::from_u32(item).unwrap_or_else(|| BthhStatus::Unknown)
    }
}

pub type BthhHidInfo = bindings::bthh_hid_info_t;

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthhProtocolMode {
    ReportMode = 0,
    BootMode = 1,
    UnsupportedMode = 0xff,
}

impl From<bindings::bthh_protocol_mode_t> for BthhProtocolMode {
    fn from(item: bindings::bthh_protocol_mode_t) -> Self {
        BthhProtocolMode::from_u32(item).unwrap_or_else(|| BthhProtocolMode::UnsupportedMode)
    }
}

impl From<BthhProtocolMode> for bindings::bthh_protocol_mode_t {
    fn from(item: BthhProtocolMode) -> Self {
        item.to_u32().unwrap()
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthhReportType {
    InputReport = 1,
    OutputReport = 2,
    FeatureReport = 3,
}

impl From<BthhReportType> for bindings::bthh_report_type_t {
    fn from(item: BthhReportType) -> Self {
        item.to_u32().unwrap()
    }
}

fn convert_report(count: i32, raw: *mut u8) -> Vec<u8> {
    let mut v: Vec<u8> = Vec::new();
    for i in 0..isize::from_i32(count).unwrap() {
        let p: *const u8 = unsafe { raw.offset(i) };
        v.push(unsafe { *p });
    }

    return v;
}

#[derive(Debug)]
pub enum HHCallbacks {
    ConnectionState(RawAddress, BthhConnectionState),
    VirtualUnplug(RawAddress, BthhStatus),
    HidInfo(RawAddress, BthhHidInfo),
    ProtocolMode(RawAddress, BthhStatus, BthhProtocolMode),
    IdleTime(RawAddress, BthhStatus, i32),
    GetReport(RawAddress, BthhStatus, Vec<u8>, i32),
    Handshake(RawAddress, BthhStatus),
}

pub struct HHCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(HHCallbacks) + Send>,
}

type HHCb = Arc<Mutex<HHCallbacksDispatcher>>;

cb_variant!(HHCb, connection_state_cb -> HHCallbacks::ConnectionState,
*mut RawAddress, bindings::bthh_connection_state_t -> BthhConnectionState, {
    let _0 = unsafe { *_0 };
});
cb_variant!(HHCb, virtual_unplug_cb -> HHCallbacks::VirtualUnplug,
*mut RawAddress, bindings::bthh_status_t -> BthhStatus, {
    let _0 = unsafe { *_0 };
});
cb_variant!(HHCb, hid_info_cb -> HHCallbacks::HidInfo,
*mut RawAddress, bindings::bthh_hid_info_t -> BthhHidInfo, {
    let _0 = unsafe { *_0 };
});
cb_variant!(HHCb, protocol_mode_cb -> HHCallbacks::ProtocolMode,
*mut RawAddress, bindings::bthh_status_t -> BthhStatus,
bindings::bthh_protocol_mode_t -> BthhProtocolMode, {
    let _0 = unsafe { *_0 };
});
cb_variant!(HHCb, idle_time_cb -> HHCallbacks::IdleTime,
*mut RawAddress, bindings::bthh_status_t -> BthhStatus, i32, {
    let _0 = unsafe { *_0 };
});
cb_variant!(HHCb, get_report_cb -> HHCallbacks::GetReport,
*mut RawAddress, bindings::bthh_status_t -> BthhStatus, *mut u8, i32, {
    let _0 = unsafe { *_0 };
    let _2 = convert_report(_3, _2);
});
cb_variant!(HHCb, handshake_cb -> HHCallbacks::Handshake,
*mut RawAddress, bindings::bthh_status_t -> BthhStatus, {
    let _0 = unsafe { *_0 };
});

struct RawHHWrapper {
    raw: *const bindings::bthh_interface_t,
}

// Pointers unsafe due to ownership but this is a static pointer so Send is ok
unsafe impl Send for RawHHWrapper {}

pub struct HidHost {
    internal: RawHHWrapper,
    is_init: bool,
    _is_enabled: bool,
    pub is_hogp_activated: bool,
    pub is_hidp_activated: bool,
    pub is_profile_updated: bool,
    // Keep callback object in memory (underlying code doesn't make copy)
    callbacks: Option<Box<bindings::bthh_callbacks_t>>,
}

impl ToggleableProfile for HidHost {
    fn is_enabled(&self) -> bool {
        self._is_enabled
    }

    fn enable(&mut self) -> bool {
        let cb_ptr = LTCheckedPtrMut::from(self.callbacks.as_mut().unwrap());

        let init = ccall!(self, init, cb_ptr.into());
        self.is_init = BtStatus::from(init) == BtStatus::Success;
        self._is_enabled = self.is_init;
        true
    }

    #[profile_enabled_or(false)]
    fn disable(&mut self) -> bool {
        ccall!(self, cleanup);
        self._is_enabled = false;
        true
    }
}

impl HidHost {
    pub fn new(intf: &BluetoothInterface) -> HidHost {
        let r = intf.get_profile_interface(SupportedProfiles::HidHost);
        HidHost {
            internal: RawHHWrapper { raw: r as *const bthh_interface_t },
            is_init: false,
            _is_enabled: false,
            is_hogp_activated: false,
            is_hidp_activated: false,
            is_profile_updated: false,
            callbacks: None,
        }
    }

    pub fn is_initialized(&self) -> bool {
        self.is_init
    }

    pub fn initialize(&mut self, callbacks: HHCallbacksDispatcher) -> bool {
        // Register dispatcher
        if get_dispatchers().lock().unwrap().set::<HHCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for HHCallbacks but it already existed");
        }

        let callbacks = Box::new(bindings::bthh_callbacks_t {
            size: 8 * 8,
            connection_state_cb: Some(connection_state_cb),
            hid_info_cb: Some(hid_info_cb),
            protocol_mode_cb: Some(protocol_mode_cb),
            idle_time_cb: Some(idle_time_cb),
            get_report_cb: Some(get_report_cb),
            virtual_unplug_cb: Some(virtual_unplug_cb),
            handshake_cb: Some(handshake_cb),
        });

        self.callbacks = Some(callbacks);

        true
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn connect(&self, addr: &mut RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, connect, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn disconnect(&self, addr: &mut RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, disconnect, addr_ptr.into(), true))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn virtual_unplug(&self, addr: &mut RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, virtual_unplug, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn set_info(&self, addr: &mut RawAddress, info: BthhHidInfo) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, set_info, addr_ptr.into(), info))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn get_protocol(&self, addr: &mut RawAddress, mode: BthhProtocolMode) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(
            self,
            get_protocol,
            addr_ptr.into(),
            bindings::bthh_protocol_mode_t::from(mode)
        ))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn set_protocol(&self, addr: &mut RawAddress, mode: BthhProtocolMode) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(
            self,
            set_protocol,
            addr_ptr.into(),
            bindings::bthh_protocol_mode_t::from(mode)
        ))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn get_idle_time(&self, addr: &mut RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, get_idle_time, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn set_idle_time(&self, addr: &mut RawAddress, idle_time: u8) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(self, set_idle_time, addr_ptr.into(), idle_time))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn get_report(
        &self,
        addr: &mut RawAddress,
        report_type: BthhReportType,
        report_id: u8,
        buffer_size: i32,
    ) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        BtStatus::from(ccall!(
            self,
            get_report,
            addr_ptr.into(),
            bindings::bthh_report_type_t::from(report_type),
            report_id,
            buffer_size
        ))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn set_report(
        &self,
        addr: &mut RawAddress,
        report_type: BthhReportType,
        report: &mut [u8],
    ) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        let report_ptr = LTCheckedPtrMut::from(report);
        BtStatus::from(ccall!(
            self,
            set_report,
            addr_ptr.into(),
            bindings::bthh_report_type_t::from(report_type),
            report_ptr.cast_into::<std::os::raw::c_char>()
        ))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn send_data(&mut self, addr: &mut RawAddress, data: &mut [u8]) -> BtStatus {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        let data_ptr = LTCheckedPtrMut::from(data);
        BtStatus::from(ccall!(
            self,
            send_data,
            addr_ptr.into(),
            data_ptr.cast_into::<std::os::raw::c_char>()
        ))
    }

    /// return true if we need to restart hh
    pub fn configure_enabled_profiles(&mut self) -> bool {
        let needs_restart = self.is_profile_updated;
        if self.is_profile_updated {
            ccall!(
                self,
                configure_enabled_profiles,
                self.is_hidp_activated,
                self.is_hogp_activated
            );
            self.is_profile_updated = false;
        }
        needs_restart
    }

    pub fn activate_hogp(&mut self, active: bool) {
        if self.is_hogp_activated != active {
            self.is_hogp_activated = active;
            self.is_profile_updated = true;
        }
    }

    pub fn activate_hidp(&mut self, active: bool) {
        if self.is_hidp_activated != active {
            self.is_hidp_activated = active;
            self.is_profile_updated = true;
        }
    }
    #[profile_enabled_or]
    pub fn cleanup(&mut self) {
        ccall!(self, cleanup)
    }
}
