//! Suspend/Resume API.

use crate::bluetooth::{
    Bluetooth, BluetoothDevice, BtifBluetoothCallbacks, DelayedActions, IBluetooth,
};
use crate::bluetooth_media::BluetoothMedia;
use crate::callbacks::Callbacks;
use crate::{BluetoothGatt, Message, RPCProxy};
use bt_topshim::btif::{BluetoothInterface, BtDiscMode};
use bt_topshim::metrics;
use log::warn;
use num_derive::{FromPrimitive, ToPrimitive};
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::Sender;

use bt_utils::socket::{BtSocket, HciChannels, MgmtCommand, HCI_DEV_NONE};

/// Defines the Suspend/Resume API.
///
/// This API is exposed by `btadapterd` and independent of the suspend/resume detection mechanism
/// which depends on the actual operating system the daemon runs on. Possible clients of this API
/// include `btmanagerd` with Chrome OS `powerd` integration, `btmanagerd` with systemd Inhibitor
/// interface, or any script hooked to suspend/resume events.
pub trait ISuspend {
    /// Adds an observer to suspend events.
    ///
    /// Returns true if the callback can be registered.
    fn register_callback(&mut self, callback: Box<dyn ISuspendCallback + Send>) -> bool;

    /// Removes an observer to suspend events.
    ///
    /// Returns true if the callback can be removed, false if `callback_id` is not recognized.
    fn unregister_callback(&mut self, callback_id: u32) -> bool;

    /// Prepares the stack for suspend, identified by `suspend_id`.
    ///
    /// Returns a positive number identifying the suspend if it can be started. If there is already
    /// a suspend, that active suspend id is returned.
    fn suspend(&mut self, suspend_type: SuspendType, suspend_id: i32);

    /// Undoes previous suspend preparation identified by `suspend_id`.
    ///
    /// Returns true if suspend can be resumed, and false if there is no suspend to resume.
    fn resume(&mut self) -> bool;
}

/// Suspend events.
pub trait ISuspendCallback: RPCProxy {
    /// Triggered when a callback is registered and given an identifier `callback_id`.
    fn on_callback_registered(&mut self, callback_id: u32);

    /// Triggered when the stack is ready for suspend and tell the observer the id of the suspend.
    fn on_suspend_ready(&mut self, suspend_id: i32);

    /// Triggered when the stack has resumed the previous suspend.
    fn on_resumed(&mut self, suspend_id: i32);
}

/// Events that are disabled when we go into suspend. This prevents spurious wakes from
/// events we know can happen but are not useful.
/// Bit 4 = Disconnect Complete.
/// Bit 19 = Mode Change.
const MASKED_EVENTS_FOR_SUSPEND: u64 = (1u64 << 4) | (1u64 << 19);

/// When we resume, we will want to reconnect audio devices that were previously connected.
/// However, we will need to delay a few seconds to avoid co-ex issues with Wi-Fi reconnection.
const RECONNECT_AUDIO_ON_RESUME_DELAY_MS: u64 = 3000;

/// TODO(b/286268874) Remove after the synchronization issue is resolved.
/// Delay sending suspend ready signal by some time.
const LE_RAND_CB_SUSPEND_READY_DELAY_MS: u64 = 100;

fn notify_suspend_state(hci_index: u16, suspended: bool) {
    log::debug!("Notify kernel suspend status: {} for hci{}", suspended, hci_index);
    let mut btsock = BtSocket::new();
    match btsock.open() {
        -1 => {
            panic!(
                "Bluetooth socket unavailable (errno {}). Try loading the kernel module first.",
                std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
            );
        }
        x => log::debug!("notify suspend Socket open at fd: {}", x),
    }
    // Bind to control channel (which is used for mgmt commands). We provide
    // HCI_DEV_NONE because we don't actually need a valid HCI dev for some MGMT commands.
    match btsock.bind_channel(HciChannels::Control, HCI_DEV_NONE) {
        -1 => {
            panic!(
                "Failed to bind control channel with errno={}",
                std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
            );
        }
        _ => (),
    };

    let command = MgmtCommand::FlossNotifySuspendState(hci_index, suspended);
    let bytes_written = btsock.write_mgmt_packet(command.into());
    if bytes_written <= 0 {
        log::error!("Failed to notify suspend state on hci:{} to {}", hci_index, suspended);
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u32)]
pub enum SuspendType {
    NoWakesAllowed,
    AllowWakeFromHid,
    Other,
}

struct SuspendState {
    le_rand_expected: bool,
    suspend_expected: bool,
    resume_expected: bool,
    suspend_id: Option<i32>,
}

impl SuspendState {
    pub fn new() -> SuspendState {
        Self {
            le_rand_expected: false,
            suspend_expected: false,
            resume_expected: false,
            suspend_id: None,
        }
    }
}

/// Implementation of the suspend API.
pub struct Suspend {
    bt: Arc<Mutex<Box<Bluetooth>>>,
    intf: Arc<Mutex<BluetoothInterface>>,
    gatt: Arc<Mutex<Box<BluetoothGatt>>>,
    media: Arc<Mutex<Box<BluetoothMedia>>>,
    tx: Sender<Message>,
    callbacks: Callbacks<dyn ISuspendCallback + Send>,

    /// This list keeps track of audio devices that had an audio profile before
    /// suspend so that we can attempt to connect after suspend.
    audio_reconnect_list: Vec<BluetoothDevice>,

    /// Active reconnection attempt after resume.
    audio_reconnect_joinhandle: Option<tokio::task::JoinHandle<()>>,

    suspend_timeout_joinhandle: Option<tokio::task::JoinHandle<()>>,
    suspend_state: Arc<Mutex<SuspendState>>,

    /// Bluetooth adapter connectable state before suspending.
    connectable_to_restore: bool,
    /// Bluetooth adapter discoverable mode before suspending.
    discoverable_mode_to_restore: BtDiscMode,
}

impl Suspend {
    pub fn new(
        bt: Arc<Mutex<Box<Bluetooth>>>,
        intf: Arc<Mutex<BluetoothInterface>>,
        gatt: Arc<Mutex<Box<BluetoothGatt>>>,
        media: Arc<Mutex<Box<BluetoothMedia>>>,
        tx: Sender<Message>,
    ) -> Suspend {
        Self {
            bt,
            intf,
            gatt,
            media,
            tx: tx.clone(),
            callbacks: Callbacks::new(tx.clone(), Message::SuspendCallbackDisconnected),
            audio_reconnect_list: Vec::new(),
            audio_reconnect_joinhandle: None,
            suspend_timeout_joinhandle: None,
            suspend_state: Arc::new(Mutex::new(SuspendState::new())),
            connectable_to_restore: false,
            discoverable_mode_to_restore: BtDiscMode::NonDiscoverable,
        }
    }

    pub(crate) fn callback_registered(&mut self, id: u32) {
        match self.callbacks.get_by_id_mut(id) {
            Some(callback) => callback.on_callback_registered(id),
            None => warn!("Suspend callback {} does not exist", id),
        }
    }

    pub(crate) fn remove_callback(&mut self, id: u32) -> bool {
        self.callbacks.remove_callback(id)
    }

    pub(crate) fn suspend_ready(&mut self, suspend_id: i32) {
        let hci_index = self.bt.lock().unwrap().get_hci_index();
        notify_suspend_state(hci_index, true);
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_suspend_ready(suspend_id);
        });
    }

    pub(crate) fn resume_ready(&mut self, suspend_id: i32) {
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_resumed(suspend_id);
        });
    }

    /// On resume, we attempt to reconnect to any audio devices connected during suspend.
    /// This marks this attempt as completed and we should clear the pending reconnects here.
    pub(crate) fn audio_reconnect_complete(&mut self) {
        self.audio_reconnect_list.clear();
        self.audio_reconnect_joinhandle = None;
    }

    pub(crate) fn get_connected_audio_devices(&self) -> Vec<BluetoothDevice> {
        let bonded_connected = self.bt.lock().unwrap().get_bonded_and_connected_devices();
        self.media.lock().unwrap().filter_to_connected_audio_devices_from(&bonded_connected)
    }
}

impl ISuspend for Suspend {
    fn register_callback(&mut self, callback: Box<dyn ISuspendCallback + Send>) -> bool {
        let id = self.callbacks.add_callback(callback);

        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _result = tx.send(Message::SuspendCallbackRegistered(id)).await;
        });

        true
    }

    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        self.remove_callback(callback_id)
    }

    fn suspend(&mut self, suspend_type: SuspendType, suspend_id: i32) {
        // Set suspend event mask
        self.intf.lock().unwrap().set_default_event_mask_except(MASKED_EVENTS_FOR_SUSPEND, 0u64);

        self.connectable_to_restore = self.bt.lock().unwrap().get_connectable_internal();
        self.discoverable_mode_to_restore =
            self.bt.lock().unwrap().get_discoverable_mode_internal();
        self.bt.lock().unwrap().set_connectable_internal(false);
        self.intf.lock().unwrap().clear_event_filter();
        self.intf.lock().unwrap().clear_filter_accept_list();

        self.bt.lock().unwrap().discovery_enter_suspend();
        self.gatt.lock().unwrap().advertising_enter_suspend();
        self.gatt.lock().unwrap().scan_enter_suspend();

        // Track connected audio devices and queue them for reconnect on resume.
        // If we still have the previous reconnect list left-over, do not try
        // to collect a new list here.
        if self.audio_reconnect_list.is_empty() {
            self.audio_reconnect_list = self.get_connected_audio_devices();
        }

        // Cancel any active reconnect task.
        if let Some(joinhandle) = &self.audio_reconnect_joinhandle {
            joinhandle.abort();
            self.audio_reconnect_joinhandle = None;
        }

        self.intf.lock().unwrap().disconnect_all_acls();

        // Handle wakeful cases (Connected/Other)
        // Treat Other the same as Connected
        match suspend_type {
            SuspendType::AllowWakeFromHid | SuspendType::Other => {
                self.intf.lock().unwrap().allow_wake_by_hid();
            }
            _ => {}
        }
        self.suspend_state.lock().unwrap().le_rand_expected = true;
        self.suspend_state.lock().unwrap().suspend_expected = true;
        self.suspend_state.lock().unwrap().suspend_id = Some(suspend_id);
        self.bt.lock().unwrap().le_rand();

        if let Some(join_handle) = &self.suspend_timeout_joinhandle {
            join_handle.abort();
            self.suspend_timeout_joinhandle = None;
        }

        let tx = self.tx.clone();
        let suspend_state = self.suspend_state.clone();
        self.suspend_timeout_joinhandle = Some(tokio::spawn(async move {
            tokio::time::sleep(tokio::time::Duration::from_millis(2000)).await;
            log::error!("Suspend did not complete in 2 seconds, continuing anyway.");

            suspend_state.lock().unwrap().le_rand_expected = false;
            suspend_state.lock().unwrap().suspend_expected = false;
            tokio::spawn(async move {
                let _result = tx.send(Message::SuspendReady(suspend_id)).await;
            });
        }));
    }

    fn resume(&mut self) -> bool {
        // Suspend ID state 0: NoRecord, 1: Recorded
        let suspend_id_state = match self.suspend_state.lock().unwrap().suspend_id {
            None => {
                log::error!("No suspend id saved at resume.");
                0
            }
            Some(_) => 1,
        };
        metrics::suspend_complete_state(suspend_id_state);
        // If no suspend id is saved here, it means floss did not receive the SuspendImminent
        // signal and as a result, the suspend flow was not run.
        // Skip the resume flow and return after logging the metrics.
        if suspend_id_state == 0 {
            return true;
        }

        let hci_index = self.bt.lock().unwrap().get_hci_index();
        notify_suspend_state(hci_index, false);

        self.intf.lock().unwrap().set_default_event_mask_except(0u64, 0u64);

        // Restore event filter and accept list to normal.
        self.intf.lock().unwrap().clear_event_filter();
        self.intf.lock().unwrap().clear_filter_accept_list();
        self.intf.lock().unwrap().restore_filter_accept_list();
        self.bt.lock().unwrap().set_connectable_internal(self.connectable_to_restore);
        if self.discoverable_mode_to_restore != BtDiscMode::NonDiscoverable {
            self.bt.lock().unwrap().set_discoverable(self.discoverable_mode_to_restore.clone(), 0);
        }

        if !self.audio_reconnect_list.is_empty() {
            let reconnect_list = self.audio_reconnect_list.clone();
            let txl = self.tx.clone();

            // Cancel any existing reconnect attempt.
            if let Some(joinhandle) = &self.audio_reconnect_joinhandle {
                joinhandle.abort();
                self.audio_reconnect_joinhandle = None;
            }

            self.audio_reconnect_joinhandle = Some(tokio::spawn(async move {
                // Wait a few seconds to avoid co-ex issues with wi-fi.
                tokio::time::sleep(tokio::time::Duration::from_millis(
                    RECONNECT_AUDIO_ON_RESUME_DELAY_MS,
                ))
                .await;

                // Queue up connections.
                for device in reconnect_list {
                    let _unused: Option<()> = txl
                        .send(Message::DelayedAdapterActions(DelayedActions::ConnectAllProfiles(
                            device,
                        )))
                        .await
                        .ok();
                }

                // Mark that we're done.
                let _unused: Option<()> =
                    txl.send(Message::AudioReconnectOnResumeComplete).await.ok();
            }));
        }

        self.bt.lock().unwrap().discovery_exit_suspend();
        self.gatt.lock().unwrap().advertising_exit_suspend();
        self.gatt.lock().unwrap().scan_exit_suspend();

        self.suspend_state.lock().unwrap().le_rand_expected = true;
        self.suspend_state.lock().unwrap().resume_expected = true;
        self.bt.lock().unwrap().le_rand();

        let tx = self.tx.clone();
        let suspend_state = self.suspend_state.clone();
        let suspend_id = self.suspend_state.lock().unwrap().suspend_id.unwrap();
        self.suspend_timeout_joinhandle = Some(tokio::spawn(async move {
            tokio::time::sleep(tokio::time::Duration::from_millis(2000)).await;
            log::error!("Resume did not complete in 2 seconds, continuing anyway.");

            suspend_state.lock().unwrap().le_rand_expected = false;
            suspend_state.lock().unwrap().resume_expected = false;
            tokio::spawn(async move {
                let _result = tx.send(Message::ResumeReady(suspend_id)).await;
            });
        }));

        true
    }
}

impl BtifBluetoothCallbacks for Suspend {
    fn le_rand_cb(&mut self, _random: u64) {
        // TODO(b/232547719): Suspend readiness may not depend only on LeRand, make a generic state
        // machine to support waiting for other conditions.
        if !self.suspend_state.lock().unwrap().le_rand_expected {
            log::warn!("Unexpected LE Rand callback, ignoring.");
            return;
        }
        self.suspend_state.lock().unwrap().le_rand_expected = false;

        if let Some(join_handle) = &self.suspend_timeout_joinhandle {
            join_handle.abort();
            self.suspend_timeout_joinhandle = None;
        }

        let suspend_id = self.suspend_state.lock().unwrap().suspend_id.unwrap();

        if self.suspend_state.lock().unwrap().suspend_expected {
            self.suspend_state.lock().unwrap().suspend_expected = false;
            let tx = self.tx.clone();
            tokio::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(
                    LE_RAND_CB_SUSPEND_READY_DELAY_MS,
                ))
                .await;
                let _result = tx.send(Message::SuspendReady(suspend_id)).await;
            });
        }

        self.suspend_state.lock().unwrap().suspend_id = Some(suspend_id);
        if self.suspend_state.lock().unwrap().resume_expected {
            self.suspend_state.lock().unwrap().resume_expected = false;
            let tx = self.tx.clone();
            tokio::spawn(async move {
                let _result = tx.send(Message::ResumeReady(suspend_id)).await;
            });
        }
    }
}
