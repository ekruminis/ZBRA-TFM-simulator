package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class SecondPrice extends AbstractTFM {
    private final static String type = "2nd Price Auction";

    public SecondPrice() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int i, String h, double f) {
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
                "Size Limit",
                "Block Size",
                "Number of TX",
                "Effective Fee",
                "TX Index",
                "TX Hash",
                "TX Offered"
        };
    }

    // Main Second-Price Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, double blockLimit, Block b, Miner miner, double target) {
        // sort current mempool by highest fee per byte price offered
        m.sort((t1, t2) -> Double.compare(t2.getByte_fee(), t1.getByte_fee()));

        double sizeUsedUp = 0; // total bytes used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        double effectiveFee = 0;  // fee per byte price to be paid by all included tx
        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((sizeUsedUp + m.get(0).getSize()) < blockLimit) {
                    // add to *confirmed* tx list
                    txList.add(m.get(0));

                    // update parameters
                    sizeUsedUp += m.get(0).getSize();

                    // log data
                    logs.add(logStart(index, m.get(0).getHash(), m.get(0).getTotal_fee()));

                    // remove from mempool and continue..
                    m.remove(0);
                    index++;
                }
                // block is filled so get lowest included tx gas price and leave..
                else {
                    effectiveFee = txList.get(txList.size()-1).getByte_fee();
                    break;
                }
            }
            // no more tx in mempool so get lowest included tx gas price and leave..
            else {
                effectiveFee = txList.get(txList.size()-1).getByte_fee();
                break;
            }
        }

        // cycle through each *confirmed* tx and calculate miner payout based on size * fee_price
        for(Transaction t : txList) {
            rewards = rewards.add(new BigDecimal(t.getSize()*effectiveFee));
        }

        return new Data(m, txList, rewards, effectiveFee, sizeUsedUp, logs);
    }
}
