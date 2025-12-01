package rubikscube;

/**
 * Two-phase algorithm implementation for solving Rubik's Cube.
 * 
 * Phase 1: Reduce cube to <U,D,R2,F2,L2,B2> subgroup
 *          (orient all pieces, position E-slice edges)
 * 
 * Phase 2: Solve within the subgroup using only allowed moves
 */
public class TwoPhase {

    // Move names for solution output
    private static final String[] MOVE_NAMES = {
        "U", "U2", "U'", "R", "R2", "R'", "F", "F2", "F'",
        "D", "D2", "D'", "L", "L2", "L'", "B", "B2", "B'"
    };

    // Phase 2 allowed moves (indices into the 18-move array)
    private static final int[] PHASE2_MOVES = {0, 1, 2, 4, 7, 9, 10, 11, 13, 16};

    // Search state
    private int[] phase1Moves = new int[31];
    private int[] phase2Moves = new int[31];
    private int[] phase1Axis = new int[31];
    private int[] phase2Axis = new int[31];

    // Coordinate stacks for phase 1
    private int[] twistStack = new int[31];
    private int[] flipStack = new int[31];
    private int[] sliceStack = new int[31];

    // Coordinate stacks for phase 2
    private int[] cornerStack = new int[31];
    private int[] edgeStack = new int[31];
    private int[] slice2Stack = new int[31];
    private int[] parityStack = new int[31];

    // Helper coordinates for merging
    private int[] urToUlStack = new int[31];
    private int[] ubToDfStack = new int[31];

    // Original cube for phase 2 coordinate computation
    private PieceCube originalCube;

    // Best solution found
    private String solution = "";
    private int solutionLength = Integer.MAX_VALUE;

    /**
     * Solve the cube, returning solution as move string.
     * @param cube The scrambled cube
     * @param maxDepth Maximum total solution length
     * @param timeout Maximum seconds to search
     * @return Solution string or error message
     */
    public static String solve(PieceCube cube, int maxDepth, long timeout) {
        TwoPhase search = new TwoPhase();
        return search.doSolve(cube, maxDepth, timeout);
    }

    private String doSolve(PieceCube cube, int maxDepth, long timeout) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout * 1000;

        // Store original cube for phase 2 coordinate computation
        originalCube = cube;

        // Initial coordinates
        twistStack[0] = cube.getTwist();
        flipStack[0] = cube.getFlip();
        sliceStack[0] = cube.getSlice();
        parityStack[0] = cube.cornerParity();
        urToUlStack[0] = cube.getURtoUL();
        ubToDfStack[0] = cube.getUBtoDF();

        // Try increasing phase 1 depths
        for (int phase1Depth = 0; phase1Depth <= maxDepth; phase1Depth++) {
            if (System.currentTimeMillis() > endTime) {
                break;
            }

            int result = phase1Search(0, phase1Depth, -1, maxDepth, endTime);
            if (result >= 0) {
                // Found a solution
                break;
            }
        }

        if (solution.isEmpty()) {
            return "Error: No solution found";
        }
        return solution;
    }

    /**
     * Phase 1 IDA* search.
     * @return -1 if no solution at this depth, >= 0 if found
     */
    private int phase1Search(int depth, int maxPhase1, int lastAxis, int maxTotal, long endTime) {
        if (System.currentTimeMillis() > endTime) return -1;

        int twist = twistStack[depth];
        int flip = flipStack[depth];
        int slice = sliceStack[depth] / 24; // Only need position, not permutation

        // Check pruning
        int prune1 = Tables.getPruning(Tables.sliceTwistPrune, Tables.N_SLICE1 * twist + slice);
        int prune2 = Tables.getPruning(Tables.sliceFlipPrune, Tables.N_SLICE1 * flip + slice);
        int estimate = Math.max(prune1, prune2);

        if (estimate > maxPhase1 - depth) {
            return -1; // Can't reach goal
        }

        // Phase 1 goal reached?
        if (twist == 0 && flip == 0 && sliceStack[depth] < 24) {
            // Initialize phase 2 coordinates
            cornerStack[depth] = cubeCornerPerm(depth);
            slice2Stack[depth] = sliceStack[depth];
            parityStack[depth] = parityStack[0]; // Recompute from moves
            
            // Compute parity after phase 1 moves
            int parity = parityStack[0];
            for (int i = 0; i < depth; i++) {
                parity = Tables.parityMove[parity][phase1Moves[i]];
            }
            parityStack[depth] = parity;

            // Compute edge coordinate
            int urUl = urToUlStack[0];
            int ubDf = ubToDfStack[0];
            for (int i = 0; i < depth; i++) {
                urUl = Tables.urToUlMove[urUl][phase1Moves[i]];
                ubDf = Tables.ubToDfMove[ubDf][phase1Moves[i]];
            }
            edgeStack[depth] = Tables.mergeURtoULandUBtoDF[urUl][ubDf];

            // Search phase 2
            int maxPhase2 = Math.min(maxTotal - depth, solutionLength - depth - 1);
            for (int phase2Depth = 0; phase2Depth <= maxPhase2; phase2Depth++) {
                int result = phase2Search(depth, phase2Depth, lastAxis, endTime);
                if (result >= 0) {
                    return result;
                }
            }
            return -1;
        }

        // Try all moves
        for (int move = 0; move < 18; move++) {
            int axis = move / 3;
            
            // Skip same axis or opposite axis after same axis
            if (axis == lastAxis) continue;
            if (axis == lastAxis - 3 || axis == lastAxis + 3) continue;

            twistStack[depth + 1] = Tables.twistMove[twist][move];
            flipStack[depth + 1] = Tables.flipMove[flip][move];
            sliceStack[depth + 1] = Tables.sliceMove[sliceStack[depth]][move];
            phase1Moves[depth] = move;
            phase1Axis[depth] = axis;

            int result = phase1Search(depth + 1, maxPhase1, axis, maxTotal, endTime);
            if (result >= 0) return result;
        }

        return -1;
    }

    /**
     * Phase 2 IDA* search.
     */
    private int phase2Search(int phase1Len, int maxPhase2, int lastAxis, long endTime) {
        return phase2SearchInner(phase1Len, 0, maxPhase2, lastAxis, endTime);
    }

    private int phase2SearchInner(int phase1Len, int depth, int maxPhase2, int lastAxis, long endTime) {
        if (System.currentTimeMillis() > endTime) return -1;

        int corner = cornerStack[phase1Len + depth];
        int edge = edgeStack[phase1Len + depth];
        int slice = slice2Stack[phase1Len + depth];
        int parity = parityStack[phase1Len + depth];

        // Check pruning
        int prune1 = Tables.getPruning(Tables.sliceCornerPrune, 
                     (Tables.N_SLICE2 * corner + slice) * 2 + parity);
        int prune2 = Tables.getPruning(Tables.sliceEdgePrune,
                     (Tables.N_SLICE2 * edge + slice) * 2 + parity);
        int estimate = Math.max(prune1, prune2);

        if (estimate > maxPhase2 - depth) {
            return -1;
        }

        // Goal reached?
        if (corner == 0 && edge == 0 && slice == 0) {
            // Build solution string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < phase1Len; i++) {
                sb.append(MOVE_NAMES[phase1Moves[i]]);
            }
            for (int i = 0; i < depth; i++) {
                sb.append(MOVE_NAMES[phase2Moves[i]]);
            }
            
            int totalLen = phase1Len + depth;
            if (totalLen < solutionLength) {
                solutionLength = totalLen;
                solution = sb.toString();
            }
            return totalLen;
        }

        // Try phase 2 moves
        for (int m : PHASE2_MOVES) {
            int axis = m / 3;
            
            if (axis == lastAxis) continue;
            if (axis == lastAxis - 3 || axis == lastAxis + 3) continue;

            cornerStack[phase1Len + depth + 1] = Tables.cornerPermMove[corner][m];
            edgeStack[phase1Len + depth + 1] = Tables.udEdgePermMove[edge][m];
            slice2Stack[phase1Len + depth + 1] = Tables.sliceMove[slice][m];
            parityStack[phase1Len + depth + 1] = Tables.parityMove[parity][m];
            phase2Moves[depth] = m;
            phase2Axis[depth] = axis;

            int result = phase2SearchInner(phase1Len, depth + 1, maxPhase2, axis, endTime);
            if (result >= 0) return result;
        }

        return -1;
    }

    /**
     * Compute corner permutation after applying phase 1 moves to original cube.
     */
    private int cubeCornerPerm(int phase1Len) {
        // Create copy of original cube
        PieceCube cube = new PieceCube();
        cube.cornerPerm = originalCube.cornerPerm.clone();
        cube.cornerOrient = originalCube.cornerOrient.clone();
        cube.edgePerm = originalCube.edgePerm.clone();
        cube.edgeOrient = originalCube.edgeOrient.clone();
        
        // Apply phase 1 moves
        for (int i = 0; i < phase1Len; i++) {
            cube.applyMove(phase1Moves[i]);
        }
        return cube.getCornerPerm();
    }
}
