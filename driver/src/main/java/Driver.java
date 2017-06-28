import io.vertx.core.Vertx;
import org.eclipse.hono.DownstreamSender;
import java.util.concurrent.CountDownLatch;

/**
 * Driver allows for the creation of multiple DownStream consumers.
 */
public class Driver {
    public static final String TENANT_ID = "DEFAULT_TENANT";
    public static final String DEVICE_ID = "4712";
    private static CountDownLatch messageSenderLatch;
    public static void main(String [] args) throws Exception {

        Vertx vertx = Vertx.vertx();

        messageSenderLatch = new CountDownLatch(1);
        //Sends telemetry data for three cities in North Carolina

        DownstreamSender NC = new DownstreamSender(TENANT_ID,DEVICE_ID);
        //Raleigh, NC
        NC.sendTelemetryData(2478307);
        //Chapel Hill, NC
        NC.sendTelemetryData(2378134);
        //Durham, NC
        NC.sendTelemetryData(2394734);

//        long timer = vertx.setTimer(10000, id -> {
//            messageSenderLatch.countDown();
//        });
//
//        messageSenderLatch = new CountDownLatch(1);
    }
}
