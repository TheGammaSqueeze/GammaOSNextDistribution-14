use crate::{
    core::uuid::Uuid,
    gatt::{
        ids::AttHandle,
        server::{
            att_database::StableAttDatabase,
            gatt_database::{PRIMARY_SERVICE_DECLARATION_UUID, SECONDARY_SERVICE_DECLARATION_UUID},
        },
    },
    packets::{
        AttAttributeDataBuilder, AttChild, AttErrorCode, AttErrorResponseBuilder, AttOpcode,
        AttReadByGroupTypeDataElementBuilder, AttReadByGroupTypeRequestView,
        AttReadByGroupTypeResponseBuilder, ParseError,
    },
};

use super::helpers::{
    att_filter_by_size_type::{filter_read_attributes_by_size_type, AttributeWithValue},
    att_grouping::find_group_end,
    att_range_filter::filter_to_range,
    payload_accumulator::PayloadAccumulator,
};

pub async fn handle_read_by_group_type_request(
    request: AttReadByGroupTypeRequestView<'_>,
    mtu: usize,
    db: &impl StableAttDatabase,
) -> Result<AttChild, ParseError> {
    let group_type: Uuid = request.get_attribute_group_type().try_into()?;

    // As per spec (5.3 Vol 3F 3.4.4.9)
    // > If an attribute in the set of requested attributes would cause an
    // > ATT_ERROR_RSP PDU then this attribute cannot be included in an
    // > ATT_READ_BY_GROUP_TYPE_RSP PDU and the attributes before this
    // > attribute shall be returned.
    //
    // Thus, we populate this response on failure, but only return it if no prior
    // matches were accumulated.
    let mut failure_response = AttErrorResponseBuilder {
        opcode_in_error: AttOpcode::READ_BY_GROUP_TYPE_REQUEST,
        handle_in_error: AttHandle::from(request.get_starting_handle()).into(),
        // the default error code if we just fail to find anything
        error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
    };

    let Some(attrs) = filter_to_range(
        request.get_starting_handle().into(),
        request.get_ending_handle().into(),
        db.list_attributes().into_iter(),
    ) else {
        failure_response.error_code = AttErrorCode::INVALID_HANDLE;
        return Ok(failure_response.into());
    };

    // As per Core Spec 5.3 Vol 3G 2.5.3 Attribute Grouping, only these UUIDs are
    // valid for the ATT_READ_BY_GROUP_TYPE_REQ PDU (even though other grouping
    // UUIDs do exist)
    if !matches!(group_type, PRIMARY_SERVICE_DECLARATION_UUID | SECONDARY_SERVICE_DECLARATION_UUID)
    {
        failure_response.error_code = AttErrorCode::UNSUPPORTED_GROUP_TYPE;
        return Ok(failure_response.into());
    }

    // MTU-2 limit comes from Core Spec 5.3 Vol 3F 3.4.4.9
    let mut matches = PayloadAccumulator::new(mtu - 2);

    // MTU-6 limit comes from Core Spec 5.3 Vol 3F 3.4.4.9
    match filter_read_attributes_by_size_type(db, attrs, group_type, mtu - 6).await {
        Ok(attrs) => {
            for AttributeWithValue { attr, value } in attrs {
                if !matches.push(AttReadByGroupTypeDataElementBuilder {
                    handle: attr.handle.into(),
                    end_group_handle: find_group_end(db, attr)
                        .expect("should never be None, since grouping UUID was validated earlier")
                        .handle
                        .into(),
                    value: AttAttributeDataBuilder { _child_: value },
                }) {
                    break;
                }
            }
        }
        Err(err) => {
            failure_response.error_code = err;
            return Ok(failure_response.into());
        }
    }

    Ok(if matches.is_empty() {
        failure_response.into()
    } else {
        AttReadByGroupTypeResponseBuilder { data: matches.into_boxed_slice() }.into()
    })
}

#[cfg(test)]
mod test {
    use crate::{
        gatt::{
            ids::AttHandle,
            server::{
                att_database::AttAttribute,
                gatt_database::{AttPermissions, CHARACTERISTIC_UUID},
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{AttAttributeDataChild, AttReadByGroupTypeRequestBuilder},
        utils::packet::{build_att_data, build_view_or_crash},
    };

    use super::*;

    #[test]
    fn test_simple_grouping() {
        // arrange: one service with a child attribute, another service with no children
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: CHARACTERISTIC_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![6, 7],
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(2).into(),
            ending_handle: AttHandle(6).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 31, &db))
                .unwrap();

        // assert: we identified both service groups
        let AttChild::AttReadByGroupTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByGroupTypeResponseBuilder {
                data: [
                    AttReadByGroupTypeDataElementBuilder {
                        handle: AttHandle(3).into(),
                        end_group_handle: AttHandle(4).into(),
                        value: build_att_data(AttAttributeDataChild::RawData([4, 5].into())),
                    },
                    AttReadByGroupTypeDataElementBuilder {
                        handle: AttHandle(5).into(),
                        end_group_handle: AttHandle(5).into(),
                        value: build_att_data(AttAttributeDataChild::RawData([6, 7].into())),
                    },
                ]
                .into()
            }
        );
    }

    #[test]
    fn test_invalid_group_type() {
        // arrange
        let db = TestAttDatabase::new(vec![]);

        // act: try using an unsupported group type
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(2).into(),
            ending_handle: AttHandle(6).into(),
            attribute_group_type: CHARACTERISTIC_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 31, &db))
                .unwrap();

        // assert: got UNSUPPORTED_GROUP_TYPE
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                handle_in_error: AttHandle(2).into(),
                opcode_in_error: AttOpcode::READ_BY_GROUP_TYPE_REQUEST,
                error_code: AttErrorCode::UNSUPPORTED_GROUP_TYPE,
            }
        );
    }

    #[test]
    fn test_range_validation() {
        // arrange: an empty (irrelevant) db
        let db = TestAttDatabase::new(vec![]);

        // act: query with an invalid attribute range
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(2).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 31, &db))
                .unwrap();

        // assert: we return an INVALID_HANDLE error
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                handle_in_error: AttHandle(3).into(),
                opcode_in_error: AttOpcode::READ_BY_GROUP_TYPE_REQUEST,
                error_code: AttErrorCode::INVALID_HANDLE,
            }
        )
    }

    #[test]
    fn test_attribute_truncation() {
        // arrange: one service with a value of length 5
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: PRIMARY_SERVICE_DECLARATION_UUID,
                permissions: AttPermissions::READABLE,
            },
            vec![1, 2, 3, 4, 5],
        )]);

        // act: read the service value with MTU = 7, so the value is truncated to MTU-6
        // = 1
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(2).into(),
            ending_handle: AttHandle(6).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 7, &db))
                .unwrap();

        // assert: we identified both service groups
        let AttChild::AttReadByGroupTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByGroupTypeResponseBuilder {
                data: [AttReadByGroupTypeDataElementBuilder {
                    handle: AttHandle(3).into(),
                    end_group_handle: AttHandle(3).into(),
                    value: build_att_data(AttAttributeDataChild::RawData([1].into())),
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
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5, 6, 7],
            ),
        ]);

        // act: read with MTU = 9, so we can only fit the first attribute (untruncated)
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(6).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 9, &db))
                .unwrap();

        // assert: we return only the first attribute
        let AttChild::AttReadByGroupTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByGroupTypeResponseBuilder {
                data: [AttReadByGroupTypeDataElementBuilder {
                    handle: AttHandle(3).into(),
                    end_group_handle: AttHandle(3).into(),
                    value: build_att_data(AttAttributeDataChild::RawData([4, 5, 6].into()))
                },]
                .into()
            }
        )
    }

    #[test]
    fn test_group_end_outside_range() {
        // arrange: one service with a child attribute
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: CHARACTERISTIC_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5, 6],
            ),
        ]);

        // act: search in an interval that includes the service but not its child
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(3).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 31, &db))
                .unwrap();

        // assert: the end_group_handle is correct, even though it exceeds the query
        // interval
        let AttChild::AttReadByGroupTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByGroupTypeResponseBuilder {
                data: [AttReadByGroupTypeDataElementBuilder {
                    handle: AttHandle(3).into(),
                    end_group_handle: AttHandle(4).into(),
                    value: build_att_data(AttAttributeDataChild::RawData([4, 5].into())),
                },]
                .into()
            }
        );
    }

    #[test]
    fn test_no_results() {
        // arrange: read out of the bounds where attributes of interest exist
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: PRIMARY_SERVICE_DECLARATION_UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttReadByGroupTypeRequestBuilder {
            starting_handle: AttHandle(5).into(),
            ending_handle: AttHandle(6).into(),
            attribute_group_type: PRIMARY_SERVICE_DECLARATION_UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_group_type_request(att_view.view(), 31, &db))
                .unwrap();

        // assert: we return ATTRIBUTE_NOT_FOUND
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                handle_in_error: AttHandle(5).into(),
                opcode_in_error: AttOpcode::READ_BY_GROUP_TYPE_REQUEST,
                error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
            }
        )
    }
}
