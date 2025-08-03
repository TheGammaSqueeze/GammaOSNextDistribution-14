package com.android.eventlib;

/**
 * Service exposed to allow other packages to query logged events in this package.
 */
interface IQueryService {
    /**
     * Remote equivalent of {@code EventQuerier#poll}.
     *
     * <p>The {@code data} must contain a {@code EARLIEST_LOG_TIME} key which contains a serialized
     * instance of {@code Instant}, a {@code TIMEOUT} key which contains a serialized instance
     * of {@code Duration}, and a {@code QUERIER} key which contains a serialized instance
     * of {@code EventQuerier}.
     *
     * <p>The return {@code Bundle} will contain a {@code EVENT} key with a serialized instance of
     * {@code Event}.
     */
    Bundle poll(in Bundle data, int skip);
}