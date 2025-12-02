package rubikscube;

/**
 * Piece-level cube representation using permutation and orientation arrays.
 * 
 * Corners (0-7): URF, UFL, ULB, UBR, DFR, DLF, DBL, DRB
 * Edges (0-11): UR, UF, UL, UB, DR, DF, DL, DB, FR, FL, BL, BR
 * 
 * Each move is applied by composing with a template permutation.
 */
public class PieceCube {

    // Corner positions: URF=0, UFL=1, ULB=2, UBR=3, DFR=4, DLF=5, DBL=6, DRB=7
    public int[] cornerPerm = {0, 1, 2, 3, 4, 5, 6, 7};
    public byte[] cornerOrient = {0, 0, 0, 0, 0, 0, 0, 0};

    // Edge positions: UR=0, UF=1, UL=2, UB=3, DR=4, DF=5, DL=6, DB=7, FR=8, FL=9, BL=10, BR=11
    public int[] edgePerm = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public byte[] edgeOrient = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    // Move templates: [move_index] = PieceCube representing that single move
    // Move indices: U=0, U2=1, U'=2, R=3, R2=4, R'=5, F=6, F2=7, F'=8, D=9, D2=10, D'=11, L=12, L2=13, L'=14, B=15, B2=16, B'=17
    private static final PieceCube[] MOVE_CUBES = new PieceCube[6];

    // Corner permutation for each basic move (clockwise quarter turn)
    private static final int[][] CORNER_PERM_MOVES = {
        {3, 0, 1, 2, 4, 5, 6, 7},  // U
        {4, 1, 2, 0, 7, 5, 6, 3},  // R
        {1, 5, 2, 3, 0, 4, 6, 7},  // F
        {0, 1, 2, 3, 5, 6, 7, 4},  // D
        {0, 2, 6, 3, 4, 1, 5, 7},  // L
        {0, 1, 3, 7, 4, 5, 2, 6}   // B
    };

    // Corner orientation change for each basic move
    private static final byte[][] CORNER_ORIENT_MOVES = {
        {0, 0, 0, 0, 0, 0, 0, 0},  // U
        {2, 0, 0, 1, 1, 0, 0, 2},  // R
        {1, 2, 0, 0, 2, 1, 0, 0},  // F
        {0, 0, 0, 0, 0, 0, 0, 0},  // D
        {0, 1, 2, 0, 0, 2, 1, 0},  // L
        {0, 0, 1, 2, 0, 0, 2, 1}   // B
    };

    // Edge permutation for each basic move
    private static final int[][] EDGE_PERM_MOVES = {
        {3, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11},   // U
        {8, 1, 2, 3, 11, 5, 6, 7, 4, 9, 10, 0},   // R
        {0, 9, 2, 3, 4, 8, 6, 7, 1, 5, 10, 11},   // F
        {0, 1, 2, 3, 5, 6, 7, 4, 8, 9, 10, 11},   // D
        {0, 1, 10, 3, 4, 5, 9, 7, 8, 2, 6, 11},   // L
        {0, 1, 2, 11, 4, 5, 6, 10, 8, 9, 3, 7}    // B
    };

    // Edge orientation change for each basic move
    private static final byte[][] EDGE_ORIENT_MOVES = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // U
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // R
        {0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0},  // F
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // D
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},  // L
        {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1}   // B
    };

    static {
        // Initialize move templates
        for (int m = 0; m < 6; m++) {
            MOVE_CUBES[m] = new PieceCube();
            MOVE_CUBES[m].cornerPerm = CORNER_PERM_MOVES[m].clone();
            MOVE_CUBES[m].cornerOrient = CORNER_ORIENT_MOVES[m].clone();
            MOVE_CUBES[m].edgePerm = EDGE_PERM_MOVES[m].clone();
            MOVE_CUBES[m].edgeOrient = EDGE_ORIENT_MOVES[m].clone();
        }
    }

    public PieceCube() {}

    /**
     * Apply a move by composing with the move template.
     * @param move 0-17 representing U, U2, U', R, R2, R', F, F2, F', D, D2, D', L, L2, L', B, B2, B'
     */
    public void applyMove(int move) {
        int face = move / 3;      // Which face (0-5)
        int turns = move % 3 + 1; // How many quarter turns (1, 2, or 3)

        for (int t = 0; t < turns; t++) {
            multiplyCorners(MOVE_CUBES[face]);
            multiplyEdges(MOVE_CUBES[face]);
        }
    }

    /**
     * Multiply corner permutation and orientation with another cube.
     */
    private void multiplyCorners(PieceCube other) {
        int[] newPerm = new int[8];
        byte[] newOrient = new byte[8];

        for (int i = 0; i < 8; i++) {
            newPerm[i] = cornerPerm[other.cornerPerm[i]];
            newOrient[i] = (byte) ((cornerOrient[other.cornerPerm[i]] + other.cornerOrient[i]) % 3);
        }

        cornerPerm = newPerm;
        cornerOrient = newOrient;
    }

    /**
     * Multiply edge permutation and orientation with another cube.
     */
    private void multiplyEdges(PieceCube other) {
        int[] newPerm = new int[12];
        byte[] newOrient = new byte[12];

        for (int i = 0; i < 12; i++) {
            newPerm[i] = edgePerm[other.edgePerm[i]];
            newOrient[i] = (byte) ((edgeOrient[other.edgePerm[i]] + other.edgeOrient[i]) % 2);
        }

        edgePerm = newPerm;
        edgeOrient = newOrient;
    }

    // ==================== Coordinate Extraction ====================

    /**
     * Corner orientation coordinate: 0 to 2186 (3^7 - 1)
     */
    public short getTwist() {
        short twist = 0;
        for (int i = 0; i < 7; i++) {
            twist = (short) (3 * twist + cornerOrient[i]);
        }
        return twist;
    }

    public void setTwist(short twist) {
        int parity = 0;
        for (int i = 6; i >= 0; i--) {
            cornerOrient[i] = (byte) (twist % 3);
            parity += cornerOrient[i];
            twist /= 3;
        }
        cornerOrient[7] = (byte) ((3 - parity % 3) % 3);
    }

    /**
     * Edge orientation coordinate: 0 to 2047 (2^11 - 1)
     */
    public short getFlip() {
        short flip = 0;
        for (int i = 0; i < 11; i++) {
            flip = (short) (2 * flip + edgeOrient[i]);
        }
        return flip;
    }

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
     * E-slice edge positions: 0 to 11879
     * Encodes which positions contain FR, FL, BL, BR edges and their order.
     */
    public short getSlice() {
        int a = 0, x = 0;
        int[] slice = new int[4];

        // Find positions of slice edges (8, 9, 10, 11) from right to left
        for (int j = 11; j >= 0; j--) {
            if (edgePerm[j] >= 8) {
                a += nCk(11 - j, x + 1);
                slice[3 - x++] = edgePerm[j];
            }
        }

        // Encode permutation of slice edges
        int b = 0;
        for (int j = 3; j > 0; j--) {
            int k = 0;
            while (slice[j] != j + 8) {
                rotateLeft(slice, 0, j);
                k++;
            }
            b = (j + 1) * b + k;
        }

        return (short) (24 * a + b);
    }

    public void setSlice(short idx) {
        int[] slice = {8, 9, 10, 11};
        int[] other = {0, 1, 2, 3, 4, 5, 6, 7};
        int b = idx % 24;
        int a = idx / 24;

        // Clear edges
        for (int i = 0; i < 12; i++) edgePerm[i] = -1;

        // Decode permutation
        for (int j = 1; j < 4; j++) {
            int k = b % (j + 1);
            b /= (j + 1);
            while (k-- > 0) rotateRight(slice, 0, j);
        }

        // Place slice edges
        int x = 3;
        for (int j = 0; j <= 11; j++) {
            if (a - nCk(11 - j, x + 1) >= 0) {
                edgePerm[j] = slice[3 - x];
                a -= nCk(11 - j, x-- + 1);
            }
        }

        // Fill remaining with non-slice edges
        x = 0;
        for (int j = 0; j < 12; j++) {
            if (edgePerm[j] == -1) edgePerm[j] = other[x++];
        }
    }

    /**
     * Corner permutation coordinate for first 6 corners: 0 to 20159
     */
    public short getCornerPerm() {
        int a = 0, x = 0;
        int[] corners = new int[6];

        for (int j = 0; j <= 7; j++) {
            if (cornerPerm[j] <= 5) {
                a += nCk(j, x + 1);
                corners[x++] = cornerPerm[j];
            }
        }

        int b = 0;
        for (int j = 5; j > 0; j--) {
            int k = 0;
            while (corners[j] != j) {
                rotateLeft(corners, 0, j);
                k++;
            }
            b = (j + 1) * b + k;
        }

        return (short) (720 * a + b);
    }

    public void setCornerPerm(short idx) {
        int[] corners = {0, 1, 2, 3, 4, 5};
        int[] other = {6, 7};
        int b = idx % 720;
        int a = idx / 720;

        for (int i = 0; i < 8; i++) cornerPerm[i] = -1;

        for (int j = 1; j < 6; j++) {
            int k = b % (j + 1);
            b /= (j + 1);
            while (k-- > 0) rotateRight(corners, 0, j);
        }

        int x = 5;
        for (int j = 7; j >= 0; j--) {
            if (a - nCk(j, x + 1) >= 0) {
                cornerPerm[j] = corners[x];
                a -= nCk(j, x-- + 1);
            }
        }

        x = 0;
        for (int j = 0; j <= 7; j++) {
            if (cornerPerm[j] == -1) cornerPerm[j] = other[x++];
        }
    }

    /**
     * UD edge permutation coordinate: 0 to 20159
     */
    public int getUDEdgePerm() {
        int a = 0, x = 0;
        int[] edges = new int[6];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] <= 5) {
                a += nCk(j, x + 1);
                edges[x++] = edgePerm[j];
            }
        }

        int b = 0;
        for (int j = 5; j > 0; j--) {
            int k = 0;
            while (edges[j] != j) {
                rotateLeft(edges, 0, j);
                k++;
            }
            b = (j + 1) * b + k;
        }

        return 720 * a + b;
    }

    public void setUDEdgePerm(int idx) {
        int[] edges = {0, 1, 2, 3, 4, 5};
        int[] other = {6, 7, 8, 9, 10, 11};
        int b = idx % 720;
        int a = idx / 720;

        for (int i = 0; i < 12; i++) edgePerm[i] = -1;

        for (int j = 1; j < 6; j++) {
            int k = b % (j + 1);
            b /= (j + 1);
            while (k-- > 0) rotateRight(edges, 0, j);
        }

        int x = 5;
        for (int j = 11; j >= 0; j--) {
            if (a - nCk(j, x + 1) >= 0) {
                edgePerm[j] = edges[x];
                a -= nCk(j, x-- + 1);
            }
        }

        x = 0;
        for (int j = 0; j < 12; j++) {
            if (edgePerm[j] == -1) edgePerm[j] = other[x++];
        }
    }

    // Helper edge coordinates for efficient phase 2 setup
    public short getURtoUL() {
        int a = 0, x = 0;
        int[] edges = new int[3];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] <= 2) {
                a += nCk(j, x + 1);
                edges[x++] = edgePerm[j];
            }
        }

        int b = 0;
        for (int j = 2; j > 0; j--) {
            int k = 0;
            while (edges[j] != j) {
                rotateLeft(edges, 0, j);
                k++;
            }
            b = (j + 1) * b + k;
        }

        return (short) (6 * a + b);
    }

    public void setURtoUL(short idx) {
        int[] edges = {0, 1, 2};
        int b = idx % 6;
        int a = idx / 6;

        for (int i = 0; i < 12; i++) edgePerm[i] = 11; // Invalid marker

        for (int j = 1; j < 3; j++) {
            int k = b % (j + 1);
            b /= (j + 1);
            while (k-- > 0) rotateRight(edges, 0, j);
        }

        int x = 2;
        for (int j = 11; j >= 0; j--) {
            if (a - nCk(j, x + 1) >= 0) {
                edgePerm[j] = edges[x];
                a -= nCk(j, x-- + 1);
            }
        }
    }

    public short getUBtoDF() {
        int a = 0, x = 0;
        int[] edges = new int[3];

        for (int j = 0; j <= 11; j++) {
            if (edgePerm[j] >= 3 && edgePerm[j] <= 5) {
                a += nCk(j, x + 1);
                edges[x++] = edgePerm[j];
            }
        }

        int b = 0;
        for (int j = 2; j > 0; j--) {
            int k = 0;
            while (edges[j] != 3 + j) {
                rotateLeft(edges, 0, j);
                k++;
            }
            b = (j + 1) * b + k;
        }

        return (short) (6 * a + b);
    }

    public void setUBtoDF(short idx) {
        int[] edges = {3, 4, 5};
        int b = idx % 6;
        int a = idx / 6;

        for (int i = 0; i < 12; i++) edgePerm[i] = 11;

        for (int j = 1; j < 3; j++) {
            int k = b % (j + 1);
            b /= (j + 1);
            while (k-- > 0) rotateRight(edges, 0, j);
        }

        int x = 2;
        for (int j = 11; j >= 0; j--) {
            if (a - nCk(j, x + 1) >= 0) {
                edgePerm[j] = edges[x];
                a -= nCk(j, x-- + 1);
            }
        }
    }

    /**
     * Merge URtoUL and UBtoDF into full UDEdgePerm.
     */
    public static int mergeURtoULandUBtoDF(short urToUl, short ubToDf) {
        PieceCube a = new PieceCube();
        PieceCube b = new PieceCube();
        a.setURtoUL(urToUl);
        b.setUBtoDF(ubToDf);

        for (int i = 0; i < 8; i++) {
            if (a.edgePerm[i] != 11) {
                if (b.edgePerm[i] != 11) return -1; // Collision
                b.edgePerm[i] = a.edgePerm[i];
            }
        }

        return b.getUDEdgePerm();
    }

    /**
     * Corner parity: 0 (even) or 1 (odd)
     */
    public short cornerParity() {
        int s = 0;
        for (int i = 7; i >= 1; i--) {
            for (int j = i - 1; j >= 0; j--) {
                if (cornerPerm[j] > cornerPerm[i]) s++;
            }
        }
        return (short) (s % 2);
    }

    /**
     * Edge parity: 0 (even) or 1 (odd)
     */
    public short edgeParity() {
        int s = 0;
        for (int i = 11; i >= 1; i--) {
            for (int j = i - 1; j >= 0; j--) {
                if (edgePerm[j] > edgePerm[i]) s++;
            }
        }
        return (short) (s % 2);
    }

    /**
     * Check if cube is solved.
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
     * Verify cube validity. Returns 0 if OK, negative error code otherwise.
     */
    public int verify() {
        // Check edge permutation
        int[] edgeCount = new int[12];
        for (int i = 0; i < 12; i++) edgeCount[edgePerm[i]]++;
        for (int i = 0; i < 12; i++) {
            if (edgeCount[i] != 1) return -2; // Missing/duplicate edge
        }

        // Check edge orientation sum (must be even)
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += edgeOrient[i];
        if (sum % 2 != 0) return -3; // Flip error

        // Check corner permutation
        int[] cornerCount = new int[8];
        for (int i = 0; i < 8; i++) cornerCount[cornerPerm[i]]++;
        for (int i = 0; i < 8; i++) {
            if (cornerCount[i] != 1) return -4; // Missing/duplicate corner
        }

        // Check corner orientation sum (must be divisible by 3)
        sum = 0;
        for (int i = 0; i < 8; i++) sum += cornerOrient[i];
        if (sum % 3 != 0) return -5; // Twist error

        // Check parity (corner and edge parity must match)
        if (edgeParity() != cornerParity()) return -6; // Parity error

        return 0;
    }

    public static int nCk(int n, int k) {
        if (n < k || k < 0) return 0;
        if (k > n / 2) k = n - k;
        int result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    private static void rotateLeft(int[] arr, int l, int r) {
        int temp = arr[l];
        for (int i = l; i < r; i++) arr[i] = arr[i + 1];
        arr[r] = temp;
    }

    private static void rotateRight(int[] arr, int l, int r) {
        int temp = arr[r];
        for (int i = r; i > l; i--) arr[i] = arr[i - 1];
        arr[l] = temp;
    }
}
