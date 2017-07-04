import io.vertx.core.Vertx;
import org.eclipse.hono.DownstreamSender;
import java.util.concurrent.CountDownLatch;

/**
 * Driver allows for the creation of multiple DownStream consumers.
 */
public class Driver {
    public static final String TENANT_ID = "DEFAULT_TENANT";
    public static void main(String [] args) throws Exception {

        Vertx vertx = Vertx.vertx();

        //Sends telemetry data for three cities in North Carolina

        DownstreamSender raleigh = new DownstreamSender(TENANT_ID,"2478307");
        DownstreamSender chapelHill = new DownstreamSender(TENANT_ID,"2378134");
        DownstreamSender durham = new DownstreamSender(TENANT_ID,"2394734");
        //Raleigh, NC
        raleigh.sendTelemetryData();
        //Chapel Hill, NC
        chapelHill.sendTelemetryData();
        //Durham, NC
        durham.sendTelemetryData();

    }
}
