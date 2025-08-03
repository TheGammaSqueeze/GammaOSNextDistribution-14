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

package com.android.car.scriptexecutor;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.internal.LargeParcelable;
import com.android.car.telemetry.scriptexecutorinterface.BundleList;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutor;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutorListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Executes Lua code in an isolated process with provided source code
 * and input arguments.
 */
public final class ScriptExecutor extends Service {
    static {
        System.loadLibrary("scriptexecutorjni");
    }

    private static final String TAG = ScriptExecutor.class.getSimpleName();

    // Dedicated "worker" thread to handle all calls related to native code.
    private HandlerThread mNativeHandlerThread;
    // Handler associated with the native worker thread.
    private Handler mNativeHandler;

    private final class IScriptExecutorImpl extends IScriptExecutor.Stub {
        @Override
        public void invokeScript(String scriptBody, String functionName,
                PersistableBundle publishedData, PersistableBundle savedState,
                IScriptExecutorListener listener) throws SecurityException {
            ensureCallerIsSystem();
            mNativeHandler.post(() ->
                    nativeInvokeScript(mLuaEnginePtr, scriptBody, functionName, publishedData,
                            null, savedState, listener));
        }

        @Override
        public void invokeScriptForLargeInput(String scriptBody, String functionName,
                ParcelFileDescriptor publishedDataFileDescriptor, PersistableBundle savedState,
                IScriptExecutorListener listener) throws SecurityException {
            ensureCallerIsSystem();
            mNativeHandler.post(() -> {
                PersistableBundle publishedData;
                try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                        publishedDataFileDescriptor)) {
                    publishedData = PersistableBundle.readFromStream(input);
                } catch (IOException e) {
                    try {
                        listener.onError(IScriptExecutorListener.ERROR_TYPE_SCRIPT_EXECUTOR_ERROR,
                                e.getMessage(), "");
                    } catch (RemoteException remoteException) {
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            // At least log "message" here, in case it was never sent back via
                            // the callback.
                            Log.e(TAG, "failed while calling listener with exception ", e);
                        }
                    }
                    return;
                }

                nativeInvokeScript(mLuaEnginePtr, scriptBody, functionName, publishedData,
                        null, savedState, listener);
            });
        }

        @Override
        public void invokeScriptForBundleList(String scriptBody, String functionName,
                BundleList bundleList, PersistableBundle savedState,
                IScriptExecutorListener listener) throws SecurityException {
            ensureCallerIsSystem();
            BundleList reconstBundles =
                    (BundleList) LargeParcelable.reconstructStableAIDLParcelable(
                            bundleList, /*keepSharedMemory=*/false);
            mNativeHandler.post(() ->
                    nativeInvokeScript(mLuaEnginePtr, scriptBody, functionName, null,
                            reconstBundles.bundles, savedState, listener));
        }
    }

    private IScriptExecutorImpl mScriptExecutorBinder;

    // Memory location of Lua Engine object which is allocated in native code.
    private long mLuaEnginePtr;

    @Override
    public void onCreate() {
        super.onCreate();

        mNativeHandlerThread = new HandlerThread(ScriptExecutor.class.getSimpleName());
        mNativeHandlerThread.start();
        mNativeHandler = new Handler(mNativeHandlerThread.getLooper());

        mLuaEnginePtr = nativeInitLuaEngine();
        mScriptExecutorBinder = new IScriptExecutorImpl();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nativeDestroyLuaEngine(mLuaEnginePtr);
        mNativeHandlerThread.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mScriptExecutorBinder;
    }

    private void ensureCallerIsSystem() throws SecurityException {
        if (UserHandle.getAppId(Binder.getCallingUid()) != Process.SYSTEM_UID) {
            throw new SecurityException("ScriptExecutor called from non-system user");
        }
    }

    /**
     * Initializes Lua Engine.
     *
     * <p>Returns memory location of Lua Engine.
     */
    private native long nativeInitLuaEngine();

    /**
     * Destroys LuaEngine at the provided memory address.
     */
    private native void nativeDestroyLuaEngine(long luaEnginePtr);

    /**
     * Calls provided Lua function.
     *
     * @param luaEnginePtr  memory address of the stored LuaEngine instance.
     * @param scriptBody    complete body of Lua script that also contains the function to be
     *                      invoked.
     * @param functionName  the name of the function to execute.
     * @param publishedData input data provided by the source which the function handles.
     * @param bundleList    input data list provided by the source which the function handles.
     * @param savedState    key-value pairs preserved from the previous invocation of the function.
     * @param listener      callback for the sandboxed environment to report back script execution
     *                      results
     *                      and errors.
     */
    private native void nativeInvokeScript(long luaEnginePtr, String scriptBody,
            String functionName, PersistableBundle publishedData,
            List<PersistableBundle> bundleList, PersistableBundle savedState,
            IScriptExecutorListener listener);
}
