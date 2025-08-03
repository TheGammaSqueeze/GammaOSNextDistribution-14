/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.view;

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.internal.lang.System_Delegate;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;
import com.android.tools.layoutlib.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.Optional;

import static com.android.layoutlib.bridge.impl.RenderAction.getCurrentContext;

/**
 * Delegate used to provide new implementation of a select few methods of {@link Choreographer}
 *
 * Through the layoutlib_create tool, the original  methods of Choreographer have been
 * replaced by calls to methods of the same name in this delegate class.
 *
 */
public class Choreographer_Delegate {
    @LayoutlibDelegate
    public static float getRefreshRate() {
        return 60.f;
    }

    @LayoutlibDelegate
    public static void postCallbackDelayedInternal(
            Choreographer thiz, int callbackType, Object action, Object token, long delayMillis) {
        BridgeContext context = getCurrentContext();
        if (context == null) {
            if (!Thread.currentThread().getName().equals("kotlinx.coroutines.DefaultExecutor")) {
                return;
            }
            ClassLoader moduleClassLoader = findCallingClassLoader();
            if (moduleClassLoader == null) {
                return;
            }
            context = RenderAction.findContextFor(moduleClassLoader);
            if (context == null) {
                return;
            }
        }
        if (callbackType != Choreographer.CALLBACK_ANIMATION) {
            // Ignore non-animation callbacks
            return;
        }
        if (action == null) {
            Bridge.getLog().error(ILayoutLog.TAG_BROKEN,
                    "Callback with null action", (Object) null, null);
        }
        context.getSessionInteractiveData().getChoreographerCallbacks().add(action,
                token, delayMillis);
    }

    @LayoutlibDelegate
    public static void removeCallbacksInternal(
            Choreographer thiz, int callbackType, Object action, Object token) {
        BridgeContext context = getCurrentContext();
        if (context == null) {
            return;
        }
        if (callbackType != Choreographer.CALLBACK_ANIMATION) {
            // Ignore non-animation callbacks
            return;
        }
        context.getSessionInteractiveData().getChoreographerCallbacks().remove(action, token);
    }

    @LayoutlibDelegate
    public static long getFrameTimeNanos(Choreographer thiz) {
        return System.nanoTime();
    }

    /**
     * With this method we are trying to find a child ClassLoader that calls this method. We assume
     * that the child ClassLoader is the first ClassLoader in the callstack that is different from
     * the current one.
     */
    @Nullable
    private static ClassLoader findCallingClassLoader() {
        final ClassLoader current = Choreographer_Delegate.class.getClassLoader();
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        try {
            return walker.walk(stackFrameStream -> {
                Optional<StackFrame> stackFrame = stackFrameStream
                        .filter(sf -> sf.getDeclaringClass().getClassLoader() != current)
                        .findFirst();
                return stackFrame.map(f -> f.getDeclaringClass().getClassLoader()).orElse(null);
            });
        } catch (Throwable ex) {
            return null;
        }
    }
}
