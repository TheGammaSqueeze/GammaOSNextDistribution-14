package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

public class ScreenOnOnlyOffHostEmulatorActivity extends BaseEmulatorActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        setupServices(this, ScreenOnOnlyOffHostService.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_screen_on_only_offhost_help)).show();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                ScreenOnOnlyOffHostService.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                ScreenOnOnlyOffHostService.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_screen_on_only_offhost_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
    }
}
