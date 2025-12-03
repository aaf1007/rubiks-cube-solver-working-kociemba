package rubikscube;

/**
 * Precomputed lookup tables for the two-phase Rubik's Cube solving algorithm.
 *
 * 1. Move Tables: Given a coordinate and a move, look up the resulting coordinate.
 *    This allows O(1) move application instead of actually manipulating the cube.
 *    Format: moveTable[currentCoord][move] = newCoord
 *
 * 2. Pruning Tables: Given a coordinate, look up the minimum moves to solve that aspect.
 *    Used as admissible heuristics for IDA* - they never overestimate, guaranteeing
 *    optimal solutions. Built via backward BFS from the solved state.
 *    Format: pruneTable[coord] = minMovesToSolve
 *
 * All tables are computed once when the class loads (takes ~1-2 seconds).
 */
public class Tables {

    // Coordinate space sizes - these define how many values each coordinate can have
    public static final int N_TWIST = 2187;        // 3^7 corner orientations (8th determined by sum=0 mod 3)
    public static final int N_FLIP = 2048;         // 2^11 edge orientations (12th determined by sum=0 mod 2)
    public static final int N_SLICE1 = 495;        // C(12,4) ways to choose which 4 positions hold E-slice edges
    public static final int N_SLICE2 = 24;         // 4! permutations of the 4 E-slice edges within their positions
    public static final int N_PARITY = 2;          // Permutation parity: 0=even, 1=odd
    public static final int N_CORNER_PERM = 20160; // Phase 2 corner permutation coordinate
    public static final int N_UD_EDGE_PERM = 20160;// Phase 2 UD-edge permutation coordinate
    public static final int N_SLICE_PERM = 11880;  // C(12,4) * 4! = full slice encoding (position + order)
    public static final int N_UR_UL = 1320;        // Helper: tracks edges UR, UF, UL for efficient merge
    public static final int N_UB_DF = 1320;        // Helper: tracks edges UB, DR, DF for efficient merge
    public static final int N_MOVES = 18;          // 6 faces * 3 turn types (90°, 180°, 270°)

    // Move Tables: [currentCoordinate][move] -> newCoordinate
    // Move encoding: U=0,1,2, R=3,4,5, F=6,7,8, D=9,10,11, L=12,13,14, B=15,16,17
    public static short[][] twistMove = new short[N_TWIST][N_MOVES];
    public static short[][] flipMove = new short[N_FLIP][N_MOVES];
    public static short[][] sliceMove = new short[N_SLICE_PERM][N_MOVES];
    public static short[][] cornerPermMove = new short[N_CORNER_PERM][N_MOVES];
    public static short[][] udEdgePermMove = new short[N_UD_EDGE_PERM][N_MOVES];
    public static short[][] urToUlMove = new short[N_UR_UL][N_MOVES];
    public static short[][] ubToDfMove = new short[N_UB_DF][N_MOVES];

    // Parity table: quarter turns flip parity, half turns preserve it
    // parityMove[currentParity][move] = newParity
    public static short[][] parityMove = {
        {1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1},
        {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0}
    };

    // Merge table: combines URtoUL and UBtoDF into full UD edge permutation
    // This avoids expensive computation during phase 2 setup
    public static short[][] mergeURtoULandUBtoDF = new short[336][336];

    // Pruning Tables: store minimum moves to solve each coordinate
    // Packed as Half-bytes (4 bits each) to save memory - 2 values per byte

    // Phase 1 pruning: heuristic = max(sliceTwistPrune, sliceFlipPrune)
    public static byte[] sliceTwistPrune = new byte[N_SLICE1 * N_TWIST / 2 + 1];
    public static byte[] sliceFlipPrune = new byte[N_SLICE1 * N_FLIP / 2];

    // Phase 2 pruning: heuristic = max(sliceCornerPrune, sliceEdgePrune)
    // These include parity in the index for more accurate pruning
    public static byte[] sliceCornerPrune = new byte[N_SLICE2 * N_CORNER_PERM * N_PARITY / 2];
    public static byte[] sliceEdgePrune = new byte[N_SLICE2 * N_UD_EDGE_PERM * N_PARITY / 2];

    // Static block runs once when class is loaded - builds all tables
    static {
        initMoveTables();
        initMergeTable();
        initPruningTables();
    }

    /**
     * Builds all move tables by simulating each move on each coordinate value.
     * For each coordinate i and face j, applies the move repeatedly (1-3 times)
     * and records the resulting coordinate. The 4th application restores the original.
     */
    private static void initMoveTables() {
        Cubie cube = new Cubie();

        // Twist move table: how corner orientations change with each move
        for (short i = 0; i < N_TWIST; i++) {
            cube.setTwist(i);
            for (int j = 0; j < 6; j++) {          // For each face (U,R,F,D,L,B)
                for (int k = 0; k < 3; k++) {      // For each turn amount (90,180,270)
                    cube.applyMove(j * 3);         // Apply one quarter turn
                    twistMove[i][j * 3 + k] = cube.getTwist();
                }
                cube.applyMove(j * 3);             // 4th turn restores original state
            }
        }

        // Flip move table: how edge orientations change with each move
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

        // Slice move table: how E-slice edge positions/order change with each move
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

        // Corner permutation move table: for phase 2
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

        // UD edge permutation move table: for phase 2
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

        // Helper move tables for efficient phase 2 edge coordinate computation
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

    /**
     * Builds the merge table that combines two helper edge coordinates into the
     * full UD edge permutation coordinate. This precomputation avoids expensive
     * merging during the phase 2 search.
     */
    private static void initMergeTable() {
        for (short i = 0; i < 336; i++) {
            for (short j = 0; j < 336; j++) {
                mergeURtoULandUBtoDF[i][j] = (short) Cubie.mergeURtoULandUBtoDF(i, j);
            }
        }
    }

    /**
     * Builds pruning tables using backward BFS from the solved state.
     *
     * Algorithm: Start with solved state (distance 0), then repeatedly find all
     * states at distance d and mark their neighbors as distance d+1 if unvisited.
     * Continue until all states are filled.
     *
     * The result is an admissible heuristic: it never overestimates the true
     * distance, so IDA* is guaranteed to find optimal solutions.
     */
    private static void initPruningTables() {
        // Initialize all entries to 0x0F (15) meaning "unvisited"
        for (int i = 0; i < sliceTwistPrune.length; i++) sliceTwistPrune[i] = -1;
        for (int i = 0; i < sliceFlipPrune.length; i++) sliceFlipPrune[i] = -1;
        for (int i = 0; i < sliceCornerPrune.length; i++) sliceCornerPrune[i] = -1;
        for (int i = 0; i < sliceEdgePrune.length; i++) sliceEdgePrune[i] = -1;

        // Phase 1: Slice position + Twist pruning table
        // Combined index = N_SLICE1 * twist + slice
        int depth = 0;
        setPruning(sliceTwistPrune, 0, (byte) 0);  // Solved state at distance 0
        int done = 1;
        while (done < N_SLICE1 * N_TWIST) {
            for (int i = 0; i < N_SLICE1 * N_TWIST; i++) {
                int twist = i / N_SLICE1;
                int slice = i % N_SLICE1;
                if (getPruning(sliceTwistPrune, i) == depth) {
                    // This state is at current depth - try all 18 moves
                    for (int m = 0; m < 18; m++) {
                        int newSlice = sliceMove[slice * 24][m] / 24;  // Only care about position, not order
                        int newTwist = twistMove[twist][m];
                        int idx = N_SLICE1 * newTwist + newSlice;
                        if (getPruning(sliceTwistPrune, idx) == 0x0F) {  // Unvisited
                            setPruning(sliceTwistPrune, idx, (byte) (depth + 1));
                            done++;
                        }
                    }
                }
            }
            depth++;
        }

        // Phase 1: Slice position + Flip pruning table
        // Combined index = N_SLICE1 * flip + slice
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

        // Phase 2: Slice permutation + Corner permutation + Parity pruning table
        // Only phase 2 moves are used (U, D all turns; R, F, L, B only half turns)
        depth = 0;
        setPruning(sliceCornerPrune, 0, (byte) 0);
        done = 1;
        while (done < N_SLICE2 * N_CORNER_PERM * N_PARITY) {
            for (int i = 0; i < N_SLICE2 * N_CORNER_PERM * N_PARITY; i++) {
                // Decode combined index
                int parity = i % 2;
                int corner = (i / 2) / N_SLICE2;
                int slice = (i / 2) % N_SLICE2;
                if (getPruning(sliceCornerPrune, i) == depth) {
                    for (int m = 0; m < 18; m++) {
                        if (!isPhase2Move(m)) continue;  // Skip non-phase-2 moves

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

        // Phase 2: Slice permutation + UD Edge permutation + Parity pruning table
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
     * Check if a move is valid for phase 2.
     * Phase 2 only allows moves that preserve the G1 subgroup:
     * - U, D faces: all turns allowed (they don't affect E-slice or orientations)
     * - R, F, L, B faces: only 180° turns (quarter turns would break G1)
     */
    private static boolean isPhase2Move(int m) {
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

    /**
     * Store a pruning value (0-15) at the given index using Half-byte packing.
     * Two values are stored per byte to save memory:
     * - Even indices use the lower 4 bits
     * - Odd indices use the upper 4 bits
     */
    public static void setPruning(byte[] table, int index, byte value) {
        if ((index & 1) == 0) {
            // Even index: store in lower, preserve upper
            table[index / 2] = (byte) ((table[index / 2] & 0xF0) | (value & 0x0F));
        } else {
            // Odd index: store in upper, preserve lower
            table[index / 2] = (byte) ((table[index / 2] & 0x0F) | ((value & 0x0F) << 4));
        }
    }

    /**
     * Retrieve a pruning value (0-15) from the given index.
     * Extracts the appropriate Half-byte based on whether index is even or odd.
     */
    public static byte getPruning(byte[] table, int index) {
        if ((index & 1) == 0) {
            return (byte) (table[index / 2] & 0x0F);
        } else {
            return (byte) ((table[index / 2] >>> 4) & 0x0F);  
        }
    }
}
