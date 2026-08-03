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
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Handles the Universal Chess Interface (UCI) communication with the Stockfish chess engine.
 */
public class StockfishEngine {
    private volatile Process process;
    private volatile OutputStreamWriter writer;
    private String enginePath;

    public static final class Evaluation {
        private final Integer centipawns;
        private final Integer mateInPly;
        private final String principalVariation;
        private final List<String> principalVariations;

        private Evaluation(Integer centipawns, Integer mateInPly, String principalVariation) {
            this.centipawns = centipawns;
            this.mateInPly = mateInPly;
            this.principalVariation = principalVariation;
            this.principalVariations = principalVariation == null
                    ? Collections.emptyList()
                    : Collections.singletonList(principalVariation);
        }

        private Evaluation(Integer centipawns, Integer mateInPly, List<String> principalVariations) {
            this.centipawns = centipawns;
            this.mateInPly = mateInPly;
            this.principalVariation = principalVariations.isEmpty() ? null : principalVariations.get(0);
            this.principalVariations = Collections.unmodifiableList(new ArrayList<>(principalVariations));
        }

        public static Evaluation centipawns(int score) {
            return new Evaluation(score, null, (String) null);
        }

        private static Evaluation centipawns(int score, String principalVariation) {
            return new Evaluation(score, null, principalVariation);
        }

        public static Evaluation mate(int mateInPly) {
            return new Evaluation(null, mateInPly, (String) null);
        }

        private static Evaluation mate(int mateInPly, String principalVariation) {
            return new Evaluation(null, mateInPly, principalVariation);
        }

        public String getPrincipalVariation() {
            return principalVariation;
        }

        public List<String> getPrincipalVariations() {
            return principalVariations;
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
    private volatile boolean forceStopRequested = false;
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
            forceStopRequested = false;

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

    public synchronized void setMultiPv(int count) {
        try {
            sendCommand("setoption name MultiPV value " + Math.max(1, count));
        } catch (IOException e) {
            System.err.println("Failed to set Stockfish MultiPV: " + e.getMessage());
        }
    }

    public synchronized void setThreads(int count) {
        try {
            sendCommand("setoption name Threads value " + Math.max(1, Math.min(4, count)));
        } catch (IOException e) {
            System.err.println("Failed to set Stockfish Threads: " + e.getMessage());
        }
    }

    public synchronized void setHash(int megabytes) {
        try {
            sendCommand("setoption name Hash value " + Math.max(16, Math.min(512, megabytes)));
        } catch (IOException e) {
            System.err.println("Failed to set Stockfish Hash: " + e.getMessage());
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
        return getBestMoveWithCommand(fen, "movetime " + movetimeMillis);
    }

    public synchronized String getBestMoveAtDepth(String fen, int depth) {
        return getBestMoveWithCommand(fen, "depth " + Math.max(1, depth));
    }

    private String getBestMoveWithCommand(String fen, String goCommand) {
        try {
            // Ensure engine is ready.
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) {
                System.err.println("Stockfish did not respond with readyok. Recent output: " + recentLines);
                return null;
            }

            stdoutLines.clear();

            sendCommand("position fen " + fen);
            sendCommand("go " + goCommand);

            // Wait for bestmove line from queued stdout.
            long deadline = System.currentTimeMillis() + 10000;
            while (!forceStopRequested && System.currentTimeMillis() < deadline) {
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
        return getEvaluationWithCommand(fen, "movetime " + movetimeMillis);
    }

    public synchronized Evaluation getEvaluationAtDepth(String fen, int depth) {
        return getEvaluationWithCommand(fen, "depth " + Math.max(1, depth));
    }

    /** Streams each improving evaluation until the requested depth or cancellation. */
    public synchronized String analyzeAtDepth(String fen, int depth,
            Consumer<Evaluation> onEvaluation, BooleanSupplier shouldContinue) {
        try {
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) return null;

            stdoutLines.clear();
            sendCommand("position fen " + fen);
            sendCommand("go depth " + Math.max(1, depth));

            Map<Integer, Evaluation> evaluationsByPv = new TreeMap<>();
            boolean stopSent = false;
            long deadline = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < deadline) {
                if (!stopSent && !shouldContinue.getAsBoolean()) {
                    sendCommand("stop");
                    stopSent = true;
                }
                String line = stdoutLines.poll(50, TimeUnit.MILLISECONDS);
                if (line == null) continue;
                if (line.startsWith("info ")) {
                    Evaluation parsed = parseEvaluationLine(line);
                    if (parsed != null) {
                        evaluationsByPv.put(parseMultiPvIndex(line), parsed);
                        Evaluation combined = combineEvaluations(parsed, evaluationsByPv);
                        if (onEvaluation != null) onEvaluation.accept(combined);
                    }
                } else if (line.startsWith("bestmove")) {
                    String[] tokens = line.split("\\s+");
                    return tokens.length >= 2 ? tokens[1] : null;
                }
            }
            if (!forceStopRequested) sendCommand("stop");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public synchronized String analyzeAtTime(String fen, int movetimeMillis, int multiPv,
            Consumer<Evaluation> onEvaluation, BooleanSupplier shouldContinue) {
        try {
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) return null;
            stdoutLines.clear();
            sendCommand("position fen " + fen);
            sendCommand("setoption name MultiPV value " + Math.max(1, Math.min(4, multiPv)));
            int boundedTime = Math.max(2000, Math.min(60000, movetimeMillis));
            sendCommand("go movetime " + boundedTime);
            Map<Integer, Evaluation> evaluationsByPv = new TreeMap<>();
            Evaluation latestEvaluation = null;
            boolean stopSent = false;
            long deadline = System.currentTimeMillis() + boundedTime + 5000L;
            while (!forceStopRequested && System.currentTimeMillis() < deadline) {
                if (!stopSent && !shouldContinue.getAsBoolean()) {
                    sendCommand("stop");
                    stopSent = true;
                }
                String line = stdoutLines.poll(50, TimeUnit.MILLISECONDS);
                if (line == null) continue;
                if (line.startsWith("info ")) {
                    Evaluation parsed = parseEvaluationLine(line);
                    if (parsed != null) {
                        evaluationsByPv.put(parseMultiPvIndex(line), parsed);
                        latestEvaluation = combineEvaluations(parsed, evaluationsByPv);
                        if (onEvaluation != null) onEvaluation.accept(latestEvaluation);
                    }
                } else if (line.startsWith("bestmove")) {
                    if (onEvaluation != null && latestEvaluation != null) {
                        onEvaluation.accept(latestEvaluation);
                    }
                    String[] tokens = line.split("\\s+");
                    return tokens.length >= 2 ? tokens[1] : null;
                }
            }
            if (!forceStopRequested) sendCommand("stop");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private Evaluation getEvaluationWithCommand(String fen, String goCommand) {
        try {
            sendCommand("isready");
            if (!waitForToken("readyok", 3000)) {
                System.err.println("Stockfish did not respond with readyok. Recent output: " + recentLines);
                return null;
            }

            stdoutLines.clear();

            sendCommand("position fen " + fen);
            sendCommand("go " + goCommand);

            Evaluation evaluation = null;
            Map<Integer, Evaluation> evaluationsByPv = new TreeMap<>();
            long deadline = System.currentTimeMillis() + 10000;
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
                            evaluationsByPv.put(parseMultiPvIndex(line), parsed);
                        }
                    } else if (line.startsWith("bestmove")) {
                        return combineEvaluations(evaluation, evaluationsByPv);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            System.err.println("Timed out waiting for evaluation. Recent output: " + recentLines);
            return combineEvaluations(evaluation, evaluationsByPv);
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
                String principalVariation = parsePrincipalVariation(tokens);
                if ("cp".equals(type)) {
                    return Evaluation.centipawns(Integer.parseInt(value), principalVariation);
                }
                if ("mate".equals(type)) {
                    return Evaluation.mate(Integer.parseInt(value), principalVariation);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String parsePrincipalVariation(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            if ("pv".equals(tokens[i]) && i + 1 < tokens.length) {
                StringBuilder pv = new StringBuilder();
                for (int j = i + 1; j < tokens.length; j++) {
                    if (pv.length() > 0) pv.append(' ');
                    pv.append(tokens[j]);
                }
                return pv.toString();
            }
        }
        return null;
    }

    private int parseMultiPvIndex(String line) {
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("multipv".equals(tokens[i])) {
                try {
                    return Integer.parseInt(tokens[i + 1]);
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private Evaluation combineEvaluations(Evaluation fallback, Map<Integer, Evaluation> evaluationsByPv) {
        if (fallback == null || evaluationsByPv.isEmpty()) {
            return fallback;
        }
        Evaluation primary = evaluationsByPv.getOrDefault(1, fallback);
        List<String> variations = new ArrayList<>();
        for (Evaluation evaluation : evaluationsByPv.values()) {
            if (evaluation.getPrincipalVariation() != null) {
                variations.add(evaluation.getPrincipalVariation());
            }
        }
        return new Evaluation(primary.centipawns, primary.mateInPly, variations);
    }

    /**
     * Stops the engine and terminates the process.
     */
    public synchronized void stop() {
        forceStopRequested = true;
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

    /**
     * Immediately terminates the engine without waiting for an active analysis
     * call to release this object's monitor.
     */
    public void forceStop() {
        forceStopRequested = true;
        readerThreadRunning = false;

        Process processToStop = process;
        OutputStreamWriter writerToClose = writer;
        if (writerToClose != null) {
            try {
                writerToClose.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        if (processToStop != null) {
            processToStop.destroyForcibly();
        }
    }
}


