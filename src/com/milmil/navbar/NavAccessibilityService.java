package com.milmil.navbar;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * 접근성 서비스. 두 가지 목적:
 *  1) 뒤로/최근앱 같은 글로벌 액션 수행(performGlobalAction).
 *  2) 이 서비스 컨텍스트로 신뢰된 오버레이(TYPE_ACCESSIBILITY_OVERLAY)를 띄울 수 있게 함.
 * 화면 내용은 읽지 않는다(canRetrieveWindowContent=false).
 */
public class NavAccessibilityService extends AccessibilityService {
    static final String TAG = "MilmilNav";
    static volatile NavAccessibilityService INSTANCE;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        NavBarService s = NavBarService.INSTANCE;
        if (s != null && event != null && event.getPackageName() != null) {
            s.onForegroundWindow(event.getPackageName().toString());
        }
    }
    @Override public void onInterrupt() { }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        Log.i(TAG, "a11y connected");
        if (ConfigStore.isEnabled(this)) {
            startForegroundService(new Intent(this, NavBarService.class));
            NavBarService s = NavBarService.INSTANCE;
            if (s != null) s.rebuildOverlays();
        }
    }

    boolean globalBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    boolean globalHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    boolean globalRecents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }
    boolean globalScreenshot() { return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); }

    @Override
    public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }
}
