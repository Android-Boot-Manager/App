package org.androidbootmanager.app.ui.installer;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;

public class DeviceInstallerViewModel extends ViewModel {
    MutableLiveData<String> codename;
    MutableLiveData<String> romName;
    public final ArrayList<Class<? extends Fragment>> flow = new ArrayList<>(Collections.singletonList(DeviceInstallerWizardPageFragment.class));
    public int flowPos = 1;

    public LiveData<String> getCodename() {
        if (codename == null) codename = new MutableLiveData<>();
        return codename;
    }

    public void setCodename(String codename) {
        getCodename(); // ensure codename not null
        this.codename.setValue(codename);
    }

    public LiveData<String> getName() {
        if (romName == null) romName = new MutableLiveData<>();
        return romName;
    }

    public void setName(String name) {
        getName(); // ensure romName not null
        this.romName.setValue(name);
    }
}
