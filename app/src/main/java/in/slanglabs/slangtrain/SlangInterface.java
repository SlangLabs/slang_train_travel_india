package in.slanglabs.slangtrain;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import in.slanglabs.platform.SlangBuddy;
import in.slanglabs.platform.SlangBuddyOptions;
import in.slanglabs.platform.SlangIntent;
import in.slanglabs.platform.SlangLocale;
import in.slanglabs.platform.SlangSession;
import in.slanglabs.platform.action.SlangIntentAction;
import in.slanglabs.platform.action.SlangUtteranceAction;
import in.slanglabs.platform.prompt.SlangMessage;

import static in.slanglabs.slangtrain.AppTravelAction.processGatherMissingData;
import static in.slanglabs.slangtrain.AppTravelAction.processInvalidUtterance;
import static in.slanglabs.slangtrain.AppTravelAction.processSearchTrain;
import static in.slanglabs.slangtrain.AppTravelAction.processSortTrains;
import static in.slanglabs.slangtrain.AppTravelAction.resetCache;

class SlangInterface {

    private static final String BUDDY_ID = "<YOUR_BUDDY_ID>";
    private static final String API_KEY = "<YOUR_API_KEY>";

    private static SlangIntentAction sAppActionHandler;

    // To initialize Slang in your application, simply call SlangInterface.init(context)
    static void init(Activity activity) {
        try {
            sAppActionHandler = new SlangTravelAction();
            SlangBuddyOptions options = new SlangBuddyOptions.Builder()
                    .setApplication(activity.getApplication())
                    .setBuddyId(BUDDY_ID)
                    .setAPIKey(API_KEY)
                    .setListener(new BuddyListener(activity.getApplicationContext()))
                    .setIntentAction(sAppActionHandler)
                    .setUtteranceAction(new UtteranceHandler())
                    .setRequestedLocales(new HashSet<Locale>() {{
                        add(SlangLocale.LOCALE_ENGLISH_IN);
                        add(SlangLocale.LOCALE_HINDI_IN);
                    }})
                    .setDefaultLocale(SlangLocale.LOCALE_ENGLISH_IN)
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

    private static Map<String, Object> getConfigOverrides() {
        HashMap<String, Object> config = new HashMap<>();

        config.put("internal.subsystems.asr.force_cloud_asr", true);

        return config;
    }

    static void startConversation(String msg, boolean isSpoken) {
        try {
            HashMap<Locale, String> strings = new HashMap<>();
            strings.put(SlangLocale.LOCALE_ENGLISH_IN, msg);
            strings.put(SlangLocale.LOCALE_ENGLISH_US, msg);
            SlangMessage message = SlangMessage.create(strings);
            SlangBuddy.startConversation(message, isSpoken);
        } catch (SlangBuddy.UninitializedUsageException e) {
            e.printStackTrace();
        }
    }

    private static class BuddyListener implements SlangBuddy.Listener {
        private Context appContext;

        BuddyListener(Context appContext) {
            this.appContext = appContext;
        }

        @Override
        public void onInitialized() {
            Log.d("BuddyListener", "Slang Initialised Successfully");
            try {
                SlangBuddy.registerIntentAction("slang_help", sAppActionHandler);
            } catch (SlangBuddy.InvalidIntentException e) {
                e.printStackTrace();
            } catch (SlangBuddy.UninitializedUsageException e) {
                e.printStackTrace();
            }

            //Start with providing hints to do the search.
            SlangBuddy.getBuiltinUI().setIntentFiltersForDisplay(
                    new HashSet<String>()
                    {{
                        add(SlangInterface.SlangTravelAction.INTENT_SEARCH_TRAIN);
                    }}
            );
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

            try {
                SlangBuddy.registerIntentAction("slang_help", null);
            } catch (SlangBuddy.InvalidIntentException e) {
                e.printStackTrace();
            } catch (SlangBuddy.UninitializedUsageException e) {
                e.printStackTrace();
            }
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

            try {
                SlangBuddy.registerIntentAction("slang_help", null);
            } catch (SlangBuddy.InvalidIntentException exception) {
                exception.printStackTrace();
            } catch (SlangBuddy.UninitializedUsageException exception) {
                exception.printStackTrace();
            }
        }
    }

    public static class SlangTravelAction implements SlangIntentAction {

        static final String INTENT_SEARCH_TRAIN = "search_train";
        static final String INTENT_SORT_TRAIN = "sort_train";
        static final String INTENT_GATHER_MISSING_DATA = "gather_missing_data";
        static final String INTENT_HELP = "slang_help";

        SlangTravelAction() {
            resetCache();
        }

        @Override
        public Status action(SlangIntent intent, SlangSession session) {
            switch (intent.getName()) {
                case INTENT_SEARCH_TRAIN:
                    processSearchTrain(intent, session);
                    break;
                case INTENT_SORT_TRAIN:
                    processSortTrains(intent, session);
                    break;
                case INTENT_GATHER_MISSING_DATA:
                    processGatherMissingData(intent, session);
                    break;
                case INTENT_HELP:
                    intent.getCompletionStatement().overrideAffirmative("Please try one of the options mentioned on the screen");
                    break;
            }

            return Status.SUCCESS;
        }
    }

    private static class UtteranceHandler implements SlangUtteranceAction {
        @Override
        public void onUtteranceDetected(String s, SlangSession slangSession) {
            //NOP
        }

        @Override
        public Status onUtteranceUnresolved(String s, SlangSession slangSession) {
            final Pair<String, Boolean> result = processInvalidUtterance(s, slangSession);
            if (null != result && null != result.first && !result.first.isEmpty()) {
                try {
                    HashMap<Locale, String> strings = new HashMap<>();
                    strings.put(SlangLocale.LOCALE_ENGLISH_IN, result.first);
                    strings.put(SlangLocale.LOCALE_HINDI_IN, result.first);
                    SlangBuddy.getClarificationMessage().overrideMessages(strings);
                    if (null != result.second && result.second) {
                        startConversation(result.first, false);
                    }
                    return Status.SUCCESS;
                } catch (SlangBuddy.UninitializedUsageException e) {
                    e.printStackTrace();
                }
            }

            return Status.FAILURE;
        }
    }
}
