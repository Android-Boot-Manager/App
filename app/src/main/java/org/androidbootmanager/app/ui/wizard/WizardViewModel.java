package org.androidbootmanager.app.ui.wizard;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class WizardViewModel extends ViewModel {
    private MutableLiveData<Class<? extends Fragment>> positiveFragment;
    private MutableLiveData<Class<? extends Fragment>> negativeFragment;

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
}
