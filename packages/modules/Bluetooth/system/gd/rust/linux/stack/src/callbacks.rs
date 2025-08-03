//! Provides utilities for managing callbacks.

use std::collections::HashMap;
use tokio::sync::mpsc::Sender;

use crate::{Message, RPCProxy};

/// Utility for managing callbacks conveniently.
pub struct Callbacks<T: Send + ?Sized> {
    callbacks: HashMap<u32, Box<T>>,
    object_id_to_cbid: HashMap<String, u32>,
    tx: Sender<Message>,
    disconnected_message: fn(u32) -> Message,
}

impl<T: RPCProxy + Send + ?Sized> Callbacks<T> {
    /// Creates new Callbacks.
    ///
    /// Parameters:
    /// `tx`: Sender to use when notifying callback disconnect events.
    /// `disconnected_message`: Constructor of the message to be sent on callback disconnection.
    pub fn new(tx: Sender<Message>, disconnected_message: fn(u32) -> Message) -> Self {
        Self {
            callbacks: HashMap::new(),
            object_id_to_cbid: HashMap::new(),
            tx,
            disconnected_message,
        }
    }

    /// Stores a new callback and monitors for callback disconnect. If the callback object id
    /// already exists, return the callback ID previously added.
    ///
    /// When the callback disconnects, a message is sent. This message should be handled and then
    /// the `remove_callback` function can be used.
    ///
    /// Returns the id of the callback.
    pub fn add_callback(&mut self, mut callback: Box<T>) -> u32 {
        if let Some(cbid) = self.object_id_to_cbid.get(&callback.get_object_id()) {
            return *cbid;
        }

        let tx = self.tx.clone();
        let disconnected_message = self.disconnected_message;
        let id = callback.register_disconnect(Box::new(move |cb_id| {
            let tx = tx.clone();
            tokio::spawn(async move {
                let _result = tx.send(disconnected_message(cb_id)).await;
            });
        }));

        self.object_id_to_cbid.insert(callback.get_object_id(), id);
        self.callbacks.insert(id, callback);
        id
    }

    /// Removes the callback given the id.
    ///
    /// When a callback is removed, disconnect monitoring is stopped and the proxy object is
    /// removed.
    ///
    /// Returns true if callback is removed, false if there is no such id.
    pub fn remove_callback(&mut self, id: u32) -> bool {
        match self.callbacks.get_mut(&id) {
            Some(callback) => {
                // Stop watching for disconnect.
                callback.unregister(id);
                // Remove the proxy object.
                self.object_id_to_cbid.remove(&callback.get_object_id());
                self.callbacks.remove(&id);
                true
            }
            None => false,
        }
    }

    /// Returns the callback object based on the given id.
    pub fn get_by_id(&self, id: u32) -> Option<&Box<T>> {
        self.callbacks.get(&id)
    }

    /// Returns the mut callback object based on the given id.
    pub fn get_by_id_mut(&mut self, id: u32) -> Option<&mut Box<T>> {
        self.callbacks.get_mut(&id)
    }

    /// Applies the given function on all active callbacks.
    pub fn for_all_callbacks<F: Fn(&mut Box<T>)>(&mut self, f: F) {
        for (_, ref mut callback) in self.callbacks.iter_mut() {
            f(callback);
        }
    }
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU32, Ordering};

    static CBID: AtomicU32 = AtomicU32::new(0);

    struct TestCallback {
        id: String,
    }

    impl TestCallback {
        fn new(id: String) -> TestCallback {
            TestCallback { id }
        }
    }

    impl RPCProxy for TestCallback {
        fn get_object_id(&self) -> String {
            self.id.clone()
        }
        fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
            CBID.fetch_add(1, Ordering::SeqCst)
        }
    }

    use super::*;

    #[test]
    fn test_add_and_remove() {
        let (tx, _rx) = crate::Stack::create_channel();
        let mut callbacks = Callbacks::new(tx.clone(), Message::AdapterCallbackDisconnected);

        let cb_string = String::from("Test Callback");

        // Test add
        let cbid = callbacks.add_callback(Box::new(TestCallback::new(cb_string.clone())));
        let found = callbacks.get_by_id(cbid);
        assert!(found.is_some());
        assert_eq!(
            cb_string,
            match found {
                Some(c) => c.get_object_id(),
                None => String::new(),
            }
        );

        // Attempting to add another callback with same object id should return the same cbid
        let cbid1 = callbacks.add_callback(Box::new(TestCallback::new(cb_string.clone())));
        assert_eq!(cbid, cbid1);

        // Test remove
        let success = callbacks.remove_callback(cbid);
        assert!(success);
        let found = callbacks.get_by_id(cbid);
        assert!(found.is_none());

        // Attempting to add another callback with same object id should now return a new cbid
        let cbid2 = callbacks.add_callback(Box::new(TestCallback::new(cb_string.clone())));
        assert_ne!(cbid, cbid2);
    }
}
