package android.scheduling;

import android.os.RemoteCallback;

/**
 * Listener interface for system subcomponents to declare whether they are performing
 * reboot-blocking work.
 *
 * {@hide}
 */
oneway interface IRequestRebootReadinessStatusListener {
  void onRequestRebootReadinessStatus(in RemoteCallback reply);
}