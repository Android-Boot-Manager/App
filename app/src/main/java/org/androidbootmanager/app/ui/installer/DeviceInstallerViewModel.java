package org.androidbootmanager.app.ui.installer;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class DeviceInstallerViewModel extends ViewModel {
    MutableLiveData<String> codename;
    public ArrayList<Class<? extends Fragment>> flow = new ArrayList<>(Arrays.asList(DeviceInstallerWizardPageFragment.class));
    public int flowPos = 0;
    public File droidboot;

    public LiveData<String> getCodename() {
        if (codename == null) codename = new MutableLiveData<>();
        return codename;
    }

    public void setCodename(String codename) {
        getCodename(); // ensure codename not null
        this.codename.setValue(codename);
    }
}
