//! HFP service facade

use bt_topshim::btif::{BluetoothInterface, RawAddress, ToggleableProfile};
use bt_topshim::profiles::hfp::{Hfp, HfpCallbacks, HfpCallbacksDispatcher};
use bt_topshim_facade_protobuf::empty::Empty;
use bt_topshim_facade_protobuf::facade::{
    ConnectAudioRequest, DisconnectAudioRequest, EventType, FetchEventsRequest,
    FetchEventsResponse, SetVolumeRequest, StartSlcRequest, StopSlcRequest,
};
use bt_topshim_facade_protobuf::facade_grpc::{create_hfp_service, HfpService};
use futures::sink::SinkExt;
use grpcio::*;

use std::str::from_utf8;
use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;
use tokio::sync::mpsc;

fn get_hfp_dispatcher(
    _hfp: Arc<Mutex<Hfp>>,
    tx: Arc<Mutex<Option<mpsc::Sender<HfpCallbacks>>>>,
) -> HfpCallbacksDispatcher {
    HfpCallbacksDispatcher {
        dispatch: Box::new(move |cb: HfpCallbacks| {
            println!("Hfp Callback found {:?}", cb);
            if let HfpCallbacks::ConnectionState(state, address) = &cb {
                println!("Hfp Connection state changed to {:?} for address {:?}", state, address);
            }
            let guard_tx = tx.lock().unwrap();
            if let Some(event_tx) = guard_tx.as_ref() {
                let txclone = event_tx.clone();
                if txclone.try_send(cb.clone()).is_err() {
                    println!("Cannot send event {:?}", cb);
                }
                /*tokio::spawn(async move {
                    let _ = txclone.send(cb).await;
                });*/
            }
        }),
    }
}

/// Main object for Hfp facade service
#[derive(Clone)]
pub struct HfpServiceImpl {
    #[allow(dead_code)]
    rt: Arc<Runtime>,
    pub btif_hfp: Arc<Mutex<Hfp>>,
    #[allow(dead_code)]
    event_tx: Arc<Mutex<Option<mpsc::Sender<HfpCallbacks>>>>,
}

impl HfpServiceImpl {
    /// Create a new instance of the root facade service
    pub fn create(rt: Arc<Runtime>, btif_intf: Arc<Mutex<BluetoothInterface>>) -> grpcio::Service {
        let btif_hfp = Arc::new(Mutex::new(Hfp::new(&btif_intf.lock().unwrap())));
        let event_tx = Arc::new(Mutex::new(None));
        btif_hfp.lock().unwrap().initialize(get_hfp_dispatcher(btif_hfp.clone(), event_tx.clone()));
        btif_hfp.lock().unwrap().enable();
        create_hfp_service(Self { rt, btif_hfp, event_tx })
    }
}

impl HfpService for HfpServiceImpl {
    fn start_slc(&mut self, ctx: RpcContext<'_>, req: StartSlcRequest, sink: UnarySink<Empty>) {
        let hfp = self.btif_hfp.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                hfp.lock().unwrap().connect(addr);
                sink.success(Empty::default()).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        })
    }

    fn stop_slc(&mut self, ctx: RpcContext<'_>, req: StopSlcRequest, sink: UnarySink<Empty>) {
        let hfp = self.btif_hfp.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                hfp.lock().unwrap().disconnect(addr);
                sink.success(Empty::default()).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        })
    }

    fn connect_audio(
        &mut self,
        ctx: RpcContext<'_>,
        req: ConnectAudioRequest,
        sink: UnarySink<Empty>,
    ) {
        let hfp = self.btif_hfp.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                hfp.lock().unwrap().connect_audio(
                    addr,
                    req.is_sco_offload_enabled,
                    req.disabled_codecs,
                );
                hfp.lock().unwrap().set_active_device(addr);
                sink.success(Empty::default()).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        })
    }

    fn disconnect_audio(
        &mut self,
        ctx: RpcContext<'_>,
        req: DisconnectAudioRequest,
        sink: UnarySink<Empty>,
    ) {
        let hfp = self.btif_hfp.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                hfp.lock().unwrap().disconnect_audio(addr);
                sink.success(Empty::default()).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        })
    }

    fn set_volume(&mut self, ctx: RpcContext<'_>, req: SetVolumeRequest, sink: UnarySink<Empty>) {
        let hfp = self.btif_hfp.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                // TODO(aritrasen): Consider using TryFrom and cap the maximum volume here
                // since `as` silently deals with data overflow, which might not be preferred.
                hfp.lock().unwrap().set_volume(req.volume as i8, addr);
                sink.success(Empty::default()).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        })
    }

    fn fetch_events(
        &mut self,
        ctx: RpcContext<'_>,
        _req: FetchEventsRequest,
        mut sink: ServerStreamingSink<FetchEventsResponse>,
    ) {
        let (tx, mut rx) = mpsc::channel(10);
        {
            let mut guard = self.event_tx.lock().unwrap();
            if guard.is_some() {
                ctx.spawn(async move {
                    sink.fail(RpcStatus::with_message(
                        RpcStatusCode::UNAVAILABLE,
                        String::from("Profile is currently already connected and streaming"),
                    ))
                    .await
                    .unwrap();
                });
                return;
            } else {
                *guard = Some(tx);
            }
        }

        ctx.spawn(async move {
            while let Some(event) = rx.recv().await {
                if let HfpCallbacks::ConnectionState(state, address) = event {
                    let mut rsp = FetchEventsResponse::new();
                    rsp.event_type = EventType::HFP_CONNECTION_STATE.into();
                    rsp.data = format!("{:?}, {}", state, address.to_string());
                    sink.send((rsp, WriteFlags::default())).await.unwrap();
                }
            }
        })
    }
}
