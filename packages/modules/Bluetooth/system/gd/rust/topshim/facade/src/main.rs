//! Starts the facade services that allow us to test the Bluetooth stack

use bt_topshim::btif;

use clap::{value_parser, Arg, Command};
use futures::channel::mpsc;
use futures::executor::block_on;
use futures::stream::StreamExt;
use grpcio::*;
use lazy_static::lazy_static;
use log::debug;
use nix::sys::signal;
use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;

mod adapter_service;
mod gatt_service;
mod hf_client_service;
mod hfp_service;
mod media_service;
mod security_service;
mod utils;

// This is needed for linking, libbt_shim_bridge needs symbols defined by
// bt_shim, however bt_shim depends on rust crates (future, tokio) that
// we use too, if we build and link them separately we ends with duplicate
// symbols. To solve that we build bt_shim with bt_topshim_facade so the rust
// compiler share the transitive dependencies.
//
// The `::*` is here to circuvent the single_component_path_imports from
// clippy that is denied on the rust command line so we can't just allow it.
// This is fine for now since bt_shim doesn't export anything
#[allow(unused)]
use bluetooth_core_rs_for_facade::*;
#[allow(unused)]
use bt_shim::*;

fn main() {
    // SAFETY: There is no signal handler installed before this.
    let sigint = unsafe { install_sigint() };
    bt_common::init_logging();
    let rt = Arc::new(Runtime::new().unwrap());
    rt.block_on(async_main(Arc::clone(&rt), sigint));
}

fn clap_command() -> Command {
    Command::new("bluetooth_topshim_facade")
        .about("The bluetooth topshim stack, with testing facades enabled and exposed via gRPC.")
        .arg(
            Arg::new("grpc-port")
                .long("grpc-port")
                .value_parser(value_parser!(u16))
                .default_value("8899"),
        )
        .arg(
            Arg::new("root-server-port")
                .long("root-server-port")
                .value_parser(value_parser!(u16))
                .default_value("8897"),
        )
        .arg(
            Arg::new("signal-port")
                .long("signal-port")
                .value_parser(value_parser!(u16))
                .default_value("8895"),
        )
        .arg(Arg::new("rootcanal-port").long("rootcanal-port").value_parser(value_parser!(u16)))
        .arg(Arg::new("btsnoop").long("btsnoop"))
        .arg(Arg::new("btsnooz").long("btsnooz"))
        .arg(Arg::new("btconfig").long("btconfig"))
        .arg(
            Arg::new("start-stack-now")
                .long("start-stack-now")
                .value_parser(value_parser!(bool))
                .default_value("true"),
        )
}

async fn async_main(rt: Arc<Runtime>, mut sigint: mpsc::UnboundedReceiver<()>) {
    let matches = clap_command().get_matches();

    let grpc_port = *matches.get_one::<u16>("grpc-port").unwrap();
    let _rootcanal_port = matches.get_one::<u16>("rootcanal-port").cloned();
    let env = Arc::new(Environment::new(2));

    let btif_intf = Arc::new(Mutex::new(btif::get_btinterface().unwrap()));

    // AdapterServiceImpl::create initializes the stack; not the best practice because the side effect is hidden
    let adapter_service_impl =
        adapter_service::AdapterServiceImpl::create(rt.clone(), btif_intf.clone());

    let security_service_impl =
        security_service::SecurityServiceImpl::create(rt.clone(), btif_intf.clone());

    let gatt_service_impl = gatt_service::GattServiceImpl::create(rt.clone(), btif_intf.clone());

    let hf_client_service_impl =
        hf_client_service::HfClientServiceImpl::create(rt.clone(), btif_intf.clone());

    let hfp_service_impl = hfp_service::HfpServiceImpl::create(rt.clone(), btif_intf.clone());

    let media_service_impl = media_service::MediaServiceImpl::create(rt.clone(), btif_intf.clone());

    let start_stack_now = *matches.get_one::<bool>("start-stack-now").unwrap();

    if start_stack_now {
        btif_intf.clone().lock().unwrap().enable();
    }

    let mut server = ServerBuilder::new(env)
        .register_service(adapter_service_impl)
        .register_service(security_service_impl)
        .register_service(gatt_service_impl)
        .register_service(hf_client_service_impl)
        .register_service(hfp_service_impl)
        .register_service(media_service_impl)
        .build()
        .unwrap();
    let addr = format!("0.0.0.0:{}", grpc_port);
    let creds = ServerCredentials::insecure();
    server.add_listening_port(addr, creds).unwrap();
    server.start();

    sigint.next().await;
    block_on(server.shutdown()).unwrap();
}

// TODO: remove as this is a temporary nix-based hack to catch SIGINT
/// # Safety
///
/// The old signal handler, if any, must be installed correctly.
unsafe fn install_sigint() -> mpsc::UnboundedReceiver<()> {
    let (tx, rx) = mpsc::unbounded();
    *SIGINT_TX.lock().unwrap() = Some(tx);

    let sig_action = signal::SigAction::new(
        signal::SigHandler::Handler(handle_sigint),
        signal::SaFlags::empty(),
        signal::SigSet::empty(),
    );
    // SAFETY: The caller guarantees that the old signal handler was installed correctly.
    // TODO(b/292218119): Make sure `handle_sigint` only makes system calls that are safe for signal
    // handlers, and only accesses global state through atomics. In particular, it must not take any
    // shared locks.
    unsafe {
        signal::sigaction(signal::SIGINT, &sig_action).unwrap();
    }

    rx
}

lazy_static! {
    static ref SIGINT_TX: Mutex<Option<mpsc::UnboundedSender<()>>> = Mutex::new(None);
}

extern "C" fn handle_sigint(_: i32) {
    let mut sigint_tx = SIGINT_TX.lock().unwrap();
    if let Some(tx) = &*sigint_tx {
        debug!("Stopping gRPC root server due to SIGINT");
        tx.unbounded_send(()).unwrap();
    }
    *sigint_tx = None;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verify_comand() {
        clap_command().debug_assert();
    }
}
