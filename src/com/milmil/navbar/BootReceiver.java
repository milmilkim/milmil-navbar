package com.milmil.navbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 부팅 완료 시, 네비바가 켜져 있던 상태면 서비스를 다시 띄운다. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (ConfigStore.isEnabled(c)) {
            c.startForegroundService(new Intent(c, NavBarService.class));
        }
    }
}
