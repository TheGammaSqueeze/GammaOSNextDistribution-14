package android.provider.cts.contacts.testapp;

import static org.junit.Assert.assertArrayEquals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.provider.CallLog;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiDevice;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Copied piecemail from EncryptionAppTest and adapted to test the call log.
public class CallLogDirectBootTest extends InstrumentationTestCase {

    private static final String LOG_TAG = CallLogDirectBootTest.class.getSimpleName();
    private static final long CONTENT_RESOLVER_TIMEOUT_MS = 5000;

    private Context mCe;
    private Context mDe;
    private UiDevice mDevice;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCe = getInstrumentation().getContext();
        mDe = mCe.createDeviceProtectedStorageContext();

        mDevice = UiDevice.getInstance(getInstrumentation());
        assertNotNull(mDevice);
    }

    public void testShadowCallComposerPicture() throws Exception {
        BootReceiver.waitForBootComplete(mDe, BootReceiver.LOCKED_BOOT_COMPLETE,60000);
        Log.i(LOG_TAG, "Locked boot complete received, starting test");

        byte[] expected = readResourceDrawable(mDe, R.drawable.cupcake);
        Log.i(LOG_TAG, "read drawable from resources");


        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        try {
            // While still locked, write a picture to the call log, assuming it ends up in the shadow.
            CompletableFuture<Pair<Uri, CallLog.CallComposerLoggingException>> resultFuture =
                    new CompletableFuture<>();
            Pair<Uri, CallLog.CallComposerLoggingException> result;
            try (InputStream inputStream =
                         mDe.getResources().openRawResource(R.drawable.cupcake)) {
                CallLog.storeCallComposerPicture(
                        mDe.createContextAsUser(android.os.Process.myUserHandle(), 0),
                        inputStream,
                        Executors.newSingleThreadExecutor(),
                        new OutcomeReceiver<Uri, CallLog.CallComposerLoggingException>() {
                            @Override
                            public void onResult(@NonNull Uri result) {
                                resultFuture.complete(Pair.create(result, null));
                            }

                            @Override
                            public void onError(CallLog.CallComposerLoggingException error) {
                                resultFuture.complete(Pair.create(null, error));
                            }
                        });
                result = resultFuture.get(CONTENT_RESOLVER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            if (result.second != null) {
                fail("Got error " + result.second.getErrorCode() + " when storing image");
            }
            Log.i(LOG_TAG, "successfully received uri for inserted image");
            Uri imageLocation = result.first.buildUpon().authority(CallLog.AUTHORITY).build();

            final CountDownLatch latch = new CountDownLatch(1);
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    latch.countDown();
                }
            };
            getInstrumentation().getContext().createDeviceProtectedStorageContext()
                    .registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

            dismissKeyguard();

            // Dismiss keyguard should have kicked off immediate broadcast
            assertTrue("USER_UNLOCKED", latch.await(1, TimeUnit.MINUTES));

            BootReceiver.waitForBootComplete(mCe, BootReceiver.BOOT_COMPLETE, 60000);

            try (ParcelFileDescriptor pfd =
                         mCe.getContentResolver().openFileDescriptor(imageLocation, "r")) {
                byte[] remoteBytes = readBytes(new FileInputStream(pfd.getFileDescriptor()));
                assertArrayEquals(expected, remoteBytes);
            }
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            dismissKeyguard();
        }
    }

    private static byte[] readResourceDrawable(Context context, int id) throws Exception {
        InputStream inputStream = context.getResources().openRawResource(id);
        return readBytes(inputStream);
    }

    private static byte[] readBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int numRead;
        do {
            numRead = inputStream.read(buffer);
            if (numRead > 0) output.write(buffer, 0, numRead);
        } while (numRead > 0);
        return output.toByteArray();
    }

    private void enterTestPin() throws Exception {
        mDevice.waitForIdle();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_5);
        mDevice.waitForIdle();
        mDevice.pressEnter();
        mDevice.waitForIdle();
    }

    private void dismissKeyguard() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        enterTestPin();
        mDevice.waitForIdle();
        mDevice.pressHome();
        mDevice.waitForIdle();
    }
}
