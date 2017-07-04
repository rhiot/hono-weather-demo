# HonoScalabilityTest

This code is being built as an example of how the Eclipse Hono operates by sending and recieving weather data.

If you're interested in Eclipse Hono, check out: http://www.eclipse.org/hono/

## Synopsis 

Before the HonoScalabilityTest can be run, a Hono server needs to be started up. Hono is in constant development, and hence the version located on the main hono page is constantly in snapshot. Our example runs on the 0.5.M5 release of hono, whiche can be found [here](https://github.com/eclipse/hono/tree/0.5-M5).

The HonoScalabilityTest is built off of the Hono sender and consumer examples located in the user guide section of the eclipse hono page.

This example has been modified to gather real world weather data using the [yahoo weather service] (http://developer.yahoo.com/weather/). To integrate the weather service into the example, we use the java wrapper developed [here](https://github.com/fedy2/yahoo-weather-java-api). 

The current iteration of the HonoScalability test runs on localhost, so make sure your hono server is running on localhost as well. The project is also configured to run on default hono server configurations.

## Instructions

Run the DownstreamConsumer.java file, this creates a consumer to read the telemetry data being sent to the hono server. Then run the DownstreamSender.java file to start sending data to the server. 
