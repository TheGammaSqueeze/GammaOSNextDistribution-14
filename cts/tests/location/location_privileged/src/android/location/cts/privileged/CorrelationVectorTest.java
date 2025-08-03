package android.location.cts.privileged;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.location.CorrelationVector;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of CorrelationVector class. This includes writing and reading
 * from parcel, and verifying computed values and getters.
 */
@RunWith(AndroidJUnit4.class)
public class CorrelationVectorTest {

    private static final double PRECISION = 0.0001;
    private static final int[] MAGNITUDE_ARRAY = new int[] {0, 5000, 10000, 5000, 0, 0, 3000, 0};
    private static final int[] MAGNITUDE_ARRAY2 = new int[] {0, 3000, 10000, 5000, 0, 0, 3000, 0};

    @Test
    public void testCorrelationVectorDescribeContents() {
        CorrelationVector correlationVector = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        assertEquals(0, correlationVector.describeContents());
    }

    @Test
    public void testCorrelationVectorWriteToParcel() {
        CorrelationVector correlationVector = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        Parcel parcel = Parcel.obtain();
        correlationVector.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CorrelationVector newCorrelationVector = CorrelationVector.CREATOR.createFromParcel(parcel);
        verifyCorrelationVectorValuesAndGetters(newCorrelationVector);
        parcel.recycle();
    }

    @Test
    public void testCreateCorrelationVectorAndGetValues() {
        CorrelationVector correlationVector = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        verifyCorrelationVectorValuesAndGetters(correlationVector);
    }

    @Test
    public void testEquals() {
        CorrelationVector correlationVector1 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        CorrelationVector correlationVector2 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        CorrelationVector correlationVector3 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY2);
        assertEquals(correlationVector1, correlationVector2);
        assertNotEquals(correlationVector1, correlationVector3);
    }

    @Test
    public void testHashCode() {
        CorrelationVector correlationVector1 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        CorrelationVector correlationVector2 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY);
        CorrelationVector correlationVector3 = createTestCorrelationVector(30d, 10d, 10, MAGNITUDE_ARRAY2);
        assertEquals(correlationVector1.hashCode(), correlationVector2.hashCode());
        assertNotEquals(correlationVector1.hashCode(), correlationVector3.hashCode());
    }

    private static void verifyCorrelationVectorValuesAndGetters(
            CorrelationVector correlationVector) {
        assertEquals(30d, correlationVector.getSamplingWidthMeters(), PRECISION);
        assertEquals(10d, correlationVector.getSamplingStartMeters(), PRECISION);
        assertEquals(10d, correlationVector.getFrequencyOffsetMetersPerSecond(), PRECISION);
        assertArrayEquals(MAGNITUDE_ARRAY, correlationVector.getMagnitude());
    }

    private static CorrelationVector createTestCorrelationVector(
            double samplingWidthMeters, double samplingStartMeters,
                    double frequencyOffsetMetersPerSecond, int[] magnitude) {
        return new CorrelationVector.Builder()
                .setSamplingWidthMeters(samplingWidthMeters)
                .setSamplingStartMeters(samplingStartMeters)
                .setFrequencyOffsetMetersPerSecond(frequencyOffsetMetersPerSecond)
                .setMagnitude(magnitude)
                .build();
    }
}
