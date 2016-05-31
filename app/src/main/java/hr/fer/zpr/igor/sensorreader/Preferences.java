package hr.fer.zpr.igor.sensorreader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    public static void setCustomString(String key, String preference, Activity activity){

        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, preference);
        editor.apply();
    }

    public static String getCustomString(String key, Activity activity){
        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(key, "");
    }

    public static String getId (Activity activity){
       return getCustomString("uid", activity);
    }

    public static void setId (String id, Activity activity){
        setCustomString("uid", id, activity);
    }
}
