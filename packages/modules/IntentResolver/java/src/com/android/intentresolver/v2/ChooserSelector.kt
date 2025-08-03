package com.android.intentresolver.v2

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.intentresolver.FeatureFlags
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(BroadcastReceiver::class)
class ChooserSelector : Hilt_ChooserSelector() {

    @Inject lateinit var featureFlags: FeatureFlags

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(CHOOSER_PACKAGE, CHOOSER_PACKAGE + CHOOSER_CLASS),
                if (featureFlags.modularFramework()) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                },
                /* flags = */ 0,
            )
        }
    }

    companion object {
        private const val CHOOSER_PACKAGE = "com.android.intentresolver"
        private const val CHOOSER_CLASS = ".v2.ChooserActivity"
    }
}
