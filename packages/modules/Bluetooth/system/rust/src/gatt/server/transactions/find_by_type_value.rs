use log::warn;

use crate::{
    core::uuid::Uuid,
    gatt::{
        ids::AttHandle,
        server::att_database::{AttAttribute, StableAttDatabase},
    },
    packets::{
        AttChild, AttErrorCode, AttErrorResponseBuilder, AttFindByTypeValueRequestView,
        AttFindByTypeValueResponseBuilder, AttOpcode, AttributeHandleRangeBuilder, Serializable,
    },
};

use super::helpers::{
    att_grouping::find_group_end, att_range_filter::filter_to_range,
    payload_accumulator::PayloadAccumulator,
};

pub async fn handle_find_by_type_value_request(
    request: AttFindByTypeValueRequestView<'_>,
    mtu: usize,
    db: &impl StableAttDatabase,
) -> AttChild {
    let Some(attrs) = filter_to_range(
        request.get_starting_handle().into(),
        request.get_ending_handle().into(),
        db.list_attributes().into_iter(),
    ) else {
        return AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::FIND_BY_TYPE_VALUE_REQUEST,
            handle_in_error: AttHandle::from(request.get_starting_handle()).into(),
            error_code: AttErrorCode::INVALID_HANDLE,
        }
        .into();
    };

    // ATT_MTU-1 limit comes from Spec 5.3 Vol 3F Sec 3.4.3.4
    let mut matches = PayloadAccumulator::new(mtu - 1);

    for attr @ AttAttribute { handle, type_, .. } in attrs {
        if Uuid::from(request.get_attribute_type()) != type_ {
            continue;
        }
        if let Ok(value) = db.read_attribute(handle).await {
            if let Ok(data) = value.to_vec() {
                if data == request.get_attribute_value().get_raw_payload().collect::<Vec<_>>() {
                    // match found
                    if !matches.push(AttributeHandleRangeBuilder {
                        found_attribute_handle: handle.into(),
                        group_end_handle: find_group_end(db, attr)
                            .map(|attr| attr.handle)
                            .unwrap_or(handle)
                            .into(),
                    }) {
                        break;
                    }
                }
            }
        } else {
            warn!("skipping {handle:?} in FindByTypeRequest since read failed")
        }
    }

    if matches.is_empty() {
        AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::FIND_BY_TYPE_VALUE_REQUEST,
            handle_in_error: AttHandle::from(request.get_starting_handle()).into(),
            error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
        }
        .into()
    } else {
        AttFindByTypeValueResponseBuilder { handles_info: matches.into_boxed_slice() }.into()
    }
}

#[cfg(test)]
mod test {
    use crate::{
        gatt::{
            ffi::Uuid,
            server::{
                gatt_database::{
                    AttPermissions, CHARACTERISTIC_UUID, PRIMARY_SERVICE_DECLARATION_UUID,
                },
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{AttAttributeDataChild, AttFindByTypeValueRequestBuilder},
        utils::packet::{build_att_data, build_view_or_crash},
    };

    use super::*;

    const UUID: Uuid = Uuid::new(0);
    const ANOTHER_UUID: Uuid = Uuid::new(1);

    const VALUE: [u8; 2] = [1, 2];
    const ANOTHER_VALUE: [u8; 2] = [3, 4];

    #[test]
    fn test_uuid_match() {
        // arrange: db all with same value, but some with different UUID
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: ANOTHER_UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(5).into(),
            attribute_type: UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 128, &db));

        // assert: we only matched the ones with the correct UUID
        let AttChild::AttFindByTypeValueResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttFindByTypeValueResponseBuilder {
                handles_info: [
                    AttributeHandleRangeBuilder {
                        found_attribute_handle: AttHandle(3).into(),
                        group_end_handle: AttHandle(3).into(),
                    },
                    AttributeHandleRangeBuilder {
                        found_attribute_handle: AttHandle(5).into(),
                        group_end_handle: AttHandle(5).into(),
                    },
                ]
                .into()
            }
        );
    }

    #[test]
    fn test_value_match() {
        // arrange: db all with same type, but some with different value
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                ANOTHER_VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(5).into(),
            attribute_type: UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 128, &db));

        // assert
        let AttChild::AttFindByTypeValueResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttFindByTypeValueResponseBuilder {
                handles_info: [
                    AttributeHandleRangeBuilder {
                        found_attribute_handle: AttHandle(3).into(),
                        group_end_handle: AttHandle(3).into(),
                    },
                    AttributeHandleRangeBuilder {
                        found_attribute_handle: AttHandle(5).into(),
                        group_end_handle: AttHandle(5).into(),
                    },
                ]
                .into()
            }
        );
    }

    #[test]
    fn test_range_check() {
        // arrange: empty db
        let db = TestAttDatabase::new(vec![]);

        // act: provide an invalid handle range
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(1).into(),
            attribute_type: UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 128, &db));

        // assert
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::FIND_BY_TYPE_VALUE_REQUEST,
                handle_in_error: AttHandle(3).into(),
                error_code: AttErrorCode::INVALID_HANDLE,
            }
        );
    }

    #[test]
    fn test_empty_response() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: UUID,
                permissions: AttPermissions::READABLE,
            },
            VALUE.into(),
        )]);

        // act: query using a range that does not overlap with matching attributes
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(4).into(),
            ending_handle: AttHandle(5).into(),
            attribute_type: UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 128, &db));

        // assert: got ATTRIBUTE_NOT_FOUND erro
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::FIND_BY_TYPE_VALUE_REQUEST,
                handle_in_error: AttHandle(4).into(),
                error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
            }
        );
    }

    #[test]
    fn test_grouping_uuid() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: CHARACTERISTIC_UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
        ]);

        // act: look for a particular characteristic declaration
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(4).into(),
            attribute_type: CHARACTERISTIC_UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 128, &db));

        // assert
        let AttChild::AttFindByTypeValueResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttFindByTypeValueResponseBuilder {
                handles_info: [AttributeHandleRangeBuilder {
                    found_attribute_handle: AttHandle(3).into(),
                    group_end_handle: AttHandle(4).into(),
                },]
                .into()
            }
        );
    }

    #[test]
    fn test_limit_total_size() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                VALUE.into(),
            ),
        ]);

        // act: use MTU = 5, so we can only fit one element in the output
        let att_view = build_view_or_crash(AttFindByTypeValueRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(4).into(),
            attribute_type: UUID.try_into().unwrap(),
            attribute_value: build_att_data(AttAttributeDataChild::RawData(VALUE.into())),
        });
        let response =
            tokio_test::block_on(handle_find_by_type_value_request(att_view.view(), 5, &db));

        // assert: only one of the two matches produced
        let AttChild::AttFindByTypeValueResponse(response) = response else {
            unreachable!("{response:?}")
        };
        assert_eq!(
            response,
            AttFindByTypeValueResponseBuilder {
                handles_info: [AttributeHandleRangeBuilder {
                    found_attribute_handle: AttHandle(3).into(),
                    group_end_handle: AttHandle(3).into(),
                },]
                .into()
            }
        );
    }
}
