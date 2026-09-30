package com.nezurstandalone.control;
/** Stable owner ordering wins ties; refresh order cannot change a tie winner. */
public final class RotationOrder {
    private RotationOrder() { }
    public static boolean before(int priority, String owner, int otherPriority, String otherOwner) {
        return priority > otherPriority || (priority == otherPriority && owner.compareTo(otherOwner) < 0);
    }
}
