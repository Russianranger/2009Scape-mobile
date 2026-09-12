package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadBindings;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMap;
import net.kdt.pojavlaunch.utils.LwjglGlfwKeycode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The existing HD controller mapping, with one persistent keyboard override per input. */
public class LauncherPreferenceGamepadFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_gamepad_bindings);
        PreferenceCategory category = findPreference("gamepad_bindings");
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        Map<String, ?> saved = preferences.getAll();
        GamepadMap defaults = GamepadMap.getDefaultMenuMap();
        String[] titles = getResources().getStringArray(R.array.gamepad_binding_inputs);

        // Normalize invalid values before ListPreference tries to read them as strings.
        SharedPreferences.Editor repair = preferences.edit();
        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            String value = GamepadBindings.readValue(saved, input);
            if (saved.containsKey(input.preferenceKey) && !value.equals(saved.get(input.preferenceKey))) {
                repair.remove(input.preferenceKey);
            }
        }
        repair.apply();

        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            ListPreference binding = new ListPreference(requireContext());
            binding.setKey(input.preferenceKey);
            binding.setTitle(titles[input.ordinal()]);
            binding.setDialogTitle(titles[input.ordinal()]);
            binding.setIconSpaceReserved(false);

            List<String> entries = new ArrayList<>();
            List<String> values = new ArrayList<>();
            entries.add(getString(R.string.gamepad_binding_default, describe(defaults.getBinding(input))));
            values.add(GamepadBindings.DEFAULT);
            entries.add(getString(R.string.gamepad_binding_unbound));
            values.add(GamepadBindings.UNBOUND);
            for (Map.Entry<Integer, String> key : GamepadBindings.KEY_NAMES.entrySet()) {
                entries.add(key.getValue());
                values.add(Integer.toString(key.getKey()));
            }
            binding.setEntries(entries.toArray(new String[0]));
            binding.setEntryValues(values.toArray(new String[0]));
            binding.setDefaultValue(GamepadBindings.DEFAULT);
            binding.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            category.addPreference(binding);
        }

        Preference reset = findPreference("gamepad_bindings_reset");
        reset.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.gamepad_bindings_reset_title)
                    .setMessage(R.string.gamepad_bindings_reset_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.gamepad_bindings_reset_title, (dialog, which) -> {
                        SharedPreferences.Editor editor = preferences.edit();
                        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
                            // Update the visible summaries and remove only our own overrides.
                            ListPreference binding = findPreference(input.preferenceKey);
                            binding.setValue(GamepadBindings.DEFAULT);
                            editor.remove(input.preferenceKey);
                        }
                        editor.apply();
                    }).show();
            return true;
        });
    }

    private String describe(int[] keys) {
        if (keys.length == 0) return getString(R.string.gamepad_binding_unbound);
        if (keys[0] == GamepadMap.MOUSE_SCROLL_UP) return getString(R.string.gamepad_binding_scroll_up);
        if (keys[0] == GamepadMap.MOUSE_SCROLL_DOWN) return getString(R.string.gamepad_binding_scroll_down);
        StringBuilder description = new StringBuilder();
        for (int key : keys) {
            if (description.length() > 0) description.append(" + ");
            if (key == LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT) {
                description.append(getString(R.string.gamepad_binding_mouse_left));
            } else if (key == LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT) {
                description.append(getString(R.string.gamepad_binding_mouse_right));
            } else {
                description.append(GamepadBindings.KEY_NAMES.get(key));
            }
        }
        return description.toString();
    }
}
