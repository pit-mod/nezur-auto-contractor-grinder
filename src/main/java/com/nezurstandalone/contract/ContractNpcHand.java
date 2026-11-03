package com.nezurstandalone.contract;
/** Prefer an empty hand; fallback only to ordinary inert items without custom data. */
public final class ContractNpcHand {
    private ContractNpcHand(){}
    public static boolean inert(int id,boolean custom,boolean enchanted){
        if(custom||enchanted)return false;
        return id==262||id==263||id==265||id==266||id==280||id==287||id==318||id==352;
    }
    public static int choose(boolean[] empty,boolean[] safe){for(int i=0;i<empty.length;i++)if(empty[i])return i;for(int i=0;i<safe.length;i++)if(safe[i])return i;return -1;}
}
