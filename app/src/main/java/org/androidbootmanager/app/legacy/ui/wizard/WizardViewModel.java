package org.androidbootmanager.app.legacy.ui.wizard;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class WizardViewModel extends ViewModel {
    private MutableLiveData<Class<? extends Fragment>> positiveFragment;
    private MutableLiveData<Class<? extends Fragment>> negativeFragment;
    private MutableLiveData<String> positiveText;
    private MutableLiveData<String> negativeText;
    private MutableLiveData<Runnable> positiveAction;
    private MutableLiveData<Runnable> negativeAction;
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

    public void setNegativeFragment(@Nullable Class<? extends Fragment> fragment) {
        if (negativeFragment == null) negativeFragment = new MutableLiveData<>();
        negativeFragment.setValue(fragment);
    }

    public void setPositiveFragment(@Nullable Class<? extends Fragment> fragment) {
        if (positiveFragment == null) positiveFragment = new MutableLiveData<>();
        positiveFragment.setValue(fragment);
    }

    @Nullable
    public Class<? extends Fragment> getNegativeFragment() {
        if (negativeFragment == null) negativeFragment = new MutableLiveData<>();
        return negativeFragment.getValue();
    }

    @Nullable
    public Class<? extends Fragment> getPositiveFragment() {
        if (positiveFragment == null) positiveFragment = new MutableLiveData<>();
        return positiveFragment.getValue();
    }

    public void setPositiveText(String newPositiveText) {
        if (positiveText == null) positiveText = new MutableLiveData<>();
        positiveText.setValue(newPositiveText);
    }

    public void setNegativeText(String newNegativeText) {
        if (negativeText == null) negativeText = new MutableLiveData<>();
        negativeText.setValue(newNegativeText);
    }

    public LiveData<String> getPositiveText() {
        if (positiveText == null) positiveText = new MutableLiveData<>("Error");
        return positiveText;
    }

    public LiveData<String> getNegativeText() {
        if (negativeText == null) negativeText = new MutableLiveData<>("Error");
        return negativeText;
    }

    public LiveData<Runnable> getPositiveAction() {
        if (positiveAction == null) positiveAction = new MutableLiveData<>(() -> {});
        return positiveAction;
    }

    public LiveData<Runnable> getNegativeAction() {
        if (negativeAction == null) negativeAction = new MutableLiveData<>(() -> {});
        return negativeAction;
    }

    public void setPositiveAction(@Nullable Runnable r) {
        if (positiveAction == null) positiveAction = new MutableLiveData<>();
        if (r == null) r = () -> {};
        positiveAction.setValue(r);
    }

    public void setNegativeAction(@Nullable Runnable r) {
        if (negativeAction == null) negativeAction = new MutableLiveData<>();
        if (r == null) r = () -> {};
        negativeAction.setValue(r);
    }
}
