import java.util.*;

public class TxHandler {

    UTXOPool pool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.pool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {

        HashSet<UTXO> utxoSet = new HashSet<>();
        double sumOfInputVals = 0, sumOfOutputVals = 0;
        int i = 0;

        for (Transaction.Input in : tx.getInputs()) {
            UTXO lastUTXO = new UTXO(in.prevTxHash, in.outputIndex);
            Transaction.Output prevTx = pool.getTxOutput(lastUTXO);

            // check 1: all outputs claimed by tx are in current utxo pool
            if (!pool.contains(lastUTXO)) { return false; }

            // check 2: signatures on each input of tx are valid
            if (in.signature == null ||
                    !Crypto.verifySignature(prevTx.address,
                            tx.getRawDataToSign(i),
                            in.signature)) { return false; }

            utxoSet.add(lastUTXO);
            sumOfInputVals += prevTx.value;
            i++;
        }

        // check 3: no utxo is claimed multiple times by tx
        if (utxoSet.size() != tx.getInputs().size()) { return false; }

        // check 4: all of tx's output values are non-negative
        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0) { return false; }
            else {
                sumOfOutputVals += out.value;
            }
        }

        // check 5: the sum of tx's input values is >= the sum of its output values
        if (sumOfInputVals < sumOfOutputVals) { return false; }

        // if passes all checks
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        List<Transaction> validTxs = new ArrayList<>();

        for (Transaction t : possibleTxs) {
            if (isValidTx(t)) {
                validTxs.add(t);
                for (Transaction.Input in : t.getInputs()) {
                    UTXO spentTx = new UTXO(in.prevTxHash, in.outputIndex);
                    this.pool.removeUTXO(spentTx);
                }
                int index = 0;
                for (Transaction.Output out : t.getOutputs()) {
                    UTXO validTx = new UTXO(t.getHash(), index);
                    index++;
                    this.pool.addUTXO(validTx, out);
                }
            }
        }
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }

}
