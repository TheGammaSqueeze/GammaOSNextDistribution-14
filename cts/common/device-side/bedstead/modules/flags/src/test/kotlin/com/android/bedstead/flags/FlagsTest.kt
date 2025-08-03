package com.android.bedstead.flags

import android.app.admin.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsDisabled
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class FlagsTest {

    @Test
    @RequireFlagsEnabled(Flags.FLAG_DUMPSYS_POLICY_ENGINE_MIGRATION_ENABLED)
    fun requireFlagEnabledAnnotation_flagIsEnabled() {
        assertThat(Flags.dumpsysPolicyEngineMigrationEnabled()).isTrue()
    }

    @Test
    @RequireFlagsDisabled(Flags.FLAG_DUMPSYS_POLICY_ENGINE_MIGRATION_ENABLED)
    fun requireFlagDisabledAnnotation_flagIsDisabled() {
        assertThat(Flags.dumpsysPolicyEngineMigrationEnabled()).isFalse()
    }

    companion object {
        @ClassRule @Rule @JvmField
        val deviceState = DeviceState()
    }

}