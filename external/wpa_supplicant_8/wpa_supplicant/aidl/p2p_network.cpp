/*
 * WPA Supplicant - P2P network Aidl interface
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "aidl_manager.h"
#include "aidl_return_util.h"
#include "misc_utils.h"
#include "p2p_network.h"

extern "C"
{
#include "config_ssid.h"
}

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
using aidl_return_util::validateAndCall;
using misc_utils::createStatus;

P2pNetwork::P2pNetwork(
	struct wpa_global *wpa_global, const char ifname[], int network_id)
	: wpa_global_(wpa_global),
	  ifname_(ifname),
	  network_id_(network_id),
	  is_valid_(true)
{}

void P2pNetwork::invalidate() { is_valid_ = false; }
bool P2pNetwork::isValid()
{
	return (is_valid_ && (retrieveNetworkPtr() != nullptr));
}

::ndk::ScopedAStatus P2pNetwork::getId(
	int32_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getIdInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::getInterfaceName(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getInterfaceNameInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::getType(
	IfaceType* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getTypeInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::getSsid(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getSsidInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::getBssid(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getBssidInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::isCurrent(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::isCurrentInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::isPersistent(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::isPersistentInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::isGroupOwner(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::isGroupOwnerInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pNetwork::setClientList(
	const std::vector<MacAddress>& in_clients)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::setClientListInternal, in_clients);
}

::ndk::ScopedAStatus P2pNetwork::getClientList(
	std::vector<MacAddress>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pNetwork::getClientListInternal, _aidl_return);
}

std::pair<uint32_t, ndk::ScopedAStatus> P2pNetwork::getIdInternal()
{
	return {network_id_, ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> P2pNetwork::getInterfaceNameInternal()
{
	return {ifname_, ndk::ScopedAStatus::ok()};
}

std::pair<IfaceType, ndk::ScopedAStatus> P2pNetwork::getTypeInternal()
{
	return {IfaceType::P2P, ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> P2pNetwork::getSsidInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::vector<uint8_t> ssid(
		wpa_ssid->ssid, wpa_ssid->ssid + wpa_ssid->ssid_len);
	return {ssid, ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
P2pNetwork::getBssidInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::vector<uint8_t> bssid;
	if (wpa_ssid->bssid_set) {
		bssid.assign(wpa_ssid->bssid, wpa_ssid->bssid + ETH_ALEN);
	}
	return {bssid, ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> P2pNetwork::isCurrentInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_s->current_ssid == wpa_ssid),
		ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> P2pNetwork::isPersistentInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_ssid->disabled == 2), ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> P2pNetwork::isGroupOwnerInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_ssid->mode == wpas_mode::WPAS_MODE_P2P_GO),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pNetwork::setClientListInternal(
	const std::vector<MacAddress> &clients)
{
	for (const auto &client : clients) {
		if (client.data.size() != ETH_ALEN) {
			return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
		}
	}
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	os_free(wpa_ssid->p2p_client_list);
	// Internal representation uses a generic MAC addr/mask storage format
	// (even though the mask is always 0xFF'ed for p2p_client_list). So, the
	// first 6 bytes holds the client MAC address and the next 6 bytes are
	// OxFF'ed.
	wpa_ssid->p2p_client_list =
		(u8 *)os_malloc(ETH_ALEN * 2 * clients.size());
	if (!wpa_ssid->p2p_client_list) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	u8 *list = wpa_ssid->p2p_client_list;
	for (const auto &client : clients) {
		os_memcpy(list, client.data.data(), ETH_ALEN);
		list += ETH_ALEN;
		os_memset(list, 0xFF, ETH_ALEN);
		list += ETH_ALEN;
	}
	wpa_ssid->num_p2p_clients = clients.size();
	return ndk::ScopedAStatus::ok();
}

std::pair<std::vector<MacAddress>, ndk::ScopedAStatus>
P2pNetwork::getClientListInternal()
{
	std::vector<MacAddress> clients;
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->p2p_client_list) {
		return {clients, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	u8 *list = wpa_ssid->p2p_client_list;
	for (size_t i = 0; i < wpa_ssid->num_p2p_clients; i++) {
		MacAddress client = MacAddress{};
		client.data = std::vector<uint8_t>(list, list + ETH_ALEN);
		clients.emplace_back(client);
		list += 2 * ETH_ALEN;
	}
	return {clients, ndk::ScopedAStatus::ok()};
}

/**
 * Retrieve the underlying |wpa_ssid| struct pointer for
 * this network.
 * If the underlying network is removed or the interface
 * this network belong to is removed, all RPC method calls
 * on this object will return failure.
 */
struct wpa_ssid *P2pNetwork::retrieveNetworkPtr()
{
	wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (!wpa_s)
		return nullptr;
	return wpa_config_get_network(wpa_s->conf, network_id_);
}

/**
 * Retrieve the underlying |wpa_supplicant| struct
 * pointer for this network.
 */
struct wpa_supplicant *P2pNetwork::retrieveIfacePtr()
{
	return wpa_supplicant_get_iface(
		(struct wpa_global *)wpa_global_, ifname_.c_str());
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
