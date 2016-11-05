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
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ListPreference list = (ListPreference) findPreference("home_key");
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        list = (ListPreference) findPreference("last_key");
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
