package com.google.android.car.kitchensink.notification;

import static android.app.Notification.FLAG_FOREGROUND_SERVICE;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Test fragment that can send all sorts of notifications.
 */
public class NotificationFragment extends Fragment {
    private static final String IMPORTANCE_HIGH_ID = "importance_high";
    private static final String IMPORTANCE_HIGH_NO_SOUND_ID = "importance_high_no_sound";
    private static final String IMPORTANCE_DEFAULT_ID = "importance_default";
    private static final String IMPORTANCE_LOW_ID = "importance_low";
    private static final String IMPORTANCE_MIN_ID = "importance_min";
    private static final String IMPORTANCE_NONE_ID = "importance_none";
    public static final String INTENT_CATEGORY_SELF_DISMISS =
            "com.google.android.car.kitchensink.notification.INTENT_CATEGORY_SELF_DISMISS";
    public static final int SELF_DISMISS_NOTIFICATION_ID = 987;
    private int mCurrentNotificationId;
    private int mCurrentGroupNotificationCount;
    private NotificationManager mManager;
    private Context mContext;
    private Handler mHandler = new Handler();
    private HashMap<Integer, Runnable> mUpdateRunnables = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_HIGH_ID, "Importance High", NotificationManager.IMPORTANCE_HIGH));

        NotificationChannel noSoundChannel = new NotificationChannel(
                IMPORTANCE_HIGH_NO_SOUND_ID, "No sound", NotificationManager.IMPORTANCE_HIGH);
        noSoundChannel.setSound(null, null);
        mManager.createNotificationChannel(noSoundChannel);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_DEFAULT_ID,
                "Importance Default",
                NotificationManager.IMPORTANCE_DEFAULT));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_LOW_ID, "Importance Low", NotificationManager.IMPORTANCE_LOW));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_MIN_ID, "Importance Min", NotificationManager.IMPORTANCE_MIN));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_NONE_ID, "Importance None", NotificationManager.IMPORTANCE_NONE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.notification_fragment, container, false);

        initCancelAllButton(view);

        initCarCategoriesButton(view);

        initImportanceHighBotton(view);
        initImportanceDefaultButton(view);
        initImportanceLowButton(view);
        initImportanceMinButton(view);

        initIncomingButton(view);
        initOngoingButton(view);
        initMessagingStyleButtonForDiffPerson(view);
        initMessagingStyleButtonForSamePerson(view);
        initMessagingStyleButtonForLongMessageSamePerson(view);
        initMessagingStyleButtonForMessageSameGroup(view);
        initMessagingStyleButtonWithMuteAction(view);
        initTestMessagesButton(view);
        initProgressButton(view);
        initProgressColorizedButton(view);
        initNavigationButton(view);
        initMediaButton(view);
        initCallButton(view);
        initCustomGroupSummaryButton(view);
        initGroupWithoutSummaryButton(view);
        initCustomizableMessageButton(view);
        initButtonWithCustomActionIcon(view);
        initSelfRemovingNotification(view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            view.post(() -> view.scrollTo(0, view.findViewById(R.id.fragment_top).getTop()));
        }
    }

    private PendingIntent createServiceIntent(int notificationId, String action) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class).setAction(action);

        return PendingIntent.getForegroundService(mContext, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private void initCancelAllButton(View view) {
        view.findViewById(R.id.cancel_all_button).setOnClickListener(v -> {
            for (Runnable runnable : mUpdateRunnables.values()) {
                mHandler.removeCallbacks(runnable);
            }
            mUpdateRunnables.clear();
            mManager.cancelAll();
        });
    }

    private void initCarCategoriesButton(View view) {
        view.findViewById(R.id.category_car_emergency_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Car Emergency")
                    .setContentText("Shows heads-up; Shows on top of the list; Does not group")
                    .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_warning_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Car Warning")
                    .setContentText(
                            "Shows heads-up; Shows on top of the list but below Car Emergency; "
                                    + "Does not group")
                    .setCategory(Notification.CATEGORY_CAR_WARNING)
                    .setColor(mContext.getColor(android.R.color.holo_orange_dark))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_info_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Car information")
                    .setContentText("Doesn't show heads-up; Importance Default; Groups")
                    .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                    .setColor(mContext.getColor(android.R.color.holo_orange_light))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

    }

    private void initImportanceHighBotton(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification1 = new Notification
                .Builder(mContext, IMPORTANCE_HIGH_ID)
                .setContentTitle("Importance High: Shows as a heads-up")
                .setContentText(
                        "Each click generates a new notification. And some "
                                + "looooooong text. "
                                + "Loooooooooooooooooooooong. "
                                + "Loooooooooooooooooooooooooooooooooooooooooooooooooong.")
                .setSmallIcon(R.drawable.car_ic_mode)
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", pendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Action (no-op)", pendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", pendingIntent).build())
                .setColor(mContext.getColor(android.R.color.holo_red_light))
                .build();

        view.findViewById(R.id.importance_high_button).setOnClickListener(
                v -> mManager.notify(mCurrentNotificationId++, notification1)
        );
    }

    private void initImportanceDefaultButton(View view) {
        view.findViewById(R.id.importance_default_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("No heads-up; Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceLowButton(View view) {
        view.findViewById(R.id.importance_low_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_LOW_ID)
                    .setContentTitle("Importance Low")
                    .setContentText("No heads-up; Below Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceMinButton(View view) {
        view.findViewById(R.id.importance_min_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_MIN_ID)
                    .setContentTitle("Importance Min")
                    .setContentText("No heads-up; Below Importance Low; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private Notification.Action getAction(String text, @DrawableRes int actionIcon) {
        Icon icon = Icon.createWithResource(mContext, actionIcon);
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(icon, text, pendingIntent).build();
    }

    private void initIncomingButton(View view) {
        view.findViewById(R.id.incoming_notificationbuilder_button).setOnClickListener(v -> {
            Notification notification = new Notification.Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setContentTitle("Unknown number")
                    .setContentText("Incoming call")
                    .setOngoing(true)
                    .setActions(
                            getAction("Answer", R.drawable.ic_answer_icon),
                            getAction("Decline", R.drawable.ic_decline_icon))
                    .build();

            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.incoming_forIncomingCall_button).setOnClickListener(v -> {

            android.app.Person caller = new android.app.Person.Builder()
                    .setName("Chuck Norris")
                    .setImportant(true)
                    .build();
            // Creating the call notification style
            int declineId = mCurrentNotificationId++;
            int answerId = mCurrentNotificationId++;
            PendingIntent declineIntent = createServiceIntent(declineId, "Decline");
            PendingIntent answerIntent = createServiceIntent(answerId, "Answer");
            Notification.CallStyle notificationStyle =
                    Notification.CallStyle.forIncomingCall(caller, declineIntent, answerIntent);

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setContentTitle("Incoming call")
                    .setContentText("Incoming call from Chuck Norris")
                    .setStyle(notificationStyle)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_CALL)
                    .build();
            notification.flags = notification.flags | FLAG_FOREGROUND_SERVICE;
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initOngoingButton(View view) {
        view.findViewById(R.id.ongoing_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Persistent/Ongoing Notification")
                    .setContentText("Cannot be dismissed; No heads-up; Importance default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setOngoing(true)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initCustomizableMessageButton(View view) {
        NumberPicker messagesPicker = view.findViewById(R.id.number_messages);
        messagesPicker.setMinValue(1);
        messagesPicker.setMaxValue(25);
        messagesPicker.setWrapSelectorWheel(true);
        NumberPicker peoplePicker = view.findViewById(R.id.number_people);
        peoplePicker.setMinValue(1);
        peoplePicker.setMaxValue(25);
        peoplePicker.setWrapSelectorWheel(true);

        view.findViewById(R.id.customizable_message_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            int numPeople = peoplePicker.getValue();
            int numMessages = messagesPicker.getValue();

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            List<Person> personList = new ArrayList<>();

            for (int i = 1; i <= numPeople; i++) {
                personList.add(new Person.Builder()
                        .setName("Person " + i)
                        .setIcon(IconCompat.createWithResource(v.getContext(),
                                i % 2 == 1 ? R.drawable.avatar1 : R.drawable.avatar2))
                        .build());
            }

            MessagingStyle messagingStyle =
                    new MessagingStyle(personList.get(0))
                            .setConversationTitle("Customizable Group chat: " + id);
            if (personList.size() > 1) {
                messagingStyle.setGroupConversation(true);
            }

            int messageNumber = 1;
            for (int i = 0; i < numMessages; i++) {
                int personNum = i % numPeople;
                if (personNum == numPeople - 1) {
                    messageNumber++;
                }
                Person person = personList.get(personNum);
                String messageText = person.getName() + "'s " + messageNumber + " message";
                messagingStyle.addMessage(
                        new MessagingStyle.Message(
                                messageText,
                                System.currentTimeMillis(),
                                person));
            }

            NotificationCompat.Builder notification = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Customizable Group chat (Title)")
                    .setContentText("Customizable Group chat (Text)")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            mManager.notify(id, notification.build());
        });
    }

    private void initMessagingStyleButtonForDiffPerson(View view) {
        view.findViewById(R.id.category_message_diff_person_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person person1 = new Person.Builder()
                    .setName("Person " + id)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.avatar1))
                    .build();
            Person person2 = new Person.Builder()
                    .setName("Person " + id + 1)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.android_logo))
                    .build();
            Person person3 = new Person.Builder()
                    .setName("Person " + id + 2)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.avatar2))
                    .build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person3)
                            .setConversationTitle("Group chat")
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person1.getName() + "'s message",
                                            System.currentTimeMillis(),
                                            person1))
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person2.getName() + "'s message",
                                            System.currentTimeMillis(),
                                            person2))
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person3.getName() + "'s message; "
                                                    + "Each click generates a new"
                                                    + "notification. And some looooooong text. "
                                                    + "Loooooooooooooooooooooong. "
                                                    + "Loooooooooooooooooooooooooong."
                                                    + "Long long long long text.",
                                            System.currentTimeMillis(),
                                            person3));

            NotificationCompat.Builder notification = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Jane, John, Joe")
                    .setContentText("Group chat")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            mManager.notify(id, notification.build());
        });
    }

    private void initMessagingStyleButtonForMessageSameGroup(View view) {
        int numOfPeople = 3;
        Person user = new Person.Builder()
                .setName("User")
                .setIcon(IconCompat.createWithResource(view.getContext(), R.drawable.avatar1))
                .build();

        MessagingStyle messagingStyle =
                new MessagingStyle(user)
                        .setConversationTitle("Same group chat")
                        .setGroupConversation(true);

        List<Person> personList = new ArrayList<>();
        for (int i = 1; i <= numOfPeople; i++) {
            personList.add(new Person.Builder()
                    .setName("Person " + i)
                    .setIcon(IconCompat.createWithResource(view.getContext(),
                            i % 2 == 1 ? R.drawable.avatar1 : R.drawable.avatar2))
                    .build());
        }

        view.findViewById(R.id.category_message_same_group_button).setOnClickListener(v -> {
            mCurrentGroupNotificationCount++;
            PendingIntent replyIntent = createServiceIntent(123456, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(123456, "read");
            Person person = personList.get(mCurrentGroupNotificationCount % numOfPeople);
            String messageText =
                    person.getName() + "'s " + mCurrentGroupNotificationCount + " message";
            messagingStyle.addMessage(
                    new MessagingStyle.Message(messageText, System.currentTimeMillis(), person));

            NotificationCompat.Builder notification = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Same Group chat (Title)")
                    .setContentText("Same Group chat (Text)")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            mManager.notify(123456, notification.build());
        });
    }

    private void initMessagingStyleButtonForSamePerson(View view) {
        view.findViewById(R.id.category_message_same_person_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person person = new Person.Builder().setName("John Doe").build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person).setConversationTitle("Hello!");
            NotificationCompat.Builder builder = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("hi")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            NotificationCompat.Builder updateNotification =
                    builder.setStyle(messagingStyle.addMessage(
                            new MessagingStyle.Message(
                                    "Message " + id,
                                    System.currentTimeMillis(),
                                    person)));
            mManager.notify(12345, updateNotification.build());
        });
    }

    private void initMessagingStyleButtonForLongMessageSamePerson(View view) {
        view.findViewById(R.id.category_long_message_same_person_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");


            Person person = new Person.Builder().setName("John Doe").build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person).setConversationTitle("Hello!");
            NotificationCompat.Builder builder = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("hi")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            String messageText = "";
            for (int i = 0; i < 100; i++) {
                messageText += " test";
            }

            NotificationCompat.Builder updateNotification =
                    builder.setStyle(messagingStyle.addMessage(
                            new MessagingStyle.Message(
                                    id + messageText,
                                    System.currentTimeMillis(),
                                    person)));
            mManager.notify(12345, updateNotification.build());
        });
    }


    private void initMessagingStyleButtonWithMuteAction(View view) {
        view.findViewById(R.id.category_message_mute_action_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");
            PendingIntent muteIntent = createServiceIntent(id, "mute");

            Person person = new Person.Builder().setName("John Doe").build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person).setConversationTitle("Hello, try muting me!");
            NotificationCompat.Builder builder = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("Muting notification when "
                            + "mute pending intent is provided by posting app")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "mute", muteIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MUTE)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            builder.setStyle(messagingStyle.addMessage(
                    new MessagingStyle.Message(
                            "Message with mute pending intent" + id,
                            System.currentTimeMillis(),
                            person)));
            mManager.notify(id, builder.build());
        });
    }

    private void initTestMessagesButton(View view) {
        view.findViewById(R.id.test_message_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person person = new Person.Builder().setName("John Doe " + id).build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person).setConversationTitle("Hello!");
            NotificationCompat.Builder builder = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("hi")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            Runnable runnable = new Runnable() {
                int mCount = 1;

                @Override
                public void run() {
                    NotificationCompat.Builder updateNotification =
                            builder.setStyle(messagingStyle.addMessage(
                                    new MessagingStyle.Message(
                                            "Message " + mCount++,
                                            System.currentTimeMillis(),
                                            person)));
                    mManager.notify(id, updateNotification.build());
                    if (mCount < 5) {
                        mHandler.postDelayed(this, 6000);
                    }
                }
            };
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private void initProgressButton(View view) {
        view.findViewById(R.id.progress_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Progress")
                    .setOngoing(/* ongoing= */ true)
                    .setContentText(
                            "Doesn't show heads-up; Importance Default; Groups; Ongoing (cannot "
                                    + "be dismissed)")
                    .setProgress(/* max= */ 100, /* progress= */ 0, /* indeterminate= */ false)
                    .setContentInfo("0%")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(id, notification);

            int progress = 0;
            Runnable runnable = getProgressNotifUpdateRunnable(id, progress, /* isColorized= */
                    false);
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private void initProgressColorizedButton(View view) {
        view.findViewById(R.id.progress_button_colorized).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Progress (Colorized)")
                    .setOngoing(/* ongoing= */ true)
                    .setContentText(
                            "Doesn't show heads-up; Importance Default; Groups; Ongoing (cannot "
                                    + "be dismissed)")
                    .setProgress(/* max= */ 100, /* progress= */ 0, /* indeterminate= */ false)
                    .setColor(mContext.getColor(android.R.color.holo_purple))
                    .setContentInfo("0%")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(id, notification);

            int progress = 0;
            Runnable runnable = getProgressNotifUpdateRunnable(id, progress, /* isColorized= */
                    true);
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private Runnable getProgressNotifUpdateRunnable(int id, int progress, boolean isColorized) {
        Runnable runnable = () -> {
            Notification.Builder builder = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Progress")
                    .setContentText("Doesn't show heads-up; Importance Default; Groups")
                    .setProgress(/* max= */ 100, progress, /* indeterminate= */ false)
                    .setOngoing(/* ongoing= */ true)
                    .setContentInfo(progress + "%")
                    .setSmallIcon(R.drawable.car_ic_mode);
            if (isColorized) {
                builder.setColor(mContext.getColor(android.R.color.holo_purple));
            }
            Notification updateNotification = builder.build();
            mManager.notify(id, updateNotification);
            if (progress + 5 <= 100) {
                mHandler.postDelayed(getProgressNotifUpdateRunnable(id, progress + 5, isColorized),
                        /* delayMillis= */ 1000);
            }
        };
        mUpdateRunnables.put(id, runnable);
        return runnable;
    }

    private void initNavigationButton(View view) {
        view.findViewById(R.id.navigation_button).setOnClickListener(v -> {

            int id1 = mCurrentNotificationId++;
            Runnable rightTurnRunnable = new Runnable() {
                int mDistance = 900;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_ID)
                            .setCategory("navigation")
                            .setContentTitle("Navigation")
                            .setContentText("Turn right in " + mDistance + " ft")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " ft")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .setOnlyAlertOnce(true)
                            .build();
                    mManager.notify(id1, updateNotification);
                    mDistance -= 100;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 1000);
                    } else {
                        mManager.cancel(id1);
                    }
                }
            };
            mUpdateRunnables.put(id1, rightTurnRunnable);
            mHandler.postDelayed(rightTurnRunnable, 1000);

            int id2 = mCurrentNotificationId++;
            Runnable exitRunnable = new Runnable() {
                int mDistance = 20;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_ID)
                            .setCategory("navigation")
                            .setContentTitle("Navigation")
                            .setContentText("Exit in " + mDistance + " miles")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " miles")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .setOnlyAlertOnce(true)
                            .build();
                    mManager.notify(id2, updateNotification);
                    mDistance -= 1;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 500);
                    }
                }
            };
            mUpdateRunnables.put(id2, exitRunnable);
            mHandler.postDelayed(exitRunnable, 10000);
        });
    }

    private void initMediaButton(View view) {
        view.findViewById(R.id.media_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification.Builder builder = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Lady Adora")
                    .setContentText("Funny Face")
                    .setColor(mContext.getColor(android.R.color.holo_orange_dark))
                    .setColorized(true)
                    .setSubText("Some album")
                    .addAction(new Notification.Action(R.drawable.thumb_down, "Thumb down", null))
                    .addAction(new Notification.Action(R.drawable.skip_prev, "Skip prev", null))
                    .addAction(new Notification.Action(R.drawable.play_arrow, "Play", null))
                    .addAction(new Notification.Action(R.drawable.skip_next, "Skip next", null))
                    .addAction(new Notification.Action(R.drawable.thumb_up, "Thumb up", null))
                    .setSmallIcon(R.drawable.play_arrow)
                    .setLargeIcon(Icon.createWithResource(mContext, R.drawable.android_logo));

            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setShowActionsInCompactView(1, 2, 3);
            MediaSession mediaSession = new MediaSession(mContext, "KitchenSink");
            style.setMediaSession(mediaSession.getSessionToken());
            builder.setStyle(style);
            mediaSession.release();

            mManager.notify(id, builder.build());
        });
    }

    private void initCallButton(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        view.findViewById(R.id.category_call_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("+1 1231231234")
                    .setContentText("Shows persistent heads-up")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setFullScreenIntent(pendingIntent, true)
                    .setColor(mContext.getColor(android.R.color.holo_red_light))
                    .setColorized(true)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initCustomGroupSummaryButton(View view) {
        view.findViewById(R.id.custom_group_summary_button).setOnClickListener(v -> {
            String groupKey = "GROUP_KEY" + mCurrentNotificationId++;
            int delay = 500;

            Notification summaryNotification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("6 New mails")
                    .setContentText("this is some summary")
                    .setSmallIcon(R.drawable.thumb_up)
                    .setLargeIcon(Icon.createWithResource(mContext, R.drawable.avatar1))
                    .setGroup(groupKey)
                    .setGroupSummary(true)
                    .setStyle(new Notification.InboxStyle()
                            .addLine("line 1")
                            .addLine("line 2")
                            .addLine("line 3")
                            .addLine("line 4")
                            .addLine("line 5")
                            .setBigContentTitle("You've received 6 messages")
                            .setSummaryText("From Alice, Bob, Claire, Douglas.."))
                    .build();

            mHandler.postDelayed(
                    () -> mManager.notify(mCurrentNotificationId++, summaryNotification), delay);
            for (int i = 1; i <= 6; i++) {
                Notification notification = new Notification
                        .Builder(mContext, IMPORTANCE_HIGH_ID)
                        .setContentTitle("Group child " + i)
                        .setSmallIcon(R.drawable.car_ic_mode)
                        .setGroup(groupKey)
                        .setSortKey(Integer.toString(6 - i))
                        .build();
                mHandler.postDelayed(() -> mManager.notify(mCurrentNotificationId++, notification),
                        delay += 5000);
            }
        });
    }

    private void initGroupWithoutSummaryButton(View view) {
        view.findViewById(R.id.group_without_summary_button).setOnClickListener(v -> {
            String groupKey = "GROUP_KEY" + mCurrentNotificationId++;

            for (int i = 1; i <= 6; i++) {
                Notification notification = new Notification
                        .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                        .setContentTitle("This notification should not group " + i)
                        .setSmallIcon(R.drawable.car_ic_mode)
                        .setGroup(groupKey)
                        .setSortKey(Integer.toString(i))
                        .build();
                mHandler.post(() -> mManager.notify(mCurrentNotificationId++, notification));
            }
        });
    }

    private void initButtonWithCustomActionIcon(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification
                .Builder(mContext, IMPORTANCE_HIGH_ID)
                .setContentTitle("Notification with custom button icon")
                .setContentText(
                        "Icons should be shown in the action buttons")
                .setSmallIcon(R.drawable.car_ic_mode)
                .addAction(
                        new Notification.Action.Builder(
                                R.drawable.architecture, "architecture", pendingIntent)
                                .build())
                .addAction(
                        new Notification.Action.Builder(
                                Icon.createWithResource(this.getContext(), R.drawable.archive),
                                "archive", pendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                Icon.createWithResource(this.getContext().getPackageName(),
                                        R.drawable.audiotrack),
                                "audio-track", pendingIntent).build())
                .setColor(mContext.getColor(android.R.color.holo_red_light))
                .build();

        view.findViewById(R.id.actions_with_icons).setOnClickListener(
                v -> mManager.notify(mCurrentNotificationId++, notification)
        );
    }

    private void initSelfRemovingNotification(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        intent.addCategory(INTENT_CATEGORY_SELF_DISMISS);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification
                .Builder(mContext, IMPORTANCE_HIGH_ID)
                .setContentTitle("Self Canceling notification")
                .setSmallIcon(R.drawable.car_ic_mode)
                .setContentIntent(pendingIntent)
                .addAction(new Notification.Action.Builder(
                        null, "Click to cancel this notification", pendingIntent
                ).build())
                .build();

        view.findViewById(R.id.self_dismiss_notification).setOnClickListener(
                v -> mManager.notify(SELF_DISMISS_NOTIFICATION_ID, notification)
        );
    }
}
