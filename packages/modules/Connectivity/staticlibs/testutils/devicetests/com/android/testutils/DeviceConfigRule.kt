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

package com.android.testutils

import android.Manifest.permission.READ_DEVICE_CONFIG
import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.provider.DeviceConfig
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.testutils.FunctionalUtils.ThrowingRunnable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

private val TAG = DeviceConfigRule::class.simpleName

private const val TIMEOUT_MS = 20_000L

/**
 * A [TestRule] that helps set [DeviceConfig] for tests and clean up the test configuration
 * automatically on teardown.
 *
 * The rule can also optionally retry tests when they fail following an external change of
 * DeviceConfig before S; this typically happens because device config flags are synced while the
 * test is running, and DisableConfigSyncTargetPreparer is only usable starting from S.
 *
 * @param retryCountBeforeSIfConfigChanged if > 0, when the test fails before S, check if
 *        the configs that were set through this rule were changed, and retry the test
 *        up to the specified number of times if yes.
 */
class DeviceConfigRule @JvmOverloads constructor(
    val retryCountBeforeSIfConfigChanged: Int = 0
) : TestRule {
    // Maps (namespace, key) -> value
    private val originalConfig = mutableMapOf<Pair<String, String>, String?>()
    private val usedConfig = mutableMapOf<Pair<String, String>, String?>()

    /**
     * Actions to be run after cleanup of the config, for the current test only.
     */
    private val currentTestCleanupActions = mutableListOf<ThrowingRunnable>()

    override fun apply(base: Statement, description: Description): Statement {
        return TestValidationUrlStatement(base, description)
    }

    private inner class TestValidationUrlStatement(
        private val base: Statement,
        private val description: Description
    ) : Statement() {
        override fun evaluate() {
            var retryCount = if (SdkLevel.isAtLeastS()) 1 else retryCountBeforeSIfConfigChanged + 1
            while (retryCount > 0) {
                retryCount--
                tryTest {
                    base.evaluate()
                    // Can't use break/return out of a loop here because this is a tryTest lambda,
                    // so set retryCount to exit instead
                    retryCount = 0
                }.catch<Throwable> { e -> // junit AssertionFailedError does not extend Exception
                    if (retryCount == 0) throw e
                    usedConfig.forEach { (key, value) ->
                        val currentValue = runAsShell(READ_DEVICE_CONFIG) {
                            DeviceConfig.getProperty(key.first, key.second)
                        }
                        if (currentValue != value) {
                            Log.w(TAG, "Test failed with unexpected device config change, retrying")
                            return@catch
                        }
                    }
                    throw e
                } cleanupStep {
                    runAsShell(WRITE_DEVICE_CONFIG) {
                        originalConfig.forEach { (key, value) ->
                            DeviceConfig.setProperty(
                                    key.first, key.second, value, false /* makeDefault */)
                        }
                    }
                } cleanupStep {
                    originalConfig.clear()
                    usedConfig.clear()
                } cleanup {
                    // Fold all cleanup actions into cleanup steps of an empty tryTest, so they are
                    // all run even if exceptions are thrown, and exceptions are reported properly.
                    currentTestCleanupActions.fold(tryTest { }) {
                        tryBlock, action -> tryBlock.cleanupStep { action.run() }
                    }.cleanup {
                        currentTestCleanupActions.clear()
                    }
                }
            }
        }
    }

    /**
     * Set a configuration key/value. After the test case ends, it will be restored to the value it
     * had when this method was first called.
     */
    fun setConfig(namespace: String, key: String, value: String?): String? {
        Log.i(TAG, "Setting config \"$key\" to \"$value\"")
        val readWritePermissions = arrayOf(READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG)

        val keyPair = Pair(namespace, key)
        val existingValue = runAsShell(*readWritePermissions) {
            DeviceConfig.getProperty(namespace, key)
        }
        if (!originalConfig.containsKey(keyPair)) {
            originalConfig[keyPair] = existingValue
        }
        usedConfig[keyPair] = value
        if (existingValue == value) {
            // Already the correct value. There may be a race if a change is already in flight,
            // but if multiple threads update the config there is no way to fix that anyway.
            Log.i(TAG, "\"$key\" already had value \"$value\"")
            return value
        }

        val future = CompletableFuture<String>()
        val listener = DeviceConfig.OnPropertiesChangedListener {
            // The listener receives updates for any change to any key, so don't react to
            // changes that do not affect the relevant key
            if (!it.keyset.contains(key)) return@OnPropertiesChangedListener
            // "null" means absent in DeviceConfig : there is no such thing as a present but
            // null value, so the following works even if |value| is null.
            if (it.getString(key, null) == value) {
                future.complete(value)
            }
        }

        return tryTest {
            runAsShell(*readWritePermissions) {
                DeviceConfig.addOnPropertiesChangedListener(
                        namespace,
                        inlineExecutor,
                        listener)
                DeviceConfig.setProperty(
                        namespace,
                        key,
                        value,
                        false /* makeDefault */)
                // Don't drop the permission until the config is applied, just in case
                future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.also {
                Log.i(TAG, "Config \"$key\" successfully set to \"$value\"")
            }
        } cleanup {
            DeviceConfig.removeOnPropertiesChangedListener(listener)
        }
    }

    private val inlineExecutor get() = Executor { r -> r.run() }

    /**
     * Add an action to be run after config cleanup when the current test case ends.
     */
    fun runAfterNextCleanup(action: ThrowingRunnable) {
        currentTestCleanupActions.add(action)
    }
}
