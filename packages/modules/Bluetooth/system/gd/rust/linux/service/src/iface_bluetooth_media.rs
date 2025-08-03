use bt_topshim::profiles::a2dp::{
    A2dpCodecBitsPerSample, A2dpCodecChannelMode, A2dpCodecConfig, A2dpCodecIndex,
    A2dpCodecSampleRate, PresentationPosition,
};
use bt_topshim::profiles::avrcp::PlayerMetadata;
use bt_topshim::profiles::hfp::{HfpCodecBitId, HfpCodecFormat};
use btstack::bluetooth_media::{BluetoothAudioDevice, IBluetoothMedia, IBluetoothMediaCallback};
use btstack::RPCProxy;

use dbus::arg::RefArg;
use dbus::nonblock::SyncConnection;
use dbus::strings::Path;

use dbus_macros::{dbus_method, dbus_propmap, dbus_proxy_obj, generate_dbus_exporter};

use dbus_projection::prelude::*;

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

use num_traits::{FromPrimitive, ToPrimitive};

use std::convert::{TryFrom, TryInto};
use std::sync::Arc;

#[allow(dead_code)]
struct BluetoothMediaCallbackDBus {}

#[dbus_propmap(A2dpCodecConfig)]
pub struct A2dpCodecConfigDBus {
    codec_type: i32,
    codec_priority: i32,
    sample_rate: i32,
    bits_per_sample: i32,
    channel_mode: i32,
    codec_specific_1: i64,
    codec_specific_2: i64,
    codec_specific_3: i64,
    codec_specific_4: i64,
}

#[dbus_propmap(BluetoothAudioDevice)]
pub struct BluetoothAudioDeviceDBus {
    address: String,
    name: String,
    a2dp_caps: Vec<A2dpCodecConfig>,
    hfp_cap: HfpCodecFormat,
    absolute_volume: bool,
}

impl_dbus_arg_from_into!(HfpCodecBitId, i32);
impl_dbus_arg_from_into!(HfpCodecFormat, i32);
impl_dbus_arg_enum!(A2dpCodecIndex);
impl_dbus_arg_from_into!(A2dpCodecSampleRate, i32);
impl_dbus_arg_from_into!(A2dpCodecBitsPerSample, i32);
impl_dbus_arg_from_into!(A2dpCodecChannelMode, i32);

#[dbus_proxy_obj(BluetoothMediaCallback, "org.chromium.bluetooth.BluetoothMediaCallback")]
impl IBluetoothMediaCallback for BluetoothMediaCallbackDBus {
    #[dbus_method("OnBluetoothAudioDeviceAdded")]
    fn on_bluetooth_audio_device_added(&mut self, device: BluetoothAudioDevice) {
        dbus_generated!()
    }

    #[dbus_method("OnBluetoothAudioDeviceRemoved")]
    fn on_bluetooth_audio_device_removed(&mut self, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("OnAbsoluteVolumeSupportedChanged")]
    fn on_absolute_volume_supported_changed(&mut self, supported: bool) {
        dbus_generated!()
    }

    #[dbus_method("OnAbsoluteVolumeChanged")]
    fn on_absolute_volume_changed(&mut self, volume: u8) {
        dbus_generated!()
    }

    #[dbus_method("OnHfpVolumeChanged")]
    fn on_hfp_volume_changed(&mut self, volume: u8, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("OnHfpAudioDisconnected")]
    fn on_hfp_audio_disconnected(&mut self, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("OnHfpDebugDump")]
    fn on_hfp_debug_dump(
        &mut self,
        active: bool,
        codec_id: u16,
        total_num_decoded_frames: i32,
        pkt_loss_ratio: f64,
        begin_ts: u64,
        end_ts: u64,
        pkt_status_in_hex: String,
        pkt_status_in_binary: String,
    ) {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct IBluetoothMediaDBus {}

#[dbus_propmap(PresentationPosition)]
pub struct PresentationPositionDBus {
    remote_delay_report_ns: u64,
    total_bytes_read: u64,
    data_position_sec: i64,
    data_position_nsec: i32,
}

impl DBusArg for PlayerMetadata {
    type DBusType = dbus::arg::PropMap;
    fn from_dbus(
        data: dbus::arg::PropMap,
        _conn: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
        _remote: Option<dbus::strings::BusName<'static>>,
        _disconnect_watcher: Option<
            std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>,
        >,
    ) -> Result<PlayerMetadata, Box<dyn std::error::Error>> {
        let mut metadata = PlayerMetadata::default();

        for (key, variant) in data {
            if variant.arg_type() != dbus::arg::ArgType::Variant {
                return Err(Box::new(DBusArgError::new(format!("{} must be a variant", key))));
            }
            match key.as_str() {
                "title" => {
                    metadata.title = String::ref_arg_to_rust(
                        variant.as_static_inner(0).unwrap(),
                        String::from("PlayerMetadata::Title"),
                    )?
                }
                "artist" => {
                    metadata.artist = String::ref_arg_to_rust(
                        variant.as_static_inner(0).unwrap(),
                        String::from("PlayerMetadata::Artist"),
                    )?
                }
                "album" => {
                    metadata.album = String::ref_arg_to_rust(
                        variant.as_static_inner(0).unwrap(),
                        String::from("PlayerMetadata::Album"),
                    )?
                }
                "length" => {
                    metadata.length_us = i64::ref_arg_to_rust(
                        variant.as_static_inner(0).unwrap(),
                        String::from("PlayerMetadata::Length"),
                    )?
                }
                _ => {}
            }
        }
        return Ok(metadata);
    }

    fn to_dbus(
        _metadata: PlayerMetadata,
    ) -> Result<dbus::arg::PropMap, Box<dyn std::error::Error>> {
        Ok(std::collections::HashMap::new())
    }

    fn log(metadata: &PlayerMetadata) -> String {
        String::from(format!("{:?}", metadata))
    }
}

#[generate_dbus_exporter(export_bluetooth_media_dbus_intf, "org.chromium.bluetooth.BluetoothMedia")]
impl IBluetoothMedia for IBluetoothMediaDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothMediaCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Initialize")]
    fn initialize(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Cleanup")]
    fn cleanup(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Connect")]
    fn connect(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("Disconnect")]
    fn disconnect(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("SetActiveDevice")]
    fn set_active_device(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("ResetActiveDevice")]
    fn reset_active_device(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("SetHfpActiveDevice", DBusLog::Disable)]
    fn set_hfp_active_device(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("SetAudioConfig")]
    fn set_audio_config(
        &mut self,
        address: String,
        codec_type: A2dpCodecIndex,
        sample_rate: A2dpCodecSampleRate,
        bits_per_sample: A2dpCodecBitsPerSample,
        channel_mode: A2dpCodecChannelMode,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetVolume", DBusLog::Disable)]
    fn set_volume(&mut self, volume: u8) {
        dbus_generated!()
    }

    #[dbus_method("SetHfpVolume")]
    fn set_hfp_volume(&mut self, volume: u8, address: String) {
        dbus_generated!()
    }

    #[dbus_method("StartAudioRequest")]
    fn start_audio_request(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetA2dpAudioStarted", DBusLog::Disable)]
    fn get_a2dp_audio_started(&mut self, address: String) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StopAudioRequest", DBusLog::Disable)]
    fn stop_audio_request(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("StartScoCall")]
    fn start_sco_call(
        &mut self,
        address: String,
        sco_offload: bool,
        disabled_codecs: HfpCodecBitId,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetHfpAudioFinalCodecs")]
    fn get_hfp_audio_final_codecs(&mut self, address: String) -> u8 {
        dbus_generated!()
    }

    #[dbus_method("StopScoCall")]
    fn stop_sco_call(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("GetPresentationPosition", DBusLog::Disable)]
    fn get_presentation_position(&mut self) -> PresentationPosition {
        dbus_generated!()
    }

    // Temporary AVRCP-related meida DBUS APIs. The following APIs intercept between Chrome CRAS
    // and cras_server as an expedited solution for AVRCP implementation. The APIs are subject to
    // change when retiring Chrome CRAS.
    #[dbus_method("SetPlayerPlaybackStatus", DBusLog::Disable)]
    fn set_player_playback_status(&mut self, status: String) {
        dbus_generated!()
    }

    #[dbus_method("SetPlayerPosition", DBusLog::Disable)]
    fn set_player_position(&mut self, position_us: i64) {
        dbus_generated!()
    }

    #[dbus_method("SetPlayerMetadata")]
    fn set_player_metadata(&mut self, metadata: PlayerMetadata) {
        dbus_generated!()
    }

    #[dbus_method("TriggerDebugDump")]
    fn trigger_debug_dump(&mut self) {
        dbus_generated!()
    }
}
