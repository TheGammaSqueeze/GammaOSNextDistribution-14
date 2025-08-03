package com.android.bluetooth.mapclient;

import java.util.Date;

/**
 * Object representation of filters to be applied on message listing
 *
 * @see #getMessagesListing(String, int, MessagesFilter, int)
 * @see #getMessagesListing(String, int, MessagesFilter, int, int, int)
 */
public final class MessagesFilter {

    public static final byte MESSAGE_TYPE_ALL = 0x00;
    public static final byte MESSAGE_TYPE_SMS_GSM = 0x01;
    public static final byte MESSAGE_TYPE_SMS_CDMA = 0x02;
    public static final byte MESSAGE_TYPE_EMAIL = 0x04;
    public static final byte MESSAGE_TYPE_MMS = 0x08;

    public static final byte READ_STATUS_ANY = 0x00;
    public static final byte READ_STATUS_UNREAD = 0x01;
    public static final byte READ_STATUS_READ = 0x02;

    public static final byte PRIORITY_ANY = 0x00;
    public static final byte PRIORITY_HIGH = 0x01;
    public static final byte PRIORITY_NON_HIGH = 0x02;

    public byte messageType = MESSAGE_TYPE_ALL;

    public String periodBegin = null;

    public String periodEnd = null;

    public byte readStatus = READ_STATUS_ANY;

    public String recipient = null;

    public String originator = null;

    public byte priority = PRIORITY_ANY;

    public MessagesFilter() {
    }

    public MessagesFilter(MessagesFilter filter) {
        this.messageType = filter.messageType;
        this.periodBegin = filter.periodBegin;
        this.periodEnd = filter.periodEnd;
        this.readStatus = filter.readStatus;
        this.recipient = filter.recipient;
        this.originator = filter.originator;
        this.priority = filter.priority;
    }

    public void setMessageType(byte filter) {
        messageType = filter;
    }

    public void setPeriod(Date filterBegin, Date filterEnd) {
        //Handle possible NPE for obexTime constructor utility
        if (filterBegin != null) {
            periodBegin = (new ObexTime(filterBegin)).toString();
        }
        if (filterEnd != null) {
            periodEnd = (new ObexTime(filterEnd)).toString();
        }
    }

    public void setReadStatus(byte readfilter) {
        readStatus = readfilter;
    }

    public void setRecipient(String filter) {
        if (filter != null && filter.isEmpty()) {
            recipient = null;
        } else {
            recipient = filter;
        }
    }

    public void setOriginator(String filter) {
        if (filter != null && filter.isEmpty()) {
            originator = null;
        } else {
            originator = filter;
        }
    }

    public void setPriority(byte filter) {
        priority = filter;
    }

    /** Builder for a {@link MessagesFilter}. */
    public static class Builder {
        private MessagesFilter mMessagesFilter = new MessagesFilter();

        /**
         * Sets the `Message Type` field of the filter.
         *
         * @param messageType to filter on.
         * @return This {@link Builder} object.
         */
        public Builder setMessageType(byte messageType) {
            mMessagesFilter.setMessageType(messageType);
            return this;
        }

        /**
         * Sets the `Originator` field of the filter.
         *
         * @param originator to filter on.
         * @return This {@link Builder} object.
         */
        public Builder setOriginator(String originator) {
            mMessagesFilter.setOriginator(originator);
            return this;
        }

        /**
         * Sets the `Period` field of the filter.
         *
         * @param filterBegin filter out messages that arrive before this time instance. Can be
         *     {@code null}, in which case no limit on how old a message can be.
         * @param filterEnd filter out messages that arrive after this time instance. Can be {@code
         *     null}, in which case no limit on how new a message can be.
         * @return This {@link Builder} object.
         */
        public Builder setPeriod(Date filterBegin, Date filterEnd) {
            mMessagesFilter.setPeriod(filterBegin, filterEnd);
            return this;
        }

        /**
         * Sets the `Priority` field of the filter.
         *
         * @param priority to filter on.
         * @return This {@link Builder} object.
         */
        public Builder setPriority(byte priority) {
            mMessagesFilter.setPriority(priority);
            return this;
        }

        /**
         * Sets the `Read Status` field of the filter.
         *
         * @param status to filter on.
         * @return This {@link Builder} object.
         */
        public Builder setReadStatus(byte status) {
            mMessagesFilter.setReadStatus(status);
            return this;
        }

        /**
         * Sets the `Recipient` field of the filter.
         *
         * @param recipient to filter on.
         * @return This {@link Builder} object.
         */
        public Builder setRecipient(String recipient) {
            mMessagesFilter.setRecipient(recipient);
            return this;
        }

        /**
         * Build the {@link MessagesFilter}.
         *
         * @return A {@link MessagesFilter} object.
         */
        public MessagesFilter build() {
            return new MessagesFilter(mMessagesFilter);
        }
    }
}
