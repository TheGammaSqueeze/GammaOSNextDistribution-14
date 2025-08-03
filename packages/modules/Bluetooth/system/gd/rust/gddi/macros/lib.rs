//! Core dependency injection macros

extern crate proc_macro;
use proc_macro::TokenStream;
use quote::{format_ident, quote};
use syn::parse::{Parse, ParseStream, Result};
use syn::punctuated::Punctuated;
use syn::{
    braced, parse, parse_macro_input, DeriveInput, Fields, FnArg, Ident, ItemFn, ItemStruct, Path,
    Token, Type,
};

/// Defines a provider function, with generated helper that implicitly fetches argument instances from the registry
#[proc_macro_attribute]
#[allow(clippy::redundant_clone)]
pub fn provides(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let function: ItemFn = parse(item).expect("can only be applied to functions");

    // Create the info needed to refer to the function & the injected version we generate
    let ident = function.sig.ident.clone();
    let injected_ident = format_ident!("__gddi_{}_injected", ident);

    // Create the info needed to generate the call to the original function
    let inputs = function.sig.inputs.iter().map(|arg| {
        if let FnArg::Typed(t) = arg {
            return t.ty.clone();
        }
        panic!("can't be applied to struct methods");
    });
    let local_var_idents = (0..inputs.len()).map(|i| format_ident!("__input{}", i));
    let local_var_idents_for_call = local_var_idents.clone();

    let emitted_code = quote! {
        // Injecting wrapper
        fn #injected_ident(registry: std::sync::Arc<gddi::Registry>) -> std::pin::Pin<gddi::ProviderFutureBox> {
            Box::pin(async move {
                // Create a local variable for each argument, to ensure they get generated in a
                // deterministic order (compiler complains otherwise)
                #(let #local_var_idents = registry.get::<#inputs>().await;)*

                // Actually call the original function
                Box::new(#ident(#(#local_var_idents_for_call),*).await) as Box<dyn std::any::Any>
            })
        }
        #function
    };
    emitted_code.into()
}

struct ModuleDef {
    name: Ident,
    providers: Punctuated<ProviderDef, Token![,]>,
    submodules: Punctuated<Path, Token![,]>,
}

enum ModuleEntry {
    Providers(Punctuated<ProviderDef, Token![,]>),
    Submodules(Punctuated<Path, Token![,]>),
}

struct ProviderDef {
    ty: Type,
    ident: Ident,
    parts: bool,
}

impl Parse for ModuleDef {
    fn parse(input: ParseStream) -> Result<Self> {
        // first thing is the module name followed by a comma
        let name = input.parse()?;
        input.parse::<Token![,]>()?;
        // Then comes submodules or provider sections, in any order
        let entries: Punctuated<ModuleEntry, Token![,]> = Punctuated::parse_terminated(input)?;
        let mut providers = Punctuated::new();
        let mut submodules = Punctuated::new();
        for entry in entries.into_iter() {
            match entry {
                ModuleEntry::Providers(value) => {
                    if !providers.is_empty() {
                        panic!("providers specified more than once");
                    }
                    providers = value;
                }
                ModuleEntry::Submodules(value) => {
                    if !submodules.is_empty() {
                        panic!("submodules specified more than once");
                    }
                    submodules = value;
                }
            }
        }
        Ok(ModuleDef { name, providers, submodules })
    }
}

impl Parse for ProviderDef {
    fn parse(input: ParseStream) -> Result<Self> {
        let parts = input.peek3(Token![=>]);
        if parts {
            match input.parse::<Ident>()?.to_string().as_str() {
                "parts" => {}
                keyword => panic!("expected 'parts', got '{}'", keyword),
            }
        }

        // A provider definition follows this format: <Type> -> <function name>
        let ty = input.parse()?;
        input.parse::<Token![=>]>()?;
        let ident = input.parse()?;
        Ok(ProviderDef { ty, ident, parts })
    }
}

impl Parse for ModuleEntry {
    fn parse(input: ParseStream) -> Result<Self> {
        match input.parse::<Ident>()?.to_string().as_str() {
            "providers" => {
                let entries;
                braced!(entries in input);
                Ok(ModuleEntry::Providers(entries.parse_terminated(ProviderDef::parse, Token![,])?))
            }
            "submodules" => {
                let entries;
                braced!(entries in input);
                Ok(ModuleEntry::Submodules(entries.parse_terminated(Path::parse, Token![,])?))
            }
            keyword => {
                panic!("unexpected keyword: {}", keyword);
            }
        }
    }
}

/// Emits a module function that registers submodules & providers with the registry
#[proc_macro]
#[allow(clippy::redundant_clone)]
pub fn module(item: TokenStream) -> TokenStream {
    let module = parse_macro_input!(item as ModuleDef);
    let init_ident = module.name.clone();
    let providers = module.providers.iter();
    let types = providers.clone().map(|p| p.ty.clone());
    let provider_idents =
        providers.clone().map(|p| format_ident!("__gddi_{}_injected", p.ident.clone()));
    let parting_functions = providers.filter_map(|p| match &p.ty {
        Type::Path(ty) if p.parts => Some(format_ident!(
            "__gddi_part_out_{}",
            ty.path.get_ident().unwrap().to_string().to_lowercase()
        )),
        _ => None,
    });
    let submodule_idents = module.submodules.iter();
    let emitted_code = quote! {
        #[doc(hidden)]
        #[allow(missing_docs)]
        pub fn #init_ident(builder: gddi::RegistryBuilder) -> gddi::RegistryBuilder {
            // Register all providers on this module
            let ret = builder #(.register_provider::<#types>(Box::new(#provider_idents)))*
            // Register all submodules on this module
            #(.register_module(#submodule_idents))*;

            #(let ret = #parting_functions(ret);)*

            ret
        }
    };
    emitted_code.into()
}

/// Emits a default implementation for Stoppable that does nothing;
#[proc_macro_derive(Stoppable)]
pub fn derive_nop_stop(item: TokenStream) -> TokenStream {
    let input = parse_macro_input!(item as DeriveInput);
    let ident = input.ident;
    let emitted_code = quote! {
        impl gddi::Stoppable for #ident {}
    };
    emitted_code.into()
}

/// Generates the code necessary to split up a type into its components
#[proc_macro_attribute]
#[allow(clippy::redundant_clone)]
pub fn part_out(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let struct_: ItemStruct = parse(item).expect("can only be applied to struct definitions");
    let struct_ident = struct_.ident.clone();
    let fields = match struct_.fields.clone() {
        Fields::Named(f) => f,
        _ => panic!("can only be applied to structs with named fields"),
    }
    .named;

    let field_names = fields.iter().map(|f| f.ident.clone().expect("field without a name"));
    let field_types = fields.iter().map(|f| f.ty.clone());

    let fn_ident = format_ident!("__gddi_part_out_{}", struct_ident.to_string().to_lowercase());

    let emitted_code = quote! {
        #struct_

        fn #fn_ident(builder: gddi::RegistryBuilder) -> gddi::RegistryBuilder {
            builder #(.register_provider::<#field_types>(Box::new(
                |registry: std::sync::Arc<gddi::Registry>| -> std::pin::Pin<gddi::ProviderFutureBox> {
                    Box::pin(async move {
                        Box::new(async move {
                            registry.get::<#struct_ident>().await.#field_names
                        }.await) as Box<dyn std::any::Any>
                    })
                })))*
        }
    };
    emitted_code.into()
}
