package android.location.cts.privileged;

import static org.junit.Assert.assertEquals;

import android.location.GnssCapabilities;
import android.location.GnssSignalType;
import android.location.GnssStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Tests fundamental functionality of {@link GnssCapabilities}. This includes writing and reading
 * from parcel, and verifying setters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssCapabilitiesTest {

    private static final GnssSignalType SIGNAL_TYPE_1 =
            GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");

    private static final GnssSignalType SIGNAL_TYPE_2 =
            GnssSignalType.create(GnssStatus.CONSTELLATION_GALILEO, 1575.42e6, "A");
    @Test
    public void testBuilderWithGnssCapabilities() {
        GnssCapabilities gnssCapabilities =
            new GnssCapabilities.Builder(getTestGnssCapabilities()).build();
        verifyTestValues(gnssCapabilities);
    }

    @Test
    public void testGetValues() {
        GnssCapabilities gnssCapabilities = getTestGnssCapabilities();
        verifyTestValues(gnssCapabilities);
    }

    @Test
    public void testWriteToParcel() {
        GnssCapabilities gnssCapabilities = getTestGnssCapabilities();
        Parcel parcel = Parcel.obtain();
        gnssCapabilities.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssCapabilities newGnssCapabilities = GnssCapabilities.CREATOR.createFromParcel(parcel);
        verifyTestValues(newGnssCapabilities);
        assertEquals(newGnssCapabilities, gnssCapabilities);
        parcel.recycle();
    }

    private static GnssCapabilities getTestGnssCapabilities() {
        GnssCapabilities.Builder builder = new GnssCapabilities.Builder();
        builder.setHasAccumulatedDeltaRange(GnssCapabilities.CAPABILITY_SUPPORTED);
        builder.setHasAntennaInfo(true);
        builder.setHasGeofencing(true);
        builder.setHasLowPowerMode(true);
        builder.setHasMeasurements(true);
        builder.setHasMeasurementCorrections(true);
        builder.setHasMeasurementCorrectionsExcessPathLength(true);
        builder.setHasMeasurementCorrectionsForDriving(true);
        builder.setHasMeasurementCorrectionsLosSats(true);
        builder.setHasMeasurementCorrectionsReflectingPlane(true);
        builder.setHasMeasurementCorrelationVectors(true);
        builder.setHasMsa(true);
        builder.setHasMsb(true);
        builder.setHasNavigationMessages(true);
        builder.setHasOnDemandTime(true);
        builder.setHasPowerTotal(true);
        builder.setHasPowerSinglebandAcquisition(true);
        builder.setHasPowerMultibandAcquisition(true);
        builder.setHasPowerSinglebandTracking(true);
        builder.setHasPowerMultibandTracking(true);
        builder.setHasPowerOtherModes(true);
        builder.setHasSatelliteBlocklist(true);
        builder.setHasSatellitePvt(true);
        builder.setHasScheduling(true);
        builder.setHasSingleShotFix(true);
        builder.setGnssSignalTypes(Arrays.asList(SIGNAL_TYPE_1, SIGNAL_TYPE_2));
        return builder.build();
    }

    private static void verifyTestValues(GnssCapabilities gnssCapabilities) {
        assertEquals(GnssCapabilities.CAPABILITY_SUPPORTED,
                gnssCapabilities.hasAccumulatedDeltaRange());
        assertEquals(true, gnssCapabilities.hasAntennaInfo());
        assertEquals(true, gnssCapabilities.hasGeofencing());
        assertEquals(true, gnssCapabilities.hasLowPowerMode());
        assertEquals(true, gnssCapabilities.hasMeasurements());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrections());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrectionsExcessPathLength());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrectionsForDriving());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrectionsLosSats());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrectionsReflectingPlane());
        assertEquals(true, gnssCapabilities.hasMeasurementCorrelationVectors());
        assertEquals(true, gnssCapabilities.hasMsa());
        assertEquals(true, gnssCapabilities.hasMsb());
        assertEquals(true, gnssCapabilities.hasNavigationMessages());
        assertEquals(true, gnssCapabilities.hasOnDemandTime());
        assertEquals(true, gnssCapabilities.hasPowerTotal());
        assertEquals(true, gnssCapabilities.hasPowerSinglebandAcquisition());
        assertEquals(true, gnssCapabilities.hasPowerMultibandAcquisition());
        assertEquals(true, gnssCapabilities.hasPowerSinglebandTracking());
        assertEquals(true, gnssCapabilities.hasPowerMultibandTracking());
        assertEquals(true, gnssCapabilities.hasPowerOtherModes());
        assertEquals(true, gnssCapabilities.hasSatelliteBlocklist());
        assertEquals(true, gnssCapabilities.hasSatellitePvt());
        assertEquals(true, gnssCapabilities.hasScheduling());
        assertEquals(true, gnssCapabilities.hasSingleShotFix());
        assertEquals(SIGNAL_TYPE_1, gnssCapabilities.getGnssSignalTypes().get(0));
        assertEquals(SIGNAL_TYPE_2, gnssCapabilities.getGnssSignalTypes().get(1));
    }
}
