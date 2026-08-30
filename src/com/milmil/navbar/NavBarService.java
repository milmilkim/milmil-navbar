package com.milmil.navbar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 하단 네비바 오버레이를 관리하는 포그라운드 서비스(항상 자동숨김).
 * 버튼: 숨기기/뒤로/홈/새로고침(잔상 제거)/밝기/옵션(리프레시 모드)/회전(정식 다이얼로그).
 */
public class NavBarService extends Service {
    static final String TAG = "MilmilNav";
    static final String ACTION_RELOAD = "com.milmil.navbar.RELOAD";
    static volatile NavBarService INSTANCE;

    private WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());

    private NavBarView bar;
    private View edge;         // 하단 스와이프업 감지 스트립
    private boolean barShown = false;

    // 하단에 걸리는 시스템 팝업(e잉크 옵션/회전)이 떠있는 동안 엣지 스트립을 치워
    // 팝업 하단 슬라이더 조작이 우리 바를 열지 않게 한다. 0=평상,1=팝업대기,2=팝업확인
    private int edgeSuppress = 0;
    private String suppressPkg;

    private final Runnable autoHide = new Runnable() {
        @Override public void run() { hideBar("timeout"); }
    };

    private final Runnable edgeRestore = new Runnable() {
        @Override public void run() { edgeSuppress = 0; if (!barShown) addEdge(); }
    };

    // ---------------- lifecycle ----------------

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        INSTANCE = this;
        reloadConfig();
        Log.i(TAG, "navbar service up");
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && ACTION_RELOAD.equals(i.getAction())) {
            main.post(new Runnable() { @Override public void run() { reloadConfig(); } });
        }
        return START_STICKY;
    }

    /** 설정 변경/최초 기동 시 오버레이를 처음부터 다시 구성한다. */
    private void reloadConfig() {
        wm = windowManager();
        removeAllViews();
        buildBar();
        setupMode();
    }

    /** 접근성 서비스가 뒤늦게 연결되면 신뢰된 오버레이 타입으로 다시 붙인다. */
    void rebuildOverlays() {
        main.post(new Runnable() { @Override public void run() { reloadConfig(); } });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        INSTANCE = null;
        removeAllViews();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------------- overlay helpers ----------------

    private WindowManager windowManager() {
        NavAccessibilityService a = NavAccessibilityService.INSTANCE;
        Context ctx = (a != null) ? a : this;
        return (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
    }

    private int overlayType() {
        return (NavAccessibilityService.INSTANCE != null)
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    private void removeView(View v) {
        if (v == null) return;
        try { wm.removeView(v); } catch (Exception ignored) {}
    }

    private void removeAllViews() {
        removeView(bar);
        removeView(edge);
        barShown = false;
        edgeSuppress = 0;
        main.removeCallbacks(autoHide);
        main.removeCallbacks(edgeRestore);
    }

    /** 하단에 걸리는 시스템 팝업을 열 때: 엣지 스트립을 치우고, 팝업이 닫히면(접근성 감지) 복구한다. */
    private void suppressEdge(String dialogPkg) {
        removeView(edge);
        edgeSuppress = 1;
        suppressPkg = dialogPkg;
        main.removeCallbacks(edgeRestore);
        main.postDelayed(edgeRestore, 30000); // 감지 실패 대비 안전 복구
    }

    /** 접근성 서비스가 전면 창 변화를 알려준다. 대상 팝업이 떴다 사라지면 엣지를 복구. */
    void onForegroundWindow(String pkg) {
        if (edgeSuppress == 0 || pkg == null) return;
        if (pkg.equals(suppressPkg)) { edgeSuppress = 2; return; } // 팝업 떴음
        if (edgeSuppress == 2) {                                    // 팝업 사라지고 다른 창 전면
            edgeSuppress = 0;
            main.removeCallbacks(edgeRestore);
            main.post(new Runnable() { @Override public void run() { if (!barShown) addEdge(); } });
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private int barHeightPx() { return dp(ConfigStore.barHeightDp(this)); }

    // ---------------- bar building ----------------

    private void buildBar() {
        List<String[]> btns = new ArrayList<>();
        btns.add(new String[]{"hide", "숨기기"}); // 맨 왼쪽 ▼
        if (ConfigStore.btn(this, "back"))       btns.add(new String[]{"back", "뒤로"});
        if (ConfigStore.btn(this, "home"))       btns.add(new String[]{"home", "홈"});
        if (ConfigStore.btn(this, "recents"))    btns.add(new String[]{"recents", "최근앱"});
        if (ConfigStore.btn(this, "refresh"))    btns.add(new String[]{"refresh", "새로고침"});
        if (ConfigStore.btn(this, "screenshot")) btns.add(new String[]{"screenshot", "스크린샷"});
        if (ConfigStore.btn(this, "brightness")) btns.add(new String[]{"brightness", "밝기"});
        if (ConfigStore.btn(this, "options"))    btns.add(new String[]{"options", "옵션"});
        if (ConfigStore.btn(this, "rotate"))     btns.add(new String[]{"rotate", "회전"});
        bar = new NavBarView(this, btns, new NavBarView.Listener() {
            @Override public void onAction(String a) { NavBarService.this.onAction(a); }
        });
    }

    private void setupMode() {
        removeView(bar);
        removeView(edge);
        barShown = false;
        addEdge();
    }

    // ---------------- show / hide ----------------

    private void showBar(String why) {
        if (barShown) { rearmAutoHide(); return; }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, barHeightPx(),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.BOTTOM;
        try { wm.addView(bar, lp); barShown = true; }
        catch (Exception e) { Log.w(TAG, "showBar " + e); }
        removeView(edge);
        rearmAutoHide();
        Log.i(TAG, "showBar " + why);
    }

    private void hideBar(String why) {
        main.removeCallbacks(autoHide);
        if (!barShown) return;
        removeView(bar);
        barShown = false;
        addEdge();
        Log.i(TAG, "hideBar " + why);
    }

    private void rearmAutoHide() {
        main.removeCallbacks(autoHide);
        main.postDelayed(autoHide, 4000);
    }

    /** 화면 맨 아래에 얇고 거의 투명한 스와이프업 감지 스트립을 붙인다. */
    private void addEdge() {
        if (edge == null) {
            edge = new View(this);
            edge.setBackgroundColor(Color.TRANSPARENT); // e잉크 검정 리프레시 밴드 방지
            edge.setOnTouchListener(new View.OnTouchListener() {
                float downY;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downY = e.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (downY - e.getRawY() > dp(8)) { showBar("swipeup"); }
                            return true;
                        case MotionEvent.ACTION_UP:
                            showBar("edgetap");
                            return true;
                    }
                    return false;
                }
            });
        }
        removeView(edge);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(28), // 투명이라 밴드 없음 → 감지 영역 넉넉히
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);
        lp.gravity = Gravity.BOTTOM;
        try { wm.addView(edge, lp); }
        catch (Exception e) { Log.w(TAG, "addEdge " + e); }
    }

    // ---------------- actions ----------------

    private void onAction(String a) {
        rearmAutoHide();
        NavAccessibilityService s = NavAccessibilityService.INSTANCE;
        switch (a) {
            case "hide":
                hideBar("hidebtn");
                break;
            case "back":
                if (s != null) s.globalBack(); else toast("접근성 서비스를 켜주세요");
                break;
            case "home":
                if (s != null) s.globalHome(); else startHome();
                break;
            case "recents":
                openRecents();
                break;
            case "refresh":
                doRefresh();
                break;
            case "screenshot":
                takeScreenshot();
                break;
            case "brightness":
                stepBrightness();
                break;
            case "options":
                openRefreshModes();
                break;
            case "rotate":
                openRotationDialog();
                break;
        }
    }

    private void startHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    /** 바를 먼저 숨긴 뒤 시스템 스크린샷을 찍는다(스샷에 네비바가 안 찍히게). */
    private void takeScreenshot() {
        hideBar("screenshot");
        main.postDelayed(new Runnable() {
            @Override public void run() {
                NavAccessibilityService s = NavAccessibilityService.INSTANCE;
                if (s != null) s.globalScreenshot(); else toast("접근성 서비스를 켜주세요");
            }
        }, 350);
    }

    /** 하오칭 리프레시 모드 선택 팝업을 바로 띄운다(브로드캐스트). 바에 가리지 않게 바를 숨긴다. */
    private void openRefreshModes() {
        hideBar("options");
        suppressEdge("com.haoqing.settings"); // 옵션창 하단 슬라이더 조작이 바를 안 열게
        Intent i = new Intent("com.haoqing.action.start.RefreshSettings");
        i.setPackage("com.haoqing.settings");
        try { sendBroadcast(i); }
        catch (Exception e) { Log.w(TAG, "refreshModes " + e); toast("리프레시 설정을 열 수 없어요"); }
    }

    /** 크레마 최근앱/메모리 관리 화면(겹친 네모)을 연다. 상단바의 그것과 동일한 브로드캐스트. */
    private void openRecents() {
        hideBar("recents");
        Intent i = new Intent("com.haoqing.action.TOGGLE_RECENTS");
        i.setPackage("com.android.systemui");
        try { sendBroadcast(i); }
        catch (Exception e) { Log.w(TAG, "recents " + e); toast("최근앱을 열 수 없어요"); }
    }

    /** 크레마 정식 회전 다이얼로그(자동/수직/수평/수동 T 4방향)를 바로 띄운다. 바는 숨긴다. */
    private void openRotationDialog() {
        hideBar("rotate");
        suppressEdge("com.android.systemui"); // 회전 다이얼로그 하단 버튼 조작이 바를 안 열게
        Intent i = new Intent("com.haoqing.action.SHOW_AUTOMATICROTATION_DIALOG");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); }
        catch (Exception e) { Log.w(TAG, "rotDialog " + e); toast("회전 설정을 열 수 없어요"); }
    }

    /** 전체화면 흑->백 순간 플래시로 e잉크 풀 리프레시(잔상 제거)를 유발한다. */
    private void doRefresh() {
        final View flash = new View(this);
        flash.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(flash, lp); }
        catch (Exception e) { Log.w(TAG, "flash " + e); return; }
        main.postDelayed(new Runnable() { @Override public void run() { flash.setBackgroundColor(Color.WHITE); } }, 130);
        main.postDelayed(new Runnable() { @Override public void run() { removeView(flash); } }, 260);
    }

    /** 화면 밝기를 단계별로 순환한다(단탭). */
    private void stepBrightness() {
        if (!Settings.System.canWrite(this)) { toast("‘시스템 설정 수정’ 권한을 켜주세요"); return; }
        try {
            ContentResolver cr = getContentResolver();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
            int[] steps = {8, 64, 128, 192, 255};
            int next = steps[0];
            for (int st : steps) { if (st > cur + 4) { next = st; break; } }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next);
        } catch (Exception e) { Log.w(TAG, "brightness " + e); }
    }

    // ---------------- misc ----------------

    private void toast(final String m) {
        main.post(new Runnable() {
            @Override public void run() { Toast.makeText(NavBarService.this, m, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "navbar", "네비바", NotificationManager.IMPORTANCE_MIN));
        startForeground(1, new Notification.Builder(this, "navbar")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("밀밀네비바 작동 중")
                .build());
    }
}
