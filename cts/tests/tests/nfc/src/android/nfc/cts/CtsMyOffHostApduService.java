package android.nfc.cts;

import android.content.Intent;
import android.nfc.cardemulation.*;
import android.os.IBinder;

public class CtsMyOffHostApduService extends OffHostApduService {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
