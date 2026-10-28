package com.mintoosuite.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* mw-v108: after the phone restarts or the app is updated, bring the background service back if the user had it switched on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (BotService.isOn(ctx)) { BotService.start(ctx); }
        } catch (Exception ignored) { }
    }
}
