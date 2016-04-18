package hr.fer.zpr.igor.sensorreader;

import android.content.Context;
import android.hardware.Sensor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ResolutionListAdapter extends ArrayAdapter {

    private int resource;
    private LayoutInflater inflater;
    private Context context;
    private List<Integer> resolutions;

    public ResolutionListAdapter (Context context, int id, List<Integer> resolutions) {
        super(context, 0, resolutions);
        resource = id;
        inflater = LayoutInflater.from( context );
        this.context=context;
        this.resolutions = resolutions;
    }

    @Override
    public View getView ( int position, View convertView, ViewGroup parent ) {

        View view = LayoutInflater.from(context).inflate(R.layout.item_sensor_list, parent, false);
        Integer resolution = resolutions.get(position);

        TextView tvAuthor = (TextView) view.findViewById(R.id.sensor_name);
        tvAuthor.setText(resolution +"");

        return view;
    }
}
