package device.generic.car.emulator;

import device.generic.car.emulator.IVehicleBusCallback;

/**
 * IVehicleBus provides generic interface for various bus interpreters
 * (emulated/virtual/physical/etc) to connect to Emulator VHAL and provides
 * VehiclePropValue update as if it were generated from real car.
 *
 * This separates bus implementation details from emulator. For example,
 * hard-coded rotary controller vendor specific key mapping can separately
 * run as a service, allowing emulator to be spinned separately from the
 * particular hardware of interest to be tested at the time.
 */

import android.hardware.automotive.vehicle.VehiclePropValue;

@VintfStability
interface IVehicleBus {
    /**
     * Sets a callback function for whenever new property value is generated
     * from the bus. Calling this function twice will result in error. To
     * change callback function, call unset function first.
     */
    void setOnNewPropValuesCallback(IVehicleBusCallback callback);

    /**
     * Unregisters a callback function that is currently active. Expected to
     * receive the same Callback that was used to set for successful unset.
     */
    void unsetOnNewPropValuesCallback(IVehicleBusCallback callback);
}
