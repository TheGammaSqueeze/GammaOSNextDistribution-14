use crate::bluetooth_manager::BluetoothManager;
use crate::config_util;
use bt_common::time::Alarm;
use bt_utils::socket::{
    BtSocket, HciChannels, MgmtCommand, MgmtCommandResponse, MgmtEvent, HCI_DEV_NONE,
};

use libc;
use log::{debug, error, info, warn};
use nix::sys::signal::{self, Signal};
use nix::unistd::Pid;
use regex::Regex;
use std::collections::{BTreeMap, HashMap};
use std::convert::TryFrom;
use std::fmt::{Display, Formatter};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};
use std::sync::{Arc, Mutex};
use tokio::io::unix::AsyncFd;
use tokio::sync::mpsc;
use tokio::time::{Duration, Instant};

/// Directory for Bluetooth pid file
pub const PID_DIR: &str = "/var/run/bluetooth";

/// Number of times to try restarting before resetting the adapter.
pub const RESET_ON_RESTART_COUNT: i32 = 2;

/// Time to wait from when IndexRemoved is sent to mgmt socket to when we send
/// it to the state machine. This debounce exists because when the Index is
/// removed due to adapter lost, userspace requires some time to actually close
/// the socket.
pub const INDEX_REMOVED_DEBOUNCE_TIME: Duration = Duration::from_millis(150);

/// Period to check the PID existence. Ideally adapter should clean up the PID
/// file by itself and uses it as the stopped signal. This is a backup mechanism
/// to avoid dead process + PID not cleaned up from happening.
pub const PID_RUNNING_CHECK_PERIOD: Duration = Duration::from_secs(60);

const HCI_BIND_MAX_RETRY: i32 = 2;

const HCI_BIND_RETRY_INTERVAL: Duration = Duration::from_millis(10);

#[derive(Debug, PartialEq, Copy, Clone)]
#[repr(u32)]
pub enum ProcessState {
    Off = 0,            // Bluetooth is not running or is not available.
    TurningOn = 1,      // We are not notified that the Bluetooth is running
    On = 2,             // Bluetooth is running
    TurningOff = 3,     // We are not notified that the Bluetooth is stopped
    PendingRestart = 4, // Bluetooth is turning on and will be restarted after started
    Restarting = 5,     // Bluetooth is turning off and will be started after stopped
}

/// Check whether adapter is enabled by checking internal state.
pub fn state_to_enabled(state: ProcessState) -> bool {
    match state {
        ProcessState::On | ProcessState::TurningOff => true,
        _ => false,
    }
}

/// Device path of hci device in sysfs. This will uniquely identify a Bluetooth
/// host controller even when the hci index changes.
pub type DevPath = String;

/// An invalid hci index.
pub const INVALID_HCI_INDEX: i32 = -1;

/// Hci index that doesn't necessarily map to the physical hciN value. Make sure
/// that |VirtualHciIndex| and |RealHciIndex| don't easily convert to each other
/// to protect from logical errors.
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct VirtualHciIndex(pub i32);
impl VirtualHciIndex {
    pub(crate) fn to_i32(&self) -> i32 {
        self.0
    }
}
impl Display for VirtualHciIndex {
    fn fmt(&self, f: &mut Formatter) -> std::fmt::Result {
        write!(f, "VirtHci{}", self.0)
    }
}

/// Hci index that maps to real system index.
#[derive(Clone, Copy, Debug, PartialEq, PartialOrd)]
pub struct RealHciIndex(pub i32);
impl RealHciIndex {
    pub(crate) fn to_i32(&self) -> i32 {
        self.0
    }
}
impl Display for RealHciIndex {
    fn fmt(&self, f: &mut Formatter) -> std::fmt::Result {
        write!(f, "RealHci{}", self.0)
    }
}

/// Adapter state actions
#[derive(Debug)]
pub enum AdapterStateActions {
    StartBluetooth(VirtualHciIndex),
    StopBluetooth(VirtualHciIndex),
    RestartBluetooth(VirtualHciIndex),
    BluetoothStarted(i32, VirtualHciIndex), // PID and HCI
    BluetoothStopped(VirtualHciIndex),
    HciDevicePresence(DevPath, RealHciIndex, bool),
}

/// Enum of all the messages that state machine handles.
#[derive(Debug)]
pub enum Message {
    AdapterStateChange(AdapterStateActions),
    PidChange(inotify::EventMask, Option<String>),
    CallbackDisconnected(u32),
    CommandTimeout(VirtualHciIndex),
    SetDesiredDefaultAdapter(VirtualHciIndex),
}

pub struct StateMachineContext {
    tx: mpsc::Sender<Message>,
    rx: mpsc::Receiver<Message>,
    state_machine: StateMachineInternal,
}

impl StateMachineContext {
    fn new(state_machine: StateMachineInternal) -> StateMachineContext {
        let (tx, rx) = mpsc::channel::<Message>(10);
        StateMachineContext { tx: tx, rx: rx, state_machine: state_machine }
    }

    pub fn get_proxy(&self) -> StateMachineProxy {
        StateMachineProxy {
            floss_enabled: self.state_machine.floss_enabled.clone(),
            default_adapter: self.state_machine.default_adapter.clone(),
            state: self.state_machine.state.clone(),
            tx: self.tx.clone(),
        }
    }
}

/// Creates a new state machine.
///
/// # Arguments
/// `invoker` - What type of process manager to use.
pub fn create_new_state_machine_context(invoker: Invoker) -> StateMachineContext {
    let floss_enabled = config_util::is_floss_enabled();
    let desired_adapter = config_util::get_default_adapter();
    let process_manager = StateMachineInternal::make_process_manager(invoker);

    StateMachineContext::new(StateMachineInternal::new(
        process_manager,
        floss_enabled,
        desired_adapter,
    ))
}

#[derive(Clone)]
/// Proxy object to give access to certain internals of the state machine. For more detailed
/// documentation, see |StateMachineInternal|.
///
/// Always construct this using |StateMachineContext::get_proxy(&self)|.
pub struct StateMachineProxy {
    /// Shared state about whether floss is enabled.
    floss_enabled: Arc<AtomicBool>,

    /// Shared state about what the default adapter should be.
    default_adapter: Arc<AtomicI32>,

    /// Shared internal state about each adapter's state.
    state: Arc<Mutex<BTreeMap<VirtualHciIndex, AdapterState>>>,

    /// Sender to future that mutates |StateMachineInternal| states.
    tx: mpsc::Sender<Message>,
}

const TX_SEND_TIMEOUT_DURATION: Duration = Duration::from_secs(3);

/// Duration to use for timeouts when starting/stopping adapters.
/// Some adapters take a while to load firmware so use a sufficiently long timeout here.
const COMMAND_TIMEOUT_DURATION: Duration = Duration::from_secs(7);

impl StateMachineProxy {
    pub fn start_bluetooth(&self, hci: VirtualHciIndex) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ = tx
                .send(Message::AdapterStateChange(AdapterStateActions::StartBluetooth(hci)))
                .await;
        });
    }

    pub fn stop_bluetooth(&self, hci: VirtualHciIndex) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ =
                tx.send(Message::AdapterStateChange(AdapterStateActions::StopBluetooth(hci))).await;
        });
    }

    pub fn restart_bluetooth(&self, hci: VirtualHciIndex) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ = tx
                .send(Message::AdapterStateChange(AdapterStateActions::RestartBluetooth(hci)))
                .await;
        });
    }

    /// Read state for an hci device.
    pub fn get_state<T, F>(&self, hci: VirtualHciIndex, call: F) -> Option<T>
    where
        F: Fn(&AdapterState) -> Option<T>,
    {
        match self.state.lock().unwrap().get(&hci) {
            Some(a) => call(&a),
            None => None,
        }
    }

    pub fn get_process_state(&self, hci: VirtualHciIndex) -> ProcessState {
        self.get_state(hci, move |a: &AdapterState| Some(a.state)).unwrap_or(ProcessState::Off)
    }

    pub fn modify_state<F>(&mut self, hci: VirtualHciIndex, call: F)
    where
        F: Fn(&mut AdapterState),
    {
        call(&mut *self.state.lock().unwrap().entry(hci).or_insert(AdapterState::new(
            String::new(),
            RealHciIndex(hci.to_i32()),
            hci,
        )))
    }

    pub fn get_tx(&self) -> mpsc::Sender<Message> {
        self.tx.clone()
    }

    pub fn get_floss_enabled(&self) -> bool {
        self.floss_enabled.load(Ordering::Relaxed)
    }

    /// Sets the |floss_enabled| atomic variable.
    ///
    /// # Returns
    /// Previous value of |floss_enabled|
    pub fn set_floss_enabled(&mut self, enabled: bool) -> bool {
        self.floss_enabled.swap(enabled, Ordering::Relaxed)
    }

    pub fn get_adapters(&self) -> Vec<AdapterState> {
        self.state.lock().unwrap().iter().map(|(_, a)| a.clone()).collect::<Vec<AdapterState>>()
    }

    pub fn get_valid_adapters(&self) -> Vec<AdapterState> {
        self.state
            .lock()
            .unwrap()
            .iter()
            // Filter to adapters that are present or enabled.
            .filter(|&(_, a)| a.present || state_to_enabled(a.state))
            .map(|(_, a)| a.clone())
            .collect::<Vec<AdapterState>>()
    }

    /// Get the default adapter.
    pub fn get_default_adapter(&mut self) -> VirtualHciIndex {
        VirtualHciIndex(self.default_adapter.load(Ordering::Relaxed))
    }

    /// Set the desired default adapter.
    pub fn set_desired_default_adapter(&mut self, adapter: VirtualHciIndex) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ = tx.send(Message::SetDesiredDefaultAdapter(adapter)).await;
        });
    }
}

fn pid_inotify_async_fd() -> AsyncFd<inotify::Inotify> {
    let mut pid_detector = inotify::Inotify::init().expect("cannot use inotify");
    pid_detector
        .add_watch(PID_DIR, inotify::WatchMask::CREATE | inotify::WatchMask::DELETE)
        .expect("failed to add watch on pid directory");
    AsyncFd::new(pid_detector).expect("failed to add async fd for pid detector")
}

/// Given an pid path, returns the adapter index for that pid path.
fn get_hci_index_from_pid_path(path: &str) -> Option<VirtualHciIndex> {
    let re = Regex::new(r"bluetooth([0-9]+).pid").unwrap();
    re.captures(path)?.get(1)?.as_str().parse().ok().map(|v| VirtualHciIndex(v))
}

fn event_name_to_string(name: Option<&std::ffi::OsStr>) -> Option<String> {
    if let Some(val) = &name {
        if let Some(strval) = val.to_str() {
            return Some(strval.to_string());
        }
    }

    return None;
}

// List existing pids and then configure inotify on pid dir.
fn configure_pid(pid_tx: mpsc::Sender<Message>) {
    // Configure PID listener.
    tokio::spawn(async move {
        debug!("Spawned pid notify task");

        // Get a list of active pid files to determine initial adapter status
        let files = config_util::list_pid_files(PID_DIR);
        for file in files {
            let _ = pid_tx
                .send_timeout(
                    Message::PidChange(inotify::EventMask::CREATE, Some(file)),
                    TX_SEND_TIMEOUT_DURATION,
                )
                .await
                .unwrap();
        }

        // Set up a PID file listener to emit PID inotify messages
        let mut pid_async_fd = pid_inotify_async_fd();

        loop {
            let r = pid_async_fd.readable_mut();
            let mut fd_ready = r.await.unwrap();
            let mut buffer: [u8; 1024] = [0; 1024];
            debug!("Found new pid inotify entries. Reading them");
            match fd_ready.try_io(|inner| inner.get_mut().read_events(&mut buffer)) {
                Ok(Ok(events)) => {
                    for event in events {
                        debug!("got some events from pid {:?}", event.mask);
                        let _ = pid_tx
                            .send_timeout(
                                Message::PidChange(event.mask, event_name_to_string(event.name)),
                                TX_SEND_TIMEOUT_DURATION,
                            )
                            .await
                            .unwrap();
                    }
                }
                Err(_) | Ok(Err(_)) => panic!("Inotify watcher on {} failed.", PID_DIR),
            }
            fd_ready.clear_ready();
            drop(fd_ready);
        }
    });
}

// Configure the HCI socket listener and prepare the system to receive mgmt events for index added
// and index removed.
fn configure_hci(hci_tx: mpsc::Sender<Message>) {
    let mut btsock = BtSocket::new();

    // If the bluetooth socket isn't available, the kernel module is not loaded and we can't
    // actually listen to it for index added/removed events.
    match btsock.open() {
        -1 => {
            panic!(
                "Bluetooth socket unavailable (errno {}). Try loading the kernel module first.",
                std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
            );
        }
        x => debug!("Socket open at fd: {}", x),
    }

    tokio::spawn(async move {
        // Bind to control channel (which is used for mgmt commands). We provide
        // HCI_DEV_NONE because we don't actually need a valid HCI dev for some MGMT commands.
        let mut bind_succ = false;
        for _i in 0..HCI_BIND_MAX_RETRY {
            match btsock.bind_channel(HciChannels::Control, HCI_DEV_NONE) {
                -1 => {
                    match std::io::Error::last_os_error().raw_os_error().unwrap_or(0) {
                        libc::EINVAL => {
                            // If MGMT hasn't been initialized EINVAL will be returned.
                            // Just wait for a short time and try again.
                            debug!("Got EINVAL in bind. Wait and try again");
                            tokio::time::sleep(HCI_BIND_RETRY_INTERVAL).await;
                            continue;
                        }
                        others => {
                            panic!("Failed to bind control channel with errno={}", others);
                        }
                    }
                }
                _ => {
                    bind_succ = true;
                    break;
                }
            };
        }

        if !bind_succ {
            panic!("bind failed too many times!!");
        }

        debug!("Spawned hci notify task");

        // Make this into an AsyncFD and start using it for IO
        let mut hci_afd = AsyncFd::new(btsock).expect("Failed to add async fd for BT socket.");

        // Start by first reading the index list
        match hci_afd.writable_mut().await {
            Ok(mut guard) => {
                let _ = guard.try_io(|sock| {
                    let command = MgmtCommand::ReadIndexList;
                    sock.get_mut().write_mgmt_packet(command.into());
                    Ok(())
                });
            }
            Err(e) => debug!("Failed to write to hci socket: {:?}", e),
        };

        // Now listen only for devices that are newly added or removed.
        loop {
            if let Ok(mut guard) = hci_afd.readable_mut().await {
                let result = guard.try_io(|sock| Ok(sock.get_mut().read_mgmt_packet()));
                let packet = match result {
                    Ok(v) => v.unwrap_or(None),
                    Err(_) => None,
                };

                if let Some(p) = packet {
                    debug!("Got a valid packet from btsocket: {:?}", p);

                    if let Ok(ev) = MgmtEvent::try_from(p) {
                        debug!("Got a valid mgmt event: {:?}", ev);

                        match ev {
                            MgmtEvent::CommandComplete { opcode: _, status: _, response } => {
                                if let MgmtCommandResponse::ReadIndexList {
                                    num_intf: _,
                                    interfaces,
                                } = response
                                {
                                    for hci in interfaces {
                                        let hci = RealHciIndex(hci.into());
                                        debug!("IndexList response: {}", hci);
                                        // We need devpath for an index or we don't use it.
                                        if let Some(d) = config_util::get_devpath_for_hci(hci) {
                                            let _ = hci_tx
                                                .send_timeout(
                                                    Message::AdapterStateChange(
                                                        AdapterStateActions::HciDevicePresence(
                                                            d, hci, true,
                                                        ),
                                                    ),
                                                    TX_SEND_TIMEOUT_DURATION,
                                                )
                                                .await
                                                .unwrap();
                                        } else {
                                            error!("IndexList: Could not get devpath for {}", hci);
                                        }
                                    }
                                }
                            }
                            MgmtEvent::IndexAdded(hci) => {
                                let hci = RealHciIndex(hci.into());
                                debug!("IndexAdded: {}", hci);
                                // We need devpath for an index or we don't use it.
                                if let Some(d) = config_util::get_devpath_for_hci(hci) {
                                    let _ = hci_tx
                                        .send_timeout(
                                            Message::AdapterStateChange(
                                                AdapterStateActions::HciDevicePresence(
                                                    d, hci, true,
                                                ),
                                            ),
                                            TX_SEND_TIMEOUT_DURATION,
                                        )
                                        .await
                                        .unwrap();
                                } else {
                                    error!("IndexAdded: Could not get devpath for {}", hci);
                                }
                            }
                            MgmtEvent::IndexRemoved(hci) => {
                                let hci = RealHciIndex(hci.into());
                                debug!("IndexRemoved: {}", hci);
                                let devpath =
                                    config_util::get_devpath_for_hci(hci).unwrap_or(String::new());
                                // Only send presence removed if the device is removed
                                // and not when userchannel takes exclusive access. This needs to
                                // be delayed a bit for when the socket legitimately disappears as
                                // it takes some time for userspace to close the socket.
                                //
                                // It's possible for devpath to be empty in this case because the
                                // index is being removed. Handlers of HciDevicePresence need to
                                // be aware of this case.
                                let txl = hci_tx.clone();
                                tokio::spawn(async move {
                                    tokio::time::sleep(INDEX_REMOVED_DEBOUNCE_TIME).await;
                                    if !config_util::check_hci_device_exists(hci) {
                                        let _ = txl
                                            .send_timeout(
                                                Message::AdapterStateChange(
                                                    AdapterStateActions::HciDevicePresence(
                                                        devpath, hci, false,
                                                    ),
                                                ),
                                                TX_SEND_TIMEOUT_DURATION,
                                            )
                                            .await
                                            .unwrap();
                                    }
                                });
                            }
                        }
                    }
                } else {
                    // Got nothing from the previous read so clear the ready bit.
                    guard.clear_ready();
                }
            }
        }
    });
}

/// Handle command timeouts per hci interface.
struct CommandTimeout {
    pub waker: Arc<Alarm>,
    expired: bool,
    per_hci_timeout: HashMap<VirtualHciIndex, Instant>,
    duration: Duration,
}

impl CommandTimeout {
    pub fn new() -> Self {
        CommandTimeout {
            waker: Arc::new(Alarm::new()),
            per_hci_timeout: HashMap::new(),
            expired: true,
            duration: COMMAND_TIMEOUT_DURATION,
        }
    }

    /// Set next command timeout. If no waker is active, reset to duration.
    fn set_next(&mut self, hci: VirtualHciIndex) {
        let wake = Instant::now() + self.duration;
        self.per_hci_timeout.entry(hci).and_modify(|v| *v = wake).or_insert(wake);

        if self.expired {
            self.waker.reset(self.duration);
            self.expired = false;
        }
    }

    /// Remove command timeout for hci interface.
    fn cancel(&mut self, hci: VirtualHciIndex) {
        self.per_hci_timeout.remove(&hci);
    }

    /// Expire entries that are older than now and set next wake.
    /// Returns list of expired hci entries.
    fn expire(&mut self) -> Vec<VirtualHciIndex> {
        let now = Instant::now();

        let mut completed: Vec<VirtualHciIndex> = Vec::new();
        let mut next_expiry = now + self.duration;

        for (hci, expiry) in &self.per_hci_timeout {
            if *expiry < now {
                completed.push(*hci);
            } else if *expiry < next_expiry {
                next_expiry = *expiry;
            }
        }

        for hci in &completed {
            self.per_hci_timeout.remove(hci);
        }

        // If there are any remaining wakeups, reset the wake.
        if !self.per_hci_timeout.is_empty() {
            let duration: Duration = next_expiry - now;
            self.waker.reset(duration);
            self.expired = false;
        } else {
            self.expired = true;
        }

        completed
    }

    /// Handles a specific timeout action.
    fn handle_timeout_action(&mut self, hci: VirtualHciIndex, action: CommandTimeoutAction) {
        match action {
            CommandTimeoutAction::ResetTimer => self.set_next(hci),
            CommandTimeoutAction::CancelTimer => self.cancel(hci),
            CommandTimeoutAction::DoNothing => (),
        }
    }
}

pub async fn mainloop(
    mut context: StateMachineContext,
    bluetooth_manager: Arc<Mutex<Box<BluetoothManager>>>,
) {
    // Set up a command timeout listener to emit timeout messages
    let cmd_timeout = Arc::new(Mutex::new(CommandTimeout::new()));

    let ct = cmd_timeout.clone();
    let timeout_tx = context.tx.clone();

    tokio::spawn(async move {
        let timer = ct.lock().unwrap().waker.clone();
        loop {
            let _expired = timer.expired().await;
            let completed = ct.lock().unwrap().expire();
            for hci in completed {
                let _ = timeout_tx
                    .send_timeout(Message::CommandTimeout(hci), TX_SEND_TIMEOUT_DURATION)
                    .await
                    .unwrap();
            }
        }
    });

    // Set up an HCI device listener to emit HCI device inotify messages.
    // This is also responsible for configuring the initial list of HCI devices available on the
    // system.
    configure_hci(context.tx.clone());
    configure_pid(context.tx.clone());

    // Listen for all messages and act on them
    loop {
        let m = context.rx.recv().await;

        if m.is_none() {
            warn!("Exiting manager mainloop");
            break;
        }

        debug!("Message handler: {:?}", m);

        match m.unwrap() {
            // Adapter action has changed
            Message::AdapterStateChange(adapter_action) => {
                // Grab previous state from lock and release
                let hci: VirtualHciIndex;
                let next_state;
                let prev_state;

                match &adapter_action {
                    AdapterStateActions::StartBluetooth(i) => {
                        hci = *i;
                        prev_state = context.state_machine.get_process_state(hci);

                        let action;
                        (next_state, action) = context.state_machine.action_start_bluetooth(hci);
                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, action);
                    }
                    AdapterStateActions::StopBluetooth(i) => {
                        hci = *i;
                        prev_state = context.state_machine.get_process_state(hci);

                        let action;
                        (next_state, action) = context.state_machine.action_stop_bluetooth(hci);
                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, action);
                    }
                    AdapterStateActions::RestartBluetooth(i) => {
                        hci = *i;
                        prev_state = context.state_machine.get_process_state(hci);

                        let action;
                        (next_state, action) = context.state_machine.action_restart_bluetooth(hci);
                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, action);
                    }
                    AdapterStateActions::BluetoothStarted(pid, i) => {
                        hci = *i;
                        prev_state = context.state_machine.get_process_state(hci);

                        let action;
                        (next_state, action) =
                            context.state_machine.action_on_bluetooth_started(*pid, hci);
                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, action);

                        if context.state_machine.has_queued_present(hci) {
                            context.state_machine.modify_state(hci, |a: &mut AdapterState| {
                                a.has_queued_present = false;
                            });
                            bluetooth_manager.lock().unwrap().callback_hci_device_change(hci, true);
                        }
                    }
                    AdapterStateActions::BluetoothStopped(i) => {
                        hci = *i;
                        prev_state = context.state_machine.get_process_state(hci);

                        let action;
                        (next_state, action) =
                            context.state_machine.action_on_bluetooth_stopped(hci);
                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, action);
                    }

                    AdapterStateActions::HciDevicePresence(devpath, i, present) => {
                        let previous_real_hci = match context
                            .state_machine
                            .get_virtual_id_by_devpath(devpath.clone())
                        {
                            Some(v) => context
                                .state_machine
                                .get_state(v, |a: &AdapterState| Some(a.real_hci)),
                            None => None,
                        };
                        hci = context.state_machine.get_updated_virtual_id(devpath.clone(), *i);

                        // If this is really a new hci device, load the enabled state from the disk.
                        if previous_real_hci.is_none() {
                            context.state_machine.modify_state(hci, |a: &mut AdapterState| {
                                a.config_enabled = config_util::is_hci_n_enabled(hci);
                            });
                        }

                        // If the real hci changed, we need to set the previous present to the
                        // opposite of the current present so that we don't no-op the action.
                        if previous_real_hci.is_some()
                            && previous_real_hci
                                != context
                                    .state_machine
                                    .get_state(hci, |a: &AdapterState| Some(a.real_hci))
                        {
                            context.state_machine.modify_state(hci, |a: &mut AdapterState| {
                                a.present = !present;
                            });
                        }

                        prev_state = context.state_machine.get_process_state(hci);

                        // Don't bother the clients if presence is unchanged. But still execute the
                        // state machine here in case there is anything else to be done (e.g.,
                        // verify the next state).
                        let presence_changed = *present
                            != context
                                .state_machine
                                .get_state(hci, |a: &AdapterState| Some(a.present))
                                .unwrap_or(false);

                        let adapter_change_action;
                        let timeout_action;
                        (next_state, adapter_change_action, timeout_action) =
                            context.state_machine.action_on_hci_presence_changed(hci, *present);

                        cmd_timeout.lock().unwrap().handle_timeout_action(hci, timeout_action);

                        match adapter_change_action {
                            AdapterChangeAction::NewDefaultAdapter(new_hci) => {
                                context
                                    .state_machine
                                    .default_adapter
                                    .store(new_hci.to_i32(), Ordering::Relaxed);
                                bluetooth_manager
                                    .lock()
                                    .unwrap()
                                    .callback_default_adapter_change(new_hci);
                            }

                            AdapterChangeAction::DoNothing => (),
                        };

                        if presence_changed {
                            // If present switched to true and we're turning on the adapter,
                            // defer the callback until the next BluetoothStarted or CommandTimeout
                            // so the clients won't get an unexpected state change after present.
                            let queue_present = *present && next_state == ProcessState::TurningOn;

                            // Always modify_state to make sure it's reset on queue_present=false,
                            // e.g., when a hci is removed while its presence is still queued.
                            context.state_machine.modify_state(hci, |a: &mut AdapterState| {
                                a.has_queued_present = queue_present;
                            });

                            if !queue_present {
                                bluetooth_manager
                                    .lock()
                                    .unwrap()
                                    .callback_hci_device_change(hci, *present);
                            }
                        }
                    }
                };

                // All actions and the resulting state changes should be logged for debugging.
                info!(
                    "{}: Action={:?}, Previous State({:?}), Next State({:?})",
                    hci, adapter_action, prev_state, next_state
                );

                // Only emit enabled event for certain transitions
                let prev_enabled = state_to_enabled(prev_state);
                let next_enabled = state_to_enabled(next_state);
                if prev_enabled != next_enabled {
                    bluetooth_manager
                        .lock()
                        .unwrap()
                        .callback_hci_enabled_change(hci, next_enabled);
                }
            }

            // Monitored pid directory has a change
            Message::PidChange(mask, filename) => match (mask, &filename) {
                (inotify::EventMask::CREATE, Some(fname)) => {
                    let path = std::path::Path::new(PID_DIR).join(&fname);
                    match (
                        get_hci_index_from_pid_path(&fname),
                        tokio::fs::read(path.clone()).await.ok(),
                    ) {
                        (Some(hci), Some(s)) => {
                            let pid = String::from_utf8(s)
                                .expect("invalid pid file")
                                .parse::<i32>()
                                .unwrap_or(0);
                            debug!("Sending bluetooth started action for {}, pid={}", hci, pid);
                            let _ = context
                                .tx
                                .send_timeout(
                                    Message::AdapterStateChange(
                                        AdapterStateActions::BluetoothStarted(pid, hci),
                                    ),
                                    TX_SEND_TIMEOUT_DURATION,
                                )
                                .await
                                .unwrap();
                            let handle = tokio::spawn(async move {
                                debug!("{}: Spawned process monitor", hci);
                                loop {
                                    tokio::time::sleep(PID_RUNNING_CHECK_PERIOD).await;
                                    // Check if process exists by sending kill -0.
                                    match nix::sys::signal::kill(Pid::from_raw(pid), None) {
                                        Err(nix::errno::Errno::ESRCH) => {
                                            warn!("{}: Process died; Removing PID file", hci);
                                            if let Err(e) = std::fs::remove_file(path) {
                                                warn!("{}: Failed to remove: {}", hci, e);
                                            }
                                            break;
                                        }
                                        Err(e) => {
                                            // Other errno should rarely happen:
                                            //   EINVAL: The value of the sig argument is an invalid
                                            //           or unsupported signal number.
                                            //   EPERM: The process does not have permission to send
                                            //          the signal to any receiving process.
                                            error!("{}: Failed to send signal: {}", hci, e);
                                            break;
                                        }
                                        _ => {}
                                    }
                                }
                            });
                            match context
                                .state_machine
                                .process_monitor
                                .lock()
                                .unwrap()
                                .insert(fname.clone(), handle)
                            {
                                Some(handle) => {
                                    warn!("{}: Aborting old handler", hci);
                                    handle.abort();
                                }
                                None => {}
                            }
                        }
                        _ => debug!("Invalid pid path: {}", fname),
                    }
                }
                (inotify::EventMask::DELETE, Some(fname)) => {
                    if let Some(hci) = get_hci_index_from_pid_path(&fname) {
                        debug!("Sending bluetooth stopped action for {}", hci);
                        context
                            .tx
                            .send_timeout(
                                Message::AdapterStateChange(AdapterStateActions::BluetoothStopped(
                                    hci,
                                )),
                                TX_SEND_TIMEOUT_DURATION,
                            )
                            .await
                            .unwrap();
                        match context.state_machine.process_monitor.lock().unwrap().remove(fname) {
                            Some(handle) => handle.abort(),
                            None => {
                                warn!("{}: Process exited but process monitor not found", hci)
                            }
                        }
                    }
                }
                _ => debug!("Ignored event {:?} - {:?}", mask, &filename),
            },

            // Callback client has disconnected
            Message::CallbackDisconnected(id) => {
                bluetooth_manager.lock().unwrap().callback_disconnected(id);
            }

            // Handle command timeouts
            Message::CommandTimeout(hci) => {
                debug!(
                    "{}: Expired action, state={:?}",
                    hci,
                    context.state_machine.get_process_state(hci)
                );
                let timeout_action = context.state_machine.action_on_command_timeout(hci);
                match timeout_action {
                    StateMachineTimeoutActions::Noop => (),
                    _ => cmd_timeout.lock().unwrap().set_next(hci),
                }

                if context.state_machine.has_queued_present(hci) {
                    context.state_machine.modify_state(hci, |a: &mut AdapterState| {
                        a.has_queued_present = false;
                    });
                    bluetooth_manager.lock().unwrap().callback_hci_device_change(hci, true);
                }
            }

            Message::SetDesiredDefaultAdapter(hci) => {
                debug!("Changing desired default adapter to {}", hci);
                match context.state_machine.set_desired_default_adapter(hci) {
                    AdapterChangeAction::NewDefaultAdapter(new_hci) => {
                        context
                            .state_machine
                            .default_adapter
                            .store(new_hci.to_i32(), Ordering::Relaxed);
                        bluetooth_manager.lock().unwrap().callback_default_adapter_change(new_hci);
                    }
                    AdapterChangeAction::DoNothing => (),
                }
            }
        }
    }
}

/// Trait that needs to be implemented by the native process manager for the
/// targeted system. This is used to manage adapter processes.
pub trait ProcessManager {
    /// Start the adapter process.
    ///
    /// # Args
    /// * `virtual_hci` - Virtual index of adapter used for apis.
    /// * `real_hci` - Real index of the adapter on the system. This can
    ///                  change during a single boot.
    fn start(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex);

    /// Stop the adapter process.
    ///
    /// # Args
    /// * `virtual_hci` - Virtual index of adapter used for apis.
    /// * `real_hci` - Real index of the adapter on the system.
    fn stop(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex);
}

pub enum Invoker {
    #[allow(dead_code)]
    NativeInvoker,
    SystemdInvoker,
    UpstartInvoker,
}

pub struct NativeInvoker {
    process_container: Option<Child>,
    bluetooth_pid: u32,
}

impl NativeInvoker {
    pub fn new() -> NativeInvoker {
        NativeInvoker { process_container: None, bluetooth_pid: 0 }
    }
}

impl ProcessManager for NativeInvoker {
    fn start(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex) {
        let new_process = Command::new("/usr/bin/btadapterd")
            .arg(format!("INDEX={} HCI={}", virtual_hci.to_i32(), real_hci.to_i32()))
            .stdout(Stdio::piped())
            .spawn()
            .expect("cannot open");
        self.bluetooth_pid = new_process.id();
        self.process_container = Some(new_process);
    }
    fn stop(&mut self, _virtual_hci: VirtualHciIndex, _real_hci: RealHciIndex) {
        match self.process_container {
            Some(ref mut _p) => {
                signal::kill(Pid::from_raw(self.bluetooth_pid as i32), Signal::SIGTERM).unwrap();
                self.process_container = None;
            }
            None => {
                warn!("Process doesn't exist");
            }
        }
    }
}

pub struct UpstartInvoker {}

impl UpstartInvoker {
    pub fn new() -> UpstartInvoker {
        UpstartInvoker {}
    }
}

impl ProcessManager for UpstartInvoker {
    fn start(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex) {
        if let Err(e) = Command::new("initctl")
            .args(&[
                "start",
                "btadapterd",
                format!("INDEX={}", virtual_hci.to_i32()).as_str(),
                format!("HCI={}", real_hci.to_i32()).as_str(),
            ])
            .output()
        {
            error!("Failed to start btadapterd: {}", e);
        }
    }

    fn stop(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex) {
        if let Err(e) = Command::new("initctl")
            .args(&[
                "stop",
                "btadapterd",
                format!("INDEX={}", virtual_hci.to_i32()).as_str(),
                format!("HCI={}", real_hci.to_i32()).as_str(),
            ])
            .output()
        {
            error!("Failed to stop btadapterd: {}", e);
        }
    }
}

pub struct SystemdInvoker {}

impl SystemdInvoker {
    pub fn new() -> SystemdInvoker {
        SystemdInvoker {}
    }
}

impl ProcessManager for SystemdInvoker {
    fn start(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex) {
        Command::new("systemctl")
            .args(&[
                "restart",
                format!("btadapterd@{}_{}.service", virtual_hci.to_i32(), real_hci.to_i32())
                    .as_str(),
            ])
            .output()
            .expect("failed to start bluetooth");
    }

    fn stop(&mut self, virtual_hci: VirtualHciIndex, real_hci: RealHciIndex) {
        Command::new("systemctl")
            .args(&[
                "stop",
                format!("btadapterd@{}_{}.service", virtual_hci.to_i32(), real_hci.to_i32())
                    .as_str(),
            ])
            .output()
            .expect("failed to stop bluetooth");
    }
}

/// Stored state of each adapter in the state machine.
#[derive(Clone, Debug)]
pub struct AdapterState {
    /// Current adapter process state.
    pub state: ProcessState,

    /// Device path for this adapter. This should be consistent across removal
    /// and addition of devices.
    pub devpath: DevPath,

    /// Real hci index for this adapter. This can change after boot as adapters are
    /// removed and re-added. Use the devpath for a more consistent look-up.
    pub real_hci: RealHciIndex,

    /// Virtual hci index for this adapter. This can be decoupled from the real
    /// hci index and is usually the first |real_hci| value that it shows up as.
    pub virt_hci: VirtualHciIndex,

    /// PID for process using this adapter.
    pub pid: i32,

    /// Whether this hci device is listed as present.
    pub present: bool,

    /// Whether the 'present' notification is being deferred until adapter is ready.
    pub has_queued_present: bool,

    /// Whether this hci device is configured to be enabled.
    pub config_enabled: bool,

    /// How many times this adapter has attempted to restart without success.
    pub restart_count: i32,
}

impl AdapterState {
    pub fn new(devpath: DevPath, real_hci: RealHciIndex, virt_hci: VirtualHciIndex) -> Self {
        AdapterState {
            state: ProcessState::Off,
            devpath,
            real_hci,
            virt_hci,
            present: false,
            has_queued_present: false,
            config_enabled: false,
            pid: 0,
            restart_count: 0,
        }
    }
}

/// Internal and core implementation of the state machine.
struct StateMachineInternal {
    /// Is Floss currently enabled?
    floss_enabled: Arc<AtomicBool>,

    /// Current default adapter.
    default_adapter: Arc<AtomicI32>,

    /// Desired default adapter.
    desired_adapter: VirtualHciIndex,

    /// Keep track of per hci state. Key = hci id, Value = State. This must be a BTreeMap because
    /// we depend on ordering for |get_lowest_available_adapter|.
    state: Arc<Mutex<BTreeMap<VirtualHciIndex, AdapterState>>>,

    /// Trace the process existence for each pid file and clean it up if needed.
    process_monitor: Arc<Mutex<HashMap<String, tokio::task::JoinHandle<()>>>>,

    /// Process manager implementation.
    process_manager: Box<dyn ProcessManager + Send>,
}

#[derive(Debug, PartialEq)]
enum StateMachineTimeoutActions {
    RetryStart,
    RetryStop,
    Noop,
}

#[derive(Debug, PartialEq)]
enum CommandTimeoutAction {
    CancelTimer,
    DoNothing,
    ResetTimer,
}

/// Actions to take when the default adapter may have changed.
#[derive(Debug, PartialEq)]
enum AdapterChangeAction {
    DoNothing,
    NewDefaultAdapter(VirtualHciIndex),
}

// Core state machine implementations.
impl StateMachineInternal {
    pub fn new(
        process_manager: Box<dyn ProcessManager + Send>,
        floss_enabled: bool,
        desired_adapter: VirtualHciIndex,
    ) -> StateMachineInternal {
        StateMachineInternal {
            floss_enabled: Arc::new(AtomicBool::new(floss_enabled)),
            default_adapter: Arc::new(AtomicI32::new(desired_adapter.to_i32())),
            desired_adapter,
            state: Arc::new(Mutex::new(BTreeMap::new())),
            process_monitor: Arc::new(Mutex::new(HashMap::new())),
            process_manager: process_manager,
        }
    }

    pub(crate) fn make_process_manager(invoker: Invoker) -> Box<dyn ProcessManager + Send> {
        match invoker {
            Invoker::NativeInvoker => Box::new(NativeInvoker::new()),
            Invoker::SystemdInvoker => Box::new(SystemdInvoker::new()),
            Invoker::UpstartInvoker => Box::new(UpstartInvoker::new()),
        }
    }

    pub(crate) fn get_real_hci_by_virtual_id(&self, hci_id: VirtualHciIndex) -> RealHciIndex {
        self.state
            .lock()
            .unwrap()
            .get(&hci_id)
            .and_then(|a: &AdapterState| Some(a.real_hci))
            .unwrap_or(RealHciIndex(hci_id.to_i32()))
    }

    /// Find the virtual id of an hci device using a devpath.
    pub(crate) fn get_virtual_id_by_devpath(&self, devpath: DevPath) -> Option<VirtualHciIndex> {
        if devpath.is_empty() {
            return None;
        }

        for (k, v) in self.state.lock().unwrap().iter() {
            if v.devpath == devpath {
                return Some(k.clone());
            }
        }

        None
    }

    /// Find the virtual id of an hci device using a real hci id.
    pub(crate) fn get_virtual_id_by_real_id(&self, hci: RealHciIndex) -> Option<VirtualHciIndex> {
        for (k, v) in self.state.lock().unwrap().iter() {
            if v.real_hci == hci {
                return Some(k.clone());
            }
        }

        None
    }

    pub(crate) fn get_next_virtual_id(
        &mut self,
        real_hci: RealHciIndex,
        devpath: Option<DevPath>,
    ) -> VirtualHciIndex {
        let new_virt = match self.state.lock().unwrap().keys().next_back() {
            Some(v) => VirtualHciIndex(v.to_i32() + 1),
            None => VirtualHciIndex(0),
        };
        self.modify_state(new_virt, |a: &mut AdapterState| {
            a.real_hci = real_hci;
            if let Some(d) = devpath.as_ref() {
                a.devpath = d.clone();
            }
        });

        return new_virt;
    }

    /// Identify the virtual hci for the given real hci. We need to match both
    /// the RealHci and devpath for it to be considered a match. Update the
    /// real_hci and devpath entries for the virtual adapter where it makes sense.
    pub(crate) fn get_updated_virtual_id(
        &mut self,
        devpath: DevPath,
        real_hci: RealHciIndex,
    ) -> VirtualHciIndex {
        let by_devpath = self.get_virtual_id_by_devpath(devpath.clone());
        let by_real = self.get_virtual_id_by_real_id(real_hci);

        match (by_devpath, by_real) {
            (Some(dev), Some(real)) => {
                // Devpath matches expectations of real hci index.
                if dev == real {
                    return real;
                }

                // If dev device doesn't match real device, replace the real id
                // in non-matching entry with fake value and update devpath matching
                // one with new real hci.
                self.modify_state(dev, |a: &mut AdapterState| {
                    a.real_hci = real_hci;
                });
                self.modify_state(real, |a: &mut AdapterState| {
                    a.real_hci = RealHciIndex(INVALID_HCI_INDEX);
                });

                return dev;
            }
            (Some(dev), None) => {
                // Device found by path and needs real_hci to be updated.
                self.modify_state(dev, |a: &mut AdapterState| {
                    a.real_hci = real_hci;
                });

                return dev;
            }
            (None, Some(real)) => {
                // If the real index is found but no entry exists with that devpath,
                // this is likely because the entry was added before the devpath became known.
                if !devpath.is_empty() {
                    self.modify_state(real, |a: &mut AdapterState| {
                        a.devpath = devpath.clone();
                    });
                }

                return real;
            }
            (None, None) => {
                // This is a brand new device. Add a new virtual device with this
                // real id and devpath.
                return self.get_next_virtual_id(real_hci, Some(devpath));
            }
        };

        // match should return on all branches above.
    }

    fn is_known(&self, hci: VirtualHciIndex) -> bool {
        self.state.lock().unwrap().contains_key(&hci)
    }

    fn get_floss_enabled(&self) -> bool {
        self.floss_enabled.load(Ordering::Relaxed)
    }

    #[cfg(test)]
    fn set_floss_enabled(&mut self, enabled: bool) -> bool {
        self.floss_enabled.swap(enabled, Ordering::Relaxed)
    }

    #[cfg(test)]
    fn set_config_enabled(&mut self, hci: VirtualHciIndex, enabled: bool) {
        self.modify_state(hci, move |a: &mut AdapterState| {
            a.config_enabled = enabled;
        });
    }

    fn has_queued_present(&self, hci: VirtualHciIndex) -> bool {
        self.get_state(hci, |a: &AdapterState| Some(a.has_queued_present)).unwrap_or(false)
    }

    fn get_process_state(&self, hci: VirtualHciIndex) -> ProcessState {
        self.get_state(hci, move |a: &AdapterState| Some(a.state)).unwrap_or(ProcessState::Off)
    }

    fn get_state<T, F>(&self, hci: VirtualHciIndex, call: F) -> Option<T>
    where
        F: Fn(&AdapterState) -> Option<T>,
    {
        match self.state.lock().unwrap().get(&hci) {
            Some(a) => call(a),
            None => None,
        }
    }

    fn modify_state<F>(&mut self, hci: VirtualHciIndex, call: F)
    where
        F: Fn(&mut AdapterState),
    {
        call(&mut *self.state.lock().unwrap().entry(hci).or_insert(AdapterState::new(
            String::new(),
            RealHciIndex(hci.to_i32()),
            hci,
        )))
    }

    /// Attempt to reset an hci device. Always set the state to ProcessState::Stopped
    /// as we expect this device to disappear and reappear.
    fn reset_hci(&mut self, hci: RealHciIndex) {
        if !config_util::reset_hci_device(hci) {
            error!("Attempted reset recovery of {} and failed.", hci);
        }
    }

    /// Gets the lowest present or enabled adapter.
    fn get_lowest_available_adapter(&self) -> Option<VirtualHciIndex> {
        self.state
            .lock()
            .unwrap()
            .iter()
            // Filter to adapters that are present or enabled.
            .filter(|&(_, a)| a.present)
            .map(|(_, a)| a.virt_hci)
            .next()
    }

    /// Set the desired default adapter. Returns a NewDefaultAdapter action if the default
    /// adapter was changed as a result (meaning the newly desired adapter is either present or
    /// enabled).
    pub fn set_desired_default_adapter(&mut self, adapter: VirtualHciIndex) -> AdapterChangeAction {
        self.desired_adapter = adapter;

        // Desired adapter isn't current and it is present. It becomes the new default adapter.
        if self.default_adapter.load(Ordering::Relaxed) != adapter.to_i32()
            && self.get_state(adapter, move |a: &AdapterState| Some(a.present)).unwrap_or(false)
        {
            self.default_adapter.store(adapter.to_i32(), Ordering::Relaxed);
            return AdapterChangeAction::NewDefaultAdapter(adapter);
        }

        // Desired adapter is either current or not present|enabled so leave the previous default
        // adapter.
        return AdapterChangeAction::DoNothing;
    }

    /// Returns the next state and an action to reset timer if we are starting bluetooth process.
    pub fn action_start_bluetooth(
        &mut self,
        hci: VirtualHciIndex,
    ) -> (ProcessState, CommandTimeoutAction) {
        let state = self.get_process_state(hci);
        let present = self.get_state(hci, move |a: &AdapterState| Some(a.present)).unwrap_or(false);
        let floss_enabled = self.get_floss_enabled();

        match state {
            // If adapter is off, we should turn it on when present and floss is enabled.
            // If adapter is turning on and we get another start request, we should just
            // repeat the same action which resets the timeout mechanism.
            ProcessState::Off | ProcessState::TurningOn if present && floss_enabled => {
                self.modify_state(hci, move |s: &mut AdapterState| {
                    s.state = ProcessState::TurningOn
                });
                self.process_manager.start(hci, self.get_real_hci_by_virtual_id(hci));
                (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
            }
            // Otherwise (enabled states) no op
            _ => (state, CommandTimeoutAction::DoNothing),
        }
    }

    /// Returns the next state and an action to reset or cancel timer if we are stopping bluetooth
    /// process.
    pub fn action_stop_bluetooth(
        &mut self,
        hci: VirtualHciIndex,
    ) -> (ProcessState, CommandTimeoutAction) {
        if !self.is_known(hci) {
            warn!("Attempting to stop unknown device {}", hci);
            return (ProcessState::Off, CommandTimeoutAction::DoNothing);
        }

        let state = self.get_process_state(hci);
        match state {
            // If adapter is turning off and we get another stop request, we should just
            // repeat the same action which resets the timeout mechanism.
            ProcessState::On | ProcessState::TurningOff => {
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::TurningOff);
                self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
                (ProcessState::TurningOff, CommandTimeoutAction::ResetTimer)
            }
            // Otherwise (disabled states) no op
            _ => (state, CommandTimeoutAction::DoNothing),
        }
    }

    /// Returns the next state and an action to reset timer if we are restarting bluetooth process.
    /// This action aims to make sure the configuration is reloaded. Only TurningOn/On states are
    /// affected.
    pub fn action_restart_bluetooth(
        &mut self,
        hci: VirtualHciIndex,
    ) -> (ProcessState, CommandTimeoutAction) {
        if !self.is_known(hci) {
            warn!("Attempting to restart unknown device {}", hci);
            return (ProcessState::Off, CommandTimeoutAction::DoNothing);
        }

        let state = self.get_process_state(hci);
        let present = self.get_state(hci, move |a: &AdapterState| Some(a.present)).unwrap_or(false);
        let floss_enabled = self.get_floss_enabled();

        match state {
            ProcessState::On if present && floss_enabled => {
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::Restarting);
                self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
                (ProcessState::Restarting, CommandTimeoutAction::ResetTimer)
            }
            ProcessState::TurningOn if present && floss_enabled => {
                self.modify_state(hci, |s: &mut AdapterState| {
                    s.state = ProcessState::PendingRestart
                });
                (ProcessState::PendingRestart, CommandTimeoutAction::DoNothing)
            }
            _ => (state, CommandTimeoutAction::DoNothing),
        }
    }

    /// Returns the next state and an action. Except a restart is pending,
    /// always return the action to cancel timer even with unknown interfaces.
    pub fn action_on_bluetooth_started(
        &mut self,
        pid: i32,
        hci: VirtualHciIndex,
    ) -> (ProcessState, CommandTimeoutAction) {
        if !self.is_known(hci) {
            warn!("Unknown device {} is started; capturing that process", hci);
            self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::Off);
        }

        let state = self.get_process_state(hci);
        let present = self.get_state(hci, move |a: &AdapterState| Some(a.present)).unwrap_or(false);
        let floss_enabled = self.get_floss_enabled();

        if state == ProcessState::PendingRestart && present && floss_enabled {
            self.modify_state(hci, |s: &mut AdapterState| {
                s.state = ProcessState::Restarting;
                s.restart_count = 0;
                s.pid = pid;
            });
            self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
            return (ProcessState::Restarting, CommandTimeoutAction::ResetTimer);
        }

        self.modify_state(hci, |s: &mut AdapterState| {
            s.state = ProcessState::On;
            s.restart_count = 0;
            s.pid = pid;
        });
        (ProcessState::On, CommandTimeoutAction::CancelTimer)
    }

    /// Returns the next state and an action to cancel (turned off) or reset timer (restarting).
    /// If unexpected, Bluetooth probably crashed, returns an action to reset the timer to restart
    /// timeout.
    pub fn action_on_bluetooth_stopped(
        &mut self,
        hci: VirtualHciIndex,
    ) -> (ProcessState, CommandTimeoutAction) {
        let state = self.get_process_state(hci);
        let (present, config_enabled) = self
            .get_state(hci, move |a: &AdapterState| Some((a.present, a.config_enabled)))
            .unwrap_or((false, false));
        let floss_enabled = self.get_floss_enabled();

        match state {
            // Normal shut down behavior.
            ProcessState::TurningOff => {
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::Off);
                (ProcessState::Off, CommandTimeoutAction::CancelTimer)
            }
            ProcessState::Restarting if floss_enabled && config_enabled => {
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::TurningOn);
                self.process_manager.start(hci, self.get_real_hci_by_virtual_id(hci));
                (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
            }
            // Running bluetooth stopped unexpectedly.
            ProcessState::On if floss_enabled && config_enabled => {
                let restart_count =
                    self.get_state(hci, |a: &AdapterState| Some(a.restart_count)).unwrap_or(0);

                // If we've restarted a number of times, attempt to use the reset mechanism instead
                // of retrying a start.
                if restart_count >= RESET_ON_RESTART_COUNT {
                    warn!(
                        "{} stopped unexpectedly. After {} restarts, trying a reset recovery.",
                        hci, restart_count
                    );
                    // Reset the restart count since we're attempting a reset now.
                    self.modify_state(hci, |s: &mut AdapterState| {
                        s.state = ProcessState::Off;
                        s.restart_count = 0;
                    });
                    let real_hci = self
                        .get_state(hci, |a: &AdapterState| Some(a.real_hci))
                        .unwrap_or(RealHciIndex(hci.to_i32()));
                    self.reset_hci(real_hci);
                    (ProcessState::Off, CommandTimeoutAction::CancelTimer)
                } else {
                    warn!(
                        "{} stopped unexpectedly, try restarting (attempt #{})",
                        hci,
                        restart_count + 1
                    );
                    self.modify_state(hci, |s: &mut AdapterState| {
                        s.state = ProcessState::TurningOn;
                        s.restart_count = s.restart_count + 1;
                    });
                    self.process_manager.start(hci, self.get_real_hci_by_virtual_id(hci));
                    (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
                }
            }
            _ => {
                warn!(
                    "{} stopped unexpectedly from {:?}. Adapter present={}, Floss enabled={}",
                    hci, state, present, floss_enabled
                );
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::Off);
                (ProcessState::Off, CommandTimeoutAction::CancelTimer)
            }
        }
    }

    /// Triggered on Bluetooth start/stop timeout. Return the actions that the
    /// state machine has taken, for the external context to reset the timer.
    pub fn action_on_command_timeout(
        &mut self,
        hci: VirtualHciIndex,
    ) -> StateMachineTimeoutActions {
        let state = self.get_process_state(hci);
        let floss_enabled = self.get_floss_enabled();
        let (present, config_enabled) = self
            .get_state(hci, |a: &AdapterState| Some((a.present, a.config_enabled)))
            .unwrap_or((false, false));

        match state {
            // If Floss is not enabled, just send |Stop| to process manager and end the state
            // machine actions.
            ProcessState::TurningOn | ProcessState::PendingRestart if !floss_enabled => {
                warn!("{}: Timed out turning on but floss is disabled", hci);
                self.modify_state(hci, |s: &mut AdapterState| s.state = ProcessState::Off);
                self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
                StateMachineTimeoutActions::Noop
            }
            // If turning on and hci is enabled, restart the process if we are below
            // the restart count. Otherwise, reset and mark turned off.
            ProcessState::TurningOn | ProcessState::PendingRestart if config_enabled => {
                let restart_count =
                    self.get_state(hci, |a: &AdapterState| Some(a.restart_count)).unwrap_or(0);

                // If we've restarted a number of times, attempt to use the reset mechanism instead
                // of retrying a start.
                if restart_count >= RESET_ON_RESTART_COUNT {
                    warn!(
                        "{} timed out while starting (present={}). After {} restarts, trying a reset recovery.",
                        hci, present, restart_count
                    );
                    // Reset the restart count since we're attempting a reset now.
                    self.modify_state(hci, |s: &mut AdapterState| {
                        s.state = ProcessState::Off;
                        s.restart_count = 0;
                    });
                    let real_hci = self
                        .get_state(hci, |s: &AdapterState| Some(s.real_hci))
                        .unwrap_or(RealHciIndex(hci.to_i32()));
                    self.reset_hci(real_hci);
                    StateMachineTimeoutActions::Noop
                } else {
                    warn!(
                        "{} timed out while starting (present={}), try restarting (attempt #{})",
                        hci,
                        present,
                        restart_count + 1
                    );
                    self.modify_state(hci, |s: &mut AdapterState| {
                        s.state = ProcessState::TurningOn;
                        s.restart_count = s.restart_count + 1;
                    });
                    self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
                    self.process_manager.start(hci, self.get_real_hci_by_virtual_id(hci));
                    StateMachineTimeoutActions::RetryStart
                }
            }
            ProcessState::TurningOff | ProcessState::Restarting => {
                info!("Killing bluetooth {}", hci);
                self.process_manager.stop(hci, self.get_real_hci_by_virtual_id(hci));
                StateMachineTimeoutActions::RetryStop
            }
            _ => StateMachineTimeoutActions::Noop,
        }
    }

    /// Handle when an hci device presence has changed.
    ///
    /// This will start adapters that are configured to be enabled if the presence is newly added.
    ///
    /// # Return
    /// Target process state.
    pub fn action_on_hci_presence_changed(
        &mut self,
        hci: VirtualHciIndex,
        present: bool,
    ) -> (ProcessState, AdapterChangeAction, CommandTimeoutAction) {
        let prev_present = self.get_state(hci, |a: &AdapterState| Some(a.present)).unwrap_or(false);
        let prev_state = self.get_process_state(hci);

        // No-op if same as previous present.
        if prev_present == present {
            return (prev_state, AdapterChangeAction::DoNothing, CommandTimeoutAction::DoNothing);
        }

        self.modify_state(hci, |a: &mut AdapterState| a.present = present);
        let floss_enabled = self.get_floss_enabled();

        let (next_state, timeout_action) =
            match self.get_state(hci, |a: &AdapterState| Some((a.state, a.config_enabled))) {
                // Start the adapter if present, config is enabled and floss is enabled.
                Some((ProcessState::Off, true)) if floss_enabled && present => {
                    // Restart count will increment for each time a Start doesn't succeed.
                    // Going from `off` -> `turning on` here usually means either
                    // a) Recovery from a previously unstartable state.
                    // b) Fresh device.
                    // Both should reset the restart count.
                    self.modify_state(hci, |a: &mut AdapterState| a.restart_count = 0);

                    self.action_start_bluetooth(hci)
                }
                _ => (prev_state, CommandTimeoutAction::DoNothing),
            };

        let default_adapter = VirtualHciIndex(self.default_adapter.load(Ordering::Relaxed));
        let desired_adapter = self.desired_adapter;

        // Two scenarios here:
        //   1) The newly present adapter is the desired adapter.
        //      * Switch to it immediately as the default adapter.
        //   2) The current default adapter is no longer present or enabled.
        //      * Switch to the lowest numbered adapter present or do nothing.
        //
        let adapter_change_action = if present && hci == desired_adapter && hci != default_adapter {
            AdapterChangeAction::NewDefaultAdapter(desired_adapter)
        } else if !present && hci == default_adapter {
            match self.get_lowest_available_adapter() {
                Some(v) => AdapterChangeAction::NewDefaultAdapter(v),
                None => AdapterChangeAction::DoNothing,
            }
        } else {
            AdapterChangeAction::DoNothing
        };

        (next_state, adapter_change_action, timeout_action)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    #[derive(Debug, PartialEq)]
    enum ExecutedCommand {
        Start,
        Stop,
    }

    struct MockProcessManager {
        last_command: VecDeque<ExecutedCommand>,
        expectations: Vec<Option<String>>,
    }

    impl MockProcessManager {
        fn new() -> MockProcessManager {
            MockProcessManager { last_command: VecDeque::new(), expectations: Vec::new() }
        }

        fn expect_start(&mut self) {
            self.last_command.push_back(ExecutedCommand::Start);
        }

        fn expect_stop(&mut self) {
            self.last_command.push_back(ExecutedCommand::Stop);
        }
    }

    impl ProcessManager for MockProcessManager {
        fn start(&mut self, _virt: VirtualHciIndex, _real: RealHciIndex) {
            self.expectations.push(match self.last_command.pop_front() {
                Some(x) => {
                    if x == ExecutedCommand::Start {
                        None
                    } else {
                        Some(format!("Got [Start], Expected: [{:?}]", x))
                    }
                }
                None => Some(format!("Got [Start], Expected: None")),
            });
        }

        fn stop(&mut self, _virt: VirtualHciIndex, _real: RealHciIndex) {
            self.expectations.push(match self.last_command.pop_front() {
                Some(x) => {
                    if x == ExecutedCommand::Stop {
                        None
                    } else {
                        Some(format!("Got [Stop], Expected: [{:?}]", x))
                    }
                }
                None => Some(format!("Got [Stop], Expected: None")),
            });
        }
    }

    impl Drop for MockProcessManager {
        fn drop(&mut self) {
            assert_eq!(self.last_command.len(), 0);
            let exp: &[String] = &[];
            // Check that we had 0 false expectations.
            assert_eq!(
                self.expectations
                    .iter()
                    .filter(|&v| !v.is_none())
                    .map(|v| v.as_ref().unwrap().clone())
                    .collect::<Vec<String>>()
                    .as_slice(),
                exp
            );
        }
    }

    // For tests, this is the default adapter we want
    const DEFAULT_ADAPTER: VirtualHciIndex = VirtualHciIndex(0);
    const ALT_ADAPTER: VirtualHciIndex = VirtualHciIndex(1);

    fn make_state_machine(process_manager: MockProcessManager) -> StateMachineInternal {
        let state_machine =
            StateMachineInternal::new(Box::new(process_manager), true, DEFAULT_ADAPTER);
        state_machine
    }

    #[test]
    fn initial_state_is_off() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let process_manager = MockProcessManager::new();
            let state_machine = make_state_machine(process_manager);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        })
    }

    #[test]
    fn off_turnoff_should_noop() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let process_manager = MockProcessManager::new();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        })
    }

    #[test]
    fn off_turnon_should_turningon() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            // Expect to send start command
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        })
    }

    #[test]
    fn turningon_turnon_again_resends_start() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            // Expect to send start command just once
            process_manager.expect_start();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(
                state_machine.action_start_bluetooth(DEFAULT_ADAPTER),
                (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
            );
        })
    }

    #[test]
    fn turningon_bluetooth_started() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::On);
        })
    }

    #[test]
    fn turningon_bluetooth_different_hci_started() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(ALT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(ALT_ADAPTER);
            state_machine.action_on_bluetooth_started(1, ALT_ADAPTER);
            assert_eq!(state_machine.get_process_state(ALT_ADAPTER), ProcessState::On);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        })
    }

    #[test]
    fn turningon_timeout() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            process_manager.expect_stop();
            process_manager.expect_start(); // start bluetooth again
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(
                state_machine.action_on_command_timeout(DEFAULT_ADAPTER),
                StateMachineTimeoutActions::RetryStart
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        })
    }

    #[test]
    fn turningon_turnoff_should_noop() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        })
    }

    #[test]
    fn on_turnoff_should_turningoff_and_send_command() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            // Expect to send stop command
            process_manager.expect_stop();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOff);
        })
    }

    #[test]
    fn on_bluetooth_stopped_multicase() {
        // Normal bluetooth stopped should restart.
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            // Expect to start again
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            assert_eq!(
                state_machine.action_on_bluetooth_stopped(DEFAULT_ADAPTER),
                (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        });

        // Stopped with no presence should restart if config enabled.
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            // Expect to start again.
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, false);
            assert_eq!(
                state_machine.action_on_bluetooth_stopped(DEFAULT_ADAPTER),
                (ProcessState::TurningOn, CommandTimeoutAction::ResetTimer)
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        });

        // If floss was disabled and we see stopped, we shouldn't restart.
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.set_floss_enabled(false);
            assert_eq!(
                state_machine.action_on_bluetooth_stopped(DEFAULT_ADAPTER),
                (ProcessState::Off, CommandTimeoutAction::CancelTimer)
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        });
    }

    #[test]
    fn turningoff_bluetooth_down_should_off() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            process_manager.expect_stop();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_stopped(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        })
    }

    #[test]
    fn restart_bluetooth() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            process_manager.expect_stop();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_stopped(DEFAULT_ADAPTER);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::On);
        })
    }

    #[test]
    fn start_bluetooth_without_device_fails() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let process_manager = MockProcessManager::new();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        });
    }

    #[test]
    fn start_bluetooth_without_floss_fails() {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let process_manager = MockProcessManager::new();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.set_floss_enabled(false);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        });
    }

    #[test]
    fn on_timeout_multicase() {
        // If a timeout occurs while turning on or off with floss enabled..
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            // Expect a stop and start for timeout.
            process_manager.expect_stop();
            process_manager.expect_start();
            // Expect another stop for stop timeout.
            process_manager.expect_stop();
            process_manager.expect_stop();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
            assert_eq!(
                state_machine.action_on_command_timeout(DEFAULT_ADAPTER),
                StateMachineTimeoutActions::RetryStart
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
            state_machine.action_on_bluetooth_started(0, DEFAULT_ADAPTER);
            state_machine.action_stop_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(
                state_machine.action_on_command_timeout(DEFAULT_ADAPTER),
                StateMachineTimeoutActions::RetryStop
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOff);
        });

        // If a timeout occurs during turning on and floss is disabled, stop the adapter.
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            // Expect a stop for timeout since floss is disabled.
            process_manager.expect_stop();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
            state_machine.set_floss_enabled(false);
            assert_eq!(
                state_machine.action_on_command_timeout(DEFAULT_ADAPTER),
                StateMachineTimeoutActions::Noop
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::Off);
        });

        // If a timeout occurs during TurningOn phase, use config_enabled to decide eventual state.
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut process_manager = MockProcessManager::new();
            process_manager.expect_start();
            process_manager.expect_stop();
            process_manager.expect_start();
            let mut state_machine = make_state_machine(process_manager);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, true);
            state_machine.set_config_enabled(DEFAULT_ADAPTER, true);
            state_machine.action_start_bluetooth(DEFAULT_ADAPTER);
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
            state_machine.action_on_hci_presence_changed(DEFAULT_ADAPTER, false);
            assert_eq!(
                state_machine.action_on_command_timeout(DEFAULT_ADAPTER),
                StateMachineTimeoutActions::RetryStart
            );
            assert_eq!(state_machine.get_process_state(DEFAULT_ADAPTER), ProcessState::TurningOn);
        });
    }

    #[test]
    fn test_updated_virtual_id() {
        let process_manager = MockProcessManager::new();
        let mut state_machine = make_state_machine(process_manager);

        // Note: Test ordering matters here. When re-ordering, keep track of what
        // the previous and next states are expected to be. Cases below will also
        // denote which match arm it's trying to test.

        // Case #1: (None, None)
        // Insert a devpath + real index at 0. Expect virtual index of 0.
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt0".into(), RealHciIndex(0)),
            VirtualHciIndex(0)
        );

        // Case #2: (None, None)
        // Inserting a real index of 2 will still get you a virtual index of 1.
        // We insert in increasing order.
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt1".into(), RealHciIndex(2)),
            VirtualHciIndex(1)
        );

        // Case #3: (Some(dev), None)
        // Inserting a new real hci for an existing devpath should return the same virtual index.
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt0".into(), RealHciIndex(12)),
            VirtualHciIndex(0)
        );
        assert_eq!(
            Some(RealHciIndex(12)),
            state_machine.get_state(VirtualHciIndex(0), |a: &AdapterState| Some(a.real_hci))
        );

        // Case #4: (Some(dev), Some(real)) if dev == real
        // When devpath and real hci match, expect a stable virtual index.
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt0".into(), RealHciIndex(12)),
            VirtualHciIndex(0)
        );

        // Case #5: (None, None) and (None, Some(real))
        // If we inserted previously without a devpath, assign this devpath to the index.
        assert_eq!(
            state_machine.get_updated_virtual_id(String::new(), RealHciIndex(0)),
            VirtualHciIndex(2)
        );
        assert_eq!(
            Some(String::new()),
            state_machine.get_state(VirtualHciIndex(2), |a: &AdapterState| Some(a.devpath.clone()))
        );
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt2".into(), RealHciIndex(0)),
            VirtualHciIndex(2)
        );
        assert_eq!(
            Some("/fake/bt2".into()),
            state_machine.get_state(VirtualHciIndex(2), |a: &AdapterState| Some(a.devpath.clone()))
        );

        // Case #6: (Some(dev), Some(real)) if dev != real
        // We always prefer the virtual index pointed to by the devpath.
        assert_eq!(
            state_machine.get_updated_virtual_id("/fake/bt0".into(), RealHciIndex(0)),
            VirtualHciIndex(0)
        );
        assert_eq!(
            Some("/fake/bt0".to_string()),
            state_machine.get_state(VirtualHciIndex(0), |a: &AdapterState| Some(a.devpath.clone()))
        );
        assert_eq!(
            Some(RealHciIndex(0)),
            state_machine.get_state(VirtualHciIndex(0), |a: &AdapterState| Some(a.real_hci))
        );
        assert_eq!(
            Some(RealHciIndex(INVALID_HCI_INDEX)),
            state_machine.get_state(VirtualHciIndex(2), |a: &AdapterState| Some(a.real_hci))
        );
    }

    #[test]
    fn path_to_pid() {
        assert_eq!(
            get_hci_index_from_pid_path("/var/run/bluetooth/bluetooth0.pid"),
            Some(VirtualHciIndex(0))
        );
        assert_eq!(
            get_hci_index_from_pid_path("/var/run/bluetooth/bluetooth1.pid"),
            Some(VirtualHciIndex(1))
        );
        assert_eq!(
            get_hci_index_from_pid_path("/var/run/bluetooth/bluetooth10.pid"),
            Some(VirtualHciIndex(10))
        );
        assert_eq!(get_hci_index_from_pid_path("/var/run/bluetooth/garbage"), None);
    }
}
