// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package icu

import (
	"android/soong/android"
)

func init() {
	host_allowlist := []string{
		"device/google/cuttlefish/host/commands/",
		"external/icu/",
		"external/skia",
		"frameworks/base/libs/hwui",
		"packages/modules/RuntimeI18n/apex/",
	}

	device_allowlist := []string{
		"external/chromium-libpac",
		"external/icu/",
		"external/v8/",
		"packages/modules/RuntimeI18n/",
		// TODO(b/155921753): Restrict this when prebuilts are in their proper
		// locations.
		"prebuilts/",
	}

	android.AddNeverAllowRules(
		android.NeverAllow().
			InDirectDeps("libandroidicu").
			WithOsClass(android.Host).
			NotIn(host_allowlist...).
			Because("libandroidicu is not intended to be used on host"),
		android.NeverAllow().
			InDirectDeps("libicuuc").
			WithOsClass(android.Device).
			NotIn(device_allowlist...).
			Because("libicuuc is not intended to be used on device"),
		android.NeverAllow().
			InDirectDeps("libicui18n").
			WithOsClass(android.Device).
			NotIn(device_allowlist...).
			Because("libicui18n is not intended to be used on device"),
	)
}
