use log::warn;

use crate::packets::{AttOpcode, AttView, AttWriteCommandView, Packet};

use super::att_database::AttDatabase;

/// This struct handles all ATT commands.
pub struct AttCommandHandler<Db: AttDatabase> {
    db: Db,
}

impl<Db: AttDatabase> AttCommandHandler<Db> {
    pub fn new(db: Db) -> Self {
        Self { db }
    }

    pub fn process_packet(&self, packet: AttView<'_>) {
        let snapshotted_db = self.db.snapshot();
        match packet.get_opcode() {
            AttOpcode::WRITE_COMMAND => {
                let Ok(packet) = AttWriteCommandView::try_parse(packet) else {
                  warn!("failed to parse WRITE_COMMAND packet");
                  return;
                };
                snapshotted_db
                    .write_no_response_attribute(packet.get_handle().into(), packet.get_value());
            }
            _ => {
                warn!("Dropping unsupported opcode {:?}", packet.get_opcode());
            }
        }
    }
}

#[cfg(test)]
mod test {
    use crate::{
        core::uuid::Uuid,
        gatt::{
            ids::AttHandle,
            server::{
                att_database::{AttAttribute, AttDatabase},
                command_handler::AttCommandHandler,
                gatt_database::AttPermissions,
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{
            AttAttributeDataChild, AttErrorCode, AttErrorResponseBuilder, AttOpcode,
            AttWriteCommandBuilder,
        },
        utils::{
            packet::{build_att_data, build_att_view_or_crash},
            task::block_on_locally,
        },
    };

    #[test]
    fn test_write_command() {
        // arrange
        let db = TestAttDatabase::new(vec![(
            AttAttribute {
                handle: AttHandle(3),
                type_: Uuid::new(0x1234),
                permissions: AttPermissions::READABLE | AttPermissions::WRITABLE_WITHOUT_RESPONSE,
            },
            vec![1, 2, 3],
        )]);
        let handler = AttCommandHandler { db: db.clone() };
        let data = AttAttributeDataChild::RawData([1, 2].into());

        // act: send write command
        let att_view = build_att_view_or_crash(AttWriteCommandBuilder {
            handle: AttHandle(3).into(),
            value: build_att_data(data.clone()),
        });
        handler.process_packet(att_view.view());

        // assert: the db has been updated
        assert_eq!(block_on_locally(db.read_attribute(AttHandle(3))).unwrap(), data);
    }

    #[test]
    fn test_unsupported_command() {
        // arrange
        let db = TestAttDatabase::new(vec![]);
        let handler = AttCommandHandler { db };

        // act: send a packet that should not be handled here
        let att_view = build_att_view_or_crash(AttErrorResponseBuilder {
            opcode_in_error: AttOpcode::EXCHANGE_MTU_REQUEST,
            handle_in_error: AttHandle(1).into(),
            error_code: AttErrorCode::UNLIKELY_ERROR,
        });
        handler.process_packet(att_view.view());

        // assert: nothing happens (we crash if anything is unhandled within a mock)
    }
}
