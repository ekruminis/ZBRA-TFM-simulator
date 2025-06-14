package ZBRA.tfm;

import java.math.BigDecimal;
import java.util.ArrayList;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;

public class FirstPrice extends AbstractTFM {
    private final static String type = "1st Price Auction";

    public FirstPrice() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int index, String hash, double feePaid, double weight, double size) {  
        return new String[] {
                String.valueOf(index),
                hash,
                String.valueOf(feePaid),
                String.valueOf(weight),
                String.valueOf(size)
        };
    }

    @Override
    public String[] logHeaders() {
       return new String[] {
                "Time",
                "Block Height",
                "Parent Hash",
                "Current Hash",
                "Miner ID",
                "Block Reward",
                "Block Size",
                "Block Weight",
                "Number of TX",
                "TX Index",
                "TX Hash",
                "TX Fee",
                "TX Weight",
                "TX Size"
        };
    }

    // Main First-Price Mechanism Implementation
    public Data fetchValidTX(ArrayList<Transaction> mempool, double weightLimit, Block block, Miner miner, double weightTarget) {
        // sort current mempool by highest fee per weight offered
        mempool.sort((tx1, tx2) -> Double.compare(tx2.getWeightFee(), tx1.getWeightFee()));

        ArrayList<String[]> logs = new ArrayList<String[]>(); // logging strings for printing later
        ArrayList<Transaction> confirmed = new ArrayList<Transaction>(); // list of *confirmed* transactions
        double weightUsedUp = 0; // total weight used by current block
        double bytesUsedUp = 0; // total bytes used by current block
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner
        int index = 1;

        while (!mempool.isEmpty()) {
            Transaction tx = mempool.get(0);
            double txWeight = tx.getWeight();

            // Skip transactions that are too large to ever fit
            if (txWeight > weightLimit) {
                mempool.remove(0);
                continue;
            }

            // Stop if this transaction would exceed the block limit
            if ((weightUsedUp + txWeight) > weightLimit) {
                break;
            }

            // Confirm the transaction
            confirmed.add(tx);
            bytesUsedUp += tx.getSize();
            weightUsedUp += txWeight;
            rewards = rewards.add(new BigDecimal(tx.getTotalFee()));
            logs.add(logStart(index, tx.getHash(), tx.getTotalFee(), tx.getWeight(), tx.getSize()));
            // Remove from mempool
            mempool.remove(0);
            index++;
        }

        return new Data(mempool, confirmed, rewards, bytesUsedUp, weightUsedUp, logs);
    }
}
