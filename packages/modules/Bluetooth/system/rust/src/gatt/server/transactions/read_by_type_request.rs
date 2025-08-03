use crate::{
    core::uuid::Uuid,
    gatt::{ids::AttHandle, server::att_database::StableAttDatabase},
    packets::{
        AttAttributeDataBuilder, AttChild, AttErrorCode, AttErrorResponseBuilder, AttOpcode,
        AttReadByTypeDataElementBuilder, AttReadByTypeRequestView, AttReadByTypeResponseBuilder,
        ParseError,
    },
};

use super::helpers::{
    att_filter_by_size_type::{filter_read_attributes_by_size_type, AttributeWithValue},
    att_range_filter::filter_to_range,
    payload_accumulator::PayloadAccumulator,
};

pub async fn handle_read_by_type_request(
    request: AttReadByTypeRequestView<'_>,
    mtu: usize,
    db: &impl StableAttDatabase,
) -> Result<AttChild, ParseError> {
    let request_type: Uuid = request.get_attribute_type().try_into()?;

    // As per spec (5.3 Vol 3F 3.4.4.1)
    // > If an attribute in the set of requested attributes would cause an
    // > ATT_ERROR_RSP PDU then this attribute cannot be included in an
    // > ATT_READ_BY_TYPE_RSP PDU and the attributes before this attribute
    // > shall be returned.
    //
    // Thus, we populate this response on failure, but only return it if no prior
    // matches were accumulated.
    let mut failure_response = AttErrorResponseBuilder {
        opcode_in_error: AttOpcode::READ_BY_TYPE_REQUEST,
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

    // MTU-2 limit comes from Core Spec 5.3 Vol 3F 3.4.4.1
    let mut out = PayloadAccumulator::new(mtu - 2);

    // MTU-4 limit comes from Core Spec 5.3 Vol 3F 3.4.4.1
    match filter_read_attributes_by_size_type(db, attrs, request_type, mtu - 4).await {
        Ok(attrs) => {
            for AttributeWithValue { attr, value } in attrs {
                if !out.push(AttReadByTypeDataElementBuilder {
                    handle: attr.handle.into(),
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

    Ok(if out.is_empty() {
        failure_response.into()
    } else {
        AttReadByTypeResponseBuilder { data: out.into_boxed_slice() }.into()
    })
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        core::uuid::Uuid,
        gatt::{
            ids::AttHandle,
            server::{
                att_database::AttAttribute, gatt_database::AttPermissions,
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{AttAttributeDataChild, AttReadByTypeRequestBuilder},
        utils::packet::{build_att_data, build_view_or_crash},
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
        let att_view = build_view_or_crash(AttReadByTypeRequestBuilder {
            starting_handle: AttHandle(2).into(),
            ending_handle: AttHandle(6).into(),
            attribute_type: UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_type_request(att_view.view(), 31, &db)).unwrap();

        // assert
        let AttChild::AttReadByTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByTypeResponseBuilder {
                data: [AttReadByTypeDataElementBuilder {
                    handle: AttHandle(3).into(),
                    value: build_att_data(AttAttributeDataChild::RawData([4, 5].into()))
                }]
                .into()
            }
        )
    }

    #[test]
    fn test_type_filtering() {
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
        let att_view = build_view_or_crash(AttReadByTypeRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(6).into(),
            attribute_type: UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_type_request(att_view.view(), 31, &db)).unwrap();

        // assert: we correctly filtered by type (so we are using the filter_by_type
        // utility)
        let AttChild::AttReadByTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByTypeResponseBuilder {
                data: [
                    AttReadByTypeDataElementBuilder {
                        handle: AttHandle(3).into(),
                        value: build_att_data(AttAttributeDataChild::RawData([4, 5].into()))
                    },
                    AttReadByTypeDataElementBuilder {
                        handle: AttHandle(6).into(),
                        value: build_att_data(AttAttributeDataChild::RawData([6, 7].into()))
                    }
                ]
                .into()
            }
        )
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
                vec![4, 5, 6],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: UUID,
                    permissions: AttPermissions::READABLE,
                },
                vec![5, 6, 7],
            ),
        ]);

        // act: read with MTU = 8, so we can only fit the first attribute (untruncated)
        let att_view = build_view_or_crash(AttReadByTypeRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(6).into(),
            attribute_type: UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_type_request(att_view.view(), 8, &db)).unwrap();

        // assert: we return only the first attribute
        let AttChild::AttReadByTypeResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttReadByTypeResponseBuilder {
                data: [AttReadByTypeDataElementBuilder {
                    handle: AttHandle(3).into(),
                    value: build_att_data(AttAttributeDataChild::RawData([4, 5, 6].into()))
                },]
                .into()
            }
        )
    }

    #[test]
    fn test_no_results() {
        // arrange: read out of the bounds where attributes of interest exist
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
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5, 6],
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttReadByTypeRequestBuilder {
            starting_handle: AttHandle(4).into(),
            ending_handle: AttHandle(6).into(),
            attribute_type: UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_type_request(att_view.view(), 31, &db)).unwrap();

        // assert: we return ATTRIBUTE_NOT_FOUND
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                handle_in_error: AttHandle(4).into(),
                opcode_in_error: AttOpcode::READ_BY_TYPE_REQUEST,
                error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
            }
        )
    }

    #[test]
    fn test_range_validation() {
        // arrange: put a non-readable attribute in the db with the right type
        let db = TestAttDatabase::new(vec![]);

        // act
        let att_view = build_view_or_crash(AttReadByTypeRequestBuilder {
            starting_handle: AttHandle(0).into(),
            ending_handle: AttHandle(6).into(),
            attribute_type: UUID.into(),
        });
        let response =
            tokio_test::block_on(handle_read_by_type_request(att_view.view(), 31, &db)).unwrap();

        // assert: we return an INVALID_HANDLE error
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{:?}", response)
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                handle_in_error: AttHandle(0).into(),
                opcode_in_error: AttOpcode::READ_BY_TYPE_REQUEST,
                error_code: AttErrorCode::INVALID_HANDLE,
            }
        )
    }
}
