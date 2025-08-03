// SPDX-License-Identifier: Apache-2.0 OR MIT

// Based on https://github.com/dtolnay/syn/blob/2.0.37/src/item.rs.

use syn::{punctuated::Punctuated, token, Attribute, Ident, Member, Path, Token, Type};

use super::PatPath;

ast_enum_of_structs! {
    /// A pattern in a local binding, function signature, match expression, or
    /// various other places.
    #[non_exhaustive]
    pub enum Pat {
        /// A pattern that binds a new variable: `ref mut binding @ SUBPATTERN`.
        Ident(PatIdent),

        /// A path pattern like `Color::Red`.
        Path(PatPath),

        /// A reference pattern: `&mut var`.
        Reference(PatReference),

        /// A struct or struct variant pattern: `Variant { x, y, .. }`.
        Struct(PatStruct),

        /// A tuple pattern: `(a, b)`.
        Tuple(PatTuple),

        /// A tuple struct or tuple variant pattern: `Variant(x, y, .., z)`.
        TupleStruct(PatTupleStruct),

        /// A type ascription pattern: `foo: f64`.
        Type(PatType),

        /// A pattern that matches any value: `_`.
        Wild(PatWild),
    }
}

ast_struct! {
    /// A pattern that binds a new variable: `ref mut binding @ SUBPATTERN`.
    pub struct PatIdent {
        pub attrs: Vec<Attribute>,
        pub by_ref: Option<Token![ref]>,
        pub mutability: Option<Token![mut]>,
        pub ident: Ident,
    }
}

ast_struct! {
    /// A reference pattern: `&mut var`.
    pub struct PatReference {
        pub attrs: Vec<Attribute>,
        pub and_token: Token![&],
        pub mutability: Option<Token![mut]>,
        pub pat: Box<Pat>,
    }
}

ast_struct! {
    /// The dots in a tuple pattern: `[0, 1, ..]`.
    pub struct PatRest {
        pub attrs: Vec<Attribute>,
        pub dot2_token: Token![..],
    }
}

ast_struct! {
    /// A struct or struct variant pattern: `Variant { x, y, .. }`.
    pub struct PatStruct {
        pub attrs: Vec<Attribute>,
        pub path: Path,
        pub brace_token: token::Brace,
        pub fields: Punctuated<FieldPat, Token![,]>,
        pub rest: Option<PatRest>,
    }
}

ast_struct! {
    /// A tuple pattern: `(a, b)`.
    pub struct PatTuple {
        pub attrs: Vec<Attribute>,
        pub paren_token: token::Paren,
        pub elems: Punctuated<Pat, Token![,]>,
    }
}

ast_struct! {
    /// A tuple struct or tuple variant pattern: `Variant(x, y, .., z)`.
    pub struct PatTupleStruct {
        pub attrs: Vec<Attribute>,
        pub path: Path,
        pub paren_token: token::Paren,
        pub elems: Punctuated<Pat, Token![,]>,
    }
}

ast_struct! {
    /// A type ascription pattern: `foo: f64`.
    pub struct PatType {
        pub attrs: Vec<Attribute>,
        pub pat: Box<Pat>,
        pub colon_token: Token![:],
        pub ty: Box<Type>,
    }
}

ast_struct! {
    /// A pattern that matches any value: `_`.
    pub struct PatWild {
        pub attrs: Vec<Attribute>,
        pub underscore_token: Token![_],
    }
}

ast_struct! {
    /// A single field in a struct pattern.
    ///
    /// Patterns like the fields of Foo `{ x, ref y, ref mut z }` are treated
    /// the same as `x: x, y: ref y, z: ref mut z` but there is no colon token.
    pub struct FieldPat {
        pub attrs: Vec<Attribute>,
        pub member: Member,
        pub colon_token: Option<Token![:]>,
        pub pat: Box<Pat>,
    }
}

mod parsing {
    use syn::{
        braced,
        ext::IdentExt,
        parenthesized,
        parse::{ParseStream, Result},
        punctuated::Punctuated,
        token, Attribute, ExprPath, Ident, Member, Path, Token,
    };

    use super::{
        FieldPat, Pat, PatIdent, PatReference, PatRest, PatStruct, PatTuple, PatTupleStruct,
        PatWild,
    };
    use crate::path;

    impl Pat {
        /// Parse a pattern that does _not_ involve `|` at the top level.
        pub fn parse_single(input: ParseStream<'_>) -> Result<Self> {
            let lookahead = input.lookahead1();
            if lookahead.peek(Ident)
                && (input.peek2(Token![::])
                    || input.peek2(Token![!])
                    || input.peek2(token::Brace)
                    || input.peek2(token::Paren)
                    || input.peek2(Token![..]))
                || input.peek(Token![self]) && input.peek2(Token![::])
                || lookahead.peek(Token![::])
                || lookahead.peek(Token![<])
                || input.peek(Token![Self])
                || input.peek(Token![super])
                || input.peek(Token![crate])
            {
                pat_path_or_struct(input)
            } else if lookahead.peek(Token![_]) {
                input.call(pat_wild).map(Pat::Wild)
            } else if lookahead.peek(Token![ref])
                || lookahead.peek(Token![mut])
                || input.peek(Token![self])
                || input.peek(Ident)
            {
                input.call(pat_ident).map(Pat::Ident)
            } else if lookahead.peek(Token![&]) {
                input.call(pat_reference).map(Pat::Reference)
            } else if lookahead.peek(token::Paren) {
                input.call(pat_paren_or_tuple)
            } else {
                Err(lookahead.error())
            }
        }
    }

    fn pat_path_or_struct(input: ParseStream<'_>) -> Result<Pat> {
        let path = path::parse_path(input)?;

        if input.peek(token::Brace) {
            pat_struct(input, path).map(Pat::Struct)
        } else if input.peek(token::Paren) {
            pat_tuple_struct(input, path).map(Pat::TupleStruct)
        } else {
            Ok(Pat::Path(ExprPath { attrs: Vec::new(), qself: None, path }))
        }
    }

    fn pat_wild(input: ParseStream<'_>) -> Result<PatWild> {
        Ok(PatWild { attrs: Vec::new(), underscore_token: input.parse()? })
    }

    fn pat_ident(input: ParseStream<'_>) -> Result<PatIdent> {
        Ok(PatIdent {
            attrs: Vec::new(),
            by_ref: input.parse()?,
            mutability: input.parse()?,
            ident: input.call(Ident::parse_any)?,
        })
    }

    fn pat_tuple_struct(input: ParseStream<'_>, path: Path) -> Result<PatTupleStruct> {
        let content;
        let paren_token = parenthesized!(content in input);

        let mut elems = Punctuated::new();
        while !content.is_empty() {
            let value = Pat::parse_single(&content)?;
            elems.push_value(value);
            if content.is_empty() {
                break;
            }
            let punct = content.parse()?;
            elems.push_punct(punct);
        }

        Ok(PatTupleStruct { attrs: Vec::new(), path, paren_token, elems })
    }

    fn pat_struct(input: ParseStream<'_>, path: Path) -> Result<PatStruct> {
        let content;
        let brace_token = braced!(content in input);

        let mut fields = Punctuated::new();
        let mut rest = None;
        while !content.is_empty() {
            let attrs = content.call(Attribute::parse_outer)?;
            if content.peek(Token![..]) {
                rest = Some(PatRest { attrs, dot2_token: content.parse()? });
                break;
            }
            let mut value = content.call(field_pat)?;
            value.attrs = attrs;
            fields.push_value(value);
            if content.is_empty() {
                break;
            }
            let punct: Token![,] = content.parse()?;
            fields.push_punct(punct);
        }

        Ok(PatStruct { attrs: Vec::new(), path, brace_token, fields, rest })
    }

    fn field_pat(input: ParseStream<'_>) -> Result<FieldPat> {
        let boxed: Option<Token![box]> = input.parse()?;
        let by_ref: Option<Token![ref]> = input.parse()?;
        let mutability: Option<Token![mut]> = input.parse()?;

        let member = if boxed.is_some() || by_ref.is_some() || mutability.is_some() {
            input.parse().map(Member::Named)
        } else {
            input.parse()
        }?;

        if boxed.is_none() && by_ref.is_none() && mutability.is_none() && input.peek(Token![:])
            || is_unnamed(&member)
        {
            return Ok(FieldPat {
                attrs: Vec::new(),
                member,
                colon_token: Some(input.parse()?),
                pat: Box::new(Pat::parse_single(input)?),
            });
        }

        let ident = match member {
            Member::Named(ident) => ident,
            Member::Unnamed(_) => unreachable!(),
        };

        let pat =
            Pat::Ident(PatIdent { attrs: Vec::new(), by_ref, mutability, ident: ident.clone() });

        Ok(FieldPat {
            attrs: Vec::new(),
            member: Member::Named(ident),
            colon_token: None,
            pat: Box::new(pat),
        })
    }

    fn pat_paren_or_tuple(input: ParseStream<'_>) -> Result<Pat> {
        let content;
        let paren_token = parenthesized!(content in input);

        let mut elems = Punctuated::new();
        while !content.is_empty() {
            let value = Pat::parse_single(&content)?;
            if content.is_empty() {
                elems.push_value(value);
                break;
            }
            elems.push_value(value);
            let punct = content.parse()?;
            elems.push_punct(punct);
        }

        Ok(Pat::Tuple(PatTuple { attrs: Vec::new(), paren_token, elems }))
    }

    fn pat_reference(input: ParseStream<'_>) -> Result<PatReference> {
        Ok(PatReference {
            attrs: Vec::new(),
            and_token: input.parse()?,
            mutability: input.parse()?,
            pat: Box::new(Pat::parse_single(input)?),
        })
    }

    fn is_unnamed(member: &Member) -> bool {
        match member {
            Member::Named(_) => false,
            Member::Unnamed(_) => true,
        }
    }
}

mod printing {
    use proc_macro2::TokenStream;
    use quote::{ToTokens, TokenStreamExt};
    use syn::Token;

    use super::{
        FieldPat, PatIdent, PatReference, PatRest, PatStruct, PatTuple, PatTupleStruct, PatType,
        PatWild,
    };

    impl ToTokens for PatIdent {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.by_ref.to_tokens(tokens);
            self.mutability.to_tokens(tokens);
            self.ident.to_tokens(tokens);
        }
    }

    impl ToTokens for PatReference {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.and_token.to_tokens(tokens);
            self.mutability.to_tokens(tokens);
            self.pat.to_tokens(tokens);
        }
    }

    impl ToTokens for PatRest {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.dot2_token.to_tokens(tokens);
        }
    }

    impl ToTokens for PatStruct {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.path.to_tokens(tokens);
            self.brace_token.surround(tokens, |tokens| {
                self.fields.to_tokens(tokens);
                // Note: We need a comma before the dot2 token if it is present.
                if !self.fields.empty_or_trailing() && self.rest.is_some() {
                    <Token![,]>::default().to_tokens(tokens);
                }
                self.rest.to_tokens(tokens);
            });
        }
    }

    impl ToTokens for PatTuple {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.paren_token.surround(tokens, |tokens| {
                self.elems.to_tokens(tokens);
            });
        }
    }

    impl ToTokens for PatTupleStruct {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.path.to_tokens(tokens);
            self.paren_token.surround(tokens, |tokens| {
                self.elems.to_tokens(tokens);
            });
        }
    }

    impl ToTokens for PatType {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.pat.to_tokens(tokens);
            self.colon_token.to_tokens(tokens);
            self.ty.to_tokens(tokens);
        }
    }

    impl ToTokens for PatWild {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            self.underscore_token.to_tokens(tokens);
        }
    }

    impl ToTokens for FieldPat {
        fn to_tokens(&self, tokens: &mut TokenStream) {
            tokens.append_all(&self.attrs);
            if let Some(colon_token) = &self.colon_token {
                self.member.to_tokens(tokens);
                colon_token.to_tokens(tokens);
            }
            self.pat.to_tokens(tokens);
        }
    }
}
