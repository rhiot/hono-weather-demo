
import org.eclipse.hono.DownstreamSender;
/**
 * Driver allows for the creation of multiple DownStream consumers.
 */
public class Driver {
    public static void main(String [] args) throws Exception {
        //Checks to make sure sender vm options are set, otherwise sets default.
        System.getProperty("sender.host","localhost");
        System.getProperty("sender.port","5671");
        System.getProperty("sender.tenant","DEFAULT_TENANT");
        System.getProperty("sender.locations",null);

        //Set list of valid locations, used as default if none are set.
        String [] places = {"2478307", "2378134", "2394734", "30079", "30074", "41415", "2440351", "2440349", "2440350",
                "2440344", "2440342", "2440347", "2440346", "2440345", "2440343", "2440341", "2440364", "28747216",
                "56854933", "967073", "676757", "2375805", "818881", "2375811", "584166", "20106400", "782055"};

        //Locations that work with the program.
        if(System.getProperty("locations") != null) {
            places = System.getProperty("sender.locations").split(",");
        }

        //Creates DownstreamSender instance.
        DownstreamSender [] locations = new DownstreamSender[places.length];

        //Instantiates each instance of DownstreamSender and calls sendTelemetryData.
        for (int i = 0; i < places.length; i++) {
            locations[i] = new DownstreamSender(places[i]);
            locations[i].sendTelemetryData();
        }

    }
}
