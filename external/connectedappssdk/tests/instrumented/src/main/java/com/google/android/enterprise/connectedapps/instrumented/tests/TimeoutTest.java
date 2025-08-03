package com.google.android.enterprise.connectedapps.instrumented.tests;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.ProfileConnectionHolder;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingStringCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for timeout behaviour.
 *
 * <p>This is required as robolectric does not have callbacks behave correctly after the binding is
 * closed.
 */
@RunWith(JUnit4.class)
public class TimeoutTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final String STRING = "String";

  private final TestProfileConnector connector = TestProfileConnector.create(context);
  private final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);
  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @After
  public void teardown() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void
      other_async_callbackTriggeredMultipleTimes_connectionHeldOpen_isReceivedMultipleTimes()
          throws Exception {
    BlockingStringCallbackListener stringCallbackListener = new BlockingStringCallbackListener();

    try (ProfileConnectionHolder connectionHolder =
        connector.addConnectionHolder(stringCallbackListener)) {
      type.other()
          .asyncIdentityStringMethodWhichCallsBackTwiceWithNonBlockingDelay(
              STRING,
              stringCallbackListener,
              /* secondsDelay= */ 60,
              new BlockingExceptionCallbackListener());
      stringCallbackListener.await();

      assertThat(stringCallbackListener.await(200, TimeUnit.SECONDS)).isEqualTo(STRING);
    }
  }
}
