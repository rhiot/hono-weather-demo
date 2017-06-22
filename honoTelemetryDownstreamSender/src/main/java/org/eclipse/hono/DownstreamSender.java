package org.eclipse.hono;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * DownstreamSender is the constructor for the DownstreamSender class. Creates telemetry connector and registers
 * DEVICE_ID under TENANT_ID in hono. Messages are sent from DownstreamSender to that respository on the hono server.
 */
public class DownstreamSender {
    // Creates connection ip and port. Change from "localhost" if hono server is registered on a different ip.
    public static final String HONO_HOST = "localhost";
    public static final int    HONO_PORT = 5671;
    // Creates publishing space and device
    public static final String TENANT_ID = "DEFAULT_TENANT";
    public static final String DEVICE_ID = "4712";
    // How many messages to be sent for each send
    public static final int COUNT = 50;
    // Creates vertx and honoclient instance
    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;
    // Creates latch to hold messages until connection established
    private final CountDownLatch latch;
    private RegistrationClient registrationClient;
    MessageSender sender;

    /**
     * DownstreamSender class constrcutor.
     * Initializes:
     * - hono client instance
     * - latch instance
     */
    public DownstreamSender() {
        Future<HonoClient> honoTracker = Future.future();
        Future<MessageSender> setupTracker = Future.future();
        //Sets latch with a count of 1. countDown() needs to be called once on latch for it to open.
        latch = new CountDownLatch(1);
        setupTracker.setHandler(r -> {
                    if (r.succeeded()) {
                        sender = setupTracker.result();
                        latch.countDown();
                    } else {
                        System.err.println("cannot connect to Hono" + r.cause());
                    }
                });
        // Initializing hono client
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HONO_HOST)
                        .port(HONO_PORT)
                        .user("hono-client")
                        .password("secret")
                        .trustStorePath("certs/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        honoClient.connect(new ProtonClientOptions(), honoTracker.completer());
        honoTracker.compose(hono -> {
            // step 2
            // create client for registering device with Hono
            Future<RegistrationClient> regTracker = Future.future();
            hono.createRegistrationClient("DEFAULT_TENANT", regTracker.completer());
            return regTracker;
        }).compose(regClient -> {

            Future<RegistrationResult> result = Future.future();
            result.setHandler(regResult -> {
                System.out.println("!!!! Registered");
                if (regResult.succeeded()) {

                    honoClient.getOrCreateTelemetrySender(TENANT_ID, setupTracker.completer());

                } else {
                    System.out.println("!!!!! FAIL");
                    regResult.cause().printStackTrace();
                }

            });

            //TODO 1. CHECK IF DEVICE IS REGISTERED BEFORE CALLING REGISTER
            //TODO 2. CREATE A DIFFERENT APP THAT WILL REGISTER DEVICES
            //TODO 3. START MULTIPLE SENDERS AND MULTIPLE DEVICES THAT WILL SEND TELEMETRY EVERY COUPLE SECONDS
            //TODO 4. TRY TO MAKE IT A TEMP SENSOR SIMULTAOR ... EVERY COUPLE SECOND SEND RANDOM INT


            regClient.register(DEVICE_ID, null, result.completer());

        }, setupTracker);
    }

    /**
     * Main method for DownstreamSender. Creates DownstreamSender instance, and prepares it to send telemetry data.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream sender...");
        //Creates DownstreamSender instance.
        DownstreamSender downstreamSender = new DownstreamSender();
        downstreamSender.sendTelemetryData();
        System.out.println("Finishing downstream sender.");
    }

    /**
     * sendTelemetryData sends telemetry data to hono server once latch is opened. Sends 100 messages.
     * @throws Exception 
     */
    private void sendTelemetryData() throws Exception {
        //Holds latch closed until coundDown() has been called enough to overcome count value (once).
        latch.await();
        //Sends 100 messages to hono server.
        for(int i = 1; i <= 100; i++) {
            sendSingleMessage(sender, i);
        }
        //Closes AMQP connection with hono server.
        vertx.close();
    }

    /**
     * sendSingleMessage sends a message
     * @param ms
     * @param value
     */
    private void sendSingleMessage(MessageSender ms, int value) {
        CountDownLatch messageSenderLatch = new CountDownLatch(1);
        System.out.println("Sending message... #" + value);

        final Map<String, Object> properties = new HashMap<>();
        properties.put("my_prop_string", "I'm a string");
        properties.put("my_prop_int", 10);

        ms.send(DEVICE_ID, properties, "myMessage" + value, "text/plain",
                v -> {
                    messageSenderLatch.countDown();
                });
        try {
            messageSenderLatch.await();
        } catch (InterruptedException e) {
        }
    }
}

