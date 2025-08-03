/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.adselection;

import androidx.annotation.NonNull;

class DebugReportingEnabledScriptStrategy extends DebugReportingScriptStrategy {

    private static final String DEFAULT_SCRIPT =
            HEADER_SCRIPT
                    + "forDebuggingOnly.reportAdAuctionWin = function(uri) {\n"
                    + "  forDebuggingOnly.__rb_debug_reporting_win_uri = uri;\n"
                    + "};\n"
                    + "forDebuggingOnly.reportAdAuctionLoss = function(uri) {\n"
                    + "  forDebuggingOnly.__rb_debug_reporting_loss_uri = uri;\n"
                    + "};\n";

    @Override
    String wrapGenerateBidsV3Js(@NonNull String jsScript) {
        return DEFAULT_SCRIPT + jsScript;
    }

    @Override
    String wrapIterativeJs(@NonNull String jsScript) {
        // TODO(b/284449758): Handle seller reject reason when bidding work is complete.
        return DEFAULT_SCRIPT + jsScript;
    }
}
