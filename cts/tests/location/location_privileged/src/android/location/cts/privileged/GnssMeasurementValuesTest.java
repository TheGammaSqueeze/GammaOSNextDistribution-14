package android.location.cts.privileged;

import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.content.Context;
import android.location.GnssCapabilities;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.cts.common.SoftAssert;
import android.location.cts.common.TestGnssMeasurementListener;
import android.location.cts.common.TestLocationListener;
import android.location.cts.common.TestLocationManager;
import android.location.cts.common.TestMeasurementUtil;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link GnssMeasurement} values.
 *
 * 1. Register for location updates.
 * 2. Register a listener for {@link GnssMeasurementsEvent}s.
 * 3. Wait for {@link #LOCATION_TO_COLLECT_COUNT} locations.
 *        3.1 Confirm locations have been found.
 * 4. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent.Callback#STATUS_READY}, the test will be skipped if the device
 *    does not support the GPS feature.
 * 5. Verify {@link GnssMeasurement}s, the test will fail if any of the fields is not populated
 *    or in the expected range.
 */
@RunWith(AndroidJUnit4.class)
public class GnssMeasurementValuesTest {

    private static final String TAG = "GnssMeasValuesTest";
    private static final int LOCATION_TO_COLLECT_COUNT = 20;

    private Context mContext;
    private TestGnssMeasurementListener mMeasurementListener;
    private TestLocationListener mLocationListener;
    private TestLocationManager mTestLocationManager;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOCATION_HARDWARE);
        mTestLocationManager = new TestLocationManager(mContext);
    }

    @After
    public void tearDown() throws Exception {
        // Unregister listeners
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mMeasurementListener != null) {
            mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Tests that one can listen for {@link GnssMeasurementsEvent} for collection purposes.
     * It only performs valid checks for the measurements received.
     * This tests uses actual data retrieved from GPS HAL.
     */
    @Test
    public void testListenForGnssMeasurements() throws Exception {
        boolean isCorrVecSupported = false;
        boolean isSatPvtSupported = false;

        // Checks if GPS hardware feature is present, skips test (pass) if not
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG)) {
            return;
        }

        if (TestMeasurementUtil.isAutomotiveDevice(mContext)) {
            Log.i(TAG, "Test is being skipped because the system has the AUTOMOTIVE feature.");
            return;
        }

        GnssCapabilities capabilities = mTestLocationManager.getLocationManager().
                getGnssCapabilities();
        isSatPvtSupported = capabilities.hasSatellitePvt();
        isCorrVecSupported = capabilities.hasMeasurementCorrelationVectors();

        mLocationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        mMeasurementListener = new TestGnssMeasurementListener(TAG, /* eventsToCollect= */
                0, /* filterByEventSize= */ false);
        mTestLocationManager.registerGnssMeasurementCallback(
                mMeasurementListener,
                new GnssMeasurementRequest.Builder()
                        .setCorrelationVectorOutputsEnabled(isCorrVecSupported)
                        .build());

        SoftAssert softAssert = new SoftAssert(TAG);
        boolean success = mLocationListener.await();
        softAssert.assertTrue(
                "Time elapsed without getting enough location fixes."
                        + " Possibly, the test has been run deep indoors."
                        + " Consider retrying test outdoors.",
                success);

        Log.i(TAG, "Location received = " + mLocationListener.isLocationReceived());

        if (isSatPvtSupported) {
            boolean foundSatellitePvt = mMeasurementListener.awaitSatellitePvt();
            softAssert.assertTrue(
                    "SatellitPvt is supported. The test must find a measurement with SatellitePvt"
                            + " within " + TestGnssMeasurementListener.MEAS_TIMEOUT_IN_SEC
                            + " seconds.",
                    foundSatellitePvt);
        }
        if (isCorrVecSupported) {
            boolean foundCorrelationVector = mMeasurementListener.awaitCorrelationVector();
            softAssert.assertTrue(
                    "CorrelationVector is supported. The test must find a measurement with "
                            + "CorrelationVector within "
                            + TestGnssMeasurementListener.CORRELATION_VECTOR_TIMEOUT_IN_SEC
                            + " seconds.",
                    foundCorrelationVector);
        }

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        int eventCount = events.size();
        Log.i(TAG, "Number of GnssMeasurement Event received = " + eventCount);

        softAssert.assertTrue(
                "GnssMeasurementEvent count", "X > 0",
                String.valueOf(eventCount), eventCount > 0);

        for (GnssMeasurementsEvent event : events) {
            // Verify Gps Event optional fields are in required ranges
            assertNotNull("GnssMeasurementEvent cannot be null.", event);
            long timeInNs = event.getClock().getTimeNanos();
            for (GnssMeasurement measurement : event.getMeasurements()) {
                TestMeasurementUtil.assertAllGnssMeasurementSystemFields(measurement, softAssert, timeInNs);
            }
        }
        softAssert.assertAll();
    }
}
