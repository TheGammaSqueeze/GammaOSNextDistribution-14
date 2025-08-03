use crate::btif::{BluetoothInterface, BtStatus, RawAddress, ToggleableProfile};
use crate::topstack::get_dispatchers;

use bitflags::bitflags;
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::FromPrimitive;
use std::convert::{TryFrom, TryInto};
use std::sync::{Arc, Mutex};
use topshim_macros::{cb_variant, profile_enabled_or};

use log::warn;

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd, Clone)]
#[repr(u8)]
pub enum HfpCodecId {
    NONE = 0x00,
    CVSD = 0x01,
    MSBC = 0x02,
    LC3 = 0x03,
}

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd, Clone)]
#[repr(u8)]
pub enum EscoCodingFormat {
    ULAW = 0x00,
    ALAW = 0x01,
    CVSD = 0x02,
    TRANSPARENT = 0x03,
    LINEAR = 0x04,
    MSBC = 0x05,
    LC3 = 0x06,
    G729A = 0x07,
    VENDOR = 0xff,
}

impl From<u8> for EscoCodingFormat {
    fn from(item: u8) -> Self {
        EscoCodingFormat::from_u8(item).unwrap()
    }
}

impl From<EscoCodingFormat> for u8 {
    fn from(item: EscoCodingFormat) -> Self {
        item as u8
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd, Clone)]
#[repr(u32)]
pub enum BthfConnectionState {
    Disconnected = 0,
    Connecting,
    Connected,
    SlcConnected,
    Disconnecting,
}

impl From<u32> for BthfConnectionState {
    fn from(item: u32) -> Self {
        BthfConnectionState::from_u32(item).unwrap()
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd, Clone)]
#[repr(u32)]
pub enum BthfAudioState {
    Disconnected = 0,
    Connecting,
    Connected,
    Disconnecting,
}

impl From<u32> for BthfAudioState {
    fn from(item: u32) -> Self {
        BthfAudioState::from_u32(item).unwrap()
    }
}

// This is used for codec-negotiation related methods that do not
// concern with the coding format. Do not confuse this with |HfpCodecFormat|.
bitflags! {
    #[derive(Default)]
    pub struct HfpCodecBitId: i32 {
        const NONE = 0b000;
        const CVSD = 0b001;
        const MSBC = 0b010;
        const LC3 =  0b100;
    }
}

impl TryInto<u8> for HfpCodecBitId {
    type Error = ();
    fn try_into(self) -> Result<u8, Self::Error> {
        Ok(self.bits().try_into().unwrap())
    }
}

impl TryInto<i32> for HfpCodecBitId {
    type Error = ();
    fn try_into(self) -> Result<i32, Self::Error> {
        Ok(self.bits())
    }
}

impl TryFrom<i32> for HfpCodecBitId {
    type Error = ();
    fn try_from(val: i32) -> Result<Self, Self::Error> {
        Self::from_bits(val).ok_or(())
    }
}

bitflags! {
    #[derive(Default)]
    pub struct HfpCodecFormat: i32 {
        const NONE =             0b0000;
        const CVSD =             0b0001;
        const MSBC_TRANSPARENT = 0b0010;
        const MSBC =             0b0100;
        const LC3_TRANSPARENT =  0b1000;
    }
}

impl TryInto<i32> for HfpCodecFormat {
    type Error = ();
    fn try_into(self) -> Result<i32, Self::Error> {
        Ok(self.bits())
    }
}

impl TryFrom<i32> for HfpCodecFormat {
    type Error = ();
    fn try_from(val: i32) -> Result<Self, Self::Error> {
        Self::from_bits(val).ok_or(())
    }
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
pub mod ffi {
    unsafe extern "C++" {
        include!("gd/rust/topshim/common/type_alias.h");
        type RawAddress = crate::btif::RawAddress;
    }

    #[derive(Debug, Copy, Clone)]
    pub struct TelephonyDeviceStatus {
        network_available: bool,
        roaming: bool,
        signal_strength: i32,
        battery_level: i32,
    }

    #[derive(Debug, Copy, Clone)]
    pub enum CallState {
        Idle,
        Incoming,
        Dialing,
        Alerting,
        Active, // Only used by CLCC response
        Held,   // Only used by CLCC response
    }

    #[derive(Debug, Copy, Clone)]
    /*
    When a SCO is created, it is necessary to have at least one call in the call list.
    Otherwise, some headsets may not be able to output sound.

    Therefore, we need to separate the call for CRAS from the call for the application so that
    we can have a correct life cycle for the call list status.
    */
    pub enum CallSource {
        CRAS,
        HID,
    }

    #[derive(Debug, Clone)]
    pub struct CallInfo {
        index: i32,
        dir_incoming: bool,
        source: CallSource,
        state: CallState,
        number: String,
    }

    #[derive(Debug, Copy, Clone)]
    pub struct PhoneState {
        num_active: i32,
        num_held: i32,
        state: CallState,
    }

    #[derive(Debug, Copy, Clone)]
    pub enum CallHoldCommand {
        ReleaseHeld,
        ReleaseActiveAcceptHeld,
        HoldActiveAcceptHeld,
        // We don't support it in our telephony stack because it's not necessary for qualification.
        // But still inform the stack about this event.
        AddHeldToConf,
    }

    unsafe extern "C++" {
        include!("hfp/hfp_shim.h");

        type HfpIntf;

        unsafe fn GetHfpProfile(btif: *const u8) -> UniquePtr<HfpIntf>;
        unsafe fn interop_insert_call_when_sco_start(bt_addr: RawAddress) -> bool;
        fn init(self: Pin<&mut HfpIntf>) -> i32;
        fn connect(self: Pin<&mut HfpIntf>, bt_addr: RawAddress) -> u32;
        fn connect_audio(
            self: Pin<&mut HfpIntf>,
            bt_addr: RawAddress,
            sco_offload: bool,
            disabled_codecs: i32,
        ) -> i32;
        fn set_active_device(self: Pin<&mut HfpIntf>, bt_addr: RawAddress) -> i32;
        fn set_volume(self: Pin<&mut HfpIntf>, volume: i8, bt_addr: RawAddress) -> i32;
        fn set_mic_volume(self: Pin<&mut HfpIntf>, volume: i8, bt_addr: RawAddress) -> u32;
        fn disconnect(self: Pin<&mut HfpIntf>, bt_addr: RawAddress) -> u32;
        fn disconnect_audio(self: Pin<&mut HfpIntf>, bt_addr: RawAddress) -> i32;
        fn device_status_notification(
            self: Pin<&mut HfpIntf>,
            status: TelephonyDeviceStatus,
            addr: RawAddress,
        ) -> u32;
        fn indicator_query_response(
            self: Pin<&mut HfpIntf>,
            device_status: TelephonyDeviceStatus,
            phone_state: PhoneState,
            addr: RawAddress,
        ) -> u32;
        fn current_calls_query_response(
            self: Pin<&mut HfpIntf>,
            call_list: &Vec<CallInfo>,
            addr: RawAddress,
        ) -> u32;
        fn phone_state_change(
            self: Pin<&mut HfpIntf>,
            phone_state: PhoneState,
            number: &String,
            addr: RawAddress,
        ) -> u32;
        fn simple_at_response(self: Pin<&mut HfpIntf>, ok: bool, addr: RawAddress) -> u32;
        fn debug_dump(self: Pin<&mut HfpIntf>);
        fn cleanup(self: Pin<&mut HfpIntf>);

    }
    extern "Rust" {
        fn hfp_connection_state_callback(state: u32, addr: RawAddress);
        fn hfp_audio_state_callback(state: u32, addr: RawAddress);
        fn hfp_volume_update_callback(volume: u8, addr: RawAddress);
        fn hfp_mic_volume_update_callback(volume: u8, addr: RawAddress);
        fn hfp_vendor_specific_at_command_callback(at_string: String, addr: RawAddress);
        fn hfp_battery_level_update_callback(battery_level: u8, addr: RawAddress);
        fn hfp_wbs_caps_update_callback(wbs_supported: bool, addr: RawAddress);
        fn hfp_swb_caps_update_callback(swb_supported: bool, addr: RawAddress);
        fn hfp_indicator_query_callback(addr: RawAddress);
        fn hfp_current_calls_query_callback(addr: RawAddress);
        fn hfp_answer_call_callback(addr: RawAddress);
        fn hfp_hangup_call_callback(addr: RawAddress);
        fn hfp_dial_call_callback(number: String, addr: RawAddress);
        fn hfp_call_hold_callback(chld: CallHoldCommand, addr: RawAddress);
        fn hfp_debug_dump_callback(
            active: bool,
            codec_id: u16,
            total_num_decoded_frames: i32,
            pkt_loss_ratio: f64,
            begin_ts: u64,
            end_ts: u64,
            pkt_status_in_hex: String,
            pkt_status_in_binary: String,
        );
    }
}

pub fn interop_insert_call_when_sco_start(bt_addr: RawAddress) -> bool {
    //Call an unsafe function in c++. This is necessary for bridge C++ interop API with floss(rust).
    unsafe { return ffi::interop_insert_call_when_sco_start(bt_addr) }
}

pub type TelephonyDeviceStatus = ffi::TelephonyDeviceStatus;

impl TelephonyDeviceStatus {
    pub fn new() -> Self {
        TelephonyDeviceStatus {
            network_available: true,
            roaming: false,
            signal_strength: 5,
            battery_level: 5,
        }
    }
}

pub type CallState = ffi::CallState;
pub type CallSource = ffi::CallSource;
pub type CallInfo = ffi::CallInfo;
pub type PhoneState = ffi::PhoneState;
pub type CallHoldCommand = ffi::CallHoldCommand;

#[derive(Clone, Debug)]
pub enum HfpCallbacks {
    ConnectionState(BthfConnectionState, RawAddress),
    AudioState(BthfAudioState, RawAddress),
    VolumeUpdate(u8, RawAddress),
    MicVolumeUpdate(u8, RawAddress),
    VendorSpecificAtCommand(String, RawAddress),
    BatteryLevelUpdate(u8, RawAddress),
    WbsCapsUpdate(bool, RawAddress),
    SwbCapsUpdate(bool, RawAddress),
    IndicatorQuery(RawAddress),
    CurrentCallsQuery(RawAddress),
    AnswerCall(RawAddress),
    HangupCall(RawAddress),
    DialCall(String, RawAddress),
    CallHold(CallHoldCommand, RawAddress),
    DebugDump(bool, u16, i32, f64, u64, u64, String, String),
}

pub struct HfpCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(HfpCallbacks) + Send>,
}

type HfpCb = Arc<Mutex<HfpCallbacksDispatcher>>;

cb_variant!(
    HfpCb,
    hfp_connection_state_callback -> HfpCallbacks::ConnectionState,
    u32 -> BthfConnectionState, RawAddress);

cb_variant!(
    HfpCb,
    hfp_audio_state_callback -> HfpCallbacks::AudioState,
    u32 -> BthfAudioState, RawAddress);

cb_variant!(
    HfpCb,
    hfp_volume_update_callback -> HfpCallbacks::VolumeUpdate,
    u8, RawAddress);

cb_variant!(
    HfpCb,
    hfp_mic_volume_update_callback -> HfpCallbacks::MicVolumeUpdate,
    u8, RawAddress);

cb_variant!(
    HfpCb,
    hfp_vendor_specific_at_command_callback -> HfpCallbacks::VendorSpecificAtCommand,
    String, RawAddress);

cb_variant!(
    HfpCb,
    hfp_battery_level_update_callback -> HfpCallbacks::BatteryLevelUpdate,
    u8, RawAddress);

cb_variant!(
    HfpCb,
    hfp_wbs_caps_update_callback -> HfpCallbacks::WbsCapsUpdate,
    bool, RawAddress);

cb_variant!(
    HfpCb,
    hfp_swb_caps_update_callback -> HfpCallbacks::SwbCapsUpdate,
    bool, RawAddress);

cb_variant!(
    HfpCb,
    hfp_indicator_query_callback -> HfpCallbacks::IndicatorQuery,
    RawAddress);

cb_variant!(
    HfpCb,
    hfp_current_calls_query_callback -> HfpCallbacks::CurrentCallsQuery,
    RawAddress);

cb_variant!(
    HfpCb,
    hfp_answer_call_callback -> HfpCallbacks::AnswerCall,
    RawAddress);

cb_variant!(
    HfpCb,
    hfp_hangup_call_callback -> HfpCallbacks::HangupCall,
    RawAddress);

cb_variant!(
    HfpCb,
    hfp_dial_call_callback -> HfpCallbacks::DialCall,
    String, RawAddress);

cb_variant!(
    HfpCb,
    hfp_call_hold_callback -> HfpCallbacks::CallHold,
    CallHoldCommand, RawAddress);

cb_variant!(
    HfpCb,
    hfp_debug_dump_callback -> HfpCallbacks::DebugDump,
    bool, u16, i32, f64, u64, u64, String, String);

pub struct Hfp {
    internal: cxx::UniquePtr<ffi::HfpIntf>,
    _is_init: bool,
    _is_enabled: bool,
}

// For *const u8 opaque btif
unsafe impl Send for Hfp {}

impl ToggleableProfile for Hfp {
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

impl Hfp {
    pub fn new(intf: &BluetoothInterface) -> Hfp {
        let hfpif: cxx::UniquePtr<ffi::HfpIntf>;
        unsafe {
            hfpif = ffi::GetHfpProfile(intf.as_raw_ptr());
        }

        Hfp { internal: hfpif, _is_init: false, _is_enabled: false }
    }

    pub fn is_initialized(&self) -> bool {
        self._is_init
    }

    pub fn initialize(&mut self, callbacks: HfpCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<HfpCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for HFP callbacks while it already exists");
        }
        self._is_init = true;
        true
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn connect(&mut self, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().connect(addr))
    }

    #[profile_enabled_or(BtStatus::NotReady.into())]
    pub fn connect_audio(
        &mut self,
        addr: RawAddress,
        sco_offload: bool,
        disabled_codecs: i32,
    ) -> i32 {
        self.internal.pin_mut().connect_audio(addr, sco_offload, disabled_codecs)
    }

    #[profile_enabled_or(BtStatus::NotReady.into())]
    pub fn set_active_device(&mut self, addr: RawAddress) -> i32 {
        self.internal.pin_mut().set_active_device(addr)
    }

    #[profile_enabled_or(BtStatus::NotReady.into())]
    pub fn set_volume(&mut self, volume: i8, addr: RawAddress) -> i32 {
        self.internal.pin_mut().set_volume(volume, addr)
    }

    #[profile_enabled_or(BtStatus::NotReady.into())]
    pub fn set_mic_volume(&mut self, volume: i8, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().set_mic_volume(volume, addr))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn disconnect(&mut self, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().disconnect(addr))
    }

    #[profile_enabled_or(BtStatus::NotReady.into())]
    pub fn disconnect_audio(&mut self, addr: RawAddress) -> i32 {
        self.internal.pin_mut().disconnect_audio(addr)
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn device_status_notification(
        &mut self,
        status: TelephonyDeviceStatus,
        addr: RawAddress,
    ) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().device_status_notification(status, addr))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn indicator_query_response(
        &mut self,
        device_status: TelephonyDeviceStatus,
        phone_state: PhoneState,
        addr: RawAddress,
    ) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().indicator_query_response(
            device_status,
            phone_state,
            addr,
        ))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn current_calls_query_response(
        &mut self,
        call_list: &Vec<CallInfo>,
        addr: RawAddress,
    ) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().current_calls_query_response(call_list, addr))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn phone_state_change(
        &mut self,
        phone_state: PhoneState,
        number: &String,
        addr: RawAddress,
    ) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().phone_state_change(phone_state, number, addr))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn simple_at_response(&mut self, ok: bool, addr: RawAddress) -> BtStatus {
        BtStatus::from(self.internal.pin_mut().simple_at_response(ok, addr))
    }

    #[profile_enabled_or]
    pub fn debug_dump(&mut self) {
        self.internal.pin_mut().debug_dump();
    }

    #[profile_enabled_or(false)]
    pub fn cleanup(&mut self) -> bool {
        self.internal.pin_mut().cleanup();
        true
    }
}
