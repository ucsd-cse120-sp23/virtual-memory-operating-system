package nachos.threads;

import java.sql.DatabaseMetaData;
import java.util.*;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) { //WE ARE ALLOWED TO USE DATA STRUCTURES, WE JUST CANNOT USE THEM TO STORE THREADS. I.e., a list of threads. 
	    lock.acquire(); //acquire lock before critical section
        while(tagExist(tag) == -1){ //loop here due to mesa semantic, details were explained during discussion
            ThreadData tData = new ThreadData(); //if there's no excahgne with tag, create one
            tData.tag = tag;                     //and set current thread to sleep.
            tData.value = value;
            dataList.add(tData); 
            tData.cv.sleep();
        } //if exits loop, that means an exchange was previously made
        int index = tagExist(tag); 
        int toReturn = dataList.get(index).value; //record the old value from the previous thread
        dataList.get(index).value = value; //set the new value
        boolean remove = dataList.get(index).toBeRemoved;
        if(remove){ // second time retriving value, remove it, exchange has been made
            dataList.remove(index);
        }else{ //first time retriving value, don't remove it, simply modify 
            dataList.get(index).toBeRemoved = true;
            dataList.get(index).cv.wake(); //wake up the previous thread 
        }
        lock.release(); //releasing lock after critical section
        return toReturn;
    }

    private int tagExist (int tag){ //helper function, check if tag exists
        for(int i = 0; i < dataList.size(); i++){
            if(dataList.get(i).tag == tag){
                return i;
            }
        }
        return -1;
    }

    private class ThreadData { //class used to store various information about an exchange
        private int tag; 
        private int value;
        private Condition2 cv = new Condition2(lock); //use CV as a reference to threads
        private boolean toBeRemoved = false;
        private ThreadData() {
        }
    }   

    private Lock lock = new Lock(); 
    private ArrayList<ThreadData> dataList = new ArrayList<ThreadData>(); // list of exchanges

    // test starts here --------------------------------------------------------------------
    // Place Rendezvous test code inside of the Rendezvous class.
    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");
    
        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
    }
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
    }
}
