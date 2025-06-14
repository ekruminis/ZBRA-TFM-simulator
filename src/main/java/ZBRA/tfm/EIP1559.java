package ZBRA.tfm;

import java.math.BigDecimal;
import java.util.ArrayList;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;

public class EIP1559 extends AbstractTFM {
    private final static String type = "EIP-1559";

    public EIP1559() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int index, String hash, double feePaid, BigDecimal feeBurned, BigDecimal feeTip) {
        return new String[] {
                String.valueOf(index),
                hash,
                String.valueOf(feePaid),
                String.valueOf(feeBurned),
                String.valueOf(feeTip)
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
                "Base Fee",
                "Fees Burned",
                "TX Index",
                "TX Hash",
                "TX Paid",
                "TX Burned",
                "TX Tip",
                "TX Weight",
                "TX Size"
        };
    }

    @Override
    public Data fetchValidTX(ArrayList<Transaction> mempool, double weightLimit, Block block, Miner miner, double weightTarget) {
        // Sort mempool by highest fee per byte
        mempool.sort((tx1, tx2) -> Double.compare(tx2.getWeightFee(), tx1.getWeightFee()));

        double sizeUsedUp = 0;
        double weightUsedUp = 0;

        ArrayList<String[]> logs = new ArrayList<>();
        BigDecimal totalUserPay = new BigDecimal("0");
        BigDecimal minerRewards = new BigDecimal("0");
        BigDecimal burned = new BigDecimal("0");

        int index = 1;

        ArrayList<Transaction> txList = new ArrayList<>();
        ArrayList<Transaction> confirmedTxList = new ArrayList<>();
        ArrayList<Transaction> unconfirmedTxList = new ArrayList<>();

        // Calculate base fee for this block (max 12.5% adjustment)
        double baseFee = block.getBaseFee() * (1.0 + 0.125 * ((block.getWeight() - weightTarget) / weightTarget));

        while (!mempool.isEmpty()) {
            Transaction tx = mempool.get(0);
            double txWeight = tx.getWeight();
            double txSize = tx.getSize();

            // Skip transactions too large to ever fit
            if (txSize > weightLimit) {
                mempool.remove(0);
                continue;
            }

            // Stop if this transaction would exceed the block size limit
            if ((weightUsedUp + txWeight) > weightLimit) {
                break;
            }

            // Stop if the transaction can't pay the base fee (mempool is sorted by fee)
            if (tx.getWeightFee() < baseFee) {
                break;
            }

            // Confirm transaction
            txList.add(tx);
            sizeUsedUp += txSize;
            weightUsedUp += txWeight;

            BigDecimal feeBurned = BigDecimal.valueOf(baseFee * txWeight);
            burned = burned.add(feeBurned);

            BigDecimal feeTip = BigDecimal.valueOf(tx.getTotalFee()).subtract(feeBurned);
            minerRewards = minerRewards.add(feeTip);

            logs.add(logStart(index, tx.getHash(), tx.getTotalFee(), feeBurned, feeTip));

            mempool.remove(0);
            index++;
        }

        return new Data(mempool, txList, minerRewards, burned, baseFee, sizeUsedUp, weightUsedUp, logs);
    }
}
