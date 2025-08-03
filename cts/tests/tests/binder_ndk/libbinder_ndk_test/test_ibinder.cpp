/*
 * Copyright (C) 2018 The Android Open Source Project
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
#define LOG_TAG "Cts-NdkBinderTest"

#include <android/binder_ibinder.h>
#include <gtest/gtest.h>

#include "utilities.h"

class NdkBinderTest_AIBinder : public NdkBinderTest {};

TEST_F(NdkBinderTest_AIBinder, Destruction) {
  bool destroyed = false;
  AIBinder* binder =
      SampleData::newBinder(nullptr, [&](SampleData*) { destroyed = true; });
  EXPECT_FALSE(destroyed);
  AIBinder_incStrong(binder);  // 1 -> 2
  EXPECT_FALSE(destroyed);
  AIBinder_decStrong(binder);  // 2 -> 1
  EXPECT_FALSE(destroyed);
  AIBinder_decStrong(binder);  // 1 -> 0
  EXPECT_TRUE(destroyed);
}

TEST_F(NdkBinderTest_AIBinder, GetClass) {
  AIBinder* binder = SampleData::newBinder();
  // class is already set since this local binder is contructed with it
  EXPECT_EQ(SampleData::kClass, AIBinder_getClass(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, AssociateClass) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_TRUE(AIBinder_associateClass(binder, SampleData::kClass));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, AssociateUnrelatedClassWithSameDescriptorFails) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_FALSE(AIBinder_associateClass(binder, SampleData::kAnotherClassWithSameDescriptor));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, AssociateWrongClassFails) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_FALSE(AIBinder_associateClass(binder, SampleData::kAnotherClass));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, ClassGetDescriptor) {
  EXPECT_NE(SampleData::kClass, SampleData::kAnotherClassWithSameDescriptor);
  EXPECT_STREQ(SampleData::kDescriptor, AIBinder_Class_getDescriptor(SampleData::kClass));
  EXPECT_STREQ(SampleData::kDescriptor,
               AIBinder_Class_getDescriptor(SampleData::kAnotherClassWithSameDescriptor));

  AIBinder* binder = SampleData::newBinder();
  EXPECT_STREQ(SampleData::kDescriptor, AIBinder_Class_getDescriptor(AIBinder_getClass(binder)));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetUserData) {
  // This test can't use the helper utility since SampleData isn't exposed
  SampleData* data = new SampleData;
  // Takes ownership of data
  AIBinder* binder = AIBinder_new(SampleData::kClass, static_cast<void*>(data));
  EXPECT_EQ(data, AIBinder_getUserData(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, DestructionGivesUserData) {
  // This test can't use the helper utility since SampleData isn't exposed
  SampleData* destroyedPointer = nullptr;
  SampleData* data = new SampleData(
      nullptr, [&](SampleData* data) { destroyedPointer = data; });
  // Takes ownership of data
  AIBinder* binder = AIBinder_new(SampleData::kClass, static_cast<void*>(data));
  EXPECT_EQ(nullptr, destroyedPointer);
  AIBinder_decStrong(binder);

  // These pointers no longer reference valid memory locations, but the pointers
  // themselves are valid
  EXPECT_EQ(data, destroyedPointer);
}

void onBinderDied(void* /*cookie*/) {}

TEST_F(NdkBinderTest_AIBinder, LinkInProcess) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_DeathRecipient* recipient =
      AIBinder_DeathRecipient_new(onBinderDied);

  EXPECT_EQ(STATUS_INVALID_OPERATION,
            AIBinder_linkToDeath(binder, recipient, nullptr /*cookie*/));

  AIBinder_DeathRecipient_delete(recipient);
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetCallingWhenNoCalling) {
  // Calling UID/PID are current calling UID/PID if there isn't an incoming
  // call.
  EXPECT_EQ(getuid(), AIBinder_getCallingUid());
  EXPECT_EQ(getpid(), AIBinder_getCallingPid());
}

TEST_F(NdkBinderTest_AIBinder, DebugRefCount) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_EQ(1, AIBinder_debugGetRefCount(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerCanPromote) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder* promoted = AIBinder_Weak_promote(weak);
  EXPECT_EQ(binder, promoted);
  AIBinder_Weak_delete(weak);
  AIBinder_decStrong(binder);
  AIBinder_decStrong(promoted);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerCanNotPromote) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder_decStrong(binder);

  AIBinder* promoted = AIBinder_Weak_promote(weak);
  EXPECT_EQ(nullptr, promoted);

  AIBinder_Weak_delete(weak);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerClonePromotes) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder_Weak* copy = AIBinder_Weak_clone(weak);
  AIBinder_Weak_delete(weak);

  AIBinder* promoted = AIBinder_Weak_promote(copy);
  EXPECT_EQ(binder, promoted);

  AIBinder_Weak_delete(copy);
  AIBinder_decStrong(promoted);
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerCloneNoPromote) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder_Weak* copy = AIBinder_Weak_clone(weak);
  AIBinder_Weak_delete(weak);

  AIBinder_decStrong(binder);

  AIBinder* promoted = AIBinder_Weak_promote(copy);
  EXPECT_EQ(nullptr, promoted);

  AIBinder_Weak_delete(copy);
}

TEST_F(NdkBinderTest_AIBinder, BinderEqual) {
  AIBinder* binder = SampleData::newBinder();

  EXPECT_FALSE(AIBinder_lt(binder, binder));

  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, BinderNotEqual) {
  AIBinder* b1 = SampleData::newBinder();
  AIBinder* b2 = SampleData::newBinder();

  EXPECT_NE(AIBinder_lt(b1, b2), AIBinder_lt(b2, b1));

  AIBinder_decStrong(b2);
  AIBinder_decStrong(b1);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerEqual) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak1 = AIBinder_Weak_new(binder);
  AIBinder_Weak* weak2 = AIBinder_Weak_new(binder);

  // doesn't need to be promotable to remember ordering
  AIBinder_decStrong(binder);

  // they are different objects
  EXPECT_NE(weak1, weak2);

  // they point to the same binder
  EXPECT_FALSE(AIBinder_Weak_lt(weak1, weak2));
  EXPECT_FALSE(AIBinder_Weak_lt(weak2, weak1));

  AIBinder_Weak_delete(weak1);
  AIBinder_Weak_delete(weak2);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerNotEqual) {
  AIBinder* b1 = SampleData::newBinder();
  AIBinder_Weak* w1 = AIBinder_Weak_new(b1);
  AIBinder* b2 = SampleData::newBinder();
  AIBinder_Weak* w2 = AIBinder_Weak_new(b2);

  bool b1ltb2 = AIBinder_lt(b1, b2);

  // doesn't need to be promotable to remember ordering
  AIBinder_decStrong(b2);
  AIBinder_decStrong(b1);

  EXPECT_EQ(b1ltb2, AIBinder_Weak_lt(w1, w2));
  EXPECT_EQ(!b1ltb2, AIBinder_Weak_lt(w2, w1));

  AIBinder_Weak_delete(w1);
  AIBinder_Weak_delete(w2);
}

TEST_F(NdkBinderTest_AIBinder, IsHandlingTransactionFalse) {
  EXPECT_FALSE(AIBinder_isHandlingTransaction());
}

TEST_F(NdkBinderTest_AIBinder, LocalIsLocal) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_FALSE(AIBinder_isRemote(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, IsAlive) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_TRUE(AIBinder_isAlive(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, CanPing) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_OK(AIBinder_ping(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetExtensionImmediatelyReturnsNull) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder* ext;
  EXPECT_OK(AIBinder_getExtension(binder, &ext));
  EXPECT_EQ(ext, nullptr);
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetSetExtensionLocally) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder* ext = SampleData::newBinder();
  EXPECT_OK(AIBinder_setExtension(binder, ext));

  AIBinder* getExt;
  EXPECT_OK(AIBinder_getExtension(binder, &getExt));
  ASSERT_EQ(ext, getExt);

  AIBinder_decStrong(ext);
  AIBinder_decStrong(getExt);
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetSetExtensionRepeatedly) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder* ext1 = SampleData::newBinder();
  AIBinder* ext2 = SampleData::newBinder();
  EXPECT_OK(AIBinder_setExtension(binder, ext1));
  EXPECT_OK(AIBinder_setExtension(binder, ext2));

  AIBinder* getExt;
  EXPECT_OK(AIBinder_getExtension(binder, &getExt));
  ASSERT_EQ(ext2, getExt);

  AIBinder_decStrong(ext1);
  AIBinder_decStrong(ext2);
  AIBinder_decStrong(getExt);
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionHappens) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK),
                                           ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, OnewayTransactionHappens) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK),
                                           ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, FLAG_ONEWAY));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionCodeMaintained) {
  AIBinder* binder = SampleData::newBinder(
      [&](transaction_code_t code, const AParcel*, AParcel*) {
        EXPECT_EQ(code, kCode);
        return STATUS_OK;
      },
      ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionCodeRangeRespected) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK));
  EXPECT_OK(SampleData::transact(binder, FIRST_CALL_TRANSACTION));
  EXPECT_OK(SampleData::transact(binder, FIRST_CALL_TRANSACTION + 1));
  EXPECT_OK(SampleData::transact(binder, LAST_CALL_TRANSACTION - 1));
  EXPECT_OK(SampleData::transact(binder, LAST_CALL_TRANSACTION));

  EXPECT_EQ(STATUS_UNKNOWN_TRANSACTION,
            SampleData::transact(binder, FIRST_CALL_TRANSACTION - 1));
  EXPECT_EQ(STATUS_UNKNOWN_TRANSACTION,
            SampleData::transact(binder, LAST_CALL_TRANSACTION + 1));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, UnknownFlagsRejected) {
  AIBinder* binder =
      SampleData::newBinder(nullptr, ExpectLifetimeTransactions(0));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, +1 + 415));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, FLAG_ONEWAY + 1));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, ~0));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, UnassociatedBinderRejected) {
  AIBinder* binder1 = SampleData::newBinder(nullptr, ExpectLifetimeTransactions(0));

  AParcel* in;
  EXPECT_EQ(STATUS_OK, AIBinder_prepareTransaction(binder1, &in));

  AIBinder* binder2 = SampleData::newBinder(nullptr, ExpectLifetimeTransactions(0));

  AParcel* out;
  // transaction on different binder object from prepare fails
  EXPECT_EQ(STATUS_BAD_VALUE, AIBinder_transact(binder2, kCode, &in, &out, 0));

  AParcel_delete(out);

  AIBinder_decStrong(binder2);
  AIBinder_decStrong(binder1);
}

void* EmptyOnCreate(void* args) { return args; }
void EmptyOnDestroy(void* /*userData*/) {}
binder_status_t EmptyOnTransact(AIBinder* /*binder*/,
                                transaction_code_t /*code*/,
                                const AParcel* /*in*/, AParcel* /*out*/) {
  return STATUS_OK;
}

TEST_F(NdkBinderTest_AIBinder, NullArguments) {
  void* const kVoidStar = reinterpret_cast<void*>(0xDEADBEEF);
  const char* const kStr = "asdf";
  AIBinder* binder = SampleData::newBinder();
  AIBinder_DeathRecipient* recipient =
      AIBinder_DeathRecipient_new(onBinderDied);
  EXPECT_NE(nullptr, recipient);

  EXPECT_EQ(nullptr, AIBinder_Class_define(nullptr, EmptyOnCreate,
                                           EmptyOnDestroy, EmptyOnTransact));
  EXPECT_EQ(nullptr, AIBinder_Class_define(kStr, nullptr, EmptyOnDestroy,
                                           EmptyOnTransact));
  EXPECT_EQ(nullptr, AIBinder_Class_define(kStr, EmptyOnCreate, nullptr,
                                           EmptyOnTransact));
  EXPECT_EQ(nullptr, AIBinder_Class_define(kStr, EmptyOnCreate, EmptyOnDestroy,
                                           nullptr));

  EXPECT_EQ(nullptr, AIBinder_new(nullptr /*clazz*/, kVoidStar /*args*/));
  EXPECT_EQ(false, AIBinder_isRemote(nullptr));
  EXPECT_EQ(false, AIBinder_isAlive(nullptr));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL, AIBinder_ping(nullptr));

  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_linkToDeath(nullptr, recipient, kVoidStar /*cookie*/));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_linkToDeath(binder, nullptr, kVoidStar /*cookie*/));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_unlinkToDeath(nullptr, recipient, kVoidStar /*cookie*/));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_unlinkToDeath(binder, nullptr, kVoidStar /*cookie*/));

  // Does not crash
  AIBinder_incStrong(nullptr);
  AIBinder_decStrong(nullptr);

  EXPECT_EQ(-1, AIBinder_debugGetRefCount(nullptr));
  EXPECT_EQ(false, AIBinder_associateClass(binder, nullptr));
  EXPECT_EQ(false, AIBinder_associateClass(nullptr, SampleData::kClass));
  EXPECT_EQ(nullptr, AIBinder_getClass(nullptr));
  EXPECT_EQ(nullptr, AIBinder_getUserData(nullptr));

  AParcel* parcel = nullptr;
  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_prepareTransaction(binder, nullptr));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL,
            AIBinder_prepareTransaction(nullptr, &parcel));
  EXPECT_EQ(nullptr, parcel);  // not modified

  {
    auto newParcel = [&] {
      AParcel* parcel = nullptr;
      EXPECT_OK(AIBinder_prepareTransaction(binder, &parcel));
      return parcel;
    };

    AParcel* inParcel = nullptr;
    AParcel* outParcel = nullptr;

    inParcel = newParcel();
    EXPECT_NE(nullptr, inParcel);
    EXPECT_EQ(
        STATUS_UNEXPECTED_NULL,
        AIBinder_transact(nullptr, kCode, &inParcel, &outParcel, 0 /*flags*/));
    EXPECT_EQ(nullptr, inParcel);   // ownership taken
    EXPECT_EQ(nullptr, outParcel);  // not modified

    EXPECT_EQ(
        STATUS_UNEXPECTED_NULL,
        AIBinder_transact(binder, kCode, nullptr, &outParcel, 0 /*flags*/));
    EXPECT_EQ(nullptr, outParcel);  // not modified

    inParcel = newParcel();
    EXPECT_NE(nullptr, inParcel);
    EXPECT_EQ(
        STATUS_UNEXPECTED_NULL,
        AIBinder_transact(binder, kCode, &inParcel, nullptr, 0 /*flags*/));
    EXPECT_EQ(nullptr, inParcel);   // ownership taken
    EXPECT_EQ(nullptr, outParcel);  // not modified
  }

  EXPECT_EQ(nullptr, AIBinder_Weak_new(nullptr));

  // Does not crash
  AIBinder_Weak_delete(nullptr);

  EXPECT_EQ(nullptr, AIBinder_Weak_promote(nullptr));

  EXPECT_EQ(nullptr, AIBinder_DeathRecipient_new(nullptr));

  EXPECT_EQ(nullptr, AIBinder_Weak_clone(nullptr));

  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  EXPECT_TRUE(AIBinder_Weak_lt(nullptr, weak));
  EXPECT_FALSE(AIBinder_Weak_lt(weak, nullptr));
  AIBinder_Weak_delete(weak);

  // Does not crash
  AIBinder_DeathRecipient_delete(nullptr);
  AIBinder_DeathRecipient_setOnUnlinked(recipient, nullptr);
  AIBinder_DeathRecipient_setOnUnlinked(nullptr, nullptr);

  AIBinder_DeathRecipient_delete(recipient);
  AIBinder_decStrong(binder);

  EXPECT_EQ(STATUS_UNEXPECTED_NULL, AIBinder_getExtension(nullptr, nullptr));
  EXPECT_EQ(STATUS_UNEXPECTED_NULL, AIBinder_setExtension(nullptr, nullptr));
}
