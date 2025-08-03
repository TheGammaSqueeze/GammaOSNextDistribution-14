package com.google.android.enterprise.connectedapps;

import android.os.UserHandle;

/**
 * {@link AutoCloseable} wrapper around a connection holder.
 *
 * <p>This will automatically call {@link UserConnector#removeConnectionHolder(UserHandle, Object)}
 * when closed.
 */
public final class UserConnectionHolder implements AutoCloseable {
  private final UserConnector userConnector;
  private final UserHandle userHandle;
  private final Object connectionHolder;

  public static UserConnectionHolder create(
      UserConnector userConnector, UserHandle userHandle, Object connectionHolder) {

    UserConnectionHolder userConnectionHolder =
        new UserConnectionHolder(userConnector, userHandle, connectionHolder);

    userConnector.addConnectionHolderAlias(userHandle, userConnectionHolder, connectionHolder);

    return userConnectionHolder;
  }

  private UserConnectionHolder(
      UserConnector userConnector, UserHandle userHandle, Object connectionHolder) {
    if (userConnector == null || userHandle == null || connectionHolder == null) {
      throw new NullPointerException();
    }
    this.userConnector = userConnector;
    this.userHandle = userHandle;
    this.connectionHolder = connectionHolder;
  }

  @Override
  public void close() {
    userConnector.removeConnectionHolder(userHandle, connectionHolder);
  }
}
