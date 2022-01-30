package org.androidbootmanager.app.legacy.ui.addrom;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.androidbootmanager.app.legacy.roms.ROM;

import java.util.ArrayList;
import java.util.Objects;

public class AddROMViewModel extends ViewModel {
    MutableLiveData<ROM> rom;
    MutableLiveData<ArrayList<Integer>> parts;
    MutableLiveData<String> cmdline;
    MutableLiveData<ArrayList<String>> strings;

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

    public LiveData<ArrayList<Integer>> getParts() {
        if (parts == null)
            parts = new MutableLiveData<>(new ArrayList<>());
        return parts;
    }

    public void addPart(Integer i){
        Objects.requireNonNull(getParts().getValue()).add(i);
    }

    public MutableLiveData<String> getCmdline() {
        if (cmdline == null)
            cmdline = new MutableLiveData<>();
        return cmdline;
    }

    public void setCmdline(String s) {
        getCmdline().setValue(s);
    }

    public ArrayList<String> getName() {
        if (strings == null) strings = new MutableLiveData<>(new ArrayList<>());
        return strings.getValue();
    }
}
