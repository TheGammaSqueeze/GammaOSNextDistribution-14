package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

@TargetApi(19)
public class DualPaymentEmulatorActivity extends BaseEmulatorActivity {
    final static int STATE_IDLE = 0;
    final static int STATE_SERVICE1_SETTING_UP = 1;
    final static int STATE_SERVICE2_SETTING_UP = 2;
    final static int STATE_MAKING_SERVICE2_DEFAULT = 3;

    int mState = STATE_IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mState = STATE_SERVICE2_SETTING_UP;
        setupServices(this, PaymentService2.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        if (mState == STATE_SERVICE2_SETTING_UP) {
            mState = STATE_SERVICE1_SETTING_UP;
            setupServices(this, PaymentService1.COMPONENT, PaymentService2.COMPONENT);
            return;
        }
        // Verify HCE service 2 is the default
        int stringId = isWalletRoleAvailable() ? R.string.nfc_hce_change_default_wallet
                : R.string.nfc_hce_change_preinstalled_wallet;
        if (makePaymentDefault(PaymentService2.COMPONENT, stringId)) {
            mState = STATE_MAKING_SERVICE2_DEFAULT;
        } else {
            // Already default
            NfcDialogs.createHceTapReaderDialog(this,null).show();
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
            NfcDialogs.createHceTapReaderDialog(this, null).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
    * Accepting isWalletRoleAvailable as a parameter to decide sequences accordingly
    **/
    public static Intent buildReaderIntent(Context context, boolean isWalletRoleAvailable) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        CommandApdu[] commandSequence = isWalletRoleAvailable
                ? PaymentService1.APDU_COMMAND_SEQUENCE : PaymentService2.APDU_COMMAND_SEQUENCE;
        String[] responseSequence = isWalletRoleAvailable
                ? PaymentService1.APDU_RESPOND_SEQUENCE : PaymentService2.APDU_RESPOND_SEQUENCE;
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                commandSequence);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                responseSequence);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_dual_payment_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(isWalletRoleAvailable() ? PaymentService1.COMPONENT
                : PaymentService2.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}
