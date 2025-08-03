/*
 * wpa_supplicant - Event notifications
 * Copyright (c) 2009-2010, Jouni Malinen <j@w1.fi>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef NOTIFY_H
#define NOTIFY_H

#include "p2p/p2p.h"
#include "bss.h"
#include "rsn_supp/pmksa_cache.h"
#include "dpp.h"

struct wps_credential;
struct wps_event_m2d;
struct wps_event_fail;
struct tls_cert_data;
struct wpa_cred;
struct rsn_pmksa_cache_entry;

int wpas_notify_supplicant_initialized(struct wpa_global *global);
void wpas_notify_supplicant_deinitialized(struct wpa_global *global);
int wpas_notify_iface_added(struct wpa_supplicant *wpa_s);
void wpas_notify_iface_removed(struct wpa_supplicant *wpa_s);
void wpas_notify_state_changed(struct wpa_supplicant *wpa_s,
			       enum wpa_states new_state,
			       enum wpa_states old_state);
void wpas_notify_disconnect_reason(struct wpa_supplicant *wpa_s);
void wpas_notify_auth_status_code(struct wpa_supplicant *wpa_s);
void wpas_notify_assoc_status_code(struct wpa_supplicant *wpa_s, const u8 *bssid, u8 timed_out,
				   const u8 *assoc_resp_ie, size_t assoc_resp_ie_len);
void wpas_notify_auth_timeout(struct wpa_supplicant *wpa_s);
void wpas_notify_roam_time(struct wpa_supplicant *wpa_s);
void wpas_notify_roam_complete(struct wpa_supplicant *wpa_s);
void wpas_notify_session_length(struct wpa_supplicant *wpa_s);
void wpas_notify_bss_tm_status(struct wpa_supplicant *wpa_s);
void wpas_notify_network_changed(struct wpa_supplicant *wpa_s);
void wpas_notify_ap_scan_changed(struct wpa_supplicant *wpa_s);
void wpas_notify_bssid_changed(struct wpa_supplicant *wpa_s);
void wpas_notify_mac_address_changed(struct wpa_supplicant *wpa_s);
void wpas_notify_auth_changed(struct wpa_supplicant *wpa_s);
void wpas_notify_network_enabled_changed(struct wpa_supplicant *wpa_s,
					 struct wpa_ssid *ssid);
void wpas_notify_network_selected(struct wpa_supplicant *wpa_s,
				  struct wpa_ssid *ssid);
void wpas_notify_network_request(struct wpa_supplicant *wpa_s,
				 struct wpa_ssid *ssid,
				 enum wpa_ctrl_req_type rtype,
				 const char *default_txt);
void wpas_notify_permanent_id_req_denied(struct wpa_supplicant *wpa_s);
void wpas_notify_scanning(struct wpa_supplicant *wpa_s);
void wpas_notify_scan_done(struct wpa_supplicant *wpa_s, int success);
void wpas_notify_scan_results(struct wpa_supplicant *wpa_s);
void wpas_notify_wps_credential(struct wpa_supplicant *wpa_s,
				const struct wps_credential *cred);
void wpas_notify_wps_event_m2d(struct wpa_supplicant *wpa_s,
			       struct wps_event_m2d *m2d);
void wpas_notify_wps_event_fail(struct wpa_supplicant *wpa_s,
				struct wps_event_fail *fail);
void wpas_notify_wps_event_success(struct wpa_supplicant *wpa_s);
void wpas_notify_wps_event_pbc_overlap(struct wpa_supplicant *wpa_s);
void wpas_notify_network_added(struct wpa_supplicant *wpa_s,
			       struct wpa_ssid *ssid);
void wpas_notify_network_removed(struct wpa_supplicant *wpa_s,
				 struct wpa_ssid *ssid);
void wpas_notify_bss_added(struct wpa_supplicant *wpa_s, u8 bssid[],
			   unsigned int id);
void wpas_notify_bss_removed(struct wpa_supplicant *wpa_s, u8 bssid[],
			     unsigned int id);
void wpas_notify_bss_freq_changed(struct wpa_supplicant *wpa_s,
				  unsigned int id);
void wpas_notify_bss_signal_changed(struct wpa_supplicant *wpa_s,
				    unsigned int id);
void wpas_notify_bss_privacy_changed(struct wpa_supplicant *wpa_s,
				     unsigned int id);
void wpas_notify_bss_mode_changed(struct wpa_supplicant *wpa_s,
				  unsigned int id);
void wpas_notify_bss_wpaie_changed(struct wpa_supplicant *wpa_s,
				   unsigned int id);
void wpas_notify_bss_rsnie_changed(struct wpa_supplicant *wpa_s,
				   unsigned int id);
void wpas_notify_bss_wps_changed(struct wpa_supplicant *wpa_s,
				 unsigned int id);
void wpas_notify_bss_ies_changed(struct wpa_supplicant *wpa_s,
				 unsigned int id);
void wpas_notify_bss_rates_changed(struct wpa_supplicant *wpa_s,
				   unsigned int id);
void wpas_notify_bss_seen(struct wpa_supplicant *wpa_s, unsigned int id);
void wpas_notify_blob_added(struct wpa_supplicant *wpa_s, const char *name);
void wpas_notify_blob_removed(struct wpa_supplicant *wpa_s, const char *name);

void wpas_notify_debug_level_changed(struct wpa_global *global);
void wpas_notify_debug_timestamp_changed(struct wpa_global *global);
void wpas_notify_debug_show_keys_changed(struct wpa_global *global);
void wpas_notify_suspend(struct wpa_global *global);
void wpas_notify_resume(struct wpa_global *global);

void wpas_notify_sta_authorized(struct wpa_supplicant *wpa_s,
				const u8 *mac_addr, int authorized,
				const u8 *p2p_dev_addr, const u8 *ip);
void wpas_notify_p2p_find_stopped(struct wpa_supplicant *wpa_s);
void wpas_notify_p2p_device_found(struct wpa_supplicant *wpa_s,
				 const u8 *addr, const struct p2p_peer_info *info,
				 const u8* peer_wfd_device_info, u8 peer_wfd_device_info_len,
				 const u8* peer_wfd_r2_device_info, u8 peer_wfd_r2_device_info_len,
				 int new_device);
void wpas_notify_p2p_device_lost(struct wpa_supplicant *wpa_s,
				 const u8 *dev_addr);
void wpas_notify_p2p_group_removed(struct wpa_supplicant *wpa_s,
				   const struct wpa_ssid *ssid,
				   const char *role);
void wpas_notify_p2p_go_neg_req(struct wpa_supplicant *wpa_s,
				const u8 *src, u16 dev_passwd_id, u8 go_intent);
void wpas_notify_p2p_go_neg_completed(struct wpa_supplicant *wpa_s,
				      struct p2p_go_neg_results *res);
void wpas_notify_p2p_invitation_result(struct wpa_supplicant *wpa_s,
				       int status, const u8 *bssid);
void wpas_notify_p2p_sd_request(struct wpa_supplicant *wpa_s,
				int freq, const u8 *sa, u8 dialog_token,
				u16 update_indic, const u8 *tlvs,
				size_t tlvs_len);
void wpas_notify_p2p_sd_response(struct wpa_supplicant *wpa_s,
				 const u8 *sa, u16 update_indic,
				 const u8 *tlvs, size_t tlvs_len);
void wpas_notify_p2p_provision_discovery(struct wpa_supplicant *wpa_s,
					 const u8 *dev_addr, int request,
					 enum p2p_prov_disc_status status,
					 u16 config_methods,
					 unsigned int generated_pin,
					 const char *group_ifname);
void wpas_notify_p2p_group_started(struct wpa_supplicant *wpa_s,
				   struct wpa_ssid *ssid, int persistent,
				   int client, const u8 *ip);
void wpas_notify_p2p_group_formation_failure(struct wpa_supplicant *wpa_s,
					     const char *reason);
void wpas_notify_persistent_group_added(struct wpa_supplicant *wpa_s,
					struct wpa_ssid *ssid);
void wpas_notify_persistent_group_removed(struct wpa_supplicant *wpa_s,
					  struct wpa_ssid *ssid);

void wpas_notify_p2p_wps_failed(struct wpa_supplicant *wpa_s,
				struct wps_event_fail *fail);

void wpas_notify_certification(struct wpa_supplicant *wpa_s,
			       struct tls_cert_data *cert,
			       const char *cert_hash);
void wpas_notify_preq(struct wpa_supplicant *wpa_s,
		      const u8 *addr, const u8 *dst, const u8 *bssid,
		      const u8 *ie, size_t ie_len, u32 ssi_signal);
void wpas_notify_eap_status(struct wpa_supplicant *wpa_s, const char *status,
			    const char *parameter);
void wpas_notify_eap_error(struct wpa_supplicant *wpa_s, int error_code);
void wpas_notify_psk_mismatch(struct wpa_supplicant *wpa_s);
void wpas_notify_network_bssid_set_changed(struct wpa_supplicant *wpa_s,
					   struct wpa_ssid *ssid);
void wpas_notify_network_type_changed(struct wpa_supplicant *wpa_s,
				      struct wpa_ssid *ssid);
void wpas_notify_p2p_invitation_received(struct wpa_supplicant *wpa_s,
					 const u8 *sa, const u8 *go_dev_addr,
					 const u8 *bssid, int id, int op_freq);
void wpas_notify_mesh_group_started(struct wpa_supplicant *wpa_s,
				    struct wpa_ssid *ssid);
void wpas_notify_mesh_group_removed(struct wpa_supplicant *wpa_s,
				    const u8 *meshid, u8 meshid_len,
				    u16 reason_code);
void wpas_notify_mesh_peer_connected(struct wpa_supplicant *wpa_s,
				     const u8 *peer_addr);
void wpas_notify_mesh_peer_disconnected(struct wpa_supplicant *wpa_s,
					const u8 *peer_addr, u16 reason_code);
void wpas_notify_anqp_query_done(struct wpa_supplicant *wpa_s, const u8* bssid,
				 const char* result,
				 const struct wpa_bss_anqp *anqp);
void wpas_notify_hs20_icon_query_done(struct wpa_supplicant *wpa_s, const u8* bssid,
				      const char* file_name, const u8* image,
				      u32 image_length);
void wpas_notify_hs20_rx_subscription_remediation(struct wpa_supplicant *wpa_s,
						  const char* url,
						  u8 osu_method);
void wpas_notify_hs20_rx_deauth_imminent_notice(struct wpa_supplicant *wpa_s,
						u8 code, u16 reauth_delay,
						const char *url);
void wpas_notify_hs20_rx_terms_and_conditions_acceptance(
		struct wpa_supplicant *wpa_s, const char *url);
void wpas_notify_dpp_config_received(struct wpa_supplicant *wpa_s,
		struct wpa_ssid *ssid, bool conn_status_requested);
void wpas_notify_dpp_config_sent(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_connection_status_sent(struct wpa_supplicant *wpa_s,
		enum dpp_status_error result);
void wpas_notify_dpp_auth_success(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_resp_pending(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_not_compatible(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_missing_auth(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_configuration_failure(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_timeout(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_auth_failure(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_failure(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_config_sent_wait_response(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_conn_status(struct wpa_supplicant *wpa_s,
		enum dpp_status_error status, const char *ssid,
		const char *channel_list, unsigned short band_list[], int size);
void wpas_notify_dpp_config_accepted(struct wpa_supplicant *wpa_s);
void wpas_notify_dpp_config_rejected(struct wpa_supplicant *wpa_s);
void wpas_notify_transition_disable(struct wpa_supplicant *wpa_s,
				    struct wpa_ssid *ssid,
				    u8 bitmap);
void wpas_notify_network_not_found(struct wpa_supplicant *wpa_s);
void wpas_notify_interworking_ap_added(struct wpa_supplicant *wpa_s,
				       struct wpa_bss *bss,
				       struct wpa_cred *cred, int excluded,
				       const char *type, int bh, int bss_load,
				       int conn_capab);
void wpas_notify_interworking_select_done(struct wpa_supplicant *wpa_s);
void wpas_notify_eap_method_selected(struct wpa_supplicant *wpa_s,
		const char* reason_string);
void wpas_notify_ssid_temp_disabled(struct wpa_supplicant *wpa_s,
		const char *reason_string);
void wpas_notify_open_ssl_failure(struct wpa_supplicant *wpa_s,
		const char *reason_string);
void wpas_notify_qos_policy_reset(struct wpa_supplicant *wpa_s);
void wpas_notify_qos_policy_request(struct wpa_supplicant *wpa_s,
		struct dscp_policy_data *policies, int num_policies);
void wpas_notify_frequency_changed(struct wpa_supplicant *wpa_s, int frequency);
ssize_t wpas_get_certificate(const char *alias, uint8_t** value);
ssize_t wpas_list_aliases(const char *prefix, char ***aliases);
void wpas_notify_pmk_cache_added(struct wpa_supplicant *wpa_s,
				 struct rsn_pmksa_cache_entry *entry);
void wpas_notify_signal_change(struct wpa_supplicant *wpa_s);
void wpas_notify_qos_policy_scs_response(struct wpa_supplicant *wpa_s,
		unsigned int num_scs_resp, int **scs_resp);
void wpas_notify_mlo_info_change_reason(struct wpa_supplicant *wpa_s,
					enum mlo_info_change_reason reason);

#endif /* NOTIFY_H */
