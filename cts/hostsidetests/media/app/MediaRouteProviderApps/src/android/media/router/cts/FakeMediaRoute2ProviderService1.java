/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_4;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_4;

import android.media.MediaRoute2Info;

/** Provides fake routes for testing route deduplication. */
public final class FakeMediaRoute2ProviderService1 extends BaseFakeRouteProviderService {

    public FakeMediaRoute2ProviderService1() {
        super(
                createPublicRoute(
                        ROUTE_ID_APP_1_ROUTE_1, ROUTE_NAME_1, MediaRoute2Info.TYPE_REMOTE_TV),
                createPublicRoute(
                        ROUTE_ID_APP_1_ROUTE_2,
                        ROUTE_NAME_2,
                        MediaRoute2Info.TYPE_UNKNOWN,
                        /* deduplicationIds...= */ ROUTE_DEDUPLICATION_ID_1),
                createPublicRoute(
                        ROUTE_ID_APP_1_ROUTE_3,
                        ROUTE_NAME_3,
                        MediaRoute2Info.TYPE_UNKNOWN,
                        /* deduplicationIds...= */ ROUTE_DEDUPLICATION_ID_2),
                createPublicRoute(
                        ROUTE_ID_APP_1_ROUTE_4, ROUTE_NAME_4, MediaRoute2Info.TYPE_UNKNOWN));
    }
}
