/*
 * join1.c
 *
 * Simple program for testing join.  After exec-ing the child, it
 * waits for the child to exit.
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "exec1.coff";
    int pid, r, status = 0;

    printf ("execing %s...\n", prog);
    pid = exec (prog, 0, 0);
    if (pid > 0) {
	printf ("...passed\n");
    } else {
	printf ("...failed (pid = %d)\n", pid); 
	exit (-1);
    }

    printf ("joining %d...\n", pid);
    r = join (pid, &status);
    if (r > 0) {
	printf ("...passed (status from child = %d)\n", status);
    } else if (r == 0) {
	printf ("...child exited with unhandled exception\n");
	exit (-1);
    } else {
	printf ("...failed (r = %d)\n", r);
	exit (-1);
    }

    // the return value from main is used as the status to exit
    return 0;
}