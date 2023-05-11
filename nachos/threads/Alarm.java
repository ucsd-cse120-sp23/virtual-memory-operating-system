package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private ArrayList<KThread> blockedList = new ArrayList<KThread>();
	private ArrayList<Long> waketimeList = new ArrayList<Long>();

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean status = Machine.interrupt().disable(); //this should be a critical section
		int index = 0;									//no interrupt within interrupt
		long currentTime = Machine.timer().getTime(); 	//get the current time
		while(index < blockedList.size()){				//loop through all the thread in blockedList
			if(currentTime >= waketimeList.get(index)){
				blockedList.get(index).ready();			//make any valid thread ready
				blockedList.remove(index);
				waketimeList.remove(index);
			} else
				index++;
		}
		Machine.interrupt().restore(status);			//enable interrupt before yielding 

		KThread.yield();								//yield because timer interrupt should always try to switch running threads
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		if(x < 0){
			return;
		}
		long wakeTime = Machine.timer().getTime() + x; //get wakeTime
		blockedList.add(KThread.currentThread()); //add the current thread to the blocked list
		waketimeList.add(wakeTime); 
		Machine.interrupt().disable(); //disable interrupt before sleeping
		KThread.sleep(); //sleep is in critical section?
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean status = Machine.interrupt().disable();
		int ind = blockedList.indexOf(thread);
		if (ind == -1) {
			Machine.interrupt().restore(status);	
			return false;
		}
		else {
			blockedList.get(ind).ready();
			blockedList.remove(ind);
			waketimeList.remove(ind);
			Machine.interrupt().restore(status);	
			return true;
		}
	}

	// Add Alarm testing code to the Alarm class

	public static void alarmTest1() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Implement more test methods here ...

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();

		// Invoke your other test methods here ...
	}

}
