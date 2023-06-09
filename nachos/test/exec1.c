/*
 * exec1.c
 *
 * Simple program for testing exec.  It does not pass any arguments to
 * the child.
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "swap4.coff";
    char *prog1 = "swap5.coff";
    char *prog2 = "write1.coff";
    char *prog3 = "write4.coff";
    char *prog4 = "write10.coff";
    int pid;
    int pid1;
    int pid2;
    int pid3;
    int pid4;
    pid = exec (prog, 0, 0);
    pid1 = exec (prog1, 0, 0);
    pid2 = exec (prog2, 0, 0);
    pid3 = exec (prog3, 0, 0);
    pid4 = exec (prog4, 0, 0);
    // the exit status of this process is the pid of the child process
    exit (pid);
    exit (pid1);
    exit (pid2);
    exit (pid3);
    exit (pid4);
}