package com.jchess.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Handles the Universal Chess Interface (UCI) communication with the Stockfish chess engine.
 */
public class StockfishEngine {
    private Process process;
    private OutputStreamWriter writer;
    private String enginePath;

    public static final class Evaluation {
        private final Integer centipawns;
        private final Integer mateInPly;

        private Evaluation(Integer centipawns, Integer mateInPly) {
            this.centipawns = centipawns;
            this.mateInPly = mateInPly;
        }

        public static Evaluation centipawns(int score) {
            return new Evaluation(score, null);
        }

        public static Evaluation mate(int mateInPly) {
            return new Evaluation(null, mateInPly);
        }

        public boolean isMate() {
            return mateInPly != null;
        }

        public int toWhiteCentipawns(boolean whiteToMove) {
            if (mateInPly != null) {
                int mateScore = 30000 - Math.min(Math.abs(mateInPly), 1000) * 100;
                return mateInPly > 0 ? (whiteToMove ? mateScore : -mateScore) : (whiteToMove ? -mateScore : mateScore);
            }
            return whiteToMove ? centipawns : -centipawns;
        }

        public String toDisplayString(boolean whiteToMove) {
            int whiteScore = toWhiteCentipawns(whiteToMove);

            if (mateInPly != null) {
                int mateMoves = Math.max(1, (Math.abs(mateInPly) + 1) / 2);
                String prefix = whiteScore > 0 ? "+M" : whiteScore < 0 ? "-M" : "M";
                return prefix + mateMoves;
            }

            double pawns = whiteScore / 100.0;
            String sign = pawns > 0 ? "+" : "";
            return String.format(java.util.Locale.US, "%s%.2f", sign, pawns);
        }
    }

    // Robust UCI reader: engine stdout is read on its own thread and lines are queued.
    private final BlockingQueue<String> stdoutLines = new LinkedBlockingQueue<>();
    private volatile boolean readerThreadRunning = false;
    private Thread stdoutReaderThread;


    // Keep a small ring buffer of recent engine lines for debugging.
    private final Deque<String> recentLines = new ArrayDeque<>();
    private static final int RECENT_LINES_LIMIT = 50;

    public static String getSavedPath() {
        File file = new File("stockfish_path.txt");
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                return br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Check standard added executable paths
        File defaultExe = new File("stockfish/stockfish-windows-x86-64-avx2.exe");
        if (defaultExe.exists()) {
            return defaultExe.getPath();
        }

        return "stockfish.exe"; // default fallback
    }

    public static void savePath(String path) {
        try (FileWriter fw = new FileWriter("stockfish_path.txt")) {
            fw.write(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StockfishEngine(String enginePath) {
        this.enginePath = enginePath;
    }

    /**
     * Starts the Stockfish process and initializes UCI mode.
     */
    public synchronized boolean start() {
        try {
            stop(); // ensure no prior process is running

            process = new ProcessBuilder(enginePath).start();
            stdoutLines.clear();
            recentLines.clear();

            writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);

            readerThreadRunning = true;
            stdoutReaderThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (readerThreadRunning && (line = r.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            recentLines.addLast(trimmed);
                            while (recentLines.size() > RECENT_LINES_LIMIT) {
                                recentLines.removeFirst();
                            }
                            stdoutLines.offer(trimmed);
                        }
                    }
                } catch (IOException ignored) {
                    // process probably terminated
                }
            }, "stockfish-stdout-reader");

            stdoutReaderThread.setDaemon(true);
            stdoutReaderThread.start();

            // UCI init
            sendCommand("uci");
            if (!waitForToken("uciok", 3000)) {
                System.err.println("Stockfish did not respond with uciok. Recent output: " + recentLines);
                return false;
            }

            sendCommand("ucinewgame");
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) {
                System.err.println("Stockfish did not respond with readyok after ucinewgame. Recent output: " + recentLines);
                return false;
            }
 
            return true;
        } catch (IOException e) {
            System.err.println("Failed to start Stockfish at: " + enginePath + " (" + e.getMessage() + ")");
            return false;
        }
    }



    private boolean waitForToken(String token, int timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;

                String line = stdoutLines.poll(Math.min(remaining, 50), TimeUnit.MILLISECONDS);
                if (line == null) continue;
                if (line.equals(token)) return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for token: " + token);
            }
        }
        return false;
    }


    /**
     * Sends a raw command to the Stockfish engine.
     */
    public synchronized void sendCommand(String cmd) throws IOException {
        if (writer != null) {
            writer.write(cmd + "\n");
            writer.flush();
        }
    }

    /**
     * Configures the engine's Skill Level (0 to 20).
     */
    public synchronized void setSkillLevel(int level) {
        int clampedLevel = Math.max(0, Math.min(20, level));
        try {
            sendCommand("setoption name Skill Level value " + clampedLevel);
        } catch (IOException e) {
            System.err.println("Failed to set Stockfish Skill Level: " + e.getMessage());
        }
    }

    /**
     * Finds the best move for the given FEN position.
     *
     * @param fen             The position in FEN format.
     * @param movetimeMillis  The time in milliseconds to let the engine search.
     * @return The best move in UCI format (e.g. "e2e4"), or null if search failed.
     */
    public synchronized String getBestMove(String fen, int movetimeMillis) {
        try {
            // Ensure engine is ready.
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) {
                System.err.println("Stockfish did not respond with readyok. Recent output: " + recentLines);
                return null;
            }

            stdoutLines.clear();

            sendCommand("position fen " + fen);
            sendCommand("go movetime " + movetimeMillis);

            // Wait for bestmove line from queued stdout.
            long deadline = System.currentTimeMillis() + Math.max(2000, movetimeMillis + 3000);
            while (System.currentTimeMillis() < deadline) {
                try {
                    String line = stdoutLines.poll(50, TimeUnit.MILLISECONDS);
                    if (line == null) continue;
                    if (line.startsWith("bestmove")) {
                        // Typical: "bestmove e2e4" or "bestmove (none)"
                        String[] tokens = line.split("\\s+");
                        if (tokens.length >= 2) return tokens[1];
                        return null;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }


            System.err.println("Timed out waiting for bestmove. Recent output: " + recentLines);
            return null;

        } catch (IOException e) {
            System.err.println("Error communicating with Stockfish: " + e.getMessage());
            return null;
        }
    }

    public synchronized Evaluation getEvaluation(String fen, int movetimeMillis) {
        try {
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) {
                System.err.println("Stockfish did not respond with readyok. Recent output: " + recentLines);
                return null;
            }

            stdoutLines.clear();

            sendCommand("position fen " + fen);
            sendCommand("go movetime " + movetimeMillis);

            Evaluation evaluation = null;
            long deadline = System.currentTimeMillis() + Math.max(2000, movetimeMillis + 3000);
            while (System.currentTimeMillis() < deadline) {
                try {
                    String line = stdoutLines.poll(50, TimeUnit.MILLISECONDS);
                    if (line == null) {
                        continue;
                    }
                    if (line.startsWith("info ")) {
                        Evaluation parsed = parseEvaluationLine(line);
                        if (parsed != null) {
                            evaluation = parsed;
                        }
                    } else if (line.startsWith("bestmove")) {
                        return evaluation;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            System.err.println("Timed out waiting for evaluation. Recent output: " + recentLines);
            return evaluation;
        } catch (IOException e) {
            System.err.println("Error communicating with Stockfish: " + e.getMessage());
            return null;
        }
    }

    private Evaluation parseEvaluationLine(String line) {
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < tokens.length - 2; i++) {
            if (!"score".equals(tokens[i])) {
                continue;
            }

            String type = tokens[i + 1];
            String value = tokens[i + 2];
            try {
                if ("cp".equals(type)) {
                    return Evaluation.centipawns(Integer.parseInt(value));
                }
                if ("mate".equals(type)) {
                    return Evaluation.mate(Integer.parseInt(value));
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Stops the engine and terminates the process.
     */
    public synchronized void stop() {
        readerThreadRunning = false;

        try {
            if (writer != null) {
                try {
                    sendCommand("quit");
                } catch (Exception ignored) {
                    // ignore
                }
            }
        } catch (Exception ignored) {
            // ignore
        } finally {
            if (process != null) {
                process.destroy();
                process = null;
            }
            writer = null;
        }
    }
}


