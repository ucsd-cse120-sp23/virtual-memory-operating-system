package nachos.vm;

import java.lang.management.MemoryNotificationInfo;
import java.util.ArrayList;

import javax.print.attribute.standard.PageRanges;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		cv = new Condition(cvLock);
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		//return super.loadSections();
			Lock lock = new Lock();
			lock.acquire();
	
			pageTable = new TranslationEntry[numPages];
			for (int i = 0; i < numPages; i++) {

				//int ppn = UserKernel.getNextFreePage();
				pageTable[i] = new TranslationEntry(-1, -1, false, false, false, false);

			}
			lock.release();
			return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		printPageTable();
		printIPT();
		System.out.println("The number of times swap is APPENDED " + numOfSwap);
		System.out.println("The number of times swap is ACCESSED " + numOfSwapAcess);

		Lock lock = new Lock();
		lock.acquire();
		cvLock.acquire();

		//printPageTable();

		for (int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].valid == true) {
				UserKernel.addFreePage(pageTable[i].ppn);
				int idx = findIdexOfPPN(pageTable[i].ppn);
				IPT.remove(idx);
				cv.wake();
			}
		}
		lock.release();
		cvLock.release();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		//cause = 1 so page fault occurs
		switch (cause) {
			case 1: int va = processor.readRegister(Processor.regBadVAddr);	//virtual address of the exception register
					//int ppn = VMKernel.getNextFreePage();
					int vpn = Processor.pageFromAddress(va);
					pageTable[vpn].vpn = vpn;
					//pageTable[vpn].ppn = ppn;
					loadPage(va);
					break;
			default:
				super.handleException(cause);
				break;
		}
	}

	protected int evictionClock(Lock lock){
		//cvLock.acquire();
		int freePages = VMKernel.getNumFreePages();
		if(freePages != 0){
			//cvLock.release();
			return -1;
		} else{
			int counter = IPT.size();
			int i = 0;
			while(true){
				while(i < counter){
					int index = IPT.get(evictPage).index;
					VMProcess curProc = IPT.get(evictPage).process;
					int ppn = curProc.pageTable[index].ppn;
					Boolean used = curProc.pageTable[index].used;
					Boolean pinned = IPT.get(evictPage).pinned;
					if(used && !pinned)
						curProc.pageTable[index].used = false;
					else if (!used && !pinned){
						curProc.pageTable[index].valid = false;
						Boolean dirty = curProc.pageTable[index].dirty;
						if(dirty){
							numOfSwapAcess++;
							byte[] memory = Machine.processor().getMemory();
							byte[] buffer = new byte[pageSize];
							System.arraycopy(memory, ppn * pageSize, buffer, 0, pageSize);
							if(freeSwapList.size() != 0){ //if there's a gap in swap file, we replace instead of append
								int free = freeSwapList.remove(0);
								VMKernel.swapFile.write(free* pageSize , buffer, 0, pageSize); //overrite 
								curProc.pageTable[index].vpn = free; 
							} else{
								int free = numOfSwap; //should really be endOfSwap namewise.... 
								numOfSwap++;
								VMKernel.swapFile.write(free* pageSize , buffer, 0, pageSize); //append
								curProc.pageTable[index].vpn = free; 
							}
						}
						evictPage = (evictPage + 1) % IPT.size();
						//cvLock.release();
						return ppn;
					}
					i++;
					evictPage = (evictPage + 1) % IPT.size();
				}
				i = 0;
				
				lock.release();
				//printPageTable();
				//System.out.println(VMKernel.getNumFreePages() + "-----------------------------------");
				if (allPinned())
					cv.sleep();
				lock.acquire();
			}
		}
	}

	protected boolean loadPage(int va) {
		Lock lock = new Lock();
		lock.acquire();

		int removedPPN = evictionClock(lock);
		//cvLock.release();

		// load sections
		Processor processor = Machine.processor();
		int vpn = Processor.pageFromAddress(va); // get vpn from va
	
		int ppn;
		
		if (removedPPN == -1){ //free pages available, just get it.
			ppn = VMKernel.getNextFreePage();
		}
		else
			ppn = removedPPN; //this page has been evicted and saved, now can be overwritten

		boolean IPTFull = false;

		if (removedPPN == -1) {
			IPTFull = false;
		} else
			IPTFull = true;


		boolean dirty = pageTable[vpn].dirty;

		byte[] memory = Machine.processor().getMemory();

		if(dirty) { //read from swapfile
			byte [] buffer = new byte[pageSize];
			VMKernel.swapFile.read(pageTable[vpn].vpn * pageSize , buffer, 0, pageSize);
			System.arraycopy(buffer, 0, memory, ppn*pageSize, pageSize); //load to physical memory
			pageTable[vpn].valid = true;
			pageTable[vpn].used = true;
			pageTable[vpn].ppn = ppn;
			int idx = findIdexOfPPN(ppn);
			IPT.get(idx).ppn = ppn;
			IPT.get(idx).index = vpn; //index, actual vpn
			IPT.get(idx).process = this;
			IPT.get(idx).pinned = false; //just loaded, shouldn't be pinned
			lock.release();
			return true;
			//pagetable[vpn].vpn which is really the spn, is useless, old spn might be taken away
		} else {
			for (int s = 0; s < coff.getNumSections(); s++) {
				
				CoffSection section = coff.getSection(s);

				for (int i = 0; i < section.getLength(); i++) {
					//check if vpn is inside section.getFirstVPN() + i, if it is inside means data segment and continue
					//otherwise zerofill it. Create a byte with size of a page and zero-fill it, then load it into the memory
					//we get from Processor.getMemory()
					//return
					if (vpn == section.getFirstVPN() + i) {
						boolean isReadOnly = section.isReadOnly();
						pageTable[vpn].readOnly = isReadOnly;
						pageTable[vpn].valid = true;
						pageTable[vpn].used = true;
						pageTable[vpn].dirty = false;
						pageTable[vpn].ppn = ppn; //updates ppn
						section.loadPage(i, ppn);
						
						if (IPTFull) {
							int idx = findIdexOfPPN(ppn);
							IPT.get(idx).ppn = ppn;
							IPT.get(idx).index = vpn; //index, actual vpn
							IPT.get(idx).process = this;
							IPT.get(idx).pinned = false; //just loaded, shouldn't be pinned
						}else{ //ipt not full
							IPTdata data = new IPTdata(ppn, vpn, false, this);
							IPT.add(data);
							//here
						}
						lock.release();
						return true;
					}
				}
			}
			
			byte array[] = new byte[pageSize];

			int paddr = ppn * pageSize;
			pageTable[vpn].readOnly = false;
			pageTable[vpn].used = true;
			pageTable[vpn].valid = true;
			pageTable[vpn].dirty = false;
			pageTable[vpn].ppn = ppn;
			System.arraycopy(array, 0, memory, paddr, pageSize);
			
			if (IPTFull) {
				int idx = findIdexOfPPN(ppn);
				IPT.get(idx).ppn = ppn;
				IPT.get(idx).index = vpn; //index, actual vpn
				IPT.get(idx).process = this;
				IPT.get(idx).pinned = false; //just loaded, shouldn't be pinned
			}else{ //ipt not full
				IPTdata data = new IPTdata(ppn, vpn, false, this);
				IPT.add(data);
			}

			lock.release();
			return true;
		}
	}

		/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		cvLock.acquire();
		byte[] memory = Machine.processor().getMemory();

		// Get VPN
		int maxVA = numPages * (pageSize + 1) - 1;
		if (vaddr < 0 || vaddr >= maxVA)
			return 0;

		int remaining = length;
		int curPageCount = 0;
		int totalRead = 0;

		while(remaining > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int ppn = pageTable[vpn].ppn;
			int idx = findIdexOfPPN(ppn);
			if (idx != -1)
				IPT.get(idx).pinned = true;
			int offse = Processor.offsetFromAddress(vaddr);
			if(pageTable[vpn].valid == false){
				this.loadPage(Processor.makeAddress(vpn, offse)); // same as just using vaddr
				ppn = pageTable[vpn].ppn;
				idx = findIdexOfPPN(ppn);
				IPT.get(idx).pinned = true;
			}

			int vaoffset = Processor.offsetFromAddress(vaddr);
			int paddr = pageSize * ppn + vaoffset;
	
			//if(remaining >= pageSize)
				//curPageCount = pageSize;
			//else
				//curPageCount = remaining;
			int spaceAvail = Math.min(length, pageSize - vaoffset);
			int leftToRead = Math.min(spaceAvail, remaining);
			remaining -= leftToRead;
			
			System.arraycopy(memory, paddr, data, offset, leftToRead);
			IPT.get(idx).pinned = false;
			cv.wake();
			totalRead += leftToRead;
			vaddr += leftToRead;
			offset += leftToRead;

			if (vaddr < 0 || vaddr >= maxVA){
				cvLock.release();
				return totalRead;
			}
		}

		cvLock.release();
		return totalRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		cvLock.acquire();
		byte[] memory = Machine.processor().getMemory();

		// Get VPN

		int maxVA = numPages * (pageSize + 1) - 1;
		if (vaddr < 0 || vaddr >= maxVA)
			return 0;

		int remaining = length;
		int curPageCount = 0;
		int totalWrote = 0;

		
		while(remaining > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int ppn = pageTable[vpn].ppn;
			int idx = findIdexOfPPN(ppn);
			if (idx != -1)
				IPT.get(idx).pinned = true;
			int offse = Processor.offsetFromAddress(vaddr);
			if(pageTable[vpn].valid == false){
				this.loadPage(Processor.makeAddress(vpn, offse));
				ppn = pageTable[vpn].ppn;
				idx = findIdexOfPPN(ppn);
				IPT.get(idx).pinned = true;
			}
			int vaoffset = Processor.offsetFromAddress(vaddr);
			
			int paddr = pageSize * ppn + vaoffset;

			int spaceAvail = Math.min(length, pageSize - vaoffset);
			int leftToWrite = Math.min(spaceAvail, remaining);
			remaining -= leftToWrite;
			
			System.arraycopy(data, offset, memory, paddr, leftToWrite);
			IPT.get(idx).pinned = false;
			cv.wake();
			totalWrote += leftToWrite;
			offset += leftToWrite;
			vaddr += leftToWrite;
			
			if (vaddr < 0 || vaddr >= maxVA) {
				cvLock.release();
				return totalWrote;
			}
		}
		cvLock.release();
		return totalWrote;
	}

	private int findIdexOfPPN( int ppn){
		for(int i = 0; i < IPT.size(); i++){
			if(IPT.get(i).ppn == ppn)
				return i;
		}
		return -1;
	}

	private class IPTdata{
		IPTdata(int p, int i, boolean pin, VMProcess proc){
			ppn = p;
			index = i;
			process = proc;
			pinned = pin;
		}
		int ppn, index;
		VMProcess process;
		boolean pinned;
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static ArrayList<Integer> freeSwapList = new ArrayList<Integer>();
	
	private static int evictPage = 0;

	private static ArrayList<IPTdata> IPT = new ArrayList<IPTdata>();

	private static int numOfSwap = 0;

	private static int numOfSwapAcess = 0;

	private Condition cv;

	private Lock cvLock = new Lock();

	public void printPageTable(){
		for(int i = 0; i < pageTable.length; i++){
			System.out.println("index/vpn: " + i + " vpn/spn: " + pageTable[i].vpn +  " ppn: " +  pageTable[i].ppn 
			+ " used: " + pageTable[i].used + " valid: " + pageTable[i].valid 
			+ " dirty: " + pageTable[i].dirty + " readOnly " + pageTable[i].readOnly);
		}
	}

	public boolean allPinned(){
		for(int i = 0; i < IPT.size(); i++){
			if(IPT.get(i).pinned == false)
				return false;
		}
		return true;
	}

	public void printIPT(){
		for(int i = 0; i < IPT.size(); i++){
			System.out.println("index/vpn: " + IPT.get(i).index + " ppn: " + IPT.get(i).ppn 
			+ " process: " + IPT.get(i).process + " pinned " + IPT.get(i).pinned);
		}
	}
}
