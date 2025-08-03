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

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;

import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;

import java.util.List;
import java.util.Set;

/** Base implementation for fake route providers used in host side media routing tests. */
public abstract class BaseFakeRouteProviderService extends MediaRoute2ProviderService {

    /** Route information of routes provided by this instance. */
    private final List<MediaRoute2Info> mRoutes;

    /** Creates an instance that provides the given route information. */
    protected BaseFakeRouteProviderService(MediaRoute2Info... routes) {
        mRoutes = List.of(routes);
    }

    protected static MediaRoute2Info createPublicRoute(
            String id, String name, int type, String... deduplicationIds) {
        return new MediaRoute2Info.Builder(id, name)
                .setType(type)
                .addFeature(FEATURE_SAMPLE)
                .setDeduplicationIds(Set.of(deduplicationIds))
                .setVisibilityPublic()
                .build();
    }

    protected static MediaRoute2Info createRestrictedRoute(
            String id, String name, Set<String> allowedPackages, String... deduplicationIds) {
        return new MediaRoute2Info.Builder(id, name)
                .addFeature(FEATURE_SAMPLE)
                .setDeduplicationIds(Set.of(deduplicationIds))
                .setVisibilityRestricted(allowedPackages)
                .build();
    }

    protected static MediaRoute2Info createPrivateRoute(
            String id, String name, String... deduplicationIds) {
        return new MediaRoute2Info.Builder(id, name)
                .addFeature(FEATURE_SAMPLE)
                .setDeduplicationIds(Set.of(deduplicationIds))
                .setVisibilityRestricted(Set.of())
                .build();
    }

    // MediaRoute2ProviderService implementation.

    @Override
    public void onSetRouteVolume(long requestId, String routeId, int volume) {}

    @Override
    public void onSetSessionVolume(long requestId, String sessionId, int volume) {}

    @Override
    public void onCreateSession(
            long requestId, String packageName, String routeId, Bundle sessionHints) {}

    @Override
    public void onReleaseSession(long requestId, String sessionId) {}

    @Override
    public void onSelectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onDeselectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onTransferToRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onDiscoveryPreferenceChanged(RouteDiscoveryPreference preference) {
        notifyRoutes(mRoutes);
    }
}
