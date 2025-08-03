package device.generic.car.emulator;

import device.generic.car.emulator.IVehicleBusCallback;
import android.hardware.automotive.vehicle.VehiclePropValue;

@VintfStability
interface IVehicleBusCallback {

    /**
     * This callback is called whenever there is a new propValue detected
     * from the bus.
     */
    oneway void onNewPropValues(in VehiclePropValue[] values);
}
