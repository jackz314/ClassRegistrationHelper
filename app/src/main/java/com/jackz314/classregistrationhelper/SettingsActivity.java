package com.jackz314.classregistrationhelper;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.ActionBar;
import androidx.core.app.NavUtils;
import androidx.work.WorkManager;

import static com.jackz314.classregistrationhelper.CourseUtils.addToWorkerQueue;
import static com.jackz314.classregistrationhelper.CourseUtils.cancelScheduledRegistration;
import static com.jackz314.classregistrationhelper.CourseUtils.scheduleRegistration;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    private static final String TAG = "SettingsActivity";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        String stringValue = value.toString();

        setBindPreferenceSummary(preference, stringValue);

        return true;
    };

    private static void setBindPreferenceSummary(Preference preference, String stringValue){
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);

        } else if (preference instanceof RingtonePreference) {
            // For ringtone preferences, look up the correct display value
            // using RingtoneManager.
            if (TextUtils.isEmpty(stringValue)) {
                // Empty values correspond to 'silent' (no ringtone).
                preference.setSummary(R.string.pref_ringtone_silent);

            } else {
                Ringtone ringtone = RingtoneManager.getRingtone(
                        preference.getContext(), Uri.parse(stringValue));

                if (ringtone == null) {
                    // Clear the summary if there was a lookup error.
                    preference.setSummary(null);
                } else {
                    // Set the summary to reflect the new ringtone display
                    // name.
                    String name = ringtone.getTitle(preference.getContext());
                    preference.setSummary(name);
                }
            }

        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
        setupActionBar();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || PrefsFragment.class.getName().equals(fragmentName);
    }

    /**
     * This is the one and only preference fragment that we are going to show since it's really simple
     */
    public static class PrefsFragment extends PreferenceFragment {

        @SuppressWarnings("unchecked")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_term)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_query_interval)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_report_interval)));

            ListPreference termListPreference = (ListPreference) findPreference(getString(R.string.pref_key_term));
            termListPreference.setEntryValues(sharedPreferences.getString(getString(R.string.pref_key_valid_term_values), "").split(";"));
            termListPreference.setEntries(sharedPreferences.getString(getString(R.string.pref_key_valid_term_names), "").split(";"));
            termListPreference.setDefaultValue(sharedPreferences.getString(getString(R.string.pref_key_term), null));
            int index = termListPreference.findIndexOfValue(sharedPreferences.getString(getString(R.string.pref_key_term), null));
            termListPreference.setSummary(index >= 0 ? termListPreference.getEntries()[index] : null);
            termListPreference.setOnPreferenceChangeListener((preference, o) -> {
                sharedPreferences.edit().putBoolean(getContext().getString(R.string.changed_settings), true).apply();
                return true;
            });

            MultiSelectListPreference majorListPreference = (MultiSelectListPreference) findPreference(getString(R.string.pref_key_major));
            majorListPreference.setEntryValues(sharedPreferences.getString(getString(R.string.pref_key_valid_major_values), "").split(";"));
            majorListPreference.setEntries(sharedPreferences.getString(getString(R.string.pref_key_valid_major_names), "").split(";"));
            majorListPreference.setDefaultValue(sharedPreferences.getStringSet(getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList("ALL"))));
            majorListPreference.setSummary(TextUtils.join(", ", majorListPreference.getValues()));
            majorListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                List<String> newValues = new ArrayList<>((HashSet<String>) newValue);
                sharedPreferences.edit().putBoolean(getContext().getString(R.string.changed_settings), true).apply();
                if(newValues.contains("ALL")){
                    sharedPreferences.edit().putStringSet(getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList("ALL"))).apply();
                    preference.setSummary("All");
                    return false;
                }else {
                    preference.setSummary(TextUtils.join(", ", newValues));
                    return true;
                }
            });

            Preference sysNotifPreference = findPreference(getString(R.string.pref_key_sys_notif_setting));
            sysNotifPreference.setOnPreferenceClickListener(preference -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
                    startActivity(intent);
                }else {
                    Intent intent = new Intent();
                    intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("app_package", getContext().getPackageName());
                    intent.putExtra("app_uid", getContext().getApplicationInfo().uid);
                    startActivity(intent);
                }
                return false;
            });

            SwitchPreference openClassPreference = (SwitchPreference)findPreference(getString(R.string.pref_key_only_show_open_classes));
            openClassPreference.setOnPreferenceChangeListener((preference, o) -> {
                sharedPreferences.edit().putBoolean(getContext().getString(R.string.changed_settings), true).apply();
                return true;
            });

            Preference schedulePreference = findPreference(getString(R.string.pref_key_schedule_register));
            schedulePreference.setOnPreferenceClickListener(preference -> {
                showDateAndTimeDialog();
                return false;
            });

            Preference cancelSchedulePreference = findPreference(getString(R.string.pref_key_cancel_schedule));
            cancelSchedulePreference.setOnPreferenceClickListener(preference -> {
                cancelScheduledRegistration(getContext());
                Toast.makeText(getContext(), "Scheduled registration cancelled!", Toast.LENGTH_SHORT).show();
                return false;
            });

            SwitchPreference autoCheckPreference = (SwitchPreference)findPreference(getString(R.string.pref_key_auto_check));

            //disable preferences when there's no login info
            String sessionId = sharedPreferences.getString(getString(R.string.session_id), null);
            if(sessionId == null){
                autoCheckPreference.setEnabled(false);
                disableCourseCheckStuff();
            }else {
                boolean autoCheck = sharedPreferences.getBoolean(getString(R.string.pref_key_auto_check), true);
                if(!autoCheck){
                    disableCourseCheckStuff();
                }else {
                    enableCourseCheckStuff();
                }
            }

            autoCheckPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if((Boolean)newValue){
                    enableCourseCheckStuff();
                }else {
                    disableCourseCheckStuff();
                }
                return true;
            });

            ListPreference queryIntervalPreference = (ListPreference) findPreference(getString(R.string.pref_key_query_interval));
            queryIntervalPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                //restart all worker stuff
                WorkManager.getInstance().cancelAllWork();
                addToWorkerQueue(getContext());
                //update summary
                setBindPreferenceSummary(queryIntervalPreference, (String) newValue);
                return true;
            });

            ListPreference reportIntervalPreference = (ListPreference) findPreference(getString(R.string.pref_key_report_interval));
            reportIntervalPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                //restart all worker stuff
                WorkManager.getInstance().cancelAllWork();
                addToWorkerQueue(getContext());
                //update summary
                setBindPreferenceSummary(reportIntervalPreference, (String) newValue);
                return true;
            });

        }

        private void showDateAndTimeDialog(){
            Calendar currentTime = Calendar.getInstance();

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            long scheduledTime = sharedPreferences.getLong(getString(R.string.pref_key_schedule_register), -1);
            if(scheduledTime != -1){
                currentTime.setTimeInMillis(scheduledTime);
            }

            int year = currentTime.get(Calendar.YEAR);
            int month = currentTime.get(Calendar.MONTH);
            int day = currentTime.get(Calendar.DAY_OF_MONTH);
            new DatePickerDialog(getContext(), (view, year1, month1, dayOfMonth) -> showTimeDialog(year1, month1, dayOfMonth), year, month, day).show();
        }

        private void showTimeDialog(int year, int month, int day){
            Calendar currentTime = Calendar.getInstance();

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            long scheduledTime = sharedPreferences.getLong(getString(R.string.pref_key_schedule_register), -1);
            if(scheduledTime != -1){
                currentTime.setTimeInMillis(scheduledTime);
            }

            int hour = currentTime.get(Calendar.HOUR);
            int minute = currentTime.get(Calendar.MINUTE);
            new TimePickerDialog(getContext(), (view, hourOfDay, minute1) -> {
                Calendar scheduleCalendar = Calendar.getInstance();
                scheduleCalendar.set(year, month, day, hourOfDay, minute1, 0);

                SimpleDateFormat sdf = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance();
                String dateString = sdf.format(scheduleCalendar.getTime());
                Toast.makeText(getContext(), String.format(Locale.getDefault(), "Scheduled registration set for: %s", dateString), Toast.LENGTH_SHORT).show();

                long scheduleTime = scheduleCalendar.getTimeInMillis();
                scheduleRegistration(getContext(), scheduleTime);
                sharedPreferences.edit().putLong(getString(R.string.pref_key_schedule_register), scheduleTime).apply();
            }, hour, minute, DateFormat.is24HourFormat(getContext())).show();
        }

        private void disableCourseCheckStuff(){
            SwitchPreference autoRegisterPreference = (SwitchPreference)findPreference(getString(R.string.pref_key_auto_reg));
            ListPreference queryInterval = (ListPreference) findPreference(getString(R.string.pref_key_query_interval));
            ListPreference reportInterval = (ListPreference) findPreference(getString(R.string.pref_key_report_interval));
            autoRegisterPreference.setEnabled(false);
            queryInterval.setEnabled(false);
            reportInterval.setEnabled(false);
            WorkManager.getInstance().cancelAllWork();
        }

        private void enableCourseCheckStuff(){
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            SwitchPreference autoRegisterPreference = (SwitchPreference)findPreference(getString(R.string.pref_key_auto_reg));
            ListPreference queryInterval = (ListPreference) findPreference(getString(R.string.pref_key_query_interval));
            ListPreference reportInterval = (ListPreference) findPreference(getString(R.string.pref_key_report_interval));
            autoRegisterPreference.setEnabled(true);
            queryInterval.setEnabled(true);
            reportInterval.setEnabled(true);
            addToWorkerQueue(getContext());
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

    }

}
