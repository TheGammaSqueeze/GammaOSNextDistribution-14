//! This module extracts the common logic in filtering attributes by type +
//! length, used in READ_BY_TYPE_REQ and READ_BY_GROUP_TYPE_REQ

use crate::{
    core::uuid::Uuid,
    gatt::server::att_database::{AttAttribute, StableAttDatabase},
    packets::{AttAttributeDataChild, AttErrorCode, Serializable},
};

use super::truncate_att_data::truncate_att_data;

/// An attribute and the value
#[derive(Debug, PartialEq, Eq)]
pub struct AttributeWithValue {
    /// The attribute
    pub attr: AttAttribute,
    pub value: AttAttributeDataChild,
}

/// Takes a StableAttDatabase, a range of handles, a target type, and a
/// size limit.
///
/// Returns an iterator of attributes in the range and matching the type,
/// with the max number of elements such that each attribute has the same
/// size.
///
/// Attributes are truncated to the attr_size limit before size comparison.
/// If an error occurs while reading, do not output further attributes.
pub async fn filter_read_attributes_by_size_type(
    db: &impl StableAttDatabase,
    attrs: impl Iterator<Item = AttAttribute>,
    target: Uuid,
    size_limit: usize,
) -> Result<impl Iterator<Item = AttributeWithValue>, AttErrorCode> {
    let target_attrs = attrs.filter(|attr| attr.type_ == target);

    let mut out = vec![];
    let mut curr_elem_size = None;

    for attr @ AttAttribute { handle, .. } in target_attrs {
        match db.read_attribute(handle).await {
            Ok(value) => {
                let value = truncate_att_data(value, size_limit);
                let value_size = value.size_in_bits().unwrap_or(0);
                if let Some(curr_elem_size) = curr_elem_size {
                    if curr_elem_size != value_size {
                        // no more attributes of the same size
                        break;
                    }
                } else {
                    curr_elem_size = Some(value_size)
                }

                out.push(AttributeWithValue { attr, value });
            }
            Err(err) => {
                if out.is_empty() {
                    return Err(err);
                }
                break;
            }
        }
    }

    Ok(out.into_iter())
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        core::uuid::Uuid,
        gatt::{
            ids::AttHandle,
            server::{
                att_database::{AttAttribute, AttDatabase, StableAttDatabase},
                gatt_database::AttPermissions,
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::AttAttributeDataChild,
    };

    const UUID: Uuid = Uuid::new(1234);
    const ANOTHER_UUID: Uuid = Uuid::new(2345);

    #[test]
    fn test_single_matching_attr() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: UUID,
                permissions: AttPermissions::READABLE,
            },
            vec![4, 5],
        )]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![AttributeWithValue {
                attr: db.find_attribute(AttHandle(3)).unwrap(),
                value: AttAttributeDataChild::RawData([4, 5].into())
            }]
        )
    }

    #[test]
    fn test_skip_mismatching_attrs() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: ANOTHER_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(6),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![6, 7],
            ),
        ]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![
                AttributeWithValue {
                    attr: db.find_attribute(AttHandle(3)).unwrap(),
                    value: AttAttributeDataChild::RawData([4, 5].into())
                },
                AttributeWithValue {
                    attr: db.find_attribute(AttHandle(6)).unwrap(),
                    value: AttAttributeDataChild::RawData([6, 7].into())
                }
            ]
        );
    }

    #[test]
    fn test_stop_once_length_changes() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(6),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![6, 7],
            ),
        ]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![AttributeWithValue {
                attr: db.find_attribute(AttHandle(3)).unwrap(),
                value: AttAttributeDataChild::RawData([4, 5].into())
            },]
        );
    }

    #[test]
    fn test_truncate_to_mtu() {
        // arrange: attr with data of length 3
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: UUID,
                permissions: AttPermissions::READABLE,
            },
            vec![4, 5, 6],
        )]);

        // act: read the attribute with max_size = 2
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            2,
        ))
        .unwrap();

        // assert: the length of the read attribute is 2
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![AttributeWithValue {
                attr: db.find_attribute(AttHandle(3)).unwrap(),
                value: AttAttributeDataChild::RawData([4, 5].into())
            },]
        );
    }

    #[test]
    fn test_no_results() {
        // arrange: an empty database
        let db = TestAttDatabase::new(vec![]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert: no results
        assert_eq!(response.count(), 0)
    }

    #[test]
    fn test_read_failure_on_first_attr() {
        // arrange: put a non-readable attribute in the db with the right type
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: UUID,
                permissions: AttPermissions::empty(),
            },
            vec![4, 5, 6],
        )]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ));

        // assert: got READ_NOT_PERMITTED
        assert!(matches!(response, Err(AttErrorCode::READ_NOT_PERMITTED)));
    }

    #[test]
    fn test_read_failure_on_subsequent_attr() {
        // arrange: put a non-readable attribute in the db with the right
        // type
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: UUID,
                    permissions: AttPermissions::empty(),
                },
                vec![5, 6, 7],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![8, 9, 10],
            ),
        ]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert: we reply with the first attribute, but not the second or third
        // (since we stop on the first failure)
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![AttributeWithValue {
                attr: db.find_attribute(AttHandle(3)).unwrap(),
                value: AttAttributeDataChild::RawData([4, 5, 6].into())
            },]
        );
    }

    #[test]
    fn test_skip_unreadable_mismatching_attr() {
        // arrange: put a non-readable attribute in the db with the wrong type
        // between two attributes of interest
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: ANOTHER_UUID,
                    permissions: AttPermissions::empty(),
                },
                vec![5, 6, 7],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![6, 7, 8],
            ),
        ]);

        // act
        let response = tokio_test::block_on(filter_read_attributes_by_size_type(
            &db,
            db.list_attributes().into_iter(),
            UUID,
            31,
        ))
        .unwrap();

        // assert: we reply with the first and third attributes, but not the second
        assert_eq!(
            response.collect::<Vec<_>>(),
            vec![
                AttributeWithValue {
                    attr: db.find_attribute(AttHandle(3)).unwrap(),
                    value: AttAttributeDataChild::RawData([4, 5, 6].into())
                },
                AttributeWithValue {
                    attr: db.find_attribute(AttHandle(5)).unwrap(),
                    value: AttAttributeDataChild::RawData([6, 7, 8].into())
                }
            ]
        );
    }
}
