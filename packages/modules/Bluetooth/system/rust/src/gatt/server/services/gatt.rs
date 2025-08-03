//! The GATT service as defined in Core Spec 5.3 Vol 3G Section 7

use std::{cell::RefCell, collections::HashMap, ops::RangeInclusive, rc::Rc};

use anyhow::Result;
use async_trait::async_trait;
use log::{error, warn};
use tokio::task::spawn_local;

use crate::{
    core::{
        shared_box::{WeakBox, WeakBoxRef},
        uuid::Uuid,
    },
    gatt::{
        callbacks::GattDatastore,
        ffi::AttributeBackingType,
        ids::{AttHandle, TransportIndex},
        server::{
            att_server_bearer::AttServerBearer,
            gatt_database::{
                AttDatabaseImpl, AttPermissions, GattCharacteristicWithHandle, GattDatabase,
                GattDatabaseCallbacks, GattDescriptorWithHandle, GattServiceWithHandle,
            },
        },
    },
    packets::{
        AttAttributeDataChild, AttAttributeDataView, AttErrorCode,
        GattClientCharacteristicConfigurationBuilder, GattClientCharacteristicConfigurationView,
        GattServiceChangedBuilder, Packet,
    },
};

#[derive(Default)]
struct GattService {
    clients: RefCell<HashMap<TransportIndex, ClientState>>,
}

#[derive(Clone)]
struct ClientState {
    bearer: WeakBox<AttServerBearer<AttDatabaseImpl>>,
    registered_for_service_change: bool,
}

// Must lie in the range specified by GATT_GATT_START_HANDLE from legacy stack
const GATT_SERVICE_HANDLE: AttHandle = AttHandle(1);
const SERVICE_CHANGE_HANDLE: AttHandle = AttHandle(3);
const SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE: AttHandle = AttHandle(4);

/// The UUID used for the GATT service (Assigned Numbers 3.4.1 Services by Name)
pub const GATT_SERVICE_UUID: Uuid = Uuid::new(0x1801);
/// The UUID used for the Service Changed characteristic (Assigned Numbers 3.8.1 Characteristics by Name)
pub const SERVICE_CHANGE_UUID: Uuid = Uuid::new(0x2A05);
/// The UUID used for the Client Characteristic Configuration descriptor (Assigned Numbers 3.7 Descriptors)
pub const CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: Uuid = Uuid::new(0x2902);

#[async_trait(?Send)]
impl GattDatastore for GattService {
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        _: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        if handle == SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE {
            Ok(GattClientCharacteristicConfigurationBuilder {
                notification: 0,
                indication: self
                    .clients
                    .borrow()
                    .get(&tcb_idx)
                    .map(|state| state.registered_for_service_change)
                    .unwrap_or(false)
                    .into(),
            }
            .into())
        } else {
            unreachable!()
        }
    }

    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        _: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        if handle == SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE {
            let ccc =
                GattClientCharacteristicConfigurationView::try_parse(data).map_err(|err| {
                    warn!("failed to parse CCC descriptor, got: {err:?}");
                    AttErrorCode::APPLICATION_ERROR
                })?;
            let mut clients = self.clients.borrow_mut();
            let state = clients.get_mut(&tcb_idx);
            let Some(state) = state else {
                error!("Received write request from disconnected client...");
                return Err(AttErrorCode::UNLIKELY_ERROR);
            };
            state.registered_for_service_change = ccc.get_indication() != 0;
            Ok(())
        } else {
            unreachable!()
        }
    }
}

impl GattDatabaseCallbacks for GattService {
    fn on_le_connect(
        &self,
        tcb_idx: TransportIndex,
        bearer: WeakBoxRef<AttServerBearer<AttDatabaseImpl>>,
    ) {
        // TODO(aryarahul): registered_for_service_change may not be false for bonded devices
        self.clients.borrow_mut().insert(
            tcb_idx,
            ClientState { bearer: bearer.downgrade(), registered_for_service_change: false },
        );
    }

    fn on_le_disconnect(&self, tcb_idx: TransportIndex) {
        self.clients.borrow_mut().remove(&tcb_idx);
    }

    fn on_service_change(&self, range: RangeInclusive<AttHandle>) {
        for (conn_id, client) in self.clients.borrow().clone() {
            if client.registered_for_service_change {
                client.bearer.with(|bearer| match bearer {
                    Some(bearer) => {
                        spawn_local(
                            bearer.send_indication(
                                SERVICE_CHANGE_HANDLE,
                                GattServiceChangedBuilder {
                                    start_handle: (*range.start()).into(),
                                    end_handle: (*range.end()).into(),
                                }
                                .into(),
                            ),
                        );
                    }
                    None => {
                        error!("Registered client's bearer has been destructed ({conn_id:?})")
                    }
                });
            }
        }
    }
}

/// Register the GATT service in the provided GATT database.
pub fn register_gatt_service(database: &mut GattDatabase) -> Result<()> {
    let this = Rc::new(GattService::default());
    database.add_service_with_handles(
        // GATT Service
        GattServiceWithHandle {
            handle: GATT_SERVICE_HANDLE,
            type_: GATT_SERVICE_UUID,
            // Service Changed Characteristic
            characteristics: vec![GattCharacteristicWithHandle {
                handle: SERVICE_CHANGE_HANDLE,
                type_: SERVICE_CHANGE_UUID,
                permissions: AttPermissions::INDICATE,
                descriptors: vec![GattDescriptorWithHandle {
                    handle: SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE,
                    type_: CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
                    permissions: AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE,
                }],
            }],
        },
        this.clone(),
    )?;
    database.register_listener(this);
    Ok(())
}
#[cfg(test)]
mod test {
    use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver};

    use super::*;

    use crate::{
        core::shared_box::SharedBox,
        gatt::{
            mocks::mock_datastore::MockDatastore,
            server::{
                att_database::AttDatabase,
                gatt_database::{
                    GattDatabase, CHARACTERISTIC_UUID, PRIMARY_SERVICE_DECLARATION_UUID,
                },
            },
        },
        packets::{AttBuilder, AttChild},
        utils::{
            packet::{build_att_data, build_view_or_crash},
            task::{block_on_locally, try_await},
        },
    };

    const TCB_IDX: TransportIndex = TransportIndex(1);
    const ANOTHER_TCB_IDX: TransportIndex = TransportIndex(2);
    const SERVICE_TYPE: Uuid = Uuid::new(0x1234);
    const CHARACTERISTIC_TYPE: Uuid = Uuid::new(0x5678);

    fn init_gatt_db() -> SharedBox<GattDatabase> {
        let mut gatt_database = GattDatabase::new();
        register_gatt_service(&mut gatt_database).unwrap();
        SharedBox::new(gatt_database)
    }

    fn add_connection(
        gatt_database: &SharedBox<GattDatabase>,
        tcb_idx: TransportIndex,
    ) -> (AttDatabaseImpl, SharedBox<AttServerBearer<AttDatabaseImpl>>, UnboundedReceiver<AttBuilder>)
    {
        let att_database = gatt_database.get_att_database(tcb_idx);
        let (tx, rx) = unbounded_channel();
        let bearer = SharedBox::new(AttServerBearer::new(att_database.clone(), move |packet| {
            tx.send(packet).unwrap();
            Ok(())
        }));
        gatt_database.on_bearer_ready(tcb_idx, bearer.as_ref());
        (att_database, bearer, rx)
    }

    #[test]
    fn test_gatt_service_discovery() {
        // arrange
        let gatt_db = init_gatt_db();
        let (att_db, _, _) = add_connection(&gatt_db, TCB_IDX);

        // act: discover all services
        let attrs = att_db.list_attributes();

        // assert: 1 service + 1 char decl + 1 char value + 1 char descriptor = 4 attrs
        assert_eq!(attrs.len(), 4);
        // assert: value handles are correct
        assert_eq!(attrs[0].handle, GATT_SERVICE_HANDLE);
        assert_eq!(attrs[2].handle, SERVICE_CHANGE_HANDLE);
        // assert: types are correct
        assert_eq!(attrs[0].type_, PRIMARY_SERVICE_DECLARATION_UUID);
        assert_eq!(attrs[1].type_, CHARACTERISTIC_UUID);
        assert_eq!(attrs[2].type_, SERVICE_CHANGE_UUID);
        assert_eq!(attrs[3].type_, CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
        // assert: permissions of value attrs are correct
        assert_eq!(attrs[2].permissions, AttPermissions::INDICATE);
        assert_eq!(
            attrs[3].permissions,
            AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE
        );
    }

    #[test]
    fn test_default_indication_subscription() {
        // arrange
        let gatt_db = init_gatt_db();
        let (att_db, _, _) = add_connection(&gatt_db, TCB_IDX);

        // act: try to read the CCC descriptor
        let resp =
            block_on_locally(att_db.read_attribute(SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE)).unwrap();

        // assert: we are not registered for either indications/notifications
        let AttAttributeDataChild::GattClientCharacteristicConfiguration(configuration) = resp else {
            unreachable!()
        };
        assert_eq!(
            configuration,
            GattClientCharacteristicConfigurationBuilder { notification: 0, indication: 0 }
        );
    }

    async fn register_for_indication(
        att_db: &impl AttDatabase,
        handle: AttHandle,
    ) -> Result<(), AttErrorCode> {
        att_db
            .write_attribute(
                handle,
                build_view_or_crash(build_att_data(GattClientCharacteristicConfigurationBuilder {
                    notification: 0,
                    indication: 1,
                }))
                .view(),
            )
            .await
    }

    #[test]
    fn test_subscribe_to_indication() {
        // arrange
        let gatt_db = init_gatt_db();
        let (att_db, _, _) = add_connection(&gatt_db, TCB_IDX);

        // act: register for service change indication
        block_on_locally(register_for_indication(&att_db, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE))
            .unwrap();
        // read our registration status
        let resp =
            block_on_locally(att_db.read_attribute(SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE)).unwrap();

        // assert: we are registered for indications
        let AttAttributeDataChild::GattClientCharacteristicConfiguration(configuration) = resp else {
            unreachable!()
        };
        assert_eq!(
            configuration,
            GattClientCharacteristicConfigurationBuilder { notification: 0, indication: 1 }
        );
    }

    #[test]
    fn test_unsubscribe_to_indication() {
        // arrange
        let gatt_db = init_gatt_db();
        let (att_db, _, _) = add_connection(&gatt_db, TCB_IDX);

        // act: register for service change indication
        block_on_locally(
            att_db.write_attribute(
                SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE,
                build_view_or_crash(build_att_data(GattClientCharacteristicConfigurationBuilder {
                    notification: 0,
                    indication: 1,
                }))
                .view(),
            ),
        )
        .unwrap();
        // act: next, unregister from this indication
        block_on_locally(
            att_db.write_attribute(
                SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE,
                build_view_or_crash(build_att_data(GattClientCharacteristicConfigurationBuilder {
                    notification: 0,
                    indication: 0,
                }))
                .view(),
            ),
        )
        .unwrap();
        // read our registration status
        let resp =
            block_on_locally(att_db.read_attribute(SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE)).unwrap();

        // assert: we are not registered for indications
        let AttAttributeDataChild::GattClientCharacteristicConfiguration(configuration) = resp else {
            unreachable!()
        };
        assert_eq!(
            configuration,
            GattClientCharacteristicConfigurationBuilder { notification: 0, indication: 0 }
        );
    }

    #[test]
    fn test_single_registered_service_change_indication() {
        block_on_locally(async {
            // arrange
            let gatt_db = init_gatt_db();
            let (att_db, _bearer, mut rx) = add_connection(&gatt_db, TCB_IDX);
            let (gatt_datastore, _) = MockDatastore::new();
            let gatt_datastore = Rc::new(gatt_datastore);
            register_for_indication(&att_db, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();

            // act: register some new service
            gatt_db
                .add_service_with_handles(
                    GattServiceWithHandle {
                        handle: AttHandle(15),
                        type_: SERVICE_TYPE,
                        characteristics: vec![GattCharacteristicWithHandle {
                            handle: AttHandle(17),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::empty(),
                            descriptors: vec![],
                        }],
                    },
                    gatt_datastore,
                )
                .unwrap();

            // assert: we received the service change indication
            let resp = rx.recv().await.unwrap();
            let AttChild::AttHandleValueIndication(resp) = resp._child_ else {
                unreachable!();
            };
            let AttAttributeDataChild::GattServiceChanged(resp) = resp.value._child_ else {
                unreachable!();
            };
            assert_eq!(resp.start_handle.handle, 15);
            assert_eq!(resp.end_handle.handle, 17);
        });
    }

    #[test]
    fn test_multiple_registered_service_change_indication() {
        block_on_locally(async {
            // arrange: two connections, both registered
            let gatt_db = init_gatt_db();
            let (att_db_1, _bearer, mut rx1) = add_connection(&gatt_db, TCB_IDX);
            let (att_db_2, _bearer, mut rx2) = add_connection(&gatt_db, ANOTHER_TCB_IDX);

            register_for_indication(&att_db_1, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();
            register_for_indication(&att_db_2, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();

            let (gatt_datastore, _) = MockDatastore::new();
            let gatt_datastore = Rc::new(gatt_datastore);

            // act: register some new service
            gatt_db
                .add_service_with_handles(
                    GattServiceWithHandle {
                        handle: AttHandle(15),
                        type_: SERVICE_TYPE,
                        characteristics: vec![GattCharacteristicWithHandle {
                            handle: AttHandle(17),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::empty(),
                            descriptors: vec![],
                        }],
                    },
                    gatt_datastore,
                )
                .unwrap();

            // assert: both connections received the service change indication
            let resp1 = rx1.recv().await.unwrap();
            let resp2 = rx2.recv().await.unwrap();
            assert!(matches!(resp1._child_, AttChild::AttHandleValueIndication(_)));
            assert!(matches!(resp2._child_, AttChild::AttHandleValueIndication(_)));
        });
    }

    #[test]
    fn test_one_unregistered_service_change_indication() {
        block_on_locally(async {
            // arrange: two connections, only the first is registered
            let gatt_db = init_gatt_db();
            let (att_db_1, _bearer, mut rx1) = add_connection(&gatt_db, TCB_IDX);
            let (_, _bearer, mut rx2) = add_connection(&gatt_db, ANOTHER_TCB_IDX);

            register_for_indication(&att_db_1, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();

            let (gatt_datastore, _) = MockDatastore::new();
            let gatt_datastore = Rc::new(gatt_datastore);

            // act: register some new service
            gatt_db
                .add_service_with_handles(
                    GattServiceWithHandle {
                        handle: AttHandle(15),
                        type_: SERVICE_TYPE,
                        characteristics: vec![GattCharacteristicWithHandle {
                            handle: AttHandle(17),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::empty(),
                            descriptors: vec![],
                        }],
                    },
                    gatt_datastore,
                )
                .unwrap();

            // assert: the first connection received the service change indication
            let resp1 = rx1.recv().await.unwrap();
            assert!(matches!(resp1._child_, AttChild::AttHandleValueIndication(_)));
            // assert: the second connection received nothing
            assert!(try_await(async move { rx2.recv().await }).await.is_err());
        });
    }

    #[test]
    fn test_one_disconnected_service_change_indication() {
        block_on_locally(async {
            // arrange: two connections, both register, but the second one disconnects
            let gatt_db = init_gatt_db();
            let (att_db_1, _bearer, mut rx1) = add_connection(&gatt_db, TCB_IDX);
            let (att_db_2, bearer_2, mut rx2) = add_connection(&gatt_db, ANOTHER_TCB_IDX);

            register_for_indication(&att_db_1, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();
            register_for_indication(&att_db_2, SERVICE_CHANGE_CCC_DESCRIPTOR_HANDLE).await.unwrap();

            drop(bearer_2);
            gatt_db.on_bearer_dropped(ANOTHER_TCB_IDX);

            let (gatt_datastore, _) = MockDatastore::new();
            let gatt_datastore = Rc::new(gatt_datastore);

            // act: register some new service
            gatt_db
                .add_service_with_handles(
                    GattServiceWithHandle {
                        handle: AttHandle(15),
                        type_: SERVICE_TYPE,
                        characteristics: vec![GattCharacteristicWithHandle {
                            handle: AttHandle(17),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::empty(),
                            descriptors: vec![],
                        }],
                    },
                    gatt_datastore,
                )
                .unwrap();

            // assert: the first connection received the service change indication
            let resp1 = rx1.recv().await.unwrap();
            assert!(matches!(resp1._child_, AttChild::AttHandleValueIndication(_)));
            // assert: the second connection is closed
            assert!(rx2.recv().await.is_none());
        });
    }
}
