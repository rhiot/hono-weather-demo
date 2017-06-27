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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * DownstreamSender is the constructor for the DownstreamSender class. Creates telemetry connector and registers
 * DEVICE_ID under TENANT_ID in hono. Messages are sent from DownstreamSender to that repository on the hono server.
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
                        System.out.println("Telemetry sender at device id " + DEVICE_ID + " has been created.");
                        if (regResult.succeeded()) {
                            honoClient.getOrCreateTelemetrySender(TENANT_ID, setupTracker.completer());
                        } else {
                            System.out.println("Telemetry sender creation has failed.");
                            regResult.cause().printStackTrace();
                        }
                    });
                    regClient.register(DEVICE_ID, null, result.completer());
                } else {
                    System.out.println("Sender has already been created. Using existing telemetry sender.");
                    honoClient.getOrCreateTelemetrySender(TENANT_ID, setupTracker.completer());
                }
            });
            regClient.get(DEVICE_ID, checker.completer());
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
        //Starts sending telemetry data.
        downstreamSender.sendTelemetryData();
        System.out.println("Finishing downstream sender.");
    }

    /**
     * sendTelemetryData sends telemetry data to hono server once latch is opened. Sends 100 messages.
     * @throws Exception
     */
    private void sendTelemetryData() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);

        //Holds latch closed until coundDown() has been called enough to overcome count value (once).
        latch.await();
        final int[] i = {1};

        long timerID = vertx.setPeriodic(1000, id -> {
            try {
                //Sends weather data from Newcastle, England
                sendSingleMessage(sender, i[0], 30079);
                //Sends weather data from Raleigh, USA
                sendSingleMessage(sender, i[0], 2478307);
            } catch (Exception e) {
                e.printStackTrace();
            }
            i[0]++;
            if(i[0] > 10000) {
                hold.countDown();
            }
        });

//        while(true) {
//            //Sends weather data from Newcastle, England
//            sendSingleMessage(sender, i, 30079);
//            //Sends weather data from Raleigh, USA
//            sendSingleMessage(sender, i , 2478307);
//
//            i++;
//
//            //Possibly one of the worst things I've ever had to do, not happy about this.
//            for(int j = 0; j < Integer.MAX_VALUE; j++) {
//                for(int k = 0; k < Integer.MAX_VALUE; k++) {
//                //Does nothing
//                }
//            }
//            //Arbitrary break out of loop.
//            if(i == 10000000) {
//                break;
//            }
//        }
        hold.await();
        //Closes AMQP connection with hono server.
        vertx.close();
    }

    /**
     * sendSingleMessage sends a message.
     * @param ms
     * @param value
     */
    private void sendSingleMessage(MessageSender ms, int value, int woeid) throws Exception {
        CountDownLatch messageSenderLatch = new CountDownLatch(1);
        System.out.println("Sending message... #" + value);

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
                v -> {
                    messageSenderLatch.countDown();
                });
        try {
            messageSenderLatch.await();
        } catch (InterruptedException e) {
        }
    }
}

