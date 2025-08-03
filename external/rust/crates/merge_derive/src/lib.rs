// SPDX-FileCopyrightText: 2020 Robin Krahl <robin.krahl@ireas.org>
// SPDX-License-Identifier: Apache-2.0 or MIT

//! A derive macro for the [`merge::Merge`][] trait.
//!
//! See the documentation for the [`merge`][] crate for more information.
//!
//! [`merge`]: https://lib.rs/crates/merge
//! [`merge::Merge`]: https://docs.rs/merge/latest/merge/trait.Merge.html

extern crate proc_macro;

use proc_macro2::TokenStream;
use quote::{quote, quote_spanned};
use std::convert::TryFrom;
use syn::{Error, Result, Token};

struct Field {
    name: syn::Member,
    span: proc_macro2::Span,
    attrs: FieldAttrs,
}

#[derive(Default)]
struct FieldAttrs {
    skip: bool,
    strategy: Option<syn::Path>,
}

enum FieldAttr {
    Skip,
    Strategy(syn::Path),
}

#[proc_macro_derive(Merge, attributes(merge))]
pub fn merge_derive(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let ast = syn::parse(input).unwrap();
    impl_merge(&ast)
        .unwrap_or_else(Error::into_compile_error)
        .into()
}

fn impl_merge(ast: &syn::DeriveInput) -> Result<TokenStream> {
    let name = &ast.ident;

    if let syn::Data::Struct(syn::DataStruct { ref fields, .. }) = ast.data {
        impl_merge_for_struct(name, fields)
    } else {
        Err(Error::new_spanned(
            ast,
            "merge::Merge can only be derived for structs",
        ))
    }
}

fn impl_merge_for_struct(name: &syn::Ident, fields: &syn::Fields) -> Result<TokenStream> {
    let assignments = gen_assignments(fields)?;

    Ok(quote! {
        impl ::merge::Merge for #name {
            fn merge(&mut self, other: Self) {
                #assignments
            }
        }
    })
}

fn gen_assignments(fields: &syn::Fields) -> Result<TokenStream> {
    let fields = fields
        .iter()
        .enumerate()
        .map(Field::try_from)
        .collect::<Result<Vec<_>>>()?;
    let assignments = fields
        .iter()
        .filter(|f| !f.attrs.skip)
        .map(|f| gen_assignment(&f));
    Ok(quote! {
        #( #assignments )*
    })
}

fn gen_assignment(field: &Field) -> TokenStream {
    use syn::spanned::Spanned;

    let name = &field.name;
    if let Some(strategy) = &field.attrs.strategy {
        quote_spanned!(strategy.span()=> #strategy(&mut self.#name, other.#name);)
    } else {
        quote_spanned!(field.span=> ::merge::Merge::merge(&mut self.#name, other.#name);)
    }
}

impl TryFrom<(usize, &syn::Field)> for Field {
    type Error = syn::Error;

    fn try_from(data: (usize, &syn::Field)) -> std::result::Result<Self, Self::Error> {
        use syn::spanned::Spanned;

        let (index, field) = data;
        Ok(Field {
            name: if let Some(ident) = &field.ident {
                syn::Member::Named(ident.clone())
            } else {
                syn::Member::Unnamed(index.into())
            },
            span: field.span(),
            attrs: FieldAttrs::new(field.attrs.iter())?,
        })
    }
}

impl FieldAttrs {
    fn new<'a, I: Iterator<Item = &'a syn::Attribute>>(iter: I) -> Result<Self> {
        let mut field_attrs = Self::default();

        for attr in iter {
            if !attr.path().is_ident("merge") {
                continue;
            }

            let parser = syn::punctuated::Punctuated::<FieldAttr, Token![,]>::parse_terminated;
            for attr in attr.parse_args_with(parser)? {
                field_attrs.apply(attr);
            }
        }

        Ok(field_attrs)
    }

    fn apply(&mut self, attr: FieldAttr) {
        match attr {
            FieldAttr::Skip => self.skip = true,
            FieldAttr::Strategy(path) => self.strategy = Some(path),
        }
    }
}

impl syn::parse::Parse for FieldAttr {
    fn parse(input: syn::parse::ParseStream) -> syn::parse::Result<Self> {
        let name: syn::Ident = input.parse()?;
        if name == "skip" {
            // TODO check remaining stream
            Ok(FieldAttr::Skip)
        } else if name == "strategy" {
            let _: Token![=] = input.parse()?;
            let path: syn::Path = input.parse()?;
            Ok(FieldAttr::Strategy(path))
        } else {
            Err(Error::new_spanned(
                &name,
                format!("Unexpected attribute: {}", name),
            ))
        }
    }
}
