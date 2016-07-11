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
import android.os.PowerManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.TextUtils;
import android.util.Log;
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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int STATUS_CONNECTED = 1;
    private static final int STATUS_DISCONNECTED = 2;
    private static final int STATUS_RECONNECTING = 3;


    private SensorManager sensorManager;
    private Integer selectedResolution;
    private Sensor selectedSensor;
    private Sensor activeSensor;
    private SensorEvent currentSensorData;
    private RelativeLayout sensorSelectButton;
    private RelativeLayout resolutionSelectButton;
    private LinearLayout sensorLayout;
    private TextView selectedSensorText;
    private TextView selectedResolutionText;
    private TextView tvConnectionStatus;
    private SensorListAdapter sensorListAdapter;
    List<Sensor> deviceSensors;
    private ResolutionListAdapter resolutionListAdapter;
    List<Integer> resolutions;
    List<View> lineCharts = new ArrayList<>();
    CollectedData data;
    private String deviceId;

    private boolean isSending = false;
    private boolean isReconnecting = false;
    private boolean hasReconnected = false;
    private boolean serverReady = true;

    private Emitter.Listener registerListener;
    private Emitter.Listener startListener;
    private Emitter.Listener stopListener;
    private Emitter.Listener reconnectingListener;
    private Emitter.Listener reconnectedListener;
    private Emitter.Listener disconnectedListener;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;
    ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);

    private Button serverButton;
    private EditText serverEditText;

    private Socket mSocket;
    private String currentServer = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceId = Preferences.getId(this);
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = UUID.randomUUID().toString();
            Preferences.setId(deviceId, this);
        }

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();

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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setConnectionStatus(STATUS_RECONNECTING);
                    }
                });
            }
        };

        reconnectedListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                hasReconnected = true;
                isReconnecting = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setConnectionStatus(STATUS_CONNECTED);
                    }
                });
            }
        };

        disconnectedListener = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setConnectionStatus(STATUS_DISCONNECTED);
                    }
                });
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

                if (!TextUtils.isEmpty(serverEditText.getText())) {
                    if (mSocket == null || !mSocket.connected()) {
                        try {

                            if (!currentServer.equals(serverEditText.getText().toString().trim())) {
                                currentServer = serverEditText.getText().toString().trim();
                                mSocket = IO.socket(currentServer);
                            }

                            mSocket.connect();
                            mSocket.on("request_registration", registerListener);
                            mSocket.on("start_sending", startListener);
                            mSocket.on("stop_sending", stopListener);
                            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                                @Override
                                public void call(Object... args) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            setConnectionStatus(STATUS_CONNECTED);
                                        }
                                    });
                                }
                            });
                            mSocket.on(Socket.EVENT_RECONNECTING, reconnectingListener);
                            mSocket.on(Socket.EVENT_RECONNECT, reconnectedListener);
                            mSocket.on(Socket.EVENT_DISCONNECT, disconnectedListener);

                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "Connection failed.", Toast.LENGTH_SHORT).show();
                        }
                    } else if (mSocket.connected() || isReconnecting) {
                        mSocket.disconnect();
                    }
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
                    builder.setNegativeButton("Disconnect", new DialogInterface.OnClickListener() {
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
                            populateResolutionList();
                            selectedSensorText.setText(selectedSensor.getName());
                            restart();
                            if (mSocket != null) {
                                updateDeviceData();
                            }
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

                    populateResolutionList();
                    builder.setAdapter(resolutionListAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            selectedResolution = (int) resolutionListAdapter.getItem(which);
                            selectedResolutionText.setText(selectedResolution + "");
                            restart();
                            if (mSocket != null) {
                                updateDeviceData();
                            }
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }
        });

        tvConnectionStatus = (TextView) findViewById(R.id.tv_connection_status);

        selectedSensorText = (TextView) findViewById(R.id.sensor_select_selected);
        selectedResolutionText = (TextView) findViewById(R.id.resolution_select_selected);

        selectedSensor = deviceSensors.get(0);

        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());

        sensorListAdapter = new SensorListAdapter(MainActivity.this, 0, deviceSensors);
        populateResolutionList();
        selectedResolution = 500;

        data = new CollectedData(deviceId, Build.MODEL, activeSensor.getType(), activeSensor.getName(), selectedResolution);

        selectedSensorText.setText(selectedSensor.getName());
        selectedResolutionText.setText(selectedResolution + "");

        execService.scheduleAtFixedRate(sensorDataTimer, 0, selectedResolution, TimeUnit.MILLISECONDS);
        initCharts();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        mSocket.disconnect();
        mSocket.close();
        wakeLock.release();
        execService.shutdown();
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
            chart.setDescription("");

            LineData data = new LineData();
            data.setValueTextColor(Color.WHITE);
            chart.setData(data);

            Legend legend = chart.getLegend();
            legend.setEnabled(false);

            XAxis xAxis = chart.getXAxis();
            xAxis.setTextColor(Color.WHITE);
            xAxis.setAvoidFirstLastClipping(true);
            xAxis.setDrawLabels(true);
            xAxis.setDrawAxisLine(true);
            xAxis.setLabelsToSkip(50);
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(true);

            YAxis yAxis1 = chart.getAxisLeft();
            yAxis1.setAxisMaxValue(selectedSensor.getMaximumRange());
            yAxis1.setAxisMinValue(-selectedSensor.getMaximumRange());
            yAxis1.setTextColor(Color.WHITE);
            yAxis1.setDrawTopYLabelEntry(true);
            yAxis1.setLabelCount(5, true);
            yAxis1.setDrawGridLines(true);

            YAxis yAxis2 = chart.getAxisRight();
            yAxis2.setEnabled(true);
            yAxis2.setDrawAxisLine(true);
            yAxis2.setDrawLabels(false);
            yAxis2.setDrawTopYLabelEntry(true);
            yAxis2.setDrawGridLines(false);

            chartView.setTag(chart);
            chartView.setLayoutParams(params);
            lineCharts.add(chartView);
            sensorLayout.addView(chartView);
        }
    }


    Runnable sensorDataTimer = new Runnable() {
        @Override
        public void run() {

            if (isSending && currentSensorData != null) {
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
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        mSocket.emit("disconnect");
        super.onStop();
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
                lineDataSet.setDrawCubic(false);
                lineDataSet.setDrawCircles(false);
                lineDataSet.setCubicIntensity(0.001f);
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setColor(ColorTemplate.getHoloBlue());
                lineDataSet.setCircleColor(ColorTemplate.getHoloBlue());
                lineDataSet.setLineWidth(1f);
                lineDataSet.setFillAlpha(65);
                lineDataSet.setFillColor(ColorTemplate.getHoloBlue());
                lineDataSet.setHighLightColor(Color.rgb(244, 117, 177));
                lineDataSet.setValueTextColor(Color.WHITE);
                lineDataSet.setValueTextSize(10f);

                lineData.addDataSet(lineDataSet);
            }

            lineData.addXValue(getXValue());
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

        int[] res = {20, 25, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        resolutions = new ArrayList<>();

        for (int i : res) {
            if (i >= selectedSensor.getMinDelay() / 1000)
                resolutions.add(i);
        }
    }

    private void restart() {

        sensorManager.unregisterListener(this);
        activeSensor = sensorManager.getDefaultSensor(selectedSensor.getType());
        initCharts();
        execService.shutdown();
        execService = Executors.newScheduledThreadPool(5);
        execService.scheduleAtFixedRate(sensorDataTimer, 0, selectedResolution, TimeUnit.MILLISECONDS);

        data = new CollectedData(deviceId, Build.MODEL, activeSensor.getType(), activeSensor.getName(), selectedResolution);
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_FASTEST);
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
        sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void startDataSending() {
        isSending = true;
    }

    private void stopDataSending() {
        isSending = false;
    }

    private void registerDevice() {

        if (hasReconnected) {
            mSocket.emit("register_device",
                    "{\"device\":\"" + Build.MODEL +
                            "\",\"reconnection\":\"" + true +
                            "\",\"id\":\"" + deviceId +
                            "\",\"sensor\":\"" + selectedSensor.getName() +
                            "\",\"sensor_vendor\":\"" + selectedSensor.getVendor() +
                            "\",\"sensor_maxrange\":\"" + selectedSensor.getMaximumRange() +
                            "\",\"data_fields\":" + Utils.numberOfValuesFormType(selectedSensor.getType()) +
                            ",\"resolution\":" + selectedResolution + "}", new Ack() {
                        @Override
                        public void call(Object... args) {
                            if (args[0].equals("new"))
                                stopDataSending();
                        }
                    });
            hasReconnected = false;
        } else {
            mSocket.emit("register_device",
                    "{\"device\":\"" + Build.MODEL +
                            "\",\"id\":\"" + deviceId +
                            "\",\"sensor\":\"" + selectedSensor.getName() +
                            "\",\"sensor_vendor\":\"" + selectedSensor.getVendor() +
                            "\",\"sensor_maxrange\":\"" + selectedSensor.getMaximumRange() +
                            "\",\"data_fields\":" + Utils.numberOfValuesFormType(selectedSensor.getType()) +
                            ",\"resolution\":" + selectedResolution + "}", new Ack() {
                        @Override
                        public void call(Object... args) {
                            if (args[0].equals("new"))
                                stopDataSending();
                        }
                    });
        }
    }

    private void updateDeviceData() {
        mSocket.emit("update_device_data",
                "{\"device\":\"" + Build.MODEL +
                        "\",\"id\":\"" + deviceId +
                        "\",\"sensor\":\"" + selectedSensor.getName() +
                        "\",\"sensor_vendor\":\"" + selectedSensor.getVendor() +
                        "\",\"sensor_maxrange\":\"" + selectedSensor.getMaximumRange() +
                        "\",\"data_fields\":" + Utils.numberOfValuesFormType(selectedSensor.getType()) +
                        ",\"resolution\":" + selectedResolution + "}");
    }

    private void setConnectionStatus(int connStatus) {

        switch (connStatus) {
            case STATUS_CONNECTED:
                tvConnectionStatus.setBackgroundColor(Color.GREEN);
                tvConnectionStatus.setText("Connected");
                serverButton.setText("Disconnect");
                break;
            case STATUS_DISCONNECTED:
                tvConnectionStatus.setBackgroundColor(Color.RED);
                tvConnectionStatus.setText("Disconnected");
                serverButton.setText("Connect");
                break;
            case STATUS_RECONNECTING:
                tvConnectionStatus.setBackgroundColor(Color.YELLOW);
                tvConnectionStatus.setText("Reconnecting");
                serverButton.setText("Disconnect");
                break;
        }
    }


    public String getXValue() {

        int count = ((LineChart) lineCharts.get(0).getTag()).getLineData().getDataSets().get(0).getEntryCount();
        int modulo = 1000 / selectedResolution;
        int seconds = count / modulo;

        String timeSeconds = (seconds % 60) < 10 ? ("0" + (seconds % 60)) : "" + (seconds % 60);
        String timeMinutes = (seconds / 60) < 10 ? ("0" + (seconds / 60)) : "" + (seconds / 60);

        return timeMinutes + ":" + timeSeconds;

    }
}
