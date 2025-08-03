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

package com.android.permissioncontroller.permission.data

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.ensureMainThread
import com.android.permissioncontroller.permission.utils.getInitializedValue
import com.android.permissioncontroller.permission.utils.shortStackTrace
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * A MediatorLiveData which tracks how long it has been inactive, compares new values before setting
 * its value (avoiding unnecessary updates), and can calculate the set difference between a list and
 * a map (used when determining whether or not to add a LiveData as a source).
 *
 * @param isStaticVal Whether or not this LiveData value is expected to change
 */
abstract class SmartUpdateMediatorLiveData<T>(private val isStaticVal: Boolean = false) :
    MediatorLiveData<T>(), DataRepository.InactiveTimekeeper {

    companion object {
        const val DEBUG = false
        val LOG_TAG = SmartUpdateMediatorLiveData::class.java.simpleName
    }

    /**
     * Boolean, whether or not this liveData has a stale value or not. Every time the liveData goes
     * inactive, its data becomes stale, until it goes active again, and is explicitly set.
     */
    var isStale = true
        private set

    private val sources = mutableListOf<SmartUpdateMediatorLiveData<*>>()

    @MainThread
    override fun setValue(newValue: T?) {
        ensureMainThread()

        if (!isInitialized) {
            // If we have received an invalid value, and this is the first time we are set,
            // notify observers.
            if (newValue == null) {
                isStale = false
                super.setValue(newValue)
                return
            }
        }

        val wasStale = isStale
        // If this liveData is not active, and is not a static value, then it is stale
        val isActiveOrStaticVal = isStaticVal || hasActiveObservers()
        // If all of this liveData's sources are non-stale, and this liveData is active or is a
        // static val, then it is non stale
        isStale = !(sources.all { !it.isStale } && isActiveOrStaticVal)

        if (valueNotEqual(super.getValue(), newValue) || (wasStale && !isStale)) {
            super.setValue(newValue)
        }
    }

    /**
     * Update the value of this LiveData.
     *
     * This usually results in an IPC when active and no action otherwise.
     */
    @MainThread
    fun update() {
        if (DEBUG) {
            Log.i(LOG_TAG, "update ${javaClass.simpleName} ${shortStackTrace()}")
        }

        if (this is SmartAsyncMediatorLiveData<T>) {
            isStale = true
        }
        onUpdate()
    }

    @MainThread protected abstract fun onUpdate()

    override var timeWentInactive: Long? = System.nanoTime()

    /**
     * Some LiveDatas have types, like Drawables which do not have a non-default equals method.
     * Those classes can override this method to change when the value is set upon calling setValue.
     *
     * @param valOne The first T to be compared
     * @param valTwo The second T to be compared
     * @return True if the two values are different, false otherwise
     */
    protected open fun valueNotEqual(valOne: T?, valTwo: T?): Boolean {
        return valOne != valTwo
    }

    override fun <S : Any?> addSource(source: LiveData<S>, onChanged: Observer<in S>) {
        addSourceWithStackTraceAttribution(source, onChanged)
    }

    private fun <S : Any?> addSourceWithStackTraceAttribution(
        source: LiveData<S>,
        onChanged: Observer<in S>
    ) {
        val stackTrace =
            if (DEBUG) {
                IllegalStateException().stackTrace
            } else {
                null
            }

        GlobalScope.launch(Main.immediate) {
            if (source is SmartUpdateMediatorLiveData) {
                if (source in sources) {
                    return@launch
                }
                sources.add(source)
            }
            try {
                super.addSource(source, onChanged)
            } catch (ex: IllegalStateException) {
                if (DEBUG) {
                    ex.stackTrace = stackTrace!!
                }
                throw ex
            }
        }
    }

    override fun <S : Any?> removeSource(toRemote: LiveData<S>) {
        GlobalScope.launch(Main.immediate) {
            if (toRemote is SmartUpdateMediatorLiveData) {
                sources.remove(toRemote)
            }
            super.removeSource(toRemote)
        }
    }

    /**
     * Gets the difference between a list and a map of livedatas, and then will add as a source all
     * livedatas which are in the list, but not the map, and will remove all livedatas which are in
     * the map, but not the list
     *
     * @param desired The list of liveDatas we want in our map, represented by a key
     * @param have The map of livedatas we currently have as sources
     * @param getLiveDataFun A function to turn a key into a liveData
     * @param onUpdateFun An optional function which will update differently based on different
     *   LiveDatas. If blank, will simply call update.
     * @return a pair of (all keys added, all keys removed)
     */
    fun <K, V : LiveData<*>> setSourcesToDifference(
        desired: Collection<K>,
        have: MutableMap<K, V>,
        getLiveDataFun: (K) -> V,
        onUpdateFun: ((K) -> Unit)? = null
    ): Pair<Set<K>, Set<K>> {
        // Ensure the map is correct when method returns
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(desired, have)
        for (key in toAdd) {
            have[key] = getLiveDataFun(key)
        }

        val removed = toRemove.map { have.remove(it) }.toMutableList()

        GlobalScope.launch(Main.immediate) {
            // If any state got out of sorts before this coroutine ran, correct it
            for (key in toRemove) {
                removed.add(have.remove(key) ?: continue)
            }

            for (liveData in removed) {
                removeSource(liveData ?: continue)
            }

            for (key in toAdd) {
                val liveData = getLiveDataFun(key)
                // Should be a no op, but there is a slight possibility it isn't
                have[key] = liveData
                val observer =
                    Observer<Any?> {
                        if (onUpdateFun != null) {
                            onUpdateFun(key)
                        } else {
                            update()
                        }
                    }
                addSourceWithStackTraceAttribution(liveData, observer)
            }
        }
        return toAdd to toRemove
    }

    override fun onActive() {
        timeWentInactive = null
        // If this is not an async livedata, and we have sources, and all sources are non-stale,
        // force update our value
        if (
            sources.isNotEmpty() &&
                sources.all { !it.isStale } &&
                this !is SmartAsyncMediatorLiveData<T>
        ) {
            update()
        }
        super.onActive()
    }

    override fun onInactive() {
        timeWentInactive = System.nanoTime()
        if (!isStaticVal) {
            isStale = true
        }
        super.onInactive()
    }

    /**
     * Get the [initialized][isInitialized] value, suspending until one is available
     *
     * @param staleOk whether [isStale] value is ok to return
     * @param forceUpdate whether to call [update] (usually triggers an IPC)
     */
    suspend fun getInitializedValue(staleOk: Boolean = false, forceUpdate: Boolean = false): T {
        return getInitializedValue(
            observe = { observer ->
                observeForever(observer)
                if (forceUpdate || (!staleOk && isStale)) {
                    update()
                }
            },
            isValueInitialized = { isInitialized && (staleOk || !isStale) }
        )
    }
}
