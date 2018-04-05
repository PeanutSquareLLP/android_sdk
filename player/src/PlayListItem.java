package com.spark.player;
import com.spark.player.internal.Utils;

public class PlayListItem {
private String m_video_url;
private String m_poster_url;
private String m_title;

public PlayListItem(String video_url, String poster_url, String title){
    m_video_url = Utils.fix_url(video_url);
    m_poster_url = Utils.fix_url(poster_url);
    m_title = title;
}

public String get_poster_url(){ return m_poster_url; }
public String get_title(){ return m_title; }
public String get_video_url(){ return m_video_url; }
}
