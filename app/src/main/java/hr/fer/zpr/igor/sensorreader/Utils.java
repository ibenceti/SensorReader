package hr.fer.zpr.igor.sensorreader;

import android.hardware.Sensor;

/**
 * Created by Igor on 15/5/2016.
 */
public class Utils {

    public static int numberOfValuesFormType (int sensorType){

        if (sensorType == Sensor.TYPE_LIGHT
                || sensorType == Sensor.TYPE_PROXIMITY
                || sensorType == Sensor.TYPE_PRESSURE
                || sensorType == Sensor.TYPE_RELATIVE_HUMIDITY
                || sensorType == Sensor.TYPE_AMBIENT_TEMPERATURE
                || sensorType == Sensor.TYPE_HEART_RATE) {
            return 1;
        } else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
                || sensorType == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            return 6;

        } else if (sensorType == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            return 5;
        } else {
            return 3;
        }
    }
}
