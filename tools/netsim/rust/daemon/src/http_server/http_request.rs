// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Request library for micro HTTP server.
//!
//! This library implements the basic parts of Request Message from
//! (RFC 5322)[ https://www.rfc-editor.org/rfc/rfc5322.html] "HTTP
//! Message Format."
//!
//! This library is only used for serving the netsim client and is not
//! meant to implement all aspects of RFC 5322. In particular,
//! this library does not implement the following:
//! * header field body with multiple lines (section 3.2.2)
//! * limits on the lengths of the header section or header field
//!
//! The main function is `HttpRequest::parse` which can be called
//! repeatedly.

use http::Request;
use http::Version;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Read;

#[allow(clippy::read_zero_byte_vec)]
pub fn parse_http_request<T: std::io::Read>(
    reader: &mut BufReader<T>,
) -> Result<Request<Vec<u8>>, String> {
    let mut line = String::new();
    reader.read_line(&mut line).map_err(|e| format!("Failed to read request line: {e}"))?;
    let mut parts = line.split_whitespace();
    let method = parts.next().ok_or("Invalid request line, missing method")?;
    let uri = parts.next().ok_or("Invalid request line, missing uri")?;
    let version_str = parts.next().ok_or("Invalid request line, missing version")?;
    let version = match version_str {
        "HTTP/0.9" => Version::HTTP_09,
        "HTTP/1.0" => Version::HTTP_10,
        "HTTP/1.1" => Version::HTTP_11,
        "HTTP/2.0" => Version::HTTP_2,
        "HTTP/3.0" => Version::HTTP_3,
        _ => return Err("Invalid HTTP version".to_string()),
    };

    let mut headers = Vec::new();
    for line in reader.lines() {
        let line = line.map_err(|e| format!("Failed to parse headers: {e}"))?;
        if let Some((name, value)) = line.split_once(':') {
            headers.push((name.to_string(), value.trim().to_string()));
        } else if line.len() > 1 {
            // no colon in a header line
            return Err(format!("Invalid header line: {line}"));
        } else {
            // empty line marks the end of headers
            break;
        }
    }

    let mut builder = Request::builder().method(method).uri(uri).version(version);
    let mut body_length: Option<usize> = None;
    for (key, value) in headers {
        builder = builder.header(key.clone(), value.clone());
        if key == "Content-Length" {
            body_length = match value.parse() {
                Ok(size) => Some(size),
                Err(err) => return Err(format!("{err:?}")),
            }
        }
    }
    let mut body = Vec::new();
    if let Some(len) = body_length {
        body.resize(len, 0);
        reader.read_exact(&mut body).map_err(|e| format!("Failed to read body: {e}"))?;
    }
    match builder.body(body) {
        Ok(request) => Ok(request),
        Err(err) => Err(format!("{err:?}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse() {
        let request = concat!(
            "GET /index.html HTTP/1.1\r\n",
            "Host: example.com\r\nContent-Length: 13\r\n\r\n",
            "Hello World\r\n"
        );
        let mut reader = BufReader::new(request.as_bytes());
        let http_request = parse_http_request::<&[u8]>(&mut reader).unwrap();
        assert_eq!(http_request.method(), "GET");
        assert_eq!(http_request.uri().to_string(), "/index.html");
        assert_eq!(http_request.version(), Version::HTTP_11);
        let mut headers = http::HeaderMap::new();
        headers.insert("Host", http::HeaderValue::from_static("example.com"));
        headers.insert("Content-Length", http::HeaderValue::from_static("13"));
        assert_eq!(http_request.headers().to_owned(), headers);
        assert_eq!(http_request.body().to_owned(), b"Hello World\r\n".to_vec());
    }

    #[test]
    fn test_parse_without_body() {
        let request = concat!("GET /index.html HTTP/1.1\r\n", "Host: example.com\r\n\r\n");
        let mut reader = BufReader::new(request.as_bytes());
        let http_request = parse_http_request::<&[u8]>(&mut reader).unwrap();
        assert_eq!(http_request.method(), "GET");
        assert_eq!(http_request.uri().to_string(), "/index.html");
        assert_eq!(http_request.version(), Version::HTTP_11);
        let mut headers = http::HeaderMap::new();
        headers.insert("Host", http::HeaderValue::from_static("example.com"));
        assert_eq!(http_request.headers().to_owned(), headers);
        assert_eq!(http_request.body().to_owned(), Vec::<u8>::new());
    }

    #[test]
    fn test_parse_without_content_length() {
        let request =
            concat!("GET /index.html HTTP/1.1\r\n", "Host: example.com\r\n\r\n", "Hello World\r\n");
        let mut reader = BufReader::new(request.as_bytes());
        let http_request = parse_http_request::<&[u8]>(&mut reader).unwrap();
        assert_eq!(http_request.method(), "GET");
        assert_eq!(http_request.uri(), "/index.html");
        assert_eq!(http_request.version(), Version::HTTP_11);
        let mut headers = http::HeaderMap::new();
        headers.insert("Host", http::HeaderValue::from_static("example.com"));
        assert_eq!(http_request.headers().to_owned(), headers);
        assert_eq!(http_request.body().to_owned(), Vec::<u8>::new());
    }
}
