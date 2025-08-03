package android.aidl.tests;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class AidlJavaTests {

    public static void main(String[] args) {
        JUnitCore junit = new JUnitCore();
        junit.addListener(new TextListener(System.out));
        Result result = junit.run(ExtensionTests.class, GenericTests.class,
            JavaOnlyImmutableAnnotationTests.class, MapTests.class, NullableTests.class,
            TestServiceClient.class, TestVersionedInterface.class, UnionTests.class,
            VintfTests.class, BuiltInTests.class, DelegatorTests.class, TrunkStableTests.class);

        System.out.println(result.wasSuccessful() ? "TEST SUCCESS" : "TEST FAILURE");
    }
}
