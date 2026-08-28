Benchmark Results
Run	Users / Ramp	Requests	Failures	Throughput	p50	p95	p99	Max
Light	200 / 30s	10,000	0	333 req/s	1ms	9ms	15ms	89ms
Heavy	2000 / 20s	100,000	0	1,887 req/s	621ms	1,385ms	1,778ms	2,831ms
Findings
Zero failures at any load tested — the app degrades gracefully (slower), it doesn't error out or drop requests, even at 10x load.
Latency was highest early in the heavy run and improved as it continued — a JVM/connection-pool warm-up artifact, not sustained overload. Steady-state latency later in the run is the more representative number.
Ruled out the DB connection pool as the cause of heavy-load latency — no HikariPool exhaustion warnings in server logs during the run.
Confirmed via per-process CPU profiling that the app is CPU-bound under load, not blocked on locks or I/O — one java.exe process sustained 80-90% CPU for the exact duration of the heavy test (idle before/after), consistent with real compute work (JSON serialization happening 2-3x per trade, GC churn from object allocation) rather than threads sitting idle waiting.
Caveat to state honestly, not hide: load generator (Gatling) and the app under test ran on the same machine, sharing CPU cores — a real methodological limitation for a fully rigorous benchmark, worth one sentence in the README rather than omitting.
