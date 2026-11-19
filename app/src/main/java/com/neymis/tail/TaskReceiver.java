package com.neymis.tail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TaskReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String command = intent.getStringExtra("command");
        if (command == null || command.isEmpty()) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.putExtra("run_cmd", command);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bundle options = null;
        context.startActivity(launch);
    }
}
