package org.lineageos.setupwizard;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

public class SetupWizardExitActivity extends BaseSetupWizardActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mark setup as completed
        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);

        // Apply transition and finish this activity
        try {
            applyForwardTransition();
        } catch (Throwable ignored) { }
        // Attempt reboot (best-effort; ignore failures)
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) pm.reboot(null);
        } catch (Throwable ignored) { }

        finish();
    }
}
