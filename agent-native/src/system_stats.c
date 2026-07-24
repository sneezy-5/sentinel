#include "system_stats.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/statvfs.h>

/* Two /proc/stat reads a short interval apart to compute %CPU (a single instantaneous
 * read only gives a cumulative total since boot, not a rate). */
static int read_cpu_total_idle(long long *total, long long *idle) {
    FILE *f = fopen("/proc/stat", "r");
    if (!f) {
        return -1;
    }
    char cpu_label[16];
    long long user, nice, system_t, idle_t, iowait, irq, softirq, steal;
    int matched = fscanf(f, "%15s %lld %lld %lld %lld %lld %lld %lld %lld",
                          cpu_label, &user, &nice, &system_t, &idle_t, &iowait, &irq, &softirq, &steal);
    fclose(f);
    if (matched < 5) {
        return -1;
    }
    *idle = idle_t + iowait;
    *total = user + nice + system_t + *idle + irq + softirq + steal;
    return 0;
}

static double read_cpu_percent(void) {
    long long total1, idle1, total2, idle2;
    if (read_cpu_total_idle(&total1, &idle1) != 0) {
        return 0.0;
    }
    usleep(100 * 1000);
    if (read_cpu_total_idle(&total2, &idle2) != 0) {
        return 0.0;
    }
    long long total_delta = total2 - total1;
    long long idle_delta = idle2 - idle1;
    if (total_delta <= 0) {
        return 0.0;
    }
    return 100.0 * (double) (total_delta - idle_delta) / (double) total_delta;
}

static void read_ram(long *ram_used_mb, long *ram_total_mb) {
    FILE *f = fopen("/proc/meminfo", "r");
    long mem_total_kb = 0, mem_available_kb = 0;
    if (f) {
        char key[64];
        long value;
        char unit[16];
        while (fscanf(f, "%63s %ld %15s", key, &value, unit) == 3) {
            if (strcmp(key, "MemTotal:") == 0) {
                mem_total_kb = value;
            } else if (strcmp(key, "MemAvailable:") == 0) {
                mem_available_kb = value;
            }
        }
        fclose(f);
    }
    *ram_total_mb = mem_total_kb / 1024;
    *ram_used_mb = (mem_total_kb - mem_available_kb) / 1024;
}

static void read_root_disk(disk_usage_t *disk) {
    struct statvfs fs_stats;
    strncpy(disk->mount, "/", sizeof(disk->mount) - 1);
    disk->mount[sizeof(disk->mount) - 1] = '\0';
    if (statvfs("/", &fs_stats) != 0) {
        disk->used_gb = 0.0;
        disk->total_gb = 0.0;
        return;
    }
    double block_size = (double) fs_stats.f_frsize;
    double total_bytes = block_size * (double) fs_stats.f_blocks;
    double free_bytes = block_size * (double) fs_stats.f_bfree;
    disk->total_gb = total_bytes / (1024.0 * 1024.0 * 1024.0);
    disk->used_gb = (total_bytes - free_bytes) / (1024.0 * 1024.0 * 1024.0);
}

static void read_network(long *rx_bytes, long *tx_bytes) {
    *rx_bytes = 0;
    *tx_bytes = 0;
    FILE *f = fopen("/proc/net/dev", "r");
    if (!f) {
        return;
    }
    char line[512];
    /* Two header lines to skip before the per-interface counters. */
    fgets(line, sizeof(line), f);
    fgets(line, sizeof(line), f);
    while (fgets(line, sizeof(line), f)) {
        char iface[64];
        long long rx, tx_placeholder[7], tx;
        int n = sscanf(line, " %63[^:]: %lld %lld %lld %lld %lld %lld %lld %lld %lld",
                       iface, &rx,
                       &tx_placeholder[0], &tx_placeholder[1], &tx_placeholder[2], &tx_placeholder[3],
                       &tx_placeholder[4], &tx_placeholder[5], &tx_placeholder[6], &tx);
        if (n < 10 || strcmp(iface, "lo") == 0) {
            continue;
        }
        *rx_bytes += rx;
        *tx_bytes += tx;
    }
    fclose(f);
}

int system_stats_collect(system_stats_t *out) {
    memset(out, 0, sizeof(*out));
    out->cpu_percent = read_cpu_percent();
    out->cpu_cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    read_ram(&out->ram_used_mb, &out->ram_total_mb);
    read_network(&out->rx_bytes, &out->tx_bytes);
    read_root_disk(&out->disks[0]);
    out->disk_count = 1;
    return 0;
}

int system_stats_to_json(const system_stats_t *stats, char *buffer, int buffer_size) {
    int offset = snprintf(buffer, (size_t) buffer_size,
        "{\"cpuPercent\":%.2f,\"cpuCores\":%d,\"ramUsedMb\":%ld,\"ramTotalMb\":%ld,"
        "\"rxBytes\":%ld,\"txBytes\":%ld,\"disks\":[",
        stats->cpu_percent, stats->cpu_cores, stats->ram_used_mb, stats->ram_total_mb,
        stats->rx_bytes, stats->tx_bytes);

    for (int i = 0; i < stats->disk_count && offset < buffer_size; i++) {
        offset += snprintf(buffer + offset, (size_t) (buffer_size - offset),
            "%s{\"mount\":\"%s\",\"usedGb\":%.2f,\"totalGb\":%.2f}",
            i > 0 ? "," : "", stats->disks[i].mount, stats->disks[i].used_gb, stats->disks[i].total_gb);
    }
    if (offset < buffer_size) {
        offset += snprintf(buffer + offset, (size_t) (buffer_size - offset), "]}");
    }
    return offset;
}
