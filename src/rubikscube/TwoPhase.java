package rubikscube;

/**
 * Two-phase algorithm implementation for solving Rubik's Cube.
 * Direct port of the working Search.java algorithm structure.
 */
public class TwoPhase {

    // Search trace storage - axis (0-5) and power (1-3)
    static int[] axis = new int[31];
    static int[] power = new int[31];

    // Phase-1 coordinates along search path
    static int[] flip = new int[31];
    static int[] twist = new int[31];
    static int[] slice = new int[31];

    // Phase-2 coordinates
    static int[] parity = new int[31];
    static int[] cornerPerm = new int[31];
    static int[] slicePerm = new int[31];
    static int[] urToUl = new int[31];
    static int[] ubToDf = new int[31];
    static int[] udEdgePerm = new int[31];

    // IDA* heuristic estimates
    static int[] minDistPhase1 = new int[31];
    static int[] minDistPhase2 = new int[31];

    /**
     * Generate solution string from axis/power arrays.
     * Outputs repeated letters: U'=UUU, U2=UU
     */
    static String solutionToString(int length) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String face = switch (axis[i]) {
                case 0 -> "U";
                case 1 -> "R";
                case 2 -> "F";
                case 3 -> "D";
                case 4 -> "L";
                case 5 -> "B";
                default -> "";
            };
            for (int j = 0; j < power[i]; j++) {
                s.append(face);
            }
        }
        return s.toString();
    }

    /**
     * Solve the cube using two-phase algorithm.
     */
    public static String solve(PieceCube pc, int maxDepth, long timeOut) {
        int s;

        // Verify cube validity
        if ((s = pc.verify()) != 0)
            return "Error " + Math.abs(s);

        // Initialize coordinates from piece cube
        flip[0] = pc.getFlip();
        twist[0] = pc.getTwist();
        parity[0] = pc.cornerParity();
        int fullSlice = pc.getSlice();
        slice[0] = fullSlice / 24;
        slicePerm[0] = fullSlice;
        cornerPerm[0] = pc.getCornerPerm();
        urToUl[0] = pc.getURtoUL();
        ubToDf[0] = pc.getUBtoDF();

        // Initialize search
        power[0] = 0;
        axis[0] = 0;
        minDistPhase1[1] = 1;
        int mv, n = 0;
        boolean busy = false;
        int depthPhase1 = 1;

        long tStart = System.currentTimeMillis();

        // Main IDA* loop for phase-1
        do {
            do {
                if ((depthPhase1 - n > minDistPhase1[n + 1]) && !busy) {
                    // Initialize next move
                    if (axis[n] == 0 || axis[n] == 3) {
                        axis[++n] = 1;
                    } else {
                        axis[++n] = 0;
                    }
                    power[n] = 1;
                } else if (++power[n] > 3) {
                    // Power overflow, increment axis
                    do {
                        if (++axis[n] > 5) {
                            // Timeout check
                            if (System.currentTimeMillis() - tStart > timeOut << 10)
                                return "Error 8";

                            if (n == 0) {
                                if (depthPhase1 >= maxDepth)
                                    return "Error 7";
                                else {
                                    depthPhase1++;
                                    axis[n] = 0;
                                    power[n] = 1;
                                    busy = false;
                                    break;
                                }
                            } else {
                                n--;
                                busy = true;
                                break;
                            }
                        } else {
                            power[n] = 1;
                            busy = false;
                        }
                    } while (n != 0 && (axis[n - 1] == axis[n] || axis[n - 1] - 3 == axis[n]));
                } else {
                    busy = false;
                }
            } while (busy);

            // Compute new coordinates after move
            mv = 3 * axis[n] + power[n] - 1;
            flip[n + 1] = Tables.flipMove[flip[n]][mv];
            twist[n + 1] = Tables.twistMove[twist[n]][mv];
            slice[n + 1] = Tables.sliceMove[slice[n] * 24][mv] / 24;

            // Compute heuristic
            minDistPhase1[n + 1] = Math.max(
                Tables.getPruning(Tables.sliceFlipPrune, Tables.N_SLICE1 * flip[n + 1] + slice[n + 1]),
                Tables.getPruning(Tables.sliceTwistPrune, Tables.N_SLICE1 * twist[n + 1] + slice[n + 1]));

            // If reached H subgroup, try phase-2
            if (minDistPhase1[n + 1] == 0 && n >= depthPhase1 - 5) {
                minDistPhase1[n + 1] = 10;
                if (n == depthPhase1 - 1 && (s = totalDepth(depthPhase1, maxDepth)) >= 0) {
                    if (s == depthPhase1 || (axis[depthPhase1 - 1] != axis[depthPhase1] && axis[depthPhase1 - 1] != axis[depthPhase1] + 3))
                        return solutionToString(s);
                }
            }
        } while (true);
    }

    /**
     * Phase-2 search. Returns total depth or -1 if no solution found.
     */
    static int totalDepth(int depthPhase1, int maxDepth) {
        int mv, d1, d2;
        int maxDepthPhase2 = Math.min(10, maxDepth - depthPhase1);

        // Apply phase-1 moves to get phase-2 starting coordinates
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * axis[i] + power[i] - 1;
            cornerPerm[i + 1] = Tables.cornerPermMove[cornerPerm[i]][mv];
            slicePerm[i + 1] = Tables.sliceMove[slicePerm[i]][mv];
            parity[i + 1] = Tables.parityMove[parity[i]][mv];
        }

        // Check corner pruning
        d1 = Tables.getPruning(Tables.sliceCornerPrune,
            (Tables.N_SLICE2 * cornerPerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d1 > maxDepthPhase2)
            return -1;

        // Compute edge coordinate
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * axis[i] + power[i] - 1;
            urToUl[i + 1] = Tables.urToUlMove[urToUl[i]][mv];
            ubToDf[i + 1] = Tables.ubToDfMove[ubToDf[i]][mv];
        }
        udEdgePerm[depthPhase1] = Tables.mergeURtoULandUBtoDF[urToUl[depthPhase1]][ubToDf[depthPhase1]];

        // Check edge pruning
        d2 = Tables.getPruning(Tables.sliceEdgePrune,
            (Tables.N_SLICE2 * udEdgePerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d2 > maxDepthPhase2)
            return -1;

        // Already solved?
        if ((minDistPhase2[depthPhase1] = Math.max(d1, d2)) == 0)
            return depthPhase1;

        // Phase-2 IDA* search
        int depthPhase2 = 1;
        int n = depthPhase1;
        boolean busy = false;
        power[depthPhase1] = 0;
        axis[depthPhase1] = 0;
        minDistPhase2[n + 1] = 1;

        do {
            do {
                if ((depthPhase1 + depthPhase2 - n > minDistPhase2[n + 1]) && !busy) {
                    if (axis[n] == 0 || axis[n] == 3) {
                        axis[++n] = 1;
                        power[n] = 2;
                    } else {
                        axis[++n] = 0;
                        power[n] = 1;
                    }
                } else if ((axis[n] == 0 || axis[n] == 3) ? (++power[n] > 3) : ((power[n] = power[n] + 2) > 3)) {
                    do {
                        if (++axis[n] > 5) {
                            if (n == depthPhase1) {
                                if (depthPhase2 >= maxDepthPhase2)
                                    return -1;
                                else {
                                    depthPhase2++;
                                    axis[n] = 0;
                                    power[n] = 1;
                                    busy = false;
                                    break;
                                }
                            } else {
                                n--;
                                busy = true;
                                break;
                            }
                        } else {
                            if (axis[n] == 0 || axis[n] == 3)
                                power[n] = 1;
                            else
                                power[n] = 2;
                            busy = false;
                        }
                    } while (n != depthPhase1 && (axis[n - 1] == axis[n] || axis[n - 1] - 3 == axis[n]));
                } else {
                    busy = false;
                }
            } while (busy);

            // Compute new coordinates
            mv = 3 * axis[n] + power[n] - 1;
            cornerPerm[n + 1] = Tables.cornerPermMove[cornerPerm[n]][mv];
            slicePerm[n + 1] = Tables.sliceMove[slicePerm[n]][mv];
            parity[n + 1] = Tables.parityMove[parity[n]][mv];
            udEdgePerm[n + 1] = Tables.udEdgePermMove[udEdgePerm[n]][mv];

            minDistPhase2[n + 1] = Math.max(
                Tables.getPruning(Tables.sliceEdgePrune,
                    (Tables.N_SLICE2 * udEdgePerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]),
                Tables.getPruning(Tables.sliceCornerPrune,
                    (Tables.N_SLICE2 * cornerPerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]));

        } while (minDistPhase2[n + 1] != 0);

        return depthPhase1 + depthPhase2;
    }
}
