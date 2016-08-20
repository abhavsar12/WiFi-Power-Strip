package com.example.arpan.wifipowerstrip;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MqttService extends Service implements MqttCallback{

    // Broker details
    private String broker = "raspberrypi";
    private String url = "tcp://raspberrypi:1883";

    // Topic and Client ID
    private String outTopic = "FromAndroid";
    private String inTopic = "ToAndroid";
    private String clientID = "AndroidPhone";

    // Client related details
    private MemoryPersistence memoryPersistence;
    private MqttClient client;
    private MqttConnectOptions connOpts;

    // Message and Quality of service option
    private MqttMessage message;
    private int qos = 0;

    // Time interval that battery percentage will be sent
    // and the alarm intent for the alarm manager that will periodically
    // send battery and time information
    int keepAliveBatterySeconds = 60*10;
    int keepBrokerAliveSeconds = 60*12;
    PendingIntent alarmIntent;

    // Binding
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        MqttService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MqttService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize client settings
        try {
            memoryPersistence = new MemoryPersistence();
            client = new MqttClient(url, clientID, memoryPersistence);
            connOpts = new MqttConnectOptions();
            connOpts.setKeepAliveInterval(keepBrokerAliveSeconds);
            connOpts.setCleanSession(true);
        } catch (MqttException e) {
            Log.e("Connection", "Failed to connect because: " + e.getReasonCode() + ":" + e.getCause());
        }
        // This receiver and filter is used to handle the battery and time info
        // that is sent periodically
        IntentFilter filter = new IntentFilter();
        filter.addAction("alarm");
        filter.addAction("update");
        registerReceiver(receiver, filter);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int starId) {
        Log.e("Checkup", "checking");
        connectToBroker();
        sendBatteryAndTime();
        return START_STICKY;
    }

    // Wrapper function that connects to the MQTT Broker on the raspberry pi
    public void connectToBroker() {
        try {
            Log.e("Connection", "Attempting Mqtt Connection...");
            client.connect(connOpts);
            client.setCallback(this);
            Log.e("Connection", "Connected");
            sendMessage("Hello from android");
        } catch (MqttException e) {
            Log.e("Connection", "Failed to connect because: " + e.getReasonCode() + ":" + e.getCause());
        }

        try {
            // Syncs every time the device connects to update the switch states in the app
            // The python client on the raspberry pi handles the sync message and send back
            // switch status data
            Log.e("Subscription", "Trying to subscribe");
            client.subscribe(inTopic, qos);
            sendMessage("Sync");
            Log.e("Subscription", "Subscribed");
        }
        catch (MqttException e) {
                Log.e("Subscription", "Failed to subscribe bc: " + e.getReasonCode() + ":" + e.getCause());
        }
    }

    // Wrapper function that sends/publishes a message to the MQTT broker on the Raspberry Pi
    public void sendMessage(String msg) {
        message = new MqttMessage(msg.getBytes());
        message.setQos(qos);
        try {
            client.publish(outTopic, message);
            Log.e("Publish", "Message: " + msg + " Published to " + broker + " at topic " + outTopic);
        }
        catch (MqttException e) {
            Log.e("Publish", "Message failed to publish");
        }

    }

    // Callback function that handles the messages received from the MQTT broker
    // In this case, the only message that the application will ever receiver in
    // switch data supplied from the sync message, so the application does not check
    // for any other messages - can be updated in the future for added functionality
    public void messageArrived(String topic, MqttMessage message) {
        Log.e("Arrival", "Received message");
        // Only needs to handle the sync case where the serve sends
        // a message in the format of 1:0/2:0/3:1/4:0
        // meaning switch 1 is off, switch 2 is off, switch 3 in on, switch 4 is off
        String msg = new String(message.getPayload());
        Log.e("Message", msg);

        // Since we know where the switch data is in the string, we just get the state data
        // The state data is converted to an integer
        int state1 = Integer.parseInt(msg.substring(2,3));
        int state2 = Integer.parseInt(msg.substring(6,7));
        int state3 = Integer.parseInt(msg.substring(10,11));
        int state4 = Integer.parseInt(msg.substring(14,15));


        // Comparing to 1 essential converts to boolean and updates the states of each switch
        Intent intent = new Intent();
        intent.setAction("sync");
        intent.putExtra("switch1State", state1 == 1);
        intent.putExtra("switch2State", state2 == 1);
        intent.putExtra("switch3State", state3 == 1);
        intent.putExtra("switch4State", state4 == 1);

        // Sends broadcast with sync action to the MainActivity with booleans
        // of all switch states
        Log.e("Broadcast", "Sending broadcast");
        sendBroadcast(intent);
    }

    // Wrapper function that sends the current battery and time info according
    // to the interval stated in the variable keepAliveBatterSeconds
    public void sendBatteryAndTime() {
        Context context = getApplicationContext();
        Intent intent = new Intent("alarm");
        alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        Calendar wakeUpTime = Calendar.getInstance();

        // Setting up the repeating alarm manager
        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                wakeUpTime.getTimeInMillis(), keepAliveBatterySeconds*1000,
                alarmIntent);
    }

    // Broadcast Receiver that handles the alarm manager and sends battery and
    // time info every given interval
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("alarm")) {
                Log.e("Alarm", "Received");
                String msg;
                Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH-mm"); // HH vs hh = 24 hour vs 12 hour format
                String format = simpleDateFormat.format(new Date());

                msg = "Current:" + Float.toString(((float)level / (float)scale) * 100.0f) + ":" + format;
                sendMessage(msg);
            }
            if (action.equals("update")) {
                Log.e("Update", "Received");
                Bundle extras = intent.getExtras();
                if (extras.getString("battery") != null) {
                    sendMessage("Update:" + "Battery:" + extras.getString("battery"));
                }
                else if (extras.getString("timer_on") != null) {
                    sendMessage("Update:" + "Timer On:" + extras.getString("timer_on"));
                }
                else if (extras.getString("timer_off") != null) {
                    sendMessage("Update:" + "Timer Off:" + extras.getString("timer_off"));
                }
            }
        }
    };

    // Additional functions that are needed to complete the implementation of MqttCallback
    @Override
    public void connectionLost(Throwable cause) {
        if (isOnline() == false) {
            Log.e("Connection", "Connection lost!");
        }
        else {
            connectToBroker();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(cm.getActiveNetworkInfo() != null &&
                cm.getActiveNetworkInfo().isAvailable() &&
                cm.getActiveNetworkInfo().isConnected())
        {
            return true;
        }

        return false;
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
