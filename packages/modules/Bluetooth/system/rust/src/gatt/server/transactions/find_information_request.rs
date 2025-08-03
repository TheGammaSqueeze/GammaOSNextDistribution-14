use crate::{
    gatt::{
        ids::AttHandle,
        server::att_database::{AttAttribute, AttDatabase},
    },
    packets::{
        AttChild, AttErrorCode, AttErrorResponseBuilder, AttFindInformationLongResponseBuilder,
        AttFindInformationRequestView, AttFindInformationResponseBuilder,
        AttFindInformationResponseFormat, AttFindInformationResponseLongEntryBuilder,
        AttFindInformationResponseShortEntryBuilder, AttFindInformationShortResponseBuilder,
        AttOpcode,
    },
};

use super::helpers::{att_range_filter::filter_to_range, payload_accumulator::PayloadAccumulator};

pub fn handle_find_information_request<T: AttDatabase>(
    request: AttFindInformationRequestView<'_>,
    mtu: usize,
    db: &T,
) -> AttChild {
    let Some(attrs) = filter_to_range(
        request.get_starting_handle().into(),
        request.get_ending_handle().into(),
        db.list_attributes().into_iter(),
    ) else {
        return AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::FIND_INFORMATION_REQUEST,
            handle_in_error: AttHandle::from(request.get_starting_handle()).into(),
            error_code: AttErrorCode::INVALID_HANDLE,
        }
        .into()
    };

    if let Some(resp) = handle_find_information_request_short(attrs.clone(), mtu) {
        AttFindInformationResponseBuilder {
            format: AttFindInformationResponseFormat::SHORT,
            _child_: resp.into(),
        }
        .into()
    } else if let Some(resp) = handle_find_information_request_long(attrs, mtu) {
        AttFindInformationResponseBuilder {
            format: AttFindInformationResponseFormat::LONG,
            _child_: resp.into(),
        }
        .into()
    } else {
        AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::FIND_INFORMATION_REQUEST,
            handle_in_error: AttHandle::from(request.get_starting_handle()).into(),
            error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
        }
        .into()
    }
}

/// Returns a builder IF we can return at least one attribute, otherwise returns
/// None
fn handle_find_information_request_short(
    attributes: impl Iterator<Item = AttAttribute>,
    mtu: usize,
) -> Option<AttFindInformationShortResponseBuilder> {
    // Core Spec 5.3 Vol 3F 3.4.3.2 gives the ATT_MTU - 2 limit
    let mut out = PayloadAccumulator::new(mtu - 2);
    for AttAttribute { handle, type_: uuid, .. } in attributes {
        if let Ok(uuid) = uuid.try_into() {
            if out.push(AttFindInformationResponseShortEntryBuilder { handle: handle.into(), uuid })
            {
                // If we successfully pushed a 16-bit UUID, continue. In all other cases, we
                // should break.
                continue;
            }
        }
        break;
    }

    if out.is_empty() {
        None
    } else {
        Some(AttFindInformationShortResponseBuilder { data: out.into_boxed_slice() })
    }
}

fn handle_find_information_request_long(
    attributes: impl Iterator<Item = AttAttribute>,
    mtu: usize,
) -> Option<AttFindInformationLongResponseBuilder> {
    // Core Spec 5.3 Vol 3F 3.4.3.2 gives the ATT_MTU - 2 limit
    let mut out = PayloadAccumulator::new(mtu - 2);

    for AttAttribute { handle, type_: uuid, .. } in attributes {
        if !out.push(AttFindInformationResponseLongEntryBuilder {
            handle: handle.into(),
            uuid: uuid.into(),
        }) {
            break;
        }
    }

    if out.is_empty() {
        None
    } else {
        Some(AttFindInformationLongResponseBuilder { data: out.into_boxed_slice() })
    }
}

#[cfg(test)]
mod test {
    use crate::{
        core::uuid::Uuid,
        gatt::server::{gatt_database::AttPermissions, test::test_att_db::TestAttDatabase},
        packets::AttFindInformationRequestBuilder,
        utils::packet::build_view_or_crash,
    };

    use super::*;

    #[test]
    fn test_long_uuids() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: Uuid::new(0x01020304),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: Uuid::new(0x01020305),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: Uuid::new(0x01020306),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttFindInformationRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(4).into(),
        });
        let response = handle_find_information_request(att_view.view(), 128, &db);

        // assert
        let AttChild::AttFindInformationResponse(response) = response else {
            unreachable!("{response:?}");
        };
        assert_eq!(
            response,
            AttFindInformationResponseBuilder {
                format: AttFindInformationResponseFormat::LONG,
                _child_: AttFindInformationLongResponseBuilder {
                    data: [
                        AttFindInformationResponseLongEntryBuilder {
                            handle: AttHandle(3).into(),
                            uuid: Uuid::new(0x01020304).into(),
                        },
                        AttFindInformationResponseLongEntryBuilder {
                            handle: AttHandle(4).into(),
                            uuid: Uuid::new(0x01020305).into(),
                        }
                    ]
                    .into()
                }
                .into()
            }
        );
    }

    #[test]
    fn test_short_uuids() {
        // arrange
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: AttHandle(3),
                    type_: Uuid::new(0x0102),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: Uuid::new(0x0103),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(5),
                    type_: Uuid::new(0x01020306),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
        ]);

        // act
        let att_view = build_view_or_crash(AttFindInformationRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(5).into(),
        });
        let response = handle_find_information_request(att_view.view(), 128, &db);

        // assert
        let AttChild::AttFindInformationResponse(response) = response else {
            unreachable!("{response:?}");
        };
        assert_eq!(
            response,
            AttFindInformationResponseBuilder {
                format: AttFindInformationResponseFormat::SHORT,
                _child_: AttFindInformationShortResponseBuilder {
                    data: [
                        AttFindInformationResponseShortEntryBuilder {
                            handle: AttHandle(3).into(),
                            uuid: Uuid::new(0x0102).try_into().unwrap(),
                        },
                        AttFindInformationResponseShortEntryBuilder {
                            handle: AttHandle(4).into(),
                            uuid: Uuid::new(0x0103).try_into().unwrap(),
                        }
                    ]
                    .into()
                }
                .into()
            }
        );
    }

    #[test]
    fn test_handle_validation() {
        // arrange: empty db
        let db = TestAttDatabase::new(vec![]);

        // act: use an invalid handle range
        let att_view = build_view_or_crash(AttFindInformationRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(2).into(),
        });
        let response = handle_find_information_request(att_view.view(), 128, &db);

        // assert: got INVALID_HANDLE
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{response:?}");
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::FIND_INFORMATION_REQUEST,
                handle_in_error: AttHandle(3).into(),
                error_code: AttErrorCode::INVALID_HANDLE,
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
                    type_: Uuid::new(0x0102),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
            (
                AttAttribute {
                    handle: AttHandle(4),
                    type_: Uuid::new(0x0103),
                    permissions: AttPermissions::READABLE,
                },
                vec![4, 5],
            ),
        ]);

        // act: use MTU = 6, so only one entry can fit
        let att_view = build_view_or_crash(AttFindInformationRequestBuilder {
            starting_handle: AttHandle(3).into(),
            ending_handle: AttHandle(5).into(),
        });
        let response = handle_find_information_request(att_view.view(), 6, &db);

        // assert: only one entry (not two) provided
        let AttChild::AttFindInformationResponse(response) = response else {
            unreachable!("{response:?}");
        };
        assert_eq!(
            response,
            AttFindInformationResponseBuilder {
                format: AttFindInformationResponseFormat::SHORT,
                _child_: AttFindInformationShortResponseBuilder {
                    data: [AttFindInformationResponseShortEntryBuilder {
                        handle: AttHandle(3).into(),
                        uuid: Uuid::new(0x0102).try_into().unwrap(),
                    },]
                    .into()
                }
                .into()
            }
        );
    }

    #[test]
    fn test_empty_output() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: Uuid::new(0x0102),
                permissions: AttPermissions::READABLE,
            },
            vec![4, 5],
        )]);

        // act: use a range that matches no attributes
        let att_view = build_view_or_crash(AttFindInformationRequestBuilder {
            starting_handle: AttHandle(4).into(),
            ending_handle: AttHandle(5).into(),
        });
        let response = handle_find_information_request(att_view.view(), 6, &db);

        // assert: got ATTRIBUTE_NOT_FOUND
        let AttChild::AttErrorResponse(response) = response else {
            unreachable!("{response:?}");
        };
        assert_eq!(
            response,
            AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::FIND_INFORMATION_REQUEST,
                handle_in_error: AttHandle(4).into(),
                error_code: AttErrorCode::ATTRIBUTE_NOT_FOUND,
            }
        );
    }
}
