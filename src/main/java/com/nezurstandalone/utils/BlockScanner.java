package com.nezurstandalone.utils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Area block search that does not melt the frame rate.
 *
 * <p>The modules that needed to find a block in an area all wrote the same triple loop over
 * {@code World.getBlockState(center.add(x, y, z))}. That is the most expensive way to ask the
 * question, for three separate reasons, and the loops were large enough that all three mattered:
 *
 * <ul>
 *   <li>{@code center.add(...)} allocates a {@link BlockPos} per block. A radius-30 cube is
 *       226,981 short-lived objects per scan, which is pure garbage-collector pressure.</li>
 *   <li>{@code World.getBlockState} re-derives the chunk from the coordinates on every single
 *       call, so the chunk lookup is paid 226,981 times instead of once per chunk.</li>
 *   <li>Most of a chunk is air. Minecraft already stores that fact - a chunk is sixteen
 *       {@link ExtendedBlockStorage} sections and the empty ones are null or flagged empty - so
 *       a typical column can skip twelve sections, 4096 blocks each, on twelve null checks.</li>
 * </ul>
 *
 * <p>This walks chunk by chunk and section by section instead, reuses one mutable position, and
 * never touches an unloaded chunk. The result answers the same question as the naive loop while
 * reading a small fraction of the blocks.
 */
public final class BlockScanner {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private BlockScanner() {
    }

    /** Receives each matching block. Return false to stop the scan early. */
    public interface Visitor {
        boolean visit(BlockPos pos, IBlockState state);
    }

    /** Decides whether a block is interesting. Called for every non-air block in range. */
    public interface Match {
        boolean test(IBlockState state);
    }

    /**
     * Visits every block within {@code radius} of the player horizontally and {@code minY}
     * to {@code maxY} vertically that {@code match} accepts.
     *
     * <p>The vertical bounds are separate from the radius on purpose: the callers that used a
     * cube were nearly all looking for something whose height is known within a few blocks, and
     * a tall scan is where most of the wasted work was.
     */
    public static void scan(int radius, int minY, int maxY, Match match, Visitor visitor) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        final int centerX = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX);
        final int centerZ = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ);
        scanAround(centerX, centerZ, radius, minY, maxY, match, visitor);
    }

    public static void scanAround(int centerX, int centerZ, int radius, int minY, int maxY,
                                  Match match, Visitor visitor) {
        if (mc.theWorld == null) return;

        minY = Math.max(0, minY);
        maxY = Math.min(255, maxY);
        if (minY > maxY || radius < 0) return;

        final int minX = centerX - radius;
        final int maxX = centerX + radius;
        final int minZ = centerZ - radius;
        final int maxZ = centerZ + radius;

        final int minChunkX = minX >> 4;
        final int maxChunkX = maxX >> 4;
        final int minChunkZ = minZ >> 4;
        final int maxChunkZ = maxZ >> 4;

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(chunkX, chunkZ);
                if (chunk == null || !chunk.isLoaded()) continue;

                ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
                if (sections == null) continue;

                // Clip the requested box to this chunk so edge chunks are not over-scanned.
                final int startX = Math.max(minX, chunkX << 4);
                final int endX = Math.min(maxX, (chunkX << 4) + 15);
                final int startZ = Math.max(minZ, chunkZ << 4);
                final int endZ = Math.min(maxZ, (chunkZ << 4) + 15);

                for (int section = minY >> 4; section <= (maxY >> 4); section++) {
                    if (section < 0 || section >= sections.length) continue;
                    ExtendedBlockStorage storage = sections[section];
                    // The whole 16x16x16 block of this column is air. Skip it wholesale.
                    if (storage == null || storage.isEmpty()) continue;

                    final int sectionMinY = Math.max(minY, section << 4);
                    final int sectionMaxY = Math.min(maxY, (section << 4) + 15);

                    for (int y = sectionMinY; y <= sectionMaxY; y++) {
                        for (int x = startX; x <= endX; x++) {
                            for (int z = startZ; z <= endZ; z++) {
                                IBlockState state = storage.get(x & 15, y & 15, z & 15);
                                if (state == null || !match.test(state)) continue;
                                cursor.set(x, y, z);
                                if (!visitor.visit(cursor, state)) return;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A search spread over many calls, for areas too large to sweep in one frame.
     *
     * <p>A map-wide radius is a few million blocks even after empty sections are skipped, which
     * is more than a frame can afford. This keeps a chunk cursor and does a fixed number of
     * chunks per call, so the cost per frame is bounded no matter how large the area is. The
     * alternative the codebase had reached for - a background thread - is not available here:
     * chunk storage is not safe to read while the client thread is mutating it.
     */
    public static final class Incremental {

        private final int radius;
        private final int minY;
        private final int maxY;
        private final Match match;

        private int cursorX;
        private int cursorZ;
        private boolean started = false;
        private int originChunkX;
        private int originChunkZ;

        public Incremental(int radius, int minY, int maxY, Match match) {
            this.radius = radius;
            this.minY = minY;
            this.maxY = maxY;
            this.match = match;
        }

        /** Restarts the sweep from the player's current chunk. */
        public void reset() {
            started = false;
        }

        /**
         * Scans at most {@code chunksPerStep} chunks.
         *
         * @return the first match found in this step, or null if this step found nothing
         */
        public BlockPos step(int chunksPerStep) {
            if (mc.theWorld == null || mc.thePlayer == null) return null;

            int span = (radius >> 4) * 2 + 1;
            if (!started) {
                originChunkX = (net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX) >> 4) - (span / 2);
                originChunkZ = (net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ) >> 4) - (span / 2);
                cursorX = 0;
                cursorZ = 0;
                started = true;
            }

            final BlockPos[] hit = new BlockPos[1];
            for (int done = 0; done < chunksPerStep && hit[0] == null; done++) {
                if (cursorZ >= span) {
                    // Swept the whole area; begin again next call so a late spawn is still found.
                    started = false;
                    return null;
                }

                int chunkX = originChunkX + cursorX;
                int chunkZ = originChunkZ + cursorZ;

                scanAround((chunkX << 4) + 8, (chunkZ << 4) + 8, 7, minY, maxY, match,
                        (pos, state) -> {
                            hit[0] = new BlockPos(pos);
                            return false;
                        });

                cursorX++;
                if (cursorX >= span) {
                    cursorX = 0;
                    cursorZ++;
                }
            }
            return hit[0];
        }
    }

    /**
     * The first matching block, or null.
     *
     * <p>Note the returned position is a fresh immutable copy - the cursor handed to a
     * {@link Visitor} is reused between blocks and must never be stored.
     */
    public static BlockPos findFirst(int radius, int minY, int maxY, Match match) {
        final BlockPos[] hit = new BlockPos[1];
        scan(radius, minY, maxY, match, (pos, state) -> {
            hit[0] = new BlockPos(pos);
            return false;
        });
        return hit[0];
    }
}
