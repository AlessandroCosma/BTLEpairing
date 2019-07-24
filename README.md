# BTLEpairing
Project for testing Bluetooth LE in Android Things

The project consists of two applications:
* SmartphoneServer: the Android app which acts as a server.
* PicoPiClient: the Android Things app for PicoPi.


This project is inspired by an Android Things application named ButtonThings, available on GitHub 6 and developed by Brandon Roberts, an Hackster.io community member. Indeed, our two apps interacts each other in a similar way as in the ButtonThings application and they use parts of its code.


The role of Master is played by the PicoPi: it starts scanning for a fixed Android device which advertises, in order to start a connection. This time, there is not a list of accessible devices, because the PicoPi is treated as an IoT device, thus without a display (even if a small optional LCD display is contained in the kit). When the target device is found, a connection is initialized: if the devices were already paired, the connection proceeds normally, else a pairing phase begins, with a pairing request sent by the PicoPi.


The role of Slave is played by the smartphone: it broadcasts information about its service and its characteristics and waits for the incoming connection requests. After the connection is established, using a simple button on the display of the smartphone, it is possible to transimt on/off commands over the BLE channel, in order to turn on or off a LED in the PicoPi.
