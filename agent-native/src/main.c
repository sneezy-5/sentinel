#include "system_stats.h"

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>

/* Must stay in sync with the path read by NativeStatsClient (agent module, Java). */
#define STATS_FILE "/run/sentinel/system_stats.json"
#define COLLECT_INTERVAL_SECONDS 10

/* Writes to a temp file then rename(): avoids a Java-side reader hitting a truncated
 * JSON mid-write (rename is atomic on the same filesystem). */
static void write_atomic(const char *path, const char *content, int length) {
    char tmp_path[256];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", path);

    FILE *f = fopen(tmp_path, "w");
    if (!f) {
        return;
    }
    fwrite(content, 1, (size_t) length, f);
    fclose(f);
    rename(tmp_path, path);
}

int main(void) {
    mkdir("/run/sentinel", 0755);

    char json_buffer[4096];
    while (1) {
        system_stats_t stats;
        system_stats_collect(&stats);
        int length = system_stats_to_json(&stats, json_buffer, sizeof(json_buffer));
        write_atomic(STATS_FILE, json_buffer, length);
        sleep(COLLECT_INTERVAL_SECONDS);
    }
    return 0;
}
