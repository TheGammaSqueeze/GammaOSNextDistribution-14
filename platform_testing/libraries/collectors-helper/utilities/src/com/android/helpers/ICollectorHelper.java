package com.android.helpers;

import java.util.Map;
import java.util.function.Function;

public interface ICollectorHelper<T> {

    /**
     * This method will have setup to start collecting the metrics.
     */
    boolean startCollecting();

    /**
     * This method will take args which passes an identifier for the helper.
     * The default implementation is to invoke {@link #startCollecting()} directly.
     */
    default boolean startCollecting(String id) {
        return startCollecting();
    }

    /**
     * This method will have setup to start collecting the metrics. The default implementation is to
     * invoke {@link #startCollecting()} directly. To apply the filters, must overload this method
     * and use the filters.
     *
     * @param filters a filter which is used to filter unwanted metrics.
     */
    default boolean startCollecting(Function<String, Boolean> filters) {
        return startCollecting();
    }

    /**
     * This method will retrieve the metrics.
     */
    Map<String, T> getMetrics();

    /**
     * This method will do the tear down to stop collecting the metrics.
     */
    boolean stopCollecting();

}
