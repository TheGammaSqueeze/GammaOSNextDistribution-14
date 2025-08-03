//! D-Bus proxy implementations of the APIs.

use btstack::suspend::{ISuspend, ISuspendCallback, SuspendType};
use btstack::RPCProxy;
use dbus::nonblock::SyncConnection;
use dbus_macros::{dbus_method, generate_dbus_exporter, generate_dbus_interface_client};
use dbus_projection::prelude::*;
use num_traits::{FromPrimitive, ToPrimitive};
use std::sync::Arc;

use crate::dbus_arg::{DBusArg, DBusArgError};

impl_dbus_arg_enum!(SuspendType);

#[derive(Clone)]
pub struct SuspendDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub struct SuspendDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: SuspendDBusRPC,
}

impl SuspendDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, path: dbus::Path<'static>) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            path,
            String::from("org.chromium.bluetooth.Suspend"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, path: dbus::Path<'static>) -> SuspendDBus {
        SuspendDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), path.clone()),
            rpc: SuspendDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), path.clone()),
            },
        }
    }
}

#[generate_dbus_interface_client(SuspendDBusRPC)]
impl ISuspend for SuspendDBus {
    #[dbus_method("RegisterCallback", DBusLog::Disable)]
    fn register_callback(&mut self, callback: Box<dyn ISuspendCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Suspend")]
    fn suspend(&mut self, _suspend_type: SuspendType, suspend_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("Resume")]
    fn resume(&mut self) -> bool {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct ISuspendCallbackDBus {}

impl RPCProxy for ISuspendCallbackDBus {}

#[generate_dbus_exporter(
    export_suspend_callback_dbus_intf,
    "org.chromium.bluetooth.SuspendCallback"
)]
impl ISuspendCallback for ISuspendCallbackDBus {
    #[dbus_method("OnCallbackRegistered")]
    fn on_callback_registered(&mut self, callback_id: u32) {}
    #[dbus_method("OnSuspendReady")]
    fn on_suspend_ready(&mut self, suspend_id: i32) {}
    #[dbus_method("OnResumed")]
    fn on_resumed(&mut self, suspend_id: i32) {}
}
