package com.spark.player.internal;
import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import com.spark.player.SparkPlayer;

public class FullScreenPlayer extends Dialog {
private boolean m_active;
private SparkPlayer m_player;
private PlayerViewManager m_viewmanager;
public FullScreenPlayer(@NonNull Context context){
    super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    m_active = false;
    m_viewmanager = new PlayerViewManager(context);
}
@Override
public void onWindowFocusChanged(boolean hasFocus){
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus)
        set_window_flags();
}
public void activate(SparkPlayer player){
    m_player = player;
    m_viewmanager.detach(player);
    addContentView(player, new ViewGroup.LayoutParams(ViewGroup.LayoutParams
        .MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    m_active = true;
    set_window_flags();
    show();
}
public void restore_player(){
    if (!m_active)
        return;
    m_viewmanager.restore(m_player);
    m_active = false;
    dismiss();
}
@Override
public void onBackPressed(){
    m_player.fullscreen(false);
    super.onBackPressed();
}
private void set_window_flags(){
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_FULLSCREEN |
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
}
}
