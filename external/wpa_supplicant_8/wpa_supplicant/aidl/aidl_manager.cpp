/*
 * WPA Supplicant - Manager for Aidl interface objects
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include <algorithm>
#include <functional>
#include <iostream>
#include <regex>

#include "aidl_manager.h"
#include "misc_utils.h"
#include <android/binder_process.h>
#include <android/binder_manager.h>
#include <aidl/android/hardware/wifi/supplicant/IpVersion.h>
#include <cutils/properties.h>

extern "C" {
#include "scan.h"
#include "src/eap_common/eap_sim_common.h"
#include "list.h"
}

namespace {

constexpr uint8_t kWfdDeviceInfoLen = 6;
constexpr uint8_t kWfdR2DeviceInfoLen = 2;
// GSM-AUTH:<RAND1>:<RAND2>[:<RAND3>]
constexpr char kGsmAuthRegex2[] = "GSM-AUTH:([0-9a-f]+):([0-9a-f]+)";
constexpr char kGsmAuthRegex3[] =
	"GSM-AUTH:([0-9a-f]+):([0-9a-f]+):([0-9a-f]+)";
// UMTS-AUTH:<RAND>:<AUTN>
constexpr char kUmtsAuthRegex[] = "UMTS-AUTH:([0-9a-f]+):([0-9a-f]+)";
constexpr size_t kGsmRandLenBytes = GSM_RAND_LEN;
constexpr size_t kUmtsRandLenBytes = EAP_AKA_RAND_LEN;
constexpr size_t kUmtsAutnLenBytes = EAP_AKA_AUTN_LEN;
const std::vector<uint8_t> kZeroBssid = {0, 0, 0, 0, 0, 0};
int32_t aidl_service_version = 0;
int32_t aidl_client_version = 0;

using aidl::android::hardware::wifi::supplicant::GsmRand;
using aidl::android::hardware::wifi::supplicant::KeyMgmtMask;

/**
 * Check if the provided |wpa_supplicant| structure represents a P2P iface or
 * not.
 */
constexpr bool isP2pIface(const struct wpa_supplicant *wpa_s)
{
	return wpa_s->global->p2p_init_wpa_s == wpa_s;
}

/**
 * Creates a unique key for the network using the provided |ifname| and
 * |network_id| to be used in the internal map of |ISupplicantNetwork| objects.
 * This is of the form |ifname|_|network_id|. For ex: "wlan0_1".
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 */
const std::string getNetworkObjectMapKey(
	const std::string &ifname, int network_id)
{
	return ifname + "_" + std::to_string(network_id);
}

/**
 * Add callback to the corresponding list after linking to death on the
 * corresponding aidl object reference.
 */
template <class CallbackType>
int registerForDeathAndAddCallbackAidlObjectToList(
	AIBinder_DeathRecipient* death_notifier,
	const std::shared_ptr<CallbackType> &callback,
	std::vector<std::shared_ptr<CallbackType>> &callback_list)
{
	binder_status_t status = AIBinder_linkToDeath(callback->asBinder().get(),
			death_notifier, nullptr /* cookie */);
	if (status != STATUS_OK) {
		wpa_printf(
			MSG_ERROR,
			"Error registering for death notification for "
			"supplicant callback object");
		return 1;
	}
	callback_list.push_back(callback);
	if (aidl_client_version == 0) {
	    callback->getInterfaceVersion(&aidl_client_version);
	    wpa_printf(MSG_INFO, "AIDL client version: %d", aidl_client_version);
	}
	return 0;
}

template <class ObjectType>
int addAidlObjectToMap(
	const std::string &key, const std::shared_ptr<ObjectType> &object,
	std::map<const std::string, std::shared_ptr<ObjectType>> &object_map)
{
	// Return failure if we already have an object for that |key|.
	if (object_map.find(key) != object_map.end())
		return 1;
	object_map[key] = object;
	if (!object_map[key].get())
		return 1;
	return 0;
}

template <class ObjectType>
int removeAidlObjectFromMap(
	const std::string &key,
	std::map<const std::string, std::shared_ptr<ObjectType>> &object_map)
{
	// Return failure if we dont have an object for that |key|.
	const auto &object_iter = object_map.find(key);
	if (object_iter == object_map.end())
		return 1;
	object_iter->second->invalidate();
	object_map.erase(object_iter);
	return 0;
}

template <class CallbackType>
int addIfaceCallbackAidlObjectToMap(
	AIBinder_DeathRecipient* death_notifier,
	const std::string &ifname, const std::shared_ptr<CallbackType> &callback,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty())
		return 1;

	auto iface_callback_map_iter = callbacks_map.find(ifname);
	if (iface_callback_map_iter == callbacks_map.end())
		return 1;
	auto &iface_callback_list = iface_callback_map_iter->second;

	// Register for death notification before we add it to our list.
	return registerForDeathAndAddCallbackAidlObjectToList<CallbackType>(
		death_notifier, callback, iface_callback_list);
}

template <class CallbackType>
int addNetworkCallbackAidlObjectToMap(
	AIBinder_DeathRecipient* death_notifier,
	const std::string &ifname, int network_id,
	const std::shared_ptr<CallbackType> &callback,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty() || network_id < 0)
		return 1;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(ifname, network_id);
	auto network_callback_map_iter = callbacks_map.find(network_key);
	if (network_callback_map_iter == callbacks_map.end())
		return 1;
	auto &network_callback_list = network_callback_map_iter->second;

	// Register for death notification before we add it to our list.
	return registerForDeathAndAddCallbackAidlObjectToList<CallbackType>(
		death_notifier, callback, network_callback_list);
}

template <class CallbackType>
int removeAllIfaceCallbackAidlObjectsFromMap(
	AIBinder_DeathRecipient* death_notifier,
	const std::string &ifname,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	auto iface_callback_map_iter = callbacks_map.find(ifname);
	if (iface_callback_map_iter == callbacks_map.end())
		return 1;
	const auto &iface_callback_list = iface_callback_map_iter->second;
	for (const auto &callback : iface_callback_list) {
		binder_status_t status = AIBinder_linkToDeath(callback->asBinder().get(),
				death_notifier, nullptr /* cookie */);
		if (status != STATUS_OK) {
			wpa_printf(
				MSG_ERROR,
				"Error deregistering for death notification for "
				"iface callback object");
		}
	}
	callbacks_map.erase(iface_callback_map_iter);
	return 0;
}

template <class CallbackType>
int removeAllNetworkCallbackAidlObjectsFromMap(
	AIBinder_DeathRecipient* death_notifier,
	const std::string &network_key,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	auto network_callback_map_iter = callbacks_map.find(network_key);
	if (network_callback_map_iter == callbacks_map.end())
		return 1;
	const auto &network_callback_list = network_callback_map_iter->second;
	for (const auto &callback : network_callback_list) {
		binder_status_t status = AIBinder_linkToDeath(callback->asBinder().get(),
				death_notifier, nullptr /* cookie */);
		if (status != STATUS_OK) {
			wpa_printf(
				MSG_ERROR,
				"Error deregistering for death "
				"notification for "
				"network callback object");
		}
	}
	callbacks_map.erase(network_callback_map_iter);
	return 0;
}

template <class CallbackType>
void removeIfaceCallbackAidlObjectFromMap(
	const std::string &ifname, const std::shared_ptr<CallbackType> &callback,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty())
		return;

	auto iface_callback_map_iter = callbacks_map.find(ifname);
	if (iface_callback_map_iter == callbacks_map.end())
		return;

	auto &iface_callback_list = iface_callback_map_iter->second;
	iface_callback_list.erase(
		std::remove(
		iface_callback_list.begin(), iface_callback_list.end(),
		callback),
		iface_callback_list.end());
}

template <class CallbackType>
void removeNetworkCallbackAidlObjectFromMap(
	const std::string &ifname, int network_id,
	const std::shared_ptr<CallbackType> &callback,
	std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty() || network_id < 0)
		return;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(ifname, network_id);

	auto network_callback_map_iter = callbacks_map.find(network_key);
	if (network_callback_map_iter == callbacks_map.end())
		return;

	auto &network_callback_list = network_callback_map_iter->second;
	network_callback_list.erase(
		std::remove(
		network_callback_list.begin(), network_callback_list.end(),
		callback),
		network_callback_list.end());
}

template <class CallbackType>
void callWithEachIfaceCallback(
	const std::string &ifname,
	const std::function<ndk::ScopedAStatus(std::shared_ptr<CallbackType>)> &method,
	const std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty())
		return;

	auto iface_callback_map_iter = callbacks_map.find(ifname);
	if (iface_callback_map_iter == callbacks_map.end())
		return;
	const auto &iface_callback_list = iface_callback_map_iter->second;
	for (const auto &callback : iface_callback_list) {
		if (!method(callback).isOk()) {
			wpa_printf(
				MSG_ERROR, "Failed to invoke AIDL iface callback");
		}
	}
}

template <class CallbackType>
void callWithEachNetworkCallback(
	const std::string &ifname, int network_id,
	const std::function<
	ndk::ScopedAStatus(std::shared_ptr<CallbackType>)> &method,
	const std::map<const std::string, std::vector<std::shared_ptr<CallbackType>>>
	&callbacks_map)
{
	if (ifname.empty() || network_id < 0)
		return;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(ifname, network_id);
	auto network_callback_map_iter = callbacks_map.find(network_key);
	if (network_callback_map_iter == callbacks_map.end())
		return;
	const auto &network_callback_list = network_callback_map_iter->second;
	for (const auto &callback : network_callback_list) {
		if (!method(callback).isOk()) {
			wpa_printf(
				MSG_ERROR,
				"Failed to invoke AIDL network callback");
		}
	}
}

int parseGsmAuthNetworkRequest(
	const std::string &params_str,
	std::vector<GsmRand> *out_rands)
{
	std::smatch matches;
	std::regex params_gsm_regex2(kGsmAuthRegex2);
	std::regex params_gsm_regex3(kGsmAuthRegex3);
	if (!std::regex_match(params_str, matches, params_gsm_regex3) &&
		!std::regex_match(params_str, matches, params_gsm_regex2)) {
		return 1;
	}
	for (uint32_t i = 1; i < matches.size(); i++) {
		GsmRand rand;
		rand.data = std::vector<uint8_t>(kGsmRandLenBytes);
		const auto &match = matches[i];
		WPA_ASSERT(match.size() >= 2 * rand.data.size());
		if (hexstr2bin(match.str().c_str(), rand.data.data(), rand.data.size())) {
			wpa_printf(MSG_ERROR, "Failed to parse GSM auth params");
			return 1;
		}
		out_rands->push_back(rand);
	}
	return 0;
}

int parseUmtsAuthNetworkRequest(
	const std::string &params_str,
	std::vector<uint8_t> *out_rand,
	std::vector<uint8_t> *out_autn)
{
	std::smatch matches;
	std::regex params_umts_regex(kUmtsAuthRegex);
	if (!std::regex_match(params_str, matches, params_umts_regex)) {
		return 1;
	}
	WPA_ASSERT(matches[1].size() >= 2 * out_rand->size());
	if (hexstr2bin(
		matches[1].str().c_str(), out_rand->data(), out_rand->size())) {
		wpa_printf(MSG_ERROR, "Failed to parse UMTS auth params");
		return 1;
	}
	WPA_ASSERT(matches[2].size() >= 2 * out_autn->size());
	if (hexstr2bin(
		matches[2].str().c_str(), out_autn->data(), out_autn->size())) {
		wpa_printf(MSG_ERROR, "Failed to parse UMTS auth params");
		return 1;
	}
	return 0;
}

inline std::vector<uint8_t> byteArrToVec(const uint8_t* arr, int len) {
	return std::vector<uint8_t>{arr, arr + len};
}

inline std::vector<uint8_t> macAddrToVec(const uint8_t* mac_addr) {
	return byteArrToVec(mac_addr, ETH_ALEN);
}

inline std::array<uint8_t, ETH_ALEN> macAddrToArray(const uint8_t* mac_addr) {
	std::array<uint8_t, ETH_ALEN> arr;
	std::copy(mac_addr, mac_addr + ETH_ALEN, std::begin(arr));
	return arr;
}

// Raw pointer to the global structure maintained by the core.
// Declared here to be accessible to onDeath()
struct wpa_global *wpa_global_;

void onDeath(void* cookie) {
	wpa_printf(MSG_ERROR, "Client died. Terminating...");
	wpa_supplicant_terminate_proc(wpa_global_);
}

}  // namespace

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {

AidlManager *AidlManager::instance_ = NULL;

AidlManager *AidlManager::getInstance()
{
	if (!instance_)
		instance_ = new AidlManager();
	return instance_;
}

void AidlManager::destroyInstance()
{
	if (instance_)
		delete instance_;
	instance_ = NULL;
}

/**
 * Check that the AIDL service is running at least the expected version.
 * Use to avoid the case where the AIDL interface version
 * is greater than the version implemented by the service.
 */
int32_t AidlManager::isAidlServiceVersionAtLeast(int32_t expected_version)
{
	return expected_version <= aidl_service_version;
}

int32_t AidlManager::isAidlClientVersionAtLeast(int32_t expected_version)
{
	return expected_version <= aidl_client_version;
}

int AidlManager::registerAidlService(struct wpa_global *global)
{
	// Create the main aidl service object and register it.
	wpa_printf(MSG_INFO, "Starting AIDL supplicant");
	supplicant_object_ = ndk::SharedRefBase::make<Supplicant>(global);
	if (!supplicant_object_->getInterfaceVersion(&aidl_service_version).isOk()) {
		aidl_service_version = Supplicant::version;
	}
	wpa_printf(MSG_INFO, "AIDL Interface version: %d", aidl_service_version);
	wpa_global_ = global;
	std::string instance = std::string() + Supplicant::descriptor + "/default";
	if (AServiceManager_addService(supplicant_object_->asBinder().get(),
			instance.c_str()) != STATUS_OK)
	{
		return 1;
	}

	// Initialize the death notifier.
	death_notifier_ = AIBinder_DeathRecipient_new(onDeath);
	return 0;
}

/**
 * Register an interface to aidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::registerInterface(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return 1;

	if (isP2pIface(wpa_s)) {
		if (addAidlObjectToMap<P2pIface>(
			wpa_s->ifname,
			ndk::SharedRefBase::make<P2pIface>(wpa_s->global, wpa_s->ifname),
			p2p_iface_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to register P2P interface with AIDL "
				"control: %s",
				wpa_s->ifname);
			return 1;
		}
		p2p_iface_callbacks_map_[wpa_s->ifname] =
			std::vector<std::shared_ptr<ISupplicantP2pIfaceCallback>>();
	} else {
		if (addAidlObjectToMap<StaIface>(
			wpa_s->ifname,
			ndk::SharedRefBase::make<StaIface>(wpa_s->global, wpa_s->ifname),
			sta_iface_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to register STA interface with AIDL "
				"control: %s",
				wpa_s->ifname);
			return 1;
		}
		sta_iface_callbacks_map_[wpa_s->ifname] =
			std::vector<std::shared_ptr<ISupplicantStaIfaceCallback>>();
		// Turn on Android specific customizations for STA interfaces
		// here!
		//
		// Turn on scan mac randomization only if driver supports.
		if (wpa_s->mac_addr_rand_supported & MAC_ADDR_RAND_SCAN) {
			if (wpas_mac_addr_rand_scan_set(
				wpa_s, MAC_ADDR_RAND_SCAN, nullptr, nullptr)) {
				wpa_printf(
					MSG_ERROR,
					"Failed to enable scan mac randomization");
			}
		}

		// Enable randomized source MAC address for GAS/ANQP
		// Set the lifetime to 0, guarantees a unique address for each GAS
		// session
		wpa_s->conf->gas_rand_mac_addr = WPAS_MAC_ADDR_STYLE_RANDOM;
		wpa_s->conf->gas_rand_addr_lifetime = 0;
	}

	// Invoke the |onInterfaceCreated| method on all registered callbacks.
	callWithEachSupplicantCallback(std::bind(
		&ISupplicantCallback::onInterfaceCreated, std::placeholders::_1,
		misc_utils::charBufToString(wpa_s->ifname)));
	return 0;
}

/**
 * Unregister an interface from aidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::unregisterInterface(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return 1;

	// Check if this interface is present in P2P map first, else check in
	// STA map.
	// Note: We can't use isP2pIface() here because interface
	// pointers (wpa_s->global->p2p_init_wpa_s == wpa_s) used by the helper
	// function is cleared by the core before notifying the AIDL interface.
	bool success =
		!removeAidlObjectFromMap(wpa_s->ifname, p2p_iface_object_map_);
	if (success) {  // assumed to be P2P
		success = !removeAllIfaceCallbackAidlObjectsFromMap(
			death_notifier_, wpa_s->ifname, p2p_iface_callbacks_map_);
	} else {  // assumed to be STA
		success = !removeAidlObjectFromMap(
			wpa_s->ifname, sta_iface_object_map_);
		if (success) {
			success = !removeAllIfaceCallbackAidlObjectsFromMap(
				death_notifier_, wpa_s->ifname, sta_iface_callbacks_map_);
		}
	}
	if (!success) {
		wpa_printf(
			MSG_ERROR,
			"Failed to unregister interface with AIDL "
			"control: %s",
			wpa_s->ifname);
		return 1;
	}

	// Invoke the |onInterfaceRemoved| method on all registered callbacks.
	callWithEachSupplicantCallback(std::bind(
		&ISupplicantCallback::onInterfaceRemoved, std::placeholders::_1,
		misc_utils::charBufToString(wpa_s->ifname)));
	return 0;
}

/**
 * Register a network to aidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is added.
 * @param ssid |wpa_ssid| struct corresponding to the network being added.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::registerNetwork(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid)
{
	if (!wpa_s || !ssid)
		return 1;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(wpa_s->ifname, ssid->id);

	if (isP2pIface(wpa_s)) {
		if (addAidlObjectToMap<P2pNetwork>(
			network_key,
			ndk::SharedRefBase::make<P2pNetwork>(wpa_s->global, wpa_s->ifname, ssid->id),
			p2p_network_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to register P2P network with AIDL "
				"control: %d",
				ssid->id);
			return 1;
		}
	} else {
		if (addAidlObjectToMap<StaNetwork>(
			network_key,
			ndk::SharedRefBase::make<StaNetwork>(wpa_s->global, wpa_s->ifname, ssid->id),
			sta_network_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to register STA network with AIDL "
				"control: %d",
				ssid->id);
			return 1;
		}
		sta_network_callbacks_map_[network_key] =
			std::vector<std::shared_ptr<ISupplicantStaNetworkCallback>>();
		// Invoke the |onNetworkAdded| method on all registered
		// callbacks.
		callWithEachStaIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantStaIfaceCallback::onNetworkAdded,
			std::placeholders::_1, ssid->id));
	}
	return 0;
}

/**
 * Unregister a network from aidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is added.
 * @param ssid |wpa_ssid| struct corresponding to the network being added.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::unregisterNetwork(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid)
{
	if (!wpa_s || !ssid)
		return 1;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(wpa_s->ifname, ssid->id);

	if (isP2pIface(wpa_s)) {
		if (removeAidlObjectFromMap(
			network_key, p2p_network_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to unregister P2P network with AIDL "
				"control: %d",
				ssid->id);
			return 1;
		}
	} else {
		if (removeAidlObjectFromMap(
			network_key, sta_network_object_map_)) {
			wpa_printf(
				MSG_ERROR,
				"Failed to unregister STA network with AIDL "
				"control: %d",
				ssid->id);
			return 1;
		}
		if (removeAllNetworkCallbackAidlObjectsFromMap(
			death_notifier_, network_key, sta_network_callbacks_map_)) {
			return 1;
		}

		// Invoke the |onNetworkRemoved| method on all registered
		// callbacks.
		callWithEachStaIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantStaIfaceCallback::onNetworkRemoved,
			std::placeholders::_1, ssid->id));
	}
	return 0;
}

// Some of the undefined AKMs in AIDL (Mostly extension AKMs like FT AKMs)
// are mapped to the main AKM. This is for the framework to map the AKM to
// correct security type.
KeyMgmtMask convertSupplicantSelectedKeyMgmtForConnectionToAidl(int key_mgmt)
{
	switch (key_mgmt) {
		case WPA_KEY_MGMT_IEEE8021X:
			return KeyMgmtMask::WPA_EAP;
		case WPA_KEY_MGMT_PSK:
			return KeyMgmtMask::WPA_PSK;
		case WPA_KEY_MGMT_NONE:
			return KeyMgmtMask::NONE;
		case WPA_KEY_MGMT_IEEE8021X_NO_WPA:
			return KeyMgmtMask::IEEE8021X;
		case WPA_KEY_MGMT_FT_IEEE8021X:
			return KeyMgmtMask::FT_EAP;
		case WPA_KEY_MGMT_FT_PSK:
			return KeyMgmtMask::FT_PSK;
		case WPA_KEY_MGMT_IEEE8021X_SHA256:
			return KeyMgmtMask::WPA_EAP_SHA256;
		case WPA_KEY_MGMT_PSK_SHA256:
			return KeyMgmtMask::WPA_PSK_SHA256;
		case WPA_KEY_MGMT_SAE:
		case WPA_KEY_MGMT_FT_SAE:
		case WPA_KEY_MGMT_SAE_EXT_KEY:
		case WPA_KEY_MGMT_FT_SAE_EXT_KEY:
			return KeyMgmtMask::SAE;
		case WPA_KEY_MGMT_WAPI_PSK:
			return KeyMgmtMask::WAPI_PSK;
		case WPA_KEY_MGMT_WAPI_CERT:
			return KeyMgmtMask::WAPI_CERT;
		case WPA_KEY_MGMT_OSEN:
			return KeyMgmtMask::OSEN;
		case WPA_KEY_MGMT_IEEE8021X_SUITE_B_192:
		case WPA_KEY_MGMT_FT_IEEE8021X_SHA384:
			return KeyMgmtMask::SUITE_B_192;
		case WPA_KEY_MGMT_FILS_SHA256:
		case WPA_KEY_MGMT_FT_FILS_SHA256:
			return KeyMgmtMask::FILS_SHA256;
		case WPA_KEY_MGMT_FILS_SHA384:
		case WPA_KEY_MGMT_FT_FILS_SHA384:
			return KeyMgmtMask::FILS_SHA384;
		case WPA_KEY_MGMT_OWE:
			return KeyMgmtMask::OWE;
		case WPA_KEY_MGMT_DPP:
			return KeyMgmtMask::DPP;
		default:
			wpa_printf(MSG_INFO, "Unable to convert supplicant key_mgmt 0x%x to AIDL",
				    key_mgmt);
			return (KeyMgmtMask) key_mgmt;
	}
}

/**
 * Notify all listeners about any state changes on a particular interface.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the state change event occured.
 */
int AidlManager::notifyStateChange(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return 1;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return 1;

	// Invoke the |onStateChanged| method on all registered callbacks.
	SupplicantStateChangeData aidl_state_change_data = {};
	aidl_state_change_data.id = UINT32_MAX;
	aidl_state_change_data.newState = static_cast<StaIfaceCallbackState>(wpa_s->wpa_state);

	if (wpa_s->current_ssid) {
		aidl_state_change_data.id = wpa_s->current_ssid->id;
		std::vector<uint8_t> aidl_ssid(
			wpa_s->current_ssid->ssid,
			wpa_s->current_ssid->ssid + wpa_s->current_ssid->ssid_len);
		aidl_state_change_data.ssid = aidl_ssid;
		wpa_printf(MSG_INFO, "assoc key_mgmt 0x%x network key_mgmt 0x%x",
			wpa_s->key_mgmt, wpa_s->current_ssid->key_mgmt);
	}
	std::array<uint8_t, ETH_ALEN> aidl_bssid;
	// wpa_supplicant sets the |pending_bssid| field when it starts a
	// connection. Only after association state does it update the |bssid|
	// field. So, in the AIDL callback send the appropriate bssid.
	if (wpa_s->wpa_state <= WPA_ASSOCIATED) {
		aidl_bssid = macAddrToArray(wpa_s->pending_bssid);
	} else {
		aidl_bssid = macAddrToArray(wpa_s->bssid);
	}
	aidl_state_change_data.bssid = aidl_bssid;

	aidl_state_change_data.filsHlpSent =
		(wpa_auth_alg_fils(wpa_s->auth_alg) &&
		 !dl_list_empty(&wpa_s->fils_hlp_req) &&
		 (wpa_s->wpa_state == WPA_COMPLETED)) ? true : false;
	if (wpa_s->wpa_state >= WPA_ASSOCIATED) {
		// wpa_supplicant sets the frequency on receiving the EVENT_ASSOC.
		aidl_state_change_data.frequencyMhz = wpa_s->assoc_freq;
		// The key_mgmt is selected prior to sending the connect command
		// to driver. But in case of CROSS-AKM Connection/Roaming, the
		// key_mgmt is updated with the one from association IE. So the
		// selected key_mgmt is accurate only after moving to
		// associated state.
		aidl_state_change_data.keyMgmtMask =
			convertSupplicantSelectedKeyMgmtForConnectionToAidl(wpa_s->key_mgmt);
	}

	// Invoke the |onStateChanged| method on all registered callbacks.
	std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
		func = std::bind(
			&ISupplicantStaIfaceCallback::onSupplicantStateChanged,
			std::placeholders::_1,
			aidl_state_change_data);
	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), func);
	return 0;
}

/**
 * Notify all listeners about a request on a particular network.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is present.
 * @param ssid |wpa_ssid| struct corresponding to the network.
 * @param type type of request.
 * @param param addition params associated with the request.
 */
int AidlManager::notifyNetworkRequest(
	struct wpa_supplicant *wpa_s, struct wpa_ssid *ssid, int type,
	const char *param)
{
	if (!wpa_s || !ssid)
		return 1;

	const std::string network_key =
		getNetworkObjectMapKey(wpa_s->ifname, ssid->id);
	if (sta_network_object_map_.find(network_key) ==
		sta_network_object_map_.end())
		return 1;

	if (type == WPA_CTRL_REQ_EAP_IDENTITY) {
		callWithEachStaNetworkCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			ssid->id,
			std::bind(
			&ISupplicantStaNetworkCallback::
				onNetworkEapIdentityRequest,
			std::placeholders::_1));
		return 0;
	}
	if (type == WPA_CTRL_REQ_SIM) {
		std::vector<GsmRand> gsm_rands;
		std::vector<uint8_t> umts_rand = std::vector<uint8_t>(16);
		std::vector<uint8_t> umts_autn = std::vector<uint8_t>(16);
		if (!parseGsmAuthNetworkRequest(param, &gsm_rands)) {
			NetworkRequestEapSimGsmAuthParams aidl_params;
			aidl_params.rands = gsm_rands;
			callWithEachStaNetworkCallback(
				misc_utils::charBufToString(wpa_s->ifname),
				ssid->id,
				std::bind(
				&ISupplicantStaNetworkCallback::
					onNetworkEapSimGsmAuthRequest,
				std::placeholders::_1, aidl_params));
			return 0;
		}
		if (!parseUmtsAuthNetworkRequest(
			param, &umts_rand, &umts_autn)) {
			NetworkRequestEapSimUmtsAuthParams aidl_params;
			aidl_params.rand = umts_rand;
			aidl_params.autn = umts_autn;
			callWithEachStaNetworkCallback(
				misc_utils::charBufToString(wpa_s->ifname),
				ssid->id,
				std::bind(
				&ISupplicantStaNetworkCallback::
					onNetworkEapSimUmtsAuthRequest,
				std::placeholders::_1, aidl_params));
			return 0;
		}
	}
	return 1;
}

#ifdef CONFIG_INTERWORKING
/**
 * Notify that the AT_PERMANENT_ID_REQ is denied from eap_peer when the strict
 * conservative peer mode is enabled.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is present.
*/
void AidlManager::notifyPermanentIdReqDenied(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s->current_ssid) {
		wpa_printf(MSG_ERROR, "Current network NULL. Drop permanent_id_req_denied event!");
		return;
	}
	struct wpa_ssid *current_ssid = wpa_s->current_ssid;

	callWithEachStaNetworkCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			current_ssid->id,
			std::bind(
			&ISupplicantStaNetworkCallback::
				onPermanentIdReqDenied,
			std::placeholders::_1));
}

/**
 * Notify all listeners about the end of an ANQP query.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 * @param bssid BSSID of the access point.
 * @param result Result of the operation ("SUCCESS" or "FAILURE").
 * @param anqp |wpa_bss_anqp| ANQP data fetched.
 */
void AidlManager::notifyAnqpQueryDone(
	struct wpa_supplicant *wpa_s, const u8 *bssid, const char *result,
	const struct wpa_bss_anqp *anqp)
{
	if (!wpa_s || !bssid || !result || !anqp)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	AnqpData aidl_anqp_data;
	Hs20AnqpData aidl_hs20_anqp_data;
	if (std::string(result) == "SUCCESS") {
		aidl_anqp_data.venueName =
			misc_utils::convertWpaBufToVector(anqp->venue_name);
		aidl_anqp_data.roamingConsortium =
			misc_utils::convertWpaBufToVector(anqp->roaming_consortium);
		aidl_anqp_data.ipAddrTypeAvailability =
			misc_utils::convertWpaBufToVector(
			anqp->ip_addr_type_availability);
		aidl_anqp_data.naiRealm =
			misc_utils::convertWpaBufToVector(anqp->nai_realm);
		aidl_anqp_data.anqp3gppCellularNetwork =
			misc_utils::convertWpaBufToVector(anqp->anqp_3gpp);
		aidl_anqp_data.domainName =
			misc_utils::convertWpaBufToVector(anqp->domain_name);

		struct wpa_bss_anqp_elem *elem;
		dl_list_for_each(elem, &anqp->anqp_elems, struct wpa_bss_anqp_elem,
				 list) {
			if (elem->infoid == ANQP_VENUE_URL && elem->protected_response) {
				aidl_anqp_data.venueUrl =
							misc_utils::convertWpaBufToVector(elem->payload);
				break;
			}
		}

#ifdef CONFIG_HS20
		aidl_hs20_anqp_data.operatorFriendlyName =
			misc_utils::convertWpaBufToVector(
			anqp->hs20_operator_friendly_name);
		aidl_hs20_anqp_data.wanMetrics =
			misc_utils::convertWpaBufToVector(anqp->hs20_wan_metrics);
		aidl_hs20_anqp_data.connectionCapability =
			misc_utils::convertWpaBufToVector(
			anqp->hs20_connection_capability);
		aidl_hs20_anqp_data.osuProvidersList =
			misc_utils::convertWpaBufToVector(
			anqp->hs20_osu_providers_list);
#else
		aidl_hs20_anqp_data.operatorFriendlyName =
			misc_utils::convertWpaBufToVector(NULL);
		aidl_hs20_anqp_data.wanMetrics =
			misc_utils::convertWpaBufToVector(NULL);
		aidl_hs20_anqp_data.connectionCapability =
			misc_utils::convertWpaBufToVector(NULL);
		aidl_hs20_anqp_data.osuProvidersList =
			misc_utils::convertWpaBufToVector(NULL);
#endif /* CONFIG_HS20 */
	}

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
				   &ISupplicantStaIfaceCallback::onAnqpQueryDone,
				   std::placeholders::_1, macAddrToVec(bssid), aidl_anqp_data,
				   aidl_hs20_anqp_data));
}
#endif /* CONFIG_INTERWORKING */

/**
 * Notify all listeners about the end of an HS20 icon query.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 * @param bssid BSSID of the access point.
 * @param file_name Name of the icon file.
 * @param image Raw bytes of the icon file.
 * @param image_length Size of the the icon file.
 */
void AidlManager::notifyHs20IconQueryDone(
	struct wpa_supplicant *wpa_s, const u8 *bssid, const char *file_name,
	const u8 *image, u32 image_length)
{
	if (!wpa_s || !bssid || !file_name || !image)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onHs20IconQueryDone,
		std::placeholders::_1, macAddrToVec(bssid), file_name,
		std::vector<uint8_t>(image, image + image_length)));
}

/**
 * Notify all listeners about the reception of HS20 subscription
 * remediation notification from the server.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 * @param url URL of the server.
 * @param osu_method OSU method (OMA_DM or SOAP_XML_SPP).
 */
void AidlManager::notifyHs20RxSubscriptionRemediation(
	struct wpa_supplicant *wpa_s, const char *url, u8 osu_method)
{
	if (!wpa_s || !url)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	OsuMethod aidl_osu_method;
	if (osu_method & 0x1) {
		aidl_osu_method = OsuMethod::OMA_DM;
	} else if (osu_method & 0x2) {
		aidl_osu_method = OsuMethod::SOAP_XML_SPP;
	}
	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onHs20SubscriptionRemediation,
		std::placeholders::_1, macAddrToVec(wpa_s->bssid), aidl_osu_method, url));
}

/**
 * Notify all listeners about the reception of HS20 imminent death
 * notification from the server.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 * @param code Death reason code sent from server.
 * @param reauth_delay Reauthentication delay in seconds sent from server.
 * @param url URL of the server containing the reason text.
 */
void AidlManager::notifyHs20RxDeauthImminentNotice(
	struct wpa_supplicant *wpa_s, u8 code, u16 reauth_delay, const char *url)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onHs20DeauthImminentNotice,
		std::placeholders::_1, macAddrToVec(wpa_s->bssid), code,
		reauth_delay, misc_utils::charBufToString(url)));
}

/**
 * Notify all listeners about the reception of HS20 terms and conditions
 * acceptance notification from the server.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 * @param url URL of the T&C server.
 */
void AidlManager::notifyHs20RxTermsAndConditionsAcceptance(
	struct wpa_supplicant *wpa_s, const char *url)
{
	if (!wpa_s || !url)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname)
			== sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
			&ISupplicantStaIfaceCallback
			::onHs20TermsAndConditionsAcceptanceRequestedNotification,
			std::placeholders::_1, macAddrToVec(wpa_s->bssid), url));
}

/**
 * Notify all listeners about the reason code for disconnection from the
 * currently connected network.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is present.
 */
void AidlManager::notifyDisconnectReason(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	const u8 *bssid = wpa_s->bssid;
	if (is_zero_ether_addr(bssid)) {
		bssid = wpa_s->pending_bssid;
	}

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onDisconnected,
		std::placeholders::_1, macAddrToVec(bssid), wpa_s->disconnect_reason < 0,
		static_cast<StaIfaceReasonCode>(
			abs(wpa_s->disconnect_reason))));
}

/**
 * Notify all listeners about association reject from the access point to which
 * we are attempting to connect.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is present.
 * @param bssid bssid of AP that rejected the association.
 * @param timed_out flag to indicate failure is due to timeout
 * (auth, assoc, ...) rather than explicit rejection response from the AP.
 * @param assoc_resp_ie Association response IE.
 * @param assoc_resp_ie_len Association response IE length.
 */
void AidlManager::notifyAssocReject(struct wpa_supplicant *wpa_s,
	const u8 *bssid, u8 timed_out, const u8 *assoc_resp_ie, size_t assoc_resp_ie_len)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
#ifdef CONFIG_MBO
	struct wpa_bss *reject_bss;
#endif /* CONFIG_MBO */
	AssociationRejectionData aidl_assoc_reject_data{};

	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;
	if (wpa_s->current_ssid) {
		aidl_assoc_reject_data.ssid = std::vector<uint8_t>(
			wpa_s->current_ssid->ssid,
			wpa_s->current_ssid->ssid + wpa_s->current_ssid->ssid_len);
	}
	aidl_assoc_reject_data.bssid = macAddrToVec(bssid);
	aidl_assoc_reject_data.statusCode = static_cast<StaIfaceStatusCode>(
						wpa_s->assoc_status_code);
	if (timed_out) {
		aidl_assoc_reject_data.timedOut = true;
	}
#ifdef CONFIG_MBO
	if (wpa_s->drv_flags & WPA_DRIVER_FLAGS_SME) {
		reject_bss = wpa_s->current_bss;
	} else {
		reject_bss = wpa_bss_get_bssid(wpa_s, bssid);
	}
	if (reject_bss && assoc_resp_ie && assoc_resp_ie_len > 0) {
		if (wpa_s->assoc_status_code ==
			WLAN_STATUS_DENIED_POOR_CHANNEL_CONDITIONS) {
			const u8 *rssi_rej;
			rssi_rej = mbo_get_attr_from_ies(
					assoc_resp_ie,
					assoc_resp_ie_len,
					OCE_ATTR_ID_RSSI_BASED_ASSOC_REJECT);
			if (rssi_rej && rssi_rej[1] == 2) {
				wpa_printf(MSG_INFO,
					   "OCE: RSSI-based association rejection from "
					   MACSTR " Delta RSSI: %u, Retry Delay: %u bss rssi: %d",
					   MAC2STR(reject_bss->bssid),
					   rssi_rej[2], rssi_rej[3], reject_bss->level);
				aidl_assoc_reject_data.isOceRssiBasedAssocRejectAttrPresent = true;
				aidl_assoc_reject_data.oceRssiBasedAssocRejectData.deltaRssi
						= rssi_rej[2];
				aidl_assoc_reject_data.oceRssiBasedAssocRejectData.retryDelayS
						= rssi_rej[3];
			}
		} else if (wpa_s->assoc_status_code == WLAN_STATUS_ASSOC_REJECTED_TEMPORARILY
			  || wpa_s->assoc_status_code == WLAN_STATUS_AP_UNABLE_TO_HANDLE_NEW_STA) {
			const u8 *assoc_disallowed;
			assoc_disallowed = mbo_get_attr_from_ies(
							assoc_resp_ie,
							assoc_resp_ie_len,
							MBO_ATTR_ID_ASSOC_DISALLOW);
			if (assoc_disallowed && assoc_disallowed[1] == 1) {
				wpa_printf(MSG_INFO,
					"MBO: association disallowed indication from "
					MACSTR " Reason: %d",
					MAC2STR(reject_bss->bssid),
					assoc_disallowed[2]);
				aidl_assoc_reject_data.isMboAssocDisallowedReasonCodePresent = true;
				aidl_assoc_reject_data.mboAssocDisallowedReason
					= static_cast<MboAssocDisallowedReasonCode>(assoc_disallowed[2]);
			}
		}
	}
#endif /* CONFIG_MBO */

	const std::function<
			ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
			func = std::bind(
			&ISupplicantStaIfaceCallback::onAssociationRejected,
			std::placeholders::_1, aidl_assoc_reject_data);
	callWithEachStaIfaceCallback(aidl_ifname, func);
}

void AidlManager::notifyAuthTimeout(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	const std::string ifname(wpa_s->ifname);
	if (sta_iface_object_map_.find(ifname) == sta_iface_object_map_.end())
		return;

	const u8 *bssid = wpa_s->bssid;
	if (is_zero_ether_addr(bssid)) {
		bssid = wpa_s->pending_bssid;
	}
	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onAuthenticationTimeout,
		std::placeholders::_1, macAddrToVec(bssid)));
}

void AidlManager::notifyBssidChanged(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	const std::string ifname(wpa_s->ifname);
	if (sta_iface_object_map_.find(ifname) == sta_iface_object_map_.end())
		return;

	// wpa_supplicant does not explicitly give us the reason for bssid
	// change, but we figure that out from what is set out of |wpa_s->bssid|
	// & |wpa_s->pending_bssid|.
	const u8 *bssid;
	BssidChangeReason reason;
	if (is_zero_ether_addr(wpa_s->bssid) &&
		!is_zero_ether_addr(wpa_s->pending_bssid)) {
		bssid = wpa_s->pending_bssid;
		reason = BssidChangeReason::ASSOC_START;
	} else if (
		!is_zero_ether_addr(wpa_s->bssid) &&
		is_zero_ether_addr(wpa_s->pending_bssid)) {
		bssid = wpa_s->bssid;
		reason = BssidChangeReason::ASSOC_COMPLETE;
	} else if (
		is_zero_ether_addr(wpa_s->bssid) &&
		is_zero_ether_addr(wpa_s->pending_bssid)) {
		bssid = wpa_s->pending_bssid;
		reason = BssidChangeReason::DISASSOC;
	} else {
		wpa_printf(MSG_ERROR, "Unknown bssid change reason");
		return;
	}

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
				   &ISupplicantStaIfaceCallback::onBssidChanged,
				   std::placeholders::_1, reason, macAddrToVec(bssid)));
}

void AidlManager::notifyWpsEventFail(
	struct wpa_supplicant *wpa_s, uint8_t *peer_macaddr, uint16_t config_error,
	uint16_t error_indication)
{
	if (!wpa_s || !peer_macaddr)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onWpsEventFail,
		std::placeholders::_1, macAddrToVec(peer_macaddr),
		static_cast<WpsConfigError>(
			config_error),
		static_cast<WpsErrorIndication>(
			error_indication)));
}

void AidlManager::notifyWpsEventSuccess(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
				   &ISupplicantStaIfaceCallback::onWpsEventSuccess,
				   std::placeholders::_1));
}

void AidlManager::notifyWpsEventPbcOverlap(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onWpsEventPbcOverlap,
		std::placeholders::_1));
}

void AidlManager::notifyP2pDeviceFound(
	struct wpa_supplicant *wpa_s, const u8 *addr,
	const struct p2p_peer_info *info, const u8 *peer_wfd_device_info,
	u8 peer_wfd_device_info_len, const u8 *peer_wfd_r2_device_info,
	u8 peer_wfd_r2_device_info_len)
{
	if (!wpa_s || !addr || !info)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	std::vector<uint8_t> aidl_peer_wfd_device_info(kWfdDeviceInfoLen);
	if (peer_wfd_device_info) {
		if (peer_wfd_device_info_len != kWfdDeviceInfoLen) {
			wpa_printf(
				MSG_ERROR, "Unexpected WFD device info len: %d",
				peer_wfd_device_info_len);
		} else {
			os_memcpy(
				aidl_peer_wfd_device_info.data(),
				peer_wfd_device_info, kWfdDeviceInfoLen);
		}
	}

	std::vector<uint8_t> aidl_peer_wfd_r2_device_info;
	if (peer_wfd_r2_device_info) {
		if (peer_wfd_r2_device_info_len != kWfdR2DeviceInfoLen) {
			wpa_printf(
				MSG_ERROR, "Unexpected WFD R2 device info len: %d",
				peer_wfd_r2_device_info_len);
			return;
		} else {
			std::copy(peer_wfd_r2_device_info,
			    peer_wfd_r2_device_info + peer_wfd_r2_device_info_len,
			    std::back_inserter(aidl_peer_wfd_r2_device_info));
		}
	}

	std::vector<uint8_t> aidl_vendor_elems;
	if (NULL != info->vendor_elems && wpabuf_len(info->vendor_elems) > 0) {
		aidl_vendor_elems.reserve(wpabuf_len(info->vendor_elems));
		std::copy(wpabuf_head_u8(info->vendor_elems),
			wpabuf_head_u8(info->vendor_elems)
				+ wpabuf_len(info->vendor_elems),
			std::back_inserter(aidl_vendor_elems));
	}

	if (isAidlServiceVersionAtLeast(3) && isAidlClientVersionAtLeast(3)) {
		P2pDeviceFoundEventParams params;
		params.srcAddress = macAddrToArray(addr);
		params.p2pDeviceAddress = macAddrToArray(info->p2p_device_addr);
		params.primaryDeviceType = byteArrToVec(info->pri_dev_type, 8);
		params.deviceName = misc_utils::charBufToString(info->device_name);
		params.configMethods = info->config_methods;
		params.deviceCapabilities = info->dev_capab;
		params.groupCapabilities = info->group_capab;
		params.wfdDeviceInfo = aidl_peer_wfd_device_info;
		params.wfdR2DeviceInfo = aidl_peer_wfd_r2_device_info;
		params.vendorElemBytes = aidl_vendor_elems;
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onDeviceFoundWithParams,
			std::placeholders::_1, params));
	} else {
	    // Use legacy callback if service or client interface version < 3
		const std::function<
			ndk::ScopedAStatus(std::shared_ptr<ISupplicantP2pIfaceCallback>)>
			func = std::bind(
			&ISupplicantP2pIfaceCallback::onDeviceFoundWithVendorElements,
			std::placeholders::_1, macAddrToVec(addr), macAddrToVec(info->p2p_device_addr),
			byteArrToVec(info->pri_dev_type, 8), misc_utils::charBufToString(info->device_name),
			static_cast<WpsConfigMethods>(info->config_methods),
			info->dev_capab, static_cast<P2pGroupCapabilityMask>(info->group_capab), aidl_peer_wfd_device_info,
			aidl_peer_wfd_r2_device_info, aidl_vendor_elems);
		callWithEachP2pIfaceCallback(wpa_s->ifname, func);
	}
}

void AidlManager::notifyP2pDeviceLost(
	struct wpa_supplicant *wpa_s, const u8 *p2p_device_addr)
{
	if (!wpa_s || !p2p_device_addr)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
				   &ISupplicantP2pIfaceCallback::onDeviceLost,
				   std::placeholders::_1, macAddrToVec(p2p_device_addr)));
}

void AidlManager::notifyP2pFindStopped(struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
				   &ISupplicantP2pIfaceCallback::onFindStopped,
				   std::placeholders::_1));
}

void AidlManager::notifyP2pGoNegReq(
	struct wpa_supplicant *wpa_s, const u8 *src_addr, u16 dev_passwd_id,
	u8 /* go_intent */)
{
	if (!wpa_s || !src_addr)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onGoNegotiationRequest,
		std::placeholders::_1, macAddrToVec(src_addr),
		static_cast<WpsDevPasswordId>(
			dev_passwd_id)));
}

void AidlManager::notifyP2pGoNegCompleted(
	struct wpa_supplicant *wpa_s, const struct p2p_go_neg_results *res)
{
	if (!wpa_s || !res)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onGoNegotiationCompleted,
		std::placeholders::_1,
		static_cast<P2pStatusCode>(
			res->status)));
}

void AidlManager::notifyP2pGroupFormationFailure(
	struct wpa_supplicant *wpa_s, const char *reason)
{
	if (!wpa_s || !reason)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onGroupFormationFailure,
		std::placeholders::_1, reason));
}

void AidlManager::notifyP2pGroupStarted(
	struct wpa_supplicant *wpa_group_s, const struct wpa_ssid *ssid,
	int persistent, int client, const u8 *ip)
{
	if (!wpa_group_s || !wpa_group_s->parent || !ssid)
		return;

	// For group notifications, need to use the parent iface for callbacks.
	struct wpa_supplicant *wpa_s = getTargetP2pIfaceForGroup(wpa_group_s);
	if (!wpa_s)
		return;

	uint32_t aidl_freq = wpa_group_s->current_bss
				 ? wpa_group_s->current_bss->freq
				 : wpa_group_s->assoc_freq;
	std::vector<uint8_t> aidl_psk(32);
	if (ssid->psk_set) {
		aidl_psk.assign(ssid->psk, ssid->psk + 32);
	}
	bool aidl_is_go = (client == 0 ? true : false);
	bool aidl_is_persistent = (persistent == 1 ? true : false);

	// notify the group device again to ensure the framework knowing this device.
	struct p2p_data *p2p = wpa_s->global->p2p;
	struct p2p_device *dev = p2p_get_device(p2p, wpa_group_s->go_dev_addr);
	if (NULL != dev) {
		wpa_printf(MSG_DEBUG, "P2P: Update GO device on group started.");
		p2p->cfg->dev_found(p2p->cfg->cb_ctx, wpa_group_s->go_dev_addr,
				&dev->info, !(dev->flags & P2P_DEV_REPORTED_ONCE));
		dev->flags |= P2P_DEV_REPORTED | P2P_DEV_REPORTED_ONCE;
	}

	P2pGroupStartedEventParams params;
	params.groupInterfaceName = misc_utils::charBufToString(wpa_group_s->ifname);
	params.isGroupOwner = aidl_is_go;
	params.ssid = byteArrToVec(ssid->ssid, ssid->ssid_len);
	params.frequencyMHz = aidl_freq;
	params.psk = aidl_psk;
	params.passphrase = misc_utils::charBufToString(ssid->passphrase);
	params.isPersistent = aidl_is_persistent;
	params.goDeviceAddress = macAddrToArray(wpa_group_s->go_dev_addr);
	params.goInterfaceAddress = aidl_is_go ? macAddrToArray(wpa_group_s->own_addr) :
			macAddrToArray(wpa_group_s->current_bss->bssid);
	if (NULL != ip && !aidl_is_go) {
		params.isP2pClientEapolIpAddressInfoPresent = true;
		os_memcpy(&params.p2pClientIpInfo.ipAddressClient, &ip[0], 4);
		os_memcpy(&params.p2pClientIpInfo.ipAddressMask, &ip[4], 4);
		os_memcpy(&params.p2pClientIpInfo.ipAddressGo, &ip[8], 4);

		wpa_printf(MSG_DEBUG, "P2P: IP Address allocated - CLI: 0x%x MASK: 0x%x GO: 0x%x",
			   params.p2pClientIpInfo.ipAddressClient,
			   params.p2pClientIpInfo.ipAddressMask,
			   params.p2pClientIpInfo.ipAddressGo);
        }
	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(&ISupplicantP2pIfaceCallback::onGroupStartedWithParams,
		std::placeholders::_1, params));
}

void AidlManager::notifyP2pGroupRemoved(
	struct wpa_supplicant *wpa_group_s, const struct wpa_ssid *ssid,
	const char *role)
{
	if (!wpa_group_s || !wpa_group_s->parent || !ssid || !role)
		return;

	// For group notifications, need to use the parent iface for callbacks.
	struct wpa_supplicant *wpa_s = getTargetP2pIfaceForGroup(wpa_group_s);
	if (!wpa_s)
		return;

	bool aidl_is_go = (std::string(role) == "GO");

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onGroupRemoved,
		std::placeholders::_1, misc_utils::charBufToString(wpa_group_s->ifname), aidl_is_go));
}

void AidlManager::notifyP2pInvitationReceived(
	struct wpa_supplicant *wpa_s, const u8 *sa, const u8 *go_dev_addr,
	const u8 *bssid, int id, int op_freq)
{
	if (!wpa_s || !sa || !go_dev_addr || !bssid)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	int aidl_network_id;
	if (id < 0) {
		aidl_network_id = UINT32_MAX;
	}
	aidl_network_id = id;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onInvitationReceived,
		std::placeholders::_1, macAddrToVec(sa), macAddrToVec(go_dev_addr),
		macAddrToVec(bssid), aidl_network_id, op_freq));
}

void AidlManager::notifyP2pInvitationResult(
	struct wpa_supplicant *wpa_s, int status, const u8 *bssid)
{
	if (!wpa_s)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onInvitationResult,
		std::placeholders::_1, bssid ? macAddrToVec(bssid) : kZeroBssid,
		static_cast<P2pStatusCode>(
			status)));
}

void AidlManager::notifyP2pProvisionDiscovery(
	struct wpa_supplicant *wpa_s, const u8 *dev_addr, int request,
	enum p2p_prov_disc_status status, u16 config_methods,
	unsigned int generated_pin, const char *group_ifname)
{
	if (!wpa_s || !dev_addr)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	std::string aidl_generated_pin;
	if (generated_pin > 0) {
		aidl_generated_pin =
			misc_utils::convertWpsPinToString(generated_pin);
	}
	bool aidl_is_request = (request == 1);

	if (isAidlServiceVersionAtLeast(3) && isAidlClientVersionAtLeast(3)) {
		P2pProvisionDiscoveryCompletedEventParams params;
		params.p2pDeviceAddress =  macAddrToArray(dev_addr);
		params.isRequest = aidl_is_request;
		params.status = static_cast<P2pProvDiscStatusCode>(status);
		params.configMethods = config_methods;
		params.generatedPin = aidl_generated_pin;
		if (group_ifname != NULL) {
			params.groupInterfaceName = misc_utils::charBufToString(group_ifname);
		}
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onProvisionDiscoveryCompletedEvent,
			std::placeholders::_1, params));
	} else {
		// Use legacy callback if service or client interface version < 3
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onProvisionDiscoveryCompleted,
			std::placeholders::_1, macAddrToVec(dev_addr), aidl_is_request,
			static_cast<P2pProvDiscStatusCode>(status),
			static_cast<WpsConfigMethods>(config_methods), aidl_generated_pin));
	}
}

void AidlManager::notifyP2pSdResponse(
	struct wpa_supplicant *wpa_s, const u8 *sa, u16 update_indic,
	const u8 *tlvs, size_t tlvs_len)
{
	if (!wpa_s || !sa || !tlvs)
		return;

	if (p2p_iface_object_map_.find(wpa_s->ifname) ==
		p2p_iface_object_map_.end())
		return;

	callWithEachP2pIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantP2pIfaceCallback::onServiceDiscoveryResponse,
		std::placeholders::_1, macAddrToVec(sa), update_indic,
		byteArrToVec(tlvs, tlvs_len)));
}

void AidlManager::notifyApStaAuthorized(
	struct wpa_supplicant *wpa_group_s, const u8 *sta, const u8 *p2p_dev_addr,
	const u8 *ip)
{
	if (!wpa_group_s || !wpa_group_s->parent || !sta)
		return;
	wpa_supplicant *wpa_s = getTargetP2pIfaceForGroup(wpa_group_s);
	if (!wpa_s)
		return;

	if (isAidlServiceVersionAtLeast(3) && isAidlClientVersionAtLeast(3)) {
		P2pPeerClientJoinedEventParams params;
		params.groupInterfaceName = misc_utils::charBufToString(wpa_group_s->ifname);
		params.clientInterfaceAddress = macAddrToArray(sta);
		params.clientDeviceAddress = p2p_dev_addr ?
				macAddrToArray(p2p_dev_addr) : macAddrToArray(kZeroBssid.data());
		int aidl_ip = 0;
		if (NULL != ip) {
			os_memcpy(&aidl_ip, &ip[0], 4);
		}
		params.clientIpAddress = aidl_ip;
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onPeerClientJoined,
			std::placeholders::_1, params));
	} else {
		// Use legacy callback if service or client interface version < 3
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onStaAuthorized,
			std::placeholders::_1, macAddrToVec(sta),
			p2p_dev_addr ? macAddrToVec(p2p_dev_addr) : kZeroBssid));
	}
}

void AidlManager::notifyApStaDeauthorized(
	struct wpa_supplicant *wpa_group_s, const u8 *sta, const u8 *p2p_dev_addr)
{
	if (!wpa_group_s || !wpa_group_s->parent || !sta)
		return;
	wpa_supplicant *wpa_s = getTargetP2pIfaceForGroup(wpa_group_s);
	if (!wpa_s)
		return;

	if (isAidlServiceVersionAtLeast(3) && isAidlClientVersionAtLeast(3)) {
		P2pPeerClientDisconnectedEventParams params;
		params.groupInterfaceName = misc_utils::charBufToString(wpa_group_s->ifname);
		params.clientInterfaceAddress = macAddrToArray(sta);
		params.clientDeviceAddress = p2p_dev_addr ?
				macAddrToArray(p2p_dev_addr) : macAddrToArray(kZeroBssid.data());

		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onPeerClientDisconnected,
			std::placeholders::_1, params));
	} else {
		// Use legacy callback if service or client interface version < 3
		callWithEachP2pIfaceCallback(
			misc_utils::charBufToString(wpa_s->ifname),
			std::bind(
			&ISupplicantP2pIfaceCallback::onStaDeauthorized,
			std::placeholders::_1, macAddrToVec(sta),
			p2p_dev_addr ? macAddrToVec(p2p_dev_addr) : kZeroBssid));
	}
}

void AidlManager::notifyExtRadioWorkStart(
	struct wpa_supplicant *wpa_s, uint32_t id)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onExtRadioWorkStart,
		std::placeholders::_1, id));
}

void AidlManager::notifyExtRadioWorkTimeout(
	struct wpa_supplicant *wpa_s, uint32_t id)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onExtRadioWorkTimeout,
		std::placeholders::_1, id));
}

void AidlManager::notifyEapError(struct wpa_supplicant *wpa_s, int error_code)
{
	if (!wpa_s)
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(
		&ISupplicantStaIfaceCallback::onEapFailure,
		std::placeholders::_1,
		macAddrToVec(wpa_s->bssid), error_code));
}

/**
 * Notify listener about a new DPP configuration received success event
 *
 * @param ifname Interface name
 * @param config Configuration object
 */
void AidlManager::notifyDppConfigReceived(struct wpa_supplicant *wpa_s,
		struct wpa_ssid *config, bool conn_status_requested)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
	DppConfigurationData aidl_dpp_config_data = {};

	if ((config->key_mgmt & WPA_KEY_MGMT_SAE) &&
			(wpa_s->drv_flags & WPA_DRIVER_FLAGS_SAE)) {
		aidl_dpp_config_data.securityAkm = DppAkm::SAE;
	} else if (config->key_mgmt & WPA_KEY_MGMT_PSK) {
		aidl_dpp_config_data.securityAkm = DppAkm::PSK;
	} else if (config->key_mgmt & WPA_KEY_MGMT_DPP) {
		aidl_dpp_config_data.securityAkm = DppAkm::DPP;
	} else {
		/* Unsupported AKM */
		wpa_printf(MSG_ERROR, "DPP: Error: Unsupported AKM 0x%X",
				config->key_mgmt);
		notifyDppFailure(wpa_s, DppFailureCode::NOT_SUPPORTED);
		return;
	}

	aidl_dpp_config_data.password = misc_utils::charBufToString(config->passphrase);
	aidl_dpp_config_data.psk = byteArrToVec(config->psk, 32);
	std::vector<uint8_t> aidl_ssid(
		config->ssid,
		config->ssid + config->ssid_len);
	aidl_dpp_config_data.ssid = aidl_ssid;

	if (aidl_dpp_config_data.securityAkm == DppAkm::DPP) {
		std::string connector_str = misc_utils::charBufToString(config->dpp_connector);
		aidl_dpp_config_data.dppConnectionKeys.connector
			= std::vector<uint8_t>(connector_str.begin(), connector_str.end());
		aidl_dpp_config_data.dppConnectionKeys.cSign
			= byteArrToVec(config->dpp_csign, config->dpp_csign_len);
		aidl_dpp_config_data.dppConnectionKeys.netAccessKey
			= byteArrToVec(config->dpp_netaccesskey, config->dpp_netaccesskey_len);
	}
	aidl_dpp_config_data.connStatusRequested = conn_status_requested;

	/* At this point, the network is already registered, notify about new
	 * received configuration
	 */
	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(
					&ISupplicantStaIfaceCallback::onDppConfigReceived,
					std::placeholders::_1, aidl_dpp_config_data));
}

/**
 * Notify listener about a DPP configuration sent success event
 *
 * @param ifname Interface name
 */
void AidlManager::notifyDppConfigSent(struct wpa_supplicant *wpa_s)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);

	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(&ISupplicantStaIfaceCallback::onDppSuccessConfigSent,
					std::placeholders::_1));
}

DppStatusErrorCode convertSupplicantDppStatusErrorCodeToAidl(
	enum dpp_status_error code)
{
	switch (code) {
		case DPP_STATUS_OK:
			return DppStatusErrorCode::SUCCESS;
		case DPP_STATUS_NOT_COMPATIBLE:
			return DppStatusErrorCode::NOT_COMPATIBLE;
		case DPP_STATUS_AUTH_FAILURE:
			return DppStatusErrorCode::AUTH_FAILURE;
		case DPP_STATUS_UNWRAP_FAILURE:
			return DppStatusErrorCode::UNWRAP_FAILURE;
		case DPP_STATUS_BAD_GROUP:
			return DppStatusErrorCode::BAD_GROUP;
		case DPP_STATUS_CONFIGURE_FAILURE:
			return DppStatusErrorCode::CONFIGURE_FAILURE;
		case DPP_STATUS_RESPONSE_PENDING:
			return DppStatusErrorCode::RESPONSE_PENDING;
		case DPP_STATUS_INVALID_CONNECTOR:
			return DppStatusErrorCode::INVALID_CONNECTOR;
		case DPP_STATUS_CONFIG_REJECTED:
			return DppStatusErrorCode::CONFIG_REJECTED;
		case DPP_STATUS_NO_MATCH:
			return DppStatusErrorCode::NO_MATCH;
		case DPP_STATUS_NO_AP:
			return DppStatusErrorCode::NO_AP;
		case DPP_STATUS_CONFIGURE_PENDING:
			return DppStatusErrorCode::CONFIGURE_PENDING;
		case DPP_STATUS_CSR_NEEDED:
			return DppStatusErrorCode::CSR_NEEDED;
		case DPP_STATUS_CSR_BAD:
			return DppStatusErrorCode::CSR_BAD;
		case DPP_STATUS_NEW_KEY_NEEDED:
			return DppStatusErrorCode::NEW_KEY_NEEDED;
		default:
			return DppStatusErrorCode::UNKNOWN;
	}
}

void AidlManager::notifyDppConnectionStatusSent(struct wpa_supplicant *wpa_s,
		enum dpp_status_error result)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(&ISupplicantStaIfaceCallback::onDppConnectionStatusResultSent,
					std::placeholders::_1,
					convertSupplicantDppStatusErrorCodeToAidl(result)));
}

/**
 * Notify listener about a DPP failure event
 *
 * @param ifname Interface name
 * @param code Status code
 */
void AidlManager::notifyDppFailure(struct wpa_supplicant *wpa_s,
		android::hardware::wifi::supplicant::DppFailureCode code) {
	notifyDppFailure(wpa_s, code, NULL, NULL, NULL, 0);
}

/**
 * Notify listener about a DPP failure event
 *
 * @param ifname Interface name
 * @param code Status code
 */
void AidlManager::notifyDppFailure(struct wpa_supplicant *wpa_s,
		DppFailureCode code, const char *ssid, const char *channel_list,
		unsigned short band_list[], int size) {
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
	std::vector<char16_t> band_list_vec(band_list, band_list + size);

	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(&ISupplicantStaIfaceCallback::onDppFailure,
					std::placeholders::_1, code, misc_utils::charBufToString(ssid),
					misc_utils::charBufToString(channel_list), band_list_vec));
}

/**
 * Notify listener about a DPP progress event
 *
 * @param ifname Interface name
 * @param code Status code
 */
void AidlManager::notifyDppProgress(
		struct wpa_supplicant *wpa_s, DppProgressCode code) {
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);

	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(&ISupplicantStaIfaceCallback::onDppProgress,
					std::placeholders::_1, code));
}

/**
 * Notify listener about a DPP success event
 *
 * @param ifname Interface name
 * @param code Status code
 */
void AidlManager::notifyDppSuccess(struct wpa_supplicant *wpa_s, DppEventType code)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);

	callWithEachStaIfaceCallback(aidl_ifname,
			std::bind(&ISupplicantStaIfaceCallback::onDppSuccess,
					std::placeholders::_1, code));
}

/**
 * Notify listener about a PMK cache added event
 *
 * @param ifname Interface name
 * @param entry PMK cache entry
 */
void AidlManager::notifyPmkCacheAdded(
	struct wpa_supplicant *wpa_s, struct rsn_pmksa_cache_entry *pmksa_entry)
{
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);

	PmkSaCacheData aidl_pmksa_data = {};
	aidl_pmksa_data.bssid = macAddrToArray(pmksa_entry->aa);
	// Serialize PmkCacheEntry into blob.
	std::stringstream ss(
		std::stringstream::in | std::stringstream::out | std::stringstream::binary);
	misc_utils::serializePmkCacheEntry(ss, pmksa_entry);
	std::vector<uint8_t> serializedEntry(
		std::istreambuf_iterator<char>(ss), {});
	aidl_pmksa_data.serializedEntry = serializedEntry;
	aidl_pmksa_data.expirationTimeInSec = pmksa_entry->expiration;

	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
		func = std::bind(
		&ISupplicantStaIfaceCallback::onPmkSaCacheAdded,
		std::placeholders::_1, aidl_pmksa_data);
	callWithEachStaIfaceCallback(aidl_ifname, func);
}

#ifdef CONFIG_WNM
BssTmStatusCode convertSupplicantBssTmStatusToAidl(
	enum bss_trans_mgmt_status_code bss_tm_status)
{
	switch (bss_tm_status) {
		case WNM_BSS_TM_ACCEPT:
			return BssTmStatusCode::ACCEPT;
		case WNM_BSS_TM_REJECT_UNSPECIFIED:
			return BssTmStatusCode::REJECT_UNSPECIFIED;
		case WNM_BSS_TM_REJECT_INSUFFICIENT_BEACON:
			return BssTmStatusCode::REJECT_INSUFFICIENT_BEACON;
		case WNM_BSS_TM_REJECT_INSUFFICIENT_CAPABITY:
			return BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY;
		case WNM_BSS_TM_REJECT_UNDESIRED:
			return BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED;
		case WNM_BSS_TM_REJECT_DELAY_REQUEST:
			return BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST;
		case WNM_BSS_TM_REJECT_STA_CANDIDATE_LIST_PROVIDED:
			return BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED;
		case WNM_BSS_TM_REJECT_NO_SUITABLE_CANDIDATES:
			return BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES;
		case WNM_BSS_TM_REJECT_LEAVING_ESS:
			return BssTmStatusCode::REJECT_LEAVING_ESS;
		default:
			return BssTmStatusCode::REJECT_UNSPECIFIED;
	}
}

BssTmDataFlagsMask setBssTmDataFlagsMask(struct wpa_supplicant *wpa_s)
{
	uint32_t flags = 0;

	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_BSS_TERMINATION_INCLUDED) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED);
	}
	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_ESS_DISASSOC_IMMINENT) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT);
	}
	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_DISASSOC_IMMINENT) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT);
	}
	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_ABRIDGED) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::WNM_MODE_ABRIDGED);
	}
	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_PREF_CAND_LIST_INCLUDED) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED);
	}
#ifdef CONFIG_MBO
	if (wpa_s->wnm_mbo_assoc_retry_delay_present) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED);
	}
	if (wpa_s->wnm_mbo_trans_reason_present) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED);
	}
	if (wpa_s->wnm_mbo_cell_pref_present) {
		flags |= static_cast<uint32_t>(BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED);
	}
#endif
	return static_cast<BssTmDataFlagsMask>(flags);
}

uint32_t getBssTmDataAssocRetryDelayMs(struct wpa_supplicant *wpa_s)
{
	uint32_t beacon_int;
	uint32_t duration_ms = 0;

	if (wpa_s->current_bss)
		beacon_int = wpa_s->current_bss->beacon_int;
	else
		beacon_int = 100; /* best guess */

	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_DISASSOC_IMMINENT) {
		// number of tbtts to milliseconds
		duration_ms = wpa_s->wnm_dissoc_timer * beacon_int * 128 / 125;
	}
	if (wpa_s->wnm_mode & WNM_BSS_TM_REQ_BSS_TERMINATION_INCLUDED) {
		//wnm_bss_termination_duration contains 12 bytes of BSS
		//termination duration subelement. Format of IE is
		// Sub eid | Length | BSS termination TSF | Duration
		//	1	 1		 8		2
		// Duration indicates number of minutes for which BSS is not
		// present.
		duration_ms = WPA_GET_LE16(wpa_s->wnm_bss_termination_duration + 10);
		// minutes to milliseconds
		duration_ms = duration_ms * 60 * 1000;
	}
#ifdef CONFIG_MBO
	if (wpa_s->wnm_mbo_assoc_retry_delay_present) {
		// number of seconds to milliseconds
		duration_ms = wpa_s->wnm_mbo_assoc_retry_delay_sec * 1000;
	}
#endif

	return duration_ms;
}
#endif

/**
 * Notify listener about the status of BSS transition management
 * request frame handling.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface on which
 * the network is present.
 */
void AidlManager::notifyBssTmStatus(struct wpa_supplicant *wpa_s)
{
#ifdef CONFIG_WNM
	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
	BssTmData aidl_bsstm_data{};

	aidl_bsstm_data.status = convertSupplicantBssTmStatusToAidl(wpa_s->bss_tm_status);
	aidl_bsstm_data.flags = setBssTmDataFlagsMask(wpa_s);
	aidl_bsstm_data.assocRetryDelayMs = getBssTmDataAssocRetryDelayMs(wpa_s);
#ifdef CONFIG_MBO
	if (wpa_s->wnm_mbo_cell_pref_present) {
		aidl_bsstm_data.mboCellPreference = static_cast
			<MboCellularDataConnectionPrefValue>
			(wpa_s->wnm_mbo_cell_preference);
	}
	if (wpa_s->wnm_mbo_trans_reason_present) {
		aidl_bsstm_data.mboTransitionReason =
			static_cast<MboTransitionReasonCode>
			(wpa_s->wnm_mbo_transition_reason);
	}
#endif

	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
		func = std::bind(
		&ISupplicantStaIfaceCallback::onBssTmHandlingDone,
		std::placeholders::_1, aidl_bsstm_data);
	callWithEachStaIfaceCallback(aidl_ifname, func);
#endif
}

TransitionDisableIndication setTransitionDisableFlagsMask(u8 bitmap)
{
	uint32_t flags = 0;

	if (bitmap & TRANSITION_DISABLE_WPA3_PERSONAL) {
		flags |= static_cast<uint32_t>(TransitionDisableIndication::
			USE_WPA3_PERSONAL);
		bitmap &= ~TRANSITION_DISABLE_WPA3_PERSONAL;
	}
	if (bitmap & TRANSITION_DISABLE_SAE_PK) {
		flags |= static_cast<uint32_t>(TransitionDisableIndication::
			USE_SAE_PK);
		bitmap &= ~TRANSITION_DISABLE_SAE_PK;
	}
	if (bitmap & TRANSITION_DISABLE_WPA3_ENTERPRISE) {
		flags |= static_cast<uint32_t>(TransitionDisableIndication::
			USE_WPA3_ENTERPRISE);
		bitmap &= ~TRANSITION_DISABLE_WPA3_ENTERPRISE;
	}
	if (bitmap & TRANSITION_DISABLE_ENHANCED_OPEN) {
		flags |= static_cast<uint32_t>(TransitionDisableIndication::
			USE_ENHANCED_OPEN);
		bitmap &= ~TRANSITION_DISABLE_ENHANCED_OPEN;
	}

	if (bitmap != 0) {
		wpa_printf(MSG_WARNING, "Unhandled transition disable bit: 0x%x", bitmap);
	}

	return static_cast<TransitionDisableIndication>(flags);
}

void AidlManager::notifyTransitionDisable(struct wpa_supplicant *wpa_s,
	struct wpa_ssid *ssid, u8 bitmap)
{
	TransitionDisableIndication flag = setTransitionDisableFlagsMask(bitmap);
	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaNetworkCallback>)>
		func = std::bind(
		&ISupplicantStaNetworkCallback::onTransitionDisable,
		std::placeholders::_1, flag);

	callWithEachStaNetworkCallback(
		misc_utils::charBufToString(wpa_s->ifname), ssid->id, func);
}

void AidlManager::notifyNetworkNotFound(struct wpa_supplicant *wpa_s)
{
	std::vector<uint8_t> aidl_ssid;

	if (!wpa_s->current_ssid) {
		wpa_printf(MSG_ERROR, "Current network NULL. Drop WPA_EVENT_NETWORK_NOT_FOUND!");
		return;
	}

	aidl_ssid.assign(
			wpa_s->current_ssid->ssid,
			wpa_s->current_ssid->ssid + wpa_s->current_ssid->ssid_len);

	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
		func = std::bind(
		&ISupplicantStaIfaceCallback::onNetworkNotFound,
		std::placeholders::_1, aidl_ssid);
	callWithEachStaIfaceCallback(misc_utils::charBufToString(wpa_s->ifname), func);
}

void AidlManager::notifyFrequencyChanged(struct wpa_supplicant *wpa_s, int frequency)
{
	if (!wpa_s)
		return;

	std::string aidl_ifname = misc_utils::charBufToString(wpa_s->ifname);
	struct wpa_supplicant *wpa_p2pdev_s = getTargetP2pIfaceForGroup(wpa_s);
	if (wpa_p2pdev_s) {
		// Notify frequency changed event on P2P interface
		const std::function<
			ndk::ScopedAStatus(std::shared_ptr<ISupplicantP2pIfaceCallback>)>
			func = std::bind(&ISupplicantP2pIfaceCallback::onGroupFrequencyChanged,
			std::placeholders::_1, aidl_ifname, frequency);
		// For group notifications, need to use the parent iface for callbacks.
		callWithEachP2pIfaceCallback(misc_utils::charBufToString(wpa_p2pdev_s->ifname), func);
	} else if (wpa_s->current_ssid) {
		// Notify frequency changed event on STA interface
		const std::function<
			ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
			func = std::bind(
			&ISupplicantStaIfaceCallback::onBssFrequencyChanged,
			std::placeholders::_1, frequency);
		callWithEachStaIfaceCallback(aidl_ifname, func);
	} else {
		wpa_printf(MSG_INFO, "Drop frequency changed event");
		return;
        }
}

void AidlManager::notifyCertification(struct wpa_supplicant *wpa_s,
		int depth, const char *subject,
		const char *altsubject[],
		int num_altsubject,
		const char *cert_hash,
		const struct wpabuf *cert)
{
	if (!wpa_s->current_ssid) {
		wpa_printf(MSG_ERROR, "Current network NULL. Drop Certification event!");
		return;
	}
	struct wpa_ssid *current_ssid = wpa_s->current_ssid;
	if (!wpa_key_mgmt_wpa_ieee8021x(current_ssid->key_mgmt)) {
		return;
	}
	if (NULL == subject || NULL == cert_hash || NULL == cert) {
		wpa_printf(MSG_ERROR,
				"Incomplete certificate information. Drop Certification event!");
		return;
	}
	if (current_ssid->eap.cert.ca_cert) {
		return;
	}

	wpa_printf(MSG_DEBUG, "notifyCertification: depth=%d subject=%s hash=%s cert-size=%zu",
			depth, subject, cert_hash, cert->used);
	std::vector<uint8_t> subjectBlob(subject, subject + strlen(subject));
	std::vector<uint8_t> certHashBlob(cert_hash, cert_hash + strlen(cert_hash));
	std::vector<uint8_t> certBlob(cert->buf, cert->buf + cert->used);

	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaNetworkCallback>)>
		func = std::bind(
		&ISupplicantStaNetworkCallback::onServerCertificateAvailable,
		std::placeholders::_1,
		depth,
		subjectBlob,
		certHashBlob,
		certBlob);

	callWithEachStaNetworkCallback(
		misc_utils::charBufToString(wpa_s->ifname), current_ssid->id, func);
}

void AidlManager::notifyAuxiliaryEvent(struct wpa_supplicant *wpa_s,
	AuxiliarySupplicantEventCode event_code, const char *reason_string)
{
	if (!wpa_s)
		return;

	const std::function<
		ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
		func = std::bind(
		&ISupplicantStaIfaceCallback::onAuxiliarySupplicantEvent,
		std::placeholders::_1, event_code, macAddrToVec(wpa_s->bssid),
		misc_utils::charBufToString(reason_string));
	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), func);
}

/**
 * Retrieve the |ISupplicantP2pIface| aidl object reference using the provided
 * ifname.
 *
 * @param ifname Name of the corresponding interface.
 * @param iface_object Aidl reference corresponding to the iface.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::getP2pIfaceAidlObjectByIfname(
	const std::string &ifname, std::shared_ptr<ISupplicantP2pIface> *iface_object)
{
	if (ifname.empty() || !iface_object)
		return 1;

	auto iface_object_iter = p2p_iface_object_map_.find(ifname);
	if (iface_object_iter == p2p_iface_object_map_.end())
		return 1;

	*iface_object = iface_object_iter->second;
	return 0;
}

/**
 * Retrieve the |ISupplicantStaIface| aidl object reference using the provided
 * ifname.
 *
 * @param ifname Name of the corresponding interface.
 * @param iface_object Aidl reference corresponding to the iface.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::getStaIfaceAidlObjectByIfname(
	const std::string &ifname, std::shared_ptr<ISupplicantStaIface> *iface_object)
{
	if (ifname.empty() || !iface_object)
		return 1;

	auto iface_object_iter = sta_iface_object_map_.find(ifname);
	if (iface_object_iter == sta_iface_object_map_.end())
		return 1;

	*iface_object = iface_object_iter->second;
	return 0;
}

/**
 * Retrieve the |ISupplicantP2pNetwork| aidl object reference using the provided
 * ifname and network_id.
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 * @param network_object Aidl reference corresponding to the network.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::getP2pNetworkAidlObjectByIfnameAndNetworkId(
	const std::string &ifname, int network_id,
	std::shared_ptr<ISupplicantP2pNetwork> *network_object)
{
	if (ifname.empty() || network_id < 0 || !network_object)
		return 1;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(ifname, network_id);

	auto network_object_iter = p2p_network_object_map_.find(network_key);
	if (network_object_iter == p2p_network_object_map_.end())
		return 1;

	*network_object = network_object_iter->second;
	return 0;
}

/**
 * Retrieve the |ISupplicantStaNetwork| aidl object reference using the provided
 * ifname and network_id.
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 * @param network_object Aidl reference corresponding to the network.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::getStaNetworkAidlObjectByIfnameAndNetworkId(
	const std::string &ifname, int network_id,
	std::shared_ptr<ISupplicantStaNetwork> *network_object)
{
	if (ifname.empty() || network_id < 0 || !network_object)
		return 1;

	// Generate the key to be used to lookup the network.
	const std::string network_key =
		getNetworkObjectMapKey(ifname, network_id);

	auto network_object_iter = sta_network_object_map_.find(network_key);
	if (network_object_iter == sta_network_object_map_.end())
		return 1;

	*network_object = network_object_iter->second;
	return 0;
}

/**
 * Add a new |ISupplicantCallback| aidl object reference to our
 * global callback list.
 *
 * @param callback Aidl reference of the |ISupplicantCallback| object.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::addSupplicantCallbackAidlObject(
	const std::shared_ptr<ISupplicantCallback> &callback)
{
	return registerForDeathAndAddCallbackAidlObjectToList<
		ISupplicantCallback>(
		death_notifier_, callback, supplicant_callbacks_);
}

/**
 * Store the |INonStandardCertCallback| aidl object reference.
 *
 * @param callback Aidl reference of the |INonStandardCertCallback| object.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::registerNonStandardCertCallbackAidlObject(
	const std::shared_ptr<INonStandardCertCallback> &callback)
{
	if (callback == nullptr) return 1;
	non_standard_cert_callback_ = callback;
	return 0;
}

/**
 * Add a new iface callback aidl object reference to our
 * interface callback list.
 *
 * @param ifname Name of the corresponding interface.
 * @param callback Aidl reference of the callback object.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::addP2pIfaceCallbackAidlObject(
	const std::string &ifname,
	const std::shared_ptr<ISupplicantP2pIfaceCallback> &callback)
{
	return addIfaceCallbackAidlObjectToMap(
		death_notifier_, ifname, callback, p2p_iface_callbacks_map_);
}

/**
 * Add a new iface callback aidl object reference to our
 * interface callback list.
 *
 * @param ifname Name of the corresponding interface.
 * @param callback Aidl reference of the callback object.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::addStaIfaceCallbackAidlObject(
	const std::string &ifname,
	const std::shared_ptr<ISupplicantStaIfaceCallback> &callback)
{
	return addIfaceCallbackAidlObjectToMap(
		death_notifier_, ifname, callback, sta_iface_callbacks_map_);
}

/**
 * Add a new network callback aidl object reference to our network callback
 * list.
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 * @param callback Aidl reference of the callback object.
 *
 * @return 0 on success, 1 on failure.
 */
int AidlManager::addStaNetworkCallbackAidlObject(
	const std::string &ifname, int network_id,
	const std::shared_ptr<ISupplicantStaNetworkCallback> &callback)
{
	return addNetworkCallbackAidlObjectToMap(
		death_notifier_, ifname, network_id, callback,
		sta_network_callbacks_map_);
}

/**
 * Finds the correct |wpa_supplicant| object for P2P notifications
 *
 * @param wpa_s the |wpa_supplicant| that triggered the P2P event.
 * @return appropriate |wpa_supplicant| object or NULL if not found.
 */
struct wpa_supplicant *AidlManager::getTargetP2pIfaceForGroup(
		struct wpa_supplicant *wpa_group_s)
{
	if (!wpa_group_s || !wpa_group_s->parent)
		return NULL;

	struct wpa_supplicant *target_wpa_s = wpa_group_s->parent;

	// check wpa_supplicant object is a p2p device interface
	if ((wpa_group_s == wpa_group_s->p2pdev) && wpa_group_s->p2p_mgmt) {
		if (p2p_iface_object_map_.find(wpa_group_s->ifname) !=
			p2p_iface_object_map_.end())
			return wpa_group_s;
	}

	if (p2p_iface_object_map_.find(target_wpa_s->ifname) !=
		p2p_iface_object_map_.end())
		return target_wpa_s;

	// try P2P device if available
	if (!target_wpa_s->p2pdev || !target_wpa_s->p2pdev->p2p_mgmt)
		return NULL;

	target_wpa_s = target_wpa_s->p2pdev;
	if (p2p_iface_object_map_.find(target_wpa_s->ifname) !=
		p2p_iface_object_map_.end())
		return target_wpa_s;

	return NULL;
}

/**
 * Removes the provided |ISupplicantCallback| aidl object reference
 * from our global callback list.
 *
 * @param callback Aidl reference of the |ISupplicantCallback| object.
 */
void AidlManager::removeSupplicantCallbackAidlObject(
	const std::shared_ptr<ISupplicantCallback> &callback)
{
	supplicant_callbacks_.erase(
		std::remove(
		supplicant_callbacks_.begin(), supplicant_callbacks_.end(),
		callback),
		supplicant_callbacks_.end());
}

/**
 * Removes the provided iface callback aidl object reference from
 * our interface callback list.
 *
 * @param ifname Name of the corresponding interface.
 * @param callback Aidl reference of the callback object.
 */
void AidlManager::removeP2pIfaceCallbackAidlObject(
	const std::string &ifname,
	const std::shared_ptr<ISupplicantP2pIfaceCallback> &callback)
{
	return removeIfaceCallbackAidlObjectFromMap(
		ifname, callback, p2p_iface_callbacks_map_);
}

/**
 * Removes the provided iface callback aidl object reference from
 * our interface callback list.
 *
 * @param ifname Name of the corresponding interface.
 * @param callback Aidl reference of the callback object.
 */
void AidlManager::removeStaIfaceCallbackAidlObject(
	const std::string &ifname,
	const std::shared_ptr<ISupplicantStaIfaceCallback> &callback)
{
	return removeIfaceCallbackAidlObjectFromMap(
		ifname, callback, sta_iface_callbacks_map_);
}

/**
 * Removes the provided network callback aidl object reference from
 * our network callback list.
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 * @param callback Aidl reference of the callback object.
 */
void AidlManager::removeStaNetworkCallbackAidlObject(
	const std::string &ifname, int network_id,
	const std::shared_ptr<ISupplicantStaNetworkCallback> &callback)
{
	return removeNetworkCallbackAidlObjectFromMap(
		ifname, network_id, callback, sta_network_callbacks_map_);
}

/**
 * Helper function to invoke the provided callback method on all the
 * registered |ISupplicantCallback| callback aidl objects.
 *
 * @param method Pointer to the required aidl method from
 * |ISupplicantCallback|.
 */
void AidlManager::callWithEachSupplicantCallback(
	const std::function<ndk::ScopedAStatus(std::shared_ptr<ISupplicantCallback>)> &method)
{
	for (const auto &callback : supplicant_callbacks_) {
		if (!method(callback).isOk()) {
			wpa_printf(MSG_ERROR, "Failed to invoke AIDL callback");
		}
	}
}

/**
 * Helper function to invoke the provided callback method on all the
 * registered iface callback aidl objects for the specified
 * |ifname|.
 *
 * @param ifname Name of the corresponding interface.
 * @param method Pointer to the required aidl method from
 * |ISupplicantIfaceCallback|.
 */
void AidlManager::callWithEachP2pIfaceCallback(
	const std::string &ifname,
	const std::function<ndk::ScopedAStatus(std::shared_ptr<ISupplicantP2pIfaceCallback>)>
	&method)
{
	callWithEachIfaceCallback(ifname, method, p2p_iface_callbacks_map_);
}

/**
 * Helper function to invoke the provided callback method on all the
 * registered interface callback aidl objects for the specified
 * |ifname|.
 *
 * @param ifname Name of the corresponding interface.
 * @param method Pointer to the required aidl method from
 * |ISupplicantIfaceCallback|.
 */
void AidlManager::callWithEachStaIfaceCallback(
	const std::string &ifname,
	const std::function<ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaIfaceCallback>)>
	&method)
{
	callWithEachIfaceCallback(ifname, method, sta_iface_callbacks_map_);
}

/**
 * Helper function to invoke the provided callback method on all the
 * registered network callback aidl objects for the specified
 * |ifname| & |network_id|.
 *
 * @param ifname Name of the corresponding interface.
 * @param network_id ID of the corresponding network.
 * @param method Pointer to the required aidl method from 
 * |ISupplicantStaNetworkCallback|.
 */
void AidlManager::callWithEachStaNetworkCallback(
	const std::string &ifname, int network_id,
	const std::function<
	ndk::ScopedAStatus(std::shared_ptr<ISupplicantStaNetworkCallback>)> &method)
{
	callWithEachNetworkCallback(
		ifname, network_id, method, sta_network_callbacks_map_);
}

void AidlManager::notifyQosPolicyReset(
	struct wpa_supplicant *wpa_s)
{
	if (!wpa_s)
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
			&ISupplicantStaIfaceCallback::onQosPolicyReset,
			std::placeholders::_1));
}

void AidlManager::notifyQosPolicyRequest(struct wpa_supplicant *wpa_s,
	struct dscp_policy_data *policies, int num_policies)
{
	if (!wpa_s || !policies)
		return;

	std::vector<QosPolicyData> qosPolicyData;
	uint32_t mask = 0;

	for (int num = 0; num < num_policies; num++) {
		QosPolicyData policy;
		QosPolicyClassifierParams classifier_params;
		QosPolicyClassifierParamsMask classifier_param_mask;
		bool ip_ver4 = false;

		if (policies[num].type4_param.ip_version == 4) {
			classifier_params.ipVersion = IpVersion::VERSION_4;
			ip_ver4 = true;
		} else {
			classifier_params.ipVersion = IpVersion::VERSION_6;
			ip_ver4 = false;
		}

		// classifier_mask parameters are defined in IEEE Std 802.11-2020, Table 9-170
		if (policies[num].type4_param.classifier_mask & BIT(1)) {
			mask |= static_cast<uint32_t>(QosPolicyClassifierParamsMask::SRC_IP);
			if (ip_ver4) {
				classifier_params.srcIp =
					byteArrToVec((const uint8_t *)
						&policies[num].type4_param.ip_params.v4.src_ip, 4);
			} else {
				classifier_params.srcIp =
					byteArrToVec((const uint8_t *)
						&policies[num].type4_param.ip_params.v6.src_ip, 16);
			}
		}
		if (policies[num].type4_param.classifier_mask & BIT(2)) {
			mask |= static_cast<uint32_t>(QosPolicyClassifierParamsMask::DST_IP);
			if (ip_ver4){
				classifier_params.dstIp =
					byteArrToVec((const uint8_t *)
						&policies[num].type4_param.ip_params.v4.dst_ip, 4);
			} else {
				classifier_params.dstIp =
					byteArrToVec((const uint8_t *)
						&policies[num].type4_param.ip_params.v6.dst_ip, 16);
			}
		}
		if (policies[num].type4_param.classifier_mask & BIT(3)) {
			mask |= static_cast<uint32_t>(QosPolicyClassifierParamsMask::SRC_PORT);
			if (ip_ver4){
				classifier_params.srcPort =
					policies[num].type4_param.ip_params.v4.src_port;
			} else {
				classifier_params.srcPort =
					policies[num].type4_param.ip_params.v6.src_port;
			}
		}

		if (policies[num].type4_param.classifier_mask & BIT(4)) {
			mask |= static_cast<uint32_t>(
				QosPolicyClassifierParamsMask::DST_PORT_RANGE);
			if (ip_ver4) {
				classifier_params.dstPortRange.startPort =
					policies[num].type4_param.ip_params.v4.dst_port;
				classifier_params.dstPortRange.endPort =
					policies[num].type4_param.ip_params.v4.dst_port;
			} else {
				classifier_params.dstPortRange.startPort =
					policies[num].type4_param.ip_params.v6.dst_port;
				classifier_params.dstPortRange.endPort =
					policies[num].type4_param.ip_params.v6.dst_port;
			}
		} else if (policies[num].port_range_info) {
			mask |= static_cast<uint32_t>(
				QosPolicyClassifierParamsMask::DST_PORT_RANGE);
			classifier_params.dstPortRange.startPort = policies[num].start_port;
			classifier_params.dstPortRange.endPort = policies[num].end_port;
		}
		if (policies[num].type4_param.classifier_mask & BIT(6)) {
			mask |= static_cast<uint32_t>(
				QosPolicyClassifierParamsMask::PROTOCOL_NEXT_HEADER);
			if (ip_ver4) {
				classifier_params.protocolNextHdr = static_cast<ProtocolNextHeader>(
					policies[num].type4_param.ip_params.v4.protocol);
			} else {
				classifier_params.protocolNextHdr = static_cast<ProtocolNextHeader>(
					policies[num].type4_param.ip_params.v6.next_header);
			}
		}
		if (policies[num].type4_param.classifier_mask & BIT(7)) {
			mask |= static_cast<uint32_t>(QosPolicyClassifierParamsMask::FLOW_LABEL);
			classifier_params.flowLabelIpv6 =
				byteArrToVec(policies[num].type4_param.ip_params.v6.flow_label, 3);
		}
		if (policies[num].domain_name_len != 0) {
			mask |= static_cast<uint32_t>(QosPolicyClassifierParamsMask::DOMAIN_NAME);
			classifier_params.domainName =
				misc_utils::charBufToString(
					reinterpret_cast<const char *>(policies[num].domain_name));
		}

		classifier_params.classifierParamMask =
			static_cast<QosPolicyClassifierParamsMask>(mask);
		policy.policyId = policies[num].policy_id;
		policy.requestType = static_cast<QosPolicyRequestType>(policies[num].req_type);
		policy.dscp = policies[num].dscp;
		policy.classifierParams = classifier_params;

		qosPolicyData.push_back(policy);
	}

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
			&ISupplicantStaIfaceCallback::onQosPolicyRequest,
			std::placeholders::_1, wpa_s->dscp_req_dialog_token, qosPolicyData));
}

void AidlManager::notifyMloLinksInfoChanged(struct wpa_supplicant *wpa_s,
					    enum mlo_info_change_reason reason)
{
	if (!wpa_s)
		return;

	if (sta_iface_object_map_.find(wpa_s->ifname) ==
		sta_iface_object_map_.end())
		return;

	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname),
		std::bind(&ISupplicantStaIfaceCallback::onMloLinksInfoChanged,
			  std::placeholders::_1,
			  static_cast<ISupplicantStaIfaceCallback::MloLinkInfoChangeReason>(reason)));
}

ssize_t AidlManager::getCertificate(const char* alias, uint8_t** value) {
	if (alias == nullptr || value == nullptr) {
		wpa_printf(MSG_ERROR, "Null pointer argument was passed to getCertificate");
		return -1;
	}
	if (auto cert = certificate_utils::getCertificate(alias, non_standard_cert_callback_)) {
		*value = (uint8_t *) os_malloc(cert->size());
		if (*value == nullptr) return -1;
		os_memcpy(*value, cert->data(), cert->size());
		return cert->size();
	}
	return -1;
}

ssize_t AidlManager::listAliases(const char *prefix, char ***aliases) {
	if (prefix == nullptr || aliases == nullptr) {
		wpa_printf(MSG_ERROR, "Null pointer argument was passed to listAliases");
		return -1;
	}

	if (auto results =
			certificate_utils::listAliases(prefix, non_standard_cert_callback_)) {
		int count = results->size();
		*aliases = (char **) os_malloc(sizeof(char *) * count);
		if (*aliases == nullptr) {
			wpa_printf(MSG_ERROR, "listAliases: os_malloc alias array error");
			return -1;
		}
		os_memset(*aliases, 0, sizeof(char *) * count);

		int index = 0;
		for (auto it = results->begin(); it != results->end(); ++it) {
			int alias_len = it->length();
			char *alias = (char *) os_malloc(alias_len + 1);
			if (alias == nullptr) {
				wpa_printf(MSG_ERROR, "listAliases: os_malloc alias string error");
				for (int i = 0; i < index; ++i) os_free((*aliases)[i]);
				os_free(*aliases);
				return -1;
			}
			os_memcpy(alias, it->data(), alias_len + 1);
			(*aliases)[index] = alias;
			index++;
		}
		return count;
	}
	return -1;
}

QosPolicyScsResponseStatusCode getQosPolicyScsResponseStatusCode(int scsResponseCode)
{
	QosPolicyScsResponseStatusCode status = QosPolicyScsResponseStatusCode::TIMEOUT;
	/* Status code as per Ieee802.11-2020 Table 9-50—Status codes */
	switch (scsResponseCode) {
		case 0: /* SUCCESS */
			status = QosPolicyScsResponseStatusCode::SUCCESS;
			break;
		case 37: /* REQUEST_DECLINED */
			status = QosPolicyScsResponseStatusCode::TCLAS_REQUEST_DECLINED;
			break;
		case 56: /* REQUESTED_TCLAS_NOT_SUPPORTED */
		case 80: /* REQUESTED_TCLAS_NOT_SUPPORTED */
			status = QosPolicyScsResponseStatusCode::TCLAS_NOT_SUPPORTED_BY_AP;
			break;
		case 57: /* INSUFFICIENT_TCLAS_PROCESSING_RESOURCES */
			status = QosPolicyScsResponseStatusCode::TCLAS_INSUFFICIENT_RESOURCES;
			break;
		case 81: /* TCLAS_RESOURCES_EXHAUSTED */
			status = QosPolicyScsResponseStatusCode::TCLAS_RESOURCES_EXHAUSTED;
			break;
		case 128: /* TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS */
			status = QosPolicyScsResponseStatusCode::TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS;
			break;
		case 129: /* TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT */
			status = QosPolicyScsResponseStatusCode::TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT;
			break;
		case 97: /* TCLAS_PROCESSING_TERMINATED */
			status = QosPolicyScsResponseStatusCode::TCLAS_PROCESSING_TERMINATED;
			break;
		default:
			status = QosPolicyScsResponseStatusCode::TIMEOUT;
			break;
		return status;
	}
	return status;
}

void AidlManager::notifyQosPolicyScsResponse(struct wpa_supplicant *wpa_s,
		unsigned int count, int **scs_resp)
{
	if (!wpa_s || !count || !scs_resp)
		return;

	std::vector<QosPolicyScsResponseStatus> scsResponses;

	for (int i = 0; i < count; i++) {
		QosPolicyScsResponseStatus resp;
		resp.policyId = scs_resp[0][i] & 0xFF;
		resp.qosPolicyScsResponseStatusCode = getQosPolicyScsResponseStatusCode(scs_resp[1][i]);
		scsResponses.push_back(resp);
	}
	callWithEachStaIfaceCallback(
		misc_utils::charBufToString(wpa_s->ifname), std::bind(
			&ISupplicantStaIfaceCallback::onQosPolicyResponseForScs,
			std::placeholders::_1, scsResponses));
}

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
