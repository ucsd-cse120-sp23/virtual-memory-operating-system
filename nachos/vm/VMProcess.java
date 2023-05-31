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
				pageTable[i].vpn = -1;
				pageTable[i].ppn = -1;
				pageTable[i].valid = false;
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
		int va = -1;
		switch (cause) {
			case 1: va = processor.readRegister(Processor.regBadVAddr);	//virtual address of the exception register
					loadPage(va);
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
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				//check if vpn is inside section.getFirstVPN() + i, if it is inside means data segment and continue
				//otherwise zerofill it. Create a byte with size of a page and zero-fill it, then load it into the memory
				//we get from Processor.getMemory()
				//return
				int vpn = Processor.pageFromAddress(va); // get vpn from va
				if (vpn == section.getFirstVPN() + i) {
					int ppn = UserKernel.getNextFreePage();
					section.loadPage(i, ppn);
					boolean isReadOnly = section.isReadOnly();

					pageTable[vpn] = new TranslationEntry(vpn, ppn, false, isReadOnly, false, false);
					return true;
				} else {
					byte array[] = new byte[pageSize];
					byte memory[] = processor.getMemory();
					System.arraycopy(array, 0, memory, 0, pageSize);
				}

			}
		}
		lock.release();
		return true;

	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
