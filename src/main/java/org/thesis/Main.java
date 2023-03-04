package org.thesis;

import org.apache.commons.lang3.time.StopWatch;
import org.thesis.tfm.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException {
        StopWatch sw = new StopWatch();

        AbstractTFM tfm = switch (args[0]) {
            case "first_price" -> new FirstPrice();
            case "second_price" -> new SecondPrice();
            case "eip1559" -> new EIP1559();
            case "pool" -> new Pool();
            default -> null;
        };

        // tfm style, time in seconds, seed, number of miners, output filename, input filename (blockchair_ethereum_transactions_20221009.tsv)
        Simulation s = new Simulation(tfm, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), args[4], args[5]);

        sw.start();
        s.simulate();
        sw.stop();

        System.out.println("\ntimer; " + sw.getTime(TimeUnit.MILLISECONDS) + " ms");
    }
}