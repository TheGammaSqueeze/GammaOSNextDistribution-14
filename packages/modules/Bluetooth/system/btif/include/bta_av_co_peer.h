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
#include "bta/include/bta_av_api.h"
#include "stack/include/a2dp_codec_api.h"
#include "stack/include/bt_types.h"
#include "types/raw_address.h"

// Macro to retrieve the number of elements in a statically allocated array
#define BTA_AV_CO_NUM_ELEMENTS(__a) (sizeof(__a) / sizeof((__a)[0]))

class BtaAvCoSep {
 public:
  BtaAvCoSep()
      : sep_info_idx(0), seid(0), codec_caps{}, num_protect(0), protect_info{} {
    Reset();
  }

  /**
   * Reset the state.
   */
  void Reset() {
    sep_info_idx = 0;
    seid = 0;
    memset(codec_caps, 0, sizeof(codec_caps));
    num_protect = 0;
    memset(protect_info, 0, sizeof(protect_info));
  }

  uint8_t sep_info_idx;                    // Local SEP index (in BTA tables)
  uint8_t seid;                            // Peer SEP index (in peer tables)
  uint8_t codec_caps[AVDT_CODEC_SIZE];     // Peer SEP codec capabilities
  uint8_t num_protect;                     // Peer SEP number of CP elements
  uint8_t protect_info[AVDT_CP_INFO_LEN];  // Peer SEP content protection info
};

class BtaAvCoPeer {
 public:
  /**
   * Default constructor to initialize the state of the member variables.
   */
  BtaAvCoPeer();

  /**
   * Initialize the state.
   *
   * @param codec_priorities the codec priorities to use for the initialization
   */
  void Init(const std::vector<btav_a2dp_codec_config_t>& codec_priorities);

  /**
   * Reset the state.
   *
   * @param bta_av_handle the BTA AV handle to use
   */
  void Reset(tBTA_AV_HNDL bta_av_handle);

  /**
   * Get the BTA AV handle.
   *
   * @return the BTA AV handle
   */
  tBTA_AV_HNDL BtaAvHandle() const { return bta_av_handle_; }

  /**
   * Get the A2DP codecs.
   *
   * @return the A2DP codecs
   */
  A2dpCodecs* GetCodecs() const { return codecs_; }

  bool ContentProtectActive() const { return content_protect_active_; }
  void SetContentProtectActive(bool cp_active) {
    content_protect_active_ = cp_active;
  }

  RawAddress addr;                                // Peer address
  BtaAvCoSep sinks[BTAV_A2DP_CODEC_INDEX_MAX];    // Supported sinks
  BtaAvCoSep sources[BTAV_A2DP_CODEC_INDEX_MAX];  // Supported sources
  uint8_t num_sinks;                      // Total number of sinks at peer
  uint8_t num_sources;                    // Total number of sources at peer
  uint8_t num_seps;                       // Total number of SEPs at peer
  uint8_t num_rx_sinks;                   // Number of received sinks
  uint8_t num_rx_sources;                 // Number of received sources
  uint8_t num_sup_sinks;                  // Number of supported sinks
  uint8_t num_sup_sources;                // Number of supported sources
  const BtaAvCoSep* p_sink;               // Currently selected sink
  const BtaAvCoSep* p_source;             // Currently selected source
  uint8_t codec_config[AVDT_CODEC_SIZE];  // Current codec configuration
  bool acceptor;                          // True if acceptor
  bool reconfig_needed;                   // True if reconfiguration is needed
  bool opened;                            // True if opened
  uint16_t mtu;                           // Maximum Transmit Unit size
  uint16_t uuid_to_connect;               // UUID of peer device

 private:
  tBTA_AV_HNDL bta_av_handle_;   // BTA AV handle to use
  A2dpCodecs* codecs_;           // Locally supported codecs
  bool content_protect_active_;  // True if Content Protect is active
};

/**
 * Cache to store all the peer and codec information.
 * It provides different APIs to retrieve the peer and update the peer data.
 */
class BtaAvCoPeerCache {
 public:
  BtaAvCoPeerCache() = default;
  std::recursive_mutex codec_lock_;  // Protect access to the codec state
  std::vector<btav_a2dp_codec_config_t> codec_priorities_;  // Configured
  BtaAvCoPeer peers_[BTA_AV_NUM_STRS];  // Connected peer information

  /**
   * Inits the cache with the appropriate data.
   * @param codec_priorities codec priorities.
   * @param supported_codecs supported codecs by the stack.
   */
  void Init(const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
            std::vector<btav_a2dp_codec_info_t>* supported_codecs);

  /**
   * Resets the cache and the peer data.
   */
  void Reset();

  /**
   * Find the peer entry for a given peer address.
   *
   * @param peer_address the peer address to use
   * @return the peer entry if found, otherwise nullptr
   */
  BtaAvCoPeer* FindPeer(const RawAddress& peer_address);

  /**
   * Find the peer Source SEP entry for a given codec index.
   *
   * @param p_peer the peer to use
   * @param codec_config the codec index to use
   * @return the peer Source SEP for the codec index if found, otherwise nullptr
   */
  BtaAvCoSep* FindPeerSource(BtaAvCoPeer* p_peer,
                             btav_a2dp_codec_index_t codec_index,
                             const uint8_t content_protect_flag);

  /**
   * Find the peer Sink SEP entry for a given codec index.
   *
   * @param p_peer the peer to use
   * @param codec_index the codec index to use
   * @return the peer Sink SEP for the codec index if found, otherwise nullptr
   */
  BtaAvCoSep* FindPeerSink(BtaAvCoPeer* p_peer,
                           btav_a2dp_codec_index_t codec_index,
                           const uint8_t content_protect_flag);

  /**
   * Find the peer entry for a given BTA AV handle.
   *
   * @param bta_av_handle the BTA AV handle to use
   * @return the peer entry if found, otherwise nullptr
   */
  BtaAvCoPeer* FindPeer(tBTA_AV_HNDL bta_av_handle);

  /**
   * Find the peer entry for a given BTA AV handle and update it with the
   * peer address.
   *
   * @param bta_av_handle the BTA AV handle to use
   * @param peer_address the peer address
   * @return the peer entry if found, otherwise nullptr
   */
  BtaAvCoPeer* FindPeerAndUpdate(tBTA_AV_HNDL bta_av_handle,
                                 const RawAddress& peer_address);

  /**
   * Find the peer UUID for a given BTA AV handle.
   *
   * @param bta_av_handle the BTA AV handle to use
   * @return the peer UUID if found, otherwise 0
   */
  uint16_t FindPeerUuid(tBTA_AV_HNDL bta_av_handle);
};

/**
 * Check if a content protection service is SCMS-T.
 *
 * @param p_orotect_info the content protection info to check
 * @return true if the Contention Protection in @param p_protect_info
 * is SCMS-T, otherwise false
 */
bool ContentProtectIsScmst(const uint8_t* p_protect_info);

/**
 * Check if audio protect info contains SCMS-T Content Protection.
 *
 * @param num_protect number of protect schemes
 * @param p_protect_info the protect info to check
 * @return true if @param p_protect_info contains SCMS-T, otherwise false
 */
bool AudioProtectHasScmst(uint8_t num_protect, const uint8_t* p_protect_info);

/**
 * Check if a peer SEP has content protection enabled.
 *
 * @param p_sep the peer SEP to check
 * @param content_protect_flag flag to check if content protect is enabled or
 * not.
 * @return true if the peer SEP has content protection enabled,
 * otherwise false
 */
bool AudioSepHasContentProtection(const BtaAvCoSep* p_sep,
                                  const uint8_t content_protect_flag);
