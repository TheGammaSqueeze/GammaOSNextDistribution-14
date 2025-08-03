/**
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

package com.android.telephony.imsmedia;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.support.annotation.GuardedBy;
import android.telephony.imsmedia.IImsAudioSession;
import android.telephony.imsmedia.IImsAudioSessionCallback;
import android.telephony.imsmedia.IImsMedia;
import android.telephony.imsmedia.IImsMediaCallback;
import android.telephony.imsmedia.IImsTextSession;
import android.telephony.imsmedia.IImsTextSessionCallback;
import android.telephony.imsmedia.IImsVideoSession;
import android.telephony.imsmedia.IImsVideoSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.VideoConfig;
import android.util.SparseArray;

import com.android.internal.util.DumpUtils;
import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

/** Controller that maintains all IMS Media sessions */
public class ImsMediaController extends Service {

    private static final String TAG = "ImsMediaController";
    private AtomicInteger mSessionId = new AtomicInteger();
    private OpenSessionCallback mCallback = new OpenSessionCallback();

    @GuardedBy("mSessions")
    private final SparseArray<IMediaSession> mSessions = new SparseArray();

    private class ImsMediaBinder extends IImsMedia.Stub {
        // TODO add permission checks
        @Override
        public void openSession(
            final ParcelFileDescriptor rtpFd, final ParcelFileDescriptor rtcpFd,
            final int sessionType, final RtpConfig rtpConfig, final IBinder callback) {
            final int sessionId = mSessionId.getAndIncrement();

            IMediaSession session;
            Log.d(TAG, "openSession: sessionId = " + sessionId
                    + ", type=" + sessionType + "," + Log.hidePii(String.valueOf(rtpConfig)));
            synchronized (mSessions) {
                switch (sessionType) {
                    case ImsMediaSession.SESSION_TYPE_AUDIO:
                        session = new AudioSession(sessionId,
                                IImsAudioSessionCallback.Stub.asInterface(callback));
                        break;
                    case ImsMediaSession.SESSION_TYPE_VIDEO:
                        JNIImsMediaService.setAssetManager(ImsMediaController.this.getAssets());
                        session = new VideoSession(sessionId,
                                IImsVideoSessionCallback.Stub.asInterface(callback));
                        break;
                    case ImsMediaSession.SESSION_TYPE_RTT:
                        session = new TextSession(sessionId,
                                IImsTextSessionCallback.Stub.asInterface(callback));
                        break;
                    default:
                        session = null;
                }

                if (session != null) {
                    mSessions.append(sessionId, session);
                    session.openSession(new OpenSessionParams(rtpFd, rtcpFd, rtpConfig,
                        mCallback));

                }
            }
        }

        @Override
        public void closeSession(IBinder session) {
            Log.d(TAG, "closeSession: " + session);
            synchronized (mSessions) {
                if (session instanceof AudioSession) {
                    final AudioSession audioSession =
                            (AudioSession) IImsAudioSession.Stub.asInterface(session);
                    audioSession.closeSession();
                } else if (session instanceof VideoSession) {
                    final VideoSession videoSession =
                            (VideoSession) IImsVideoSession.Stub.asInterface(session);
                    videoSession.closeSession();
                } else if (session instanceof TextSession) {
                    final TextSession textSession =
                            (TextSession) IImsTextSession.Stub.asInterface(session);
                    textSession.closeSession();
                }
            }
        }

        @Override
        public void generateVideoSprop(VideoConfig[] videoConfigList, IBinder callback) {

            if (videoConfigList == null || callback == null) {
                Log.w(TAG, "[SPROP] Invalid params");
                return;
            }

            try {
                int len = videoConfigList.length;
                String[] spropList = new String[len];

                int idx = 0;
                for (VideoConfig config : videoConfigList) {
                    Parcel parcel = Parcel.obtain();
                    config.writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    spropList[idx] = JNIImsMediaService.generateSprop(parcel.marshall());
                    parcel.recycle();
                    idx++;
                }
                IImsMediaCallback.Stub.asInterface(callback).onVideoSpropResponse(spropList);
            } catch (Exception e) {
                Log.e(TAG, "[SPROP] Error: " + e.toString());
            }
        }
    }

    private IImsMedia.Stub mImsMediaBinder = new ImsMediaBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, Thread.currentThread().getName() + " onBind");
        return mImsMediaBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, Thread.currentThread().getName() + " onUnbind");
        try {
            synchronized (mSessions) {
                while (mSessions.size() > 0) {
                    mImsMediaBinder.closeSession((IBinder) mSessions.valueAt(0));
                    mSessions.removeAt(0);
                }
                JNIImsMediaService.clearListener();
                mSessions.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "onUnbind: e = " + e);
        }
        return true;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // Release all wakelocks (if leaked)
        WakeLockManager.getInstance().cleanup();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!DumpUtils.checkDumpAndUsageStatsPermission(this, TAG, writer)) return;

        writer.println("===== ImsMedia Service dump =====");

        writer.println("\n--- ImsMediaController ---\n");
        synchronized (mSessions) {
            writer.println("Session List: \n\tCount: " + mSessions.size());
            for (int i = 0; i < mSessions.size(); i++) {
                int sessionId = mSessions.keyAt(i);
                writer.println("\tSession Id: " + sessionId
                        + "\tObj:" + mSessions.get(sessionId));
            }
        }
        writer.println("\n--- END: ImsMediaController ---\n");

        WakeLockManager.getInstance().dump(writer);

        writer.println("===== END : ImsMedia Service dump =====");
    }

    private IMediaSession getSession(int sessionId) {
        synchronized (mSessions) {
            return mSessions.get(sessionId);
        }
    }

    public class OpenSessionCallback {
        public void onOpenSessionSuccess(int sessionId, Object session) {
            Log.d(TAG, "onOpenSessionSuccess: sessionId = " + sessionId);
            getSession(sessionId).onOpenSessionSuccess(session);
        }

        public void onOpenSessionFailure(int sessionId, int error) {
            synchronized (mSessions) {
                Log.e(TAG, "onOpenSessionFailure: sessionId = " + sessionId + " error = " + error);
                getSession(sessionId).onOpenSessionFailure(error);
                mSessions.remove(sessionId);
            }
        }

        /**
         * Called when the session is closed.
         *
         * @param sessionId identifier of the session
         */
        public void onSessionClosed(int sessionId) {
            synchronized (mSessions) {
                Log.d(TAG, "onSessionClosed: sessionId = " + sessionId);
                getSession(sessionId).onSessionClosed();
                mSessions.remove(sessionId);

                if (mSessions.size() <= 0) {
                    WakeLockManager.getInstance().cleanup();
                }
            }
        }
    }
}
