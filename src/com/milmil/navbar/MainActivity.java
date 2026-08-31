package com.milmil.navbar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 밀밀네비바 설정 화면. */
public class MainActivity extends Activity {

    private TextView statusView;
    private final List<Switch> a11ySwitches = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        root.addView(title("밀밀네비바"));

        root.addView(sw("네비바 켜기", ConfigStore.isEnabled(this), new Consumer<Boolean>() {
            @Override public void accept(Boolean v) {
                if (v) {
                    ConfigStore.setEnabled(MainActivity.this, true);
                    startForegroundService(reloadIntent());
                } else {
                    ConfigStore.setEnabled(MainActivity.this, false);
                    stopService(new Intent(MainActivity.this, NavBarService.class));
                }
            }
        }));

        root.addView(section("버튼"));
        root.addView(hint("‘뒤로’와 ‘스크린샷’은 접근성 서비스가 필요합니다. 접근성을 켠 뒤에 선택할 수 있어요."));
        String[][] btns = {
                {"back", "뒤로"}, {"home", "홈"}, {"recents", "최근앱(메모리)"},
                {"refresh", "새로고침(잔상 제거)"}, {"screenshot", "스크린샷"}, {"brightness", "밝기"},
                {"options", "옵션(리프레시 모드)"}, {"rotate", "회전"}
        };
        for (String[] bt : btns) {
            final String key = bt[0];
            boolean needsA11y = "back".equals(key) || "screenshot".equals(key);
            Switch s = sw(bt[1] + (needsA11y ? " (접근성 필요)" : ""),
                    ConfigStore.btn(this, key), new Consumer<Boolean>() {
                @Override public void accept(Boolean v) { ConfigStore.setBtn(MainActivity.this, key, v); reloadIfOn(); }
            });
            if (needsA11y) { s.setEnabled(a11yOn()); a11ySwitches.add(s); }
            root.addView(s);
        }

        root.addView(section("권한"));
        root.addView(btn("접근성 서비스 켜기 (뒤로·스크린샷 필수)", new Runnable() {
            @Override public void run() { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        }));
        root.addView(btn("다른 앱 위에 표시 허용", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        root.addView(btn("시스템 설정 수정 허용 (밝기 필수)", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));

        statusView = hint("");
        root.addView(statusView);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) statusView.setText(status());
        // 접근성 상태에 따라 뒤로/스크린샷 토글 활성화 여부 갱신
        boolean a11y = a11yOn();
        for (Switch s : a11ySwitches) s.setEnabled(a11y);
        // 권한(오버레이 등)을 설정에서 부여하고 돌아왔을 때 서비스가 오버레이를 다시 붙이도록 리로드
        reloadIfOn();
    }

    // ---------- helpers ----------

    private Intent reloadIntent() {
        Intent i = new Intent(this, NavBarService.class);
        i.setAction(NavBarService.ACTION_RELOAD);
        return i;
    }

    private void reloadIfOn() {
        if (ConfigStore.isEnabled(this)) startForegroundService(reloadIntent());
    }

    private String status() {
        return "상태\n"
                + "· 접근성: " + (a11yOn() ? "켜짐" : "꺼짐") + "\n"
                + "· 오버레이 표시: " + (Settings.canDrawOverlays(this) ? "허용" : "거부") + "\n"
                + "· 시스템 설정 수정: " + (Settings.System.canWrite(this) ? "허용" : "거부");
    }

    private boolean a11yOn() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private TextView title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private TextView section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.DKGRAY);
        tv.setPadding(0, dp(18), 0, dp(4));
        return tv;
    }

    private TextView hint(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.DKGRAY);
        tv.setPadding(0, dp(20), 0, 0);
        return tv;
    }

    private Switch sw(String label, boolean val, final Consumer<Boolean> onChange) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setChecked(val);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setOnCheckedChangeListener((v, c) -> onChange.accept(c));
        return s;
    }

    private Button btn(String label, final Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
