package test6;

parcelable Foo {
    ParcelableHolder ph;
    ParcelFileDescriptor[] pfds; // not moveable
}
