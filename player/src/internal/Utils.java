package com.spark.player.internal;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.util.TypedValue;

import java.lang.reflect.Field;
public class Utils {
public static int dp2px(Context context, int dp){
    Resources r = context.getResources();
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
        r.getDisplayMetrics());
}

public static String fix_url(String url){
    return url!=null && url.startsWith("//") ? "https:"+url : url;
}
public static Activity get_activity(Context context){
    if (context==null)
        return null;
    if (context instanceof Activity)
        return (Activity)context;
    if (context instanceof ContextWrapper)
        return get_activity(((ContextWrapper)context).getBaseContext());
    return null;
}
static final Field find_field(Object obj, Class<?> type){
    Class<?> obj_class = obj.getClass();
    Field field = null;
    do {
        for (Field f: obj_class.getDeclaredFields())
        {
            if (f.getType().isAssignableFrom(type))
            {
                field = f;
                break;
            }
        }
    } while (field==null && (obj_class = obj_class.getSuperclass()) != null);
    return field;
}
static final Object get_field(Object obj, Class<?> type){
    Field field = find_field(obj, type);
    if (field==null)
        return null;
    try {
        field.setAccessible(true);
        return field.get(obj);
    } catch(IllegalAccessException e){ e.printStackTrace(); }
    return null;
}
}
