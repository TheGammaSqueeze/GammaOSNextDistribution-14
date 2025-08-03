//! Macro for topshim

extern crate proc_macro;

use proc_macro::TokenStream;
use quote::{format_ident, quote};
use syn::parse::{Parse, ParseStream, Result};
use syn::{parse_macro_input, Block, Ident, Path, Stmt, Token, Type};

/// Parsed structure for callback variant
struct CbVariant {
    dispatcher: Type,
    fn_pair: (Ident, Path),
    arg_pairs: Vec<(Type, Option<Type>)>,
    stmts: Vec<Stmt>,
}

impl Parse for CbVariant {
    fn parse(input: ParseStream) -> Result<Self> {
        // First thing should be the dispatcher
        let dispatcher: Type = input.parse()?;
        input.parse::<Token![,]>()?;

        // Name and return type are parsed
        let name: Ident = input.parse()?;
        input.parse::<Token![->]>()?;
        let rpath: Path = input.parse()?;

        let mut arg_pairs: Vec<(Type, Option<Type>)> = Vec::new();
        let mut stmts: Vec<Stmt> = Vec::new();

        while input.peek(Token![,]) {
            // Discard the comma
            input.parse::<Token![,]>()?;

            // Check if we're expecting the final Block
            if input.peek(syn::token::Brace) {
                let block: Block = input.parse()?;
                stmts.extend(block.stmts);

                break;
            }

            // Grab the next type argument
            let start_type: Type = input.parse()?;

            if input.peek(Token![->]) {
                // Discard ->
                input.parse::<Token![->]>()?;

                // Try to parse Token![_]. If that works, we will
                // consume this value and not pass it forward.
                // Otherwise, try to parse as syn::Type and pass forward for
                // conversion.
                if input.peek(Token![_]) {
                    input.parse::<Token![_]>()?;
                    arg_pairs.push((start_type, None));
                } else {
                    let end_type: Type = input.parse()?;
                    arg_pairs.push((start_type, Some(end_type)));
                }
            } else {
                arg_pairs.push((start_type.clone(), Some(start_type)));
            }
        }

        // TODO: Validate there are no more tokens; currently they are ignored.
        Ok(CbVariant { dispatcher, fn_pair: (name, rpath), arg_pairs, stmts })
    }
}

#[proc_macro]
/// Implement C function to convert callback into enum variant.
///
/// Expected syntax:
///     ```compile_fail
///     cb_variant(DispatcherType, function_name -> EnumType::Variant, args..., {
///         // Statements (maybe converting types)
///         // Args in order will be _0, _1, etc.
///     })
///     ```
///
/// args can do conversions inline as well. In order for conversions to work, the relevant
/// From<T> trait should also be implemented.
///
/// Example:
///     u32 -> BtStatus (requires impl From<u32> for BtStatus)
///
/// To consume a value during conversion, you can use "Type -> _". This is useful when you want
/// to convert a pointer + size into a single Vec (i.e. using ptr_to_vec).
///
/// Example:
///     u32 -> _
pub fn cb_variant(input: TokenStream) -> TokenStream {
    let parsed_cptr = parse_macro_input!(input as CbVariant);

    let dispatcher = parsed_cptr.dispatcher;
    let (ident, rpath) = parsed_cptr.fn_pair;

    let mut params = proc_macro2::TokenStream::new();
    let mut args = proc_macro2::TokenStream::new();
    for (i, (start, end)) in parsed_cptr.arg_pairs.iter().enumerate() {
        let ident = format_ident!("_{}", i);
        params.extend(quote! { #ident: #start, });

        match end {
            Some(v) => {
                // Argument needs an into translation if it doesn't match the start
                if start != v {
                    args.extend(quote! { #end::from(#ident), });
                } else {
                    args.extend(quote! {#ident,});
                }
            }
            // If there's no end type, just consume it instead.
            None => (),
        }
    }

    let mut stmts = proc_macro2::TokenStream::new();
    for stmt in parsed_cptr.stmts {
        stmts.extend(quote! { #stmt });
    }

    let dispatcher_str = quote!(#dispatcher).to_string();
    let tokens = quote! {
        #[no_mangle]
        extern "C" fn #ident(#params) {
            #stmts
                (get_dispatchers()
                    .lock()
                    .expect("Couldn't lock dispatchers!")
                    .get::<#dispatcher>()
                    .expect(concat!("Couldn't find dispatcher type: ", #dispatcher_str))
                    .clone()
                    .lock()
                    .expect(concat!("Couldn't lock specific dispatcher: ", #dispatcher_str))
                    .dispatch)(#rpath(#args));
            }
    };

    TokenStream::from(tokens)
}

// TODO: Replace below macro with a public crate, such as https://crates.io/crates/adorn
#[proc_macro_attribute]
/// Macro to check if the profile has been initialized
///
/// Function who applies this macro should also include log::warn and the self must implement
/// fn is_initialized(&self) -> bool
///
/// Example:
///     ```
///     use log::warn;
///     #[profile_enabled_or]
///     fn foo(&self) {
///         // actual code
///     }
///     ```
///     expands as
///     ```
///     use log::warn;
///     fn foo(&self) {
///         if !self.is_enabled() {
///             warn!("Tried to {} but internal hasn't been enabled", "foo");
///             return ;
///         }
///         // actual code
///     }
///     ```
/// One can specify a return value on uninitialized case
///     ```
///     use log::warn;
///     #[profile_enabled_or("not ready")]
///     fn foo(&self) -> &str {
///         // actual code
///     }
///     ```
///     expands as
///     ```
///     use log::warn;
///     fn foo(&self) -> &str {
///         if !self.is_enabled() {
///             warn!("Tried to {} but internal hasn't been enabled", "foo");
///             return "not ready";
///         }
///         // actual code
///         return "success"
///     }
///     ```
pub fn profile_enabled_or(attr: TokenStream, item: TokenStream) -> TokenStream {
    generate_profile_enabled_or_tokenstream(item, attr.to_string())
}

/// Similar to profile_enabled_or but return Default::default() when profile is not enabled.
#[proc_macro_attribute]
pub fn profile_enabled_or_default(_attr: TokenStream, item: TokenStream) -> TokenStream {
    generate_profile_enabled_or_tokenstream(item, String::from("Default::default()"))
}

fn generate_profile_enabled_or_tokenstream(item: TokenStream, attr_string: String) -> TokenStream {
    let mut input = syn::parse_macro_input!(item as syn::ItemFn);

    let fn_name = input.sig.ident.to_string();

    let ret_stmt: proc_macro2::TokenStream = format!("return {};", attr_string).parse().unwrap();

    let check_block = quote::quote! {
        if !self.is_enabled() {
            warn!("Tried to {} but internal hasn't been enabled", #fn_name);
            #ret_stmt
        }
    };

    input.block.stmts.insert(0, syn::parse(check_block.into()).unwrap());

    let output = quote::quote! {
        #input
    };

    output.into()
}
