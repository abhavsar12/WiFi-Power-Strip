/*
 Basic ESP8266 MQTT example

 This sketch demonstrates the capabilities of the pubsub library in combination
 with the ESP8266 board/library.

 It connects to an MQTT server then:
  - publishes "hello world" to the topic "outTopic" every two seconds
  - subscribes to the topic "inTopic", printing out any messages
    it receives. NB - it assumes the received payloads are strings not binary
  - If the first character of the topic "inTopic" is an 1, switch ON the ESP Led,
    else switch it off

 It will reconnect to the server if the connection is lost using a blocking
 reconnect function. See the 'mqtt_reconnect_nonblocking' example for how to
 achieve the same result without blocking the main loop.

 To install the ESP8266 board, (using Arduino 1.6.4+):
  - Add the following 3rd party board manager under "File -> Preferences -> Additional Boards Manager URLs":
       http://arduino.esp8266.com/stable/package_esp8266com_index.json
  - Open the "Tools -> Board -> Board Manager" and click install for the ESP8266"
  - Select your ESP8266 in "Tools -> Board"

*/

#include <ESP8266WiFi.h>
#include <PubSubClient.h>

// Update these with values suitable for your network.

const char* ssid = "";
const char* password = "";
const char* mqtt_server = "raspberrypi";
const char* url = "tcp://raspberrypi:1883";
IPAddress ip(#,#,#,#);

const char* outTopic = "FromArduino";
const char* inTopic = "ToArduino";

const short int BUILTIN_LED1 = 2; //GPIO2
const short int RELAY1 = 16; // D0
const short int RELAY2 = 5;  // D1
const short int RELAY3 = 4;  // D2
const short int RELAY4 = 0;  // D3

WiFiClient espClient;
PubSubClient client(ip, 1883, espClient);
long lastMsg = 0;
char msg[50];
int value = 0;

int asciiToInt(char a) { // Converts char to ascii for handling purposes
  int b = (int)a - 48;
  return b;
}

void stateChange(char tempPlug, char tempState) {
  int plug = asciiToInt(tempPlug);
  int stateint = asciiToInt(tempState);
  bool state = stateint == 1;
  
  switch (plug) {
    case 1:      
      digitalWrite(RELAY1, !state); // ESP8266 has an active low
      Serial.println("1");
      break;
    case 2:
      digitalWrite(RELAY2, !state);
      Serial.println("2");
      break;
    case 3:
      digitalWrite(RELAY3, !state);
      Serial.println("3");
      break;
    case 4:
      digitalWrite(RELAY4, !state);
      Serial.println("4");
      break;
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived from [");
  Serial.print(topic);
  Serial.print("]: ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
  
//  digitalWrite(BUILTIN_LED1, LOW); 
//  delay(1000);
//  digitalWrite(BUILTIN_LED1, HIGH);

  if (length == 3) { // Length 3 means standard status change message
    // Parse message 'plug:state'
    stateChange((char)payload[0], (char)payload[2]);
    Serial.println("Standard change");
  }
  else if (length == 15) { // Length 15 means all 4 plug values are incoming ex. 1:0/2:0/3:1/4:0
    stateChange((char)payload[0], (char)payload[2]);
    stateChange((char)payload[4], (char)payload[6]);
    stateChange((char)payload[8], (char)payload[10]);
    stateChange((char)payload[12], (char)payload[14]);
    Serial.println("Sync change");
  }
}

void setup_wifi() {

  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Attempt to connect
    bool result = client.connect("ESP8266Client");
    if (result) {
      Serial.println("connected");
      // Once connected, publish an announcement...
      client.publish(outTopic, "Hello From ESP8266");
      // ... and resubscribe
      client.subscribe(inTopic);
      client.publish(outTopic, "Sync"); // Asks server to send back all outlet values
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

void setup() {
  pinMode(BUILTIN_LED1, OUTPUT);     // Initialize pins as outputs
  pinMode(RELAY1, OUTPUT);
  pinMode(RELAY2, OUTPUT);
  pinMode(RELAY3, OUTPUT);
  pinMode(RELAY4, OUTPUT);
  
  Serial.begin(115200);
  setup_wifi();
  client.setCallback(callback);
}

void loop() {

  if (!client.connected()) {
    reconnect();
  }
  client.loop();

//  long now = millis();
//  if (now - lastMsg > 2000) {
//    lastMsg = now;
//    ++value;
//    snprintf (msg, 75, "hello world #%ld", value);
//    Serial.print("Publish message: ");
//    Serial.println(msg);
//    client.publish(outTopic, msg);
//  }
}
