#ifndef SENTINEL_PROCESS_STATS_H
#define SENTINEL_PROCESS_STATS_H

typedef struct {
    int pid;
    char name[256];
    long rss_kb;
    unsigned long utime_ticks;
    unsigned long stime_ticks;
} process_stats_t;

/* Generic fallback via /proc/{pid}/status and /proc/{pid}/stat (architecture doc,
 * section 3.2, ProcessAdapter). Returns 0 on success, -1 if the pid doesn't exist or
 * is no longer readable. */
int process_stats_collect(int pid, process_stats_t *out);

#define PROCESS_STATS_TOP_N 5

typedef struct {
    process_stats_t processes[PROCESS_STATS_TOP_N];
    int count;
} top_processes_t;

/* Scans every PID under /proc, keeping the top PROCESS_STATS_TOP_N by RSS (VmRSS) - a
 * lightweight approximation of `top` sorted by memory. Individual processes that vanish
 * mid-scan or aren't readable (permissions) are skipped rather than failing the whole
 * scan - out->count just ends up lower. Returns 0 on success, -1 only if /proc itself
 * can't be opened. */
int process_stats_top_by_rss(top_processes_t *out);

#endif
