package hr.fer.zpr.igor.sensorreader;

import android.content.Context;
import android.hardware.Sensor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Igor on 6/4/2016.
 */
public class SensorListAdapter extends ArrayAdapter {

    private int resource;
    private LayoutInflater inflater;
    private Context context;
    private List<Sensor> sensors;

    public SensorListAdapter (Context context, int id, List<Sensor> sensors) {
        super(context, 0, sensors);
        resource = id;
        inflater = LayoutInflater.from( context );
        this.context=context;
        this.sensors = sensors;
    }

    @Override
    public View getView ( int position, View convertView, ViewGroup parent ) {

        View view = LayoutInflater.from(context).inflate(R.layout.item_sensor_list, parent, false);
        Sensor sensor = sensors.get(position);

        TextView tvAuthor = (TextView) view.findViewById(R.id.sensor_name);
        tvAuthor.setText(sensor.getName());

        return view;
    }
}
