package android.nfc.cts;

import android.nfc.cardemulation.*;
import android.os.Bundle;

public class CtsMyHostNfcFService extends HostNfcFService {
    @Override
    public byte[] processNfcFPacket(byte[] commandPacket, Bundle extras) {
        return new byte[0];
    }

    @Override
    public void onDeactivated(int reason) {
        return;
    }
}
