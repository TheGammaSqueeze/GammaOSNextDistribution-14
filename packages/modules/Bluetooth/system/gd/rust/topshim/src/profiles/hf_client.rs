use crate::bindings::root as bindings;
use crate::btif::{BluetoothInterface, BtStatus, RawAddress, SupportedProfiles, ToggleableProfile};
use crate::ccall;
use crate::profiles::hf_client::bindings::bthf_client_interface_t;
use crate::topstack::get_dispatchers;
use crate::utils::{LTCheckedPtr, LTCheckedPtrMut};

use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::FromPrimitive;
use std::sync::{Arc, Mutex};
use topshim_macros::{cb_variant, profile_enabled_or};

use log::warn;

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
/// Represents the various connection states a Hands-Free client would go through.
pub enum BthfClientConnectionState {
    Disconnected = 0,
    Connecting,
    Connected,
    SlcConnected,
    Disconnecting,
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
/// Represents the various connection states the audio channel for a
/// Hands-Free client would go through.
pub enum BthfClientAudioState {
    Disconnected = 0,
    Connecting,
    Connected,
    Disconnecting,
}

impl From<bindings::bthf_client_connection_state_t> for BthfClientConnectionState {
    fn from(item: bindings::bthf_client_connection_state_t) -> Self {
        BthfClientConnectionState::from_u32(item).unwrap_or(BthfClientConnectionState::Disconnected)
    }
}

impl From<bindings::bthf_client_audio_state_t> for BthfClientAudioState {
    fn from(item: bindings::bthf_client_audio_state_t) -> Self {
        BthfClientAudioState::from_u32(item).unwrap_or(BthfClientAudioState::Disconnected)
    }
}

#[derive(Debug)]
pub enum BthfClientCallbacks {
    /// Callback invoked when the connection state of the client changes.
    /// Params (Address, Connection state, peer features, child features)
    ConnectionState(RawAddress, BthfClientConnectionState, u32, u32),

    /// Callback invoked when the audio connection state of the client changes.
    AudioState(RawAddress, BthfClientAudioState),
    // TODO(b/262264556): Incomplete implementation. Other callbacks will be implemented if necessary.
}

pub struct BthfClientCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(BthfClientCallbacks) + Send>,
}

type BthfClientCb = Arc<Mutex<BthfClientCallbacksDispatcher>>;

cb_variant!(
    BthfClientCb,
    hf_client_connection_state_cb -> BthfClientCallbacks::ConnectionState,
    *const RawAddress,
    bindings::bthf_client_connection_state_t -> BthfClientConnectionState,
    u32, u32, {
        let _0 = unsafe { *_0 };
    }
);

cb_variant!(
    BthfClientCb,
    hf_client_audio_state_cb -> BthfClientCallbacks::AudioState,
    *const RawAddress,
    bindings::bthf_client_audio_state_t -> BthfClientAudioState,{
        let _0 = unsafe { *_0 };
    }
);

struct RawHfClientWrapper {
    raw: *const bindings::bthf_client_interface_t,
}

unsafe impl Send for RawHfClientWrapper {}

pub struct HfClient {
    internal: RawHfClientWrapper,
    is_init: bool,
    is_enabled: bool,
    callbacks: Option<Box<bindings::bthf_client_callbacks_t>>,
}

impl ToggleableProfile for HfClient {
    fn is_enabled(&self) -> bool {
        self.is_enabled
    }

    fn enable(&mut self) -> bool {
        let cb_ptr = LTCheckedPtrMut::from(self.callbacks.as_mut().unwrap());

        let init = ccall!(self, init, cb_ptr.into());
        self.is_init = BtStatus::from(init) == BtStatus::Success;
        self.is_enabled = self.is_init;
        true
    }

    #[profile_enabled_or(false)]
    fn disable(&mut self) -> bool {
        ccall!(self, cleanup);
        self.is_enabled = false;
        true
    }
}

impl HfClient {
    pub fn new(intf: &BluetoothInterface) -> HfClient {
        let r = intf.get_profile_interface(SupportedProfiles::HfClient);
        HfClient {
            internal: RawHfClientWrapper { raw: r as *const bthf_client_interface_t },
            is_init: false,
            is_enabled: false,
            callbacks: None,
        }
    }

    pub fn is_initialized(&self) -> bool {
        self.is_init
    }

    pub fn initialize(&mut self, callbacks: BthfClientCallbacksDispatcher) -> bool {
        // Register dispatcher
        if get_dispatchers().lock().unwrap().set::<BthfClientCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for BthfClienCallbacks but it already existed");
        }

        let callbacks = Box::new(bindings::bthf_client_callbacks_t {
            // TODO(b/262264556): Incomplete implementation. Only necessary callbacks are implemented currently.
            size: 22 * 8,
            connection_state_cb: Some(hf_client_connection_state_cb),
            audio_state_cb: Some(hf_client_audio_state_cb),
            vr_cmd_cb: None,
            network_state_cb: None,
            network_roaming_cb: None,
            network_signal_cb: None,
            battery_level_cb: None,
            current_operator_cb: None,
            call_cb: None,
            callsetup_cb: None,
            callheld_cb: None,
            resp_and_hold_cb: None,
            clip_cb: None,
            call_waiting_cb: None,
            current_calls_cb: None,
            volume_change_cb: None,
            cmd_complete_cb: None,
            subscriber_info_cb: None,
            in_band_ring_tone_cb: None,
            last_voice_tag_number_callback: None,
            ring_indication_cb: None,
            unknown_event_cb: None,
        });
        self.callbacks = Some(callbacks);

        true
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn connect(&self, addr: RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtr::from_ref(&addr);
        BtStatus::from(ccall!(self, connect, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn disconnect(&self, addr: RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtr::from_ref(&addr);
        BtStatus::from(ccall!(self, disconnect, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn connect_audio(&mut self, addr: RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtr::from_ref(&addr);
        BtStatus::from(ccall!(self, connect_audio, addr_ptr.into()))
    }

    #[profile_enabled_or(BtStatus::NotReady)]
    pub fn disconnect_audio(&mut self, addr: RawAddress) -> BtStatus {
        let addr_ptr = LTCheckedPtr::from_ref(&addr);
        BtStatus::from(ccall!(self, disconnect_audio, addr_ptr.into()))
    }

    #[profile_enabled_or]
    pub fn cleanup(&mut self) {
        ccall!(self, cleanup)
    }
    // TODO(b/262264556): Incomplete API implementation. Only necessary APIs are implemented currently.
}
