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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * DownstreamSender is the constructor for the DownstreamSender class. Creates telemetry connector and registers
 * DEVICE_ID under TENANT_ID in hono. Messages are sent from DownstreamSender to that repository on the hono server.
 */
public class DownstreamSender {
    // Creates connection ip and port. Change from "localhost" if hono server is registered on a different ip.
    private final String HONO_HOST = System.getProperty("sender.host","localhost");
    private final int    HONO_PORT = Integer.parseInt(System.getProperty("sender.port","5671"));
    // Creates publishing space and device
    private String TENANT_ID = System.getProperty("sender.tenant","DEFAULT_TENANT");
    private String DEVICE_ID;
    // Creates vertx and honoclient instance
    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;
    // Creates latch to hold messages until connection established
    private final CountDownLatch latch;
    private MessageSender sender;

    /**
     * DownstreamSender class constrcutor.
     * Initializes:
     * - hono client instance
     * - latch instance
     */
    public DownstreamSender(String deviceID) {
        //Sets deviceID for DownstreamSender.
        DEVICE_ID = deviceID;
        System.out.println(DEVICE_ID);
        //Sets latch with a count of 1. countDown() needs to be called once on latch for it to open.
        latch = new CountDownLatch(1);

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
     * sendTelemetryData sends telemetry data to hono server once latch is opened. Sends 100 messages.
     * @throws Exception Any exception that could be called. We don't care for them.
     */
    public void sendTelemetryData() throws Exception {
        //Holds latch closed until coundDown() has been called enough to overcome count value (once).
        latch.await();
        final int[] i = {1};
        //Runs send message every 1 seconds.
        vertx.setPeriodic(5000, id -> {
            try {
                //Sends weather data from specified location.
                sendSingleMessage(sender, i[0], Integer.parseInt(DEVICE_ID));
            } catch (Exception e) {
                e.printStackTrace();
            }
            i[0]++;
        });
    }

    /**
     * sendSingleMessage sends a message.
     * @param ms MessageSender that messages are send through.
     * @param value Number of message to be send.
     * @param woeid Location of area to be checked.
     */
    private void sendSingleMessage(MessageSender ms, int value, int woeid) throws Exception {
        //Creates new latch to hold message send until all of the information is prepared.
        CountDownLatch messageSenderLatch = new CountDownLatch(1);
        System.out.println("Device " + woeid + "  - Sending message... #" + value);
        //Creates weather service object to get weather from yahoo weather service using a WOEID value.
        YahooWeatherService service = new YahooWeatherService();
        //Currently monitors weather in Newcastle, England.
        Channel channel = service.getForecast("" + woeid, DegreeUnit.CELSIUS);
        //Empty properties hash map.
        final Map<String, Object> properties = new HashMap<>();
        //Creates JSON string to send location and temperature of each weather reading.
        JSONObject payload = new JSONObject();
        payload.put("location", channel.getLocation().getCity());
        payload.put("temperature", channel.getItem().getCondition().getTemp());
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
     * Main method for DownstreamSender. Creates DownstreamSender instance, and prepares it to send telemetry data.
     * @param args Default main string array.
     * @throws Exception Any exception that could be called. We don't care for them.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream sender...");
        //Creates DownstreamSender instance.
        DownstreamSender downstreamSender = new DownstreamSender("30079");
        //Starts sending telemetry data for Newcastle, England.
        downstreamSender.sendTelemetryData();
        System.out.println("Finishing downstream sender.");
    }
}

