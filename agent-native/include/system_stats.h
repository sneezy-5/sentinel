#ifndef SENTINEL_SYSTEM_STATS_H
#define SENTINEL_SYSTEM_STATS_H

#include "process_stats.h"

typedef struct {
    double used_gb;
    double total_gb;
    char mount[256];
} disk_usage_t;

typedef struct {
    double cpu_percent;
    int cpu_cores;
    long ram_used_mb;
    long ram_total_mb;
    long rx_bytes;
    long tx_bytes;
    disk_usage_t disks[16];
    int disk_count;
    /* Top processes by RSS, like `top` sorted by memory - processes[0] is the heaviest. */
    top_processes_t top_processes;
} system_stats_t;

/* Reads /proc and /sys to fill out. Returns 0 on success. */
int system_stats_collect(system_stats_t *out);

/* Serializes out to JSON into buffer (of size buffer_size). Returns the number of bytes written. */
int system_stats_to_json(const system_stats_t *stats, char *buffer, int buffer_size);

#endif
