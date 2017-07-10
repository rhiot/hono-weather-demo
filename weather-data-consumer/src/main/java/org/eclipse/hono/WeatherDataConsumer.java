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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.MessageHelper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.concurrent.CountDownLatch;

public class WeatherDataConsumer {

    //Sets the tenantID to VM arguments, defaults to "DEFAULT_TENANT".
    private final String TENANT_ID = System.getProperty("consumer.tenant","DEFAULT_TENANT");

    //Vertx instance, allows for AMQP connections to be created.
    private final Vertx vertx = Vertx.vertx();

    private final HonoClient honoClient;

    //Latch is a synchronization aid that allows for threads to be held until necessary processes are complete.
    private final CountDownLatch latch;

    /**
     * WeatherDataConsumer is the constructor for the WeatherDataConsumer class. Defines the honoClient by creating
     * a vertx AMQP connection with the desired hono server. Then latch is created with only a single waiting time
     * set for count.
     */
    private WeatherDataConsumer() {
        //Establishes honoClient by making an AMQP connection to the hono server.
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        //Sets the host to the VM argument, defaults to "localhost".
                        .host(System.getProperty("consumer.host","localhost"))
                        //Sets the port to the VM argument, defaults to "15671".
                        .port(Short.parseShort(System.getProperty("consumer.port","15671")))
                        //Sets the username to the VM arguments, defaults to "user1@HONO".
                        .user(System.getProperty("consumer.user","user1@HONO"))
                        //Sets the password to VM arguments, defaults to "pw".
                        .password(System.getProperty("consumer.pw","pw"))
                        //Sets the certs pathway to VM arguments, defaults to "certs/trusted-certs.pem".
                        .trustStorePath(System.getProperty("consumer.certsPath","certs/trusted-certs.pem"))
                        .disableHostnameVerification()
                        .build());
        //Sets latch to have a count value of 1, meaning one countDown needs to be invoked for latch to open.
        latch = new CountDownLatch(1);
    }

    /**
     * consumeWeatherData connects the WeatherDataConsumer to the hono server to read from TENANT_ID.
     * @throws Exception N/A
     */
    private void consumeWeatherData() throws Exception {
        final Future<MessageConsumer> consumerFuture = Future.future();

        consumerFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry consumer : " + result.cause());
            }
            latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();

        honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
                    honoClient.createTelemetryConsumer(TENANT_ID,
                            msg -> handleWeatherMessage(msg), consumerFuture.completer());
                },
                consumerFuture);

        latch.await();
        //If consumer connects, then reads information.
        if (consumerFuture.succeeded())
            System.in.read();
        //Closes AMQP connection with hono server.
        vertx.close();
    }

    /**
     * handleWeatherMessage takes received telemetry message and processes it to be printed to command line.
     * @param msg Telemetry message received through hono server.
     */
    private void handleWeatherMessage(final Message msg) {
        final Section body = msg.getBody();
        //Ensures that message is Data (type of AMQP messaging). Otherwise exits method.
        if (!(body instanceof Data))
            return;
        //Gets deviceID.
        final String deviceID = MessageHelper.getDeviceId(msg);
        //Creates JSON parser to read input telemetry weather data. Prints data to console output.
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(((Data) msg.getBody()).getValue().toString());
            JSONObject payload = (JSONObject) obj;
            System.out.println(new StringBuilder("Device: ").append(deviceID).append("; Location: ").
                    append(payload.get("location")).append("; Temperature:").append(payload.get("temperature")));
        } catch (ParseException e) {
            System.out.println("Data was not sent in a readable way. Check telemetry input.");
            e.printStackTrace();
        }
    }


    /**
     * Main method for WeatherDataConsumer, creates instance WeatherDataConsumer, and prepares it to consume telemetry
     * data.
     * @param args Commandline string argument. Not used in WeatherDataConsumer.
     * @throws Exception N/A
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream consumer...");
        //Creates instance WeatherDataConsumer
        WeatherDataConsumer weatherDataConsumer = new WeatherDataConsumer();
        //Prepares created WeatherDataConsumer to consume telemetry data. Method below.
        weatherDataConsumer.consumeWeatherData();
        System.out.println("Finishing downstream consumer.");
    }
}
