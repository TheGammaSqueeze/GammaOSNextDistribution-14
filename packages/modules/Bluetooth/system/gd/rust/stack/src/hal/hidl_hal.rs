//! Implementation of the HAl that talks to BT controller over Android's HIDL
use crate::hal::internal::{InnerHal, RawHal};
use bt_packets::hci::{Acl, Command, Event, Iso, Sco};
use gddi::{module, provides};
use lazy_static::lazy_static;
use pdl_runtime::Packet;
use std::sync::Arc;
use std::sync::Mutex;
use tokio::runtime::Runtime;
use tokio::select;
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};

module! {
    hidl_hal_module,
    providers {
        RawHal => provide_hidl_hal,
    }
}

#[provides]
async fn provide_hidl_hal(rt: Arc<Runtime>) -> RawHal {
    let (raw_hal, inner_hal) = InnerHal::new();
    let (init_tx, mut init_rx) = unbounded_channel();
    *CALLBACKS.lock().unwrap() = Some(Callbacks {
        init_tx,
        evt_tx: inner_hal.evt_tx,
        acl_tx: inner_hal.acl_tx,
        iso_tx: inner_hal.iso_tx,
        sco_tx: inner_hal.sco_tx,
    });
    ffi::start_hal();
    init_rx.recv().await.unwrap();

    rt.spawn(dispatch_outgoing(
        inner_hal.cmd_rx,
        inner_hal.acl_rx,
        inner_hal.iso_rx,
        inner_hal.sco_rx,
    ));

    raw_hal
}

#[cxx::bridge(namespace = bluetooth::hal)]
// TODO Either use or remove these functions, this shouldn't be the long term state
#[allow(dead_code)]
#[allow(unsafe_op_in_unsafe_fn)]
mod ffi {
    unsafe extern "C++" {
        include!("src/hal/ffi/hidl.h");
        fn start_hal();
        fn stop_hal();
        fn send_command(data: &[u8]);
        fn send_acl(data: &[u8]);
        fn send_sco(data: &[u8]);
        fn send_iso(data: &[u8]);
    }

    extern "Rust" {
        fn on_init_complete();
        fn on_event(data: &[u8]);
        fn on_acl(data: &[u8]);
        fn on_sco(data: &[u8]);
        fn on_iso(data: &[u8]);
    }
}

struct Callbacks {
    init_tx: UnboundedSender<()>,
    evt_tx: UnboundedSender<Event>,
    acl_tx: UnboundedSender<Acl>,
    iso_tx: UnboundedSender<Iso>,
    sco_tx: UnboundedSender<Sco>,
}

lazy_static! {
    static ref CALLBACKS: Mutex<Option<Callbacks>> = Mutex::new(None);
}

fn on_init_complete() {
    let callbacks = CALLBACKS.lock().unwrap();
    callbacks.as_ref().unwrap().init_tx.send(()).unwrap();
}

fn on_event(data: &[u8]) {
    log::error!("got event: {:02x?}", data);
    let callbacks = CALLBACKS.lock().unwrap();
    match Event::parse(data) {
        Ok(p) => callbacks.as_ref().unwrap().evt_tx.send(p).unwrap(),
        Err(e) => log::error!("failure to parse event: {:?} data: {:02x?}", e, data),
    }
}

fn on_acl(data: &[u8]) {
    let callbacks = CALLBACKS.lock().unwrap();
    match Acl::parse(data) {
        Ok(p) => callbacks.as_ref().unwrap().acl_tx.send(p).unwrap(),
        Err(e) => log::error!("failure to parse incoming ACL: {:?} data: {:02x?}", e, data),
    }
}

fn on_sco(data: &[u8]) {
    let callbacks = CALLBACKS.lock().unwrap();
    match Sco::parse(data) {
        Ok(p) => callbacks.as_ref().unwrap().sco_tx.send(p).unwrap(),
        Err(e) => log::error!("failure to parse incoming SCO: {:?} data: {:02x?}", e, data),
    }
}

fn on_iso(data: &[u8]) {
    let callbacks = CALLBACKS.lock().unwrap();
    match Iso::parse(data) {
        Ok(p) => callbacks.as_ref().unwrap().iso_tx.send(p).unwrap(),
        Err(e) => log::error!("failure to parse incoming ISO: {:?} data: {:02x?}", e, data),
    }
}

async fn dispatch_outgoing(
    mut cmd_rx: UnboundedReceiver<Command>,
    mut acl_rx: UnboundedReceiver<Acl>,
    mut iso_rx: UnboundedReceiver<Iso>,
    mut sco_rx: UnboundedReceiver<Sco>,
) {
    loop {
        select! {
            Some(cmd) = cmd_rx.recv() => ffi::send_command(&cmd.to_bytes()),
            Some(acl) = acl_rx.recv() => ffi::send_acl(&acl.to_bytes()),
            Some(iso) = iso_rx.recv() => ffi::send_iso(&iso.to_bytes()),
            Some(sco) = sco_rx.recv() => ffi::send_sco(&sco.to_bytes()),
            else => break,
        }
    }
}
