package android.telecom.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telecom.cts.api29incallservice.CtsApi29InCallServiceControl;
import android.telecom.cts.api29incallservice.ICtsApi29InCallServiceControl;
import android.util.Log;
import android.util.Pair;

import junit.framework.TestCase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Api29InCallUtils {
    private static final String LOG_TAG = Api29InCallUtils.class.getSimpleName();

    public static Pair<ServiceConnection, ICtsApi29InCallServiceControl> setupControl(
            Context context) throws Exception {

        Intent bindIntent = new Intent(CtsApi29InCallServiceControl.CONTROL_INTERFACE_ACTION);
        ComponentName controlComponentName =
                ComponentName.createRelative(
                        CtsApi29InCallServiceControl.class.getPackage().getName(),
                        CtsApi29InCallServiceControl.class.getName());

        bindIntent.setComponent(controlComponentName);
        CompletableFuture<ICtsApi29InCallServiceControl> resultFuture = new CompletableFuture<>();

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(LOG_TAG, "Service Connected: " + name);
                resultFuture.complete(ICtsApi29InCallServiceControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        boolean success = context.bindService(bindIntent,
                serviceConnection, Context.BIND_AUTO_CREATE);

        if (!success) {
            TestCase.fail("Failed to get control interface -- bind error");
        }
        return Pair.create(serviceConnection,
                resultFuture.get(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS));
    }

    public static void tearDownControl(Context context, ServiceConnection serviceConnection) {
        context.unbindService(serviceConnection);
    }

    private Api29InCallUtils() {}
}
