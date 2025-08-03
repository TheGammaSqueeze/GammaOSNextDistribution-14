package com.android.bedstead.nene.bugreports

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class BugReportsTest {

    @Test
    fun setTakeQuickBugreports_true_valueIsSet() {
        TestApis.bugReports().setTakeQuickBugReports(true).use {

            assertThat(TestApis.bugReports().willTakeQuickBugReports()).isTrue()
        }
    }

    @Test
    fun setTakeQuickBugreports_false_valueIsSet() {
        TestApis.bugReports().setTakeQuickBugReports(false).use {

            assertThat(TestApis.bugReports().willTakeQuickBugReports()).isFalse()
        }
    }

    @Test
    fun setTakeQuickBugreports_autoclose_resetsValue() {
        TestApis.bugReports().setTakeQuickBugReports(false)

        TestApis.bugReports().setTakeQuickBugReports(true).use {
            // Allow to autoclose
        }

        assertThat(TestApis.bugReports().willTakeQuickBugReports()).isFalse()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }

}