use log::warn;

use crate::{
    gatt::ids::AttHandle,
    packets::{
        AttChild, AttErrorCode, AttErrorResponseBuilder, AttFindByTypeValueRequestView,
        AttFindInformationRequestView, AttOpcode, AttReadByGroupTypeRequestView,
        AttReadByTypeRequestView, AttReadRequestView, AttView, AttWriteRequestView, Packet,
        ParseError,
    },
};

use super::{
    att_database::AttDatabase,
    transactions::{
        find_by_type_value::handle_find_by_type_value_request,
        find_information_request::handle_find_information_request,
        read_by_group_type_request::handle_read_by_group_type_request,
        read_by_type_request::handle_read_by_type_request, read_request::handle_read_request,
        write_request::handle_write_request,
    },
};

/// This struct handles all requests needing ACKs. Only ONE should exist per
/// bearer per database, to ensure serialization.
pub struct AttRequestHandler<Db: AttDatabase> {
    db: Db,
}

impl<Db: AttDatabase> AttRequestHandler<Db> {
    pub fn new(db: Db) -> Self {
        Self { db }
    }

    // Runs a task to process an incoming packet. Takes an exclusive reference to
    // ensure that only one request is outstanding at a time (notifications +
    // commands should take a different path)
    pub async fn process_packet(&mut self, packet: AttView<'_>, mtu: usize) -> AttChild {
        match self.try_parse_and_process_packet(packet, mtu).await {
            Ok(result) => result,
            Err(_) => {
                // parse error, assume it's an unsupported request
                // TODO(aryarahul): distinguish between REQUEST_NOT_SUPPORTED and INVALID_PDU
                AttErrorResponseBuilder {
                    opcode_in_error: packet.get_opcode(),
                    handle_in_error: AttHandle(0).into(),
                    error_code: AttErrorCode::REQUEST_NOT_SUPPORTED,
                }
                .into()
            }
        }
    }

    async fn try_parse_and_process_packet(
        &mut self,
        packet: AttView<'_>,
        mtu: usize,
    ) -> Result<AttChild, ParseError> {
        let snapshotted_db = self.db.snapshot();
        match packet.get_opcode() {
            AttOpcode::READ_REQUEST => {
                Ok(handle_read_request(AttReadRequestView::try_parse(packet)?, mtu, &self.db).await)
            }
            AttOpcode::READ_BY_GROUP_TYPE_REQUEST => {
                handle_read_by_group_type_request(
                    AttReadByGroupTypeRequestView::try_parse(packet)?,
                    mtu,
                    &snapshotted_db,
                )
                .await
            }
            AttOpcode::READ_BY_TYPE_REQUEST => {
                handle_read_by_type_request(
                    AttReadByTypeRequestView::try_parse(packet)?,
                    mtu,
                    &snapshotted_db,
                )
                .await
            }
            AttOpcode::FIND_INFORMATION_REQUEST => Ok(handle_find_information_request(
                AttFindInformationRequestView::try_parse(packet)?,
                mtu,
                &snapshotted_db,
            )),
            AttOpcode::FIND_BY_TYPE_VALUE_REQUEST => Ok(handle_find_by_type_value_request(
                AttFindByTypeValueRequestView::try_parse(packet)?,
                mtu,
                &snapshotted_db,
            )
            .await),
            AttOpcode::WRITE_REQUEST => {
                Ok(handle_write_request(AttWriteRequestView::try_parse(packet)?, &self.db).await)
            }
            _ => {
                warn!("Dropping unsupported opcode {:?}", packet.get_opcode());
                Err(ParseError::InvalidEnumValue)
            }
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        core::uuid::Uuid,
        gatt::server::{
            att_database::{AttAttribute, AttPermissions},
            request_handler::AttRequestHandler,
            test::test_att_db::TestAttDatabase,
        },
        packets::{
            AttAttributeDataChild, AttReadRequestBuilder, AttReadResponseBuilder,
            AttWriteResponseBuilder,
        },
        utils::packet::{build_att_data, build_att_view_or_crash},
    };

    #[test]
    fn test_read_request() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE,
            },
            vec![1, 2, 3],
        )]);
        let mut handler = AttRequestHandler { db };
        let att_view = build_att_view_or_crash(AttReadRequestBuilder {
            attribute_handle: AttHandle(3).into(),
        });

        // act
        let response = tokio_test::block_on(handler.process_packet(att_view.view(), 31));

        // assert
        assert_eq!(
            response,
            AttChild::AttReadResponse(AttReadResponseBuilder {
                value: build_att_data(AttAttributeDataChild::RawData([1, 2, 3].into()))
            })
        );
    }

    #[test]
    fn test_unsupported_request() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE,
            },
            vec![1, 2, 3],
        )]);
        let mut handler = AttRequestHandler { db };
        let att_view = build_att_view_or_crash(AttWriteResponseBuilder {});

        // act
        let response = tokio_test::block_on(handler.process_packet(att_view.view(), 31));

        // assert
        assert_eq!(
            response,
            AttChild::AttErrorResponse(AttErrorResponseBuilder {
                opcode_in_error: AttOpcode::WRITE_RESPONSE,
                handle_in_error: AttHandle(0).into(),
                error_code: AttErrorCode::REQUEST_NOT_SUPPORTED
            })
        );
    }
}
