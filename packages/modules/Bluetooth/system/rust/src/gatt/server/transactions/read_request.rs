use crate::{
    gatt::server::att_database::AttDatabase,
    packets::{
        AttAttributeDataBuilder, AttChild, AttErrorResponseBuilder, AttOpcode, AttReadRequestView,
        AttReadResponseBuilder,
    },
};

use super::helpers::truncate_att_data::truncate_att_data;

pub async fn handle_read_request<T: AttDatabase>(
    request: AttReadRequestView<'_>,
    mtu: usize,
    db: &T,
) -> AttChild {
    let handle = request.get_attribute_handle().into();

    match db.read_attribute(handle).await {
        Ok(data) => AttReadResponseBuilder {
            // as per 5.3 3F 3.4.4.4 ATT_READ_RSP, we truncate to MTU - 1
            value: AttAttributeDataBuilder { _child_: truncate_att_data(data, mtu - 1) },
        }
        .into(),
        Err(error_code) => AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::READ_REQUEST,
            handle_in_error: handle.into(),
            error_code,
        }
        .into(),
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        core::uuid::Uuid,
        gatt::{
            ids::AttHandle,
            server::{
                att_database::{AttAttribute, AttPermissions},
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{AttAttributeDataChild, AttErrorCode, AttReadRequestBuilder, Serializable},
        utils::packet::{build_att_data, build_view_or_crash},
    };

    fn make_db_with_handle_and_value(handle: u16, value: Vec<u8>) -> TestAttDatabase {
        TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(handle),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE,
            },
            value,
        )])
    }

    fn do_read_request_with_handle_and_mtu(
        handle: u16,
        mtu: usize,
        db: &TestAttDatabase,
    ) -> AttChild {
        let att_view = build_view_or_crash(AttReadRequestBuilder {
            attribute_handle: AttHandle(handle).into(),
        });
        tokio_test::block_on(handle_read_request(att_view.view(), mtu, db))
    }

    #[test]
    fn test_simple_read() {
        let db = make_db_with_handle_and_value(3, vec![4, 5]);

        let response = do_read_request_with_handle_and_mtu(3, 31, &db);

        response.to_vec().unwrap(); // check it serializes
        assert_eq!(
            response,
            AttChild::AttReadResponse(AttReadResponseBuilder {
                value: build_att_data(AttAttributeDataChild::RawData([4, 5].into()))
            })
        )
    }

    #[test]
    fn test_truncated_read() {
        let db = make_db_with_handle_and_value(3, vec![4, 5]);

        // act
        let response = do_read_request_with_handle_and_mtu(3, 2, &db);

        // assert
        assert_eq!(response.to_vec().unwrap(), vec![4]);
    }

    #[test]
    fn test_missed_read() {
        let db = make_db_with_handle_and_value(3, vec![4, 5]);

        // act
        let response = do_read_request_with_handle_and_mtu(4, 31, &db);

        // assert
        assert_eq!(
            response,
            AttChild::AttErrorResponse(AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::READ_REQUEST,
                handle_in_error: AttHandle(4).into(),
                error_code: AttErrorCode::INVALID_HANDLE,
            })
        );
    }

    fn make_db_with_unreadable_handle(handle: u16) -> TestAttDatabase {
        TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(handle),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::empty(),
            },
            vec![],
        )])
    }

    #[test]
    fn test_not_readable() {
        let db = make_db_with_unreadable_handle(3);

        // act
        let response = do_read_request_with_handle_and_mtu(3, 31, &db);

        // assert
        assert_eq!(
            response,
            AttChild::AttErrorResponse(AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::READ_REQUEST,
                handle_in_error: AttHandle(3).into(),
                error_code: AttErrorCode::READ_NOT_PERMITTED,
            })
        );
    }
}
