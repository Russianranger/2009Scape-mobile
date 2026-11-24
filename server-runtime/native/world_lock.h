#ifndef SCAPE_WORLD_LOCK_H
#define SCAPE_WORLD_LOCK_H
/* POSIX record lock interoperates with Android FileChannel.tryLock(). */
int scape_world_lock(const char *path);
#endif
