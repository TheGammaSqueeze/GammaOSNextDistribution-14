//! HCI layer facade

use crate::hal::{AclHal, IsoHal, ScoHal};
use crate::hci::{EventRegistry, RawCommandSender};
use bt_common::GrpcFacade;
use bt_facade_helpers::RxAdapter;
use bt_facade_proto::common::Data;
use bt_facade_proto::empty::Empty;
use bt_facade_proto::hci_facade::EventRequest;
use bt_facade_proto::hci_facade_grpc::{create_hci_facade, HciFacade};
use bt_packets::hci::{Acl, Command, Event, EventCode, Iso, LeMetaEvent, Sco, SubeventCode};
use gddi::{module, provides, Stoppable};
use grpcio::*;
use std::convert::TryFrom;
use tokio::sync::mpsc::{channel, Sender};

module! {
    facade_module,
    providers {
        HciFacadeService => provide_facade,
    }
}

#[provides]
async fn provide_facade(
    commands: RawCommandSender,
    events: EventRegistry,
    acl: AclHal,
    sco: ScoHal,
    iso: IsoHal,
) -> HciFacadeService {
    let (evt_tx, evt_rx) = channel::<Event>(10);
    let (le_evt_tx, le_evt_rx) = channel::<LeMetaEvent>(10);
    HciFacadeService {
        commands,
        events,
        evt_tx,
        evt_rx: RxAdapter::new(evt_rx),
        le_evt_tx,
        le_evt_rx: RxAdapter::new(le_evt_rx),
        acl_tx: acl.tx,
        acl_rx: RxAdapter::from_arc(acl.rx),
        sco_tx: sco.tx,
        sco_rx: RxAdapter::from_arc(sco.rx),
        iso_tx: iso.tx,
        iso_rx: RxAdapter::from_arc(iso.rx),
    }
}

/// HCI layer facade service
#[allow(missing_docs)]
#[derive(Clone, Stoppable)]
pub struct HciFacadeService {
    pub commands: RawCommandSender,
    events: EventRegistry,
    evt_tx: Sender<Event>,
    pub evt_rx: RxAdapter<Event>,
    le_evt_tx: Sender<LeMetaEvent>,
    pub le_evt_rx: RxAdapter<LeMetaEvent>,
    pub acl_tx: Sender<Acl>,
    pub acl_rx: RxAdapter<Acl>,
    pub sco_tx: Sender<Sco>,
    pub sco_rx: RxAdapter<Sco>,
    pub iso_tx: Sender<Iso>,
    pub iso_rx: RxAdapter<Iso>,
}

impl HciFacadeService {
    /// Register for the event & plug in the channel to get them back on
    pub async fn register_event(&mut self, code: u32) {
        self.events
            .register(
                EventCode::try_from(u8::try_from(code).unwrap()).unwrap(),
                self.evt_tx.clone(),
            )
            .await;
    }

    /// Register for the le event & plug in the channel to get them back on
    pub async fn register_le_event(&mut self, code: u32) {
        self.events
            .register_le(
                SubeventCode::try_from(u8::try_from(code).unwrap()).unwrap(),
                self.le_evt_tx.clone(),
            )
            .await;
    }
}

impl GrpcFacade for HciFacadeService {
    fn into_grpc(self) -> grpcio::Service {
        create_hci_facade(self)
    }
}

impl HciFacade for HciFacadeService {
    fn send_command(&mut self, ctx: RpcContext<'_>, data: Data, sink: UnarySink<Empty>) {
        let packet = Command::parse(&data.payload).unwrap();
        let mut commands = self.commands.clone();
        let evt_tx = self.evt_tx.clone();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
            let response = commands.send(packet).await.unwrap();
            evt_tx.send(response).await.unwrap();
        });
    }

    fn request_event(&mut self, ctx: RpcContext<'_>, req: EventRequest, sink: UnarySink<Empty>) {
        let mut clone = self.clone();
        ctx.spawn(async move {
            clone.register_event(req.code).await;
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn request_le_subevent(
        &mut self,
        ctx: RpcContext<'_>,
        req: EventRequest,
        sink: UnarySink<Empty>,
    ) {
        let mut clone = self.clone();
        ctx.spawn(async move {
            clone.register_le_event(req.code).await;
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn send_acl(&mut self, ctx: RpcContext<'_>, packet: Data, sink: UnarySink<Empty>) {
        let acl_tx = self.acl_tx.clone();
        ctx.spawn(async move {
            acl_tx.send(Acl::parse(&packet.payload).unwrap()).await.unwrap();
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn stream_events(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: ServerStreamingSink<Data>) {
        self.evt_rx.stream_grpc(ctx, sink);
    }

    fn stream_le_subevents(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: ServerStreamingSink<Data>,
    ) {
        self.le_evt_rx.stream_grpc(ctx, sink);
    }

    fn stream_acl(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: ServerStreamingSink<Data>) {
        self.acl_rx.stream_grpc(ctx, sink);
    }
}
