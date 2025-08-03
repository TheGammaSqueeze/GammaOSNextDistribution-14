package android.server.wm.settings;

import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Test @Rule class that opens and closes an {@link AutoCloseable} around the test.
 */
class AutoCloseableRule implements TestRule {

    private final Supplier<AutoCloseable> mSupplier;

    public AutoCloseableRule(Supplier<AutoCloseable> supplier) {
        mSupplier = supplier;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (final AutoCloseable wrapper = mSupplier.get()) {
                    base.evaluate();
                }
            }
        };
    }
}

