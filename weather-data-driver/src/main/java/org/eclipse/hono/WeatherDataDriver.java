package org.eclipse.hono;
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
public class WeatherDataDriver {
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

        //Creates WeatherDataSender instance.
        WeatherDataSender[] locations = new WeatherDataSender[places.length];

        //Instantiates each instance of WeatherDataSender and calls sendWeatherData.
        for (int i = 0; i < places.length; i++) {
            locations[i] = new WeatherDataSender(places[i]);
            locations[i].sendWeatherData();
        }

    }
}
