## JChess Fix: Stockfish not responding
- [ ] Update `src/com/jchess/util/StockfishEngine.java` to use a robust UCI stdout reader thread + blocking queue (remove `reader.ready()` polling).
- [ ] Update `src/com/jchess/view/GamePanel.java` to prevent multiple concurrent engine threads (guard around `triggerComputerMove`) and improve logging when moves are rejected.
- [ ] Compile and run to verify: engine starts, responds with `uciok/readyok/bestmove`, and computer move is applied.
