package com.xwallet.legacy.presenter.activities.settings;

import android.app.Activity;
import android.os.Bundle;

import com.xwallet.R;


/**
 * Used for Unit testing onlyg
 */
public class TestActivity extends Activity { //extends Activity, not BRActivity!
    private static final String TAG = TestActivity.class.getName();


    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controller_about);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


}
