package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.print.attribute.standard.PagesPerMinute;
import javax.print.event.PrintJobAttributeEvent;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// OpenFileList initialize to null
		OpenFileList.add(UserKernel.console.openForReading());
		OpenFileList.add(UserKernel.console.openForWriting());
		// OpenFileList.add(UserKernel.console.openForReading());
		for (int i = 2; i < 16; i++) {
			OpenFileList.add(null);
		}
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);
		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
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
		//System.out.println("HERE" + remaining);
		int curPageCount = 0;
		int totalRead = 0;
		while(remaining > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int vaoffset = Processor.offsetFromAddress(vaddr);
			int ppn = pageTable[vpn].ppn;
			//System.out.println(ppn);
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
			//System.out.println("read: " + totalRead);
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

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		Lock lock = new Lock();
		lock.acquire();
		if (numPages > UserKernel.getNumFreePages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		// load sections
		int vpn = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				vpn = section.getFirstVPN() + i;
				// for now, just assume virtual addresses=physical addresses
				int ppn = UserKernel.getNextFreePage();
				section.loadPage(i, ppn);
				boolean isReadOnly = section.isReadOnly();

				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, isReadOnly, false, false); // maybe???????
			}
		}
		for (int i = 0; i < 9; i++) {
			vpn = vpn + 1;
			int ppn = UserKernel.getNextFreePage();
			pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		}
		lock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		Lock lock = new Lock();
		lock.acquire();
		System.out.println("BLAKBLACKASKCASKCASCA");
		for (int i = 0; i < pageTable.length; i++) {
			UserKernel.addFreePage(pageTable[i].ppn);
			
		}
		lock.release();
		System.out.println(UserKernel.freePPNs);
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (this.currProcessID == 0)
			Machine.halt();
		else
			return -1;

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private void handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process

		//Terminate the thread		
		numProcess--;

		// Close all Files in file table
		for (int i = 0; i < OpenFileList.size(); i++) {
			handleClose(i);
		}
		// Release all memory return physical pages to the UserKernel
		unloadSections();
		// Close the coff sections
		coff.close();
		// If it has a parent process -> save child's exit status in parent
		if (parent != null) {
			parent.exitStatus.put(this.currProcessID, status);
		}
		
		if (numProcess == 0)
			Kernel.kernel.terminate();

		this.thread.finish();
	}

	private int handleCreate(int vaname) {
		// check table size, must be less than 16
		// if (OpenFileList.size() == 16)
		// return -1;

		// get the string
		String filename = readVirtualMemoryString(vaname, 256);
		// check whether string is null
		if (filename == null || filename == "")
			return -1;

		// creates a new file
		OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
		// checks whether string consists of unprintable characters
		if (file == null)
			return -1;

		for (int i = 0; i < 16; i++) {
			if (OpenFileList.get(i) == null) {
				OpenFileList.set(i, file);
				return i;
			}
		}
		return -1;
	}

	private int handleOpen(int vaname) {
		// check table size, must be less than 16
		// if (OpenFileList.size() == 16)
		// return -1;

		// get the string
		String filename = readVirtualMemoryString(vaname, 256);
		// check whether string is null
		if (filename == null || filename == "")
			return -1;

		// creates a new file
		OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
		// checks whether string consists of unprintable characters
		if (file == null)
			return -1;

		for (int i = 0; i < 16; i++) {
			if (OpenFileList.get(i) == null) {
				OpenFileList.set(i, file);
				return i;
			}
		}
		return -1;
	}

	private int handleClose(int descriptor) {
		if (descriptor > 15 || descriptor < 0)
			return -1;
		if (OpenFileList.get(descriptor) == null)
			return -1;
		OpenFileList.get(descriptor).close();
		OpenFileList.set(descriptor, null);
		return 0;
	}

	private int handleRead(int descriptor, int buf, int count) {
		if (descriptor > 15 || descriptor < 0)
			return -1;
		OpenFile file = OpenFileList.get(descriptor);
		if (file == null)
			return -1;

		if (count < 0)
			return -1;

		if (buf < 0 || buf >= numPages * pageSize)
			return -1;

		byte buffer[] = new byte[pageSize];
		int remaining = count;
		int curPageCount;
		int totalBytesRead = 0;
		int bytesRead = 0;
		while (remaining > 0) {
			if (remaining > pageSize)
				curPageCount = pageSize;
			else
				curPageCount = remaining;
			remaining -= pageSize;
			bytesRead = file.read(buffer, 0, curPageCount);
			if(bytesRead==-1) 
				return -1;
			boolean isReadOnly = pageTable[buf/pageSize].readOnly;
			if(isReadOnly)
				return -1;
			int bytesWrote = writeVirtualMemory(buf, buffer, 0, bytesRead);			
			if (bytesWrote == -1 || bytesWrote < bytesRead)
				return -1;

			totalBytesRead += bytesRead;
			buf += bytesWrote;
		}
		return totalBytesRead;
	}

	private int handleWrite(int descriptor, int buf, int count) {
		//System.out.println("asdiasdhkasdasdj");
		if (descriptor > 15 || descriptor < 0)
			return -1;
		OpenFile file = OpenFileList.get(descriptor);
		if (file == null)
			return -1;

		if (count < 0)
			return -1;

		if (buf < 0 || buf >= numPages * pageSize)
			return -1;

		byte buffer[] = new byte[pageSize];
		int remaining = count;
		int curPageCount;
		int totalBytesWrote = 0;
		int bytesWrote = 0;
		while (remaining > 0) {
			if (remaining >= pageSize)
				curPageCount = pageSize;
			else
				curPageCount = remaining;
			int bytesRead = readVirtualMemory(buf, buffer, 0, curPageCount);
			remaining -= bytesRead;
			bytesWrote = file.write(buffer, 0, bytesRead);
			if (bytesWrote == -1 || bytesWrote < bytesRead)
				return -1;
			totalBytesWrote += bytesWrote;
			if ((buf + totalBytesWrote) >= numPages * pageSize)
				return -1;
			buf += bytesRead;
		}
		return totalBytesWrote;
	}

	private int handleUnlink(int vaname) {
		// get the string
		String filename = readVirtualMemoryString(vaname, 256);
		// check whether string is null
		if (filename == null || filename == "")
			return -1;

		Boolean onSuccess = ThreadedKernel.fileSystem.remove(filename);
		if (onSuccess)
			return 0;
		return -1;
	}

	private int handleExec(int file, int argc, int argv) {
		String filename = readVirtualMemoryString(file, 256);
		// check if filename is a valid file with .coff extension
		if (filename == null || !filename.contains(".coff")) {
			return -1;
		}

		if (argc < 0)
			return -1;

		byte[] argumentList = new byte[4 * argc];
		String[] arguments = new String[argc];
		for (int i = 0; i < argc; i++) {
			int bytesRead = readVirtualMemory(argv + i * 4, argumentList, i * 4, 4);
			String argumentEntry = readVirtualMemoryString(Lib.bytesToInt(argumentList, i * 4), 256);
			arguments[i] = argumentEntry;
		}

		UserProcess childProcess = UserProcess.newUserProcess();
		children.put(processID, childProcess);
		childProcess.currProcessID = processID;
		childProcess.parent = this;
		numProcess++;
		processID++;
		if (childProcess.execute(filename, arguments))
			return processID - 1;
		else 
			return -1;
	}

	private int handleJoin(int pid, int saddr){
		if(!children.containsKey(pid))
			return -1;
		int maxVA = numPages * pageSize;
		if(saddr < 0 || saddr >= maxVA)
			return -1;
		UserProcess childProcess = children.get(pid);
		childProcess.thread.join();
		children.remove(pid);
		if(!exitStatus.containsKey(pid) || exitStatus.get(pid) == -1)
			return 0;
		int childStatus = exitStatus.get(pid);
		exitStatus.remove(pid);
		String stringStatus = "" + childStatus;
		byte [] buffer = stringStatus.getBytes();
		writeVirtualMemory(saddr, buffer);
		return 1;
	}
	

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				handleExit(a0);
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallClose:
				return handleClose(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
			default:
				handleExit(-1);
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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

		switch (cause) {
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				//Lib.debug(dbgProcess, "Unexpected exception: "
					//	+ Processor.exceptionNames[cause]);
				//Lib.assertNotReached("Unexpected exception");
				handleExit(-1);
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private ArrayList<OpenFile> OpenFileList = new ArrayList<OpenFile>();

	private int openCount = 0;

	static int processID = 0;
	protected int currProcessID;

	private UserProcess parent = null;

	static int numProcess = 0;

	private HashMap<Integer, Integer> exitStatus = new HashMap<Integer, Integer>();

	HashMap<Integer, UserProcess> children = new HashMap<Integer, UserProcess>();
}
