// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Derive macro for `AsCborValue`.
use proc_macro2::TokenStream;
use quote::{quote, quote_spanned};
use syn::{
    parse_macro_input, parse_quote, spanned::Spanned, Data, DeriveInput, Fields, GenericParam,
    Generics, Index,
};

/// Derive macro that implements the `AsCborValue` trait.  Using this macro requires
/// that `AsCborValue`, `CborError` and `cbor_type_error` are locally `use`d.
#[proc_macro_derive(AsCborValue)]
pub fn derive_as_cbor_value(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    derive_as_cbor_value_internal(&input)
}

fn derive_as_cbor_value_internal(input: &DeriveInput) -> proc_macro::TokenStream {
    let name = &input.ident;

    // Add a bound `T: AsCborValue` for every type parameter `T`.
    let generics = add_trait_bounds(&input.generics);
    let (impl_generics, ty_generics, where_clause) = generics.split_for_impl();

    let from_val = from_val_struct(&input.data);
    let to_val = to_val_struct(&input.data);

    let expanded = quote! {
        // The generated impl
        impl #impl_generics AsCborValue for #name #ty_generics #where_clause {
            fn from_cbor_value(value: ciborium::value::Value) -> Result<Self, CborError> {
                #from_val
            }
            fn to_cbor_value(self) -> Result<ciborium::value::Value, CborError> {
                #to_val
            }
        }
    };

    expanded.into()
}

/// Add a bound `T: AsCborValue` for every type parameter `T`.
fn add_trait_bounds(generics: &Generics) -> Generics {
    let mut generics = generics.clone();
    for param in &mut generics.params {
        if let GenericParam::Type(ref mut type_param) = *param {
            type_param.bounds.push(parse_quote!(AsCborValue));
        }
    }
    generics
}

/// Generate an expression to convert an instance of a compound type to `ciborium::value::Value`
fn to_val_struct(data: &Data) -> TokenStream {
    match *data {
        Data::Struct(ref data) => {
            match data.fields {
                Fields::Named(ref fields) => {
                    // Expands to an expression like
                    //
                    //     {
                    //         let mut v = Vec::new();
                    //         v.try_reserve(3).map_err(|_e| CborError::AllocationFailed)?;
                    //         v.push(AsCborValue::to_cbor_value(self.x)?);
                    //         v.push(AsCborValue::to_cbor_value(self.y)?);
                    //         v.push(AsCborValue::to_cbor_value(self.z)?);
                    //         Ok(ciborium::value::Value::Array(v))
                    //     }
                    let nfields = fields.named.len();
                    let recurse = fields.named.iter().map(|f| {
                        let name = &f.ident;
                        quote_spanned! {f.span()=>
                            v.push(AsCborValue::to_cbor_value(self.#name)?)
                        }
                    });
                    quote! {
                        {
                            let mut v = Vec::new();
                            v.try_reserve(#nfields).map_err(|_e| CborError::AllocationFailed)?;
                            #(#recurse; )*
                            Ok(ciborium::value::Value::Array(v))
                        }
                    }
                }
                Fields::Unnamed(_) => unimplemented!(),
                Fields::Unit => unimplemented!(),
            }
        }
        Data::Enum(_) => {
            quote! {
                let v: ciborium::value::Integer = (self as i32).into();
                Ok(ciborium::value::Value::Integer(v))
            }
        }
        Data::Union(_) => unimplemented!(),
    }
}

/// Generate an expression to convert a `ciborium::value::Value` into an instance of a compound
/// type.
fn from_val_struct(data: &Data) -> TokenStream {
    match data {
        Data::Struct(ref data) => {
            match data.fields {
                Fields::Named(ref fields) => {
                    // Expands to an expression like
                    //
                    //     let mut a = match value {
                    //         ciborium::value::Value::Array(a) => a,
                    //         _ => return cbor_type_error(&value, "arr"),
                    //     };
                    //     if a.len() != 3 {
                    //         return Err(CborError::UnexpectedItem("arr", "arr len 3"));
                    //     }
                    //     // Fields specified in reverse order to reduce shifting.
                    //     Ok(Self {
                    //         z: <ZType>::from_cbor_value(a.remove(2))?,
                    //         y: <YType>::from_cbor_value(a.remove(1))?,
                    //         x: <XType>::from_cbor_value(a.remove(0))?,
                    //     })
                    //
                    // but using fully qualified function call syntax.
                    let nfields = fields.named.len();
                    let recurse = fields.named.iter().enumerate().rev().map(|(i, f)| {
                        let name = &f.ident;
                        let index = Index::from(i);
                        let typ = &f.ty;
                        quote_spanned! {f.span()=>
                                        #name: <#typ>::from_cbor_value(a.remove(#index))?
                        }
                    });
                    quote! {
                        let mut a = match value {
                            ciborium::value::Value::Array(a) => a,
                            _ => return cbor_type_error(&value, "arr"),
                        };
                        if a.len() != #nfields {
                            return Err(CborError::UnexpectedItem(
                                "arr",
                                concat!("arr len ", stringify!(#nfields)),
                            ));
                        }
                        // Fields specified in reverse order to reduce shifting.
                        Ok(Self {
                            #(#recurse, )*
                        })
                    }
                }
                Fields::Unnamed(_) => unimplemented!(),
                Fields::Unit => unimplemented!(),
            }
        }
        Data::Enum(enum_data) => {
            // This only copes with variants with no fields.
            // Expands to an expression like:
            //
            //     use core::convert::TryInto;
            //     let v: i32 = match value {
            //         ciborium::value::Value::Integer(i) => i.try_into().map_err(|_| {
            //             CborError::OutOfRangeIntegerValue
            //         })?,
            //         v => return cbor_type_error(&v, &"int"),
            //     };
            //     match v {
            //         x if x == Self::Variant1 as i32 => Ok(Self::Variant1),
            //         x if x == Self::Variant2 as i32 => Ok(Self::Variant2),
            //         x if x == Self::Variant3 as i32 => Ok(Self::Variant3),
            //         _ => Err( CborError::OutOfRangeIntegerValue),
            //     }
            let recurse = enum_data.variants.iter().map(|variant| {
                let vname = &variant.ident;
                quote_spanned! {variant.span()=>
                                x if x == Self::#vname as i32 => Ok(Self::#vname),
                }
            });

            quote! {
                use core::convert::TryInto;
                // First get the int value as an `i32`.
                let v: i32 = match value {
                    ciborium::value::Value::Integer(i) => i.try_into().map_err(|_| {
                        CborError::OutOfRangeIntegerValue
                    })?,
                    v => return cbor_type_error(&v, &"int"),
                };
                // Now match against enum possibilities.
                match v {
                    #(#recurse)*
                    _ => Err(
                        CborError::OutOfRangeIntegerValue
                    ),
                }
            }
        }
        Data::Union(_) => unimplemented!(),
    }
}
