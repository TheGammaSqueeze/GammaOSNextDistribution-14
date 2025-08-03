/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "storage_helper.h"

#include "client_parser.h"
#include "common/strings.h"
#include "le_audio_types.h"
#include "os/log.h"
#include "stack/include/bt_types.h"

using le_audio::types::hdl_pair;

namespace le_audio {
static constexpr uint8_t LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC = 0x00;
static constexpr uint8_t LEAUDIO_ASE_STORAGE_CURRENT_LAYOUT_MAGIC = 0x00;
static constexpr uint8_t LEAUDIO_HANDLES_STORAGE_CURRENT_LAYOUT_MAGIC = 0x00;
static constexpr uint8_t LEAUDIO_CODEC_ID_SZ = 5;

static constexpr size_t LEAUDIO_STORAGE_MAGIC_SZ =
    sizeof(uint8_t) /* magic is always uint8_t */;

static constexpr size_t LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ =
    LEAUDIO_STORAGE_MAGIC_SZ + sizeof(uint8_t); /* num_of_entries */

static constexpr size_t LEAUDIO_PACS_ENTRY_HDR_SZ =
    sizeof(uint16_t) /*handle*/ + sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint8_t) /* number of pack records in single characteristic */;

static constexpr size_t LEAUDIO_PACS_ENTRY_SZ =
    sizeof(uint8_t) /* size of single pac record */ +
    LEAUDIO_CODEC_ID_SZ /*codec id*/ +
    sizeof(uint8_t) /*codec capabilities len*/ +
    sizeof(uint8_t) /*metadata len*/;

static constexpr size_t LEAUDIO_ASES_ENTRY_SZ =
    sizeof(uint16_t) /*handle*/ + sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint8_t) /*direction*/ + sizeof(uint8_t) /*ase id*/;

static constexpr size_t LEAUDIO_STORAGE_HANDLES_ENTRIES_SZ =
    LEAUDIO_STORAGE_MAGIC_SZ + sizeof(uint16_t) /*control point handle*/ +
    sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint16_t) /*sink audio location handle*/ +
    sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint16_t) /*source audio location handle*/ +
    sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint16_t) /*supported context type handle*/ +
    sizeof(uint16_t) /*ccc handle*/ +
    sizeof(uint16_t) /*available context type handle*/ +
    sizeof(uint16_t) /*ccc handle*/ + sizeof(uint16_t) /* tmas handle */;

bool serializePacs(const le_audio::types::PublishedAudioCapabilities& pacs,
                   std::vector<uint8_t>& out) {
  auto num_of_pacs = pacs.size();
  if (num_of_pacs == 0 || (num_of_pacs > std::numeric_limits<uint8_t>::max())) {
    LOG_WARN("No pacs available");
    return false;
  }

  /* Calculate the total size */
  auto pac_bin_size = LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ;
  for (auto pac_tuple : pacs) {
    auto& pac_recs = std::get<1>(pac_tuple);
    pac_bin_size += LEAUDIO_PACS_ENTRY_HDR_SZ;
    for (const auto& pac : pac_recs) {
      pac_bin_size += LEAUDIO_PACS_ENTRY_SZ;
      pac_bin_size += pac.metadata.size();
      pac_bin_size += pac.codec_spec_caps.RawPacketSize();
    }
  }

  out.resize(pac_bin_size);
  auto* ptr = out.data();

  /* header */
  UINT8_TO_STREAM(ptr, LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC);
  UINT8_TO_STREAM(ptr, num_of_pacs);

  /* pacs entries */
  for (auto pac_tuple : pacs) {
    auto& pac_recs = std::get<1>(pac_tuple);
    uint16_t handle = std::get<0>(pac_tuple).val_hdl;
    uint16_t ccc_handle = std::get<0>(pac_tuple).ccc_hdl;

    UINT16_TO_STREAM(ptr, handle);
    UINT16_TO_STREAM(ptr, ccc_handle);
    UINT8_TO_STREAM(ptr, pac_recs.size());

    LOG_VERBOSE(" Handle: 0x%04x, ccc handle: 0x%04x, pac count: %d", handle,
                ccc_handle, static_cast<int>(pac_recs.size()));

    for (const auto& pac : pac_recs) {
      /* Pac len */
      auto pac_len = LEAUDIO_PACS_ENTRY_SZ +
                     pac.codec_spec_caps.RawPacketSize() + pac.metadata.size();
      LOG_VERBOSE("Pac size %d", static_cast<int>(pac_len));
      UINT8_TO_STREAM(ptr, pac_len - 1 /* Minus size */);

      /* Codec ID*/
      UINT8_TO_STREAM(ptr, pac.codec_id.coding_format);
      UINT16_TO_STREAM(ptr, pac.codec_id.vendor_company_id);
      UINT16_TO_STREAM(ptr, pac.codec_id.vendor_codec_id);

      /* Codec caps */
      LOG_VERBOSE("Codec capability size %d",
                  static_cast<int>(pac.codec_spec_caps.RawPacketSize()));
      UINT8_TO_STREAM(ptr, pac.codec_spec_caps.RawPacketSize());
      if (pac.codec_spec_caps.RawPacketSize() > 0) {
        ptr = pac.codec_spec_caps.RawPacket(ptr);
      }

      /* Metadata */
      LOG_VERBOSE("Metadata size %d", static_cast<int>(pac.metadata.size()));
      UINT8_TO_STREAM(ptr, pac.metadata.size());
      if (pac.metadata.size() > 0) {
        ARRAY_TO_STREAM(ptr, pac.metadata.data(), (int)pac.metadata.size());
      }
    }
  }
  return true;
}

bool SerializeSinkPacs(const le_audio::LeAudioDevice* leAudioDevice,
                       std::vector<uint8_t>& out) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }
  LOG_VERBOSE("Device %s, num of PAC characteristics: %d",
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_),
              static_cast<int>(leAudioDevice->snk_pacs_.size()));
  return serializePacs(leAudioDevice->snk_pacs_, out);
}

bool SerializeSourcePacs(const le_audio::LeAudioDevice* leAudioDevice,
                         std::vector<uint8_t>& out) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }
  LOG_VERBOSE("Device %s, num of PAC characteristics: %d",
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_),
              static_cast<int>(leAudioDevice->src_pacs_.size()));
  return serializePacs(leAudioDevice->src_pacs_, out);
}

bool deserializePacs(LeAudioDevice* leAudioDevice,
                     types::PublishedAudioCapabilities& pacs_db,
                     const std::vector<uint8_t>& in) {
  if (in.size() <
      LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ + LEAUDIO_PACS_ENTRY_SZ) {
    LOG_WARN("There is not single PACS stored");
    return false;
  }

  auto* ptr = in.data();

  uint8_t magic;
  STREAM_TO_UINT8(magic, ptr);

  if (magic != LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC) {
    LOG_ERROR("Invalid magic (%d!=%d) for device %s", magic,
              LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC,
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  uint8_t num_of_pacs_chars;
  STREAM_TO_UINT8(num_of_pacs_chars, ptr);

  if (in.size() < LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ +
                      (num_of_pacs_chars * LEAUDIO_PACS_ENTRY_SZ)) {
    LOG_ERROR("Invalid persistent storage data for device %s",
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  /* pacs entries */
  while (num_of_pacs_chars--) {
    struct hdl_pair hdl_pair;
    uint8_t pac_count;

    STREAM_TO_UINT16(hdl_pair.val_hdl, ptr);
    STREAM_TO_UINT16(hdl_pair.ccc_hdl, ptr);
    STREAM_TO_UINT8(pac_count, ptr);

    LOG_VERBOSE(" Handle: 0x%04x, ccc handle: 0x%04x, pac_count: %d",
                hdl_pair.val_hdl, hdl_pair.ccc_hdl, pac_count);

    pacs_db.push_back(std::make_tuple(
        hdl_pair, std::vector<struct le_audio::types::acs_ac_record>()));

    auto hdl = hdl_pair.val_hdl;
    auto pac_tuple_iter = std::find_if(
        pacs_db.begin(), pacs_db.end(),
        [&hdl](auto& pac_ent) { return std::get<0>(pac_ent).val_hdl == hdl; });

    std::vector<struct le_audio::types::acs_ac_record> pac_recs;
    while (pac_count--) {
      uint8_t pac_len;
      STREAM_TO_UINT8(pac_len, ptr);
      LOG_VERBOSE("Pac len %d", pac_len);

      if (client_parser::pacs::ParseSinglePac(pac_recs, pac_len, ptr) < 0) {
        LOG_ERROR("Cannot parse stored PACs (impossible)");
        return false;
      }
      ptr += pac_len;
    }
    leAudioDevice->RegisterPACs(&std::get<1>(*pac_tuple_iter), &pac_recs);
  }

  return true;
}

bool DeserializeSinkPacs(le_audio::LeAudioDevice* leAudioDevice,
                         const std::vector<uint8_t>& in) {
  LOG_VERBOSE("");
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }
  return deserializePacs(leAudioDevice, leAudioDevice->snk_pacs_, in);
}

bool DeserializeSourcePacs(le_audio::LeAudioDevice* leAudioDevice,
                           const std::vector<uint8_t>& in) {
  LOG_VERBOSE("");
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }
  return deserializePacs(leAudioDevice, leAudioDevice->src_pacs_, in);
}

bool SerializeAses(const le_audio::LeAudioDevice* leAudioDevice,
                   std::vector<uint8_t>& out) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }

  auto num_of_ases = leAudioDevice->ases_.size();
  LOG_DEBUG(" device: %s, number of ases %d",
            ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_),
            static_cast<int>(num_of_ases));

  if (num_of_ases == 0 || (num_of_ases > std::numeric_limits<uint8_t>::max())) {
    LOG_WARN("No ases available for device %s",
             ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  /* Calculate the total size */
  auto ases_bin_size = LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ +
                       num_of_ases * LEAUDIO_ASES_ENTRY_SZ;
  out.resize(ases_bin_size);
  auto* ptr = out.data();

  /* header */
  UINT8_TO_STREAM(ptr, LEAUDIO_ASE_STORAGE_CURRENT_LAYOUT_MAGIC);
  UINT8_TO_STREAM(ptr, num_of_ases);

  /* pacs entries */
  for (const auto& ase : leAudioDevice->ases_) {
    LOG_VERBOSE(
        "Storing ASE ID: %d, direction %s, handle 0x%04x, ccc_handle 0x%04x",
        ase.id,
        ase.direction == le_audio::types::kLeAudioDirectionSink ? "sink "
                                                                : "source",
        ase.hdls.val_hdl, ase.hdls.ccc_hdl);

    UINT16_TO_STREAM(ptr, ase.hdls.val_hdl);
    UINT16_TO_STREAM(ptr, ase.hdls.ccc_hdl);
    UINT8_TO_STREAM(ptr, ase.id);
    UINT8_TO_STREAM(ptr, ase.direction);
  }

  return true;
}

bool DeserializeAses(le_audio::LeAudioDevice* leAudioDevice,
                     const std::vector<uint8_t>& in) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }

  if (in.size() <
      LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ + LEAUDIO_ASES_ENTRY_SZ) {
    LOG_WARN("There is not single ASE stored for device %s",
             ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  auto* ptr = in.data();

  uint8_t magic;
  STREAM_TO_UINT8(magic, ptr);

  if (magic != LEAUDIO_ASE_STORAGE_CURRENT_LAYOUT_MAGIC) {
    LOG_ERROR("Invalid magic (%d!=%d", magic,
              LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC);
    return false;
  }

  uint8_t num_of_ases;
  STREAM_TO_UINT8(num_of_ases, ptr);

  if (in.size() < LEAUDIO_STORAGE_HEADER_WITH_ENTRIES_SZ +
                      (num_of_ases * LEAUDIO_ASES_ENTRY_SZ)) {
    LOG_ERROR("Invalid persistent storage data for device %s",
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  LOG_DEBUG("Loading %d Ases for device %s", num_of_ases,
            ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
  /* sets entries */
  while (num_of_ases--) {
    uint16_t handle;
    uint16_t ccc_handle;
    uint8_t direction;
    uint8_t ase_id;

    STREAM_TO_UINT16(handle, ptr);
    STREAM_TO_UINT16(ccc_handle, ptr);
    STREAM_TO_UINT8(ase_id, ptr);
    STREAM_TO_UINT8(direction, ptr);

    leAudioDevice->ases_.emplace_back(handle, ccc_handle, direction, ase_id);
    LOG_VERBOSE(
        " Loading ASE ID: %d, direction %s, handle 0x%04x, ccc_handle 0x%04x",
        ase_id,
        direction == le_audio::types::kLeAudioDirectionSink ? "sink "
                                                            : "source",
        handle, ccc_handle);
  }

  return true;
}

bool SerializeHandles(const LeAudioDevice* leAudioDevice,
                      std::vector<uint8_t>& out) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }

  /* Calculate the total size */
  out.resize(LEAUDIO_STORAGE_HANDLES_ENTRIES_SZ);
  auto* ptr = out.data();

  /* header */
  UINT8_TO_STREAM(ptr, LEAUDIO_HANDLES_STORAGE_CURRENT_LAYOUT_MAGIC);

  if (leAudioDevice->ctp_hdls_.val_hdl == 0 ||
      leAudioDevice->ctp_hdls_.ccc_hdl == 0) {
    LOG_WARN("Invalid control point handles for device %s",
             ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  UINT16_TO_STREAM(ptr, leAudioDevice->ctp_hdls_.val_hdl);
  UINT16_TO_STREAM(ptr, leAudioDevice->ctp_hdls_.ccc_hdl);

  UINT16_TO_STREAM(ptr, leAudioDevice->snk_audio_locations_hdls_.val_hdl);
  UINT16_TO_STREAM(ptr, leAudioDevice->snk_audio_locations_hdls_.ccc_hdl);

  UINT16_TO_STREAM(ptr, leAudioDevice->src_audio_locations_hdls_.val_hdl);
  UINT16_TO_STREAM(ptr, leAudioDevice->src_audio_locations_hdls_.ccc_hdl);

  UINT16_TO_STREAM(ptr, leAudioDevice->audio_supp_cont_hdls_.val_hdl);
  UINT16_TO_STREAM(ptr, leAudioDevice->audio_supp_cont_hdls_.ccc_hdl);

  UINT16_TO_STREAM(ptr, leAudioDevice->audio_avail_hdls_.val_hdl);
  UINT16_TO_STREAM(ptr, leAudioDevice->audio_avail_hdls_.ccc_hdl);

  UINT16_TO_STREAM(ptr, leAudioDevice->tmap_role_hdl_);

  return true;
}

bool DeserializeHandles(LeAudioDevice* leAudioDevice,
                        const std::vector<uint8_t>& in) {
  if (leAudioDevice == nullptr) {
    LOG_WARN(" Skipping unknown device");
    return false;
  }

  if (in.size() != LEAUDIO_STORAGE_HANDLES_ENTRIES_SZ) {
    LOG_WARN("There is not single ASE stored for device %s",
             ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  auto* ptr = in.data();

  uint8_t magic;
  STREAM_TO_UINT8(magic, ptr);

  if (magic != LEAUDIO_HANDLES_STORAGE_CURRENT_LAYOUT_MAGIC) {
    LOG_ERROR("Invalid magic (%d!=%d) for device %s", magic,
              LEAUDIO_PACS_STORAGE_CURRENT_LAYOUT_MAGIC,
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  STREAM_TO_UINT16(leAudioDevice->ctp_hdls_.val_hdl, ptr);
  STREAM_TO_UINT16(leAudioDevice->ctp_hdls_.ccc_hdl, ptr);
  LOG_VERBOSE("ctp.val_hdl: 0x%04x, ctp.ccc_hdl: 0x%04x",
              leAudioDevice->ctp_hdls_.val_hdl,
              leAudioDevice->ctp_hdls_.ccc_hdl);

  STREAM_TO_UINT16(leAudioDevice->snk_audio_locations_hdls_.val_hdl, ptr);
  STREAM_TO_UINT16(leAudioDevice->snk_audio_locations_hdls_.ccc_hdl, ptr);
  LOG_VERBOSE(
      "snk_audio_locations_hdls_.val_hdl: 0x%04x,"
      "snk_audio_locations_hdls_.ccc_hdl: 0x%04x",
      leAudioDevice->snk_audio_locations_hdls_.val_hdl,
      leAudioDevice->snk_audio_locations_hdls_.ccc_hdl);

  STREAM_TO_UINT16(leAudioDevice->src_audio_locations_hdls_.val_hdl, ptr);
  STREAM_TO_UINT16(leAudioDevice->src_audio_locations_hdls_.ccc_hdl, ptr);
  LOG_VERBOSE(
      "src_audio_locations_hdls_.val_hdl: 0x%04x,"
      "src_audio_locations_hdls_.ccc_hdl: 0x%04x",
      leAudioDevice->src_audio_locations_hdls_.val_hdl,
      leAudioDevice->src_audio_locations_hdls_.ccc_hdl);

  STREAM_TO_UINT16(leAudioDevice->audio_supp_cont_hdls_.val_hdl, ptr);
  STREAM_TO_UINT16(leAudioDevice->audio_supp_cont_hdls_.ccc_hdl, ptr);
  LOG_VERBOSE(
      "audio_supp_cont_hdls_.val_hdl: 0x%04x,"
      "audio_supp_cont_hdls_.ccc_hdl: 0x%04x",
      leAudioDevice->audio_supp_cont_hdls_.val_hdl,
      leAudioDevice->audio_supp_cont_hdls_.ccc_hdl);

  STREAM_TO_UINT16(leAudioDevice->audio_avail_hdls_.val_hdl, ptr);
  STREAM_TO_UINT16(leAudioDevice->audio_avail_hdls_.ccc_hdl, ptr);
  LOG_VERBOSE(
      "audio_avail_hdls_.val_hdl: 0x%04x,"
      "audio_avail_hdls_.ccc_hdl: 0x%04x",
      leAudioDevice->audio_avail_hdls_.val_hdl,
      leAudioDevice->audio_avail_hdls_.ccc_hdl);

  STREAM_TO_UINT16(leAudioDevice->tmap_role_hdl_, ptr);
  LOG_VERBOSE("tmap_role_hdl_: 0x%04x", leAudioDevice->tmap_role_hdl_);

  leAudioDevice->known_service_handles_ = true;
  return true;
}
}  // namespace le_audio
