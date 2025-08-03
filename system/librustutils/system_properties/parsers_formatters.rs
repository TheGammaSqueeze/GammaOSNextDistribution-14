//! Parsers and formatters.
//!
//! These functions should only be used in the system properties generated code.

use std::str::FromStr;
use std::string::ToString;

type Result<T> = std::result::Result<T, String>;

// Parsers.

/// Parses the given string as a `T`, or returns an error including the string value.
pub fn parse<T: FromStr>(s: &str) -> Result<T> {
    s.parse::<T>()
        .map_err(|_| format!("Can't convert '{}' to '{}'.", s, std::any::type_name::<T>()))
}

/// Parses the given string as a boolean or returns an error message including the string.
///
/// `true` and `1` are both considered true, `false` and `0` are false. Any other value is invalid.
pub fn parse_bool(s: &str) -> Result<bool> {
    match s {
        "1" | "true" => Ok(true),
        "0" | "false" => Ok(false),
        _ => Err(format!("Can't convert '{}' to 'bool'.", s)),
    }
}

fn parse_list_with<T, F>(s: &str, f: F) -> Result<Vec<T>>
where
    F: Fn(&str) -> Result<T>,
{
    let mut result = Vec::new();
    if s.is_empty() {
        return Ok(result);
    }

    let mut chars = s.chars();
    let mut current = chars.next();
    while current.is_some() {
        // Extract token.
        let mut token = String::with_capacity(s.len());
        while let Some(value) = current {
            if value == ',' {
                break;
            }
            if value == '\\' {
                current = chars.next()
            }
            if let Some(value) = current {
                token.push(value);
            }
            current = chars.next();
        }
        // Parse token.
        result.push(f(token.as_str())?);
        current = chars.next()
    }

    Ok(result)
}

/// Parses the given string as a comma-separated list of `T`s.
///
/// Literal commas can be escaped with `\`.
pub fn parse_list<T: FromStr>(s: &str) -> Result<Vec<T>> {
    parse_list_with(s, parse)
}

/// Parses the given string as a comma-separated list of booleans.
///
/// Literal commas can be escaped with `\`.
pub fn parse_bool_list(s: &str) -> Result<Vec<bool>> {
    parse_list_with(s, parse_bool)
}

// Formatters.

/// Converts the given value to a string.
pub fn format<T: ToString>(v: &T) -> String {
    v.to_string()
}

/// Converts the given value to a string `true` or `false`.
pub fn format_bool(v: &bool) -> String {
    if *v {
        return "true".into();
    }
    "false".into()
}

/// Converts the given value to a string `1` or `0`.
pub fn format_bool_as_int(v: &bool) -> String {
    if *v {
        return "1".into();
    }
    "0".into()
}

fn format_list_with<T, F>(v: &[T], f: F) -> String
where
    F: Fn(&T) -> String,
{
    let mut result = String::new();
    for item in v {
        let formatted = f(item);
        result.push_str(formatted.as_str());
        result.push(',');
    }
    result.pop();
    result
}

/// Converts the given list of values to a string, separated by commas.
pub fn format_list<T: ToString>(v: &[T]) -> String {
    format_list_with(v, format)
}

/// Converts the given list of booleans to a string, separated by commas.
pub fn format_bool_list(v: &[bool]) -> String {
    format_list_with(v, format_bool)
}

/// Converts the given list of booleans to a string of `0`s and `1`s separated by commas.
pub fn format_bool_list_as_int(v: &[bool]) -> String {
    format_list_with(v, format_bool_as_int)
}
