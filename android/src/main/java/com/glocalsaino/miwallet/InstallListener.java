package com.glocalsaino.miwallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.glocalsaino.miwallet.ui.PassImportActivity;

public class InstallListener extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String rawReferrerString = intent.getStringExtra("referrer");
        if (rawReferrerString != null) {

            final Intent newIntent = new Intent(context, PassImportActivity.class);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            newIntent.setData(Uri.parse(rawReferrerString));

            context.startActivity(newIntent);
        }
    }

}
