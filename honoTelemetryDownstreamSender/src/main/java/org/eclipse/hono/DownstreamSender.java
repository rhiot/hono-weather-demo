package org.eclipse.hono;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactoryImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;


public class DownstreamSender {
    // Creates connection ip and port.
    public static final String HONO_HOST = "localhost";
    public static final int    HONO_PORT = 5671;
    // Creates publishing space and device
    public static final String TENANT_ID = "DEFAULT_TENANT";
    public static final String DEVICE_ID = "4711";
    // How many messages to be sent for each send
    public static final int COUNT = 50;
    // Creates vertx and honoclient instance
    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;
    // Creates latch to hold messages until connection established
    private final CountDownLatch latch;

    /**
     * DownstreamSender class constrcutor.
     * Initializes:
     * - hono client instance
     * - latch instance
     */
    public DownstreamSender() {
        Future<HonoClient> honoTracker = Future.future();
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
            hono.createRegistrationClient("tenant", regTracker.completer());
            return regTracker;
        }).compose(regClient -> {
            // step 3
            // create client for sending telemetry data to Hono server
            registrationClient = regClient;
            createProducer(TEST_TENANT_ID, setupTracker.completer());
        }, setupTracker);
        latch = new CountDownLatch(1);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream sender...");
        DownstreamSender downstreamSender = new DownstreamSender();
        downstreamSender.sendTelemetryData();
        System.out.println("Finishing downstream sender.");
    }


    private void sendTelemetryData() throws Exception {
        final Future<MessageSender> senderFuture = Future.future();

        senderFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry sender : " + result.cause().getMessage());
            }
            latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();
        honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
                    honoClient.getOrCreateTelemetrySender(TENANT_ID, senderFuture.completer());
                },
                senderFuture);

        latch.await();

        if (senderFuture.succeeded()) {
            MessageSender ms = senderFuture.result();

//            IntStream.range(0, COUNT).forEach(value -> {
                sendSingleMessage(ms, 1);
//            });
        }

        vertx.close();
    }

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

