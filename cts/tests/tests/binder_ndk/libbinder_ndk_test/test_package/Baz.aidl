package test_package;

parcelable Baz {
    String a="FOO";
    int b=42;
    float c=3.14f;
    // This field doesn't exist in version 1.
    @nullable String[] d;
}
