package com.spark.player.internal;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import com.google.android.exoplayer2.PlaybackParameters;
import com.spark.player.BuildConfig;
import com.spark.player.R;
import com.spark.player.SparkPlayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SettingsDialog extends BottomSheetDialog {
private static String TEXT = "text";
private static String IMAGE = "image";
private static String[] FROM = {TEXT, IMAGE};
private static int[] TO = {R.id.spark_settings_item_text,
    R.id.spark_settings_item_icon};
private Map<Float, String> speed_map;
private SparkPlayer m_player;
private ExoPlayerController m_controller;
private ListView m_list_view;

SettingsDialog(@NonNull Context context, SparkPlayer player){
    super(context);
    m_player = player;
    m_controller = player.get_controller();
    setContentView(R.layout.spark_settings_dialog);
    m_list_view = findViewById(R.id.spark_settings_list);
    TextView about = findViewById(R.id.powered_by);
    about.setText(context.getString(R.string.powered_by,
        BuildConfig.VERSION_NAME));
    speed_map = new HashMap<>();
    speed_map.put(0.25f, "0.25x");
    speed_map.put(0.5f, "0.5x");
    speed_map.put(0.75f, "0.75x");
    speed_map.put(1f, context.getString(R.string.normal));
    speed_map.put(1.25f, "1.25x");
    speed_map.put(1.5f, "1.5x");
    speed_map.put(2f, "2x");
    expand_on_show(this);
    init_main_menu();
}

private void expand_on_show(BottomSheetDialog dialog){
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        @Override
        public void onShow(DialogInterface dialog) {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            FrameLayout view = d.findViewById(
                android.support.design.R.id.design_bottom_sheet);
            if (view==null)
                return;
            BottomSheetBehavior.from(view)
                .setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    });
}

private void init_main_menu(){
    final MenuAdapter adapter = new MenuAdapter(getContext());
    m_list_view.setAdapter(new MenuAdapter(getContext()));
    m_list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view,
            int position, long id)
        {
            hide();
            if (adapter.m_texts.get(position)==R.string.quality)
                show_quality_dialog();
            else if (adapter.m_texts.get(position)==R.string.playback_speed)
                show_speed_dialog();
        }
    });
}

private void show_quality_dialog(){
    Context context = getContext();
    final List<QualityItem> items = m_controller.get_quality_items();
    QualityItem selected = m_controller.get_selected_quality();
    Collections.sort(items, new QualityItem.BitrateComparator());
    ArrayList<Map<String, Object>> data = new ArrayList<>();
    Map<String, Object> m = new HashMap<>();
    m.put(TEXT, context.getResources().getString(R.string.auto));
    m.put(IMAGE, selected==null ? R.drawable.ic_check : null);
    data.add(m);
    for (QualityItem item: items)
    {
        m = new HashMap<>();
        m.put(TEXT, item.toString());
        m.put(IMAGE, item.equals(selected) ? R.drawable.ic_check : null);
        data.add(m);
    }
    ListView list = new ListView(context);
    SimpleAdapter adapter = new SimpleAdapter(context, data,
        R.layout.spark_settings_item, FROM, TO);
    list.setAdapter(adapter);
    final BottomSheetDialog dialog = show_list_dialog(list);
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view,
            int position, long id)
        {
            m_controller.set_quality(position==0 ? null :
                items.get(position-1));
            dialog.hide();
        }
    });
}

private void show_speed_dialog(){
    Context context = getContext();
    final Float[] rates = speed_map.keySet().toArray(new Float[0]);
    Arrays.sort(rates);
    ArrayList<Map<String, Object>> data = new ArrayList<>();
    float current = m_player.getPlaybackParameters().speed;
    for (Float rate : rates)
    {
        Map<String, Object> m = new HashMap<>();
        m.put(TEXT, speed_map.get(rate));
        m.put(IMAGE, rate==current ? R.drawable.ic_check : null);
        data.add(m);
    }
    ListView list = new ListView(context);
    SimpleAdapter adapter = new SimpleAdapter(context, data,
        R.layout.spark_settings_item, FROM, TO);
    list.setAdapter(adapter);
    final BottomSheetDialog dialog = show_list_dialog(list);
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view,
            int position, long id)
        {
            m_player.setPlaybackParameters(new PlaybackParameters(
                rates[position], rates[position]));
            dialog.hide();
        }
    });
}

private BottomSheetDialog show_list_dialog(ListView list){
    final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
    dialog.setContentView(list);
    expand_on_show(dialog);
    dialog.show();
    return dialog;
}

private class MenuAdapter extends BaseAdapter {
    Context m_context;
    ArrayList<Integer> m_texts = new ArrayList<>();
    ArrayList<Integer> m_images = new ArrayList<>();
    ArrayList<String> m_labels = new ArrayList<>();
    LayoutInflater m_inflater;

    MenuAdapter(Context context){
        super();
        m_inflater = (LayoutInflater)context.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        m_context = context;
        List<QualityItem> items = m_controller.get_quality_items();
        if (items.size()>1)
        {
            m_texts.add(R.string.quality);
            m_images.add(R.drawable.ic_gear_dark);
            QualityItem cur_quality = m_controller.get_selected_quality();
            m_labels.add(cur_quality==null ?
                context.getResources().getString(R.string.auto) :
                cur_quality.toString());

        }
        m_texts.add(R.string.playback_speed);
        m_images.add(R.drawable.ic_speed);
        m_labels.add(speed_map.get(m_player.getPlaybackParameters().speed));
    }

    @Override
    public View getView(int position, View row, ViewGroup parent){
        if(row==null)
        {
            row = m_inflater.inflate(R.layout.spark_settings_item, parent,
                false);
        }
        TextView text = row.findViewById(R.id.spark_settings_item_text);
        ImageView image = row.findViewById(R.id.spark_settings_item_icon);
        TextView label_text =
            row.findViewById(R.id.spark_settings_item_label_text);
        View label = row.findViewById(R.id.spark_settings_item_label);
        text.setText(m_texts.get(position));
        image.setImageResource(m_images.get(position));
        if (m_labels.get(position)!=null)
        {
            label.setVisibility(View.VISIBLE);
            label_text.setText(m_labels.get(position));
        }
        return row;
    }

    @Override
    public int getCount(){ return m_texts.size(); }

    @Override
    public Object getItem(int position){ return m_texts.get(position); }

    @Override
    public long getItemId(int position){ return position; }
}
}
