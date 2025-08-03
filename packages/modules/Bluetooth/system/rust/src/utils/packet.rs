//! Utility for packet manipulation on top of the codegen from PDL

use crate::packets::{
    AttAttributeDataBuilder, AttAttributeDataChild, AttBuilder, AttChild, AttOpcode, Builder,
    OwnedAttView, OwnedPacket, Serializable,
};

/// Convert an ATT builder child into an owned AttView, for use in test
pub fn build_att_view_or_crash(child: impl Into<AttChild>) -> OwnedAttView {
    let child = child.into();
    let opcode = HACK_child_to_opcode(&child);
    let serialized = AttBuilder { _child_: child, opcode }.to_vec().unwrap();
    OwnedAttView::try_parse(serialized.into_boxed_slice()).unwrap()
}

/// Convert an arbitrary packet builder into an OwnedView, for use in test
pub fn build_view_or_crash<T: Builder>(builder: T) -> T::OwnedPacket {
    let buf = builder.to_vec().unwrap();
    T::OwnedPacket::try_parse(buf.into_boxed_slice()).unwrap()
}

/// Hack to workaround PDL limitations where constraints are ignored in builders
/// TODO(aryarahul) - get rid of this, PDL should deal with it!
#[allow(non_snake_case)]
pub fn HACK_child_to_opcode(child: &AttChild) -> AttOpcode {
    match child {
        AttChild::RawData(_vec) => unreachable!(),
        AttChild::AttFindInformationRequest(_) => AttOpcode::FIND_INFORMATION_REQUEST,
        AttChild::AttReadByGroupTypeRequest(_) => AttOpcode::READ_BY_GROUP_TYPE_REQUEST,
        AttChild::AttReadByTypeRequest(_) => AttOpcode::READ_BY_TYPE_REQUEST,
        AttChild::AttReadRequest(_) => AttOpcode::READ_REQUEST,
        AttChild::AttReadResponse(_) => AttOpcode::READ_RESPONSE,
        AttChild::AttErrorResponse(_) => AttOpcode::ERROR_RESPONSE,
        AttChild::AttReadByGroupTypeResponse(_) => AttOpcode::READ_BY_GROUP_TYPE_RESPONSE,
        AttChild::AttReadByTypeResponse(_) => AttOpcode::READ_BY_TYPE_RESPONSE,
        AttChild::AttFindInformationResponse(_) => AttOpcode::FIND_INFORMATION_RESPONSE,
        AttChild::AttFindByTypeValueRequest(_) => AttOpcode::FIND_BY_TYPE_VALUE_REQUEST,
        AttChild::AttFindByTypeValueResponse(_) => AttOpcode::FIND_BY_TYPE_VALUE_RESPONSE,
        AttChild::AttWriteRequest(_) => AttOpcode::WRITE_REQUEST,
        AttChild::AttWriteResponse(_) => AttOpcode::WRITE_RESPONSE,
        AttChild::AttHandleValueIndication(_) => AttOpcode::HANDLE_VALUE_INDICATION,
        AttChild::AttHandleValueConfirmation(_) => AttOpcode::HANDLE_VALUE_CONFIRMATION,
        AttChild::AttExchangeMtuRequest(_) => AttOpcode::EXCHANGE_MTU_REQUEST,
        AttChild::AttExchangeMtuResponse(_) => AttOpcode::EXCHANGE_MTU_RESPONSE,
        AttChild::AttWriteCommand(_) => AttOpcode::WRITE_COMMAND,
    }
}

/// Utility to simplify assembly of AttData by reducing boilerplate
pub fn build_att_data(child: impl Into<AttAttributeDataChild>) -> AttAttributeDataBuilder {
    AttAttributeDataBuilder { _child_: child.into() }
}
