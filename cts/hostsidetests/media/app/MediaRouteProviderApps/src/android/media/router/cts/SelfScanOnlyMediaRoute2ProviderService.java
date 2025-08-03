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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_SELF_SCAN_ONLY;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_SELF_SCAN_ONLY;

import android.media.MediaRoute2Info;

/**
 * A route provider service intended to test {@link
 * android.media.MediaRoute2ProviderService#CATEGORY_SELF_SCAN_ONLY}.
 */
public final class SelfScanOnlyMediaRoute2ProviderService extends BaseFakeRouteProviderService {

    public SelfScanOnlyMediaRoute2ProviderService() {
        super(
                createPublicRoute(
                        ROUTE_ID_SELF_SCAN_ONLY,
                        ROUTE_NAME_SELF_SCAN_ONLY,
                        MediaRoute2Info.TYPE_REMOTE_TV));
    }
}
