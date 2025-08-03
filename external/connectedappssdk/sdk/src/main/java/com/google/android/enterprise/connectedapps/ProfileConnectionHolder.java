package com.google.android.enterprise.connectedapps;


/**
 * {@link AutoCloseable} wrapper around a connection holder.
 *
 * <p>This will automatically call {@link ProfileConnector#removeConnectionHolder(Object)} when
 * closed.
 */
public final class ProfileConnectionHolder implements AutoCloseable {
  private final ProfileConnector profileConnector;
  private final Object connectionHolder;

  public static ProfileConnectionHolder create(
      ProfileConnector profileConnector, Object connectionHolder) {

    ProfileConnectionHolder profileConnectionHolder =
        new ProfileConnectionHolder(profileConnector, connectionHolder);

    profileConnector.addConnectionHolderAlias(profileConnectionHolder, connectionHolder);

    return profileConnectionHolder;
  }

  private ProfileConnectionHolder(ProfileConnector profileConnector, Object connectionHolder) {
    if (profileConnector == null || connectionHolder == null) {
      throw new NullPointerException();
    }
    this.profileConnector = profileConnector;
    this.connectionHolder = connectionHolder;
  }

  @Override
  public void close() {
    profileConnector.removeConnectionHolder(connectionHolder);
  }
}
