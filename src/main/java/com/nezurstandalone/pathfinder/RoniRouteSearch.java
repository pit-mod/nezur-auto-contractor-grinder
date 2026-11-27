package com.nezurstandalone.pathfinder;

import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Roni route ordering/cost model adapted to Nezur immutable collision captures. */
public final class RoniRouteSearch {
    public interface Terrain {
        boolean isWalkable(BlockPos pos);
        boolean isAir(BlockPos pos);
        boolean isSpaceFree(BlockPos pos);
        boolean canTraverse(BlockPos from, BlockPos to);
        boolean isSolid(BlockPos pos);
        boolean isPassable(BlockPos pos);
    }
    private final Terrain terrain;
    private final int jumpHeight;
    public RoniRouteSearch(Terrain terrain, int jumpHeight) {
        this.terrain=terrain;this.jumpHeight=Math.max(1,Math.min(5,jumpHeight));
    }
    private final Random random = new Random();
    private final Map<BlockPos, Boolean> walkableCache = new ConcurrentHashMap<>();
    private final Map<BlockPos, Integer> costCache = new ConcurrentHashMap<>();

    private int computeDropPenalty(BlockPos pos) {
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 2}, {2, -2}, {-2, 2}, {-2, -2}
        };
        for (int[] offset : offsets) {
            BlockPos neighbor = pos.add(offset[0], 0, offset[1]);
            if (terrain.isAir(neighbor) && terrain.isAir(neighbor.down()) && terrain.isAir(neighbor.down(2))) {
                return 30;
            }
        }
        return 0;
    }

    public List<BlockPos> findPath(BlockPos start, BlockPos target, int maxIterations) {
        return findPathRandomized(start, target, maxIterations, 0);
    }

    public List<BlockPos> findPathRandomized(BlockPos start, BlockPos target, int maxIterations, int randomness) {
        if (walkableCache.size() > 10000) {
            walkableCache.clear();
            costCache.clear();
        }
        int jumpHeight = this.jumpHeight;
        maxIterations=Math.max(1,Math.min(10000,maxIterations));
        randomness=Math.max(0,Math.min(100,randomness));
        if (jumpHeight > 2) {
            List<BlockPos> path = runAStar(start, target, maxIterations * 3 / 4, randomness, 2, jumpHeight);
            if (!path.isEmpty() && path.size() < 50) {
                return path;
            }
            return runAStar(start, target, maxIterations / 2, randomness, jumpHeight, jumpHeight);
        }
        return runAStar(start, target, maxIterations, randomness, jumpHeight, jumpHeight);
    }

    private List<BlockPos> buildPath(PathNode endNode) {
        LinkedList<BlockPos> path = new LinkedList<>();
        for (PathNode node = endNode; node != null; node = node.parent) {
            path.addFirst(node.pos);
        }
        return path;
    }

    public void clearCaches() {
        walkableCache.clear();
        costCache.clear();
    }

    private BlockPos snapToWalkable(BlockPos pos, int maxOffset) {
        if (isWalkableCached(pos)) {
            return pos;
        }
        for (int offset = 1; offset <= maxOffset; offset++) {
            BlockPos above = pos.up(offset);
            BlockPos below = pos.down(offset);
            if (isWalkableCached(above)) {
                return above;
            }
            if (isWalkableCached(below)) {
                return below;
            }
        }
        return pos;
    }

    public void invalidateCache(BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos neighbor = pos.add(dx, dy, dz);
                    walkableCache.remove(neighbor);
                    costCache.remove(neighbor);
                }
            }
        }
    }

    private boolean isWalkableCached(BlockPos pos) {
        Boolean cached = walkableCache.get(pos);
        if (cached != null) {
            return cached;
        }
        boolean walkable = terrain.isWalkable(pos);
        walkableCache.put(pos, walkable);
        return walkable;
    }

    private List<BlockPos> runAStar(BlockPos start, BlockPos target, int maxIterations, int randomness, int minJump, int maxJump) {
        BlockPos goal = snapToWalkable(target, 10);
        PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingInt(PathNode::getTotalCost));
        Map<BlockPos, Integer> bestCosts = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        open.add(new PathNode(start, 0, heuristicDistance(start, goal), null));
        bestCosts.put(start, 0);
        int iterations = 0;
        PathNode bestNode = null;
        int bestDistance = Integer.MAX_VALUE;
        while (!open.isEmpty() && iterations < maxIterations && !Thread.currentThread().isInterrupted()) {
            PathNode current = open.poll();
            ++iterations;
            int distance = heuristicDistance(current.pos, goal);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNode = current;
            }
            if (current.pos.equals(goal)) {
                return buildPath(current);
            }
            if (!closed.add(current.pos)) {
                continue;
            }
            for (PathCandidate candidate : generateNeighbors(current.pos, minJump, maxJump, randomness, iterations)) {
                int dy = candidate.pos.getY() - current.pos.getY();
                if (dy < -10 || dy > maxJump || !terrain.canTraverse(current.pos, candidate.pos)) {
                    continue;
                }
                int positionCost = getCachedCost(candidate.pos);
                int dropPenalty = computeDropPenalty(candidate.pos);
                int noise = randomness > 0 ? random.nextInt(randomness / 5 + 1) : 0;
                int cost = current.pathCost + candidate.moveCost + positionCost + dropPenalty + noise;
                Integer knownCost = bestCosts.get(candidate.pos);
                if (knownCost == null || cost < knownCost) {
                    bestCosts.put(candidate.pos, cost);
                    int heuristicNoise = randomness > 0 ? random.nextInt(randomness / 2 + 1) - randomness / 4 : 0;
                    open.add(new PathNode(candidate.pos, cost, heuristicDistance(candidate.pos, goal) + heuristicNoise, current));
                }
            }
        }
        return bestNode != null ? buildPath(bestNode) : Collections.emptyList();
    }

    private int getCachedCost(BlockPos pos) {
        Integer cached = costCache.get(pos);
        if (cached != null) {
            return cached;
        }
        int cost = computePositionCost(pos);
        costCache.put(pos, cost);
        return cost;
    }

    private int heuristicDistance(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        int dy = Math.abs(a.getY() - b.getY());
        int diag = Math.min(dx, dz);
        int straight = Math.max(dx, dz) - diag;
        return diag * 14 + straight * 10 + dy * 20;
    }

    private List<PathCandidate> generateNeighbors(BlockPos pos, int minJump, int maxJump, int randomness, int iteration) {
        List<PathCandidate> candidates = new ArrayList<>(32);
        int baseX = pos.getX();
        int baseY = pos.getY();
        int baseZ = pos.getZ();
        int[][] dirs = {
                {1, 0, 10}, {-1, 0, 10}, {0, 1, 10}, {0, -1, 10},
                {1, 1, 14}, {1, -1, 14}, {-1, 1, 14}, {-1, -1, 14}
        };
        int dirNumber = 0;
        for (int[] dir : dirs) {
            ++dirNumber;
            int x = baseX + dir[0];
            int z = baseZ + dir[1];
            int dirCost = dir[2];
            Set<Integer> heights = new LinkedHashSet<>();
            heights.add(baseY - 1);
            heights.add(baseY);
            for (int j = 1; j <= Math.min(minJump, maxJump); j++) {
                heights.add(baseY + j);
            }
            if (maxJump > minJump) {
                if (dirNumber % 3 == 0) {
                    heights.add(baseY + (maxJump + minJump) / 2);
                    heights.add(baseY + maxJump);
                }
                if (iteration < 50 && dirNumber % 2 == 0) {
                    for (int j = minJump + 1; j <= maxJump; j += 2) {
                        heights.add(baseY + j);
                    }
                }
            }
            for (int y : heights) {
                BlockPos candidate = new BlockPos(x, y, z);
                if (terrain.isSpaceFree(candidate)) {
                    for (int k = 1; k <= 10; k++) {
                        BlockPos below = candidate.down(k);
                        if (isWalkableCached(below)) {
                            candidates.add(new PathCandidate(below, dirCost + k * 2));
                            break;
                        }
                        if (!terrain.isAir(below)) {
                            break;
                        }
                    }
                }
                if (isWalkableCached(candidate)) {
                    candidates.add(new PathCandidate(candidate, dirCost));
                }
            }
        }
        if (randomness > 20) {
            Collections.shuffle(candidates, random);
        }
        return candidates;
    }

    private int computePositionCost(BlockPos pos) {
        int cost = 0;
        boolean[] blocked = new boolean[8];
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int blockedSides = 0;
        int blockedCorners = 0;
        for (int i = 0; i < offsets.length; i++) {
            BlockPos neighbor = pos.add(offsets[i][0], 0, offsets[i][1]);
            boolean feetBlocked = terrain.isSolid(neighbor) && !terrain.isPassable(neighbor);
            boolean headBlocked = terrain.isSolid(neighbor.up()) && !terrain.isPassable(neighbor.up());
            blocked[i] = feetBlocked || headBlocked;
            if (blocked[i]) {
                if (i < 4) {
                    blockedSides++;
                    cost += 15;
                } else {
                    blockedCorners++;
                    cost += 8;
                }
            }
        }
        int blockedTotal = blockedSides + blockedCorners;
        boolean corridorX = blocked[0] && blocked[1] && !blocked[2] && !blocked[3];
        boolean corridorZ = blocked[2] && blocked[3] && !blocked[0] && !blocked[1];
        if ((corridorX || corridorZ) && blockedTotal == 2) {
            cost /= 3;
        } else if (blockedTotal >= 3) {
            cost += 25;
            if (hasAdjacentBlockedSides(blocked)) {
                cost += 12;
            }
        } else if (blockedTotal == 2 && hasAdjacentBlockedSides(blocked)) {
            cost += 12;
        }
        return cost;
    }

    private boolean hasAdjacentBlockedSides(boolean[] blocked) {
        for (int i = 0; i < 4; i++) {
            if (blocked[i] && (blocked[(i + 1) % 4] || blocked[(i + 3) % 4])) {
                return true;
            }
        }
        return false;
    }

    private class PathNode {
        final PathNode parent;
        final int heuristicCost;
        final int pathCost;
        final BlockPos pos;

        PathNode(BlockPos pos, int pathCost, int heuristicCost, PathNode parent) {
            this.pos = pos;
            this.pathCost = pathCost;
            this.heuristicCost = heuristicCost;
            this.parent = parent;
        }

        int getTotalCost() {
            return pathCost + heuristicCost;
        }
    }

    private class PathCandidate {
        final BlockPos pos;
        final int moveCost;

        PathCandidate(BlockPos pos, int moveCost) {
            this.pos = pos;
            this.moveCost = moveCost;
        }
    }
}
