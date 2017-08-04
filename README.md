# hono-weather-demo

Due to the Hono M6 update, the current iteration of this code simply is not working. 


This code is being built as an example of how the Eclipse Hono operates by sending and recieving weather data.

If you're interested in Eclipse Hono, check out: http://www.eclipse.org/hono/

## Synopsis 

Before the demo can be run, a Hono server needs to be started up. Hono is in constant development, and hence the version located on the main hono page is constantly in snapshot. Our example runs on the 0.5.M5 release of hono, whiche can be found [here](https://github.com/eclipse/hono/tree/0.5-M5).

The demo is built off of the Hono sender and consumer examples located in the user guide section of the eclipse hono page.

This example has been modified to gather real world weather data using the [yahoo weather service](http://developer.yahoo.com/weather/). To integrate the weather service into the example, we use the java wrapper developed [here](https://github.com/fedy2/yahoo-weather-java-api). 

The current iteration of the demo runs on localhost, so make sure your hono server is running on localhost as well. The project is also configured to run on default hono server configurations.

## Instructions
Once you've gotten the project downloaded into a local repository from Github, go ahead and run 

> mvn clean install

Before data can start being sent to our hono server, we're going to need something to listen at the other end. Let's get our weather-data-consumer started. **If you've changed any of the configurations from the Hono server, you'll need to add VM arguments to our consumer and sender. See instructions below.**   

> java -jar weather-data-consumer/target/weather-data-consumer-0.1-SNAPSHOT.jar

Once the consumer is running properly, go ahead and start up the weather-data-sender.

> java -jar weather-data-sender/target/weather-data-sender-0.1-SNAPSHOT.jar

Sender will go ahead and authenticate all of the devices you've added. Once it starts sending data, go ahead and switch back to consumer. There you can see all of the incoming weather data being displayed to the screen. 

## Configuration
To change the default configurations easily within the project, we've made several key variables accessible via VM arguements. We're going to break up our arguments for each class. The variable after the dash is the argument name, the variable in the parenthesis is the default variable, and the variable description follows.

### WeatherDataConsumer
- consumer.tenant ("DEFAULT_TENANT") -- The tenant that the consumer is listening at on the server. 
- consumer.host ("localhost") --  The IP of the hosting server that the consumer is reading from. 
- consumer.port ("15671") -- The port number that the consumer is listening at on the host.
- consumer.user ("user1@HONO") -- The username registered with the server.
- consumer.pw ("pw") -- The password associated with said username. 
- consumer.certsPath ("certs/trusted-certs.pem") -- The pathway to the certs file to authenticate sending/receiving messages from the server. 

### WeatherDataDrvier
- sender.tenant ("DEAULT_TENANT") -- The tenant that the sender is listening at on the server.
- sender.host ("locatlhost") -- The IP of the hosting server that the sender is sending messages to.
- sender.port ("5671") -- The port number that the sender is sending to on the host.
- driver.locations ("2478307", "2378134", "2394734", "30079", "30074", "41415", "2440351", "2440349", "2440350",
                "2440344", "2440342", "2440347", "2440346", "2440345", "2440343", "2440341", "2440364", "28747216",
                "56854933", "967073", "676757", "2375805", "818881", "2375811", "584166", "20106400", "782055") -- Locations that the user wants to record weather data from.

### WeatherDataSender
- sender.tenant ("DEAULT_TENANT") -- The tenant that the sender is listening at on the server.
- sender.host ("locatlhost") -- The IP of the hosting server that the sender is sending messages to.
- sender.port ("5671") -- The port number that the sender is sending to on the host.
- sender.user ("hono-client") -- The username registered with the server.
- sender.pw ("secret") -- The password associated with said username.
- sender.certsPath ("certs/trusted-certs.pem") -- The pathway to the certs file to authenticate sending/receiving messages from the server.
