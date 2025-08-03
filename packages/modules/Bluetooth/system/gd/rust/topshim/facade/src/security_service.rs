//! Security service facade

use bt_topshim::btif::{BluetoothInterface, BtTransport, RawAddress};

use bt_topshim_facade_protobuf::empty::Empty;
use bt_topshim_facade_protobuf::facade::{
    CreateBondRequest, CreateBondResponse, GenerateOobDataRequest, RemoveBondRequest,
};
use bt_topshim_facade_protobuf::facade_grpc::{create_security_service, SecurityService};
use grpcio::*;

use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;

/// Main object for Adapter facade service
#[derive(Clone)]
pub struct SecurityServiceImpl {
    #[allow(dead_code)]
    rt: Arc<Runtime>,
    #[allow(dead_code)]
    btif_intf: Arc<Mutex<BluetoothInterface>>,
}

#[allow(dead_code)]
impl SecurityServiceImpl {
    /// Create a new instance of the root facade service
    pub fn create(rt: Arc<Runtime>, btif_intf: Arc<Mutex<BluetoothInterface>>) -> grpcio::Service {
        create_security_service(Self { rt, btif_intf })
    }
}

impl SecurityService for SecurityServiceImpl {
    fn remove_bond(&mut self, ctx: RpcContext<'_>, req: RemoveBondRequest, sink: UnarySink<Empty>) {
        let raw_address = RawAddress::from_string(req.address).unwrap();
        self.btif_intf.lock().unwrap().remove_bond(&raw_address);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn generate_local_oob_data(
        &mut self,
        ctx: RpcContext<'_>,
        req: GenerateOobDataRequest,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().generate_local_oob_data(req.transport);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn create_bond(
        &mut self,
        ctx: RpcContext<'_>,
        req: CreateBondRequest,
        sink: UnarySink<CreateBondResponse>,
    ) {
        let btif = self.btif_intf.clone();
        ctx.spawn(async move {
            let bt_addr = &req.address;
            if let Some(addr) = RawAddress::from_string(bt_addr) {
                let status =
                    btif.lock().unwrap().create_bond(&addr, BtTransport::from(req.transport));
                let mut resp = CreateBondResponse::new();
                resp.status = status;
                sink.success(resp).await.unwrap();
            } else {
                sink.fail(RpcStatus::with_message(
                    RpcStatusCode::INVALID_ARGUMENT,
                    format!("Invalid Request Address: {}", bt_addr),
                ))
                .await
                .unwrap();
            }
        });
    }
}
