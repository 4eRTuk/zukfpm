/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class ZukPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    public final static String KEY_LONG_TAP = "long_tap";
    public final static String KEY_SWIPE_LEFT = "swipe_left";
    public final static String KEY_SWIPE_RIGHT = "swipe_right";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ListPreference list = (ListPreference) findPreference("long_tap");
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        list = (ListPreference) findPreference("swipe_left");
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        list = (ListPreference) findPreference("swipe_right");
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        ListPreference list = (ListPreference) preference;
        CharSequence[] values = list.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(o)) {
                preference.setSummary(list.getEntries()[i]);
                break;
            }
        }

        return true;
    }
}
