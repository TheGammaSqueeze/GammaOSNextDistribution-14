//! Macros to make working with dbus-rs easier.
//!
//! This crate provides several macros to make it easier to project Rust types
//! and traits onto D-Bus.
extern crate proc_macro;

use quote::{format_ident, quote, ToTokens};

use std::fs::File;
use std::io::Write;
use std::path::Path;

use syn::parse::Parser;
use syn::punctuated::Punctuated;
use syn::token::Comma;
use syn::{Expr, FnArg, ImplItem, ItemImpl, ItemStruct, Meta, NestedMeta, Pat, ReturnType, Type};

use crate::proc_macro::TokenStream;

const OUTPUT_DEBUG: bool = false;

fn debug_output_to_file(gen: &proc_macro2::TokenStream, filename: String) {
    if !OUTPUT_DEBUG {
        return;
    }

    let filepath = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap())
        .join(filename)
        .to_str()
        .unwrap()
        .to_string();

    let path = Path::new(&filepath);
    let mut file = File::create(&path).unwrap();
    file.write_all(gen.to_string().as_bytes()).unwrap();
}

/// Marks a method to be projected to a D-Bus method and specifies the D-Bus method name.
///
/// Example:
///   `#[dbus_method("GetAdapterFoo")]`
///   `#[dbus_method("RegisterFoo", DBusLog::Enable(DBusLogOptions::LogAll, DBusLogVerbosity::Info))`
///
/// # Args
///
/// `dbus_method_name`: String. The D-Bus method name.
/// `dbus_logging`: enum DBusLog, optional. Whether to enable logging and the log verbosity.
///                 Note that log is disabled by default for outgoing dbus messages, but enabled
///                 by default with verbose level for incoming messages.
#[proc_macro_attribute]
pub fn dbus_method(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let ori_item: proc_macro2::TokenStream = item.clone().into();
    let gen = quote! {
        #[allow(unused_variables)]
        #ori_item
    };
    gen.into()
}

/// Generates a function to export a Rust object to D-Bus. The result will provide an IFaceToken
/// that must then be registered to an object.
///
/// Example:
///   `#[generate_dbus_exporter(export_foo_dbus_intf, "org.example.FooInterface")]`
///   `#[generate_dbus_exporter(export_foo_dbus_intf, "org.example.FooInterface", FooMixin, foo)]`
///
/// This generates a method called `export_foo_dbus_intf` that will export a Rust object type into a
/// interface token for `org.example.FooInterface`. This interface must then be inserted to an
/// object in order to be exported.
///
/// If the mixin parameter is provided, you must provide the mixin class when registering with
/// crossroads (and that's the one that should be Arc<Mutex<...>>.
///
/// In order to use the interface via D-Bus calls, the Rust object needs to declare its methods with
/// the attribute #[dbus_method()].
///
/// # Args
///
/// `exporter`: Function name for outputted interface exporter.
/// `interface`: Name of the interface where this object should be exported.
/// `mixin_type`: The name of the Mixin struct. Mixins should be used when
///               exporting multiple interfaces and objects under a single object
///               path.
/// `mixin_name`: Name of this object in the mixin where it's implemented.
#[proc_macro_attribute]
pub fn generate_dbus_exporter(attr: TokenStream, item: TokenStream) -> TokenStream {
    let ori_item: proc_macro2::TokenStream = item.clone().into();

    let args = Punctuated::<Expr, Comma>::parse_separated_nonempty.parse(attr.clone()).unwrap();

    let fn_ident = if let Expr::Path(p) = &args[0] {
        p.path.get_ident().unwrap()
    } else {
        panic!("function name must be specified");
    };

    let dbus_iface_name = if let Expr::Lit(lit) = &args[1] {
        lit
    } else {
        panic!("D-Bus interface name must be specified");
    };

    // Must provide both a mixin type and name.
    let (mixin_type, mixin_name) = if args.len() > 3 {
        match (&args[2], &args[3]) {
            (Expr::Path(t), Expr::Path(n)) => (Some(t), Some(n)),
            (_, _) => (None, None),
        }
    } else {
        (None, None)
    };

    let ast: ItemImpl = syn::parse(item.clone()).unwrap();
    let api_iface_ident = ast.trait_.unwrap().1.to_token_stream();

    let mut register_methods = quote! {};

    // If the object isn't expected to be part of a mixin, expect the object
    // type to be Arc<Mutex<Box<T>>>. Otherwise, we accept any type T and depend
    // on the field name lookup to throw an error.
    let obj_type = match mixin_type {
        None => quote! { std::sync::Arc<std::sync::Mutex<Box<T>>> },
        Some(t) => quote! { Box<#t> },
    };

    for item in ast.items {
        if let ImplItem::Method(method) = item {
            if method.attrs.len() != 1 {
                continue;
            }

            let attr = &method.attrs[0];
            if !attr.path.get_ident().unwrap().to_string().eq("dbus_method") {
                continue;
            }

            let meta_list = match attr.parse_meta().unwrap() {
                Meta::List(meta_list) => meta_list,
                _ => continue,
            };

            let dbus_method_name = meta_list.nested[0].clone();

            // logging is default to verbose if not specified
            let dbus_logging = if meta_list.nested.len() > 1 {
                meta_list.nested[1].clone()
            } else {
                let token =
                    quote! { DBusLog::Enable(DBusLogOptions::LogAll, DBusLogVerbosity::Verbose) };
                syn::parse2::<NestedMeta>(token).unwrap()
            };

            let method_name = method.sig.ident;

            let mut arg_names = quote! {};
            let mut method_args = quote! {};
            let mut make_args = quote! {};
            let mut dbus_input_vars = quote! {};
            let mut dbus_input_types = quote! {};
            let mut args_debug = quote! {};
            let mut args_debug_format = String::new();

            for input in method.sig.inputs {
                if let FnArg::Typed(ref typed) = input {
                    let arg_type = &typed.ty;
                    if let Pat::Ident(pat_ident) = &*typed.pat {
                        let ident = pat_ident.ident.clone();
                        let mut dbus_input_ident = ident.to_string();
                        dbus_input_ident.push_str("_");
                        let dbus_input_arg = format_ident!("{}", dbus_input_ident);
                        let ident_string = ident.to_string();

                        arg_names = quote! {
                            #arg_names #ident_string,
                        };

                        method_args = quote! {
                            #method_args #ident,
                        };

                        dbus_input_vars = quote! {
                            #dbus_input_vars #dbus_input_arg,
                        };

                        dbus_input_types = quote! {
                            #dbus_input_types
                            <#arg_type as DBusArg>::DBusType,
                        };

                        make_args = quote! {
                            #make_args
                            let #ident = <#arg_type as DBusArg>::from_dbus(
                                #dbus_input_arg,
                                Some(conn_clone.clone()),
                                Some(ctx.message().sender().unwrap().into_static()),
                                Some(dc_watcher_clone.clone()),
                            );

                            if let Result::Err(e) = #ident {
                                return Err(dbus_crossroads::MethodErr::invalid_arg(
                                    e.to_string().as_str()
                                ));
                            }

                            let #ident = #ident.unwrap();
                        };

                        args_debug = quote! {
                            #args_debug
                            <#arg_type as DBusArg>::log(&#ident),
                        };

                        if args_debug_format.len() != 0 {
                            args_debug_format.push_str(", ");
                        }
                        args_debug_format.push_str("{:?}");
                    }
                }
            }

            let dbus_input_args = quote! {
                (#dbus_input_vars): (#dbus_input_types)
            };

            let mut output_names = quote! {};
            let mut output_type = quote! {};
            let mut ret = quote! {Ok(())};
            if let ReturnType::Type(_, t) = method.sig.output {
                output_type = quote! {<#t as DBusArg>::DBusType,};
                ret = quote! {Ok((<#t as DBusArg>::to_dbus(ret).unwrap(),))};
                output_names = quote! { "out", };
            }

            let debug = quote! {
                let args_formatted = format!(#args_debug_format, #args_debug);
                DBusLog::log(#dbus_logging, "dbus in", #dbus_iface_name, #dbus_method_name, args_formatted.as_str());
            };

            let method_call = match mixin_name {
                Some(name) => {
                    quote! {
                        let ret = obj.#name.lock().unwrap().#method_name(#method_args);
                    }
                }
                None => {
                    quote! {
                        let ret = obj.lock().unwrap().#method_name(#method_args);
                    }
                }
            };

            register_methods = quote! {
                #register_methods

                let conn_clone = conn.clone();
                let dc_watcher_clone = disconnect_watcher.clone();
                let handle_method = move |ctx: &mut dbus_crossroads::Context,
                                          obj: &mut #obj_type,
                                          #dbus_input_args |
                      -> Result<(#output_type), dbus_crossroads::MethodErr> {
                    #make_args
                    #debug
                    #method_call
                    #ret
                };
                ibuilder.method(
                    #dbus_method_name,
                    (#arg_names),
                    (#output_names),
                    handle_method,
                );
            };
        }
    }

    // If mixin is not given, we enforce the API trait is implemented when exporting.
    let type_t = match mixin_type {
        None => quote! { <T: 'static + #api_iface_ident + Send + ?Sized> },
        Some(_) => quote! {},
    };

    let gen = quote! {
        #ori_item

        pub fn #fn_ident #type_t(
            conn: std::sync::Arc<dbus::nonblock::SyncConnection>,
            cr: &mut dbus_crossroads::Crossroads,
            disconnect_watcher: std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>,
        ) -> dbus_crossroads::IfaceToken<#obj_type> {
            cr.register(#dbus_iface_name, |ibuilder| {
                #register_methods
            })
        }
    };

    debug_output_to_file(&gen, format!("out-{}.rs", fn_ident.to_string()));

    gen.into()
}

/// Generates a client implementation of a D-Bus interface.
///
/// Example:
///   #[generate_dbus_interface_client]
///
/// The impl containing #[dbus_method()] will contain a generated code to call the method via D-Bus.
///
/// Example:
///   #[generate_dbus_interface_client(SomeRPC)]
///
/// When the RPC wrapper struct name is specified, it also generates the more RPC-friendly struct:
/// * All methods are async, allowing clients to await (yield) without blocking. Even methods that
///   are sync at the server side requires clients to "wait" for the return.
/// * All method returns are wrapped with `Result`, allowing clients to detect D-Bus level errors in
///   addition to API-level errors.
#[proc_macro_attribute]
pub fn generate_dbus_interface_client(attr: TokenStream, item: TokenStream) -> TokenStream {
    let rpc_struct_name = attr.to_string();

    let ast: ItemImpl = syn::parse(item.clone()).unwrap();
    let trait_path = ast.trait_.unwrap().1;
    let struct_path = match *ast.self_ty {
        Type::Path(path) => path,
        _ => panic!("Struct path not available"),
    };

    // Generated methods
    let mut methods = quote! {};

    // Generated RPC-friendly methods (async and Result-wrapped).
    let mut rpc_methods = quote! {};

    // Iterate on every methods of a trait impl
    for item in ast.items {
        if let ImplItem::Method(method) = item {
            // If the method is not marked with #[dbus_method], just copy the
            // original method body.
            if method.attrs.len() != 1 {
                methods = quote! {
                    #methods

                    #method
                };
                continue;
            }

            let attr = &method.attrs[0];
            if !attr.path.get_ident().unwrap().to_string().eq("dbus_method") {
                continue;
            }

            let sig = &method.sig;

            // For RPC-friendly method, copy the original signature but add public, async, and wrap
            // the return with Result.
            let mut rpc_sig = sig.clone();
            rpc_sig.asyncness = Some(<syn::Token![async]>::default());
            rpc_sig.output = match rpc_sig.output {
                syn::ReturnType::Default => {
                    syn::parse(quote! {-> Result<(), dbus::Error>}.into()).unwrap()
                }
                syn::ReturnType::Type(_arrow, path) => {
                    syn::parse(quote! {-> Result<#path, dbus::Error>}.into()).unwrap()
                }
            };
            let rpc_sig = quote! {
                pub #rpc_sig
            };

            let dbus_method_name = if let Meta::List(meta_list) = attr.parse_meta().unwrap() {
                Some(meta_list.nested[0].clone())
            } else {
                None
            };

            if dbus_method_name.is_none() {
                continue;
            }

            let mut input_list = quote! {};

            let mut object_conversions = quote! {};

            // Iterate on every parameter of a method to build a tuple, e.g.
            // `(param1, param2, param3)`
            for input in &method.sig.inputs {
                if let FnArg::Typed(ref typed) = input {
                    let arg_type = &typed.ty;
                    if let Pat::Ident(pat_ident) = &*typed.pat {
                        let ident = pat_ident.ident.clone();

                        let is_box = if let Type::Path(type_path) = &**arg_type {
                            if type_path.path.segments[0].ident.to_string().eq("Box") {
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        };

                        if is_box {
                            // A Box<dyn> parameter means this is an object that should be exported
                            // on D-Bus.
                            object_conversions = quote! {
                                #object_conversions
                                    let #ident = {
                                        let path = dbus::Path::new(#ident.get_object_id()).unwrap();
                                        #ident.export_for_rpc();
                                        path
                                    };
                            };
                        } else {
                            // Convert every parameter to its corresponding type recognized by
                            // the D-Bus library.
                            object_conversions = quote! {
                                #object_conversions
                                    let #ident = <#arg_type as DBusArg>::to_dbus(#ident).unwrap();
                            };
                        }
                        input_list = quote! {
                            #input_list
                            #ident,
                        };
                    }
                }
            }

            let mut output_as_dbus_arg = quote! {};
            if let ReturnType::Type(_, t) = &method.sig.output {
                output_as_dbus_arg = quote! {<#t as DBusArg>};
            }

            let input_tuple = quote! {
                (#input_list)
            };

            let body = match &method.sig.output {
                // Build the method call to `self.client_proxy`. `method` or `method_noreturn`
                // depends on whether there is a return from the function.
                ReturnType::Default => {
                    quote! {
                        self.client_proxy.method_noreturn(#dbus_method_name, #input_tuple)
                    }
                }
                _ => {
                    quote! {
                        let ret: #output_as_dbus_arg::DBusType = self.client_proxy.method(
                            #dbus_method_name,
                            #input_tuple,
                        );
                        #output_as_dbus_arg::from_dbus(ret, None, None, None).unwrap()
                    }
                }
            };
            let rpc_body = match &method.sig.output {
                // Build the async method call to `self.client_proxy`.
                ReturnType::Default => {
                    quote! {
                        self.client_proxy
                            .async_method_noreturn(#dbus_method_name, #input_tuple)
                            .await
                    }
                }
                _ => {
                    quote! {
                        self.client_proxy
                            .async_method(#dbus_method_name, #input_tuple)
                            .await
                            .map(|(x,)| {
                                #output_as_dbus_arg::from_dbus(x, None, None, None).unwrap()
                            })
                    }
                }
            };

            // Assemble the method body. May have object conversions if there is a param that is
            // a proxy object (`Box<dyn>` type).
            let body = quote! {
                #object_conversions

                #body
            };
            let rpc_body = quote! {
                #object_conversions

                #rpc_body
            };

            // The method definition is its signature and the body.
            let generated_method = quote! {
                #sig {
                    #body
                }
            };
            let generated_rpc_method = quote! {
                #rpc_sig {
                    #rpc_body
                }
            };

            // Assemble all the method definitions.
            methods = quote! {
                #methods

                #generated_method
            };
            rpc_methods = quote! {
                #rpc_methods

                #generated_rpc_method
            };
        }
    }

    // Generated code for the RPC wrapper struct.
    let rpc_gen = if rpc_struct_name.is_empty() {
        quote! {}
    } else {
        let rpc_struct = format_ident!("{}", rpc_struct_name);
        quote! {
            impl #rpc_struct {
                #rpc_methods
            }
        }
    };

    // The final generated code.
    let gen = quote! {
        impl #trait_path for #struct_path {
            #methods
        }

        #rpc_gen
    };

    debug_output_to_file(
        &gen,
        std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap())
            .join(format!("out-{}.rs", struct_path.path.get_ident().unwrap()))
            .to_str()
            .unwrap()
            .to_string(),
    );

    gen.into()
}

fn copy_without_attributes(item: &TokenStream) -> TokenStream {
    let mut ast: ItemStruct = syn::parse(item.clone()).unwrap();
    for field in &mut ast.fields {
        field.attrs.clear();
    }

    let gen = quote! {
        #ast
    };

    gen.into()
}

/// Generates a DBusArg implementation to transform Rust plain structs to a D-Bus data structure.
///
/// The D-Bus structure constructed by this macro has the signature `a{sv}`.
///
/// # Examples
///
/// Assume you have a struct as follows:
/// ```
///     struct FooBar {
///         foo: i32,
///         bar: u8,
///     }
/// ```
///
/// In order to serialize this into D-Bus (and deserialize it), you must re-declare this struct
/// as follows. Note that the field names must match but the struct name does not.
/// ```ignore
///     #[dbus_propmap(FooBar)]
///     struct AnyNameIsFineHere {
///         foo: i32,
///         bar: u8
///     }
/// ```
///
/// The resulting serialized D-Bus data will look like the following:
///
/// ```text
/// array [
///     dict {
///         key: "foo",
///         value: Variant(Int32(0))
///     }
///     dict {
///         key: "bar",
///         value: Variant(Byte(0))
///     }
/// ]
/// ```
// TODO: Support more data types of struct fields (currently only supports integers and enums).
#[proc_macro_attribute]
pub fn dbus_propmap(attr: TokenStream, item: TokenStream) -> TokenStream {
    let ori_item: proc_macro2::TokenStream = copy_without_attributes(&item).into();

    let ast: ItemStruct = syn::parse(item.clone()).unwrap();

    let args = Punctuated::<Expr, Comma>::parse_separated_nonempty.parse(attr.clone()).unwrap();
    let struct_ident =
        if let Expr::Path(p) = &args[0] { p.path.get_ident().unwrap().clone() } else { ast.ident };

    let struct_str = struct_ident.to_string();

    let mut make_fields = quote! {};
    let mut field_idents = quote! {};

    let mut insert_map_fields = quote! {};
    for field in ast.fields {
        let field_ident = field.ident;

        if field_ident.is_none() {
            continue;
        }

        let field_str = field_ident.as_ref().unwrap().clone().to_string();

        let field_type = if let Type::Path(t) = field.ty {
            t
        } else {
            continue;
        };

        field_idents = quote! {
            #field_idents #field_ident,
        };

        let field_type_name = format_ident! {"{}_type_", field_str};
        let make_field = quote! {
            match #field_ident.arg_type() {
                dbus::arg::ArgType::Variant => {}
                _ => {
                    return Err(Box::new(DBusArgError::new(String::from(format!(
                        "{}.{} must be a variant",
                        #struct_str, #field_str
                    )))));
                }
            };
            let #field_ident = <<#field_type as DBusArg>::DBusType as RefArgToRust>::ref_arg_to_rust(
                #field_ident.as_static_inner(0).unwrap(),
                format!("{}.{}", #struct_str, #field_str),
            )?;
            type #field_type_name = #field_type;
            let #field_ident = #field_type_name::from_dbus(
                #field_ident,
                conn__.clone(),
                remote__.clone(),
                disconnect_watcher__.clone(),
            )?;
        };

        make_fields = quote! {
            #make_fields

            let #field_ident = match data__.get(#field_str) {
                Some(data) => data,
                None => {
                    return Err(Box::new(DBusArgError::new(String::from(format!(
                        "{}.{} is required",
                        #struct_str, #field_str
                    )))));
                }
            };
            #make_field
        };

        insert_map_fields = quote! {
            #insert_map_fields
            let field_data__ = DBusArg::to_dbus(data__.#field_ident)?;
            map__.insert(String::from(#field_str), dbus::arg::Variant(Box::new(field_data__)));
        };
    }

    let gen = quote! {
        #[allow(dead_code)]
        #ori_item

        impl DBusArg for #struct_ident {
            type DBusType = dbus::arg::PropMap;

            fn from_dbus(
                data__: dbus::arg::PropMap,
                conn__: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
                remote__: Option<dbus::strings::BusName<'static>>,
                disconnect_watcher__: Option<std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>>,
            ) -> Result<#struct_ident, Box<dyn std::error::Error>> {
                #make_fields

                return Ok(#struct_ident {
                    #field_idents
                });
            }

            fn to_dbus(data__: #struct_ident) -> Result<dbus::arg::PropMap, Box<dyn std::error::Error>> {
                let mut map__: dbus::arg::PropMap = std::collections::HashMap::new();
                #insert_map_fields
                return Ok(map__);
            }

            fn log(data: &#struct_ident) -> String {
                String::from(format!("{:?}", data))
            }
        }
    };

    debug_output_to_file(&gen, format!("out-{}.rs", struct_ident.to_string()));

    gen.into()
}

/// Generates a DBusArg implementation of a Remote RPC proxy object.
///
/// Example:
///   `#[dbus_proxy_obj(FooCallback, "org.example.FooCallbackInterface")]`
///
/// In order to call the remote methods, declare them with the attribute #[dbus_method()].
///
/// # Args
///
/// `struct_ident`: A freeform name used to identify the object struct.
/// `dbus_iface_name`: Name of the interface where this object should be exported.
#[proc_macro_attribute]
pub fn dbus_proxy_obj(attr: TokenStream, item: TokenStream) -> TokenStream {
    let ori_item: proc_macro2::TokenStream = item.clone().into();

    let args = Punctuated::<Expr, Comma>::parse_separated_nonempty.parse(attr.clone()).unwrap();

    let struct_ident = if let Expr::Path(p) = &args[0] {
        p.path.get_ident().unwrap()
    } else {
        panic!("struct name must be specified");
    };

    let dbus_iface_name = if let Expr::Lit(lit) = &args[1] {
        lit
    } else {
        panic!("D-Bus interface name must be specified");
    };

    let mut method_impls = quote! {};

    let ast: ItemImpl = syn::parse(item.clone()).unwrap();
    let self_ty = ast.self_ty;
    let trait_ = ast.trait_.unwrap().1;

    for item in ast.items {
        if let ImplItem::Method(method) = item {
            // If the method is not marked with #[dbus_method], just copy the
            // original method body.
            if method.attrs.len() != 1 {
                method_impls = quote! {
                    #method_impls
                    #method
                };
                continue;
            }

            let attr = &method.attrs[0];
            if !attr.path.get_ident().unwrap().to_string().eq("dbus_method") {
                continue;
            }

            let meta_list = match attr.parse_meta().unwrap() {
                Meta::List(meta_list) => meta_list,
                _ => continue,
            };

            let dbus_method_name = meta_list.nested[0].clone();

            // logging is default to disabled if not specified
            let dbus_logging = if meta_list.nested.len() > 1 {
                meta_list.nested[1].clone()
            } else {
                let token = quote! { DBusLog::Disable };
                syn::parse2::<NestedMeta>(token).unwrap()
            };

            let method_sig = method.sig.clone();

            let mut method_args = quote! {};
            let mut args_debug = quote! {};
            let mut args_debug_format = String::new();

            for input in method.sig.inputs {
                if let FnArg::Typed(ref typed) = input {
                    if let Pat::Ident(pat_ident) = &*typed.pat {
                        let ident = pat_ident.ident.clone();

                        method_args = quote! {
                            #method_args DBusArg::to_dbus(#ident).unwrap(),
                        };

                        args_debug = quote! {
                            #args_debug &#ident,
                        };

                        if args_debug_format.len() != 0 {
                            args_debug_format.push_str(", ");
                        }
                        args_debug_format.push_str("{:?}");
                    }
                }
            }

            let debug = quote! {
                let args_formatted = format!(#args_debug_format, #args_debug);
                DBusLog::log(#dbus_logging, "dbus out", #dbus_iface_name, #dbus_method_name, args_formatted.as_str());
            };

            method_impls = quote! {
                #method_impls
                #[allow(unused_variables)]
                #method_sig {
                    let remote__ = self.remote.clone();
                    let objpath__ = self.objpath.clone();
                    let conn__ = self.conn.clone();

                    #debug

                    let proxy = dbus::nonblock::Proxy::new(
                        remote__,
                        objpath__,
                        std::time::Duration::from_secs(2),
                        conn__,
                    );
                    let future: dbus::nonblock::MethodReply<()> = proxy.method_call(
                        #dbus_iface_name,
                        #dbus_method_name,
                        (#method_args),
                    );

                    // Acquire await lock before pushing task.
                    let has_await_block = {
                        let await_guard = self.futures_awaiting.lock().unwrap();
                        self.cb_futures.lock().unwrap().push_back(future);
                        *await_guard
                    };

                    // Only insert async task if there isn't already one.
                    if !has_await_block {
                        // Callbacks will await in the order they were called.
                        let futures = self.cb_futures.clone();
                        let already_awaiting = self.futures_awaiting.clone();
                        tokio::spawn(async move {
                            // Check for another await block.
                            {
                                let mut await_guard = already_awaiting.lock().unwrap();
                                if *await_guard {
                                    return;
                                }

                                // We are now the only awaiting block. Mark and
                                // drop the lock.
                                *await_guard = true;
                            }

                            loop {
                                // Go through all pending futures and await them.
                                while futures.lock().unwrap().len() > 0 {
                                    let future = {
                                        let mut guard = futures.lock().unwrap();
                                        match guard.pop_front() {
                                            Some(f) => f,
                                            None => {break;}
                                        }
                                    };
                                    let _result = future.await;
                                }

                                // Acquire await block and make final check on
                                // futures list to avoid racing against
                                // insertion. Must acquire in-order to avoid a
                                // deadlock.
                                {
                                    let mut await_guard = already_awaiting.lock().unwrap();
                                    let futures_guard = futures.lock().unwrap();
                                    if (*futures_guard).len() > 0 {
                                        continue;
                                    }

                                    *await_guard = false;
                                    break;
                                }
                            }
                        });
                    }
                }
            };
        }
    }

    let gen = quote! {
        #ori_item

        impl RPCProxy for #self_ty {}

        struct #struct_ident {
            conn: std::sync::Arc<dbus::nonblock::SyncConnection>,
            remote: dbus::strings::BusName<'static>,
            objpath: Path<'static>,
            disconnect_watcher: std::sync::Arc<std::sync::Mutex<DisconnectWatcher>>,

            /// Callback futures to await. If accessing with |futures_awaiting|,
            /// always acquire |futures_awaiting| first to avoid deadlock.
            cb_futures: std::sync::Arc<std::sync::Mutex<std::collections::VecDeque<dbus::nonblock::MethodReply<()>>>>,

            /// Is there a task already awaiting on |cb_futures|? If acquiring
            /// with |cb_futures|, always acquire this lock first to avoid deadlocks.
            futures_awaiting: std::sync::Arc<std::sync::Mutex<bool>>,
        }

        impl #struct_ident {
            fn new(
                conn: std::sync::Arc<dbus::nonblock::SyncConnection>,
                remote: dbus::strings::BusName<'static>,
                objpath: Path<'static>,
                disconnect_watcher: std::sync::Arc<std::sync::Mutex<DisconnectWatcher>>) -> Self {
                Self {
                    conn,
                    remote,
                    objpath,
                    disconnect_watcher,
                    cb_futures: std::sync::Arc::new(std::sync::Mutex::new(std::collections::VecDeque::new())),
                    futures_awaiting: std::sync::Arc::new(std::sync::Mutex::new(false)),
                }
            }
        }

        impl #trait_ for #struct_ident {
            #method_impls
        }

        impl RPCProxy for #struct_ident {
            fn register_disconnect(&mut self, disconnect_callback: Box<dyn Fn(u32) + Send>) -> u32 {
                return self.disconnect_watcher.lock().unwrap().add(self.remote.clone(), disconnect_callback);
            }

            fn get_object_id(&self) -> String {
                self.objpath.to_string().clone()
            }

            fn unregister(&mut self, id: u32) -> bool {
                self.disconnect_watcher.lock().unwrap().remove(self.remote.clone(), id)
            }
        }

        impl DBusArg for Box<dyn #trait_ + Send> {
            type DBusType = Path<'static>;

            fn from_dbus(
                objpath__: Path<'static>,
                conn__: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
                remote__: Option<dbus::strings::BusName<'static>>,
                disconnect_watcher__: Option<std::sync::Arc<std::sync::Mutex<DisconnectWatcher>>>,
            ) -> Result<Box<dyn #trait_ + Send>, Box<dyn std::error::Error>> {
                Ok(Box::new(#struct_ident::new(
                    conn__.unwrap(),
                    remote__.unwrap(),
                    objpath__,
                    disconnect_watcher__.unwrap(),
                )))
            }

            fn to_dbus(_data: Box<dyn #trait_ + Send>) -> Result<Path<'static>, Box<dyn std::error::Error>> {
                // This impl represents a remote DBus object, so `to_dbus` does not make sense.
                panic!("not implemented");
            }

            fn log(_data: &Box<dyn #trait_ + Send>) -> String {
                String::from(format!("Box<dyn>"))
            }
        }
    };

    debug_output_to_file(&gen, format!("out-{}.rs", struct_ident.to_string()));

    gen.into()
}

/// Generates the definition of `DBusArg` trait required for D-Bus projection.
///
/// Due to Rust orphan rule, `DBusArg` trait needs to be defined locally in the crate that wants to
/// use D-Bus projection. Providing `DBusArg` as a public trait won't let other crates implement
/// it for structs defined in foreign crates. As a workaround, this macro is provided to generate
/// `DBusArg` trait definition.
#[proc_macro]
pub fn generate_dbus_arg(_item: TokenStream) -> TokenStream {
    let gen = quote! {
        use dbus::arg::RefArg;
        use dbus::nonblock::SyncConnection;
        use dbus::strings::BusName;
        use dbus_projection::DisconnectWatcher;
        use dbus_projection::impl_dbus_arg_from_into;

        use std::convert::{TryFrom, TryInto};
        use std::error::Error;
        use std::fmt;
        use std::hash::Hash;
        use std::sync::{Arc, Mutex};

        // Key for serialized Option<T> in propmap
        const OPTION_KEY: &'static str = "optional_value";

        #[derive(Debug)]
        pub(crate) struct DBusArgError {
            message: String,
        }

        impl DBusArgError {
            pub fn new(message: String) -> DBusArgError {
                DBusArgError { message }
            }
        }

        impl fmt::Display for DBusArgError {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
                write!(f, "{}", self.message)
            }
        }

        impl Error for DBusArgError {}

        /// Trait for converting `dbus::arg::RefArg` to a Rust type.
        ///
        /// This trait needs to be implemented for all types that need to be
        /// converted from the D-Bus representation (`dbus::arg::RefArg`) to
        /// a Rust representation.
        ///
        /// These implementations should be provided as part of this macros
        /// library since the reference types are defined by the D-Bus specification
        /// (look under Basic Types, Container Types, etc) in
        /// https://dbus.freedesktop.org/doc/dbus-specification.html.
        pub(crate) trait RefArgToRust {
            type RustType;
            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                name: String,
            ) -> Result<Self::RustType, Box<dyn Error>>;
        }

        impl<T: 'static + DirectDBus> RefArgToRust for T {
            type RustType = T;
            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                name: String,
            ) -> Result<Self::RustType, Box<dyn Error>> {
                let any = arg.as_any();
                if !any.is::<<Self as DBusArg>::DBusType>() {
                    return Err(Box::new(DBusArgError::new(String::from(format!(
                        "{} type does not match: expected {}, found {}",
                        name,
                        std::any::type_name::<<Self as DBusArg>::DBusType>(),
                        arg.arg_type().as_str(),
                    )))));
                }
                let arg = (*any.downcast_ref::<<Self as DBusArg>::DBusType>().unwrap()).clone();
                return Ok(arg);
            }
        }

        impl RefArgToRust for std::fs::File {
            type RustType = std::fs::File;

            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                name: String,
            ) -> Result<Self::RustType, Box<dyn Error>> {
                let any = arg.as_any();
                if !any.is::<<Self as DBusArg>::DBusType>() {
                    return Err(Box::new(DBusArgError::new(String::from(format!(
                        "{} type does not match: expected {}, found {}",
                        name,
                        std::any::type_name::<<Self as DBusArg>::DBusType>(),
                        arg.arg_type().as_str(),
                    )))));
                }
                let arg = match (*any.downcast_ref::<<Self as DBusArg>::DBusType>().unwrap()).try_clone() {
                    Ok(foo) => foo,
                    Err(_) => return Err(Box::new(DBusArgError::new(String::from(format!("{} cannot clone file.", name))))),
                };

                return Ok(arg);
            }
        }

        impl RefArgToRust for dbus::arg::PropMap {
            type RustType = dbus::arg::PropMap;
            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                name: String,
            ) -> Result<Self::RustType, Box<dyn Error>> {
                let mut map: dbus::arg::PropMap = std::collections::HashMap::new();
                let mut iter = match arg.as_iter() {
                    None => {
                        return Err(Box::new(DBusArgError::new(String::from(format!(
                            "{} is not iterable",
                            name,
                        )))))
                    }
                    Some(item) => item,
                };
                let mut key = iter.next();
                let mut val = iter.next();
                while !key.is_none() && !val.is_none() {
                    let k = key.unwrap().as_str().unwrap().to_string();
                    let val_clone = val.unwrap().box_clone();
                    let v = dbus::arg::Variant(
                        val_clone
                            .as_static_inner(0)
                            .ok_or(Box::new(DBusArgError::new(String::from(format!(
                                "{}.{} is not a variant",
                                name, k
                            )))))?
                            .box_clone(),
                    );
                    map.insert(k, v);
                    key = iter.next();
                    val = iter.next();
                }
                return Ok(map);
            }
        }

        // A vector is convertible from DBus' dynamic type RefArg to Rust's Vec, if the elements
        // of the vector are also convertible themselves recursively.
        impl<T: 'static + RefArgToRust<RustType = T>> RefArgToRust for Vec<T> {
            type RustType = Vec<T>;
            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                _name: String,
            ) -> Result<Self::RustType, Box<dyn Error>> {
                let mut vec: Vec<T> = vec![];
                let mut iter = arg.as_iter().ok_or(Box::new(DBusArgError::new(format!(
                    "Failed parsing array for `{}`",
                    _name
                ))))?;
                let mut val = iter.next();
                while !val.is_none() {
                    let arg = val.unwrap().box_clone();
                    let arg = <T as RefArgToRust>::ref_arg_to_rust(&arg, _name.clone() + " element")?;
                    vec.push(arg);
                    val = iter.next();
                }
                return Ok(vec);
            }
        }

        impl<
                K: 'static + Eq + Hash + RefArgToRust<RustType = K>,
                V: 'static + RefArgToRust<RustType = V>
            > RefArgToRust for std::collections::HashMap<K, V>
        {
            type RustType = std::collections::HashMap<K, V>;

            fn ref_arg_to_rust(
                arg: &(dyn dbus::arg::RefArg + 'static),
                name: String,
            ) -> Result<Self::RustType, Box<dyn Error>> {
                let mut map: std::collections::HashMap<K, V> = std::collections::HashMap::new();
                let mut iter = arg.as_iter().unwrap();
                let mut key = iter.next();
                let mut val = iter.next();
                while !key.is_none() && !val.is_none() {
                    let k = key.unwrap().box_clone();
                    let k = <K as RefArgToRust>::ref_arg_to_rust(&k, name.clone() + " key")?;
                    let v = val.unwrap().box_clone();
                    let v = <V as RefArgToRust>::ref_arg_to_rust(&v, name.clone() + " value")?;
                    map.insert(k, v);
                    key = iter.next();
                    val = iter.next();
                }
                Ok(map)
            }
        }

        /// Trait describing how to convert to and from a D-Bus type, and to log D-Bus transaction.
        ///
        /// All Rust structs that need to be serialized to and from D-Bus need
        /// to implement this trait. Basic and container types will have their
        /// implementation provided by this macros crate.
        ///
        /// For Rust objects, implement the std::convert::TryFrom and std::convert::TryInto
        /// traits into the relevant basic or container types for serialization. A
        /// helper macro is provided in the `dbus_projection` macro (impl_dbus_arg_from_into).
        /// For enums, use `impl_dbus_arg_enum`.
        ///
        /// When implementing this trait for Rust container types (i.e. Option<T>),
        /// you must first select the D-Bus container type used (i.e. array, property map, etc) and
        /// then implement the `from_dbus`, `to_dbus`, and `log` functions.
        pub(crate) trait DBusArg {
            type DBusType;

            fn from_dbus(
                x: Self::DBusType,
                conn: Option<Arc<dbus::nonblock::SyncConnection>>,
                remote: Option<BusName<'static>>,
                disconnect_watcher: Option<Arc<Mutex<DisconnectWatcher>>>,
            ) -> Result<Self, Box<dyn Error>>
            where
                Self: Sized;

            fn to_dbus(x: Self) -> Result<Self::DBusType, Box<dyn Error>>;

            fn log(x: &Self) -> String;
        }

        // Types that implement dbus::arg::Append do not need any conversion.
        pub(crate) trait DirectDBus: Clone + std::fmt::Display {}
        impl DirectDBus for bool {}
        impl DirectDBus for i32 {}
        impl DirectDBus for u32 {}
        impl DirectDBus for i64 {}
        impl DirectDBus for u64 {}
        impl DirectDBus for f64 {}
        impl DirectDBus for i16 {}
        impl DirectDBus for u16 {}
        impl DirectDBus for u8 {}
        impl DirectDBus for String {}
        impl<T: DirectDBus> DBusArg for T {
            type DBusType = T;

            fn from_dbus(
                data: T,
                _conn: Option<Arc<dbus::nonblock::SyncConnection>>,
                _remote: Option<BusName<'static>>,
                _disconnect_watcher: Option<Arc<Mutex<DisconnectWatcher>>>,
            ) -> Result<T, Box<dyn Error>> {
                return Ok(data);
            }

            fn to_dbus(data: T) -> Result<T, Box<dyn Error>> {
                return Ok(data);
            }

            fn log(data: &T) -> String {
                String::from(format!("{}", data))
            }
        }

        // Represent i8 as D-Bus's i16, since D-Bus only has unsigned type for BYTE.
        impl_dbus_arg_from_into!(i8, i16);

        impl DBusArg for std::fs::File {
            type DBusType = std::fs::File;

            fn from_dbus(
                data: std::fs::File,
                _conn: Option<Arc<dbus::nonblock::SyncConnection>>,
                _remote: Option<BusName<'static>>,
                _disconnect_watcher: Option<Arc<Mutex<DisconnectWatcher>>>,
            ) -> Result<std::fs::File, Box<dyn Error>> {
                return Ok(data);
            }

            fn to_dbus(data: std::fs::File) -> Result<std::fs::File, Box<dyn Error>> {
                return Ok(data);
            }

            fn log(data: &std::fs::File) -> String {
                String::from(format!("{:?}", data))
            }
        }

        impl<T: DBusArg> DBusArg for Vec<T> where T: std::fmt::Debug {
            type DBusType = Vec<T::DBusType>;

            fn from_dbus(
                data: Vec<T::DBusType>,
                conn: Option<Arc<dbus::nonblock::SyncConnection>>,
                remote: Option<BusName<'static>>,
                disconnect_watcher: Option<Arc<Mutex<DisconnectWatcher>>>,
            ) -> Result<Vec<T>, Box<dyn Error>> {
                let mut list: Vec<T> = vec![];
                for prop in data {
                    let t = T::from_dbus(
                        prop,
                        conn.clone(),
                        remote.clone(),
                        disconnect_watcher.clone(),
                    )?;
                    list.push(t);
                }
                Ok(list)
            }

            fn to_dbus(data: Vec<T>) -> Result<Vec<T::DBusType>, Box<dyn Error>> {
                let mut list: Vec<T::DBusType> = vec![];
                for item in data {
                    let t = T::to_dbus(item)?;
                    list.push(t);
                }
                Ok(list)
            }

            fn log(data: &Vec<T>) -> String {
                String::from(format!("{:?}", data))
            }
        }

        impl<T: DBusArg> DBusArg for Option<T>
            where
                <T as DBusArg>::DBusType: dbus::arg::RefArg
                    + 'static
                    + RefArgToRust<RustType = <T as DBusArg>::DBusType>,
                T: std::fmt::Debug
        {
            type DBusType = dbus::arg::PropMap;

            fn from_dbus(
                data: dbus::arg::PropMap,
                conn: Option<Arc<dbus::nonblock::SyncConnection>>,
                remote: Option<BusName<'static>>,
                disconnect_watcher: Option<Arc<Mutex<DisconnectWatcher>>>)
                -> Result<Option<T>, Box<dyn Error>> {

                // It's Ok if the key doesn't exist. That just means we have an empty option (i.e.
                // None).
                let prop_value = match data.get(OPTION_KEY) {
                    Some(data) => data,
                    None => {
                        return Ok(None);
                    }
                };

                // Make sure the option type was encoded correctly. If the key exists but the value
                // is not right, we return an Err type.
                match prop_value.arg_type() {
                    dbus::arg::ArgType::Variant => (),
                    _ => {
                        return Err(Box::new(DBusArgError::new(String::from(format!("{} must be a variant", OPTION_KEY)))));
                    }
                };

                // Convert the Variant into the target type and return an Err if that fails.
                let ref_value: <T as DBusArg>::DBusType = match <<T as DBusArg>::DBusType as RefArgToRust>::ref_arg_to_rust(
                    prop_value.as_static_inner(0).unwrap(),
                    OPTION_KEY.to_string()) {
                    Ok(v) => v,
                    Err(e) => return Err(e),
                };

                let value = match T::from_dbus(ref_value, conn, remote, disconnect_watcher) {
                    Ok(v) => Some(v),
                    Err(e) => return Err(e),
                };

                Ok(value)
            }

            fn to_dbus(data: Option<T>) -> Result<dbus::arg::PropMap, Box<dyn Error>> {
                let mut props = dbus::arg::PropMap::new();

                if let Some(d) = data {
                    let b = T::to_dbus(d)?;
                    props.insert(OPTION_KEY.to_string(), dbus::arg::Variant(Box::new(b)));
                }

                Ok(props)
            }

            fn log(data: &Option<T>) -> String {
                String::from(format!("{:?}", data))
            }
        }

        impl<K: Eq + Hash + DBusArg, V: DBusArg> DBusArg for std::collections::HashMap<K, V>
            where
                <K as DBusArg>::DBusType: 'static
                    + Eq
                    + Hash
                    + dbus::arg::RefArg
                    + RefArgToRust<RustType = <K as DBusArg>::DBusType>,
                K: std::fmt::Debug,
                V: std::fmt::Debug,
        {
            type DBusType = std::collections::HashMap<K::DBusType, V::DBusType>;

            fn from_dbus(
                data: std::collections::HashMap<K::DBusType, V::DBusType>,
                conn: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
                remote: Option<dbus::strings::BusName<'static>>,
                disconnect_watcher: Option<
                    std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>>,
            ) -> Result<std::collections::HashMap<K, V>, Box<dyn std::error::Error>> {
                let mut map = std::collections::HashMap::new();
                for (key, val) in data {
                    let k = K::from_dbus(
                        key,
                        conn.clone(),
                        remote.clone(),
                        disconnect_watcher.clone()
                    )?;
                    let v = V::from_dbus(
                        val,
                        conn.clone(),
                        remote.clone(),
                        disconnect_watcher.clone()
                    )?;
                    map.insert(k, v);
                }
                Ok(map)
            }

            fn to_dbus(
                data: std::collections::HashMap<K, V>,
            ) -> Result<std::collections::HashMap<K::DBusType, V::DBusType>, Box<dyn std::error::Error>>
            {
                let mut map = std::collections::HashMap::new();
                for (key, val) in data {
                    let k = K::to_dbus(key)?;
                    let v = V::to_dbus(val)?;
                    map.insert(k, v);
                }
                Ok(map)
            }

            fn log(data: &std::collections::HashMap<K, V>) -> String {
                String::from(format!("{:?}", data))
            }
        }
    };

    debug_output_to_file(&gen, format!("out-generate_dbus_arg.rs"));

    gen.into()
}
