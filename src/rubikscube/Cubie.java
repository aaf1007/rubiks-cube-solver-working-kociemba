package rubikscube;

/**
 * Cubie-level representation of a Rubik's Cube.
 *
 * The cube is represented by 4 arrays:
 * - cornerPerm[8]: which corner piece is at each position (0-7)
 * - cornerOrient[8]: how each corner is twisted (0=correct, 1=CW, 2=CCW)
 * - edgePerm[12]: which edge piece is at each position (0-11)
 * - edgeOrient[12]: whether each edge is flipped (0=correct, 1=flipped)
 *
 * Corner positions: URF=0, UFL=1, ULB=2, UBR=3 (top), DFR=4, DLF=5, DBL=6, DRB=7 (bottom)
 * Edge positions: UR=0, UF=1, UL=2, UB=3 (top), DR=4, DF=5, DL=6, DB=7 (bottom),
 *                 FR=8, FL=9, BL=10, BR=11 (E-slice/middle layer)
 *
 * Moves are applied by permutation composition with precomputed move templates.
 */
public class Cubie {

    // Which corner piece occupies each position (solved = identity permutation)
    public int[] cornerPerm = {0, 1, 2, 3, 4, 5, 6, 7};
    // Orientation of each corner: 0=no twist, 1=clockwise twist, 2=counter-clockwise
    public byte[] cornerOrient = {0, 0, 0, 0, 0, 0, 0, 0};

    // Which edge piece occupies each position (solved = identity)
    public int[] edgePerm = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    // Orientation of each edge: 0=correct, 1=flipped
    public byte[] edgeOrient = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    // Move templates: one Cubie for each face's quarter turn
    // To apply U2, we apply MOVE_CUBES[0] twice; for U', apply it 3 times
    private static final Cubie[] MOVE_CUBES = new Cubie[6];

    // Corner permutation templates for each face's clockwise quarter turn
    // Read as: after U move, position 0 gets the piece from position 3, etc.
    private static final int[][] CORNER_PERM_MOVES = {
        {3, 0, 1, 2, 4, 5, 6, 7},  // U: cycles top 4 corners
        {4, 1, 2, 0, 7, 5, 6, 3},  // R: cycles right 4 corners
        {1, 5, 2, 3, 0, 4, 6, 7},  // F: cycles front 4 corners
        {0, 1, 2, 3, 5, 6, 7, 4},  // D: cycles bottom 4 corners
        {0, 2, 6, 3, 4, 1, 5, 7},  // L: cycles left 4 corners
        {0, 1, 3, 7, 4, 5, 2, 6}   // B: cycles back 4 corners
    };

    // Corner orientation change for each move
    // U and D don't twist corners; R, F, L, B twist the corners they move
    private static final byte[][] CORNER_ORIENT_MOVES = {
        {0, 0, 0, 0, 0, 0, 0, 0},  // U: no twist
        {2, 0, 0, 1, 1, 0, 0, 2},  // R: twists 4 corners
        {1, 2, 0, 0, 2, 1, 0, 0},  // F: twists 4 corners
        {0, 0, 0, 0, 0, 0, 0, 0},  // D: no twist
        {0, 1, 2, 0, 0, 2, 1, 0},  // L: twists 4 corners
        {0, 0, 1, 2, 0, 0, 2, 1}   // B: twists 4 corners
    };

    // Edge permutation templates for each face's clockwise quarter turn
    private static final int[][] EDGE_PERM_MOVES = {
        {3, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11},   // U: cycles UR,UF,UL,UB
        {8, 1, 2, 3, 11, 5, 6, 7, 4, 9, 10, 0},   // R: cycles UR,FR,DR,BR
        {0, 9, 2, 3, 4, 8, 6, 7, 1, 5, 10, 11},   // F: cycles UF,FL,DF,FR
        {0, 1, 2, 3, 5, 6, 7, 4, 8, 9, 10, 11},   // D: cycles DR,DF,DL,DB
        {0, 1, 10, 3, 4, 5, 9, 7, 8, 2, 6, 11},   // L: cycles UL,BL,DL,FL
        {0, 1, 2, 11, 4, 5, 6, 10, 8, 9, 3, 7}    // B: cycles UB,BR,DB,BL
    };

    // Edge orientation change for each move
    // F and B flip edges (change orientation); U, R, D, L don't
    private static final byte[][] EDGE_ORIENT_MOVES = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // U: no flip
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // R: no flip
        {0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0},  // F: flips 4 edges
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // D: no flip
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // L: no flip
        {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1}   // B: flips 4 edges
    };

    // Build move templates from the static arrays
    static {
        for (int m = 0; m < 6; m++) {
            MOVE_CUBES[m] = new Cubie();
            MOVE_CUBES[m].cornerPerm = CORNER_PERM_MOVES[m].clone();
            MOVE_CUBES[m].cornerOrient = CORNER_ORIENT_MOVES[m].clone();
            MOVE_CUBES[m].edgePerm = EDGE_PERM_MOVES[m].clone();
            MOVE_CUBES[m].edgeOrient = EDGE_ORIENT_MOVES[m].clone();
        }
    }

    /** Default constructor: creates a solved cube (identity permutation, zero orientation) */
    public Cubie() {}

    /**
     * Apply a move to the cube by composing with the move template.
     *
     * @param move Move index 0-17:
     *             U=0, U2=1, U'=2, R=3, R2=4, R'=5, F=6, F2=7, F'=8,
     *             D=9, D2=10, D'=11, L=12, L2=13, L'=14, B=15, B2=16, B'=17
     */
    public void applyMove(int move) {
        int face = move / 3;      // Which face (0=U, 1=R, 2=F, 3=D, 4=L, 5=B)
        int turns = move % 3 + 1; // Quarter turns: 1=90°, 2=180°, 3=270°

        // Apply the quarter-turn template 'turns' times
        for (int t = 0; t < turns; t++) {
            multiplyCorners(MOVE_CUBES[face]);
            multiplyEdges(MOVE_CUBES[face]);
        }
    }

    /**
     * Compose this cube's corner state with another cube (permutation multiplication).
     * The result represents applying 'other' after 'this'.
     *
     * New permutation: follow both mappings
     * New orientation: sum of orientations (mod 3 for corners)
     */
    private void multiplyCorners(Cubie other) {
        int[] newPerm = new int[8];
        byte[] newOrient = new byte[8];

        for (int i = 0; i < 8; i++) {
            // Position i gets the piece that 'other' puts there, traced through 'this'
            newPerm[i] = cornerPerm[other.cornerPerm[i]];
            // Orientation combines: original twist + twist from the move
            newOrient[i] = (byte) ((cornerOrient[other.cornerPerm[i]] + other.cornerOrient[i]) % 3);
        }

        cornerPerm = newPerm;
        cornerOrient = newOrient;
    }

    /**
     * Compose this cube's edge state with another cube (permutation multiplication).
     * Same logic as corners, but orientation is mod 2 (flip/no flip).
     */
    private void multiplyEdges(Cubie other) {
        int[] newPerm = new int[12];
        byte[] newOrient = new byte[12];

        for (int i = 0; i < 12; i++) {
            newPerm[i] = edgePerm[other.edgePerm[i]];
            newOrient[i] = (byte) ((edgeOrient[other.edgePerm[i]] + other.edgeOrient[i]) % 2);
        }

        edgePerm = newPerm;
        edgeOrient = newOrient;
    }

    // Coordinate extraction and setting methods
    // These convert between the 4-array representation and integer coordinates
    // used by the move/pruning tables.

    /**
     * Get corner orientation (twist) coordinate: 0 to 2186.
     * Encodes orientations of first 7 corners in base 3.
     * The 8th corner's orientation is determined by the constraint: sum = 0 mod 3.
     */
    public short getTwist() {
        short twist = 0;
        for (int i = 0; i < 7; i++) {
            twist = (short) (3 * twist + cornerOrient[i]);
        }
        return twist;
    }

    /**
     * Set corner orientations from twist coordinate.
     * Decodes base-3 number and computes 8th orientation from parity constraint.
     */
    public void setTwist(short twist) {
        int parity = 0;
        for (int i = 6; i >= 0; i--) {
            cornerOrient[i] = (byte) (twist % 3);
            parity += cornerOrient[i];
            twist /= 3;
        }
        // 8th corner orientation makes total sum divisible by 3
        cornerOrient[7] = (byte) ((3 - parity % 3) % 3);
    }

    /**
     * Get edge orientation (flip) coordinate: 0 to 2047.
     * Encodes orientations of first 11 edges in base 2.
     * The 12th edge's orientation is determined by the constraint: sum = 0 mod 2.
     */
    public short getFlip() {
        short flip = 0;
        for (int i = 0; i < 11; i++) {
            flip = (short) (2 * flip + edgeOrient[i]);
        }
        return flip;
    }

    /**
     * Set edge orientations from flip coordinate.
     * Decodes binary number and computes 12th orientation from parity constraint.
     */
    public void setFlip(short flip) {
        int parity = 0;
        for (int i = 10; i >= 0; i--) {
            edgeOrient[i] = (byte) (flip % 2);
            parity += edgeOrient[i];
            flip /= 2;
        }
        edgeOrient[11] = (byte) ((2 - parity % 2) % 2);
    }

    /**
     * Get E-slice coordinate: 0 to 11879.
     * Encodes both which 4 positions hold E-slice edges (FR, FL, BL, BR = pieces 8-11)
     * AND their permutation within those positions.
     *
     * The coordinate = 24 * (combinatorial position) + (permutation within 0-23)
     * - Position part: C(12,4) = 495 ways to choose 4 positions from 12
     * - Permutation part: 4! = 24 ways to arrange 4 edges
     */
    public short getSlice() {
        int posCoord = 0, count = 0;
        int[] slice = new int[4];

        // Find positions of slice edges (pieces 8-11) and encode combinatorially
        for (int j = 11; j >= 0; j--) {
            if (edgePerm[j] >= 8) {
                posCoord += nCk(11 - j, count + 1);  // Combinatorial encoding of positions
                slice[3 - count++] = edgePerm[j];
            }
        }

        // Encode permutation of the 4 slice edges using factorial number system
        int permCoord = 0;
        for (int j = 3; j > 0; j--) {
            int rotations = 0;
            while (slice[j] != j + 8) {
                rotateLeft(slice, 0, j);
                rotations++;
            }
            permCoord = (j + 1) * permCoord + rotations;
        }

        return (short) (24 * posCoord + permCoord);  // Combine position and permutation
    }

    /**
     * Set edge permutation from slice coordinate.
     * Decodes the coordinate into positions and permutation, then places edges.
     */
    public void setSlice(short idx) {
        int[] slice = {8, 9, 10, 11};     // Slice edge pieces
        int[] other = {0, 1, 2, 3, 4, 5, 6, 7};  // Non-slice edges
        int permCoord = idx % 24;   // Permutation part
        int posCoord = idx / 24;    // Position part

        // Clear all edge positions
        for (int i = 0; i < 12; i++) edgePerm[i] = -1;

        // Decode permutation using factorial number system
        for (int j = 1; j < 4; j++) {
            int rotations = permCoord % (j + 1);
            permCoord /= (j + 1);
            while (rotations-- > 0) rotateRight(slice, 0, j);
        }

        // Place slice edges at positions decoded from combinatorial encoding
        int count = 3;
        for (int j = 0; j <= 11; j++) {
            if (posCoord - nCk(11 - j, count + 1) >= 0) {
                edgePerm[j] = slice[3 - count];
                posCoord -= nCk(11 - j, count-- + 1);
            }
        }

        // Fill remaining positions with non-slice edges
        count = 0;
        for (int j = 0; j < 12; j++) {
            if (edgePerm[j] == -1) edgePerm[j] = other[count++];
        }
    }

    /**
     * Get corner permutation coordinate for phase 2: 0 to 20159.
     * Encodes position and order of corners 0-5 (the other 2 are determined by parity).
     * Uses combinatorial encoding for positions + factorial encoding for permutation.
     */
    public short getCornerPerm() {
        int posCoord = 0, count = 0;
        int[] corners = new int[6];

        // Find corners 0-5 and encode their positions combinatorially
        for (int j = 0; j <= 7; j++) {
            if (cornerPerm[j] <= 5) {
                posCoord += nCk(j, count + 1);
                corners[count++] = cornerPerm[j];
            }
        }

        // Encode permutation using factorial number system
        int permCoord = 0;
        for (int j = 5; j > 0; j--) {
            int rotations = 0;
            while (corners[j] != j) {
                rotateLeft(corners, 0, j);
                rotations++;
            }
            permCoord = (j + 1) * permCoord + rotations;
        }

        return (short) (720 * posCoord + permCoord);  // 6! = 720
    }

    /** Set corner permutation from coordinate. Inverse of getCornerPerm(). */
    public void setCornerPerm(short idx) {
        int[] corners = {0, 1, 2, 3, 4, 5};
        int[] other = {6, 7};
        int permCoord = idx % 720;
        int posCoord = idx / 720;

        for (int i = 0; i < 8; i++) cornerPerm[i] = -1;

        for (int j = 1; j < 6; j++) {
            int rotations = permCoord % (j + 1);
            permCoord /= (j + 1);
            while (rotations-- > 0) rotateRight(corners, 0, j);
        }

        int count = 5;
        for (int j = 7; j >= 0; j--) {
            if (posCoord - nCk(j, count + 1) >= 0) {
                cornerPerm[j] = corners[count];
                posCoord -= nCk(j, count-- + 1);
            }
        }

        count = 0;
        for (int j = 0; j <= 7; j++) {
            if (cornerPerm[j] == -1) cornerPerm[j] = other[count++];
        }
    }

    /**
     * Get UD edge permutation coordinate for phase 2: 0 to 20159.
     * Encodes position and order of edges 0-5 (the UD layer edges).
     * Same encoding scheme as corner permutation.
     */
    public int getUDEdgePerm() {
        int posCoord = 0, count = 0;
        int[] edges = new int[6];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] <= 5) {
                posCoord += nCk(j, count + 1);
                edges[count++] = edgePerm[j];
            }
        }

        int permCoord = 0;
        for (int j = 5; j > 0; j--) {
            int rotations = 0;
            while (edges[j] != j) {
                rotateLeft(edges, 0, j);
                rotations++;
            }
            permCoord = (j + 1) * permCoord + rotations;
        }

        return 720 * posCoord + permCoord;
    }

    /** Set UD edge permutation from coordinate. Inverse of getUDEdgePerm(). */
    public void setUDEdgePerm(int idx) {
        int[] edges = {0, 1, 2, 3, 4, 5};
        int[] other = {6, 7, 8, 9, 10, 11};
        int permCoord = idx % 720;
        int posCoord = idx / 720;

        for (int i = 0; i < 12; i++) edgePerm[i] = -1;

        for (int j = 1; j < 6; j++) {
            int rotations = permCoord % (j + 1);
            permCoord /= (j + 1);
            while (rotations-- > 0) rotateRight(edges, 0, j);
        }

        int count = 5;
        for (int j = 11; j >= 0; j--) {
            if (posCoord - nCk(j, count + 1) >= 0) {
                edgePerm[j] = edges[count];
                posCoord -= nCk(j, count-- + 1);
            }
        }

        count = 0;
        for (int j = 0; j < 12; j++) {
            if (edgePerm[j] == -1) edgePerm[j] = other[count++];
        }
    }

    // Helper edge coordinates for efficient phase 2 computation
    // Instead of computing full UD edge perm during search, we track two smaller
    // coordinates and merge them only when needed (using precomputed merge table).

    /** Get coordinate for edges UR, UF, UL (pieces 0, 1, 2). Range: 0-1319. */
    public short getURtoUL() {
        int posCoord = 0, count = 0;
        int[] edges = new int[3];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] <= 2) {
                posCoord += nCk(j, count + 1);
                edges[count++] = edgePerm[j];
            }
        }

        int permCoord = 0;
        for (int j = 2; j > 0; j--) {
            int rotations = 0;
            while (edges[j] != j) {
                rotateLeft(edges, 0, j);
                rotations++;
            }
            permCoord = (j + 1) * permCoord + rotations;
        }

        return (short) (6 * posCoord + permCoord);  // 3! = 6
    }

    /** Set edge permutation from URtoUL coordinate. Only sets edges 0-2. */
    public void setURtoUL(short idx) {
        int[] edges = {0, 1, 2};
        int permCoord = idx % 6;
        int posCoord = idx / 6;

        for (int i = 0; i < 12; i++) edgePerm[i] = 11;  // Mark as unset

        for (int j = 1; j < 3; j++) {
            int rotations = permCoord % (j + 1);
            permCoord /= (j + 1);
            while (rotations-- > 0) rotateRight(edges, 0, j);
        }

        int count = 2;
        for (int j = 11; j >= 0; j--) {
            if (posCoord - nCk(j, count + 1) >= 0) {
                edgePerm[j] = edges[count];
                posCoord -= nCk(j, count-- + 1);
            }
        }
    }

    /** Get coordinate for edges UB, DR, DF (pieces 3, 4, 5). Range: 0-1319. */
    public short getUBtoDF() {
        int posCoord = 0, count = 0;
        int[] edges = new int[3];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] >= 3 && edgePerm[j] <= 5) {
                posCoord += nCk(j, count + 1);
                edges[count++] = edgePerm[j];
            }
        }

        int permCoord = 0;
        for (int j = 2; j > 0; j--) {
            int rotations = 0;
            while (edges[j] != 3 + j) {
                rotateLeft(edges, 0, j);
                rotations++;
            }
            permCoord = (j + 1) * permCoord + rotations;
        }

        return (short) (6 * posCoord + permCoord);
    }

    /** Set edge permutation from UBtoDF coordinate. Only sets edges 3-5. */
    public void setUBtoDF(short idx) {
        int[] edges = {3, 4, 5};
        int permCoord = idx % 6;
        int posCoord = idx / 6;

        for (int i = 0; i < 12; i++) edgePerm[i] = 11;

        for (int j = 1; j < 3; j++) {
            int rotations = permCoord % (j + 1);
            permCoord /= (j + 1);
            while (rotations-- > 0) rotateRight(edges, 0, j);
        }

        int count = 2;
        for (int j = 11; j >= 0; j--) {
            if (posCoord - nCk(j, count + 1) >= 0) {
                edgePerm[j] = edges[count];
                posCoord -= nCk(j, count-- + 1);
            }
        }
    }

    /**
     * Merge URtoUL and UBtoDF helper coordinates into full UDEdgePerm.
     * Creates two partial cubes, combines their edge info, and extracts the result.
     * Returns -1 if the coordinates conflict (shouldn't happen with valid inputs).
     */
    public static int mergeURtoULandUBtoDF(short urToUl, short ubToDf) {
        Cubie cubeA = new Cubie();
        Cubie cubeB = new Cubie();
        cubeA.setURtoUL(urToUl);
        cubeB.setUBtoDF(ubToDf);

        // Merge: copy edges from cubeA into cubeB where cubeB is unset
        for (int i = 0; i < 8; i++) {
            if (cubeA.edgePerm[i] != 11) {
                if (cubeB.edgePerm[i] != 11) return -1;  // Both set = conflict
                cubeB.edgePerm[i] = cubeA.edgePerm[i];
            }
        }

        return cubeB.getUDEdgePerm();
    }

    /**
     * Compute corner permutation parity: 0 (even) or 1 (odd).
     * Counts inversions in the permutation. A valid cube must have
     * corner parity == edge parity.
     */
    public short cornerParity() {
        int inversions = 0;
        // Count inversions: pairs (i,j) where i < j but perm[i] > perm[j]
        for (int i = 7; i >= 1; i--) {
            for (int j = i - 1; j >= 0; j--) {
                if (cornerPerm[j] > cornerPerm[i]) inversions++;
            }
        }
        return (short) (inversions % 2);
    }

    /**
     * Compute edge permutation parity: 0 (even) or 1 (odd).
     * Same algorithm as corner parity.
     */
    public short edgeParity() {
        int inversions = 0;
        for (int i = 11; i >= 1; i--) {
            for (int j = i - 1; j >= 0; j--) {
                if (edgePerm[j] > edgePerm[i]) inversions++;
            }
        }
        return (short) (inversions % 2);
    }

    /**
     * Check if the cube is in the solved state.
     * Solved means identity permutation and zero orientation for all pieces.
     */
    public boolean isSolved() {
        for (int i = 0; i < 8; i++) {
            if (cornerPerm[i] != i || cornerOrient[i] != 0) return false;
        }
        for (int i = 0; i < 12; i++) {
            if (edgePerm[i] != i || edgeOrient[i] != 0) return false;
        }
        return true;
    }

    /**
     * Verify that the cube state is valid (physically possible to reach from solved).
     *
     * @return 0 if valid, negative error code otherwise:
     *         -2: Missing or duplicate edge
     *         -3: Edge orientation sum not divisible by 2
     *         -4: Missing or duplicate corner
     *         -5: Corner orientation sum not divisible by 3
     *         -6: Corner and edge parity don't match
     */
    public int verify() {
        // Each edge piece must appear exactly once
        int[] edgeCount = new int[12];
        for (int i = 0; i < 12; i++) edgeCount[edgePerm[i]]++;
        for (int i = 0; i < 12; i++) {
            if (edgeCount[i] != 1) return -2;
        }

        // Edge orientation sum must be even (physical constraint)
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += edgeOrient[i];
        if (sum % 2 != 0) return -3;

        // Each corner piece must appear exactly once
        int[] cornerCount = new int[8];
        for (int i = 0; i < 8; i++) cornerCount[cornerPerm[i]]++;
        for (int i = 0; i < 8; i++) {
            if (cornerCount[i] != 1) return -4;
        }

        // Corner orientation sum must be divisible by 3 (physical constraint)
        sum = 0;
        for (int i = 0; i < 8; i++) sum += cornerOrient[i];
        if (sum % 3 != 0) return -5;

        // Corner and edge parity must match (physical constraint)
        if (edgeParity() != cornerParity()) return -6;

        return 0;
    }

    /**
     * Compute binomial coefficient C(n,k) = n! / (k! * (n-k)!).
     * Used for combinatorial coordinate encoding.
     */
    public static int nCk(int n, int k) {
        if (n < k || k < 0) return 0;
        if (k > n / 2) k = n - k;  // Optimization: C(n,k) = C(n,n-k)
        int result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /** Rotate array elements left within range [left,right]: arr[left] moves to arr[right] */
    private static void rotateLeft(int[] arr, int left, int right) {
        int temp = arr[left];
        for (int i = left; i < right; i++) arr[i] = arr[i + 1];
        arr[right] = temp;
    }

    /** Rotate array elements right within range [left,right]: arr[right] moves to arr[left] */
    private static void rotateRight(int[] arr, int left, int right) {
        int temp = arr[right];
        for (int i = right; i > left; i--) arr[i] = arr[i - 1];
        arr[left] = temp;
    }
}
