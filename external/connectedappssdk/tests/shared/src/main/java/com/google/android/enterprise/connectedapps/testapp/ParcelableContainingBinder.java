package com.google.android.enterprise.connectedapps.testapp;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableContainingBinder implements Parcelable {

  IBinder binder;

  public ParcelableContainingBinder() {
    binder = new Binder();
  }

  private ParcelableContainingBinder(Parcel in) {
    binder = in.readStrongBinder();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeStrongBinder(binder);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<ParcelableContainingBinder> CREATOR =
      new Creator<ParcelableContainingBinder>() {
        @Override
        public ParcelableContainingBinder createFromParcel(Parcel in) {
          return new ParcelableContainingBinder(in);
        }

        @Override
        public ParcelableContainingBinder[] newArray(int size) {
          return new ParcelableContainingBinder[size];
        }
      };
}
