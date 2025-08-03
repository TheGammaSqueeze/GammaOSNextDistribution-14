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

package com.android.adservices.service.js;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;

import androidx.javascriptengine.IsolateStartupParameters;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;
import androidx.javascriptengine.MemoryLimitExceededException;
import androidx.javascriptengine.SandboxDeadException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

/**
 * A convenience class to execute JS scripts using a WebView. Because arguments to the {@link
 * #evaluate(String, List, IsolateSettings)} methods are set at WebView level, calls to that methods
 * are serialized to avoid one scripts being able to interfere one another.
 *
 * <p>The class is re-entrant, for best performance when using it on multiple thread is better to
 * have every thread using its own instance.
 */
public class JSScriptEngine {
    public static final String ENTRY_POINT_FUNC_NAME = "__rb_entry_point";

    @VisibleForTesting public static final String TAG = JSScriptEngine.class.getSimpleName();
    public static final String WASM_MODULE_BYTES_ID = "__wasmModuleBytes";
    public static final String WASM_MODULE_ARG_NAME = "wasmModule";

    public static final String NON_SUPPORTED_MAX_HEAP_SIZE_EXCEPTION_MSG =
            "JS isolate does not support Max heap size";
    public static final String JS_SCRIPT_ENGINE_CONNECTION_EXCEPTION_MSG =
            "Unable to create isolate";
    public static final String JS_SCRIPT_ENGINE_SANDBOX_DEAD_MSG =
            "Unable to evaluate on isolate due to sandbox dead exception";
    public static final String JS_EVALUATE_METHOD_NAME = "JSScriptEngine#evaluate";
    public static final Set<Class<? extends Exception>> RETRYABLE_EXCEPTIONS_FROM_JS_ENGINE =
            Set.of(JSScriptEngineConnectionException.class);

    @SuppressLint("StaticFieldLeak")
    private static JSScriptEngine sSingleton;

    @NonNull private final Context mContext;
    @NonNull private final JavaScriptSandboxProvider mJsSandboxProvider;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final Profiler mProfiler;
    @NonNull private final LoggerFactory.Logger mLogger;

    /**
     * Extracting the logic to create the JavaScriptSandbox in a factory class for better
     * testability. This factory class creates a single instance of {@link JavaScriptSandbox} until
     * the instance is invalidated by calling {@link
     * JavaScriptSandboxProvider#destroyCurrentInstance()}. The instance is returned wrapped in a
     * {@code Future}
     *
     * <p>Throws {@link JSSandboxIsNotAvailableException} if JS Sandbox is not available in the
     * current version of the WebView
     */
    @VisibleForTesting
    static class JavaScriptSandboxProvider {
        private final Object mSandboxLock = new Object();
        private StopWatch mSandboxInitStopWatch;
        private Profiler mProfiler;
        private LoggerFactory.Logger mLogger;

        @GuardedBy("mSandboxLock")
        private FluentFuture<JavaScriptSandbox> mFutureSandbox;
        JavaScriptSandboxProvider(Profiler profiler, LoggerFactory.Logger logger) {
            mProfiler = profiler;
            mLogger = logger;
        }

        public FluentFuture<JavaScriptSandbox> getFutureInstance(Context context) {
            synchronized (mSandboxLock) {
                if (mFutureSandbox == null) {
                    if (!AvailabilityChecker.isJSSandboxAvailable()) {
                        JSSandboxIsNotAvailableException exception =
                                new JSSandboxIsNotAvailableException();
                        mLogger.e(
                                exception,
                                "JS Sandbox is not available in this version of WebView "
                                        + "or WebView is not installed at all!");
                        mFutureSandbox =
                                FluentFuture.from(Futures.immediateFailedFuture(exception));
                        return mFutureSandbox;
                    }

                    mLogger.d("Creating JavaScriptSandbox");
                    mSandboxInitStopWatch =
                            mProfiler.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);

                    mFutureSandbox =
                            FluentFuture.from(
                                    JavaScriptSandbox.createConnectedInstanceAsync(
                                            // This instance will have the same lifetime
                                            // of the PPAPI process
                                            context.getApplicationContext()));

                    mFutureSandbox.addCallback(
                            new FutureCallback<JavaScriptSandbox>() {
                                @Override
                                public void onSuccess(JavaScriptSandbox result) {
                                    mSandboxInitStopWatch.stop();
                                    mLogger.d("JSScriptEngine created.");
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    mSandboxInitStopWatch.stop();
                                    mLogger.e(t, "JavaScriptSandbox initialization failed");
                                }
                            },
                            AdServicesExecutors.getLightWeightExecutor());
                }

                return mFutureSandbox;
            }
        }

        public ListenableFuture<Void> destroyIfCurrentInstance(
                @NonNull JavaScriptSandbox javaScriptSandbox) {
            synchronized (mSandboxLock) {
                if (mFutureSandbox != null) {
                    ListenableFuture<JavaScriptSandbox> futureSandbox = mFutureSandbox;
                    return mFutureSandbox
                            .<Void>transform(
                                    jsSandbox -> {
                                        synchronized (mSandboxLock) {
                                            if (mFutureSandbox != futureSandbox) {
                                                mLogger.d(
                                                        "mFutureSandbox is already set to a"
                                                            + " different future which indicates"
                                                            + " this is not the active sandbox");
                                                return null;
                                            }
                                            if (jsSandbox == javaScriptSandbox) {
                                                mLogger.d(
                                                        "Closing connection from JSScriptEngine to"
                                                            + " WebView Sandbox as the sandbox"
                                                            + " requested is the current instance");
                                                jsSandbox.close();
                                                mFutureSandbox = null;
                                            } else {
                                                mLogger.d(
                                                        "Not closing the connection from"
                                                            + " JSScriptEngine to WebView sandbox"
                                                            + " as this is not the same instance as"
                                                            + " requested");
                                            }
                                            return null;
                                        }
                                    },
                                    AdServicesExecutors.getLightWeightExecutor())
                            .catching(
                                    Throwable.class,
                                    t -> {
                                        mLogger.w(
                                                t,
                                                "JavaScriptSandbox initialization failed,"
                                                        + " won't close");
                                        return null;
                                    },
                                    AdServicesExecutors.getLightWeightExecutor());
                } else {
                    return Futures.immediateVoidFuture();
                }
            }
        }

        /**
         * Closes the connection with {@code JavaScriptSandbox}. Any running computation will fail.
         * A new call to {@link #getFutureInstance(Context)} will create the instance again.
         */
        public ListenableFuture<Void> destroyCurrentInstance() {
            synchronized (mSandboxLock) {
                if (mFutureSandbox != null) {
                    ListenableFuture<Void> result =
                            mFutureSandbox
                                    .<Void>transform(
                                            jsSandbox -> {
                                                mLogger.d(
                                                        "Closing connection from JSScriptEngine to"
                                                                + " WebView Sandbox");
                                                jsSandbox.close();
                                                return null;
                                            },
                                            AdServicesExecutors.getLightWeightExecutor())
                                    .catching(
                                            Throwable.class,
                                            t -> {
                                                mLogger.w(
                                                        t,
                                                        "JavaScriptSandbox initialization failed,"
                                                                + " won't close");
                                                return null;
                                            },
                                            AdServicesExecutors.getLightWeightExecutor());
                    mFutureSandbox = null;
                    return result;
                } else {
                    return Futures.immediateVoidFuture();
                }
            }
        }
    }

    /**
     * @return JSScriptEngine instance
     */
    public static JSScriptEngine getInstance(
            @NonNull Context context, @NonNull LoggerFactory.Logger logger) {
        synchronized (JSScriptEngine.class) {
            if (sSingleton == null) {
                Profiler profiler = Profiler.createNoOpInstance(TAG);
                sSingleton =
                        new JSScriptEngine(
                                context,
                                new JavaScriptSandboxProvider(profiler, logger),
                                profiler,
                                // There is no blocking call or IO code in the service logic
                                AdServicesExecutors.getLightWeightExecutor(),
                                logger);
            }

            return sSingleton;
        }
    }

    /**
     * @return a singleton JSScriptEngine instance with the given profiler
     * @throws IllegalStateException if an existing instance exists
     */
    @VisibleForTesting
    public static JSScriptEngine getInstanceForTesting(
            @NonNull Context context, @NonNull Profiler profiler, LoggerFactory.Logger logger) {
        synchronized (JSScriptEngine.class) {
            // If there is no instance already created or the instance was shutdown
            if (sSingleton != null) {
                throw new IllegalStateException(
                        "Unable to initialize test JSScriptEngine multiple times using"
                                + "the real JavaScriptSandboxProvider.");
            }

            sSingleton =
                    new JSScriptEngine(
                            context,
                            new JavaScriptSandboxProvider(profiler, logger),
                            profiler,
                            AdServicesExecutors.getLightWeightExecutor(),
                            logger);
        }

        return sSingleton;
    }

    /**
     * This method will instantiate a new instance of JSScriptEngine every time. It is intended to
     * be used with a fake/mock {@link JavaScriptSandboxProvider}. Using a real one would cause
     * exception when trying to create the second instance of {@link JavaScriptSandbox}.
     *
     * @return a new JSScriptEngine instance
     */
    @VisibleForTesting
    public static JSScriptEngine createNewInstanceForTesting(
            @NonNull Context context,
            @NonNull JavaScriptSandboxProvider jsSandboxProvider,
            @NonNull Profiler profiler,
            @NonNull LoggerFactory.Logger logger) {
        return new JSScriptEngine(
                context,
                jsSandboxProvider,
                profiler,
                AdServicesExecutors.getLightWeightExecutor(),
                logger);
    }

    /**
     * Closes the connection with WebView. Any running computation will be terminated. It is not
     * necessary to recreate instances of {@link JSScriptEngine} after this call; new calls to
     * {@code evaluate} for existing instance will cause the connection to WV to be restored if
     * necessary.
     *
     * @return A future to be used by tests needing to know when the sandbox close call happened.
     */
    public ListenableFuture<Void> shutdown() {
        return mJsSandboxProvider.destroyCurrentInstance();
    }

    @VisibleForTesting
    @SuppressWarnings("FutureReturnValueIgnored")
    JSScriptEngine(
            @NonNull Context context,
            @NonNull JavaScriptSandboxProvider jsSandboxProvider,
            @NonNull Profiler profiler,
            @NonNull ListeningExecutorService executorService,
            @NonNull LoggerFactory.Logger logger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(jsSandboxProvider);
        Objects.requireNonNull(profiler);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(logger);

        this.mContext = context;
        this.mJsSandboxProvider = jsSandboxProvider;
        this.mProfiler = profiler;
        this.mExecutorService = executorService;
        this.mLogger = logger;
        // Forcing initialization of WebView
        jsSandboxProvider.getFutureInstance(mContext);
    }

    /**
     * Same as {@link #evaluate(String, List, String, IsolateSettings, RetryStrategy)} where the
     * entry point function name is {@link #ENTRY_POINT_FUNC_NAME}.
     */
    @NonNull
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull IsolateSettings isolateSettings,
            @NonNull RetryStrategy retryStrategy) {
        return evaluate(jsScript, args, ENTRY_POINT_FUNC_NAME, isolateSettings, retryStrategy);
    }

    /**
     * Invokes the function {@code entryFunctionName} defined by the JS code in {@code jsScript} and
     * return the result. It will reset the WebView status after evaluating the script.
     *
     * @param jsScript The JS script
     * @param args The arguments to pass when invoking {@code entryFunctionName}
     * @param entryFunctionName The name of a function defined in {@code jsScript} that should be
     *     invoked.
     * @return A {@link ListenableFuture} containing the JS string representation of the result of
     *     {@code entryFunctionName}'s invocation
     */
    @NonNull
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @NonNull IsolateSettings isolateSettings,
            @NonNull RetryStrategy retryStrategy) {
        return evaluateInternal(
                jsScript, args, entryFunctionName, null, isolateSettings, retryStrategy);
    }

    /**
     * Loads the WASM module defined by {@code wasmBinary}, invokes the function {@code
     * entryFunctionName} defined by the JS code in {@code jsScript} and return the result. It will
     * reset the WebView status after evaluating the script. The function is expected to accept all
     * the arguments defined in {@code args} plus an extra final parameter of type {@code
     * WebAssembly.Module}.
     *
     * @param jsScript The JS script
     * @param args The arguments to pass when invoking {@code entryFunctionName}
     * @param entryFunctionName The name of a function defined in {@code jsScript} that should be
     *     invoked.
     * @return A {@link ListenableFuture} containing the JS string representation of the result of
     *     {@code entryFunctionName}'s invocation
     */
    @NonNull
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull byte[] wasmBinary,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @NonNull IsolateSettings isolateSettings,
            @NonNull RetryStrategy retryStrategy) {
        Objects.requireNonNull(wasmBinary);

        return evaluateInternal(
                jsScript, args, entryFunctionName, wasmBinary, isolateSettings, retryStrategy);
    }

    @NonNull
    private ListenableFuture<String> evaluateInternal(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @Nullable byte[] wasmBinary,
            @NonNull IsolateSettings isolateSettings,
            @NonNull RetryStrategy retryStrategy) {
        Objects.requireNonNull(jsScript);
        Objects.requireNonNull(args);
        Objects.requireNonNull(entryFunctionName);
        Objects.requireNonNull(retryStrategy);
        return retryStrategy.call(
                () ->
                        ClosingFuture.from(mJsSandboxProvider.getFutureInstance(mContext))
                                .transformAsync(
                                        (closer, jsSandbox) ->
                                                evaluateOnSandbox(
                                                        closer,
                                                        jsSandbox,
                                                        jsScript,
                                                        args,
                                                        entryFunctionName,
                                                        wasmBinary,
                                                        isolateSettings),
                                        mExecutorService)
                                .finishToFuture(),
                RETRYABLE_EXCEPTIONS_FROM_JS_ENGINE,
                mLogger,
                JS_EVALUATE_METHOD_NAME);
    }

    @NonNull
    private ClosingFuture<String> evaluateOnSandbox(
            @NonNull ClosingFuture.DeferredCloser closer,
            @NonNull JavaScriptSandbox jsSandbox,
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @Nullable byte[] wasmBinary,
            @NonNull IsolateSettings isolateSettings) {

        boolean hasWasmModule = wasmBinary != null;
        if (hasWasmModule) {
            Preconditions.checkState(
                    isWasmSupported(jsSandbox),
                    "Cannot evaluate a JS script depending on WASM on the JS"
                            + " Sandbox available on this device");
        }

        JavaScriptIsolate jsIsolate = createIsolate(jsSandbox, isolateSettings);
        closer.eventuallyClose(new CloseableIsolateWrapper(jsIsolate, mLogger), mExecutorService);

        if (hasWasmModule) {
            mLogger.d(
                    "Evaluating JS script with associated WASM on thread %s",
                    Thread.currentThread().getName());
            try {
                jsIsolate.provideNamedData(WASM_MODULE_BYTES_ID, wasmBinary);
            } catch (IllegalStateException ise) {
                mLogger.d(ise, "Unable to pass WASM byte array to JS Isolate");
                throw new JSExecutionException("Unable to pass WASM byte array to JS Isolate", ise);
            }
        } else {
            mLogger.d("Evaluating JS script on thread %s", Thread.currentThread().getName());
        }

        String entryPointCall = callEntryPoint(args, entryFunctionName, hasWasmModule);

        String fullScript = jsScript + "\n" + entryPointCall;
        mLogger.v("Calling WebView for script %s", fullScript);

        StopWatch jsExecutionStopWatch =
                mProfiler.start(JSScriptEngineLogConstants.JAVA_EXECUTION_TIME);
        int traceCookie = Tracing.beginAsyncSection(Tracing.JSSCRIPTENGINE_EVALUATE_ON_SANDBOX);
        return ClosingFuture.from(jsIsolate.evaluateJavaScriptAsync(fullScript))
                .transform(
                        (ignoredCloser, result) -> {
                            jsExecutionStopWatch.stop();
                            mLogger.v("WebView result is " + result);
                            Tracing.endAsyncSection(
                                    Tracing.JSSCRIPTENGINE_EVALUATE_ON_SANDBOX, traceCookie);
                            return result;
                        },
                        mExecutorService)
                .catching(
                        Exception.class,
                        (ignoredCloser, exception) -> {
                            mLogger.v("Failure running JS in WebView: " + exception.getMessage());
                            jsExecutionStopWatch.stop();
                            Tracing.endAsyncSection(
                                    Tracing.JSSCRIPTENGINE_EVALUATE_ON_SANDBOX, traceCookie);
                            if (exception instanceof SandboxDeadException) {
                                /*
                                   Although we are already checking for this during createIsolate
                                   method, the creation might be successful in edge cases.
                                   However the evaluation will fail with SandboxDeadException.
                                   Whenever we encounter this error, we should ensure to destroy
                                   the current instance as all other evaluations will fail.
                                */
                                mJsSandboxProvider.destroyIfCurrentInstance(jsSandbox);
                                throw new JSScriptEngineConnectionException(
                                        JS_SCRIPT_ENGINE_SANDBOX_DEAD_MSG, exception);
                            } else if (exception instanceof MemoryLimitExceededException) {
                                /*
                                  In case of androidx.javascriptengine.MemoryLimitExceededException
                                  we should not retry the JS Evaluation but close the current
                                  instance of Javascript Sandbox.
                                */
                                mJsSandboxProvider.destroyIfCurrentInstance(jsSandbox);
                            }
                            throw new JSExecutionException(
                                    "Failure running JS in WebView: " + exception.getMessage(),
                                    exception);
                        },
                        mExecutorService);
    }

    private boolean isWasmSupported(JavaScriptSandbox jsSandbox) {
        boolean wasmCompilationSupported =
                jsSandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_WASM_COMPILATION);
        // We will pass the WASM binary via `provideNamesData`
        // The JS will read the data using android.consumeNamedDataAsArrayBuffer
        boolean provideConsumeArrayBufferSupported =
                jsSandbox.isFeatureSupported(
                        JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER);
        // The call android.consumeNamedDataAsArrayBuffer to read the WASM byte array
        // returns a promises so all our code will be in a promise chain
        boolean promiseReturnSupported =
                jsSandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN);
        mLogger.v(
                String.format(
                        "Is WASM supported? WASM_COMPILATION: %b  PROVIDE_CONSUME_ARRAY_BUFFER: %b,"
                                + " PROMISE_RETURN: %b",
                        wasmCompilationSupported,
                        provideConsumeArrayBufferSupported,
                        promiseReturnSupported));
        return wasmCompilationSupported
                && provideConsumeArrayBufferSupported
                && promiseReturnSupported;
    }

    /**
     * @return a future value indicating if the JS Sandbox installed on the device supports WASM
     *     execution or an error if the connection to the JS Sandbox failed.
     */
    public ListenableFuture<Boolean> isWasmSupported() {
        return mJsSandboxProvider
                .getFutureInstance(mContext)
                .transform(this::isWasmSupported, mExecutorService);
    }

    /**
     * @return a future value indicating if the JS Sandbox installed on the device supports
     *     configurable Heap size.
     */
    @VisibleForTesting
    public ListenableFuture<Boolean> isConfigurableHeapSizeSupported() {
        return mJsSandboxProvider
                .getFutureInstance(mContext)
                .transform(this::isConfigurableHeapSizeSupported, mExecutorService);
    }

    private boolean isConfigurableHeapSizeSupported(JavaScriptSandbox jsSandbox) {
        boolean isConfigurableHeapSupported =
                jsSandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE);
        mLogger.v("Is configurable max heap size supported? : %b", isConfigurableHeapSupported);
        return isConfigurableHeapSupported;
    }

    /**
     * Creates a new isolate. This method handles the case where the `JavaScriptSandbox` process has
     * been terminated by closing this connection. The ongoing call will fail, we won't try to
     * recover it to keep the code simple.
     *
     * <p>Throws error in case, we have enforced max heap memory restrictions and isolate does not
     * support that feature
     */
    private JavaScriptIsolate createIsolate(
            JavaScriptSandbox jsSandbox, IsolateSettings isolateSettings) {
        // TODO: b/321237839. After upgrading the dependency on javascriptengine to beta1, revisit
        // the exception handling of this method.
        int traceCookie = Tracing.beginAsyncSection(Tracing.JSSCRIPTENGINE_CREATE_ISOLATE);
        StopWatch isolateStopWatch =
                mProfiler.start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME);
        try {
            if (!isConfigurableHeapSizeSupported(jsSandbox)
                    && isolateSettings.getEnforceMaxHeapSizeFeature()) {
                mLogger.e("Memory limit enforcement required, but not supported by Isolate");
                throw new IllegalStateException(NON_SUPPORTED_MAX_HEAP_SIZE_EXCEPTION_MSG);
            }

            JavaScriptIsolate javaScriptIsolate;
            if (isolateSettings.getEnforceMaxHeapSizeFeature()
                    && isolateSettings.getMaxHeapSizeBytes() > 0) {
                mLogger.d(
                        "Creating JS isolate with memory limit: %d bytes",
                        isolateSettings.getMaxHeapSizeBytes());
                IsolateStartupParameters startupParams = new IsolateStartupParameters();
                startupParams.setMaxHeapSizeBytes(isolateSettings.getMaxHeapSizeBytes());
                javaScriptIsolate = jsSandbox.createIsolate(startupParams);
            } else {
                mLogger.d("Creating JS isolate with unbounded memory limit");
                javaScriptIsolate = jsSandbox.createIsolate();
            }
            return javaScriptIsolate;
        } catch (IllegalStateException isolateMemoryLimitUnsupported) {
            mLogger.e(
                    "JavaScriptIsolate does not support setting max heap size, cannot create an"
                            + " isolate to run JS code into.");
            throw new JSScriptEngineConnectionException(
                    JS_SCRIPT_ENGINE_CONNECTION_EXCEPTION_MSG, isolateMemoryLimitUnsupported);
        } catch (RuntimeException jsSandboxIsDisconnected) {
            mLogger.e(
                    "JavaScriptSandboxProcess is disconnected, cannot create an isolate to run JS"
                            + " code into. Resetting connection with AwJavaScriptSandbox to enable"
                            + " future calls.");
            mJsSandboxProvider.destroyIfCurrentInstance(jsSandbox);
            throw new JSScriptEngineConnectionException(
                    JS_SCRIPT_ENGINE_CONNECTION_EXCEPTION_MSG, jsSandboxIsDisconnected);
        } finally {
            isolateStopWatch.stop();
            Tracing.endAsyncSection(Tracing.JSSCRIPTENGINE_CREATE_ISOLATE, traceCookie);
        }
    }

    /**
     * @return The JS code for the definition an anonymous function containing the declaration of
     *     the value of {@code args} and the invocation of the given {@code entryFunctionName}. If
     *     the {@code addWasmBinary} parameter is true, the target function is expected to accept an
     *     extra final parameter 'wasmModule' of type {@code WebAssembly.Module} and the method will
     *     return a promise.
     */
    @NonNull
    private String callEntryPoint(
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            boolean addWasmBinary) {
        StringBuilder resultBuilder = new StringBuilder("(function() {\n");
        // Declare args as constant inside this function closure to avoid any direct access by
        // the functions in the script we are calling.
        for (JSScriptArgument arg : args) {
            // Avoiding to use addJavaScriptInterface because too expensive, just
            // declaring the string parameter as part of the script.
            resultBuilder.append(arg.variableDeclaration());
            resultBuilder.append("\n");
        }

        String argumentPassing =
                args.stream().map(JSScriptArgument::name).collect(Collectors.joining(","));
        if (addWasmBinary) {
            argumentPassing += "," + WASM_MODULE_ARG_NAME;
            resultBuilder.append(
                    String.format(
                            "return android.consumeNamedDataAsArrayBuffer(\"%s\")"
                                    + ".then((__value) => {\n"
                                    + " return WebAssembly.compile(__value).then((%s) => {\n",
                            WASM_MODULE_BYTES_ID, WASM_MODULE_ARG_NAME));
        }

        // Call entryFunctionName with the constants just declared as parameters
        resultBuilder.append(
                String.format(
                        "return JSON.stringify(%s(%s));\n", entryFunctionName, argumentPassing));

        if (addWasmBinary) {
            resultBuilder.append("})});\n");
        }
        resultBuilder.append("})();\n");

        return resultBuilder.toString();
    }

    /**
     * Checks if JS Sandbox is available in the WebView version that is installed on the device
     * before attempting to create it. Attempting to create JS Sandbox when it's not available
     * results in returning of a null value.
     */
    public static class AvailabilityChecker {

        /**
         * @return true if JS Sandbox is available in the current WebView version, false otherwise.
         */
        public static boolean isJSSandboxAvailable() {
            return JavaScriptSandbox.isSupported();
        }
    }

    /**
     * Wrapper class required to convert an {@link java.lang.AutoCloseable} {@link
     * JavaScriptIsolate} into a {@link Closeable} type.
     */
    private static class CloseableIsolateWrapper implements Closeable {
        @NonNull final JavaScriptIsolate mIsolate;

        @NonNull final LoggerFactory.Logger mLogger;

        CloseableIsolateWrapper(@NonNull JavaScriptIsolate isolate, LoggerFactory.Logger logger) {
            Objects.requireNonNull(isolate);
            mIsolate = isolate;
            mLogger = logger;
        }

        @Override
        public void close() {
            int traceCookie = Tracing.beginAsyncSection(Tracing.JSSCRIPTENGINE_CLOSE_ISOLATE);
            mLogger.d("Closing WebView isolate");
            // Closing the isolate will also cause the thread in WebView to be terminated if
            // still running.
            // There is no need to verify if ISOLATE_TERMINATION is supported by WebView
            // because there is no new API but just new capability on the WebView side for
            // existing API.
            mIsolate.close();
            Tracing.endAsyncSection(Tracing.JSSCRIPTENGINE_CLOSE_ISOLATE, traceCookie);
        }
    }
}
