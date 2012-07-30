/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.motion;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiSensorHelper;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


@Kroll.module
public class MotionModule extends KrollModule implements SensorEventListener
{
	private static final String LCAT = "TiMotion";
	private static final String EVENT_UPDATE = "update";
	private static final String EVENT_ACC = "accelerometer";
	private static final String EVENT_GYRO = "gyroscope";
	private static final String EVENT_MAG = "magnetometer";
	private static final String EVENT_MOTION = "motion";

 	private float[] valuesAccelerometer;
	private float[] valuesMagneticField;

	private float[] matrixR;
	private float[] matrixI;
	private float[] matrixValues;

	private boolean accelerometerRegistered = false;
	private boolean gyroscopeRegistered = false;
	private boolean magnetometerRegistered = false;
	private boolean motionRegistered = false;
	private long lastSensorEventTimestamp = 0;
	private int refreshRate = 30; //time between data in ms
	private boolean computeRotationMatrix = true;

	@Kroll.constant public static final float STANDARD_GRAVITY = SensorManager.STANDARD_GRAVITY;

	public MotionModule()
	{
		super();
	}

	public MotionModule(TiContext tiContext)
	{
		this();
	}

	@Override
	public void eventListenerAdded(String type, int count, final KrollProxy proxy)
	{
		if (EVENT_ACC.equals(type)) {
			if (!accelerometerRegistered) {
				TiSensorHelper.registerListener(Sensor.TYPE_ACCELEROMETER, this, refreshRate);
				accelerometerRegistered = true;
			}
		}
		else if (EVENT_GYRO.equals(type)) {
			if (!gyroscopeRegistered) {
				TiSensorHelper.registerListener(Sensor.TYPE_ORIENTATION, this, refreshRate);
				gyroscopeRegistered = true;
			}
		}
		else if (EVENT_MAG.equals(type)) {
			if (!magnetometerRegistered) {
				TiSensorHelper.registerListener(Sensor.TYPE_MAGNETIC_FIELD, this, refreshRate);
				magnetometerRegistered = true;
			}
		}
		else if (EVENT_MOTION.equals(type)) {
			if (!motionRegistered) {
				if (!magnetometerRegistered) TiSensorHelper.registerListener(Sensor.TYPE_MAGNETIC_FIELD, this, refreshRate);
				if (!gyroscopeRegistered) TiSensorHelper.registerListener(Sensor.TYPE_ORIENTATION, this, refreshRate);
				if (!accelerometerRegistered) TiSensorHelper.registerListener(Sensor.TYPE_ACCELEROMETER, this, refreshRate);
				motionRegistered = true;
			}
		}
		super.eventListenerAdded(type, count, proxy);
	}

	@Override
	public void eventListenerRemoved(String type, int count, KrollProxy proxy)
	{
		if (EVENT_ACC.equals(type)) {
			if (accelerometerRegistered) {
				TiSensorHelper.unregisterListener(Sensor.TYPE_ACCELEROMETER, this);
				accelerometerRegistered = false;
			}
		}
		else if (EVENT_GYRO.equals(type)) {
			if (gyroscopeRegistered) {
				TiSensorHelper.unregisterListener(Sensor.TYPE_ORIENTATION, this);
				gyroscopeRegistered = false;
			}
		}
		else if (EVENT_MAG.equals(type)) {
			if (magnetometerRegistered) {
				TiSensorHelper.unregisterListener(Sensor.TYPE_MAGNETIC_FIELD, this);
				magnetometerRegistered = false;
			}
		}
		else if (EVENT_MOTION.equals(type)) {
			if (motionRegistered) {
				TiSensorHelper.unregisterListener(Sensor.TYPE_MAGNETIC_FIELD, this);
				TiSensorHelper.unregisterListener(Sensor.TYPE_ACCELEROMETER, this);
				TiSensorHelper.unregisterListener(Sensor.TYPE_ORIENTATION, this);
				motionRegistered = false;
			}
		}
		super.eventListenerRemoved(type, count, proxy);
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	public void onSensorChanged(int sensor, float[] values)
	{
		// if (event.timestamp - lastSensorEventTimestamp > 100) {
		lastSensorEventTimestamp = event.timestamp;
		KrollDict data = new KrollDict();
			
		float x = values[0];
		float y = values[1];
		float z = values[2];

		data.put("timestamp", lastSensorEventTimestamp);

		if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			// valuesAccelerometer[0] = x;
			// valuesAccelerometer[1] = y;
			// valuesAccelerometer[2] = z;

		   	if (accelerometerRegistered)
		   	{
				data.put("type", EVENT_ACC);
				data.put("x", x);
				data.put("y", y);
				data.put("z", z);
				fireEvent(EVENT_ACC, data);
			}
		}
		if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
		{
			// valuesMagneticField[0] = x;
			// valuesMagneticField[1] = y;
			// valuesMagneticField[2] = z;
			if (magnetometerRegistered)
		   	{
				data.put("type", EVENT_MAG);
				data.put("x", x);
				data.put("y", y);
				data.put("z", z);
				fireEvent(EVENT_MAG, data);
			}
		}
		if (sensor.getType() == Sensor.TYPE_ORIENTATION && gyroscopeRegistered)
		{
			data.put("type", EVENT_GYRO);
			data.put("yaw", x);
			data.put("pitch", y);
			data.put("roll", z);
			fireEvent(EVENT_GYRO, data);
		}

		if (motionRegistered)
		{
		}
		// }
	}

	@Kroll.getProperty @Kroll.method
	public int getRefreshRate()
	{
		return refreshRate;
	}

	@Kroll.method @Kroll.setProperty
	public void setRefreshRate(int rate)
	{
		refreshRate = rate;
		if (accelerometerRegistered || motionRegistered)
		{
			TiSensorHelper.unregisterListener(Sensor.TYPE_ACCELEROMETER, this);
			TiSensorHelper.registerListener(Sensor.TYPE_ACCELEROMETER, this, refreshRate);
		}
		if (gyroscopeRegistered || motionRegistered)
		{
			TiSensorHelper.unregisterListener(Sensor.TYPE_ORIENTATION, this);
			TiSensorHelper.registerListener(Sensor.TYPE_ORIENTATION, this, refreshRate);
		}
		if (magnetometerRegistered || motionRegistered)
		{
			TiSensorHelper.unregisterListener(Sensor.TYPE_MAGNETIC_FIELD, this);
			TiSensorHelper.registerListener(Sensor.TYPE_MAGNETIC_FIELD, this, refreshRate);
		}
	}

	@Kroll.getProperty @Kroll.method
	public boolean getComputeRotationMatrix()
	{
		return computeRotationMatrix;
	}

	@Kroll.method @Kroll.setProperty
	public void setRComputeRotationMatrix(boolean value)
	{
		computeRotationMatrix = value;
	}
}
