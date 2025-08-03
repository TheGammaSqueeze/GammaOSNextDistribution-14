//! Adapter service facade

use bt_topshim::btif;
use bt_topshim::btif::{BaseCallbacks, BaseCallbacksDispatcher, BluetoothInterface, BtIoCap};

use crate::utils::converters::{bluetooth_property_to_event_data, event_data_from_string};
use bt_topshim_facade_protobuf::empty::Empty;
use bt_topshim_facade_protobuf::facade::{
    EventType, FetchEventsRequest, FetchEventsResponse, SetDefaultEventMaskExceptRequest,
    SetDiscoveryModeRequest, SetDiscoveryModeResponse, SetLocalIoCapsRequest,
    SetLocalIoCapsResponse, ToggleDiscoveryRequest, ToggleDiscoveryResponse, ToggleStackRequest,
    ToggleStackResponse,
};
use bt_topshim_facade_protobuf::facade_grpc::{create_adapter_service, AdapterService};
use futures::sink::SinkExt;
use grpcio::*;
use num_traits::cast::FromPrimitive;

use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;
use tokio::sync::mpsc;
use tokio::sync::Mutex as TokioMutex;

fn get_bt_dispatcher(
    btif: Arc<Mutex<BluetoothInterface>>,
    tx: mpsc::Sender<BaseCallbacks>,
) -> BaseCallbacksDispatcher {
    BaseCallbacksDispatcher {
        dispatch: Box::new(move |cb: BaseCallbacks| {
            if tx.clone().try_send(cb.clone()).is_err() {
                println!("Cannot send event {:?}", cb);
            }
            match cb {
                BaseCallbacks::AdapterState(state) => {
                    println!("State changed to {:?}", state);
                }
                BaseCallbacks::SspRequest(addr, _, _, variant, passkey) => {
                    println!(
                        "SSP Request made for address {:?} with variant {:?} and passkey {:?}",
                        addr.to_string(),
                        variant,
                        passkey
                    );
                    btif.lock().unwrap().ssp_reply(&addr, variant, 1, passkey);
                }
                BaseCallbacks::AdapterProperties(status, _, properties) => {
                    println!(
                        "Adapter attributes changed, status = {:?}, properties = {:?}",
                        status, properties
                    );
                }
                BaseCallbacks::DiscoveryState(state) => {
                    println!("Discovery state changed, state = {:?}, ", state);
                }
                BaseCallbacks::DeviceFound(_, properties) => {
                    println!("Device found with properties : {:?}", properties)
                }
                BaseCallbacks::BondState(_, address, state, _) => {
                    println!(
                        "Device in state {:?} with device address {}",
                        state,
                        address.to_string()
                    );
                }
                _ => (),
            }
        }),
    }
}

/// Main object for Adapter facade service
#[derive(Clone)]
pub struct AdapterServiceImpl {
    #[allow(dead_code)]
    rt: Arc<Runtime>,
    btif_intf: Arc<Mutex<BluetoothInterface>>,
    event_rx: Arc<TokioMutex<mpsc::Receiver<BaseCallbacks>>>,
    #[allow(dead_code)]
    event_tx: mpsc::Sender<BaseCallbacks>,
}

fn encode_hex(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(2 * bytes.len());
    for &b in bytes {
        let bstr: String = format!("{:02X}", b);
        s.push_str(&bstr);
    }
    s
}

impl AdapterServiceImpl {
    /// Create a new instance of the root facade service
    pub fn create(rt: Arc<Runtime>, btif_intf: Arc<Mutex<BluetoothInterface>>) -> grpcio::Service {
        let (event_tx, rx) = mpsc::channel(10);
        btif_intf.lock().unwrap().initialize(
            get_bt_dispatcher(btif_intf.clone(), event_tx.clone()),
            vec!["INIT_gd_hci=true".to_string()],
        );
        create_adapter_service(Self {
            rt,
            btif_intf,
            event_rx: Arc::new(TokioMutex::new(rx)),
            event_tx,
        })
    }
}

impl AdapterService for AdapterServiceImpl {
    fn fetch_events(
        &mut self,
        ctx: RpcContext<'_>,
        _req: FetchEventsRequest,
        mut sink: ServerStreamingSink<FetchEventsResponse>,
    ) {
        let rx = self.event_rx.clone();
        ctx.spawn(async move {
            while let Some(event) = rx.lock().await.recv().await {
                match event {
                    BaseCallbacks::AdapterState(_state) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::ADAPTER_STATE.into();
                        rsp.params.insert(
                            String::from("state"),
                            event_data_from_string(String::from("ON")),
                        );
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::SspRequest(_, _, _, _, _) => {}
                    BaseCallbacks::LeRandCallback(random) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::LE_RAND.into();
                        rsp.params.insert(
                            String::from("data"),
                            event_data_from_string(random.to_string()),
                        );
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::GenerateLocalOobData(transport, data) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::GENERATE_LOCAL_OOB_DATA.into();
                        rsp.params.insert(
                            String::from("is_valid"),
                            event_data_from_string(String::from(if data.is_valid {
                                "1"
                            } else {
                                "0"
                            })),
                        );
                        rsp.params.insert(
                            String::from("transport"),
                            event_data_from_string(format!("{}", transport)),
                        );
                        rsp.params.insert(
                            String::from("address"),
                            event_data_from_string(encode_hex(&data.address)),
                        );
                        rsp.params.insert(
                            String::from("confirmation"),
                            event_data_from_string(encode_hex(&data.c)),
                        );
                        rsp.params.insert(
                            String::from("randomizer"),
                            event_data_from_string(encode_hex(&data.r)),
                        );
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::AdapterProperties(status, _, properties) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::ADAPTER_PROPERTY.into();
                        rsp.params.insert(
                            String::from("status"),
                            event_data_from_string(format!("{:?}", status)),
                        );
                        for property in properties.clone() {
                            let (key, event_data) = bluetooth_property_to_event_data(property);
                            if key == "skip" {
                                continue;
                            }
                            rsp.params.insert(key, event_data);
                        }
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::DiscoveryState(state) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::DISCOVERY_STATE.into();
                        rsp.params.insert(
                            String::from("discovery_state"),
                            event_data_from_string(format!("{:?}", state)),
                        );
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::DeviceFound(_, properties) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::DEVICE_FOUND.into();
                        for property in properties.clone() {
                            let (key, event_data) = bluetooth_property_to_event_data(property);
                            if key == "skip" {
                                continue;
                            }
                            rsp.params.insert(key, event_data);
                        }
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    BaseCallbacks::BondState(_, address, state, _) => {
                        let mut rsp = FetchEventsResponse::new();
                        rsp.event_type = EventType::BOND_STATE.into();
                        rsp.params.insert(
                            String::from("bond_state"),
                            event_data_from_string(format!("{:?}", state)),
                        );
                        rsp.params.insert(
                            String::from("address"),
                            event_data_from_string(address.to_string()),
                        );
                        sink.send((rsp, WriteFlags::default())).await.unwrap();
                    }
                    _ => (),
                }
            }
        })
    }

    fn toggle_stack(
        &mut self,
        ctx: RpcContext<'_>,
        req: ToggleStackRequest,
        sink: UnarySink<ToggleStackResponse>,
    ) {
        match req.start_stack {
            true => self.btif_intf.lock().unwrap().enable(),
            false => self.btif_intf.lock().unwrap().disable(),
        };
        ctx.spawn(async move {
            sink.success(ToggleStackResponse::default()).await.unwrap();
        })
    }

    fn set_discovery_mode(
        &mut self,
        ctx: RpcContext<'_>,
        req: SetDiscoveryModeRequest,
        sink: UnarySink<SetDiscoveryModeResponse>,
    ) {
        let scan_mode = if req.enable_inquiry_scan {
            btif::BtScanMode::ConnectableDiscoverable
        } else if req.enable_page_scan {
            btif::BtScanMode::Connectable
        } else {
            btif::BtScanMode::None_
        };
        let status = self
            .btif_intf
            .lock()
            .unwrap()
            .set_adapter_property(btif::BluetoothProperty::AdapterScanMode(scan_mode));

        let mut resp = SetDiscoveryModeResponse::new();
        resp.status = status;
        ctx.spawn(async move {
            sink.success(resp).await.unwrap();
        })
    }

    fn clear_event_filter(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        self.btif_intf.lock().unwrap().clear_event_filter();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn clear_event_mask(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        self.btif_intf.lock().unwrap().clear_event_mask();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn clear_filter_accept_list(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().clear_filter_accept_list();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn disconnect_all_acls(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        self.btif_intf.lock().unwrap().disconnect_all_acls();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn le_rand(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        self.btif_intf.lock().unwrap().le_rand();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn allow_wake_by_hid(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        self.btif_intf.lock().unwrap().allow_wake_by_hid();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn restore_filter_accept_list(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().restore_filter_accept_list();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_default_event_mask_except(
        &mut self,
        ctx: RpcContext<'_>,
        req: SetDefaultEventMaskExceptRequest,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().set_default_event_mask_except(req.mask, req.le_mask);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_event_filter_inquiry_result_all_devices(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().set_event_filter_inquiry_result_all_devices();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_event_filter_connection_setup_all_devices(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        self.btif_intf.lock().unwrap().set_event_filter_connection_setup_all_devices();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_local_io_caps(
        &mut self,
        ctx: RpcContext<'_>,
        req: SetLocalIoCapsRequest,
        sink: UnarySink<SetLocalIoCapsResponse>,
    ) {
        let status = self.btif_intf.lock().unwrap().set_adapter_property(
            btif::BluetoothProperty::LocalIoCaps(
                BtIoCap::from_i32(req.io_capability).unwrap_or(BtIoCap::Unknown),
            ),
        );
        let mut resp = SetLocalIoCapsResponse::new();
        resp.status = status;
        ctx.spawn(async move {
            sink.success(resp).await.unwrap();
        })
    }

    fn toggle_discovery(
        &mut self,
        ctx: RpcContext<'_>,
        req: ToggleDiscoveryRequest,
        sink: UnarySink<ToggleDiscoveryResponse>,
    ) {
        let status = match req.is_start {
            true => self.btif_intf.lock().unwrap().start_discovery(),
            false => self.btif_intf.lock().unwrap().cancel_discovery(),
        };
        let mut resp = ToggleDiscoveryResponse::new();
        resp.status = status;
        ctx.spawn(async move {
            sink.success(resp).await.unwrap();
        })
    }
}
