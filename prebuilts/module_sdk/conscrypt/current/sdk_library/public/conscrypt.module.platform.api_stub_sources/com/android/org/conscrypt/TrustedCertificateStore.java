/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.org.conscrypt;

import java.io.File;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

/**
 * A source for trusted root certificate authority (CA) certificates
 * supporting an immutable system CA directory along with mutable
 * directories allowing the user addition of custom CAs and user
 * removal of system CAs. This store supports the {@code
 * TrustedCertificateKeyStoreSpi} wrapper to allow a traditional
 * KeyStore interface for use with {@link
 * javax.net.ssl.TrustManagerFactory.init}.
 *
 * <p>The CAs are accessed via {@code KeyStore} style aliases. Aliases
 * are made up of a prefix identifying the source ("system:" vs
 * "user:") and a suffix based on the OpenSSL X509_NAME_hash_old
 * function of the CA's subject name. For example, the system CA for
 * "C=US, O=VeriSign, Inc., OU=Class 3 Public Primary Certification
 * Authority" could be represented as "system:7651b327.0". By using
 * the subject hash, operations such as {@link #getCertificateAlias
 * getCertificateAlias} can be implemented efficiently without
 * scanning the entire store.
 *
 * <p>In addition to supporting the {@code
 * TrustedCertificateKeyStoreSpi} implementation, {@code
 * TrustedCertificateStore} also provides the additional public
 * method  {@link #findIssuer} to allow  efficient lookup operations
 * for CAs again based on the file naming convention.
 *
 * <p>The KeyChainService users the {@link installCertificate} and
 * {@link #deleteCertificateEntry} to install user CAs as well as
 * delete those user CAs as well as system CAs. The deletion of system
 * CAs is performed by placing an exact copy of that CA in the deleted
 * directory. Such deletions are intended to persist across upgrades
 * but not intended to mask a CA with a matching name or public key
 * but is otherwise reissued in a system update. Reinstalling a
 * deleted system certificate simply removes the copy from the deleted
 * directory, reenabling the original in the system directory.
 *
 * <p>Note that the default mutable directory is created by init via
 * configuration in the system/core/rootdir/init.rc file. The
 * directive "mkdir /data/misc/keychain 0775 system system"
 * ensures that its owner and group are the system uid and system
 * gid and that it is world readable but only writable by the system
 * user.
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class TrustedCertificateStore implements com.android.org.conscrypt.ConscryptCertStore {

public TrustedCertificateStore() { throw new RuntimeException("Stub!"); }

public static final boolean isUser(java.lang.String alias) { throw new RuntimeException("Stub!"); }

public static void setDefaultUserDirectory(java.io.File root) { throw new RuntimeException("Stub!"); }

public java.security.cert.Certificate getCertificate(java.lang.String alias) { throw new RuntimeException("Stub!"); }

public java.security.cert.Certificate getCertificate(java.lang.String alias, boolean includeDeletedSystem) { throw new RuntimeException("Stub!"); }

public java.util.Date getCreationDate(java.lang.String alias) { throw new RuntimeException("Stub!"); }

public java.util.Set<java.lang.String> aliases() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.lang.String> userAliases() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.lang.String> allSystemAliases() { throw new RuntimeException("Stub!"); }

public boolean containsAlias(java.lang.String alias) { throw new RuntimeException("Stub!"); }

public java.lang.String getCertificateAlias(java.security.cert.Certificate c) { throw new RuntimeException("Stub!"); }

public java.lang.String getCertificateAlias(java.security.cert.Certificate c, boolean includeDeletedSystem) { throw new RuntimeException("Stub!"); }

/**
 * Returns true to indicate that the certificate was added by the
 * user, false otherwise.
 */

public boolean isUserAddedCertificate(java.security.cert.X509Certificate cert) { throw new RuntimeException("Stub!"); }

/**
 * Returns a File for where the certificate is found if it exists
 * or where it should be installed if it does not exist. The
 * caller can disambiguate these cases by calling {@code
 * File.exists()} on the result.
 *
 * @VisibleForTesting
 */

public java.io.File getCertificateFile(java.io.File dir, java.security.cert.X509Certificate x) { throw new RuntimeException("Stub!"); }

/**
 * This non-{@code KeyStoreSpi} public interface is used by {@code
 * TrustManagerImpl} to locate a CA certificate with the same name
 * and public key as the provided {@code X509Certificate}. We
 * match on the name and public key and not the entire certificate
 * since a CA may be reissued with the same name and PublicKey but
 * with other differences (for example when switching signature
 * from md2WithRSAEncryption to SHA1withRSA)
 */

public java.security.cert.X509Certificate getTrustAnchor(java.security.cert.X509Certificate c) { throw new RuntimeException("Stub!"); }

/**
 * This non-{@code KeyStoreSpi} public interface is used by {@code
 * TrustManagerImpl} to locate the CA certificate that signed the
 * provided {@code X509Certificate}.
 */

public java.security.cert.X509Certificate findIssuer(java.security.cert.X509Certificate c) { throw new RuntimeException("Stub!"); }

public java.util.Set<java.security.cert.X509Certificate> findAllIssuers(java.security.cert.X509Certificate c) { throw new RuntimeException("Stub!"); }

/**
 * Attempt to build a certificate chain from the supplied {@code leaf}
 * argument through the chain of issuers as high up as known. If the chain
 * can't be completed, the most complete chain available will be returned.
 * This means that a list with only the {@code leaf} certificate is returned
 * if no issuer certificates could be found.
 *
 * @throws java.security.cert.CertificateException if there was a problem parsing the
 *             certificates
 */

public java.util.List<java.security.cert.X509Certificate> getCertificateChain(java.security.cert.X509Certificate leaf) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

/**
 * This non-{@code KeyStoreSpi} public interface is used by the
 * {@code KeyChainService} to install new CA certificates. It
 * silently ignores the certificate if it already exists in the
 * store.
 */

public void installCertificate(java.security.cert.X509Certificate cert) throws java.security.cert.CertificateException, java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * This could be considered the implementation of {@code
 * TrustedCertificateKeyStoreSpi.engineDeleteEntry} but we
 * consider {@code TrustedCertificateKeyStoreSpi} to be read
 * only. Instead, this is used by the {@code KeyChainService} to
 * delete CA certificates.
 */

public void deleteCertificateEntry(java.lang.String alias) throws java.security.cert.CertificateException, java.io.IOException { throw new RuntimeException("Stub!"); }
}

