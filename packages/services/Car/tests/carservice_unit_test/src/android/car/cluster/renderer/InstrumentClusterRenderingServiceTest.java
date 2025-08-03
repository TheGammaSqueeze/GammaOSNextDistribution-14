/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cluster.renderer;


import static android.app.ActivityOptions.KEY_PACKAGE_NAME;
import static android.car.Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE;
import static android.car.Car.PERMISSION_CAR_DISPLAY_IN_CLUSTER;
import static android.car.cluster.renderer.InstrumentClusterRenderingService.EXTRA_BUNDLE_KEY_FOR_INSTRUMENT_CLUSTER_HELPER;
import static android.content.pm.PackageManager.GET_RESOLVED_FILTER;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.KeyEvent.KEYCODE_1;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.CarLibLog;
import android.car.cluster.ClusterActivityState;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileDescriptor;
import java.util.List;


/** Unit tests for {@link InstrumentClusterRenderingService}. */
@RunWith(MockitoJUnitRunner.class)
public final class InstrumentClusterRenderingServiceTest extends AbstractExtendedMockitoTestCase {
    @Mock
    private NavigationRenderer mNavigationRenderer;

    @Mock
    private ParcelFileDescriptor mParcelFileDescriptor;

    @Mock
    private FileDescriptor mMockFileDescriptor;

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private TestableInstrumentClusterRenderingService mService;
    private TestableInstrumentClusterHelper mTestableInstrumentClusterHelper;
    private IInstrumentCluster mRendererBinder;

    public InstrumentClusterRenderingServiceTest() {
        super(CarLibLog.TAG_CLUSTER);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(BitmapFactory.class);
    }

    @Before
    public void setup() {
        TestableInstrumentClusterRenderingService.setNavigationRenderer(mNavigationRenderer);
        mTestableInstrumentClusterHelper = new TestableInstrumentClusterHelper();
    }

    private void bindService(Intent intent) throws Exception {
        intent.setComponent(ComponentName.createRelative(InstrumentationRegistry.getContext(),
                TestableInstrumentClusterRenderingService.class.getName()));

        IBinder binder = mServiceRule.bindService(intent);
        mService = ((TestableInstrumentClusterRenderingService.BinderWrapper) binder).getService();
        mRendererBinder =
                (IInstrumentCluster) ((TestableInstrumentClusterRenderingService.BinderWrapper)
                        binder).getParentBinder();
    }

    private Intent createBindIntentWithClusterHelper() {
        Bundle args = new Bundle();
        args.putBinder(EXTRA_BUNDLE_KEY_FOR_INSTRUMENT_CLUSTER_HELPER,
                mTestableInstrumentClusterHelper.asBinder());
        return new Intent().putExtra(EXTRA_BUNDLE_KEY_FOR_INSTRUMENT_CLUSTER_HELPER, args);
    }

    @Test
    public void bindService_success() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        assertThat(mRendererBinder).isNotNull();
    }

    @Test
    @ExpectWtf
    public void bindService_withoutClusterHelper() throws Exception {
        bindService(new Intent().putExtras(new Bundle()));

        assertThat(mRendererBinder).isNotNull();
    }

    private List<ResolveInfo> createActivityResolveInfo(String packageName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfo.activityInfo.name = "Test";
        return ImmutableList.of(resolveInfo);
    }

    @Test
    public void setNavigationContextOwner_launchesNavigationComponent() throws Exception {
        int userId = ActivityManager.getCurrentUser();
        String packageName = "com.test";
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);

        doReturn(new String[]{packageName})
                .when(mService.mSpyPackageManager).getPackagesForUid(userId);
        doReturn(PERMISSION_GRANTED)
                .when(mService.mSpyPackageManager).checkPermission(
                PERMISSION_CAR_DISPLAY_IN_CLUSTER, packageName);
        doReturn(createActivityResolveInfo(packageName)).when(mService.mSpyPackageManager)
                .queryIntentActivitiesAsUser(any(), eq(GET_RESOLVED_FILTER),
                        eq(UserHandle.of(userId)));

        mRendererBinder.setNavigationContextOwner(userId, 123);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(1);
    }

    @Test
    public void setNavigationContextOwner_navigationComponentAlreadyLaunched_doesNothing()
            throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        mockPackageManagerInteraction(userId);

        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(1);
    }

    @Test
    public void setNavigationContextOwner_noPackages_doesNothing() throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        doReturn(new String[]{}).when(mService.mSpyPackageManager).getPackagesForUid(userId);

        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(0);
    }

    @Test
    public void setNavigationContextOwner_clusterPermissionMissing_doesNothing() throws Exception {
        int userId = ActivityManager.getCurrentUser();
        String packageName = "com.test";
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        doReturn(new String[]{packageName})
                .when(mService.mSpyPackageManager).getPackagesForUid(userId);
        doReturn(PERMISSION_DENIED)
                .when(mService.mSpyPackageManager).checkPermission(
                PERMISSION_CAR_DISPLAY_IN_CLUSTER, packageName);

        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(0);
    }

    @Test
    public void setNavigationContextOwner_failureWhenStartingNavigationActivity()
            throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        mTestableInstrumentClusterHelper.mRuntimeFailureOnInteraction = true;
        mockPackageManagerInteraction(userId);

        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(0);
    }

    @Test
    public void setNavigationContextOwner_activityNotFoundWhenStartingNavigationActivity()
            throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        mTestableInstrumentClusterHelper.mActivityNotFoundFailureOnInteraction = true;
        mockPackageManagerInteraction(userId);

        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(0);
    }

    @Test
    public void updateActivityState_notVisible_releasesNavigationComponent() throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);
        mockPackageManagerInteraction(userId);
        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        ClusterActivityState clusterActivityNewState = ClusterActivityState
                .create(/* visible= */ false, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityNewState);

        assertThat(mService.mOnNavigationComponentReleasedCalled).isTrue();
    }

    private void mockPackageManagerInteraction(int userId) {
        String packageName = "com.test";
        doReturn(new String[]{packageName})
                .when(mService.mSpyPackageManager).getPackagesForUid(userId);
        doReturn(PERMISSION_GRANTED)
                .when(mService.mSpyPackageManager).checkPermission(
                PERMISSION_CAR_DISPLAY_IN_CLUSTER, packageName);
        doReturn(createActivityResolveInfo(packageName)).when(mService.mSpyPackageManager)
                .queryIntentActivitiesAsUser(any(), eq(GET_RESOLVED_FILTER),
                        eq(UserHandle.of(userId)));
    }

    @Test
    public void clusterOnKeyEvent_triggersOnKeyEventOnService() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        mRendererBinder.onKeyEvent(new KeyEvent(KeyEvent.FLAG_EDITOR_ACTION, KEYCODE_1));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mService.mOnKeyEventCalled).isTrue();
    }

    @Test
    public void onKeyEvent_doesNothing() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        mService.onKeyEvent(new KeyEvent(KeyEvent.FLAG_EDITOR_ACTION, KEYCODE_1));

        assertThat(mService.mOnKeyEventCalled).isTrue();
    }

    @Test
    public void onNavigationComponentLaunched_doesNothing() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        mService.onNavigationComponentLaunched();

        assertThat(mService.mNumOfTimesOnNavigationComponentLaunchedCalled).isEqualTo(1);
    }

    @Test
    public void onNavigationComponentReleased_doesNothing() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        mService.onNavigationComponentReleased();

        assertThat(mService.mOnNavigationComponentReleasedCalled).isTrue();
    }

    @Test
    public void startFixedActivityMode_callsClusterHelper() throws Exception {
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        Intent intent = new Intent().putExtra(CAR_EXTRA_CLUSTER_ACTIVITY_STATE,
                clusterActivityState.toBundle());
        Bundle activityOptionsBundle = new Bundle();
        activityOptionsBundle.putString(KEY_PACKAGE_NAME, "temp.pkg");
        ActivityOptions activityOptions = ActivityOptions.fromBundle(activityOptionsBundle);
        int userId = ActivityManager.getCurrentUser();

        bindService(createBindIntentWithClusterHelper());
        boolean succeeded = mService.startFixedActivityModeForDisplayAndUser(intent,
                activityOptions,
                userId);

        assertThat(succeeded).isTrue();
        assertThat(mTestableInstrumentClusterHelper.mStartFixedActivityModeUserId).isEqualTo(
                userId);
        assertThat(ClusterActivityState.fromBundle(
                mTestableInstrumentClusterHelper.mStartFixedActivityIntent
                        .getBundleExtra(CAR_EXTRA_CLUSTER_ACTIVITY_STATE)).toString())
                .isEqualTo(clusterActivityState.toString());
        assertThat(mTestableInstrumentClusterHelper.mStartFixedActivityModeActivityOptions
                .getString(KEY_PACKAGE_NAME))
                .isEqualTo("temp.pkg");
    }

    @Test
    public void startFixedActivityMode_activityStateMissingInTheIntent() throws Exception {
        bindService(createBindIntentWithClusterHelper());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);

        Bundle activityOptionsBundle = new Bundle();
        activityOptionsBundle.putString(KEY_PACKAGE_NAME, "temp.pkg");
        ActivityOptions activityOptions = ActivityOptions.fromBundle(activityOptionsBundle);
        int userId = ActivityManager.getCurrentUser();

        boolean succeeded = mService.startFixedActivityModeForDisplayAndUser(new Intent(),
                activityOptions,
                userId);

        assertThat(succeeded).isTrue();
        assertThat(mTestableInstrumentClusterHelper.mStartFixedActivityModeUserId).isEqualTo(
                userId);
        assertThat(ClusterActivityState.fromBundle(
                mTestableInstrumentClusterHelper.mStartFixedActivityIntent
                        .getBundleExtra(CAR_EXTRA_CLUSTER_ACTIVITY_STATE)).toString())
                .isEqualTo(clusterActivityState.toString());
        assertThat(mTestableInstrumentClusterHelper.mStartFixedActivityModeActivityOptions
                .getString(KEY_PACKAGE_NAME))
                .isEqualTo("temp.pkg");
    }

    @Test
    public void startFixedActivityMode_clusterHelperFailure() throws Exception {
        mTestableInstrumentClusterHelper.mRemoteFailureOnInteraction = true;
        bindService(createBindIntentWithClusterHelper());
        ClusterActivityState clusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4));
        mService.setClusterActivityState(clusterActivityState);

        Bundle activityOptionsBundle = new Bundle();
        activityOptionsBundle.putString(KEY_PACKAGE_NAME, "temp.pkg");
        ActivityOptions activityOptions = ActivityOptions.fromBundle(activityOptionsBundle);
        int userId = ActivityManager.getCurrentUser();

        boolean succeeded = mService.startFixedActivityModeForDisplayAndUser(new Intent(),
                activityOptions,
                userId);

        assertThat(succeeded).isFalse();
    }

    @ExpectWtf
    @Test
    public void startFixedActivityMode_clusterHelperAbsent() throws Exception {
        int userId = ActivityManager.getCurrentUser();
        bindService(new Intent().putExtras(new Bundle()));

        mService.onBind(new Intent().putExtras(new Bundle()));
        boolean succeeded = mService.startFixedActivityModeForDisplayAndUser(new Intent(),
                ActivityOptions.fromBundle(new Bundle()),
                userId);

        assertThat(succeeded).isFalse();
    }

    @Test
    public void stopFixedActivityMode() throws Exception {
        int displayId = 1;
        bindService(createBindIntentWithClusterHelper());

        mService.stopFixedActivityMode(displayId);

        assertThat(mTestableInstrumentClusterHelper.mStopFixedActivityModeDisplayId).isEqualTo(
                displayId);
    }

    @ExpectWtf
    @Test
    public void stopFixedActivityMode_clusterHelperAbsent() throws Exception {
        int displayId = 1;
        bindService(new Intent().putExtras(new Bundle()));

        mService.stopFixedActivityMode(displayId);

        assertThat(mTestableInstrumentClusterHelper.mStopFixedActivityModeDisplayId).isNotEqualTo(
                displayId);
    }

    @Test
    public void stopFixedActivityMode_clusterHelperFailure() throws Exception {
        int displayId = 1;
        bindService(createBindIntentWithClusterHelper());
        mTestableInstrumentClusterHelper.mRemoteFailureOnInteraction = true;

        mService.stopFixedActivityMode(displayId);
    }

    @Test
    public void getBitmap_invalidHeight() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        assertThrows(IllegalArgumentException.class,
                () -> mService.getBitmap(Uri.parse("content://temp.com/tempFile"), 100, -1)
        );
    }

    @Test
    public void getBitmap_invalidOffLanesAlpha() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        assertThrows(IllegalArgumentException.class,
                () -> mService.getBitmap(Uri.parse("content://temp.com/tempFile"), 100, 100, 1.1f)
        );
    }

    @Test
    public void getBitmap_contextOwnerMissing() throws Exception {
        bindService(createBindIntentWithClusterHelper());

        Bitmap bitmap =
                mService.getBitmap(Uri.parse("content://temp.com/tempFile"), 100, 100);

        assertThat(bitmap).isNull();
    }

    @Test
    public void getBitmap_unknownAuthority() throws Exception {
        // Arrange
        String packageName = "com.test";
        int userId = ActivityManager.getCurrentUser();
        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        mService.setClusterActivityState(ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4)));

        mockPackageManagerInteraction(userId);
        mockAuthorityForPackage(null, packageName);
        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Act
        Bitmap bitmap =
                mService.getBitmap(Uri.parse("content://temp.com/tempFile"), 100, 100);

        // Assert
        assertThat(bitmap).isNull();
    }

    @Test
    public void getBitmap_success() throws Exception {
        // Arrange
        String packageName = "com.test";
        int userId = ActivityManager.getCurrentUser();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        bindService(createBindIntentWithClusterHelper());
        mService.setClusterActivityLaunchOptions(ActivityOptions.makeBasic());
        mService.setClusterActivityState(ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */new Rect(1, 2, 3, 4)));

        mockPackageManagerInteraction(userId);
        mockAuthorityForPackage("temp.com", packageName);
        mockBitmapReadingFromFileDescriptor(bitmap);
        mRendererBinder.setNavigationContextOwner(userId, 123);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Act
        Bitmap bitmapReturned =
                mService.getBitmap(Uri.parse("content://temp.com/tempFile"), 100, 100);

        // Assert
        assertThat(bitmapReturned).isEqualTo(bitmap);
    }

    private void mockBitmapReadingFromFileDescriptor(Bitmap bitmap) throws Exception {
        doReturn(bitmap).when(() -> BitmapFactory.decodeFileDescriptor(mMockFileDescriptor));
        when(mParcelFileDescriptor.getFileDescriptor()).thenReturn(mMockFileDescriptor);
        doReturn(mParcelFileDescriptor).when(mService.mSpyContentResolver)
                .openFileDescriptor(any(), eq("r"));
    }

    private void mockAuthorityForPackage(@Nullable String authority, String packageName)
            throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        if (authority == null) {
            packageInfo.providers = null;
        } else {
            ProviderInfo providerInfo = new ProviderInfo();
            providerInfo.authority = authority;
            packageInfo.providers = new ProviderInfo[]{providerInfo};
        }

        doReturn(packageInfo).when(mService.mSpyPackageManager)
                .getPackageInfo(packageName,
                        PackageManager.GET_PROVIDERS | PackageManager.MATCH_ANY_USER);
    }

    /**
     * A fake InstrumentClusterHelper stub used for testing. This can't be mocked because this
     * is sent via binder.
     */
    private static final class TestableInstrumentClusterHelper extends
            IInstrumentClusterHelper.Stub {
        Intent mStartFixedActivityIntent = null;
        int mStartFixedActivityModeUserId = -1;
        Bundle mStartFixedActivityModeActivityOptions = null;
        boolean mRemoteFailureOnInteraction = false;
        boolean mRuntimeFailureOnInteraction = false;
        boolean mActivityNotFoundFailureOnInteraction = false;
        int mStopFixedActivityModeDisplayId = -1;

        @Override
        public boolean startFixedActivityModeForDisplayAndUser(Intent intent,
                Bundle activityOptionsBundle, int userId) throws RemoteException {
            if (mRemoteFailureOnInteraction) {
                throw new RemoteException("failure");
            }
            if (mRuntimeFailureOnInteraction) {
                throw new RuntimeException("failure");
            }
            if (mActivityNotFoundFailureOnInteraction) {
                throw new ActivityNotFoundException("failure");
            }
            mStartFixedActivityIntent = intent;
            mStartFixedActivityModeActivityOptions = activityOptionsBundle;
            mStartFixedActivityModeUserId = userId;
            return true;
        }

        @Override
        public void stopFixedActivityMode(int displayId) throws RemoteException {
            if (mRemoteFailureOnInteraction) {
                throw new RemoteException("failure");
            }
            if (mRuntimeFailureOnInteraction) {
                throw new RuntimeException("failure");
            }
            if (mActivityNotFoundFailureOnInteraction) {
                throw new ActivityNotFoundException("failure");
            }
            mStopFixedActivityModeDisplayId = displayId;
        }
    }

    /**
     * The {@link InstrumentClusterRenderingService} can't be tested directly because it has no
     * way to provide the service object.
     * This class helps is providng the service object using {@link BinderWrapper} and also helps
     * to spy on the package manager.
     */
    public static final class TestableInstrumentClusterRenderingService extends
            InstrumentClusterRenderingService {
        private static NavigationRenderer sNavigationRenderer;
        PackageManager mSpyPackageManager;
        ContentResolver mSpyContentResolver;
        boolean mOnKeyEventCalled;
        int mNumOfTimesOnNavigationComponentLaunchedCalled;
        boolean mOnNavigationComponentReleasedCalled;

        private IBinder mParentBinder;
        private final IBinder mBinder = new BinderWrapper();

        static void setNavigationRenderer(NavigationRenderer navigationRenderer) {
            sNavigationRenderer = navigationRenderer;
        }

        /**
         * A container class that contains the service object and the binder returned from the
         * base {@link InstrumentClusterRenderingService}.
         */
        public final class BinderWrapper extends Binder {
            TestableInstrumentClusterRenderingService getService() {
                return TestableInstrumentClusterRenderingService.this;
            }

            IBinder getParentBinder() {
                return mParentBinder;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            mParentBinder = super.onBind(intent);
            mSpyPackageManager = spy(super.getPackageManager());
            mSpyContentResolver = spy(super.getContentResolver());
            return mBinder;
        }

        @Nullable
        @Override
        public NavigationRenderer getNavigationRenderer() {
            return sNavigationRenderer;
        }

        @Override
        public void onKeyEvent(@NonNull KeyEvent keyEvent) {
            mOnKeyEventCalled = true;
            super.onKeyEvent(keyEvent);
        }

        @Override
        public void onNavigationComponentLaunched() {
            mNumOfTimesOnNavigationComponentLaunchedCalled++;
            super.onNavigationComponentLaunched();
        }

        @Override
        public void onNavigationComponentReleased() {
            mOnNavigationComponentReleasedCalled = true;
            super.onNavigationComponentReleased();
        }

        @Override
        public PackageManager getPackageManager() {
            return mSpyPackageManager;
        }

        @Override
        public ContentResolver getContentResolver() {
            return mSpyContentResolver;
        }
    }
}
