package com.google.android.enterprise.connectedapps.testing;

import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.UserConnector;

public interface FakeUserConnector extends UserConnector {
  boolean hasExplicitConnectionHolders(UserHandle userHandle);
}
