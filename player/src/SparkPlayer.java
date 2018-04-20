package com.spark.player;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.spark.player.internal.PlaybackControlView;
import com.spark.player.internal.ExoPlayerController;
import com.spark.player.internal.FullScreenPlayer;
import com.spark.player.internal.PlayerState;
import com.spark.player.internal.SparkTimeBar;
import com.spark.player.internal.Utils;
import com.spark.player.internal.WebViewController;
import net.protyposis.android.spectaculum.InputSurfaceHolder;
import net.protyposis.android.spectaculum.LibraryHelper;
import net.protyposis.android.spectaculum.SpectaculumView;
import net.protyposis.android.spectaculum.effects.ImmersiveEffect;
import net.protyposis.android.spectaculum.gles.GLUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class SparkPlayer extends FrameLayout implements SparkPlayerAPI,
    GestureDetector.OnGestureListener
{
private static String m_customer;
private Context m_context;
private Handler m_handler;
private View m_video_view;
private ExoPlayerController m_controller;
private ExoPlayer m_exoplayer;
private PlayerState m_state;
private FrameLayout m_overlay;
private AspectRatioFrameLayout m_content;
private PlaybackControlView m_control_view;
private FullScreenPlayer m_fullscreen;
private GestureDetector m_gesturedetector;
private float m_downdistance;
private SparkPlayerConfig m_config;
private Set<SparkPlayerAPI.EventListener> m_listeners_dep;
private Set<EventListener> m_listeners;
private ImmersiveEffect m_immersive;
private NetworkImageView m_poster;
private ImageLoader m_image_loader;
private ProgressBar m_loading;
private SparkTimeBar m_timebar;
private float m_panx;
private float m_pany;
private int m_prev_video_width = 960;
private int m_prev_video_height = 540;
private boolean m_controlbar_enabled;
private boolean m_seeking;
private String m_poster_url;
private String m_title;
private int m_prev_orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
private int m_prev_conf_orientation;
private int m_max_height;
private int m_max_width;
// XXX andrey/pavelki: create a map <String, SparkModule>
private SparkModule m_spark_watch_next;
private SparkModule m_spark_persistent;
private SparkModule m_spark_thumbnails;
private SparkModule m_spark_position;
private SparkEventListener m_listener;
private boolean m_js_attach_ready = false;
final private Queue<String> m_msg_queue = new LinkedList<>();

public SparkPlayer(Context context){
    this(context, null);
}
public SparkPlayer(Context context, AttributeSet attrs){
    super(context, attrs);
    Log.d(Const.TAG, "init");
    m_context = context;
    m_handler = new Handler(Looper.myLooper() != null ?
        Looper.myLooper() : Looper.getMainLooper());
    m_state = new PlayerState();
    m_config = new SparkPlayerConfig();
    m_listeners_dep = new CopyOnWriteArraySet<>();
    m_listeners = new CopyOnWriteArraySet<>();
    setBackgroundColor(getResources().getColor(R.color.black));
    m_prev_conf_orientation = getResources().getConfiguration().orientation;
    if (attrs != null)
    {
        TypedArray style = m_context.getTheme().obtainStyledAttributes(attrs,
            R.styleable.SparkPlayer, 0, 0);
        String customer = style.getString(R.styleable.SparkPlayer_customer);
        if (customer!=null)
            set_customer(context, customer);
        m_config.floatmode = style.getBoolean(
            R.styleable.SparkPlayer_float_mode, m_config.floatmode);
        m_config.float_close_on_touch = style.getBoolean(
            R.styleable.SparkPlayer_float_close_on_touch,
            m_config.float_close_on_touch);
        m_config.thumbnails = style.getBoolean(
            R.styleable.SparkPlayer_thumbnails, m_config.thumbnails);
        m_config.vrmode = style.getBoolean(R.styleable.SparkPlayer_vr_mode,
            m_config.vrmode);
        m_config.position_memory = style.getBoolean(
            R.styleable.SparkPlayer_position_memory, m_config.position_memory);
        m_config.watch_next = style.getBoolean(
            R.styleable.SparkPlayer_watch_next, m_config.watch_next);
        m_config.bottom_settings_menu = style.getBoolean(
            R.styleable.SparkPlayer_bottom_settings_menu,
            m_config.bottom_settings_menu);
        m_config.auto_fullscreen = style.getBoolean(
            R.styleable.SparkPlayer_auto_fullscreen,
            m_config.auto_fullscreen);
        m_max_height = style.getDimensionPixelSize(
            R.styleable.SparkPlayer_max_height, Integer.MAX_VALUE);
        m_max_width = style.getDimensionPixelSize(
            R.styleable.SparkPlayer_max_width, Integer.MAX_VALUE);
        m_config.bottom_edge_timebar = style.getBoolean(
            R.styleable.SparkPlayer_bottom_edge_timebar,
            m_config.bottom_edge_timebar);
        style.recycle();
    }
    setup_player();
}
private void setup_player(){
    LayoutInflater.from(m_context).inflate(R.layout.spark_player, this);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    m_listener = new SparkEventListener();
    m_content = findViewById(R.id.spark_player_content);
    Display display = ((WindowManager)m_context.getSystemService(Context
        .WINDOW_SERVICE)).getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    m_content.setAspectRatio(Math.max(size.x, size.y)/
        (float)Math.min(size.x, size.y));
    m_content.requestLayout();
    m_overlay = findViewById(R.id.spark_ad_overlay);
    m_poster = findViewById(R.id.poster_image);
    m_loading = findViewById(R.id.spark_loading_progress);
    WebViewController.register(this);
    m_controller = new ExoPlayerController(this);
    m_state.m_inited = m_controller.init(m_context, m_overlay);
    m_exoplayer = m_controller.get_exoplayer();
    m_controlbar_enabled = true;
    m_control_view = findViewById(R.id.spark_playback_control);
    m_timebar = findViewById(R.id.spark_progress);
    m_control_view.setPlayer(this);
    m_control_view.show();
    m_controller.add_event_listener(m_listener);
    m_controller.get_exoplayer().addListener(m_listener);
    m_fullscreen = new FullScreenPlayer(m_context);
    m_gesturedetector = new GestureDetector(m_context, this);
    m_gesturedetector.setIsLongpressEnabled(false);
    m_content.setOnTouchListener(new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event){
            return m_gesturedetector.onTouchEvent(event); }
    });
    RequestQueue queue = Volley.newRequestQueue(m_context);
    m_image_loader = new ImageLoader(queue, new ImageLoader.ImageCache() {
        private final LruCache<String, Bitmap> m_cache = new LruCache<>(10);
        public void putBitmap(String url, Bitmap bitmap) {
            m_cache.put(url, bitmap); }
        public Bitmap getBitmap(String url) { return m_cache.get(url); }
    });
    setup_spark_modules(m_config);
    check_create_video_view();
    update_poster_state();
    if (!WebViewController.m_js_inited || !m_js_attach_ready)
        check_hola();
    send_msg("attach", null);
}
public ExoPlayerController get_controller(){ return m_controller; }
public void setup_spark_modules(SparkPlayerConfig config){
    try {
        Class<?> spark_factory = Class.forName("com.spark.library.SparkLoaderFactory");
        Method init = spark_factory.getMethod("init");
        init.invoke(null);
        Method get_instance = spark_factory.getMethod("get_instance");
        SparkLoader spark_loader = (SparkLoader) get_instance.invoke(null);
        for(SparkModuleFactory m: spark_loader.get_modules())
        {
            if (!WebViewController.check_feature(m.get_name()))
                continue;
            switch (m.get_name())
            {
                case "playlist":
                    if (config.watch_next && m_spark_watch_next==null)
                        m_spark_watch_next = m.init(this);
                    break;
                case "thumbnails":
                    if (config.thumbnails && m_spark_thumbnails==null)
                        m_spark_thumbnails = m.init(this);
                    break;
                case "persistent_video":
                    if (config.floatmode && !config.vrmode &&
                        m_spark_persistent==null)
                    {
                        m_spark_persistent = m.init(this);
                    }
                    break;
                case "position_memory":
                    if (config.position_memory && m_spark_position==null)
                        m_spark_position = m.init(this);
                    break;
            }
        }
    } catch(NoSuchMethodException|IllegalAccessException|InvocationTargetException|
        ClassNotFoundException e) { e.printStackTrace(); }
}
public String module_cb(String module, String fn, String value){
    switch (module)
    {
    case "playlist":
        if (m_spark_watch_next!=null)
            return m_spark_watch_next.module_cb(fn, value);
        break;
    case "thumbnails":
        if (m_spark_thumbnails!=null)
            return m_spark_thumbnails.module_cb(fn, value);
        break;
    case "persistent_video":
        if (m_spark_persistent!=null)
            return m_spark_persistent.module_cb(fn, value);
        break;
    case "position_memory":
        if (m_spark_position!=null)
            return m_spark_position.module_cb(fn, value);
        break;
    }
    return "";
}
@Override
protected void onMeasure(int width_spec, int height_spec){
    super.onMeasure(width_spec, height_spec);
    int width_mode = MeasureSpec.getMode(width_spec);
    int width_size = MeasureSpec.getSize(width_spec);
    int height_mode = MeasureSpec.getMode(height_spec);
    int height_size = MeasureSpec.getSize(height_spec);
    int video_width = get_video_width();
    int video_height = get_video_height();
    if (video_width==0)
    {
        video_width = m_prev_video_width;
        video_height = m_prev_video_height;
    }
    else
    {
        m_prev_video_width = video_width;
        m_prev_video_height = video_height;
    }
    float aspect = (float)video_width/video_height;
    int max_width = Math.min(width_mode==MeasureSpec.AT_MOST ? width_size :
        Integer.MAX_VALUE, m_max_width);
    int max_height = Math.min(height_mode==MeasureSpec.AT_MOST ? height_size :
        Integer.MAX_VALUE, m_max_height);
    int width, height;
    if (width_mode==MeasureSpec.EXACTLY && height_mode==MeasureSpec.EXACTLY)
    {
        width = width_size;
        height = height_size;
    }
    else if (width_mode==MeasureSpec.EXACTLY)
    {
        width = width_size;
        height = Math.min(Math.round(width_size/aspect), max_height);
    }
    else if (height_mode==MeasureSpec.EXACTLY)
    {
        height = height_size;
        width = Math.min(Math.round(height_size*aspect), max_width);
    }
    else
    {
        float scale = video_width>max_width || video_height>max_height ?
            Math.min((float)max_width/video_width,
            (float)max_height/video_height) : 1;
        width = Math.round(scale*video_width);
        height = Math.round(scale*video_height);
    }
    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
}
@Override
public void vr_mode(Boolean state){
    Log.d(Const.TAG, "set video vr mode "+state);
    if (m_config.vrmode==state)
        return;
    m_config.vrmode = state;
    check_create_video_view();
}
public static void set_customer(Context m_context, String customer){
    if (m_customer!=null && m_customer.equals(customer))
        return;
    m_customer = customer;
    WebViewController.init(m_context.getApplicationContext(), customer, true);
}
public static String get_customer(){ return m_customer; }
public View get_video_view() {return m_video_view; }
private synchronized void check_create_video_view(){
    Log.d(Const.TAG, "check_create_video_view ad:"+isPlayingAd());
    if (!m_state.m_inited || m_video_view!=null && ((m_config.vrmode &&
        !isPlayingAd()) == m_state.m_vr_active))
    {
        return;
    }
    if (m_video_view!=null)
        m_content.removeView(m_video_view);
    if (m_config.vrmode && !isPlayingAd())
    {
        Log.d(Const.TAG, "add 360 degree video view");
        m_panx = 0.0f;
        m_pany = 0.0f;
        SpectaculumView spectaculum = new SpectaculumView(m_context);
        spectaculum.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        m_immersive = new ImmersiveEffect();
        spectaculum.addEffect(m_immersive);
        spectaculum.selectEffect(0);
        m_content.addView(spectaculum, 0);
        m_controller.set_view(spectaculum);
        m_video_view = spectaculum;
        m_state.m_vr_active = true;
        return;
    }
    Log.d(Const.TAG, "add normal video view");
    TextureView texture_view = new TextureView(m_context);
    texture_view.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
    m_content.addView(texture_view, 0);
    m_video_view = texture_view;
    m_controller.set_view(texture_view);
    m_state.m_vr_active = false;
}
@Override
public void play(){
    Log.d(Const.TAG, "play "+m_state.m_inited);
    if (!m_state.m_inited)
        return;
    setPlayWhenReady(true);
}
@Override
public void pause(){
    if (!m_state.m_inited)
        return;
    Log.d(Const.TAG, "pause");
    setPlayWhenReady(false);
}
@Override
public void load(String url){
    Log.d(Const.TAG, "load "+url+" "+m_state.m_inited);
    if (!m_state.m_inited)
        return;
    m_controller.load(url);
}
@Override
public void queue(PlayItem item){
    Log.d(Const.TAG, "queue "+item.get_media()+" "+item.get_ad_tag());
    if (!m_state.m_inited)
        return;
    m_title = item.get_title();
    m_controller.queue(item);
    String poster = item.get_poster();
    set_poster(poster);
    m_control_view.hide();
}
public String get_url(){ return m_controller.get_url(); }
public String get_title(){ return m_title; }
@Override
public void set_poster(String poster_url){
    if (poster_url!=null && !poster_url.isEmpty())
        m_poster.setImageUrl(poster_url, m_image_loader);
    m_poster_url = poster_url;
    update_poster_state();
}
public String get_poster(){ return m_poster_url; }
private void update_poster_state(){
    int state = getPlaybackState();
    m_poster.setVisibility(m_poster_url!=null && !m_poster_url.isEmpty() &&
        (state==Player.STATE_IDLE || state==Player.STATE_ENDED ||
        !m_controller.has_video_track()) ? VISIBLE : GONE);
}
private void update_loading_state(){
    m_loading.setVisibility(getPlaybackState()==Player.STATE_BUFFERING &&
        !isPlayingAd() ? VISIBLE : GONE);
}
private void update_keep_screen_state(){
    int state = getPlaybackState();
    setKeepScreenOn(getPlayWhenReady() && state!=Player.STATE_ENDED &&
        state!=Player.STATE_IDLE);
}
@Override
public void uninit(){
    Log.d(Const.TAG, "uninit");
    m_controller.get_exoplayer().removeListener(m_listener);
    m_controller.remove_event_listener(m_listener);
    m_controller.uninit();
    WebViewController.unregister(this);
}
private boolean is_fully_visible(){
    if (!isShown())
        return false;
    int width = getWidth(), height = getHeight();
    Rect r = new Rect(0, 0, width, height);
    getParent().getChildVisibleRect(this, r, null);
    return r.height()==height && r.width()==width && r.top>=0 &&
        r.bottom>0 && r.left>=0 && r.right>0;
}
@Override
public void onConfigurationChanged(Configuration conf){
    if (m_prev_conf_orientation==conf.orientation)
        return;
    m_prev_conf_orientation = conf.orientation;
    if (m_config.auto_fullscreen && getPlaybackState()!=Player.STATE_IDLE &&
        conf.orientation==Configuration.ORIENTATION_LANDSCAPE &&
        get_aspect()>1 && is_fully_visible() && !is_floating())
    {
        fullscreen(true);
    }
}
@Override
public void fullscreen(Boolean state){
    boolean new_state = state!=null ? state : !m_state.m_fullscreen;
    Log.d(Const.TAG, "fullscreen "+new_state);
    if (new_state==m_state.m_fullscreen)
        return;
    m_state.m_fullscreen = new_state;
    show_fullscreen();
}
private void show_fullscreen(){
    check_create_video_view();
    ViewGroup.LayoutParams lp = m_loading.getLayoutParams();
    Activity activity = Utils.get_activity(m_context);
    if (m_state.m_fullscreen)
    {
        m_fullscreen.activate(this);
        if (activity!=null)
        {
            m_prev_orientation = activity.getRequestedOrientation();
            activity.setRequestedOrientation(get_aspect()>1 ?
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        lp.width = lp.height = Utils.dp2px(m_context, 96);
    }
    else
    {
        if (activity!=null)
            activity.setRequestedOrientation(m_prev_orientation);
        m_fullscreen.restore_player();
        lp.width = lp.height = Utils.dp2px(m_context, 64);
    }
    m_loading.setLayoutParams(lp);
    for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
        listener.on_fullscreen_changed(m_state.m_fullscreen);
    for (EventListener listener : m_listeners)
        listener.onFullscreenChanged(m_state.m_fullscreen);
}
public boolean is_fullscreen(){ return m_state.m_fullscreen; }
@Override
public void float_mode(Boolean state){
    if (!m_state.m_inited || m_config.floatmode== state ||
        m_spark_persistent==null)
    {
        return;
    }
    m_config.floatmode = state;
    m_spark_persistent.set_status(state ? Const.feature_status.ENABLED :
        Const.feature_status.DISABLED);
}
public boolean is_floating(){
    return m_spark_persistent!=null &&
        m_spark_persistent.get_state(null)==Const.feature_state.ACTIVE;
}
public void set_watch_next_items(PlayListItem[] items){
    if (m_spark_watch_next==null)
        return;
    m_spark_watch_next.set_state(items);}
public SparkModule get_watch_next_ctrl(){ return m_spark_watch_next; }
@Override
public int get_video_width(){
    Drawable poster = m_poster!=null ? m_poster.getDrawable() : null;
    if (m_controller.has_video_track())
        return m_controller.get_video_width();
    else if (poster!=null && m_poster.getVisibility()==VISIBLE &&
        poster.getIntrinsicWidth()>0)
    {
        return poster.getIntrinsicWidth();
    }
    return 0;
}
@Override
public int get_video_height(){
    Drawable poster = m_poster!=null ? m_poster.getDrawable() : null;
    if (m_controller.has_video_track())
        return m_controller.get_video_height();
    else if (poster!=null && m_poster.getVisibility()==VISIBLE &&
        poster.getIntrinsicHeight()>0)
    {
        return poster.getIntrinsicHeight();
    }
    return 0;
}
public float get_aspect(){
    float height = get_video_height();
    return height==0 ? 0 : get_video_width()/height;
}
@Override
public void set_controls_state(boolean enabled){
    m_controlbar_enabled = enabled;
    if (!enabled)
        m_control_view.hide();
}
@Override
public boolean get_controls_state(){ return m_controlbar_enabled; }
@Override
public void set_controls_visibility(boolean visible){
    if (visible)
        m_control_view.show();
    else
        m_control_view.hide();
}
public void set_ad_overlay_visibility(boolean visible){
    if (m_overlay==null)
        return;
    m_overlay.setVisibility(visible ? VISIBLE : GONE);
}
@Override
public boolean get_controls_visibility(){ return m_control_view.isVisible(); }
@Override
public boolean get_vr_mode(){ return m_state.m_vr_active; }
public SparkPlayerConfig get_config() { return m_config; }
@Override
public void setVisibility(int visibility) {
    super.setVisibility(visibility);
    if (m_video_view !=null)
        m_video_view.setVisibility(visibility);
}
private float get_distance(MotionEvent e){
    float distance_x = e.getX(1)-e.getX(0);
    float distance_y = e.getY(1)-e.getY(0);
    return (float)Math.sqrt(distance_x*distance_x+distance_y*distance_y);
}
@Override
public boolean onScroll(MotionEvent e1, MotionEvent e2, float dist_x,
    float dist_y)
{
    Log.d(Const.TAG, "on scroll "+e1+" "+e2);
    int n_taps = e2.getPointerCount();
    if (!m_state.m_inited || n_taps==1 && !m_state.m_vr_active)
        return false;
    if (n_taps==1)
    {
        float[] matrix = new float[16];
        m_panx += dist_x*180f/m_video_view.getWidth();
        m_pany += dist_y*180f/m_video_view.getHeight();
        m_pany = LibraryHelper.clamp(m_pany, -90, 90);
        GLUtils.Matrix.setRotateEulerM(matrix, 0, -m_pany, -m_panx, 0);
        m_immersive.setRotationMatrix(matrix);
        return true;
    }
    if (m_downdistance==0.0f)
        m_downdistance = get_distance(e2);
    else if (get_distance(e2)>m_downdistance*2.f)
        fullscreen(true);
    else if (get_distance(e2)<m_downdistance/2.f)
        fullscreen(false);
    return true;
}
@Override
public boolean onDown(MotionEvent e){
    if (!m_state.m_inited || is_floating())
        return false;
    m_downdistance = 0.0f;
    if (m_state.m_vr_active)
        requestDisallowInterceptTouchEvent(true);
    return true;
}
@Override
public void onShowPress(MotionEvent e){}
@Override
public boolean onSingleTapUp(MotionEvent e){
    if (!m_state.m_inited || is_floating())
        return false;
    requestDisallowInterceptTouchEvent(false);
    if (m_control_view.isVisible())
        m_control_view.hide();
    else if (m_controlbar_enabled)
        m_control_view.show();
    return true;
}
@Override
public void onLongPress(MotionEvent e){}
@Override
public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
    float velocityY)
{
    return false;
}
@Override
public void addListener(Player.EventListener listener){
    m_exoplayer.addListener(listener); }
@Override
public void removeListener(Player.EventListener listener){
    m_exoplayer.removeListener(listener); }
public void addListener(EventListener listener){
    m_exoplayer.addListener(listener);
    m_listeners.add(listener);
}
public void removeListener(EventListener listener){
    m_exoplayer.removeListener(listener);
    m_listeners.remove(listener);
}
@Override
public int getPlaybackState(){ return m_exoplayer.getPlaybackState(); }
@Override
public void setPlayWhenReady(boolean playWhenReady){
    m_exoplayer.setPlayWhenReady(playWhenReady); }
@Override
public boolean getPlayWhenReady(){ return m_exoplayer.getPlayWhenReady(); }
@Override
public void setRepeatMode(int repeatMode){
    m_exoplayer.setRepeatMode(repeatMode); }
@Override
public int getRepeatMode(){ return m_exoplayer.getRepeatMode(); }
@Override
public void setShuffleModeEnabled(boolean shuffleModeEnabled){
    m_exoplayer.setShuffleModeEnabled(shuffleModeEnabled); }
@Override
public boolean getShuffleModeEnabled(){
    return m_exoplayer.getShuffleModeEnabled(); }
@Override
public boolean isLoading(){ return m_exoplayer.isLoading(); }
@Override
public void seekToDefaultPosition(){
    seekToDefaultPosition(getCurrentWindowIndex()); }
@Override
public void seekToDefaultPosition(int windowIndex){
    seekTo(windowIndex, C.TIME_UNSET); }
@Override
public void seekTo(long positionMs){
    seekTo(getCurrentWindowIndex(), positionMs); }
@Override
public void seekTo(int windowIndex, long positionMs){
    m_seeking = true;
    m_exoplayer.seekTo(windowIndex, positionMs);
}
public boolean is_seeking(){
    return m_seeking || m_spark_thumbnails!=null &&
        m_spark_thumbnails.get_state(null)==Const.feature_state.ACTIVE;
}
public boolean is_scrubbing(){ return m_timebar.isScrubbing(); }
@Override
public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters){
    m_exoplayer.setPlaybackParameters(playbackParameters);
}
@Override
public PlaybackParameters getPlaybackParameters(){
    return m_exoplayer.getPlaybackParameters();
}
@Override
public void stop(){ m_exoplayer.stop(); }
public void onPause(){
    if (m_video_view instanceof SpectaculumView)
        ((SpectaculumView)m_video_view).onPause();
}
public void onResume(){
    if (m_video_view instanceof SpectaculumView)
        ((SpectaculumView)m_video_view).onResume();
}
@Override
public void release(){ this.uninit(); }
@Override
public int getRendererCount(){
    return m_exoplayer.getRendererCount();
}

@Override
public int getRendererType(int index){
    return m_exoplayer.getRendererType(index);
}
@Override
public TrackGroupArray getCurrentTrackGroups(){
    return m_exoplayer.getCurrentTrackGroups();
}
@Override
public TrackSelectionArray getCurrentTrackSelections(){
    return m_exoplayer.getCurrentTrackSelections();
}
@Nullable
@Override
public Object getCurrentManifest(){
    return m_exoplayer.getCurrentManifest();
}
@Override
public Timeline getCurrentTimeline(){
    return m_exoplayer.getCurrentTimeline();
}
@Override
public int getCurrentPeriodIndex(){
    return m_exoplayer.getCurrentPeriodIndex();
}
@Override
public int getCurrentWindowIndex(){
    return m_exoplayer.getCurrentWindowIndex();
}
@Override
public int getNextWindowIndex(){
    return m_exoplayer.getNextWindowIndex();
}
@Override
public int getPreviousWindowIndex(){
    return m_exoplayer.getPreviousWindowIndex();
}
@Override
public long getDuration(){
    return m_exoplayer.getDuration();
}
public long getVideoDuration(){
    // original getDuration() method returns ad duration when ad playing
    Timeline timeline = m_exoplayer.getCurrentTimeline();
    if (timeline.isEmpty())
        return C.TIME_UNSET;
    Timeline.Window window = new Timeline.Window();
    return timeline.getWindow(m_exoplayer.getCurrentWindowIndex(),
        window).getDurationMs();
}
@Override
public long getCurrentPosition(){
    return m_exoplayer.getCurrentPosition();
}
@Override
public long getBufferedPosition(){
    return m_exoplayer.getBufferedPosition();
}
@Override
public int getBufferedPercentage(){
    return m_exoplayer.getBufferedPercentage();
}
@Override
public boolean isCurrentWindowDynamic(){
    return m_exoplayer.isCurrentWindowDynamic();
}
@Override
public boolean isCurrentWindowSeekable(){
    return m_exoplayer.isCurrentWindowSeekable();
}
@Override
public boolean isPlayingAd(){
    return m_exoplayer.isPlayingAd();
}
@Override
public int getCurrentAdGroupIndex(){
    return m_exoplayer.getCurrentAdGroupIndex();
}
@Override
public int getCurrentAdIndexInAdGroup(){
    return m_exoplayer.getCurrentAdIndexInAdGroup();
}
@Override
public long getContentPosition(){
    return m_exoplayer.getContentPosition();
}

// XXX andrey: deprecated API, replaced by Player interface implementation
@Override @Deprecated
public boolean is_playing(){
    return getPlayWhenReady() && getPlaybackState()==Player.STATE_READY;
}
@Override @Deprecated
public boolean is_playing_ad(){ return isPlayingAd(); }
@Override @Deprecated
public boolean is_paused(){ return !getPlayWhenReady(); }
@Override @Deprecated
public int get_playback_state(){ return getPlaybackState(); }
@Override @Deprecated
public void seek(long position){ seekTo(position); }
@Override @Deprecated
public long get_position(){ return getCurrentPosition(); }
@Override @Deprecated
public long get_duration(){ return getDuration(); }
@Override @Deprecated
public void add_listener(SparkPlayerAPI.EventListener listener){
    m_listeners_dep.add(listener); }
@Override @Deprecated
public void remove_listener(SparkPlayerAPI.EventListener listener){
    m_listeners_dep.remove(listener); }
public void js_inited(){ setup_spark_modules(get_config()); }
public void js_attach_ready(){ m_js_attach_ready = true; }
public void send_spark_message(String type, final String subtype,
    final String param1, final int param2, final SparkPlayerCallback cb)
{
    // XXX andrey/pavelki: future reserve
    if (BuildConfig.DEBUG && !type.equals("stats"))
        throw new AssertionError();
    if (!WebViewController.m_js_inited)
        return;
    m_handler.post(new Runnable() {
        @Override
        public void run(){
            String script = "stats."+subtype+"('"+param1+"',"+param2+")";
            WebViewController.call_spark_api(script,
                new ValueCallback<String>()
            {
                @Override
                public void onReceiveValue(String s){
                    if (cb!=null)
                        cb.done(s);
                }
            });
        }
    });
}
public void send_msg(String cmd, String data){
    final String msg = "{\"cmd\":\""+cmd+"\""+",\"hash\":"+hashCode()+
        (data!=null ? ","+data : "")+"}";
    m_handler.post(new Runnable(){
        @Override
        public void run(){
            synchronized(m_msg_queue){
                if (!m_js_attach_ready || m_msg_queue.size()>0)
                {
                    m_msg_queue.add(msg);
                    return;
                }
            }
            send_string(msg);
        }
    });
}
private void send_string(String msg){
    Log.d(Const.TAG, "send message "+msg);
    WebViewController.evaluate("window.hola_cdn && hola_cdn"+
        ".android_message('"+msg+"')", null);
}
private void check_hola(){
    if (!WebViewController.m_js_inited || !m_js_attach_ready)
    {
        m_handler.postDelayed(new Runnable() {
            @Override
            public void run(){ check_hola(); }
        }, 300);
    }
    if (WebViewController.get_instance()==null || !WebViewController.is_ready())
        return;
    m_handler.post(new Runnable() {
        @Override
        public void run(){
            WebViewController.evaluate("window.hola_cdn && "+
                "typeof hola_cdn.android_message", new ValueCallback<String>()
            {
                @Override
                public void onReceiveValue(String s){
                    if (!s.equals("\"function\""))
                        return;
                    Log.d(Const.TAG, "JS engine is inited");
                    WebViewController.trigger_js();
                    if (!m_js_attach_ready || m_msg_queue.size()<0)
                        return;
                    synchronized(m_msg_queue){
                        Log.d(Const.TAG, "flush msg queue");
                        if (m_msg_queue.size()>0)
                        {
                            for (String msg: m_msg_queue)
                                send_string(msg);
                            m_msg_queue.clear();
                        }
                    }
                }
            });
        }
    });
}

public interface EventListener extends Player.EventListener {
    void onFullscreenChanged(boolean is_fullscreen);
    void onNewVideo(String url);
    void onTimeUpdate(int position);
    void onAdStart();
    void onAdEnd();
}

public static abstract class DefaultEventListener
    extends Player.DefaultEventListener implements EventListener {
    @Override
    public void onFullscreenChanged(boolean is_fullscreen){}
    @Override
    public void onNewVideo(String url){}
    @Override
    public void onTimeUpdate(int position){}
    @Override
    public void onAdStart(){}
    @Override
    public void onAdEnd(){}
}

private class SparkEventListener extends Player.DefaultEventListener implements
    ExoPlayerController.VideoEventListener, InputSurfaceHolder.Callback
{
    private boolean m_play_when_ready = false;
    private int m_playback_state = Player.STATE_IDLE;
    @Override
    public void onPlayerStateChanged(boolean play_when_ready, int playback_state){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
        {
            if (m_play_when_ready!=play_when_ready)
            {
                if (play_when_ready)
                    listener.on_play();
                else
                    listener.on_pause();
            }
            if (m_playback_state!=playback_state)
                listener.on_state_changed(playback_state);
        }
        m_play_when_ready = play_when_ready;
        m_playback_state = playback_state;
        update_poster_state();
        update_loading_state();
        update_keep_screen_state();
    }
    @Override
    public void onSeekProcessed(){
        m_seeking = false;
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_seeked();
    }
    @Override
    public void onPlayerError(ExoPlaybackException error){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_error(error);
    }
    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_timeline_changed(timeline, manifest);
    }
    @Override
    public void onTracksChanged(TrackGroupArray track_groups, TrackSelectionArray track_selections){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_tracks_changed(track_groups, track_selections);
    }
    @Override
    public void onLoadingChanged(boolean is_loading){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_loading_changed(is_loading);
    }
    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_position_discontinuity(reason);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playback_parameters){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_playback_parameters_changed(playback_parameters);
    }
    @Override
    public void on_video_size(int width, int height){
        float aspect = ((float)width)/height;
        Log.d(Const.TAG, "video aspect "+aspect);
        m_content.setAspectRatio(aspect);
        if (m_video_view instanceof SpectaculumView)
            ((SpectaculumView) m_video_view).updateResolution(width, height);
        Activity activity = Utils.get_activity(m_context);
        if (activity!=null && is_fullscreen())
        {
            activity.setRequestedOrientation(aspect>1 ?
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        m_content.requestLayout();
    }
    @Override
    public void on_rendered_first(){
        Log.d(Const.TAG, "rendered first frame"); }
    @Override
    public void on_ad_start(){
        Log.d(Const.TAG, "ad started st:"+isPlayingAd());
        set_controls_state(false);
        check_create_video_view();
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_ad_start();
        for (EventListener listener : m_listeners)
            listener.onAdStart();
        update_loading_state();
    }
    @Override
    public void on_ad_end(){
        Log.d(Const.TAG, "ad ended st:"+isPlayingAd());
        set_controls_state(true);
        check_create_video_view();
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_ad_end();
        for (EventListener listener : m_listeners)
            listener.onAdEnd();
        update_loading_state();
    }
    @Override
    public void time_update(int cur_pos){
        for (EventListener listener : m_listeners)
            listener.onTimeUpdate(cur_pos);
    }
    @Override
    public void on_new_video(String url){
        for (SparkPlayerAPI.EventListener listener : m_listeners_dep)
            listener.on_new_video(url);
        for (EventListener listener : m_listeners)
            listener.onNewVideo(url);
    }
    @Override
    public void surfaceCreated(InputSurfaceHolder holder){
        m_controller.set_texture(holder.getSurfaceTexture()); }
    @Override
    public void surfaceDestroyed(InputSurfaceHolder holder){
        m_controller.set_texture(null); }
}
}
