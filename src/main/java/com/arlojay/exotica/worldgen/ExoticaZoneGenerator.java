package com.arlojay.exotica.worldgen;

import com.arlojay.exotica.ExoticaMod;
import com.badlogic.gdx.math.MathUtils;
import finalforeach.cosmicreach.blocks.BlockState;
import finalforeach.cosmicreach.savelib.blockdata.LayeredBlockData;
import finalforeach.cosmicreach.util.GameTag;
import finalforeach.cosmicreach.world.Chunk;
import finalforeach.cosmicreach.world.Zone;
import finalforeach.cosmicreach.worldgen.ChunkColumn;
import finalforeach.cosmicreach.worldgen.ZoneGenerator;
import finalforeach.cosmicreach.worldgen.noise.SimplexNoise;
import finalforeach.cosmicreach.worldgen.trees.PoplarTree;

import java.util.HashSet;
import java.util.Random;

public class ExoticaZoneGenerator extends ZoneGenerator {
    private final float baseLevel = 128f;
    private final float seaLevel = 128f;

    private final int softMaxY = 512;
    private final int softMinY = 0;

    BlockState airBlock = this.getBlockStateInstance("base:air[default]");
    BlockState stoneBlock = this.getBlockStateInstance("base:stone_basalt[default]");
    BlockState dirtBlock = this.getBlockStateInstance("base:dirt[default]");
    BlockState sandBlock = this.getBlockStateInstance("base:sand[default]");
    BlockState sandstoneBlock = this.getBlockStateInstance("base:sandstone[default]");
    BlockState grassBlock = this.getBlockStateInstance("base:grass[default]");
    BlockState waterBlock = this.getBlockStateInstance("base:water[default]");

    private SimplexNoise heightNoise;
    private SimplexNoise amplificationNoise;
    private SimplexNoise roughnessNoise;
    private SimplexNoise ridgeNoise;
    private SimplexNoise heightBumpNoise;
    private SimplexNoise continentNoise;
    private SimplexNoise wackyNoise;
    private SimplexNoise randomSeeder;
    private SimplexNoise fadeNoise;
    private SimplexNoise temperatureNoise;
    private SimplexNoise humidityNoise;
    private final Random random = new Random();

    private final float[] columnHeightsRaw = new float[5 * 5];

    private final float[] columnHeights = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] bumpStrengths = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] bumpMixes = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] lowestHeights = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] continentValues = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] amplificationValues = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] fadeValues = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] temperatureValues = new float[CHUNK_WIDTH * CHUNK_WIDTH];
    private final float[] humidityValues = new float[CHUNK_WIDTH * CHUNK_WIDTH];

    private float lastRoughnessValue = 0f;
    private float lastAmplificationValue = 0f;
    private float lastContinentValue = 0f;
    private float lastWackyValue = 0f;

    @Override
    public String getSaveKey() {
        return ExoticaMod.MOD_ID;
    }

    @Override
    protected String getName() {
        return "Exotica";
    }

    @Override
    public void create() {
        var random = new Random(this.seed);
        var seeds = random.longs(512).toArray();

        heightNoise = new SimplexNoise(seeds[0]);
        amplificationNoise = new SimplexNoise(seeds[1]);
        roughnessNoise = new SimplexNoise(seeds[2]);
        ridgeNoise = new SimplexNoise(seeds[3]);
        heightBumpNoise = new SimplexNoise(seeds[4]);
        continentNoise = new SimplexNoise(seeds[5]);
        wackyNoise = new SimplexNoise(seeds[6]);
        randomSeeder = new SimplexNoise(seeds[7]);
        fadeNoise = new SimplexNoise(seeds[8]);
        temperatureNoise = new SimplexNoise(seeds[9]);
        humidityNoise = new SimplexNoise(seeds[10]);
    }

    static abstract class Structure {
        public abstract void place(Zone zone, long seed, int x, int y, int z);
        public abstract boolean canPlace(Zone zone, int x, int y, int z);
    }

    private static final GameTag TEMPERATE_SOIL_TAG = GameTag.get("soil_temperate");
    private static final Structure POPLAR_TREE_STRUCTURE = new Structure() {
        @Override
        public void place(Zone zone, long seed, int x, int y, int z) {
            PoplarTree.generateTree(seed, zone, x, y, z);
        }

        @Override
        public boolean canPlace(Zone zone, int x, int y, int z) {
            var block = zone.getBlockState(x, y - 1, z);
            if(block == null) return false;

            return block.hasTag(TEMPERATE_SOIL_TAG);
        }
    };
    
    class SurfaceStructure {
        public final int localX;
        public final int localZ;
        private final Structure structure;

        public SurfaceStructure(int localX, int localZ, Structure structure) {
            this.localX = localX;
            this.localZ = localZ;
            this.structure = structure;
        }

        public void place(Zone zone, int x, int y, int z) {
            this.structure.place(zone, seed, x, y, z);
        }
        public boolean canPlace(Zone zone, int x, int y, int z) {
            return this.structure.canPlace(zone, x, y, z);
        }
    }

    @Override
    public void generateForChunkColumn(Zone zone, ChunkColumn col) {
        int maxChunkY = Math.floorDiv(this.softMaxY, 16);
        int minChunkY = Math.floorDiv(this.softMinY, 16);

        if(col.chunkY < minChunkY || col.chunkY > maxChunkY) return;

        int blockX = col.getBlockX();
        int blockY = col.getBlockY();
        int blockZ = col.getBlockZ();


        for(int x = 0, i = 0; x < CHUNK_WIDTH; x++) {
            for(int z = 0; z < CHUNK_WIDTH; z++, i++) {
//                int interpolatedIndex = ((x >> 2) * 5 + (z >> 2));
//                float dx = (x / 4f) % 1f;
//                float rdx = 1f - dx;
//                float dz = (z / 4f) % 1f;
//                float rdz = 1f - dz;
//                (
//                        (columnHeightsRaw[interpolatedIndex] * rdx + columnHeightsRaw[interpolatedIndex + 5] * dx) * rdz +
//                        (columnHeightsRaw[interpolatedIndex + 1] * rdx + columnHeightsRaw[interpolatedIndex + 6] * dx) * dz
//                );

                columnHeights[i] = getHeight(blockX + x, blockZ + z);
                lowestHeights[i] = Float.NaN;
                bumpStrengths[i] = 16f * (lastWackyValue * 0.5f + 0.5f) * MathUtils.clamp(lastContinentValue * 8f + lastAmplificationValue + 0.5f, 0f, 1f);
                bumpMixes[i] = (lastRoughnessValue * 0.3f + 0.5f);
                continentValues[i] = lastContinentValue;
                amplificationValues[i] = lastAmplificationValue;
                fadeValues[i] = fadeNoise.noise2((blockX + x) * 0.1f, (blockZ + z) * 0.1f);
                humidityValues[i] = humidityNoise.noise2((blockX + x * 4) * 0.01f, (blockZ + z * 4) * 0.01f);
                temperatureValues[i] = temperatureNoise.noise2((blockX + x) * 0.01f, (blockZ + z) * 0.01f);
            }
        }

        random.setSeed(Float.floatToRawIntBits(randomSeeder.noise2(blockX, blockZ)));
        HashSet<SurfaceStructure> structures = new HashSet<>();

        for(int i = 0; i < 6; i++) {
            int x = random.nextInt(0, 16);
            int z = random.nextInt(0, 16);

            float humidity = humidityValues[x * CHUNK_WIDTH + z];
            float temperature = temperatureValues[x * CHUNK_WIDTH + z];

            if(humidity > 0f && temperature > 0f) {
                structures.add(new SurfaceStructure(x, z, POPLAR_TREE_STRUCTURE));
            } else if(humidity < 0f || temperature > 0f && random.nextFloat(0f, 1f) > 0.8f) {
                structures.add(new SurfaceStructure(x, z, POPLAR_TREE_STRUCTURE));
            }
        }

        boolean topChunk = true;
        for(int chunkY = col.chunkY + 16; chunkY >= col.chunkY; chunkY--, topChunk = false) {
            Chunk chunk = null;
            if(!topChunk) {
                chunk = zone.getChunkAtChunkCoords(col.chunkX, chunkY, col.chunkZ);

                if (chunk == null) {
                    chunk = new Chunk(col.chunkX, chunkY, col.chunkZ);

                    chunk.initChunkData(() -> new LayeredBlockData<>(airBlock));

                    zone.addChunk(chunk);
                    col.addChunk(chunk);
                }
            }

            for(int localX = 0, i = 0; localX < Chunk.CHUNK_WIDTH; localX++) {
                int globalX = blockX + localX;

                for(int localZ = 0; localZ < Chunk.CHUNK_WIDTH; localZ++, i++) {
                    int globalZ = blockZ + localZ;

                    float columnHeight = columnHeights[i];
                    float bumpStrength = bumpStrengths[i];
                    float bumpMix = bumpMixes[i];
                    float lowestHeight = lowestHeights[i];
                    float continentValue = continentValues[i];
                    float amplificationValue = amplificationValues[i];

                    for (int localY = Chunk.CHUNK_WIDTH - 1; localY >= 0; localY--) {
                        int globalY = chunkY * CHUNK_WIDTH + localY;

                        float distanceFromSurface = Math.abs(globalY - columnHeight);

                        float heightBump = distanceFromSurface < bumpStrength ? ((
                                heightBumpNoise.noise3_XZBeforeY(globalX * 0.01f, globalY * 0.02f, globalZ * 0.01f) * bumpMix +
                                heightBumpNoise.noise3_XZBeforeY(globalX * 0.06f, globalY * 0.1f, globalZ * 0.06f) * (1 - bumpMix)
                        ) + 1f) * bumpStrength : bumpStrength;

                        float height = columnHeight + heightBump;

                        if(Float.isNaN(lowestHeight)) {
                            lowestHeight = Math.min(globalY, height);
                        }
                        if(height < lowestHeight && globalY > height) {
                            lowestHeight = height;
                        }

                        if(topChunk) continue;

                        boolean grass = true;
                        if(continentValue < 0.05f) grass = false;
                        if(lowestHeight > seaLevel + 8f + fadeValues[i] * amplificationValue * 4f) grass = true;
                        if(globalY < seaLevel + 1) grass = false;
                        if(height < seaLevel + 2.5f) grass = false;

                        boolean isAir = true;
                        if(grass) {
                            if(globalY <= lowestHeight - 4) {
                                chunk.setBlockState(stoneBlock, localX, localY, localZ);
                                isAir = false;
                            } else if(globalY <= lowestHeight - 1) {
                                chunk.setBlockState(dirtBlock, localX, localY, localZ);
                                isAir = false;
                            } else if(globalY <= lowestHeight) {
                                chunk.setBlockState(grassBlock, localX, localY, localZ);
                                isAir = false;
                            }
                        } else {
                            if(globalY <= lowestHeight - 4) {
                                chunk.setBlockState(stoneBlock, localX, localY, localZ);
                                isAir = false;
                            } else if(globalY <= lowestHeight - 2) {
                                chunk.setBlockState(sandstoneBlock, localX, localY, localZ);
                                isAir = false;
                            } else if(globalY <= lowestHeight) {
                                chunk.setBlockState(sandBlock, localX, localY, localZ);
                                isAir = false;
                            }
                        }
                        if(globalY <= seaLevel && isAir) {
                            chunk.setBlockState(waterBlock, localX, localY, localZ);
                        }
                        if(globalY <= lowestHeight && globalY > lowestHeight - 1) {
                            SurfaceStructure placedStructure = null;
                            for (var structure : structures) {
                                if (structure.localX == localX && structure.localZ == localZ) {
                                    if (structure.canPlace(zone, globalX, globalY + 1, globalZ)) {
                                        structure.place(zone, globalX, globalY + 1, globalZ);
                                        placedStructure = structure;
                                        break;
                                    }
                                }
                            }
                            if (placedStructure != null) structures.remove(placedStructure);
                        }
                    }

                    lowestHeights[i] = lowestHeight;
                }
            }
        }
    }

    private float octave(SimplexNoise noise, float x, float z, int octaves, float lacunarity, float roughness) {
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

    private float getHeight(float x, float z) {
        x *= 0.001f;
        z *= 0.001f;

        float amplificationRaw = amplificationNoise.noise2(x, z);
        float amplification = amplificationRaw * Math.abs(amplificationRaw) * 0.45f + 0.55f;
        float roughnessRaw = roughnessNoise.noise2(x, z);
        float roughness = Math.max(0, roughnessRaw * 0.65f + 0.35f);
        float continentRaw = octave(continentNoise, x * 3f, z * 3f, 2, 2.3f, 0.25f);
        float wackyRaw = wackyNoise.noise2(x * 1.5f, z * 1.5f);

        float continent;
        if(continentRaw > 0.4f) {
            continent = MathUtils.map(0.4f, 1.0f, 0.7f, 1.0f, continentRaw);
        } else if(continentRaw > -0.4f) {
            continent = MathUtils.map(-0.4f, 0.4f, 0.3f, 0.7f, continentRaw);
        } else {
            continent = MathUtils.map(-1.0f, -0.4f, 0.0f, 0.3f, continentRaw);
        }

        float ridgeRaw = octave(ridgeNoise,
                x * 4f, z * 4f,
                3, 2f, 0.4f
        );
        float ridge = 1f - Math.abs(ridgeRaw);
        ridge *= amplification * roughness;
        ridge *= ridge;

        float groundHeight = baseLevel;
        groundHeight += (octave(heightNoise,
                x * 3f, z * 3f,
                4, 2.3f, 0.55f + roughnessRaw * 0.15f
        ) * 0.5f + 0.5f) * (128f * amplification);
        groundHeight += (heightNoise.noise2(x * 30f, z * 30f) * 0.5f + 0.5f) * 3f * roughness;
        groundHeight += ridge * 32f;


        float seaHeight = seaLevel - 32f;


        lastRoughnessValue = roughnessRaw;
        lastAmplificationValue = amplificationRaw;
        lastContinentValue = continentRaw;
        lastWackyValue = wackyRaw;
        return MathUtils.lerp(seaHeight, groundHeight, continent);
    }

    @Override
    public int getDefaultRespawnYLevel() {
        return Integer.MIN_VALUE;
    }
}
