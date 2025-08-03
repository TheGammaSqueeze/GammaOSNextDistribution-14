//! This module converts a GattDatastore to an AttDatabase,
//! by converting a registry of services into a list of attributes, and proxying
//! ATT read/write requests into characteristic reads/writes

use std::{cell::RefCell, collections::BTreeMap, ops::RangeInclusive, rc::Rc};

use anyhow::{bail, Result};
use async_trait::async_trait;
use log::{error, warn};

use crate::{
    core::{
        shared_box::{SharedBox, WeakBox, WeakBoxRef},
        uuid::Uuid,
    },
    gatt::{
        callbacks::{GattWriteRequestType, RawGattDatastore},
        ffi::AttributeBackingType,
        ids::{AttHandle, TransportIndex},
    },
    packets::{
        AttAttributeDataChild, AttAttributeDataView, AttErrorCode,
        GattCharacteristicDeclarationValueBuilder, GattCharacteristicPropertiesBuilder,
        GattServiceDeclarationValueBuilder, UuidBuilder,
    },
};

use super::{
    att_database::{AttAttribute, AttDatabase},
    att_server_bearer::AttServerBearer,
};

pub use super::att_database::AttPermissions;

/// Primary Service Declaration from Bluetooth Assigned Numbers 3.5 Declarations
pub const PRIMARY_SERVICE_DECLARATION_UUID: Uuid = Uuid::new(0x2800);
/// Secondary Service Declaration from Bluetooth Assigned Numbers 3.5 Declarations
pub const SECONDARY_SERVICE_DECLARATION_UUID: Uuid = Uuid::new(0x2801);
/// Characteristic Declaration from Bluetooth Assigned Numbers 3.5 Declarations
pub const CHARACTERISTIC_UUID: Uuid = Uuid::new(0x2803);

/// A GattService (currently, only primary services are supported) has an
/// identifying UUID and a list of contained characteristics, as well as a
/// handle (indicating the attribute where the service declaration will live)
#[derive(Debug, Clone)]
pub struct GattServiceWithHandle {
    /// The handle of the service declaration
    pub handle: AttHandle,
    /// The type of the service
    pub type_: Uuid,
    /// A list of contained characteristics (that must have handles between the
    /// service declaration handle, and that of the next service)
    pub characteristics: Vec<GattCharacteristicWithHandle>,
}

/// A GattCharacteristic consists of a handle (where the value attribute lives),
/// a UUID identifying its type, and permissions indicating what operations can
/// be performed
#[derive(Debug, Clone)]
pub struct GattCharacteristicWithHandle {
    /// The handle of the characteristic value attribute. The characteristic
    /// declaration is one before this handle.
    pub handle: AttHandle,
    /// The UUID representing the type of the characteristic value.
    pub type_: Uuid,
    /// The permissions (read/write) indicate what operations can be performed.
    pub permissions: AttPermissions,
    /// The descriptors associated with this characteristic
    pub descriptors: Vec<GattDescriptorWithHandle>,
}

/// A GattDescriptor consists of a handle, type_, and permissions (similar to a
/// GattCharacteristic) It is guaranteed that the handle of the GattDescriptor
/// is after the handle of the characteristic value attribute, and before the
/// next characteristic/service declaration
#[derive(Debug, Clone)]
pub struct GattDescriptorWithHandle {
    /// The handle of the descriptor.
    pub handle: AttHandle,
    /// The UUID representing the type of the descriptor.
    pub type_: Uuid,
    /// The permissions (read/write) indicate what operations can be performed.
    pub permissions: AttPermissions,
}

/// The GattDatabase implements AttDatabase, and converts attribute reads/writes
/// into GATT operations to be sent to the upper layers
#[derive(Default)]
pub struct GattDatabase {
    schema: RefCell<GattDatabaseSchema>,
    listeners: RefCell<Vec<Rc<dyn GattDatabaseCallbacks>>>,
}

#[derive(Default)]
struct GattDatabaseSchema {
    attributes: BTreeMap<AttHandle, AttAttributeWithBackingValue>,
}

#[derive(Clone)]
enum AttAttributeBackingValue {
    Static(AttAttributeDataChild),
    DynamicCharacteristic(Rc<dyn RawGattDatastore>),
    DynamicDescriptor(Rc<dyn RawGattDatastore>),
}

#[derive(Clone)]
struct AttAttributeWithBackingValue {
    attribute: AttAttribute,
    value: AttAttributeBackingValue,
}

/// Callbacks that can be registered on the GattDatabase to watch for
/// events of interest.
///
/// Note: if the GattDatabase is dropped (e.g. due to unregistration), these
/// callbacks will not be invoked, even if the relevant event occurs later.
/// e.g. if we open the db, connect, close the db, then disconnect, then on_le_disconnect()
/// will NEVER be invoked.
pub trait GattDatabaseCallbacks {
    /// A peer device on the given bearer has connected to this database (and can see its attributes)
    fn on_le_connect(
        &self,
        tcb_idx: TransportIndex,
        bearer: WeakBoxRef<AttServerBearer<AttDatabaseImpl>>,
    );
    /// A peer device has disconnected from this database
    fn on_le_disconnect(&self, tcb_idx: TransportIndex);
    /// The attributes in the specified range have changed
    fn on_service_change(&self, range: RangeInclusive<AttHandle>);
}

impl GattDatabase {
    /// Constructor, wrapping a GattDatastore
    pub fn new() -> Self {
        Default::default()
    }

    /// Register an event listener
    pub fn register_listener(&self, callbacks: Rc<dyn GattDatabaseCallbacks>) {
        self.listeners.borrow_mut().push(callbacks);
    }

    /// When a connection has been made with access to this database.
    /// The supplied bearer is guaranteed to be ready for use.
    pub fn on_bearer_ready(
        &self,
        tcb_idx: TransportIndex,
        bearer: WeakBoxRef<AttServerBearer<AttDatabaseImpl>>,
    ) {
        for listener in self.listeners.borrow().iter() {
            listener.on_le_connect(tcb_idx, bearer.clone());
        }
    }

    /// When the connection has dropped.
    pub fn on_bearer_dropped(&self, tcb_idx: TransportIndex) {
        for listener in self.listeners.borrow().iter() {
            listener.on_le_disconnect(tcb_idx);
        }
    }

    /// Add a service with pre-allocated handles (for co-existence with C++) backed by the supplied datastore
    /// Assumes that the characteristic DECLARATION handles are one less than
    /// the characteristic handles.
    /// Returns failure if handles overlap with ones already allocated
    pub fn add_service_with_handles(
        &self,
        service: GattServiceWithHandle,
        datastore: Rc<dyn RawGattDatastore>,
    ) -> Result<()> {
        let mut attributes = BTreeMap::new();
        let mut attribute_cnt = 0;

        let mut add_attribute = |attribute: AttAttribute, value: AttAttributeBackingValue| {
            attribute_cnt += 1;
            attributes.insert(attribute.handle, AttAttributeWithBackingValue { attribute, value })
        };

        let mut characteristics = vec![];

        // service definition
        add_attribute(
            AttAttribute {
                handle: service.handle,
                type_: PRIMARY_SERVICE_DECLARATION_UUID,
                permissions: AttPermissions::READABLE,
            },
            AttAttributeBackingValue::Static(
                GattServiceDeclarationValueBuilder { uuid: UuidBuilder::from(service.type_) }
                    .into(),
            ),
        );

        // characteristics
        for characteristic in service.characteristics {
            characteristics.push(characteristic.clone());

            // declaration
            // Recall that we assume the declaration handle is one less than the value
            // handle
            let declaration_handle = AttHandle(characteristic.handle.0 - 1);

            add_attribute(
                AttAttribute {
                    handle: declaration_handle,
                    type_: CHARACTERISTIC_UUID,
                    permissions: AttPermissions::READABLE,
                },
                AttAttributeBackingValue::Static(
                    GattCharacteristicDeclarationValueBuilder {
                        properties: GattCharacteristicPropertiesBuilder {
                            broadcast: 0,
                            read: characteristic.permissions.readable().into(),
                            write_without_response: characteristic
                                .permissions
                                .writable_without_response()
                                .into(),
                            write: characteristic.permissions.writable_with_response().into(),
                            notify: 0,
                            indicate: characteristic.permissions.indicate().into(),
                            authenticated_signed_writes: 0,
                            extended_properties: 0,
                        },
                        handle: characteristic.handle.into(),
                        uuid: characteristic.type_.into(),
                    }
                    .into(),
                ),
            );

            // value
            add_attribute(
                AttAttribute {
                    handle: characteristic.handle,
                    type_: characteristic.type_,
                    permissions: characteristic.permissions,
                },
                AttAttributeBackingValue::DynamicCharacteristic(datastore.clone()),
            );

            // descriptors
            for descriptor in characteristic.descriptors {
                add_attribute(
                    AttAttribute {
                        handle: descriptor.handle,
                        type_: descriptor.type_,
                        permissions: descriptor.permissions,
                    },
                    AttAttributeBackingValue::DynamicDescriptor(datastore.clone()),
                );
            }
        }

        // validate attributes for overlap
        let mut static_data = self.schema.borrow_mut();

        for handle in attributes.keys() {
            if static_data.attributes.contains_key(handle) {
                bail!("duplicate handle detected");
            }
        }
        if attributes.len() != attribute_cnt {
            bail!("duplicate handle detected");
        }

        // if we made it here, we successfully loaded the new service
        static_data.attributes.extend(attributes.clone());

        // re-entrancy via the listeners is possible, so we prevent it by dropping here
        drop(static_data);

        // notify listeners if any attribute changed
        let added_handles = attributes.into_iter().map(|attr| attr.0).collect::<Vec<_>>();
        if !added_handles.is_empty() {
            for listener in self.listeners.borrow().iter() {
                listener.on_service_change(
                    *added_handles.iter().min().unwrap()..=*added_handles.iter().max().unwrap(),
                );
            }
        }

        Ok(())
    }

    /// Remove a previously-added service by service handle
    pub fn remove_service_at_handle(&self, service_handle: AttHandle) -> Result<()> {
        let mut static_data = self.schema.borrow_mut();

        // find next service
        let next_service_handle = static_data
            .attributes
            .values()
            .find(|attribute| {
                attribute.attribute.handle > service_handle
                    && attribute.attribute.type_ == PRIMARY_SERVICE_DECLARATION_UUID
            })
            .map(|service| service.attribute.handle);

        // predicate matching all handles in our service
        let in_service_pred = |handle: AttHandle| {
            service_handle <= handle && next_service_handle.map(|x| handle < x).unwrap_or(true)
        };

        // record largest attribute matching predicate
        let largest_service_handle =
            static_data.attributes.keys().filter(|handle| in_service_pred(**handle)).max().cloned();

        // clear out attributes
        static_data.attributes.retain(|curr_handle, _| !in_service_pred(*curr_handle));

        // re-entrancy via the listeners is possible, so we prevent it by dropping here
        drop(static_data);

        // notify listeners if any attribute changed
        if let Some(largest_service_handle) = largest_service_handle {
            for listener in self.listeners.borrow().iter() {
                listener.on_service_change(service_handle..=largest_service_handle);
            }
        }

        Ok(())
    }
}

impl SharedBox<GattDatabase> {
    /// Generate an impl AttDatabase from a backing GattDatabase, associated
    /// with a given connection.
    ///
    /// Note: After the AttDatabaseImpl is constructed, we MUST call on_bearer_ready() with
    /// the resultant bearer, so that the listeners get the correct sequence of callbacks.
    pub fn get_att_database(&self, tcb_idx: TransportIndex) -> AttDatabaseImpl {
        AttDatabaseImpl { gatt_db: self.downgrade(), tcb_idx }
    }
}

/// An implementation of AttDatabase wrapping an underlying GattDatabase
pub struct AttDatabaseImpl {
    gatt_db: WeakBox<GattDatabase>,
    tcb_idx: TransportIndex,
}

#[async_trait(?Send)]
impl AttDatabase for AttDatabaseImpl {
    async fn read_attribute(
        &self,
        handle: AttHandle,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        let value = self.gatt_db.with(|gatt_db| {
            let Some(gatt_db) = gatt_db else {
                // db must have been closed
                return Err(AttErrorCode::INVALID_HANDLE);
            };
            let services = gatt_db.schema.borrow();
            let Some(attr) = services.attributes.get(&handle) else {
                return Err(AttErrorCode::INVALID_HANDLE);
            };
            if !attr.attribute.permissions.readable() {
                return Err(AttErrorCode::READ_NOT_PERMITTED);
            }
            Ok(attr.value.clone())
        })?;

        match value {
            AttAttributeBackingValue::Static(val) => return Ok(val),
            AttAttributeBackingValue::DynamicCharacteristic(datastore) => {
                datastore
                    .read(
                        self.tcb_idx,
                        handle,
                        /* offset */ 0,
                        AttributeBackingType::Characteristic,
                    )
                    .await
            }
            AttAttributeBackingValue::DynamicDescriptor(datastore) => {
                datastore
                    .read(
                        self.tcb_idx,
                        handle,
                        /* offset */ 0,
                        AttributeBackingType::Descriptor,
                    )
                    .await
            }
        }
    }

    async fn write_attribute(
        &self,
        handle: AttHandle,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        let value = self.gatt_db.with(|gatt_db| {
            let Some(gatt_db) = gatt_db else {
                // db must have been closed
                return Err(AttErrorCode::INVALID_HANDLE);
            };
            let services = gatt_db.schema.borrow();
            let Some(attr) = services.attributes.get(&handle) else {
                return Err(AttErrorCode::INVALID_HANDLE);
            };
            if !attr.attribute.permissions.writable_with_response() {
                return Err(AttErrorCode::WRITE_NOT_PERMITTED);
            }
            Ok(attr.value.clone())
        })?;

        match value {
            AttAttributeBackingValue::Static(val) => {
                error!("A static attribute {val:?} is marked as writable - ignoring it and rejecting the write...");
                return Err(AttErrorCode::WRITE_NOT_PERMITTED);
            }
            AttAttributeBackingValue::DynamicCharacteristic(datastore) => {
                datastore
                    .write(
                        self.tcb_idx,
                        handle,
                        AttributeBackingType::Characteristic,
                        GattWriteRequestType::Request,
                        data,
                    )
                    .await
            }
            AttAttributeBackingValue::DynamicDescriptor(datastore) => {
                datastore
                    .write(
                        self.tcb_idx,
                        handle,
                        AttributeBackingType::Descriptor,
                        GattWriteRequestType::Request,
                        data,
                    )
                    .await
            }
        }
    }

    fn write_no_response_attribute(&self, handle: AttHandle, data: AttAttributeDataView<'_>) {
        let value = self.gatt_db.with(|gatt_db| {
            let Some(gatt_db) = gatt_db else {
                // db must have been closed
                return None;
            };
            let services = gatt_db.schema.borrow();
            let Some(attr) = services.attributes.get(&handle) else {
                warn!("cannot find handle {handle:?}");
                return None;
            };
            if !attr.attribute.permissions.writable_without_response() {
                warn!("trying to write without response to {handle:?}, which doesn't support it");
                return None;
            }
            Some(attr.value.clone())
        });

        let Some(value) = value else {
            return;
        };

        match value {
            AttAttributeBackingValue::Static(val) => {
                error!("A static attribute {val:?} is marked as writable - ignoring it and rejecting the write...");
            }
            AttAttributeBackingValue::DynamicCharacteristic(datastore) => {
                datastore.write_no_response(
                    self.tcb_idx,
                    handle,
                    AttributeBackingType::Characteristic,
                    data,
                );
            }
            AttAttributeBackingValue::DynamicDescriptor(datastore) => {
                datastore.write_no_response(
                    self.tcb_idx,
                    handle,
                    AttributeBackingType::Descriptor,
                    data,
                );
            }
        };
    }

    fn list_attributes(&self) -> Vec<AttAttribute> {
        self.gatt_db.with(|db| {
            db.map(|db| db.schema.borrow().attributes.values().map(|attr| attr.attribute).collect())
                .unwrap_or_default()
        })
    }
}

impl Clone for AttDatabaseImpl {
    fn clone(&self) -> Self {
        Self { gatt_db: self.gatt_db.clone(), tcb_idx: self.tcb_idx }
    }
}

impl AttDatabaseImpl {
    /// When the bearer owning this AttDatabase is invalidated,
    /// we must notify the listeners tied to our GattDatabase.
    ///
    /// Note: AttDatabases referring to the backing GattDatabase
    /// may still exist after bearer invalidation, but the bearer will
    /// no longer exist (so packets can no longer be sent/received).
    pub fn on_bearer_dropped(&self) {
        self.gatt_db.with(|db| {
            db.map(|db| {
                for listener in db.listeners.borrow().iter() {
                    listener.on_le_disconnect(self.tcb_idx)
                }
            })
        });
    }
}

#[cfg(test)]
mod test {
    use tokio::{join, sync::mpsc::error::TryRecvError, task::spawn_local};

    use crate::{
        gatt::mocks::{
            mock_database_callbacks::{MockCallbackEvents, MockCallbacks},
            mock_datastore::{MockDatastore, MockDatastoreEvents},
            mock_raw_datastore::{MockRawDatastore, MockRawDatastoreEvents},
        },
        packets::Packet,
        utils::{
            packet::{build_att_data, build_view_or_crash},
            task::block_on_locally,
        },
    };

    use super::*;

    const SERVICE_HANDLE: AttHandle = AttHandle(1);
    const SERVICE_TYPE: Uuid = Uuid::new(0x1234);

    const CHARACTERISTIC_DECLARATION_HANDLE: AttHandle = AttHandle(2);
    const CHARACTERISTIC_VALUE_HANDLE: AttHandle = AttHandle(3);
    const CHARACTERISTIC_TYPE: Uuid = Uuid::new(0x5678);

    const DESCRIPTOR_HANDLE: AttHandle = AttHandle(4);
    const DESCRIPTOR_TYPE: Uuid = Uuid::new(0x9ABC);

    const TCB_IDX: TransportIndex = TransportIndex(1);

    #[test]
    fn test_read_empty_db() {
        let gatt_db = SharedBox::new(GattDatabase::new());
        let att_db = gatt_db.get_att_database(TCB_IDX);

        let resp = tokio_test::block_on(att_db.read_attribute(AttHandle(1)));

        assert_eq!(resp, Err(AttErrorCode::INVALID_HANDLE))
    }

    #[test]
    fn test_single_service() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);

        let attrs = att_db.list_attributes();
        let service_value = tokio_test::block_on(att_db.read_attribute(SERVICE_HANDLE));

        assert_eq!(
            attrs,
            vec![AttAttribute {
                handle: SERVICE_HANDLE,
                type_: PRIMARY_SERVICE_DECLARATION_UUID,
                permissions: AttPermissions::READABLE
            }]
        );
        assert_eq!(
            service_value,
            Ok(AttAttributeDataChild::GattServiceDeclarationValue(
                GattServiceDeclarationValueBuilder { uuid: SERVICE_TYPE.into() }
            ))
        );
    }

    #[test]
    fn test_service_removal() {
        // arrange three services, each with a single characteristic
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_datastore = Rc::new(gatt_datastore);
        let gatt_db = SharedBox::new(GattDatabase::new());

        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(1),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(3),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                gatt_datastore.clone(),
            )
            .unwrap();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(6),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                gatt_datastore.clone(),
            )
            .unwrap();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(7),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(9),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                gatt_datastore,
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        assert_eq!(att_db.list_attributes().len(), 9);

        // act: remove the middle service
        gatt_db.remove_service_at_handle(AttHandle(4)).unwrap();
        let attrs = att_db.list_attributes();

        // assert that the middle service is gone
        assert_eq!(attrs.len(), 6, "{attrs:?}");

        // assert the other two old services are still there
        assert_eq!(
            attrs[0],
            AttAttribute {
                handle: AttHandle(1),
                type_: PRIMARY_SERVICE_DECLARATION_UUID,
                permissions: AttPermissions::READABLE
            }
        );
        assert_eq!(
            attrs[3],
            AttAttribute {
                handle: AttHandle(7),
                type_: PRIMARY_SERVICE_DECLARATION_UUID,
                permissions: AttPermissions::READABLE
            }
        );
    }

    #[test]
    fn test_single_characteristic_declaration() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE
                            | AttPermissions::WRITABLE_WITH_RESPONSE
                            | AttPermissions::INDICATE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);

        let attrs = att_db.list_attributes();
        let characteristic_decl =
            tokio_test::block_on(att_db.read_attribute(CHARACTERISTIC_DECLARATION_HANDLE));

        assert_eq!(attrs.len(), 3, "{attrs:?}");
        assert_eq!(attrs[0].type_, PRIMARY_SERVICE_DECLARATION_UUID);
        assert_eq!(
            attrs[1],
            AttAttribute {
                handle: CHARACTERISTIC_DECLARATION_HANDLE,
                type_: CHARACTERISTIC_UUID,
                permissions: AttPermissions::READABLE
            }
        );
        assert_eq!(
            attrs[2],
            AttAttribute {
                handle: CHARACTERISTIC_VALUE_HANDLE,
                type_: CHARACTERISTIC_TYPE,
                permissions: AttPermissions::READABLE
                    | AttPermissions::WRITABLE_WITH_RESPONSE
                    | AttPermissions::INDICATE
            }
        );

        assert_eq!(
            characteristic_decl,
            Ok(AttAttributeDataChild::GattCharacteristicDeclarationValue(
                GattCharacteristicDeclarationValueBuilder {
                    properties: GattCharacteristicPropertiesBuilder {
                        read: 1,
                        broadcast: 0,
                        write_without_response: 0,
                        write: 1,
                        notify: 0,
                        indicate: 1,
                        authenticated_signed_writes: 0,
                        extended_properties: 0,
                    },
                    handle: CHARACTERISTIC_VALUE_HANDLE.into(),
                    uuid: CHARACTERISTIC_TYPE.into()
                }
            ))
        );
    }

    #[test]
    fn test_all_characteristic_permissions() {
        // arrange
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        let att_db = gatt_db.get_att_database(TCB_IDX);

        // act: add a characteristic with all permission bits set
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::all(),
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();

        // assert: the characteristic declaration has all the bits we support set
        let characteristic_decl =
            tokio_test::block_on(att_db.read_attribute(CHARACTERISTIC_DECLARATION_HANDLE));
        assert_eq!(
            characteristic_decl,
            Ok(AttAttributeDataChild::GattCharacteristicDeclarationValue(
                GattCharacteristicDeclarationValueBuilder {
                    properties: GattCharacteristicPropertiesBuilder {
                        read: 1,
                        broadcast: 0,
                        write_without_response: 1,
                        write: 1,
                        notify: 0,
                        indicate: 1,
                        authenticated_signed_writes: 0,
                        extended_properties: 0,
                    },
                    handle: CHARACTERISTIC_VALUE_HANDLE.into(),
                    uuid: CHARACTERISTIC_TYPE.into()
                }
            ))
        );
    }

    #[test]
    fn test_single_characteristic_value() {
        // arrange: create a database with a single characteristic
        let (gatt_datastore, mut data_evts) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data = AttAttributeDataChild::RawData(Box::new([1, 2]));

        // act: read from the database, and supply a value from the backing datastore
        let characteristic_value = tokio_test::block_on(async {
            join!(
                async {
                    let MockDatastoreEvents::Read(
                    TCB_IDX,
                    CHARACTERISTIC_VALUE_HANDLE,
                    AttributeBackingType::Characteristic,
                    reply,
                ) = data_evts.recv().await.unwrap() else {
                    unreachable!()
                };
                    reply.send(Ok(data.clone())).unwrap();
                },
                att_db.read_attribute(CHARACTERISTIC_VALUE_HANDLE)
            )
            .1
        });

        // assert: the supplied value matches what the att datastore returned
        assert_eq!(characteristic_value, Ok(data));
    }

    #[test]
    fn test_unreadable_characteristic() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::empty(),
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();

        let characteristic_value = tokio_test::block_on(
            gatt_db.get_att_database(TCB_IDX).read_attribute(CHARACTERISTIC_VALUE_HANDLE),
        );

        assert_eq!(characteristic_value, Err(AttErrorCode::READ_NOT_PERMITTED));
    }

    #[test]
    fn test_handle_clash() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());

        let result = gatt_db.add_service_with_handles(
            GattServiceWithHandle {
                handle: SERVICE_HANDLE,
                type_: SERVICE_TYPE,
                characteristics: vec![GattCharacteristicWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: CHARACTERISTIC_TYPE,
                    permissions: AttPermissions::WRITABLE_WITH_RESPONSE,
                    descriptors: vec![],
                }],
            },
            Rc::new(gatt_datastore),
        );

        assert!(result.is_err());
    }

    #[test]
    fn test_handle_clash_with_existing() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_datastore = Rc::new(gatt_datastore);
        let gatt_db = Rc::new(GattDatabase::new());

        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![],
                },
                gatt_datastore.clone(),
            )
            .unwrap();

        let result = gatt_db.add_service_with_handles(
            GattServiceWithHandle {
                handle: SERVICE_HANDLE,
                type_: SERVICE_TYPE,
                characteristics: vec![],
            },
            gatt_datastore,
        );

        assert!(result.is_err());
    }

    #[test]
    fn test_write_single_characteristic_callback_invoked() {
        // arrange: create a database with a single characteristic
        let (gatt_datastore, mut data_evts) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::WRITABLE_WITH_RESPONSE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        // act: write to the database
        let recv_data = block_on_locally(async {
            // start write task
            let cloned_data = data.view().to_owned_packet();
            spawn_local(async move {
                att_db
                    .write_attribute(CHARACTERISTIC_VALUE_HANDLE, cloned_data.view())
                    .await
                    .unwrap();
            });

            let MockDatastoreEvents::Write(
                TCB_IDX,
                CHARACTERISTIC_VALUE_HANDLE,
                AttributeBackingType::Characteristic,
                recv_data,
                _,
            ) = data_evts.recv().await.unwrap() else {
                unreachable!();
            };
            recv_data
        });

        // assert: the received value matches what we supplied
        assert_eq!(
            recv_data.view().get_raw_payload().collect::<Vec<_>>(),
            data.view().get_raw_payload().collect::<Vec<_>>()
        );
    }

    #[test]
    fn test_write_single_characteristic_recv_response() {
        // arrange: create a database with a single characteristic
        let (gatt_datastore, mut data_evts) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::WRITABLE_WITH_RESPONSE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        // act: write to the database
        let res = tokio_test::block_on(async {
            join!(
                async {
                    let MockDatastoreEvents::Write(_,_,_,_,reply) = data_evts.recv().await.unwrap() else {
                        unreachable!();
                    };
                    reply.send(Err(AttErrorCode::UNLIKELY_ERROR)).unwrap();
                },
                att_db.write_attribute(CHARACTERISTIC_VALUE_HANDLE, data.view())
            )
            .1
        });

        // assert: the supplied value matches what the att datastore returned
        assert_eq!(res, Err(AttErrorCode::UNLIKELY_ERROR));
    }

    #[test]
    fn test_unwriteable_characteristic() {
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        let characteristic_value = tokio_test::block_on(
            gatt_db
                .get_att_database(TCB_IDX)
                .write_attribute(CHARACTERISTIC_VALUE_HANDLE, data.view()),
        );

        assert_eq!(characteristic_value, Err(AttErrorCode::WRITE_NOT_PERMITTED));
    }

    #[test]
    fn test_single_descriptor_declaration() {
        let (gatt_datastore, mut data_evts) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![GattDescriptorWithHandle {
                            handle: DESCRIPTOR_HANDLE,
                            type_: DESCRIPTOR_TYPE,
                            permissions: AttPermissions::READABLE,
                        }],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data = AttAttributeDataChild::RawData(Box::new([1, 2]));

        let descriptor_value = block_on_locally(async {
            // start write task
            let pending_read =
                spawn_local(async move { att_db.read_attribute(DESCRIPTOR_HANDLE).await.unwrap() });

            let MockDatastoreEvents::Read(
                TCB_IDX,
                DESCRIPTOR_HANDLE,
                AttributeBackingType::Descriptor,
                reply,
            ) = data_evts.recv().await.unwrap() else {
                unreachable!();
            };

            reply.send(Ok(data.clone())).unwrap();

            pending_read.await.unwrap()
        });

        assert_eq!(descriptor_value, data);
    }

    #[test]
    fn test_write_descriptor() {
        // arrange: db with a writable descriptor
        let (gatt_datastore, mut data_evts) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![GattDescriptorWithHandle {
                            handle: DESCRIPTOR_HANDLE,
                            type_: DESCRIPTOR_TYPE,
                            permissions: AttPermissions::WRITABLE_WITH_RESPONSE,
                        }],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        // act: write, and wait for the callback to be invoked
        block_on_locally(async {
            // start write task
            spawn_local(async move {
                att_db.write_attribute(DESCRIPTOR_HANDLE, data.view()).await.unwrap()
            });

            let MockDatastoreEvents::Write(
                TCB_IDX,
                DESCRIPTOR_HANDLE,
                AttributeBackingType::Descriptor,
                _,
                _,
            ) = data_evts.recv().await.unwrap() else {
                unreachable!();
            };
        });

        // assert: nothing, if we reach this far we are OK
    }

    #[test]
    fn test_multiple_descriptors() {
        // arrange: a database with some characteristics and descriptors
        let (gatt_datastore, _) = MockDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(1),
                    type_: SERVICE_TYPE,
                    characteristics: vec![
                        GattCharacteristicWithHandle {
                            handle: AttHandle(3),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::READABLE,
                            descriptors: vec![GattDescriptorWithHandle {
                                handle: AttHandle(4),
                                type_: DESCRIPTOR_TYPE,
                                permissions: AttPermissions::READABLE,
                            }],
                        },
                        GattCharacteristicWithHandle {
                            handle: AttHandle(6),
                            type_: CHARACTERISTIC_TYPE,
                            permissions: AttPermissions::READABLE,
                            descriptors: vec![
                                GattDescriptorWithHandle {
                                    handle: AttHandle(7),
                                    type_: DESCRIPTOR_TYPE,
                                    permissions: AttPermissions::WRITABLE_WITH_RESPONSE,
                                },
                                GattDescriptorWithHandle {
                                    handle: AttHandle(8),
                                    type_: DESCRIPTOR_TYPE,
                                    permissions: AttPermissions::READABLE
                                        | AttPermissions::WRITABLE_WITH_RESPONSE,
                                },
                            ],
                        },
                    ],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();

        // act: get the attributes
        let attributes = gatt_db.get_att_database(TCB_IDX).list_attributes();

        // assert: check the attributes are in the correct order
        assert_eq!(attributes.len(), 8);
        assert_eq!(attributes[0].type_, PRIMARY_SERVICE_DECLARATION_UUID);
        assert_eq!(attributes[1].type_, CHARACTERISTIC_UUID);
        assert_eq!(attributes[2].type_, CHARACTERISTIC_TYPE);
        assert_eq!(attributes[3].type_, DESCRIPTOR_TYPE);
        assert_eq!(attributes[4].type_, CHARACTERISTIC_UUID);
        assert_eq!(attributes[5].type_, CHARACTERISTIC_TYPE);
        assert_eq!(attributes[6].type_, DESCRIPTOR_TYPE);
        assert_eq!(attributes[7].type_, DESCRIPTOR_TYPE);
        // assert: check the handles of the descriptors are correct
        assert_eq!(attributes[3].handle, AttHandle(4));
        assert_eq!(attributes[6].handle, AttHandle(7));
        assert_eq!(attributes[7].handle, AttHandle(8));
        // assert: check the permissions of the descriptors are correct
        assert_eq!(attributes[3].permissions, AttPermissions::READABLE);
        assert_eq!(attributes[6].permissions, AttPermissions::WRITABLE_WITH_RESPONSE);
        assert_eq!(
            attributes[7].permissions,
            AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE
        );
    }

    #[test]
    fn test_multiple_datastores() {
        // arrange: create a database with two services backed by different datastores
        let gatt_db = SharedBox::new(GattDatabase::new());

        let (gatt_datastore_1, mut data_evts_1) = MockDatastore::new();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(1),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(3),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore_1),
            )
            .unwrap();

        let (gatt_datastore_2, mut data_evts_2) = MockDatastore::new();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(6),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore_2),
            )
            .unwrap();

        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data = AttAttributeDataChild::RawData(Box::new([1, 2]));

        // act: read from the second characteristic and supply a response from the second datastore
        let characteristic_value = tokio_test::block_on(async {
            join!(
                async {
                    let MockDatastoreEvents::Read(
                    TCB_IDX,
                    AttHandle(6),
                    AttributeBackingType::Characteristic,
                    reply,
                ) = data_evts_2.recv().await.unwrap() else {
                    unreachable!()
                };
                    reply.send(Ok(data.clone())).unwrap();
                },
                att_db.read_attribute(AttHandle(6))
            )
            .1
        });

        // assert: the supplied value matches what the att datastore returned
        assert_eq!(characteristic_value, Ok(data));
        // the first datastore received no events
        assert_eq!(data_evts_1.try_recv().unwrap_err(), TryRecvError::Empty);
        // the second datastore has no remaining events
        assert_eq!(data_evts_2.try_recv().unwrap_err(), TryRecvError::Empty);
    }

    fn make_bearer(
        gatt_db: &SharedBox<GattDatabase>,
    ) -> SharedBox<AttServerBearer<AttDatabaseImpl>> {
        SharedBox::new(AttServerBearer::new(gatt_db.get_att_database(TCB_IDX), |_| {
            unreachable!();
        }))
    }

    #[test]
    fn test_connection_listener() {
        // arrange: db with a listener
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        gatt_db.register_listener(Rc::new(callbacks));
        let bearer = make_bearer(&gatt_db);

        // act: open a connection
        gatt_db.on_bearer_ready(TCB_IDX, bearer.as_ref());

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        assert!(matches!(event, MockCallbackEvents::OnLeConnect(TCB_IDX, _)));
    }

    #[test]
    fn test_disconnection_listener() {
        // arrange: db with a listener
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        gatt_db.register_listener(Rc::new(callbacks));

        // act: disconnect
        gatt_db.on_bearer_dropped(TCB_IDX);

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        assert!(matches!(event, MockCallbackEvents::OnLeDisconnect(TCB_IDX)));
    }

    #[test]
    fn test_multiple_listeners() {
        // arrange: db with two listeners
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks1, mut rx1) = MockCallbacks::new();
        gatt_db.register_listener(Rc::new(callbacks1));
        let (callbacks2, mut rx2) = MockCallbacks::new();
        gatt_db.register_listener(Rc::new(callbacks2));

        // act: disconnect
        gatt_db.on_bearer_dropped(TCB_IDX);

        // assert: we got the callback on both listeners
        let event = rx1.blocking_recv().unwrap();
        assert!(matches!(event, MockCallbackEvents::OnLeDisconnect(TCB_IDX)));
        let event = rx2.blocking_recv().unwrap();
        assert!(matches!(event, MockCallbackEvents::OnLeDisconnect(TCB_IDX)));
    }

    #[test]
    fn test_add_service_changed_listener() {
        // arrange: db with a listener
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        let (datastore, _) = MockDatastore::new();

        // act: start listening and add a new service
        gatt_db.register_listener(Rc::new(callbacks));
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(6),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::empty(),
                        descriptors: vec![],
                    }],
                },
                Rc::new(datastore),
            )
            .unwrap();

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        let MockCallbackEvents::OnServiceChange(range) = event else {
            unreachable!();
        };
        assert_eq!(*range.start(), AttHandle(4));
        assert_eq!(*range.end(), AttHandle(6));
    }

    #[test]
    fn test_partial_remove_service_changed_listener() {
        // arrange: db with two services and a listener
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        let (datastore, _) = MockDatastore::new();
        let datastore = Rc::new(datastore);
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(6),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::empty(),
                        descriptors: vec![],
                    }],
                },
                datastore.clone(),
            )
            .unwrap();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(8),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(10),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::empty(),
                        descriptors: vec![],
                    }],
                },
                datastore,
            )
            .unwrap();

        // act: start listening and remove the first service
        gatt_db.register_listener(Rc::new(callbacks));
        gatt_db.remove_service_at_handle(AttHandle(4)).unwrap();

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        let MockCallbackEvents::OnServiceChange(range) = event else {
            unreachable!();
        };
        assert_eq!(*range.start(), AttHandle(4));
        assert_eq!(*range.end(), AttHandle(6));
    }

    #[test]
    fn test_full_remove_service_changed_listener() {
        // arrange: db with a listener and a service
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        let (datastore, _) = MockDatastore::new();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: AttHandle(6),
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::empty(),
                        descriptors: vec![],
                    }],
                },
                Rc::new(datastore),
            )
            .unwrap();

        // act: start listening and remove the service
        gatt_db.register_listener(Rc::new(callbacks));
        gatt_db.remove_service_at_handle(AttHandle(4)).unwrap();

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        let MockCallbackEvents::OnServiceChange(range) = event else {
            unreachable!();
        };
        assert_eq!(*range.start(), AttHandle(4));
        assert_eq!(*range.end(), AttHandle(6));
    }

    #[test]
    fn test_trivial_remove_service_changed_listener() {
        // arrange: db with a listener and a trivial service
        let gatt_db = SharedBox::new(GattDatabase::new());
        let (callbacks, mut rx) = MockCallbacks::new();
        let (datastore, _) = MockDatastore::new();
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: AttHandle(4),
                    type_: SERVICE_TYPE,
                    characteristics: vec![],
                },
                Rc::new(datastore),
            )
            .unwrap();

        // act: start listening and remove the service
        gatt_db.register_listener(Rc::new(callbacks));
        gatt_db.remove_service_at_handle(AttHandle(4)).unwrap();

        // assert: we got the callback
        let event = rx.blocking_recv().unwrap();
        let MockCallbackEvents::OnServiceChange(range) = event else {
            unreachable!();
        };
        assert_eq!(*range.start(), AttHandle(4));
        assert_eq!(*range.end(), AttHandle(4));
    }

    #[test]
    fn test_write_no_response_single_characteristic() {
        // arrange: create a database with a single characteristic
        let (gatt_datastore, mut data_evts) = MockRawDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::WRITABLE_WITHOUT_RESPONSE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        // act: write without response to the database
        att_db.write_no_response_attribute(CHARACTERISTIC_VALUE_HANDLE, data.view());

        // assert: we got a callback
        let event = data_evts.blocking_recv().unwrap();
        let MockRawDatastoreEvents::WriteNoResponse(TCB_IDX, CHARACTERISTIC_VALUE_HANDLE, AttributeBackingType::Characteristic, recv_data) = event else {
            unreachable!("{event:?}");
        };
        assert_eq!(
            recv_data.view().get_raw_payload().collect::<Vec<_>>(),
            data.view().get_raw_payload().collect::<Vec<_>>()
        );
    }

    #[test]
    fn test_unwriteable_without_response_characteristic() {
        // arrange: db with a characteristic that is writable, but not writable-without-response
        let (gatt_datastore, mut data_events) = MockRawDatastore::new();
        let gatt_db = SharedBox::new(GattDatabase::new());
        gatt_db
            .add_service_with_handles(
                GattServiceWithHandle {
                    handle: SERVICE_HANDLE,
                    type_: SERVICE_TYPE,
                    characteristics: vec![GattCharacteristicWithHandle {
                        handle: CHARACTERISTIC_VALUE_HANDLE,
                        type_: CHARACTERISTIC_TYPE,
                        permissions: AttPermissions::READABLE
                            | AttPermissions::WRITABLE_WITH_RESPONSE,
                        descriptors: vec![],
                    }],
                },
                Rc::new(gatt_datastore),
            )
            .unwrap();
        let att_db = gatt_db.get_att_database(TCB_IDX);
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(Box::new([1, 2]))));

        // act: try writing without response to this characteristic
        att_db.write_no_response_attribute(CHARACTERISTIC_VALUE_HANDLE, data.view());

        // assert: no callback was sent
        assert_eq!(data_events.try_recv().unwrap_err(), TryRecvError::Empty);
    }
}
