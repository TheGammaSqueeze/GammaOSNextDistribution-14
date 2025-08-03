//! Anything related to the Qualification API (IBluetoothQA).

use crate::callbacks::Callbacks;
use crate::{bluetooth::FLOSS_VER, Message, RPCProxy};
use bt_topshim::btif::{BtDiscMode, BtStatus};
use bt_topshim::profiles::hid_host::BthhReportType;
use tokio::sync::mpsc::Sender;

/// Defines the Qualification API
pub trait IBluetoothQA {
    /// Register client callback
    fn register_qa_callback(&mut self, callback: Box<dyn IBluetoothQACallback + Send>) -> u32;
    /// Unregister a client callback
    fn unregister_qa_callback(&mut self, callback_id: u32) -> bool;
    /// Register a media player
    fn add_media_player(&self, name: String, browsing_supported: bool);
    /// Send RFCOMM MSC command to the remote
    fn rfcomm_send_msc(&self, dlci: u8, addr: String);
    /// Fetch adapter's discoverable mode.
    /// Result will be returned in the callback |OnFetchDiscoverableModeComplete|
    fn fetch_discoverable_mode(&self);
    /// Fetch adapter's connectable mode.
    /// Result will be returned in the callback |OnFetchConnectableComplete|
    fn fetch_connectable(&self);
    /// Set adapter's connectable mode.
    /// Result will be returned in the callback |OnSetConnectableComplete|
    fn set_connectable(&self, mode: bool);
    /// Fetch the adapter's Bluetooth friendly name.
    /// Result will be returned in the callback |OnFetchAliasComplete|
    fn fetch_alias(&self);
    /// Returns the adapter's Device ID information in modalias format
    /// used by the kernel and udev.
    fn get_modalias(&self) -> String;
    /// Gets HID report on the peer.
    /// Result will be returned in the callback |OnGetHIDReportComplete|
    fn get_hid_report(&self, addr: String, report_type: BthhReportType, report_id: u8);
    /// Sets HID report to the peer.
    /// Result will be returned in the callback |OnSetHIDReportComplete|
    fn set_hid_report(&self, addr: String, report_type: BthhReportType, report: String);
    /// Snd HID data report to the peer.
    /// Result will be returned in the callback |OnSendHIDDataComplete|
    fn send_hid_data(&self, addr: String, data: String);
}

pub trait IBluetoothQACallback: RPCProxy {
    fn on_fetch_discoverable_mode_completed(&mut self, mode: BtDiscMode);
    fn on_fetch_connectable_completed(&mut self, connectable: bool);
    fn on_set_connectable_completed(&mut self, succeed: bool);
    fn on_fetch_alias_completed(&mut self, alias: String);
    fn on_get_hid_report_completed(&mut self, status: BtStatus);
    fn on_set_hid_report_completed(&mut self, status: BtStatus);
    fn on_send_hid_data_completed(&mut self, status: BtStatus);
}

pub struct BluetoothQA {
    tx: Sender<Message>,
    callbacks: Callbacks<dyn IBluetoothQACallback + Send>,
}

impl BluetoothQA {
    pub fn new(tx: Sender<Message>) -> BluetoothQA {
        BluetoothQA {
            tx: tx.clone(),
            callbacks: Callbacks::new(tx.clone(), Message::QaCallbackDisconnected),
        }
    }
    pub fn on_fetch_discoverable_mode_completed(&mut self, mode: BtDiscMode) {
        self.callbacks.for_all_callbacks(|cb| {
            cb.on_fetch_discoverable_mode_completed(mode.clone());
        });
    }
    pub fn on_fetch_connectable_completed(&mut self, connectable: bool) {
        self.callbacks.for_all_callbacks(|cb| {
            cb.on_fetch_connectable_completed(connectable);
        });
    }
    pub fn on_set_connectable_completed(&mut self, succeed: bool) {
        self.callbacks.for_all_callbacks(|cb: &mut Box<dyn IBluetoothQACallback + Send>| {
            cb.on_set_connectable_completed(succeed);
        });
    }
    pub fn on_fetch_alias_completed(&mut self, alias: String) {
        self.callbacks.for_all_callbacks(|cb: &mut Box<dyn IBluetoothQACallback + Send>| {
            cb.on_fetch_alias_completed(alias.clone());
        });
    }
    pub fn on_get_hid_report_completed(&mut self, status: BtStatus) {
        self.callbacks.for_all_callbacks(|cb: &mut Box<dyn IBluetoothQACallback + Send>| {
            cb.on_get_hid_report_completed(status);
        });
    }
    pub fn on_set_hid_report_completed(&mut self, status: BtStatus) {
        self.callbacks.for_all_callbacks(|cb: &mut Box<dyn IBluetoothQACallback + Send>| {
            cb.on_set_hid_report_completed(status);
        });
    }
    pub fn on_send_hid_data_completed(&mut self, status: BtStatus) {
        self.callbacks.for_all_callbacks(|cb: &mut Box<dyn IBluetoothQACallback + Send>| {
            cb.on_send_hid_data_completed(status);
        });
    }
}

impl IBluetoothQA for BluetoothQA {
    fn register_qa_callback(&mut self, callback: Box<dyn IBluetoothQACallback + Send>) -> u32 {
        self.callbacks.add_callback(callback)
    }

    fn unregister_qa_callback(&mut self, callback_id: u32) -> bool {
        self.callbacks.remove_callback(callback_id)
    }
    fn add_media_player(&self, name: String, browsing_supported: bool) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaAddMediaPlayer(name, browsing_supported)).await;
        });
    }
    fn rfcomm_send_msc(&self, dlci: u8, addr: String) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaRfcommSendMsc(dlci, addr)).await;
        });
    }
    fn fetch_discoverable_mode(&self) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaFetchDiscoverableMode).await;
        });
    }
    fn fetch_connectable(&self) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaFetchConnectable).await;
        });
    }
    fn set_connectable(&self, mode: bool) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaSetConnectable(mode)).await;
        });
    }
    fn fetch_alias(&self) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaFetchAlias).await;
        });
    }
    fn get_modalias(&self) -> String {
        format!("bluetooth:v00E0pC405d{:04x}", FLOSS_VER)
    }
    fn get_hid_report(&self, addr: String, report_type: BthhReportType, report_id: u8) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaGetHidReport(addr, report_type, report_id)).await;
        });
    }
    fn set_hid_report(&self, addr: String, report_type: BthhReportType, report: String) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaSetHidReport(addr, report_type, report)).await;
        });
    }
    fn send_hid_data(&self, addr: String, data: String) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::QaSendHidData(addr, data)).await;
        });
    }
}
