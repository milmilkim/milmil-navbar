package com.milmil.navbar;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;

/**
 * 오닉스 스타일 하단바: 흰 배경 + 균등 간격의 검은 벡터 아이콘만.
 * 보더/칸/텍스트/애니메이션 없음.
 */
public class NavBarView extends LinearLayout {

    public interface Listener { void onAction(String action); }

    private final Listener listener;

    /** buttons: 각 원소 {action, label} — 라벨은 사용하지 않음(아이콘만). */
    public NavBarView(Context c, List<String[]> buttons, Listener l) {
        super(c);
        this.listener = l;
        setOrientation(HORIZONTAL);
        setBackgroundColor(Color.WHITE);
        for (String[] b : buttons) addView(makeButton(c, b[0]));
    }

    private static int iconFor(String action) {
        switch (action) {
            case "hide":       return R.drawable.ic_hide;
            case "back":       return R.drawable.ic_back;
            case "home":       return R.drawable.ic_home;
            case "recents":    return R.drawable.ic_recents;
            case "refresh":    return R.drawable.ic_refresh;
            case "screenshot": return R.drawable.ic_screenshot;
            case "brightness": return R.drawable.ic_brightness;
            case "options":    return R.drawable.ic_options;
            case "rotate":     return R.drawable.ic_rotate;
        }
        return 0;
    }

    private View makeButton(Context c, final String action) {
        ImageView icon = new ImageView(c);
        icon.setImageResource(iconFor(action));
        // 아이콘은 각자 색을 가진다(뒤로=흰 삼각형+검정 테두리, 나머지=검정). 틴트 없음.
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(15);
        icon.setPadding(pad, pad, pad, pad);
        icon.setClickable(true);
        icon.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (listener != null) listener.onAction(action);
            }
        });
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        icon.setLayoutParams(lp);
        return icon;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
