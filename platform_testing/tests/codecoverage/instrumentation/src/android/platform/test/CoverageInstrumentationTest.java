package android.platform.test.coverage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CoverageInstrumentationTest {

    private Example mExample;

    @Before
    public void setUp() {
        mExample = new Example();
    }

    @Test
    public void testCoveredMethod() {
        mExample.coveredMethod();
    }
}
