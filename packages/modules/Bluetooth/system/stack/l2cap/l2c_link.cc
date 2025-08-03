/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  this file contains the functions relating to link management. A "link"
 *  is a connection between this device and another device. Only ACL links
 *  are managed.
 *
 ******************************************************************************/
#define LOG_TAG "l2c_link"

#include <bluetooth/log.h>

#include <cstdint>

#include "device/include/device_iot_config.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/l2cap_acl_interface.h"
#include "stack/include/l2cap_hci_link_interface.h"
#include "stack/include/l2cap_security_interface.h"
#include "stack/l2cap/l2c_int.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

bool BTM_ReadPowerMode(const RawAddress& remote_bda, tBTM_PM_MODE* p_mode);
bool btm_dev_support_role_switch(const RawAddress& bd_addr);
tBTM_STATUS btm_sec_disconnect(uint16_t handle, tHCI_STATUS reason,
                               std::string);
void btm_acl_created(const RawAddress& bda, uint16_t hci_handle,
                     uint8_t link_role, tBT_TRANSPORT transport);
void btm_acl_removed(uint16_t handle);
void btm_ble_decrement_link_topology_mask(uint8_t link_role);
void btm_sco_acl_removed(const RawAddress* bda);

static void l2c_link_send_to_lower(tL2C_LCB* p_lcb, BT_HDR* p_buf,
                                   tL2C_TX_COMPLETE_CB_INFO* p_cbi);
static BT_HDR* l2cu_get_next_buffer_to_send(tL2C_LCB* p_lcb,
                                            tL2C_TX_COMPLETE_CB_INFO* p_cbi);

void l2c_link_hci_conn_comp(tHCI_STATUS status, uint16_t handle,
                            const RawAddress& p_bda) {
  tL2C_CONN_INFO ci;
  tL2C_LCB* p_lcb;
  tL2C_CCB* p_ccb;

  /* Save the parameters */
  ci.status = status;
  ci.bd_addr = p_bda;

  /* See if we have a link control block for the remote device */
  p_lcb = l2cu_find_lcb_by_bd_addr(ci.bd_addr, BT_TRANSPORT_BR_EDR);

  /* If we don't have one, allocate one */
  if (p_lcb == nullptr) {
    p_lcb = l2cu_allocate_lcb(ci.bd_addr, false, BT_TRANSPORT_BR_EDR);
    if (p_lcb == nullptr) {
      log::warn("Failed to allocate an LCB");
      return;
    }
    log::debug("Allocated l2cap control block for new connection state:{}",
               link_state_text(p_lcb->link_state));
    p_lcb->link_state = LST_CONNECTING;
  }

  if ((p_lcb->link_state == LST_CONNECTED) &&
      (status == HCI_ERR_CONNECTION_EXISTS)) {
    log::warn("Connection already exists handle:0x{:04x}", handle);
    return;
  } else if (p_lcb->link_state != LST_CONNECTING) {
    log::error(
        "Link received unexpected connection complete state:{} status:{} "
        "handle:0x{:04x}",
        link_state_text(p_lcb->link_state), hci_error_code_text(status),
        p_lcb->Handle());
    if (status != HCI_SUCCESS) {
      log::error("Disconnecting...");
      l2c_link_hci_disc_comp(p_lcb->Handle(), status);
    }
    return;
  }

  /* Save the handle */
  l2cu_set_lcb_handle(*p_lcb, handle);

  if (ci.status == HCI_SUCCESS) {
    /* Connected OK. Change state to connected */
    p_lcb->link_state = LST_CONNECTED;

    /* Get the peer information if the l2cap flow-control/rtrans is supported */
    l2cu_send_peer_info_req(p_lcb, L2CAP_EXTENDED_FEATURES_INFO_TYPE);

    if (p_lcb->IsBonding()) {
      log::debug("Link is dedicated bonding handle:0x{:04x}", p_lcb->Handle());
      if (l2cu_start_post_bond_timer(handle)) return;
    }

    alarm_cancel(p_lcb->l2c_lcb_timer);

    /* For all channels, send the event through their FSMs */
    for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;
         p_ccb = p_ccb->p_next_ccb) {
      l2c_csm_execute(p_ccb, L2CEVT_LP_CONNECT_CFM, &ci);
    }

    if (!p_lcb->ccb_queue.p_first_ccb) {
      uint64_t timeout_ms = L2CAP_LINK_STARTUP_TOUT * 1000;
      alarm_set_on_mloop(p_lcb->l2c_lcb_timer, timeout_ms,
                         l2c_lcb_timer_timeout, p_lcb);
    }
  }
  /* Max number of acl connections.                          */
  /* If there's an lcb disconnecting set this one to holding */
  else if ((ci.status == HCI_ERR_MAX_NUM_OF_CONNECTIONS) &&
           l2cu_lcb_disconnecting()) {
    log::warn("Delaying connection as reached max number of links:{}",
              HCI_ERR_MAX_NUM_OF_CONNECTIONS);
    p_lcb->link_state = LST_CONNECT_HOLDING;
    p_lcb->InvalidateHandle();
  } else {
    /* Just in case app decides to try again in the callback context */
    p_lcb->link_state = LST_DISCONNECTING;

    /* Connection failed. For all channels, send the event through */
    /* their FSMs. The CCBs should remove themselves from the LCB  */
    for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;) {
      tL2C_CCB* pn = p_ccb->p_next_ccb;

      l2c_csm_execute(p_ccb, L2CEVT_LP_CONNECT_CFM_NEG, &ci);

      p_ccb = pn;
    }

    log::info("Disconnecting link handle:0x{:04x} status:{}", p_lcb->Handle(),
              hci_error_code_text(status));
    p_lcb->SetDisconnectReason(status);
    /* Release the LCB */
    if (p_lcb->ccb_queue.p_first_ccb == NULL)
      l2cu_release_lcb(p_lcb);
    else /* there are any CCBs remaining */
    {
      if (ci.status == HCI_ERR_CONNECTION_EXISTS) {
        /* we are in collision situation, wait for connecttion request from
         * controller */
        p_lcb->link_state = LST_CONNECTING;
      } else {
        l2cu_create_conn_br_edr(p_lcb);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_sec_comp
 *
 * Description      This function is called when required security procedures
 *                  are completed.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_sec_comp(const RawAddress* p_bda,
                       UNUSED_ATTR tBT_TRANSPORT transport, void* p_ref_data,
                       tBTM_STATUS status) {
  tL2C_CONN_INFO ci;
  tL2C_LCB* p_lcb;
  tL2C_CCB* p_ccb;
  tL2C_CCB* p_next_ccb;

  log::debug("btm_status={}, BD_ADDR={}, transport={}", btm_status_text(status),
             ADDRESS_TO_LOGGABLE_CSTR(*p_bda), bt_transport_text(transport));

  if (status == BTM_SUCCESS_NO_SECURITY) {
    status = BTM_SUCCESS;
  }

  /* Save the parameters */
  ci.status = status;
  ci.bd_addr = *p_bda;

  p_lcb = l2cu_find_lcb_by_bd_addr(*p_bda, transport);

  /* If we don't have one, this is an error */
  if (!p_lcb) {
    log::warn("L2CAP got sec_comp for unknown BD_ADDR");
    return;
  }

  /* Match p_ccb with p_ref_data returned by sec manager */
  for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb; p_ccb = p_next_ccb) {
    p_next_ccb = p_ccb->p_next_ccb;

    if (p_ccb == p_ref_data) {
      switch (status) {
        case BTM_SUCCESS:
          l2c_csm_execute(p_ccb, L2CEVT_SEC_COMP, &ci);
          break;

        case BTM_DELAY_CHECK:
          /* start a timer - encryption change not received before L2CAP connect
           * req */
          alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                             L2CAP_DELAY_CHECK_SM4_TIMEOUT_MS,
                             l2c_ccb_timer_timeout, p_ccb);
          return;

        default:
          l2c_csm_execute(p_ccb, L2CEVT_SEC_COMP_NEG, &ci);
          break;
      }
    }
  }
}

/*******************************************************************************
**
** Function         l2c_link_iot_store_disc_reason
**
** Description      iot store disconnection reason to local conf file
**
** Returns          void
**
*******************************************************************************/
static void l2c_link_iot_store_disc_reason(RawAddress& bda, uint8_t reason) {
  const char* disc_keys[] = {
      IOT_CONF_KEY_GAP_DISC_CONNTIMEOUT_COUNT,
  };
  const uint8_t disc_reasons[] = {
      HCI_ERR_CONNECTION_TOUT,
  };
  int i = 0;
  int num = sizeof(disc_keys) / sizeof(disc_keys[0]);

  if (reason == (uint8_t)-1) return;

  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(bda, IOT_CONF_KEY_GAP_DISC_COUNT);
  for (i = 0; i < num; i++) {
    if (disc_reasons[i] == reason) {
      DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(bda, disc_keys[i]);
      break;
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_hci_disc_comp
 *
 * Description      This function is called when an HCI Disconnect Complete
 *                  event is received.
 *
 * Returns          true if the link is known about, else false
 *
 ******************************************************************************/
bool l2c_link_hci_disc_comp(uint16_t handle, tHCI_REASON reason) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);
  tL2C_CCB* p_ccb;
  bool status = true;
  bool lcb_is_free = true;

  /* If we don't have one, maybe an SCO link. Send to MM */
  if (!p_lcb) {
    status = false;
  } else {
    l2c_link_iot_store_disc_reason(p_lcb->remote_bd_addr, reason);

    p_lcb->SetDisconnectReason(reason);

    /* Just in case app decides to try again in the callback context */
    p_lcb->link_state = LST_DISCONNECTING;

    /* Check for BLE and handle that differently */
    if (p_lcb->transport == BT_TRANSPORT_LE)
      btm_ble_decrement_link_topology_mask(p_lcb->LinkRole());
    /* Link is disconnected. For all channels, send the event through */
    /* their FSMs. The CCBs should remove themselves from the LCB     */
    for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;) {
      tL2C_CCB* pn = p_ccb->p_next_ccb;

      /* Keep connect pending control block (if exists)
       * Possible Race condition when a reconnect occurs
       * on the channel during a disconnect of link. This
       * ccb will be automatically retried after link disconnect
       * arrives
       */
      if (p_ccb != p_lcb->p_pending_ccb) {
        l2c_csm_execute(p_ccb, L2CEVT_LP_DISCONNECT_IND, &reason);
      }
      p_ccb = pn;
    }

    if (p_lcb->transport == BT_TRANSPORT_BR_EDR)
      /* Tell SCO management to drop any SCOs on this ACL */
      btm_sco_acl_removed(&p_lcb->remote_bd_addr);

    /* If waiting for disconnect and reconnect is pending start the reconnect
       now
       race condition where layer above issued connect request on link that was
       disconnecting
     */
    if (p_lcb->ccb_queue.p_first_ccb != NULL || p_lcb->p_pending_ccb) {
      log::debug("l2c_link_hci_disc_comp: Restarting pending ACL request");
      /* Release any held buffers */
      while (!list_is_empty(p_lcb->link_xmit_data_q)) {
        BT_HDR* p_buf =
            static_cast<BT_HDR*>(list_front(p_lcb->link_xmit_data_q));
        list_remove(p_lcb->link_xmit_data_q, p_buf);
        osi_free(p_buf);
      }
      /* for LE link, always drop and re-open to ensure to get LE remote feature
       */
      if (p_lcb->transport == BT_TRANSPORT_LE) {
        btm_acl_removed(handle);
      } else {
        /* If we are going to re-use the LCB without dropping it, release all
        fixed channels
        here */
        int xx;
        for (xx = 0; xx < L2CAP_NUM_FIXED_CHNLS; xx++) {
          if (p_lcb->p_fixed_ccbs[xx] &&
              p_lcb->p_fixed_ccbs[xx] != p_lcb->p_pending_ccb) {
            l2cu_release_ccb(p_lcb->p_fixed_ccbs[xx]);

            p_lcb->p_fixed_ccbs[xx] = NULL;
            (*l2cb.fixed_reg[xx].pL2CA_FixedConn_Cb)(
                xx + L2CAP_FIRST_FIXED_CHNL, p_lcb->remote_bd_addr, false,
                p_lcb->DisconnectReason(), p_lcb->transport);
          }
        }
        /* Cleanup connection state to avoid race conditions because
         * l2cu_release_lcb won't be invoked to cleanup */
        btm_acl_removed(p_lcb->Handle());
        p_lcb->InvalidateHandle();
      }
      if (p_lcb->transport == BT_TRANSPORT_LE) {
        if (l2cu_create_conn_le(p_lcb))
          lcb_is_free = false; /* still using this lcb */
      } else {
        l2cu_create_conn_br_edr(p_lcb);
        lcb_is_free = false; /* still using this lcb */
      }
    }

    p_lcb->p_pending_ccb = NULL;

    /* Release the LCB */
    if (lcb_is_free) l2cu_release_lcb(p_lcb);
  }

  /* Now that we have a free acl connection, see if any lcbs are pending */
  if (lcb_is_free &&
      ((p_lcb = l2cu_find_lcb_by_state(LST_CONNECT_HOLDING)) != NULL)) {
    /* we found one-- create a connection */
    l2cu_create_conn_br_edr(p_lcb);
  }

  return status;
}

/*******************************************************************************
 *
 * Function         l2c_link_timeout
 *
 * Description      This function is called when a link timer expires
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_timeout(tL2C_LCB* p_lcb) {
  tL2C_CCB* p_ccb;
  tBTM_STATUS rc;

  log::debug("L2CAP - l2c_link_timeout() link state:{} is_bonding:{}",
             link_state_text(p_lcb->link_state), logbool(p_lcb->IsBonding()));

  /* If link was connecting or disconnecting, clear all channels and drop the
   * LCB */
  if ((p_lcb->link_state == LST_CONNECTING_WAIT_SWITCH) ||
      (p_lcb->link_state == LST_CONNECTING) ||
      (p_lcb->link_state == LST_CONNECT_HOLDING) ||
      (p_lcb->link_state == LST_DISCONNECTING)) {
    p_lcb->p_pending_ccb = NULL;

    /* For all channels, send a disconnect indication event through */
    /* their FSMs. The CCBs should remove themselves from the LCB   */
    for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;) {
      tL2C_CCB* pn = p_ccb->p_next_ccb;

      l2c_csm_execute(p_ccb, L2CEVT_LP_DISCONNECT_IND, NULL);

      p_ccb = pn;
    }

    /* Release the LCB */
    l2cu_release_lcb(p_lcb);
  }

  /* If link is connected, check for inactivity timeout */
  if (p_lcb->link_state == LST_CONNECTED) {
    /* If no channels in use, drop the link. */
    if (!p_lcb->ccb_queue.p_first_ccb) {
      uint64_t timeout_ms;
      bool start_timeout = true;

      log::warn("TODO: Remove this callback into bcm_sec_disconnect");
      rc = btm_sec_disconnect(
          p_lcb->Handle(), HCI_ERR_PEER_USER,
          "stack::l2cap::l2c_link::l2c_link_timeout All channels closed");

      if (rc == BTM_CMD_STORED) {
        /* Security Manager will take care of disconnecting, state will be
         * updated at that time */
        start_timeout = false;
      } else if (rc == BTM_CMD_STARTED) {
        p_lcb->link_state = LST_DISCONNECTING;
        timeout_ms = L2CAP_LINK_DISCONNECT_TIMEOUT_MS;
      } else if (rc == BTM_SUCCESS) {
        l2cu_process_fixed_disc_cback(p_lcb);
        /* BTM SEC will make sure that link is release (probably after pairing
         * is done) */
        p_lcb->link_state = LST_DISCONNECTING;
        start_timeout = false;
      } else if (rc == BTM_BUSY) {
        /* BTM is still executing security process. Let lcb stay as connected */
        start_timeout = false;
      } else if (p_lcb->IsBonding()) {
        acl_disconnect_from_handle(p_lcb->Handle(), HCI_ERR_PEER_USER,
                                   "stack::l2cap::l2c_link::l2c_link_timeout "
                                   "Timer expired while bonding");
        l2cu_process_fixed_disc_cback(p_lcb);
        p_lcb->link_state = LST_DISCONNECTING;
        timeout_ms = L2CAP_LINK_DISCONNECT_TIMEOUT_MS;
      } else {
        /* probably no buffer to send disconnect */
        timeout_ms = BT_1SEC_TIMEOUT_MS;
      }

      if (start_timeout) {
        alarm_set_on_mloop(p_lcb->l2c_lcb_timer, timeout_ms,
                           l2c_lcb_timer_timeout, p_lcb);
      }
    } else {
      /* Check in case we were flow controlled */
      l2c_link_check_send_pkts(p_lcb, 0, NULL);
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_info_resp_timer_timeout
 *
 * Description      This function is called when an info request times out
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_info_resp_timer_timeout(void* data) {
  tL2C_LCB* p_lcb = (tL2C_LCB*)data;
  tL2C_CCB* p_ccb;
  tL2C_CONN_INFO ci;

  /* If we timed out waiting for info response, just continue using basic if
   * allowed */
  if (p_lcb->w4_info_rsp) {
    /* If waiting for security complete, restart the info response timer */
    for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;
         p_ccb = p_ccb->p_next_ccb) {
      if ((p_ccb->chnl_state == CST_ORIG_W4_SEC_COMP) ||
          (p_ccb->chnl_state == CST_TERM_W4_SEC_COMP)) {
        alarm_set_on_mloop(p_lcb->info_resp_timer,
                           L2CAP_WAIT_INFO_RSP_TIMEOUT_MS,
                           l2c_info_resp_timer_timeout, p_lcb);
        return;
      }
    }

    p_lcb->w4_info_rsp = false;

    /* If link is in process of being brought up */
    if ((p_lcb->link_state != LST_DISCONNECTED) &&
        (p_lcb->link_state != LST_DISCONNECTING)) {
      /* Notify active channels that peer info is finished */
      if (p_lcb->ccb_queue.p_first_ccb) {
        ci.status = HCI_SUCCESS;
        ci.bd_addr = p_lcb->remote_bd_addr;

        for (p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;
             p_ccb = p_ccb->p_next_ccb) {
          l2c_csm_execute(p_ccb, L2CEVT_L2CAP_INFO_RSP, &ci);
        }
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_adjust_allocation
 *
 * Description      This function is called when a link is created or removed
 *                  to calculate the amount of packets each link may send to
 *                  the HCI without an ack coming back.
 *
 *                  Currently, this is a simple allocation, dividing the
 *                  number of Controller Packets by the number of links. In
 *                  the future, QOS configuration should be examined.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_adjust_allocation(void) {
  uint16_t qq, yy, qq_remainder;
  tL2C_LCB* p_lcb;
  uint16_t hi_quota, low_quota;
  uint16_t num_lowpri_links = 0;
  uint16_t num_hipri_links = 0;
  uint16_t controller_xmit_quota = l2cb.num_lm_acl_bufs;
  uint16_t high_pri_link_quota = L2CAP_HIGH_PRI_MIN_XMIT_QUOTA_A;
  bool is_share_buffer =
      (l2cb.num_lm_ble_bufs == L2C_DEF_NUM_BLE_BUF_SHARED) ? true : false;

  /* If no links active, reset buffer quotas and controller buffers */
  if (l2cb.num_used_lcbs == 0) {
    l2cb.controller_xmit_window = l2cb.num_lm_acl_bufs;
    l2cb.round_robin_quota = l2cb.round_robin_unacked = 0;
    return;
  }

  /* First, count the links */
  for (yy = 0, p_lcb = &l2cb.lcb_pool[0]; yy < MAX_L2CAP_LINKS; yy++, p_lcb++) {
    if (p_lcb->in_use &&
        (is_share_buffer || p_lcb->transport != BT_TRANSPORT_LE)) {
      if (p_lcb->acl_priority == L2CAP_PRIORITY_HIGH)
        num_hipri_links++;
      else
        num_lowpri_links++;
    }
  }

  /* now adjust high priority link quota */
  low_quota = num_lowpri_links ? 1 : 0;
  while ((num_hipri_links * high_pri_link_quota + low_quota) >
         controller_xmit_quota)
    high_pri_link_quota--;

  /* Work out the xmit quota and buffer quota high and low priorities */
  hi_quota = num_hipri_links * high_pri_link_quota;
  low_quota =
      (hi_quota < controller_xmit_quota) ? controller_xmit_quota - hi_quota : 1;

  /* Work out and save the HCI xmit quota for each low priority link */

  /* If each low priority link cannot have at least one buffer */
  if (num_lowpri_links > low_quota) {
    l2cb.round_robin_quota = low_quota;
    qq = qq_remainder = 1;
  }
  /* If each low priority link can have at least one buffer */
  else if (num_lowpri_links > 0) {
    l2cb.round_robin_quota = 0;
    l2cb.round_robin_unacked = 0;
    qq = low_quota / num_lowpri_links;
    qq_remainder = low_quota % num_lowpri_links;
  }
  /* If no low priority link */
  else {
    l2cb.round_robin_quota = 0;
    l2cb.round_robin_unacked = 0;
    qq = qq_remainder = 1;
  }

  log::debug(
      "l2c_link_adjust_allocation  num_hipri: {}  num_lowpri: {}  low_quota: "
      "{}  round_robin_quota: {}  qq: {}",
      num_hipri_links, num_lowpri_links, low_quota, l2cb.round_robin_quota, qq);

  /* Now, assign the quotas to each link */
  for (yy = 0, p_lcb = &l2cb.lcb_pool[0]; yy < MAX_L2CAP_LINKS; yy++, p_lcb++) {
    if (p_lcb->in_use &&
        (is_share_buffer || p_lcb->transport != BT_TRANSPORT_LE)) {
      if (p_lcb->acl_priority == L2CAP_PRIORITY_HIGH) {
        p_lcb->link_xmit_quota = high_pri_link_quota;
      } else {
        /* Safety check in case we switched to round-robin with something
         * outstanding */
        /* if sent_not_acked is added into round_robin_unacked then don't add it
         * again */
        /* l2cap keeps updating sent_not_acked for exiting from round robin */
        if ((p_lcb->link_xmit_quota > 0) && (qq == 0))
          l2cb.round_robin_unacked += p_lcb->sent_not_acked;

        p_lcb->link_xmit_quota = qq;
        if (qq_remainder > 0) {
          p_lcb->link_xmit_quota++;
          qq_remainder--;
        }
      }

      log::debug(
          "l2c_link_adjust_allocation LCB {}   Priority: {}  XmitQuota: {}", yy,
          p_lcb->acl_priority, p_lcb->link_xmit_quota);

      log::debug("SentNotAcked: {}  RRUnacked: {}", p_lcb->sent_not_acked,
                 l2cb.round_robin_unacked);

      /* There is a special case where we have readjusted the link quotas and */
      /* this link may have sent anything but some other link sent packets so */
      /* so we may need a timer to kick off this link's transmissions. */
      if ((p_lcb->link_state == LST_CONNECTED) &&
          (!list_is_empty(p_lcb->link_xmit_data_q)) &&
          (p_lcb->sent_not_acked < p_lcb->link_xmit_quota)) {
        alarm_set_on_mloop(p_lcb->l2c_lcb_timer,
                           L2CAP_LINK_FLOW_CONTROL_TIMEOUT_MS,
                           l2c_lcb_timer_timeout, p_lcb);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_adjust_chnl_allocation
 *
 * Description      This function is called to calculate the amount of packets
 *                  each non-F&EC channel may have outstanding.
 *
 *                  Currently, this is a simple allocation, dividing the number
 *                  of packets allocated to the link by the number of channels.
 *                  In the future, QOS configuration should be examined.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_adjust_chnl_allocation(void) {
  /* assign buffer quota to each channel based on its data rate requirement */
  for (uint8_t xx = 0; xx < MAX_L2CAP_CHANNELS; xx++) {
    tL2C_CCB* p_ccb = l2cb.ccb_pool + xx;

    if (!p_ccb->in_use) continue;

    tL2CAP_CHNL_DATA_RATE data_rate = p_ccb->tx_data_rate + p_ccb->rx_data_rate;
    p_ccb->buff_quota = L2CAP_CBB_DEFAULT_DATA_RATE_BUFF_QUOTA * data_rate;
    log::debug(
        "CID:0x{:04x} FCR Mode:{} Priority:{} TxDataRate:{} RxDataRate:{} "
        "Quota:{}",
        p_ccb->local_cid, p_ccb->peer_cfg.fcr.mode, p_ccb->ccb_priority,
        p_ccb->tx_data_rate, p_ccb->rx_data_rate, p_ccb->buff_quota);

    /* quota may be change so check congestion */
    l2cu_check_channel_congestion(p_ccb);
  }
}

void l2c_link_init(const uint16_t acl_buffer_count_classic) {
  l2cb.num_lm_acl_bufs = acl_buffer_count_classic;
  l2cb.controller_xmit_window = acl_buffer_count_classic;
}

/*******************************************************************************
 *
 * Function         l2c_link_role_changed
 *
 * Description      This function is called whan a link's central/peripheral
 *role change event is received. It simply updates the link control block.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_role_changed(const RawAddress* bd_addr, uint8_t new_role,
                           uint8_t hci_status) {
  /* Make sure not called from HCI Command Status (bd_addr and new_role are
   * invalid) */
  if (bd_addr != nullptr) {
    /* If here came form hci role change event */
    tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(*bd_addr, BT_TRANSPORT_BR_EDR);
    if (p_lcb) {
      if (new_role == HCI_ROLE_CENTRAL) {
        p_lcb->SetLinkRoleAsCentral();
      } else {
        p_lcb->SetLinkRoleAsPeripheral();
      }

      /* Reset high priority link if needed */
      if (hci_status == HCI_SUCCESS)
        l2cu_set_acl_priority(*bd_addr, p_lcb->acl_priority, true);
    }
  }

  /* Check if any LCB was waiting for switch to be completed */
  tL2C_LCB* p_lcb = &l2cb.lcb_pool[0];
  for (uint8_t xx = 0; xx < MAX_L2CAP_LINKS; xx++, p_lcb++) {
    if ((p_lcb->in_use) && (p_lcb->link_state == LST_CONNECTING_WAIT_SWITCH)) {
      l2cu_create_conn_after_switch(p_lcb);
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_pin_code_request
 *
 * Description      This function is called whan a pin-code request is received
 *                  on a connection. If there are no channels active yet on the
 *                  link, it extends the link first connection timer.  Make sure
 *                  that inactivity timer is not extended if PIN code happens
 *                  to be after last ccb released.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_pin_code_request(const RawAddress& bd_addr) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(bd_addr, BT_TRANSPORT_BR_EDR);

  if ((p_lcb) && (!p_lcb->ccb_queue.p_first_ccb)) {
    alarm_set_on_mloop(p_lcb->l2c_lcb_timer, L2CAP_LINK_CONNECT_EXT_TIMEOUT_MS,
                       l2c_lcb_timer_timeout, p_lcb);
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_check_power_mode
 *
 * Description      This function is called to check power mode.
 *
 * Returns          true if link is going to be active from park
 *                  false if nothing to send or not in park mode
 *
 ******************************************************************************/
static bool l2c_link_check_power_mode(tL2C_LCB* p_lcb) {
  bool need_to_active = false;

  // Return false as LM modes are applicable for BREDR transport
  if (p_lcb->is_transport_ble()) return false;
  /*
   * We only switch park to active only if we have unsent packets
   */
  if (list_is_empty(p_lcb->link_xmit_data_q)) {
    for (tL2C_CCB* p_ccb = p_lcb->ccb_queue.p_first_ccb; p_ccb;
         p_ccb = p_ccb->p_next_ccb) {
      if (!fixed_queue_is_empty(p_ccb->xmit_hold_q)) {
        need_to_active = true;
        break;
      }
    }
  } else {
    need_to_active = true;
  }

  /* if we have packets to send */
  if (need_to_active) {
    /* check power mode */
    tBTM_PM_MODE mode;
    if (BTM_ReadPowerMode(p_lcb->remote_bd_addr, &mode)) {
      if (mode == BTM_PM_STS_PENDING) {
        log::debug("LCB(0x{:x}) is in PM pending state", p_lcb->Handle());
        return true;
      }
    }
  }
  return false;
}

/*******************************************************************************
 *
 * Function         l2c_link_check_send_pkts
 *
 * Description      This function is called to check if it can send packets
 *                  to the Host Controller. It may be passed the address of
 *                  a packet to send.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_check_send_pkts(tL2C_LCB* p_lcb, uint16_t local_cid,
                              BT_HDR* p_buf) {
  bool single_write = false;

  /* Save the channel ID for faster counting */
  if (p_buf) {
    p_buf->event = local_cid;
    if (local_cid != 0) {
      single_write = true;
    }

    p_buf->layer_specific = 0;
    list_append(p_lcb->link_xmit_data_q, p_buf);

    if (p_lcb->link_xmit_quota == 0) {
      if (p_lcb->transport == BT_TRANSPORT_LE)
        l2cb.ble_check_round_robin = true;
      else
        l2cb.check_round_robin = true;
    }
  }

  /* If this is called from uncongested callback context break recursive
   *calling.
   ** This LCB will be served when receiving number of completed packet event.
   */
  if (l2cb.is_cong_cback_context) {
    log::warn("skipping, is_cong_cback_context=true");
    return;
  }

  /* If we are in a scenario where there are not enough buffers for each link to
  ** have at least 1, then do a round-robin for all the LCBs
  */
  if ((p_lcb == NULL) || (p_lcb->link_xmit_quota == 0)) {
    log::debug("Round robin");
    if (p_lcb == NULL) {
      p_lcb = l2cb.lcb_pool;
    } else if (!single_write) {
      p_lcb++;
    }

    /* Loop through, starting at the next */
    for (int xx = 0; xx < MAX_L2CAP_LINKS; xx++, p_lcb++) {
      /* Check for wraparound */
      if (p_lcb == &l2cb.lcb_pool[MAX_L2CAP_LINKS]) p_lcb = &l2cb.lcb_pool[0];

      /* If controller window is full, nothing to do */
      if (((l2cb.controller_xmit_window == 0 ||
            (l2cb.round_robin_unacked >= l2cb.round_robin_quota)) &&
           (p_lcb->transport == BT_TRANSPORT_BR_EDR)) ||
          (p_lcb->transport == BT_TRANSPORT_LE &&
           (l2cb.ble_round_robin_unacked >= l2cb.ble_round_robin_quota ||
            l2cb.controller_le_xmit_window == 0))) {
        log::debug("Skipping lcb {} due to controller window full", xx);
        continue;
      }

      if ((!p_lcb->in_use) || (p_lcb->link_state != LST_CONNECTED) ||
          (p_lcb->link_xmit_quota != 0) || (l2c_link_check_power_mode(p_lcb))) {
        log::debug("Skipping lcb {} due to quota", xx);
        continue;
      }

      /* See if we can send anything from the Link Queue */
      if (!list_is_empty(p_lcb->link_xmit_data_q)) {
        log::verbose("Sending to lower layer");
        p_buf = (BT_HDR*)list_front(p_lcb->link_xmit_data_q);
        list_remove(p_lcb->link_xmit_data_q, p_buf);
        l2c_link_send_to_lower(p_lcb, p_buf, NULL);
      } else if (single_write) {
        /* If only doing one write, break out */
        log::debug("single_write is true, skipping");
        break;
      }
      /* If nothing on the link queue, check the channel queue */
      else {
        tL2C_TX_COMPLETE_CB_INFO cbi = {};
        log::debug("Check next buffer");
        p_buf = l2cu_get_next_buffer_to_send(p_lcb, &cbi);
        if (p_buf != NULL) {
          log::debug("Sending next buffer");
          l2c_link_send_to_lower(p_lcb, p_buf, &cbi);
        }
      }
    }

    /* If we finished without using up our quota, no need for a safety check */
    if ((l2cb.controller_xmit_window > 0) &&
        (l2cb.round_robin_unacked < l2cb.round_robin_quota) &&
        (p_lcb->transport == BT_TRANSPORT_BR_EDR))
      l2cb.check_round_robin = false;

    if ((l2cb.controller_le_xmit_window > 0) &&
        (l2cb.ble_round_robin_unacked < l2cb.ble_round_robin_quota) &&
        (p_lcb->transport == BT_TRANSPORT_LE))
      l2cb.ble_check_round_robin = false;
  } else /* if this is not round-robin service */
  {
    /* link_state or power mode not ready, can't send anything else */
    if ((p_lcb->link_state != LST_CONNECTED) ||
        (l2c_link_check_power_mode(p_lcb))) {
      log::warn("Can't send, link state: {} not LST_CONNECTED or power mode BTM_PM_STS_PENDING",
                p_lcb->link_state);
      return;
    }
    log::verbose(
        "Direct send, transport={}, xmit_window={}, le_xmit_window={}, "
        "sent_not_acked={}, link_xmit_quota={}",
        p_lcb->transport, l2cb.controller_xmit_window,
        l2cb.controller_le_xmit_window, p_lcb->sent_not_acked,
        p_lcb->link_xmit_quota);

    /* See if we can send anything from the link queue */
    while (((l2cb.controller_xmit_window != 0 &&
             (p_lcb->transport == BT_TRANSPORT_BR_EDR)) ||
            (l2cb.controller_le_xmit_window != 0 &&
             (p_lcb->transport == BT_TRANSPORT_LE))) &&
           (p_lcb->sent_not_acked < p_lcb->link_xmit_quota)) {
      if (list_is_empty(p_lcb->link_xmit_data_q)) {
        log::verbose("No transmit data, skipping");
        break;
      }
      log::verbose("Sending to lower layer");
      p_buf = (BT_HDR*)list_front(p_lcb->link_xmit_data_q);
      list_remove(p_lcb->link_xmit_data_q, p_buf);
      l2c_link_send_to_lower(p_lcb, p_buf, NULL);
    }

    if (!single_write) {
      /* See if we can send anything for any channel */
      log::verbose("Trying to send other data when single_write is false");
      while (((l2cb.controller_xmit_window != 0 &&
               (p_lcb->transport == BT_TRANSPORT_BR_EDR)) ||
              (l2cb.controller_le_xmit_window != 0 &&
               (p_lcb->transport == BT_TRANSPORT_LE))) &&
             (p_lcb->sent_not_acked < p_lcb->link_xmit_quota)) {
        tL2C_TX_COMPLETE_CB_INFO cbi = {};
        p_buf = l2cu_get_next_buffer_to_send(p_lcb, &cbi);
        if (p_buf == NULL) {
          log::verbose("No next buffer, skipping");
          break;
        }
        log::verbose("Sending to lower layer");
        l2c_link_send_to_lower(p_lcb, p_buf, &cbi);
      }
    }

    /* There is a special case where we have readjusted the link quotas and  */
    /* this link may have sent anything but some other link sent packets so  */
    /* so we may need a timer to kick off this link's transmissions.         */
    if ((!list_is_empty(p_lcb->link_xmit_data_q)) &&
        (p_lcb->sent_not_acked < p_lcb->link_xmit_quota)) {
      alarm_set_on_mloop(p_lcb->l2c_lcb_timer,
                         L2CAP_LINK_FLOW_CONTROL_TIMEOUT_MS,
                         l2c_lcb_timer_timeout, p_lcb);
    }
  }
}

void l2c_OnHciModeChangeSendPendingPackets(RawAddress remote) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(remote, BT_TRANSPORT_BR_EDR);
  if (p_lcb != NULL) {
    /* There might be any pending packets due to SNIFF or PENDING state */
    /* Trigger L2C to start transmission of the pending packets. */
    log::verbose(
        "btm mode change to active; check l2c_link for outgoing packets");
    l2c_link_check_send_pkts(p_lcb, 0, NULL);
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_send_to_lower
 *
 * Description      This function queues the buffer for HCI transmission
 *
 ******************************************************************************/
static void l2c_link_send_to_lower_br_edr(tL2C_LCB* p_lcb, BT_HDR* p_buf) {
  const uint16_t link_xmit_quota = p_lcb->link_xmit_quota;

  if (link_xmit_quota == 0) {
    l2cb.round_robin_unacked++;
  }
  p_lcb->sent_not_acked++;
  p_buf->layer_specific = 0;
  l2cb.controller_xmit_window--;

  acl_send_data_packet_br_edr(p_lcb->remote_bd_addr, p_buf);
  log::verbose(
      "TotalWin={},Hndl=0x{:x},Quota={},Unack={},RRQuota={},RRUnack={}",
      l2cb.controller_xmit_window, p_lcb->Handle(), p_lcb->link_xmit_quota,
      p_lcb->sent_not_acked, l2cb.round_robin_quota, l2cb.round_robin_unacked);
}

static void l2c_link_send_to_lower_ble(tL2C_LCB* p_lcb, BT_HDR* p_buf) {
  const uint16_t link_xmit_quota = p_lcb->link_xmit_quota;

  if (link_xmit_quota == 0) {
    l2cb.ble_round_robin_unacked++;
  }
  p_lcb->sent_not_acked++;
  p_buf->layer_specific = 0;
  l2cb.controller_le_xmit_window--;

  acl_send_data_packet_ble(p_lcb->remote_bd_addr, p_buf);
  log::debug("TotalWin={},Hndl=0x{:x},Quota={},Unack={},RRQuota={},RRUnack={}",
             l2cb.controller_le_xmit_window, p_lcb->Handle(),
             p_lcb->link_xmit_quota, p_lcb->sent_not_acked,
             l2cb.ble_round_robin_quota, l2cb.ble_round_robin_unacked);
}

static void l2c_link_send_to_lower(tL2C_LCB* p_lcb, BT_HDR* p_buf,
                                   tL2C_TX_COMPLETE_CB_INFO* p_cbi) {
  if (p_lcb->transport == BT_TRANSPORT_BR_EDR) {
    l2c_link_send_to_lower_br_edr(p_lcb, p_buf);
  } else {
    l2c_link_send_to_lower_ble(p_lcb, p_buf);
  }
  if (p_cbi) l2cu_tx_complete(p_cbi);
}

void l2c_packets_completed(uint16_t handle, uint16_t num_sent) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);
  if (p_lcb == nullptr) {
    return;
  }
  p_lcb->update_outstanding_packets(num_sent);

  switch (p_lcb->transport) {
    case BT_TRANSPORT_BR_EDR:
      l2cb.controller_xmit_window += num_sent;
      if (p_lcb->is_round_robin_scheduling())
        l2cb.update_outstanding_classic_packets(num_sent);
      break;
    case BT_TRANSPORT_LE:
      l2cb.controller_le_xmit_window += num_sent;
      if (p_lcb->is_round_robin_scheduling())
        l2cb.update_outstanding_le_packets(num_sent);
      break;
    default:
      log::error("Unknown transport received:{}", p_lcb->transport);
      return;
  }

  l2c_link_check_send_pkts(p_lcb, 0, NULL);

  if (p_lcb->is_high_priority()) {
    switch (p_lcb->transport) {
      case BT_TRANSPORT_LE:
        if (l2cb.ble_check_round_robin &&
            l2cb.is_ble_round_robin_quota_available())
          l2c_link_check_send_pkts(NULL, 0, NULL);
        break;
      case BT_TRANSPORT_BR_EDR:
        if (l2cb.check_round_robin &&
            l2cb.is_classic_round_robin_quota_available()) {
          l2c_link_check_send_pkts(NULL, 0, NULL);
        }
        break;
      default:
        break;
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_link_segments_xmitted
 *
 * Description      This function is called from the HCI Interface when an ACL
 *                  data packet segment is transmitted.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_link_segments_xmitted(BT_HDR* p_msg) {
  uint8_t* p = (uint8_t*)(p_msg + 1) + p_msg->offset;

  /* Extract the handle */
  uint16_t handle{HCI_INVALID_HANDLE};
  STREAM_TO_UINT16(handle, p);
  handle = HCID_GET_HANDLE(handle);

  /* Find the LCB based on the handle */
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);
  if (p_lcb == nullptr) {
    log::warn("Received segment complete for unknown connection handle:{}",
              handle);
    osi_free(p_msg);
    return;
  }

  if (p_lcb->link_state != LST_CONNECTED) {
    log::info("Received segment complete for unconnected connection handle:{}:",
              handle);
    osi_free(p_msg);
    return;
  }

  /* Enqueue the buffer to the head of the transmit queue, and see */
  /* if we can transmit anything more.                             */
  list_prepend(p_lcb->link_xmit_data_q, p_msg);

  l2c_link_check_send_pkts(p_lcb, 0, NULL);
}

tBTM_STATUS l2cu_ConnectAclForSecurity(const RawAddress& bd_addr) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_lcb && (p_lcb->link_state == LST_CONNECTED ||
                p_lcb->link_state == LST_CONNECTING)) {
    log::warn("Connection already exists");
    return BTM_CMD_STARTED;
  }

  /* Make sure an L2cap link control block is available */
  if (!p_lcb &&
      (p_lcb = l2cu_allocate_lcb(bd_addr, true, BT_TRANSPORT_BR_EDR)) == NULL) {
    log::warn("failed allocate LCB for {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return BTM_NO_RESOURCES;
  }

  l2cu_create_conn_br_edr(p_lcb);
  return BTM_SUCCESS;
}

void l2cble_update_sec_act(const RawAddress& bd_addr, uint16_t sec_act) {
  tL2C_LCB* lcb = l2cu_find_lcb_by_bd_addr(bd_addr, BT_TRANSPORT_LE);
  lcb->sec_act = sec_act;
}

/******************************************************************************
 *
 * Function         l2cu_get_next_channel_in_rr
 *
 * Description      get the next channel to send on a link. It also adjusts the
 *                  CCB queue to do a basic priority and round-robin scheduling.
 *
 * Returns          pointer to CCB or NULL
 *
 ******************************************************************************/
tL2C_CCB* l2cu_get_next_channel_in_rr(tL2C_LCB* p_lcb) {
  tL2C_CCB* p_serve_ccb = NULL;
  tL2C_CCB* p_ccb;

  int i, j;

  /* scan all of priority until finding a channel to serve */
  for (i = 0; (i < L2CAP_NUM_CHNL_PRIORITY) && (!p_serve_ccb); i++) {
    /* scan all channel within serving priority group until finding a channel to
     * serve */
    for (j = 0; (j < p_lcb->rr_serv[p_lcb->rr_pri].num_ccb) && (!p_serve_ccb);
         j++) {
      /* scaning from next serving channel */
      p_ccb = p_lcb->rr_serv[p_lcb->rr_pri].p_serve_ccb;

      if (!p_ccb) {
        log::error("p_serve_ccb is NULL, rr_pri={}", p_lcb->rr_pri);
        return NULL;
      }

      log::verbose("RR scan pri={}, lcid=0x{:04x}, q_cout={}",
                   p_ccb->ccb_priority, p_ccb->local_cid,
                   fixed_queue_length(p_ccb->xmit_hold_q));

      /* store the next serving channel */
      /* this channel is the last channel of its priority group */
      if ((p_ccb->p_next_ccb == NULL) ||
          (p_ccb->p_next_ccb->ccb_priority != p_ccb->ccb_priority)) {
        /* next serving channel is set to the first channel in the group */
        p_lcb->rr_serv[p_lcb->rr_pri].p_serve_ccb =
            p_lcb->rr_serv[p_lcb->rr_pri].p_first_ccb;
      } else {
        /* next serving channel is set to the next channel in the group */
        p_lcb->rr_serv[p_lcb->rr_pri].p_serve_ccb = p_ccb->p_next_ccb;
      }

      if (p_ccb->chnl_state != CST_OPEN) continue;

      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        log::debug("Connection oriented channel");
        if (fixed_queue_is_empty(p_ccb->xmit_hold_q)) continue;

      } else {
        /* eL2CAP option in use */
        if (p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE) {
          if (p_ccb->fcrb.wait_ack || p_ccb->fcrb.remote_busy) continue;

          if (fixed_queue_is_empty(p_ccb->fcrb.retrans_q)) {
            if (fixed_queue_is_empty(p_ccb->xmit_hold_q)) continue;

            /* If in eRTM mode, check for window closure */
            if ((p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) &&
                (l2c_fcr_is_flow_controlled(p_ccb)))
              continue;
          }
        } else {
          if (fixed_queue_is_empty(p_ccb->xmit_hold_q)) continue;
        }
      }

      /* found a channel to serve */
      p_serve_ccb = p_ccb;
      /* decrease quota of its priority group */
      p_lcb->rr_serv[p_lcb->rr_pri].quota--;
    }

    /* if there is no more quota of the priority group or no channel to have
     * data to send */
    if ((p_lcb->rr_serv[p_lcb->rr_pri].quota == 0) || (!p_serve_ccb)) {
      /* serve next priority group */
      p_lcb->rr_pri = (p_lcb->rr_pri + 1) % L2CAP_NUM_CHNL_PRIORITY;
      /* initialize its quota */
      p_lcb->rr_serv[p_lcb->rr_pri].quota =
          L2CAP_GET_PRIORITY_QUOTA(p_lcb->rr_pri);
    }
  }

  if (p_serve_ccb) {
    log::verbose("RR service pri={}, quota={}, lcid=0x{:04x}",
                 p_serve_ccb->ccb_priority,
                 p_lcb->rr_serv[p_serve_ccb->ccb_priority].quota,
                 p_serve_ccb->local_cid);
  }

  return p_serve_ccb;
}

/******************************************************************************
 *
 * Function         l2cu_get_next_buffer_to_send
 *
 * Description      get the next buffer to send on a link. It also adjusts the
 *                  CCB queue to do a basic priority and round-robin scheduling.
 *
 * Returns          pointer to buffer or NULL
 *
 ******************************************************************************/
BT_HDR* l2cu_get_next_buffer_to_send(tL2C_LCB* p_lcb,
                                     tL2C_TX_COMPLETE_CB_INFO* p_cbi) {
  tL2C_CCB* p_ccb;
  BT_HDR* p_buf;

  /* Highest priority are fixed channels */
  int xx;

  p_cbi->cb = NULL;

  for (xx = 0; xx < L2CAP_NUM_FIXED_CHNLS; xx++) {
    p_ccb = p_lcb->p_fixed_ccbs[xx];
    if (p_ccb == NULL) continue;

    /* eL2CAP option in use */
    if (p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE) {
      if (p_ccb->fcrb.wait_ack || p_ccb->fcrb.remote_busy) continue;

      /* No more checks needed if sending from the reatransmit queue */
      if (fixed_queue_is_empty(p_ccb->fcrb.retrans_q)) {
        if (fixed_queue_is_empty(p_ccb->xmit_hold_q)) continue;

        /* If in eRTM mode, check for window closure */
        if ((p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) &&
            (l2c_fcr_is_flow_controlled(p_ccb)))
          continue;
      }

      p_buf = l2c_fcr_get_next_xmit_sdu_seg(p_ccb, 0);
      if (p_buf != NULL) {
        l2cu_check_channel_congestion(p_ccb);
        l2cu_set_acl_hci_header(p_buf, p_ccb);
        return (p_buf);
      }
    } else {
      if (!fixed_queue_is_empty(p_ccb->xmit_hold_q)) {
        p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_ccb->xmit_hold_q);
        if (NULL == p_buf) {
          log::error("No data to be sent");
          return (NULL);
        }

        /* Prepare callback info for TX completion */
        p_cbi->cb = l2cb.fixed_reg[xx].pL2CA_FixedTxComplete_Cb;
        p_cbi->local_cid = p_ccb->local_cid;
        p_cbi->num_sdu = 1;

        l2cu_check_channel_congestion(p_ccb);
        l2cu_set_acl_hci_header(p_buf, p_ccb);
        return (p_buf);
      }
    }
  }

  /* get next serving channel in round-robin */
  p_ccb = l2cu_get_next_channel_in_rr(p_lcb);

  /* Return if no buffer */
  if (p_ccb == NULL) return (NULL);

  if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
    /* Check credits */
    if (p_ccb->peer_conn_cfg.credits == 0) {
      log::debug("No credits to send packets");
      return NULL;
    }

    bool last_piece_of_sdu = false;
    p_buf = l2c_lcc_get_next_xmit_sdu_seg(p_ccb, &last_piece_of_sdu);
    p_ccb->peer_conn_cfg.credits--;

    if (last_piece_of_sdu) {
      // TODO: send callback up the stack. Investigate setting p_cbi->cb to
      // notify after controller ack send.
    }

  } else {
    if (p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE) {
      p_buf = l2c_fcr_get_next_xmit_sdu_seg(p_ccb, 0);
      if (p_buf == NULL) return (NULL);
    } else {
      p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_ccb->xmit_hold_q);
      if (NULL == p_buf) {
        log::error("#2: No data to be sent");
        return (NULL);
      }
    }
  }

  if (p_ccb->p_rcb && p_ccb->p_rcb->api.pL2CA_TxComplete_Cb &&
      (p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_ERTM_MODE))
    (*p_ccb->p_rcb->api.pL2CA_TxComplete_Cb)(p_ccb->local_cid, 1);

  l2cu_check_channel_congestion(p_ccb);

  l2cu_set_acl_hci_header(p_buf, p_ccb);

  return (p_buf);
}
