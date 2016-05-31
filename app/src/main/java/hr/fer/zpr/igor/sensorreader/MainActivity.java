package hr.fer.zpr.igor.sensorreader;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Integer selectedResolution;
    private Sensor selectedSensor;
    private Sensor activeSensor;
    private SensorEvent currentSensorData;
    private Handler handler;
    private RelativeLayout sensorSelectButton;
    private RelativeLayout resolutionSelectButton;
    private LinearLayout sensorLayout;
    private TextView selectedSensorText;
    private TextView selectedResolutionText;
    private SensorListAdapter sensorListAdapter;
    List<Sensor> deviceSensors;
    private ResolutionListAdapter resolutionListAdapter;
    List<Integer> resolutions;
    List<View> lineCharts = new ArrayList<>();
    CollectedData data;
    private String deviceId;
    private int dataSizeLimit;
    private int dataTimeLimit;
    private int timeElapsed;
    private boolean isSending = false;
    private boolean isReconnecting = false;
    private boolean hasReconnected = false;
    private boolean serverReady = true;
    private Emitter.Listener registerListener;
    private Emitter.Listener startListener;
    private Emitter.Listener stopListener;
    private Emitter.Listener reconnectingListener;
    private Emitter.Listener reconnectedListener;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    private Button serverButton;
    private EditText serverEditText;

    private Socket mSocket;
    Timer myTimer;


//    {
//        try {
//            mSocket = IO.socket("http://192.168.1.2:3000");
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        deviceId = Preferences.getId(this);
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = UUID.randomUUID().toString();
            Preferences.setId(deviceId, this);
        }

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();

        populateResolutionList();

//        mSocket.on("start_sending", new Emitter.Listener() {
//            @Override
//            public void call(Object... args) {
//                Log.d("START_SENDING", "");
//            }
//        });

        registerListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                registerDevice();
            }
        };

        startListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                isSending = true;
            }
        };

        stopListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                isSending = false;
            }
        };

        reconnectingListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                isReconnecting = true;
            }
        };

        reconnectedListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                hasReconnected = true;
                isReconnecting = false;
            }
        };

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        sensorLayout = (LinearLayout) findViewById(R.id.sensor_layout);

        serverEditText = (EditText) findViewById(R.id.server_et);
        serverButton = (Button) findViewById(R.id.server_btn);
        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    mSocket = IO.socket(serverEditText.getText().toString());
                    mSocket.connect();

                    mSocket.on("request_registration", registerListener);
                    mSocket.on("start_sending", startListener);
                    mSocket.on("stop_sending", stopListener);
                    mSocket.on(Socket.EVENT_RECONNECTING, reconnectingListener);
                    mSocket.on(Socket.EVENT_RECONNECT, reconnectedListener);

                    Toast.makeText(MainActivity.this, "Connected to server.", Toast.LENGTH_SHORT).show();

                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Connection failed.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        sensorSelectButton = (RelativeLayout) findViewById(R.id.sensor_select_layout);
        sensorSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isSending) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Sending data");
                    builder.setMessage("Can't change settings while sending data.");
                    builder.setNegativeButton("Stop sending", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            isSending = false;
                            mSocket.disconnect();
                            dialog.dismiss();
                        }
                    });
                    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();

                } else {

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
                            registerDevice();
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }
        });

        resolutionSelectButton = (RelativeLayout) findViewById(R.id.resolution_select_layout);
        resolutionSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isSending) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Sending data");
                    builder.setMessage("Can't change settings while sending data.");
                    builder.setNegativeButton("Stop sending", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            isSending = false;
                            mSocket.disconnect();
                            dialog.dismiss();
                        }
                    });
                    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();

                } else {

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
                            registerDevice();
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }
        });

        selectedSensorText = (TextView) findViewById(R.id.sensor_select_selected);
        selectedResolutionText = (TextView) findViewById(R.id.resolution_select_selected);

        selectedSensor = deviceSensors.get(0);
        initCharts();
        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());
        selectedResolution = 500;

        data = new CollectedData(deviceId, activeSensor.getType(), activeSensor.getName(), selectedResolution);

        selectedSensorText.setText(selectedSensor.getName());
        selectedResolutionText.setText(selectedResolution + "");
        handler = new Handler();
        handler.postDelayed(sensorDataTimer, selectedResolution);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        myTimer.cancel();
        mSocket.disconnect();
        mSocket.close();
        wakeLock.release();
    }

    private void initCharts() {

        int chartNumber = Utils.numberOfValuesFormType(selectedSensor.getType());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, 1.0f);

        sensorLayout.removeAllViews();
        sensorLayout.setWeightSum(chartNumber);

        if (!lineCharts.isEmpty())
            lineCharts.clear();

        for (int i = 0; i < chartNumber; i++) {

            View chartView = getLayoutInflater().inflate(R.layout.layout_graph, null, false);
            LineChart chart = (LineChart) chartView.findViewById(R.id.line_chart);

            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setBackgroundColor(Color.DKGRAY);

            LineData data = new LineData();
            data.setValueTextColor(Color.WHITE);
            chart.setData(data);

            Legend legend = chart.getLegend();
            legend.setForm(Legend.LegendForm.LINE);
            legend.setTextColor(Color.WHITE);

            XAxis xAxis = chart.getXAxis();
            xAxis.setTextColor(Color.WHITE);
            xAxis.setDrawGridLines(false);
            xAxis.setAvoidFirstLastClipping(true);

            YAxis yAxis1 = chart.getAxisLeft();
            yAxis1.setTextColor(Color.WHITE);
            yAxis1.setDrawGridLines(true);

            YAxis yAxis2 = chart.getAxisRight();
            yAxis2.setEnabled(false);

            chartView.setTag(chart);
            chartView.setLayoutParams(params);
            lineCharts.add(chartView);
            sensorLayout.addView(chartView);
        }
    }


    Runnable sensorDataTimer = new Runnable() {
        @Override
        public void run() {

            if (isSending && currentSensorData != null){
                if (data.isReadyToSend() && !isReconnecting && serverReady) {

                    sendData(data.getData().toString());
                    Log.i("Sensor changed", data.getData().toString());
                    data.reset();
                }
                data.addData(currentSensorData);
            }
            if (currentSensorData != null) {
                addSensorDataToCharts(currentSensorData);
            }
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
                lineDataSet.setDrawCubic(false);
                lineDataSet.setCubicIntensity(0.001f);
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

    private void addSensorDataToCharts(SensorEvent event) {

        for (int i = 0; i < lineCharts.size(); i++) {

            addLineDataEntry(event.values[i], (LineChart) lineCharts.get(i).getTag());
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
        //resolutions.add(1100);
        //resolutions.add(1200);
        //resolutions.add(1300);
        //resolutions.add(1400);
        //resolutions.add(1500);
    }

    private void restart() {

        sensorManager.unregisterListener(this);
        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());
        initCharts();
        handler = new Handler();
        handler.postDelayed(sensorDataTimer, selectedResolution);

        data = new CollectedData(deviceId, activeSensor.getType(), activeSensor.getName(), selectedResolution);
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void sendData(String message) {

        if (TextUtils.isEmpty(message)) {
            return;
        }
        serverReady = false;
        mSocket.emit("new message", message, new Ack() {
            @Override
            public void call(Object... args) {
                serverReady = true;
            }
        });
    }

    private void stopSensorListening() {
        sensorManager.unregisterListener(this);
    }

    private void startSensorListening() {
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void startDataSending() {
        isSending = true;
    }

    private void stopDataSending() {
        isSending = false;
    }

    private void registerDevice(){

        if (hasReconnected){
            mSocket.emit("register_device",
                    "{\"device\":\"" + Build.MODEL +
                            "\",\"reconnection\":\"" + true +
                            "\",\"id\":\"" + deviceId +
                            "\",\"sensor\":\"" + selectedSensor.getName() +
                            "\",\"data_fields\":" + Utils.numberOfValuesFormType(selectedSensor.getType()) +
                            ",\"resolution\":" + selectedResolution + "}");

            hasReconnected = false;
        } else {
            mSocket.emit("register_device",
                    "{\"device\":\"" + Build.MODEL +
                            "\",\"id\":\"" + deviceId +
                            "\",\"sensor\":\"" + selectedSensor.getName() +
                            "\",\"data_fields\":" + Utils.numberOfValuesFormType(selectedSensor.getType()) +
                            ",\"resolution\":" + selectedResolution + "}");
        }
    }
}
