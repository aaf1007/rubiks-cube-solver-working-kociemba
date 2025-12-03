package rubikscube;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Facelet representation of a Rubik's Cube.
 *
 * This class handles the input format: a text file with 54 colored stickers.
 * It parses the file and converts the sticker colors into a piece-level
 * representation (Cubie) that solver can use.
 *
 * The cube is unfolded in the file as:
 *       OOO          (Up face)
 *       OOO
 *       OOO
 *    GGGWWWBBBYYY    (Left, Front, Right, Back faces)
 *    GGGWWWBBBYYY
 *    GGGWWWBBBYYY
 *       RRR          (Down face)
 *       RRR
 *       RRR
 *
 * Face indices (0-5): U=0, R=1, F=2, D=3, L=4, B=5
 * Sticker indices per face (0-8): reading order top-left to bottom-right
 */
public class RubiksCube {

    // Face/color constants - these map to positions in the stickers array
    // Colors in your scheme: O=Orange(Up), B=Blue(Right), W=White(Front),
    //                        R=Red(Down), G=Green(Left), Y=Yellow(Back)
    public static final int U = 0;
    public static final int R = 1;
    public static final int F = 2;
    public static final int D = 3;
    public static final int L = 4;
    public static final int B = 5;

    // All 54 stickers stored as face colors (0-5)
    // Index = face*9 + position, where position is 0-8 reading left-to-right, top-to-bottom
    public int[] stickers = new int[54];

    // Corner facelet positions: which 3 sticker indices make up each corner
    // Format: [corner_index][0,1,2] where:
    //   [0] = the U/D facelet (used to determine orientation)
    //   [1] = clockwise facelet when looking at U/D face
    //   [2] = counter-clockwise facelet
    private static final int[][] CORNER_FACELETS = {
        {8, 9, 20},   // URF: U's sticker 8, R's sticker 0, F's sticker 2
        {6, 18, 38},  // UFL
        {0, 36, 47},  // ULB
        {2, 45, 11},  // UBR
        {29, 26, 15}, // DFR
        {27, 44, 24}, // DLF
        {33, 53, 42}, // DBL
        {35, 17, 51}  // DRB
    };

    // Edge facelet positions: which 2 sticker indices make up each edge
    // Format: [edge_index][0,1] - order determines "correct" vs "flipped" orientation
    private static final int[][] EDGE_FACELETS = {
        {5, 10},  // UR
        {7, 19},  // UF
        {3, 37},  // UL
        {1, 46},  // UB
        {32, 16}, // DR
        {28, 25}, // DF
        {30, 43}, // DL
        {34, 52}, // DB
        {23, 12}, // FR
        {21, 41}, // FL
        {50, 39}, // BL
        {48, 14}  // BR
    };

    // Expected colors for each corner piece in solved state
    // Used to identify which piece is at each position
    private static final int[][] CORNER_COLORS = {
        {U, R, F}, {U, F, L}, {U, L, B}, {U, B, R},  // Top layer corners
        {D, F, R}, {D, L, F}, {D, B, L}, {D, R, B}   // Bottom layer corners
    };

    // Expected colors for each edge piece in solved state
    private static final int[][] EDGE_COLORS = {
        {U, R}, {U, F}, {U, L}, {U, B},  // Top layer edges
        {D, R}, {D, F}, {D, L}, {D, B},  // Bottom layer edges
        {F, R}, {F, L}, {B, L}, {B, R}   // Middle layer (E-slice) edges
    };

    /**
     * Default constructor: creates a solved cube where each face has its own color.
     */
    public RubiksCube() {
        for (int face = 0; face < 6; face++) {
            for (int i = 0; i < 9; i++) {
                stickers[face * 9 + i] = face;
            }
        }
    }

    /**
     * Parse cube state from input file.
     *
     * File format is a 9-line text file representing the unfolded cube:
     * - Lines 0-2: Up face (3 chars each, starting at column 3)
     * - Lines 3-5: Left, Front, Right, Back faces (3 chars each, side by side)
     * - Lines 6-8: Down face (3 chars each, starting at column 3)
     *
     * @param filename Path to the input file
     * @throws IOException if file can't be read or has wrong format
     */
    public RubiksCube(String filename) throws IOException {
        String[] lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            lines = new String[9];
            // Read all 9 lines
            for (int i = 0; i < 9; i++) {
                lines[i] = reader.readLine();
                if (lines[i] == null) {
                    throw new IOException("File has fewer than 9 lines");
                }
            }
        }

        // Parse each face from its position in the unfolded layout
        parseFace(lines, 0, 3, U);  // Up face: lines 0-2, columns 3-5
        parseFace(lines, 3, 0, L);  // Left face: lines 3-5, columns 0-2
        parseFace(lines, 3, 3, F);  // Front face: lines 3-5, columns 3-5
        parseFace(lines, 3, 6, R);  // Right face: lines 3-5, columns 6-8
        parseFace(lines, 3, 9, B);  // Back face: lines 3-5, columns 9-11
        parseFace(lines, 6, 3, D);  // Down face: lines 6-8, columns 3-5
    }

    /**
     * Parse a 3x3 face from the input lines into the stickers array.
     *
     * @param lines     All 9 lines from the input file
     * @param startLine First line of this face (0, 3, or 6)
     * @param startCol  First column of this face
     * @param faceIndex Which face this is (0-5)
     */
    private void parseFace(String[] lines, int startLine, int startCol, int faceIndex) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                char c = lines[startLine + row].charAt(startCol + col);
                stickers[faceIndex * 9 + row * 3 + col] = charToFace(c);
            }
        }
    }

    /**
     * Convert a color character to a face index.
     * This defines the color scheme used in input files.
     */
    private int charToFace(char c) {
        switch (c) {
            case 'O': return U;  // Orange = Up face
            case 'B': return R;  // Blue = Right face
            case 'W': return F;  // White = Front face
            case 'R': return D;  // Red = Down face
            case 'G': return L;  // Green = Left face
            case 'Y': return B;  // Yellow = Back face
            default: throw new IllegalArgumentException("Invalid color: " + c);
        }
    }

    /**
     * Convert sticker representation to piece representation.
     *
     * This is the key conversion that identifies:
     * 1. Which of the 8 corner pieces is at each corner position
     * 2. How each corner is twisted (orientation 0, 1, or 2)
     * 3. Which of the 12 edge pieces is at each edge position
     * 4. Whether each edge is flipped (orientation 0 or 1)
     *
     * @return A Cubie representing the same cube state
     */
    public Cubie toCubie() {
        Cubie cubie = new Cubie();

        // Process each of the 8 corner positions
        for (int pos = 0; pos < 8; pos++) {
            // Find orientation: which of the 3 facelets has the U or D color?
            // This tells us how the corner is twisted:
            //   0 = U/D color is on U/D face (no twist)
            //   1 = U/D color is rotated clockwise
            //   2 = U/D color is rotated counter-clockwise
            int orientation;
            for (orientation = 0; orientation < 3; orientation++) {
                int color = stickers[CORNER_FACELETS[pos][orientation]];
                if (color == U || color == D) {
                    break;
                }
            }

            // Get the other two colors (going clockwise from the U/D facelet)
            int color1 = stickers[CORNER_FACELETS[pos][(orientation + 1) % 3]];
            int color2 = stickers[CORNER_FACELETS[pos][(orientation + 2) % 3]];

            // Match these colors to identify which corner piece this is
            for (int piece = 0; piece < 8; piece++) {
                if (color1 == CORNER_COLORS[piece][1] && color2 == CORNER_COLORS[piece][2]) {
                    cubie.cornerPerm[pos] = piece;
                    cubie.cornerOrient[pos] = (byte) orientation;
                    break;
                }
            }
        }

        // Process each of the 12 edge positions
        for (int pos = 0; pos < 12; pos++) {
            int color0 = stickers[EDGE_FACELETS[pos][0]];
            int color1 = stickers[EDGE_FACELETS[pos][1]];

            // Match colors to identify which edge piece and whether it's flipped
            for (int piece = 0; piece < 12; piece++) {
                if (color0 == EDGE_COLORS[piece][0] && color1 == EDGE_COLORS[piece][1]) {
                    // Colors match in order - not flipped
                    cubie.edgePerm[pos] = piece;
                    cubie.edgeOrient[pos] = 0;
                    break;
                }
                if (color0 == EDGE_COLORS[piece][1] && color1 == EDGE_COLORS[piece][0]) {
                    // Colors match in reverse order - flipped
                    cubie.edgePerm[pos] = piece;
                    cubie.edgeOrient[pos] = 1;
                    break;
                }
            }
        }

        return cubie;
    }
}
