package com.milmil.navbar;

import android.content.Context;
import android.content.SharedPreferences;

/** 네비바 설정을 SharedPreferences("navbar")에 저장. */
public class ConfigStore {
    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("navbar", Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) { return p(c).getBoolean("enabled", false); }
    public static void setEnabled(Context c, boolean v) { p(c).edit().putBoolean("enabled", v).apply(); }

    /** true = 자동 숨김(B, 기본), false = 상시 표시(A) */
    public static boolean isAutoHide(Context c) { return p(c).getBoolean("autohide", true); }
    public static void setAutoHide(Context c, boolean v) { p(c).edit().putBoolean("autohide", v).apply(); }

    /** 기기를 물리적으로 돌리면 회전 제안 팝업을 띄울지 (A 방식) */
    public static boolean rotationSuggest(Context c) { return p(c).getBoolean("rot_suggest", true); }
    public static void setRotationSuggest(Context c, boolean v) { p(c).edit().putBoolean("rot_suggest", v).apply(); }

    public static int barHeightDp(Context c) { return p(c).getInt("bar_h", 56); }
    public static void setBarHeightDp(Context c, int v) { p(c).edit().putInt("bar_h", v).apply(); }

    public static boolean btn(Context c, String key) {
        // 접근성 필요(뒤로·스크린샷)와 밝기는 기본 OFF
        boolean def = !("brightness".equals(key) || "screenshot".equals(key) || "back".equals(key));
        return p(c).getBoolean("btn_" + key, def);
    }
    public static void setBtn(Context c, String key, boolean v) { p(c).edit().putBoolean("btn_" + key, v).apply(); }
}
