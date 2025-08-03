/*
 * Copyright (C) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Test Rust service for the AIDL compiler.

use aidl_test_fixedsizearray::aidl::android::aidl::fixedsizearray::FixedSizeArrayExample::{
    IRepeatFixedSizeArray, IntParcelable::IntParcelable,
};
use aidl_test_interface::aidl::android::aidl::tests::nested::{
    INestedService, ParcelableWithNested,
};
use aidl_test_interface::aidl::android::aidl::tests::ITestService::{
    self, BnTestService, BpTestService, Empty::Empty,
};
use aidl_test_interface::aidl::android::aidl::tests::{
    extension::ExtendableParcelable::ExtendableParcelable, extension::MyExt::MyExt,
    BackendType::BackendType, ByteEnum::ByteEnum, CircularParcelable::CircularParcelable,
    ConstantExpressionEnum::ConstantExpressionEnum, ICircular, INamedCallback, INewName, IOldName,
    IntEnum::IntEnum, LongEnum::LongEnum, RecursiveList::RecursiveList, StructuredParcelable,
    Union,
};
use aidl_test_interface::binder::{
    self, BinderFeatures, Interface, ParcelFileDescriptor, SpIBinder,
};
use aidl_test_versioned_interface::aidl::android::aidl::versioned::tests::{
    BazUnion::BazUnion, Foo::Foo, IFooInterface, IFooInterface::BnFooInterface,
    IFooInterface::BpFooInterface,
};
use android_aidl_test_trunk::aidl::android::aidl::test::trunk::{
    ITrunkStableTest, ITrunkStableTest::BnTrunkStableTest, ITrunkStableTest::BpTrunkStableTest,
    ITrunkStableTest::IMyCallback, ITrunkStableTest::MyEnum::MyEnum,
    ITrunkStableTest::MyOtherParcelable::MyOtherParcelable,
    ITrunkStableTest::MyParcelable::MyParcelable, ITrunkStableTest::MyUnion::MyUnion,
};
use simple_parcelable::SimpleParcelable;
use std::collections::HashMap;
use std::sync::Mutex;

fn dup_fd(fd: &ParcelFileDescriptor) -> ParcelFileDescriptor {
    ParcelFileDescriptor::new(fd.as_ref().try_clone().unwrap())
}

struct NamedCallback(String);

impl Interface for NamedCallback {}

impl INamedCallback::INamedCallback for NamedCallback {
    fn GetName(&self) -> binder::Result<String> {
        Ok(self.0.clone())
    }
}

struct OldName;

impl Interface for OldName {}

impl IOldName::IOldName for OldName {
    fn RealName(&self) -> binder::Result<String> {
        Ok("OldName".into())
    }
}

#[derive(Debug, Default)]
struct NewName;

impl Interface for NewName {}

impl INewName::INewName for NewName {
    fn RealName(&self) -> binder::Result<String> {
        Ok("NewName".into())
    }
}

#[derive(Debug, Default)]
struct Circular;

impl Interface for Circular {}

impl ICircular::ICircular for Circular {
    fn GetTestService(
        &self,
    ) -> binder::Result<Option<binder::Strong<dyn ITestService::ITestService>>> {
        Ok(None)
    }
}

#[derive(Default)]
struct TestService {
    service_map: Mutex<HashMap<String, binder::Strong<dyn INamedCallback::INamedCallback>>>,
}

impl Interface for TestService {}

macro_rules! impl_repeat {
    ($repeat_name:ident, $type:ty) => {
        fn $repeat_name(&self, token: $type) -> binder::Result<$type> {
            Ok(token)
        }
    };
}

macro_rules! impl_reverse {
    ($reverse_name:ident, $type:ty) => {
        fn $reverse_name(
            &self,
            input: &[$type],
            repeated: &mut Vec<$type>,
        ) -> binder::Result<Vec<$type>> {
            repeated.clear();
            repeated.extend_from_slice(input);
            Ok(input.iter().rev().cloned().collect())
        }
    };
}

macro_rules! impl_repeat_reverse {
    ($repeat_name:ident, $reverse_name:ident, $type:ty) => {
        impl_repeat! {$repeat_name, $type}
        impl_reverse! {$reverse_name, $type}
    };
}

macro_rules! impl_repeat_nullable {
    ($repeat_nullable_name:ident, $type:ty) => {
        fn $repeat_nullable_name(
            &self,
            input: Option<&[$type]>,
        ) -> binder::Result<Option<Vec<$type>>> {
            Ok(input.map(<[$type]>::to_vec))
        }
    };
}

impl ITestService::ITestService for TestService {
    impl_repeat! {RepeatByte, i8}
    impl_reverse! {ReverseByte, u8}

    fn UnimplementedMethod(&self, _: i32) -> binder::Result<i32> {
        // Pretend this method hasn't been implemented
        Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }

    fn TestOneway(&self) -> binder::Result<()> {
        Err(binder::StatusCode::UNKNOWN_ERROR.into())
    }

    fn Deprecated(&self) -> binder::Result<()> {
        Ok(())
    }

    impl_repeat_reverse! {RepeatBoolean, ReverseBoolean, bool}
    impl_repeat_reverse! {RepeatChar, ReverseChar, u16}
    impl_repeat_reverse! {RepeatInt, ReverseInt, i32}
    impl_repeat_reverse! {RepeatLong, ReverseLong, i64}
    impl_repeat_reverse! {RepeatFloat, ReverseFloat, f32}
    impl_repeat_reverse! {RepeatDouble, ReverseDouble, f64}
    impl_repeat_reverse! {RepeatByteEnum, ReverseByteEnum, ByteEnum}
    impl_repeat_reverse! {RepeatIntEnum, ReverseIntEnum, IntEnum}
    impl_repeat_reverse! {RepeatLongEnum, ReverseLongEnum, LongEnum}
    impl_reverse! {ReverseString, String}
    impl_reverse! {ReverseStringList, String}
    impl_reverse! {ReverseUtf8CppString, String}

    fn RepeatString(&self, input: &str) -> binder::Result<String> {
        Ok(input.into())
    }

    fn RepeatUtf8CppString(&self, input: &str) -> binder::Result<String> {
        Ok(input.into())
    }

    fn GetOtherTestService(
        &self,
        name: &str,
    ) -> binder::Result<binder::Strong<dyn INamedCallback::INamedCallback>> {
        let mut service_map = self.service_map.lock().unwrap();
        let other_service = service_map.entry(name.into()).or_insert_with(|| {
            let named_callback = NamedCallback(name.into());
            INamedCallback::BnNamedCallback::new_binder(named_callback, BinderFeatures::default())
        });
        Ok(other_service.to_owned())
    }

    fn SetOtherTestService(
        &self,
        name: &str,
        service: &binder::Strong<dyn INamedCallback::INamedCallback>,
    ) -> binder::Result<bool> {
        let mut service_map = self.service_map.lock().unwrap();
        if let Some(existing_service) = service_map.get(name) {
            if existing_service == service {
                return Ok(true);
            }
        }
        service_map.insert(name.into(), service.clone());
        Ok(false)
    }

    fn VerifyName(
        &self,
        service: &binder::Strong<dyn INamedCallback::INamedCallback>,
        name: &str,
    ) -> binder::Result<bool> {
        service.GetName().map(|found_name| found_name == name)
    }

    fn GetInterfaceArray(
        &self,
        names: &[String],
    ) -> binder::Result<Vec<binder::Strong<dyn INamedCallback::INamedCallback>>> {
        names.iter().map(|name| self.GetOtherTestService(name)).collect()
    }

    fn VerifyNamesWithInterfaceArray(
        &self,
        services: &[binder::Strong<dyn INamedCallback::INamedCallback>],
        names: &[String],
    ) -> binder::Result<bool> {
        if services.len() == names.len() {
            for (s, n) in services.iter().zip(names) {
                if !self.VerifyName(s, n)? {
                    return Ok(false);
                }
            }
            Ok(true)
        } else {
            Ok(false)
        }
    }

    fn GetNullableInterfaceArray(
        &self,
        names: Option<&[Option<String>]>,
    ) -> binder::Result<Option<Vec<Option<binder::Strong<dyn INamedCallback::INamedCallback>>>>>
    {
        if let Some(names) = names {
            let mut services = vec![];
            for name in names {
                if let Some(name) = name {
                    services.push(Some(self.GetOtherTestService(name)?));
                } else {
                    services.push(None);
                }
            }
            Ok(Some(services))
        } else {
            Ok(None)
        }
    }

    fn VerifyNamesWithNullableInterfaceArray(
        &self,
        services: Option<&[Option<binder::Strong<dyn INamedCallback::INamedCallback>>]>,
        names: Option<&[Option<String>]>,
    ) -> binder::Result<bool> {
        if let (Some(services), Some(names)) = (services, names) {
            for (s, n) in services.iter().zip(names) {
                if let (Some(s), Some(n)) = (s, n) {
                    if !self.VerifyName(s, n)? {
                        return Ok(false);
                    }
                } else if s.is_some() || n.is_some() {
                    return Ok(false);
                }
            }
            Ok(true)
        } else {
            Ok(services.is_none() && names.is_none())
        }
    }

    fn GetInterfaceList(
        &self,
        names: Option<&[Option<String>]>,
    ) -> binder::Result<Option<Vec<Option<binder::Strong<dyn INamedCallback::INamedCallback>>>>>
    {
        self.GetNullableInterfaceArray(names)
    }

    fn VerifyNamesWithInterfaceList(
        &self,
        services: Option<&[Option<binder::Strong<dyn INamedCallback::INamedCallback>>]>,
        names: Option<&[Option<String>]>,
    ) -> binder::Result<bool> {
        self.VerifyNamesWithNullableInterfaceArray(services, names)
    }

    fn RepeatParcelFileDescriptor(
        &self,
        read: &ParcelFileDescriptor,
    ) -> binder::Result<ParcelFileDescriptor> {
        Ok(dup_fd(read))
    }

    fn ReverseParcelFileDescriptorArray(
        &self,
        input: &[ParcelFileDescriptor],
        repeated: &mut Vec<Option<ParcelFileDescriptor>>,
    ) -> binder::Result<Vec<ParcelFileDescriptor>> {
        repeated.clear();
        repeated.extend(input.iter().map(dup_fd).map(Some));
        Ok(input.iter().rev().map(dup_fd).collect())
    }

    fn ThrowServiceException(&self, code: i32) -> binder::Result<()> {
        Err(binder::Status::new_service_specific_error(code, None))
    }

    impl_repeat_nullable! {RepeatNullableIntArray, i32}
    impl_repeat_nullable! {RepeatNullableByteEnumArray, ByteEnum}
    impl_repeat_nullable! {RepeatNullableIntEnumArray, IntEnum}
    impl_repeat_nullable! {RepeatNullableLongEnumArray, LongEnum}
    impl_repeat_nullable! {RepeatNullableStringList, Option<String>}

    fn RepeatNullableString(&self, input: Option<&str>) -> binder::Result<Option<String>> {
        Ok(input.map(String::from))
    }

    fn RepeatNullableUtf8CppString(&self, input: Option<&str>) -> binder::Result<Option<String>> {
        Ok(input.map(String::from))
    }

    fn RepeatNullableParcelable(&self, input: Option<&Empty>) -> binder::Result<Option<Empty>> {
        Ok(input.cloned())
    }

    impl_repeat_nullable! {RepeatNullableParcelableArray, Option<Empty>}
    impl_repeat_nullable! {RepeatNullableParcelableList, Option<Empty>}

    fn TakesAnIBinder(&self, _: &SpIBinder) -> binder::Result<()> {
        Ok(())
    }

    fn TakesANullableIBinder(&self, _: Option<&SpIBinder>) -> binder::Result<()> {
        Ok(())
    }

    fn TakesAnIBinderList(&self, _: &[SpIBinder]) -> binder::Result<()> {
        Ok(())
    }

    fn TakesANullableIBinderList(&self, _: Option<&[Option<SpIBinder>]>) -> binder::Result<()> {
        Ok(())
    }

    fn ReverseNullableUtf8CppString(
        &self,
        input: Option<&[Option<String>]>,
        repeated: &mut Option<Vec<Option<String>>>,
    ) -> binder::Result<Option<Vec<Option<String>>>> {
        if let Some(input) = input {
            *repeated = Some(input.to_vec());
            Ok(Some(input.iter().rev().cloned().collect()))
        } else {
            // We don't touch `repeated` here, since
            // the C++ test service doesn't either
            Ok(None)
        }
    }

    fn ReverseUtf8CppStringList(
        &self,
        input: Option<&[Option<String>]>,
        repeated: &mut Option<Vec<Option<String>>>,
    ) -> binder::Result<Option<Vec<Option<String>>>> {
        self.ReverseNullableUtf8CppString(input, repeated)
    }

    fn GetCallback(
        &self,
        return_null: bool,
    ) -> binder::Result<Option<binder::Strong<dyn INamedCallback::INamedCallback>>> {
        if return_null {
            Ok(None)
        } else {
            self.GetOtherTestService("ABT: always be testing").map(Some)
        }
    }

    fn FillOutStructuredParcelable(
        &self,
        parcelable: &mut StructuredParcelable::StructuredParcelable,
    ) -> binder::Result<()> {
        parcelable.shouldBeJerry = "Jerry".into();
        parcelable.shouldContainThreeFs = vec![parcelable.f, parcelable.f, parcelable.f];
        parcelable.shouldBeByteBar = ByteEnum::BAR;
        parcelable.shouldBeIntBar = IntEnum::BAR;
        parcelable.shouldBeLongBar = LongEnum::BAR;
        parcelable.shouldContainTwoByteFoos = vec![ByteEnum::FOO, ByteEnum::FOO];
        parcelable.shouldContainTwoIntFoos = vec![IntEnum::FOO, IntEnum::FOO];
        parcelable.shouldContainTwoLongFoos = vec![LongEnum::FOO, LongEnum::FOO];

        parcelable.const_exprs_1 = ConstantExpressionEnum::decInt32_1;
        parcelable.const_exprs_2 = ConstantExpressionEnum::decInt32_2;
        parcelable.const_exprs_3 = ConstantExpressionEnum::decInt64_1;
        parcelable.const_exprs_4 = ConstantExpressionEnum::decInt64_2;
        parcelable.const_exprs_5 = ConstantExpressionEnum::decInt64_3;
        parcelable.const_exprs_6 = ConstantExpressionEnum::decInt64_4;
        parcelable.const_exprs_7 = ConstantExpressionEnum::hexInt32_1;
        parcelable.const_exprs_8 = ConstantExpressionEnum::hexInt32_2;
        parcelable.const_exprs_9 = ConstantExpressionEnum::hexInt32_3;
        parcelable.const_exprs_10 = ConstantExpressionEnum::hexInt64_1;

        parcelable.shouldSetBit0AndBit2 = StructuredParcelable::BIT0 | StructuredParcelable::BIT2;

        parcelable.u = Some(Union::Union::Ns(vec![1, 2, 3]));
        parcelable.shouldBeConstS1 = Some(Union::Union::S(Union::S1.to_string()));
        Ok(())
    }

    fn RepeatExtendableParcelable(
        &self,
        ep: &ExtendableParcelable,
        ep2: &mut ExtendableParcelable,
    ) -> binder::Result<()> {
        ep2.a = ep.a;
        ep2.b = ep.b.clone();

        let my_ext = ep.ext.get_parcelable::<MyExt>()?;
        if let Some(my_ext) = my_ext {
            ep2.ext.set_parcelable(my_ext)?;
        } else {
            ep2.ext.reset();
        }

        Ok(())
    }

    fn ReverseList(&self, list: &RecursiveList) -> binder::Result<RecursiveList> {
        let mut reversed: Option<RecursiveList> = None;
        let mut cur: Option<&RecursiveList> = Some(list);
        while let Some(node) = cur {
            reversed = Some(RecursiveList { value: node.value, next: reversed.map(Box::new) });
            cur = node.next.as_ref().map(|n| n.as_ref());
        }
        // `list` is always not empty, so is `reversed`.
        Ok(reversed.unwrap())
    }

    fn ReverseIBinderArray(
        &self,
        input: &[SpIBinder],
        repeated: &mut Vec<Option<SpIBinder>>,
    ) -> binder::Result<Vec<SpIBinder>> {
        *repeated = input.iter().cloned().map(Some).collect();
        Ok(input.iter().rev().cloned().collect())
    }

    fn ReverseNullableIBinderArray(
        &self,
        input: Option<&[Option<SpIBinder>]>,
        repeated: &mut Option<Vec<Option<SpIBinder>>>,
    ) -> binder::Result<Option<Vec<Option<SpIBinder>>>> {
        let input = input.expect("input is null");
        *repeated = Some(input.to_vec());
        Ok(Some(input.iter().rev().cloned().collect()))
    }

    fn RepeatSimpleParcelable(
        &self,
        input: &SimpleParcelable,
        repeat: &mut SimpleParcelable,
    ) -> binder::Result<SimpleParcelable> {
        *repeat = input.clone();
        Ok(input.clone())
    }

    fn ReverseSimpleParcelables(
        &self,
        input: &[SimpleParcelable],
        repeated: &mut Vec<SimpleParcelable>,
    ) -> binder::Result<Vec<SimpleParcelable>> {
        *repeated = input.to_vec();
        Ok(input.iter().rev().cloned().collect())
    }

    fn GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn IOldName::IOldName>> {
        Ok(IOldName::BnOldName::new_binder(OldName, BinderFeatures::default()))
    }

    fn GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn INewName::INewName>> {
        Ok(INewName::BnNewName::new_binder(NewName, BinderFeatures::default()))
    }

    fn GetUnionTags(&self, input: &[Union::Union]) -> binder::Result<Vec<Union::Tag::Tag>> {
        Ok(input
            .iter()
            .map(|u| match u {
                Union::Union::Ns(_) => Union::Tag::Tag::ns,
                Union::Union::N(_) => Union::Tag::Tag::n,
                Union::Union::M(_) => Union::Tag::Tag::m,
                Union::Union::S(_) => Union::Tag::Tag::s,
                Union::Union::Ibinder(_) => Union::Tag::Tag::ibinder,
                Union::Union::Ss(_) => Union::Tag::Tag::ss,
                Union::Union::Be(_) => Union::Tag::Tag::be,
            })
            .collect::<Vec<_>>())
    }

    fn GetCppJavaTests(&self) -> binder::Result<Option<SpIBinder>> {
        Ok(None)
    }

    fn getBackendType(&self) -> binder::Result<BackendType> {
        Ok(BackendType::RUST)
    }

    fn GetCircular(
        &self,
        _: &mut CircularParcelable,
    ) -> binder::Result<binder::Strong<dyn ICircular::ICircular>> {
        Ok(ICircular::BnCircular::new_binder(Circular, BinderFeatures::default()))
    }
}

struct FooInterface;

impl Interface for FooInterface {}

impl IFooInterface::IFooInterface for FooInterface {
    fn originalApi(&self) -> binder::Result<()> {
        Ok(())
    }
    fn acceptUnionAndReturnString(&self, u: &BazUnion) -> binder::Result<String> {
        match u {
            BazUnion::IntNum(n) => Ok(n.to_string()),
        }
    }
    fn returnsLengthOfFooArray(&self, foos: &[Foo]) -> binder::Result<i32> {
        Ok(foos.len() as i32)
    }
    fn ignoreParcelablesAndRepeatInt(
        &self,
        _in_foo: &Foo,
        _inout_foo: &mut Foo,
        _out_foo: &mut Foo,
        value: i32,
    ) -> binder::Result<i32> {
        Ok(value)
    }
}

struct TrunkStableTest;

impl Interface for TrunkStableTest {}

impl ITrunkStableTest::ITrunkStableTest for TrunkStableTest {
    fn repeatParcelable(&self, in_parcel: &MyParcelable) -> binder::Result<MyParcelable> {
        let tmp: MyParcelable = MyParcelable { a: in_parcel.a, b: in_parcel.b, c: in_parcel.c };
        Ok(tmp)
    }
    fn repeatEnum(&self, in_enum: MyEnum) -> binder::Result<MyEnum> {
        Ok(in_enum)
    }
    fn repeatUnion(&self, in_union: &MyUnion) -> binder::Result<MyUnion> {
        match in_union {
            MyUnion::A(n) => Ok(MyUnion::A(*n)),
            MyUnion::B(n) => Ok(MyUnion::B(*n)),
            MyUnion::C(n) => Ok(MyUnion::C(*n)),
        }
    }
    fn repeatOtherParcelable(
        &self,
        in_parcel: &MyOtherParcelable,
    ) -> binder::Result<MyOtherParcelable> {
        let tmp: MyOtherParcelable = MyOtherParcelable { a: in_parcel.a, b: in_parcel.b };
        Ok(tmp)
    }
    fn callMyCallback(
        &self,
        _in_cb: &binder::Strong<dyn IMyCallback::IMyCallback>,
    ) -> binder::Result<()> {
        let p1 = MyParcelable::default();
        let _ = _in_cb.repeatParcelable(&p1);

        let e1 = MyEnum::THREE;
        let _ = _in_cb.repeatEnum(e1);

        let u1 = MyUnion::A(12);
        let _ = _in_cb.repeatUnion(&u1);

        let o1 = MyOtherParcelable::default();
        // expected to fail when not using the frozen version
        let _ = _in_cb.repeatOtherParcelable(&o1);

        Ok(())
    }
}

struct NestedService;

impl Interface for NestedService {}

impl INestedService::INestedService for NestedService {
    fn flipStatus(
        &self,
        p: &ParcelableWithNested::ParcelableWithNested,
    ) -> binder::Result<INestedService::Result::Result> {
        if p.status == ParcelableWithNested::Status::Status::OK {
            Ok(INestedService::Result::Result {
                status: ParcelableWithNested::Status::Status::NOT_OK,
            })
        } else {
            Ok(INestedService::Result::Result { status: ParcelableWithNested::Status::Status::OK })
        }
    }
    fn flipStatusWithCallback(
        &self,
        st: ParcelableWithNested::Status::Status,
        cb: &binder::Strong<dyn INestedService::ICallback::ICallback>,
    ) -> binder::Result<()> {
        if st == ParcelableWithNested::Status::Status::OK {
            cb.done(ParcelableWithNested::Status::Status::NOT_OK)
        } else {
            cb.done(ParcelableWithNested::Status::Status::OK)
        }
    }
}

struct FixedSizeArrayService;

impl Interface for FixedSizeArrayService {}

impl IRepeatFixedSizeArray::IRepeatFixedSizeArray for FixedSizeArrayService {
    fn RepeatBytes(&self, input: &[u8; 3], repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]> {
        *repeated = *input;
        Ok(*input)
    }
    fn RepeatInts(&self, input: &[i32; 3], repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]> {
        *repeated = *input;
        Ok(*input)
    }
    fn RepeatBinders(
        &self,
        input: &[SpIBinder; 3],
        repeated: &mut [Option<SpIBinder>; 3],
    ) -> binder::Result<[SpIBinder; 3]> {
        *repeated = input.clone().map(Some);
        Ok(input.clone())
    }
    fn RepeatParcelables(
        &self,
        input: &[IntParcelable; 3],
        repeated: &mut [IntParcelable; 3],
    ) -> binder::Result<[IntParcelable; 3]> {
        *repeated = *input;
        Ok(*input)
    }
    fn Repeat2dBytes(
        &self,
        input: &[[u8; 3]; 2],
        repeated: &mut [[u8; 3]; 2],
    ) -> binder::Result<[[u8; 3]; 2]> {
        *repeated = *input;
        Ok(*input)
    }
    fn Repeat2dInts(
        &self,
        input: &[[i32; 3]; 2],
        repeated: &mut [[i32; 3]; 2],
    ) -> binder::Result<[[i32; 3]; 2]> {
        *repeated = *input;
        Ok(*input)
    }
    fn Repeat2dBinders(
        &self,
        input: &[[SpIBinder; 3]; 2],
        repeated: &mut [[Option<SpIBinder>; 3]; 2],
    ) -> binder::Result<[[SpIBinder; 3]; 2]> {
        *repeated = input.clone().map(|nested| nested.map(Some));
        Ok(input.clone())
    }
    fn Repeat2dParcelables(
        &self,
        input: &[[IntParcelable; 3]; 2],
        repeated: &mut [[IntParcelable; 3]; 2],
    ) -> binder::Result<[[IntParcelable; 3]; 2]> {
        *repeated = *input;
        Ok(*input)
    }
}

fn main() {
    binder::ProcessState::set_thread_pool_max_thread_count(0);
    binder::ProcessState::start_thread_pool();

    let service_name = <BpTestService as ITestService::ITestService>::get_descriptor();
    let service = BnTestService::new_binder(TestService::default(), BinderFeatures::default());
    binder::add_service(service_name, service.as_binder()).expect("Could not register service");

    let versioned_service_name = <BpFooInterface as IFooInterface::IFooInterface>::get_descriptor();
    let versioned_service = BnFooInterface::new_binder(FooInterface, BinderFeatures::default());
    binder::add_service(versioned_service_name, versioned_service.as_binder())
        .expect("Could not register service");

    let nested_service_name =
        <INestedService::BpNestedService as INestedService::INestedService>::get_descriptor();
    let nested_service =
        INestedService::BnNestedService::new_binder(NestedService, BinderFeatures::default());
    binder::add_service(nested_service_name, nested_service.as_binder())
        .expect("Could not register service");

    let fixed_size_array_service_name =
        <IRepeatFixedSizeArray::BpRepeatFixedSizeArray as IRepeatFixedSizeArray::IRepeatFixedSizeArray>::get_descriptor();
    let fixed_size_array_service = IRepeatFixedSizeArray::BnRepeatFixedSizeArray::new_binder(
        FixedSizeArrayService,
        BinderFeatures::default(),
    );
    binder::add_service(fixed_size_array_service_name, fixed_size_array_service.as_binder())
        .expect("Could not register service");

    let trunk_stable_service_name =
        <BpTrunkStableTest as ITrunkStableTest::ITrunkStableTest>::get_descriptor();
    let trunk_stable_service =
        BnTrunkStableTest::new_binder(TrunkStableTest, BinderFeatures::default());
    binder::add_service(trunk_stable_service_name, trunk_stable_service.as_binder())
        .expect("Could not register service");

    binder::ProcessState::join_thread_pool();
}
