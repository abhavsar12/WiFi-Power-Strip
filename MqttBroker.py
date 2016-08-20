import paho.mqtt.client as mqtt
import MySQLdb
import datetime

# Wrapper function that updates the database given the field name,
# plug number, and value all as strings since mysql commands are
# taken as a string command
def updateDBInt(field, plug, value):
    command = "UPDATE outlets SET "+field+"="+value+" WHERE plug="+plug+";"
    curs.execute(command)
    db.commit()

def updateDBTime(field, plug, value):
    command = "UPDATE outlets SET "+field+"=CONCAT('"+value+"') WHERE plug="+plug+";"
    curs.execute(command)
    db.commit()

# Wrapper function that gets data from the database, passing in
# a string for the field name and an int for the plug number
def getDB(field, plug):
    command = "SELECT "+field+" FROM outlets;"
    curs.execute(command)
    db.commit() # Refresh database info
    values = curs.fetchall()
    value = values[plug-1] # Subtract 1 bc out of bounds error
    return value[0]

def getAllStatus():
    return ("1:"+str(getDB('currentstatus', 1))+
                "/2:"+str(getDB('currentstatus', 2))+
                "/3:"+str(getDB('currentstatus', 3))+
                "/4:"+str(getDB('currentstatus', 4)))
            
# Function that sends a string message to the ESP8266
# indicating whether to turn an outlet on or off.
# This also updates the database with the correct status
def changeStatus(outlet, status):
    if status == '0':
        print("Turning port " + outlet + " off")
    if status == '1':
        print("Turning port " + outlet + " on")
    client.publish("ToArduino", outlet + ":" + status)
    updateDBInt('currentstatus', outlet, status)

def compareTimer(timeStamp):
    data = timeStamp.split("-")
    stampHour = int(data[0])
    stampMinute = int(data[1])
    curTime = datetime.time(hour = stampHour, minute = stampMinute)

    # As of now, timer functionality is only on outlet 2, so timer data
    # is pulled from plug 2
    timerOnTimeDelta = getDB("timer_on", 2)
    timerOn = (datetime.datetime.min + timerOnTimeDelta).time()

    timerOffTimeDelta = getDB("timer_off", 2)
    timerOff = (datetime.datetime.min + timerOffTimeDelta).time()

    result = curTime >= timerOn and curTime < timerOff
    return result

# Connects to the Mqtt broker and subscribes to listen to both
# the arduino(ESP8266) and the android application
def on_connect(cleint, userdata, rc):
    print("Connected with result code: " + str(rc))
    client.subscribe("FromAndroid")
    client.subscribe("FromArduino")

# Callback function that handles a variety of messages from
# the user
#
# 
def on_message(client, userdata, msg):
    message = str(msg.payload)
    print(msg.topic + " " + message)
    # All data is sent with : delimeters which allows for
    # simple parsing of data
    # data[0] will be the case, and the following elements
    # in the list will be used as data to be analyzed
    data = message.split(":")

    # Cases to be handled
    if data[0] == "State":
        changeStatus(data[1], data[2])
        
    elif data[0] == "Sync":
        allStatus = getAllStatus()
        # Sends to appropriate device depending on
        # where the message came from
        if msg.topic == 'FromArduino':
            client.publish("ToArduino", allStatus)
        if msg.topic == 'FromAndroid':
            client.publish("ToAndroid", allStatus)
            
    elif data[0] == "Current":
        maxBat = getDB('stopchargingpercent', 1)
        curBat = data[1]
        curBat = int(float(curBat))
        curTime = data[2]
        if curBat>=maxBat:
            # As of now, only the first outlet is set to
            # limit the battery charge protection feature
            changeStatus('1', '0') # Turns outlet 1 off (State = 0)
            allStatus = getAllStatus()
        if compareTimer(curTime):
            changeStatus('2', '1')
            allStatus = getAllStatus()
        else:
            changeStatus('2', '0')
            allStatus = getAllStatus()
        client.publish("ToAndroid", allStatus)

    elif data[0] == "Update":
        if data[1] == "Battery":
            maxBatteryLevel = data[2]
            updateDBInt("stopchargingpercent", '1', maxBatteryLevel)
        elif data[1] == "Timer On":
            temp = data[2]
            timeData = temp.split("-")
            time = timeData[0]+":"+timeData[1]
            updateDBTime("timer_on", '2', time)
        elif data[1] == "Timer Off":
            temp = data[2]
            timeData = temp.split("-")
            time = timeData[0]+":"+timeData[1]
            updateDBTime("timer_off", '2', time)
            

#MySQL setup
db = MySQLdb.connect("localhost", "user", "123", "powerstrip")
curs=db.cursor()

#Mqtt setup
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

client.connect("raspberrypi", 1883, 60)
client.publish("ToArduino", "Hello from Server")

client.loop_forever()
