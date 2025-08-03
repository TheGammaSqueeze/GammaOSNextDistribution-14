package com.android.healthconnect.controller.utils

import android.content.Context
import android.provider.DeviceConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

interface FeatureUtils {
    fun isSessionTypesEnabled(): Boolean

    fun isExerciseRouteReadAllEnabled(): Boolean

    fun isEntryPointsEnabled(): Boolean

    fun isNewAppPriorityEnabled(): Boolean

    fun isNewInformationArchitectureEnabled(): Boolean

    fun isBackgroundReadEnabled(): Boolean

    fun isImportExportEnabled(): Boolean
}

class FeatureUtilsImpl(context: Context) : FeatureUtils, DeviceConfig.OnPropertiesChangedListener {

    companion object {
        private const val HEALTH_FITNESS_FLAGS_NAMESPACE = DeviceConfig.NAMESPACE_HEALTH_FITNESS
        private const val PROPERTY_EXERCISE_ROUTE_ENABLED = "exercise_routes_enable"
        private const val PROPERTY_EXERCISE_ROUTE_READ_ALL_ENABLED =
            "exercise_routes_read_all_enable"
        private const val PROPERTY_SESSIONS_TYPE_ENABLED = "session_types_enable"
        private const val PROPERTY_ENTRY_POINTS_ENABLED = "entry_points_enable"
        private const val PROPERTY_AGGREGATION_SOURCE_CONTROL_ENABLED =
            "aggregation_source_controls_enable"
        private const val PROPERTY_NEW_INFORMATION_ARCHITECTURE_ENABLED =
            "new_information_architecture_enable"
        private const val PROPERTY_IMPORT_EXPORT_ENABLED = "import_export_enable"
    }

    private val lock = Any()

    init {
        DeviceConfig.addOnPropertiesChangedListener(
            HEALTH_FITNESS_FLAGS_NAMESPACE, context.mainExecutor, this)
    }

    private var isSessionTypesEnabled =
        DeviceConfig.getBoolean(
            HEALTH_FITNESS_FLAGS_NAMESPACE, PROPERTY_SESSIONS_TYPE_ENABLED, true)

    private var isExerciseRouteReadAllEnabled = true

    private var isEntryPointsEnabled =
        DeviceConfig.getBoolean(HEALTH_FITNESS_FLAGS_NAMESPACE, PROPERTY_ENTRY_POINTS_ENABLED, true)

    private var isNewAppPriorityEnabled = true

    private var isNewInformationArchitectureEnabled =
        DeviceConfig.getBoolean(
            HEALTH_FITNESS_FLAGS_NAMESPACE, PROPERTY_NEW_INFORMATION_ARCHITECTURE_ENABLED, false)

    private var isImportExportEnabled =
        DeviceConfig.getBoolean(
            HEALTH_FITNESS_FLAGS_NAMESPACE, PROPERTY_IMPORT_EXPORT_ENABLED, false)

    override fun isNewAppPriorityEnabled(): Boolean {
        synchronized(lock) {
            return isNewAppPriorityEnabled
        }
    }

    override fun isNewInformationArchitectureEnabled(): Boolean {
        synchronized(lock) {
            return isNewInformationArchitectureEnabled
        }
    }

    override fun isSessionTypesEnabled(): Boolean {
        synchronized(lock) {
            return isSessionTypesEnabled
        }
    }

    override fun isExerciseRouteReadAllEnabled(): Boolean {
        synchronized(lock) {
            return isExerciseRouteReadAllEnabled
        }
    }

    override fun isEntryPointsEnabled(): Boolean {
        synchronized(lock) {
            return isEntryPointsEnabled
        }
    }

    override fun isBackgroundReadEnabled(): Boolean {
        synchronized(lock) {
            return false
        }
    }

    override fun isImportExportEnabled(): Boolean {
        synchronized(lock) {
            return isImportExportEnabled
        }
    }

    override fun onPropertiesChanged(properties: DeviceConfig.Properties) {
        synchronized(lock) {
            if (!properties.namespace.equals(HEALTH_FITNESS_FLAGS_NAMESPACE)) {
                return
            }

            for (name in properties.keyset) {
                when (name) {
                    PROPERTY_EXERCISE_ROUTE_READ_ALL_ENABLED -> {
                        isExerciseRouteReadAllEnabled =
                            properties.getBoolean(PROPERTY_EXERCISE_ROUTE_READ_ALL_ENABLED, true)
                    }
                    PROPERTY_SESSIONS_TYPE_ENABLED ->
                        isSessionTypesEnabled =
                            properties.getBoolean(PROPERTY_SESSIONS_TYPE_ENABLED, true)
                    PROPERTY_ENTRY_POINTS_ENABLED ->
                        isEntryPointsEnabled =
                            properties.getBoolean(PROPERTY_ENTRY_POINTS_ENABLED, true)
                    PROPERTY_AGGREGATION_SOURCE_CONTROL_ENABLED -> isNewAppPriorityEnabled = true
                    PROPERTY_NEW_INFORMATION_ARCHITECTURE_ENABLED ->
                        isNewInformationArchitectureEnabled =
                            properties.getBoolean(
                                PROPERTY_NEW_INFORMATION_ARCHITECTURE_ENABLED, false)
                    PROPERTY_IMPORT_EXPORT_ENABLED ->
                        isImportExportEnabled =
                            properties.getBoolean(PROPERTY_IMPORT_EXPORT_ENABLED, false)
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class FeaturesModule {
    @Provides
    @Singleton
    fun providesFeatureUtils(@ApplicationContext context: Context): FeatureUtils {
        return FeatureUtilsImpl(context)
    }
}
