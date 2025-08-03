package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class ScreenOnOnlyOffHostService {
    public static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
                    ScreenOnOnlyOffHostService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu("A000000476416E64726F696443545340", true),
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "*",
    };
}
