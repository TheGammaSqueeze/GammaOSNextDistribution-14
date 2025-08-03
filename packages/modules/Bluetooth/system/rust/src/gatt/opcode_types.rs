//! This module lets us classify AttOpcodes to determine how to handle them

use crate::packets::AttOpcode;

/// The type of ATT operation performed by the packet
/// (see Core Spec 5.3 Vol 3F 3.3 Attribute PDU for details)
pub enum OperationType {
    /// Client -> server, no response expected
    Command,
    /// Client -> server, response expected
    Request,
    /// Server -> client, response to a request
    Response,
    /// Server -> client, no response expected
    Notification,
    /// Server -> client, response expected
    Indication,
    /// Client -> server, response to an indication
    Confirmation,
}

/// Classify an opcode by its operation type. Note that this could be done using
/// bitmasking, but is done explicitly for clarity.
pub fn classify_opcode(opcode: AttOpcode) -> OperationType {
    match opcode {
        AttOpcode::ERROR_RESPONSE => OperationType::Response,
        AttOpcode::EXCHANGE_MTU_RESPONSE => OperationType::Response,
        AttOpcode::FIND_INFORMATION_RESPONSE => OperationType::Response,
        AttOpcode::FIND_BY_TYPE_VALUE_RESPONSE => OperationType::Response,
        AttOpcode::READ_BY_TYPE_RESPONSE => OperationType::Response,
        AttOpcode::READ_RESPONSE => OperationType::Response,
        AttOpcode::READ_BLOB_RESPONSE => OperationType::Response,
        AttOpcode::READ_MULTIPLE_RESPONSE => OperationType::Response,
        AttOpcode::READ_BY_GROUP_TYPE_RESPONSE => OperationType::Response,
        AttOpcode::WRITE_RESPONSE => OperationType::Response,
        AttOpcode::PREPARE_WRITE_RESPONSE => OperationType::Response,
        AttOpcode::EXECUTE_WRITE_RESPONSE => OperationType::Response,
        AttOpcode::READ_MULTIPLE_VARIABLE_RESPONSE => OperationType::Response,

        AttOpcode::EXCHANGE_MTU_REQUEST => OperationType::Request,
        AttOpcode::FIND_INFORMATION_REQUEST => OperationType::Request,
        AttOpcode::FIND_BY_TYPE_VALUE_REQUEST => OperationType::Request,
        AttOpcode::READ_BY_TYPE_REQUEST => OperationType::Request,
        AttOpcode::READ_REQUEST => OperationType::Request,
        AttOpcode::READ_BLOB_REQUEST => OperationType::Request,
        AttOpcode::READ_MULTIPLE_REQUEST => OperationType::Request,
        AttOpcode::READ_BY_GROUP_TYPE_REQUEST => OperationType::Request,
        AttOpcode::WRITE_REQUEST => OperationType::Request,
        AttOpcode::PREPARE_WRITE_REQUEST => OperationType::Request,
        AttOpcode::EXECUTE_WRITE_REQUEST => OperationType::Request,
        AttOpcode::READ_MULTIPLE_VARIABLE_REQUEST => OperationType::Request,

        AttOpcode::WRITE_COMMAND => OperationType::Command,
        AttOpcode::SIGNED_WRITE_COMMAND => OperationType::Command,

        AttOpcode::HANDLE_VALUE_NOTIFICATION => OperationType::Notification,

        AttOpcode::HANDLE_VALUE_INDICATION => OperationType::Indication,

        AttOpcode::HANDLE_VALUE_CONFIRMATION => OperationType::Confirmation,
    }
}
