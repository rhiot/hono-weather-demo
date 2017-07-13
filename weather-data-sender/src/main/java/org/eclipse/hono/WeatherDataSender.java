/**
 *  Copyright 2005-2017 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.eclipse.hono;

import com.github.fedy2.weather.YahooWeatherService;
import com.github.fedy2.weather.data.Channel;
import com.github.fedy2.weather.data.unit.DegreeUnit;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationResult;
import org.json.simple.JSONObject;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class WeatherDataSender {
    // Creates publishing space and device
    private String DEVICE_ID;
    // Creates vertx and honoclient instance
    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;
    // Creates latch to hold messages until connection established
    private final CountDownLatch latch;
    // MessageSender sends the messages through the vertx connection.
    private MessageSender sender;
    // City of weather data.
    private String city;
    // Temperature in city.
    private int temp;

    /**
     * WeatherDataSender class constrcutor.
     * Initializes:
     * - hono client instance
     * - latch instance
     */
    public WeatherDataSender(String deviceID) throws JAXBException {
        //Sets tennantID to VM arguments, defaults to "DEFAULT_TENANT".
        String TENANT_ID = System.getProperty("sender.tenant","DEFAULT_TENANT");
        //Sets deviceID for WeatherDataSender.
        DEVICE_ID = deviceID;
        //Sets latch with a count of 1. countDown() needs to be called once on latch for it to open.
        latch = new CountDownLatch(1);

        //Keeps weather updated.
        weatherUpdate();
        vertx.setPeriodic(60000, gather -> {
            System.out.println(new StringBuilder("Weather updated for: ").append(deviceID));
            weatherUpdate();
        });

        Future<MessageSender> setupTracker = Future.future();
        setupTracker.setHandler(r -> {
            if (r.succeeded()) {
                sender = setupTracker.result();
                latch.countDown();
            } else {
                System.err.println("cannot connect to Hono" + r.cause());
            }
        });
        // Initializing hono client
        Future<HonoClient> honoTracker = Future.future();
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        //Sets the host to VM arguments, defaults to "localhost".
                        .host(System.getProperty("sender.host","localhost"))
                        //Sets the port to VM arguments, defaults to "5671".
                        .port(Integer.parseInt(System.getProperty("sender.port","5671")))
                        //Sets the user to VM arguments, defaults to "hono-client".
                        .user(System.getProperty("sender.user","hono-client"))
                        //Sets the password to VM arguments, defaults to "secret".
                        .password(System.getProperty("sender.password","secret"))
                        //Sets the certs pathway to VM arguments, defaults to "certs/trusted-certs.pem".
                        .trustStorePath(System.getProperty("sender.certsPath","certs/trusted-certs.pem"))
                        .disableHostnameVerification()
                        .build());
        honoClient.connect(new ProtonClientOptions(), honoTracker.completer());
        honoTracker.compose(hono -> {
            // step 2
            // create client for registering device with Hono
            Future<RegistrationClient> regTracker = Future.future();
            hono.createRegistrationClient(TENANT_ID, regTracker.completer());
            return regTracker;
        }).compose((RegistrationClient regClient) -> {
            //Checks to see if device has already been registered, uses device is true, otherwise registers device.
            Future<RegistrationResult> checker = Future.future();
            Future<RegistrationResult> result = Future.future();
            checker.setHandler(checkResult -> {
                System.out.println(checkResult.result().getStatus());
                if (checkResult.result().getStatus() != 200){
                    result.setHandler(regResult -> {
                        System.out.println("Telemetry sender at device id " + deviceID + " has been created.");
                        if (regResult.succeeded()) {
                            honoClient.getOrCreateTelemetrySender(TENANT_ID, setupTracker.completer());
                        } else {
                            System.out.println("Telemetry sender creation has failed.");
                            regResult.cause().printStackTrace();
                        }
                    });
                    regClient.register(deviceID, null, result.completer());
                } else {
                    System.out.println("Sender has already been created. Using existing telemetry sender.");
                    honoClient.getOrCreateTelemetrySender(TENANT_ID, setupTracker.completer());
                }
            });
            regClient.get(deviceID, checker.completer());
        }, setupTracker);
    }

    /**
     * weatherUpdate keeps the weather updated with the Yahoo weather service, without overloading the messages
     * send to the service.
     */
    private void weatherUpdate() {
        //Creates weather service object to get weather from yahoo weather service using a WOEID value.
        YahooWeatherService service = null;
        try {
            service = new YahooWeatherService();
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        //Currently monitors weather in Newcastle, England.
        final Channel[] channel = new Channel[1];
        YahooWeatherService finalService = service;
            try {
                channel[0] = finalService.getForecast(DEVICE_ID, DegreeUnit.CELSIUS);
                city = channel[0].getLocation().getCity();
                temp = channel[0].getItem().getCondition().getTemp();
            } catch (Exception e) {
                System.out.println("Something has gone wrong with the weather service.");
                // Needed for debugging.
//                e.printStackTrace();
            }
    }

    /**
     * sendWeatherData sends telemetry data to hono server once latch is opened. Sends 100 messages.
     * @throws Exception Any exception that could be called. We don't care for them.
     */
    public void sendWeatherData() throws Exception {
        //Holds latch closed until coundDown() has been called enough to overcome count value (once).
        latch.await();
        final int[] i = {1};
        //Runs send message every 1 seconds.
        vertx.setPeriodic(5000, id -> {
            try {
                //Sends weather data from specified location.
                sendWeatherMessage(sender, i[0], Integer.parseInt(DEVICE_ID));
            } catch (Exception e) {
                e.printStackTrace();
            }
            i[0]++;
        });
    }

    /**
     * sendWeatherMessage sends a message.
     * @param ms MessageSender that messages are send through.
     * @param value Number of message to be send.
     * @param woeid Location of area to be checked.
     */
    private void sendWeatherMessage(MessageSender ms, int value, int woeid) throws Exception {
        //Creates new latch to hold message send until all of the information is prepared.
        CountDownLatch messageSenderLatch = new CountDownLatch(1);
        System.out.println("Device " + woeid + "  - Sending message... #" + value);
        //Empty properties hash map.
        final Map<String, Object> properties = new HashMap<>();
        //Creates JSON string to send location and temperature of each weather reading.
        JSONObject payload = new JSONObject();
        payload.put("location", city);
        payload.put("temperature", temp);
        //Sends message to consumer
        ms.send(DEVICE_ID, properties, payload.toJSONString(), "text/JSON",
                v -> messageSenderLatch.countDown());
        try {
            messageSenderLatch.await();
        } catch (InterruptedException e) {
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * Main method for WeatherDataSender. Creates WeatherDataSender instance, and prepares it to send telemetry data.
     * @param args Default main string array.
     * @throws Exception Any exception that could be called. We don't care for them.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream sender...");
        //Creates WeatherDataSender instance.
        WeatherDataSender weatherDataSender = new WeatherDataSender("30079");
        //Starts sending telemetry data for Newcastle, England.
        weatherDataSender.sendWeatherData();
        System.out.println("Finishing downstream sender.");
    }
}

