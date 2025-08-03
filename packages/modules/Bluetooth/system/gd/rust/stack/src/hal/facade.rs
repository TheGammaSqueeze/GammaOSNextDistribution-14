//! BT HCI HAL facade

use crate::hal::{AclHal, ControlHal, IsoHal, ScoHal};
use bt_common::GrpcFacade;
use bt_facade_helpers::RxAdapter;
use bt_facade_proto::common::Data;
use bt_facade_proto::empty::Empty;
use bt_facade_proto::hal_facade_grpc::{create_hci_hal_facade, HciHalFacade};
use bt_packets::hci::{Acl, Command, Event, Iso, Sco};
use gddi::{module, provides, Stoppable};
use grpcio::*;

module! {
    hal_facade_module,
    providers {
        HciHalFacadeService => provide_facade,
    }
}

#[provides]
async fn provide_facade(
    control: ControlHal,
    acl: AclHal,
    iso: IsoHal,
    sco: ScoHal,
) -> HciHalFacadeService {
    HciHalFacadeService {
        evt_rx: RxAdapter::from_arc(control.rx.clone()),
        acl_rx: RxAdapter::from_arc(acl.rx.clone()),
        iso_rx: RxAdapter::from_arc(iso.rx.clone()),
        sco_rx: RxAdapter::from_arc(sco.rx.clone()),
        control,
        acl,
        iso,
        sco,
    }
}

/// HCI HAL facade service
#[derive(Clone, Stoppable)]
pub struct HciHalFacadeService {
    evt_rx: RxAdapter<Event>,
    acl_rx: RxAdapter<Acl>,
    iso_rx: RxAdapter<Iso>,
    sco_rx: RxAdapter<Sco>,
    control: ControlHal,
    acl: AclHal,
    iso: IsoHal,
    sco: ScoHal,
}

impl GrpcFacade for HciHalFacadeService {
    fn into_grpc(self) -> grpcio::Service {
        create_hci_hal_facade(self)
    }
}

impl HciHalFacade for HciHalFacadeService {
    fn send_command(&mut self, ctx: RpcContext<'_>, data: Data, sink: UnarySink<Empty>) {
        let cmd_tx = self.control.tx.clone();
        ctx.spawn(async move {
            cmd_tx.send(Command::parse(&data.payload).unwrap()).await.unwrap();
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn send_acl(&mut self, ctx: RpcContext<'_>, data: Data, sink: UnarySink<Empty>) {
        let acl_tx = self.acl.tx.clone();
        ctx.spawn(async move {
            acl_tx.send(Acl::parse(&data.payload).unwrap()).await.unwrap();
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn send_sco(&mut self, ctx: RpcContext<'_>, data: Data, sink: UnarySink<Empty>) {
        let sco_tx = self.sco.tx.clone();
        ctx.spawn(async move {
            sco_tx.send(Sco::parse(&data.payload).unwrap()).await.unwrap();
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn send_iso(&mut self, ctx: RpcContext<'_>, data: Data, sink: UnarySink<Empty>) {
        let iso_tx = self.iso.tx.clone();
        ctx.spawn(async move {
            iso_tx.send(Iso::parse(&data.payload).unwrap()).await.unwrap();
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn stream_events(&mut self, ctx: RpcContext<'_>, _: Empty, sink: ServerStreamingSink<Data>) {
        self.evt_rx.stream_grpc(ctx, sink);
    }

    fn stream_acl(&mut self, ctx: RpcContext<'_>, _: Empty, sink: ServerStreamingSink<Data>) {
        self.acl_rx.stream_grpc(ctx, sink);
    }

    fn stream_sco(&mut self, ctx: RpcContext<'_>, _: Empty, sink: ServerStreamingSink<Data>) {
        self.sco_rx.stream_grpc(ctx, sink);
    }

    fn stream_iso(&mut self, ctx: RpcContext<'_>, _: Empty, sink: ServerStreamingSink<Data>) {
        self.iso_rx.stream_grpc(ctx, sink);
    }
}
