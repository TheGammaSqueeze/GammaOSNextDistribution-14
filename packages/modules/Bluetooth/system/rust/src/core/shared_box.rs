//! Wrapper around Rc<> to make ownership clearer
//!
//! The idea is to have ownership represented by a SharedBox<T>.
//! Temporary ownership can be held using a WeakBox<T>, which should
//! not be held across async points. This reduces the risk of accidental
//! lifetime extension.

use std::{
    ops::Deref,
    rc::{Rc, Weak},
};

/// A Box<> where static "weak" references to the contents can be taken,
/// and fallibly upgraded at a later point. Unlike Rc<>, weak references
/// cannot be upgraded back to owning references, so ownership remains clear
/// and reference cycles avoided.
#[derive(Debug)]
pub struct SharedBox<T: ?Sized>(Rc<T>);

impl<T> SharedBox<T> {
    /// Constructor
    pub fn new(t: T) -> Self {
        Self(t.into())
    }

    /// Constructs a new SharedBox<T> while giving you a WeakBox<T> to the allocation,
    /// to allow you to construct a T which holds a weak pointer to itself.
    pub fn new_cyclic(f: impl FnOnce(WeakBox<T>) -> T) -> Self {
        Self(Rc::new_cyclic(|weak| f(WeakBox(weak.clone()))))
    }

    /// Produce a weak reference to the contents
    pub fn downgrade(&self) -> WeakBox<T> {
        WeakBox(Rc::downgrade(&self.0))
    }

    /// Produce an upgraded weak reference to the contents
    pub fn as_ref(&self) -> WeakBoxRef<T> {
        WeakBoxRef(self.0.deref(), Rc::downgrade(&self.0))
    }
}

impl<T> From<T> for SharedBox<T> {
    fn from(value: T) -> Self {
        Self(value.into())
    }
}

impl<T> Deref for SharedBox<T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        self.0.deref()
    }
}

/// A weak reference to the contents within a SharedBox<>
pub struct WeakBox<T: ?Sized>(Weak<T>);

impl<T: ?Sized> WeakBox<T> {
    /// Fallibly upgrade to a strong reference, passed into the supplied closure.
    /// The strong reference is not passed into the closure to avoid accidental
    /// lifetime extension.
    ///
    /// Note: reference-counting is used so that, if the passed-in closure drops
    /// the SharedBox<>, the strong reference remains safe. But please don't
    /// do that!
    pub fn with<U>(&self, f: impl FnOnce(Option<WeakBoxRef<T>>) -> U) -> U {
        f(self.0.upgrade().as_deref().map(|x| WeakBoxRef(x, self.0.clone())))
    }
}

impl<T: ?Sized> Clone for WeakBox<T> {
    fn clone(&self) -> Self {
        Self(self.0.clone())
    }
}

/// A strong reference to the contents within a SharedBox<>.
pub struct WeakBoxRef<'a, T: ?Sized>(&'a T, Weak<T>);

impl<'a, T: ?Sized> WeakBoxRef<'a, T> {
    /// Downgrade to a weak reference (with static lifetime) to the contents
    /// within the underlying SharedBox<>
    pub fn downgrade(&self) -> WeakBox<T> {
        WeakBox(self.1.clone())
    }
}

impl<'a, T: ?Sized> Deref for WeakBoxRef<'a, T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        self.0
    }
}

impl<'a, T: ?Sized> Clone for WeakBoxRef<'a, T> {
    fn clone(&self) -> Self {
        Self(self.0, self.1.clone())
    }
}
