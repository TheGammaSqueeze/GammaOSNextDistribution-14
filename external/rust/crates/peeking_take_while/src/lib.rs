//! Provides the `peeking_take_while` iterator adaptor method.
//!
//! The `peeking_take_while` method is very similar to `take_while`, but behaves
//! differently when used with a borrowed iterator (perhaps returned by
//! `Iterator::by_ref`).
//!
//! `peeking_take_while` peeks at the next item in the iterator and runs the
//! predicate on that peeked item. This avoids consuming the first item yielded
//! by the underlying iterator for which the predicate returns `false`. On the
//! other hand, `take_while` will consume that first item for which the
//! predicate returns `false`, and it will be lost.
//!
//! In case the closure may have side effects, it could be necessary to apply
//! [`fuse`](Iterator::fuse) on the returned iterator, to prevent the predicate
//! from being called after it first returned `false`.
//!
//! ```
//! // Bring the `peeking_take_while` method for peekable iterators into
//! // scope.
//! use peeking_take_while::PeekableExt;
//!
//! # fn main() {
//! // Let's say we have two collections we want to iterate through: `xs` and
//! // `ys`. We want to perform one operation on all the leading contiguous
//! // elements that match some predicate, and a different thing with the rest of
//! // the elements. With the `xs`, we will use the normal `take_while`. With the
//! // `ys`, we will use `peeking_take_while`.
//!
//! let xs: Vec<u8> = (0..100).collect();
//! let ys = xs.clone();
//!
//! let mut iter_xs = xs.into_iter();
//! let mut iter_ys = ys.into_iter().peekable();
//!
//! {
//!     // Let's do one thing with all the items that are less than 10.
//! #   fn do_things_with<T>(_: T) {}
//!
//!     let xs_less_than_ten = iter_xs.by_ref().take_while(|x| *x < 10);
//!     for x in xs_less_than_ten {
//!         do_things_with(x);
//!     }
//!
//!     let ys_less_than_ten = iter_ys.by_ref().peeking_take_while(|y| *y < 10);
//!     for y in ys_less_than_ten {
//!         do_things_with(y);
//!     }
//! }
//!
//! // And now we will do some other thing with the items that are greater than
//! // or equal to 10.
//!
//! // ...except, when using plain old `take_while` we lost 10!
//! assert_eq!(iter_xs.next(), Some(11));
//!
//! // However, when using `peeking_take_while` we did not! Great!
//! assert_eq!(iter_ys.next(), Some(10));
//! # }
//! ```

#![no_std]
#![forbid(
    clippy::as_conversions,
    clippy::cast_ptr_alignment,
    missing_docs,
    trivial_casts,
    unsafe_code
)]

// ANDROID: Unconditionally use std to allow building as a dylib
#[macro_use]
extern crate std;

use core::fmt;

/// The `Iterator` extension trait that provides the `peeking_take_while`
/// method.
///
/// See the [module documentation](./index.html) for details.
pub trait PeekableExt<I>: Iterator
where
    I: Iterator,
{
    /// The `peeking_take_while` method is very similar to `take_while`, but behaves
    /// differently when used with a borrowed iterator (perhaps returned by
    /// `Iterator::by_ref`).
    ///
    /// `peeking_take_while` peeks at the next item in the iterator and runs the
    /// predicate on that peeked item. This avoids consuming the first item yielded
    /// by the underlying iterator for which the predicate returns `false`. On the
    /// other hand, `take_while` will consume that first item for which the
    /// predicate returns `false`, and it will be lost.
    ///
    /// In contrast to `take_while`, iterating the iterator might call the predicate again
    /// after it first returned `false` (the returned iterator isn't fused).
    /// If that is not intended, calling [`fuse`](Iterator::fuse) on the returned iterator
    /// prevents that.
    fn peeking_take_while<P>(&mut self, predicate: P) -> PeekingTakeWhile<'_, I, P>
    where
        P: FnMut(&Self::Item) -> bool;
}

impl<I: Iterator> PeekableExt<I> for core::iter::Peekable<I> {
    #[inline]
    fn peeking_take_while<P>(&mut self, predicate: P) -> PeekingTakeWhile<'_, I, P>
    where
        P: FnMut(&Self::Item) -> bool,
    {
        PeekingTakeWhile {
            iter: self,
            predicate,
        }
    }
}

/// The iterator returned by `peeking_take_while`.
///
/// See the [module documentation](./index.html) for details.
pub struct PeekingTakeWhile<'a, I, P>
where
    I: Iterator,
{
    pub(crate) iter: &'a mut core::iter::Peekable<I>,
    pub(crate) predicate: P,
}

impl<I, P> fmt::Debug for PeekingTakeWhile<'_, I, P>
where
    I: Iterator + fmt::Debug,
    I::Item: fmt::Debug,
{
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("PeekingTakeWhile")
            .field("iter", &self.iter)
            .finish()
    }
}

impl<I, P> Iterator for PeekingTakeWhile<'_, I, P>
where
    I: Iterator,
    P: FnMut(&I::Item) -> bool,
{
    type Item = I::Item;

    #[inline]
    fn next(&mut self) -> Option<Self::Item> {
        self.iter.next_if(&mut self.predicate)
    }

    #[inline]
    fn size_hint(&self) -> (usize, Option<usize>) {
        // can't know a lower bound, due to the predicate
        (0, self.iter.size_hint().1)
    }

    #[inline]
    fn fold<B, F>(mut self, mut accum: B, mut f: F) -> B
    where
        F: FnMut(B, I::Item) -> B,
    {
        while let Some(x) = self.iter.next_if(&mut self.predicate) {
            accum = f(accum, x);
        }
        accum
    }
}

// interestingly, `PeekingTakeWhile` is not automatically fused,
// even when the inner iterator is fused, see also: `tests::not_fused`.

#[cfg(test)]
mod tests {
    use crate::PeekableExt;

    #[test]
    fn basic() {
        let mut it0 = (1..11).peekable();
        let a: u32 = it0.peeking_take_while(|&i| i < 5).sum();
        let b: u32 = it0.sum();
        assert_eq!(a, 10);
        assert_eq!(b, 45);
    }

    #[test]
    fn basic_fused() {
        let mut it0 = (1..11).peekable();
        let a: u32 = it0.peeking_take_while(|&i| i < 5).fuse().sum();
        let b: u32 = it0.sum();
        assert_eq!(a, 10);
        assert_eq!(b, 45);
    }

    #[test]
    fn not_fused() {
        let mut it0 = (0..10).peekable();
        let mut ax = true;
        let mut it1 = it0.peeking_take_while(|_| {
            ax = !ax;
            ax
        });
        assert!(it1.next().is_none());
        assert_eq!(it1.next(), Some(0));
        assert!(it1.next().is_none());
        assert_eq!(it1.next(), Some(1));
        assert_eq!(ax, true);
    }

    #[test]
    fn fused() {
        let mut it0 = (0..10).peekable();
        let mut ax = true;
        let mut it1 = it0
            .peeking_take_while(|_| {
                ax = !ax;
                ax
            })
            .fuse();
        assert!(it1.next().is_none());
        assert!(it1.next().is_none());
        assert_eq!(ax, false);
    }
}
