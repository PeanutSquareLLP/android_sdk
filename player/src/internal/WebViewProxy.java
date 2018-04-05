package com.spark.player.internal;
import android.util.Log;
import android.webkit.JavascriptInterface;
import com.google.android.exoplayer2.C;
import com.spark.player.Const;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
final class WebViewProxy {
private List<WeakReference<ExoPlayerController>> m_proxy_list;
WebViewProxy(){
    m_proxy_list = new LinkedList<>();
    Log.d(Const.TAG, m_proxy_list.toString());
}
synchronized void register_proxy(ExoPlayerController proxy){
    m_proxy_list.add(new WeakReference<>(proxy)); }
synchronized void unregister_proxy(ExoPlayerController proxy){
    Iterator<WeakReference<ExoPlayerController>> it = m_proxy_list.iterator();
    while (it.hasNext())
    {
        ExoPlayerController proxy_test = it.next().get();
        if (proxy_test!=null && proxy_test.equals(proxy))
        {
            it.remove();
            break;
        }
    }
}
@JavascriptInterface
public synchronized String get_player_ids(){
    StringBuilder sb = new StringBuilder();
    for (WeakReference<ExoPlayerController> p : m_proxy_list)
    {
        ExoPlayerController proxy_test = p.get();
        if (proxy_test!=null)
            sb.append(proxy_test.hashCode()).append(',');
    }
    if (sb.length()==0)
        return "";
    return sb.substring(0, sb.length()-1);
}
private synchronized ExoPlayerController findPlayer(int hashCode){
    for (WeakReference<ExoPlayerController> p : m_proxy_list)
    {
        ExoPlayerController proxy_test = p.get();
        if (proxy_test!=null && proxy_test.hashCode()==hashCode)
            return proxy_test;
    }
    Log.e(Const.TAG, "player not found "+hashCode);
    return null;
}
@JavascriptInterface
public long get_duration(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
        {
            long dur = p.get_video_duration();
            return dur==C.TIME_UNSET ? 0 : dur;
        }
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return 0;
}
@JavascriptInterface
public int get_pos(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.get_pos();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return 0;
}
@JavascriptInterface
public int get_ws_socket(){ return -1; }
@JavascriptInterface
public String get_url(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.get_url();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return "";
}
@JavascriptInterface
public int get_bitrate(){ return 0; }
@JavascriptInterface
public void seek(long ms, int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            p.seek(ms);
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
}
@JavascriptInterface
public int get_bandwidth(){ return 0; }
@JavascriptInterface
public String get_levels(){ return ""; }
@JavascriptInterface
public boolean is_live_stream(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.is_live_stream();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return false;
}
@JavascriptInterface
public String get_segment_info(String url){ return ""; }
@JavascriptInterface
public String get_state(){ return "PLAYING"; }
@JavascriptInterface
public String get_buffered(){ return ""; }
@JavascriptInterface
public long get_buffered_pos(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
        {
            long pos = p.get_buffred_pos();
            return pos==C.TIME_UNSET ? 0 : pos;
        }
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return 0;
}
@JavascriptInterface
public void js_attach_ready(int hashCode){
    ExoPlayerController p = findPlayer(hashCode);
    if (p!=null)
        p.js_attach_ready();
}
@JavascriptInterface
public boolean is_prepared(int hashCode){
    ExoPlayerController p = findPlayer(hashCode);
    return p!=null && p.is_prepared();
}
@JavascriptInterface
public String get_app_label(int hashCode){
    ExoPlayerController p = findPlayer(hashCode);
    return p!=null ? p.get_app_label() : "";
}
@JavascriptInterface
public String get_player_name(){ return Const.PLAYER_NAME; }
@JavascriptInterface
public void wrapper_attached(){}
@JavascriptInterface
public boolean is_ad_playing(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.is_playing_ad();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return false;
}
@JavascriptInterface
public String get_poster(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.get_poster();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return null;
}
@JavascriptInterface
public String get_title(int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            return p.get_title();
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
    return null;
}
@JavascriptInterface
public void module_cb(final String module, String fn, String value,
    int hashCode){
    try {
        ExoPlayerController p = findPlayer(hashCode);
        if (p!=null)
            p.module_cb(module, fn, value);
    } catch(Exception e){
        Log.d(Const.TAG, "exception hash: "+hashCode);
        Log.e(Const.TAG, "exception", e);
    }
}
void trigger_js(){
    for (WeakReference<ExoPlayerController> p : m_proxy_list)
    {
        ExoPlayerController proxy_test = p.get();
        if (proxy_test!=null)
            proxy_test.js_inited();
    }
}
void unregister_all(){
    while (m_proxy_list.size()>0)
    {
        ExoPlayerController proxy_test = m_proxy_list.get(0).get();
        if (proxy_test!=null)
            proxy_test.get_player().release();
    }
}
}
