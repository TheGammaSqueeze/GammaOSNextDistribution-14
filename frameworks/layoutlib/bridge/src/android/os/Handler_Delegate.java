/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.os;

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.util.HandlerMessageQueue;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;
import com.android.tools.layoutlib.annotations.NotNull;

import static com.android.layoutlib.bridge.impl.RenderAction.getCurrentContext;

/**
 * Delegate overriding selected methods of android.os.Handler
 *
 * Through the layoutlib_create tool, selected methods of Handler have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 *
 */
public class Handler_Delegate {
    @LayoutlibDelegate
    /*package*/ static boolean sendMessageAtTime(Handler handler, Message msg, long uptimeMillis) {
        // get the callback
        IHandlerCallback callback = sCallbacks.get();
        if (callback != null) {
            callback.sendMessageAtTime(handler, msg, uptimeMillis);
        } else {
            if (msg.callback != null) {
                BridgeContext context = getCurrentContext();
                if (context == null) {
                    return true;
                }
                context.getSessionInteractiveData()
                        .getHandlerMessageQueue()
                        .add(handler, uptimeMillis, msg.callback);
            }
        }
        return true;
    }

    /**
     * Current implementation of Compose uses {@link Handler#postAtFrontOfQueue} to execute state
     * updates. We can not intercept postAtFrontOfQueue Compose calls, however we can intecept
     * internal Handler calls. Since postAtFrontOfQueue is just a wrapper of
     * sendMessageAtFrontOfQueue we re-define sendMessageAtFrontOfQueue here to catch Compose calls
     * (we are only interested in them) and execute them.
     * TODO(b/137794558): Clean/rework this when Compose reworks Handler usage.
     */
    @LayoutlibDelegate
    /*package*/ static boolean sendMessageAtFrontOfQueue(Handler handler, Message msg) {
        // We will also catch calls from the Choreographer that have no callback.
        if (msg.callback != null) {
            BridgeContext context = getCurrentContext();
            if (context == null) {
                return true;
            }
            context.getSessionInteractiveData()
                    .getHandlerMessageQueue()
                    .add(handler, 0, msg.callback);
        }

        return true;
    }

    // -------- Delegate implementation
    /**
     * Executed all the collected callbacks
     *
     * @return if there are more callbacks to execute
     */
    public static boolean executeCallbacks() {
        BridgeContext context = getCurrentContext();
        if (context == null) {
            return false;
        }
        HandlerMessageQueue queue = context.getSessionInteractiveData().getHandlerMessageQueue();
        long uptimeMillis = SystemClock_Delegate.uptimeMillis();
        Runnable r;
        while ((r = queue.extractFirst(uptimeMillis)) != null) {
            executeSafely(r);
        }
        return queue.isNotEmpty();
    }

    public interface IHandlerCallback {
        void sendMessageAtTime(Handler handler, Message msg, long uptimeMillis);
    }

    private final static ThreadLocal<IHandlerCallback> sCallbacks =
        new ThreadLocal<IHandlerCallback>();

    public static void setCallback(IHandlerCallback callback) {
        sCallbacks.set(callback);
    }

    /**
     * The runnables we are executing are mostly library/user code and we have no guarantee that it
     * is safe to execute them. Thus, we have to wrap each executing in try/catch block to isolate
     * dangerous executions.
     * @param r a runnable to be executed
     */
    private static void executeSafely(@NotNull Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Bridge.getLog().error(ILayoutLog.TAG_BROKEN, "Failed executing Handler callback", t,
                    null, null);
        }
    }
}
