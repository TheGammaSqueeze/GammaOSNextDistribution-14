/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.iwlan;

import android.net.ipsec.ike.exceptions.IkeIOException;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.ipsec.ike.exceptions.IkeNetworkLostException;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.Map;

public class IwlanError {

    // error types
    public static final int NO_ERROR = 0;

    // IKE lib related
    public static final int IKE_PROTOCOL_EXCEPTION = 1;
    public static final int IKE_INTERNAL_IO_EXCEPTION = 2;
    public static final int IKE_GENERIC_EXCEPTION = 3; // catch all

    // Known internal types
    public static final int EPDG_SELECTOR_SERVER_SELECTION_FAILED = 4;
    public static final int TUNNEL_TRANSFORM_FAILED = 5;
    public static final int SIM_NOT_READY_EXCEPTION = 6;
    public static final int IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED = 7;
    public static final int IKE_NETWORK_LOST_EXCEPTION = 8;
    public static final int TUNNEL_NOT_FOUND = 9;
    public static final int EPDG_ADDRESS_ONLY_IPV4_ALLOWED = 10;
    public static final int EPDG_ADDRESS_ONLY_IPV6_ALLOWED = 11;
    public static final int IKE_INIT_TIMEOUT = 12;
    public static final int IKE_MOBILITY_TIMEOUT = 13;
    public static final int IKE_DPD_TIMEOUT = 14;

    @IntDef({
        NO_ERROR,
        IKE_PROTOCOL_EXCEPTION,
        IKE_INTERNAL_IO_EXCEPTION,
        IKE_GENERIC_EXCEPTION,
        EPDG_SELECTOR_SERVER_SELECTION_FAILED,
        TUNNEL_TRANSFORM_FAILED,
        SIM_NOT_READY_EXCEPTION,
        IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED,
        IKE_NETWORK_LOST_EXCEPTION,
        TUNNEL_NOT_FOUND,
        EPDG_ADDRESS_ONLY_IPV4_ALLOWED,
        EPDG_ADDRESS_ONLY_IPV6_ALLOWED,
        IKE_INIT_TIMEOUT,
        IKE_MOBILITY_TIMEOUT,
        IKE_DPD_TIMEOUT
    })
    @interface IwlanErrorType {}

    private static final Map<Integer, String> sErrorTypeStrings =
            Map.ofEntries(
                    Map.entry(NO_ERROR, "IWLAN_NO_ERROR"),
                    Map.entry(IKE_PROTOCOL_EXCEPTION, "IWLAN_IKE_PROTOCOL_EXCEPTION"),
                    Map.entry(IKE_INTERNAL_IO_EXCEPTION, "IWLAN_IKE_INTERNAL_IO_EXCEPTION"),
                    Map.entry(IKE_GENERIC_EXCEPTION, "IWLAN_IKE_GENERIC_EXCEPTION"),
                    Map.entry(
                            EPDG_SELECTOR_SERVER_SELECTION_FAILED,
                            "IWLAN_EPDG_SELECTOR_SERVER_SELECTION_FAILED"),
                    Map.entry(TUNNEL_TRANSFORM_FAILED, "IWLAN_TUNNEL_TRANSFORM_FAILED"),
                    Map.entry(SIM_NOT_READY_EXCEPTION, "IWLAN_SIM_NOT_READY_EXCEPTION"),
                    Map.entry(
                            IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED,
                            "IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED"),
                    Map.entry(IKE_NETWORK_LOST_EXCEPTION, "IWLAN_IKE_NETWORK_LOST_EXCEPTION"),
                    Map.entry(TUNNEL_NOT_FOUND, "IWLAN_TUNNEL_NOT_FOUND"),
                    Map.entry(IKE_INIT_TIMEOUT, "IKE_INIT_TIMEOUT"),
                    Map.entry(IKE_MOBILITY_TIMEOUT, "IKE_MOBILITY_TIMEOUT"),
                    Map.entry(IKE_DPD_TIMEOUT, "IKE_DPD_TIMEOUT"),
                    Map.entry(EPDG_ADDRESS_ONLY_IPV4_ALLOWED, "EPDG_ADDRESS_ONLY_IPV4_ALLOWED"),
                    Map.entry(EPDG_ADDRESS_ONLY_IPV6_ALLOWED, "EPDG_ADDRESS_ONLY_IPV6_ALLOWED"));
    private int mErrorType;
    private Exception mException;

    public IwlanError(@IwlanErrorType int err) {
        mErrorType = err;
    }

    public IwlanError(@IwlanErrorType int err, @NonNull Exception exception) {
        mErrorType = err;
        mException = exception;
    }

    /**
     * Sets the IwlanError based on the Exception: 1. IkeException is base the class for all IKE
     * exception ErrorType: IKE_GENERIC_EXCEPTION. 2. IkeProtocolException is for specific protocol
     * errors (like IKE notify error codes) ErrorType: IKE_PROTOCOL_EXCEPTION 3.
     * IkeInternalException is just a wrapper for various exceptions that IKE lib may encounter
     * ErrorType: IKE_INTERNAL_IO_EXCEPTION if the Exception is instance of IOException ErrorType:
     * IKE_GENERIC_EXCEPTION for all the other.
     */
    public IwlanError(@NonNull Exception exception) {
        // resolve into specific types if possible
        if (exception instanceof IkeProtocolException) {
            IwlanErrorIkeProtocolException((IkeProtocolException) exception);
        } else if (exception instanceof IkeIOException) {
            IwlanErrorIkeIOException((IkeIOException) exception);
        } else if (exception instanceof IkeInternalException) {
            IwlanErrorIkeInternalException((IkeInternalException) exception);
        } else if (exception instanceof IkeNetworkLostException) {
            IwlanErrorIkeNetworkLostException((IkeNetworkLostException) exception);
        } else {
            mErrorType = IKE_GENERIC_EXCEPTION;
            mException = exception;
        }
    }

    private void IwlanErrorIkeProtocolException(@NonNull IkeProtocolException exception) {
        mErrorType = IKE_PROTOCOL_EXCEPTION;
        mException = exception;
    }

    private void IwlanErrorIkeInternalException(@NonNull IkeInternalException exception) {
        if (exception.getCause() instanceof IOException) {
            mErrorType = IKE_INTERNAL_IO_EXCEPTION;
        } else {
            mErrorType = IKE_GENERIC_EXCEPTION;
        }
        mException = exception;
    }

    private void IwlanErrorIkeIOException(@NonNull IkeIOException exception) {
        mErrorType = IKE_INTERNAL_IO_EXCEPTION;
        mException = exception;
    }

    private void IwlanErrorIkeNetworkLostException(@NonNull IkeNetworkLostException exception) {
        mErrorType = IKE_NETWORK_LOST_EXCEPTION;
        mException = exception;
    }

    public @IwlanErrorType int getErrorType() {
        return mErrorType;
    }

    public Exception getException() {
        return mException;
    }

    private static @NonNull String getErrorTypeString(@IwlanErrorType int error) {
        String s = sErrorTypeStrings.get(error);
        return (s == null ? "IWLAN_UNKNOWN_ERROR_TYPE" : s);
    }

    @Override
    public String toString() {
        return ("TYPE: " + getErrorTypeString(mErrorType) + " " + errorDetailsString());
    }

    private String errorDetailsString() {
        StringBuilder sb = new StringBuilder();

        if (mException == null) {
            return "";
        }

        switch (mErrorType) {
            case IKE_GENERIC_EXCEPTION:
                sb.append("MSG: ").append(mException.getMessage()).append("\n CAUSE: ");
                sb.append(mException.getCause());
                break;
            case IKE_PROTOCOL_EXCEPTION:
                sb.append("ERR: ")
                        .append(((IkeProtocolException) mException).getErrorType())
                        .append("\nDATA:");
                for (byte b : ((IkeProtocolException) mException).getErrorData()) {
                    sb.append(String.format("%02x ", b));
                }
                break;
            case IKE_NETWORK_LOST_EXCEPTION:
                sb.append("ERR: ")
                        .append(mException.getMessage())
                        .append("\n CAUSE: ")
                        .append(mException.getCause())
                        .append("\n NETWORK: ")
                        .append(((IkeNetworkLostException) mException).getNetwork());
                break;
            default:
                sb.append("-No Details-");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IwlanError)) {
            return false;
        }
        IwlanError error = (IwlanError) o;
        boolean ret = false;
        if (mErrorType == error.getErrorType()) {
            if (mException != null && error.getException() != null) {
                ret = mException.getClass().equals(error.getException().getClass());
                if (ret && mException instanceof IkeProtocolException) {
                    ret =
                            (((IkeProtocolException) mException).getErrorType()
                                    == ((IkeProtocolException) error.getException())
                                            .getErrorType());
                }
            } else if (mException == null && error.getException() == null) {
                ret = true;
            }
        }
        return ret;
    }
}
