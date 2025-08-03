/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define TLOG_TAG "swbcc"

#include <assert.h>
#include <dice/android.h>
#include <dice/cbor_writer.h>
#include <dice/dice.h>
#include <dice/ops.h>
#include <dice/ops/trait/cose.h>
#include <dice/utils.h>
#include <interface/hwbcc/hwbcc.h>
#include <lib/hwbcc/common/swbcc.h>
#include <lib/hwkey/hwkey.h>
#include <lib/rng/trusty_rng.h>
#include <lib/system_state/system_state.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

static const uint8_t kdf_ctx[] = "RkpDerivCtx";
static const uint8_t uds_ctx[] = "UdsDeriveCtx";

/* ZERO UUID represents non-secure world */
static const struct uuid zero_uuid = UUID_INITIAL_VALUE(zero_uuid);

/* Set of information required to derive DICE artifacts for the child node. */
struct ChildNodeInfo {
    uint8_t code_hash[DICE_HASH_SIZE];
    uint8_t authority_hash[DICE_HASH_SIZE];
    DiceAndroidConfigValues config_descriptor;
};

struct dice_root_state {
    /* Unique Device Secret - A hardware backed secret */
    uint8_t UDS[DICE_CDI_SIZE];
    /* Public key of the key pair derived from a seed derived from UDS. */
    uint8_t UDS_pub_key[DICE_PUBLIC_KEY_SIZE];
    /* Secret (of size: DICE_HIDDEN_SIZE) with factory reset life time. */
    uint8_t FRS[DICE_HIDDEN_SIZE];
    /**
     * Information about the child node of Trusty in the DICE chain in
     * non-secure world (e.g. ABL).
     */
    struct ChildNodeInfo child_node_info;
};

struct swbcc_srv_state {
    void* dice_ctx;
    struct dice_root_state dice_root;
    /**
     * This is set to 1 when a deprivileged call is received from non-secure
     * world. Assumption: there are no concurrent calls to this app.
     */
    bool ns_deprivileged;
};

static struct swbcc_srv_state srv_state;

static int dice_result_to_err(DiceResult result) {
    switch (result) {
    case kDiceResultOk:
        return NO_ERROR;
    case kDiceResultInvalidInput:
        return ERR_INVALID_ARGS;
    case kDiceResultBufferTooSmall:
        return ERR_NOT_ENOUGH_BUFFER;
    case kDiceResultPlatformError:
        return (int)result;
    }
}

struct swbcc_session {
    uint8_t key_seed[DICE_PRIVATE_KEY_SEED_SIZE];
    uint8_t pub_key[DICE_PUBLIC_KEY_SIZE];
    uint8_t priv_key[DICE_PRIVATE_KEY_SIZE];

    uint8_t test_key_seed[DICE_PRIVATE_KEY_SEED_SIZE];
    uint8_t test_pub_key[DICE_PUBLIC_KEY_SIZE];
    uint8_t test_priv_key[DICE_PRIVATE_KEY_SIZE];

    struct uuid client_uuid;
};

/* Max size of COSE_Sign1 including payload. */
#define MAX_CERTIFICATE_SIZE 512

/* Set of DICE artifacts passed on from one stage to the next */
struct DICEArtifacts {
    uint8_t next_cdi_attest[DICE_CDI_SIZE];
    uint8_t next_cdi_seal[DICE_CDI_SIZE];
    uint8_t next_certificate[MAX_CERTIFICATE_SIZE];
    size_t next_certificate_size;
};

/* Checks if the call to the TA is from non-secure world. */
static bool is_zero_uuid(const struct uuid peer) {
    if (memcmp(&peer, &zero_uuid, sizeof(zero_uuid))) {
        return false;
    } else {
        return true;
    }
}

static int derive_seed(uint8_t* ctx, uint8_t* seed) {
    long rc = hwkey_open();
    if (rc < 0) {
        TLOGE("Failed hwkey_open(): %ld\n", rc);
        return rc;
    }
    hwkey_session_t session = (hwkey_session_t)rc;

    uint32_t kdf_version = HWKEY_KDF_VERSION_1;
    rc = hwkey_derive(session, &kdf_version, ctx, seed,
                      DICE_PRIVATE_KEY_SEED_SIZE);
    if (rc != NO_ERROR) {
        TLOGE("Failed hwkey_derive(): %ld\n", rc);
        goto out;
    }

    rc = NO_ERROR;

out:
    hwkey_close(session);
    return (int)rc;
}

int swbcc_glob_init(const uint8_t FRS[DICE_HIDDEN_SIZE],
                    const uint8_t code_hash[DICE_HASH_SIZE],
                    const uint8_t authority_hash[DICE_HASH_SIZE],
                    const DiceAndroidConfigValues* config_descriptor) {
    assert(FRS);

    srv_state.ns_deprivileged = false;

    memcpy(srv_state.dice_root.FRS, FRS, DICE_HIDDEN_SIZE);

    memcpy(srv_state.dice_root.child_node_info.code_hash, code_hash,
           DICE_HIDDEN_SIZE);
    memcpy(srv_state.dice_root.child_node_info.authority_hash, authority_hash,
           DICE_HIDDEN_SIZE);
    srv_state.dice_root.child_node_info.config_descriptor.configs =
            config_descriptor->configs;
    /* Component name is not copied, assuming it points to string literals which
     * are static. */
    srv_state.dice_root.child_node_info.config_descriptor.component_name =
            config_descriptor->component_name;
    srv_state.dice_root.child_node_info.config_descriptor.component_version =
            config_descriptor->component_version;

    int rc;
    DiceResult result;
    uint8_t ctx[DICE_PRIVATE_KEY_SEED_SIZE];

    memset(ctx, 0, sizeof(ctx));
    memcpy(ctx, uds_ctx, sizeof(uds_ctx));

    /* Init UDS */
    rc = derive_seed(ctx, srv_state.dice_root.UDS);
    if (rc != NO_ERROR) {
        TLOGE("Failed to derive a hardware backed key for UDS.\n");
        return rc;
    }

    /* Derive private key seed */
    uint8_t private_key_seed[DICE_PRIVATE_KEY_SEED_SIZE];
    result = DiceDeriveCdiPrivateKeySeed(NULL, srv_state.dice_root.UDS,
                                         private_key_seed);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to derive a seed for UDS key pair.\n");
        return rc;
    }
    /**
     * Derive UDS key pair. UDS public key is kept in dice_root to construct
     * the certificate chain for the child nodes. UDS private key is derived in
     * every DICE operation which uses it.
     */
    uint8_t UDS_private_key[DICE_PRIVATE_KEY_SIZE];
    result = DiceKeypairFromSeed(NULL, private_key_seed,
                                 srv_state.dice_root.UDS_pub_key,
                                 UDS_private_key);

    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to derive UDS key pair.\n");
        return rc;
    }

    return rc;
}

int swbcc_init(swbcc_session_t* s, const struct uuid* client) {
    int rc;
    DiceResult result;
    uint8_t ctx[DICE_PRIVATE_KEY_SEED_SIZE];

    struct swbcc_session* session =
            (struct swbcc_session*)calloc(1, sizeof(*session));
    if (!session) {
        return ERR_NO_MEMORY;
    }

    session->client_uuid = *client;

    /**
     * If the call to hwbcc is to obtain the DICE artifacts, we do not need to
     * initialize anything other than the UUID in the session, because the
     * common UDS is initialized during the initialization of the service. We
     * only need to track the client UUID in that case in order to retrieve the
     * client's CDI inputs (e.g. code hash). But at this point we do not know
     * which API method the client is going to call. However, we know that if
     * the call is from non-secure world, the goal is to retrieve the DICE
     * artifacts. Therefore, we filter based on the zero UUID for now. But in
     * the future, we can filter the legacy case of creating a KM specific BCC
     * via the KM UUID, because the main purpose of hwbcc service is to provide
     * the DICE artifacts to the clients.
     */
    if (is_zero_uuid(session->client_uuid)) {
        *s = (swbcc_session_t)session;

        /**
         * Stop serving calls from non-secure world after receiving
         * `ns_deprivilege` call.
         */
        if (srv_state.ns_deprivileged) {
            return ERR_NOT_ALLOWED;
        }

        return NO_ERROR;
    }

    STATIC_ASSERT(sizeof(ctx) >= sizeof(*client) + sizeof(kdf_ctx));

    memset(ctx, 0, sizeof(ctx));
    memcpy(ctx, client, sizeof(*client));
    memcpy(ctx + sizeof(*client), kdf_ctx, sizeof(kdf_ctx));

    /* Init BCC keys */
    rc = derive_seed(ctx, session->key_seed);
    if (rc != NO_ERROR) {
        goto err;
    }

    result = DiceKeypairFromSeed(srv_state.dice_ctx, session->key_seed,
                                 session->pub_key, session->priv_key);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to generate keypair: %d\n", rc);
        return rc;
    }

    /* Init test keys */
    rc = trusty_rng_secure_rand(session->test_key_seed,
                                sizeof(session->test_key_seed));
    if (rc != NO_ERROR) {
        goto err;
    }

    result = DiceKeypairFromSeed(srv_state.dice_ctx, session->test_key_seed,
                                 session->test_pub_key, session->test_priv_key);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to generate test keypair: %d\n", rc);
        return rc;
    }

    *s = (swbcc_session_t)session;
    return NO_ERROR;

err:
    free(session);
    return rc;
}

int swbcc_ns_deprivilege(swbcc_session_t s) {
    srv_state.ns_deprivileged = true;
    return NO_ERROR;
}

void swbcc_close(swbcc_session_t s) {
    free(s);
}

/*
 * Format and (size) of a COSE_Sign1 Msg in this case is:
 * Array header (1) | Protected Params (4) | Unprotected Params (1) |
 * MAC Key Hdr (2) | MAC Key (32) | Sig Hdr (2) | Sig (64)
 */
#define MAC_SIGN1_SIZE (106)

int swbcc_sign_key(swbcc_session_t s,
                   uint32_t test_mode,
                   int32_t cose_algorithm,
                   const uint8_t* key,
                   uint32_t key_size,
                   const uint8_t* aad,
                   size_t aad_size,
                   uint8_t* cose_sign1,
                   size_t cose_sign1_buf_size,
                   size_t* cose_sign1_size) {
    int rc;
    DiceResult result;
    const uint8_t* signing_key;
    struct swbcc_session* session = s;

    assert(s);
    assert(key);
    assert(aad);
    assert(cose_sign1);
    assert(cose_sign1_size);
    assert(cose_sign1_buf_size >= MAC_SIGN1_SIZE);

    if (cose_algorithm != HWBCC_ALGORITHM_ED25519) {
        TLOGE("Signing algorithm is not supported: %d\n", cose_algorithm);
        return ERR_NOT_SUPPORTED;
    }

    signing_key = test_mode ? session->test_priv_key : session->priv_key;

    result = DiceCoseSignAndEncodeSign1(
            srv_state.dice_ctx, key, key_size, aad, aad_size, signing_key,
            cose_sign1_buf_size, cose_sign1, cose_sign1_size);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to generate COSE_Sign1: %d\n", rc);
        return rc;
    }

    return NO_ERROR;
}

#define CONFIG_DESCRIPTOR_TOTAL_SIZE 48

/*
 * Format and (size) of a COSE_Sign1 Msg in this case is:
 * Array header (1) | Protected Params (4) | Unprotected Params (1) |
 * CWT Hdr (2) | CWT (76) | Sig Hdr (2) | Sig (64)
 */
#define BCC_SIGN1_SIZE (150)

/*
 * Format and (size) of BCC in this case is:
 * Array header (1) | Encoded pub key (44) | COSE_Sign1 certificate
 */
#define BCC_TOTAL_SIZE (45 + BCC_SIGN1_SIZE)

static int encode_degenerate_cert(void* dice_ctx,
                                  const uint8_t* seed,
                                  uint8_t* cert,
                                  size_t cert_buf_size,
                                  size_t* cert_size) {
    int rc;
    DiceResult result;
    DiceInputValues input_values = {};

    /*
     * No need to provide DICE inputs for this self-signed certificate other
     * than the configuration descriptor which should conform to the
     * specification from the RKP HAL.
     */
    DiceAndroidConfigValues config_values = {};
    uint8_t config_descriptor_encoded[CONFIG_DESCRIPTOR_TOTAL_SIZE];
    size_t config_descriptor_encoded_size = 0;

    result = DiceAndroidFormatConfigDescriptor(
            &config_values, sizeof(config_descriptor_encoded),
            config_descriptor_encoded, &config_descriptor_encoded_size);

    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to format config descriptor : %d\n", rc);
        return rc;
    }

    input_values.config_type = kDiceConfigTypeDescriptor;
    input_values.config_descriptor = config_descriptor_encoded;
    input_values.config_descriptor_size = config_descriptor_encoded_size;

    result = DiceGenerateCertificate(dice_ctx, seed, seed, &input_values,
                                     cert_buf_size, cert, cert_size);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to generate certificate: %d\n", rc);
        return rc;
    }

    return NO_ERROR;
}

int swbcc_get_bcc(swbcc_session_t s,
                  uint32_t test_mode,
                  uint8_t* bcc,
                  size_t bcc_buf_size,
                  size_t* bcc_size) {
    int rc;
    DiceResult result;
    struct CborOut out;
    const uint8_t* seed;
    const uint8_t* pub_key;
    size_t bcc_used;
    struct swbcc_session* session = s;

    assert(s);
    assert(bcc);
    assert(bcc_size);
    assert(bcc_buf_size >= BCC_TOTAL_SIZE);

    if (test_mode) {
        seed = session->test_key_seed;
        pub_key = session->test_pub_key;
    } else {
        seed = session->key_seed;
        pub_key = session->pub_key;
    }

    /* Encode BCC */
    CborOutInit(bcc, bcc_buf_size, &out);
    CborWriteArray(2, &out);
    assert(!CborOutOverflowed(&out));

    bcc_used = CborOutSize(&out);
    bcc += bcc_used;
    bcc_buf_size -= bcc_used;
    *bcc_size = bcc_used;

    /* Encode first entry in the array which is a COSE_Key */
    result = DiceCoseEncodePublicKey(srv_state.dice_ctx, pub_key, bcc_buf_size,
                                     bcc, &bcc_used);
    rc = dice_result_to_err(result);
    if (rc != NO_ERROR) {
        TLOGE("Failed to encode public key: %d\n", rc);
        return rc;
    }

    bcc += bcc_used;
    bcc_buf_size -= bcc_used;
    *bcc_size += bcc_used;

    /* Encode second entry in the array which is a COSE_Sign1 */
    rc = encode_degenerate_cert(srv_state.dice_ctx, seed, bcc, bcc_buf_size,
                                &bcc_used);
    if (rc != NO_ERROR) {
        TLOGE("Failed to generate certificate: %d\n", rc);
        return rc;
    }

    *bcc_size += bcc_used;
    return NO_ERROR;
}

/*
 * Size of a DICE artifacts handed over from root (without Bcc) is:
 * CBOR tags + Two CDIs = 71
 */
#define DICE_ARTIFACTS_WO_BCC_TOTAL_SIZE 71

int swbcc_get_dice_artifacts(swbcc_session_t s,
                             uint64_t context,
                             uint8_t* dice_artifacts,
                             size_t dice_artifacts_buf_size,
                             size_t* dice_artifacts_size) {
    assert(s);
    int rc;
    DiceResult result;
    assert(dice_artifacts);
    assert(dice_artifacts_size);
    assert(dice_artifacts_buf_size >= DICE_ARTIFACTS_WO_BCC_TOTAL_SIZE);

    struct DICEArtifacts dice_artifacts_for_target = {};

    /**
     * TODO: Currently, we assume that the only caller of this method is ABL
     * (i.e. non-secure world). Therefore, we return DICE artifacts (i.e.
     * BccHandover) without a Bcc, for privacy reasons. However, when this
     * method serves TAs, certain refactoring will be required, such as:
     * differentiating the caller from the caller's UUID,
     * having multiple child nodes in DICE root corresponding to the TAs, which
     * contain their DICE information, and including the Bcc in the DICE
     * chain returned to the TAs.
     */

    /* Initialize the DICE input values. */
    DiceInputValues input_values = {};
    memcpy(input_values.code_hash,
           srv_state.dice_root.child_node_info.code_hash,
           sizeof(srv_state.dice_root.child_node_info.code_hash));

    input_values.config_type = kDiceConfigTypeDescriptor;

    uint8_t config_descriptor_encoded[CONFIG_DESCRIPTOR_TOTAL_SIZE];
    size_t config_descriptor_encoded_size = 0;

    result = DiceAndroidFormatConfigDescriptor(
            &(srv_state.dice_root.child_node_info.config_descriptor),
            sizeof(config_descriptor_encoded), config_descriptor_encoded,
            &config_descriptor_encoded_size);

    rc = dice_result_to_err(result);

    if (rc != NO_ERROR) {
        TLOGE("Failed to format config descriptor : %d\n", rc);
        return rc;
    }

    input_values.config_descriptor = config_descriptor_encoded;
    input_values.config_descriptor_size = config_descriptor_encoded_size;

    memcpy(input_values.authority_hash,
           srv_state.dice_root.child_node_info.authority_hash,
           sizeof(srv_state.dice_root.child_node_info.authority_hash));

    /* Set the mode */
    if (system_state_app_loading_unlocked()) {
        input_values.mode = kDiceModeDebug;
    } else {
        input_values.mode = kDiceModeNormal;
    }

    /*
     * DICE artifacts to be handed over from root to the child nodes takes the
     * following format.
     * BccHandover = {
     *   1 : bstr .size 32,	// CDI_Attest
     *   2 : bstr .size 32,	// CDI_Seal
     *   ? 3 : Bcc,        	// Cert_Chain
     * }
     * where Bcc = [
     *         PubKeyEd25519 / PubKeyECDSA256, // Root pub key
     *         BccEntry,                       // Root -> leaf
     *       ]
     * In the BccHandover returned to non-secure world, we do not include
     * anything pre-FRS. Therefore, only the two CDIs are included in the
     * BccHandover.
     */

    // Factory reset secret is mixed in only for the non-secure world.
    memcpy(input_values.hidden, srv_state.dice_root.FRS,
           sizeof(srv_state.dice_root.FRS));

    result = DiceMainFlow(NULL, srv_state.dice_root.UDS,
                          srv_state.dice_root.UDS, &input_values, 0, NULL, NULL,
                          dice_artifacts_for_target.next_cdi_attest,
                          dice_artifacts_for_target.next_cdi_seal);
    rc = dice_result_to_err(result);

    if (rc != NO_ERROR) {
        TLOGE("Failed to derive DICE CDIs : %d\n", rc);
        return rc;
    }

    struct CborOut out;
    CborOutInit(dice_artifacts, dice_artifacts_buf_size, &out);
    CborWriteMap(2, &out);
    CborWriteInt(1, &out);
    CborWriteBstr(DICE_CDI_SIZE, dice_artifacts_for_target.next_cdi_attest,
                  &out);
    CborWriteInt(2, &out);
    CborWriteBstr(DICE_CDI_SIZE, dice_artifacts_for_target.next_cdi_seal, &out);
    assert(!CborOutOverflowed(&out));

    *dice_artifacts_size = CborOutSize(&out);

    return NO_ERROR;
}
