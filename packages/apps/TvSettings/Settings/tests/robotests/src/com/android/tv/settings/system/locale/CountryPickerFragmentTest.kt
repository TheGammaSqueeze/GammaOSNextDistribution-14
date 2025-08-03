package com.android.tv.settings.system.locale

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.android.internal.app.LocaleStore
import com.android.tv.settings.testutils.SettingsShadowActivityManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.androidx.fragment.FragmentController
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [SettingsShadowActivityManager::class])
class CountryPickerFragmentTest {
    companion object {
        val EXTRA_PARENT_LOCALE = "PARENT_LOCALE"
        val parentLocale = LocaleStore.fromLocale(Locale.ENGLISH)
    }

    @Test
    fun testCountrySorting() {
        val fragment = createCountryPickerFragment()
        val preferenceScreen = fragment.preferenceScreen
        assertThat(preferenceScreen.preferenceCount).isEqualTo(3)
        assertThat(preferenceScreen.getPreference(0).title)
                .isEqualTo(LocaleStore.fromLocale(Locale.CANADA).fullCountryNameNative)
        assertThat(preferenceScreen.getPreference(1).title)
                .isEqualTo(LocaleStore.fromLocale(Locale.UK).fullCountryNameNative)
        assertThat(preferenceScreen.getPreference(2).title)
                .isEqualTo(LocaleStore.fromLocale(Locale.US).fullCountryNameNative)
    }

    private fun createCountryPickerFragment(): CountryPickerFragment {
        val fragment = TestCountryPickerFragment()
        fragment.arguments = Bundle().apply {
            putSerializable(EXTRA_PARENT_LOCALE, parentLocale)
        }

        return FragmentController.of(fragment).create().start().get()
    }

    class TestCountryPickerFragment : CountryPickerFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
            val viewModel = ViewModelProvider(requireActivity())[LocaleDataViewModel::class.java]
            viewModel.mLocaleMap[parentLocale] = listOf(LocaleStore.fromLocale(Locale.UK),
                    LocaleStore.fromLocale(Locale.CANADA), LocaleStore.fromLocale(Locale.US))
            super.onCreatePreferences(savedInstanceState, s)
        }
    }
}