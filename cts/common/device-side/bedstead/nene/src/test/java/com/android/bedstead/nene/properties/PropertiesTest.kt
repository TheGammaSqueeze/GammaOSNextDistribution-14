package com.android.bedstead.nene.properties

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PropertiesTest {

    @Test
    fun set_valueIsSet() {
        TestApis.properties().set(KEY, VALUE).use {

            assertThat(TestApis.properties().get(KEY)).isEqualTo(VALUE)
        }
    }

    @Test
    fun set_autoclose_resetsValue() {
        TestApis.properties().set(KEY, VALUE).use {
            TestApis.properties().set(KEY, DIFFERENT_VALUE).use {
                // Allow to autoclose
            }

            assertThat(TestApis.properties().get(KEY)).isEqualTo(VALUE)
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        val KEY = "test123"

        val VALUE = "value123"

        val DIFFERENT_VALUE = "value234"
    }

}