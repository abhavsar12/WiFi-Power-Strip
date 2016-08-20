package com.example.arpan.wifipowerstrip;

import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.Calendar;

/* This application was created by Arpan Bhavsar on 7/30/16
 *
 * This app connects to a Mqtt Broker set up on a Raspberry Pi that
 * acts as a server with a MySql database run with a Python script.
 * The app controls a wireless power strip that enables the user to switch
 * desired outlets on and off given by switches in the application.
 *
 * Additionally, the application features an overcharge protection feature
 * that allows the user to select a battery percentage for with the corresponding
 * outlet will turn off after a max charge has been reached to prevent damage and
 * life shortage to the battery life (ex. User is able to stop charging the phone
 * when the phone has been charge 90%).
 *
 * There is also a programmable timer that is useful to turn lights and appliances
 * on and off at given times. This is useful to save energy when common lights
 * and appliances are left on by accident.
 */

public class MainActivity extends AppCompatActivity {

    // App Preferences
    SharedPreferences app_preferences;

    // Switch states
    private boolean switch1State;
    private boolean switch2State;
    private boolean switch3State;
    private boolean switch4State;

    // Switches
    private Switch switch1;
    private Switch switch2;
    private Switch switch3;
    private Switch switch4;

    // Service variables
    private MqttService mService;
    private boolean mBound = false;

    // Spinner for max battery charge percentages
    Spinner spinner;

    // Textview for the timers
    TextView timeron;
    TextView timeroff;

    // Timer values
    int hourOn;
    int minuteOn;
    int hourOff;
    int minuteOff;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((MqttService.LocalBinder) service).getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeSwitches();
        initializeOptions();

        Intent intent = new Intent(this, MqttService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBound) {
            mService.sendMessage("Sync");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("sync");
        registerReceiver(receiver, filter);
    }

    // Saves state of switch from current session on app pause or close
    public void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = app_preferences.edit();
        editor.putBoolean("switch1State", switch1State);
        editor.putBoolean("switch2State", switch2State);
        editor.putBoolean("switch3State", switch3State);
        editor.putBoolean("switch4State", switch4State);
        editor.commit();
        unregisterReceiver(receiver);
    }

    public void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void initializeSwitches() {
        // Initialize switch states, false = default state if no preferences are given
        app_preferences = getApplicationContext().getSharedPreferences("preferences", 0);
        switch1State = app_preferences.getBoolean("switch1State", false);
        switch2State = app_preferences.getBoolean("switch2State", false);
        switch3State = app_preferences.getBoolean("switch3State", false);
        switch4State = app_preferences.getBoolean("switch4State", false);

        // Set initial switch states
        switch1 = (Switch) findViewById(R.id.switch1);
        switch2 = (Switch) findViewById(R.id.switch2);
        switch3 = (Switch) findViewById(R.id.switch3);
        switch4 = (Switch) findViewById(R.id.switch4);

        switch1.setChecked(switch1State);
        switch2.setChecked(switch2State);
        switch3.setChecked(switch3State);
        switch4.setChecked(switch4State);

        // Create listeners
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switch1State = isChecked;
                if (isChecked)
                    mService.sendMessage("State:1:1");
                else
                    mService.sendMessage("State:1:0");
            }
        });
        switch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switch2State = isChecked;
                if (isChecked)
                    mService.sendMessage("State:2:1");
                else
                    mService.sendMessage("State:2:0");
            }
        });
        switch3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switch3State = isChecked;
                if (isChecked)
                    mService.sendMessage("State:3:1");
                else
                    mService.sendMessage("State:3:0");
            }
        });
        switch4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switch4State = isChecked;
                if (isChecked)
                    mService.sendMessage("State:4:1");
                else
                    mService.sendMessage("State:4:0");
            }
        });

    }

    public void initializeOptions() {
        // The following code listens for the item selected as an option for a max battery percentage
        // to be charged up to. As soon as the option is clicked, it is immediately sent to update the
        // database on the raspberry pi. Also, an intent is broadcasted to the alarm receiver to
        // check the status of the current battery to see if the phone outlet should be on at the
        // time that the battery option is selected.
        app_preferences = getApplicationContext().getSharedPreferences("preferences", 0);
        spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.battery_percentages, android.R.layout.simple_spinner_dropdown_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int batteryPos = app_preferences.getInt("battery_pos", 0);
        spinner.setSelection(batteryPos);
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        Object item = parent.getItemAtPosition(pos);
                        SharedPreferences.Editor editor = app_preferences.edit();
                        editor.putInt("battery_pos", pos);
                        editor.commit();
                        Intent intent1 = new Intent();
                        intent1.setAction("update");
                        intent1.putExtra("battery", item.toString());
                        sendBroadcast(intent1);

                        Intent intent2 = new Intent();
                        intent2.setAction("alarm");
                        sendBroadcast(intent2);

                    }
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        timeron = (TextView) findViewById(R.id.timeron);
        timeroff = (TextView) findViewById(R.id.timeroff);
        Calendar mcurrentTime = Calendar.getInstance();
        hourOn = app_preferences.getInt("hour_on",mcurrentTime.get(Calendar.HOUR_OF_DAY));
        minuteOn = app_preferences.getInt("minute_on",mcurrentTime.get(Calendar.MINUTE));
        hourOff = app_preferences.getInt("hour_off",mcurrentTime.get(Calendar.HOUR_OF_DAY));
        minuteOff = app_preferences.getInt("minute_off",mcurrentTime.get(Calendar.MINUTE));
        String timeOn = String.format("%02d:%02d", hourOn, minuteOn);
        String timeOff = String.format("%02d:%02d", hourOff, minuteOff);
        timeron.setText(timeOn);
        timeroff.setText(timeOff);
        timeron.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar mcurrentTime = Calendar.getInstance();
                int hour = app_preferences.getInt("hour_on",mcurrentTime.get(Calendar.HOUR_OF_DAY));
                int minute = app_preferences.getInt("minute_on",mcurrentTime.get(Calendar.MINUTE));
                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        String timeOn = String.format("%02d:%02d", selectedHour, selectedMinute);
                        timeron.setText(timeOn);
                        SharedPreferences.Editor editor = app_preferences.edit();
                        editor.putInt("hour_on", selectedHour);
                        editor.putInt("minute_on", selectedMinute);
                        editor.commit();
                        Intent intent1 = new Intent();
                        intent1.setAction("update");
                        intent1.putExtra("timer_on", selectedHour + "-" + selectedMinute);
                        sendBroadcast(intent1);

                        Intent intent2 = new Intent();
                        intent2.setAction("alarm");
                        sendBroadcast(intent2);
                    }
                }, hour, minute, true);//Yes 24 hour time
                mTimePicker.setTitle("Select On Time");
                mTimePicker.show();

            }
        });

        timeroff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar mcurrentTime = Calendar.getInstance();
                int hour = app_preferences.getInt("hour_off",mcurrentTime.get(Calendar.HOUR_OF_DAY));
                int minute = app_preferences.getInt("minute_off",mcurrentTime.get(Calendar.MINUTE));
                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        String timeOff = String.format("%02d:%02d", selectedHour, selectedMinute);
                        timeroff.setText(timeOff);
                        SharedPreferences.Editor editor = app_preferences.edit();
                        editor.putInt("hour_off", selectedHour);
                        editor.putInt("minute_off", selectedMinute);
                        editor.commit();
                        Intent intent1 = new Intent();
                        intent1.setAction("update");
                        intent1.putExtra("timer_off", selectedHour + "-" + selectedMinute);
                        sendBroadcast(intent1);

                        Intent intent2 = new Intent();
                        intent2.setAction("alarm");
                        sendBroadcast(intent2);
                    }
                }, hour, minute, true);//Yes 24 hour time
                mTimePicker.setTitle("Select Off Time");
                mTimePicker.show();

            }
        });
    }

    // Method that sets switches to boolean values sent from the service
    public void syncSwitches(boolean s1, boolean s2, boolean s3, boolean s4) {
        // Sets switches to database's values
        if (switch1 != null) { // Prevents from being run if service is restarted without activity running
            switch1.setChecked(s1);
            switch2.setChecked(s2);
            switch3.setChecked(s3);
            switch4.setChecked(s4);
        }
    }

    // Broadcast Receiver that handles the sync event in the service and sends the
    // boolean values from the intent to the syncSwitches method
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("sync")) {
                Log.e("Sync", "Received");
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    syncSwitches(extras.getBoolean("switch1State"),
                            extras.getBoolean("switch2State"),
                            extras.getBoolean("switch3State"),
                            extras.getBoolean("switch4State"));
                }
            }
        }
    };

}
