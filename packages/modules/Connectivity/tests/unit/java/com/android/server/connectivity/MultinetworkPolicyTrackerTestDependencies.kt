package com.android.server.connectivity

import android.content.res.Resources
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY
import android.provider.DeviceConfig.OnPropertiesChangedListener
import com.android.internal.annotations.GuardedBy
import com.android.server.connectivity.MultinetworkPolicyTracker.CONFIG_ACTIVELY_PREFER_BAD_WIFI
import java.util.concurrent.Executor

class MultinetworkPolicyTrackerTestDependencies(private val resources: Resources) :
        MultinetworkPolicyTracker.Dependencies() {
    @GuardedBy("listeners")
    private var configActivelyPreferBadWifi = 0
    // TODO : move this to an actual fake device config object
    @GuardedBy("listeners")
    private val listeners = mutableListOf<Pair<Executor, OnPropertiesChangedListener>>()

    fun putConfigActivelyPreferBadWifi(value: Int) {
        synchronized(listeners) {
            if (value == configActivelyPreferBadWifi) return
            configActivelyPreferBadWifi = value
            val p = DeviceConfig.Properties(NAMESPACE_CONNECTIVITY,
                    mapOf(CONFIG_ACTIVELY_PREFER_BAD_WIFI to value.toString()))
            listeners.forEach { (executor, listener) ->
                executor.execute { listener.onPropertiesChanged(p) }
            }
        }
    }

    override fun getConfigActivelyPreferBadWifi(): Int {
        return synchronized(listeners) { configActivelyPreferBadWifi }
    }

    override fun addOnDevicePropertiesChangedListener(
        e: Executor,
        listener: OnPropertiesChangedListener
    ) {
        synchronized(listeners) {
            listeners.add(e to listener)
        }
    }

    override fun getResourcesForActiveSubId(res: ConnectivityResources, id: Int): Resources =
            resources
}
