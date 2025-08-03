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
package com.android.intentresolver.shortcuts

import android.app.ActivityManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.pm.ShortcutManager.ShareShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import android.service.chooser.ChooserTarget
import android.text.TextUtils
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.measurements.Tracer
import com.android.intentresolver.measurements.runTracing
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Encapsulates shortcuts loading logic from either AppPredictor or ShortcutManager.
 *
 * A ShortcutLoader instance can be viewed as a per-profile singleton hot stream of shortcut
 * updates. The shortcut loading is triggered in the constructor or by the [reset] method, the
 * processing happens on the [dispatcher] and the result is delivered through the [callback] on the
 * default [scope]'s dispatcher, the main thread.
 */
@OpenForTesting
open class ShortcutLoader
@VisibleForTesting
constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appPredictor: AppPredictorProxy?,
    private val userHandle: UserHandle,
    private val isPersonalProfile: Boolean,
    private val targetIntentFilter: IntentFilter?,
    private val dispatcher: CoroutineDispatcher,
    private val callback: Consumer<Result>
) {
    private val shortcutToChooserTargetConverter = ShortcutToChooserTargetConverter()
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val appPredictorCallback =
        ScopedAppTargetListCallback(scope) { onAppPredictorCallback(it) }.toAppPredictorCallback()

    private val appTargetSource =
        MutableSharedFlow<Array<DisplayResolveInfo>?>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    private val shortcutSource =
        MutableSharedFlow<ShortcutData?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isDestroyed
        get() = !scope.isActive

    @MainThread
    constructor(
        context: Context,
        scope: CoroutineScope,
        appPredictor: AppPredictor?,
        userHandle: UserHandle,
        targetIntentFilter: IntentFilter?,
        callback: Consumer<Result>
    ) : this(
        context,
        scope,
        appPredictor?.let { AppPredictorProxy(it) },
        userHandle,
        userHandle == UserHandle.of(ActivityManager.getCurrentUser()),
        targetIntentFilter,
        Dispatchers.IO,
        callback
    )

    init {
        appPredictor?.registerPredictionUpdates(dispatcher.asExecutor(), appPredictorCallback)
        scope
            .launch {
                appTargetSource
                    .combine(shortcutSource) { appTargets, shortcutData ->
                        if (appTargets == null || shortcutData == null) {
                            null
                        } else {
                            runTracing("filter-shortcuts-${userHandle.identifier}") {
                                filterShortcuts(
                                    appTargets,
                                    shortcutData.shortcuts,
                                    shortcutData.isFromAppPredictor,
                                    shortcutData.appPredictorTargets
                                )
                            }
                        }
                    }
                    .filter { it != null }
                    .flowOn(dispatcher)
                    .collect { callback.accept(it ?: error("can not be null")) }
            }
            .invokeOnCompletion {
                runCatching { appPredictor?.unregisterPredictionUpdates(appPredictorCallback) }
                Log.d(TAG, "destroyed, user: $userHandle")
            }
        reset()
    }

    /** Clear application targets (see [updateAppTargets] and initiate shortcuts loading. */
    @OpenForTesting
    open fun reset() {
        Log.d(TAG, "reset shortcut loader for user $userHandle")
        appTargetSource.tryEmit(null)
        shortcutSource.tryEmit(null)
        scope.launch(dispatcher) { loadShortcuts() }
    }

    /**
     * Update resolved application targets; as soon as shortcuts are loaded, they will be filtered
     * against the targets and the is delivered to the client through the [callback].
     */
    @OpenForTesting
    open fun updateAppTargets(appTargets: Array<DisplayResolveInfo>) {
        appTargetSource.tryEmit(appTargets)
    }

    @WorkerThread
    private fun loadShortcuts() {
        // no need to query direct share for work profile when its locked or disabled
        if (!shouldQueryDirectShareTargets()) {
            Log.d(TAG, "skip shortcuts loading for user $userHandle")
            return
        }
        Log.d(TAG, "querying direct share targets for user $userHandle")
        queryDirectShareTargets(false)
    }

    @WorkerThread
    private fun queryDirectShareTargets(skipAppPredictionService: Boolean) {
        if (!skipAppPredictionService && appPredictor != null) {
            try {
                Log.d(TAG, "query AppPredictor for user $userHandle")
                Tracer.beginAppPredictorQueryTrace(userHandle)
                appPredictor.requestPredictionUpdate()
                return
            } catch (e: Throwable) {
                endAppPredictorQueryTrace(userHandle)
                // we might have been destroyed concurrently, nothing left to do
                if (isDestroyed) {
                    return
                }
                Log.e(TAG, "Failed to query AppPredictor for user $userHandle", e)
            }
        }
        // Default to just querying ShortcutManager if AppPredictor not present.
        if (targetIntentFilter == null) {
            Log.d(TAG, "skip querying ShortcutManager for $userHandle")
            sendShareShortcutInfoList(
                emptyList(),
                isFromAppPredictor = false,
                appPredictorTargets = null
            )
            return
        }
        Log.d(TAG, "query ShortcutManager for user $userHandle")
        val shortcuts =
            runTracing("shortcut-mngr-${userHandle.identifier}") {
                queryShortcutManager(targetIntentFilter)
            }
        Log.d(TAG, "receive shortcuts from ShortcutManager for user $userHandle")
        sendShareShortcutInfoList(shortcuts, false, null)
    }

    @WorkerThread
    private fun queryShortcutManager(targetIntentFilter: IntentFilter): List<ShareShortcutInfo> {
        val selectedProfileContext = context.createContextAsUser(userHandle, 0 /* flags */)
        val sm =
            selectedProfileContext.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager?
        val pm = context.createContextAsUser(userHandle, 0 /* flags */).packageManager
        return sm?.getShareTargets(targetIntentFilter)?.filter {
            pm.isPackageEnabled(it.targetComponent.packageName)
        }
            ?: emptyList()
    }

    @WorkerThread
    private fun onAppPredictorCallback(appPredictorTargets: List<AppTarget>) {
        endAppPredictorQueryTrace(userHandle)
        Log.d(TAG, "receive app targets from AppPredictor")
        if (appPredictorTargets.isEmpty() && shouldQueryDirectShareTargets()) {
            // APS may be disabled, so try querying targets ourselves.
            queryDirectShareTargets(true)
            return
        }
        val pm = context.createContextAsUser(userHandle, 0).packageManager
        val pair = appPredictorTargets.toShortcuts(pm)
        sendShareShortcutInfoList(pair.shortcuts, true, pair.appTargets)
    }

    @WorkerThread
    private fun List<AppTarget>.toShortcuts(pm: PackageManager): ShortcutsAppTargetsPair =
        fold(ShortcutsAppTargetsPair(ArrayList(size), ArrayList(size))) { acc, appTarget ->
            val shortcutInfo = appTarget.shortcutInfo
            val packageName = appTarget.packageName
            val className = appTarget.className
            if (shortcutInfo != null && className != null && pm.isPackageEnabled(packageName)) {
                (acc.shortcuts as ArrayList<ShareShortcutInfo>).add(
                    ShareShortcutInfo(shortcutInfo, ComponentName(packageName, className))
                )
                (acc.appTargets as ArrayList<AppTarget>).add(appTarget)
            }
            acc
        }

    @WorkerThread
    private fun sendShareShortcutInfoList(
        shortcuts: List<ShareShortcutInfo>,
        isFromAppPredictor: Boolean,
        appPredictorTargets: List<AppTarget>?
    ) {
        shortcutSource.tryEmit(ShortcutData(shortcuts, isFromAppPredictor, appPredictorTargets))
    }

    private fun filterShortcuts(
        appTargets: Array<DisplayResolveInfo>,
        shortcuts: List<ShareShortcutInfo>,
        isFromAppPredictor: Boolean,
        appPredictorTargets: List<AppTarget>?
    ): Result {
        if (appPredictorTargets != null && appPredictorTargets.size != shortcuts.size) {
            throw RuntimeException(
                "resultList and appTargets must have the same size." +
                    " resultList.size()=" +
                    shortcuts.size +
                    " appTargets.size()=" +
                    appPredictorTargets.size
            )
        }
        val directShareAppTargetCache = HashMap<ChooserTarget, AppTarget>()
        val directShareShortcutInfoCache = HashMap<ChooserTarget, ShortcutInfo>()
        // Match ShareShortcutInfos with DisplayResolveInfos to be able to use the old code path
        // for direct share targets. After ShareSheet is refactored we should use the
        // ShareShortcutInfos directly.
        val resultRecords: MutableList<ShortcutResultInfo> = ArrayList()
        for (displayResolveInfo in appTargets) {
            val matchingShortcuts =
                shortcuts.filter { it.targetComponent == displayResolveInfo.resolvedComponentName }
            if (matchingShortcuts.isEmpty()) continue
            val chooserTargets =
                shortcutToChooserTargetConverter.convertToChooserTarget(
                    matchingShortcuts,
                    shortcuts,
                    appPredictorTargets,
                    directShareAppTargetCache,
                    directShareShortcutInfoCache
                )
            val resultRecord = ShortcutResultInfo(displayResolveInfo, chooserTargets)
            resultRecords.add(resultRecord)
        }
        return Result(
            isFromAppPredictor,
            appTargets,
            resultRecords.toTypedArray(),
            directShareAppTargetCache,
            directShareShortcutInfoCache
        )
    }

    /**
     * Returns `false` if `userHandle` is the work profile and it's either in quiet mode or not
     * running.
     */
    private fun shouldQueryDirectShareTargets(): Boolean = isPersonalProfile || isProfileActive

    @get:VisibleForTesting
    protected val isProfileActive: Boolean
        get() =
            userManager.isUserRunning(userHandle) &&
                userManager.isUserUnlocked(userHandle) &&
                !userManager.isQuietModeEnabled(userHandle)

    private class ShortcutData(
        val shortcuts: List<ShareShortcutInfo>,
        val isFromAppPredictor: Boolean,
        val appPredictorTargets: List<AppTarget>?
    )

    /** Resolved shortcuts with corresponding app targets. */
    class Result(
        val isFromAppPredictor: Boolean,
        /**
         * Input app targets (see [ShortcutLoader.updateAppTargets] the shortcuts were process
         * against.
         */
        val appTargets: Array<DisplayResolveInfo>,
        /** Shortcuts grouped by app target. */
        val shortcutsByApp: Array<ShortcutResultInfo>,
        val directShareAppTargetCache: Map<ChooserTarget, AppTarget>,
        val directShareShortcutInfoCache: Map<ChooserTarget, ShortcutInfo>
    )

    /** Shortcuts grouped by app. */
    class ShortcutResultInfo(
        val appTarget: DisplayResolveInfo,
        val shortcuts: List<ChooserTarget?>
    )

    private class ShortcutsAppTargetsPair(
        val shortcuts: List<ShareShortcutInfo>,
        val appTargets: List<AppTarget>?
    )

    /** A wrapper around AppPredictor to facilitate unit-testing. */
    @VisibleForTesting
    open class AppPredictorProxy internal constructor(private val mAppPredictor: AppPredictor) {
        /** [AppPredictor.registerPredictionUpdates] */
        open fun registerPredictionUpdates(
            callbackExecutor: Executor,
            callback: AppPredictor.Callback
        ) = mAppPredictor.registerPredictionUpdates(callbackExecutor, callback)

        /** [AppPredictor.unregisterPredictionUpdates] */
        open fun unregisterPredictionUpdates(callback: AppPredictor.Callback) =
            mAppPredictor.unregisterPredictionUpdates(callback)

        /** [AppPredictor.requestPredictionUpdate] */
        open fun requestPredictionUpdate() = mAppPredictor.requestPredictionUpdate()
    }

    companion object {
        private const val TAG = "ShortcutLoader"

        private fun PackageManager.isPackageEnabled(packageName: String): Boolean {
            if (TextUtils.isEmpty(packageName)) {
                return false
            }
            return runCatching {
                    val appInfo =
                        getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(
                                PackageManager.GET_META_DATA.toLong()
                            )
                        )
                    appInfo.enabled && (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) == 0
                }
                .getOrDefault(false)
        }

        private fun endAppPredictorQueryTrace(userHandle: UserHandle) {
            val duration = Tracer.endAppPredictorQueryTrace(userHandle)
            Log.d(TAG, "AppPredictor query duration for user $userHandle: $duration ms")
        }
    }
}
