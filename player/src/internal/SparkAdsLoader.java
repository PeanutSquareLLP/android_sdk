package com.spark.player.internal;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.spark.player.Const;
import com.spark.player.PlayItem;
import com.spark.player.R;

import java.util.Timer;
import java.util.TimerTask;
class SparkAdsLoader implements VideoAdPlayer.VideoAdPlayerCallback, SparkAdsOverlay.Listener {
private final boolean DEBUG = true;
private final Handler m_handler;
private final int m_timeout;
private final Context m_context;
private final ExoPlayer m_player;
private ImaAdsLoader m_adsloader;
private SparkAdsOverlay m_overlay;
private boolean m_forceskip;
private WebView m_webview;
private Timer m_timer;
private Button m_button;
SparkAdsLoader(Context context, PlayItem item, ExoPlayer player, Handler handler)
{
    ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(context);
    ImaSdkSettings settings = new ImaSdkSettings();
    settings.setDebugMode(DEBUG);
    builder.setImaSdkSettings(settings);
    m_adsloader = builder.buildForAdTag(Uri.parse(item.get_ad_tag()));
    m_adsloader.addCallback(this);
    m_handler = handler;
    m_timeout = item.get_ad_timeout();
    m_context = context;
    m_player = player;
}
void add_callback(VideoAdPlayer.VideoAdPlayerCallback listener){
    m_adsloader.addCallback(listener); }
void remove_callback(VideoAdPlayer.VideoAdPlayerCallback listener){
    m_adsloader.removeCallback(listener); }
AdsMediaSource get_media_source(MediaSource media_source,
    DefaultDataSourceFactory datasource, ViewGroup overlay)
{
    m_overlay = (SparkAdsOverlay) overlay;
    m_overlay.set_listener(this);
    return new AdsMediaSource(media_source, datasource, m_adsloader, overlay);
}
void release(){
    stop_timer();
    remove_skip_button();
    m_adsloader.stopAd();
    m_adsloader.release();
    if (m_overlay == null)
        return;
    if (m_overlay.getChildCount()>0)
    {
        View first_view = m_overlay.getChildAt(0);
        if (first_view instanceof WebView)
            ((WebView) first_view).destroy();
    }
    m_overlay.removeAllViews();
    m_overlay.remove_listener();
    m_overlay = null;
}
private Ad get_current_ad(){
    AdsManager ads_manager =
        (AdsManager) Utils.get_field(m_adsloader, AdsManager.class);
    if (ads_manager==null)
        return null;
    return ads_manager.getCurrentAd();
}
@Override
public void onPlay(){
    Log.d(Const.TAG, "adsLoader onPlay");
    Ad ad = get_current_ad();
    Log.d(Const.TAG, "adsLoader found ad "+ad.getDuration());
    m_forceskip = ad.isLinear() && !ad.isSkippable() && m_timeout!=0 &&
        (ad.getDuration()*1000>m_timeout+2000);
}
private void stop_timer(){
    if (m_timer==null)
        return;
    m_timer.cancel();
    m_timer = null;
}
private void update_skip_button(){
    int left = (int)Math.floor((m_timeout-m_player.getCurrentPosition())/1000.)+1;
    if (left>0)
    {
        m_button.setText(m_context.getString(R.string.wait_ad)+" "+left);
        m_button.setClickable(false);
        return;
    }
    m_button.setClickable(true);
    m_button.setText(m_context.getString(R.string.skip_ad));
    m_button.setTextSize(TypedValue.COMPLEX_UNIT_PX,
        m_button.getTextSize()*1.5f);
    m_button.setTextColor(Color.WHITE);
    m_button.setPadding(m_button.getPaddingLeft()/2, m_button.getPaddingTop(),
        m_button.getPaddingRight()/2, m_button.getPaddingBottom());
    m_button.setHeight((int)(m_button.getHeight()*1.5f));
    m_button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view){ release(); }
    });
    stop_timer();
}
private void update_ui(){
    ((Activity)m_context).runOnUiThread(new Runnable(){
        @Override
        public void run(){ update_skip_button(); }
    });
}
private void create_skip_button(){
    LayoutInflater inflater = LayoutInflater.from(m_context);
    View view = inflater.inflate(R.layout.spark_ad_skip_button, m_overlay,
        true);
    m_button = view.findViewById(R.id.spark_ad_skip_button);
    update_skip_button();
    m_timer = new Timer();
    m_timer.schedule(new TimerTask() {
        @Override
        public void run(){ update_ui(); }
    }, 0, 300);

}
private void remove_skip_button(){
    if (m_overlay==null || m_button==null)
        return;
    m_overlay.removeView((View) m_button.getParent());
}
@Override
public void onVolumeChanged(int i){}
@Override
public void onPause(){
    Log.d(Const.TAG, "adsLoader onPause");
}
@Override
public void onResume(){
    Log.d(Const.TAG, "adsLoader onResume");
}
@Override
public void onEnded(){
    Log.d(Const.TAG, "adsLoader onEnded");
    stop_timer();
    remove_skip_button();
}
@Override
public void onError(){
    Log.d(Const.TAG, "adsLoader onError");
    release();
}
@Override
public void webview_added(WebView child){
    m_webview = child;
    if (m_forceskip)
        create_skip_button();
}
}
