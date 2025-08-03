/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.externalservice.common;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

/**
 * A class that encapsulates information about a running service process
 * and information about how it was created.
 */
public class RunningServiceInfo implements Parcelable {
    /** Timeout to wait for the condition variable to be opened when retrieving service info. */
    public static final int CONDITION_TIMEOUT = 10 * 1000 /* 10 seconds */;

    /** The UNIX user ID of the running service process. */
    public int uid;

    /** The UNIX process ID of the running service process. */
    public int pid;

    /** The package name that the process is running under. */
    public String packageName;

    /** The value reported from the test's ZygotePreload.getZygotePid(). */
    public int zygotePid;

    /** The value reported from the test's ZygotePreload.getZygotePackage(). */
    public String zygotePackage;

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeInt(uid);
        dest.writeInt(pid);
        dest.writeString(packageName);
        dest.writeInt(zygotePid);
        dest.writeString(zygotePackage);
    }

    public static final Parcelable.Creator<RunningServiceInfo> CREATOR
            = new Parcelable.Creator<RunningServiceInfo>() {
        public RunningServiceInfo createFromParcel(Parcel source) {
            RunningServiceInfo info = new RunningServiceInfo();
            info.uid = source.readInt();
            info.pid = source.readInt();
            info.packageName = source.readString();
            info.zygotePid = source.readInt();
            info.zygotePackage = source.readString();
            return info;
        }
        public RunningServiceInfo[] newArray(int size) {
            return new RunningServiceInfo[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public static class IdentifyHandler extends Handler {
        private final String mLoggingTag;
        private final ConditionVariable mCondition;

        IdentifyHandler(String loggingTag, ConditionVariable signalVariable) {
            super(Looper.getMainLooper());
            this.mLoggingTag = loggingTag;
            mCondition = signalVariable;
        }

        RunningServiceInfo mInfo;

        @Override
        public void handleMessage(Message msg) {
            Log.d(mLoggingTag, "Received message: " + msg);
            switch (msg.what) {
                case ServiceMessages.MSG_IDENTIFY_RESPONSE:
                    msg.getData().setClassLoader(RunningServiceInfo.class.getClassLoader());
                    mInfo = msg.getData().getParcelable(ServiceMessages.IDENTIFY_INFO);
                    mCondition.open();
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public static RunningServiceInfo identifyService(
            Messenger service,
            String loggingTag,
            ConditionVariable signalVariable) throws RemoteException {
        IdentifyHandler handler = new IdentifyHandler(loggingTag, signalVariable);
        Messenger local = new Messenger(handler);

        Message msg = Message.obtain(null, ServiceMessages.MSG_IDENTIFY);
        msg.replyTo = local;

        signalVariable.close();
        service.send(msg);

        if (!signalVariable.block(CONDITION_TIMEOUT)) {
            return null;
        }
        return handler.mInfo;
    }
}
