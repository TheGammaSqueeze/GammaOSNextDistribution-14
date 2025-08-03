package android.test;

public class ClassAddedInExt1 {
    private ClassAddedInExt1() {}
    public static final int FIELD_ADDED_IN_EXT_1 = 1;
    public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 2;
    public void methodAddedInExt1() { throw new RuntimeException("Stub!"); }
    public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
}
