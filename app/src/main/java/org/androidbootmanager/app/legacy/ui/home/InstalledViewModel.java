package org.androidbootmanager.app.legacy.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class InstalledViewModel extends ViewModel {
    private MutableLiveData<String> codename;

    public LiveData<String> getCodename() {
        if (codename == null)
            codename = new MutableLiveData<>();
        return codename;
    }

    public void setCodename(String codename) {
        if (this.codename == null)
            this.codename = new MutableLiveData<>(codename);
        else
            this.codename.setValue(codename);
    }
}
