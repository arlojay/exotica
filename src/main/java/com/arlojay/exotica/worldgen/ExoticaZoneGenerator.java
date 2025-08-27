package com.arlojay.exotica.worldgen;

import com.arlojay.exotica.ExoticaMod;
import finalforeach.cosmicreach.blocks.BlockState;
import finalforeach.cosmicreach.savelib.blockdata.LayeredBlockData;
import finalforeach.cosmicreach.world.Chunk;
import finalforeach.cosmicreach.world.Zone;
import finalforeach.cosmicreach.worldgen.ChunkColumn;
import finalforeach.cosmicreach.worldgen.ZoneGenerator;
import finalforeach.cosmicreach.worldgen.noise.SimplexNoise;

public class ExoticaZoneGenerator extends ZoneGenerator {
    private final float baseLevel = 64f;
    private final float seaLevel = 64f;

    private final int softMaxY = 255;
    private final int softMinY = 0;

    // Fetches on class instantiation, so will not be null
    BlockState airBlock = this.getBlockStateInstance("base:air[default]");
    BlockState stoneBlock = this.getBlockStateInstance("base:stone_basalt[default]");
    BlockState waterBlock = this.getBlockStateInstance("base:water[default]");

    private SimplexNoise heightNoise;
    private SimplexNoise amplificationNoise;

    @Override
    public String getSaveKey() {
        return ExoticaMod.MOD_ID;
    }

    @Override
    protected String getName() {
        // Not fetched from the lang file
        return "Exotica";
    }

    // Called on world load/create, after this.seed is set
    @Override
    public void create() {
        // Create noise generators
        heightNoise = new SimplexNoise(this.seed);
        amplificationNoise = new SimplexNoise(this.seed);
    }

    // Generate a chunk-column of the world at once (easier for the lighting engine this way)
    @Override
    public void generateForChunkColumn(Zone zone, ChunkColumn col) {
        int maxChunkY = Math.floorDiv(this.softMaxY, 16);
        int minChunkY = Math.floorDiv(this.softMinY, 16);

        // Only generate the regions containing minY < y < maxY
        if(col.chunkY < minChunkY || col.chunkY > maxChunkY) return;


        for(int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
            var chunk = zone.getChunkAtChunkCoords(col.chunkX, chunkY, col.chunkZ);

            if (chunk == null) {
                // Create a new chunk if it doesn't exist
                chunk = new Chunk(col.chunkX, chunkY, col.chunkZ);

                // Create the chunk data
                chunk.initChunkData(() -> new LayeredBlockData<>(airBlock));

                // Register the chunks in the world and column
                zone.addChunk(chunk);
                col.addChunk(chunk);
            }

            // === Block placing logic ===


            // Loop through all blocks in the chunk
            for(int localX = 0; localX < Chunk.CHUNK_WIDTH; localX++) {
                int globalX = chunk.blockX + localX;

                for(int localZ = 0; localZ < Chunk.CHUNK_WIDTH; localZ++) {
                    int globalZ = chunk.blockZ + localZ;

                    // Only have to sample height once for each Y column (not going to change on the Y axis ;p)
                    double columnHeight = getHeight(globalX, globalZ);

                    for (int localY = 0; localY < Chunk.CHUNK_WIDTH; localY++) {
                        int globalY = chunk.blockY + localY;

                        // Below the ground
                        if(globalY <= columnHeight) {
                            // Don't want to set existing solid blocks to air in unloaded chunks (what about structures?)
                            chunk.setBlockState(stoneBlock, localX, localY, localZ);
                        }
                        // Above the ground
                        else {
                            // Below the sea level
                            if(globalY <= seaLevel) {
                                chunk.setBlockState(waterBlock, localX, localY, localZ);
                            }
                        }
                    }
                }
            }
        }
    }

    private double octave(SimplexNoise noise, float x, float z, int octaves, float lacunarity, float roughness) {
        float scale = 1;
        float weight = 1;
        float sumWeight = 0;
        float total = 0;

        for(int i = 0; i < octaves; i++) {
            total += noise.noise2(x * scale, z * scale) * weight;
            scale *= lacunarity;

            sumWeight += weight;
            weight *= roughness;
        }

        return total / sumWeight;
    }

    private double getHeight(float x, float z) {
        x *= 0.001f;
        z *= 0.001f;
        final float noiseAmplitude = 32f;

        float amplification = amplificationNoise.noise2(x, z) * 0.45f + 0.55f;

        return octave(heightNoise, x, z, 4, 2f, 0.5f) * (noiseAmplitude * amplification) + baseLevel;
    }

    @Override
    public int getDefaultRespawnYLevel() {
        return 0;
    }
}
