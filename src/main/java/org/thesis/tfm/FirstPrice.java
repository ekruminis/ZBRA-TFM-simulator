package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class FirstPrice extends AbstractTFM {
    private final static String type = "1st Price Auction";

    public FirstPrice() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int i, String h, long f) {
        return new String[] {
                String.valueOf(i),
                h,
                String.valueOf(f),
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
                "Gas Limit",
                "Block Size (gas)",
                "Number of TX confirmed",
                "TX Index",
                "TX Hash",
                "TX Fee"
        };
    }

    // Main First-Price Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, long gasLimit, Block b) {
        // sort current mempool by highest gas price offered
        m.sort((t1, t2) -> Long.compare(t2.getGasPrice(), t1.getGasPrice()));

        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        ArrayList<Transaction> confirmed = new ArrayList<Transaction>(); // list of *confirmed* transactions
        long gasUsedUp = 0; // total gas used by current block
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if (!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((gasUsedUp + m.get(0).getGasUsed()) < gasLimit) {
                    // add to *confirmed* tx list
                    confirmed.add(m.get(0));

                    // update parameters
                    gasUsedUp += m.get(0).getGasUsed();
                    rewards = rewards.add((new BigDecimal(m.get(0).getFee())));

                    // log data
                    logs.add(logStart(index, m.get(0).getHash(), m.get(0).getFee()));

                    // remove from mempool and continue..
                    m.remove(0);
                    index++;
                } else {
                    break;
                }
            }
            else {
                break;
            }
        }

        return new Data(m, confirmed, rewards, gasUsedUp, logs);
    }
}
