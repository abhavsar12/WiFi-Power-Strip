# WiFi Power Strip

The WiFi Power Strip in an Internet of Things (IoT) device that allows for an android application to control the state of outlets on a modified 4 outlet power strip. The system works with an android app sending commands to a Raspberry Pi server running a python script to handle the commands, and sending those commands to a NodeMcu ESP8266 module (a third party arduino nano with added wifi) to control a simple 4 channel relay for each of the outlets on the strip.

The system runs off of a MQTT (Message Queue Telemetry Transport) messaging protocal that receives and send messages to the approriapte client that is listening/subscribed. A popular version of MQTT is Mosquitto, which is run on a Raspberry Pi alongside the afromentions python script that connects to the broker and receives handles all messages.

Additionally, all data is stored on a MySQL database that keeps track of current states of outlets and other settings explained below.

# Features

This Power Strip has added features that add to the functionality of the device beyond manual control of all outlets. As of this version of the system, the first outlet is run on a feature called "Max Battery Charge." Essentially, cellphone batteries should not be charged to a full charge every night, and should not remain plugged in after it has completed its charge. This results in overcharging that significantly reduces the life of the phone battery.

To combat this, the user is able to select which battery percentage they would like their phone charge up to. Through a service running on the android application, the phone sends battery data periodically. The max charge percentage is stored in the MySQL database as previously mentioned, and once the required battery charge is met, the outlet automatically shuts off until the battery level drops below the user specified percentage.

Addtionaly, the second outlet has a programmable timer that allows, for example, a light or fan to only run for a certain amount of time. This is useful for vacation timers, power saving options, and convenice to prevent unnecessary usage of energy. Again, periodic time data is sent to the server, checking the time against the timer data stored on the database which then enables and disables the outlet accordingly.

Finally, throughout the code there will be various instances of the word "sync." This is a useful feauture where, whenever either the ESP8266 is reset, or the android application is reopened, each device sends a message "sync" request the state of all outlets so that it may reflect the most current data of the outlets. This is done by pulling the state data from the database that the python script reads, packages, and sends to be handled by the appropriate device.
