//! HF Client service facade

use bt_topshim::btif::{BluetoothInterface, RawAddress, ToggleableProfile};
use bt_topshim::profiles::hf_client::{BthfClientCallbacksDispatcher, HfClient};
use bt_topshim_facade_protobuf::facade::{
    ConnectAudioRequest, ConnectAudioResponse, DisconnectAudioRequest, DisconnectAudioResponse,
    StartSlcRequest, StartSlcResponse, StopSlcRequest, StopSlcResponse,
};
use bt_topshim_facade_protobuf::facade_grpc::{create_hf_client_service, HfClientService};
use grpcio::*;
use num_traits::cast::ToPrimitive;

use std::str::from_utf8;
use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;

fn get_hf_client_dispatcher() -> BthfClientCallbacksDispatcher {
    BthfClientCallbacksDispatcher { dispatch: Box::new(move |_cb| {}) }
}

/// Main object for Hf(Hands free) client facade service
#[derive(Clone)]
pub struct HfClientServiceImpl {
    #[allow(dead_code)]
    rt: Arc<Runtime>,
    pub hf_client: Arc<Mutex<HfClient>>,
}

impl HfClientServiceImpl {
    /// Create a new instance of the root facade service
    pub fn create(rt: Arc<Runtime>, btif_intf: Arc<Mutex<BluetoothInterface>>) -> grpcio::Service {
        let hf_client = Arc::new(Mutex::new(HfClient::new(&btif_intf.lock().unwrap())));
        hf_client.lock().unwrap().initialize(get_hf_client_dispatcher());
        hf_client.lock().unwrap().enable();
        create_hf_client_service(Self { rt, hf_client })
    }
}

impl HfClientService for HfClientServiceImpl {
    fn start_slc(
        &mut self,
        ctx: RpcContext<'_>,
        req: StartSlcRequest,
        sink: UnarySink<StartSlcResponse>,
    ) {
        let hf_client = self.hf_client.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                let status = hf_client.lock().unwrap().connect(addr);
                let mut resp = StartSlcResponse::new();
                resp.status = status.to_i32().unwrap();
                sink.success(resp).await.unwrap();
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

    fn stop_slc(
        &mut self,
        ctx: RpcContext<'_>,
        req: StopSlcRequest,
        sink: UnarySink<StopSlcResponse>,
    ) {
        let hf_client = self.hf_client.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                let status = hf_client.lock().unwrap().disconnect(addr);
                let mut resp = StopSlcResponse::new();
                resp.status = status.to_i32().unwrap();
                sink.success(resp).await.unwrap();
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
        sink: UnarySink<ConnectAudioResponse>,
    ) {
        let hf_client = self.hf_client.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                let status = hf_client.lock().unwrap().connect_audio(addr);
                let mut resp = ConnectAudioResponse::new();
                resp.status = status.to_i32().unwrap();
                sink.success(resp).await.unwrap();
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
        sink: UnarySink<DisconnectAudioResponse>,
    ) {
        let hf_client = self.hf_client.clone();
        ctx.spawn(async move {
            let addr_bytes = &req.connection.unwrap().cookie;
            let bt_addr = from_utf8(addr_bytes).unwrap();
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                let status = hf_client.lock().unwrap().disconnect_audio(addr);
                let mut resp = DisconnectAudioResponse::new();
                resp.status = status.to_i32().unwrap();
                sink.success(resp).await.unwrap();
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
}
