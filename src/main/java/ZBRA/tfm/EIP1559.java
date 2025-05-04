package ZBRA.tfm;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class EIP1559 extends AbstractTFM {
    private final static String type = "EIP-1559";

    public EIP1559() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int i, String h, double f, BigDecimal b, BigDecimal t) {
        return new String[] {
                String.valueOf(i),
                h,
                String.valueOf(f),
                String.valueOf(b),
                String.valueOf(t)
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
                "Base Fee",
                "Fees Burned",
                "TX Index",
                "TX Hash",
                "TX Paid",
                "TX Burned",
                "TX Tip"
        };
    }

    // Main EIP-1559 Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, double blockLimit, Block b, Miner miner, double target) {
        // sort current mempool by highest fee per byte price offered
        m.sort((t1, t2) -> Double.compare(t2.getByte_fee(), t1.getByte_fee()));

        double sizeUsedUp = 0; // total bytes used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner
        BigDecimal burned = new BigDecimal("0"); // total burned

        // calculate base fee for this current block (max of 12.5% update in a single cycle)
        double baseFee = (b.getBaseFee()*(1.0+0.125*((b.getSize()-target)/target)));

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((sizeUsedUp + m.get(0).getSize()) < blockLimit) {
                    // if current mempool tx is capable of paying base fee..
                    if(m.get(0).getByte_fee() >= baseFee) {
                        // add to *confirmed* tx list
                        txList.add(m.get(0));

                        // update parameters
                        sizeUsedUp += m.get(0).getSize();
                        BigDecimal burn = new BigDecimal(baseFee * m.get(0).getSize());
                        burned = burned.add(burn);
                        BigDecimal val = new BigDecimal( m.get(0).getTotal_fee()).subtract(burn);
                        rewards = rewards.add(val);

                        // log data
                        logs.add(logStart(index, m.get(0).getHash(), m.get(0).getTotal_fee(), burn, val));

                        // remove from mempool and continue..
                        m.remove(0);
                        index++;
                    }
                    // mempool tx are sorted so as soon as one TX can't afford to pay base fee, we can leave
                    else {
                        break;
                    }
                }
                else {
                    break;
                }
            }
            else {
                break;
            }
        }

        return new Data(m, txList, rewards, burned, baseFee, sizeUsedUp, logs);
    }
}
