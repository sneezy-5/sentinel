#include "process_stats.h"

#include <ctype.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
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

static int is_all_digits(const char *s) {
    if (*s == '\0') {
        return 0;
    }
    for (const char *p = s; *p; p++) {
        if (!isdigit((unsigned char) *p)) {
            return 0;
        }
    }
    return 1;
}

/* Insertion-sorts candidate into out (descending by rss_kb), dropping the smallest once
 * full. PROCESS_STATS_TOP_N is small (5) - a shift-on-insert is simpler than qsort-ing
 * the whole /proc scan and plenty fast at this size. */
static void insert_top(top_processes_t *out, const process_stats_t *candidate) {
    if (out->count < PROCESS_STATS_TOP_N) {
        out->processes[out->count] = *candidate;
        out->count++;
    } else if (candidate->rss_kb <= out->processes[out->count - 1].rss_kb) {
        return;
    } else {
        out->processes[out->count - 1] = *candidate;
    }
    for (int i = out->count - 1; i > 0 && out->processes[i].rss_kb > out->processes[i - 1].rss_kb; i--) {
        process_stats_t tmp = out->processes[i];
        out->processes[i] = out->processes[i - 1];
        out->processes[i - 1] = tmp;
    }
}

int process_stats_top_by_rss(top_processes_t *out) {
    memset(out, 0, sizeof(*out));
    DIR *proc_dir = opendir("/proc");
    if (!proc_dir) {
        return -1;
    }
    struct dirent *entry;
    while ((entry = readdir(proc_dir)) != NULL) {
        if (!is_all_digits(entry->d_name)) {
            continue;
        }
        process_stats_t candidate;
        /* Process exited between readdir() and the /proc/{pid}/* reads, or isn't
         * readable - skip it, not a hard failure for the whole scan. */
        if (process_stats_collect(atoi(entry->d_name), &candidate) == 0) {
            insert_top(out, &candidate);
        }
    }
    closedir(proc_dir);
    return 0;
}
