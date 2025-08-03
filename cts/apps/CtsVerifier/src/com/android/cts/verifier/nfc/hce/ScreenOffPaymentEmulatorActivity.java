package com.android.cts.verifier.nfc.hce;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

public class ScreenOffPaymentEmulatorActivity extends BaseEmulatorActivity {
    final static int STATE_SCREEN_ON = 0;
    final static int STATE_SCREEN_OFF = 1;
    private static final int SECURE_NFC_ENABLED_DIALOG_ID = 1;
    private int mState = STATE_SCREEN_ON;

    private ScreenOnOffReceiver mReceiver;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mState = STATE_SCREEN_ON;
        setupServices(this, ScreenOffPaymentService.COMPONENT);

        mReceiver = new ScreenOnOffReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);

        NfcManager nfcManager = getSystemService(NfcManager.class);
        mNfcAdapter = nfcManager.getDefaultAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter.isSecureNfcSupported() && mNfcAdapter.isSecureNfcEnabled()) {
            showDialog(SECURE_NFC_ENABLED_DIALOG_ID);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    void onServicesSetup(boolean result) {
        // Verify ScreenOff HCE service is the default
        if (makePaymentDefault(ScreenOffPaymentService.COMPONENT,
                R.string.nfc_hce_change_preinstalled_wallet)) {
            // Wait for callback
        } else {
                NfcDialogs.createHceTapReaderDialog(this,
                        getString(R.string.nfc_screen_off_hce_payment_help)).show();
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
            NfcDialogs.createHceTapReaderDialog(this,
                    getString(R.string.nfc_screen_off_hce_payment_help)).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                ScreenOffPaymentService.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                ScreenOffPaymentService.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_screen_off_hce_payment_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(ScreenOffPaymentService.COMPONENT) && mState == STATE_SCREEN_OFF) {
            getPassButton().setEnabled(true);
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case SECURE_NFC_ENABLED_DIALOG_ID:
                return NfcDialogs.createSecureNfcEnabledDialog(this);
            default:
                return super.onCreateDialog(id, args);
        }
    }

    private class ScreenOnOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mState = STATE_SCREEN_OFF;
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mState = STATE_SCREEN_ON;
            }
        }
    }
}
