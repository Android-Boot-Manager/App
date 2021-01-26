package org.androidbootmanager.app.ui.addrom;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.androidbootmanager.app.roms.ROM;

public class AddROMViewModel extends ViewModel {
    MutableLiveData<ROM> rom;

    public LiveData<ROM> getROM() {
        if (rom == null)
            rom = new MutableLiveData<>();
        return rom;
    }

    public void setROM(ROM r) {
        if (rom == null)
            rom = new MutableLiveData<>(r);
        else
            rom.setValue(r);
    }

}
