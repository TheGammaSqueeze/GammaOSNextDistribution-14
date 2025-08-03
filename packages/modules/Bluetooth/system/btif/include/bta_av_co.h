/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <audio_hal_interface/a2dp_encoding.h>

#include "btif/include/bta_av_co_peer.h"

/**
 * BTA AV codec callouts state.
 */
class BtaAvCoState {
 public:
  BtaAvCoState() = default;

  /**
   * Set the active peer for the state.
   * @param peer
   */
  void setActivePeer(BtaAvCoPeer* peer);

  /**
   * Gets the active peer for the state.
   * @return pointer to the active peer.
   */
  BtaAvCoPeer* getActivePeer() const;

  /**
   * Gets the codec config for the state.
   * @return the active codec config.
   */
  uint8_t* getCodecConfig();

  /**
   * Updates the codec config
   * @param codec_config codec config that needs to be updated.
   */
  void setCodecConfig(const uint8_t* codec_config);

  /**
   * Clears the codec config.
   */
  void clearCodecConfig();

  /**
   * Resets the state.
   */
  void Reset();
  virtual ~BtaAvCoState() = default;

 private:
  // The current active peer
  BtaAvCoPeer* active_peer_;
  // Current codec configuration
  uint8_t codec_config_[AVDT_CODEC_SIZE];
};

class BtaAvCo {
 public:
  BtaAvCo(bool content_protect_enabled, BtaAvCoPeerCache* bta_av_co_peer_bank)
      : peer_cache_(bta_av_co_peer_bank),
        content_protect_enabled_(content_protect_enabled),
        content_protect_flag_(0) {
    Reset();
  }

  virtual ~BtaAvCo() = default;

  /**
   * Initialize the state.
   *
   * @param codec_priorities the codec priorities to use for the initialization
   * @param supported_codecs return the list of supported codecs
   */
  void Init(const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
            std::vector<btav_a2dp_codec_info_t>* supported_codecs);

  /**
   * Checks whether a codec is supported.
   *
   * @param codec_index the index of the codec to check
   * @return true if the codec is supported, otherwise false
   */
  bool IsSupportedCodec(btav_a2dp_codec_index_t codec_index);

  /**
   * Get the current codec configuration for the active peer.
   *
   * @return the current codec configuration if found, otherwise nullptr
   */
  A2dpCodecConfig* GetActivePeerCurrentCodec();

  /**
   * Get the current codec configuration for a peer.
   *
   * @param peer_address the peer address
   * @return the current codec configuration if found, otherwise nullptr
   */
  A2dpCodecConfig* GetPeerCurrentCodec(const RawAddress& peer_address);

  /**
   * Process the AVDTP discovery result: number of Stream End Points (SEP)
   * found during the AVDTP stream discovery process.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param num_seps the number of discovered SEPs
   * @param num_sinks number of discovered Sink SEPs
   * @param num_sources number of discovered Source SEPs
   * @param uuid_local local UUID
   */
  void ProcessDiscoveryResult(tBTA_AV_HNDL bta_av_handle,
                              const RawAddress& peer_address, uint8_t num_seps,
                              uint8_t num_sinks, uint8_t num_sources,
                              uint16_t uuid_local);

  /**
   * Process retrieved codec configuration and content protection from
   * Peer Sink SEP.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param p_codec_info the peer sink capability filled-in by the caller.
   * On success, it will contain the current codec configuration for the peer.
   * @param p_sep_info_idx the peer SEP index for the corresponding peer
   * sink capability filled-in by the caller. On success, it will contain
   * the SEP index for the current codec configuration for the peer.
   * @param seid the peer SEP index in peer tables
   * @param p_num_protect the peer SEP number of content protection elements
   * filled-in by the caller. On success, it will contain the SEP number of
   * content protection elements for the current codec configuration for the
   * peer.
   * @param p_protect_info the peer SEP content protection info filled-in by
   * the caller. On success, it will contain the SEP content protection info
   * for the current codec configuration for the peer.
   * @return A2DP_SUCCESS on success, otherwise A2DP_FAIL
   */
  tA2DP_STATUS ProcessSourceGetConfig(tBTA_AV_HNDL bta_av_handle,
                                      const RawAddress& peer_address,
                                      uint8_t* p_codec_info,
                                      uint8_t* p_sep_info_idx, uint8_t seid,
                                      uint8_t* p_num_protect,
                                      uint8_t* p_protect_info);

  /**
   * Process retrieved codec configuration and content protection from
   * Peer Source SEP.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param p_codec_info the peer source capability filled-in by the caller.
   * On success, it will contain the current codec configuration for the peer.
   * @param p_sep_info_idx the peer SEP index for the corresponding peer
   * source capability filled-in by the caller. On success, it will contain
   * the SEP index for the current codec configuration for the peer.
   * @param seid the peer SEP index in peer tables
   * @param p_num_protect the peer SEP number of content protection elements
   * filled-in by the caller. On success, it will contain the SEP number of
   * content protection elements for the current codec configuration for the
   * peer.
   * @param p_protect_info the peer SEP content protection info filled-in by
   * the caller. On success, it will contain the SEP content protection info
   * for the current codec configuration for the peer.
   * @return A2DP_SUCCESS on success, otherwise A2DP_FAIL
   */
  tA2DP_STATUS ProcessSinkGetConfig(tBTA_AV_HNDL bta_av_handle,
                                    const RawAddress& peer_address,
                                    uint8_t* p_codec_info,
                                    uint8_t* p_sep_info_idx, uint8_t seid,
                                    uint8_t* p_num_protect,
                                    uint8_t* p_protect_info);

  /**
   * Process AVDTP Set Config to set the codec and content protection
   * configuration of the audio stream.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param p_codec_info the codec configuration to set
   * @param seid stream endpoint ID of stream initiating the operation
   * @param peer_address the peer address
   * @param num_protect the peer SEP number of content protection elements
   * @param p_protect_info the peer SEP content protection info
   * @param t_local_sep the local SEP: AVDT_TSEP_SRC or AVDT_TSEP_SNK
   * @param avdt_handle the AVDTP handle
   */
  void ProcessSetConfig(tBTA_AV_HNDL bta_av_handle,
                        const RawAddress& peer_address,
                        const uint8_t* p_codec_info, uint8_t seid,
                        uint8_t num_protect, const uint8_t* p_protect_info,
                        uint8_t t_local_sep, uint8_t avdt_handle);

  /**
   * Process AVDTP Open when the stream connection is opened.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param mtu the MTU of the connection
   */
  void ProcessOpen(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                   uint16_t mtu);

  /**
   * Process AVDTP Close when the stream connection is closed.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   */
  void ProcessClose(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address);

  /**
   * Process AVDTP Start when the audio data streaming is started.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param p_codec_info the codec configuration
   * @param p_no_rtp_header on return, set to true if the audio data packets
   * should not contain RTP header
   */
  void ProcessStart(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                    const uint8_t* p_codec_info, bool* p_no_rtp_header);

  /**
   * Process AVDTP Stop when the audio data streaming is stopped.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   */
  void ProcessStop(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address);

  /**
   * Get the next encoded audio data packet to send.
   *
   * @param p_codec_info the codec configuration
   * @param p_timestamp on return, set to the timestamp of the data packet
   * @return the next encoded data packet or nullptr if no encoded data to send
   */
  BT_HDR* GetNextSourceDataPacket(const uint8_t* p_codec_info,
                                  uint32_t* p_timestamp);

  /**
   * An audio packet has been dropped.
   * This signal can be used by the encoder to reduce the encoder bit rate
   * setting.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   */
  void DataPacketWasDropped(tBTA_AV_HNDL bta_av_handle,
                            const RawAddress& peer_address);

  /**
   * Process AVDTP Audio Delay when the initial delay report is received by
   * the Source.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param delay the reported delay in 1/10th of a millisecond
   */
  void ProcessAudioDelay(tBTA_AV_HNDL bta_av_handle,
                         const RawAddress& peer_address, uint16_t delay);

  /**
   * Update the MTU of the audio data connection.
   *
   * @param bta_av_handle the BTA AV handle to identify the peer
   * @param peer_address the peer address
   * @param mtu the new MTU of the audio data connection
   */
  void UpdateMtu(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                 uint16_t mtu);

  /**
   * Set the active peer.
   *
   * @param peer_address the peer address
   * @return true on success, otherwise false
   */
  bool SetActivePeer(const RawAddress& peer_address);

  /**
   * Save the reconfig codec
   *
   * @param new_codec_config the new codec config
   */
  void SaveCodec(const uint8_t* new_codec_config);

  /**
   * Get the encoder parameters for a peer.
   *
   * @param peer_address the peer address
   * @param p_peer_params on return, set to the peer's encoder parameters
   */
  void GetPeerEncoderParameters(const RawAddress& peer_address,
                                tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params);

  /**
   * Get the Source encoder interface for the current codec.
   *
   * @return the Source encoder interface for the current codec
   */
  const tA2DP_ENCODER_INTERFACE* GetSourceEncoderInterface();

  /**
   * Set the codec user configuration.
   *
   * @param peer_address the peer address
   * @param codec_user_config the codec user configuration to set
   * @param p_restart_output if there is a change in the encoder configuration
   * that requires restarting of the A2DP connection, flag |p_restart_output|
   * will be set to true.
   * @return true on success, otherwise false
   */
  bool SetCodecUserConfig(const RawAddress& peer_address,
                          const btav_a2dp_codec_config_t& codec_user_config,
                          bool* p_restart_output);

  /**
   * Set the codec audio configuration.
   *
   * @param codec_audio_config the codec audio configuration to set
   * @return true on success, otherwise false
   */
  bool SetCodecAudioConfig(const btav_a2dp_codec_config_t& codec_audio_config);

  /**
   * Get the Source encoder maximum frame size for the current codec.
   *
   * @return the effective frame size for the current codec
   */
  int GetSourceEncoderEffectiveFrameSize();

  /**
   * Report the source codec state for a peer
   *
   * @param p_peer the peer to report
   * @return true on success, otherwise false
   */
  bool ReportSourceCodecState(BtaAvCoPeer* p_peer);

  /**
   * Report the sink codec state for a peer
   *
   * @param p_peer the peer to report
   * @return true on success, otherwise false
   */
  bool ReportSinkCodecState(BtaAvCoPeer* p_peer);

  /**
   * Get the content protection flag.
   *
   * @return the content protection flag. It should be one of the following:
   * AVDT_CP_SCMS_COPY_NEVER, AVDT_CP_SCMS_COPY_ONCE, AVDT_CP_SCMS_COPY_FREE
   */
  uint8_t ContentProtectFlag() const { return content_protect_flag_; }

  /**
   * Set the content protection flag.
   *
   * @param cp_flag the content protection flag. It should be one of the
   * following:
   * AVDT_CP_SCMS_COPY_NEVER, AVDT_CP_SCMS_COPY_ONCE, AVDT_CP_SCMS_COPY_FREE
   * NOTE: If Content Protection is not enabled on the system, then
   * the only acceptable vailue is AVDT_CP_SCMS_COPY_FREE.
   */
  void SetContentProtectFlag(uint8_t cp_flag) {
    if (!ContentProtectEnabled() && (cp_flag != AVDT_CP_SCMS_COPY_FREE)) {
      return;
    }
    content_protect_flag_ = cp_flag;
  }

  /**
   * Dump debug-related information.
   *
   * @param fd the file descritor to use for writing the ASCII formatted
   * information
   */
  void DebugDump(int fd);

  /**
   * Access peer data via cache.
   */
  BtaAvCoPeerCache* peer_cache_;

 private:
  /**
   * Reset the state.
   */
  void Reset();

  /**
   * Select the Source codec configuration based on peer codec support.
   *
   * Furthermore, the local state for the remaining non-selected codecs is
   * updated to reflect whether the codec is selectable.
   *
   * @param p_peer the peer to use
   * @return a pointer to the corresponding SEP Sink entry on success,
   * otherwise nullptr
   */
  const BtaAvCoSep* SelectSourceCodec(BtaAvCoPeer* p_peer);

  /**
   * Select the Sink codec configuration based on peer codec support.
   *
   * Furthermore, the local state for the remaining non-selected codecs is
   * updated to reflect whether the codec is selectable.
   *
   * @param p_peer the peer to use
   * @return a pointer to the corresponding SEP Source entry on success,
   * otherwise nullptr
   */
  const BtaAvCoSep* SelectSinkCodec(BtaAvCoPeer* p_peer);

  /**
   * Save new codec configuration.
   *
   * @param p_peer the peer to use
   * @param new_codec_config the new codec configuration to use
   * @param num_protect the number of content protection elements
   * @param p_protect_info the content protection info to use
   */
  void SaveNewCodecConfig(BtaAvCoPeer* p_peer, const uint8_t* new_codec_config,
                          uint8_t num_protect, const uint8_t* p_protect_info);

  /**
   * Set the Over-The-Air preferred codec configuration.
   *
   * The OTA preferred codec configuration is ignored if the current
   * codec configuration contains explicit user configuration, or if the
   * codec configuration for the same codec contains explicit user
   * configuration.
   *
   * @param p_peer is the peer device that sent the OTA codec configuration
   * @param p_ota_codec_config contains the received OTA A2DP codec
   * configuration from the remote peer. Note: this is not the peer codec
   * capability, but the codec configuration that the peer would like to use.
   * @param num_protect is the number of content protection methods to use
   * @param p_protect_info contains the content protection information to use.
   * @param p_restart_output if there is a change in the encoder configuration
   * that requires restarting of the A2DP connection, flag |p_restart_output|
   * is set to true.
   * @return true on success, otherwise false
   */
  bool SetCodecOtaConfig(BtaAvCoPeer* p_peer, const uint8_t* p_ota_codec_config,
                         uint8_t num_protect, const uint8_t* p_protect_info,
                         bool* p_restart_output);

  /**
   * Update all selectable Source codecs with the corresponding codec
   * information from a Sink peer.
   *
   * @param p_peer the peer Sink SEP to use
   * @return the number of codecs that have been updated
   */
  size_t UpdateAllSelectableSourceCodecs(BtaAvCoPeer* p_peer);

  /**
   * Update a selectable Source codec with the corresponding codec information
   * from a Sink peer.
   *
   * @param codec_config the codec config info to identify the codec to update
   * @param p_peer the peer Sink SEP to use
   * @return true if the codec is updated, otherwise false
   */
  bool UpdateSelectableSourceCodec(const A2dpCodecConfig& codec_config,
                                   BtaAvCoPeer* p_peer);

  /**
   * Update all selectable Sink codecs with the corresponding codec
   * information from a Source peer.
   *
   * @param p_peer the peer Source SEP to use
   * @return the number of codecs that have been updated
   */
  size_t UpdateAllSelectableSinkCodecs(BtaAvCoPeer* p_peer);

  /**
   * Update a selectable Sink codec with the corresponding codec information
   * from a Source peer.
   *
   * @param codec_config the codec config info to identify the codec to update
   * @param p_peer the peer Source SEP to use
   * @return true if the codec is updated, otherwise false
   */
  bool UpdateSelectableSinkCodec(const A2dpCodecConfig& codec_config,
                                 BtaAvCoPeer* p_peer);

  /**
   * Attempt to select Source codec configuration for a Sink peer.
   *
   * @param codec_config the codec configuration to use
   * @param p_peer the Sink peer to use
   * @return a pointer to the corresponding SEP Sink entry on success,
   * otnerwise nullptr
   */
  const BtaAvCoSep* AttemptSourceCodecSelection(
      const A2dpCodecConfig& codec_config, BtaAvCoPeer* p_peer);

  /**
   * Attempt to select Sink codec configuration for a Source peer.
   *
   * @param codec_config the codec configuration to use
   * @param p_peer the Source peer to use
   * @return a pointer to the corresponding SEP Source entry on success,
   * otnerwise nullptr
   */
  const BtaAvCoSep* AttemptSinkCodecSelection(
      const A2dpCodecConfig& codec_config, BtaAvCoPeer* p_peer);

  /**
   * Let the HAL offload provider select codec configuration.
   *
   * @param p_peer the peer to use
   * @param configuration configuration from the offload provider
   */
  std::optional<::bluetooth::audio::a2dp::provider::a2dp_configuration>
  GetProviderCodecConfiguration(BtaAvCoPeer* p_peer);

  /**
   * Select the HAL proposed configuration.
   */
  BtaAvCoSep* SelectProviderCodecConfiguration(
      BtaAvCoPeer* p_peer,
      const ::bluetooth::audio::a2dp::provider::a2dp_configuration&
          provider_codec_config);

  bool ContentProtectEnabled() const { return content_protect_enabled_; }

  const bool content_protect_enabled_;  // True if Content Protect is enabled
  uint8_t content_protect_flag_;        // Content Protect flag
  BtaAvCoState bta_av_legacy_state_;
};
