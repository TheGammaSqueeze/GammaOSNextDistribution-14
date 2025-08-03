use core::any::Any;
use std::collections::HashMap;

use dbus_macros::{dbus_propmap, generate_dbus_arg};

use dbus::arg::{Arg, ArgType, IterAppend};
use dbus::Signature;

generate_dbus_arg!();

#[derive(Debug, Default, Clone, PartialEq)]
struct OtherStruct {
    address: String,
}

#[dbus_propmap(OtherStruct)]
struct OtherStructDBus {
    address: String,
}

#[derive(Debug, Default, Clone, PartialEq)]
struct SomeStruct {
    name: String,
    number: i32,
    other_struct: OtherStruct,
    bytes: Vec<u8>,
    dict: HashMap<String, Vec<i32>>,
    nested: Vec<Vec<String>>,
    recursive: Vec<SomeStruct>,
}

#[dbus_propmap(SomeStruct)]
struct SomeStructDBus {
    name: String,
    number: i32,
    other_struct: OtherStruct,
    bytes: Vec<u8>,
    dict: HashMap<String, Vec<i32>>,
    nested: Vec<Vec<String>>,
    recursive: Vec<SomeStruct>,
}

// Pretends to be a D-Bus dictionary.
#[derive(Debug)]
struct FakeDictionary {
    items: Vec<(String, Box<dyn RefArg>)>,
}

impl RefArg for FakeDictionary {
    fn arg_type(&self) -> ArgType {
        todo!()
    }
    fn signature(&self) -> dbus::Signature<'static> {
        todo!()
    }
    fn append(&self, _: &mut IterAppend<'_>) {
        todo!()
    }
    fn as_any(&self) -> &(dyn Any + 'static) {
        todo!()
    }
    fn as_any_mut(&mut self) -> &mut (dyn Any + 'static) {
        todo!()
    }
    fn box_clone(&self) -> Box<dyn RefArg + 'static> {
        Box::new(FakeDictionary {
            items: self.items.iter().map(|(k, v)| (k.clone(), v.box_clone())).collect(),
        })
    }

    fn as_iter<'b>(&'b self) -> Option<Box<dyn Iterator<Item = &'b dyn RefArg> + 'b>> {
        Some(Box::new(
            self.items
                .iter()
                .flat_map(|(k, v)| vec![k as &dyn RefArg, v as &dyn RefArg].into_iter()),
        ))
    }
}

impl Arg for FakeDictionary {
    const ARG_TYPE: ArgType = ArgType::Array;
    fn signature() -> dbus::Signature<'static> {
        Signature::from("a{sv}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dbus_propmap_error() {
        let data_dbus = String::from("some data");
        let result = <dbus::arg::PropMap as RefArgToRust>::ref_arg_to_rust(
            &data_dbus,
            String::from("Some Variable"),
        );
        assert!(result.is_err());
        assert_eq!("Some Variable is not iterable", result.unwrap_err().to_string());
    }

    fn wrap_variant<T: 'static + dbus::arg::RefArg>(data: T) -> Box<dyn RefArg> {
        Box::new(dbus::arg::Variant(data))
    }

    #[test]
    fn test_dbus_propmap_success() {
        let data_dbus = FakeDictionary {
            items: vec![
                (String::from("name"), wrap_variant(String::from("foo"))),
                (String::from("number"), wrap_variant(100)),
                (
                    String::from("other_struct"),
                    wrap_variant(FakeDictionary {
                        items: vec![(
                            String::from("address"),
                            wrap_variant(String::from("aa:bb:cc:dd:ee:ff")),
                        )],
                    }),
                ),
                (String::from("bytes"), wrap_variant(vec![1 as u8, 2, 3])),
                (
                    String::from("dict"),
                    wrap_variant(HashMap::from([
                        (String::from("key-0"), Box::new(vec![5, 6, 7, 8])),
                        (String::from("key-1"), Box::new(vec![-5, -6, -7, -8])),
                    ])),
                ),
                (
                    String::from("nested"),
                    wrap_variant(vec![
                        vec![
                            String::from("string a"),
                            String::from("string b"),
                            String::from("string c"),
                        ],
                        vec![String::from("string 1"), String::from("string 2")],
                    ]),
                ),
                (
                    String::from("recursive"),
                    wrap_variant(vec![FakeDictionary {
                        items: vec![
                            (String::from("name"), wrap_variant(String::from("bar"))),
                            (String::from("number"), wrap_variant(200)),
                            (
                                String::from("other_struct"),
                                wrap_variant(FakeDictionary {
                                    items: vec![(
                                        String::from("address"),
                                        wrap_variant(String::from("xx")),
                                    )],
                                }),
                            ),
                            (String::from("bytes"), wrap_variant(Vec::<u8>::new())),
                            (
                                String::from("dict"),
                                wrap_variant(HashMap::from([
                                    (String::from("key-2"), Box::new(vec![5, 5, 6, 8, 8])),
                                    (String::from("key-3"), Box::new(vec![])),
                                ])),
                            ),
                            (String::from("nested"), wrap_variant(Vec::<Vec<u8>>::new())),
                            (String::from("recursive"), wrap_variant(Vec::<FakeDictionary>::new())),
                        ],
                    }]),
                ),
            ],
        };
        let result = <dbus::arg::PropMap as RefArgToRust>::ref_arg_to_rust(
            &data_dbus,
            String::from("Some Variable"),
        );
        assert!(result.is_ok());
        let result = result.unwrap();
        let result_struct = <SomeStruct as DBusArg>::from_dbus(result, None, None, None).unwrap();
        let expected_struct = SomeStruct {
            name: String::from("foo"),
            number: 100,
            other_struct: OtherStruct { address: String::from("aa:bb:cc:dd:ee:ff") },
            bytes: vec![1, 2, 3],
            dict: HashMap::from([
                (String::from("key-0"), vec![5, 6, 7, 8]),
                (String::from("key-1"), vec![-5, -6, -7, -8]),
            ]),
            nested: vec![
                vec![String::from("string a"), String::from("string b"), String::from("string c")],
                vec![String::from("string 1"), String::from("string 2")],
            ],
            recursive: vec![SomeStruct {
                name: String::from("bar"),
                number: 200,
                other_struct: OtherStruct { address: String::from("xx") },
                bytes: vec![],
                dict: HashMap::from([
                    (String::from("key-2"), vec![5, 5, 6, 8, 8]),
                    (String::from("key-3"), vec![]),
                ]),
                nested: vec![],
                recursive: vec![],
            }],
        };
        assert_eq!(expected_struct, result_struct);
    }
}
