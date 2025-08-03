Fixed APKs, along with v4 signatures and digests used in ChecksumsTest.java.
Has to be submitted instead of built to keep hashes constant.

Generation of these apks was performed using the `apksigner` command-line tool,
which lives at `tools/apksig/src/apksigner/java/com/android/apksigner/` in the
android source tree.  Please refer to the usage instructions there for how to
sign APKs using different keystores, providers, etc.

Source app:
    cts/hostsidetests/appsecurity/test-apps/tinyapp

Use this command to re-generate the apk and v4 signature file:
    apksigner sign --v2-signing-enabled false --v3-signing-enabled false --v4-signing-enabled false --key cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.pk8 --cert cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.x509.pem -out cts/tests/tests/content/data/CtsPkgInstallTinyAppV1.apk cts/hostsidetests/appsecurity/res/pkgsigverify/original.apk
    apksigner sign --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled --key cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.pk8 --cert cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.x509.pem -out cts/tests/tests/content/data/CtsPkgInstallTinyAppV2V3V4.apk cts/hostsidetests/appsecurity/res/pkgsigverify/original.apk
    apksigner sign --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled --key cts/hostsidetests/appsecurity/certs/pkgsigverify/ec-p384.pk8 --cert cts/hostsidetests/appsecurity/certs/pkgsigverify/ec-p384.x509.pem -out cts/tests/tests/content/data/CtsPkgInstallTinyAppV2V3V4-Sha512withEC.apk cts/hostsidetests/appsecurity/res/pkgsigverify/original.apk
    apksigner sign --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled --verity-enabled --key cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.pk8 --cert cts/hostsidetests/appsecurity/certs/pkgsigverify/dsa-3072.x509.pem -out cts/tests/tests/content/data/CtsPkgInstallTinyAppV2V3V4-Verity.apk cts/hostsidetests/appsecurity/res/pkgsigverify/original.apk

!Please note that all hardcoded hashes in ChecksumsTest.java will have to be changed!
Use md5sum, sha1sum, sha256sum, sha512sum to regenerate full apk hashes.

To enable signature check, use ApkChecksums.writeChecksums to store the required checksums:
    CtsPkgInstallTinyAppV2V3V4.digests
    HelloWorld5.digests
    HelloWorld5_hdpi-v4.digests
    HelloWorld5_mdpi-v4.digests

Create a self-signed certificate:
    openssl req -x509 -newkey rsa:4096 -nodes -keyout test-key.pem -out test-cert.x509.pem -days 36500 -subj "/C=US/ST=California/L=Mountain View/O=Android/OU=Android/CN=Android/emailAddress=incremental-dev@google.com"
Sign:
    openssl cms -sign -binary -nosmimecap -in CtsPkgInstallTinyAppV2V3V4.digests -signer test-cert.x509.pem -inkey test-key.pem -outform der -out CtsPkgInstallTinyAppV2V3V4.digests.signature
    openssl cms -sign -binary -nosmimecap -in HelloWorld5.digests -signer test-cert.x509.pem -inkey test-key.pem -outform der -out HelloWorld5.digests.signature
    openssl cms -sign -binary -nosmimecap -in HelloWorld5_hdpi-v4.digests -signer test-cert.x509.pem -inkey test-key.pem -outform der -out HelloWorld5_hdpi-v4.digests.signature
    openssl cms -sign -binary -nosmimecap -in HelloWorld5_mdpi-v4.digests -signer test-cert.x509.pem -inkey test-key.pem -outform der -out HelloWorld5_mdpi-v4.digests.signature

Verify the resulting signature:
    openssl cms -verify -binary -in CtsPkgInstallTinyAppV2V3V4.digests.signature -inform der -CAfile test-cert.x509.pem -signer test-cert.x509.pem -content CtsPkgInstallTinyAppV2V3V4.digests
Print out the content of the signature:
    openssl pkcs7 -print -inform DER -in CtsPkgInstallTinyAppV2V3V4.digests.signature

malformed.apk is a copy of CtsPkgInstallTinyAppV2V3V4.apk, and malformed.apk.idsig is a purposefully created idsig causing OOM

cts-testkey1.x509.pem is a copy of cts/hostsidetests/appsecurity/certs/cts-testkey1.x509.pem