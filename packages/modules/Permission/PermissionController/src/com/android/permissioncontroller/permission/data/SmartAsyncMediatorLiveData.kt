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

import android.os.Binder
import com.android.permissioncontroller.permission.utils.IPC
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A LiveData which loads its data in a background AsyncTask. It will cancel current tasks, if new
 * requests come during execution
 *
 * @param isStaticVal Whether or not this LiveData value is expected to change
 * @param alwaysUpdateOnActive Whether or not this LiveData should update upon going active
 */
abstract class SmartAsyncMediatorLiveData<T>(
    isStaticVal: Boolean = false,
    private val alwaysUpdateOnActive: Boolean = true
) : SmartUpdateMediatorLiveData<T>(isStaticVal) {

    private var currentJob: Job? = null
    @Volatile private var jobQueued = false
    @Volatile private var jobRunning = false

    /**
     * The main function which will load data. It should periodically check isCancelled to see if it
     * should stop working. If data is loaded, it should call "postValue".
     */
    abstract suspend fun loadDataAndPostValue(job: Job)

    override fun onUpdate() {
        updateAsync()
    }

    open fun updateAsync() {
        if (jobRunning) {
            jobQueued = true
            return
        } else {
            jobRunning = true
        }

        GlobalScope.launch(IPC) {
            currentJob = coroutineContext[Job]
            loadDataAndPostValue(currentJob!!)
            // TODO ntmyren: generalize this command to the IPC dispatcher
            Binder.flushPendingCommands()
            jobRunning = false
            if (jobQueued) {
                jobQueued = false
                GlobalScope.launch(Main.immediate) { updateAsync() }
            }
        }
    }

    override fun onActive() {
        super.onActive()

        if (alwaysUpdateOnActive) {
            updateAsync()
        }
    }

    override fun onInactive() {
        cancelJobIfRunning()
        jobQueued = false
        super.onInactive()
    }

    private fun cancelJobIfRunning() {
        currentJob?.let { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
    }
}
