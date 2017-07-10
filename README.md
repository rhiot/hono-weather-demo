# hono-weather-demo

This code is being built as an example of how the Eclipse Hono operates by sending and recieving weather data.

If you're interested in Eclipse Hono, check out: http://www.eclipse.org/hono/

## Synopsis 

Before the demo can be run, a Hono server needs to be started up. Hono is in constant development, and hence the version located on the main hono page is constantly in snapshot. Our example runs on the 0.5.M5 release of hono, whiche can be found [here](https://github.com/eclipse/hono/tree/0.5-M5).

The demo is built off of the Hono sender and consumer examples located in the user guide section of the eclipse hono page.

This example has been modified to gather real world weather data using the [yahoo weather service](http://developer.yahoo.com/weather/). To integrate the weather service into the example, we use the java wrapper developed [here](https://github.com/fedy2/yahoo-weather-java-api). 

The current iteration of the demo runs on localhost, so make sure your hono server is running on localhost as well. The project is also configured to run on default hono server configurations.

## Instructions
Before data can start being sent to our hono server, we're going to need something to listen at the other end. Let's get our weather-data-consumer started. **If you've changed any of the configurations from the Hono server, you'll need to add VM arguments to our consumer and sender. See instructions below.**   

> java -jar weather-data-consumer/target/weather-data-consumer-0.1-SNAPSHOT.jar

Once the consumer is running properly, go ahead and start up the weather-data-sender.

> java -jar weather-data-sender/target/weather-data-sender-0.1-SNAPSHOT.jar

Sender will go ahead and authenticate all of the devices you've added. Once it starts sending data, go ahead and switch back to consumer. There you can see all of the incoming weather data being displayed to the screen. 
