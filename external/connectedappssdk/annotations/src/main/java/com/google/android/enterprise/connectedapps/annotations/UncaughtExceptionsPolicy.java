package com.google.android.enterprise.connectedapps.annotations;

/** Determines what to do when a cross-profile method has an uncaught exception. */
public enum UncaughtExceptionsPolicy {
  /** Notify the caller about the uncaught exception, then rethrow it. */
  NOTIFY_RETHROW(/* rethrowExceptions= */ true),
  /** Notify the caller about the uncaught exception, then suppress it. */
  NOTIFY_SUPPRESS(/* rethrowExceptions= */ false);

  public final boolean rethrowExceptions;

  UncaughtExceptionsPolicy(boolean rethrowExceptions) {
    this.rethrowExceptions = rethrowExceptions;
  }
}
