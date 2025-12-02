package rubikscube;

/**
 * Precomputed move tables and pruning tables for the two-phase algorithm.
 * 
 * Move tables: [coordinate][move] -> new coordinate
 * Pruning tables: [combined_coord] -> minimum moves to solve that aspect
 * 
 * All tables are computed once at class load time.
 */
public class Tables {

    // ==================== Constants ====================
    
    public static final int N_TWIST = 2187;       // 3^7 corner orientations
    public static final int N_FLIP = 2048;        // 2^11 edge orientations
    public static final int N_SLICE1 = 495;       // C(12,4) slice edge positions
    public static final int N_SLICE2 = 24;        // 4! slice edge permutations
    public static final int N_PARITY = 2;         // Even or odd
    public static final int N_CORNER_PERM = 20160;// 8!/2 corner permutations (phase 2)
    public static final int N_UD_EDGE_PERM = 20160;// Phase 2 UD edge permutations
    public static final int N_SLICE_PERM = 11880; // C(12,4) * 4! full slice encoding
    public static final int N_UR_UL = 1320;       // Helper coordinate
    public static final int N_UB_DF = 1320;       // Helper coordinate
    public static final int N_MOVES = 18;         // 6 faces * 3 turn types

    // ==================== Move Tables ====================
    
    public static short[][] twistMove = new short[N_TWIST][N_MOVES];
    public static short[][] flipMove = new short[N_FLIP][N_MOVES];
    public static short[][] sliceMove = new short[N_SLICE_PERM][N_MOVES];
    public static short[][] cornerPermMove = new short[N_CORNER_PERM][N_MOVES];
    public static short[][] udEdgePermMove = new short[N_UD_EDGE_PERM][N_MOVES];
    public static short[][] urToUlMove = new short[N_UR_UL][N_MOVES];
    public static short[][] ubToDfMove = new short[N_UB_DF][N_MOVES];
    
    // Parity change table: [parity][move] -> new parity
    public static short[][] parityMove = {
        {1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1},
        {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0}
    };

    // Merge table for phase 2 edge coordinate
    public static short[][] mergeURtoULandUBtoDF = new short[336][336];

    // ==================== Pruning Tables ====================
    
    // Phase 1 pruning: slice position + twist, slice position + flip
    public static byte[] sliceTwistPrune = new byte[N_SLICE1 * N_TWIST / 2 + 1];
    public static byte[] sliceFlipPrune = new byte[N_SLICE1 * N_FLIP / 2];
    
    // Phase 2 pruning: slice + corner + parity, slice + edge + parity
    public static byte[] sliceCornerPrune = new byte[N_SLICE2 * N_CORNER_PERM * N_PARITY / 2];
    public static byte[] sliceEdgePrune = new byte[N_SLICE2 * N_UD_EDGE_PERM * N_PARITY / 2];

    // ==================== Static Initialization ====================
    
    static {
        initMoveTables();
        initMergeTable();
        initPruningTables();
    }

    private static void initMoveTables() {
        PieceCube cube = new PieceCube();

        // Twist move table
        for (short i = 0; i < N_TWIST; i++) {
            cube.setTwist(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    twistMove[i][j * 3 + k] = cube.getTwist();
                }
                cube.applyMove(j * 3); // 4th turn restores
            }
        }

        // Flip move table
        for (short i = 0; i < N_FLIP; i++) {
            cube.setFlip(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    flipMove[i][j * 3 + k] = cube.getFlip();
                }
                cube.applyMove(j * 3);
            }
        }

        // Slice move table
        for (short i = 0; i < N_SLICE_PERM; i++) {
            cube.setSlice(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    sliceMove[i][j * 3 + k] = cube.getSlice();
                }
                cube.applyMove(j * 3);
            }
        }

        // Corner permutation move table
        for (short i = 0; i < N_CORNER_PERM; i++) {
            cube.setCornerPerm(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    cornerPermMove[i][j * 3 + k] = cube.getCornerPerm();
                }
                cube.applyMove(j * 3);
            }
        }

        // UD edge permutation move table
        for (short i = 0; i < N_UD_EDGE_PERM; i++) {
            cube.setUDEdgePerm(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    udEdgePermMove[i][j * 3 + k] = (short) cube.getUDEdgePerm();
                }
                cube.applyMove(j * 3);
            }
        }

        // UR-UL helper move table
        for (short i = 0; i < N_UR_UL; i++) {
            cube.setURtoUL(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    urToUlMove[i][j * 3 + k] = cube.getURtoUL();
                }
                cube.applyMove(j * 3);
            }
        }

        // UB-DF helper move table
        for (short i = 0; i < N_UB_DF; i++) {
            cube.setUBtoDF(i);
            for (int j = 0; j < 6; j++) {
                for (int k = 0; k < 3; k++) {
                    cube.applyMove(j * 3);
                    ubToDfMove[i][j * 3 + k] = cube.getUBtoDF();
                }
                cube.applyMove(j * 3);
            }
        }
    }

    private static void initMergeTable() {
        for (short i = 0; i < 336; i++) {
            for (short j = 0; j < 336; j++) {
                mergeURtoULandUBtoDF[i][j] = (short) PieceCube.mergeURtoULandUBtoDF(i, j);
            }
        }
    }

    private static void initPruningTables() {
        // Initialize all to -1 (0x0F in nibble)
        for (int i = 0; i < sliceTwistPrune.length; i++) sliceTwistPrune[i] = -1;
        for (int i = 0; i < sliceFlipPrune.length; i++) sliceFlipPrune[i] = -1;
        for (int i = 0; i < sliceCornerPrune.length; i++) sliceCornerPrune[i] = -1;
        for (int i = 0; i < sliceEdgePrune.length; i++) sliceEdgePrune[i] = -1;

        // Phase 1: Slice + Twist pruning
        int depth = 0;
        setPruning(sliceTwistPrune, 0, (byte) 0);
        int done = 1;
        while (done < N_SLICE1 * N_TWIST) {
            for (int i = 0; i < N_SLICE1 * N_TWIST; i++) {
                int twist = i / N_SLICE1;
                int slice = i % N_SLICE1;
                if (getPruning(sliceTwistPrune, i) == depth) {
                    for (int m = 0; m < 18; m++) {
                        int newSlice = sliceMove[slice * 24][m] / 24;
                        int newTwist = twistMove[twist][m];
                        int idx = N_SLICE1 * newTwist + newSlice;
                        if (getPruning(sliceTwistPrune, idx) == 0x0F) {
                            setPruning(sliceTwistPrune, idx, (byte) (depth + 1));
                            done++;
                        }
                    }
                }
            }
            depth++;
        }

        // Phase 1: Slice + Flip pruning
        depth = 0;
        setPruning(sliceFlipPrune, 0, (byte) 0);
        done = 1;
        while (done < N_SLICE1 * N_FLIP) {
            for (int i = 0; i < N_SLICE1 * N_FLIP; i++) {
                int flip = i / N_SLICE1;
                int slice = i % N_SLICE1;
                if (getPruning(sliceFlipPrune, i) == depth) {
                    for (int m = 0; m < 18; m++) {
                        int newSlice = sliceMove[slice * 24][m] / 24;
                        int newFlip = flipMove[flip][m];
                        int idx = N_SLICE1 * newFlip + newSlice;
                        if (getPruning(sliceFlipPrune, idx) == 0x0F) {
                            setPruning(sliceFlipPrune, idx, (byte) (depth + 1));
                            done++;
                        }
                    }
                }
            }
            depth++;
        }

        // Phase 2: Slice + Corner + Parity pruning
        depth = 0;
        setPruning(sliceCornerPrune, 0, (byte) 0);
        done = 1;
        while (done < N_SLICE2 * N_CORNER_PERM * N_PARITY) {
            for (int i = 0; i < N_SLICE2 * N_CORNER_PERM * N_PARITY; i++) {
                int parity = i % 2;
                int corner = (i / 2) / N_SLICE2;
                int slice = (i / 2) % N_SLICE2;
                if (getPruning(sliceCornerPrune, i) == depth) {
                    for (int m = 0; m < 18; m++) {
                        // Phase 2 only uses U, U2, U', D, D2, D', R2, F2, L2, B2
                        if (!isPhase2Move(m)) continue;
                        
                        int newSlice = sliceMove[slice][m];
                        int newCorner = cornerPermMove[corner][m];
                        int newParity = parityMove[parity][m];
                        int idx = (N_SLICE2 * newCorner + newSlice) * 2 + newParity;
                        if (getPruning(sliceCornerPrune, idx) == 0x0F) {
                            setPruning(sliceCornerPrune, idx, (byte) (depth + 1));
                            done++;
                        }
                    }
                }
            }
            depth++;
        }

        // Phase 2: Slice + Edge + Parity pruning
        depth = 0;
        setPruning(sliceEdgePrune, 0, (byte) 0);
        done = 1;
        while (done < N_SLICE2 * N_UD_EDGE_PERM * N_PARITY) {
            for (int i = 0; i < N_SLICE2 * N_UD_EDGE_PERM * N_PARITY; i++) {
                int parity = i % 2;
                int edge = (i / 2) / N_SLICE2;
                int slice = (i / 2) % N_SLICE2;
                if (getPruning(sliceEdgePrune, i) == depth) {
                    for (int m = 0; m < 18; m++) {
                        if (!isPhase2Move(m)) continue;
                        
                        int newSlice = sliceMove[slice][m];
                        int newEdge = udEdgePermMove[edge][m];
                        int newParity = parityMove[parity][m];
                        int idx = (N_SLICE2 * newEdge + newSlice) * 2 + newParity;
                        if (getPruning(sliceEdgePrune, idx) == 0x0F) {
                            setPruning(sliceEdgePrune, idx, (byte) (depth + 1));
                            done++;
                        }
                    }
                }
            }
            depth++;
        }
    }

    /**
     * Check if move is valid for phase 2.
     * Phase 2 moves: U, U2, U', D, D2, D', R2, F2, L2, B2
     */
    private static boolean isPhase2Move(int m) {
        // U=0,1,2, D=9,10,11 are all valid
        // R=4, F=7, L=13, B=16 (only 180 degree turns)
        switch (m) {
            case 0: case 1: case 2:   // U, U2, U'
            case 9: case 10: case 11: // D, D2, D'
            case 4:  // R2
            case 7:  // F2
            case 13: // L2
            case 16: // B2
                return true;
            default:
                return false;
        }
    }

    // ==================== Pruning Table Access ====================

    public static void setPruning(byte[] table, int index, byte value) {
        if ((index & 1) == 0) {
            table[index / 2] = (byte) ((table[index / 2] & 0xF0) | (value & 0x0F));
        } else {
            table[index / 2] = (byte) ((table[index / 2] & 0x0F) | ((value & 0x0F) << 4));
        }
    }

    public static byte getPruning(byte[] table, int index) {
        if ((index & 1) == 0) {
            return (byte) (table[index / 2] & 0x0F);
        } else {
            return (byte) ((table[index / 2] >>> 4) & 0x0F);
        }
    }
}
