package org.androidbootmanager.app;
import android.text.TextWatcher;
import android.text.Editable;

class ConfigTextWatcher implements TextWatcher {
	ConfigFile config;
	String key;
	
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
}
