package storage;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

import common.*;
import rmi.*;
import naming.*;

/** Storage server.

    <p>
    Storage servers respond to client file access requests. The files accessible
    through a storage server are those accessible under a given directory of the
    local filesystem.
 */
public class StorageServer implements Storage, Command
{
	// private param
	private File my_root;			// local file system root directory
	private Path my_root_path;		// local file root path obj
	private String my_root_string;	// String dir for local root
	private int my_client_port;		// 
	private int my_command_port;	//
	private boolean start;
	private boolean running;
	private Skeleton<Command> SS_cmd_Sklt;
	private Skeleton<Storage> SS_Strg_Sklt;
	//
	/** Creates a storage server, given a directory on the local filesystem, and
        ports to use for the client and command interfaces.

        <p>
        The ports may have to be specified if the storage server is running
        behind a firewall, and specific ports are open.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @param client_port Port to use for the client interface, or zero if the
                           system should decide the port.
        @param command_port Port to use for the command interface, or zero if
                            the system should decide the port.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
	 */
	public StorageServer(File root, int client_port, int command_port)
	{
		if(root == null) throw new NullPointerException();
		this.my_root = root;
		this.my_root_path = new Path();
		this.my_root_string = root.getAbsolutePath().replace('\\', '/'); // In case of Windows OS...
		this.my_client_port = client_port;
		this.my_command_port = command_port;
		this.start = false;
		this.running = false;
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Creats a storage server, given a directory on the local filesystem.

        <p>
        This constructor is equivalent to
        <code>StorageServer(root, 0, 0)</code>. The system picks the ports on
        which the interfaces are made available.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
	 */
	public StorageServer(File root)
	{
		if(root == null) throw new NullPointerException();
		this.my_root = root;
		this.my_root_path = new Path();
		this.my_root_string = root.getAbsolutePath().replace('\\', '/'); // In case of Windows OS...
		// TBD!!
		this.my_client_port = 8080;
		this.my_command_port = 8081;
		this.start = false;
		this.running = false;
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Starts the storage server and registers it with the given naming
        server.

        @param hostname The externally-routable hostname of the local host on
                        which the storage server is running. This is used to
                        ensure that the stub which is provided to the naming
                        server by the <code>start</code> method carries the
                        externally visible hostname or address of this storage
                        server.
        @param naming_server Remote interface for the naming server with which
                             the storage server is to register.
        @throws UnknownHostException If a stub cannot be created for the storage
                                     server because a valid address has not been
                                     assigned.
        @throws FileNotFoundException If the directory with which the server was
                                      created does not exist or is in fact a
                                      file.
        @throws RMIException If the storage server cannot be started, or if it
                             cannot be registered.
	 */
	public synchronized void start(String hostname, Registration naming_server) throws RMIException, UnknownHostException, FileNotFoundException
	{
		if(this.start == true) return;
		// Step 1: generate 2 skeletons and their addr, 1 for client, 1 for internal command
		// Command Interface Skeleton addr & obj
		InetSocketAddress SS_cmd_Sklt_addr = new InetSocketAddress(hostname, this.my_command_port);
		this.SS_cmd_Sklt = new Skeleton<Command>(Command.class, this, SS_cmd_Sklt_addr);
		// Storage Interface Skeleton addr & obj
		InetSocketAddress SS_Strg_Sklt_addr = new InetSocketAddress(hostname, this.my_client_port);
		this.SS_Strg_Sklt = new Skeleton<Storage>(Storage.class, this, SS_Strg_Sklt_addr);
		// Step 2: use the stub to access Naming Server Registration interface
		// Get existing files (non-directory) on the local root and convert them to relative Path obj
		Path[] files = Path.list(this.my_root);
		// Get client stub & command_stub for naming server
		Command SS_cmd_Stb = Stub.create(Command.class, SS_cmd_Sklt);
		Storage SS_Strg_Stb = Stub.create(Storage.class, SS_Strg_Sklt);
		// Registration and get del files
		Path[] del = naming_server.register(SS_Strg_Stb, SS_cmd_Stb, files);
		// Delete those files on the local file system
		for(int i = 0; i < del.length; i++)
		{
			delete(del[i]);
		}
		// Delete empty directory
		delEmpDir(this.my_root);
		// Step 3: start all the skeleton
		this.start = true;
		this.running = true;
		this.SS_cmd_Sklt.start();
		this.SS_Strg_Sklt.start();
		//throw new UnsupportedOperationException("not implemented");
	}
	
	// helper function for deleting directory (assuming non-empty)
	protected boolean delDir(Path src)
	{
		if(src == null || src.isRoot())
		{
			System.out.println("Illegal input para!");
			return false;
		}
		//
		File f_obj = src.toFile(this.my_root);
		if(!f_obj.isDirectory())
		{
			System.out.println("Not a dir!");
			return false;
		}
		else if(!f_obj.exists())
		{
			System.out.println("Not exist!");
			return false;
		}
		else
		{
			// Iteratively delete dir
			Queue<File> level = new LinkedList<File>();
			Stack<File> tool = new Stack<File>();
			level.offer(f_obj);
			// Step 1: go and find all the directory (level traverse)
			while(!level.isEmpty())
			{
				File temp = level.peek();
				File[] sub = temp.listFiles();
				int length = sub.length;
				for(int i = 0; i < length; i++)
				{
					if(sub[i].isFile())
					{
						if(!sub[i].delete()) return false; // delete files
					}
					else level.offer(sub[i]); // go deep into sub-dir
				}
				//
				tool.push(temp);
				level.poll();
			}
			// Step 2: delete dir (now they are all empty)
			while(!tool.empty())
			{
				File temp = tool.peek();
				if(!temp.delete()) return false;
				tool.pop();
			}
			//
			return true;
		}
	}
	
	// helper function for clearing empty directory
	protected void delEmpDir(File root)
	{
		if(root == null) return;
		//
		if(root.exists())
		{
			Queue<File> level = new LinkedList<File>();
			Stack<File> tool = new Stack<File>();
			level.offer(root);
			// Step 1: go and find all the directory (level traverse)
			while(!level.isEmpty())
			{
				File temp = level.peek();
				File[] children = temp.listFiles();
				int length = children.length;
				for(int i = 0; i < length; i++)
				{
					if(children[i].isDirectory()) level.offer(children[i]);
				}
				//
				tool.push(temp);
				level.poll();
			}
			// Step 2: delete empty
			while(!tool.isEmpty())
			{
				File temp = tool.peek();
				if(temp.listFiles().length == 0) temp.delete(); // this is an empty directory
				//
				tool.pop();
			}
			//
			return;
		}
		else
		{
			System.out.println("This directory does not exist!");
			return;
		}
	}

	/** Stops the storage server.

        <p>
        The server should not be restarted.
	 */
	public void stop()
	{
		if(this.running == true)
		{
			this.SS_cmd_Sklt.stop();
			this.SS_Strg_Sklt.stop();
			// TBD!
			while(this.SS_cmd_Sklt.getUtil() != 1) {}
			while(this.SS_Strg_Sklt.getUtil() != 1) {}
			//
			this.stopped(new Throwable());
			return;
		}
		else return; // already stopped

		//throw new UnsupportedOperationException("not implemented");
	}

	/** Called when the storage server has shut down.

        @param cause The cause for the shutdown, if any, or <code>null</code> if
                     the server was shut down by the user's request.
	 */
	protected void stopped(Throwable cause)
	{
	}

	// The following methods are documented in Storage.java.
	@Override
	public synchronized long size(Path file) throws FileNotFoundException
	{
		if(file == null) throw new NullPointerException();
		//
		String local_path = this.my_root_string + file.toString(); // Append the root directory head of the given path
		File f_obj = new File(local_path);
		if(f_obj.exists() && f_obj.isFile()) return f_obj.length();
		else throw new FileNotFoundException();
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public synchronized byte[] read(Path file, long offset, int length) throws FileNotFoundException, IOException
	{
		if(file == null) throw new NullPointerException();
		//
		String local_path = this.my_root_string + file.toString();
		File f_obj = new File(local_path);
		//
		if(f_obj.exists() && f_obj.isFile())
		{
			// Boundary check
			long total = f_obj.length();
			if(total < offset + length || length < 0) throw new IndexOutOfBoundsException();
			//
			FileInputStream fis = new FileInputStream(f_obj);
			byte[] ret = new byte[length];
			fis.read(ret, (int)offset, length); // overflow???
			fis.close();
			return ret;
		}
		else throw new FileNotFoundException();
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public synchronized void write(Path file, long offset, byte[] data) throws FileNotFoundException, IOException
	{
		if(file == null || data == null) throw new NullPointerException();
		if(offset < 0) throw new IndexOutOfBoundsException();
		//
		String local_path = this.my_root_string + file.toString();
		File f_obj = new File(local_path);
		//
		if(f_obj.exists() && f_obj.isFile())
		{
			RandomAccessFile raf = new RandomAccessFile(f_obj, "rw");
			raf.seek(offset);
			raf.write(data);
			//
			raf.close();
		}
		else throw new FileNotFoundException();
		//throw new UnsupportedOperationException("not implemented");
	}

	// The following methods are documented in Command.java.
	@Override
	public synchronized boolean create(Path file)
	{
		if(file == null) throw new NullPointerException();
		//
		if(file.isRoot()) return false; // No creation on root directory
		//
		Path p_file = file.parent();
		String p_local_path = this.my_root_string + p_file.toString();
		String local_path = this.my_root_string + file.toString();
		File p_f_obj = new File(p_local_path);
		File f_obj = new File(local_path);
		p_f_obj.mkdirs();
		//
		try {
			boolean ret = f_obj.createNewFile();
			return ret;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public synchronized boolean delete(Path path)
	{
		if(path == null) throw new NullPointerException();
		//
		if(path.isRoot()) return false; // Root directory cannot be deleted
		//
		File f_obj = path.toFile(this.my_root);
		return f_obj.isDirectory() ? delDir(path) : f_obj.delete(); // Can delete common file object/empty dir
		//throw new UnsupportedOperationException("not implemented");
	}

	// ???
	@Override
	public synchronized boolean copy(Path file, Storage server) throws RMIException, FileNotFoundException, IOException
	{
		if(file == null || server == null) throw new NullPointerException();
		//
		long length = server.size(file);
		byte[] data = server.read(file, 0, (int)length);
		Path p_file = file.parent();
		String p_local_path = this.my_root_string + p_file.toString();
		String local_path = this.my_root_string + file.toString();
		File p_f_obj = new File(p_local_path);
		File f_obj = new File(local_path);
		p_f_obj.mkdirs();
		f_obj.createNewFile();
		//
		FileOutputStream fos = new FileOutputStream(local_path);
		fos.write(data);
		fos.close();
		return true;
		//throw new UnsupportedOperationException("not implemented");
	}
}
