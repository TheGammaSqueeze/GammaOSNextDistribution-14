// SPDX-License-Identifier: Apache-2.0 OR MIT

// Based on https://github.com/dtolnay/syn/blob/2.0.37/src/item.rs.

use proc_macro2::TokenStream;
use syn::{
    punctuated::Punctuated, token, Abi, Attribute, Generics, Ident, Lifetime, ReturnType, Token,
    Type, Visibility,
};

use super::{Pat, PatType};

ast_struct! {
    /// A free-standing function: `fn process(n: usize) -> Result<()> { ...
    /// }`.
    pub struct ItemFn {
        pub attrs: Vec<Attribute>,
        pub vis: Visibility,
        pub sig: Signature,
        pub block: Box<Block>,
    }
}

ast_struct! {
    /// A braced block containing Rust statements.
    pub struct Block {
        pub brace_token: token::Brace,
        /// Statements in a block
        pub stmts: TokenStream,
    }
}

ast_struct! {
    /// A function signature in a trait or implementation: `unsafe fn
    /// initialize(&self)`.
    pub struct Signature {
        pub constness: Option<Token![const]>,
        pub asyncness: Option<Token![async]>,
        pub unsafety: Option<Token![unsafe]>,
        pub abi: Option<Abi>,
        pub fn_token: Token![fn],
        pub ident: Ident,
        pub generics: Generics,
        pub paren_token: token::Paren,
        pub inputs: Punctuated<FnArg, Token![,]>,
        pub variadic: Option<Variadic>,
        pub output: ReturnType,
    }
}

ast_enum_of_structs! {
    /// An argument in a function signature: the `n: usize` in `fn f(n: usize)`.
    pub enum FnArg {
        /// The `self` argument of an associated method, whether taken by value
        /// or by reference.
        Receiver(Receiver),

        /// A function argument accepted by pattern and type.
        Typed(PatType),
    }
}

ast_struct! {
    /// The `self` argument of an associated method, whether taken by value
    /// or by reference.
    pub struct Receiver {
        pub attrs: Vec<Attribute>,
        pub reference: Option<(Token![&], Option<Lifetime>)>,
        pub mutability: Option<Token![mut]>,
        pub self_token: Token![self],
        pub colon_token: Option<Token![:]>,
        pub ty: Box<Type>,
    }
}

ast_struct! {
    /// The variadic argument of a foreign function.
    pub struct Variadic {
        pub attrs: Vec<Attribute>,
        pub pat: Option<(Box<Pat>, Token![:])>,
        pub dots: Token![...],
        pub comma: Option<Token![,]>,
    }
}

mod parsing {
    use syn::{
        braced, parenthesized,
        parse::{discouraged::Speculative, Parse, ParseStream, Result},
        punctuated::Punctuated,
        Abi, Attribute, Error, Generics, Ident, Lifetime, Path, ReturnType, Token, Type, TypePath,
        TypeReference, Visibility,
    };

    use super::{Block, FnArg, ItemFn, Receiver, Signature, Variadic};
    use crate::pat::{Pat, PatType, PatWild};

    impl Parse for Block {
        fn parse(input: ParseStream<'_>) -> Result<Self> {
            let content;
            Ok(Self { brace_token: braced!(content in input), stmts: content.parse()? })
        }
    }

    impl Parse for Signature {
        fn parse(input: ParseStream<'_>) -> Result<Self> {
            let constness: Option<Token![const]> = input.parse()?;
            let asyncness: Option<Token![async]> = input.parse()?;
            let unsafety: Option<Token![unsafe]> = input.parse()?;
            let abi: Option<Abi> = input.parse()?;
            let fn_token: Token![fn] = input.parse()?;
            let ident: Ident = input.parse()?;
            let mut generics: Generics = input.parse()?;

            let content;
            let paren_token = parenthesized!(content in input);
            let (inputs, variadic) = parse_fn_args(&content)?;

            let output: ReturnType = input.parse()?;
            generics.where_clause = input.parse()?;

            Ok(Self {
                constness,
                asyncness,
                unsafety,
                abi,
                fn_token,
                ident,
                generics,
                paren_token,
                inputs,
                variadic,
                output,
            })
        }
    }

    impl Parse for ItemFn {
        fn parse(input: ParseStream<'_>) -> Result<Self> {
            let attrs = input.call(Attribute::parse_outer)?;
            let vis: Visibility = input.parse()?;
            let sig: Signature = input.parse()?;
            let block = input.parse()?;
            Ok(Self { attrs, vis, sig, block: Box::new(block) })
        }
    }

    impl Parse for FnArg {
        fn parse(input: ParseStream<'_>) -> Result<Self> {
            let allow_variadic = false;
            let attrs = input.call(Attribute::parse_outer)?;
            match parse_fn_arg_or_variadic(input, attrs, allow_variadic)? {
                FnArgOrVariadic::FnArg(arg) => Ok(arg),
                FnArgOrVariadic::Variadic(_) => unreachable!(),
            }
        }
    }

    enum FnArgOrVariadic {
        FnArg(FnArg),
        Variadic(Variadic),
    }

    fn parse_fn_arg_or_variadic(
        input: ParseStream<'_>,
        attrs: Vec<Attribute>,
        allow_variadic: bool,
    ) -> Result<FnArgOrVariadic> {
        let ahead = input.fork();
        if let Ok(mut receiver) = ahead.parse::<Receiver>() {
            input.advance_to(&ahead);
            receiver.attrs = attrs;
            return Ok(FnArgOrVariadic::FnArg(FnArg::Receiver(receiver)));
        }

        // Hack to parse pre-2018 syntax in
        // test/ui/rfc-2565-param-attrs/param-attrs-pretty.rs
        // because the rest of the test case is valuable.
        if input.peek(Ident) && input.peek2(Token![<]) {
            let span = input.fork().parse::<Ident>()?.span();
            return Ok(FnArgOrVariadic::FnArg(FnArg::Typed(PatType {
                attrs,
                pat: Box::new(Pat::Wild(PatWild {
                    attrs: Vec::new(),
                    underscore_token: Token![_](span),
                })),
                colon_token: Token![:](span),
                ty: input.parse()?,
            })));
        }

        let pat = Box::new(Pat::parse_single(input)?);
        let colon_token: Token![:] = input.parse()?;

        if allow_variadic {
            if let Some(dots) = input.parse::<Option<Token![...]>>()? {
                return Ok(FnArgOrVariadic::Variadic(Variadic {
                    attrs,
                    pat: Some((pat, colon_token)),
                    dots,
                    comma: None,
                }));
            }
        }

        Ok(FnArgOrVariadic::FnArg(FnArg::Typed(PatType {
            attrs,
            pat,
            colon_token,
            ty: input.parse()?,
        })))
    }

    impl Parse for Receiver {
        fn parse(input: ParseStream<'_>) -> Result<Self> {
            let reference = if input.peek(Token![&]) {
                let ampersand: Token![&] = input.parse()?;
                let lifetime: Option<Lifetime> = input.parse()?;
                Some((ampersand, lifetime))
            } else {
                None
            };
            let mutability: Option<Token![mut]> = input.parse()?;
            let self_token: Token![self] = input.parse()?;
            let colon_token: Option<Token![:]> =
                if reference.is_some() { None } else { input.parse()? };
            let ty: Type = if colon_token.is_some() {
                input.parse()?
            } else {
                let mut ty = Type::Path(TypePath {
                    qself: None,
                    path: Path::from(Ident::new("Self", self_token.span)),
                });
                if let Some((ampersand, lifetime)) = reference.as_ref() {
                    ty = Type::Reference(TypeReference {
                        and_token: Token![&](ampersand.span),
                        lifetime: lifetime.clone(),
                        mutability: mutability.as_ref().map(|m| Token![mut](m.span)),
                        elem: Box::new(ty),
                    });
                }
                ty
            };
            Ok(Self {
                attrs: Vec::new(),
                reference,
                mutability,
                self_token,
                colon_token,
                ty: Box::new(ty),
            })
        }
    }

    fn parse_fn_args(
        input: ParseStream<'_>,
    ) -> Result<(Punctuated<FnArg, Token![,]>, Option<Variadic>)> {
        let mut args = Punctuated::new();
        let mut variadic = None;
        let mut has_receiver = false;

        while !input.is_empty() {
            let attrs = input.call(Attribute::parse_outer)?;

            if let Some(dots) = input.parse::<Option<Token![...]>>()? {
                variadic = Some(Variadic {
                    attrs,
                    pat: None,
                    dots,
                    comma: if input.is_empty() { None } else { Some(input.parse()?) },
                });
                break;
            }

            let allow_variadic = true;
            let arg = match parse_fn_arg_or_variadic(input, attrs, allow_variadic)? {
                FnArgOrVariadic::FnArg(arg) => arg,
                FnArgOrVariadic::Variadic(arg) => {
                    variadic = Some(Variadic {
                        comma: if input.is_empty() { None } else { Some(input.parse()?) },
                        ..arg
                    });
                    break;
                }
            };

            match &arg {
                FnArg::Receiver(receiver) if has_receiver => {
                    return Err(Error::new(
                        receiver.self_token.span,
                        "unexpected second method receiver",
                    ));
                }
                FnArg::Receiver(receiver) if !args.is_empty() => {
                    return Err(Error::new(receiver.self_token.span, "unexpected method receiver"));
                }
                FnArg::Receiver(_) => has_receiver = true,
                FnArg::Typed(_) => {}
            }
            args.push_value(arg);

            if input.is_empty() {
                break;
            }

            let comma: Token![,] = input.parse()?;
            args.push_punct(comma);
        }

        Ok((args, variadic))
    }
}

mod printing {
    use proc_macro2::TokenStream;
    use quote::{ToTokens, TokenStreamExt};
    use syn::{Token, Type};

    use super::{Block, ItemFn, Receiver, Signature, Variadic};

    impl ToTokens for ItemFn {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.vis.to_tokens(tokens);
            self.sig.to_tokens(tokens);
            self.block.to_tokens(tokens);
        }
    }

    impl ToTokens for Block {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            self.brace_token.surround(tokens, |tokens| {
                tokens.append_all(self.stmts.clone());
            });
        }
    }

    impl ToTokens for Signature {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            self.constness.to_tokens(tokens);
            self.asyncness.to_tokens(tokens);
            self.unsafety.to_tokens(tokens);
            self.abi.to_tokens(tokens);
            self.fn_token.to_tokens(tokens);
            self.ident.to_tokens(tokens);
            self.generics.to_tokens(tokens);
            self.paren_token.surround(tokens, |tokens| {
                self.inputs.to_tokens(tokens);
                if let Some(variadic) = &self.variadic {
                    if !self.inputs.empty_or_trailing() {
                        <Token![,]>::default().to_tokens(tokens);
                    }
                    variadic.to_tokens(tokens);
                }
            });
            self.output.to_tokens(tokens);
            self.generics.where_clause.to_tokens(tokens);
        }
    }

    impl ToTokens for Receiver {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            if let Some((ampersand, lifetime)) = &self.reference {
                ampersand.to_tokens(tokens);
                lifetime.to_tokens(tokens);
            }
            self.mutability.to_tokens(tokens);
            self.self_token.to_tokens(tokens);
            if let Some(colon_token) = &self.colon_token {
                colon_token.to_tokens(tokens);
                self.ty.to_tokens(tokens);
            } else {
                let consistent = match (&self.reference, &self.mutability, &*self.ty) {
                    (Some(_), mutability, Type::Reference(ty)) => {
                        mutability.is_some() == ty.mutability.is_some()
                            && match &*ty.elem {
                                Type::Path(ty) => ty.qself.is_none() && ty.path.is_ident("Self"),
                                _ => false,
                            }
                    }
                    (None, _, Type::Path(ty)) => ty.qself.is_none() && ty.path.is_ident("Self"),
                    _ => false,
                };
                if !consistent {
                    <Token![:]>::default().to_tokens(tokens);
                    self.ty.to_tokens(tokens);
                }
            }
        }
    }

    impl ToTokens for Variadic {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            if let Some((pat, colon)) = &self.pat {
                pat.to_tokens(tokens);
                colon.to_tokens(tokens);
            }
            self.dots.to_tokens(tokens);
            self.comma.to_tokens(tokens);
        }
    }
}
