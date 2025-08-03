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

//! Request router for micro HTTP server.
//!
//! This module implements a basic request router with matching of URI
//! fields. For example
//!
//!   router.add_route("/user/{id})", handle_user);
//!
//! will register a handler that matches user ids.
//!
//! This library is only used for serving the netsim client and is not
//! meant to implement all aspects of an http router.

use crate::http_server::server_response::ResponseWritable;

use http::{Request, Uri};

type RequestHandler = Box<dyn Fn(&Request<Vec<u8>>, &str, ResponseWritable)>;

pub struct Router {
    routes: Vec<(Uri, RequestHandler)>,
}

impl Router {
    pub fn new() -> Router {
        Router { routes: Vec::new() }
    }

    pub fn add_route(&mut self, route: Uri, handler: RequestHandler) {
        self.routes.push((route, handler));
    }

    pub fn handle_request(&self, request: &Request<Vec<u8>>, writer: ResponseWritable) {
        for (route, handler) in &self.routes {
            if let Some(param) = match_route(
                route.path().to_string().as_str(),
                request.uri().path().to_string().as_str(),
            ) {
                handler(request, param, writer);
                return;
            }
        }
        let body = format!("404 Not found (netsim): HttpRouter unknown uri {:?}", request.uri());
        writer.put_error(404, body.as_str());
    }
}

/// Match the uri against the route and return extracted parameter or
/// None.
///
/// Example:
///   pattern: "/users/{id}/info"
///   uri: "/users/33/info"
///   result: Some("33")
///
fn match_route<'a>(route: &str, uri: &'a str) -> Option<&'a str> {
    let open = route.find('{');
    let close = route.find('}');

    // check for literal routes with no parameter
    if open.is_none() && close.is_none() {
        return if route == uri { Some("") } else { None };
    }

    // check for internal errors in the app's route table.
    if open.is_none() || close.is_none() || open.unwrap() > close.unwrap() {
        panic!("Malformed route pattern: {route}");
    }

    // check for match of route like "/user/{id}/info"
    let open = open.unwrap();
    let close = close.unwrap();
    let prefix = &route[0..open];
    let suffix = &route[close + 1..];

    if uri.starts_with(prefix) && uri.ends_with(suffix) {
        Some(&uri[prefix.len()..(uri.len() - suffix.len())])
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http_server::server_response::ServerResponseWriter;
    use http::Version;
    use std::io::Cursor;

    fn handle_index(_request: &Request<Vec<u8>>, _param: &str, writer: ResponseWritable) {
        writer.put_ok_with_vec("text/html", b"Hello, world!".to_vec(), vec![]);
    }

    fn handle_user(_request: &Request<Vec<u8>>, user_id: &str, writer: ResponseWritable) {
        let body = format!("Hello, {user_id}!");
        writer.put_ok("application/json", body.as_str(), vec![]);
    }

    fn handle_query(request: &Request<Vec<u8>>, _param: &str, writer: ResponseWritable) {
        let body = format!("The query is '{}'!", request.uri().query().unwrap());
        writer.put_ok("text/plain", body.as_str(), vec![]);
    }

    #[test]
    fn test_match_route() {
        assert_eq!(match_route("/user/{id}", "/user/1920"), Some("1920"));
        assert_eq!(match_route("/user/{id}/info", "/user/123/info"), Some("123"));
        assert_eq!(match_route("{id}/user/info", "123/user/info"), Some("123"));
        assert_eq!(match_route("/{id}/", "/123/"), Some("123"));
        assert_eq!(match_route("/user", "/user"), Some(""));
        assert_eq!(match_route("/", "/"), Some(""));
        assert_eq!(match_route("a", "b"), None);
        assert_eq!(match_route("/{id}", "|123"), None);
        assert_eq!(match_route("{id}/", "123|"), None);
        assert_eq!(match_route("/{id}/", "/123|"), None);
        assert_eq!(match_route("/{id}/", "|123/"), None);
    }

    #[test]
    fn test_handle_request() {
        let mut router = Router::new();
        router.add_route(Uri::from_static("/"), Box::new(handle_index));
        router.add_route(Uri::from_static("/user/{id}"), Box::new(handle_user));
        let request = Request::builder()
            .method("GET")
            .uri("/")
            .version(Version::HTTP_11)
            .body(Vec::<u8>::new())
            .unwrap();
        let mut stream = Cursor::new(Vec::new());
        let mut writer = ServerResponseWriter::new(&mut stream);
        router.handle_request(&request, &mut writer);
        let written_bytes = stream.get_ref();
        let expected_bytes =
            b"HTTP/1.1 200 OK\r\ncontent-type: text/html\r\ncontent-length: 13\r\n\r\nHello, world!";
        assert_eq!(written_bytes, expected_bytes);

        let request = Request::builder()
            .method("GET")
            .uri("/user/1920")
            .version(Version::HTTP_11)
            .body(Vec::<u8>::new())
            .unwrap();
        let mut stream = Cursor::new(Vec::new());
        let mut writer = ServerResponseWriter::new(&mut stream);
        router.handle_request(&request, &mut writer);
        let written_bytes = stream.get_ref();
        let expected_bytes =
            b"HTTP/1.1 200 OK\r\ncontent-type: application/json\r\ncontent-length: 12\r\n\r\nHello, 1920!";
        assert_eq!(written_bytes, expected_bytes);
    }

    #[test]
    fn test_mismatch_uri() {
        let mut router = Router::new();
        router.add_route(Uri::from_static("/user/{id}"), Box::new(handle_user));
        let request = Request::builder()
            .method("GET")
            .uri("/player/1920")
            .version(Version::HTTP_11)
            .body(Vec::<u8>::new())
            .unwrap();
        let mut stream = Cursor::new(Vec::new());
        let mut writer = ServerResponseWriter::new(&mut stream);
        router.handle_request(&request, &mut writer);
        let written_bytes = stream.get_ref();
        let expected_bytes =
            b"HTTP/1.1 404 Not Found\r\ncontent-type: text/plain\r\ncontent-length: 59\r\n\r\n404 Not found (netsim): HttpRouter unknown uri /player/1920";
        assert_eq!(written_bytes, expected_bytes);
    }

    #[test]
    fn test_handle_query() {
        let mut router = Router::new();
        router.add_route(Uri::from_static("/user"), Box::new(handle_query));
        let request = Request::builder()
            .method("GET")
            .uri("/user?name=hello")
            .version(Version::HTTP_11)
            .body(Vec::<u8>::new())
            .unwrap();
        let mut stream = Cursor::new(Vec::new());
        let mut writer = ServerResponseWriter::new(&mut stream);
        router.handle_request(&request, &mut writer);
        let written_bytes = stream.get_ref();
        let expected_bytes =
            b"HTTP/1.1 200 OK\r\ncontent-type: text/plain\r\ncontent-length: 26\r\n\r\nThe query is 'name=hello'!";
        assert_eq!(written_bytes, expected_bytes);
    }
}
