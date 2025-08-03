package android.nfc.cts;


import android.nfc.cardemulation.*;
import android.os.Bundle;

import java.util.List;

public class CtsMyHostApduService extends HostApduService {
    static final String POLLING_LOOP_RECEIVED_ACTION = "CTS_NFC_POLLING_LOOP";
    static final String SERVICE_NAME_EXTRA = "CTS_NFC_SERVICE_NAME_EXTRA";
    static final String POLLING_FRAMES_EXTRA = "CTS_NFC_POLLING_FRAMES_EXTRA";


    public void ctsNotifyUnhandled() {
      return;
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        return new byte[0];
    }

    @Override
    public void onDeactivated(int reason) {
        return;
    }

    @Override
    public void processPollingFrames(List<PollingFrame> frames) {
        if (CardEmulationTest.sCurrentPollLoopReceiver != null) {
            CardEmulationTest.sCurrentPollLoopReceiver.notifyPollingLoop(this.getClass().getName(),
                    frames);
        }
    }
}
