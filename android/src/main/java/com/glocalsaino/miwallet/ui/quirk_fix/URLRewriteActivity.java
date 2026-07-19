package com.glocalsaino.miwallet.ui.quirk_fix;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.glocalsaino.miwallet.ui.AlertFragment;
import com.glocalsaino.miwallet.ui.WhatPassActivity;
import com.glocalsaino.miwallet.ui.PassImportActivity;

public class URLRewriteActivity extends WhatPassActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Uri data = getIntent().getData();
        final String url = data != null ? new URLRewriteController(getTracker()).getUrlByUri(data) : null;

        if (url == null) {
            AlertFragment alert = new AlertFragment();
            getSupportFragmentManager().beginTransaction().add(alert, "AlertFrag").commit();

            return;
        }

        final Intent intent = new Intent(this, PassImportActivity.class);
        intent.setData(Uri.parse(url));
        startActivity(intent);
        finish();
    }

}
