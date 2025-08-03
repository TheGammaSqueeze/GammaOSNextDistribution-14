use std::{
    rc::Rc,
    sync::{Arc, Mutex},
};

use bluetooth_core::{
    core::uuid::Uuid,
    gatt::{
        self,
        ffi::AttributeBackingType,
        ids::{AdvertiserId, AttHandle, ServerId, TransportIndex},
        mocks::{
            mock_datastore::{MockDatastore, MockDatastoreEvents},
            mock_transport::MockAttTransport,
        },
        server::{
            gatt_database::{
                AttPermissions, GattCharacteristicWithHandle, GattDescriptorWithHandle,
                GattServiceWithHandle, CHARACTERISTIC_UUID, PRIMARY_SERVICE_DECLARATION_UUID,
            },
            isolation_manager::IsolationManager,
            services::{
                gap::DEVICE_NAME_UUID,
                gatt::{
                    CLIENT_CHARACTERISTIC_CONFIGURATION_UUID, GATT_SERVICE_UUID,
                    SERVICE_CHANGE_UUID,
                },
            },
            GattModule, IndicationError,
        },
    },
    packets::{
        AttAttributeDataChild, AttBuilder, AttChild, AttErrorCode, AttErrorResponseBuilder,
        AttFindByTypeValueRequestBuilder, AttFindInformationRequestBuilder,
        AttFindInformationResponseChild, AttHandleValueConfirmationBuilder,
        AttHandleValueIndicationBuilder, AttOpcode, AttReadByTypeRequestBuilder,
        AttReadRequestBuilder, AttReadResponseBuilder, AttWriteRequestBuilder,
        AttWriteResponseBuilder, GattClientCharacteristicConfigurationBuilder,
        GattServiceChangedBuilder, GattServiceDeclarationValueBuilder, Serializable,
        UuidAsAttDataBuilder,
    },
    utils::packet::{build_att_data, build_att_view_or_crash},
};

use tokio::{
    sync::mpsc::{error::TryRecvError, UnboundedReceiver},
    task::spawn_local,
};
use utils::start_test;

mod utils;

const TCB_IDX: TransportIndex = TransportIndex(1);
const SERVER_ID: ServerId = ServerId(2);
const ADVERTISER_ID: AdvertiserId = AdvertiserId(3);

const ANOTHER_TCB_IDX: TransportIndex = TransportIndex(2);
const ANOTHER_SERVER_ID: ServerId = ServerId(3);
const ANOTHER_ADVERTISER_ID: AdvertiserId = AdvertiserId(4);

const SERVICE_HANDLE: AttHandle = AttHandle(6);
const CHARACTERISTIC_HANDLE: AttHandle = AttHandle(8);
const DESCRIPTOR_HANDLE: AttHandle = AttHandle(9);

const SERVICE_TYPE: Uuid = Uuid::new(0x0102);
const CHARACTERISTIC_TYPE: Uuid = Uuid::new(0x0103);
const DESCRIPTOR_TYPE: Uuid = Uuid::new(0x0104);

const DATA: [u8; 4] = [1, 2, 3, 4];
const ANOTHER_DATA: [u8; 4] = [5, 6, 7, 8];

fn start_gatt_module() -> (gatt::server::GattModule, UnboundedReceiver<(TransportIndex, AttBuilder)>)
{
    let (transport, transport_rx) = MockAttTransport::new();
    let arbiter = IsolationManager::new();
    let gatt = GattModule::new(Rc::new(transport), Arc::new(Mutex::new(arbiter)));

    (gatt, transport_rx)
}

fn create_server_and_open_connection(
    gatt: &mut GattModule,
) -> UnboundedReceiver<MockDatastoreEvents> {
    gatt.open_gatt_server(SERVER_ID).unwrap();
    let (datastore, data_rx) = MockDatastore::new();
    gatt.register_gatt_service(
        SERVER_ID,
        GattServiceWithHandle {
            handle: SERVICE_HANDLE,
            type_: SERVICE_TYPE,
            characteristics: vec![GattCharacteristicWithHandle {
                handle: CHARACTERISTIC_HANDLE,
                type_: CHARACTERISTIC_TYPE,
                permissions: AttPermissions::READABLE
                    | AttPermissions::WRITABLE_WITH_RESPONSE
                    | AttPermissions::INDICATE,
                descriptors: vec![GattDescriptorWithHandle {
                    handle: DESCRIPTOR_HANDLE,
                    type_: DESCRIPTOR_TYPE,
                    permissions: AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE,
                }],
            }],
        },
        datastore,
    )
    .unwrap();
    gatt.get_isolation_manager().associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
    gatt.on_le_connect(TCB_IDX, Some(ADVERTISER_ID)).unwrap();
    data_rx
}

#[test]
fn test_service_read() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        create_server_and_open_connection(&mut gatt);

        // act
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: SERVICE_HANDLE.into(),
            })
            .view(),
        );
        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        // assert
        assert_eq!(tcb_idx, TCB_IDX);
        assert_eq!(
            resp,
            AttBuilder {
                opcode: AttOpcode::READ_RESPONSE,
                _child_: AttReadResponseBuilder {
                    value: build_att_data(GattServiceDeclarationValueBuilder {
                        uuid: SERVICE_TYPE.into()
                    })
                }
                .into()
            }
        );
    })
}

#[test]
fn test_server_closed_while_connected() {
    start_test(async move {
        // arrange: set up a connection to a closed server
        let (mut gatt, mut transport_rx) = start_gatt_module();

        // open a server and connect
        create_server_and_open_connection(&mut gatt);
        gatt.close_gatt_server(SERVER_ID).unwrap();

        // act: read from the closed server
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: SERVICE_HANDLE.into(),
            })
            .view(),
        );
        let (_, resp) = transport_rx.recv().await.unwrap();

        // assert that the read failed, but that a response was provided
        assert_eq!(resp.opcode, AttOpcode::ERROR_RESPONSE);
        assert_eq!(
            resp._child_,
            AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::READ_REQUEST,
                handle_in_error: SERVICE_HANDLE.into(),
                error_code: AttErrorCode::INVALID_HANDLE
            }
            .into()
        )
    });
}

#[test]
fn test_characteristic_read() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        let data = AttAttributeDataChild::RawData(DATA.into());

        let mut data_rx = create_server_and_open_connection(&mut gatt);

        // act
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: CHARACTERISTIC_HANDLE.into(),
            })
            .view(),
        );
        let tx = if let MockDatastoreEvents::Read(
            TCB_IDX,
            CHARACTERISTIC_HANDLE,
            AttributeBackingType::Characteristic,
            tx,
        ) = data_rx.recv().await.unwrap()
        {
            tx
        } else {
            unreachable!()
        };
        tx.send(Ok(data.clone())).unwrap();
        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        // assert
        assert_eq!(tcb_idx, TCB_IDX);
        assert_eq!(
            resp,
            AttBuilder {
                opcode: AttOpcode::READ_RESPONSE,
                _child_: AttReadResponseBuilder { value: build_att_data(data) }.into()
            }
        );
    })
}

#[test]
fn test_characteristic_write() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        let data = AttAttributeDataChild::RawData(DATA.into());

        let mut data_rx = create_server_and_open_connection(&mut gatt);

        // act
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttWriteRequestBuilder {
                handle: CHARACTERISTIC_HANDLE.into(),
                value: build_att_data(data.clone()),
            })
            .view(),
        );
        let (tx, written_data) = if let MockDatastoreEvents::Write(
            TCB_IDX,
            CHARACTERISTIC_HANDLE,
            AttributeBackingType::Characteristic,
            written_data,
            tx,
        ) = data_rx.recv().await.unwrap()
        {
            (tx, written_data)
        } else {
            unreachable!()
        };
        tx.send(Ok(())).unwrap();
        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        // assert
        assert_eq!(tcb_idx, TCB_IDX);
        assert_eq!(
            resp,
            AttBuilder {
                opcode: AttOpcode::WRITE_RESPONSE,
                _child_: AttWriteResponseBuilder {}.into()
            }
        );
        assert_eq!(
            data.to_vec().unwrap(),
            written_data.view().get_raw_payload().collect::<Vec<_>>()
        )
    })
}

#[test]
fn test_send_indication() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        let data = AttAttributeDataChild::RawData(DATA.into());

        create_server_and_open_connection(&mut gatt);

        // act
        let pending_indication = spawn_local(
            gatt.get_bearer(TCB_IDX).unwrap().send_indication(CHARACTERISTIC_HANDLE, data.clone()),
        );

        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        gatt.get_bearer(TCB_IDX)
            .unwrap()
            .handle_packet(build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view());

        // assert
        assert!(matches!(pending_indication.await.unwrap(), Ok(())));
        assert_eq!(tcb_idx, TCB_IDX);
        assert_eq!(
            resp,
            AttBuilder {
                opcode: AttOpcode::HANDLE_VALUE_INDICATION,
                _child_: AttHandleValueIndicationBuilder {
                    handle: CHARACTERISTIC_HANDLE.into(),
                    value: build_att_data(data),
                }
                .into()
            }
        );
    })
}

#[test]
fn test_send_indication_and_disconnect() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        create_server_and_open_connection(&mut gatt);

        // act: send an indication, then disconnect
        let pending_indication = spawn_local(gatt.get_bearer(TCB_IDX).unwrap().send_indication(
            CHARACTERISTIC_HANDLE,
            AttAttributeDataChild::RawData([1, 2, 3, 4].into()),
        ));
        transport_rx.recv().await.unwrap();
        gatt.on_le_disconnect(TCB_IDX).unwrap();

        // assert: the pending indication resolves appropriately
        assert!(matches!(
            pending_indication.await.unwrap(),
            Err(IndicationError::ConnectionDroppedWhileWaitingForConfirmation)
        ));
    })
}

#[test]
fn test_write_to_descriptor() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();

        let data = AttAttributeDataChild::RawData(DATA.into());

        let mut data_rx = create_server_and_open_connection(&mut gatt);

        // act
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttWriteRequestBuilder {
                handle: DESCRIPTOR_HANDLE.into(),
                value: build_att_data(data.clone()),
            })
            .view(),
        );
        let (tx, written_data) = if let MockDatastoreEvents::Write(
            TCB_IDX,
            DESCRIPTOR_HANDLE,
            AttributeBackingType::Descriptor,
            written_data,
            tx,
        ) = data_rx.recv().await.unwrap()
        {
            (tx, written_data)
        } else {
            unreachable!()
        };
        tx.send(Ok(())).unwrap();
        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        // assert
        assert_eq!(tcb_idx, TCB_IDX);
        assert_eq!(
            resp,
            AttBuilder {
                opcode: AttOpcode::WRITE_RESPONSE,
                _child_: AttWriteResponseBuilder {}.into()
            }
        );
        assert_eq!(
            data.to_vec().unwrap(),
            written_data.view().get_raw_payload().collect::<Vec<_>>()
        )
    })
}

#[test]
fn test_multiple_servers() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();
        let data = AttAttributeDataChild::RawData(DATA.into());
        let another_data = AttAttributeDataChild::RawData(ANOTHER_DATA.into());
        // open the default server (SERVER_ID on CONN_ID)
        let mut data_rx_1 = create_server_and_open_connection(&mut gatt);
        // open a second server and connect to it (ANOTHER_SERVER_ID on ANOTHER_CONN_ID)
        let (datastore, mut data_rx_2) = MockDatastore::new();
        gatt.open_gatt_server(ANOTHER_SERVER_ID).unwrap();
        gatt.register_gatt_service(
            ANOTHER_SERVER_ID,
            GattServiceWithHandle {
                handle: SERVICE_HANDLE,
                type_: SERVICE_TYPE,
                characteristics: vec![GattCharacteristicWithHandle {
                    handle: CHARACTERISTIC_HANDLE,
                    type_: CHARACTERISTIC_TYPE,
                    permissions: AttPermissions::READABLE,
                    descriptors: vec![],
                }],
            },
            datastore,
        )
        .unwrap();
        gatt.get_isolation_manager()
            .associate_server_with_advertiser(ANOTHER_SERVER_ID, ANOTHER_ADVERTISER_ID);
        gatt.on_le_connect(ANOTHER_TCB_IDX, Some(ANOTHER_ADVERTISER_ID)).unwrap();

        // act: read from both connections
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: CHARACTERISTIC_HANDLE.into(),
            })
            .view(),
        );
        gatt.get_bearer(ANOTHER_TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: CHARACTERISTIC_HANDLE.into(),
            })
            .view(),
        );
        // service the first read with `data`
        let MockDatastoreEvents::Read(
            TCB_IDX,
            _, _,
            tx,
        ) = data_rx_1.recv().await.unwrap() else {
            unreachable!()
        };
        tx.send(Ok(data.clone())).unwrap();
        // and then the second read with `another_data`
        let MockDatastoreEvents::Read(
            ANOTHER_TCB_IDX,
            _, _,
            tx,
        ) = data_rx_2.recv().await.unwrap() else {
            unreachable!()
        };
        tx.send(Ok(another_data.clone())).unwrap();

        // receive both response packets
        let (tcb_idx_1, resp_1) = transport_rx.recv().await.unwrap();
        let (tcb_idx_2, resp_2) = transport_rx.recv().await.unwrap();

        // assert: the responses were routed to the correct connections
        assert_eq!(tcb_idx_1, TCB_IDX);
        assert_eq!(resp_1._child_.to_vec().unwrap(), DATA);
        assert_eq!(tcb_idx_2, ANOTHER_TCB_IDX);
        assert_eq!(resp_2._child_.to_vec().unwrap(), ANOTHER_DATA);
    })
}

#[test]
fn test_read_device_name() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();
        create_server_and_open_connection(&mut gatt);

        // act: try to read the device name
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadByTypeRequestBuilder {
                starting_handle: AttHandle(1).into(),
                ending_handle: AttHandle(0xFFFF).into(),
                attribute_type: DEVICE_NAME_UUID.into(),
            })
            .view(),
        );
        let (tcb_idx, resp) = transport_rx.recv().await.unwrap();

        // assert: the name should not be readable
        assert_eq!(tcb_idx, TCB_IDX);
        let AttChild::AttErrorResponse(resp) = resp._child_ else {
            unreachable!("{resp:?}");
        };
        assert_eq!(resp.error_code, AttErrorCode::INSUFFICIENT_AUTHENTICATION);
    });
}

#[test]
fn test_ignored_service_change_indication() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();
        create_server_and_open_connection(&mut gatt);

        // act: add a new service
        let (datastore, _) = MockDatastore::new();
        gatt.register_gatt_service(
            SERVER_ID,
            GattServiceWithHandle {
                handle: AttHandle(30),
                type_: SERVICE_TYPE,
                characteristics: vec![],
            },
            datastore,
        )
        .unwrap();

        // assert: no packets should be sent
        assert_eq!(transport_rx.try_recv().unwrap_err(), TryRecvError::Empty);
    });
}

#[test]
fn test_service_change_indication() {
    start_test(async move {
        // arrange
        let (mut gatt, mut transport_rx) = start_gatt_module();
        create_server_and_open_connection(&mut gatt);

        // act: discover the GATT server
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttFindByTypeValueRequestBuilder {
                starting_handle: AttHandle::MIN.into(),
                ending_handle: AttHandle::MAX.into(),
                attribute_type: PRIMARY_SERVICE_DECLARATION_UUID.try_into().unwrap(),
                attribute_value: build_att_data(UuidAsAttDataBuilder {
                    uuid: GATT_SERVICE_UUID.into(),
                }),
            })
            .view(),
        );
        let AttChild::AttFindByTypeValueResponse(resp) = transport_rx.recv().await.unwrap().1._child_ else {
            unreachable!()
        };
        let (starting_handle, ending_handle) = (
            resp.handles_info[0].clone().found_attribute_handle,
            resp.handles_info[0].clone().group_end_handle,
        );
        // act: discover the service changed characteristic
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttReadByTypeRequestBuilder {
                starting_handle,
                ending_handle,
                attribute_type: CHARACTERISTIC_UUID.into(),
            })
            .view(),
        );
        let AttChild::AttReadByTypeResponse(resp) = transport_rx.recv().await.unwrap().1._child_ else {
            unreachable!()
        };
        let service_change_char_handle = resp.data.into_vec().into_iter().find_map(|characteristic| {
            let AttAttributeDataChild::GattCharacteristicDeclarationValue(decl) = characteristic.value._child_ else {
                unreachable!();
            };
            if decl.uuid == SERVICE_CHANGE_UUID.into() {
                Some(decl.handle)
            } else {
                None
            }
        }).unwrap();
        // act: find the CCC descriptor for the service changed characteristic
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttFindInformationRequestBuilder {
                starting_handle: service_change_char_handle.clone(),
                ending_handle: AttHandle::MAX.into(),
            })
            .view(),
        );
        let AttChild::AttFindInformationResponse(resp) = transport_rx.recv().await.unwrap().1._child_ else {
            unreachable!()
        };
        let AttFindInformationResponseChild::AttFindInformationShortResponse(resp) = resp._child_ else {
            unreachable!()
        };
        let service_change_descriptor_handle = resp
            .data
            .into_vec()
            .into_iter()
            .find_map(|attr| {
                if attr.uuid == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID.try_into().unwrap() {
                    Some(attr.handle)
                } else {
                    None
                }
            })
            .unwrap();
        // act: register for indications on this handle
        gatt.get_bearer(TCB_IDX).unwrap().handle_packet(
            build_att_view_or_crash(AttWriteRequestBuilder {
                handle: service_change_descriptor_handle,
                value: build_att_data(GattClientCharacteristicConfigurationBuilder {
                    notification: 0,
                    indication: 1,
                }),
            })
            .view(),
        );
        let AttChild::AttWriteResponse(_) = transport_rx.recv().await.unwrap().1._child_ else {
            unreachable!()
        };
        // act: add a new service
        let (datastore, _) = MockDatastore::new();
        gatt.register_gatt_service(
            SERVER_ID,
            GattServiceWithHandle {
                handle: AttHandle(30),
                type_: SERVICE_TYPE,
                characteristics: vec![],
            },
            datastore,
        )
        .unwrap();

        // assert: we got an indication
        let AttChild::AttHandleValueIndication(indication) = transport_rx.recv().await.unwrap().1._child_ else {
            unreachable!()
        };
        assert_eq!(indication.handle, service_change_char_handle);
        assert_eq!(
            indication.value,
            build_att_data(GattServiceChangedBuilder {
                start_handle: AttHandle(30).into(),
                end_handle: AttHandle(30).into(),
            })
        );
    });
}

#[test]
fn test_closing_gatt_server_unisolates_advertiser() {
    start_test(async move {
        // arrange
        let (mut gatt, _) = start_gatt_module();
        gatt.open_gatt_server(SERVER_ID).unwrap();
        gatt.get_isolation_manager().associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);

        // act
        gatt.close_gatt_server(SERVER_ID).unwrap();

        // assert
        let is_advertiser_isolated =
            gatt.get_isolation_manager().is_advertiser_isolated(ADVERTISER_ID);
        assert!(!is_advertiser_isolated);
    });
}

#[test]
fn test_disconnection_unisolates_connection() {
    start_test(async move {
        // arrange
        let (mut gatt, _) = start_gatt_module();
        create_server_and_open_connection(&mut gatt);

        // act
        gatt.on_le_disconnect(TCB_IDX).unwrap();

        // assert
        let is_connection_isolated = gatt.get_isolation_manager().is_connection_isolated(TCB_IDX);
        assert!(!is_connection_isolated);
    });
}
