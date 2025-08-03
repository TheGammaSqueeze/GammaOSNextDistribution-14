//! Test methods to confirm basic functionality of trait implementations.

extern crate alloc;
use authgraph_core::key::{
    AesKey, EcSignKey, EcVerifyKey, EcdhSecret, HmacKey, Identity, Key, Nonce12, PseudoRandKey,
    CURVE25519_PRIV_KEY_LEN, EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION, IDENTITY_VERSION,
};
use authgraph_core::keyexchange;
use authgraph_core::traits::{
    AesGcm, Device, EcDh, EcDsa, Hkdf, Hmac, MonotonicClock, Rng, Sha256,
};
use authgraph_core::{ag_err, error::Error};
use authgraph_wire::ErrorCode;
use coset::{
    cbor::Value,
    iana::{self, EnumI64},
    Algorithm, CborSerializable, CoseKey, CoseKeyBuilder, CoseSign1Builder, HeaderBuilder,
    KeyOperation, KeyType, Label,
};
use std::ffi::CString;

/// Test basic [`Rng`] functionality.
pub fn test_rng<R: Rng>(rng: &mut R) {
    let mut nonce1 = [0; 16];
    let mut nonce2 = [0; 16];
    rng.fill_bytes(&mut nonce1);
    assert_ne!(nonce1, nonce2, "random value is all zeroes!");

    rng.fill_bytes(&mut nonce2);
    assert_ne!(nonce1, nonce2, "two random values match!");
}

/// Test basic [`MonotonicClock`] functionality.
pub fn test_clock<C: MonotonicClock>(clock: &C) {
    let t1 = clock.now();
    let t2 = clock.now();
    assert!(t2.0 >= t1.0);
    std::thread::sleep(std::time::Duration::from_millis(400));
    let t3 = clock.now();
    assert!(t3.0 > (t1.0 + 200));
}

/// Test basic [`Sha256`] functionality.
pub fn test_sha256<S: Sha256>(digest: &S) {
    let tests: &[(&'static [u8], &'static str)] = &[
        (b"", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        (b"abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
    ];
    for (i, (data, want)) in tests.iter().enumerate() {
        let got = digest.compute_sha256(data).unwrap();
        assert_eq!(hex::encode(got), *want, "incorrect for case {i}")
    }
}

/// Test basic [`Hmac`] functionality.
pub fn test_hmac<H: Hmac>(hmac: &H) {
    struct TestCase {
        key: &'static str, // 32 bytes, hex-encoded
        data: &'static [u8],
        want: &'static str, // 32 bytes, hex-encoded
    }
    let tests = [
        TestCase {
            key: "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            data: b"Hello",
            want: "0adc968519e7e86e9fde625df7037baeab85ea5001583b93b9f576258bf7b20c",
        },
        TestCase {
            key: "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            data: &[],
            want: "d38b42096d80f45f826b44a9d5607de72496a415d3f4a1a8c88e3bb9da8dc1cb",
        },
    ];

    for (i, test) in tests.iter().enumerate() {
        let key = hex::decode(test.key).unwrap();
        let key = HmacKey(key.try_into().unwrap());
        let got = hmac.compute_hmac(&key, test.data).unwrap();
        assert_eq!(hex::encode(&got), test.want, "incorrect for case {i}");
    }
}

/// Test basic HKDF functionality.
pub fn test_hkdf<H: Hkdf>(h: &H) {
    struct TestCase {
        ikm: &'static str,
        salt: &'static str,
        info: &'static str,
        want: &'static str,
    }

    let tests = [
        // RFC 5869 section A.1
        TestCase {
            ikm: "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            salt: "000102030405060708090a0b0c",
            info: "f0f1f2f3f4f5f6f7f8f9",
            want: "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf",
        },
        // RFC 5869 section A.2
        TestCase {
            ikm: concat!(
                "000102030405060708090a0b0c0d0e0f",
                "101112131415161718191a1b1c1d1e1f",
                "202122232425262728292a2b2c2d2e2f",
                "303132333435363738393a3b3c3d3e3f",
                "404142434445464748494a4b4c4d4e4f",
            ),
            salt: concat!(
                "606162636465666768696a6b6c6d6e6f",
                "707172737475767778797a7b7c7d7e7f",
                "808182838485868788898a8b8c8d8e8f",
                "909192939495969798999a9b9c9d9e9f",
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
            ),
            info: concat!(
                "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf",
                "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf",
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf",
                "e0e1e2e3e4e5e6e7e8e9eaebecedeeef",
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
            ),
            want: "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c",
        },
        TestCase {
            ikm: "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            salt: "",
            info: "",
            want: "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d",
        },
    ];

    for (i, test) in tests.iter().enumerate() {
        let ikm = hex::decode(test.ikm).unwrap();
        let salt = hex::decode(test.salt).unwrap();
        let info = hex::decode(test.info).unwrap();

        let got = hkdf(h, &salt, &ikm, &info).unwrap().0;
        assert_eq!(hex::encode(got), test.want, "incorrect for case {i}");
    }
}

fn hkdf(hkdf: &dyn Hkdf, salt: &[u8], ikm: &[u8], info: &[u8]) -> Result<PseudoRandKey, Error> {
    let ikm = EcdhSecret(ikm.to_vec());
    let prk = hkdf.extract(salt, &ikm)?;
    hkdf.expand(&prk, info)
}

/// Simple test that AES key generation is random.
pub fn test_aes_gcm_keygen<A: AesGcm, R: Rng>(aes: &A, rng: &mut R) {
    let key1 = aes.generate_key(rng).unwrap();
    let key2 = aes.generate_key(rng).unwrap();
    assert_ne!(key1.0, key2.0, "identical generated AES keys!");
}

/// Test basic AES-GCM round-trip functionality.
pub fn test_aes_gcm_roundtrip<A: AesGcm, R: Rng>(aes: &A, rng: &mut R) {
    let key = aes.generate_key(rng).unwrap();
    let msg = b"The Magic Words are Squeamish Ossifrage";
    let aad = b"the aad";
    let nonce = Nonce12(*b"1243567890ab");
    let ct = aes.encrypt(&key, msg, aad, &nonce).unwrap();
    let pt = aes.decrypt(&key, &ct, aad, &nonce).unwrap();
    assert_eq!(pt, msg);

    // Modifying any of the inputs should induce failure.
    let bad_key = aes.generate_key(rng).unwrap();
    let bad_aad = b"the AAD";
    let bad_nonce = Nonce12(*b"ab1243567890");
    let mut bad_ct = ct.clone();
    bad_ct[0] ^= 0x01;

    assert!(aes.decrypt(&bad_key, &ct, aad, &nonce).is_err());
    assert!(aes.decrypt(&key, &bad_ct, aad, &nonce).is_err());
    assert!(aes.decrypt(&key, &ct, bad_aad, &nonce).is_err());
    assert!(aes.decrypt(&key, &ct, aad, &bad_nonce).is_err());
}

/// Test AES-GCM against test vectors.
pub fn test_aes_gcm<A: AesGcm>(aes: &A) {
    struct TestCase {
        key: &'static str,
        iv: &'static str,
        aad: &'static str,
        msg: &'static str,
        ct: &'static str,
        tag: &'static str,
    }

    // Test vectors from Wycheproof aes_gcm_test.json.
    let aes_gcm_tests = [
        TestCase {
            // tcId: 73
            key: "92ace3e348cd821092cd921aa3546374299ab46209691bc28b8752d17f123c20",
            iv: "00112233445566778899aabb",
            aad: "00000000ffffffff",
            msg: "00010203040506070809",
            ct: "e27abdd2d2a53d2f136b",
            tag: "9a4a2579529301bcfb71c78d4060f52c",
        },
        TestCase {
            // tcId: 74
            key: "29d3a44f8723dc640239100c365423a312934ac80239212ac3df3421a2098123",
            iv: "00112233445566778899aabb",
            aad: "aabbccddeeff",
            msg: "",
            ct: "",
            tag: "2a7d77fa526b8250cb296078926b5020",
        },
        TestCase {
            // tcId: 75
            key: "80ba3192c803ce965ea371d5ff073cf0f43b6a2ab576b208426e11409c09b9b0",
            iv: "4da5bf8dfd5852c1ea12379d",
            aad: "",
            msg: "",
            ct: "",
            tag: "4771a7c404a472966cea8f73c8bfe17a",
        },
        TestCase {
            // tcId: 76
            key: "cc56b680552eb75008f5484b4cb803fa5063ebd6eab91f6ab6aef4916a766273",
            iv: "99e23ec48985bccdeeab60f1",
            aad: "",
            msg: "2a",
            ct: "06",
            tag: "633c1e9703ef744ffffb40edf9d14355",
        },
        TestCase {
            // tcId: 77
            key: "51e4bf2bad92b7aff1a4bc05550ba81df4b96fabf41c12c7b00e60e48db7e152",
            iv: "4f07afedfdc3b6c2361823d3",
            aad: "",
            msg: "be3308f72a2c6aed",
            ct: "cf332a12fdee800b",
            tag: "602e8d7c4799d62c140c9bb834876b09",
        },
        TestCase {
            // tcId: 78
            key: "67119627bd988eda906219e08c0d0d779a07d208ce8a4fe0709af755eeec6dcb",
            iv: "68ab7fdbf61901dad461d23c",
            aad: "",
            msg: "51f8c1f731ea14acdb210a6d973e07",
            ct: "43fc101bff4b32bfadd3daf57a590e",
            tag: "ec04aacb7148a8b8be44cb7eaf4efa69",
        },
    ];
    for (i, test) in aes_gcm_tests.iter().enumerate() {
        let key = AesKey(hex::decode(test.key).unwrap().try_into().unwrap());
        let nonce = Nonce12(hex::decode(test.iv).unwrap().try_into().unwrap());
        let aad = hex::decode(test.aad).unwrap();
        let msg = hex::decode(test.msg).unwrap();
        let want_hex = test.ct.to_owned() + test.tag;

        let got = aes.encrypt(&key, &msg, &aad, &nonce).unwrap();
        assert_eq!(hex::encode(&got), want_hex, "incorrect for case {i}");

        let got_pt = aes.decrypt(&key, &got, &aad, &nonce).unwrap();
        assert_eq!(hex::encode(got_pt), test.msg, "incorrect decrypt for case {i}");
    }
}

/// Test `EcDh` impl for ECDH.
pub fn test_ecdh<E: EcDh>(ecdh: &E) {
    let key1 = ecdh.generate_key().unwrap();
    let key2 = ecdh.generate_key().unwrap();
    let secret12 = ecdh.compute_shared_secret(&key1.priv_key, &key2.pub_key).unwrap();
    let secret21 = ecdh.compute_shared_secret(&key2.priv_key, &key1.pub_key).unwrap();
    assert_eq!(secret12.0, secret21.0);
}

/// Test `EcDsa` impl for verify.
pub fn test_ecdsa<E: EcDsa>(ecdsa: &E) {
    let ed25519_key = coset::CoseKeyBuilder::new_okp_key()
        .param(iana::OkpKeyParameter::Crv as i64, Value::from(iana::EllipticCurve::Ed25519 as u64))
        .param(
            iana::OkpKeyParameter::X as i64,
            Value::from(
                hex::decode("7d4d0e7f6153a69b6242b522abbee685fda4420f8834b108c3bdae369ef549fa")
                    .unwrap(),
            ),
        )
        .algorithm(coset::iana::Algorithm::EdDSA)
        .build();
    let p256_key = CoseKeyBuilder::new_ec2_pub_key(
        iana::EllipticCurve::P_256,
        hex::decode("2927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838").unwrap(),
        hex::decode("c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e").unwrap(),
    )
    .algorithm(iana::Algorithm::ES256)
    .build();
    let p384_key = CoseKeyBuilder::new_ec2_pub_key(
        iana::EllipticCurve::P_384,
        hex::decode("2da57dda1089276a543f9ffdac0bff0d976cad71eb7280e7d9bfd9fee4bdb2f20f47ff888274389772d98cc5752138aa").unwrap(),
        hex::decode("4b6d054d69dcf3e25ec49df870715e34883b1836197d76f8ad962e78f6571bbc7407b0d6091f9e4d88f014274406174f").unwrap(),
    )
    .algorithm(iana::Algorithm::ES384)
    .build();

    struct TestCase {
        key: EcVerifyKey,
        msg: &'static str, // hex
        sig: &'static str, // hex
    }
    let tests = [
        // Wycheproof: eddsa_test.json tcId=5
        TestCase {
            key: EcVerifyKey::Ed25519(ed25519_key),
            msg: "313233343030",
            sig: "657c1492402ab5ce03e2c3a7f0384d051b9cf3570f1207fc78c1bcc98c281c2bf0cf5b3a289976458a1be6277a5055545253b45b07dcc1abd96c8b989c00f301",
        },
        // Wycheproof: ecdsa_secp256r1_sha256_test.json tcId=3
        TestCase {
            key: EcVerifyKey::P256(p256_key),
            msg: "313233343030",
            sig: "304502202ba3a8be6b94d5ec80a6d9d1190a436effe50d85a1eee859b8cc6af9bd5c2e18022100b329f479a2bbd0a5c384ee1493b1f5186a87139cac5df4087c134b49156847db",
        },
        // Wycheproof: ecdsa_secp384r1_sha384_test.json tcId=3
        TestCase {
            key: EcVerifyKey::P384(p384_key),
            msg: "313233343030",
            sig: "3065023012b30abef6b5476fe6b612ae557c0425661e26b44b1bfe19daf2ca28e3113083ba8e4ae4cc45a0320abd3394f1c548d7023100e7bf25603e2d07076ff30b7a2abec473da8b11c572b35fc631991d5de62ddca7525aaba89325dfd04fecc47bff426f82",
        },
    ];

    for (i, test) in tests.iter().enumerate() {
        let sig = hex::decode(test.sig).unwrap();
        let msg = hex::decode(test.msg).unwrap();

        assert!(ecdsa.verify_signature(&test.key, &msg, &sig).is_ok(), "failed for case {i}");

        // A modified message should not verify.
        let mut bad_msg = msg.clone();
        bad_msg[0] ^= 0x01;
        assert!(
            ecdsa.verify_signature(&test.key, &bad_msg, &sig).is_err(),
            "unexpected success for case {i}"
        );

        // A modified signature should not verify.
        let mut bad_sig = sig;
        bad_sig[0] ^= 0x01;
        assert!(
            ecdsa.verify_signature(&test.key, &msg, &bad_sig).is_err(),
            "unexpected success for case {i}"
        );
    }
}

/// Test EdDSA signing and verification for Ed25519.
pub fn test_ed25519_round_trip<E: EcDsa>(ecdsa: &E) {
    // Wycheproof: eddsa_test.json
    let ed25519_pub_key = coset::CoseKeyBuilder::new_okp_key()
        .param(iana::OkpKeyParameter::Crv as i64, Value::from(iana::EllipticCurve::Ed25519 as u64))
        .param(
            iana::OkpKeyParameter::X as i64,
            Value::from(
                hex::decode("7d4d0e7f6153a69b6242b522abbee685fda4420f8834b108c3bdae369ef549fa")
                    .unwrap(),
            ),
        )
        .algorithm(coset::iana::Algorithm::EdDSA)
        .build();
    let ed25519_verify_key = EcVerifyKey::Ed25519(ed25519_pub_key);
    let ed25519_sign_key = EcSignKey::Ed25519(
        hex::decode("add4bb8103785baf9ac534258e8aaf65f5f1adb5ef5f3df19bb80ab989c4d64b")
            .unwrap()
            .try_into()
            .unwrap(),
    );
    test_ecdsa_round_trip(ecdsa, &ed25519_verify_key, &ed25519_sign_key)
}

// It's not possible to include a generic test for `EcDsa::sign` with NIST curves because the
// format of the `EcSignKey` is implementation-dependent.  The following tests are therefore
// specific to implementations (such as the reference implementation) which store private key
// material for NIST EC curves in the form of DER-encoded `ECPrivateKey` structures.

/// Test EdDSA signing and verification for P-256.
pub fn test_p256_round_trip<E: EcDsa>(ecdsa: &E) {
    // Generated with: openssl ecparam --name prime256v1 -genkey -noout -out p256-privkey.pem
    //
    // Contents (der2ascii -pem -i p256-privkey.pem):
    //
    // SEQUENCE {
    //   INTEGER { 1 }
    //   OCTET_STRING { `0733c93e22240ba783739f9e2bd4b4065bfcecac9268362587dc814da5b84080` }
    //   [0] {
    //     # secp256r1
    //     OBJECT_IDENTIFIER { 1.2.840.10045.3.1.7 }
    //   }
    //   [1] {
    //     BIT_STRING { `00` `04`
    //                  `2b31afcfab1aba1f8850d7ecfa235e14d60a1ef5b2a75b93ccaa4322de094477`
    //                  `21ba560a040bab8c922edd32a279e9d3ac991f1507d4b4beded5fd80298b7cee`
    //     }
    //   }
    // }
    let p256_priv_key = hex::decode("307702010104200733c93e22240ba783739f9e2bd4b4065bfcecac9268362587dc814da5b84080a00a06082a8648ce3d030107a144034200042b31afcfab1aba1f8850d7ecfa235e14d60a1ef5b2a75b93ccaa4322de09447721ba560a040bab8c922edd32a279e9d3ac991f1507d4b4beded5fd80298b7cee").unwrap();
    let p256_pub_key = CoseKeyBuilder::new_ec2_pub_key(
        iana::EllipticCurve::P_256,
        hex::decode("2b31afcfab1aba1f8850d7ecfa235e14d60a1ef5b2a75b93ccaa4322de094477").unwrap(),
        hex::decode("21ba560a040bab8c922edd32a279e9d3ac991f1507d4b4beded5fd80298b7cee").unwrap(),
    )
    .algorithm(iana::Algorithm::ES256)
    .build();

    test_ecdsa_round_trip(ecdsa, &EcVerifyKey::P256(p256_pub_key), &EcSignKey::P256(p256_priv_key))
}

/// Test EdDSA signing and verification for P-384.
pub fn test_p384_round_trip<E: EcDsa>(ecdsa: &E) {
    // Generated with: openssl ecparam --name secp384r1 -genkey -noout -out p384-privkey.pem
    //
    // Contents (der2ascii -pem -i p384-privkey.pem):
    //
    // SEQUENCE {
    //   INTEGER { 1 }
    //   OCTET_STRING { `81a9d9e43e47dbbf3e7e4e9e06d467b1b126603969bf80f0ade1e1aea9ed534884b81d86ece0bbd41d541bf6d22f6be2` }
    //   [0] {
    //     # secp384r1
    //     OBJECT_IDENTIFIER { 1.3.132.0.34 }
    //   }
    //   [1] {
    //     BIT_STRING { `00` `04`
    //                  `fdf3f076a6e98047baf68a44d319f0200a03c4807eb0e869db88e1c9758ba96647fecbe0456c475feeb67021e053de93`
    //                  `478ad58e972d52af0ea5911fe24f82448e9c073263aaa49117c451e787eced645796e50b24ee2c632a6c77e6d430ad01`
    //     }
    //   }
    // }
    let p384_priv_key = hex::decode("3081a4020101043081a9d9e43e47dbbf3e7e4e9e06d467b1b126603969bf80f0ade1e1aea9ed534884b81d86ece0bbd41d541bf6d22f6be2a00706052b81040022a16403620004fdf3f076a6e98047baf68a44d319f0200a03c4807eb0e869db88e1c9758ba96647fecbe0456c475feeb67021e053de93478ad58e972d52af0ea5911fe24f82448e9c073263aaa49117c451e787eced645796e50b24ee2c632a6c77e6d430ad01").unwrap();
    let p384_pub_key = CoseKeyBuilder::new_ec2_pub_key(
        iana::EllipticCurve::P_384,
        hex::decode("fdf3f076a6e98047baf68a44d319f0200a03c4807eb0e869db88e1c9758ba96647fecbe0456c475feeb67021e053de93").unwrap(),
        hex::decode("478ad58e972d52af0ea5911fe24f82448e9c073263aaa49117c451e787eced645796e50b24ee2c632a6c77e6d430ad01").unwrap(),
    )
    .algorithm(iana::Algorithm::ES384)
        .build();

    test_ecdsa_round_trip(ecdsa, &EcVerifyKey::P384(p384_pub_key), &EcSignKey::P384(p384_priv_key))
}

fn test_ecdsa_round_trip<E: EcDsa>(ecdsa: &E, verify_key: &EcVerifyKey, sign_key: &EcSignKey) {
    let msg = b"This is the message";
    let sig = ecdsa.sign(sign_key, msg).unwrap();

    assert!(ecdsa.verify_signature(verify_key, msg, &sig).is_ok());

    // A modified message should not verify.
    let mut bad_msg = *msg;
    bad_msg[0] ^= 0x01;
    assert!(ecdsa.verify_signature(verify_key, &bad_msg, &sig).is_err());

    // A modified signature should not verify.
    let mut bad_sig = sig;
    bad_sig[0] ^= 0x01;
    assert!(ecdsa.verify_signature(verify_key, msg, &bad_sig).is_err());
}

/// Test `create` method of key exchange protocol
pub fn test_key_exchange_create(source: &mut keyexchange::AuthGraphParticipant) {
    let create_result = source.create();
    assert!(create_result.is_ok());

    // TODO: Add more tests on the values returned from `create` (some of these tests may
    // need to be done in `libauthgraph_boringssl_test`)
    // 1. dh_key is not None,
    // 2. dh_key->pub key is in CoseKey encoding (e..g purpose)
    // 3. dh_key->priv_key arc can be decrypted from the pbk from the AgDevice, the IV attached
    //    in the unprotected headers, nonce for key exchange and the payload type = SecretKey
    //    attached in the protected headers
    // 5. identity decodes to a CBOR vector and the second element is a bstr of
    //    CoseKey
    // 6. nonce is same as the nonce attached in the protected header of the arc in
    //    #3 above
    // 7. ECDH can be performed from the dh_key returned from this method
}

/// Test `init` method of key exchange protocol
pub fn test_key_exchange_init(
    source: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: peer_ke_pub_key, .. },
        identity: peer_identity,
        nonce: peer_nonce,
        version: peer_version,
    } = source.create().unwrap();

    let init_result =
        sink.init(&peer_ke_pub_key.unwrap(), &peer_identity, &peer_nonce, peer_version);
    assert!(init_result.is_ok())
    // TODO: add more tests on init_result
}

/// Test `finish` method of key exchange protocol
pub fn test_key_exchange_finish(
    source: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: p1_version,
    } = source.create().unwrap();

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info: keyexchange::SessionInfo { session_id_signature: p2_signature, .. },
    } = sink.init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version).unwrap();

    let finish_result = source.finish(
        &p2_ke_pub_key.unwrap(),
        &p2_identity,
        &p2_signature,
        &p2_nonce,
        p2_version,
        Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
    );
    assert!(finish_result.is_ok())
    // TODO: add more tests on finish_result
}

/// Test `authentication_complete` method of key exchange protocol
pub fn test_key_exchange_auth_complete(
    source: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: p1_version,
    } = source.create().unwrap();

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info:
            keyexchange::SessionInfo {
                shared_keys: p2_shared_keys,
                session_id: p2_session_id,
                session_id_signature: p2_signature,
            },
    } = sink.init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version).unwrap();

    let keyexchange::SessionInfo {
        shared_keys: _p1_shared_keys,
        session_id: p1_session_id,
        session_id_signature: p1_signature,
    } = source
        .finish(
            &p2_ke_pub_key.unwrap(),
            &p2_identity,
            &p2_signature,
            &p2_nonce,
            p2_version,
            Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        )
        .unwrap();

    let auth_complete_result = sink.authentication_complete(&p1_signature, p2_shared_keys);
    assert!(auth_complete_result.is_ok());
    assert_eq!(p1_session_id, p2_session_id)
    // TODO: add more tests on finish_result, and encrypt/decrypt using the agreed keys
}

/// Verify that the key exchange protocol works when source's version is higher than sink's version
/// and that the negotiated version is sink's version
pub fn test_ke_with_newer_source(
    source_newer: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    let source_version = source_newer.get_version();
    let sink_version = sink.get_version();
    assert!(source_version > sink_version);

    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: p1_version,
    } = source_newer.create().unwrap();

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info:
            keyexchange::SessionInfo {
                shared_keys: p2_shared_keys,
                session_id: p2_session_id,
                session_id_signature: p2_signature,
            },
    } = sink.init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version).unwrap();
    assert_eq!(p2_version, sink_version);

    let keyexchange::SessionInfo {
        shared_keys: _p1_shared_keys,
        session_id: p1_session_id,
        session_id_signature: p1_signature,
    } = source_newer
        .finish(
            &p2_ke_pub_key.unwrap(),
            &p2_identity,
            &p2_signature,
            &p2_nonce,
            p2_version,
            Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        )
        .unwrap();

    let auth_complete_result = sink.authentication_complete(&p1_signature, p2_shared_keys);
    assert!(auth_complete_result.is_ok());
    assert_eq!(p1_session_id, p2_session_id)
}

/// Verify that the key exchange protocol works when sink's version is higher than sources's version
/// and that the negotiated version is source's version
pub fn test_ke_with_newer_sink(
    source: &mut keyexchange::AuthGraphParticipant,
    sink_newer: &mut keyexchange::AuthGraphParticipant,
) {
    let source_version = source.get_version();
    let sink_version = sink_newer.get_version();
    assert!(sink_version > source_version);

    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: p1_version,
    } = source.create().unwrap();

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info:
            keyexchange::SessionInfo {
                shared_keys: p2_shared_keys,
                session_id: p2_session_id,
                session_id_signature: p2_signature,
            },
    } = sink_newer
        .init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version)
        .unwrap();
    assert_eq!(p2_version, source_version);

    let keyexchange::SessionInfo {
        shared_keys: _p1_shared_keys,
        session_id: p1_session_id,
        session_id_signature: p1_signature,
    } = source
        .finish(
            &p2_ke_pub_key.unwrap(),
            &p2_identity,
            &p2_signature,
            &p2_nonce,
            p2_version,
            Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        )
        .unwrap();

    let auth_complete_result = sink_newer.authentication_complete(&p1_signature, p2_shared_keys);
    assert!(auth_complete_result.is_ok());
    assert_eq!(p1_session_id, p2_session_id)
}

/// Verify that the key exchange protocol prevents version downgrade attacks when both source and
/// sink have versions newer than version 1
pub fn test_ke_for_version_downgrade(
    source: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    let source_version = source.get_version();
    let sink_version = sink.get_version();
    assert!(source_version > 1);
    assert!(sink_version > 1);

    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: _p1_version,
    } = source.create().unwrap();

    let downgraded_version = 1;

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info:
            keyexchange::SessionInfo {
                shared_keys: _p2_shared_keys,
                session_id: _p2_session_id,
                session_id_signature: p2_signature,
            },
    } = sink
        .init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, downgraded_version)
        .unwrap();
    assert_eq!(p2_version, downgraded_version);

    let finish_result = source.finish(
        &p2_ke_pub_key.unwrap(),
        &p2_identity,
        &p2_signature,
        &p2_nonce,
        p2_version,
        Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
    );
    // `finish` should fail with signature verification error
    match finish_result {
        Ok(_) => panic!("protocol downgrade prevention is broken"),
        Err(e) => match e {
            Error(ErrorCode::InvalidSignature, _) => {}
            _ => panic!("wrong error on protocol downgrade"),
        },
    }
}

/// Verify that the key exchange protocol prevents replay attacks
pub fn test_ke_for_replay(
    source: &mut keyexchange::AuthGraphParticipant,
    sink: &mut keyexchange::AuthGraphParticipant,
) {
    // Round 1 of the protocol
    let keyexchange::SessionInitiationInfo {
        ke_key: Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
        identity: p1_identity,
        nonce: p1_nonce,
        version: p1_version,
    } = source.create().unwrap();

    let keyexchange::KeInitResult {
        session_init_info:
            keyexchange::SessionInitiationInfo {
                ke_key: Key { pub_key: p2_ke_pub_key, .. },
                identity: p2_identity,
                nonce: p2_nonce,
                version: p2_version,
            },
        session_info:
            keyexchange::SessionInfo {
                shared_keys: p2_shared_keys,
                session_id: p2_session_id,
                session_id_signature: p2_signature,
            },
    } = sink.init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version).unwrap();

    let keyexchange::SessionInfo {
        shared_keys: _p1_shared_keys,
        session_id: p1_session_id,
        session_id_signature: p1_signature,
    } = source
        .finish(
            &p2_ke_pub_key.clone().unwrap(),
            &p2_identity,
            &p2_signature,
            &p2_nonce,
            p2_version,
            Key { pub_key: p1_ke_pub_key.clone(), arc_from_pbk: p1_ke_priv_key_arc.clone() },
        )
        .unwrap();

    let auth_complete_result = sink.authentication_complete(&p1_signature, p2_shared_keys.clone());
    assert!(auth_complete_result.is_ok());
    assert_eq!(p1_session_id, p2_session_id);

    // An attacker may try to run the key exchange protocol again, but this time, they try to
    // replay the inputs of the previous protocol run, ignoring the outputs of `create` and `init`
    // of the existing protocol run. In such cases, `finish` and `authentication_complete` should
    // fail as per the measures against replay attacks.
    source.create().unwrap();

    sink.init(p1_ke_pub_key.as_ref().unwrap(), &p1_identity, &p1_nonce, p1_version).unwrap();

    let finish_result = source.finish(
        &p2_ke_pub_key.unwrap(),
        &p2_identity,
        &p2_signature,
        &p2_nonce,
        p2_version,
        Key { pub_key: p1_ke_pub_key, arc_from_pbk: p1_ke_priv_key_arc },
    );
    match finish_result {
        Ok(_) => panic!("replay prevention is broken in finish"),
        Err(e) if e.0 == ErrorCode::InvalidKeKey => {}
        Err(e) => panic!("got error {e:?}, wanted ErrorCode::InvalidKeKey"),
    }

    let auth_complete_result = sink.authentication_complete(&p1_signature, p2_shared_keys);
    match auth_complete_result {
        Ok(_) => panic!("replay prevention is broken in authentication_complete"),
        Err(e) if e.0 == ErrorCode::InvalidSharedKeyArcs => {}
        Err(e) => panic!("got error {e:?}, wanted ErrorCode::InvalidSharedKeyArcs"),
    }
}

/// Test the logic of decoding and validating `Identity` using the test data created with open-dice.
pub fn validate_identity<E: EcDsa>(ecdsa: &E) {
    for cert_chain_len in 0..6 {
        let (pvt_sign_key, identity) =
            create_identity(cert_chain_len).expect("error in creating identity");
        // decode identity
        let decoded_identity =
            Identity::from_slice(&identity).expect("error in decoding the identity");
        // validate identity
        let verify_key =
            decoded_identity.validate(ecdsa).expect("error in validating the identity");
        // re-encode the decoded identity
        decoded_identity.to_vec().expect("failed to serialize Identity");

        // sign using the private signing key derived from the leaf CDI attest
        let data = b"test string to sign";

        let protected =
            HeaderBuilder::new().algorithm(verify_key.get_cose_sign_algorithm()).build();
        let cose_sign1 = CoseSign1Builder::new()
            .protected(protected)
            .payload(data.to_vec())
            .try_create_signature(&[], |input| ecdsa.sign(&pvt_sign_key, input))
            .expect("error creating the signature")
            .build();
        // verify the signature with the public signing key extracted from the leaf certificate in
        // the DICE chain
        cose_sign1
            .verify_signature(&[], |signature, data| {
                ecdsa.verify_signature(&verify_key, data, signature)
            })
            .expect("error in verifying the signature");
    }
}

/// Construct a CBOR serialized `Identity` with a `CertChain` containing the given number of
/// certificate entries, using open-dice. The maximum length supported by this method is 5.
/// Return the private signing key corresponding to the public signing key.
pub fn create_identity(dice_chain_len: usize) -> Result<(EcSignKey, Vec<u8>), Error> {
    const UDS: [u8; diced_open_dice::CDI_SIZE] = [
        0x1d, 0xa5, 0xea, 0x90, 0x47, 0xfc, 0xb5, 0xf6, 0x47, 0x12, 0xd3, 0x65, 0x9c, 0xf2, 0x00,
        0xe0, 0x06, 0xf7, 0xe8, 0x9e, 0x2f, 0xd0, 0x94, 0x7f, 0xc9, 0x9a, 0x9d, 0x40, 0xf7, 0xce,
        0x13, 0x21,
    ];
    let pvt_key_seed = diced_open_dice::derive_cdi_private_key_seed(&UDS).unwrap();
    let (root_pub_key, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
        .expect("failed to create key pair from seed.");
    let root_pub_cose_key = CoseKey {
        kty: KeyType::Assigned(iana::KeyType::OKP),
        alg: Some(Algorithm::Assigned(iana::Algorithm::EdDSA)),
        key_ops: vec![KeyOperation::Assigned(iana::KeyOperation::Verify)].into_iter().collect(),
        params: vec![
            (
                Label::Int(iana::Ec2KeyParameter::Crv.to_i64()),
                iana::EllipticCurve::Ed25519.to_i64().into(),
            ),
            (Label::Int(iana::Ec2KeyParameter::X.to_i64()), Value::Bytes(root_pub_key.to_vec())),
        ],
        ..Default::default()
    };
    let root_pub_cose_key_bstr =
        root_pub_cose_key.to_vec().expect("failed to serialize root pub key");

    if dice_chain_len == 0 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }

    const CODE_HASH_PVMFW: [u8; diced_open_dice::HASH_SIZE] = [
        0x16, 0x48, 0xf2, 0x55, 0x53, 0x23, 0xdd, 0x15, 0x2e, 0x83, 0x38, 0xc3, 0x64, 0x38, 0x63,
        0x26, 0x0f, 0xcf, 0x5b, 0xd1, 0x3a, 0xd3, 0x40, 0x3e, 0x23, 0xf8, 0x34, 0x4c, 0x6d, 0xa2,
        0xbe, 0x25, 0x1c, 0xb0, 0x29, 0xe8, 0xc3, 0xfb, 0xb8, 0x80, 0xdc, 0xb1, 0xd2, 0xb3, 0x91,
        0x4d, 0xd3, 0xfb, 0x01, 0x0f, 0xe4, 0xe9, 0x46, 0xa2, 0xc0, 0x26, 0x57, 0x5a, 0xba, 0x30,
        0xf7, 0x15, 0x98, 0x14,
    ];
    const AUTHORITY_HASH_PVMFW: [u8; diced_open_dice::HASH_SIZE] = [
        0xf9, 0x00, 0x9d, 0xc2, 0x59, 0x09, 0xe0, 0xb6, 0x98, 0xbd, 0xe3, 0x97, 0x4a, 0xcb, 0x3c,
        0xe7, 0x6b, 0x24, 0xc3, 0xe4, 0x98, 0xdd, 0xa9, 0x6a, 0x41, 0x59, 0x15, 0xb1, 0x23, 0xe6,
        0xc8, 0xdf, 0xfb, 0x52, 0xb4, 0x52, 0xc1, 0xb9, 0x61, 0xdd, 0xbc, 0x5b, 0x37, 0x0e, 0x12,
        0x12, 0xb2, 0xfd, 0xc1, 0x09, 0xb0, 0xcf, 0x33, 0x81, 0x4c, 0xc6, 0x29, 0x1b, 0x99, 0xea,
        0xae, 0xfd, 0xaa, 0x0d,
    ];
    const HIDDEN_PVMFW: [u8; diced_open_dice::HIDDEN_SIZE] = [
        0xa2, 0x01, 0xd0, 0xc0, 0xaa, 0x75, 0x3c, 0x06, 0x43, 0x98, 0x6c, 0xc3, 0x5a, 0xb5, 0x5f,
        0x1f, 0x0f, 0x92, 0x44, 0x3b, 0x0e, 0xd4, 0x29, 0x75, 0xe3, 0xdb, 0x36, 0xda, 0xc8, 0x07,
        0x97, 0x4d, 0xff, 0xbc, 0x6a, 0xa4, 0x8a, 0xef, 0xc4, 0x7f, 0xf8, 0x61, 0x7d, 0x51, 0x4d,
        0x2f, 0xdf, 0x7e, 0x8c, 0x3d, 0xa3, 0xfc, 0x63, 0xd4, 0xd4, 0x74, 0x8a, 0xc4, 0x14, 0x45,
        0x83, 0x6b, 0x12, 0x7e,
    ];
    let comp_name_1 = CString::new("Protected VM firmware").expect("CString::new failed");
    let config_values_1 = diced_open_dice::DiceConfigValues {
        component_name: Some(&comp_name_1),
        component_version: Some(1),
        resettable: true,
        ..Default::default()
    };
    let config_descriptor_1 = diced_open_dice::retry_bcc_format_config_descriptor(&config_values_1)
        .expect("failed to format config descriptor");
    let input_values_1 = diced_open_dice::InputValues::new(
        CODE_HASH_PVMFW,
        diced_open_dice::Config::Descriptor(config_descriptor_1.as_slice()),
        AUTHORITY_HASH_PVMFW,
        diced_open_dice::DiceMode::kDiceModeDebug,
        HIDDEN_PVMFW,
    );
    let (cdi_values_1, cert_1) = diced_open_dice::retry_dice_main_flow(&UDS, &UDS, &input_values_1)
        .expect("Failed to run first main flow");

    if dice_chain_len == 1 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
            Value::from_slice(&cert_1).expect("failed to deserialize the certificate into CBOR"),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key_seed =
            diced_open_dice::derive_cdi_private_key_seed(&cdi_values_1.cdi_attest).unwrap();
        let (_, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
            .expect("failed to create key pair from seed.");
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }

    const CODE_HASH_SERVICE_VM: [u8; diced_open_dice::HASH_SIZE] = [
        0xa4, 0x0c, 0xcb, 0xc1, 0xbf, 0xfa, 0xcc, 0xfd, 0xeb, 0xf4, 0xfc, 0x43, 0x83, 0x7f, 0x46,
        0x8d, 0xd8, 0xd8, 0x14, 0xc1, 0x96, 0x14, 0x1f, 0x6e, 0xb3, 0xa0, 0xd9, 0x56, 0xb3, 0xbf,
        0x2f, 0xfa, 0x88, 0x70, 0x11, 0x07, 0x39, 0xa4, 0xd2, 0xa9, 0x6b, 0x18, 0x28, 0xe8, 0x29,
        0x20, 0x49, 0x0f, 0xbb, 0x8d, 0x08, 0x8c, 0xc6, 0x54, 0xe9, 0x71, 0xd2, 0x7e, 0xa4, 0xfe,
        0x58, 0x7f, 0xd3, 0xc7,
    ];
    const AUTHORITY_HASH_SERVICE_VM: [u8; diced_open_dice::HASH_SIZE] = [
        0xb2, 0x69, 0x05, 0x48, 0x56, 0xb5, 0xfa, 0x55, 0x6f, 0xac, 0x56, 0xd9, 0x02, 0x35, 0x2b,
        0xaa, 0x4c, 0xba, 0x28, 0xdd, 0x82, 0x3a, 0x86, 0xf5, 0xd4, 0xc2, 0xf1, 0xf9, 0x35, 0x7d,
        0xe4, 0x43, 0x13, 0xbf, 0xfe, 0xd3, 0x36, 0xd8, 0x1c, 0x12, 0x78, 0x5c, 0x9c, 0x3e, 0xf6,
        0x66, 0xef, 0xab, 0x3d, 0x0f, 0x89, 0xa4, 0x6f, 0xc9, 0x72, 0xee, 0x73, 0x43, 0x02, 0x8a,
        0xef, 0xbc, 0x05, 0x98,
    ];
    const HIDDEN_SERVICE_VM: [u8; diced_open_dice::HIDDEN_SIZE] = [
        0x5b, 0x3f, 0xc9, 0x6b, 0xe3, 0x95, 0x59, 0x40, 0x5e, 0x64, 0xe5, 0x64, 0x3f, 0xfd, 0x21,
        0x09, 0x9d, 0xf3, 0xcd, 0xc7, 0xa4, 0x2a, 0xe2, 0x97, 0xdd, 0xe2, 0x4f, 0xb0, 0x7d, 0x7e,
        0xf5, 0x8e, 0xd6, 0x4d, 0x84, 0x25, 0x54, 0x41, 0x3f, 0x8f, 0x78, 0x64, 0x1a, 0x51, 0x27,
        0x9d, 0x55, 0x8a, 0xe9, 0x90, 0x35, 0xab, 0x39, 0x80, 0x4b, 0x94, 0x40, 0x84, 0xa2, 0xfd,
        0x73, 0xeb, 0x35, 0x7a,
    ];

    let comp_name_2 = CString::new("VM entry").expect("CString::new failed");
    let config_values_2 = diced_open_dice::DiceConfigValues {
        component_name: Some(&comp_name_2),
        component_version: Some(12),
        resettable: true,
        ..Default::default()
    };
    let config_descriptor_2 = diced_open_dice::retry_bcc_format_config_descriptor(&config_values_2)
        .expect("failed to format config descriptor");

    let input_values_2 = diced_open_dice::InputValues::new(
        CODE_HASH_SERVICE_VM,
        diced_open_dice::Config::Descriptor(config_descriptor_2.as_slice()),
        AUTHORITY_HASH_SERVICE_VM,
        diced_open_dice::DiceMode::kDiceModeDebug,
        HIDDEN_SERVICE_VM,
    );

    let (cdi_values_2, cert_2) = diced_open_dice::retry_dice_main_flow(
        &cdi_values_1.cdi_attest,
        &cdi_values_1.cdi_seal,
        &input_values_2,
    )
    .expect("Failed to run first main flow");

    if dice_chain_len == 2 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
            Value::from_slice(&cert_1).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_2).expect("failed to deserialize the certificate into CBOR"),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key_seed =
            diced_open_dice::derive_cdi_private_key_seed(&cdi_values_2.cdi_attest).unwrap();
        let (_, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
            .expect("failed to create key pair from seed.");
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }

    const CODE_HASH_PAYLOAD: [u8; diced_open_dice::HASH_SIZE] = [
        0x08, 0x78, 0xc2, 0x5b, 0xe7, 0xea, 0x3d, 0x62, 0x70, 0x22, 0xd9, 0x1c, 0x4f, 0x3c, 0x2e,
        0x2f, 0x0f, 0x97, 0xa4, 0x6f, 0x6d, 0xd5, 0xe6, 0x4a, 0x6d, 0xbe, 0x34, 0x2e, 0x56, 0x04,
        0xaf, 0xef, 0x74, 0x3f, 0xec, 0xb8, 0x44, 0x11, 0xf4, 0x2f, 0x05, 0xb2, 0x06, 0xa3, 0x0e,
        0x75, 0xb7, 0x40, 0x9a, 0x4c, 0x58, 0xab, 0x96, 0xe7, 0x07, 0x97, 0x07, 0x86, 0x5c, 0xa1,
        0x42, 0x12, 0xf0, 0x34,
    ];
    const AUTHORITY_HASH_PAYLOAD: [u8; diced_open_dice::HASH_SIZE] = [
        0xc7, 0x97, 0x5b, 0xa9, 0x9e, 0xbf, 0x0b, 0xeb, 0xe7, 0x7f, 0x69, 0x8f, 0x8e, 0xcf, 0x04,
        0x7d, 0x2c, 0x0f, 0x4d, 0xbe, 0xcb, 0xf5, 0xf1, 0x4c, 0x1d, 0x1c, 0xb7, 0x44, 0xdf, 0xf8,
        0x40, 0x90, 0x09, 0x65, 0xab, 0x01, 0x34, 0x3e, 0xc2, 0xc4, 0xf7, 0xa2, 0x3a, 0x5c, 0x4e,
        0x76, 0x4f, 0x42, 0xa8, 0x6c, 0xc9, 0xf1, 0x7b, 0x12, 0x80, 0xa4, 0xef, 0xa2, 0x4d, 0x72,
        0xa1, 0x21, 0xe2, 0x47,
    ];

    let comp_name_3 = CString::new("Payload").expect("CString::new failed");
    let config_values_3 = diced_open_dice::DiceConfigValues {
        component_name: Some(&comp_name_3),
        component_version: Some(12),
        resettable: true,
        ..Default::default()
    };
    let config_descriptor_3 = diced_open_dice::retry_bcc_format_config_descriptor(&config_values_3)
        .expect("failed to format config descriptor");

    let input_values_3 = diced_open_dice::InputValues::new(
        CODE_HASH_PAYLOAD,
        diced_open_dice::Config::Descriptor(config_descriptor_3.as_slice()),
        AUTHORITY_HASH_PAYLOAD,
        diced_open_dice::DiceMode::kDiceModeDebug,
        [0u8; diced_open_dice::HIDDEN_SIZE],
    );

    let (cdi_values_3, cert_3) = diced_open_dice::retry_dice_main_flow(
        &cdi_values_2.cdi_attest,
        &cdi_values_2.cdi_seal,
        &input_values_3,
    )
    .expect("Failed to run first main flow");

    if dice_chain_len == 3 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
            Value::from_slice(&cert_1).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_2).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_3).expect("failed to deserialize the certificate into CBOR"),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key_seed =
            diced_open_dice::derive_cdi_private_key_seed(&cdi_values_3.cdi_attest).unwrap();
        let (_, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
            .expect("failed to create key pair from seed.");
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }

    const CODE_HASH_APK1: [u8; diced_open_dice::HASH_SIZE] = [
        0x41, 0x92, 0x0d, 0xd0, 0xf5, 0x60, 0xe3, 0x69, 0x26, 0x7f, 0xb8, 0xbc, 0x12, 0x3a, 0xd1,
        0x95, 0x1d, 0xb8, 0x9a, 0x9c, 0x3a, 0x3f, 0x01, 0xbf, 0xa8, 0xd9, 0x6d, 0xe9, 0x90, 0x30,
        0x1d, 0x0b, 0xaf, 0xef, 0x74, 0x3f, 0xec, 0xb8, 0x44, 0x11, 0xf4, 0x2f, 0x05, 0xb2, 0x06,
        0xa3, 0x0e, 0x75, 0xb7, 0x40, 0x9a, 0x4c, 0x58, 0xab, 0x96, 0xe7, 0x07, 0x97, 0x07, 0x86,
        0x5c, 0xa1, 0x42, 0x12,
    ];
    const AUTHORITY_HASH_APK1: [u8; diced_open_dice::HASH_SIZE] = [
        0xe3, 0xd9, 0x1c, 0xf5, 0x6f, 0xee, 0x73, 0x40, 0x3d, 0x95, 0x59, 0x67, 0xea, 0x5d, 0x01,
        0xfd, 0x25, 0x9d, 0x5c, 0x88, 0x94, 0x3a, 0xc6, 0xd7, 0xa9, 0xdc, 0x4c, 0x60, 0x81, 0xbe,
        0x2b, 0x74, 0x40, 0x90, 0x09, 0x65, 0xab, 0x01, 0x34, 0x3e, 0xc2, 0xc4, 0xf7, 0xa2, 0x3a,
        0x5c, 0x4e, 0x76, 0x4f, 0x42, 0xa8, 0x6c, 0xc9, 0xf1, 0x7b, 0x12, 0x80, 0xa4, 0xef, 0xa2,
        0x4d, 0x72, 0xa1, 0x21,
    ];

    let comp_name_4 = CString::new("APK1").expect("CString::new failed");
    let config_values_4 = diced_open_dice::DiceConfigValues {
        component_name: Some(&comp_name_4),
        component_version: Some(12),
        resettable: true,
        ..Default::default()
    };
    let config_descriptor_4 = diced_open_dice::retry_bcc_format_config_descriptor(&config_values_4)
        .expect("failed to format config descriptor");

    let input_values_4 = diced_open_dice::InputValues::new(
        CODE_HASH_APK1,
        diced_open_dice::Config::Descriptor(config_descriptor_4.as_slice()),
        AUTHORITY_HASH_APK1,
        diced_open_dice::DiceMode::kDiceModeDebug,
        [0u8; diced_open_dice::HIDDEN_SIZE],
    );

    let (cdi_values_4, cert_4) = diced_open_dice::retry_dice_main_flow(
        &cdi_values_3.cdi_attest,
        &cdi_values_3.cdi_seal,
        &input_values_4,
    )
    .expect("Failed to run first main flow");

    if dice_chain_len == 4 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
            Value::from_slice(&cert_1).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_2).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_3).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_4).expect("failed to deserialize the certificate into CBOR"),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key_seed =
            diced_open_dice::derive_cdi_private_key_seed(&cdi_values_4.cdi_attest).unwrap();
        let (_, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
            .expect("failed to create key pair from seed.");
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }

    const CODE_HASH_APEX1: [u8; diced_open_dice::HASH_SIZE] = [
        0x52, 0x93, 0x2b, 0xb0, 0x8d, 0xec, 0xdf, 0x54, 0x1f, 0x5c, 0x10, 0x9d, 0x17, 0xce, 0x7f,
        0xac, 0xb0, 0x2b, 0xe2, 0x99, 0x05, 0x7d, 0xa3, 0x9b, 0xa6, 0x3e, 0xf9, 0x99, 0xa2, 0xea,
        0xd4, 0xd9, 0x1d, 0x0b, 0xaf, 0xef, 0x74, 0x3f, 0xec, 0xb8, 0x44, 0x11, 0xf4, 0x2f, 0x05,
        0xb2, 0x06, 0xa3, 0x0e, 0x75, 0xb7, 0x40, 0x9a, 0x4c, 0x58, 0xab, 0x96, 0xe7, 0x07, 0x97,
        0x07, 0x86, 0x5c, 0xa1,
    ];
    const AUTHORITY_HASH_APEX1: [u8; diced_open_dice::HASH_SIZE] = [
        0xd1, 0xfc, 0x3d, 0x5f, 0xa0, 0x5f, 0x02, 0xd0, 0x83, 0x9b, 0x0e, 0x32, 0xc2, 0x27, 0x09,
        0x12, 0xcc, 0xfc, 0x42, 0xf6, 0x0d, 0xf4, 0x7d, 0xc8, 0x80, 0x1a, 0x64, 0x25, 0xa7, 0xfa,
        0x4a, 0x37, 0x2b, 0x74, 0x40, 0x90, 0x09, 0x65, 0xab, 0x01, 0x34, 0x3e, 0xc2, 0xc4, 0xf7,
        0xa2, 0x3a, 0x5c, 0x4e, 0x76, 0x4f, 0x42, 0xa8, 0x6c, 0xc9, 0xf1, 0x7b, 0x12, 0x80, 0xa4,
        0xef, 0xa2, 0x4d, 0x72,
    ];

    let comp_name_5 = CString::new("APEX1").expect("CString::new failed");
    let config_values_5 = diced_open_dice::DiceConfigValues {
        component_name: Some(&comp_name_5),
        component_version: Some(12),
        resettable: true,
        ..Default::default()
    };
    let config_descriptor_5 = diced_open_dice::retry_bcc_format_config_descriptor(&config_values_5)
        .expect("failed to format config descriptor");

    let input_values_5 = diced_open_dice::InputValues::new(
        CODE_HASH_APEX1,
        diced_open_dice::Config::Descriptor(config_descriptor_5.as_slice()),
        AUTHORITY_HASH_APEX1,
        diced_open_dice::DiceMode::kDiceModeDebug,
        [0u8; diced_open_dice::HIDDEN_SIZE],
    );

    let (cdi_values_5, cert_5) = diced_open_dice::retry_dice_main_flow(
        &cdi_values_4.cdi_attest,
        &cdi_values_4.cdi_seal,
        &input_values_5,
    )
    .expect("Failed to run first main flow");

    if dice_chain_len == 5 {
        let cert_chain = Value::Array(vec![
            Value::Integer(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into()),
            Value::Bytes(root_pub_cose_key_bstr.clone()),
            Value::from_slice(&cert_1).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_2).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_3).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_4).expect("failed to deserialize the certificate into CBOR"),
            Value::from_slice(&cert_5).expect("failed to deserialize the certificate into CBOR"),
        ]);
        let identity = Value::Array(vec![
            Value::Integer(IDENTITY_VERSION.into()),
            Value::Bytes(cert_chain.to_vec()?),
        ]);
        let pvt_key_seed =
            diced_open_dice::derive_cdi_private_key_seed(&cdi_values_5.cdi_attest).unwrap();
        let (_, pvt_key) = diced_open_dice::keypair_from_seed(pvt_key_seed.as_array())
            .expect("failed to create key pair from seed.");
        let pvt_key: [u8; CURVE25519_PRIV_KEY_LEN] =
            pvt_key.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(|e| {
                ag_err!(InternalError, "error in constructing the private signing key {:?}", e)
            })?;
        return Ok((EcSignKey::Ed25519(pvt_key), identity.to_vec()?));
    }
    Err(ag_err!(InternalError, "this method supports the maximum length of 5 for a DICE chain"))
}

/// Add a smoke test for `get_identity` in the `Device` trait, to ensure that the returned Identity
/// passes a set of validation.
pub fn test_get_identity<D: Device, E: EcDsa>(ag_device: &D, ecdsa: &E) {
    let (_, identity) = ag_device.get_identity().unwrap();
    identity.validate(ecdsa).unwrap();
}
