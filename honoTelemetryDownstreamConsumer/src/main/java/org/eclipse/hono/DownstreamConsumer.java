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

/**
 * DownstreamConsumer connects to a locally hosted eclipse hono server, and reads any telemetry data that is sent to
 * the server. To get a server started see here: https://www.eclipse.org/hono/getting-started/
 *
 * DownstreamConsumer reads by directly connecting to the hono server, it is not read via the rest adapter or the
 * mqtt adapter.
 *
 */
public class DownstreamConsumer {
    //private from "localhost" to designated ip if you want to run DownstreamConsumer on a different ip.
    private final String QPID_ROUTER_HOST = System.getProperty("consumer.host","localhost");
    //Public port to the docker 15671 container, which is the dispatch router container qrouter.
    private final short  QPID_ROUTER_PORT = Short.parseShort(System.getProperty("consumer.port","15671"));

    //Location for devices to be registered.
    private final String TENANT_ID = System.getProperty("consumer.tenant","DEFAULT_TENANT");

    //Vertx instance, allows for AMQP connections to be created.
    private final Vertx vertx = Vertx.vertx();

    private final HonoClient honoClient;

    //Latch is a synchronization aid that allows for threads to be held until necessary processes are complete.
    private final CountDownLatch latch;

    /**
     * DownstreamConsumer is the constructor for the DownstreamConsumer class. Defines the honoClient by creating
     * a vertx AMQP connection with the desired hono server. Then latch is created with only a single waiting time
     * set for count.
     */
    private DownstreamConsumer() {
        //Establishes honoClient by making an AMQP connection to the hono server.
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(QPID_ROUTER_HOST)
                        .port(QPID_ROUTER_PORT)
                        //Username and password
                        .user("user1@HONO")
                        .password("pw")
                        .trustStorePath("certs/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        //Sets latch to have a count value of 1, meaning one countDown needs to be invoked for latch to open.
        latch = new CountDownLatch(1);
    }

    /**
     * consumeTelemetryData connects the DownstreamConsumer to the hono server to read from TENANT_ID.
     * @throws Exception N/A
     */
    private void consumeTelemetryData() throws Exception {
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
                            msg -> handleTelemetryMessage(msg), consumerFuture.completer());
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
     * handleTelemetryMessage takes received telemetry message and processes it to be printed to command line.
     * @param msg Telemetry message received through hono server.
     */
    private void handleTelemetryMessage(final Message msg) {
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
     * Main method for DownstreamConsumer, creates instance DownstreamConsumer, and prepares it to consume telemetry
     * data.
     * @param args Commandline string argument. Not used in DownstreamConsumer.
     * @throws Exception N/A
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream consumer...");
        //Creates instance DownstreamConsumer
        DownstreamConsumer downstreamConsumer = new DownstreamConsumer();
        //Prepares created DownstreamConsumer to consume telemetry data. Method below.
        downstreamConsumer.consumeTelemetryData();
        System.out.println("Finishing downstream consumer.");
    }
}
