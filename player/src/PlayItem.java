package com.spark.player;
public class PlayItem {
private final String m_ad_tag;
private final String m_media;
private final String m_poster;
private final String m_title;
private final int m_ad_timeout;
public PlayItem(String ad_tag, String media){
    this(ad_tag, media, null, null, 0); }
public PlayItem(String ad_tag, String media, String poster){
    this(ad_tag, media, poster, null, 0); }
public PlayItem(String ad_tag, String media, String poster, String title){
    this(ad_tag, media, poster, null, 0); }
public PlayItem(String ad_tag, String media, String poster, String title,
    int ad_timeout)
{
    m_ad_tag = ad_tag;
    m_media = media;
    m_poster = poster;
    m_title = title;
    m_ad_timeout = ad_timeout;
}
public String get_ad_tag(){ return m_ad_tag; }
public String get_media(){ return m_media; }
public String get_poster(){ return m_poster; }
public String get_title(){ return m_title; }
public int get_ad_timeout(){ return m_ad_timeout; }
}
