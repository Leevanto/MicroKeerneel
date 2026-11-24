// Why a user process is (or isn't) able to run.
// Sent to the kernel as the "reason" in a RESCHEDULE message,
// and stored by the kernel in the process's PCB.
enum processstate {
    RUNNABLE,         // can be scheduled any time
    INBOX_EMPTY,      // waiting for a message to arrive
    OUTBOX_FULL,      // waiting for room in its outbox
    QUANTUM_EXPIRED,  // used up its time slice; can run again
    FINISHED          // main() returned; never schedule again
}