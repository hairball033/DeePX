package com.smallhairball.deepx;

/** Small code257-only VT screen used by full-screen workspace programs such as nano. */
final class WorkspaceVtTerminalV241 {
    private static final int ROWS = 30;
    private static final int COLS = 100;
    private final char[][] cells = new char[ROWS][COLS];
    private int row;
    private int col;
    private int savedRow;
    private int savedCol;
    private boolean alternate;
    private final StringBuilder escape = new StringBuilder();

    WorkspaceVtTerminalV241() { clear(); }

    boolean isAlternateScreen() { return alternate; }

    int cursorOffset() {
        return Math.max(0, Math.min(render().length(), row * (COLS + 1) + col));
    }

    void feed(String chunk) {
        if (chunk == null) return;
        for (int index = 0; index < chunk.length();) {
            char code = chunk.charAt(index++);
            if (escape.length() > 0) {
                escape.append(code);
                if (escape.length() == 2 && code != '[' && code != ']' && code != '(') {
                    escape.setLength(0);
                // '[' itself is in the CSI final-byte ASCII range.  Do not treat the
                // introducer ESC [ as a completed sequence: its parameter slice would
                // be substring(2, 1), crashing the host as soon as nano paints.
                } else if (escape.length() > 2 && escape.charAt(1) == '['
                        && code >= 0x40 && code <= 0x7e) {
                    applyCsi(escape.substring(2, escape.length() - 1), code);
                    escape.setLength(0);
                } else if (escape.charAt(1) == ']' && (code == 7
                        || escape.length() >= 2 && escape.charAt(escape.length() - 2) == 27
                        && code == '\\')) {
                    escape.setLength(0);
                } else if (escape.length() > 128) {
                    escape.setLength(0);
                }
                continue;
            }
            if (code == 27) {
                escape.append(code);
            } else if (code == '\r') {
                col = 0;
            } else if (code == '\n') {
                lineFeed();
            } else if (code == '\b') {
                col = Math.max(0, col - 1);
            } else if (code == '\t') {
                col = Math.min(COLS - 1, (col + 8) & ~7);
            } else if (code >= 0x20 && code != 0x7f) {
                cells[row][col] = code;
                if (++col >= COLS) {
                    col = 0;
                    lineFeed();
                }
            }
        }
    }

    String render() {
        StringBuilder out = new StringBuilder(ROWS * (COLS + 1));
        for (int r = 0; r < ROWS; r++) {
            int end = COLS;
            while (end > 0 && cells[r][end - 1] == ' ') end--;
            out.append(cells[r], 0, end);
            if (r + 1 < ROWS) out.append('\n');
        }
        return out.toString();
    }

    private void applyCsi(String raw, char command) {
        boolean privateMode = raw.startsWith("?");
        String body = privateMode ? raw.substring(1) : raw;
        int[] values = parameters(body);
        int first = values.length == 0 || values[0] == 0 ? 1 : values[0];
        if (privateMode && (command == 'h' || command == 'l')) {
            for (int value : values) {
                if (value == 1049 || value == 47 || value == 1047) {
                    alternate = command == 'h';
                    if (alternate) clear();
                }
            }
            return;
        }
        switch (command) {
            case 'A': row = Math.max(0, row - first); break;
            case 'B': row = Math.min(ROWS - 1, row + first); break;
            case 'C': col = Math.min(COLS - 1, col + first); break;
            case 'D': col = Math.max(0, col - first); break;
            case 'G': col = clamp(first - 1, COLS); break;
            case 'd': row = clamp(first - 1, ROWS); break;
            case 'H':
            case 'f':
                row = clamp((values.length > 0 && values[0] > 0 ? values[0] : 1) - 1, ROWS);
                col = clamp((values.length > 1 && values[1] > 0 ? values[1] : 1) - 1, COLS);
                break;
            case 'J':
                if (values.length == 0 || values[0] == 0 || values[0] == 2 || values[0] == 3) clear();
                break;
            case 'K':
                int mode = values.length == 0 ? 0 : values[0];
                int start = mode == 1 || mode == 2 ? 0 : col;
                int end = mode == 0 ? COLS : Math.min(COLS, col + 1);
                if (mode == 2) end = COLS;
                for (int c = start; c < end; c++) cells[row][c] = ' ';
                break;
            case 's': savedRow = row; savedCol = col; break;
            case 'u': row = savedRow; col = savedCol; break;
            default: break; // Styling, mouse and device-status sequences do not change text.
        }
    }

    private void lineFeed() {
        if (row + 1 < ROWS) {
            row++;
            return;
        }
        for (int r = 1; r < ROWS; r++) {
            System.arraycopy(cells[r], 0, cells[r - 1], 0, COLS);
        }
        for (int c = 0; c < COLS; c++) cells[ROWS - 1][c] = ' ';
    }

    private void clear() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) cells[r][c] = ' ';
        }
        row = 0;
        col = 0;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum - 1, value));
    }

    private static int[] parameters(String value) {
        if (value == null || value.length() == 0) return new int[0];
        String[] parts = value.split(";", -1);
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            int colon = part.indexOf(':');
            if (colon >= 0) part = part.substring(0, colon);
            try { result[index] = Integer.parseInt(part.length() == 0 ? "0" : part); }
            catch (NumberFormatException ignored) { result[index] = 0; }
        }
        return result;
    }
}
