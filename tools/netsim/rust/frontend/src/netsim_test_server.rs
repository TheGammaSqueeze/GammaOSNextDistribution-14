//! netsim Rust grpc test server
use std::io::Read;
use std::sync::Arc;
use std::{io, thread};

use futures::channel::oneshot;
use futures::executor::block_on;
use futures::prelude::*;
use grpcio::{
    ChannelBuilder, Environment, ResourceQuota, RpcContext, ServerBuilder, ServerCredentials,
    UnarySink,
};

use netsim_proto::frontend::VersionResponse;
use netsim_proto::frontend_grpc::{create_frontend_service, FrontendService};

#[derive(Clone)]
struct FrontendClient;

impl FrontendService for FrontendClient {
    fn get_version(
        &mut self,
        ctx: RpcContext<'_>,
        req: protobuf::well_known_types::empty::Empty,
        sink: UnarySink<VersionResponse>,
    ) {
        let response = VersionResponse {
            version: "netsim test server version 0.0.1".to_string(),
            ..Default::default()
        };
        let f = sink
            .success(response)
            .map_err(move |e| eprintln!("failed to reply {:?}: {:?}", req, e))
            .map(|_| ());
        ctx.spawn(f)
    }

    fn list_device(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: protobuf::well_known_types::empty::Empty,
        _sink: grpcio::UnarySink<netsim_proto::frontend::ListDeviceResponse>,
    ) {
        todo!()
    }

    fn patch_device(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: netsim_proto::frontend::PatchDeviceRequest,
        _sink: grpcio::UnarySink<protobuf::well_known_types::empty::Empty>,
    ) {
        todo!()
    }

    fn reset(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: protobuf::well_known_types::empty::Empty,
        _sink: grpcio::UnarySink<protobuf::well_known_types::empty::Empty>,
    ) {
        todo!()
    }

    fn patch_capture(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: netsim_proto::frontend::PatchCaptureRequest,
        _sink: grpcio::UnarySink<protobuf::well_known_types::empty::Empty>,
    ) {
        todo!()
    }

    fn list_capture(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: protobuf::well_known_types::empty::Empty,
        _sink: grpcio::UnarySink<netsim_proto::frontend::ListCaptureResponse>,
    ) {
        todo!()
    }

    fn get_capture(
        &mut self,
        _ctx: grpcio::RpcContext,
        _req: netsim_proto::frontend::GetCaptureRequest,
        _sink: grpcio::ServerStreamingSink<netsim_proto::frontend::GetCaptureResponse>,
    ) {
        todo!()
    }
}

fn main() {
    let env = Arc::new(Environment::new(1));
    let service = create_frontend_service(FrontendClient);

    let quota = ResourceQuota::new(Some("HelloServerQuota")).resize_memory(1024 * 1024);
    let ch_builder = ChannelBuilder::new(env.clone()).set_resource_quota(quota);

    let mut server = ServerBuilder::new(env)
        .register_service(service)
        .channel_args(ch_builder.build_args())
        .build()
        .unwrap();
    let port = server.add_listening_port("127.0.0.1:50051", ServerCredentials::insecure()).unwrap();
    server.start();
    println!("listening on port {}", port);

    let (tx, rx) = oneshot::channel();
    thread::spawn(move || {
        println!("Press ENTER to exit...");
        let _ = io::stdin().read(&mut [0]).unwrap();
        tx.send(())
    });
    let _ = block_on(rx);
    let _ = block_on(server.shutdown());
}
