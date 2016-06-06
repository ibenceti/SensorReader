package hr.fer.zpr.igor.sensorreader;

import android.hardware.SensorEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CollectedData {

    private static final String DEVICE_ID = "device_id";
    private static final String DEVICE_NAME = "device_name";
    private static final String SENSOR_TYPE = "sensor_type";
    private static final String SENSOR_NAME = "sensor_name";
    private static final String DATA_ARRAYS = "data";
    private static final String RESOLUTION = "resolution";
    private static final String START_TIMESTAMP = "start_timestamp";


    private String deviceId;
    private String deviceName;
    private int sensorType;
    private String sensorName;
    private JSONObject data;
    private JSONArray dataArrays;
    private int resolution;
    private long startTimestamp = 0;
    private int nrOfMeasurementsTaken = 0;
    private int nrOfValuesMeasured = 0;
    private boolean isInitialised = false;

    public CollectedData(String deviceId, String deviceName, int sensorType, String sensorName, int resolution) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.sensorType = sensorType;
        this.sensorName = sensorName;
        this.resolution = resolution;
        data = new JSONObject();
        dataArrays = new JSONArray();

        nrOfValuesMeasured = Utils.numberOfValuesFormType(sensorType);
        startTimestamp = System.currentTimeMillis();

        try {
            data.put(DEVICE_ID, deviceId);
            data.put(DEVICE_NAME, deviceName);
            data.put(SENSOR_TYPE, sensorType);
            data.put(SENSOR_NAME, sensorName);
            data.put(RESOLUTION, resolution);
            data.put(START_TIMESTAMP, startTimestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < nrOfValuesMeasured; i++) {

            JSONArray dataArray = new JSONArray();
            dataArrays.put(dataArray);
        }

    }


    public void addData(SensorEvent sensorEvent) {

        nrOfMeasurementsTaken++;

        for (int i = 0; i < nrOfValuesMeasured; i++) {

            try {
                ((JSONArray) dataArrays.get(i)).put(sensorEvent.values[i]);
//                Log.i("JSON Data",  dataArrays.get(i).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public JSONObject getData() {

        try {
            data.put(DATA_ARRAYS, dataArrays);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return data;
    }

    public boolean isReadyToSend() {
        return nrOfMeasurementsTaken >= (1000 / resolution);
    }

    public void reset() {
        startTimestamp = System.currentTimeMillis();
        nrOfMeasurementsTaken = 0;
        try {
            data.remove(DATA_ARRAYS);
            data.remove(START_TIMESTAMP);
            data.put(START_TIMESTAMP, startTimestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        dataArrays = new JSONArray();

        for (int i = 0; i < nrOfValuesMeasured; i++) {

            JSONArray dataArray = new JSONArray();
            dataArrays.put(dataArray);
        }
    }

}
