//! GATT service facade

use bt_topshim::btif::{BluetoothInterface, Uuid};
use bt_topshim::profiles::gatt::{
    AdvertiseParameters, Gatt, GattFilterParam, PeriodicAdvertisingParameters,
};
use bt_topshim::profiles::gatt::{
    GattAdvCallbacksDispatcher, GattAdvInbandCallbacksDispatcher, GattClientCallbacksDispatcher,
    GattScannerCallbacksDispatcher, GattScannerInbandCallbacksDispatcher,
    GattServerCallbacksDispatcher,
};
use bt_topshim_facade_protobuf::empty::Empty;
//use bt_topshim_facade_protobuf::facade::{
//    EventType, FetchEventsRequest, FetchEventsResponse, SetDiscoveryModeRequest,
//    SetDiscoveryModeResponse, ToggleStackRequest, ToggleStackResponse,
//};
use bt_topshim_facade_protobuf::facade_grpc::{create_gatt_service, GattService};
//use futures::sink::SinkExt;
use crate::btif::RawAddress;
use grpcio::*;

use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;
use tokio::sync::mpsc;
use tokio::sync::Mutex as TokioMutex;

struct GattCallbacks {}

/// Main object for GATT facade service
#[derive(Clone)]
pub struct GattServiceImpl {
    #[allow(dead_code)]
    rt: Arc<Runtime>,
    #[allow(dead_code)]
    btif_intf: Arc<Mutex<BluetoothInterface>>,
    #[allow(dead_code)]
    gatt: Arc<Mutex<Gatt>>,
    #[allow(dead_code)]
    event_rx: Arc<TokioMutex<mpsc::Receiver<GattCallbacks>>>,
    #[allow(dead_code)]
    event_tx: mpsc::Sender<GattCallbacks>,
}

impl GattServiceImpl {
    /// Create a new instance of the root facade service
    pub fn create(rt: Arc<Runtime>, btif_intf: Arc<Mutex<BluetoothInterface>>) -> grpcio::Service {
        let (event_tx, rx) = mpsc::channel(10);
        let btif_clone = btif_intf.clone();
        let me = Self {
            rt,
            btif_intf,
            gatt: Arc::new(Mutex::new(Gatt::new(&btif_clone.lock().unwrap()).unwrap())),
            event_rx: Arc::new(TokioMutex::new(rx)),
            event_tx,
        };
        me.gatt.lock().unwrap().initialize(
            GattClientCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("Received Gatt Client Callback: {:?}", cb);
                }),
            },
            GattServerCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("Received Gatt Server Callback: {:?}", cb);
                }),
            },
            GattScannerCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("received Gatt scanner callback: {:?}", cb);
                }),
            },
            GattScannerInbandCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("received Gatt scanner inband callback: {:?}", cb);
                }),
            },
            GattAdvInbandCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("received Gatt advertiser inband callback: {:?}", cb);
                }),
            },
            GattAdvCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    println!("received Gatt advertising callback: {:?}", cb);
                }),
            },
        );

        create_gatt_service(me)
    }

    fn create_raw_address(&self) -> RawAddress {
        RawAddress { address: [0; 6] }
    }

    fn create_advertise_parameters(&self) -> AdvertiseParameters {
        AdvertiseParameters {
            advertising_event_properties: 0,
            min_interval: 0,
            max_interval: 0,
            channel_map: 0,
            tx_power: 0,
            primary_advertising_phy: 0,
            secondary_advertising_phy: 0,
            scan_request_notification_enable: 0,
            own_address_type: 0,
        }
    }

    fn create_periodic_advertising_parameters(&self) -> PeriodicAdvertisingParameters {
        PeriodicAdvertisingParameters {
            enable: false,
            include_adi: false,
            min_interval: 0,
            max_interval: 0,
            periodic_advertising_properties: 0,
        }
    }

    fn create_gatt_filter_param(&self) -> GattFilterParam {
        GattFilterParam {
            feat_seln: 0,
            list_logic_type: 0,
            filt_logic_type: 0,
            rssi_high_thres: 0,
            rssi_low_thres: 0,
            delay_mode: 0,
            found_timeout: 0,
            lost_timeout: 0,
            found_timeout_count: 0,
            num_of_tracking_entries: 0,
        }
    }

    fn create_uuid(&self) -> Uuid {
        Uuid::from([0; 16])
    }
}

impl GattService for GattServiceImpl {
    //    fn fetch_events(
    //        &mut self,
    //        ctx: RpcContext<'_>,
    //        _req: FetchEventsRequest,
    //        mut sink: ServerStreamingSink<FetchEventsResponse>,
    //    ) {
    //        let rx = self.event_rx.clone();
    //        ctx.spawn(async move {
    //            while let Some(event) = rx.lock().await.recv().await {
    //                match event {
    //                    GattCallbacks::AdapterState(_state) => {
    //                        let mut rsp = FetchEventsResponse::new();
    //                        rsp.event_type = EventType::ADAPTER_STATE;
    //                        rsp.data = "ON".to_string();
    //                        sink.send((rsp, WriteFlags::default())).await.unwrap();
    //                    }
    //                    GattCallbacks::SspRequest(_, _, _, _, _) => {}
    //                    GattCallbacks::LeRandCallback(random) => {
    //                        let mut rsp = FetchEventsResponse::new();
    //                        rsp.event_type = EventType::LE_RAND;
    //                        rsp.data = random.to_string();
    //                        sink.send((rsp, WriteFlags::default())).await.unwrap();
    //                    }
    //                    _ => (),
    //                }
    //            }
    //        })
    //    }

    // TODO(optedoblivion): Implement all send messages and returns
    // Advertising
    fn register_advertiser(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.register_advertiser();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn unregister_advertiser(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.unregister(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn get_own_address(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.get_own_address(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_parameters(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.set_parameters(0, self.create_advertise_parameters());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_data(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.set_data(0, true, vec![0]);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn advertising_enable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.enable(0, true, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn advertising_disable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.enable(0, false, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn start_advertising(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.start_advertising(0, self.create_advertise_parameters(), vec![0], vec![0], 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn start_advertising_set(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.start_advertising_set(
            0,
            self.create_advertise_parameters(),
            vec![0],
            vec![0],
            self.create_periodic_advertising_parameters(),
            vec![0],
            0,
            0,
        );
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_periodic_advertising_parameters(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser
            .set_periodic_advertising_parameters(0, self.create_periodic_advertising_parameters());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_periodic_advertising_data(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.set_periodic_advertising_data(0, vec![0]);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_periodic_advertising_enable(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let advertiser = &mut self.gatt.lock().unwrap().advertiser;
        advertiser.set_periodic_advertising_enable(0, true, false);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    // Scanner
    fn register_scanner(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.register_scanner(self.create_uuid());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn unregister_scanner(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.unregister(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn start_scan(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.start_scan();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn stop_scan(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.stop_scan();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn scan_filter_setup(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.scan_filter_setup(0, 0, 0, self.create_gatt_filter_param());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn scan_filter_add(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        println!("Unimplemented!");
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn scan_filter_clear(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.scan_filter_clear(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn scan_filter_enable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.scan_filter_enable();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn scan_filter_disable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.scan_filter_disable();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn set_scan_parameters(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.set_scan_parameters(0, 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn batch_scan_config_storage(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.batchscan_config_storage(0, 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn batch_scan_enable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.batchscan_enable(0, 0, 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn batch_scan_disable(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.batchscan_disable();
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn batch_scan_read_reports(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.batchscan_read_reports(0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn start_sync(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.start_sync(0, self.create_raw_address(), 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn stop_sync(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.stop_sync(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn cancel_create_sync(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.cancel_create_sync(0, self.create_raw_address());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn transfer_sync(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.transfer_sync(self.create_raw_address(), 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn transfer_set_info(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.transfer_set_info(self.create_raw_address(), 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn sync_tx_parameters(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let scanner = &mut self.gatt.lock().unwrap().scanner;
        scanner.sync_tx_parameters(self.create_raw_address(), 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    // GATT Client
    fn register_client(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.register_client(&self.create_uuid(), true);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn unregister_client(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.unregister_client(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn client_connect(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.connect(0, &self.create_raw_address(), 0, true, 0, true, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn client_disconnect(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.disconnect(0, &self.create_raw_address(), 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn refresh(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.refresh(0, &self.create_raw_address());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn search_service(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.search_service(0, None);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn btif_gattc_discover_service_by_uuid(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.btif_gattc_discover_service_by_uuid(0, &self.create_uuid());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn read_characteristic(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.read_characteristic(0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn read_using_characteristic_uuid(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.read_using_characteristic_uuid(0, &self.create_uuid(), 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn write_characteristic(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.write_characteristic(0, 0, 0, 0, &[0]);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn read_descriptor(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.read_descriptor(0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn write_descriptor(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.write_descriptor(0, 0, 0, &[0]);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn execute_write(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.execute_write(0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn register_for_notification(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.register_for_notification(0, &self.create_raw_address(), 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn deregister_for_notification(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.deregister_for_notification(0, &self.create_raw_address(), 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn read_remote_rssi(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.read_remote_rssi(0, &self.create_raw_address());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn get_device_type(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.get_device_type(&self.create_raw_address());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn configure_mtu(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.configure_mtu(0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn conn_parameter_update(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.conn_parameter_update(&self.create_raw_address(), 0, 0, 0, 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn client_set_preferred_phy(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.set_preferred_phy(&self.create_raw_address(), 0, 0, 0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn client_read_phy(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.read_phy(0, &self.create_raw_address());
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn test_command(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        println!("Not implemented!");
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn get_gatt_db(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        let client = &mut self.gatt.lock().unwrap().client;
        client.get_gatt_db(0);
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn register_server(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn unregister_server(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn server_connect(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn server_disconnect(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn add_service(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn stop_service(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn delete_service(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn send_indication(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn send_response(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn server_set_preferred_phy(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<Empty>,
    ) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }

    fn server_read_phy(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<Empty>) {
        ctx.spawn(async move {
            sink.success(Empty::default()).await.unwrap();
        })
    }
}
