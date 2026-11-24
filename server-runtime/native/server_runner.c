/* APK-installed headless JVM process; deliberately independent of ART/client JVM. */
#include <dlfcn.h>
#include <errno.h>
#include <malloc.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <unistd.h>
#include "jvm_layout.h"
#include "world_lock.h"

typedef int (*jli_launch_fn)(int, char **, int, const char **, int, const char **,
    const char *, const char *, const char *, const char *,
    unsigned char, unsigned char, unsigned char, int);

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    pid_t parent = getppid();
    /* If Android kills the supervisor, give Java its normal shutdown signal.
     * The world lock remains held until shutdown actually finishes. */
    if (parent == 1 || prctl(PR_SET_PDEATHSIG, SIGTERM) != 0 || getppid() != parent)
        return 70;
    if (getuid() == 0 || geteuid() == 0) return 71;
    if (!mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE)) {
        fputs("[native] Could not configure Android allocator for HotSpot\n", stderr);
        return 78;
    }
    if (scape_world_lock(getenv("SCAPE_WORLD_LOCK")) < 0) return 77;
    const char *home = getenv("JAVA_HOME");
    const char *jli_path = getenv("SCAPE_JLI_PATH");
    const char *jvm_path = getenv("SCAPE_JVM_PATH");
    if (!home || !jli_path || !jvm_path) return 72;
    if (!scape_jvm_layout_init(home, jvm_path)) return 76;
    void *jli = dlopen(jli_path, RTLD_NOW | RTLD_GLOBAL);
    if (!jli) { fprintf(stderr, "[native] %s\n", dlerror()); return 73; }
    jli_launch_fn launch = (jli_launch_fn)dlsym(jli, "JLI_Launch");
    if (!launch) return 74;
    printf("[native] Headless server pid=%u uid=%u\n", (unsigned)getpid(), (unsigned)getuid());
    argv[0] = "java";
    return launch(argc, argv, 0, NULL, 0, NULL, "17", "17", "java", "openjdk", 0, 1, 0, 0);
}
