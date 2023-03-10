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
    public String[] logStart(int i, String cc, String h, long o, long f, long g) {
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
                "Gas Limit",
                "Block Size (gas)",
                "Number of Confirmed TX",
                "Number of Unconfirmed TX",
                "Effective Fee",
                "Fees Burned",
                "TX Index",
                "TX Confirmed",
                "TX Hash",
                "TX Offered",
                "TX Paid",
                "TX Gas Used"
        };
    }

    // Main Burning Second-Price Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, long gasLimit, Block b, Miner miner) {
        // sort current mempool by highest gas fees offered
        m.sort((t1, t2) -> Long.compare(t2.getFee(), t1.getFee())); // t2.getGasPrice(), t1.getGasPrice()

        long gasUsedUp = 0; // total gas used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        long effectiveGasFee = 0;  // gas price to be paid by all included tx
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
                if ((gasUsedUp + m.get(0).getGasUsed()) < gasLimit) {
                    // add to  tx list
                    txList.add(m.get(0));

                    gasUsedUp += m.get(0).getGasUsed();

                    // remove from mempool and continue..
                    m.remove(0);
                }
                // block is filled so split txlist in half, decide which tx get confirmed/unconfirmed, get miner payout..
                else {
                    int split = (int)(txList.size()/2);
                    confirmedTxList = new ArrayList<Transaction>(txList.subList(0, split));
                    unconfirmedTxList = new ArrayList<Transaction>(txList.subList(split, txList.size()-1));
                    effectiveGasFee = unconfirmedTxList.get(0).getFee();

                    break;
                }
            }
            // no more tx in mempool so split txlist in half, decide which tx get confirmed/unconfirmed, get miner payout.
            else {
                int split = (int)(txList.size()/2);
                confirmedTxList = new ArrayList<Transaction>(txList.subList(0, split));
                unconfirmedTxList = new ArrayList<Transaction>(txList.subList(split, txList.size()-1));
                effectiveGasFee = unconfirmedTxList.get(0).getFee();

                break;
            }
        }

        gasUsedUp = 0; // reset gas used, only count confirmed txs gas

        // cycle through each *confirmed* tx and calculate user fees based on gas_used * effective_fee, log data
        for(Transaction t : confirmedTxList) {
            userPay = userPay.add(BigDecimal.valueOf(effectiveGasFee));

            // update parameters
            gasUsedUp += t.getGasUsed();

            // log data
            logs.add(logStart(index, "YES", t.getHash(), t.getFee(), effectiveGasFee, t.getGasUsed()));

            index++;
        }

        // cycle through each *unconfirmed* tx and calculate miner payout based on gas_used * gas_price
        for(Transaction t : unconfirmedTxList) {
            rewards = rewards.add(new BigDecimal(t.getFee()));
            logs.add(logStart(index, "no", t.getHash(), t.getFee(), 0, t.getGasUsed()));

            index++;
        }

        burned = userPay.subtract(rewards);

        // add unconfirmed tx back into mempool
        m.addAll(unconfirmedTxList);

        return new Data(m, confirmedTxList, unconfirmedTxList, rewards, effectiveGasFee, burned, gasUsedUp, logs);
    }
}
