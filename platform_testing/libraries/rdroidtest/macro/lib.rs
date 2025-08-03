//! Attribute proc macro for rdroidtest instances.
use proc_macro::TokenStream;
use proc_macro2::TokenStream as TokenStream2;
use quote::{quote, ToTokens};
use syn::{parse_macro_input, ItemFn, Meta};

/// Macro to mark an `rdroidtest` test function.  Can take one optional argument, an expression that
/// evaluates to a `Vec` of parameter (name, value) pairs.
///
/// Also detects `#[ignore]` and `#[ignore_if(<expr>)]` attributes on the test function.
#[proc_macro_attribute]
pub fn rdroidtest(args: TokenStream, item: TokenStream) -> TokenStream {
    // Only accept code that parses as a function definition.
    let item = parse_macro_input!(item as ItemFn);
    let fn_name = &item.sig.ident;

    // If the attribute has any arguments, they are expected to be a parameter generator expression.
    let param_gen: Option<TokenStream2> = if args.is_empty() { None } else { Some(args.into()) };

    // Look for `#[ignore]` and `#[ignore_if(<expr>)]` attributes on the wrapped item.
    let mut ignore_if: Option<TokenStream2> = None;
    let mut ignored = false;
    for attr in &item.attrs {
        match &attr.meta {
            Meta::Path(path) if path.to_token_stream().to_string().as_str() == "ignore" => {
                // `#[ignore]` attribute.
                ignored = true;
            }
            Meta::List(list) if list.path.to_token_stream().to_string().as_str() == "ignore_if" => {
                // `#[ignore_if(<expr>)]` attribute.
                ignore_if = Some(list.tokens.clone());
            }
            _ => {}
        }
    }
    if ignored {
        // `#[ignore]` trumps any specified `#[ignore_if]`.
        ignore_if = Some(if param_gen.is_some() {
            // `ignore_if` needs to be something invoked with a single parameter.
            quote! { |_p| true }.into_iter().collect()
        } else {
            quote! { true }.into_iter().collect()
        });
    }

    // Build up an invocation of the appropriate `rdroidtest` declarative macro.
    let invocation = match (param_gen, ignore_if) {
        (Some(pg), Some(ii)) => quote! { ::rdroidtest::ptest!( #fn_name, #pg, ignore_if: #ii ); },
        (Some(pg), None) => quote! { ::rdroidtest::ptest!( #fn_name, #pg ); },
        (None, Some(ii)) => quote! { ::rdroidtest::test!( #fn_name, ignore_if: #ii ); },
        (None, None) => quote! { ::rdroidtest::test!( #fn_name ); },
    };

    let mut stream = TokenStream2::new();
    stream.extend([invocation]);
    stream.extend(item.into_token_stream());
    stream.into_token_stream().into()
}

/// Macro to mark conditions for ignoring an `rdroidtest` test function.  Expands to nothing here,
/// scanned for by the [`rdroidtest`] attribute macro.
#[proc_macro_attribute]
pub fn ignore_if(_args: TokenStream, item: TokenStream) -> TokenStream {
    item
}
