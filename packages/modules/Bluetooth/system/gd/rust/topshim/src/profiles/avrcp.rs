use crate::btif::{BluetoothInterface, BtStatus, RawAddress, SupportedProfiles, ToggleableProfile};
use crate::topstack::get_dispatchers;

use std::sync::{Arc, Mutex};
use topshim_macros::{cb_variant, profile_enabled_or};

use crate::bindings::root as bindings;
use crate::ccall;
use crate::utils::LTCheckedPtrMut;

use log::warn;

#[derive(Debug, Default)]
pub struct PlayerMetadata {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub length_us: i64,
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
pub mod ffi {
    unsafe extern "C++" {
        include!("gd/rust/topshim/common/type_alias.h");
        type RawAddress = crate::btif::RawAddress;
    }

    unsafe extern "C++" {
        include!("btav/btav_shim.h");
        include!("btav_sink/btav_sink_shim.h");

        type AvrcpIntf;

        unsafe fn GetAvrcpProfile(btif: *const u8) -> UniquePtr<AvrcpIntf>;

        fn init(self: Pin<&mut AvrcpIntf>);
        fn cleanup(self: Pin<&mut AvrcpIntf>);
        fn connect(self: Pin<&mut AvrcpIntf>, bt_addr: RawAddress) -> u32;
        fn disconnect(self: Pin<&mut AvrcpIntf>, bt_addr: RawAddress) -> u32;
        fn set_volume(self: Pin<&mut AvrcpIntf>, volume: i8);
        fn set_playback_status(self: Pin<&mut AvrcpIntf>, status: &String);
        fn set_position(self: Pin<&mut AvrcpIntf>, position_us: i64);
        fn set_metadata(
            self: Pin<&mut AvrcpIntf>,
            title: &String,
            artist: &String,
            album: &String,
            length_us: i64,
        );
        fn add_player(self: Pin<&mut AvrcpIntf>, name: &String, browsing_supported: bool) -> u16;

    }
    extern "Rust" {
        fn avrcp_device_connected(addr: RawAddress, absolute_volume_enabled: bool);
        fn avrcp_device_disconnected(addr: RawAddress);
        fn avrcp_absolute_volume_update(volume: u8);
        fn avrcp_send_key_event(key: u8, state: u8);
        fn avrcp_set_active_device(addr: RawAddress);
    }
}

#[derive(Debug)]
pub enum AvrcpCallbacks {
    /// Emitted when avrcp completes connection.
    /// Params: Device address, Absolute Volume Enabled
    AvrcpDeviceConnected(RawAddress, bool),
    /// Emitted when avrcp device disconnected.
    /// Params: Device address
    AvrcpDeviceDisconnected(RawAddress),
    /// Emitted when the absolute volume of a connected AVRCP device changed
    /// Params: Volume
    AvrcpAbsoluteVolumeUpdate(u8),
    /// Emitted when received a key event from a connected AVRCP device
    /// Params: Key, Value
    AvrcpSendKeyEvent(u8, u8),
    /// Emitted when received request from AVRCP interface to set a device to active
    /// Params: Device address
    AvrcpSetActiveDevice(RawAddress),
}

pub struct AvrcpCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(AvrcpCallbacks) + Send>,
}

type AvrcpCb = Arc<Mutex<AvrcpCallbacksDispatcher>>;

cb_variant!(
    AvrcpCb,
    avrcp_device_connected -> AvrcpCallbacks::AvrcpDeviceConnected,
    RawAddress, bool);

cb_variant!(
    AvrcpCb,
    avrcp_device_disconnected -> AvrcpCallbacks::AvrcpDeviceDisconnected,
    RawAddress);

cb_variant!(
    AvrcpCb,
    avrcp_absolute_volume_update -> AvrcpCallbacks::AvrcpAbsoluteVolumeUpdate,
    u8, {}
);

cb_variant!(
    AvrcpCb,
    avrcp_send_key_event -> AvrcpCallbacks::AvrcpSendKeyEvent,
    u8, u8, {}
);

cb_variant!(
    AvrcpCb,
    avrcp_set_active_device -> AvrcpCallbacks::AvrcpSetActiveDevice,
    RawAddress);

pub struct Avrcp {
    internal: cxx::UniquePtr<ffi::AvrcpIntf>,
    _is_init: bool,
    _is_enabled: bool,
}

// For *const u8 opaque btif
unsafe impl Send for Avrcp {}

impl ToggleableProfile for Avrcp {
    fn is_enabled(&self) -> bool {
        self._is_enabled
    }

    fn enable(&mut self) -> bool {
        self.internal.pin_mut().init();
        self._is_enabled = true;
        true
    }

    #[profile_enabled_or(false)]
    fn disable(&mut self) -> bool {
        self.internal.pin_mut().cleanup();
        self._is_enabled = false;
        true
    }
}

impl Avrcp {
    pub fn new(intf: &BluetoothInterface) -> Avrcp {
        let avrcpif: cxx::UniquePtr<ffi::AvrcpIntf>;
        unsafe {
            avrcpif = ffi::GetAvrcpProfile(intf.as_raw_ptr());
        }

        Avrcp { internal: avrcpif, _is_init: false, _is_enabled: false }
    }

    pub fn is_initialized(&self) -> bool {
        self._is_init
    }

    pub fn initialize(&mut self, callbacks: AvrcpCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<AvrcpCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for Avrcp callbacks while it already exists");
        }
        self._is_init = true;
        true
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn connect(&mut self, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().connect(addr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn disconnect(&mut self, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().disconnect(addr.into()))
    }

    #[profile_enabled_or]
    pub fn set_volume(&mut self, volume: i8) {
        self.internal.pin_mut().set_volume(volume);
    }

    #[profile_enabled_or(false)]
    pub fn cleanup(&mut self) -> bool {
        self.internal.pin_mut().cleanup();
        true
    }

    #[profile_enabled_or]
    pub fn set_playback_status(&mut self, status: &String) {
        self.internal.pin_mut().set_playback_status(status);
    }

    #[profile_enabled_or]
    pub fn set_position(&mut self, position_us: i64) {
        self.internal.pin_mut().set_position(position_us);
    }

    #[profile_enabled_or]
    pub fn set_metadata(&mut self, metadata: &PlayerMetadata) {
        self.internal.pin_mut().set_metadata(
            &metadata.title,
            &metadata.artist,
            &metadata.album,
            metadata.length_us,
        );
    }

    #[profile_enabled_or]
    pub fn add_player(&mut self, name: &String, browsing_supported: bool) {
        self.internal.pin_mut().add_player(name, browsing_supported);
    }
}

struct RawAvrcpCtrlWrapper {
    raw: *const bindings::btrc_ctrl_interface_t,
}

// Pointers unsafe due to ownership but this is a static pointer so Send is ok.
unsafe impl Send for RawAvrcpCtrlWrapper {}

pub struct AvrcpCtrl {
    internal: RawAvrcpCtrlWrapper,
    callbacks: Option<Box<bindings::btrc_ctrl_callbacks_t>>,
}

cb_variant!(AvrcpCtCb, avrcp_connection_state_cb -> AvrcpCtrlCallbacks::ConnectionState,
bool, bool, *const RawAddress, {
    let _2 = unsafe { *_2 };
});

cb_variant!(AvrcpCtCb, avrcp_getrcfeatures_cb -> AvrcpCtrlCallbacks::GetRCFeatures,
*const RawAddress, i32, {
    let _0 = unsafe { *_0 };
});

cb_variant!(AvrcpCtCb, avrcp_get_cover_art_psm_cb -> AvrcpCtrlCallbacks::GetCoverArtPsm,
*const RawAddress, u16, {
    let _0 = unsafe { *_0 };
});

cb_variant!(AvrcpCtCb, avrcp_passthrough_rsp_cb -> AvrcpCtrlCallbacks::PassthroughRsp,
*const RawAddress, i32, i32, {
    let _0 = unsafe { *_0 };
});

#[derive(Debug)]
pub enum AvrcpCtrlCallbacks {
    PassthroughRsp(RawAddress, i32, i32),
    ConnectionState(bool, bool, RawAddress),
    GetRCFeatures(RawAddress, i32),
    GetCoverArtPsm(RawAddress, u16),
}

pub struct AvrcpCtrlCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(AvrcpCtrlCallbacks) + Send>,
}

type AvrcpCtCb = Arc<Mutex<AvrcpCtrlCallbacksDispatcher>>;

impl AvrcpCtrl {
    pub fn new(intf: &BluetoothInterface) -> AvrcpCtrl {
        let r = intf.get_profile_interface(SupportedProfiles::AvrcpCtrl);
        AvrcpCtrl {
            internal: RawAvrcpCtrlWrapper { raw: r as *const bindings::btrc_ctrl_interface_t },
            callbacks: None,
        }
    }

    pub fn initialize(&mut self, callbacks: AvrcpCtrlCallbacksDispatcher) -> bool {
        // Register dispatcher
        if get_dispatchers().lock().unwrap().set::<AvrcpCtCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for AvrcpCt Callbacks but it already existed");
        }

        let callbacks = Box::new(bindings::btrc_ctrl_callbacks_t {
            size: 21 * 8,
            passthrough_rsp_cb: Some(avrcp_passthrough_rsp_cb),
            groupnavigation_rsp_cb: None,
            connection_state_cb: Some(avrcp_connection_state_cb),
            getrcfeatures_cb: Some(avrcp_getrcfeatures_cb),
            setplayerappsetting_rsp_cb: None,
            playerapplicationsetting_cb: None,
            playerapplicationsetting_changed_cb: None,
            setabsvol_cmd_cb: None,
            registernotification_absvol_cb: None,
            track_changed_cb: None,
            play_position_changed_cb: None,
            play_status_changed_cb: None,
            get_folder_items_cb: None,
            change_folder_path_cb: None,
            set_browsed_player_cb: None,
            set_addressed_player_cb: None,
            addressed_player_changed_cb: None,
            now_playing_contents_changed_cb: None,
            available_player_changed_cb: None,
            get_cover_art_psm_cb: Some(avrcp_get_cover_art_psm_cb),
        });

        self.callbacks = Some(callbacks);

        let cb_ptr = LTCheckedPtrMut::from(self.callbacks.as_mut().unwrap());

        ccall!(self, init, cb_ptr.into());

        true
    }

    pub fn send_pass_through_cmd(
        &mut self,
        addr: RawAddress,
        key_code: u8,
        key_state: u8,
    ) -> BtStatus {
        ccall!(self, send_pass_through_cmd, &addr, key_code.into(), key_state.into()).into()
    }
}
