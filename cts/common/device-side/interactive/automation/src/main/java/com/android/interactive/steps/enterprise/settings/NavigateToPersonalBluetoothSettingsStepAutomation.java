package com.android.interactive.steps.enterprise.settings;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.NavigateToPersonalBluetoothSettingsStep")
public final class NavigateToPersonalBluetoothSettingsStepAutomation implements Automation<Nothing> {
    @Override
    public Nothing automate() {
        Intent intent = new Intent("android.settings.BLUETOOTH_SETTINGS");
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);

        TestApis.context().instrumentedContext().startActivity(intent);

        return Nothing.NOTHING;
    }
}