package com.nezurstandalone.pathfinder;

/**
 * The allocation arena for one A* search: a node pool held in parallel primitive arrays, an
 * index-based binary heap, and open-addressed long-keyed maps.
 *
 * <p>The previous search allocated, per iteration, one {@code BlockPos} per neighbour (26),
 * a {@code Node} object per neighbour <em>examined</em> (not merely accepted), plus the
 * boxing behind {@code HashMap<BlockPos, Node>} and {@code HashSet<BlockPos>}. At the
 * 15,000-iteration ceiling that is on the order of half a million short-lived objects for a
 * single route, which is what turns repeated re-pathing into visible young-gen churn.
 *
 * <p>Everything here is keyed by {@link net.minecraft.util.BlockPos#toLong()} and stored in
 * primitive arrays that grow geometrically and are reused across searches. One arena is bound
 * to one thread (the pathfinder executor) — it is deliberately not thread-safe, and
 * {@link #reset()} must be the first thing a search does.
 */
final class SearchArena {

    private static final int INITIAL_NODES = 4096;
    private static final int INITIAL_TABLE = 8192;
    /** Open addressing degrades badly past ~0.6; resize before that. */
    private static final float LOAD_FACTOR = 0.55f;
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    // --- node pool (structure of arrays) -------------------------------------
    private long[] nodeKey;
    private int[] nodeX;
    private int[] nodeY;
    private int[] nodeZ;
    private double[] nodeG;
    private double[] nodeF;
    private int[] nodeParent;
    private int nodeCount;

    // --- binary heap over node indices, ordered by nodeF ---------------------
    private int[] heap;
    private int heapSize;

    // --- open set: key -> node index ----------------------------------------
    private long[] openKeys;
    private int[] openVals;
    private int openSize;
    private int openMask;

    // --- closed set: key ------------------------------------------------------
    private long[] closedKeys;
    private int closedSize;
    private int closedMask;

    // --- walkable-height cache: key -> height --------------------------------
    // The same column is queried repeatedly (once as a candidate, again as a
    // neighbour of each of its own neighbours), and each miss costs a block state
    // lookup plus a collision-box fetch.
    private long[] heightKeys;
    private double[] heightVals;
    private int heightSize;
    private int heightMask;

    SearchArena() {
        nodeKey = new long[INITIAL_NODES];
        nodeX = new int[INITIAL_NODES];
        nodeY = new int[INITIAL_NODES];
        nodeZ = new int[INITIAL_NODES];
        nodeG = new double[INITIAL_NODES];
        nodeF = new double[INITIAL_NODES];
        nodeParent = new int[INITIAL_NODES];
        heap = new int[INITIAL_NODES];

        allocOpen(INITIAL_TABLE);
        allocClosed(INITIAL_TABLE);
        allocHeight(INITIAL_TABLE);
    }

    /**
     * Returns the arena to an empty state without releasing its arrays, so the next search
     * runs entirely out of memory that is already warm.
     */
    void reset() {
        nodeCount = 0;
        heapSize = 0;
        openSize = 0;
        closedSize = 0;
        heightSize = 0;
        java.util.Arrays.fill(openKeys, EMPTY_KEY);
        java.util.Arrays.fill(closedKeys, EMPTY_KEY);
        java.util.Arrays.fill(heightKeys, EMPTY_KEY);
    }

    // ------------------------------------------------------------------ nodes

    int newNode(long key, int x, int y, int z, double g, double f, int parent) {
        if (nodeCount == nodeKey.length) {
            growNodes();
        }
        int idx = nodeCount++;
        nodeKey[idx] = key;
        nodeX[idx] = x;
        nodeY[idx] = y;
        nodeZ[idx] = z;
        nodeG[idx] = g;
        nodeF[idx] = f;
        nodeParent[idx] = parent;
        return idx;
    }

    int nodeX(int idx) { return nodeX[idx]; }
    int nodeY(int idx) { return nodeY[idx]; }
    int nodeZ(int idx) { return nodeZ[idx]; }
    long nodeKey(int idx) { return nodeKey[idx]; }
    double nodeG(int idx) { return nodeG[idx]; }
    int nodeParent(int idx) { return nodeParent[idx]; }

    /** Relaxes an existing node onto a cheaper route and re-orders it in the heap. */
    void improve(int idx, double g, double f, int parent) {
        nodeG[idx] = g;
        nodeF[idx] = f;
        nodeParent[idx] = parent;
        int at = heapIndexOf(idx);
        if (at >= 0) {
            siftUp(at);
        } else {
            heapPush(idx);
        }
    }

    private void growNodes() {
        int size = nodeKey.length << 1;
        nodeKey = java.util.Arrays.copyOf(nodeKey, size);
        nodeX = java.util.Arrays.copyOf(nodeX, size);
        nodeY = java.util.Arrays.copyOf(nodeY, size);
        nodeZ = java.util.Arrays.copyOf(nodeZ, size);
        nodeG = java.util.Arrays.copyOf(nodeG, size);
        nodeF = java.util.Arrays.copyOf(nodeF, size);
        nodeParent = java.util.Arrays.copyOf(nodeParent, size);
    }

    // ------------------------------------------------------------------- heap

    boolean heapEmpty() {
        return heapSize == 0;
    }

    void heapPush(int nodeIdx) {
        if (heapSize == heap.length) {
            heap = java.util.Arrays.copyOf(heap, heap.length << 1);
        }
        heap[heapSize] = nodeIdx;
        siftUp(heapSize++);
    }

    int heapPop() {
        int top = heap[0];
        heap[0] = heap[--heapSize];
        if (heapSize > 0) {
            siftDown(0);
        }
        return top;
    }

    private int heapIndexOf(int nodeIdx) {
        // Linear, but only reached when a node is relaxed onto a cheaper route, which is a
        // small fraction of expansions; a full index map would cost more than it saves.
        for (int i = 0; i < heapSize; i++) {
            if (heap[i] == nodeIdx) {
                return i;
            }
        }
        return -1;
    }

    private void siftUp(int i) {
        int node = heap[i];
        double f = nodeF[node];
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (f >= nodeF[heap[parent]]) {
                break;
            }
            heap[i] = heap[parent];
            i = parent;
        }
        heap[i] = node;
    }

    private void siftDown(int i) {
        int node = heap[i];
        double f = nodeF[node];
        int half = heapSize >>> 1;
        while (i < half) {
            int child = (i << 1) + 1;
            int right = child + 1;
            if (right < heapSize && nodeF[heap[right]] < nodeF[heap[child]]) {
                child = right;
            }
            if (nodeF[heap[child]] >= f) {
                break;
            }
            heap[i] = heap[child];
            i = child;
        }
        heap[i] = node;
    }

    // --------------------------------------------------------------- open set

    /** @return node index for {@code key}, or -1 */
    int openGet(long key) {
        int slot = slotFor(openKeys, openMask, key);
        return openKeys[slot] == key ? openVals[slot] : -1;
    }

    void openPut(long key, int nodeIdx) {
        int slot = slotFor(openKeys, openMask, key);
        if (openKeys[slot] != key) {
            openKeys[slot] = key;
            openSize++;
        }
        openVals[slot] = nodeIdx;
        if (openSize > openKeys.length * LOAD_FACTOR) {
            rehashOpen();
        }
    }

    void openRemove(long key) {
        // Tombstone-free removal is not needed here: entries only leave the open set when
        // they enter the closed set, and the closed set is what every lookup consults first.
        int slot = slotFor(openKeys, openMask, key);
        if (openKeys[slot] == key) {
            openVals[slot] = -1;
        }
    }

    private void allocOpen(int capacity) {
        openKeys = new long[capacity];
        openVals = new int[capacity];
        openMask = capacity - 1;
        java.util.Arrays.fill(openKeys, EMPTY_KEY);
    }

    private void rehashOpen() {
        long[] oldKeys = openKeys;
        int[] oldVals = openVals;
        allocOpen(oldKeys.length << 1);
        openSize = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY && oldVals[i] >= 0) {
                openPut(oldKeys[i], oldVals[i]);
            }
        }
    }

    // ------------------------------------------------------------- closed set

    boolean closedContains(long key) {
        return closedKeys[slotFor(closedKeys, closedMask, key)] == key;
    }

    void closedAdd(long key) {
        int slot = slotFor(closedKeys, closedMask, key);
        if (closedKeys[slot] != key) {
            closedKeys[slot] = key;
            if (++closedSize > closedKeys.length * LOAD_FACTOR) {
                rehashClosed();
            }
        }
    }

    private void allocClosed(int capacity) {
        closedKeys = new long[capacity];
        closedMask = capacity - 1;
        java.util.Arrays.fill(closedKeys, EMPTY_KEY);
    }

    private void rehashClosed() {
        long[] old = closedKeys;
        allocClosed(old.length << 1);
        closedSize = 0;
        for (int i = 0; i < old.length; i++) {
            if (old[i] != EMPTY_KEY) {
                closedAdd(old[i]);
            }
        }
    }

    // ---------------------------------------------------------- height cache

    /** @return cached walkable height, or {@link Double#NaN} on a miss */
    double heightGet(long key) {
        int slot = slotFor(heightKeys, heightMask, key);
        return heightKeys[slot] == key ? heightVals[slot] : Double.NaN;
    }

    void heightPut(long key, double value) {
        int slot = slotFor(heightKeys, heightMask, key);
        if (heightKeys[slot] != key) {
            heightKeys[slot] = key;
            heightSize++;
        }
        heightVals[slot] = value;
        if (heightSize > heightKeys.length * LOAD_FACTOR) {
            rehashHeight();
        }
    }

    private void allocHeight(int capacity) {
        heightKeys = new long[capacity];
        heightVals = new double[capacity];
        heightMask = capacity - 1;
        java.util.Arrays.fill(heightKeys, EMPTY_KEY);
    }

    private void rehashHeight() {
        long[] oldKeys = heightKeys;
        double[] oldVals = heightVals;
        allocHeight(oldKeys.length << 1);
        heightSize = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY) {
                heightPut(oldKeys[i], oldVals[i]);
            }
        }
    }

    // ---------------------------------------------------------------- probing

    /** Linear probing on a mixed hash; tables are power-of-two so the mask replaces modulo. */
    private static int slotFor(long[] table, int mask, long key) {
        int slot = hash(key) & mask;
        while (true) {
            long present = table[slot];
            if (present == key || present == EMPTY_KEY) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private static int hash(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (int) h;
    }
}
