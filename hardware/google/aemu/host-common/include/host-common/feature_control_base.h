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

#include <stdbool.h>

typedef enum {
#define FEATURE_CONTROL_ITEM(item) kFeature_##item,
#include "FeatureControlDefHost.h"
#include "FeatureControlDefGuest.h"
#undef FEATURE_CONTROL_ITEM
    kFeature_unknown
} Feature;

// Call this function first to initialize the feature control.
void feature_initialize();

// Get the access rules given by |name| if they exist, otherwise returns NULL
bool feature_is_enabled(Feature feature);
void feature_set_enabled_override(Feature feature, bool isEnabled);
void feature_reset_enabled_to_default(Feature feature);

// Set the feature if it is not user-overriden.
void feature_set_if_not_overridden(Feature feature, bool enable);

// Set the feature if it is not user-overriden or disabled from the guest.
void feature_set_if_not_overridden_or_guest_disabled(Feature feature, bool enable);

// Runs applyCachedServerFeaturePatterns then
// asyncUpdateServerFeaturePatterns. See FeatureControl.h
// for more info. To be called only once on startup.
void feature_update_from_server();
