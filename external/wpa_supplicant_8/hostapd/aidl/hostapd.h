/*
 * aidl interface for wpa_hostapd daemon
 * Copyright (c) 2004-2018, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2004-2018, Roshan Pius <rpius@google.com>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#pragma once

#include <map>
#include <string>

#include <android-base/macros.h>

#include <aidl/android/hardware/wifi/hostapd/BnHostapd.h>

extern "C"
{
#include "utils/common.h"
#include "utils/eloop.h"
#include "utils/includes.h"
#include "utils/wpa_debug.h"
#include "ap/hostapd.h"
#include "ap/sta_info.h"
}

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {

/**
 * Implementation of the hostapd aidl object. This aidl
 * object is used core for global control operations on
 * hostapd.
 */
class Hostapd : public BnHostapd
{
public:
	Hostapd(hapd_interfaces* interfaces);
	~Hostapd() override = default;

	// Aidl methods exposed.
	::ndk::ScopedAStatus addAccessPoint(
	    const IfaceParams& iface_params, const NetworkParams& nw_params) override;
	::ndk::ScopedAStatus removeAccessPoint(const std::string& iface_name) override;
	::ndk::ScopedAStatus terminate() override;
	::ndk::ScopedAStatus registerCallback(
	    const std::shared_ptr<IHostapdCallback>& callback) override;
	::ndk::ScopedAStatus forceClientDisconnect(
	    const std::string& iface_name,
	    const std::vector<uint8_t>& client_address,
	    Ieee80211ReasonCode reason_code) override;
	::ndk::ScopedAStatus setDebugParams(DebugLevel level) override;
private:
	// Corresponding worker functions for the AIDL methods.
	::ndk::ScopedAStatus addAccessPointInternal(
	    const IfaceParams& iface_params,
	    const NetworkParams& nw_params);
	::ndk::ScopedAStatus addSingleAccessPoint(
	    const IfaceParams& IfaceParams,
	    const ChannelParams& channelParams,
	    const NetworkParams& nw_params,
	    std::string br_name,
	    std::string owe_transition_ifname);
	::ndk::ScopedAStatus addConcurrentAccessPoints(
	    const IfaceParams& IfaceParams,
	    const NetworkParams& nw_params);
	::ndk::ScopedAStatus removeAccessPointInternal(const std::string& iface_name);
	::ndk::ScopedAStatus registerCallbackInternal(
	    const std::shared_ptr<IHostapdCallback>& callback);
	::ndk::ScopedAStatus forceClientDisconnectInternal(
	    const std::string& iface_name,
	    const std::vector<uint8_t>& client_address,
	    Ieee80211ReasonCode reason_code);
	::ndk::ScopedAStatus setDebugParamsInternal(DebugLevel level);

	// Raw pointer to the global structure maintained by the core.
	struct hapd_interfaces* interfaces_;
	// Callbacks registered.
	std::vector<std::shared_ptr<IHostapdCallback>> callbacks_;
	// Death notifier.
	AIBinder_DeathRecipient* death_notifier_;
	// Bridge and its managed interfaces.
	std::map<std::string, std::vector<std::string>> br_interfaces_;
	DISALLOW_COPY_AND_ASSIGN(Hostapd);
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
