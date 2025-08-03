/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.ipsec.ike.cts;

import static android.net.IpSecAlgorithm.AUTH_AES_CMAC;
import static android.net.IpSecAlgorithm.AUTH_AES_XCBC;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_AES_GCM;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305;
import static android.net.IpSecAlgorithm.AUTH_HMAC_MD5;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA1;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA256;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA384;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA512;
import static android.net.IpSecAlgorithm.CRYPT_AES_CBC;
import static android.net.IpSecAlgorithm.CRYPT_AES_CTR;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_1024_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_1536_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_3072_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_4096_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_CURVE_25519;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_NONE;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_3DES;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_CBC;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_CTR;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_CHACHA20_POLY1305;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_AES_CMAC_96;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_AES_XCBC_96;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_384_192;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_NONE;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_128;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_192;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_256;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_UNUSED;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_CMAC;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_SHA2_384;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_SHA2_512;
import static android.os.Build.VERSION.CODENAME;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.net.IpSecAlgorithm;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.SaProposal;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SaProposalTest {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final List<Pair<Integer, Integer>> IKE_NORMAL_MODE_CIPHERS =
            getNormalModeCiphers(true /* isIke */);
    private static final List<Pair<Integer, Integer>> IKE_COMBINED_MODE_CIPHERS =
            getCombinedModeCiphers(true /* isIke */);
    private static final List<Pair<Integer, Integer>> CHILD_NORMAL_MODE_CIPHERS =
            getNormalModeCiphers(false /* isIke */);
    private static final List<Pair<Integer, Integer>> CHILD_COMBINED_MODE_CIPHERS =
            getCombinedModeCiphers(false /* isIke */);

    private static final List<Integer> IKE_INTEGRITY_ALGOS = getIntegrityAlgos(true /* isIke */);
    private static final List<Integer> CHILD_INTEGRITY_ALGOS = getIntegrityAlgos(false /* isIke */);

    private static final List<Integer> DH_GROUPS = getDhGroups(false /* includeNone */);
    private static final List<Integer> DH_GROUPS_WITH_NONE = getDhGroups(true /* includeNone */);
    private static final List<Integer> PRFS = getPrfs();

    // Utility method for 3DES and ChaChaPoly
    private static void addCipherWithFixedKeyLenIfSupported(
            int cipherId, boolean isIke, List<Pair<Integer, Integer>> ciphers) {
        final Set<Integer> supportedAlgos =
                isIke
                        ? IkeSaProposal.getSupportedEncryptionAlgorithms()
                        : ChildSaProposal.getSupportedEncryptionAlgorithms();

        if (supportedAlgos.contains(cipherId)) {
            ciphers.add(new Pair<>(cipherId, KEY_LEN_UNUSED));
        }
    }

    // Utility method for AES-CBC, AES-CTR and AES-GCM
    private static void addAesCipherIfSupported(
            int cipherId, boolean isIke, List<Pair<Integer, Integer>> ciphers) {
        final Set<Integer> supportedAlgos =
                isIke
                        ? IkeSaProposal.getSupportedEncryptionAlgorithms()
                        : ChildSaProposal.getSupportedEncryptionAlgorithms();

        if (supportedAlgos.contains(cipherId)) {
            ciphers.add(new Pair<>(cipherId, KEY_LEN_AES_128));
            ciphers.add(new Pair<>(cipherId, KEY_LEN_AES_192));
            ciphers.add(new Pair<>(cipherId, KEY_LEN_AES_256));
        }
    }

    private static List<Pair<Integer, Integer>> getNormalModeCiphers(boolean isIke) {
        final List<Pair<Integer, Integer>> ciphers = new ArrayList<>();
        addCipherWithFixedKeyLenIfSupported(ENCRYPTION_ALGORITHM_3DES, isIke, ciphers);
        addAesCipherIfSupported(ENCRYPTION_ALGORITHM_AES_CBC, isIke, ciphers);
        addAesCipherIfSupported(ENCRYPTION_ALGORITHM_AES_CTR, isIke, ciphers);
        return ciphers;
    }

    private static List<Pair<Integer, Integer>> getCombinedModeCiphers(boolean isIke) {
        final List<Pair<Integer, Integer>> ciphers = new ArrayList<>();
        addCipherWithFixedKeyLenIfSupported(ENCRYPTION_ALGORITHM_CHACHA20_POLY1305, isIke, ciphers);
        addAesCipherIfSupported(ENCRYPTION_ALGORITHM_AES_GCM_8, isIke, ciphers);
        addAesCipherIfSupported(ENCRYPTION_ALGORITHM_AES_GCM_12, isIke, ciphers);
        addAesCipherIfSupported(ENCRYPTION_ALGORITHM_AES_GCM_16, isIke, ciphers);
        return ciphers;
    }

    private static List<Integer> getIntegrityAlgos(boolean isIke) {
        final List<Integer> algoList = new ArrayList<>();

        if (isIke) {
            algoList.addAll(IkeSaProposal.getSupportedIntegrityAlgorithms());
        } else {
            algoList.addAll(ChildSaProposal.getSupportedIntegrityAlgorithms());
        }
        algoList.remove(INTEGRITY_ALGORITHM_NONE);

        return algoList;
    }

    private static List<Integer> getDhGroups(boolean includeNone) {
        final List<Integer> algoList = new ArrayList<>();

        algoList.addAll(SaProposal.getSupportedDhGroups());
        if (!includeNone) {
            algoList.remove(DH_GROUP_NONE);
        }
        return algoList;
    }

    private static List<Integer> getPrfs() {
        final List<Integer> algoList = new ArrayList<>();
        algoList.addAll(IkeSaProposal.getSupportedPseudorandomFunctions());
        return algoList;
    }

    // Package private
    static IkeSaProposal buildIkeSaProposalWithNormalModeCipher() {
        // This IkeSaProposal will be used for IKE exchange tests with pre-captured responses
        // that expect KE payload with MODP 1024. Make sure MODP 1024 is the first DH group in the
        // proposal so that IKE client will sent KE payload using MODP 1024.
        return buildIkeSaProposalWithNormalModeCipher(true /* preferModp1024 */);
    }

    private static IkeSaProposal buildIkeSaProposalWithNormalModeCipher(boolean preferModp1024) {
        return buildIkeSaProposal(
                IKE_NORMAL_MODE_CIPHERS, IKE_INTEGRITY_ALGOS, PRFS, DH_GROUPS, preferModp1024);
    }

    // Package private
    static IkeSaProposal buildIkeSaProposalWithCombinedModeCipher() {
        return buildIkeSaProposalWithCombinedModeCipher(true /* hasIntegrityNone */);
    }

    private static IkeSaProposal buildIkeSaProposalWithCombinedModeCipher(
            boolean hasIntegrityNone) {
        return buildIkeSaProposalWithCombinedModeCipher(
                hasIntegrityNone, false /* preferModp1024 */);
    }

    private static IkeSaProposal buildIkeSaProposalWithCombinedModeCipher(
            boolean hasIntegrityNone, boolean preferModp1024) {
        final List<Integer> integerAlgos = new ArrayList<>();
        if (hasIntegrityNone) {
            integerAlgos.add(INTEGRITY_ALGORITHM_NONE);
        }
        return buildIkeSaProposal(
                IKE_COMBINED_MODE_CIPHERS, integerAlgos, PRFS, DH_GROUPS, preferModp1024);
    }

    private static IkeSaProposal buildIkeSaProposal(
            List<Pair<Integer, Integer>> ciphers,
            List<Integer> integrityAlgos,
            List<Integer> prfs,
            List<Integer> dhGroups) {
        return buildIkeSaProposal(
                ciphers, integrityAlgos, prfs, dhGroups, false /* preferModp1024 */);
    }

    private static IkeSaProposal buildIkeSaProposal(
            List<Pair<Integer, Integer>> ciphers,
            List<Integer> integrityAlgos,
            List<Integer> prfs,
            List<Integer> dhGroups,
            boolean preferModp1024) {
        IkeSaProposal.Builder builder = new IkeSaProposal.Builder();

        for (Pair<Integer, Integer> pair : ciphers) {
            builder.addEncryptionAlgorithm(pair.first, pair.second);
        }
        for (int algo : integrityAlgos) {
            builder.addIntegrityAlgorithm(algo);
        }
        for (int algo : prfs) {
            builder.addPseudorandomFunction(algo);
        }

        // Make sure MODP 1024 is the first DH group if preferModp1024 is true
        if (preferModp1024) {
            builder.addDhGroup(DH_GROUP_1024_BIT_MODP);
        }
        for (int algo : dhGroups) {
            if (algo != DH_GROUP_1024_BIT_MODP || !preferModp1024) {
                builder.addDhGroup(algo);
            }
        }

        return builder.build();
    }

    // Package private
    static ChildSaProposal buildChildSaProposalWithNormalModeCipher() {
        return buildChildSaProposal(
                CHILD_NORMAL_MODE_CIPHERS, CHILD_INTEGRITY_ALGOS, DH_GROUPS_WITH_NONE);
    }

    // Package private
    static ChildSaProposal buildChildSaProposalWithCombinedModeCipher() {
        return buildChildSaProposalWithCombinedModeCipher(true /* hasIntegrityNone */);
    }

    private static ChildSaProposal buildChildSaProposalWithCombinedModeCipher(
            boolean hasIntegrityNone) {
        final List<Integer> integerAlgos = new ArrayList<>();
        if (hasIntegrityNone) {
            integerAlgos.add(INTEGRITY_ALGORITHM_NONE);
        }

        return buildChildSaProposal(CHILD_COMBINED_MODE_CIPHERS, integerAlgos, DH_GROUPS_WITH_NONE);
    }

    private static ChildSaProposal buildChildSaProposal(
            List<Pair<Integer, Integer>> ciphers,
            List<Integer> integrityAlgos,
            List<Integer> dhGroups) {
        ChildSaProposal.Builder builder = new ChildSaProposal.Builder();

        for (Pair<Integer, Integer> pair : ciphers) {
            builder.addEncryptionAlgorithm(pair.first, pair.second);
        }
        for (int algo : integrityAlgos) {
            builder.addIntegrityAlgorithm(algo);
        }
        for (int algo : dhGroups) {
            builder.addDhGroup(algo);
        }

        return builder.build();
    }

    // Package private
    static ChildSaProposal buildChildSaProposalWithOnlyCiphers() {
        return buildChildSaProposal(
                CHILD_COMBINED_MODE_CIPHERS, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    @Test
    public void testBuildIkeSaProposalWithNormalModeCipher() {
        IkeSaProposal saProposal =
                buildIkeSaProposalWithNormalModeCipher(false /* preferModp1024 */);

        assertEquals(IKE_NORMAL_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertEquals(IKE_INTEGRITY_ALGOS, saProposal.getIntegrityAlgorithms());
        assertEquals(PRFS, saProposal.getPseudorandomFunctions());
        assertEquals(DH_GROUPS, saProposal.getDhGroups());
    }

    @Test
    public void testBuildIkeSaProposalWithCombinedModeCipher() {
        IkeSaProposal saProposal =
                buildIkeSaProposalWithCombinedModeCipher(false /* hasIntegrityNone */);

        assertEquals(IKE_COMBINED_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertEquals(PRFS, saProposal.getPseudorandomFunctions());
        assertEquals(DH_GROUPS, saProposal.getDhGroups());
        assertTrue(saProposal.getIntegrityAlgorithms().isEmpty());
    }

    @Test
    public void testBuildIkeSaProposalWithCombinedModeCipherAndIntegrityNone() {
        IkeSaProposal saProposal =
                buildIkeSaProposalWithCombinedModeCipher(true /* hasIntegrityNone */);

        assertEquals(IKE_COMBINED_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertEquals(PRFS, saProposal.getPseudorandomFunctions());
        assertEquals(DH_GROUPS, saProposal.getDhGroups());
        assertEquals(Arrays.asList(INTEGRITY_ALGORITHM_NONE), saProposal.getIntegrityAlgorithms());
    }

    @Test
    public void testBuildChildSaProposalWithNormalModeCipher() {
        ChildSaProposal saProposal = buildChildSaProposalWithNormalModeCipher();

        assertEquals(CHILD_NORMAL_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertEquals(CHILD_INTEGRITY_ALGOS, saProposal.getIntegrityAlgorithms());
        assertEquals(DH_GROUPS_WITH_NONE, saProposal.getDhGroups());
    }

    @Test
    public void testBuildChildProposalWithCombinedModeCipher() {
        ChildSaProposal saProposal =
                buildChildSaProposalWithCombinedModeCipher(false /* hasIntegrityNone */);

        assertEquals(CHILD_COMBINED_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertTrue(saProposal.getIntegrityAlgorithms().isEmpty());
        assertEquals(DH_GROUPS_WITH_NONE, saProposal.getDhGroups());
    }

    @Test
    public void testBuildChildProposalWithCombinedModeCipherAndIntegrityNone() {
        ChildSaProposal saProposal =
                buildChildSaProposalWithCombinedModeCipher(true /* hasIntegrityNone */);

        assertEquals(CHILD_COMBINED_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertEquals(Arrays.asList(INTEGRITY_ALGORITHM_NONE), saProposal.getIntegrityAlgorithms());
        assertEquals(DH_GROUPS_WITH_NONE, saProposal.getDhGroups());
    }

    @Test
    public void testBuildChildSaProposalWithOnlyCiphers() {
        ChildSaProposal saProposal = buildChildSaProposalWithOnlyCiphers();

        assertEquals(CHILD_COMBINED_MODE_CIPHERS, saProposal.getEncryptionAlgorithms());
        assertTrue(saProposal.getIntegrityAlgorithms().isEmpty());
        assertTrue(saProposal.getDhGroups().isEmpty());
    }

    private static final String IPSEC_NAME_NA = "";

    private static class CryptoInfo {
        public final int minSdk;
        public final String ipSecName;

        CryptoInfo(int minSdk, String ipSecName) {
            this.minSdk = minSdk;
            this.ipSecName = ipSecName;
        }
    }

    private static final Map<Integer, CryptoInfo> ALL_ENCRYPT_ALGOS = getAllCiphers();
    private static final Map<Integer, CryptoInfo> ALL_INTEGRITY_ALGOS = getAllIntegrityAlgos();
    private static final Map<Integer, Integer> ALL_DH_GROUPS = getAllDhGroups();
    private static final Map<Integer, Integer> ALL_PRFS = getAllPrfs();

    private static Map<Integer, CryptoInfo> getAllCiphers() {
        final Map<Integer, CryptoInfo> ciphers = new HashMap<>();
        ciphers.put(ENCRYPTION_ALGORITHM_3DES, new CryptoInfo(R, IPSEC_NAME_NA));
        ciphers.put(ENCRYPTION_ALGORITHM_AES_CBC, new CryptoInfo(R, CRYPT_AES_CBC));
        ciphers.put(ENCRYPTION_ALGORITHM_AES_CTR, new CryptoInfo(R, CRYPT_AES_CTR));
        ciphers.put(ENCRYPTION_ALGORITHM_AES_GCM_8, new CryptoInfo(R, AUTH_CRYPT_AES_GCM));
        ciphers.put(ENCRYPTION_ALGORITHM_AES_GCM_12, new CryptoInfo(R, AUTH_CRYPT_AES_GCM));
        ciphers.put(ENCRYPTION_ALGORITHM_AES_GCM_16, new CryptoInfo(R, AUTH_CRYPT_AES_GCM));
        ciphers.put(
                ENCRYPTION_ALGORITHM_CHACHA20_POLY1305,
                new CryptoInfo(R, AUTH_CRYPT_CHACHA20_POLY1305));
        return ciphers;
    }

    private static Map<Integer, CryptoInfo> getAllIntegrityAlgos() {
        final Map<Integer, CryptoInfo> integrityAlgos = new HashMap<>();
        integrityAlgos.put(INTEGRITY_ALGORITHM_NONE, new CryptoInfo(R, IPSEC_NAME_NA));
        integrityAlgos.put(INTEGRITY_ALGORITHM_HMAC_SHA1_96, new CryptoInfo(R, AUTH_HMAC_SHA1));
        integrityAlgos.put(INTEGRITY_ALGORITHM_AES_XCBC_96, new CryptoInfo(R, AUTH_AES_XCBC));
        integrityAlgos.put(
                INTEGRITY_ALGORITHM_HMAC_SHA2_256_128, new CryptoInfo(R, AUTH_HMAC_SHA256));
        integrityAlgos.put(
                INTEGRITY_ALGORITHM_HMAC_SHA2_384_192, new CryptoInfo(R, AUTH_HMAC_SHA384));
        integrityAlgos.put(
                INTEGRITY_ALGORITHM_HMAC_SHA2_512_256, new CryptoInfo(R, AUTH_HMAC_SHA512));
        integrityAlgos.put(INTEGRITY_ALGORITHM_AES_CMAC_96, new CryptoInfo(S, AUTH_AES_CMAC));

        return integrityAlgos;
    }

    private static Map<Integer, Integer> getAllDhGroups() {
        final Map<Integer, Integer> dhGroups = new HashMap<>();
        dhGroups.put(DH_GROUP_NONE, R);
        dhGroups.put(DH_GROUP_1024_BIT_MODP, R);
        dhGroups.put(DH_GROUP_1536_BIT_MODP, R);
        dhGroups.put(DH_GROUP_2048_BIT_MODP, R);
        dhGroups.put(DH_GROUP_3072_BIT_MODP, R);
        dhGroups.put(DH_GROUP_4096_BIT_MODP, R);
        dhGroups.put(DH_GROUP_CURVE_25519, S);
        return dhGroups;
    }

    private static Map<Integer, Integer> getAllPrfs() {
        final Map<Integer, Integer> prfs = new HashMap<>();
        prfs.put(PSEUDORANDOM_FUNCTION_HMAC_SHA1, R);
        prfs.put(PSEUDORANDOM_FUNCTION_AES128_XCBC, R);
        prfs.put(PSEUDORANDOM_FUNCTION_SHA2_256, R);
        prfs.put(PSEUDORANDOM_FUNCTION_SHA2_384, R);
        prfs.put(PSEUDORANDOM_FUNCTION_SHA2_512, R);
        prfs.put(PSEUDORANDOM_FUNCTION_AES128_CMAC, S);
        return prfs;
    }

    private static Set<Integer> getExpectedSupportedCryptoAlgorithms(
            Map<Integer, CryptoInfo> algoMap, int sdkLevel) {
        final Set<Integer> supportedSet = new HashSet<>();
        for (Entry<Integer, CryptoInfo> entry : algoMap.entrySet()) {
            if (sdkLevel >= entry.getValue().minSdk) {
                supportedSet.add(entry.getKey());
            }
        }

        return supportedSet;
    }

    private static Set<Integer> getExpectedSupportedDhOrPrf(
            Map<Integer, Integer> algoMap, int sdkLevel) {
        final Set<Integer> supportedSet = new HashSet<>();
        for (Entry<Integer, Integer> entry : algoMap.entrySet()) {
            if (sdkLevel >= entry.getValue()) {
                supportedSet.add(entry.getKey());
            }
        }

        return supportedSet;
    }

    private static void checkGetSupportedIkeSaAlgos(int sdkLevel) {
        assertEquals(
                getExpectedSupportedCryptoAlgorithms(ALL_ENCRYPT_ALGOS, sdkLevel),
                IkeSaProposal.getSupportedEncryptionAlgorithms());
        assertEquals(
                getExpectedSupportedCryptoAlgorithms(ALL_INTEGRITY_ALGOS, sdkLevel),
                IkeSaProposal.getSupportedIntegrityAlgorithms());
        assertEquals(
                getExpectedSupportedDhOrPrf(ALL_DH_GROUPS, sdkLevel),
                IkeSaProposal.getSupportedDhGroups());
        assertEquals(
                getExpectedSupportedDhOrPrf(ALL_PRFS, sdkLevel),
                IkeSaProposal.getSupportedPseudorandomFunctions());
    }

    @Test
    public void testGetSupportedIkeAlgosOnReleasedSdk() {
        // It is a release branch.
        assumeTrue("REL".equals(CODENAME));
        checkGetSupportedIkeSaAlgos(SDK_INT);
    }

    @Test
    public void testGetSupportedIkeAlgosOnPrereleasedSdk() {
        // On a pre-released branch, the VERSION.SDK_INT is still the previous version code, and the
        // value of the current version code is CUR_DEVELOPMENT. For example, on an S development
        // branch, VERSION.SDK_INT is still VERSION_CODES.R (30), and VERSION_CODES.S is
        // CUR_DEVELOPMENT (1000).
        assumeFalse("REL".equals(CODENAME));
        checkGetSupportedIkeSaAlgos(CUR_DEVELOPMENT);
    }

    private static void checkGetSupportedChildSaAlgos(Set<String> expectedAlgos) {
        final Set<String> supportedIpSecAlgos = new HashSet<>();

        for (int algo : ChildSaProposal.getSupportedEncryptionAlgorithms()) {
            supportedIpSecAlgos.add(ALL_ENCRYPT_ALGOS.get(algo).ipSecName);
        }

        for (int algo : ChildSaProposal.getSupportedIntegrityAlgorithms()) {
            if (algo != INTEGRITY_ALGORITHM_NONE) {
                supportedIpSecAlgos.add(ALL_INTEGRITY_ALGOS.get(algo).ipSecName);
            }
        }
        assertEquals(expectedAlgos, supportedIpSecAlgos);
    }

    @Test
    @IgnoreUpTo(R)
    public void testGetSupportedChildAlgosAtLeastSdkS() {
        // MD5 is not allowed by IKE for security reasons
        final Set<String> expectedAlgos = new HashSet<>();
        expectedAlgos.addAll(IpSecAlgorithm.getSupportedAlgorithms());
        expectedAlgos.remove(AUTH_HMAC_MD5);

        checkGetSupportedChildSaAlgos(expectedAlgos);
    }

    @Test
    @IgnoreAfter(R)
    public void testGetSupportedChildAlgosPreS() {
        final Set<String> expectedAlgos = new HashSet<>();
        expectedAlgos.add(ALL_ENCRYPT_ALGOS.get(ENCRYPTION_ALGORITHM_AES_CBC).ipSecName);
        expectedAlgos.add(ALL_ENCRYPT_ALGOS.get(ENCRYPTION_ALGORITHM_AES_GCM_8).ipSecName);
        expectedAlgos.add(ALL_ENCRYPT_ALGOS.get(ENCRYPTION_ALGORITHM_AES_GCM_12).ipSecName);
        expectedAlgos.add(ALL_ENCRYPT_ALGOS.get(ENCRYPTION_ALGORITHM_AES_GCM_16).ipSecName);

        expectedAlgos.add(ALL_INTEGRITY_ALGOS.get(INTEGRITY_ALGORITHM_HMAC_SHA1_96).ipSecName);
        expectedAlgos.add(ALL_INTEGRITY_ALGOS.get(INTEGRITY_ALGORITHM_HMAC_SHA2_256_128).ipSecName);
        expectedAlgos.add(ALL_INTEGRITY_ALGOS.get(INTEGRITY_ALGORITHM_HMAC_SHA2_384_192).ipSecName);
        expectedAlgos.add(ALL_INTEGRITY_ALGOS.get(INTEGRITY_ALGORITHM_HMAC_SHA2_512_256).ipSecName);

        checkGetSupportedChildSaAlgos(expectedAlgos);
    }

    // TODO(b/148689509): Test throwing exception when algorithm combination is invalid
}
