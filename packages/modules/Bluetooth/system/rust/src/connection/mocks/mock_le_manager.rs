//! This module mocks the behavior of le_impl in GD (excluding timers).
//! It tracks both the internal state of le_impl, as well as the connect list in the controller.
//! It also enforces all (implicit) invariants of le_impl as documented in le_manager.rs, and
//! asserts on violation.

use std::{cell::RefCell, collections::HashSet, fmt::Debug, rc::Rc};

use crate::{
    connection::{
        attempt_manager::ConnectionMode,
        le_manager::{
            ErrorCode, InactiveLeAclManager, LeAclManager, LeAclManagerConnectionCallbacks,
        },
        LeConnection,
    },
    core::address::AddressWithType,
};

#[derive(Clone)]
pub struct MockLeAclManager {
    active: Rc<RefCell<Option<Rc<MockActiveLeAclManager>>>>,
    callbacks: Rc<RefCell<Option<Box<dyn LeAclManagerConnectionCallbacks>>>>,
}

impl MockLeAclManager {
    pub fn new() -> Self {
        Self { active: Rc::new(RefCell::new(None)), callbacks: Rc::new(RefCell::new(None)) }
    }

    fn inner(&self) -> Rc<MockActiveLeAclManager> {
        self.active.borrow().as_ref().unwrap().clone()
    }

    pub fn current_acceptlist(&self) -> HashSet<AddressWithType> {
        self.inner().current_acceptlist()
    }

    pub fn current_connection_mode(&self) -> Option<ConnectionMode> {
        self.inner().current_connection_mode()
    }

    pub fn on_le_connect(&self, address: AddressWithType, status: ErrorCode) {
        let inner = self.inner();
        inner.on_le_connect(address, status);
        drop(inner);

        if status == ErrorCode::SUCCESS {
            self.callbacks
                .borrow()
                .as_deref()
                .unwrap()
                .on_le_connect(address, Ok(LeConnection { remote_address: address }));
        } else {
            self.callbacks.borrow().as_deref().unwrap().on_le_connect(address, Err(status));
        }
    }

    pub fn on_le_disconnect(&self, address: AddressWithType) {
        let inner = self.inner();
        inner.on_le_disconnect(address);
        drop(inner);

        self.callbacks.borrow().as_deref().unwrap().on_disconnect(address);
    }
}

impl InactiveLeAclManager for MockLeAclManager {
    type ActiveManager = Rc<MockActiveLeAclManager>;

    fn register_callbacks(
        self,
        callbacks: impl LeAclManagerConnectionCallbacks + 'static,
    ) -> Self::ActiveManager {
        let out = MockActiveLeAclManager::new();
        *self.active.borrow_mut() = Some(out.clone());
        *self.callbacks.borrow_mut() = Some(Box::new(callbacks));
        out
    }
}

#[derive(Debug)]
pub struct MockActiveLeAclManager {
    state: RefCell<MockLeManagerInternalState>,
}

#[derive(Clone, Debug)]
struct MockLeManagerInternalState {
    direct_connect_list: HashSet<AddressWithType>,
    background_connect_list: HashSet<AddressWithType>,
    currently_connected: HashSet<AddressWithType>,
}

impl MockActiveLeAclManager {
    pub fn new() -> Rc<Self> {
        Rc::new(MockActiveLeAclManager {
            state: RefCell::new(MockLeManagerInternalState {
                direct_connect_list: HashSet::new(),
                background_connect_list: HashSet::new(),
                currently_connected: HashSet::new(),
            }),
        })
    }

    pub fn current_acceptlist(&self) -> HashSet<AddressWithType> {
        let state = self.state.borrow();
        &(&state.direct_connect_list | &state.background_connect_list)
            - (&state.currently_connected)
    }

    pub fn current_connection_mode(&self) -> Option<ConnectionMode> {
        let state = self.state.borrow();

        if !state.direct_connect_list.is_empty() {
            Some(ConnectionMode::Direct)
        } else if state
            .background_connect_list
            .difference(&state.currently_connected)
            .next()
            .is_some()
        {
            Some(ConnectionMode::Background)
        } else {
            None
        }
    }

    pub fn on_le_connect(&self, address: AddressWithType, status: ErrorCode) {
        let mut state = self.state.borrow_mut();
        state.direct_connect_list.remove(&address);
        if status == ErrorCode::SUCCESS {
            let ok = state.currently_connected.insert(address);
            assert!(ok, "Already connected");
        }
    }

    pub fn on_le_disconnect(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        let ok = state.currently_connected.remove(&address);
        assert!(ok, "Not connected");
    }
}

impl LeAclManager for Rc<MockActiveLeAclManager> {
    fn add_to_direct_list(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        assert!(
            !state.currently_connected.contains(&address),
            "Must NOT be currently connected to this address"
        );
        let ok = state.direct_connect_list.insert(address);
        assert!(ok, "Already in direct connect list");
    }

    fn add_to_background_list(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        assert!(
            !state.currently_connected.contains(&address),
            "Must NOT be currently connected to this address"
        );
        let ok = state.background_connect_list.insert(address);
        assert!(ok, "Already in background connect list");
    }

    fn remove_from_all_lists(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        assert!(
            !state.currently_connected.contains(&address),
            "Must NOT be currently connected to this address"
        );
        let ok1 = state.direct_connect_list.remove(&address);
        let ok2 = state.background_connect_list.remove(&address);
        assert!(ok1 || ok2, "Present in neither direct nor background connect list");
    }
}
