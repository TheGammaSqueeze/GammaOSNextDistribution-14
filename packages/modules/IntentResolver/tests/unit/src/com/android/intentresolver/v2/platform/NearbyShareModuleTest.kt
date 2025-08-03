package com.android.intentresolver.v2.platform

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.testing.TestableResources

import androidx.test.platform.app.InstrumentationRegistry

import com.android.intentresolver.R

import com.google.common.truth.Truth8.assertThat

import org.junit.Before
import org.junit.Test

class NearbyShareModuleTest {

    lateinit var context: Context

    /** Create Resources with overridden values. */
    private fun Context.fakeResources(
        config: Configuration? = null,
        block: TestableResources.() -> Unit
    ) =
        TestableResources(resources)
            .apply { config?.let { overrideConfiguration(it) } }
            .apply(block)
            .resources

    @Before
    fun setup() {
        val instr = InstrumentationRegistry.getInstrumentation()
        context = instr.context
    }

    @Test
    fun valueIsAbsent_whenUnset() {
        val secureSettings = fakeSecureSettings {}
        val resources =
            context.fakeResources { addOverride(R.string.config_defaultNearbySharingComponent, "") }

        val componentName = NearbyShareModule.nearbyShareComponent(resources, secureSettings)
        assertThat(componentName).isEmpty()
    }

    @Test
    fun defaultValue_readFromResources() {
        val secureSettings = fakeSecureSettings {}
        val resources =
            context.fakeResources {
                addOverride(
                    R.string.config_defaultNearbySharingComponent,
                    "com.example/.ComponentName"
                )
            }

        val nearbyShareComponent = NearbyShareModule.nearbyShareComponent(resources, secureSettings)

        assertThat(nearbyShareComponent).hasValue(
            ComponentName.unflattenFromString("com.example/.ComponentName"))
    }

    @Test
    fun secureSettings_overridesDefault() {
        val secureSettings = fakeSecureSettings {
            putString(Settings.Secure.NEARBY_SHARING_COMPONENT, "com.example/.BComponent")
        }
        val resources =
            context.fakeResources {
                addOverride(
                    R.string.config_defaultNearbySharingComponent,
                    "com.example/.AComponent"
                )
            }

        val nearbyShareComponent = NearbyShareModule.nearbyShareComponent(resources, secureSettings)

        assertThat(nearbyShareComponent).hasValue(
            ComponentName.unflattenFromString("com.example/.BComponent"))
    }
}
