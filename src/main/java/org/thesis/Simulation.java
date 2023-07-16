package org.thesis;

import com.google.gson.*;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.thesis.blockchain.Block;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Transaction;
import org.thesis.tfm.AbstractTFM;


import java.io.FileReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;

import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.math3.distribution.PoissonDistribution;

import static java.math.BigDecimal.ROUND_HALF_EVEN;
import static java.nio.file.StandardOpenOption.*;

public class Simulation {
    Path mainPath; // path to main simulation log file
    Path sumPath; // path to summary of the simulation log file
    Path inputPath; // path to the input dataset
    AbstractTFM tfm;
    int time;
    CsvWriter mainCW; // csv writer for main log file
    CsvWriter sumCW; // csv write for summary log file
    Random r1; // random generator for number of tx to fetch
    Random r2; // random generator for which tx from dataset to take
    Iterator<Transaction> iter;

    ArrayList<Transaction> data = new ArrayList<Transaction>(); // dataset containing all transactions from input file
    ArrayList<Transaction> mempool = new ArrayList<Transaction>(); // mempool arraylist
    ArrayList<Miner> miners = new ArrayList<>(); // list of miners
    int totalStake = -1; // total stake for all miners

    BigDecimal totalPayout = new BigDecimal("0"); // total payout awarded to all miners (summary logging)
    ArrayList<Block> blockchain = new ArrayList<Block>(); // arraylist of all blocks

    final long SIZE_LIMIT = 2_000_000;
    final long TARGET = 1_000_000;

    // print out headers depending on tfm + read input file and load all tx to dataset
    public void jsonStart() throws IOException {
        mainCW.writeComment(Arrays.toString(tfm.logHeaders()));
        Transaction t = new Transaction("h", 1, 1);
        try (FileReader fReader = new FileReader(inputPath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(fReader);
            if (rootElement.isJsonArray()) {
                JsonArray jsonArray = rootElement.getAsJsonArray();
                Gson gson = new GsonBuilder().create();
                for (JsonElement jsonElement : jsonArray) {
                    if(jsonElement.isJsonObject()) {
                        t = new Transaction(jsonElement.getAsJsonObject().get("hash").getAsString(), jsonElement.getAsJsonObject().get("size").getAsDouble(), jsonElement.getAsJsonObject().get("fee").getAsDouble());
                        data.add(t);
                    }
                }
            }
            System.out.println("dataset size; " + data.size());

            // shuffle entire dataset and open up a stream for it
            Collections.shuffle(data, r2);
            Stream<Transaction> stream = data.stream();
            iter = stream.iterator();

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("err;" + t);
        }

    }

    // add random number of txs to the mempool, draw randomly from complete dataset
    public void fetchTX() {
        try {
            PoissonDistribution p = new PoissonDistribution(4000);
            int y = (int)(p.sample()*r1.nextDouble(2));

            int x = 0;
            while(iter.hasNext() && x < y) {
                mempool.add(iter.next());
                x++;
            }

        }
        catch (Exception e) {
            System.out.println("no more tx available!!");
        }
    }

    // main simulation method
    public void simulate() throws IOException {
        int cycles = time;

        jsonStart();

        blockchain.add(new Block()); // create *GENESIS* block

        if(tfm.getType().equals("EIP-1559") || tfm.getType().equals("Reserve Pool")) {
            // set base fee to what it was on 2022-10-09 (dataset)
            blockchain.get(0).setBaseFee(0.00000035); // TODO ***
            if (tfm.getType().equals("Reserve Pool")) {
                // initialise reserve pool with 10 BTC at start
                blockchain.get(0).updatePool(new BigDecimal("100")); // TODO ***
            }
        }

        int x = 0;
        while(iter.hasNext() && x < 20_000) {
            mempool.add(iter.next());
            x++;
        }

        // while there are blocks to mine and dataset has not been exhausted..
        while(cycles >= 0 && iter.hasNext()) {

            // add new tx to current mempool
            try {
                fetchTX();
            } catch(Exception e) {
                System.out.println("error in fetchTX(); " + e);
                break;
            }

            // assign a winning miner of the block based on their stake value
            Miner winnerMiner = Objects.requireNonNull(getWinningMiner());

            // decide which tx to confirm in a block cycle, output is all the data required for logging
            Data results = tfm.fetchValidTX(mempool, SIZE_LIMIT, blockchain.get(blockchain.size()-1), winnerMiner, TARGET);
            mempool = results.getMempool();

            // append block to the blockchain
            String time = Instant.now().toString();
            blockchain.add(new Block(
                    (blockchain.get(blockchain.size()-1).getIndex()+1),
                    winnerMiner.getID(),
                    (blockchain.get(blockchain.size()-1).getCurrentHash()),
                    org.apache.commons.codec.digest.DigestUtils.sha256Hex( time ), // TODO redo
                    SIZE_LIMIT,
                    results
                    ));

            // update payout (for summary logging later)
            totalPayout = totalPayout.add(blockchain.get(blockchain.size()-1).getRewards());

            // log for main logger file
            String [] basics = {time,
                    String.valueOf(blockchain.size() - 1),
                    blockchain.get(blockchain.size()-1).getParentHash(),
                    blockchain.get(blockchain.size()-1).getCurrentHash(),
                    String.valueOf(blockchain.get(blockchain.size()-1).getMinerID()),
                    String.valueOf(blockchain.get(blockchain.size()-1).getRewards()),
                    String.valueOf(SIZE_LIMIT),
                    String.valueOf(blockchain.get(blockchain.size()-1).getSize()),
                    String.valueOf(blockchain.get(blockchain.size()-1).getTXNumber())
            };

            // read extra data for main logger file
            for(String[] s : results.getLogs()) {
                // for 1st Price TFM
                if(blockchain.get(blockchain.size()-1).getBaseFee() == -1) {
                    mainCW.writeRow(ArrayUtils.addAll(basics, s));
                }
                // Others
                else{
                    String[] basics2 = {};

                    if(tfm.getType().equals("2nd Price Auction")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 1);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                    }

                    else if(tfm.getType().equals("EIP-1559")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 2);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length+1] = String.valueOf(blockchain.get(blockchain.size() - 1).getBurned());
                    }

                    else if(tfm.getType().equals("Reserve Pool")){
                        basics2 = Arrays.copyOf(basics, basics.length + 2);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length+1] = String.valueOf(blockchain.get(blockchain.size() - 1).getPool());
                    }

                    else if(tfm.getType().equals("Burning 2nd Price Auction")){
                        basics2 = Arrays.copyOf(basics, basics.length + 3);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getUnconfirmedTXs().size());
                        basics2[basics.length+1] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length+2] = String.valueOf(blockchain.get(blockchain.size() - 1).getBurned());
                    }

                    // write main log data
                    mainCW.writeRow(ArrayUtils.addAll(basics2, s));
                }
            }

            System.out.println(cycles + " blocks left!!");
            cycles--;

            // update miner winnings data (for summary log file)
            winnerMiner.updateWinnings(blockchain.get(blockchain.size()-1).getRewards());
        }

        // finished simulation, log summary of results
        csvEnd();
    }

    // decide on winner of block based on their stake
    private Miner getWinningMiner() {
        // winning number
        int randomNumber = new Random().nextInt(totalStake);

        // find winning miner
        int cumulativeStake = 0;
        for (Miner miner : miners) {
            cumulativeStake += miner.getStake();
            if (randomNumber < cumulativeStake) {
                return miner;
            }
        }

        return null;
    }

    // Logging of the summary of simulation results
    public void csvEnd() throws IOException {
        this.mainCW.close();

        // log *main* summary of simulation results (avg. block reward + avg. fee + avg. block size, variances)
        BigDecimal bp = new BigDecimal("0");
        BigDecimal tf = new BigDecimal("0");
        BigDecimal bs = new BigDecimal("0");

        ArrayList<BigDecimal> bpArr = new ArrayList<>();
        ArrayList<BigDecimal> tfArr = new ArrayList<>();
        ArrayList<BigDecimal> bsArr = new ArrayList<>();

        for (int i = 1; i < blockchain.size(); i++) {
            BigDecimal blockFee = new BigDecimal("0");
            bp = bp.add(blockchain.get(i).getRewards());
            bs = bs.add(new BigDecimal(blockchain.get(i).getSize()));

            bpArr.add(blockchain.get(i).getRewards());
            bsArr.add(new BigDecimal(blockchain.get(i).getSize()));

            try {
                if(blockchain.get(i).getTXNumber() > 0) {
                    for (Transaction t : blockchain.get(i).getConfirmedTXs()) {
                        blockFee = blockFee.add(new BigDecimal(t.getTotal_fee()));
                    }
                    if(blockFee.compareTo(BigDecimal.ZERO) > 0) {
                        blockFee = blockFee.divide(new BigDecimal(blockchain.get(i).getTXNumber()), 10, ROUND_HALF_EVEN);
                    }
                }
            } catch (Exception e) {
                System.out.println("error in csvEnd() for blockFee; " + e);
            }

            tf = tf.add(blockFee);
            tfArr.add(blockFee);

        }

        BigDecimal meanPay = bp.divide( (new BigDecimal(blockchain.size()-1) ), 10, RoundingMode.HALF_EVEN);
        BigDecimal meanFee = tf.divide( (new BigDecimal(blockchain.size()-1) ), 10, RoundingMode.HALF_EVEN);
        BigDecimal meanSize = bs.divide( (new BigDecimal(blockchain.size()-1)), 10, RoundingMode.HALF_EVEN);


        BigDecimal varPay = new BigDecimal("0");
        for(BigDecimal b : bpArr) {
            varPay = varPay.add(b.subtract(meanPay).pow(2));
        }

        BigDecimal varFee = new BigDecimal("0");
        for(BigDecimal b : tfArr) {
            varFee = varFee.add(b.subtract(meanFee).pow(2));
        }

        BigDecimal varSize = new BigDecimal("0");
        for(BigDecimal b : bsArr) {
            varSize = varSize.add(b.subtract(meanSize).pow(2));
        }

        MathContext mc = new MathContext(10);

        sumCW.writeRow("TFM Type", "Avg. Block Payout", "Variance Between Block Payout", "Avg. TX Fee", "Variance Between TX Fees", "Avg. Block Size", "Block Size Variance");
        sumCW.writeRow(
                tfm.getType(),
                String.valueOf(bp.divide( (new BigDecimal(blockchain.size()-2) ), 10, RoundingMode.HALF_EVEN)),
                String.valueOf((varPay.divide((new BigDecimal(blockchain.size()-1) ), BigDecimal.ROUND_HALF_UP)).sqrt(mc)),
                String.valueOf(tf.divide( (new BigDecimal(blockchain.size()-2) ), 10, RoundingMode.HALF_EVEN)),
                String.valueOf((varFee.divide((new BigDecimal(blockchain.size()-1) ), BigDecimal.ROUND_HALF_UP)).sqrt(mc)),
                String.valueOf(bs.divide( (new BigDecimal(blockchain.size()-2)), 10, RoundingMode.HALF_EVEN).divide(new BigDecimal(SIZE_LIMIT), 10, ROUND_HALF_EVEN)),
                String.valueOf((varSize.divide((new BigDecimal(blockchain.size()-1) ), BigDecimal.ROUND_HALF_UP)).sqrt(mc))
        );

        IntStream.range(0, 5).forEach(i -> sumCW.writeRow("")); // create empty space in file, just for better readability

        // log miner summary data
        sumCW.writeRow("Miner ID", "% of Stake Power", "Total Payout", "% of Total Network Payout", "Shared Pool Effect");
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.HALF_UP);
        BigDecimal tp = totalPayout;
        for (Miner m : miners) {
            if(m.getRewards().compareTo(BigDecimal.ZERO) > 0) {
                sumCW.writeRow(
                        String.valueOf(m.getID()),
                        String.valueOf((Double.parseDouble(df.format(((double) m.getStake() / totalStake))))),
                        String.valueOf(m.getRewards()),
                        String.valueOf(m.getRewards().divide(tp, 10, ROUND_HALF_EVEN)),
                        String.valueOf(m.getPoolEffect())
                );
            }
            else {
                sumCW.writeRow(
                        String.valueOf(m.getID()),
                        String.valueOf((Double.parseDouble(df.format(((double)m.getStake()/totalStake))))),
                        String.valueOf(m.getRewards()),
                        String.valueOf(0),
                        String.valueOf(m.getPoolEffect()),
                        String.valueOf(m.getPrivatePool())
                );
            }
        }

        IntStream.range(0, 5).forEach(i -> sumCW.writeRow("")); // create empty space in file, just for better readability

        // log block summary data
        sumCW.writeRow("Block Index", "Bytes Used", "% Of Max Block Size", "TX Count", "TX in Mempool", "Miner ID", "Miner Rewards", "Total Burned", "Avg. fee", "Base Fee", "Pool Amount", "Effect on Pool", "Taken From Private Pool", "Block Surplus", "Block Reward Bonus");
        for (int i = 1; i < blockchain.size(); i++) {
            BigDecimal b = new BigDecimal("0");
            try {
                if(blockchain.get(i).getTXNumber() > 0) {
                    b = (blockchain.get(i).getRewards().add(blockchain.get(i).getBurned())).divide(new BigDecimal(blockchain.get(i).getTXNumber()), 10, ROUND_HALF_EVEN);
                }
            }
            catch (Exception e) {
                System.out.println("error in csvEnd() avg fee; " + e);
            }
            sumCW.writeRow(
                    String.valueOf(blockchain.get(i).getIndex()),
                    String.valueOf(blockchain.get(i).getSize()),
                    String.valueOf((double) blockchain.get(i).getSize() / SIZE_LIMIT),
                    String.valueOf(blockchain.get(i).getLogs().getTxCount()),
                    String.valueOf(blockchain.get(i).getLogs().getMempoolSize()),
                    String.valueOf(blockchain.get(i).getMinerID()),
                    String.valueOf(blockchain.get(i).getRewards()),
                    String.valueOf(blockchain.get(i).getBurned()),
                    String.valueOf(b),
                    String.valueOf((new BigDecimal(blockchain.get(i).getBaseFee()))),
                    String.valueOf(blockchain.get(i).getPool()),
                    String.valueOf(blockchain.get(i).getLogs().getPoolEffect()),
                    String.valueOf(blockchain.get(i).getLogs().isTakeFromPrivate()),
                    String.valueOf(blockchain.get(i).getLogs().getBlockSurplus()),
                    String.valueOf(blockchain.get(i).getLogs().getBlockReward())
                    );
        }

        this.sumCW.close();
    }

    // constructor for simulation, initialise all required data, create files, etc.
    public Simulation(AbstractTFM a, int t, int x, int m, String s,  String i) throws IOException {
        this.mainPath = Paths.get("output/" + s + ".csv");
        this.mainPath.toFile().getParentFile().mkdirs();
        this.mainCW = CsvWriter.builder().build(mainPath, CREATE, TRUNCATE_EXISTING); //(mainPath, APPEND, CREATE)

        this.sumPath = Paths.get("output/" + s + "-summary.csv");
        this.sumPath.toFile().getParentFile().mkdirs();
        this.sumCW = CsvWriter.builder().build(sumPath, CREATE, TRUNCATE_EXISTING); //(sumPath, APPEND, CREATE)

        this.tfm = a;
        this.time = t;

        this.inputPath = Paths.get("input/"+i);

        this.r1 = new Random(x);
        this.r2 = new Random(x * 2);

        // randomly create miners + assign stake to them
        for (int j = 1; j < m; j++) {
            miners.add(new Miner(j, r2.nextInt(new PoissonDistribution(500, 190.0).sample())));
        }

        // total stake in the network
        totalStake = miners.stream().mapToInt(Miner::getStake).sum();

    }
}
