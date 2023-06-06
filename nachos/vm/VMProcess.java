package nachos.vm;

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
			if (numPages > UserKernel.getNumFreePages()) {
				coff.close();
				Lib.debug(dbgProcess, "\tinsufficient physical memory");
				return false;
			}
	
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
		super.unloadSections();
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
					int ppn = UserKernel.getNextFreePage();
					pageTable[i].vpn = i;
					pageTable[i].ppn = ppn;
					loadPage(va);
					break;
			default:
				super.handleException(cause);
				break;
		}
	}

	protected boolean loadPage(int va) {
		Lock lock = new Lock();
		lock.acquire();
		if (numPages > UserKernel.getNumFreePages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		Processor processor = Machine.processor();
		int vpn = Processor.pageFromAddress(va); // get vpn from va

		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				//check if vpn is inside section.getFirstVPN() + i, if it is inside means data segment and continue
				//otherwise zerofill it. Create a byte with size of a page and zero-fill it, then load it into the memory
				//we get from Processor.getMemory()
				//return

				if (vpn == section.getFirstVPN() + i) {
					int ppn = pageTable[vpn].ppn;
					boolean isReadOnly = section.isReadOnly();
					pageTable[vpn].readOnly = isReadOnly;
					pageTable[vpn].valid = true;
					pageTable[vpn].used = true;
					pageTable[vpn].dirty = false;
					section.loadPage(i, ppn);
					lock.release();
					return true;
				}
			}
		}
		
		byte memory[] = processor.getMemory();
		byte array[] = new byte[pageSize];

		int ppn = pageTable[vpn].ppn;
		int paddr = ppn * pageSize;
		pageTable[vpn].readOnly = false;
		pageTable[vpn].used = true;
		pageTable[vpn].valid = true;
		pageTable[vpn].dirty = false;
		System.arraycopy(array, 0, memory, paddr, pageSize);
		lock.release();
		return true;

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
			int offse = Processor.offsetFromAddress(vaddr);
			if(pageTable[vpn].valid == false){
				this.loadPage(Processor.makeAddress(vpn, offse));
			}
			int vaoffset = Processor.offsetFromAddress(vaddr);
			int ppn = pageTable[vpn].ppn;
			int paddr = pageSize * ppn + vaoffset;
	
			//if(remaining >= pageSize)
				//curPageCount = pageSize;
			//else
				//curPageCount = remaining;
			int spaceAvail = Math.min(length, pageSize - vaoffset);
			int leftToRead = Math.min(spaceAvail, remaining);
			remaining -= leftToRead;
			System.arraycopy(memory, paddr, data, offset, leftToRead);
			totalRead += leftToRead;
			vaddr += leftToRead;
			offset += leftToRead;

			if (vaddr < 0 || vaddr >= maxVA)
				return totalRead;
		}
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
			int offse = Processor.offsetFromAddress(vaddr);
			if(pageTable[vpn].valid == false){
				this.loadPage(Processor.makeAddress(vpn, offse));
			}
			int vaoffset = Processor.offsetFromAddress(vaddr);
			int ppn = pageTable[vpn].ppn;
			int paddr = pageSize * ppn + vaoffset;

			int spaceAvail = Math.min(length, pageSize - vaoffset);
			int leftToWrite = Math.min(spaceAvail, remaining);
			remaining -= leftToWrite;
			System.arraycopy(data, offset, memory, paddr, leftToWrite);	
			totalWrote += leftToWrite;
			offset += leftToWrite;
			vaddr += leftToWrite;
			
			if (vaddr < 0 || vaddr >= maxVA)
				return totalWrote;
		}
		return totalWrote;
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
