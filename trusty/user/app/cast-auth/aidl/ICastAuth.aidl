interface ICastAuth {
    const @utf8InCpp String PORT = "com.android.trusty.cast_auth";

    void ProvisionKey(in byte[] wrappedKey);

    byte[] SignHash(in byte[] hash);
}
