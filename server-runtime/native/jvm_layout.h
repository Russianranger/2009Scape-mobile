#ifndef SCAPE_JVM_LAYOUT_H
#define SCAPE_JVM_LAYOUT_H

/* Call once in the standalone runner, before loading JLI or starting threads. */
int scape_jvm_layout_init(const char *home, const char *installed_jvm);

#endif
