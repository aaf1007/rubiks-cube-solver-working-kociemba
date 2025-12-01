package rubikscube;

import java.io.*;

/**
 * Sticker-level cube representation.
 * Parses input file into 54 face colors, then converts to piece representation.
 * 
 * Face indices (0-5): U, R, F, D, L, B
 * Sticker indices per face (0-8): reading order top-left to bottom-right
 */
public class StickerCube {

    // Face constants (used as color identifiers after parsing)
    public static final int U = 0;  // Up (Orange in your scheme)
    public static final int R = 1;  // Right (Blue)
    public static final int F = 2;  // Front (White)
    public static final int D = 3;  // Down (Red)
    public static final int L = 4;  // Left (Green)
    public static final int B = 5;  // Back (Yellow)

    // 54 stickers stored as face colors (0-5)
    public int[] stickers = new int[54];

    // Corner facelet positions: which 3 stickers make up each corner
    // Order: [corner_index][0,1,2] = U/D facelet, clockwise facelet, counter-clockwise facelet
    private static final int[][] CORNER_FACELETS = {
        {8, 9, 20},   // URF: U9, R1, F3
        {6, 18, 38},  // UFL: U7, F1, L3
        {0, 36, 47},  // ULB: U1, L1, B3
        {2, 45, 11},  // UBR: U3, B1, R3
        {29, 26, 15}, // DFR: D3, F9, R7
        {27, 44, 24}, // DLF: D1, L9, F7
        {33, 53, 42}, // DBL: D7, B9, L7
        {35, 17, 51}  // DRB: D9, R9, B7
    };

    // Edge facelet positions: which 2 stickers make up each edge
    private static final int[][] EDGE_FACELETS = {
        {5, 10},  // UR: U6, R2
        {7, 19},  // UF: U8, F2
        {3, 37},  // UL: U4, L2
        {1, 46},  // UB: U2, B2
        {32, 16}, // DR: D6, R8
        {28, 25}, // DF: D2, F8
        {30, 43}, // DL: D4, L8
        {34, 52}, // DB: D8, B8
        {23, 12}, // FR: F6, R4
        {21, 41}, // FL: F4, L6
        {50, 39}, // BL: B6, L4
        {48, 14}  // BR: B4, R6
    };

    // What colors each corner should have (matches CORNER_FACELETS order)
    private static final int[][] CORNER_COLORS = {
        {U, R, F}, {U, F, L}, {U, L, B}, {U, B, R},
        {D, F, R}, {D, L, F}, {D, B, L}, {D, R, B}
    };

    // What colors each edge should have
    private static final int[][] EDGE_COLORS = {
        {U, R}, {U, F}, {U, L}, {U, B},
        {D, R}, {D, F}, {D, L}, {D, B},
        {F, R}, {F, L}, {B, L}, {B, R}
    };

    /**
     * Default constructor - creates solved cube.
     */
    public StickerCube() {
        for (int face = 0; face < 6; face++) {
            for (int i = 0; i < 9; i++) {
                stickers[face * 9 + i] = face;
            }
        }
    }

    /**
     * Parse cube state from file.
     * Expected format:
     *    OOO      (Up face, rows 0-2)
     *    OOO
     *    OOO
     * GGGWWWBBBYYY  (L, F, R, B faces, rows 3-5)
     * GGGWWWBBBYYY
     * GGGWWWBBBYYY
     *    RRR      (Down face, rows 6-8)
     *    RRR
     *    RRR
     */
    public StickerCube(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String[] lines = new String[9];

        for (int i = 0; i < 9; i++) {
            lines[i] = reader.readLine();
            if (lines[i] == null) {
                reader.close();
                throw new IOException("File has fewer than 9 lines");
            }
        }
        reader.close();

        // Parse Up face (lines 0-2, chars 3-5)
        parseFace(lines, 0, 3, U);

        // Parse Left face (lines 3-5, chars 0-2)
        parseFace(lines, 3, 0, L);

        // Parse Front face (lines 3-5, chars 3-5)
        parseFace(lines, 3, 3, F);

        // Parse Right face (lines 3-5, chars 6-8)
        parseFace(lines, 3, 6, R);

        // Parse Back face (lines 3-5, chars 9-11)
        parseFace(lines, 3, 9, B);

        // Parse Down face (lines 6-8, chars 3-5)
        parseFace(lines, 6, 3, D);
    }

    /**
     * Parse a 3x3 face from input lines.
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
     * Convert color character to face index.
     * Your color scheme: O=Up, B=Right, W=Front, R=Down, G=Left, Y=Back
     */
    private int charToFace(char c) {
        switch (c) {
            case 'O': return U;  // Orange = Up
            case 'B': return R;  // Blue = Right
            case 'W': return F;  // White = Front
            case 'R': return D;  // Red = Down
            case 'G': return L;  // Green = Left
            case 'Y': return B;  // Yellow = Back
            default: throw new IllegalArgumentException("Invalid color: " + c);
        }
    }

    /**
     * Convert sticker representation to piece representation.
     * Identifies which piece is at each position and its orientation.
     */
    public PieceCube toPieceCube() {
        PieceCube pc = new PieceCube();

        // Process corners
        for (int pos = 0; pos < 8; pos++) {
            // Find orientation: which facelet has U or D color?
            int orientation;
            for (orientation = 0; orientation < 3; orientation++) {
                int color = stickers[CORNER_FACELETS[pos][orientation]];
                if (color == U || color == D) {
                    break;
                }
            }

            // Get colors of the other two facelets (clockwise from U/D)
            int color1 = stickers[CORNER_FACELETS[pos][(orientation + 1) % 3]];
            int color2 = stickers[CORNER_FACELETS[pos][(orientation + 2) % 3]];

            // Find which corner piece this is
            for (int piece = 0; piece < 8; piece++) {
                if (color1 == CORNER_COLORS[piece][1] && color2 == CORNER_COLORS[piece][2]) {
                    pc.cornerPerm[pos] = piece;
                    pc.cornerOrient[pos] = (byte) orientation;
                    break;
                }
            }
        }

        // Process edges
        for (int pos = 0; pos < 12; pos++) {
            int color0 = stickers[EDGE_FACELETS[pos][0]];
            int color1 = stickers[EDGE_FACELETS[pos][1]];

            // Find which edge piece this is and its orientation
            for (int piece = 0; piece < 12; piece++) {
                if (color0 == EDGE_COLORS[piece][0] && color1 == EDGE_COLORS[piece][1]) {
                    pc.edgePerm[pos] = piece;
                    pc.edgeOrient[pos] = 0;
                    break;
                }
                if (color0 == EDGE_COLORS[piece][1] && color1 == EDGE_COLORS[piece][0]) {
                    pc.edgePerm[pos] = piece;
                    pc.edgeOrient[pos] = 1;
                    break;
                }
            }
        }

        return pc;
    }
}
