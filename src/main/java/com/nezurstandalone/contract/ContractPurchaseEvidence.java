package com.nezurstandalone.contract;

/** A balance decrease is corroboration only; the caller also requires the fresh paid chooser. */
public final class ContractPurchaseEvidence {
    private ContractPurchaseEvidence(){}
    public static boolean debited(double before,double after,double cost){
        return Double.isFinite(before)&&Double.isFinite(after)&&Double.isFinite(cost)
                &&cost>0&&before>=cost&&after>=0&&before-after>=cost-.001;
    }
}
