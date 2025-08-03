/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.registration;

import android.telephony.ims.SipMessage;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.ParseExceptionListener;
import gov.nist.javax.sip.parser.StringMsgParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Iterator;

import javax.sip.header.ContentLengthHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/***
 * Class responsible of converting an RCS SIP Message
 * {@link Message} to a Platform SIP message
 * {@link SipMessage} and vice versa.
 */
public final class MessageConverter {

    private MessageConverter() {
    }

    public static SipMessage toPlatformMessage(Message message) {
        String startLine;
        if (message instanceof Request) {
            startLine = getRequestStartLine((Request) message);
        } else {
            startLine = getResponseStartLine((Response) message);
        }

        StringBuilder headers = new StringBuilder();
        for (Iterator<SIPHeader> it = ((SIPMessage) message).getHeaders(); it.hasNext(); ) {
            SIPHeader header = it.next();
            if (header instanceof ContentLengthHeader) {
                continue;
            }
            headers.append(header);
        }

        int length = message.getRawContent() != null ? message.getRawContent().length : 0;
        headers
                .append(SIPHeader.CONTENT_LENGTH)
                .append(": ")
                .append(length)
                .append("\r\n");

        byte[] rawContent = message.getRawContent();
        rawContent = rawContent == null ? new byte[0] : message.getRawContent();
        return new SipMessage(startLine, headers.toString(), rawContent);
    }

    public static Message toStackMessage(SipMessage message) throws ParseException {
        // The AOSP version of nist-sip has a parseSIPMessage() method that has a different
        // contract.
        // Fallback to parseSIPMessage(byte[] msgBuffer) in case the first attempt fails.
        Method method;
        try {
            method =
                    StringMsgParser.class.getDeclaredMethod(
                            "parseSIPMessage",
                            byte[].class,
                            boolean.class,
                            boolean.class,
                            ParseExceptionListener.class);
            return (Message)
                    method.invoke(
                            new StringMsgParser(),
                            message.toEncodedMessage(),
                            true,
                            false,
                            (ParseExceptionListener)
                                    (ex, sipMessage, headerClass, headerText, messageText) -> {
                                        throw ex;
                                    });
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            try {
                method = StringMsgParser.class.getDeclaredMethod("parseSIPMessage", byte[].class);
                return (Message) method.invoke(new StringMsgParser(), message.toEncodedMessage());
            } catch (IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException ex) {
                ex.printStackTrace();
                throw new ParseException("Failed to invoke parseSIPMessage", 0);
            }
        }
    }

    private static String getRequestStartLine(Request request) {
        StringBuilder startLine = new StringBuilder();

        startLine.append(request.getMethod());
        startLine.append(" ");
        startLine.append(request.getRequestURI());
        startLine.append(" SIP/2.0\r\n");

        return startLine.toString();
    }

    private static String getResponseStartLine(Response response) {
        StringBuilder startLine = new StringBuilder();

        startLine.append("SIP/2.0 ");
        startLine.append(response.getStatusCode());
        startLine.append(" ");
        startLine.append(response.getReasonPhrase());
        startLine.append("\r\n");

        return startLine.toString();
    }
}
