package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.location.GnssMeasurementRequest;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of {@link GnssMeasurementRequest} class. This includes writing
 * and reading from parcel, and verifying computed values and getters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssMeasurementRequestTest {
    private static final int TEST_INTERVAL_MS = 2000;

    @Test
    public void testGetValues() {
        GnssMeasurementRequest request1 =
                new GnssMeasurementRequest.Builder().setFullTracking(true).build();
        assertTrue(request1.isFullTracking());
        GnssMeasurementRequest request2 =
                new GnssMeasurementRequest.Builder().setFullTracking(false).build();
        assertFalse(request2.isFullTracking());

        GnssMeasurementRequest request3 =
                new GnssMeasurementRequest.Builder().setIntervalMillis(TEST_INTERVAL_MS).build();
        assertThat(request3.getIntervalMillis()).isEqualTo(TEST_INTERVAL_MS);
    }

    @Test
    public void testDescribeContents() {
        GnssMeasurementRequest request =
                new GnssMeasurementRequest.Builder().setFullTracking(true).build();
        assertEquals(request.describeContents(), 0);
    }

    @Test
    public void testWriteToParcel() {
        GnssMeasurementRequest request =
                new GnssMeasurementRequest.Builder().setFullTracking(true).build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssMeasurementRequest fromParcel = GnssMeasurementRequest.CREATOR.createFromParcel(parcel);

        assertEquals(request, fromParcel);
    }

    @Test
    public void testEquals() {
        GnssMeasurementRequest request1 =
                new GnssMeasurementRequest.Builder().setFullTracking(true).build();
        GnssMeasurementRequest request2 = new GnssMeasurementRequest.Builder(request1).build();
        GnssMeasurementRequest request3 =
                new GnssMeasurementRequest.Builder().setFullTracking(false).build();
        GnssMeasurementRequest request4 =
                new GnssMeasurementRequest.Builder().setIntervalMillis(TEST_INTERVAL_MS).build();
        assertEquals(request1, request2);
        assertNotEquals(request3, request2);
        assertNotEquals(request4, request3);
    }
}
