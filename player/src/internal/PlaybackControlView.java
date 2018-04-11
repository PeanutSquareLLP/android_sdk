package com.spark.player.internal;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Util;
import com.spark.player.R;
import com.spark.player.SparkModule;
import com.spark.player.SparkPlayer;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;

public class PlaybackControlView extends FrameLayout {

public static final int DEFAULT_SHOW_TIMEOUT_MS = 5000;
private final ComponentListener m_listener;
private final View m_live_view;
private final View m_play_btn;
private final View m_pause_btn;
private final ImageButton m_replay_btn;
private final TextView m_title;
private final ImageButton m_top_menu;
private final ImageButton m_bottom_menu;
private final ImageButton m_fullscreen_btn;
private final TextView m_duration_view;
private final TextView m_position_view;
private final FrameLayout m_timebar_holder;
private final LinearLayout m_bottom_bar;
private final SparkTimeBar m_timebar;
private final StringBuilder m_format_builder;
private final Formatter m_formatter;
private final Timeline.Period m_period;
private final Timeline.Window m_window;
private SparkPlayer m_player;
private ControlDispatcher m_dispatcher;
private boolean m_is_attached_to_window;
private boolean m_scrubbing;
private int m_show_timeout_ms;
private long m_hide_at_ms;
private long[] m_ad_group_times_ms;
private boolean[] m_played_ad_groups;
private final Runnable m_update_progress_action = new Runnable() {
    @Override
    public void run(){ updateProgress(); }
};
private final Runnable m_hide_action = new Runnable() {
    @Override
    public void run(){ hide(); }
};

public PlaybackControlView(Context context){
    this(context, null);
}

public PlaybackControlView(Context context, AttributeSet attrs){
    this(context, attrs, 0);
}

public PlaybackControlView(Context context, AttributeSet attrs,
    int defStyleAttr)
{
    super(context, attrs, defStyleAttr);
    LayoutInflater.from(context).inflate(R.layout.spark_player_controlbar, this);
    m_live_view = findViewById(R.id.live_control);
    m_duration_view = findViewById(R.id.spark_duration);
    m_position_view = findViewById(R.id.spark_position);
    m_timebar = findViewById(R.id.spark_progress);
    m_top_menu = findViewById(R.id.spark_player_menu_button);
    m_bottom_menu = findViewById(R.id.spark_player_gear_button);
    m_play_btn = findViewById(R.id.spark_play_button);
    m_pause_btn = findViewById(R.id.spark_pause_button);
    m_replay_btn = findViewById(R.id.spark_replay_button);
    m_fullscreen_btn = findViewById(R.id.spark_fullscreen_button);
    m_title = findViewById(R.id.spark_player_title);
    m_timebar_holder = findViewById(R.id.spark_timebar_holder);
    m_bottom_bar = findViewById(R.id.spark_bottom_bar);
    m_show_timeout_ms = DEFAULT_SHOW_TIMEOUT_MS;
    m_period = new Timeline.Period();
    m_window = new Timeline.Window();
    m_format_builder = new StringBuilder();
    m_formatter = new Formatter(m_format_builder, Locale.getDefault());
    m_ad_group_times_ms = new long[0];
    m_played_ad_groups = new boolean[0];
    m_listener = new ComponentListener();
    m_timebar.addListener(m_listener);
    m_play_btn.setOnClickListener(m_listener);
    m_pause_btn.setOnClickListener(m_listener);
    m_replay_btn.setOnClickListener(m_listener);
    m_top_menu.setOnClickListener(m_listener);
    m_bottom_menu.setOnClickListener(m_listener);
    m_fullscreen_btn.setOnClickListener(m_listener);
    m_dispatcher = new DefaultControlDispatcher();
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
}

public SparkPlayer getPlayer(){ return m_player; }

public void setPlayer(SparkPlayer player){
    if (m_player==player)
        return;
    if (m_player!=null)
        m_player.removeListener(m_listener);
    m_player = player;
    if (player!=null)
        player.addListener(m_listener);
    boolean in_bottom = m_player.get_config().bottom_settings_menu;
    m_top_menu.setVisibility(in_bottom ? GONE : VISIBLE);
    m_bottom_menu.setVisibility(in_bottom ? VISIBLE : GONE);
    updateAll();
}

public void setShowTimeoutMs(int timeout){ m_show_timeout_ms = timeout; }

public void show(){
    if (!isVisible())
    {
        setVisibility(VISIBLE);
        m_timebar.showPlayhead(true);
        updateAll();
    }
    hideAfterTimeout();
}

public void hide(){
    if (!isVisible())
        return;
    setVisibility(GONE);
    m_timebar.showPlayhead(false);
    removeCallbacks(m_hide_action);
    m_hide_at_ms = C.TIME_UNSET;
}

public boolean isVisible(){ return getVisibility()==VISIBLE; }

private void hideAfterTimeout(){
    removeCallbacks(m_hide_action);
    if (m_show_timeout_ms>0)
    {
        m_hide_at_ms = SystemClock.uptimeMillis() + m_show_timeout_ms;
        if (m_is_attached_to_window)
            postDelayed(m_hide_action, m_show_timeout_ms);
    }
    else
        m_hide_at_ms = C.TIME_UNSET;
}

private void updateAll(){
    updateViewsVisibility();
    updatePlaybackButtons();
    updateProgress();
    updateTitle();
    updateFullscreenButton();
    updateTimebarLayout();
}

private void updateViewsVisibility(){
    if (m_player==null)
        return;
    boolean live = m_player.isCurrentWindowDynamic();
    m_live_view.setVisibility(live ? VISIBLE : GONE);
    m_position_view.setVisibility(live ? GONE : VISIBLE);
    m_duration_view.setVisibility(live ? GONE : VISIBLE);
    m_timebar.setVisibility(live || m_player.isPlayingAd() ? GONE : VISIBLE);
}

private void updateTimebarLayout(){
    boolean is_ff = m_player.is_fullscreen();
    boolean at_bottom = !is_ff && !m_player.isCurrentWindowDynamic() &&
        m_player.get_config().bottom_edge_timebar;
    ViewGroup timebar_parent = (ViewGroup)m_timebar.getParent();
    LinearLayout.LayoutParams blp =
        (LinearLayout.LayoutParams)m_bottom_bar.getLayoutParams();
    if (at_bottom && timebar_parent==m_timebar_holder)
    {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = m_timebar.getBarHeight()/2-
            m_timebar.getTouchHeight()/2;
        timebar_parent.removeView(m_timebar);
        m_player.addView(m_timebar, m_player.getChildCount(), lp);
        blp.setMargins(0, 0, 0, Utils.dp2px(getContext(), 10));
        m_bottom_bar.setLayoutParams(blp);
    }
    else if (!at_bottom && timebar_parent!=m_timebar_holder)
    {
        timebar_parent.removeView(m_timebar);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        m_timebar_holder.addView(m_timebar, lp);
        blp.setMargins(0, 0, 0, 0);
        m_player.setPadding(0, 0, 0, 0);
        m_bottom_bar.setLayoutParams(blp);
    }
}

private void updatePlaybackButtons(){
    if (!m_is_attached_to_window || m_player==null)
        return;
    boolean ended = m_player.getPlaybackState()==Player.STATE_ENDED;
    if (ended && !m_player.is_floating())
        show();
    boolean playing = m_player.getPlayWhenReady();
    m_play_btn.setVisibility(!playing && !ended ? View.VISIBLE : View.GONE);
    m_pause_btn.setVisibility(playing && !ended ? View.VISIBLE: View.GONE);
    m_replay_btn.setVisibility(ended ? VISIBLE : GONE);
}

private void updateTitle(){
    if (m_player==null)
        return;
    m_title.setText(m_player.get_title());
    m_title.setVisibility(m_player.is_fullscreen() ? VISIBLE : GONE);
}

private void updateFullscreenButton(){
    if (m_player==null)
        return;
    m_fullscreen_btn.setImageResource(m_player.is_fullscreen() ?
        R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
}

public void updateAutoHide(){
    Player p = getPlayer();
    SparkModule wn = m_player.get_watch_next_ctrl();
    setAutoHide(p!=null && p.getPlayWhenReady() &&
        p.getPlaybackState()!=Player.STATE_ENDED &&
        (wn==null || (boolean)wn.get_state("auto_hide")));
}

public void setAutoHide(boolean val){
    setShowTimeoutMs(val ? DEFAULT_SHOW_TIMEOUT_MS : -1);
    if (isVisible())
        hideAfterTimeout();
}

private int updateAdGroups(Timeline timeline, Timeline.Window w){
    int ad_group_count = 0;
    long dur_us = w.durationUs;
    for (int i = w.firstPeriodIndex; i<=w.lastPeriodIndex; i++)
    {
        timeline.getPeriod(i, m_period);
        int count = m_period.getAdGroupCount();
        for (int j = 0; j<count; j++)
        {
            long time_in_period_us = m_period.getAdGroupTimeUs(j);
            if (time_in_period_us==C.TIME_END_OF_SOURCE)
            {
                if (m_period.durationUs==C.TIME_UNSET)
                    continue;
                time_in_period_us = m_period.durationUs;
            }
            long time_in_window_us = time_in_period_us +
                m_period.getPositionInWindowUs();
            if (time_in_window_us < 0 || time_in_window_us>dur_us)
                continue;
            if (ad_group_count==m_ad_group_times_ms.length)
            {
                int len = m_ad_group_times_ms.length==0 ? 1 :
                    m_ad_group_times_ms.length * 2;
                m_ad_group_times_ms = Arrays.copyOf(m_ad_group_times_ms, len);
                m_played_ad_groups = Arrays.copyOf(m_played_ad_groups, len);
            }
            m_ad_group_times_ms[ad_group_count] = C.usToMs(time_in_window_us);
            m_played_ad_groups[ad_group_count] = m_period.hasPlayedAdGroup(j);
            ad_group_count++;
        }
    }
    return ad_group_count;
}

private void updateProgress(){
    if (!m_is_attached_to_window)
        return;
    long pos_ms = 0;
    long buffered_pos_ms = 0;
    long dur_ms = 0;
    if (m_player!=null)
    {
        long dur_us = 0;
        int ad_group_count = 0;
        Timeline timeline = m_player.getCurrentTimeline();
        if (!timeline.isEmpty())
        {
            timeline.getWindow(m_player.getCurrentWindowIndex(), m_window);
            if (m_window.durationUs!=C.TIME_UNSET)
            {
                dur_us = m_window.durationUs;
                ad_group_count = updateAdGroups(timeline, m_window);
            }
        }
        dur_ms = C.usToMs(dur_us);
        buffered_pos_ms = pos_ms;
        if (m_player.isPlayingAd())
        {
            pos_ms += m_player.getContentPosition();
            buffered_pos_ms = pos_ms;
        }
        else
        {
            pos_ms += m_player.getCurrentPosition();
            buffered_pos_ms += m_player.getBufferedPosition();
        }
        m_timebar.setAdGroupTimesMs(m_ad_group_times_ms, m_played_ad_groups,
            ad_group_count);
    }
    m_duration_view.setText(Util.getStringForTime(m_format_builder,
        m_formatter, dur_ms));
    if (!m_scrubbing)
    {
        m_position_view.setText(Util.getStringForTime(m_format_builder,
            m_formatter, pos_ms));
    }
    m_timebar.setPosition(pos_ms);
    m_timebar.setBufferedPosition(buffered_pos_ms);
    m_timebar.setDuration(dur_ms);
    removeCallbacks(m_update_progress_action);
    int state = m_player==null ? Player.STATE_IDLE :
        m_player.getPlaybackState();
    if (state==Player.STATE_IDLE || state==Player.STATE_ENDED)
        return;
    long delay_ms = 1000;
    if (m_player.getPlayWhenReady() && state==Player.STATE_READY)
    {
        float speed = m_player.getPlaybackParameters().speed;
        if (speed<=0.1f)
            delay_ms = 1000;
        else if (speed <= 5f)
        {
            long update_period = 1000/Math.max(1, Math.round(1/speed));
            long media_time_delay = update_period-(pos_ms%update_period);
            if (media_time_delay < (update_period/5))
                media_time_delay += update_period;
            delay_ms = speed==1 ? media_time_delay :
                (long)(media_time_delay/speed);
        }
        else
            delay_ms = 200;
    }
    postDelayed(m_update_progress_action, delay_ms);
}

private void seekTo(int win, long pos){
    boolean dispatched = m_dispatcher.dispatchSeekTo(m_player, win, pos);
    if (dispatched)
        return;
    updateProgress();
}

private void seekToTimeBarPosition(long pos){
    seekTo(m_player.getCurrentWindowIndex(), pos);
}

@Override
public void onAttachedToWindow(){
    super.onAttachedToWindow();
    m_is_attached_to_window = true;
    if (m_hide_at_ms!=C.TIME_UNSET)
    {
        long delay = m_hide_at_ms-SystemClock.uptimeMillis();
        if (delay<=0)
            hide();
        else
            postDelayed(m_hide_action, delay);
    }
    updateAll();
}

@Override
public void onDetachedFromWindow(){
    super.onDetachedFromWindow();
    m_is_attached_to_window = false;
    removeCallbacks(m_update_progress_action);
    removeCallbacks(m_hide_action);
}

@Override
public boolean dispatchKeyEvent(KeyEvent event){
    return dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event);
}

public boolean dispatchMediaKeyEvent(KeyEvent event){
    int code = event.getKeyCode();
    if (m_player==null || !isHandledMediaKey(code))
        return false;
    if (event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)
    {
        switch (code)
        {
        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            m_dispatcher.dispatchSetPlayWhenReady(m_player,
                !m_player.getPlayWhenReady());
            break;
        case KeyEvent.KEYCODE_MEDIA_PLAY:
            m_dispatcher.dispatchSetPlayWhenReady(m_player, true);
            break;
        case KeyEvent.KEYCODE_MEDIA_PAUSE:
            m_dispatcher.dispatchSetPlayWhenReady(m_player, false);
            break;
        default:
            break;
        }
    }
    return true;
}

private static boolean isHandledMediaKey(int keyCode){
    return keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        || keyCode==KeyEvent.KEYCODE_MEDIA_PLAY
        || keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE;
}

private final class ComponentListener extends SparkPlayer.DefaultEventListener
    implements TimeBar.OnScrubListener, OnClickListener
{
    @Override
    public void onScrubStart(TimeBar timeBar, long position){
        removeCallbacks(m_hide_action);
        m_scrubbing = true;
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position){
        m_position_view.setText(Util.getStringForTime(m_format_builder,
            m_formatter, position));
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled){
        m_scrubbing = false;
        if (!canceled && m_player!=null)
            seekToTimeBarPosition(position);
        hideAfterTimeout();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int state){
        updatePlaybackButtons();
        updateProgress();
        updateAutoHide();
    }
    
    @Override
    public void onPositionDiscontinuity(int reason){ updateProgress(); }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest){
        updateViewsVisibility();
        updateProgress();
    }

    @Override
    public void onFullscreenChanged(boolean is_fullscreen){ updateAll(); }

    @Override
    public void onNewVideo(String url){ updateTitle(); }

    @Override
    public void onAdStart(){ updateViewsVisibility(); }

    @Override
    public void onAdEnd(){ updateViewsVisibility(); }

    @Override
    public void onClick(View view){
        hideAfterTimeout();
        if (m_player==null)
            return;
        if (m_play_btn==view)
            m_dispatcher.dispatchSetPlayWhenReady(m_player, true);
        else if (m_pause_btn==view)
            m_dispatcher.dispatchSetPlayWhenReady(m_player, false);
        else if (m_replay_btn==view)
        {
            m_player.seekTo(0);
            m_player.setPlayWhenReady(true);
        }
        else if (m_top_menu==view || m_bottom_menu==view)
            new SettingsDialog(getContext(), m_player).show();
        else if (m_fullscreen_btn==view)
            m_player.fullscreen(null);
    }
}
}
