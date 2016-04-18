package hr.fer.zpr.igor.sensorreader;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Integer selectedResolution;
    private Sensor selectedSensor;
    private Sensor activeSensor;
    private SensorEvent currentSensorData;
    private LineChart chart1;
    private LineChart chart2;
    private LineChart chart3;
    private Handler handler;
    private RelativeLayout sensorSelectButton;
    private RelativeLayout resolutionSelectButton;
    private TextView selectedSensorText;
    private TextView selectedResolutionText;
    private SensorListAdapter sensorListAdapter;
    List<Sensor> deviceSensors;
    private ResolutionListAdapter resolutionListAdapter;
    List<Integer> resolutions;
    List<View> graphViews;

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("http://192.168.1.2:3000");
        } catch (URISyntaxException e) {}
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        populateResolutionList();

        mSocket.on("new message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Toast.makeText(MainActivity.this, "message recieved", Toast.LENGTH_SHORT).show();
            }
        });
        mSocket.connect();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        sensorSelectButton = (RelativeLayout) findViewById(R.id.sensor_select_layout);
        sensorSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                sensorListAdapter = new SensorListAdapter(MainActivity.this, 0, deviceSensors);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select sensor:");
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builder.setAdapter(sensorListAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedSensor = (Sensor) sensorListAdapter.getItem(which);
                        selectedSensorText.setText(selectedSensor.getName());
                        restart();
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        });

        resolutionSelectButton = (RelativeLayout) findViewById(R.id.resolution_select_layout);
        resolutionSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                resolutionListAdapter = new ResolutionListAdapter(MainActivity.this, 0, resolutions);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select resolution:");
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builder.setAdapter(resolutionListAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedResolution = (int) resolutionListAdapter.getItem(which);
                        selectedResolutionText.setText(selectedResolution + "");
                        restart();
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        });

        selectedSensorText = (TextView) findViewById(R.id.sensor_select_selected);
        selectedResolutionText = (TextView) findViewById(R.id.resolution_select_selected);

        initCharts();
        selectedSensor = deviceSensors.get(0);
        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());
        selectedResolution = 500;
        selectedSensorText.setText(selectedSensor.getName());
        selectedResolutionText.setText(selectedResolution + "");
        handler = new Handler();
        handler.postDelayed(sensorDataTimer, 500);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocket.disconnect();
    }

    private void initCharts() {
        //start: chart1

        //TODO popis senzora s brojem vrijednosti koje vracaju + inflate taj broj grafova

        chart1 = (LineChart) findViewById(R.id.line_chart1);
        chart1.setDragEnabled(true);
        chart1.setScaleEnabled(true);
        chart1.setPinchZoom(true);
        chart1.setBackgroundColor(Color.DKGRAY);

        LineData data1 = new LineData();
        data1.setValueTextColor(Color.WHITE);
        chart1.setData(data1);

        Legend legend1 = chart1.getLegend();
        legend1.setForm(Legend.LegendForm.LINE);
        legend1.setTextColor(Color.WHITE);

        XAxis xAxis1 = chart1.getXAxis();
        xAxis1.setTextColor(Color.WHITE);
        xAxis1.setDrawGridLines(false);
        xAxis1.setAvoidFirstLastClipping(true);

        YAxis yAxis11 = chart1.getAxisLeft();
        yAxis11.setTextColor(Color.WHITE);
        yAxis11.setDrawGridLines(true);

        YAxis yAxis12 = chart1.getAxisRight();
        yAxis12.setEnabled(false);
        //end:char1

        //start: chart2
        chart2 = (LineChart) findViewById(R.id.line_chart2);
        chart2.setDragEnabled(true);
        chart2.setScaleEnabled(true);
        chart2.setPinchZoom(true);
        chart2.setBackgroundColor(Color.DKGRAY);

        LineData data2 = new LineData();
        data2.setValueTextColor(Color.WHITE);
        chart2.setData(data2);

        Legend legend2 = chart2.getLegend();
        legend2.setForm(Legend.LegendForm.LINE);
        legend2.setTextColor(Color.WHITE);

        XAxis xAxis2 = chart2.getXAxis();
        xAxis2.setTextColor(Color.WHITE);
        xAxis2.setDrawGridLines(false);
        xAxis2.setAvoidFirstLastClipping(true);

        YAxis yAxis21 = chart2.getAxisLeft();
        yAxis21.setTextColor(Color.WHITE);
        yAxis21.setDrawGridLines(true);

        YAxis yAxis22 = chart2.getAxisRight();
        yAxis22.setEnabled(false);
        //end: chart2

        //start: chart3
        chart3 = (LineChart) findViewById(R.id.line_chart3);
        chart3.setDragEnabled(true);
        chart3.setScaleEnabled(true);
        chart3.setPinchZoom(true);
        chart3.setBackgroundColor(Color.DKGRAY);

        LineData data3 = new LineData();
        data3.setValueTextColor(Color.WHITE);
        chart3.setData(data3);

        Legend legend3 = chart3.getLegend();
        legend3.setForm(Legend.LegendForm.LINE);
        legend3.setTextColor(Color.WHITE);

        XAxis xAxis3 = chart3.getXAxis();
        xAxis3.setTextColor(Color.WHITE);
        xAxis3.setDrawGridLines(false);
        xAxis3.setAvoidFirstLastClipping(true);

        YAxis yAxis31 = chart3.getAxisLeft();
        yAxis31.setTextColor(Color.WHITE);
        yAxis31.setDrawGridLines(true);

        YAxis yAxis32 = chart3.getAxisRight();
        yAxis32.setEnabled(false);
        //end: chart3
    }

    Runnable sensorDataTimer = new Runnable() {
        @Override
        public void run() {
            addSensorData(currentSensorData);
            sendData("Sensor changed.");
            Log.i("Sensor changed", currentSensorData.values.length + "");
            handler.postDelayed(sensorDataTimer, selectedResolution);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        currentSensorData = event;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void addLineDataEntry(float data, LineChart lineChart) {

        LineData lineData = lineChart.getLineData();
        LineDataSet lineDataSet;

        if (lineData != null) {
            lineDataSet = (LineDataSet) lineData.getDataSetByIndex(0);

            if (lineDataSet == null) {
                lineDataSet = new LineDataSet(null, "Sensor data");
                lineDataSet.setDrawCircles(false);
                lineDataSet.setDrawCubic(true);
                lineDataSet.setCubicIntensity(0.1f);
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setColor(ColorTemplate.getHoloBlue());
                lineDataSet.setCircleColor(ColorTemplate.getHoloBlue());
                lineDataSet.setLineWidth(1f);
                lineDataSet.setCircleSize(4f);
                lineDataSet.setFillAlpha(65);
                lineDataSet.setFillColor(ColorTemplate.getHoloBlue());
                lineDataSet.setHighLightColor(Color.rgb(244, 117, 177));
                lineDataSet.setValueTextColor(Color.WHITE);
                lineDataSet.setValueTextSize(10f);

                lineData.addDataSet(lineDataSet);
            }

            lineData.addXValue("");
            lineData.addEntry(new Entry(data, lineDataSet.getEntryCount()), 0);

            lineChart.notifyDataSetChanged();
            lineChart.setVisibleXRange(250, 250);
            lineChart.moveViewToX(lineData.getXValCount() - 101);
        }
    }

    private void addSensorData(SensorEvent event) {

        for (int i = 0; i < event.values.length; i++) {
           //todo generiraj listu viewholdera umjesto view pa dodaj podathe u linechartove
        }
    }

    private void populateResolutionList() {

        resolutions = new ArrayList<>();
        resolutions.add(1);
        resolutions.add(5);
        resolutions.add(10);
        resolutions.add(15);
        resolutions.add(20);
        resolutions.add(25);
        resolutions.add(50);
        resolutions.add(100);
        resolutions.add(200);
        resolutions.add(300);
        resolutions.add(400);
        resolutions.add(500);
        resolutions.add(600);
        resolutions.add(700);
        resolutions.add(800);
        resolutions.add(900);
        resolutions.add(1000);
        resolutions.add(1100);
        resolutions.add(1200);
        resolutions.add(1300);
        resolutions.add(1400);
        resolutions.add(1500);
    }

    private void restart() {

        sensorManager.unregisterListener(this);
        chart1.clear();
        chart2.clear();
        chart3.clear();
        initCharts();

        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());
        handler = new Handler();
        handler.postDelayed(sensorDataTimer, selectedResolution);
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void sendData(String message) {

        if (TextUtils.isEmpty(message)) {
            return;
        }
        mSocket.emit("new message", message);
    }

}
