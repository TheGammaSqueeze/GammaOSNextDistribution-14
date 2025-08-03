// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "aemu/base/export.h"
#include "Features.h"
// #include "HWMatching.h"

#include <functional>
#include <string>
#include <vector>
#include <ostream>

namespace android {
namespace featurecontrol {

// featurecontrol is used to switch on/off advanced features It loads
// sdk/emulator/lib/advancedFeatures.ini for default values and
// .android/advancedFeatures.ini for user overridden values.
// It is expected to be initialized at the beginning of the emulator.
// For easier testing, one may also want to pass the override value through
// command line and call setEnabledOverride. (Command line override not
// implemented yet)
//
// featurecontrol::isEnabled is thread safe, all other methods are not.
//
// To add new features, please (1) add it to android/data/advancedFeatures.ini
// and (2) add a new line to
// FeatureControlDef.h, in the following format:
// FEATURE_CONTROL_ITEM(YOUR_FEATURE_NAME)

void initialize();

bool isEnabled(Feature feature);
bool isEnabledByGuest(Feature feature);
AEMU_EXPORT void setEnabledOverride(Feature feature, bool isEnabled);
void resetEnabledToDefault(Feature feature);

// Queries whether this feature is tied to the guest.
bool isGuestFeature(Feature feature);

// returns true if the user has specified it in
// home directory's user-based advancedFeatures.ini.
bool isOverridden(Feature feature);

// like setEnabledOverride, except it is a no-op
// if isOverridden(feature) == true.
void setIfNotOverriden(Feature feature, bool isEnabled);
// like setIfNotOverriden, except it is a no-op
// if the guest did not enable it too.
void setIfNotOverridenOrGuestDisabled(Feature feature, bool isEnabled);

Feature stringToFeature(const std::string& str);

// For hardware configurations special enough to warrant
// disabling or enabling features, we use the concept of
// "feature pattern" which consists of properties of hardware
// in question and a set of features to force-enable or disable.

// applyCachedServerFeaturePatterns() queries host hardware
// confiruation, takes current cached patterns, and enables
// or disables features based on which patterns match the host.
// If there is no cached patterns, no action is taken.
void applyCachedServerFeaturePatterns();
// asyncUpdateServerFeaturePatterns():
// If the current cached feature patterns don't exist or are over 24 hours old,
// asyncUpdateServerFeaturePatterns() starts a download of
// a protobuf containing the latest feature patterns, replacing
// the current cached ones.
void asyncUpdateServerFeaturePatterns();

// Queries the current set of features in various ways:
// - whether the default guest/host/server config has attempted
// to enable the feature.
// - whether the user has overriden the feature.
// - the resulting set of enabled features, which also accounts for
// programmatic setting of features.
std::vector<Feature> getEnabledNonOverride();
std::vector<Feature> getEnabledOverride();
std::vector<Feature> getDisabledOverride();
std::vector<Feature> getEnabled();
void writeFeaturesToStream(std::ostream& os);

// Overrides feature_is_enabled function above to use a user-provided callback
// instead.
void setFeatureEnabledCallback(std::function<bool(Feature)> cb);

// Customization point for the client to inject feature override calls.
void productFeatureOverride();
} // namespace android
} // namespace featurecontrol
