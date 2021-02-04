package org.androidbootmanager.app.util;

import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

public class ConfigTextWatcher implements TextWatcher {
    final ConfigFile config;
    final String key;

    public ConfigTextWatcher(ConfigFile config, String key) {
        this.config = config;
        this.key = key;
    }

    @Override
    public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4) {
    }

    @Override
    public void onTextChanged(CharSequence p1, int p2, int p3, int p4) {
    }

    @Override
    public void afterTextChanged(Editable p1) {
        config.set(key, p1.toString());
    }

    public static void attachTo(EditText e, ConfigFile config, String key) {
        e.addTextChangedListener(new ConfigTextWatcher(config, key));
    }

    public static void attachTo(int e, View v, ConfigFile config, String key) {
        attachTo(v.findViewById(e), config, key);
    }
}
