package com.android.tv.settings.library.util;

import android.os.Binder;
import android.os.IBinder;

/**
 * Can be used to pass arbitrary data in an intent within the same process. Note that data will
 * be lost if intent is serialized and deserialized.
 */
public class DataBinder<T> extends Binder {
    public final T data;

    public static <T> DataBinder<T> with(T data) {
        return new DataBinder<>(data);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(IBinder binder) {
        return  ((DataBinder<T>) binder).data;
    }

    private DataBinder(T data) {
        this.data = data;
    }
}
