package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Burning2ndPrice extends AbstractTFM {
    private final static String type = "Burning 2nd Price Auction";

    public Burning2ndPrice() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int i, String cc, String h, double o, double f, double g) {
        return new String[] {
                String.valueOf(i),
                cc,
                h,
                String.valueOf(o),
                String.valueOf(f),
                String.valueOf(g)
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
                "Number of Confirmed TX",
                "Number of Unconfirmed TX",
                "Effective Fee",
                "Fees Burned",
                "TX Index",
                "TX Confirmed",
                "TX Hash",
                "TX Offered",
                "TX Paid",
                "TX Size"
        };
    }

    // Main Burning Second-Price Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, double blockLimit, Block b, Miner miner, double target) {
        // sort current mempool by highest fee per byte offered
        m.sort((t1, t2) -> Double.compare(t2.getByte_fee(), t1.getByte_fee())); // t2.getGasPrice(), t1.getGasPrice()

        double sizeUsedUp = 0; // total size used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        double effectiveFee = 0;  // fee per byte price to be paid by all included tx
        BigDecimal userPay = new BigDecimal("0"); // total fees paid by confirmed users

        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of included transactions
        ArrayList<Transaction> confirmedTxList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        ArrayList<Transaction> unconfirmedTxList = new ArrayList<Transaction>(); // list of *unconfirmed* transactions

        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner
        BigDecimal burned = new BigDecimal("0"); // total burned

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((sizeUsedUp + m.get(0).getSize()) < blockLimit) {
                    // add to  tx list
                    txList.add(m.get(0));

                    sizeUsedUp += m.get(0).getSize();

                    // remove from mempool and continue..
                    m.remove(0);
                }
                // block is filled so split txlist in half, decide which tx get confirmed/unconfirmed, get miner payout..
                else {
                    txList.sort((t1, t2) -> Double.compare(t2.getTotal_fee(), t1.getTotal_fee()));
                    int split = (int)(txList.size()/2);
                    confirmedTxList = new ArrayList<Transaction>(txList.subList(0, split));
                    unconfirmedTxList = new ArrayList<Transaction>(txList.subList(split, txList.size()-1));
                    effectiveFee = unconfirmedTxList.get(0).getByte_fee();

                    break;
                }
            }
            // no more tx in mempool so split txlist in half, decide which tx get confirmed/unconfirmed, get miner payout.
            else {

                txList.sort((t1, t2) -> Double.compare(t2.getTotal_fee(), t1.getTotal_fee()));
                int split = (int)(txList.size()/2);
                confirmedTxList = new ArrayList<Transaction>(txList.subList(0, split));
                unconfirmedTxList = new ArrayList<Transaction>(txList.subList(split, txList.size()-1));
                effectiveFee = unconfirmedTxList.get(0).getByte_fee();

                break;
            }
        }

        sizeUsedUp = 0; // reset size used, only count confirmed txs size

        // cycle through each *confirmed* tx and calculate user fees based on size * effective_fee, log data
        for(Transaction t : confirmedTxList) {
            userPay = userPay.add(BigDecimal.valueOf(t.getSize() * effectiveFee));

            // update parameters
            sizeUsedUp += t.getSize();

            // log data
            logs.add(logStart(index, "YES", t.getHash(), t.getTotal_fee(), t.getSize() * effectiveFee, t.getSize()));

            index++;
        }

        // cycle through each *unconfirmed* tx and calculate miner payout
        for(Transaction t : unconfirmedTxList) {
            rewards = rewards.add(new BigDecimal(t.getTotal_fee()));
            logs.add(logStart(index, "no", t.getHash(), t.getTotal_fee(), 0, t.getSize()));

            index++;
        }

        burned = userPay.subtract(rewards);

        // send unconfirmed txs back into mempool
        m.addAll(unconfirmedTxList);

        return new Data(m, confirmedTxList, unconfirmedTxList, rewards, effectiveFee, burned, sizeUsedUp, logs);
    }
}
