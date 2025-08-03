/*
 * WPA Supplicant - Aidl entry point to wpa_supplicant core
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include <android/binder_process.h>
#include <android/binder_manager.h>

#include "aidl_manager.h"

extern "C"
{
#include "aidl.h"
#include "aidl_i.h"
#include "utils/common.h"
#include "utils/eloop.h"
#include "utils/includes.h"
#include "dpp.h"
}

using aidl::android::hardware::wifi::supplicant::AidlManager;
using aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode;
using aidl::android::hardware::wifi::supplicant::DppEventType;
using aidl::android::hardware::wifi::supplicant::DppFailureCode;
using aidl::android::hardware::wifi::supplicant::DppProgressCode;

static void wpas_aidl_notify_dpp_failure(struct wpa_supplicant *wpa_s, DppFailureCode code);
static void wpas_aidl_notify_dpp_progress(struct wpa_supplicant *wpa_s, DppProgressCode code);
static void wpas_aidl_notify_dpp_success(struct wpa_supplicant *wpa_s, DppEventType code);

void wpas_aidl_sock_handler(
	int /* sock */, void * /* eloop_ctx */, void * /* sock_ctx */)
{
	ABinderProcess_handlePolledCommands();
}

struct wpas_aidl_priv *wpas_aidl_init(struct wpa_global *global)
{
	struct wpas_aidl_priv *priv;
	AidlManager *aidl_manager;

	priv = (wpas_aidl_priv *)os_zalloc(sizeof(*priv));
	if (!priv)
		return NULL;
	priv->global = global;

	wpa_printf(MSG_DEBUG, "Initing aidl control");

	ABinderProcess_setupPolling(&priv->aidl_fd);
	if (priv->aidl_fd < 0)
		goto err;

	wpa_printf(MSG_INFO, "Processing aidl events on FD %d", priv->aidl_fd);
	// Look for read events from the aidl socket in the eloop.
	if (eloop_register_read_sock(
		priv->aidl_fd, wpas_aidl_sock_handler, global, priv) < 0)
		goto err;

	aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		goto err;
	if (aidl_manager->registerAidlService(global)) {
		goto err;
	}
	// We may not need to store this aidl manager reference in the
	// global data strucure because we've made it a singleton class.
	priv->aidl_manager = (void *)aidl_manager;

	return priv;
err:
	wpas_aidl_deinit(priv);
	return NULL;
}

void wpas_aidl_deinit(struct wpas_aidl_priv *priv)
{
	if (!priv)
		return;

	wpa_printf(MSG_DEBUG, "Deiniting aidl control");

	AidlManager::destroyInstance();
	eloop_unregister_read_sock(priv->aidl_fd);
	os_free(priv);
}

int wpas_aidl_register_interface(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s || !wpa_s->global->aidl)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Registering interface to aidl control: %s",
		wpa_s->ifname);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->registerInterface(wpa_s);
}

int wpas_aidl_unregister_interface(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s || !wpa_s->global->aidl)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Deregistering interface from aidl control: %s",
		wpa_s->ifname);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->unregisterInterface(wpa_s);
}

int wpas_aidl_register_network(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid)
{
	if (!wpa_s || !wpa_s->global->aidl || !ssid)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Registering network to aidl control: %d", ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->registerNetwork(wpa_s, ssid);
}

int wpas_aidl_unregister_network(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid)
{
	if (!wpa_s || !wpa_s->global->aidl || !ssid)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Deregistering network from aidl control: %d", ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->unregisterNetwork(wpa_s, ssid);
}

int wpas_aidl_notify_state_changed(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s || !wpa_s->global->aidl)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Notifying state change event to aidl control: %d",
		wpa_s->wpa_state);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->notifyStateChange(wpa_s);
}

int wpas_aidl_notify_network_request(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid,
	enum wpa_ctrl_req_type rtype, const char *default_txt)
{
	if (!wpa_s || !wpa_s->global->aidl || !ssid)
		return 1;

	wpa_printf(
		MSG_DEBUG, "Notifying network request to aidl control: %d",
		ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return 1;

	return aidl_manager->notifyNetworkRequest(
		wpa_s, ssid, rtype, default_txt);
}

void wpas_aidl_notify_permanent_id_req_denied(
		struct wpa_supplicant *wpa_s)
{
	if (!wpa_s || !wpa_s->global->aidl)
		return;

	wpa_printf(MSG_DEBUG, "Notifying permanent_id_req denied to aidl control.");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	return aidl_manager->notifyPermanentIdReqDenied(wpa_s);
}

void wpas_aidl_notify_anqp_query_done(
	struct wpa_supplicant *wpa_s, const u8 *bssid, const char *result,
	const struct wpa_bss_anqp *anqp)
{
	if (!wpa_s || !wpa_s->global->aidl || !bssid || !result || !anqp)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying ANQP query done to aidl control: " MACSTR "result: %s",
		MAC2STR(bssid), result);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyAnqpQueryDone(wpa_s, bssid, result, anqp);
}

void wpas_aidl_notify_hs20_icon_query_done(
	struct wpa_supplicant *wpa_s, const u8 *bssid, const char *file_name,
	const u8 *image, u32 image_length)
{
	if (!wpa_s || !wpa_s->global->aidl || !bssid || !file_name || !image)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying HS20 icon query done to aidl control: " MACSTR
		"file_name: %s",
		MAC2STR(bssid), file_name);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyHs20IconQueryDone(
		wpa_s, bssid, file_name, image, image_length);
}

void wpas_aidl_notify_hs20_rx_subscription_remediation(
	struct wpa_supplicant *wpa_s, const char *url, u8 osu_method)
{
	if (!wpa_s || !wpa_s->global->aidl || !url)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying HS20 subscription remediation rx to aidl control: %s",
		url);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyHs20RxSubscriptionRemediation(
		wpa_s, url, osu_method);
}

void wpas_aidl_notify_hs20_rx_deauth_imminent_notice(
	struct wpa_supplicant *wpa_s, u8 code, u16 reauth_delay, const char *url)
{
	if (!wpa_s || !wpa_s->global->aidl)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying HS20 deauth imminent notice rx to aidl control: %s",
		url ? url : "<no URL>");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyHs20RxDeauthImminentNotice(
		wpa_s, code, reauth_delay, url);
}

void wpas_aidl_notify_hs20_rx_terms_and_conditions_acceptance(
		struct wpa_supplicant *wpa_s, const char *url)
{
	if (!wpa_s || !wpa_s->global->aidl || !url)
		return;

	wpa_printf(MSG_DEBUG,
			"Notifying HS20 terms and conditions acceptance rx to aidl control: %s",
			url);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyHs20RxTermsAndConditionsAcceptance(wpa_s, url);
}

void wpas_aidl_notify_disconnect_reason(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying disconnect reason to aidl control: %d",
		wpa_s->disconnect_reason);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDisconnectReason(wpa_s);
}

void wpas_aidl_notify_mlo_info_change_reason(struct wpa_supplicant *wpa_s,
					     enum mlo_info_change_reason reason)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying MLO info change reason to aidl control: %d",
		   reason);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyMloLinksInfoChanged(wpa_s, reason);
}

void wpas_aidl_notify_assoc_reject(struct wpa_supplicant *wpa_s,
	const u8 *bssid, u8 timed_out, const u8 *assoc_resp_ie, size_t assoc_resp_ie_len)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying assoc reject to aidl control: %d",
		wpa_s->assoc_status_code);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyAssocReject(wpa_s, bssid, timed_out, assoc_resp_ie, assoc_resp_ie_len);
}

void wpas_aidl_notify_auth_timeout(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying auth timeout to aidl control");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyAuthTimeout(wpa_s);
}

void wpas_aidl_notify_bssid_changed(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying bssid changed to aidl control");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyBssidChanged(wpa_s);
}

void wpas_aidl_notify_wps_event_fail(
	struct wpa_supplicant *wpa_s, uint8_t *peer_macaddr, uint16_t config_error,
	uint16_t error_indication)
{
	if (!wpa_s || !peer_macaddr)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying Wps event fail to aidl control: %d, %d",
		config_error, error_indication);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyWpsEventFail(
		wpa_s, peer_macaddr, config_error, error_indication);
}

void wpas_aidl_notify_wps_event_success(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying Wps event success to aidl control");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyWpsEventSuccess(wpa_s);
}

void wpas_aidl_notify_wps_event_pbc_overlap(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying Wps event PBC overlap to aidl control");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyWpsEventPbcOverlap(wpa_s);
}

void wpas_aidl_notify_p2p_device_found(
	struct wpa_supplicant *wpa_s, const u8 *addr,
	const struct p2p_peer_info *info, const u8 *peer_wfd_device_info,
	u8 peer_wfd_device_info_len, const u8 *peer_wfd_r2_device_info,
	u8 peer_wfd_r2_device_info_len)
{
	if (!wpa_s || !addr || !info)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying P2P device found to aidl control " MACSTR,
		MAC2STR(info->p2p_device_addr));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pDeviceFound(
		wpa_s, addr, info, peer_wfd_device_info,
		peer_wfd_device_info_len, peer_wfd_r2_device_info,
		peer_wfd_r2_device_info_len);
}

void wpas_aidl_notify_p2p_device_lost(
	struct wpa_supplicant *wpa_s, const u8 *p2p_device_addr)
{
	if (!wpa_s || !p2p_device_addr)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying P2P device lost to aidl control " MACSTR,
		MAC2STR(p2p_device_addr));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pDeviceLost(wpa_s, p2p_device_addr);
}

void wpas_aidl_notify_p2p_find_stopped(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying P2P find stop to aidl control");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pFindStopped(wpa_s);
}

void wpas_aidl_notify_p2p_go_neg_req(
	struct wpa_supplicant *wpa_s, const u8 *src_addr, u16 dev_passwd_id,
	u8 go_intent)
{
	if (!wpa_s || !src_addr)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P GO negotiation request to aidl control " MACSTR,
		MAC2STR(src_addr));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pGoNegReq(
		wpa_s, src_addr, dev_passwd_id, go_intent);
}

void wpas_aidl_notify_p2p_go_neg_completed(
	struct wpa_supplicant *wpa_s, const struct p2p_go_neg_results *res)
{
	if (!wpa_s || !res)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P GO negotiation completed to aidl control: %d",
		res->status);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pGoNegCompleted(wpa_s, res);
}

void wpas_aidl_notify_p2p_group_formation_failure(
	struct wpa_supplicant *wpa_s, const char *reason)
{
	if (!wpa_s || !reason)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P Group formation failure to aidl control: %s",
		reason);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pGroupFormationFailure(wpa_s, reason);
}

void wpas_aidl_notify_p2p_group_started(
	struct wpa_supplicant *wpa_s, const struct wpa_ssid *ssid, int persistent,
	int client, const u8 *ip)
{
	if (!wpa_s || !ssid)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying P2P Group start to aidl control: %d",
		ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pGroupStarted(wpa_s, ssid, persistent, client, ip);
}

void wpas_aidl_notify_p2p_group_removed(
	struct wpa_supplicant *wpa_s, const struct wpa_ssid *ssid, const char *role)
{
	if (!wpa_s || !ssid || !role)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying P2P Group removed to aidl control: %d",
		ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pGroupRemoved(wpa_s, ssid, role);
}

void wpas_aidl_notify_p2p_invitation_received(
	struct wpa_supplicant *wpa_s, const u8 *sa, const u8 *go_dev_addr,
	const u8 *bssid, int id, int op_freq)
{
	if (!wpa_s || !sa || !go_dev_addr || !bssid)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P invitation received to aidl control: %d " MACSTR, id,
		MAC2STR(bssid));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pInvitationReceived(
		wpa_s, sa, go_dev_addr, bssid, id, op_freq);
}

void wpas_aidl_notify_p2p_invitation_result(
	struct wpa_supplicant *wpa_s, int status, const u8 *bssid)
{
	if (!wpa_s)
		return;
	if (bssid) {
		wpa_printf(
			MSG_DEBUG,
			"Notifying P2P invitation result to aidl control: " MACSTR,
			MAC2STR(bssid));
	} else {
		wpa_printf(
			MSG_DEBUG,
			"Notifying P2P invitation result to aidl control: NULL "
			"bssid");
	}

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pInvitationResult(wpa_s, status, bssid);
}

void wpas_aidl_notify_p2p_provision_discovery(
	struct wpa_supplicant *wpa_s, const u8 *dev_addr, int request,
	enum p2p_prov_disc_status status, u16 config_methods,
	unsigned int generated_pin, const char *group_ifname)
{
	if (!wpa_s || !dev_addr)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P provision discovery to aidl control " MACSTR,
		MAC2STR(dev_addr));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pProvisionDiscovery(
		wpa_s, dev_addr, request, status, config_methods,
		generated_pin, group_ifname);
}

void wpas_aidl_notify_p2p_sd_response(
	struct wpa_supplicant *wpa_s, const u8 *sa, u16 update_indic,
	const u8 *tlvs, size_t tlvs_len)
{
	if (!wpa_s || !sa || !tlvs)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P service discovery response to aidl control " MACSTR,
		MAC2STR(sa));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyP2pSdResponse(
		wpa_s, sa, update_indic, tlvs, tlvs_len);
}

void wpas_aidl_notify_ap_sta_authorized(
	struct wpa_supplicant *wpa_s, const u8 *sta, const u8 *p2p_dev_addr,
	const u8 *ip)
{
	if (!wpa_s || !sta)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P AP STA authorized to aidl control " MACSTR,
		MAC2STR(sta));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyApStaAuthorized(wpa_s, sta, p2p_dev_addr, ip);
}

void wpas_aidl_notify_ap_sta_deauthorized(
	struct wpa_supplicant *wpa_s, const u8 *sta, const u8 *p2p_dev_addr)
{
	if (!wpa_s || !sta)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying P2P AP STA deauthorized to aidl control " MACSTR,
		MAC2STR(sta));

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyApStaDeauthorized(wpa_s, sta, p2p_dev_addr);
}

void wpas_aidl_notify_eap_error(struct wpa_supplicant *wpa_s, int error_code)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "Notifying EAP Error: %d ", error_code);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyEapError(wpa_s, error_code);
}

void wpas_aidl_notify_dpp_config_received(struct wpa_supplicant *wpa_s,
		struct wpa_ssid *ssid, bool conn_status_requested)
{
	if (!wpa_s || !ssid)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying DPP configuration received for SSID %d", ssid->id);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppConfigReceived(wpa_s, ssid, conn_status_requested);
}

void wpas_aidl_notify_dpp_config_sent(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_success(wpa_s, DppEventType::CONFIGURATION_SENT);
}

void wpas_aidl_notify_dpp_connection_status_sent(struct wpa_supplicant *wpa_s,
                                                 enum dpp_status_error result)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG, "wpas_aidl_notify_dpp_connection_status_sent %d ", result);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppConnectionStatusSent(wpa_s, result);
}

/* DPP Progress notifications */
void wpas_aidl_notify_dpp_auth_success(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_progress(wpa_s, DppProgressCode::AUTHENTICATION_SUCCESS);
}

void wpas_aidl_notify_dpp_resp_pending(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_progress(wpa_s, DppProgressCode::RESPONSE_PENDING);
}

/* DPP Failure notifications */
void wpas_aidl_notify_dpp_not_compatible(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::NOT_COMPATIBLE);
}

void wpas_aidl_notify_dpp_missing_auth(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::AUTHENTICATION);
}

void wpas_aidl_notify_dpp_configuration_failure(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::CONFIGURATION);
}

void wpas_aidl_notify_dpp_timeout(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::TIMEOUT);
}

void wpas_aidl_notify_dpp_auth_failure(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::AUTHENTICATION);
}

void wpas_aidl_notify_dpp_fail(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::FAILURE);
}

void wpas_aidl_notify_dpp_config_sent_wait_response(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_progress(wpa_s, DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE);
}

/* DPP notification helper functions */
static void wpas_aidl_notify_dpp_failure(struct wpa_supplicant *wpa_s, DppFailureCode code)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying DPP failure event %d", code);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppFailure(wpa_s, code);
}

static void wpas_aidl_notify_dpp_progress(struct wpa_supplicant *wpa_s, DppProgressCode code)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying DPP progress event %d", code);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppProgress(wpa_s, code);
}

void wpas_aidl_notify_dpp_config_accepted(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_progress(wpa_s, DppProgressCode::CONFIGURATION_ACCEPTED);
}

static void wpas_aidl_notify_dpp_config_applied(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_success(wpa_s, DppEventType::CONFIGURATION_APPLIED);
}

static void wpas_aidl_notify_dpp_success(struct wpa_supplicant *wpa_s, DppEventType code)
{
	if (!wpa_s)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying DPP progress event %d", code);

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppSuccess(wpa_s, code);
}

void wpas_aidl_notify_dpp_config_rejected(struct wpa_supplicant *wpa_s)
{
	wpas_aidl_notify_dpp_failure(wpa_s, DppFailureCode::CONFIGURATION_REJECTED);
}

static void wpas_aidl_notify_dpp_no_ap_failure(struct wpa_supplicant *wpa_s,
		const char *ssid, const char *channel_list, unsigned short band_list[],
		int size)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG,
			"Notifying DPP NO AP event for SSID %s\nTried channels: %s",
			ssid ? ssid : "N/A", channel_list ? channel_list : "N/A");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppFailure(wpa_s, DppFailureCode::CANNOT_FIND_NETWORK,
			ssid, channel_list, band_list, size);
}

void wpas_aidl_notify_dpp_enrollee_auth_failure(struct wpa_supplicant *wpa_s,
		const char *ssid, unsigned short band_list[], int size)
{
	if (!wpa_s)
		return;

	wpa_printf(MSG_DEBUG,
			"Notifying DPP Enrollee authentication failure, SSID %s",
			ssid ? ssid : "N/A");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyDppFailure(wpa_s, DppFailureCode::ENROLLEE_AUTHENTICATION,
			ssid, NULL, band_list, size);
}


void wpas_aidl_notify_dpp_conn_status(struct wpa_supplicant *wpa_s, enum dpp_status_error status,
		const char *ssid, const char *channel_list, unsigned short band_list[], int size)
{
	switch (status)
	{
	case DPP_STATUS_OK:
		wpas_aidl_notify_dpp_config_applied(wpa_s);
		break;

	case DPP_STATUS_NO_AP:
		wpas_aidl_notify_dpp_no_ap_failure(wpa_s, ssid, channel_list, band_list, size);
		break;

	case DPP_STATUS_AUTH_FAILURE:
		wpas_aidl_notify_dpp_enrollee_auth_failure(wpa_s, ssid, band_list, size);
		break;

	default:
		break;
	}
}

void wpas_aidl_notify_pmk_cache_added(
	struct wpa_supplicant *wpa_s,
	struct rsn_pmksa_cache_entry *pmksa_entry)
{
	if (!wpa_s || !pmksa_entry)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(
		MSG_DEBUG,
		"Notifying PMK cache added event");

	aidl_manager->notifyPmkCacheAdded(wpa_s, pmksa_entry);
}

void wpas_aidl_notify_bss_tm_status(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_DEBUG, "Notifying BSS transition status");

	aidl_manager->notifyBssTmStatus(wpa_s);
}

void wpas_aidl_notify_transition_disable(struct wpa_supplicant *wpa_s,
						struct wpa_ssid *ssid,
						u8 bitmap)
{
	if (!wpa_s || !ssid)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyTransitionDisable(wpa_s, ssid, bitmap);
}

void wpas_aidl_notify_network_not_found(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_DEBUG, "Notify network not found");

	aidl_manager->notifyNetworkNotFound(wpa_s);
}

void wpas_aidl_notify_frequency_changed(struct wpa_supplicant *wpa_s, int frequency)
{
	if (!wpa_s)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_INFO, "Notify %s frequency changed to %d",
	    wpa_s->ifname, frequency);

	aidl_manager->notifyFrequencyChanged(wpa_s, frequency);
}

void wpas_aidl_notify_ceritification(struct wpa_supplicant *wpa_s,
		int depth, const char *subject,
		const char *altsubject[],
		int num_altsubject,
		const char *cert_hash,
		const struct wpabuf *cert)
{
	if (!wpa_s)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_DEBUG, "Notify certification");

	aidl_manager->notifyCertification(wpa_s,
			depth,
			subject,
			altsubject,
			num_altsubject,
			cert_hash,
			cert);
}

void wpas_aidl_notify_auxiliary_event(struct wpa_supplicant *wpa_s,
	AuxiliarySupplicantEventCode event_code, const char *reason_string)
{
	if (!wpa_s)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_DEBUG, "Notify auxiliary event, code=%d",
		static_cast<int>(event_code));
	aidl_manager->notifyAuxiliaryEvent(wpa_s, event_code, reason_string);
}

void wpas_aidl_notify_eap_method_selected(struct wpa_supplicant *wpa_s,
	const char *reason_string)
{
	wpas_aidl_notify_auxiliary_event(wpa_s,
		AuxiliarySupplicantEventCode::EAP_METHOD_SELECTED,
		reason_string);
}

void wpas_aidl_notify_ssid_temp_disabled(struct wpa_supplicant *wpa_s,
	const char *reason_string)
{
	wpas_aidl_notify_auxiliary_event(wpa_s,
		AuxiliarySupplicantEventCode::SSID_TEMP_DISABLED,
		reason_string);
}

void wpas_aidl_notify_open_ssl_failure(struct wpa_supplicant *wpa_s,
	const char *reason_string)
{
	wpas_aidl_notify_auxiliary_event(wpa_s,
		AuxiliarySupplicantEventCode::OPEN_SSL_FAILURE,
		reason_string);
}

void wpas_aidl_notify_qos_policy_reset(
	struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;
	wpa_printf(
		MSG_DEBUG, "Notifying Qos Policy Reset");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyQosPolicyReset(wpa_s);
}

void wpas_aidl_notify_qos_policy_request(struct wpa_supplicant *wpa_s,
	struct dscp_policy_data *policies, int num_policies)
{
	if (!wpa_s || !policies)
		return;

	wpa_printf(
		MSG_DEBUG, "Notifying Qos Policy Request");

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	aidl_manager->notifyQosPolicyRequest(wpa_s, policies, num_policies);
}

ssize_t wpas_aidl_get_certificate(const char* alias, uint8_t** value)
{
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return -1;

	wpa_printf(MSG_INFO, "Requesting certificate from framework");

	return aidl_manager->getCertificate(alias, value);
}

ssize_t wpas_aidl_list_aliases(const char *prefix, char ***aliases)
{
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return -1;

	wpa_printf(MSG_INFO, "Requesting aliases from framework");

	return aidl_manager->listAliases(prefix, aliases);
}

void wpas_aidl_notify_qos_policy_scs_response(struct wpa_supplicant *wpa_s,
		unsigned int count, int **scs_resp)
{
	if (!wpa_s || !count || !scs_resp)
		return;

	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager)
		return;

	wpa_printf(MSG_DEBUG, "Notifying Qos Policy SCS Response");
	aidl_manager->notifyQosPolicyScsResponse(wpa_s, count, scs_resp);
}
