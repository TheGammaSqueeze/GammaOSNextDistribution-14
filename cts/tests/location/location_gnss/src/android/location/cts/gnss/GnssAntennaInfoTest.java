package android.location.cts.gnss;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.location.GnssAntennaInfo;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.cts.common.TestGnssStatusCallback;
import android.location.cts.common.TestLocationManager;
import android.location.cts.common.TestMeasurementUtil;
import android.location.cts.common.TestUtils;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/** Tests {@link GnssAntennaInfo} values. */
@RunWith(JUnit4.class)
public class GnssAntennaInfoTest {

    private static final String TAG = "GnssAntInfoValuesTest";
    private static final int STATUS_TO_COLLECT_COUNT = 20;
    private static final int HZ_PER_MHZ = (int) 1e6;
    private static final double CARRIER_FREQ_TOLERANCE_HZ = 10 * 1e6;

    private TestLocationManager mTestLocationManager;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        mTestLocationManager = new TestLocationManager(context);
    }

    /**
     * Tests that the carrier frequency reported from {@link GnssAntennaInfo} can be found in
     * GnssStatus.
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot access package manager to scan for permissions")
    public void testGnssAntennaInfoValues() throws Exception {
        // Checks if GPS hardware feature is present, skips test (pass) if not
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));

        // Checks if ANTENNA_INFO capability is supported, skips the test if no
        assumeTrue(
                mTestLocationManager.getLocationManager().getGnssCapabilities().hasAntennaInfo());

        // Revoke location permissions from packages before running GnssStatusTest stops
        // active location requests, allowing this test to receive all necessary Gnss callbacks.
        List<String> courseLocationPackages = TestUtils.revokePermissions(ACCESS_COARSE_LOCATION);
        List<String> fineLocationPackages = TestUtils.revokePermissions(ACCESS_FINE_LOCATION);

        try {
            // Registers GnssStatus Listener
            TestGnssStatusCallback testGnssStatusCallback =
                    new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
            checkGnssChange(testGnssStatusCallback);

            float[] carrierFrequencies = testGnssStatusCallback.getCarrierFrequencies();
            List<GnssAntennaInfo> antennaInfos =
                    mTestLocationManager.getLocationManager().getGnssAntennaInfos();

            assertThat(antennaInfos).isNotNull();
            for (GnssAntennaInfo antennaInfo : antennaInfos) {
                double antennaInfoFreqHz = antennaInfo.getCarrierFrequencyMHz() * HZ_PER_MHZ;
                assertWithMessage(
                        "Carrier frequency in GnssAntennaInfo must be found in GnssStatus.").that(
                        carrierFrequencies).usingTolerance(CARRIER_FREQ_TOLERANCE_HZ).contains(
                        antennaInfoFreqHz);
            }
        } finally {
            // For each location package, re-grant the permission
            TestUtils.grantLocationPermissions(ACCESS_COARSE_LOCATION, courseLocationPackages);
            TestUtils.grantLocationPermissions(ACCESS_FINE_LOCATION, fineLocationPackages);
        }
    }

    private void checkGnssChange(TestGnssStatusCallback testGnssStatusCallback)
            throws InterruptedException {
        mTestLocationManager.registerGnssStatusCallback(testGnssStatusCallback);

        LocationListener listener = location -> {};
        mTestLocationManager.getLocationManager().requestLocationUpdates(
                LocationManager.GPS_PROVIDER, /* minTimeMs= */0, /* minDistanceM= */ 0, listener,
                Looper.getMainLooper());

        boolean isAutomotiveDevice = TestMeasurementUtil.isAutomotiveDevice(context);
        boolean success = true;
        if(!isAutomotiveDevice){
            success = testGnssStatusCallback.awaitStart();
        }
        success = success ? testGnssStatusCallback.awaitStatus() : false;
        if(!isAutomotiveDevice){
            success = success ? testGnssStatusCallback.awaitTtff() : false;
        }
        mTestLocationManager.getLocationManager().removeUpdates(listener);
        if(!isAutomotiveDevice){
            success = success ? testGnssStatusCallback.awaitStop() : false;
        }
        mTestLocationManager.unregisterGnssStatusCallback(testGnssStatusCallback);

        assertWithMessage(
                "Time elapsed without getting the right status changes."
                        + " Possibly, the test has been run deep indoors."
                        + " Consider retrying test outdoors.").that(success).isTrue();
    }
}
