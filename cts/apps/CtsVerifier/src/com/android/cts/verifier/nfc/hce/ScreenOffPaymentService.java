package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class ScreenOffPaymentService extends HceService {
    static final String TAG = "ScreenOffPaymentService";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            ScreenOffPaymentService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.PPSE_AID, true),
        HceUtils.buildSelectApdu(HceUtils.MC_AID, true),
        HceUtils.buildCommandApdu("80CA01F000", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "FFFF9000",
        "FFEF9000",
        "FFDFFFAABB9000"
    };

    public ScreenOffPaymentService() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}
