package ZBRA;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;
import ZBRA.tfm.AbstractTFM;
import de.siegmar.fastcsv.writer.CsvWriter;

public class Simulation {
    Path mainPath; // path to main simulation log file
    Path sumPath; // path to summary of the simulation log file
    Path inputPath; // path to the input dataset
    AbstractTFM tfm;
    int cycles;
    CsvWriter mainCW; // csv writer for main log file
    CsvWriter sumCW; // csv write for summary log file
    Random randomSeed1; // random generator for number of tx to fetch
    Random randomSeed2; // random generator for which tx from dataset to take
    Iterator<Transaction> iter;

    ArrayList<Transaction> data = new ArrayList<Transaction>(); // dataset containing all transactions from input file
    ArrayList<Transaction> mempool = new ArrayList<Transaction>(); // mempool arraylist
    ArrayList<Miner> miners = new ArrayList<>(); // list of miners
    int totalStake = -1; // total stake for all miners

    BigDecimal totalPayout = new BigDecimal("0"); // total payout awarded to all miners (summary logging)
    ArrayList<Block> blockchain = new ArrayList<Block>(); // arraylist of all blocks

    // SIMULATION PARAMETERS
    final long SIZE_LIMIT = 4_000_000;  // size limit of a block
    final long TARGET = 2_000_000; // target size of the block
    final double MEAN_TX_ARRIVAL_RATE = 2_471.0; // mean tx arrival rate for Gamma-Poisson distribution
    final double ALPHA = 0.03; // shape parameter for Gamma distribution variance (increase this for more variance)
    final double BASE_FEE = 0.0000002333;  // base fee for EIP-1559 and Reserve Pool TFM types
    final double RESERVE_POOL_BASE = 134.38; // reserve pool base amount
    final long MEMPOOL_INITIAL_SIZE = 93_824; // initial size of the mempool

    // print out headers depending on tfm + read input file and load all tx to dataset
    public void jsonStart() throws IOException {
        BigDecimal byteFeeTotal = new BigDecimal(0);

        BigDecimal weightFeeTotal = new BigDecimal(0);

        mainCW.writeComment(Arrays.toString(tfm.logHeaders()));
        try (FileReader fReader = new FileReader(inputPath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(fReader);
            if (rootElement.isJsonArray()) {
                JsonArray jsonArray = rootElement.getAsJsonArray();
                Gson gson = new GsonBuilder().create();
                for (JsonElement jsonElement : jsonArray) {
                    if (jsonElement.isJsonObject()) {
                        Transaction t = new Transaction(
                            jsonElement.getAsJsonObject().get("hash").getAsString(),
                            jsonElement.getAsJsonObject().get("size").getAsDouble(),
                            jsonElement.getAsJsonObject().get("weight").getAsDouble(),
                            jsonElement.getAsJsonObject().get("fee").getAsDouble()
                        );
                        data.add(t);

                        byteFeeTotal = byteFeeTotal.add(BigDecimal.valueOf(t.getByteFee()));
                        weightFeeTotal = weightFeeTotal.add(BigDecimal.valueOf(t.getWeightFee()));
                    }
                }
            }
            // print size of the dataset
            System.out.println("Dataset size: " + data.size() + " txs");
            System.out.println("byteFee total: " + byteFeeTotal + " \t\tavg fee per byte: " + byteFeeTotal.divide(BigDecimal.valueOf(data.size()), 10, RoundingMode.HALF_EVEN));
            System.out.println("weightFee total: " + weightFeeTotal + " \t\tavg fee per weight: " + weightFeeTotal.divide(BigDecimal.valueOf(data.size()), 10, RoundingMode.HALF_EVEN));

            Collections.shuffle(data, randomSeed2);
            iter = data.stream().iterator();
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
            throw e;
        }
    }

    // add random number of txs to the mempool, draw randomly from complete dataset, Gamma-Poisson (negative binomial) distribution
    public int fetchTX() {
        try {
            double shape = 1.0 / ALPHA;
            double scale = MEAN_TX_ARRIVAL_RATE * ALPHA;

            GammaDistribution gamma = new GammaDistribution(shape, scale);
            double lambda = gamma.sample(); // λ varies from cycle to cycle

            PoissonDistribution poisson = new PoissonDistribution(lambda);
            int y = poisson.sample(); // Final TX count

            int x = 0;
            while (iter.hasNext() && x < y) {
                mempool.add(iter.next());
                x++;
            }
            return y;
        } catch (Exception e) {
            System.err.println("Error in fetchTX: " + e.getMessage());
            return -1;
        }
    }

    // main simulation method
    public void simulate() throws IOException {
        int cycles = this.cycles;
        jsonStart();

        blockchain.add(new Block()); // create *GENESIS* block

        if (tfm.getType().equals("EIP-1559") || tfm.getType().equals("Reserve Pool")) {
            // set base fee to what it was on 2022-10-09 (dataset)
            blockchain.get(0).setBaseFee(BASE_FEE); // TODO ***
            if (tfm.getType().equals("Reserve Pool")) {
                // initialise reserve pool with BTC at start
                blockchain.get(0).updatePool(new BigDecimal(RESERVE_POOL_BASE)); // TODO ***
            }
        }

        int fetchedTxNo = 0;
        while (iter.hasNext() && fetchedTxNo < MEMPOOL_INITIAL_SIZE) {
            mempool.add(iter.next());
            fetchedTxNo++;
        }
        int txMean = 0;
        int noCycles = 0;
        // while there are blocks to mine and dataset has not been exhausted..
        while (cycles >= 0 && iter.hasNext()) {
            int fetch = -1;
            // add new tx to current mempool
            try {
                fetch = fetchTX();
            } catch (Exception e) {
                System.out.println("error in fetchTX(); " + e);
                break;
            }

            // assign a winning miner of the block based on their stake value
            Miner winnerMiner = Objects.requireNonNull(getWinningMiner());

            // decide which tx to confirm in a block cycle, output is all the data required for logging
            Data results = tfm.fetchValidTX(mempool, SIZE_LIMIT, blockchain.get(blockchain.size() - 1), winnerMiner, TARGET);
            mempool = results.getMempool();

            // append block to the blockchain
            String time = Instant.now().toString();
            blockchain.add(new Block(
                (blockchain.get(blockchain.size() - 1).getIndex() + 1),
                winnerMiner.getID(),
                (blockchain.get(blockchain.size() - 1).getCurrentHash()),
                DigestUtils.sha256Hex(time), // TODO redo
                SIZE_LIMIT,
                results
            ));

            // update payout (for summary logging later)
            totalPayout = totalPayout.add(blockchain.get(blockchain.size() - 1).getRewards());

            // log for main logger file
            String[] basics = {time,
                String.valueOf(blockchain.size() - 1),
                blockchain.get(blockchain.size() - 1).getParentHash(),
                blockchain.get(blockchain.size() - 1).getCurrentHash(),
                String.valueOf(blockchain.get(blockchain.size() - 1).getMinerID()),
                String.valueOf(blockchain.get(blockchain.size() - 1).getRewards()),
                String.valueOf(SIZE_LIMIT),
                String.valueOf(blockchain.get(blockchain.size() - 1).getWeight()),
                String.valueOf(blockchain.get(blockchain.size() - 1).getTXNumber())
            };

            // read extra data for main logger file
            for (String[] s : results.getLogs()) {
                // for 1st Price TFM
                if (blockchain.get(blockchain.size() - 1).getBaseFee() == -1) {
                    mainCW.writeRecord(ArrayUtils.addAll(basics, s));
                }
                // Others
                else {
                    String[] basics2 = {};

                    if (tfm.getType().equals("2nd Price Auction")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 1);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                    } else if (tfm.getType().equals("EIP-1559")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 2);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length + 1] = String.valueOf(blockchain.get(blockchain.size() - 1).getBurned());
                    } else if (tfm.getType().equals("Reserve Pool")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 2);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length + 1] = String.valueOf(blockchain.get(blockchain.size() - 1).getPool());
                    } else if (tfm.getType().equals("Burning 2nd Price Auction")) {
                        basics2 = Arrays.copyOf(basics, basics.length + 3);
                        basics2[basics.length] = String.valueOf(blockchain.get(blockchain.size() - 1).getUnconfirmedTXs().size());
                        basics2[basics.length + 1] = String.valueOf(blockchain.get(blockchain.size() - 1).getBaseFee());
                        basics2[basics.length + 2] = String.valueOf(blockchain.get(blockchain.size() - 1).getBurned());
                    }

                    // write main log data
                    mainCW.writeRecord(ArrayUtils.addAll(basics2, s));
                }
            }

            System.out.println(cycles + " blocks left!!  tx fetched: \t\t\t" + fetch + "\t\t mempool size: " + mempool.size() + " tx confirmed: " + results.getConfirmed().size());
            cycles--;
            noCycles++;
            txMean += fetch;

            // update miner winnings data (for summary log file)
            winnerMiner.updateWinnings(blockchain.get(blockchain.size() - 1).getRewards());
        }
        System.out.println("mean tx fetched: " + (txMean / (noCycles)) + " total cycles: " + noCycles + " total confirmed: " + txMean);
        // finished simulation, log summary of results
        csvEnd();
    }

    // decide on winner of block based on their stake
    private Miner getWinningMiner() {
        if (totalStake <= 0) {
            System.err.println("Error: Total stake is zero or negative. Cannot determine a winning miner.");
            return null;
        }
        int randomNumber = randomSeed1.nextInt(totalStake); // Use randomSeed1 for consistency
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
        if (totalStake <= 0) {
            System.err.println("Error: Total stake is zero or negative. Cannot calculate miner statistics.");
            return;
        }

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
            bs = bs.add(new BigDecimal(blockchain.get(i).getWeight()));

            bpArr.add(blockchain.get(i).getRewards());
            bsArr.add(new BigDecimal(blockchain.get(i).getWeight()));

            try {
                if (blockchain.get(i).getTXNumber() > 0) {
                    for (Transaction t : blockchain.get(i).getConfirmedTXs()) {
                        blockFee = blockFee.add(new BigDecimal(t.getTotalFee()));
                    }
                    if (blockFee.compareTo(BigDecimal.ZERO) > 0) {
                        blockFee = blockFee.divide(new BigDecimal(blockchain.get(i).getTXNumber()), 10, RoundingMode.HALF_EVEN);
                    }
                }
            } catch (Exception e) {
                System.out.println("error in csvEnd() for blockFee; " + e);
            }

            tf = tf.add(blockFee);
            tfArr.add(blockFee);

        }

        BigDecimal meanPay = bp.divide((new BigDecimal(blockchain.size() - 1)), 10, RoundingMode.HALF_EVEN);
        BigDecimal meanFee = tf.divide((new BigDecimal(blockchain.size() - 1)), 10, RoundingMode.HALF_EVEN);
        BigDecimal meanSize = bs.divide((new BigDecimal(blockchain.size() - 1)), 10, RoundingMode.HALF_EVEN);


        BigDecimal varPay = new BigDecimal("0");
        for (BigDecimal b : bpArr) {
            varPay = varPay.add(b.subtract(meanPay).pow(2));
        }

        BigDecimal varFee = new BigDecimal("0");
        for (BigDecimal b : tfArr) {
            varFee = varFee.add(b.subtract(meanFee).pow(2));
        }

        BigDecimal varSize = new BigDecimal("0");
        for (BigDecimal b : bsArr) {
            varSize = varSize.add(b.subtract(meanSize).pow(2));
        }

        MathContext mc = new MathContext(10);

        sumCW.writeRecord(
            "TFM Type", 
            "Avg. Block Payout", 
            "Variance Between Block Payout", 
            "Avg. TX Fee", 
            "Variance Between TX Fees", 
            "Avg. Block Size", 
            "Block Size Variance"
        );
        sumCW.writeRecord(
            tfm.getType(),
            String.valueOf(bp.divide((new BigDecimal(blockchain.size() - 2)), 10, RoundingMode.HALF_EVEN)),
            String.valueOf((varPay.divide((new BigDecimal(blockchain.size() - 1)), RoundingMode.HALF_UP)).sqrt(mc)),
            String.valueOf(tf.divide((new BigDecimal(blockchain.size() - 2)), 10, RoundingMode.HALF_EVEN)),
            String.valueOf((varFee.divide((new BigDecimal(blockchain.size() - 1)), RoundingMode.HALF_UP)).sqrt(mc)),
            String.valueOf(bs.divide((new BigDecimal(blockchain.size() - 2)), 10, RoundingMode.HALF_EVEN).divide(new BigDecimal(SIZE_LIMIT), 10, RoundingMode.HALF_EVEN)),
            String.valueOf((varSize.divide((new BigDecimal(blockchain.size() - 1)), RoundingMode.HALF_UP)).sqrt(mc))
        );

        IntStream.range(0, 5).forEach(i -> sumCW.writeRecord("")); // create empty space in file, just for better readability

        // log miner summary data
        sumCW.writeRecord(
            "Miner ID", 
            "% of Stake Power", 
            "Total Payout", 
            "% of Total Network Payout", 
            "Shared Pool Effect"
        );
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.HALF_UP);
        BigDecimal tp = totalPayout;
        for (Miner m : miners) {
            if (m.getRewards().compareTo(BigDecimal.ZERO) > 0) {
                sumCW.writeRecord(
                    String.valueOf(m.getID()),
                    String.valueOf((Double.parseDouble(df.format(((double) m.getStake() / totalStake))))),
                    String.valueOf(m.getRewards()),
                    String.valueOf(m.getRewards().divide(tp, 10, RoundingMode.HALF_EVEN)),
                    String.valueOf(m.getPublicPoolEffect())
                );
            } else {
                sumCW.writeRecord(
                    String.valueOf(m.getID()),
                    String.valueOf((Double.parseDouble(df.format(((double) m.getStake() / totalStake))))),
                    String.valueOf(m.getRewards()),
                    String.valueOf(0),
                    String.valueOf(m.getPublicPoolEffect())
                );
            }
        }

        IntStream.range(0, 5).forEach(i -> sumCW.writeRecord("")); // create empty space in file, just for better readability

        // log block summary data
        sumCW.writeRecord("Block Index", "Bytes Used", "% Of Max Block Size", "TX Count", "TX in Mempool", "Miner ID", "Miner Rewards", "Total Burned", "Avg. fee", "Base Fee", "Pool Amount", "Effect on Pool", "Taken From Private Pool", "Block Reward Total", "Block Tips Total");
        for (int i = 1; i < blockchain.size(); i++) {
            BigDecimal b = new BigDecimal("0");
            try {
                if (blockchain.get(i).getTXNumber() > 0) {
                    b = (blockchain.get(i).getRewards().add(blockchain.get(i).getBurned())).divide(new BigDecimal(blockchain.get(i).getTXNumber()), 10, RoundingMode.HALF_EVEN);
                }
            } catch (Exception e) {
                System.out.println("error in csvEnd() avg fee; " + e);
            }
            sumCW.writeRecord(
                String.valueOf(blockchain.get(i).getIndex()),
                String.valueOf(blockchain.get(i).getWeight()),
                String.valueOf((double) blockchain.get(i).getWeight() / SIZE_LIMIT),
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
                String.valueOf(blockchain.get(i).getLogs().getBlockTotalReward()),
                String.valueOf(blockchain.get(i).getLogs().getBlockTipsTotal())
            );
        }

        this.sumCW.close();
    }

    // constructor for simulation, initialise all required data, create files, etc.
    public Simulation(AbstractTFM tfmType, int cycles, int seed, int noOfMiners, String outputFileName, String inputFileName) throws IOException {
        this.mainPath = Paths.get("output/" + outputFileName + ".csv");
        this.mainPath.toFile().getParentFile().mkdirs();
        this.mainCW = CsvWriter.builder().build(mainPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        this.sumPath = Paths.get("output/" + outputFileName + "-summary.csv");
        this.sumPath.toFile().getParentFile().mkdirs();
        this.sumCW = CsvWriter.builder().build(sumPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        this.tfm = tfmType;
        this.cycles = cycles;

        this.inputPath = Paths.get("input/" + inputFileName);

        this.randomSeed1 = new Random(seed);
        this.randomSeed2 = new Random(seed * 2);

        // randomly create miners + assign stake to them
        PoissonDistribution poisson = new PoissonDistribution(500);
        for (int minerId = 1; minerId <= noOfMiners; minerId++) {
            miners.add(new Miner(minerId, Math.max(1, poisson.sample())));
        }

        // total stake in the network
        totalStake = miners.stream().mapToInt(Miner::getStake).sum();
    }
}
