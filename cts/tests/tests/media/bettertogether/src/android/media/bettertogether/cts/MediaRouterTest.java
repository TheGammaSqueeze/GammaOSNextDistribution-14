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
package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteCategory;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;
import android.media.MediaRouter.UserRouteInfo;
import android.media.RemoteControlClient;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link android.media.MediaRouter}.
 */
@NonMainlineTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaRouterTest {

    private static final int TEST_ROUTE_NAME_RESOURCE_ID = R.string.test_user_route_name;
    private static final int TEST_CATEGORY_NAME_RESOURCE_ID = R.string.test_route_category_name;
    private static final int TEST_ICON_RESOURCE_ID = R.drawable.single_face;
    private static final int TEST_MAX_VOLUME = 100;
    private static final int TEST_VOLUME = 17;
    private static final int TEST_VOLUME_DIRECTION = -2;
    private static final int TEST_PLAYBACK_STREAM = AudioManager.STREAM_ALARM;
    private static final int TEST_VOLUME_HANDLING = RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    private static final int TEST_PLAYBACK_TYPE = RouteInfo.PLAYBACK_TYPE_LOCAL;
    private static final CharSequence TEST_ROUTE_DESCRIPTION = "test_user_route_description";
    private static final CharSequence TEST_STATUS = "test_user_route_status";
    private static final CharSequence TEST_GROUPABLE_CATEGORY_NAME = "test_groupable_category_name";

    private MediaRouter mMediaRouter;
    private RouteCategory mTestCategory;
    private RouteCategory mTestGroupableCategory;
    private CharSequence mTestRouteName;
    private Drawable mTestIconDrawable;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mMediaRouter = (MediaRouter) mContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mTestCategory = mMediaRouter.createRouteCategory(TEST_CATEGORY_NAME_RESOURCE_ID, false);
        mTestGroupableCategory = mMediaRouter.createRouteCategory(TEST_GROUPABLE_CATEGORY_NAME,
                true);
        mTestRouteName = mContext.getText(TEST_ROUTE_NAME_RESOURCE_ID);
        mTestIconDrawable = mContext.getDrawable(TEST_ICON_RESOURCE_ID);
    }

    @After
    public void tearDown() {
        mMediaRouter.clearUserRoutes();
    }

    /**
     * Test {@link MediaRouter#selectRoute(int, RouteInfo)}.
     */
    @Test
    public void testSelectRoute() {
        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);
        assertThat(prevSelectedRoute).isNotNull();

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        mMediaRouter.selectRoute(userRoute.getSupportedTypes(), userRoute);

        RouteInfo nowSelectedRoute = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_USER);
        assertThat(nowSelectedRoute).isEqualTo(userRoute);
        assertThat(nowSelectedRoute.getCategory()).isEqualTo(mTestCategory);

        mMediaRouter.selectRoute(prevSelectedRoute.getSupportedTypes(), prevSelectedRoute);
    }

    /**
     * Test {@link MediaRouter#getRouteCount()}.
     */
    @Test
    public void testGetRouteCount() {
        final int count = mMediaRouter.getRouteCount();
        assertWithMessage("By default, a media router has at least one route.")
                .that(count > 0).isTrue();

        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        assertThat(mMediaRouter.getRouteCount()).isEqualTo(count + 1);

        mMediaRouter.removeUserRoute(userRoute0);
        assertThat(mMediaRouter.getRouteCount()).isEqualTo(count);

        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);
        assertThat(mMediaRouter.getRouteCount()).isEqualTo(count + 2);

        mMediaRouter.clearUserRoutes();
        assertThat(mMediaRouter.getRouteCount()).isEqualTo(count);
    }

    /**
     * Test {@link MediaRouter#getRouteAt(int)}.
     */
    @Test
    public void testGetRouteAt() throws Exception {
        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);

        int count = mMediaRouter.getRouteCount();
        assertThat(mMediaRouter.getRouteAt(count - 2)).isEqualTo(userRoute0);
        assertThat(mMediaRouter.getRouteAt(count - 1)).isEqualTo(userRoute1);
    }

    /**
     * Test {@link MediaRouter.UserRouteInfo} with the default route.
     */
    @Test
    public void testDefaultRouteInfo() {
        RouteInfo route = mMediaRouter.getDefaultRoute();

        assertThat(route.getCategory()).isNotNull();
        assertThat(route.getName()).isNotNull();
        assertThat(route.getName(mContext)).isNotNull();
        assertThat(route.isEnabled()).isTrue();
        assertThat(route.isConnecting()).isFalse();
        assertThat(route.getDeviceType()).isEqualTo(RouteInfo.DEVICE_TYPE_UNKNOWN);
        assertThat(route.getPlaybackType()).isEqualTo(RouteInfo.PLAYBACK_TYPE_LOCAL);
        assertThat(route.getDescription()).isNull();
        assertThat(route.getStatus()).isNull();
        assertThat(route.getIconDrawable()).isNull();
        assertThat(route.getGroup()).isNull();

        Object tag = new Object();
        route.setTag(tag);
        assertThat(route.getTag()).isEqualTo(tag);
        assertThat(route.getPlaybackStream()).isEqualTo(AudioManager.STREAM_MUSIC);

        int curVolume = route.getVolume();
        int maxVolume = route.getVolumeMax();
        assertThat(curVolume <= maxVolume).isTrue();
    }

    /**
     * Test {@link MediaRouter.UserRouteInfo}.
     */
    @Test
    public void testUserRouteInfo() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        assertThat(userRoute.isEnabled()).isTrue();
        assertThat(userRoute.isConnecting()).isFalse();
        assertThat(userRoute.getCategory()).isEqualTo(mTestCategory);
        assertThat(userRoute.getDeviceType()).isEqualTo(RouteInfo.DEVICE_TYPE_UNKNOWN);
        assertThat(userRoute.getPlaybackType()).isEqualTo(RouteInfo.PLAYBACK_TYPE_REMOTE);

        // Test setName by CharSequence object.
        userRoute.setName(mTestRouteName);
        assertThat(userRoute.getName()).isEqualTo(mTestRouteName);

        userRoute.setName(null);
        assertThat(userRoute.getName()).isNull();

        // Test setName by resource ID.
        // The getName() method tries to find the resource in application resources which was stored
        // when the media router is first initialized. In contrast, getName(Context) method tries to
        // find the resource in a given context's resources. So if we call getName(Context) with a
        // context which has the same resources, two methods will return the same value.
        userRoute.setName(TEST_ROUTE_NAME_RESOURCE_ID);
        assertThat(userRoute.getName()).isEqualTo(mTestRouteName);
        assertThat(userRoute.getName(mContext)).isEqualTo(mTestRouteName);

        userRoute.setDescription(TEST_ROUTE_DESCRIPTION);
        assertThat(userRoute.getDescription()).isEqualTo(TEST_ROUTE_DESCRIPTION);

        userRoute.setStatus(TEST_STATUS);
        assertThat(userRoute.getStatus()).isEqualTo(TEST_STATUS);

        Object tag = new Object();
        userRoute.setTag(tag);
        assertThat(userRoute.getTag()).isEqualTo(tag);

        userRoute.setPlaybackStream(TEST_PLAYBACK_STREAM);
        assertThat(userRoute.getPlaybackStream()).isEqualTo(TEST_PLAYBACK_STREAM);

        userRoute.setIconDrawable(mTestIconDrawable);
        assertThat(userRoute.getIconDrawable()).isEqualTo(mTestIconDrawable);

        userRoute.setIconDrawable(null);
        assertThat(userRoute.getIconDrawable()).isNull();

        userRoute.setIconResource(TEST_ICON_RESOURCE_ID);
        assertThat(getBitmap(mTestIconDrawable).sameAs(getBitmap(userRoute.getIconDrawable())))
                .isTrue();

        userRoute.setVolumeMax(TEST_MAX_VOLUME);
        assertThat(userRoute.getVolumeMax()).isEqualTo(TEST_MAX_VOLUME);

        userRoute.setVolume(TEST_VOLUME);
        assertThat(userRoute.getVolume()).isEqualTo(TEST_VOLUME);

        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent mediaButtonIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        RemoteControlClient rcc = new RemoteControlClient(mediaButtonIntent);
        userRoute.setRemoteControlClient(rcc);
        assertThat(userRoute.getRemoteControlClient()).isEqualTo(rcc);

        userRoute.setVolumeHandling(TEST_VOLUME_HANDLING);
        assertThat(userRoute.getVolumeHandling()).isEqualTo(TEST_VOLUME_HANDLING);

        userRoute.setPlaybackType(TEST_PLAYBACK_TYPE);
        assertThat(userRoute.getPlaybackType()).isEqualTo(TEST_PLAYBACK_TYPE);
    }

    /**
     * Test {@link MediaRouter.RouteGroup}.
     */
    @Test
    public void testRouteGroup() {
        // Create a route with a groupable category.
        // A route does not belong to any group until it is added to a media router or to a group.
        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        assertThat(userRoute0.getGroup()).isNull();

        // Call addUserRoute(UserRouteInfo).
        // For the route whose category is groupable, this method does not directly add the route in
        // the media router. Instead, it creates a RouteGroup, adds the group in the media router,
        // and puts the route inside that group.
        mMediaRouter.addUserRoute(userRoute0);
        RouteGroup routeGroup = userRoute0.getGroup();
        assertThat(routeGroup).isNotNull();
        assertThat(routeGroup.getRouteCount()).isEqualTo(1);
        assertThat(routeGroup.getRouteAt(0)).isEqualTo(userRoute0);

        // Create another two routes with the same category.
        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        UserRouteInfo userRoute2 = mMediaRouter.createUserRoute(mTestGroupableCategory);

        // Add userRoute2 at the end of the group.
        routeGroup.addRoute(userRoute2);
        assertThat(userRoute2.getGroup()).isSameInstanceAs(routeGroup);
        assertThat(routeGroup.getRouteCount()).isEqualTo(2);
        assertThat(routeGroup.getRouteAt(0)).isEqualTo(userRoute0);
        assertThat(routeGroup.getRouteAt(1)).isEqualTo(userRoute2);

        // To place routes in order, add userRoute1 to the group between userRoute0 and userRoute2.
        routeGroup.addRoute(userRoute1, 1);
        assertThat(userRoute1.getGroup()).isSameInstanceAs(routeGroup);
        assertThat(routeGroup.getRouteCount()).isEqualTo(3);
        assertThat(routeGroup.getRouteAt(0)).isEqualTo(userRoute0);
        assertThat(routeGroup.getRouteAt(1)).isEqualTo(userRoute1);
        assertThat(routeGroup.getRouteAt(2)).isEqualTo(userRoute2);

        // Remove userRoute0.
        routeGroup.removeRoute(userRoute0);
        assertThat(userRoute0.getGroup()).isNull();
        assertThat(routeGroup.getRouteCount()).isEqualTo(2);
        assertThat(routeGroup.getRouteAt(0)).isEqualTo(userRoute1);
        assertThat(routeGroup.getRouteAt(1)).isEqualTo(userRoute2);

        // Remove userRoute1 which is the first route in the group now.
        routeGroup.removeRoute(0);
        assertThat(userRoute1.getGroup()).isNull();
        assertThat(routeGroup.getRouteCount()).isEqualTo(1);
        assertThat(routeGroup.getRouteAt(0)).isEqualTo(userRoute2);

        // Routes in different categories cannot be added to the same group.
        UserRouteInfo userRouteInAnotherCategory = mMediaRouter.createUserRoute(mTestCategory);
        assertThrows(IllegalArgumentException.class,
                () -> routeGroup.addRoute(userRouteInAnotherCategory));

        // Set an icon for the group.
        routeGroup.setIconDrawable(mTestIconDrawable);
        assertThat(routeGroup.getIconDrawable()).isEqualTo(mTestIconDrawable);

        routeGroup.setIconDrawable(null);
        assertThat(routeGroup.getIconDrawable()).isNull();

        routeGroup.setIconResource(TEST_ICON_RESOURCE_ID);
        assertThat(getBitmap(mTestIconDrawable).sameAs(getBitmap(routeGroup.getIconDrawable())))
                .isTrue();
    }

    /**
     * Test {@link MediaRouter.RouteCategory}.
     */
    @Test
    public void testRouteCategory() {
        // Test getName() for category whose name is set with resource ID.
        RouteCategory routeCategory = mMediaRouter.createRouteCategory(
                TEST_CATEGORY_NAME_RESOURCE_ID, false);

        // The getName() method tries to find the resource in application resources which was stored
        // when the media router is first initialized. In contrast, getName(Context) method tries to
        // find the resource in a given context's resources. So if we call getName(Context) with a
        // context which has the same resources, two methods will return the same value.
        CharSequence categoryName = mContext.getText(
                TEST_CATEGORY_NAME_RESOURCE_ID);
        assertThat(routeCategory.getName()).isEqualTo(categoryName);
        assertThat(routeCategory.getName(mContext)).isEqualTo(categoryName);

        assertThat(routeCategory.isGroupable()).isFalse();
        assertThat(routeCategory.getSupportedTypes()).isEqualTo(MediaRouter.ROUTE_TYPE_USER);

        final int count = mMediaRouter.getCategoryCount();
        assertWithMessage("By default, a media router has at least one route category.")
                .that(count > 0).isTrue();

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(routeCategory);
        mMediaRouter.addUserRoute(userRoute);
        assertThat(mMediaRouter.getCategoryCount()).isEqualTo(count + 1);
        assertThat(mMediaRouter.getCategoryAt(count)).isEqualTo(routeCategory);

        List<RouteInfo> routesInCategory = new ArrayList<RouteInfo>();
        routeCategory.getRoutes(routesInCategory);
        assertThat(routesInCategory).hasSize(1);

        RouteInfo route = routesInCategory.get(0);
        assertThat(route).isEqualTo(userRoute);

        // Test getName() for category whose name is set with CharSequence object.
        RouteCategory newRouteCategory = mMediaRouter.createRouteCategory(categoryName, false);
        assertThat(newRouteCategory.getName()).isEqualTo(categoryName);
    }

    @Test
    public void testCallback() {
        MediaRouterCallback callback = new MediaRouterCallback();
        MediaRouter.Callback mrc = (MediaRouter.Callback) callback;
        MediaRouter.SimpleCallback mrsc = (MediaRouter.SimpleCallback) callback;

        final int allRouteTypes = MediaRouter.ROUTE_TYPE_LIVE_AUDIO
                | MediaRouter.ROUTE_TYPE_LIVE_VIDEO | MediaRouter.ROUTE_TYPE_USER;
        mMediaRouter.addCallback(allRouteTypes, callback);

        // Test onRouteAdded().
        callback.reset();
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        assertThat(callback.mOnRouteAddedCalled).isTrue();
        assertThat(callback.mAddedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteAdded(mMediaRouter, callback.mAddedRoute);
        mrsc.onRouteAdded(mMediaRouter, callback.mAddedRoute);

        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(allRouteTypes);

        // Test onRouteSelected() and onRouteUnselected().
        callback.reset();
        mMediaRouter.selectRoute(MediaRouter.ROUTE_TYPE_USER, userRoute);
        assertThat(callback.mOnRouteUnselectedCalled).isTrue();
        assertThat(callback.mUnselectedRoute).isEqualTo(prevSelectedRoute);
        assertThat(callback.mOnRouteSelectedCalled).isTrue();
        assertThat(callback.mSelectedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUnselected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mUnselectedRoute);
        mrc.onRouteSelected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mSelectedRoute);
        mrsc.onRouteUnselected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER,
                callback.mUnselectedRoute);
        mrsc.onRouteSelected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mSelectedRoute);

        // Test onRouteChanged().
        // It is called when the route's name, description, status or tag is updated.
        callback.reset();
        userRoute.setName(mTestRouteName);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
        assertThat(callback.mChangedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        userRoute.setDescription(TEST_ROUTE_DESCRIPTION);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
        assertThat(callback.mChangedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        userRoute.setStatus(TEST_STATUS);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
        assertThat(callback.mChangedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        Object tag = new Object();
        userRoute.setTag(tag);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
        assertThat(callback.mChangedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        // Test onRouteVolumeChanged().
        userRoute.setVolumeMax(TEST_MAX_VOLUME);
        callback.reset();
        userRoute.setVolume(TEST_VOLUME);
        assertThat(callback.mOnRouteVolumeChangedCalled).isTrue();
        assertThat(callback.mVolumeChangedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteVolumeChanged(mMediaRouter, callback.mVolumeChangedRoute);
        mrsc.onRouteVolumeChanged(mMediaRouter, callback.mVolumeChangedRoute);

        // Test onRouteRemoved().
        callback.reset();
        mMediaRouter.removeUserRoute(userRoute);
        assertThat(callback.mOnRouteRemovedCalled).isTrue();
        assertThat(callback.mRemovedRoute).isEqualTo(userRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);
        mrsc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);

        // Test onRouteGrouped() and onRouteUngrouped().
        mMediaRouter.clearUserRoutes();
        UserRouteInfo groupableRoute0 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        UserRouteInfo groupableRoute1 = mMediaRouter.createUserRoute(mTestGroupableCategory);

        // Adding a route of groupable category in the media router does not directly add the route.
        // Instead, it creates a RouteGroup, adds the group as a route in the media router, and puts
        // the route inside that group. Therefore onRouteAdded() is called for the group, and
        // onRouteGrouped() is called for the route.
        callback.reset();
        mMediaRouter.addUserRoute(groupableRoute0);

        RouteGroup group = groupableRoute0.getGroup();
        assertThat(callback.mOnRouteAddedCalled).isTrue();
        assertThat(callback.mAddedRoute).isEqualTo(group);

        assertThat(callback.mOnRouteGroupedCalled).isTrue();
        assertThat(callback.mGroupedRoute).isEqualTo(groupableRoute0);
        assertThat(callback.mGroup).isEqualTo(group);
        assertThat(callback.mRouteIndexInGroup).isEqualTo(0);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);
        mrsc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);

        // Add another route to the group.
        callback.reset();
        group.addRoute(groupableRoute1);
        assertThat(callback.mOnRouteGroupedCalled).isTrue();
        assertThat(callback.mGroupedRoute).isEqualTo(groupableRoute1);
        assertThat(callback.mRouteIndexInGroup).isEqualTo(1);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);
        mrsc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);

        // Since removing a route from the group changes the group's name, onRouteChanged() is
        // called.
        callback.reset();
        group.removeRoute(groupableRoute1);
        assertThat(callback.mOnRouteUngroupedCalled).isTrue();
        assertThat(callback.mUngroupedRoute).isEqualTo(groupableRoute1);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
        assertThat(callback.mChangedRoute).isEqualTo(group);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        // When a group has no routes, the group is removed from the media router.
        callback.reset();
        group.removeRoute(0);
        assertThat(callback.mOnRouteUngroupedCalled).isTrue();
        assertThat(callback.mUngroupedRoute).isEqualTo(groupableRoute0);
        assertThat(callback.mOnRouteRemovedCalled).isTrue();
        assertThat(callback.mRemovedRoute).isEqualTo(group);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);
        mrsc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrsc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);

        // In this case, onRouteChanged() is not called.
        assertThat(callback.mOnRouteChangedCalled).isFalse();

        // Try removing the callback.
        mMediaRouter.removeCallback(callback);
        callback.reset();
        mMediaRouter.addUserRoute(groupableRoute0);
        assertThat(callback.mOnRouteAddedCalled).isFalse();

        mMediaRouter.selectRoute(prevSelectedRoute.getSupportedTypes(), prevSelectedRoute);
    }

    /**
     * Test {@link MediaRouter#addCallback(int, MediaRouter.Callback, int)}.
     */
    @Test
    public void testAddCallbackWithFlags() {
        MediaRouterCallback callback = new MediaRouterCallback();
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_USER, callback);

        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);

        // Currently mCallback is set for the type MediaRouter.ROUTE_TYPE_USER.
        // Changes on prevSelectedRoute will not invoke mCallback since the types do not match.
        callback.reset();
        Object tag0 = new Object();
        prevSelectedRoute.setTag(tag0);
        assertThat(callback.mOnRouteChangedCalled).isFalse();

        // Remove mCallback and add it again with flag MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS.
        // This flag will make the callback be invoked even when the types do not match.
        mMediaRouter.removeCallback(callback);
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_USER, callback,
                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS);

        callback.reset();
        Object tag1 = new Object();
        prevSelectedRoute.setTag(tag1);
        assertThat(callback.mOnRouteChangedCalled).isTrue();
    }

    /**
     * Test {@link MediaRouter.VolumeCallback)}.
     */
    @Test
    public void testVolumeCallback() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        userRoute.setVolumeHandling(RouteInfo.PLAYBACK_VOLUME_VARIABLE);
        MediaRouterVolumeCallback callback = new MediaRouterVolumeCallback();
        MediaRouter.VolumeCallback mrvc = (MediaRouter.VolumeCallback) callback;
        userRoute.setVolumeCallback(callback);

        userRoute.requestSetVolume(TEST_VOLUME);
        assertThat(callback.mOnVolumeSetRequestCalled).isTrue();
        assertThat(callback.mRouteInfo).isEqualTo(userRoute);
        assertThat(callback.mVolume).isEqualTo(TEST_VOLUME);
        // Call the callback method directly so it is marked as tested
        mrvc.onVolumeSetRequest(callback.mRouteInfo, callback.mVolume);

        callback.reset();
        userRoute.requestUpdateVolume(TEST_VOLUME_DIRECTION);
        assertThat(callback.mOnVolumeUpdateRequestCalled).isTrue();
        assertThat(callback.mRouteInfo).isEqualTo(userRoute);
        assertThat(callback.mDirection).isEqualTo(TEST_VOLUME_DIRECTION);
        // Call the callback method directly so it is marked as tested
        mrvc.onVolumeUpdateRequest(callback.mRouteInfo, callback.mDirection);
    }

    private Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);

        return result;
    }

    private class MediaRouterVolumeCallback extends MediaRouter.VolumeCallback {
        private boolean mOnVolumeUpdateRequestCalled;
        private boolean mOnVolumeSetRequestCalled;
        private RouteInfo mRouteInfo;
        private int mDirection;
        private int mVolume;

        public void reset() {
            mOnVolumeUpdateRequestCalled = false;
            mOnVolumeSetRequestCalled = false;
            mRouteInfo = null;
            mDirection = 0;
            mVolume = 0;
        }

        @Override
        public void onVolumeUpdateRequest(RouteInfo info, int direction) {
            mOnVolumeUpdateRequestCalled = true;
            mRouteInfo = info;
            mDirection = direction;
        }

        @Override
        public void onVolumeSetRequest(RouteInfo info, int volume) {
            mOnVolumeSetRequestCalled = true;
            mRouteInfo = info;
            mVolume = volume;
        }
    }

    private class MediaRouterCallback extends MediaRouter.SimpleCallback {
        private boolean mOnRouteSelectedCalled;
        private boolean mOnRouteUnselectedCalled;
        private boolean mOnRouteAddedCalled;
        private boolean mOnRouteRemovedCalled;
        private boolean mOnRouteChangedCalled;
        private boolean mOnRouteGroupedCalled;
        private boolean mOnRouteUngroupedCalled;
        private boolean mOnRouteVolumeChangedCalled;

        private RouteInfo mSelectedRoute;
        private RouteInfo mUnselectedRoute;
        private RouteInfo mAddedRoute;
        private RouteInfo mRemovedRoute;
        private RouteInfo mChangedRoute;
        private RouteInfo mGroupedRoute;
        private RouteInfo mUngroupedRoute;
        private RouteInfo mVolumeChangedRoute;
        private RouteGroup mGroup;
        private int mRouteIndexInGroup = -1;

        public void reset() {
            mOnRouteSelectedCalled = false;
            mOnRouteUnselectedCalled = false;
            mOnRouteAddedCalled = false;
            mOnRouteRemovedCalled = false;
            mOnRouteChangedCalled = false;
            mOnRouteGroupedCalled = false;
            mOnRouteUngroupedCalled = false;
            mOnRouteVolumeChangedCalled = false;

            mSelectedRoute = null;
            mUnselectedRoute = null;
            mAddedRoute = null;
            mRemovedRoute = null;
            mChangedRoute = null;
            mGroupedRoute = null;
            mUngroupedRoute = null;
            mVolumeChangedRoute = null;
            mGroup = null;
            mRouteIndexInGroup = -1;
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            mOnRouteSelectedCalled = true;
            mSelectedRoute = info;
        }

        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            mOnRouteUnselectedCalled = true;
            mUnselectedRoute = info;
        }

        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            mOnRouteAddedCalled = true;
            mAddedRoute = info;
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            mOnRouteRemovedCalled = true;
            mRemovedRoute = info;
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            mOnRouteChangedCalled = true;
            mChangedRoute = info;
        }

        @Override
        public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group,
                int index) {
            mOnRouteGroupedCalled = true;
            mGroupedRoute = info;
            mGroup = group;
            mRouteIndexInGroup = index;
        }

        @Override
        public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {
            mOnRouteUngroupedCalled = true;
            mUngroupedRoute = info;
            mGroup = group;
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {
            mOnRouteVolumeChangedCalled = true;
            mVolumeChangedRoute = info;
        }
    }
}
