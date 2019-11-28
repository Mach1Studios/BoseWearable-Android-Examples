package com.bose.ar.multi_example;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements HomeFragment.Listener {
    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content1, HomeFragment.newInstance(R.id.content1))
                .replace(R.id.content2, HomeFragment.newInstance(R.id.content2))
                .commit();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();


    }

    @Override
    public void onDeviceConnected(final int id, @NonNull final Bundle args) {
        final MainFragment fragment = new MainFragment();
        args.putInt(MainFragment.ARG_TOOLBAR_ID, id == R.id.content1 ? R.id.toolbar1 : R.id.toolbar2);
        fragment.setArguments(args);
        getSupportFragmentManager()
            .beginTransaction()
            .addToBackStack(null)
            .replace(id, fragment)
            .commit();
    }
}
