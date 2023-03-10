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
                "Number of TX",
                "Effective Fee",
                "TX Index",
                "TX Hash",
                "TX Offered"
        };
    }

    // Main Second-Price Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, long gasLimit, Block b, Miner miner) {
        // sort current mempool by highest gas price offered
        m.sort((t1, t2) -> Long.compare(t2.getGasPrice(), t1.getGasPrice()));

        long gasUsedUp = 0; // total gas used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        long effectiveGasFee = 0;  // gas price to be paid by all included tx
        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((gasUsedUp + m.get(0).getGasUsed()) < gasLimit) {
                    // add to *confirmed* tx list
                    txList.add(m.get(0));

                    // update parameters
                    gasUsedUp += m.get(0).getGasUsed();

                    // log data
                    logs.add(logStart(index, m.get(0).getHash(), m.get(0).getFee()));

                    // remove from mempool and continue..
                    m.remove(0);
                    index++;
                }
                // block is filled so get lowest included tx gas price and leave..
                else {
                    effectiveGasFee = txList.get(txList.size()-1).getGasPrice();
                    break;
                }
            }
            // no more tx in mempool so get lowest included tx gas price and leave..
            else {
                effectiveGasFee = txList.get(txList.size()-1).getGasPrice();
                break;
            }
        }

        // cycle through each *confirmed* tx and calculate miner payout based on gas_used * gas_price
        for(Transaction t : txList) {
            rewards = rewards.add(new BigDecimal(t.getGasUsed()*effectiveGasFee));
        }

        return new Data(m, txList, rewards, effectiveGasFee, gasUsedUp, logs);
    }
}
