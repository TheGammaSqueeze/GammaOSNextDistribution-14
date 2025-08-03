use crate::{
    gatt::server::att_database::AttDatabase,
    packets::{
        AttChild, AttErrorResponseBuilder, AttOpcode, AttWriteRequestView, AttWriteResponseBuilder,
    },
};

pub async fn handle_write_request<T: AttDatabase>(
    request: AttWriteRequestView<'_>,
    db: &T,
) -> AttChild {
    let handle = request.get_handle().into();
    match db.write_attribute(handle, request.get_value()).await {
        Ok(()) => AttWriteResponseBuilder {}.into(),
        Err(error_code) => AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::WRITE_REQUEST,
            handle_in_error: handle.into(),
            error_code,
        }
        .into(),
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use tokio_test::block_on;

    use crate::{
        core::uuid::Uuid,
        gatt::{
            ids::AttHandle,
            server::{
                att_database::{AttAttribute, AttDatabase},
                gatt_database::AttPermissions,
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{
            AttAttributeDataChild, AttChild, AttErrorCode, AttErrorResponseBuilder,
            AttWriteRequestBuilder, AttWriteResponseBuilder,
        },
        utils::packet::{build_att_data, build_view_or_crash},
    };

    #[test]
    fn test_successful_write() {
        // arrange: db with one writable attribute
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(1),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE,
            },
            vec![],
        )]);
        let data = AttAttributeDataChild::RawData([1, 2].into());

        // act: write to the attribute
        let att_view = build_view_or_crash(AttWriteRequestBuilder {
            handle: AttHandle(1).into(),
            value: build_att_data(data.clone()),
        });
        let resp = block_on(handle_write_request(att_view.view(), &db));

        // assert: that the write succeeded
        assert_eq!(resp, AttChild::from(AttWriteResponseBuilder {}));
        assert_eq!(block_on(db.read_attribute(AttHandle(1))).unwrap(), data);
    }

    #[test]
    fn test_failed_write() {
        // arrange: db with no writable attributes
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(1),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE,
            },
            vec![],
        )]);
        // act: write to the attribute
        let att_view = build_view_or_crash(AttWriteRequestBuilder {
            handle: AttHandle(1).into(),
            value: build_att_data(AttAttributeDataChild::RawData([1, 2].into())),
        });
        let resp = block_on(handle_write_request(att_view.view(), &db));

        // assert: that the write failed
        assert_eq!(
            resp,
            AttChild::from(AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::WRITE_REQUEST,
                handle_in_error: AttHandle(1).into(),
                error_code: AttErrorCode::WRITE_NOT_PERMITTED
            })
        );
    }
}
