/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.stubs;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class CommandReceiver extends BroadcastReceiver {

    private static final String TAG = "CommandReceiver";

    // Requires flags and targetPackage
    public static final int COMMAND_BIND_SERVICE = 1;
    // Requires targetPackage
    public static final int COMMAND_UNBIND_SERVICE = 2;
    public static final int COMMAND_START_FOREGROUND_SERVICE = 3;
    public static final int COMMAND_STOP_FOREGROUND_SERVICE = 4;
    public static final int COMMAND_START_FOREGROUND_SERVICE_LOCATION = 5;
    public static final int COMMAND_STOP_FOREGROUND_SERVICE_LOCATION = 6;
    public static final int COMMAND_START_ALERT_SERVICE = 7;
    public static final int COMMAND_STOP_ALERT_SERVICE = 8;
    public static final int COMMAND_SELF_INDUCED_ANR = 9;
    public static final int COMMAND_START_ACTIVITY = 10;
    public static final int COMMAND_STOP_ACTIVITY = 11;
    public static final int COMMAND_CREATE_FGSL_PENDING_INTENT = 12;
    public static final int COMMAND_SEND_FGSL_PENDING_INTENT = 13;
    public static final int COMMAND_BIND_FOREGROUND_SERVICE = 14;
    public static final int COMMAND_START_CHILD_PROCESS = 15;
    public static final int COMMAND_STOP_CHILD_PROCESS = 16;
    public static final int COMMAND_WAIT_FOR_CHILD_PROCESS_GONE = 17;
    public static final int COMMAND_START_SERVICE = 18;
    public static final int COMMAND_STOP_SERVICE = 19;
    public static final int COMMAND_START_FOREGROUND_SERVICE_STICKY = 20;
    public static final int COMMAND_STOP_FOREGROUND_SERVICE_STICKY = 21;
    public static final int COMMAND_EMPTY = 22;
    public static final int COMMAND_START_FOREGROUND_SERVICE_SPOOF_PACKAGE_NAME = 23;
    public static final int COMMAND_CREATE_ACTIVE_MEDIA_SESSION = 24;
    public static final int COMMAND_CREATE_ACTIVE_MEDIA_SESSION_FGS_DELEGATE = 25;
    public static final int COMMAND_ACTIVATE_MEDIA_SESSION_FGS_DELEGATE = 26;
    public static final int COMMAND_DEACTIVATE_MEDIA_SESSION_FGS_DELEGATE = 27;
    public static final int COMMAND_RELEASE_MEDIA_SESSION_FGS_DELEGATE = 28;

    public static final String KEY_PENDING_INTENT = "android.app.stubs.key.PENDING_INTENT";

    public static final int RESULT_CHILD_PROCESS_STARTED = IBinder.FIRST_CALL_TRANSACTION;
    public static final int RESULT_CHILD_PROCESS_STOPPED = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int RESULT_CHILD_PROCESS_GONE = IBinder.FIRST_CALL_TRANSACTION + 2;

    public static final String EXTRA_COMMAND = "android.app.stubs.extra.COMMAND";
    public static final String EXTRA_TARGET_PACKAGE = "android.app.stubs.extra.TARGET_PACKAGE";
    public static final String EXTRA_FLAGS = "android.app.stubs.extra.FLAGS";
    public static final String EXTRA_CALLBACK = "android.app.stubs.extra.callback";
    public static final String EXTRA_CHILD_CMDLINE = "android.app.stubs.extra.child_cmdline";
    public static final String EXTRA_TIMEOUT = "android.app.stubs.extra.child_cmdline";
    public static final String EXTRA_MESSENGER = "android.app.stubs.extra.EXTRA_MESSENGER";

    public static final String SERVICE_NAME = "android.app.stubs.LocalService";
    public static final String FG_SERVICE_NAME = "android.app.stubs.LocalForegroundService";
    public static final String FG_LOCATION_SERVICE_NAME =
            "android.app.stubs.LocalForegroundServiceLocation";
    public static final String FG_STICKY_SERVICE_NAME =
            "android.app.stubs.LocalForegroundServiceSticky";

    public static final String ACTIVITY_NAME = "android.app.stubs.SimpleActivity";

    private static ArrayMap<String,ServiceConnection> sServiceMap = new ArrayMap<>();

    // Map a packageName to a Intent that starts an Activity.
    private static ArrayMap<String, Intent> sActivityIntent = new ArrayMap<>();

    // Map a packageName to a PendingIntent.
    private static ArrayMap<String, PendingIntent> sPendingIntent = new ArrayMap<>();

    /** The child process, started via {@link #COMMAND_START_CHILD_PROCESS} */
    private static Process sChildProcess;

    private static MediaSession mMediaSession = null;

    /**
     * Handle the different types of binding/unbinding requests.
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Use the application context as the receiver context could be restricted.
        context = context.getApplicationContext();
        int command = intent.getIntExtra(EXTRA_COMMAND, -1);
        Log.d(TAG + "_" + context.getPackageName(), "Got command " + command + ", intent="
                + intent);
        Bundle resultExtras = null;
        switch (command) {
            case COMMAND_BIND_SERVICE:
                doBindService(context, intent, SERVICE_NAME);
                break;
            case COMMAND_UNBIND_SERVICE:
                doUnbindService(context, intent);
                break;
            case COMMAND_START_FOREGROUND_SERVICE:
                doStartForegroundService(context, intent);
                break;
            case COMMAND_START_SERVICE:
                doStartService(context, intent);
                break;
            case COMMAND_STOP_FOREGROUND_SERVICE:
            case COMMAND_STOP_SERVICE:
                doStopService(context, intent, FG_SERVICE_NAME);
                break;
            case COMMAND_START_FOREGROUND_SERVICE_LOCATION:
                doStartForegroundServiceWithType(context, intent);
                break;
            case COMMAND_STOP_FOREGROUND_SERVICE_LOCATION:
                doStopService(context, intent, FG_LOCATION_SERVICE_NAME);
                break;
            case COMMAND_START_FOREGROUND_SERVICE_STICKY:
                doStartForegroundServiceSticky(context, intent);
                break;
            case COMMAND_STOP_FOREGROUND_SERVICE_STICKY:
                doStopService(context, intent, FG_STICKY_SERVICE_NAME);
                break;
            case COMMAND_START_ALERT_SERVICE:
                doStartAlertService(context);
                break;
            case COMMAND_STOP_ALERT_SERVICE:
                doStopAlertService(context);
                break;
            case COMMAND_SELF_INDUCED_ANR:
                doSelfInducedAnr(context);
                break;
            case COMMAND_START_ACTIVITY:
                doStartActivity(context, intent);
                break;
            case COMMAND_STOP_ACTIVITY:
                doStopActivity(context, intent);
                break;
            case COMMAND_CREATE_FGSL_PENDING_INTENT:
                final PendingIntent pendingIntent = doCreateFgslPendingIntent(context, intent);
                resultExtras = new Bundle();
                resultExtras.putParcelable(KEY_PENDING_INTENT, pendingIntent);
                break;
            case COMMAND_SEND_FGSL_PENDING_INTENT:
                doSendFgslPendingIntent(context, intent);
                break;
            case COMMAND_BIND_FOREGROUND_SERVICE:
                doBindService(context, intent, FG_LOCATION_SERVICE_NAME);
                break;
            case COMMAND_START_CHILD_PROCESS:
                doStartChildProcess(context, intent);
                break;
            case COMMAND_STOP_CHILD_PROCESS:
                doStopChildProcess(context, intent);
                break;
            case COMMAND_WAIT_FOR_CHILD_PROCESS_GONE:
                doWaitForChildProcessGone(context, intent);
                break;
            case COMMAND_EMPTY:
                break;
            case COMMAND_START_FOREGROUND_SERVICE_SPOOF_PACKAGE_NAME:
                doStartForegroundServiceSpoofPackageName(context, intent);
                break;
            case COMMAND_CREATE_ACTIVE_MEDIA_SESSION:
                doStartMediaPlayback(context, intent.getParcelableExtra(
                        Intent.EXTRA_REMOTE_CALLBACK, RemoteCallback.class));
                break;
            case COMMAND_CREATE_ACTIVE_MEDIA_SESSION_FGS_DELEGATE:
                doStartMediaPlaybackFgsDelegate(context, intent.getParcelableExtra(
                        Intent.EXTRA_REMOTE_CALLBACK, RemoteCallback.class));
                break;
            case COMMAND_ACTIVATE_MEDIA_SESSION_FGS_DELEGATE:
                doChangeMediaPlaybackIsActiveFgsDelegate(
                        /* isActive= */ true,
                        intent.getParcelableExtra(
                                Intent.EXTRA_REMOTE_CALLBACK, RemoteCallback.class));
                break;
            case COMMAND_DEACTIVATE_MEDIA_SESSION_FGS_DELEGATE:
                doChangeMediaPlaybackIsActiveFgsDelegate(
                        /* isActive= */ false,
                        intent.getParcelableExtra(
                                Intent.EXTRA_REMOTE_CALLBACK, RemoteCallback.class));
                break;
            case COMMAND_RELEASE_MEDIA_SESSION_FGS_DELEGATE:
                doReleaseMediaPlaybackFgsDelegate(
                        intent.getParcelableExtra(
                                Intent.EXTRA_REMOTE_CALLBACK, RemoteCallback.class));
                break;
        }
        if (resultExtras != null) {
            setResultExtras(resultExtras);
        }
    }

    private void doBindService(Context context, Intent commandIntent, String serviceName) {
        String targetPackage = getTargetPackage(commandIntent);
        int flags = getFlags(commandIntent);

        Intent bindIntent = new Intent();
        bindIntent.setComponent(new ComponentName(targetPackage, serviceName));

        ServiceConnection connection = addServiceConnection(targetPackage);

        context.bindService(bindIntent, connection, flags | Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        context.unbindService(sServiceMap.remove(targetPackage));
    }

    private void doStartForegroundService(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.putExtras(commandIntent);
        fgsIntent.setComponent(new ComponentName(targetPackage, FG_SERVICE_NAME));
        int command = LocalForegroundService.COMMAND_START_FOREGROUND;
        fgsIntent.putExtras(LocalForegroundService.newCommand(command));
        try {
            context.startForegroundService(fgsIntent);
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.d(TAG, "startForegroundService gets an "
                    + " ForegroundServiceStartNotAllowedException", e);
        }
    }

    private void doStartService(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.putExtras(commandIntent);
        fgsIntent.setComponent(new ComponentName(targetPackage, FG_SERVICE_NAME));
        context.startService(fgsIntent);
    }

    private void doStartForegroundServiceWithType(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.putExtras(commandIntent); // include the fg service type if any.
        fgsIntent.setComponent(new ComponentName(targetPackage, FG_LOCATION_SERVICE_NAME));
        int command = LocalForegroundServiceLocation.COMMAND_START_FOREGROUND_WITH_TYPE;
        fgsIntent.putExtras(LocalForegroundService.newCommand(command));
        try {
            context.startForegroundService(fgsIntent);
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.d(TAG, "startForegroundService gets an "
                    + "ForegroundServiceStartNotAllowedException", e);
        }
    }

    private void doStartForegroundServiceSticky(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.putExtras(commandIntent);
        fgsIntent.setComponent(new ComponentName(targetPackage, FG_STICKY_SERVICE_NAME));
        int command = LocalForegroundService.COMMAND_START_FOREGROUND;
        fgsIntent.putExtras(LocalForegroundService.newCommand(command));
        try {
            context.startForegroundService(fgsIntent);
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.d(TAG, "startForegroundService gets an "
                    + "ForegroundServiceStartNotAllowedException", e);
        }
    }

    private void doStopService(Context context, Intent commandIntent,
            String serviceName) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.setComponent(new ComponentName(targetPackage, serviceName));
        context.stopService(fgsIntent);
    }

    private void doStartAlertService(Context context) {
        Intent intent = new Intent(context, LocalAlertService.class);
        intent.setAction(LocalAlertService.COMMAND_SHOW_ALERT);
        context.startService(intent);
    }

    private void doStopAlertService(Context context) {
        Intent intent = new Intent(context, LocalAlertService.class);
        intent.setAction(LocalAlertService.COMMAND_HIDE_ALERT);
        context.startService(intent);
    }

    private void doSelfInducedAnr(Context context) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        am.appNotResponding("CTS - self induced");
    }

    private void doStartActivity(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent activityIntent = new Intent(Intent.ACTION_MAIN);
        sActivityIntent.put(targetPackage, activityIntent);
        activityIntent.putExtras(commandIntent);
        activityIntent.setComponent(new ComponentName(targetPackage, ACTIVITY_NAME));
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
    }

    private void doStopActivity(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent activityIntent = sActivityIntent.remove(targetPackage);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent.putExtra("finish", true);
        context.startActivity(activityIntent);
    }

    private PendingIntent doCreateFgslPendingIntent(Context context, Intent commandIntent) {
        final String targetPackage = getTargetPackage(commandIntent);
        final Intent intent = new Intent().setComponent(
                new ComponentName(targetPackage, FG_LOCATION_SERVICE_NAME));
        int command = LocalForegroundServiceLocation.COMMAND_START_FOREGROUND_WITH_TYPE;
        intent.putExtras(LocalForegroundService.newCommand(command));
        final PendingIntent pendingIntent = PendingIntent.getForegroundService(context, 0,
                intent, PendingIntent.FLAG_IMMUTABLE);
        sPendingIntent.put(targetPackage, pendingIntent);
        return pendingIntent;
    }

    private void doSendFgslPendingIntent(Context context, Intent commandIntent) {
        final String targetPackage = getTargetPackage(commandIntent);
        try {
            ((PendingIntent) sPendingIntent.remove(targetPackage)).send();
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Caugtht exception:", e);
        }
    }

    private void doStartChildProcess(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        final IBinder callback = extras.getBinder(EXTRA_CALLBACK);
        final String[] cmdline = extras.getStringArray(EXTRA_CHILD_CMDLINE);
        final Parcel data = Parcel.obtain();
        final Parcel reply = Parcel.obtain();

        try {
            sChildProcess = Runtime.getRuntime().exec(cmdline);
            if (sChildProcess != null) {
                Log.i(TAG, "Forked child: " + sChildProcess);
                callback.transact(RESULT_CHILD_PROCESS_STARTED, data, reply, 0);
            } // else the remote will fail with timeout
        } catch (Exception e) {
            Log.e(TAG, "Unable to execute command", e);
            sChildProcess = null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void doStopChildProcess(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        final IBinder callback = extras.getBinder(EXTRA_CALLBACK);
        final long timeout = extras.getLong(EXTRA_TIMEOUT);
        waitForChildProcessGone(true, callback, RESULT_CHILD_PROCESS_STOPPED, timeout);
    }

    private void doWaitForChildProcessGone(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        final IBinder callback = extras.getBinder(EXTRA_CALLBACK);
        final long timeout = extras.getLong(EXTRA_TIMEOUT);
        waitForChildProcessGone(false, callback, RESULT_CHILD_PROCESS_GONE, timeout);
    }

    private static synchronized void waitForChildProcessGone(final boolean destroy,
            final IBinder callback, final int transactionCode, final long timeout) {
        if (destroy) {
            sChildProcess.destroy();
        }
        new Thread(() -> {
            final Parcel data = Parcel.obtain();
            final Parcel reply = Parcel.obtain();
            try {
                if (sChildProcess != null && sChildProcess.isAlive()) {
                    final boolean exit = sChildProcess.waitFor(timeout, TimeUnit.MILLISECONDS);
                    if (exit) {
                        Log.i(TAG, "Child process died: " + sChildProcess);
                        callback.transact(transactionCode, data, reply, 0);
                    } else {
                        Log.w(TAG, "Child process is still alive: " + sChildProcess);
                    }
                } else {
                    callback.transact(transactionCode, data, reply, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error", e);
            } finally {
                data.recycle();
                reply.recycle();
            }
        }).start();
    }

    /**
     * Directly call IActivityManager.startService() using a spoofed packageName which is known to
     * be allowlisted by Android framework to be able to start foreground service
     * from the background. Framework will disallow the foreground service to start from the
     * background and a ForegroundServiceStartNotAllowedException will be caught.
     * @param context
     * @param commandIntent
     */
    private void doStartForegroundServiceSpoofPackageName(Context context, Intent commandIntent) {
        String targetPackage = getTargetPackage(commandIntent);
        Intent fgsIntent = new Intent();
        fgsIntent.putExtras(commandIntent);
        fgsIntent.setComponent(new ComponentName(targetPackage, FG_SERVICE_NAME));
        int command = LocalForegroundService.COMMAND_START_FOREGROUND;
        fgsIntent.putExtras(LocalForegroundService.newCommand(command));
        try {
            final PackageManager pm = context.getPackageManager();
            String spoofPackageName = pm.getAttentionServicePackageName();
            if (TextUtils.isEmpty(spoofPackageName)) {
                Log.d(TAG, "getAttentionServicePackageName() returns empty");
                spoofPackageName = pm.getSystemCaptionsServicePackageName();
            }
            if (TextUtils.isEmpty(spoofPackageName)) {
                Log.d(TAG, "getSystemCaptionsServicePackageName() returns empty");
                spoofPackageName = "android";
            }
            Log.d(TAG, "spoofPackageName: " + spoofPackageName);
            final IBinder activityProxy = android.os.ServiceManager.getService("activity");
            // Call IActivityManager.startService() directly using a spoofed packageName.
            IActivityManager.Stub.asInterface(activityProxy).startService(
                    context.getIApplicationThread(),
                    fgsIntent,
                    null,
                    true,
                    spoofPackageName,
                    null,
                    android.os.Process.myUserHandle().getIdentifier()
            );
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.d(TAG, "startForegroundService gets an "
                    + " ForegroundServiceStartNotAllowedException", e);
        } catch (LinkageError e) {
            // IActivityManager.startService() is a hidden API, access hidden API could get
            // LinkageError, consider the test as pass if we get LinkageError.
            Log.d(TAG, "startForegroundService gets an LinkageError", e);
        } catch (RemoteException e) {
            Log.d(TAG, "startForegroundService gets an RemoteException", e);
        }
    }

    private void doStartMediaPlayback(Context context, RemoteCallback callback) {
        mMediaSession = new MediaSession(context, TAG);
        mMediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                final Intent fgsIntent = new Intent(context, LocalForegroundService.class);
                fgsIntent.putExtras(LocalForegroundService.newCommand(
                        LocalForegroundService.COMMAND_START_FOREGROUND));
                try {
                    context.startForegroundService(fgsIntent);
                } catch (ForegroundServiceStartNotAllowedException e) {
                    Log.e(TAG, "Error while trying to start an FGS", e);
                }
            }

            @Override
            public void onPause() {
                super.onPause();
                final Intent intent = new Intent(context, LocalForegroundService.class);
                intent.putExtras(LocalForegroundService.newCommand(
                        LocalForegroundService.COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION));
                context.startService(intent);
            }

            @Override
            public void onStop() {
                super.onStop();
                final Intent intent = new Intent(context, LocalForegroundService.class);
                context.stopService(intent);
                mMediaSession.release();
            }
        });
        mMediaSession.setActive(true);

        callback.sendResult(null);
    }

    /**
     * Use FGS delegate to promote the app's procstate and provide keep-alive.
     */
    private void doStartMediaPlaybackFgsDelegate(Context context, RemoteCallback callback) {
        mMediaSession = new MediaSession(context, TAG);
        mMediaSession.setCallback(
                new MediaSession.Callback() {
                    @Override
                    public void onPlay() {
                        super.onPlay();
                        setPlaybackState(PlaybackState.STATE_PLAYING, mMediaSession);
                    }

                    @Override
                    public void onPause() {
                        super.onPause();
                        setPlaybackState(PlaybackState.STATE_PAUSED, mMediaSession);
                    }

                    @Override
                    public void onStop() {
                        super.onStop();
                        setPlaybackState(PlaybackState.STATE_STOPPED, mMediaSession);
                    }
                });
        mMediaSession.setActive(true);
        callback.sendResult(null);
    }

    private void doChangeMediaPlaybackIsActiveFgsDelegate(
            boolean isActive, RemoteCallback callback) {
        if (mMediaSession != null) {
            mMediaSession.setActive(isActive);
        }
        callback.sendResult(null);
    }

    private void doReleaseMediaPlaybackFgsDelegate(RemoteCallback callback) {
        if (mMediaSession != null) {
            mMediaSession.release();
        }
        callback.sendResult(null);
    }

    private void setPlaybackState(int state, MediaSession mediaSession) {
        final long allActions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND;
        PlaybackState playbackState = new PlaybackState.Builder().setActions(allActions)
                .setState(state, 0L, 0.0f).build();
        mediaSession.setPlaybackState(playbackState);
    }

    private String getTargetPackage(Intent intent) {
        return intent.getStringExtra(EXTRA_TARGET_PACKAGE);
    }

    private int getFlags(Intent intent) {
        return intent.getIntExtra(EXTRA_FLAGS, 0);
    }

    public static void sendCommand(Context context, int command, String sourcePackage,
            String targetPackage, int flags, Bundle extras) {
        final Intent intent = makeIntent(command, sourcePackage, targetPackage, flags, extras);
        Log.d(TAG, "Sending broadcast " + intent);
        context.sendOrderedBroadcast(intent, null);
    }

    public static void sendCommandWithResultReceiver(Context context, int command,
            String sourcePackage, String targetPackage, int flags, Bundle extras,
            BroadcastReceiver resultReceiver) {
        final Intent intent = makeIntent(command, sourcePackage, targetPackage, flags, extras);
        Log.d(TAG, "Sending broadcast with result receiver " + intent);
        context.sendOrderedBroadcast(intent, null, resultReceiver, null,
                Activity.RESULT_OK, null, null);
    }

    public static void sendCommandWithBroadcastOptions(Context context, int command,
            String sourcePackage, String targetPackage, int flags, Bundle extras,
            Bundle broadcastOptions) {
        final Intent intent = makeIntent(command, sourcePackage, targetPackage, flags, extras);
        Log.d(TAG, "Sending broadcast with BroadcastOptions " + intent);
        context.sendOrderedBroadcast(intent, null, broadcastOptions, null, null, 0, null, null);
    }

    private static Intent makeIntent(int command, String sourcePackage,
            String targetPackage, int flags, Bundle extras) {
        Intent intent = new Intent();
        if (command == COMMAND_BIND_SERVICE || command == COMMAND_START_FOREGROUND_SERVICE
                || command == COMMAND_STOP_FOREGROUND_SERVICE || command == COMMAND_START_ACTIVITY
                || command == COMMAND_START_FOREGROUND_SERVICE_LOCATION || command == COMMAND_UNBIND_SERVICE) {
            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        intent.setComponent(new ComponentName(sourcePackage, "android.app.stubs.CommandReceiver"));
        intent.putExtra(EXTRA_COMMAND, command);
        intent.putExtra(EXTRA_FLAGS, flags);
        intent.putExtra(EXTRA_TARGET_PACKAGE, targetPackage);
        if (extras != null) {
            intent.putExtras(extras);
        }
        return intent;
    }

    private ServiceConnection addServiceConnection(final String packageName) {
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        sServiceMap.put(packageName, connection);
        return connection;
    }
}
