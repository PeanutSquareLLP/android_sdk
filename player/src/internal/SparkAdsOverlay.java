package com.spark.player.internal;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
public class SparkAdsOverlay extends FrameLayout {
private Listener m_listener = null;
public SparkAdsOverlay(Context context){ this(context, null); }
public SparkAdsOverlay(Context context, AttributeSet attrs){
    this(context, attrs, 0); }
public SparkAdsOverlay(Context context, AttributeSet attrs, int defStyleAttr){
    super(context, attrs, defStyleAttr); }
void set_listener(Listener listener){ m_listener = listener; }
void remove_listener(){ m_listener = null; }
@Override
public void onViewAdded(View child){
    super.onViewAdded(child);
    if (child instanceof WebView && m_listener!=null)
        m_listener.webview_added((WebView) child);
}
interface Listener {
    void webview_added(WebView child);
}
}
