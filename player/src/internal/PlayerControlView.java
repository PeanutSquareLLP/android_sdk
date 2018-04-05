package com.spark.player.internal;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.spark.player.R;
import com.spark.player.SparkModule;
import com.spark.player.SparkPlayer;
import com.spark.player.SparkPlayerAPI;
public class PlayerControlView extends PlaybackControlView {
private ExoPlayerController m_controller;
private SparkPlayer m_spark_player;
private Player.DefaultEventListener m_player_listener;
private View m_live_control;
private View m_position;
private View m_duration;
private DefaultTimeBar m_timebar;
private ImageButton m_top_menu;
private ImageButton m_bottom_menu;
private ImageButton m_fullscreen_btn;
private ImageButton m_play_btn;
private ImageButton m_pause_btn;
private ImageButton m_replay_btn;
private TextView m_title;
public PlayerControlView(Context context){
    this(context, null);
}

public PlayerControlView(Context context, AttributeSet attrs){
    this(context, attrs, 0);
}

public PlayerControlView(Context context, AttributeSet attrs, int defStyleAttr){
    this(context, attrs, defStyleAttr, attrs);
}

public PlayerControlView(Context context, AttributeSet attrs, int defStyleAttr,
    AttributeSet playbackAttrs){
    super(context, attrs, defStyleAttr, playbackAttrs);
    m_live_control = findViewById(R.id.live_control);
    m_position = findViewById(R.id.exo_position);
    m_duration = findViewById(R.id.exo_duration);
    m_timebar = findViewById(R.id.exo_progress);
    m_top_menu = findViewById(R.id.spark_player_menu_button);
    m_bottom_menu = findViewById(R.id.spark_player_gear_button);
    m_play_btn = findViewById(R.id.spark_play_button);
    m_pause_btn = findViewById(R.id.spark_pause_button);
    m_replay_btn = findViewById(R.id.spark_replay_button);
    m_fullscreen_btn = findViewById(R.id.spark_fullscreen_button);
    m_title = findViewById(R.id.spark_player_title);
    OnClickListener on_click = new OnClickListener() {
        @Override
        public void onClick(View v){
            new SettingsDialog(getContext(), m_spark_player, m_controller)
                .show();
        }
    };
    m_top_menu.setOnClickListener(on_click);
    m_bottom_menu.setOnClickListener(on_click);
    m_fullscreen_btn.setOnClickListener(new OnClickListener(){
        @Override
        public void onClick(View v){
            if (m_spark_player!=null)
                m_spark_player.fullscreen(null);
        }
    });
    m_player_listener = new Player.DefaultEventListener() {
        @Override
        public void onPlayerStateChanged(boolean playing, int state){
            update_auto_hide();
            update_playback_buttons();
        }
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest){
            boolean live = getPlayer().isCurrentWindowDynamic();
            m_live_control.setVisibility(live ? VISIBLE : GONE);
            m_position.setVisibility(live ? GONE : VISIBLE);
            m_duration.setVisibility(live ? GONE : VISIBLE);
            m_timebar.setVisibility(live ? GONE : VISIBLE);
        }
    };
    OnClickListener button_click_listener = new OnClickListener() {
        @Override
        public void onClick(View v){
            Player p = getPlayer();
            if (v == m_play_btn)
                p.setPlayWhenReady(true);
            else if (v == m_pause_btn)
                p.setPlayWhenReady(false);
            else if (v == m_replay_btn)
            {
                p.seekTo(0);
                p.setPlayWhenReady(true);
            }
        }
    };
    m_play_btn.setOnClickListener(button_click_listener);
    m_pause_btn.setOnClickListener(button_click_listener);
    m_replay_btn.setOnClickListener(button_click_listener);
    update_playback_buttons();
}

@Override
public void setPlayer(Player player) {
    Player old = getPlayer();
    super.setPlayer(player);
    if (old==player)
        return;
    if (old!=null)
        old.removeListener(m_player_listener);
    if (player!=null)
        player.addListener(m_player_listener);
    update_playback_buttons();
}

private void update_playback_buttons(){
    Player p = getPlayer();
    boolean playing = p!=null && p.getPlayWhenReady();
    boolean ended = p!=null && p.getPlaybackState()==Player.STATE_ENDED;
    if (ended && !m_spark_player.is_floating())
        show();
    m_play_btn.setVisibility(!playing && !ended ? VISIBLE : GONE);
    m_pause_btn.setVisibility(playing && !ended ? VISIBLE : GONE);
    m_replay_btn.setVisibility(ended ? VISIBLE : GONE);
}

private void update_title(){
    m_title.setText(m_spark_player.get_title());
    m_title.setVisibility(m_spark_player.is_fullscreen() ? VISIBLE : GONE);
}

private void update_fullscreen_button(){
    m_fullscreen_btn.setImageResource(m_spark_player.is_fullscreen() ?
        R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
}

public void update_auto_hide(){
    Player p = getPlayer();
    SparkModule wn = m_spark_player.get_watch_next_ctrl();
    set_auto_hide(p!=null && p.getPlayWhenReady() &&
        p.getPlaybackState()!=Player.STATE_ENDED &&
        (wn==null || (boolean)wn.get_state("auto_hide")));
}

public void set_auto_hide(boolean val){
    setShowTimeoutMs(val ? DEFAULT_SHOW_TIMEOUT_MS : -1);
    if (isVisible())
        show();
}

public void set_player_controller(ExoPlayerController controller){
    m_controller = controller;
    m_controller.add_event_listener(
        new ExoPlayerController.DefaultVideoEventListener() {
            @Override
            public void on_new_video(String url){ update_title(); }
        });
}

public void set_spark_player(SparkPlayer player){
    m_spark_player = player;
    boolean in_bottom = m_spark_player.get_config().m_bottom_settings_menu;
    m_top_menu.setVisibility(in_bottom ? GONE : VISIBLE);
    m_bottom_menu.setVisibility(in_bottom ? VISIBLE : GONE);
    m_spark_player.add_listener(new SparkPlayerAPI.DefaultEventListener() {
        @Override
        public void on_fullscreen_changed(boolean is_fullscreen){
            update_fullscreen_button();
            update_title();
        }
    });
    update_fullscreen_button();
    update_title();
}
}
