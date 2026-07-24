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

#endif
