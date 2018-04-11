package com.spark.player.internal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.spark.player.R;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

public class SparkTimeBar extends View implements TimeBar {

public static final int BAR_HEIGHT_DP = 2;
public static final int TOUCH_TARGET_HEIGHT_DP = 26;
public static final int AD_MARKER_WIDTH_DP = 4;
public static final int SCRUBBER_ENABLED_SIZE_DP = 12;
public static final int SCRUBBER_DISABLED_SIZE_DP = 0;
public static final int SCRUBBER_DRAGGED_SIZE_DP = 20;
private static final int FINE_SCRUB_Y_THRESHOLD_DP = -50;
private static final int FINE_SCRUB_RATIO = 3;
private final Rect m_seek_bounds;
private final Rect m_progress_bar;
private final Rect m_buffered_bar;
private final Rect m_scrub_bar;
private final Rect m_touch_delegate_bounds;
private final Paint m_played_paint;
private final Paint m_buffered_paint;
private final Paint m_unplayed_paint;
private final Paint m_ad_marker_paint;
private final Paint m_played_ad_marker_paint;
private final Paint m_scrubber_paint;
private final int m_bar_height;
private final int m_touch_height;
private final int m_ad_marker_width;
private final int m_scrubber_enabled_size;
private final int m_scrubber_disabled_size;
private final int m_scrubber_dragged_size;
private final int m_fine_scrub_y_threshold;
private final StringBuilder m_format_builder;
private final Formatter m_formatter;
private final CopyOnWriteArraySet<OnScrubListener> m_listeners;
private int m_last_coarse_scrub_x_position;
private int[] m_location_on_screen;
private Point m_touch_position;
private boolean m_scrubbing;
private boolean m_playhead_visible;
private long m_scrub_position;
private long m_duration;
private long m_position;
private long m_buffered_position;
private int m_ad_group_count;
private long[] m_ad_group_times_ms;
private boolean[] m_played_ad_groups;
private ViewGroup m_overlay_parent;
private ShapeDrawable m_playhead;

public SparkTimeBar(Context context, AttributeSet attrs){
    super(context, attrs);
    m_seek_bounds = new Rect();
    m_progress_bar = new Rect();
    m_buffered_bar = new Rect();
    m_scrub_bar = new Rect();
    m_touch_delegate_bounds = new Rect();
    m_played_paint = new Paint();
    m_buffered_paint = new Paint();
    m_unplayed_paint = new Paint();
    m_ad_marker_paint = new Paint();
    m_played_ad_marker_paint = new Paint();
    m_scrubber_paint = new Paint();
    m_scrubber_paint.setAntiAlias(true);
    m_listeners = new CopyOnWriteArraySet<>();
    Resources res = context.getResources();
    DisplayMetrics dm = res.getDisplayMetrics();
    m_fine_scrub_y_threshold = dpToPx(dm, FINE_SCRUB_Y_THRESHOLD_DP);
    m_bar_height = dpToPx(dm, BAR_HEIGHT_DP);
    m_touch_height = dpToPx(dm, TOUCH_TARGET_HEIGHT_DP);
    m_ad_marker_width = dpToPx(dm, AD_MARKER_WIDTH_DP);
    m_scrubber_enabled_size = dpToPx(dm, SCRUBBER_ENABLED_SIZE_DP);
    m_scrubber_disabled_size = dpToPx(dm, SCRUBBER_DISABLED_SIZE_DP);
    m_scrubber_dragged_size = dpToPx(dm, SCRUBBER_DRAGGED_SIZE_DP);
    m_played_paint.setColor(res.getColor(R.color.played));
    m_scrubber_paint.setColor(res.getColor(R.color.played));
    m_buffered_paint.setColor(res.getColor(R.color.buffered));
    m_unplayed_paint.setColor(res.getColor(R.color.unplayed));
    m_ad_marker_paint.setColor(res.getColor(R.color.ad_marker));
    m_played_ad_marker_paint.setColor(res.getColor(R.color.played_ad_marker));
    m_format_builder = new StringBuilder();
    m_formatter = new Formatter(m_format_builder, Locale.getDefault());
    m_duration = C.TIME_UNSET;
    setFocusable(true);
}

public int getBarHeight(){ return m_bar_height; }

public int getTouchHeight(){ return m_touch_height; }

@Override
public void addListener(OnScrubListener listener){
    m_listeners.add(listener);
}

@Override
public void removeListener(OnScrubListener listener){
    m_listeners.remove(listener);
}

@Override
public void setKeyTimeIncrement(long time){}

@Override
public void setKeyCountIncrement(int count){}

@Override
public void setPosition(long position){
    m_position = position;
    setContentDescription(Util.getStringForTime(m_format_builder, m_formatter,
        position));
    update();
}

@Override
public void setBufferedPosition(long bufferedPosition){
    m_buffered_position = bufferedPosition;
    update();
}

@Override
public void setDuration(long duration){
    m_duration = duration;
    if (m_scrubbing && duration==C.TIME_UNSET)
        stopScrubbing(true);
    update();
}

@Override
public void setAdGroupTimesMs(@Nullable long[] adGroupTimesMs,
    @Nullable boolean[] playedAdGroups, int adGroupCount)
{
    Assertions.checkArgument(adGroupCount==0 || (adGroupTimesMs!=null &&
        playedAdGroups!=null));
    m_ad_group_count = adGroupCount;
    m_ad_group_times_ms = adGroupTimesMs;
    m_played_ad_groups = playedAdGroups;
    update();
}

@Override
public void setEnabled(boolean enabled){
    super.setEnabled(enabled);
    if (m_scrubbing && !enabled)
        stopScrubbing(true);
}

@Override
public void onDraw(Canvas canvas){
    canvas.save();
    drawTimeBar(canvas);
    canvas.restore();
    drawPlayhead();
}

@Override
public boolean onTouchEvent(MotionEvent event){
    if (!isEnabled() || m_duration<=0 || !m_playhead_visible)
        return false;
    Point touchPosition = resolveRelativeTouchPosition(event);
    int x = touchPosition.x;
    int y = touchPosition.y;
    switch (event.getAction())
    {
    case MotionEvent.ACTION_DOWN:
        if (m_seek_bounds.contains(x, y))
        {
            startScrubbing();
            positionScrubber(x);
            m_scrub_position = getScrubberPosition();
            update();
            invalidate();
            return true;
        }
        break;
    case MotionEvent.ACTION_MOVE:
        if (m_scrubbing)
        {
            if (y<m_fine_scrub_y_threshold)
            {
                int relativeX = x - m_last_coarse_scrub_x_position;
                positionScrubber(m_last_coarse_scrub_x_position +
                    relativeX / FINE_SCRUB_RATIO);
            }
            else
            {
                m_last_coarse_scrub_x_position = x;
                positionScrubber(x);
            }
            m_scrub_position = getScrubberPosition();
            for (OnScrubListener listener : m_listeners)
                listener.onScrubMove(this, m_scrub_position);
            update();
            invalidate();
            return true;
        }
        break;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:
        if (m_scrubbing)
        {
            stopScrubbing(event.getAction()==MotionEvent.ACTION_CANCEL);
            return true;
        }
        break;
    }
    return false;
}

@Override
protected void onDetachedFromWindow(){
    super.onDetachedFromWindow();
    setOverlayParent(null);
}

@Override
protected void onMeasure(int width_spec, int height_spec){
    int height_mode = MeasureSpec.getMode(height_spec);
    int height_size = MeasureSpec.getSize(height_spec);
    int height = height_mode==MeasureSpec.UNSPECIFIED ? m_touch_height:
        height_mode==MeasureSpec.EXACTLY ? height_size :
        Math.min(m_touch_height, height_size);
    setMeasuredDimension(MeasureSpec.getSize(width_spec), height);
}

@Override
protected void onLayout(boolean changed, int left, int top, int right,
    int bottom)
{
    int width = right-left;
    int height = bottom-top;
    int bar_y = (height-m_touch_height)/2;
    int seek_left = getPaddingLeft();
    int seek_right = width-getPaddingRight();
    int progress_y = bar_y+(m_touch_height-m_bar_height)/2;
    m_seek_bounds.set(seek_left, bar_y, seek_right, bar_y+m_touch_height);
    m_progress_bar.set(m_seek_bounds.left, progress_y, m_seek_bounds.right,
        progress_y+m_bar_height);
    if (changed)
        findOverflowParent(top, bottom);
    if (changed && m_overlay_parent!=null)
    {
        m_touch_delegate_bounds.set(m_seek_bounds);
        m_overlay_parent.offsetDescendantRectToMyCoords(this,
            m_touch_delegate_bounds);
        m_overlay_parent.setTouchDelegate(
            new TouchDelegate(m_touch_delegate_bounds, this));
    }
    update();
}

private void findOverflowParent(int top, int bottom){
    bottom += (m_touch_height-(bottom-top))/2;
    ViewParent parent = getParent();
    while (parent!= null && parent instanceof ViewGroup &&
        bottom > ((ViewGroup)parent).getHeight())
    {
        bottom += ((ViewGroup)parent).getBottom();
        parent = parent.getParent();
    }
    if (m_overlay_parent!=parent)
        setOverlayParent(parent);
}

private void setOverlayParent(ViewParent parent){
    if (m_playhead!=null && m_overlay_parent!=null)
    {
        m_overlay_parent.getOverlay().remove(m_playhead);
        m_playhead = null;
    }
    if (m_overlay_parent!=null)
        m_overlay_parent.setTouchDelegate(null);
    m_overlay_parent = parent instanceof ViewGroup ? (ViewGroup)parent : null;
}

private void startScrubbing(){
    m_scrubbing = true;
    setPressed(true);
    ViewParent parent = getParent();
    if (parent!=null)
        parent.requestDisallowInterceptTouchEvent(true);
    for (OnScrubListener listener : m_listeners)
        listener.onScrubStart(this, getScrubberPosition());
}

private void stopScrubbing(boolean canceled){
    m_scrubbing = false;
    setPressed(false);
    ViewParent parent = getParent();
    if (parent!=null)
        parent.requestDisallowInterceptTouchEvent(false);
    invalidate();
    for (OnScrubListener listener : m_listeners)
        listener.onScrubStop(this, getScrubberPosition(), canceled);
}

private void update(){
    m_buffered_bar.set(m_progress_bar);
    m_scrub_bar.set(m_progress_bar);
    long new_scrub_time = m_scrubbing ? m_scrub_position : m_position;
    if (m_duration>0)
    {
        int buff_width = (int)((m_progress_bar.width() * m_buffered_position)/
            m_duration);
        m_buffered_bar.right = Math.min(m_progress_bar.left + buff_width,
            m_progress_bar.right);
        int scrub_pos = (int)((m_progress_bar.width() * new_scrub_time)/
            m_duration);
        m_scrub_bar.right = Math.min(m_progress_bar.left + scrub_pos,
            m_progress_bar.right);
    }
    else
    {
        m_buffered_bar.right = m_progress_bar.left;
        m_scrub_bar.right = m_progress_bar.left;
    }
    invalidate(m_seek_bounds);
}

private void positionScrubber(float x){
    m_scrub_bar.right = Util.constrainValue((int)x, m_progress_bar.left,
        m_progress_bar.right);
}

private Point resolveRelativeTouchPosition(MotionEvent e){
    if (m_location_on_screen==null)
    {
        m_location_on_screen = new int[2];
        m_touch_position = new Point();
    }
    getLocationOnScreen(m_location_on_screen);
    m_touch_position.set( ((int)e.getRawX()) - m_location_on_screen[0],
        ((int)e.getRawY()) - m_location_on_screen[1]);
    return m_touch_position;
}

private long getScrubberPosition(){
    if (m_progress_bar.width()<=0 || m_duration==C.TIME_UNSET)
        return 0;
    return (m_scrub_bar.width() * m_duration) / m_progress_bar.width();
}

private void drawTimeBar(Canvas canvas){
    int height = m_progress_bar.height();
    int top = m_progress_bar.centerY()-height/2;
    int bottom = top+height;
    if (m_duration<=0)
    {
        canvas.drawRect(m_progress_bar.left, top, m_progress_bar.right,
            bottom, m_unplayed_paint);
        return;
    }
    int buff_left = m_buffered_bar.left;
    int buff_rigth = m_buffered_bar.right;
    int progress_left = Math.max(Math.max(m_progress_bar.left, buff_rigth),
        m_scrub_bar.right);
    if (progress_left<m_progress_bar.right)
    {
        canvas.drawRect(progress_left, top, m_progress_bar.right, bottom,
            m_unplayed_paint);
    }
    buff_left = Math.max(buff_left, m_scrub_bar.right);
    if (buff_rigth>buff_left)
        canvas.drawRect(buff_left, top, buff_rigth, bottom, m_buffered_paint);
    if (m_scrub_bar.width()>0)
    {
        canvas.drawRect(m_scrub_bar.left, top, m_scrub_bar.right, bottom,
            m_played_paint);
    }
    for (int i = 0; i<m_ad_group_count; i++)
    {
        long ad_time = Util.constrainValue(m_ad_group_times_ms[i], 0,
            m_duration);
        int markerPositionOffset = (int)(m_progress_bar.width()*ad_time/
            m_duration) - m_ad_marker_width/2;
        int marker_left = m_progress_bar.left+Math.min(m_progress_bar.width()-
                m_ad_marker_width, Math.max(0, markerPositionOffset));
        Paint paint = m_played_ad_groups[i] ? m_played_ad_marker_paint :
            m_ad_marker_paint;
        canvas.drawRect(marker_left, top, marker_left+m_ad_marker_width,
            bottom, paint);
    }
}

public void showPlayhead(boolean visible){
    if (m_playhead_visible==visible)
        return;
    if (visible)
    {
        m_playhead_visible = true;
        drawPlayhead();
        return;
    }
    m_playhead_visible = false;
    if (m_playhead!=null && m_overlay_parent!=null)
        m_overlay_parent.getOverlay().remove(m_playhead);
    m_playhead = null;
}

private void drawPlayhead(){
    if (m_duration<=0 || m_overlay_parent==null || !m_playhead_visible ||
        !isShown())
    {
        return;
    }
    int size = (m_scrubbing || isFocused()) ? m_scrubber_dragged_size
        : (isEnabled() ? m_scrubber_enabled_size : m_scrubber_disabled_size);
    int radius = size/2;
    int x = Util.constrainValue(m_scrub_bar.right, m_scrub_bar.left+radius,
        m_progress_bar.right-radius);
    int y = m_scrub_bar.centerY();
    if (m_playhead==null)
    {
        m_playhead = new ShapeDrawable(new OvalShape());
        m_playhead.getPaint().set(m_scrubber_paint);
        m_overlay_parent.getOverlay().add(m_playhead);
    }
    final Rect r = new Rect(x-radius, y-radius, x+radius, y+radius);
    m_overlay_parent.offsetDescendantRectToMyCoords(this, r);
    m_playhead.setBounds(r);
}

private static int dpToPx(DisplayMetrics displayMetrics, int dps){
    return (int)(dps * displayMetrics.density + 0.5f);
}
}
