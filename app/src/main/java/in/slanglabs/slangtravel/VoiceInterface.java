package in.slanglabs.slangtravel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import in.slanglabs.platform.SlangBuddy;
import in.slanglabs.platform.SlangBuddyOptions;
import in.slanglabs.platform.SlangEntity;
import in.slanglabs.platform.SlangIntent;
import in.slanglabs.platform.SlangLocale;
import in.slanglabs.platform.SlangSession;
import in.slanglabs.platform.action.SlangIntentAction;
import in.slanglabs.platform.prompt.SlangMessage;

public class VoiceInterface {

    private static String app_id = "set_your_buddy_id";
    private static String api_key = "set_your_api_key";

    private static boolean searchCompleted;

    private static String source = "", destination = "", startDate = "", dateString = "", currentLocale = "en";

    // To initialize Slang in your application, simply call VoiceInterface.init(context)
    public static void init(Activity activity) {
        try {
            SlangBuddyOptions options = new SlangBuddyOptions.Builder()
                    .setApplication(activity.getApplication())
                    .setBuddyId(app_id)
                    .setAPIKey(api_key)
                    .setListener(new BuddyListener(activity.getApplicationContext()))
                    .setIntentAction(new SlangTravelAction(activity.getApplicationContext()))
                    .setRequestedLocales(SlangLocale.getSupportedLocales())
                    .setDefaultLocale(SlangLocale.LOCALE_ENGLISH_IN)
                    .setAutomaticHelpDisplayThreshold(100)
                    // change env to production when the buddy is published to production
                    .setEnvironment(SlangBuddy.Environment.STAGING)
                    .setConfigOverrides(getConfigOverrides())
                    .setStartActivity(activity)
                    .build();
            SlangBuddy.initialize(options);

        } catch (SlangBuddyOptions.InvalidOptionException e) {
            e.printStackTrace();
        } catch (SlangBuddy.InsufficientPrivilegeException e) {
            e.printStackTrace();
        }
    }

    private static boolean shouldForceProdTier() {
        return true;
    }

    private static Map<String, Object> getConfigOverrides() {
        HashMap<String, Object> config = new HashMap<>();
        if (shouldForceProdTier()) {
            config.put("internal.common.io.server_host", "infer.slanglabs.in");
            config.put("internal.common.io.analytics_server_host", "analytics.slanglabs.in");
        }
        return config;
    }

    public static void startConversation(String msg, boolean isSpoken) {
        try {
            HashMap<Locale, String> strings = new HashMap<>();
            strings.put(SlangLocale.LOCALE_ENGLISH_IN, msg);
            strings.put(SlangLocale.LOCALE_ENGLISH_US, msg);
            SlangMessage message = SlangMessage.create(strings);
            Log.d("Debug", "Starting a new conversation: " + msg);
            SlangBuddy.startConversation(message, isSpoken);
        } catch (SlangBuddy.UninitializedUsageException e) {
            e.printStackTrace();
        }
    }

    private static class BuddyListener implements SlangBuddy.Listener {
        private Context appContext;

        public BuddyListener(Context appContext) {
            this.appContext = appContext;
        }

        @Override
        public void onInitialized() {
            Log.d("BuddyListener", "Slang Initialised Successfully");
            try {
                SlangBuddy.registerIntentAction("slang_help", null);
            } catch (SlangBuddy.InvalidIntentException e) {
                e.printStackTrace();
            } catch (SlangBuddy.UninitializedUsageException e) {
                e.printStackTrace();
            }

            MainActivity.setHelpIntentsDisplay();
        }

        @Override
        public void onInitializationFailed(final SlangBuddy.InitializationError e) {
            Log.d("BuddyListener", "Slang failed:" + e.getMessage());

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(appContext, "Failed to initialise Slang:" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 10);
        }

        /**
         * Handle locale changes that the user requested with Slang. This triggers an internal
         * broadcast that will be handled by components that need to handle this (eg change the
         * help text that is shown in every page to match the language of the locale)
         * @param newLocale
         */
        @Override
        public void onLocaleChanged(final Locale newLocale) {
            Log.d("BuddyListener", "Locale Changed:" + newLocale.getDisplayName());

            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(appContext);
            Intent localIntent = new Intent("localeChanged");

            localIntent.putExtra("localeBroadcast", newLocale.getLanguage());

            // Send local broadcast
            localBroadcastManager.sendBroadcast(localIntent);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(appContext, "Locale Changed:" + newLocale.getDisplayName(), Toast.LENGTH_LONG).show();
                }
            }, 10);
        }

        @Override
        public void onLocaleChangeFailed(final Locale newLocale, final SlangBuddy.LocaleChangeError e) {
            Log.d("BuddyListener",
                    "Locale(" + newLocale.getDisplayName() + ") Change Failed:" + e.getMessage());

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(appContext,
                            "Locale(" + newLocale.getDisplayName() + ") Change Failed:" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }, 10);
        }
    }

    public static class SlangTravelAction implements SlangIntentAction {

        private Context appContext;

        public static final String INTENT_FILTER_TRAINS = "filter_train";
        public static final String INTENT_SEARCH_TRAIN = "search_train";
        public static final String INTENT_SORT_TRAIN = "sort_train";
        private static final String INTENT_MISC = "miscellaneous";

        private static final String ENTITY_RANGE = "range";
        private static final String ENTITY_TIME_OF_THE_DAY = "time_of_the_day";
        private static final String ENTITY_TIME_ONE = "time_one";
        private static final String ENTITY_TIME_TWO = "time_two";
        private static final String ENTITY_AC_TYPE = "ac_type";
        private static final String ENTITY_NON_AC_TYPE = "non_ac_type";
        private static final String ENTITY_DEPARTING = "departing";
        private static final String ENTITY_ARRIVING = "arriving";

        private static final String ENTITY_SOURCE = "src";
        private static final String ENTITY_DESTINATION = "dest";
        private static final String ENTITY_START_DATE = "start_date";
        private static final String ENTITY_GENERIC_CITY = "generic_city";

        private enum PromptType {
            NO_SEARCH_YET_ERROR,
            SORT_CRITERIA_MISSING_ERROR,
            NEGATIVE_CASES_NOT_SUPPORTED_ERROR,
            UNSUPPORTED_FEATURE_ERROR,
            FILTER_ON_ARRIVAL_OR_DEPARTURE,
            FILTER_ON_TIMING,
            SEARCH_FOR_MATCHING_TRAINS,
            SHOWING_LIST_OF_TRAINS;
        }

        public SlangTravelAction(Context context) {
            appContext = context;
            searchCompleted = false;
        }

        @Override
        public Status action(SlangIntent intent, SlangSession session) {

            switch (intent.getName()) {
                case INTENT_SEARCH_TRAIN:
                    searchTrains(intent, session);
                    break;

                case INTENT_FILTER_TRAINS:
                    if (searchCompleted) {
                        filterTrains(intent, session);
                    } else {
                        intent.
                            getCompletionStatement().
                            overrideAffirmative(
                                getPrompt(PromptType.NO_SEARCH_YET_ERROR, session)
                            );
                    }
                    break;

                case INTENT_SORT_TRAIN:
                    if (searchCompleted) {
                        SlangEntity sort = intent.getEntity("sort");
                        String sortType = "";
                        if (sort.isResolved()) {
                            sortType = sort.getValue();
                            if (session.getCurrentActivity() instanceof DetailsActivity) {
                                String sortText = "Sorting by " + sortType;
                                ((DetailsActivity) session.getCurrentActivity()).setSort(sortText);
                            }
                        } else {
                            String prompt = getPrompt(PromptType.SORT_CRITERIA_MISSING_ERROR, session);
                            intent.getCompletionStatement().overrideAffirmative(prompt);
                            startConversation(prompt, false);
                        }
                    }
                    break;

                case INTENT_MISC:
                    if (intent.getEntity("condition_not").isResolved()) {
                        intent.
                            getCompletionStatement().
                            overrideNegative(getPrompt(PromptType.NEGATIVE_CASES_NOT_SUPPORTED_ERROR, session));
                    } else {
                        intent.
                            getCompletionStatement().
                            overrideNegative(getPrompt(PromptType.UNSUPPORTED_FEATURE_ERROR, session));
                    }

                    // Inform Slang that we could not handle the intent. This will trigger
                    // the negative condition of the confirmation statement
                    return Status.FAILURE;
            }

            // Inform Slang that we successfully handled the intent. This will trigger the
            // affirmative condition of the confirmation statement
            return Status.SUCCESS;
        }

        private class FilterDetails {
            public String range = "", timeOfDay = "", timeOne = "", timeTwo = "", acType = "",
                nonAcType = "", depart = "", arrive = "", departArrive = "departing";
        }

        private FilterDetails getFilterDetails(SlangIntent intent) {
            FilterDetails details = new FilterDetails();
            SlangEntity entityRange = intent.getEntity(ENTITY_RANGE);
            SlangEntity entityTimeOfTheDay = intent.getEntity(ENTITY_TIME_OF_THE_DAY);
            SlangEntity entityTimeOne = intent.getEntity(ENTITY_TIME_ONE);
            SlangEntity entityTimeTwo = intent.getEntity(ENTITY_TIME_TWO);
            SlangEntity entityAcType = intent.getEntity(ENTITY_AC_TYPE);
            SlangEntity entityNonAcType = intent.getEntity(ENTITY_NON_AC_TYPE);
            SlangEntity entityDepart = intent.getEntity(ENTITY_DEPARTING);
            SlangEntity entityArrive = intent.getEntity(ENTITY_ARRIVING);

            if (entityRange.isResolved()) {
                details.range = entityRange.getValue();
            }
            if (entityTimeOfTheDay.isResolved()) {
                details.timeOfDay = entityTimeOfTheDay.getValue();
            }
            if (entityTimeOne.isResolved()) {
                details.timeOne = entityTimeOne.getValue();
            }
            if (entityTimeTwo.isResolved()) {
                details.timeTwo = entityTimeTwo.getValue();
            }
            if (entityAcType.isResolved()) {
                details.acType = entityAcType.getValue();
            }
            if (entityNonAcType.isResolved()) {
                details.nonAcType = entityNonAcType.getValue();
            }
            if (entityDepart.isResolved()) {
                details.depart = entityDepart.getValue();
            }
            if (entityArrive.isResolved()) {
                details.arrive = entityArrive.getValue();
            }
            if (!details.arrive.isEmpty()) {
                details.departArrive = "arriving";
            }

            return details;
        }

        private void filterTrains(SlangIntent intent, SlangSession session) {
            FilterDetails filterDetails = getFilterDetails(intent);
            String promptToSpeak = null;

            if (!filterDetails.depart.isEmpty() && !filterDetails.arrive.isEmpty()) {
                String prompt = getPrompt(PromptType.FILTER_ON_ARRIVAL_OR_DEPARTURE, session);

                intent.getCompletionStatement().overrideAffirmative(prompt);
                startConversation(prompt, false);
                return;
            }

            if (session.getCurrentActivity() instanceof DetailsActivity) {
                // We are already in the train listing page. Proceed to sort/filter

                DetailsActivity activity = (DetailsActivity) session.getCurrentActivity();
                String setSort = "";

                activity.hideUpdated();
                if (!filterDetails.acType.isEmpty() && filterDetails.nonAcType.isEmpty()) {
                    setSort += "Showing only AC trains. ";
                } else if (filterDetails.acType.isEmpty() && !filterDetails.nonAcType.isEmpty()) {
                    setSort += "Showing only non AC trains. ";
                } else if (!filterDetails.acType.isEmpty()) {
                    setSort += "Showing both AC and non AC trains. ";
                }
                if (filterDetails.range.isEmpty()) {
                    if (!filterDetails.timeOfDay.isEmpty()) {
                        setSort += "Showing trains " + filterDetails.departArrive + " in the " + filterDetails.timeOfDay + ".";
                    }
                } else {
                    if (filterDetails.range.equals("between")) {
                        if (!filterDetails.timeOne.isEmpty() && !filterDetails.timeTwo.isEmpty()) {
                            SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
                            SimpleDateFormat formatSlang = new SimpleDateFormat("h:mm a");
                            String timeOneString = "";
                            String timeTwoString = "";
                            try {
                                Date date = format.parse(filterDetails.timeOne);
                                timeOneString = String.valueOf(formatSlang.format(date));
                                date = format.parse(filterDetails.timeTwo);
                                timeTwoString = String.valueOf(formatSlang.format(date));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            setSort += "Showing trains " + filterDetails.departArrive + " between " + timeOneString + " and " + timeTwoString;
                        } else {
                            promptToSpeak = getPrompt(PromptType.FILTER_ON_TIMING, session);
                        }
                    } else {
                        if (!filterDetails.timeOne.isEmpty()) {
                            SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
                            SimpleDateFormat formatSlang = new SimpleDateFormat("h:mm a");
                            String timeString = "";
                            try {
                                Date date = format.parse(filterDetails.timeOne);
                                timeString = String.valueOf(formatSlang.format(date));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            setSort += "Showing trains " + filterDetails.departArrive + " " + filterDetails.range + " " + timeString;
                        } else {
                            promptToSpeak = getPrompt(PromptType.FILTER_ON_TIMING, session);
                        }
                    }
                }

                if (promptToSpeak != null) {
                    // We has issues with the filters specified. So inform the user and start the
                    // conversation again
                    intent.getCompletionStatement().overrideAffirmative(promptToSpeak);
                    startConversation(promptToSpeak, false);
                } else {
                    // We detected the correct filters. Apply that and stop listening
                    setSort = setSort.trim();
                    activity.setSort(setSort);
                    intent.getCompletionStatement().overrideAffirmative(setSort);
                }
            } else {
                // User is trying to apply filter but he has not searched for trains in the
                // first place.
                promptToSpeak = getPrompt(PromptType.SEARCH_FOR_MATCHING_TRAINS, session);
                intent.getCompletionStatement().overrideAffirmative(promptToSpeak);
                startConversation(promptToSpeak, false);
            }
        }

        private void searchTrains(SlangIntent intent, SlangSession session) {
            SlangEntity entitySource = intent.getEntity(ENTITY_SOURCE);
            SlangEntity entityDestination = intent.getEntity(ENTITY_DESTINATION);
            SlangEntity entityStartDate = intent.getEntity(ENTITY_START_DATE);
            SlangEntity entityGenericCity = intent.getEntity(ENTITY_GENERIC_CITY);
            String promptToSpeak = null;

            if (entitySource.isResolved()) {
                source = entitySource.getValue();
            }

            if (entityDestination.isResolved()) {
                destination = entityDestination.getValue();
            }

            if (entityStartDate.isResolved()) {
                startDate = entityStartDate.getValue();
                SimpleDateFormat dateFormatterSlang = new SimpleDateFormat("dd-MM-yyyy");
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
                try {
                    Date date = dateFormatter.parse(startDate);
                    dateString = String.valueOf(dateFormatterSlang.format(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            }

            if (entityGenericCity.isResolved()) {
                if (!source.isEmpty() && destination.isEmpty()) {
                    destination = entityGenericCity.getValue();
                } else if (source.isEmpty()) {
                    source = entityGenericCity.getValue();
                }
            }

            if (session.getCurrentActivity() instanceof MainActivity) {
                MainActivity activity = ((MainActivity) session.getCurrentActivity());
                if(!source.isEmpty()) {
                    activity.setSource(source);
                }
                if (!destination.isEmpty()) {
                    activity.setDestination(destination);
                }
                if (!startDate.isEmpty()) {
                    activity.setStartDate(dateString);
                }
            }

            if (source.isEmpty()) {
                // source is not set. Ask the user to set that
                promptToSpeak = entitySource.getPrompt().getQuestion();
            } else if (destination.isEmpty()) {
                // destination is not set. Ask the user to set that
                promptToSpeak = entityDestination.getPrompt().getQuestion();
            } else if (startDate.isEmpty()) {
                // travel date is not set. Ask the user to set that
                promptToSpeak = entityStartDate.getPrompt().getQuestion();
            }

            if (promptToSpeak != null) {
                intent.getCompletionStatement().overrideAffirmative(promptToSpeak);
                VoiceInterface.startConversation(promptToSpeak, false);
            } else {
                searchCompleted = true;

                // If we are not in the search listing page, launch that. If we are already
                // there, just refresh its contents
                if (session.getCurrentActivity() instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) session.getCurrentActivity();
                    mainActivity.openDetailsActivity();
                } else if ((session.getCurrentActivity() instanceof DetailsActivity)) {
                    // refresh activity
                    showTrains(source, destination, intent, session);
                }
            }
        }

        private void showTrains(
            String source,
            String destination,
            SlangIntent intent,
            SlangSession session
        ) {
            String showCity =  source + " to " + destination;
            String showDate = "for " + dateString;
            DetailsActivity detailsActivity = (DetailsActivity) session.getCurrentActivity();

            detailsActivity.setShowCity(showCity);
            detailsActivity.setShowDate(showDate);
            detailsActivity.setSort(appContext.getResources().getString(R.string.wow_so_many_trains));
            detailsActivity.setUpdated();
            intent.
                getCompletionStatement().
                overrideAffirmative(getPrompt(PromptType.SHOWING_LIST_OF_TRAINS, session));
        }

        private String getPrompt(PromptType type, SlangSession session) {
            String eng = null, hi = null;

            switch (type) {
                case SHOWING_LIST_OF_TRAINS:
                    eng = "Showing updated list of trains.";
                    hi = "हम ट्रेनों की नई सूची दिखा रहे हैं.";
                    break;

                case SORT_CRITERIA_MISSING_ERROR:
                    eng = "Please specify your sorting criteria";
                    hi = "कृपया हमें अपने छँटाई मापदंड बताएं.";
                    break;

                case NO_SEARCH_YET_ERROR:
                    eng = "We'll do that once you search for trains for your preferred date and destination.";
                    hi = "कृपया पहले अपनी पसंदीदा तारीख और गंतव्य के लिए ट्रेनों की खोज करें";
                    break;

                case NEGATIVE_CASES_NOT_SUPPORTED_ERROR:
                    eng = "Negative cases are not supported in this demo";
                    hi = "इस डेमो में नकारात्मक शर्तों का उपयोग नहीं किया जाता है";
                    break;

                case UNSUPPORTED_FEATURE_ERROR:
                    eng = "Sorry, this feature is not supported by this app.";
                    hi = "क्षमा करें, यह सुविधा इस ऐप द्वारा समर्थित नहीं है.";
                    break;

                case FILTER_ON_ARRIVAL_OR_DEPARTURE:
                    eng = "You can only filter using one of arrival or departure timings. Please try again.";
                    hi = "आप केवल आगमन या प्रस्थान के समय का उपयोग करके फ़िल्टर कर सकते हैं. कृपया पुन: प्रयास करें.";
                    break;

                case FILTER_ON_TIMING:
                    eng = "Please enter the timings you want to filter for";
                    hi = "कृपया हमें बताएं कि आप किस समय के लिए फ़िल्टर करना चाहते हैं";
                    break;

                case SEARCH_FOR_MATCHING_TRAINS:
                    eng = "Please search for trains matching your requirements";
                    hi = "कृपया अपनी आवश्यकताओं से मेल खाने वाली ट्रेनों की खोज करें";
                    break;
            }

            return
                session.getCurrentLocale().getLanguage().equals("en") ?
                    eng : hi;
        }

        public static void clearAll() {
            source = "";
            destination = "";
            startDate = "";
            dateString = "";
            searchCompleted = false;
        }
    }
}
