/*
 * Copyright (C) 2024 The Android Open Source Project
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


package android.crypto.hpke;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface HpkeSpi {

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public void engineInitSender(PublicKey recipientKey, byte[] info, PrivateKey senderKey, byte[] psk, byte[] psk_id);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public void engineInitSenderWithSeed(PublicKey recipientKey, byte[] info, PrivateKey senderKey, byte[] psk, byte[] psk_id, byte[] sKe);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public void engineInitRecipient(byte[] encapsulated, PrivateKey recipientKey, byte[] info, PublicKey senderKey, byte[] psk, byte[] psk_id);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public byte[] engineSeal(byte[] plaintext, byte[] aad);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public byte[] engineOpen(byte[] ciphertext, byte[] aad);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public byte[] engineExport(int length, byte[] context);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_HPKE_V_APIS)
public byte[] getEncapsulated();
}
