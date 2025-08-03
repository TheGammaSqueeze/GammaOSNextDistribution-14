// Copyright 2021, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! NCI API module

use crate::{CommandSender, LogicalConnectionsRegistry, Result};
use bytes::Bytes;
use log::{debug, error};
use nfc_hal::{HalEvent, HalEventRegistry, HalEventStatus};
use nfc_packets::nci::RfMappingConfiguration;
use nfc_packets::nci::{self, CommandBuilder, DataPacket, Opcode};
use nfc_packets::nci::{ConnCloseCommandBuilder, ConnCreateCommandBuilder};
use nfc_packets::nci::{DestParam, DestParamTypes, DestTypes};
use nfc_packets::nci::{FeatureEnable, PacketBoundaryFlag, ResetType};
use nfc_packets::nci::{InitCommandBuilder, ResetCommandBuilder};
use nfc_packets::nci::{InitResponse, ResponseChild};
use tokio::sync::oneshot;

type ConnCallback = fn(u8, u16, &[u8]);

struct NfcData {
    init_response: Option<InitResponse>,
    rf_callback: Option<ConnCallback>,
    hci_callback: Option<ConnCallback>,
}

type RespCallback = fn(u16, &[u8]);

/// NCI API object to manage static API data
pub struct NciApi {
    /// Command Sender external interface
    commands: Option<CommandSender>,
    /// Interface to Logical Connections Registry
    connections: Option<LogicalConnectionsRegistry>,
    /// The NFC response callback
    callback: Option<RespCallback>,
    /// HalEventRegistry is used to register for HAL events
    hal_events: Option<HalEventRegistry>,
    nfc_data: NfcData,
}

impl NciApi {
    /// NciApi constructor
    pub fn new() -> NciApi {
        let nfc_data = NfcData { init_response: None, rf_callback: None, hci_callback: None };
        NciApi { commands: None, connections: None, callback: None, hal_events: None, nfc_data }
    }

    /** ****************************************************************************
     **
     ** Function         nfc_enable
     **
     ** Description      This function enables NFC. Prior to calling NFC_Enable:
     **                  - the NFCC must be powered up, and ready to receive
     **                    commands.
     **
     **                  This function opens the NCI transport (if applicable),
     **                  resets the NFC controller, and initializes the NFC
     **                  subsystems.
     **
     **                  When the NFC startup procedure is completed, an
     **                  NFC_ENABLE_REVT is returned to the application using the
     **                  tNFC_RESPONSE_CBACK.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_Enable(tNFC_RESPONSE_CBACK* p_cback);
    pub async fn nfc_enable(&mut self, callback: RespCallback) {
        let nci = crate::init().await;

        self.commands = Some(nci.commands);
        self.connections = Some(nci.connections);
        self.callback = Some(callback);
        self.hal_events = Some(nci.hal_events);
    }
    /** ****************************************************************************
     **
     ** Function         NFC_Disable
     **
     ** Description      This function performs clean up routines for shutting down
     **                  NFC and closes the NCI transport (if using dedicated NCI
     **                  transport).
     **
     **                  When the NFC shutdown procedure is completed, an
     **                  NFC_DISABLED_REVT is returned to the application using the
     **                  tNFC_RESPONSE_CBACK.
     **
     ** Returns          nothing
     **
     *******************************************************************************/
    /// extern void NFC_Disable(void);
    pub async fn nfc_disable(&mut self) {
        let (tx, rx) = oneshot::channel::<HalEventStatus>();
        if let Some(mut event) = self.hal_events.take() {
            event.register(HalEvent::CloseComplete, tx).await;

            if let Some(cmd) = self.commands.take() {
                drop(cmd);
            }
            if let Some(conn) = self.connections.take() {
                drop(conn);
            }
            let status = rx.await.unwrap();
            debug!("Shutdown complete {:?}.", status);

            if let Some(cb) = self.callback.take() {
                cb(1, &[]);
            }
        }
    }

    /** ****************************************************************************
     **
     ** Function         NFC_Init
     **
     ** Description      This function initializes control blocks for NFC
     **
     ** Returns          nothing
     **
     *******************************************************************************/
    /// extern void NFC_Init(tHAL_NFC_ENTRY* p_hal_entry_tbl);
    pub async fn nfc_init(&mut self) -> Result<()> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let reset = cmd
                .send_and_notify(
                    ResetCommandBuilder { gid: 0, pbf, reset_type: ResetType::ResetConfig }
                        .build()
                        .into(),
                )
                .await?;
            let _notification_packet = reset.notification.await?;
            let init = cmd
                .send(
                    InitCommandBuilder { gid: 0, pbf, feature_enable: FeatureEnable::Rfu }
                        .build()
                        .into(),
                )
                .await?;
            if let ResponseChild::InitResponse(irp) = init.specialize() {
                if let Some(conn) = self.connections.as_mut() {
                    // Open static RF connection
                    // TODO: use channels instead of callcacks here
                    // the data can be tranlated to c-callback at the shim level
                    conn.open(0, self.nfc_data.rf_callback, 0, 0).await;
                    // Open static HCI connection
                    conn.open(
                        1, /* TODO: link constants to the c header */
                        self.nfc_data.hci_callback,
                        irp.get_max_data_payload(),
                        irp.get_num_of_credits(),
                    )
                    .await;
                }
                self.nfc_data.init_response = Some(irp);
            }
        }
        Ok(())
    }

    /** *****************************************************************************
     **
     ** Function         NFC_GetLmrtSize
     **
     ** Description      Called by application wto query the Listen Mode Routing
     **                  Table size supported by NFCC
     **
     ** Returns          Listen Mode Routing Table size
     **
     *******************************************************************************/
    /// extern uint16_t NFC_GetLmrtSize(void);
    pub async fn nfc_get_lmrt_size(&mut self) -> u16 {
        if let Some(ir) = &self.nfc_data.init_response {
            ir.get_max_rout_tbls_size()
        } else {
            0
        }
    }

    /** *****************************************************************************
     **
     ** Function         NFC_SetConfig
     **
     ** Description      This function is called to send the configuration parameter
     **                  TLV to NFCC. The response from NFCC is reported by
     **                  tNFC_RESPONSE_CBACK as NFC_SET_CONFIG_REVT.
     **
     ** Parameters       tlv_size - the length of p_param_tlvs.
     **                  p_param_tlvs - the parameter ID/Len/Value list
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_SetConfig(uint8_t tlv_size, uint8_t* p_param_tlvs);
    pub async fn nfc_set_config(&mut self, param_tlvs: &[u8]) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let rp = cmd
                .send(
                    CommandBuilder {
                        gid: 0,
                        pbf,
                        op: Opcode::CoreSetConfig,
                        payload: Some(Bytes::copy_from_slice(param_tlvs)),
                    }
                    .build(),
                )
                .await?;
            let raw = Bytes::from(rp);
            if let Some(cb) = self.callback {
                cb(2, &raw[3..]);
            }
            Ok(raw[3])
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }

    /** *****************************************************************************
     **
     ** Function         NFC_GetConfig
     **
     ** Description      This function is called to retrieve the parameter TLV from
     **                  NFCC. The response from NFCC is reported by
     **                  tNFC_RESPONSE_CBACK as NFC_GET_CONFIG_REVT.
     **
     ** Parameters       num_ids - the number of parameter IDs
     **                  p_param_ids - the parameter ID list.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_GetConfig(uint8_t num_ids, uint8_t* p_param_ids);
    pub async fn nfc_get_config(&mut self, param_tlvs: &[u8]) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let rp = cmd
                .send(
                    CommandBuilder {
                        gid: 0,
                        pbf,
                        op: Opcode::CoreGetConfig,
                        payload: Some(Bytes::copy_from_slice(param_tlvs)),
                    }
                    .build(),
                )
                .await?;
            let raw = Bytes::from(rp);
            if let Some(cb) = self.callback {
                cb(3, &raw[3..]);
            }
            Ok(raw[3])
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }
    /** ****************************************************************************
     **
     ** Function         NFC_ConnCreate
     **
     ** Description      This function is called to create a logical connection with
     **                  NFCC for data exchange.
     **                  The response from NFCC is reported in tNFC_CONN_CBACK
     **                  as NFC_CONN_CREATE_CEVT.
     **
     ** Parameters       dest_type - the destination type
     **                  id   - the NFCEE ID or RF Discovery ID .
     **                  protocol - the protocol
     **                  p_cback - the data callback function to receive data from
     **                  NFCC
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    //extern tNFC_STATUS NFC_ConnCreate(uint8_t dest_type, uint8_t id,
    //                                  uint8_t protocol, tNFC_CONN_CBACK* p_cback);
    pub async fn nfc_conn_create(
        &mut self,
        dest_type: u8,
        id: u8,
        protocol: u8,
        callback: ConnCallback,
    ) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        let mut destparams: Vec<DestParam> = vec![];
        let dt = DestTypes::try_from(dest_type).unwrap();
        match dt {
            DestTypes::NfccLpbk => (),
            DestTypes::Remote => {
                let parameter = vec![id, protocol];
                destparams.push(DestParam { ptype: DestParamTypes::RfDisc, parameter });
            }
            DestTypes::Nfcee => {
                let parameter: Vec<u8> = vec![id, protocol];
                destparams.push(DestParam { ptype: DestParamTypes::Nfcee, parameter });
            }
            _ => return Ok(nci::Status::InvalidParam as u8),
        }
        if let Some(cmd) = self.commands.as_mut() {
            let rp = cmd
                .send(ConnCreateCommandBuilder { gid: 0, pbf, dt, destparams }.build().into())
                .await?;
            if let ResponseChild::ConnCreateResponse(ccrp) = rp.specialize() {
                let status = ccrp.get_status();
                if status == nci::Status::Ok {
                    if let Some(conn) = self.connections.as_mut() {
                        conn.open(
                            ccrp.get_conn_id(),
                            Some(callback),
                            ccrp.get_mpps(),
                            ccrp.get_ncreds(),
                        )
                        .await;
                        let conn_create_evt =
                            [status as u8, dest_type, id, ccrp.get_mpps(), ccrp.get_ncreds()];
                        callback(ccrp.get_conn_id(), 0, &conn_create_evt[..]);
                    } else {
                        return Ok(nci::Status::NotInitialized as u8);
                    }
                }
                Ok(status as u8)
            } else {
                Ok(nci::Status::Failed as u8)
            }
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }

    /** ****************************************************************************
     **
     ** Function         NFC_ConnClose
     **
     ** Description      This function is called to close a logical connection with
     **                  NFCC.
     **                  The response from NFCC is reported in tNFC_CONN_CBACK
     **                  as NFC_CONN_CLOSE_CEVT.
     **
     ** Parameters       conn_id - the connection id.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    //extern tNFC_STATUS NFC_ConnClose(uint8_t conn_id);
    pub async fn nfc_conn_close(&mut self, conn_id: u8) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(conn) = self.connections.as_mut() {
            if let Some(cb) = conn.close(conn_id).await {
                if let Some(cmd) = self.commands.as_mut() {
                    let rp = cmd
                        .send(ConnCloseCommandBuilder { gid: 0, pbf, conn_id }.build().into())
                        .await?;
                    if let ResponseChild::ConnCloseResponse(ccrp) = rp.specialize() {
                        let status = ccrp.get_status() as u8;
                        let conn_close_evt = [status];
                        cb(conn_id, 1, &conn_close_evt[..]);
                        return Ok(status);
                    } else {
                        return Ok(nci::Status::Failed as u8);
                    }
                }
            } else {
                return Ok(nci::Status::InvalidParam as u8);
            }
        }
        Ok(nci::Status::NotInitialized as u8)
    }

    /** *****************************************************************************
     **
     ** Function         NFC_SetStaticRfCback
     **
     ** Description      This function is called to update the data callback function
     **                  to receive the data for the given connection id.
     **
     ** Parameters       p_cback - the connection callback function
     **
     ** Returns          Nothing
     **
     *******************************************************************************/
    //extern void NFC_SetStaticRfCback(tNFC_CONN_CBACK* p_cback);
    pub async fn nfc_set_static_rf_callback(&mut self, callback: ConnCallback) {
        self.nfc_data.rf_callback = Some(callback);
        if let Some(conn) = self.connections.as_mut() {
            conn.set_static_callback(0, Some(callback)).await;
        }
    }

    /** *****************************************************************************
     **
     ** Function         NFC_SetStaticHciCback
     **
     ** Description      This function to update the data callback function
     **                  to receive the data for the static Hci connection id.
     **
     ** Parameters       p_cback - the connection callback function
     **
     ** Returns          Nothing
     **
     *******************************************************************************/
    //extern void NFC_SetStaticHciCback(tNFC_CONN_CBACK* p_cback);
    pub async fn nfc_set_static_hci_callback(&mut self, callback: ConnCallback) {
        self.nfc_data.hci_callback = Some(callback);
        if let Some(conn) = self.connections.as_mut() {
            conn.set_static_callback(1, Some(callback)).await;
        }
    }

    /*******************************************************************************
     **
     ** Function         NFC_SetReassemblyFlag
     **
     ** Description      This function is called to set if nfc will reassemble
     **                  nci packet as much as its buffer can hold or it should not
     **                  reassemble but forward the fragmented nci packet to layer
     **                  above. If nci data pkt is fragmented, nfc may send multiple
     **                  NFC_DATA_CEVT with status NFC_STATUS_CONTINUE before sending
     **                  NFC_DATA_CEVT with status NFC_STATUS_OK based on reassembly
     **                  configuration and reassembly buffer size
     **
     ** Parameters       reassembly - flag to indicate if nfc may reassemble or not
     **
     ** Returns          Nothing
     **
     *******************************************************************************/
    //extern void NFC_SetReassemblyFlag(bool reassembly);

    /** ****************************************************************************
     **
     ** Function         NFC_SendData
     **
     ** Description      This function is called to send the given data packet
     **                  to the connection identified by the given connection id.
     **
     ** Parameters       conn_id - the connection id.
     **                  p_data - the data packet
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    //extern tNFC_STATUS NFC_SendData(uint8_t conn_id, NFC_HDR* p_data);
    pub async fn nfc_send_data(&mut self, conn_id: u8, data: &[u8]) -> Result<u8> {
        if let Some(conn) = self.connections.as_mut() {
            match DataPacket::parse(data) {
                Ok(pkt) => {
                    conn.send_packet(conn_id, pkt).await;
                    return Ok(nci::Status::Ok as u8);
                }
                Err(e) => {
                    error!("Data packet is invalid:{:?}", e);
                    return Ok(nci::Status::InvalidParam as u8);
                }
            }
        }
        Ok(nci::Status::NotInitialized as u8)
    }

    /** ****************************************************************************
     **
     ** Function         NFC_FlushData
     **
     ** Description      This function is called to discard the tx data queue of
     **                  the given connection id.
     **
     ** Parameters       conn_id - the connection id.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    //extern tNFC_STATUS NFC_FlushData(uint8_t conn_id);
    pub async fn nfc_flush_data(&mut self, conn_id: u8) -> Result<u8> {
        if let Some(conn) = self.connections.as_mut() {
            if conn.flush_data(conn_id).await {
                Ok(nci::Status::Ok as u8)
            } else {
                Ok(nci::Status::Failed as u8)
            }
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }

    /** ****************************************************************************
     **
     ** Function         NFC_DiscoveryMap
     **
     ** Description      This function is called to set the discovery interface
     **                  mapping. The response from NFCC is reported by
     **                  tNFC_DISCOVER_CBACK as. NFC_MAP_DEVT.
     **
     ** Parameters       num - the number of items in p_params.
     **                  p_maps - the discovery interface mappings
     **                  p_cback - the discovery callback function
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    // extern tNFC_STATUS NFC_DiscoveryMap(uint8_t num, tNFC_DISCOVER_MAPS* p_maps,
    //                                    tNFC_DISCOVER_CBACK* p_cback);
    pub async fn nfc_discovery_map(&mut self, _maps: Vec<RfMappingConfiguration>) -> Result<u8> {
        Ok(0)
    }

    /*******************************************************************************
     **
     ** Function         NFC_DiscoveryStart
     **
     ** Description      This function is called to start Polling and/or Listening.
     **                  The response from NFCC is reported by tNFC_DISCOVER_CBACK
     **                  as NFC_START_DEVT. The notification from NFCC is reported by
     **                  tNFC_DISCOVER_CBACK as NFC_RESULT_DEVT.
     **
     ** Parameters       num_params - the number of items in p_params.
     **                  p_params - the discovery parameters
     **                  p_cback - the discovery callback function
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    // extern tNFC_STATUS NFC_DiscoveryStart(uint8_t num_params,
    //                                       tNFC_DISCOVER_PARAMS* p_params,
    //                                       tNFC_DISCOVER_CBACK* p_cback);

    /*******************************************************************************
     **
     ** Function         NFC_DiscoverySelect
     **
     ** Description      If tNFC_DISCOVER_CBACK reports status=NFC_MULTIPLE_PROT,
     **                  the application needs to use this function to select the
     **                  the logical endpoint to continue. The response from NFCC is
     **                  reported by tNFC_DISCOVER_CBACK as NFC_SELECT_DEVT.
     **
     ** Parameters       rf_disc_id - The ID identifies the remote device.
     **                  protocol - the logical endpoint on the remote device
     **                  rf_interface - the RF interface to communicate with NFCC
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    // extern tNFC_STATUS NFC_DiscoverySelect(uint8_t rf_disc_id, uint8_t protocol,
    //                                        uint8_t rf_interface);
}

impl Default for NciApi {
    fn default() -> Self {
        Self::new()
    }
}
