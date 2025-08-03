package com.example.sampleleanbacklauncher.notifications;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Parcel;

public class TvNotification {
    /**
     * This projection MUST be used for the query when using {@link #fromCursor(Cursor)}.
     */
    public static final String[] PROJECTION =
            {NotificationsContract.COLUMN_SBN_KEY,
                    NotificationsContract.COLUMN_PACKAGE_NAME,
                    NotificationsContract.COLUMN_NOTIF_TITLE,
                    NotificationsContract.COLUMN_NOTIF_TEXT,
                    NotificationsContract.COLUMN_DISMISSIBLE,
                    NotificationsContract.COLUMN_ONGOING,
                    NotificationsContract.COLUMN_SMALL_ICON,
                    NotificationsContract.COLUMN_CHANNEL,
                    NotificationsContract.COLUMN_PROGRESS,
                    NotificationsContract.COLUMN_PROGRESS_MAX,
                    NotificationsContract.COLUMN_HAS_CONTENT_INTENT,
                    NotificationsContract.COLUMN_BIG_PICTURE,
                    NotificationsContract.COLUMN_CONTENT_BUTTON_LABEL,
                    NotificationsContract.COLUMN_DISMISS_BUTTON_LABEL,
                    NotificationsContract.COLUMN_TAG};

    public static final int COLUMN_INDEX_KEY = 0;
    public static final int COLUMN_INDEX_PACKAGE_NAME = 1;
    public static final int COLUMN_INDEX_NOTIF_TITLE = 2;
    public static final int COLUMN_INDEX_NOTIF_TEXT = 3;
    public static final int COLUMN_INDEX_DISMISSIBLE = 4;
    public static final int COLUMN_INDEX_ONGOING = 5;
    public static final int COLUMN_INDEX_SMALL_ICON = 6;
    public static final int COLUMN_INDEX_CHANNEL = 7;
    public static final int COLUMN_INDEX_PROGRESS = 8;
    public static final int COLUMN_INDEX_PROGRESS_MAX = 9;
    public static final int COLUMN_INDEX_HAS_CONTENT_INTENT = 10;
    public static final int COLUMN_INDEX_BIG_PICTURE = 11;
    public static final int COLUMN_INDEX_CONTENT_BUTTON_LABEL = 12;
    public static final int COLUMN_INDEX_DISMISS_BUTTON_LABEL = 13;
    public static final int COLUMN_INDEX_TAG = 14;

    private String mNotificationKey;
    private String mPackageName;
    private String mTitle;
    private String mText;
    private boolean mDismissible;
    private boolean mIsOngoing;
    private Icon mSmallIcon;
    private int mChannel;
    private int mProgress;
    private int mProgressMax;
    private boolean mHasContentIntent;
    private Bitmap mBigPicture;
    private String mContentButtonLabel;
    private String mDismissButtonLabel;
    private String mTag;

    public TvNotification(String key, String packageName, String title, String text,
                          boolean dismissible, boolean ongoing, Icon smallIcon, int channel,
                          int progress, int progressMax, boolean hasContentIntent, Bitmap bigPicture,
                          String contentButtonLabel, String dismissButtonLabel, String tag) {
        mNotificationKey = key;
        mPackageName = packageName;
        mTitle = title;
        mText = text;
        mDismissible = dismissible;
        mIsOngoing = ongoing;
        mSmallIcon = smallIcon;
        mChannel = channel;
        mProgress = progress;
        mProgressMax = progressMax;
        mHasContentIntent = hasContentIntent;
        mBigPicture = bigPicture;
        mContentButtonLabel = contentButtonLabel;
        mDismissButtonLabel = dismissButtonLabel;
        mTag = tag;
    }

    public String getNotificationKey() {
        return mNotificationKey;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getText() {
        return mText;
    }

    public boolean isDismissible() {
        return mDismissible;
    }

    public boolean isOngoing() {
        return mIsOngoing;
    }

    public Icon getSmallIcon() {
        return mSmallIcon;
    }

    public int getChannel() {
        return mChannel;
    }

    public int getProgress() {
        return mProgress;
    }

    public int getProgressMax() {
        return mProgressMax;
    }

    public boolean hasContentIntent() {
        return mHasContentIntent;
    }

    public Bitmap getBigPicture() {
        return mBigPicture;
    }

    public String getContentButtonLabel() {
        return mContentButtonLabel;
    }

    public String getDismissButtonLabel() {
        return mDismissButtonLabel;
    }

    public String getTag() {
        return mTag;
    }

    // Converts cursor returned from query with PROJECTION
    public static TvNotification fromCursor(Cursor cursor) {
        int index = 0;
        String key = cursor.getString(index++);
        String packageName = cursor.getString(index++);
        String title = cursor.getString(index++);
        String text = cursor.getString(index++);
        boolean dismissible = cursor.getInt(index++) != 0;
        boolean ongoing = cursor.getInt(index++) != 0;
        byte[] smallIconData = cursor.getBlob(index++);
        Icon smallIcon = getIconFromBytes(smallIconData);

        int channel = cursor.getInt(index++);
        int progress = cursor.getInt(index++);
        int progressMax = cursor.getInt(index++);
        boolean hasContentIntent = cursor.getInt(index++) != 0;
        byte[] bigPictureData = cursor.getBlob(index++);
        Bitmap bigPicture = getBitmapFromBytes(bigPictureData);
        String contentButtonLabel = cursor.getString(index++);
        String dismissButtonLabel = cursor.getString(index++);
        String tag = cursor.getString(index);

        return new TvNotification(key, packageName, title, text, dismissible, ongoing,
                smallIcon, channel, progress, progressMax, hasContentIntent, bigPicture,
                contentButtonLabel, dismissButtonLabel, tag);
    }

    private static Bitmap getBitmapFromBytes(byte[] blob) {
        if (blob != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.length);
            return bitmap;
        }

        return null;
    }

    private static Icon getIconFromBytes(byte[] blob) {
        Parcel in = Parcel.obtain();
        Icon icon = null;
        if (blob != null) {
            in.unmarshall(blob, 0, blob.length);
            in.setDataPosition(0);
            icon = in.readParcelable(Icon.class.getClassLoader());
        }

        in.recycle();
        return icon;
    }
}

