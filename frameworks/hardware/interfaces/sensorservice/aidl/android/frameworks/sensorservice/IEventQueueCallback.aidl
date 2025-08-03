package android.frameworks.sensorservice;

import android.hardware.sensors.Event;

/**
 * An IEventQueueCallback describes the callback that is called upon
 * any events.
 */
@VintfStability
oneway interface IEventQueueCallback {
    /**
     * When any event is obtained from the sensor, this function must be called
     * with the event data.
     *
     * Implementation of this function must finish in short time predictably.
     * It must never block or run for extended period of time. It must offload
     * heavy computation to a separate thread.
     *
     * @param event the event data.
     */
    void onEvent(in Event event);
}
