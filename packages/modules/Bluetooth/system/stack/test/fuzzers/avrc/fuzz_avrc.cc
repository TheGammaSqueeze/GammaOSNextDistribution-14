/*
 * Copyright 2022 The Android Open Source Project
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

#include "osi/include/allocator.h"
#include "stack/include/avrc_api.h"

static void free_avrc_response(tAVRC_RESPONSE& result) {
  // AVRC_BldResponse
  switch (result.pdu) {
    case AVRC_PDU_NEXT_GROUP:
    case AVRC_PDU_PREV_GROUP:
      break;

    case AVRC_PDU_GET_CAPABILITIES:
      break;

    case AVRC_PDU_LIST_PLAYER_APP_ATTR:
      break;

    case AVRC_PDU_LIST_PLAYER_APP_VALUES:
      break;

    case AVRC_PDU_GET_CUR_PLAYER_APP_VALUE:
      osi_free_and_reset((void**)&result.get_cur_app_val.p_vals);
      break;

    case AVRC_PDU_SET_PLAYER_APP_VALUE:
      break;

    case AVRC_PDU_GET_PLAYER_APP_ATTR_TEXT:
      for (int i = 0; i < result.get_app_attr_txt.num_attr; i++) {
        osi_free_and_reset((void**)&result.get_app_attr_txt.p_attrs[i].p_str);
      }
      osi_free_and_reset((void**)&result.get_app_attr_txt.p_attrs);
      break;

    case AVRC_PDU_GET_PLAYER_APP_VALUE_TEXT:
      for (int i = 0; i < result.get_app_val_txt.num_attr; i++) {
        osi_free_and_reset((void**)&result.get_app_val_txt.p_attrs[i].p_str);
      }
      osi_free_and_reset((void**)&result.get_app_val_txt.p_attrs);
      break;

    case AVRC_PDU_INFORM_DISPLAY_CHARSET:
      break;

    case AVRC_PDU_INFORM_BATTERY_STAT_OF_CT:
      break;

    case AVRC_PDU_GET_ELEMENT_ATTR:
      for (int i = 0; i < result.get_attrs.num_attrs; i++) {
        osi_free_and_reset((void**)&result.get_attrs.p_attrs[i].name.p_str);
      }
      osi_free_and_reset((void**)&result.get_attrs.p_attrs);
      break;

    case AVRC_PDU_GET_PLAY_STATUS:
      break;

    case AVRC_PDU_REGISTER_NOTIFICATION:
      break;

    case AVRC_PDU_REQUEST_CONTINUATION_RSP:
      break;

    case AVRC_PDU_ABORT_CONTINUATION_RSP:
      break;

    case AVRC_PDU_SET_ADDRESSED_PLAYER:
      break;

    case AVRC_PDU_PLAY_ITEM:
      break;

    case AVRC_PDU_SET_ABSOLUTE_VOLUME:
      break;

    case AVRC_PDU_ADD_TO_NOW_PLAYING:
      break;

    case AVRC_PDU_SET_BROWSED_PLAYER:
      for (int i = 0; i < result.br_player.folder_depth; i++) {
        osi_free_and_reset((void**)&result.br_player.p_folders[i].p_str);
      }
      osi_free_and_reset((void**)&result.br_player.p_folders);
      break;

    case AVRC_PDU_GET_FOLDER_ITEMS:
      for (int i = 0; i < result.get_items.item_count; i++) {
        switch (result.get_items.p_item_list[i].item_type) {
          case AVRC_ITEM_PLAYER:
            osi_free_and_reset(
                (void**)&result.get_items.p_item_list[i].u.player.name.p_str);
            break;
          case AVRC_ITEM_FOLDER:
            osi_free_and_reset(
                (void**)&result.get_items.p_item_list[i].u.folder.name.p_str);
            break;
          case AVRC_ITEM_MEDIA:
            osi_free_and_reset(
                (void**)&result.get_items.p_item_list[i].u.media.name.p_str);
            for (int j = 0;
                 j < result.get_items.p_item_list[i].u.media.attr_count; j++) {
              osi_free_and_reset((void**)&result.get_items.p_item_list[i]
                                     .u.media.p_attr_list[j]
                                     .name.p_str);
            }
            osi_free_and_reset(
                (void**)&result.get_items.p_item_list[i].u.media.p_attr_list);
            break;
        }
      }
      osi_free_and_reset((void**)&result.get_items.p_item_list);
      break;

    case AVRC_PDU_CHANGE_PATH:
      break;

    case AVRC_PDU_GET_ITEM_ATTRIBUTES:
      for (int i = 0; i < result.get_attrs.num_attrs; i++) {
        osi_free_and_reset((void**)&result.get_attrs.p_attrs[i].name.p_str);
      }
      osi_free_and_reset((void**)&result.get_attrs.p_attrs);
      break;

    case AVRC_PDU_GET_TOTAL_NUM_OF_ITEMS:
      break;

    case AVRC_PDU_SEARCH:
      break;
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  uint8_t scratch_buf[512]{};
  uint16_t scratch_buf_len = 512;
  tAVRC_MSG msg{};
  tAVRC_RESPONSE result{};

  if (size < 2) return 0;

  /* 4 command type codes
   * AVRC_CMD_CTRL 0
   * AVRC_CMD_STATUS 1
   * AVRC_CMD_SPEC_INQ 2
   * AVRC_CMD_NOTIF 3
   */
  msg.hdr.ctype = data[0] % 4;

  /* AVRC_Ctrl_ParsResponse handles opcode with AVRC_OP_VENDOR and
   * AVRC_OP_BROWSE
   * AVRC_ParsResponse handles opcode with AVRC_OP_VENDOR and
   * AVRC_OP_PASS_THRU
   * So we got 3 cases here */
  switch (data[1] % 3) {
    case 0:
      msg.hdr.opcode = AVRC_OP_PASS_THRU;
      msg.pass.p_pass_data = (uint8_t*)&data[2];
      msg.pass.pass_len = size - 2;
      break;
    case 1:
      msg.hdr.opcode = AVRC_OP_VENDOR;
      msg.vendor.p_vendor_data = (uint8_t*)&data[2];
      msg.vendor.vendor_len = size - 2;
      break;
    case 2:
      msg.hdr.opcode = AVRC_OP_BROWSE;
      msg.browse.p_browse_data = (uint8_t*)&data[2];
      msg.browse.browse_len = size - 2;
      break;
  }

  AVRC_Ctrl_ParsResponse(&msg, &result, scratch_buf, &scratch_buf_len);
  free_avrc_response(result);

  memset(&result, 0, sizeof(result));
  AVRC_ParsResponse(&msg, &result, scratch_buf, scratch_buf_len);
  free_avrc_response(result);

  return 0;
}
