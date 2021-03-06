package com.spark.player.internal;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import java.text.DecimalFormat;
import java.util.Comparator;

class QualityItem {
TrackGroupArray m_groups;
int m_renderer_index;
int m_group_index;
int m_track_index;
Format m_format;
QualityItem(TrackGroupArray groups, int renderer_index, int group_index, int track_index){
    m_groups = groups;
    m_renderer_index = renderer_index;
    m_group_index = group_index;
    m_track_index = track_index;
    m_format = m_groups.get(m_group_index).getFormat(m_track_index);
}
private String scale_number(double n){
    int k = 1024;
    String[] sizes = {"", "K", "M", "G", "T", "P"};
    int i = (int) (Math.log(n)/Math.log(k));
    n /= Math.pow(k, i);
    if (n<0.001)
        return "0";
    if (n>=k-1)
        n = Math.floor(n);
    DecimalFormat df = new DecimalFormat(n<1 ? "0.###" : n<10 ? "0.##" : n<100 ? "0.#" : "0" );
    return df.format(n)+sizes[i];
}
public boolean equals(QualityItem item){
    return item!=null && m_groups==item.m_groups &&
        m_renderer_index==item.m_renderer_index &&
        m_group_index==item.m_group_index &&
        m_track_index==item.m_track_index;
}
public String toString(){
    return m_format.height!=Format.NO_VALUE ? m_format.height+"p" :
        scale_number(m_format.bitrate)+"bps";
}
public static class BitrateComparator implements Comparator<QualityItem> {
    public int compare(QualityItem a, QualityItem b){
        return a.m_format.bitrate-b.m_format.bitrate;
    }
}
}
