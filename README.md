# Rubik's Cube Solver

A high-performance Rubik's Cube solver implementing Herbert Kociemba's Two-Phase Algorithm. This solver can find solutions for any valid cube configuration in under one second, producing solutions typically within 20-25 moves.

## Overview

The Rubik's Cube has approximately 43 quintillion (4.3 x 10^19) possible configurations, making naive search approaches computationally intractable. This implementation addresses this challenge by decomposing the problem into two smaller subproblems, reducing the effective search space from 10^19 states to approximately 10^7 + 10^6 states.

## Algorithm

### Two-Phase Approach

**Phase 1: Reach the G1 Subgroup**
- Transforms any scrambled cube into a special subgroup G1
- Uses all 18 possible moves (6 faces x 3 turn types)
- Goal: Orient all corners, orient all edges, position E-slice edges in the middle layer
- Typical depth: 7-12 moves

**Phase 2: Solve from G1**
- Solves from G1 to the completed state using restricted moves
- Restricted moves: U, U', U2, D, D', D2, R2, L2, F2, B2
- These moves preserve the properties established in Phase 1
- Typical depth: 10-18 moves

### Key Optimizations

- **Coordinate-Based Representation**: Cube states are encoded as integers, enabling O(1) table lookups
- **Move Tables**: Precomputed tables transform move application from O(n) operations to O(1) lookups
- **Pruning Tables**: Admissible heuristics built via backward BFS, providing strong pruning for IDA* search
- **Nibble Packing**: Pruning values stored as 4-bit nibbles, reducing memory usage by 50%

## Project Structure

```
src/rubikscube/
    Solver.java       - Entry point, handles file I/O
    RubiksCube.java   - Parses input file, converts stickers to pieces
    Cubie.java        - Cube representation using permutation arrays
    Tables.java       - Precomputed move and pruning tables
    TwoPhase.java     - IDA* search implementation for both phases
```

## Building and Running

### Compilation

```bash
cd src
javac rubikscube/*.java
```

### Usage

```bash
java rubikscube.Solver <input_file> <output_file>
```

### Example

```bash
java rubikscube.Solver scramble.txt solution.txt
```

## Input Format

The input file represents an unfolded cube using color characters:

```
   OOO
   OOO
   OOO
GGGWWWBBBYYY
GGGWWWBBBYYY
GGGWWWBBBYYY
   RRR
   RRR
   RRR
```

**Color Mapping:**
- O = Orange (Up face)
- W = White (Front face)
- G = Green (Left face)
- B = Blue (Right face)
- R = Red (Down face)
- Y = Yellow (Back face)

## Output Format

The solution is a sequence of face letters representing moves:
- Single letter (e.g., `U`) = 90-degree clockwise turn
- Double letter (e.g., `UU`) = 180-degree turn
- Triple letter (e.g., `UUU`) = 90-degree counter-clockwise turn (equivalent to U')

Example output: `RRFFUULLBB`

## Technical Details

### Coordinate System

The algorithm uses integer coordinates to efficiently encode cube state aspects:

| Coordinate | Range | Description |
|------------|-------|-------------|
| Twist | 0-2186 | Corner orientation (3^7 values) |
| Flip | 0-2047 | Edge orientation (2^11 values) |
| Slice | 0-11879 | E-slice edge positions and permutation |
| Corner Perm | 0-20159 | Corner permutation for Phase 2 |
| UD Edge Perm | 0-20159 | UD-layer edge permutation for Phase 2 |

### Complexity Analysis

**Time Complexity:**
- Table initialization: O(N x 18 x D) where N is coordinate size and D is max depth
- Search per node: O(1) for move and pruning table lookups
- Initialization time: 2-5 seconds on modern hardware

**Space Complexity:**
- Move tables: ~1.2 MB
- Pruning tables: ~0.8 MB (with nibble packing)
- Search stack: O(d) where d is solution depth

### Heuristic Design

The pruning tables provide admissible heuristics by storing minimum moves to reach the goal from each coordinate. The heuristic for each phase is computed as:

```
h = max(table1[coord1], table2[coord2])
```

Taking the maximum of multiple pruning lookups ensures the heuristic remains admissible while providing stronger pruning than any single table alone.

## Performance

| Metric | Value |
|--------|-------|
| Average solve time | < 1 second |
| Solution length | 20-25 moves |
| Memory usage | ~2 MB |
| Table initialization | 2-5 seconds |

## Error Codes

The solver returns error codes for invalid cube configurations:

| Code | Description |
|------|-------------|
| Error 2 | Missing or duplicate edge piece |
| Error 3 | Edge orientation constraint violated |
| Error 4 | Missing or duplicate corner piece |
| Error 5 | Corner orientation constraint violated |
| Error 6 | Parity constraint violated |
| Error 7 | No solution found within depth limit |
| Error 8 | Timeout exceeded |

## References

1. Kociemba, H. "Two-Phase Algorithm." https://kociemba.org/math/twophase.htm
2. Korf, R. E. (1997). "Finding Optimal Solutions to Rubik's Cube Using Pattern Databases." Proceedings of the National Conference on Artificial Intelligence.
3. Feldhausen, R. (2008). "Implementing a Two-Phase Algorithm for Solving a Rubik's Cube." Kansas State University.

## Author

Anton Florendo

## License

This project was developed for CMPT 225 at Simon Fraser University.
