#include "process_stats.h"

#include <stdio.h>
#include <string.h>

int process_stats_collect(int pid, process_stats_t *out) {
    memset(out, 0, sizeof(*out));
    out->pid = pid;

    char status_path[64];
    snprintf(status_path, sizeof(status_path), "/proc/%d/status", pid);
    FILE *status_file = fopen(status_path, "r");
    if (!status_file) {
        return -1;
    }
    char line[256];
    while (fgets(line, sizeof(line), status_file)) {
        if (strncmp(line, "Name:", 5) == 0) {
            sscanf(line + 5, "%255s", out->name);
        } else if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line + 6, "%ld", &out->rss_kb);
        }
    }
    fclose(status_file);

    char stat_path[64];
    snprintf(stat_path, sizeof(stat_path), "/proc/%d/stat", pid);
    FILE *stat_file = fopen(stat_path, "r");
    if (!stat_file) {
        return -1;
    }
    /* Fields 14 (utime) and 15 (stime) of /proc/{pid}/stat, in clock ticks. The process
     * name (field 2) is in parentheses and can contain spaces: skip to the closing
     * parenthesis to stay aligned. */
    char buffer[512];
    if (fgets(buffer, sizeof(buffer), stat_file)) {
        char *after_name = strrchr(buffer, ')');
        if (after_name) {
            sscanf(after_name + 1,
                   " %*c %*d %*d %*d %*d %*d %*u %*u %*u %*u %*u %lu %lu",
                   &out->utime_ticks, &out->stime_ticks);
        }
    }
    fclose(stat_file);
    return 0;
}
