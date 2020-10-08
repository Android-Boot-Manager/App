package org.androidbootmanager.app.ui.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.mikepenz.aboutlibraries.LibsBuilder;

import org.androidbootmanager.app.R;

public class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        new LibsBuilder().start(requireActivity());
        ((NavHostFragment) getActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().navigate(R.id.nav_home);
        return super.onCreateView(inflater, container, savedInstanceState);
    }
}
