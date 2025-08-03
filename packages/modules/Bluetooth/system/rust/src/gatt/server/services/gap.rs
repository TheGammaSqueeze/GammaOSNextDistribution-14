//! The GAP service as defined in Core Spec 5.3 Vol 3C Section 12

use std::rc::Rc;

use anyhow::Result;
use async_trait::async_trait;

use crate::{
    core::uuid::Uuid,
    gatt::{
        callbacks::GattDatastore,
        ffi::AttributeBackingType,
        ids::{AttHandle, TransportIndex},
        server::gatt_database::{
            AttPermissions, GattCharacteristicWithHandle, GattDatabase, GattServiceWithHandle,
        },
    },
    packets::{AttAttributeDataChild, AttAttributeDataView, AttErrorCode},
};

struct GapService;

// Must lie in the range specified by GATT_GAP_START_HANDLE from legacy stack
const GAP_SERVICE_HANDLE: AttHandle = AttHandle(20);
const DEVICE_NAME_HANDLE: AttHandle = AttHandle(22);
const DEVICE_APPEARANCE_HANDLE: AttHandle = AttHandle(24);

/// The UUID used for the GAP service (Assigned Numbers 3.4.1 Services by Name)
pub const GAP_SERVICE_UUID: Uuid = Uuid::new(0x1800);
/// The UUID used for the Device Name characteristic (Assigned Numbers 3.8.1 Characteristics by Name)
pub const DEVICE_NAME_UUID: Uuid = Uuid::new(0x2A00);
/// The UUID used for the Device Appearance characteristic (Assigned Numbers 3.8.1 Characteristics by Name)
pub const DEVICE_APPEARANCE_UUID: Uuid = Uuid::new(0x2A01);

#[async_trait(?Send)]
impl GattDatastore for GapService {
    async fn read(
        &self,
        _: TransportIndex,
        handle: AttHandle,
        _: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        match handle {
            DEVICE_NAME_HANDLE => {
                // for non-bonded peers, don't let them read the device name
                // TODO(aryarahul): support discoverability, when we make this the main GATT server
                Err(AttErrorCode::INSUFFICIENT_AUTHENTICATION)
            }
            // 0x0000 from AssignedNumbers => "Unknown"
            DEVICE_APPEARANCE_HANDLE => Ok(AttAttributeDataChild::RawData([0x00, 0x00].into())),
            _ => unreachable!("unexpected handle read"),
        }
    }

    async fn write(
        &self,
        _: TransportIndex,
        _: AttHandle,
        _: AttributeBackingType,
        _: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        unreachable!("no GAP data should be writable")
    }
}

/// Register the GAP service in the provided GATT database.
pub fn register_gap_service(database: &mut GattDatabase) -> Result<()> {
    database.add_service_with_handles(
        // GAP Service
        GattServiceWithHandle {
            handle: GAP_SERVICE_HANDLE,
            type_: GAP_SERVICE_UUID,
            // Device Name
            characteristics: vec![
                GattCharacteristicWithHandle {
                    handle: DEVICE_NAME_HANDLE,
                    type_: DEVICE_NAME_UUID,
                    permissions: AttPermissions::READABLE,
                    descriptors: vec![],
                },
                // Appearance
                GattCharacteristicWithHandle {
                    handle: DEVICE_APPEARANCE_HANDLE,
                    type_: DEVICE_APPEARANCE_UUID,
                    permissions: AttPermissions::READABLE,
                    descriptors: vec![],
                },
            ],
        },
        Rc::new(GapService),
    )
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        core::shared_box::SharedBox,
        gatt::server::{
            att_database::AttDatabase,
            gatt_database::{GattDatabase, CHARACTERISTIC_UUID, PRIMARY_SERVICE_DECLARATION_UUID},
        },
        utils::task::block_on_locally,
    };

    const TCB_IDX: TransportIndex = TransportIndex(1);

    fn init_dbs() -> (SharedBox<GattDatabase>, impl AttDatabase) {
        let mut gatt_database = GattDatabase::new();
        register_gap_service(&mut gatt_database).unwrap();
        let gatt_database = SharedBox::new(gatt_database);
        let att_database = gatt_database.get_att_database(TCB_IDX);
        (gatt_database, att_database)
    }

    #[test]
    fn test_gap_service_discovery() {
        // arrange
        let (_gatt_db, att_db) = init_dbs();

        // act: discover all services
        let attrs = att_db.list_attributes();

        // assert: 1 service + (2 characteristics) * (declaration + value attrs) = 5 attrs
        assert_eq!(attrs.len(), 5);
        // assert: value handles are correct
        assert_eq!(attrs[0].handle, GAP_SERVICE_HANDLE);
        assert_eq!(attrs[2].handle, DEVICE_NAME_HANDLE);
        assert_eq!(attrs[4].handle, DEVICE_APPEARANCE_HANDLE);
        // assert: types are correct
        assert_eq!(attrs[0].type_, PRIMARY_SERVICE_DECLARATION_UUID);
        assert_eq!(attrs[1].type_, CHARACTERISTIC_UUID);
        assert_eq!(attrs[2].type_, DEVICE_NAME_UUID);
        assert_eq!(attrs[3].type_, CHARACTERISTIC_UUID);
        assert_eq!(attrs[4].type_, DEVICE_APPEARANCE_UUID);
        // assert: permissions of value attrs are correct
        assert_eq!(attrs[2].permissions, AttPermissions::READABLE);
        assert_eq!(attrs[4].permissions, AttPermissions::READABLE);
    }

    #[test]
    fn test_read_device_name_not_discoverable() {
        // arrange
        let (_gatt_db, att_db) = init_dbs();

        // act: try to read the device name
        let name = block_on_locally(att_db.read_attribute(DEVICE_NAME_HANDLE));

        // assert: the name is not readable
        assert_eq!(name, Err(AttErrorCode::INSUFFICIENT_AUTHENTICATION));
    }

    #[test]
    fn test_read_device_appearance() {
        // arrange
        let (_gatt_db, att_db) = init_dbs();

        // act: try to read the device name
        let name = block_on_locally(att_db.read_attribute(DEVICE_APPEARANCE_HANDLE));

        // assert: the name is not readable
        assert_eq!(name, Ok(AttAttributeDataChild::RawData([0x00, 0x00].into())));
    }
}
