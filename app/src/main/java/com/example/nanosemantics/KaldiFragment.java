package com.example.nanosemantics;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseFragment;

public class KaldiFragment extends BrowseFragment {

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupUI();
    }

    private void setupUI() {
        Resources res = getResources();
        setTitle(res.getString(R.string.press_play));
        int color = ContextCompat.getColor(getActivity(), R.color.black);
        setBrandColor(color);
    }
}
