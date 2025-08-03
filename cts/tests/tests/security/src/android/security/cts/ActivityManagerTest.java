/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.security.cts;


import static android.app.ActivityOptions.ANIM_SCENE_TRANSITION;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;
import static android.view.Window.FEATURE_ACTIVITY_TRANSITIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Application;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerTest extends StsExtraBusinessLogicTestCase {

    private boolean canSupportMultiuser() {
        String output = ShellUtils.runShellCommand("pm get-max-users");
        if (output.contains("Maximum supported users:")) {
            return Integer.parseInt(output.split(": ", 2)[1].trim()) > 1;
        }
        return false;
    }

    @AsbSecurityTest(cveBugId = 217934898)
    @Test
    public void testActivityManager_registerUidChangeObserver_onlyNoInteractAcrossPermission()
            throws Exception {
        if (!canSupportMultiuser()) {
            return;
        }
        String out = "";
        final UidImportanceObserver observer = new UidImportanceObserver();
        final ActivityManager mActMan = (ActivityManager) getContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int currentUser = mActMan.getCurrentUser();
        try {

            mActMan.addOnUidImportanceListener(observer,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            out = ShellUtils.runShellCommand("pm create-user testUser");
            out = out.length() > 2 ? out.substring(out.length() - 2) : out;

            ShellUtils.runShellCommand("am switch-user " + out);

            Thread.sleep(5000);
            assertFalse(observer.didObserverOtherUser());
        } finally {
            ShellUtils.runShellCommand("am switch-user " + currentUser);
            ShellUtils.runShellCommand("pm remove-user " + out);
            mActMan.removeOnUidImportanceListener(observer);
        }
    }

    @AsbSecurityTest(cveBugId = 217934898)
    @Test
    public void testActivityManager_registerUidChangeObserver_allPermission()
            throws Exception {
        if (!canSupportMultiuser()) {
            return;
        }
        String out = "";
        final UidImportanceObserver observer = new UidImportanceObserver();
        final ActivityManager mActMan = (ActivityManager) getContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int currentUser = mActMan.getCurrentUser();
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation
                .adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS_FULL");
        try {
            mActMan.addOnUidImportanceListener(observer,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            out = ShellUtils.runShellCommand("pm create-user testUser");
            out = out.length() > 2 ? out.substring(out.length() - 2) : out;

            ShellUtils.runShellCommand("am switch-user " + out);

            Thread.sleep(5000);
            assertTrue(observer.didObserverOtherUser());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
            ShellUtils.runShellCommand("am switch-user " + currentUser);
            ShellUtils.runShellCommand("pm remove-user " + out);
            mActMan.removeOnUidImportanceListener(observer);
        }
    }

    @AsbSecurityTest(cveBugId = 19394591)
    @Test
    public void testActivityManager_injectInputEvents() throws ClassNotFoundException {
        try {
            /*
             * Should throw NoSuchMethodException. getEnclosingActivityContainer() has been
             * removed/renamed.
             * Patch:  https://android.googlesource.com/platform/frameworks/base/+/aa7e3ed%5E!/
             */
            Class.forName("android.app.ActivityManagerNative").getMethod(
                    "getEnclosingActivityContainer", IBinder.class);
            fail("ActivityManagerNative.getEnclosingActivityContainer() API should not be" +
                    "available in patched devices: Device is vulnerable to CVE-2015-1533");
        } catch (NoSuchMethodException e) {
            // Patched devices should throw this exception
        }
    }

    // b/144285917
    @AsbSecurityTest(cveBugId = 144285917)
    @Test
    public void testActivityManager_attachNullApplication() {
        SecurityException securityException = null;
        Exception unexpectedException = null;
        try {
            final Object iam = ActivityManager.class.getDeclaredMethod("getService").invoke(null);
            Class.forName("android.app.IActivityManager").getDeclaredMethod("attachApplication",
                    Class.forName("android.app.IApplicationThread"), long.class)
                    .invoke(iam, null /* thread */, 0 /* startSeq */);
        } catch (SecurityException e) {
            securityException = e;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SecurityException) {
                securityException = (SecurityException) e.getCause();
            } else {
                unexpectedException = e;
            }
        } catch (Exception e) {
            unexpectedException = e;
        }
        if (unexpectedException != null) {
            Log.w("ActivityManagerTest", "Unexpected exception", unexpectedException);
        }

        assertNotNull("Expect SecurityException by attaching null application", securityException);
    }

    // b/166667403
    @AsbSecurityTest(cveBugId = 166667403)
    @Test
    public void testActivityManager_appExitReasonPackageNames() {
        final String mockPackage = "com.foo.bar";
        final String realPackage = "com.android.compatibility.common.deviceinfo";
        final Context context = InstrumentationRegistry.getTargetContext();
        final ActivityManager am = context.getSystemService(ActivityManager.class);
        try {
            am.getHistoricalProcessExitReasons(mockPackage, 0, 0);
            fail("Expecting SecurityException");
        } catch (SecurityException e) {
            // expected
        }

        final int totalLoops = 10000;
        int mockPackagescores = 0;
        final double tolerance = 0.2d;
        for (int i = 0; i < totalLoops; i++) {
            final long realPackageTiming = measureGetHistoricalProcessExitReasons(am, realPackage);
            final long mockPackageTiming = measureGetHistoricalProcessExitReasons(am, mockPackage);
            mockPackagescores += mockPackageTiming < realPackageTiming ? 1 : 0;
        }

        assertTrue(Math.abs((double) mockPackagescores / totalLoops - 0.5d) < tolerance);
    }

    @AsbSecurityTest(cveBugId = 237290578)
    @Test
    public void testActivityManager_stripTransitionFromActivityOptions() throws Exception {
        Context targetContext = getInstrumentation().getTargetContext();

        // Need to start a base activity since this requires shared element transition.
        final Intent baseIntent = new Intent(targetContext, BaseActivity.class);
        baseIntent.setFlags(FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NEW_TASK);
        final BaseActivity baseActivity = (BaseActivity) SystemUtil.callWithShellPermissionIdentity(
                () -> getInstrumentation().startActivitySync(baseIntent));

        RemoteTransition someRemote = new RemoteTransition(new IRemoteTransition.Stub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                t.apply();
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
            }

            @Override
            public void mergeAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t, IBinder mergeTarget,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
            }

            @Override
            public void onTransitionConsumed(IBinder transition, boolean aborted)
                throws RemoteException {
            }
        });
        ActivityOptions opts = ActivityOptions.makeRemoteTransition(someRemote);
        assertTrue(waitUntil(() -> baseActivity.mResumed));
        ActivityOptions sceneOpts = baseActivity.mSceneOpts;
        assertEquals(ANIM_SCENE_TRANSITION, sceneOpts.getAnimationType());

        // Prepare the intent
        final Intent intent = new Intent(targetContext, ActivityOptionsActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NEW_TASK);
        final Bundle optionsBundle = opts.toBundle();
        optionsBundle.putAll(sceneOpts.toBundle());
        final ActivityOptionsActivity activity =
                (ActivityOptionsActivity) SystemUtil.callWithShellPermissionIdentity(
                        () -> getInstrumentation().startActivitySync(intent, optionsBundle));
        assertTrue(waitUntil(() -> activity.mResumed));

        assertTrue(activity.mPreCreate || activity.mPreStart);
        assertNull(activity.mReceivedTransition);
    }

    @AsbSecurityTest(cveBugId = 286882367)
    @Test
    public void testActivityManager_rejectRemoteTransition() throws Exception {
        Context targetContext = getInstrumentation().getTargetContext();
        final Intent baseIntent = new Intent(targetContext, WaitEnterAnimActivity.class);
        baseIntent.setFlags(FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NEW_TASK);

        final boolean[] remoteCalled = new boolean[]{false};
        RemoteTransition someRemote = new RemoteTransition(new IRemoteTransition.Stub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                remoteCalled[0] = true;
                t.apply();
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
            }

            @Override
            public void mergeAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t, IBinder mergeTarget,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                remoteCalled[0] = true;
            }

            @Override
            public void onTransitionConsumed(IBinder transition, boolean aborted)
                throws RemoteException {
            }
        });
        ActivityOptions opts = ActivityOptions.makeRemoteTransition(someRemote);

        boolean securityException = false;
        final WaitEnterAnimActivity baseActivity;
        try {
            baseActivity = (WaitEnterAnimActivity)
                    getInstrumentation().startActivitySync(baseIntent, opts.toBundle());
            assertTrue(waitUntil(() -> baseActivity.mAnimComplete));
        } catch (SecurityException se) {
            securityException = true;
        }

        assertFalse(remoteCalled[0]);
        assertTrue(securityException);
    }

    @AsbSecurityTest(cveBugId = 289549315)
    @Test
    public void testActivityManager_backupAgentCreated_rejectIfCallerUidNotEqualsPackageUid()
            throws Exception {
        SecurityException securityException = null;
        Exception unexpectedException = null;
        try {
            final Object iam = ActivityManager.class.getDeclaredMethod("getService").invoke(null);
            Class.forName("android.app.IActivityManager").getDeclaredMethod("backupAgentCreated",
                            String.class, IBinder.class, int.class)
                    .invoke(iam, /* agentPackageName*/ "android", /* agent */ null, /* userId */ 0);
        } catch (SecurityException e) {
            securityException = e;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SecurityException) {
                securityException = (SecurityException) e.getCause();
            } else {
                unexpectedException = e;
            }
        } catch (Exception e) {
            unexpectedException = e;
        }
        if (unexpectedException != null) {
            Log.w("ActivityManagerTest", "Unexpected exception", unexpectedException);
            fail("ActivityManagerNative.backupAgentCreated() API should have thrown "
                    + "SecurityException when invoked from process with uid not matching target "
                    + "package uid.");
        }

        assertNotNull("Expected SecurityException when caller's uid doesn't match package uid",
                securityException);
        assertEquals("android does not belong to uid " + Process.myUid(),
                securityException.getMessage());
    }

    /**
     * Run ActivityManager.getHistoricalProcessExitReasons once, return the time spent on it.
     */
    private long measureGetHistoricalProcessExitReasons(ActivityManager am, String pkg) {
        final long start = System.nanoTime();
        try {
            am.getHistoricalProcessExitReasons(pkg, 0, 0);
        } catch (Exception e) {
        }
        return System.nanoTime() - start;
    }

    static final class UidImportanceObserver implements ActivityManager.OnUidImportanceListener {

        private boolean mObservedNonOwned = false;
        private int mMyUid;

        UidImportanceObserver() {
            mMyUid = UserHandle.getUserId(Process.myUid());
        }

        public void onUidImportance(int uid, int importance) {
            Log.i("ActivityManagerTestObserver", "Observing change for "
                    + uid + " by user " + UserHandle.getUserId(uid));
            if (UserHandle.getUserId(uid) != mMyUid) {
                mObservedNonOwned = true;
            }
        }

        public boolean didObserverOtherUser() {
            return this.mObservedNonOwned;
        }
    }

    private boolean waitUntil(Callable<Boolean> test) throws Exception {
        long timeoutMs = 2000;
        final long timeout = System.currentTimeMillis() + timeoutMs;
        while (!test.call()) {
            final long waitMs = timeout - System.currentTimeMillis();
            if (waitMs <= 0) break;
            try {
                wait(timeoutMs);
            } catch (InterruptedException e) {
                // retry
            }
        }
        return test.call();
    }

    public static class BaseActivity extends Activity {
        public boolean mResumed = false;
        public ActivityOptions mSceneOpts = null;

        @Override
        public void onCreate(Bundle i) {
            super.onCreate(i);
            getWindow().requestFeature(FEATURE_ACTIVITY_TRANSITIONS);
            mSceneOpts = ActivityOptions.makeSceneTransitionAnimation(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            mResumed = true;
        }
    }

    public static class WaitEnterAnimActivity extends Activity {
        public boolean mAnimComplete = false;

        @Override
        public void onEnterAnimationComplete() {
            mAnimComplete = true;
        }
    }

    public static class ActivityOptionsActivity extends Activity {
        public RemoteTransition mReceivedTransition = null;
        public boolean mPreCreate = false;
        public boolean mPreStart = false;
        public boolean mResumed = false;

        public ActivityOptionsActivity() {
            registerActivityLifecycleCallbacks(new Callbacks());
        }

        private class Callbacks implements Application.ActivityLifecycleCallbacks {
            private void accessOptions(Activity activity) {
                try {
                    Field mPendingOptions = Activity.class.getDeclaredField("mPendingOptions");
                    mPendingOptions.setAccessible(true);
                    ActivityOptions options = (ActivityOptions) mPendingOptions.get(activity);
                    if (options != null) {
                        mReceivedTransition = options.getRemoteTransition();
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onActivityPreCreated(Activity activity, Bundle i) {
                mPreCreate = true;
                if (mReceivedTransition == null) {
                    accessOptions(activity);
                }
            }

            @Override
            public void onActivityPreStarted(Activity activity) {
                mPreStart = true;
                if (mReceivedTransition == null) {
                    accessOptions(activity);
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                mResumed = true;
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        }
    }
}
