package org.androidbootmanager.app.ui.installer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DeviceInstallerViewModel extends ViewModel {
    MutableLiveData<String> codename;

    public LiveData<String> getCodename() {
        if (codename == null) codename = new MutableLiveData<>();
        return codename;
    }

    public void setCodename(String codename) {
        getCodename(); // ensure codename not null
        this.codename.setValue(codename);
    }
}
