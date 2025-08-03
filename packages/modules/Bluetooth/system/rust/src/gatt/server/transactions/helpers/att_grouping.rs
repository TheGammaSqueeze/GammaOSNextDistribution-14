//! This module is a utility encapsulating the "attribute grouping" logic
//! defined in 5.3 Vol 3G Sec 2.5.3, and used in the ATT operations
//! READ_BY_GROUP_TYPE_REQ and FIND_BY_TYPE_REQ.

use crate::core::uuid::Uuid;

use crate::gatt::server::att_database::{AttAttribute, StableAttDatabase};
use crate::gatt::server::gatt_database::{
    CHARACTERISTIC_UUID, PRIMARY_SERVICE_DECLARATION_UUID, SECONDARY_SERVICE_DECLARATION_UUID,
};

const GROUPING_ATTRIBUTES: [Uuid; 3] =
    [PRIMARY_SERVICE_DECLARATION_UUID, SECONDARY_SERVICE_DECLARATION_UUID, CHARACTERISTIC_UUID];

/// Gets the "level" of an attribute UUID.
/// The group of an attribute with level X extends until just before the next
/// attribute with level <= X.
fn get_grouping_level(uuid: Uuid) -> usize {
    match uuid {
        PRIMARY_SERVICE_DECLARATION_UUID | SECONDARY_SERVICE_DECLARATION_UUID => 1,
        CHARACTERISTIC_UUID => 2,
        _ => 3,
    }
}

/// Finds the handle of the last attribute in a group defined by the
/// group_start. Returns None if the group_start is not a supported group type.
///
/// Expects `attrs` to be in sorted order by attribute handle.
///
/// Attribute grouping is defined in 5.3 Vol 3G Sec 2.5.3 Attribute Grouping
pub fn find_group_end(
    db: &impl StableAttDatabase,
    group_start: AttAttribute,
) -> Option<AttAttribute> {
    if !GROUPING_ATTRIBUTES.contains(&group_start.type_) {
        return None; // invalid / unsupported grouping attribute
    }

    Some(
        db.list_attributes()
            .into_iter()
            // ignore attributes at or before the current position
            .skip_while(|attr| attr.handle <= group_start.handle)
            // consider only attributes strictly within the current group
            .take_while(|attr| {
                get_grouping_level(attr.type_) > get_grouping_level(group_start.type_)
            })
            .last()
            // if there are no other attributes in our group, just return the group_start handle
            .unwrap_or(group_start),
    )
}

#[cfg(test)]
mod test {
    use crate::gatt::{
        ids::AttHandle,
        server::{gatt_database::AttPermissions, test::test_att_db::TestAttDatabase},
    };

    use super::*;

    const OTHER_UUID: Uuid = Uuid::new(1234);

    fn db_from_attrs(attrs: impl IntoIterator<Item = AttAttribute>) -> TestAttDatabase {
        TestAttDatabase::new(attrs.into_iter().map(|attr| (attr, vec![])).collect())
    }

    fn attr(handle: AttHandle, type_: Uuid) -> AttAttribute {
        AttAttribute { handle, type_, permissions: AttPermissions::READABLE }
    }

    #[test]
    fn test_primary_service_group_terminated_by_primary_service() {
        let db = db_from_attrs([
            attr(AttHandle(00), PRIMARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(20), CHARACTERISTIC_UUID),
            attr(AttHandle(30), OTHER_UUID),
            attr(AttHandle(40), PRIMARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(30))
    }

    #[test]
    fn test_primary_service_group_terminated_by_secondary_service() {
        let db = db_from_attrs([
            attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(20), CHARACTERISTIC_UUID),
            attr(AttHandle(30), OTHER_UUID),
            attr(AttHandle(40), SECONDARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(30))
    }

    #[test]
    fn test_secondary_service_group_terminated_by_primary_service() {
        let db = db_from_attrs([
            attr(AttHandle(10), SECONDARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(20), CHARACTERISTIC_UUID),
            attr(AttHandle(30), OTHER_UUID),
            attr(AttHandle(40), CHARACTERISTIC_UUID),
            attr(AttHandle(50), PRIMARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end =
            find_group_end(&db, attr(AttHandle(10), SECONDARY_SERVICE_DECLARATION_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(40))
    }

    #[test]
    fn test_secondary_service_group_terminated_by_secondary_service() {
        let db = db_from_attrs([
            attr(AttHandle(10), SECONDARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(20), CHARACTERISTIC_UUID),
            attr(AttHandle(30), OTHER_UUID),
            attr(AttHandle(40), CHARACTERISTIC_UUID),
            attr(AttHandle(50), SECONDARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end =
            find_group_end(&db, attr(AttHandle(10), SECONDARY_SERVICE_DECLARATION_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(40))
    }

    #[test]
    fn test_characteristic_group_terminated_by_service() {
        let db = db_from_attrs([
            attr(AttHandle(10), CHARACTERISTIC_UUID),
            attr(AttHandle(20), OTHER_UUID),
            attr(AttHandle(30), SECONDARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), CHARACTERISTIC_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(20))
    }

    #[test]
    fn test_characteristic_group_terminated_by_characteristic() {
        let db = db_from_attrs([
            attr(AttHandle(10), CHARACTERISTIC_UUID),
            attr(AttHandle(20), OTHER_UUID),
            attr(AttHandle(30), CHARACTERISTIC_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), CHARACTERISTIC_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(20))
    }

    #[test]
    fn test_non_terminated_group() {
        let db = db_from_attrs([
            attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID),
            attr(AttHandle(20), CHARACTERISTIC_UUID),
            attr(AttHandle(30), OTHER_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), PRIMARY_SERVICE_DECLARATION_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(30))
    }

    #[test]
    fn test_empty_non_terminated_group() {
        let db = db_from_attrs([attr(AttHandle(10), CHARACTERISTIC_UUID)]);

        let group_end = find_group_end(&db, attr(AttHandle(10), CHARACTERISTIC_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(10))
    }

    #[test]
    fn test_empty_group() {
        let db = db_from_attrs([
            attr(AttHandle(10), CHARACTERISTIC_UUID),
            attr(AttHandle(20), SECONDARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), CHARACTERISTIC_UUID));

        assert_eq!(group_end.unwrap().handle, AttHandle(10))
    }

    #[test]
    fn test_non_grouping_attribute() {
        let db = db_from_attrs([
            attr(AttHandle(10), CHARACTERISTIC_UUID),
            attr(AttHandle(20), SECONDARY_SERVICE_DECLARATION_UUID),
        ]);

        let group_end = find_group_end(&db, attr(AttHandle(10), OTHER_UUID));

        assert_eq!(group_end, None)
    }
}
