package org.androidbootmanager.app;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class ConfiguratorActivity extends FragmentActivity {

    public interface FragmentCreator {
        Fragment run();
    }

    abstract static public class BaseFragment extends Fragment {
        protected int layout = 0;
        protected ConfiguratorActivity xcontext = null;

        abstract protected void onPreInit();

        abstract protected void onInit();

        public BaseFragment() {
            super();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            onPreInit();
            return inflater.inflate(layout, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            xcontext = (ConfiguratorActivity) getActivity();
            onInit();
            super.onViewCreated(view, savedInstanceState);
        }

        public static void registerTabs(Resources res, ConfiguratorActivity.TabAdapter adapter) {
            adapter.addFragment(RomTabFragment::new, res.getString(R.string.roms));
            adapter.addFragment(EntryTabFragment::new, res.getString(R.string.entries));
            adapter.addFragment(GeneralTabFragment::new, res.getString(R.string.general));
        }
    }


    public static class TabAdapter extends FragmentStateAdapter {
        private final List<FragmentCreator> flist = new ArrayList<>();
        private final List<String> tlist = new ArrayList<>();

        TabAdapter(FragmentActivity fm) {
            super(fm);
        }

        @NonNull
		@Override
        public Fragment createFragment(int position) {
            return flist.get(position).run();
        }

        public void addFragment(FragmentCreator fragment, String title) {
            flist.add(fragment);
            tlist.add(title);
        }

        @Override
        public int getItemCount() {
            return flist.size();
        }

        public String getTlist(int position) {
            return tlist.get(position);
        }
    }

	private TabAdapter adapter;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        setContentView(R.layout.cfg);
		ViewPager2 viewPager = (ViewPager2) findViewById(R.id.viewPager);
		TabLayout tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        adapter = new TabAdapter(this);
        BaseFragment.registerTabs(getResources(), adapter);
        viewPager.setAdapter(adapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getTlist(position))
        ).attach();
    }
}
