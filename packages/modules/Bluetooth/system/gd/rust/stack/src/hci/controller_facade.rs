//! Controller facade

use crate::hci::controller::{null_terminated_to_string, ControllerExports};
use crate::hci::Hci;
use bt_common::GrpcFacade;
use bt_facade_proto::common::BluetoothAddress;
use bt_facade_proto::controller_facade::{NameMsg, OpCodeMsg, SingleValueMsg, SupportedMsg};
use bt_facade_proto::controller_facade_grpc::{create_controller_facade, ControllerFacade};
use bt_facade_proto::empty::Empty;
use bt_packets::hci::{OpCode, ReadLocalNameBuilder, WriteLocalNameBuilder};
use gddi::{module, provides, Stoppable};
use grpcio::*;
use std::convert::TryFrom;
use std::sync::Arc;

module! {
    controller_facade_module,
    providers {
        ControllerFacadeService => provide_facade,
    }
}

#[provides]
async fn provide_facade(exports: Arc<ControllerExports>, hci: Hci) -> ControllerFacadeService {
    ControllerFacadeService { exports, hci }
}

/// Controller facade service
#[allow(missing_docs)]
#[derive(Clone, Stoppable)]
pub struct ControllerFacadeService {
    pub exports: Arc<ControllerExports>,
    hci: Hci,
}

impl GrpcFacade for ControllerFacadeService {
    fn into_grpc(self) -> grpcio::Service {
        create_controller_facade(self)
    }
}

impl ControllerFacade for ControllerFacadeService {
    fn get_mac_address(
        &mut self,
        ctx: RpcContext<'_>,
        _req: Empty,
        sink: UnarySink<BluetoothAddress>,
    ) {
        let clone = self.clone();
        ctx.spawn(async move {
            let mut address = BluetoothAddress::new();
            let address_bytes: [u8; 6] = clone.exports.address.into();
            address.address = address_bytes.to_vec();
            sink.success(address).await.unwrap();
        });
    }

    fn write_local_name(&mut self, ctx: RpcContext<'_>, req: NameMsg, sink: UnarySink<Empty>) {
        let mut clone = self.clone();
        let mut builder = WriteLocalNameBuilder { local_name: [0; 248] };
        builder.local_name[0..req.name.len()].copy_from_slice(&req.name);
        ctx.spawn(async move {
            clone.hci.commands.send(builder.build()).await;
            sink.success(Empty::default()).await.unwrap();
        });
    }

    fn get_local_name(&mut self, ctx: RpcContext<'_>, _req: Empty, sink: UnarySink<NameMsg>) {
        let mut clone = self.clone();
        ctx.spawn(async move {
            let local_name = null_terminated_to_string(
                clone.hci.commands.send(ReadLocalNameBuilder {}).await.get_local_name(),
            )
            .into_bytes();
            let msg = NameMsg { name: local_name, ..NameMsg::default() };
            sink.success(msg).await.unwrap();
        });
    }

    fn is_supported_command(
        &mut self,
        ctx: RpcContext<'_>,
        op_code_msg: OpCodeMsg,
        sink: UnarySink<SupportedMsg>,
    ) {
        let clone = self.clone();
        let opcode = OpCode::try_from(u16::try_from(op_code_msg.op_code).unwrap()).unwrap();
        ctx.spawn(async move {
            let supported_msg = SupportedMsg {
                supported: clone.exports.commands.is_supported(opcode),
                ..SupportedMsg::default()
            };
            sink.success(supported_msg).await.unwrap();
        });
    }

    fn get_le_number_of_supported_advertising_sets(
        &mut self,
        ctx: RpcContext<'_>,
        _: Empty,
        sink: UnarySink<SingleValueMsg>,
    ) {
        let clone = self.clone();
        ctx.spawn(async move {
            let msg = SingleValueMsg {
                value: clone.exports.le_supported_advertising_sets.into(),
                ..SingleValueMsg::default()
            };
            sink.success(msg).await.unwrap();
        });
    }

    fn supports_simple_pairing(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports_secure_connections(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_simultaneous_le_br_edr(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_interlaced_inquiry_scan(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_rssi_with_inquiry_results(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_extended_inquiry_response(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_role_switch(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports3_slot_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports5_slot_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports_classic2m_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports_classic3m_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports3_slot_edr_packets(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports5_slot_edr_packets(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }

    fn supports_sco(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports_hv2_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_hv3_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ev3_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ev4_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }

    fn supports_ev5_packets(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_esco2m_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_esco3m_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports3_slot_esco_edr_packets(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_hold_mode(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_sniff_mode(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_park_mode(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_non_flushable_pb(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_sniff_subrating(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_encryption_pause(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_encryption(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_connection_parameters_request(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_extended_reject(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_peripheral_initiated_features_exchange(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_ping(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_data_packet_length_extension(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_privacy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_extended_scanner_filter_policies(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble2m_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_stable_modulation_index_tx(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_stable_modulation_index_rx(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_coded_phy(&mut self, _: RpcContext<'_>, _: Empty, _: UnarySink<SupportedMsg>) {
        todo!()
    }
    fn supports_ble_extended_advertising(
        &mut self,
        ctx: RpcContext<'_>,
        _: Empty,
        sink: UnarySink<SupportedMsg>,
    ) {
        let clone = self.clone();
        ctx.spawn(async move {
            let supported_msg = SupportedMsg {
                supported: clone.exports.le_features.extended_advertising,
                ..SupportedMsg::default()
            };
            sink.success(supported_msg).await.unwrap();
        });
    }
    fn supports_ble_periodic_advertising(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_channel_selection_algorithm2(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_power_class1(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_minimum_used_channels(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connection_cte_request(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connection_cte_response(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connectionless_cte_transmitter(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connectionless_cte_receiver(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_antenna_switching_during_cte_tx(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_antenna_switching_during_cte_rx(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_receiving_constant_tone_extensions(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_periodic_advertising_sync_transfer_sender(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_periodic_advertising_sync_transfer_recipient(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_sleep_clock_accuracy_updates(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_remote_public_key_validation(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connected_isochronous_stream_central(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_connected_isochronous_stream_peripheral(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_isochronous_broadcaster(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_synchronized_receiver(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_isochronous_channels_host_support(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_power_control_request(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_power_change_indication(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_path_loss_monitoring(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
    fn supports_ble_periodic_advertising_adi(
        &mut self,
        _: RpcContext<'_>,
        _: Empty,
        _: UnarySink<SupportedMsg>,
    ) {
        todo!()
    }
}
