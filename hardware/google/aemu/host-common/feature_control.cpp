/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include "feature_control.h"
#include "Features.h"
#include "FeatureControl.h"

#include <string>
#include <unordered_map>
#include <vector>


namespace android {
namespace featurecontrol {

static std::function<bool(Feature)> sFeatureEnabledCb;

void setFeatureEnabledCallback(std::function<bool(Feature)> cb) {
    sFeatureEnabledCb = cb;
}
}  // namespace featurecontrol
}  // namespace android

struct FeatureState {
    std::unordered_map<Feature, bool> enabled{
#define FEATURE_CONTROL_ITEM(item, idx) {  kFeature_##item, false },
#include "FeatureControlDefHost.h"
#include "FeatureControlDefGuest.h"
#undef FEATURE_CONTROL_ITEM
       };
};

static FeatureState sFeatureState;

// Call this function first to initialize the feature control.
void feature_initialize() { }

// Get the access rules given by |name| if they exist, otherwise returns NULL
bool feature_is_enabled(Feature feature) {
    if (android::featurecontrol::sFeatureEnabledCb)
       return android::featurecontrol::sFeatureEnabledCb(
            static_cast<android::featurecontrol::Feature>(feature));

    return sFeatureState.enabled[feature];
}

void feature_set_enabled_override(Feature feature, bool isEnabled) {
    sFeatureState.enabled[feature] = isEnabled;
}

void feature_reset_enabled_to_default(Feature feature) {
    sFeatureState.enabled[feature] = false;
}

// Set the feature if it is not user-overriden.
void feature_set_if_not_overridden(Feature feature, bool enable) {
    sFeatureState.enabled[feature] = enable;
}

// Set the feature if it is not user-overriden or disabled from the guest.
void feature_set_if_not_overridden_or_guest_disabled(Feature feature, bool enable) {
    sFeatureState.enabled[feature] = enable;
}

// Runs applyCachedServerFeaturePatterns then
// asyncUpdateServerFeaturePatterns. See FeatureControl.h
// for more info. To be called only once on startup.
void feature_update_from_server() { }

const char* feature_name(Feature feature) {
    static const std::unordered_map<Feature, std::string>* const sFeatureNames = [] {
        return new std::unordered_map<Feature, std::string>({
#define FEATURE_CONTROL_ITEM(item, idx) {  kFeature_##item,  #item },
#include "FeatureControlDefHost.h"
#include "FeatureControlDefGuest.h"
#undef FEATURE_CONTROL_ITEM
        });
    }();

    auto it = sFeatureNames->find(feature);
    if (it == sFeatureNames->end()) {
        return "InvalidFeature";
    }

    return it->second.c_str();
}


Feature feature_from_name(const char* name) {
    if (name == nullptr) {
        return kFeature_unknown;
    }

    static const std::unordered_map<std::string, Feature>* const sNameToFeature = [] {
        return new std::unordered_map<std::string, Feature>({
#define FEATURE_CONTROL_ITEM(item, idx) { #item, kFeature_##item },
#include "FeatureControlDefHost.h"
#include "FeatureControlDefGuest.h"
#undef FEATURE_CONTROL_ITEM
        });
    }();

    auto it = sNameToFeature->find(std::string(name));
    if (it == sNameToFeature->end()) {
        return kFeature_unknown;
    }

    return it->second;
}