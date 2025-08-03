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

package android.security.cts;

import android.app.Service;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service which receives an AttributionSource from the test via Binder transaction.
 */
public class AttributionSourceService extends Service {
    private static final String TAG = "AttributionSourceService";
    private static final long CONNECT_WAIT_MS = 5000;

    public static final int MSG_READ_ATTRIBUTION_SOURCE_BUNDLE = 0;
    public static final int MSG_READ_ATTRIBUTION_SOURCE = 1;
    public static final int MSG_EXIT = 2;

    private static final String KEY_READ_RESULT = "AttributionSourceResult";

    private final Messenger mMessenger = new Messenger(new MainHandler());

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private static class MainHandler extends Handler {
        @Override
        public void handleMessage(Message receivingMessage) {
            switch (receivingMessage.what) {
                case MSG_READ_ATTRIBUTION_SOURCE_BUNDLE:
                case MSG_READ_ATTRIBUTION_SOURCE:
                    Message replyMessage = Message.obtain(null, receivingMessage.what);

                    Bundle replyBundle = replyMessage.getData();
                    try {
                        if (receivingMessage.what == MSG_READ_ATTRIBUTION_SOURCE_BUNDLE) {
                            Bundle receivingBundle = receivingMessage.getData();
                            AttributionSource attributionSource = receivingBundle.getParcelable(
                                    AttributionSourceTest.ATTRIBUTION_SOURCE_KEY,
                                    AttributionSource.class);
                        } else {
                            AttributionSource attributionSource =
                                    (AttributionSource) receivingMessage.obj;
                        }

                        replyBundle.putByte(KEY_READ_RESULT, (byte) 1);
                        Log.i(TAG, "Successfully read AttributionSource");
                    } catch (SecurityException e) {
                        replyBundle.putByte(KEY_READ_RESULT, (byte) 0);
                        Log.e(TAG, "Failed to read AttributionSource: " + e);
                    }
                    replyMessage.setData(replyBundle);

                    try {
                        receivingMessage.replyTo.send(replyMessage);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Could not report result to remote, "
                                + "received exception from remote: " + e);
                    }

                    break;
                case MSG_EXIT:
                    Log.i(TAG, "Exiting (received MSG_EXIT)");
                    System.exit(0);
                default:
                    Log.e(TAG, "Unknown message type: " + receivingMessage.what);
                    super.handleMessage(receivingMessage);
            }
        }
    }

    private static class SettableFuture<T> extends FutureTask<T> {

        SettableFuture() {
            super(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    throw new IllegalStateException(
                            "Empty task, use #setResult instead of calling run.");
                }
            });
        }

        SettableFuture(Callable<T> callable) {
            super(callable);
        }

        SettableFuture(Runnable runnable, T result) {
            super(runnable, result);
        }

        public void setResult(T result) {
            set(result);
        }
    }

    public static class AttributionSourceServiceConnection {
        private Messenger mService = null;
        private boolean mBind = false;
        private final Object mLock = new Object();
        private final Context mContext;
        private final HandlerThread mReplyThread;
        private ReplyHandler mReplyHandler;
        private Messenger mReplyMessenger;

        public AttributionSourceServiceConnection(final Context context) {
            mContext = context;
            mReplyThread = new HandlerThread("AttributionSourceServiceConnection");
            mReplyThread.start();
            mReplyHandler = new ReplyHandler(mReplyThread.getLooper());
            mReplyMessenger = new Messenger(mReplyHandler);
        }

        private static final class ReplyHandler extends Handler {

            private final LinkedBlockingQueue<SettableFuture<Byte>> mFuturesQueue =
                    new LinkedBlockingQueue<>();

            private ReplyHandler(Looper looper) {
                super(looper);
            }

            /**
             * Add future for the report back from the service
             *
             * @param report a future to get the result of the unparceling.
             */
            public void addFuture(SettableFuture<Byte> report) {
                if (!mFuturesQueue.offer(report)) {
                    Log.e(TAG, "Could not request another report.");
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_READ_ATTRIBUTION_SOURCE_BUNDLE:
                    case MSG_READ_ATTRIBUTION_SOURCE:
                        SettableFuture<Byte> task = mFuturesQueue.poll();
                        if (task == null) break;
                        Bundle b = msg.getData();
                        byte result = b.getByte(KEY_READ_RESULT);
                        task.setResult(result);
                        break;
                    default:
                        Log.e(TAG, "Unknown message type: " + msg.what);
                        super.handleMessage(msg);
                }
            }
        }

        private ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.i(TAG, "Service connected.");
                synchronized (mLock) {
                    mService = new Messenger(iBinder);
                    mBind = true;
                    mLock.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.i(TAG, "Service disconnected.");
                synchronized (mLock) {
                    mService = null;
                    mBind = false;
                }
            }
        };

        private Messenger blockingGetBoundService() throws TimeoutException {
            synchronized (mLock) {
                if (!mBind) {
                    mContext.bindService(new Intent(mContext, AttributionSourceService.class),
                            mConnection, Context.BIND_AUTO_CREATE);
                    mBind = true;
                }
                try {
                    long start = System.currentTimeMillis();
                    while (mService == null && mBind) {
                        long now = System.currentTimeMillis();
                        long elapsed = now - start;
                        if (elapsed < CONNECT_WAIT_MS) {
                            mLock.wait(CONNECT_WAIT_MS - elapsed);
                        } else {
                            throw new TimeoutException(
                                    "Timed out connecting to AttributionSourceService.");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Waiting for AttributionSourceService interrupted: " + e);
                }
                if (!mBind) {
                    Log.w(TAG, "Could not get service, service disconnected.");
                }
                return mService;
            }
        }

        public void start() {
            synchronized (mLock) {
                if (!mBind) {
                    mContext.bindService(new Intent(mContext, AttributionSourceService.class),
                            mConnection, Context.BIND_AUTO_CREATE);
                    mBind = true;
                }
            }
        }

        /**
         * Stop the service process and unbind.
         */
        public void stop() throws RemoteException {
            synchronized (mLock) {
                if (mBind) {
                    mService.send(Message.obtain(null, MSG_EXIT));
                    mContext.unbindService(mConnection);
                    mBind = false;
                    mService = null;
                }

                mReplyThread.quit();
            }
        }

        public boolean postAttributionSource(AttributionSource attributionSource, long timeout,
                boolean putBundle) throws TimeoutException {
            Messenger service = blockingGetBoundService();
            Message m = null;

            if (putBundle) {
                m = Message.obtain(null, MSG_READ_ATTRIBUTION_SOURCE_BUNDLE);
                m.getData().putParcelable(AttributionSourceTest.ATTRIBUTION_SOURCE_KEY,
                        attributionSource);
            } else {
                m = Message.obtain(null, MSG_READ_ATTRIBUTION_SOURCE, attributionSource);
            }

            m.replyTo = mReplyMessenger;

            SettableFuture<Byte> task = new SettableFuture<>();

            synchronized (this) {
                mReplyHandler.addFuture(task);
                try {
                    service.send(m);
                } catch (RemoteException e) {
                    Log.e(TAG, "Received exception while sending AttributionSource: " + e);
                    return false;
                }
            }

            boolean res = false;
            try {
                byte byteResult = (timeout < 0) ? task.get() :
                        task.get(timeout, TimeUnit.MILLISECONDS);
                res = byteResult != 0;
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Received exception while retrieving result: " + e);
            }
            return res;
        }
    }
}
