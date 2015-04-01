package naming;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import rmi.*;
import common.*;
import storage.*;

/** Naming server.

    <p>
    Each instance of the filesystem is centered on a single naming server. The
    naming server maintains the filesystem directory tree. It does not store any
    file data - this is done by separate storage servers. The primary purpose of
    the naming server is to map each file name (path) to the storage server
    which hosts the file's contents.

    <p>
    The naming server provides two interfaces, <code>Service</code> and
    <code>Registration</code>, which are accessible through RMI. Storage servers
    use the <code>Registration</code> interface to inform the naming server of
    their existence. Clients use the <code>Service</code> interface to perform
    most filesystem operations. The documentation accompanying these interfaces
    provides details on the methods supported.

    <p>
    Stubs for accessing the naming server must typically be created by directly
    specifying the remote network address. To make this possible, the client and
    registration interfaces are available at well-known ports defined in
    <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration
{
	// private param
	private HashMap<Path, UtilObj> dir_tree;
	public ArrayList<Object> cmd_list;
	public ArrayList<Object> Strg_list;
	private Skeleton<Registration> NS_reg;
	private Skeleton<Service> NS_svc;
	private Boolean start;
	private Boolean running;
	private Boolean errorOnce;
	//
	public int ptr = 0;

	/** Creates the naming server object.

        <p>
        The naming server is not started.
	 */
	public NamingServer()
	{
		this.dir_tree = new HashMap<Path, UtilObj>();
		InetSocketAddress reg_addr = new InetSocketAddress("localhost", NamingStubs.REGISTRATION_PORT);
		this.NS_reg = new Skeleton<Registration>(Registration.class, this, reg_addr);
		InetSocketAddress svc_addr = new InetSocketAddress("localhost", NamingStubs.SERVICE_PORT);
		this.NS_svc = new Skeleton<Service>(Service.class, this, svc_addr);
		// initialization of array list, watch for parameter type
		this.cmd_list = new ArrayList<Object>(0);
		this.Strg_list = new ArrayList<Object>(0);
		//
		this.start = false;
		this.running = false;
		this.errorOnce = false;
		// put a root dir in the dir tree when creating a new Naming Server
		Path my_root = new Path("/");
		this.dir_tree.put(my_root, new UtilObj(null, null, true, this, my_root));
		//
		/*
		Thread repThr = new Thread(this);
		repThr.start();
		 */
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Starts the naming server.

        <p>
        After this method is called, it is possible to access the client and
        registration interfaces of the naming server remotely.

        @throws RMIException If either of the two skeletons, for the client or
                             registration server interfaces, could not be
                             started. The user should not attempt to start the
                             server again if an exception occurs.
	 */
	public synchronized void start() throws RMIException
	{
		if(this.start == true)
		{
			System.out.println("This Naming Server cannot be re-started!");
			return;
		}
		else if(this.errorOnce == true)
		{
			System.out.println("An error has occurred last time. Please check and re-run the program!");
			return;
		}
		else
		{
			// TBD!
			try {
				this.NS_reg.start();
				this.NS_svc.start();
			} catch (RMIException e) {
				this.errorOnce = true;
				this.start = false;
				this.running = false;
				//
				throw e;
			}
			//
			this.start = true;
			this.running = true;
			this.errorOnce = false;
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Stops the naming server.

        <p>
        This method commands both the client and registration interface
        skeletons to stop. It attempts to interrupt as many of the threads that
        are executing naming server code as possible. After this method is
        called, the naming server is no longer accessible remotely. The naming
        server should not be restarted.
	 */
	public void stop()
	{
		if(this.running == false) return;
		else
		{
			this.NS_reg.stop();
			this.NS_svc.stop();
			// TBD!
			while(this.NS_reg.getUtil() != 1) {}
			while(this.NS_svc.getUtil() != 1) {}
			//
			this.running = false;
			//
			this.stopped(new Throwable());
			//
			return;
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Indicates that the server has completely shut down.

        <p>
        This method should be overridden for error reporting and application
        exit purposes. The default implementation does nothing.

        @param cause The cause for the shutdown, or <code>null</code> if the
                     shutdown was by explicit user request.
	 */
	protected void stopped(Throwable cause)
	{
	}

	// The following public methods are documented in Service.java.
	// helper func for lock/unlock
	protected void unit_lock(Path path, boolean exclusive) throws FileNotFoundException, InterruptedException
	{
		if(this.dir_tree.containsKey(path))
		{
			// write lock req
			if(exclusive)
			{
				synchronized(this.dir_tree.get(path))
				{
					//System.out.println("###Lock for Write###");
					//
					while(this.dir_tree.get(path).get_hasWrreq())
					{
						this.dir_tree.get(path).wait();
					}
					//
					this.dir_tree.get(path).set_hasWrreq(true);
					//
					//System.out.println("Write lock req gets to last wait......");
					//
					while(this.dir_tree.get(path).get_numRdreq() > 0)
					{
						System.out.println("Wr_req: " + this.dir_tree.get(path).get_numRdreq());
						//
						//this.dir_tree.get(path).notify();
						this.dir_tree.get(path).wait();
					}
					// do invalidation
					// 1. check to see if there is any replication (must be a file, not dir)
					if(!this.dir_tree.get(path).getIsDir() && this.dir_tree.get(path).cmd.size() > 1)
					{
						// 2. keep the first copy (at pos = 0) and del others (maintain D_S)
						for(int xx = 1; xx < this.dir_tree.get(path).cmd.size();)
						{
							Command Todel = (Command)this.dir_tree.get(path).cmd.get(xx);
							try {
								Todel.delete(path);
							} catch (RMIException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							//
							this.dir_tree.get(path).cmd.remove(xx);
							this.dir_tree.get(path).Strg.remove(xx);
						}
						// 3. refresh UtilObj info
						this.dir_tree.get(path).chg_num_rep(false); // set rep = 0
						this.dir_tree.get(path).chg_valid_cpy(false); // set valid_cpy = 1;
						this.dir_tree.get(path).rst_numRdreq(); // set num_Rdreq = 0;
					}
					//
					this.dir_tree.get(path).notifyAll();
				}
			}
			// read lock req
			else
			{
				synchronized(this.dir_tree.get(path))
				{
					this.dir_tree.get(path).chg_num_rep(true); // record # of read
					//
					//System.out.println("###Lock for Read###");
					//
					//System.out.println("Read lock req gets to last wait.......");
					//
					while(this.dir_tree.get(path).get_hasWrreq() || (this.dir_tree.get(path).get_num_rep() >= 20 && !this.dir_tree.get(path).getIsDir()))
					{
						//this.dir_tree.get(path).notify();
						this.dir_tree.get(path).wait();
					}
					//
					this.dir_tree.get(path).chg_numRdreq(true);
					//
					this.dir_tree.get(path).notifyAll();
				}
			}
		}
		else throw new FileNotFoundException();
	}

	protected void unit_unlock(Path path, boolean exclusive)
	{
		// write unlock req
		if(exclusive)
		{
			synchronized(this.dir_tree.get(path))
			{
				if(this.dir_tree.get(path).get_hasWrreq()) this.dir_tree.get(path).set_hasWrreq(false);
				else throw new IllegalArgumentException();
				//
				this.dir_tree.get(path).notifyAll();
			}
		}
		// read unlock req
		else
		{
			synchronized(this.dir_tree.get(path))
			{
				//System.out.println("***Read unlock***");
				//System.out.println("Before: " + this.dir_tree.get(path).get_numRdreq());
				//System.out.println("Write req rec: " + this.dir_tree.get(path).get_hasWrreq());
				//
				if(this.dir_tree.get(path).get_numRdreq() > 0)
				{
					this.dir_tree.get(path).chg_numRdreq(false);
					//System.out.println("After: " + this.dir_tree.get(path).get_numRdreq());
				}
				else throw new IllegalArgumentException();
				//
				this.dir_tree.get(path).notifyAll();
			}
		}
	}

	@Override
	public void lock(Path path, boolean exclusive) throws FileNotFoundException
	{
		if(path == null) throw new NullPointerException();
		if(!this.dir_tree.containsKey(path)) throw new FileNotFoundException();
		if(path.isRoot())
		{
			try {
				unit_lock(path, exclusive);
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			//
			return;
		}
		//
		Path[] extras = get_dir_deep_down(path);
		Path my_root = new Path("/");
		// write lock req
		// req the write lock for this specific file and req read locks for objs along the path
		if(exclusive)
		{
			try {
				unit_lock(my_root, false);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			//
			for(int i = 0; i < extras.length; i++)
			{
				try {
					unit_lock(extras[i], false);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			//
			try {
				unit_lock(path, true);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// read lock req
		// req read locks for all objs along the path
		else
		{
			try {
				unit_lock(my_root, false);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			//
			System.out.println("root read locked!");
			//
			for(int i = 0; i < extras.length; i++)
			{
				try {
					unit_lock(extras[i], false);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//
				System.out.println("The " + i + "th parent read locked!");
			}
			//
			try {
				unit_lock(path, false);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//
			System.out.println("tar file read locked!");
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public void unlock(Path path, boolean exclusive) // Should unlock ops be done sequentially? TBD
	{
		if(path == null) throw new NullPointerException();
		if(path.isRoot())
		{
			unit_unlock(path, exclusive);
			return;
		}
		//
		Path[] extras = get_dir_deep_down(path);
		Path my_root = new Path("/");
		// write unlock req
		if(exclusive)
		{
			unit_unlock(my_root, false);
			//
			for(int i = 0; i < extras.length; i++)
			{
				unit_unlock(extras[i], false);
			}
			//
			unit_unlock(path, true);
		}
		// read unlock req
		else
		{
			unit_unlock(my_root, false);
			//
			for(int i = 0; i < extras.length; i++)
			{
				unit_unlock(extras[i], false);
			}
			//
			unit_unlock(path, false);
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public boolean isDirectory(Path path) throws RMIException, FileNotFoundException
	{
		if(path == null) throw new NullPointerException();
		//
		if(this.dir_tree.containsKey(path))
		{
			return this.dir_tree.get(path).getIsDir();
		}
		else throw new FileNotFoundException();
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public String[] list(Path directory) throws RMIException, FileNotFoundException
	{
		if(directory == null) throw new NullPointerException();
		//
		if(!directory.isRoot() && (!this.dir_tree.containsKey(directory) || !this.dir_tree.get(directory).getIsDir()))
			throw new FileNotFoundException();
		else
		{
			ArrayList<String> ret = new ArrayList<String>(0);
			Iterator<Entry<Path, UtilObj>> itr = this.dir_tree.entrySet().iterator();
			while(itr.hasNext())
			{
				Map.Entry<Path, UtilObj> my_entry = (Map.Entry<Path, UtilObj>)itr.next();
				Path temp = my_entry.getKey();
				// Both files and sub-dir will be included in the list
				if(!temp.isRoot() && temp.parent().equals(directory)) ret.add(temp.last());
			}
			//
			return (String[])ret.toArray(new String[ret.size()]);
		}
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public boolean createFile(Path file) throws RMIException, FileNotFoundException
	{
		if(file == null) throw new NullPointerException();
		if(file.isRoot()) return false;
		//
		Path p_dir = file.parent();
		if(p_dir.isRoot() || (this.dir_tree.containsKey(p_dir)&& this.dir_tree.get(p_dir).getIsDir()))
		{
			if(this.dir_tree.containsKey(file)) return false; // Already exists
			else
			{
				// Randomly select a Storage Server to assign the new file
				if(this.ptr >= this.cmd_list.size()) this.ptr = 0;
				this.dir_tree.put(file, new UtilObj(this.cmd_list.get(this.ptr), this.Strg_list.get(this.ptr), false, this, file));
				//
				Command this_cmd = (Command)this.cmd_list.get(this.ptr);
				this.ptr++;
				return this_cmd.create(file);
			}
		}
		else throw new FileNotFoundException();
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public boolean createDirectory(Path directory) throws RMIException, FileNotFoundException
	{
		if(directory == null) throw new NullPointerException();
		if(directory.isRoot()) return false;
		//
		Path p_dir = directory.parent();
		if(p_dir.isRoot() || (this.dir_tree.containsKey(p_dir)&& this.dir_tree.get(p_dir).getIsDir()))
		{
			if(this.dir_tree.containsKey(directory)) return false; // Already exists
			else
			{
				// Randomly select a Storage Server to assign the new dir
				if(this.ptr >= this.cmd_list.size()) this.ptr = 0;
				this.dir_tree.put(directory, new UtilObj(this.cmd_list.get(this.ptr), this.Strg_list.get(this.ptr), true, this, directory));
				this.ptr++;
				//
				return true;
			}
		}
		else throw new FileNotFoundException();
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	// ???
	public boolean delete(Path path) throws RMIException, FileNotFoundException
	{
		if(path == null) throw new NullPointerException();
		if(path.isRoot()) return false; // illegal to delete root dir
		//
		boolean ret = true;
		if(this.dir_tree.containsKey(path))
		{
			if(this.dir_tree.get(path).getIsDir())
			{
				ArrayList<Path> delGrp = new ArrayList<Path>(0);
				// Find all related files/dir and then do the following
				for(Entry<Path, UtilObj> myitr : this.dir_tree.entrySet())
				{
					if(myitr.getKey().isSubpath(path)) delGrp.add(myitr.getKey());
				}
				//
				HashSet<Command> base = new HashSet<Command>();
				for(Path myitr2 : delGrp)
				{
					// 1. Stop the thread
					this.dir_tree.get(myitr2).stopMe();
					// 2. loop thru the stub list and delete the local file (Storage Server RMI call)
					for(int ii = 0; ii < this.dir_tree.get(myitr2).cmd.size(); ii++)
					{
						Command temp = (Command)this.dir_tree.get(myitr2).cmd.get(ii);
						base.add(temp);
					}
					// 3. del from dir tree
					this.dir_tree.remove(myitr2);
				}
				//
				for(Command loc : base)
				{
					ret = ret && loc.delete(path);
				}
			}
			else
			{
				// 1. Stop the thread
				this.dir_tree.get(path).stopMe();
				// 2. loop thru the stub list and delete the local file (Storage Server RMI call)
				for(int ii = 0; ii < this.dir_tree.get(path).cmd.size(); ii++)
				{
					Command temp = (Command)this.dir_tree.get(path).cmd.get(ii);
					ret = ret && temp.delete(path);
				}
				// 3. del from dir tree
				this.dir_tree.remove(path);
			}
			//
			return ret;
		}
		else throw new FileNotFoundException();
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Storage getStorage(Path file) throws RMIException, FileNotFoundException
	{
		if(file == null) throw new NullPointerException();
		if(file.isRoot()) throw new FileNotFoundException();
		//
		if(this.dir_tree.containsKey(file) && !this.dir_tree.get(file).getIsDir())
		{
			return this.dir_tree.get(file).getStorageStub();
		}
		else throw new FileNotFoundException(); // TBD!
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	// helper function: get all the parent directory to be created
	protected Path[] get_dir_deep_down(Path src)
	{
		ArrayList<Path> ret = new ArrayList<Path>(0);
		Path temp = null;
		if(!src.isRoot())
		{
			temp = (new Path(src.toString())).parent();
			while(!temp.isRoot())
			{
				ret.add(temp);
				temp = temp.parent();
			}
		}
		//
		return ret.toArray(new Path[ret.size()]);
	}
	//

	// The method register is documented in Registration.java.
	@Override
	public Path[] register(Storage client_stub, Command command_stub, Path[] files) throws RMIException, NullPointerException, IllegalStateException
	{
		if(client_stub == null || command_stub == null || files == null)
			throw new NullPointerException();
		if(this.cmd_list.contains(command_stub) && this.Strg_list.contains(client_stub))
			throw new IllegalStateException();
		//
		ArrayList<Path> ret = new ArrayList<Path>(0);
		//
		// Check which file on the local Storage Server
		// is also available on Naming Server's registered Storage Server
		// and then put it into the del list
		try {
			lock(new Path("/"), true);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//
		int num_given_files = files.length;	
		for(int i = 0; i < num_given_files; i++)
		{
			// This is the duplicate, to be del
			if(files[i].isRoot()) {}
			else
			{
				if(this.dir_tree.containsKey(files[i])) ret.add(files[i]);
				// This is a new file (non-directory) that needs to be sync to the Naming Server dir tree
				else
				{
					this.dir_tree.put(files[i], new UtilObj(command_stub, client_stub, false, this, files[i]));
					// now add the possible directories brought by this added file
					Path[] prt_dirs = get_dir_deep_down(files[i]);
					int prt_dirs_len = prt_dirs.length;
					for(int x = 0; x < prt_dirs_len; x++)
					{
						if(!this.dir_tree.containsKey(prt_dirs[x])) this.dir_tree.put(prt_dirs[x], new UtilObj(command_stub, client_stub, true, this, prt_dirs[x]));
					}
				}
			}
		}
		this.cmd_list.add(command_stub);
		this.Strg_list.add(client_stub);
		//
		unlock(new Path("/"), true);
		//
		return (Path[])ret.toArray(new Path[ret.size()]);
		//
		//throw new UnsupportedOperationException("not implemented");
	}

	/*	public void run() {
		// TODO Auto-generated method stub
		while(true)
		{
			for(Entry<Path, UtilObj> my_entry : this.dir_tree.entrySet())
			{
				Path my_key = my_entry.getKey();
				UtilObj my_value = my_entry.getValue();
				if(my_value.getIsDir()) {}
				else
				{
					boolean cond = false;
					int cp_stub_len = 0;
					int total_stub_len = 0;
					synchronized(my_value)
					{
						cond = my_value.get_num_rep() >= 20;
						cp_stub_len = my_value.get_valid_cpy();
						total_stub_len = this.cmd_list.size();
						//
						my_value.notifyAll();
					}
					//
					if(cond && cp_stub_len != total_stub_len) // there is still available storage servers
					{
						//
						System.out.println(".......In replication.......");
						//
						if(this.ptr >= total_stub_len) this.ptr = 0;
						while(my_value.cmd.contains(this.cmd_list.get(ptr)))
						{
							ptr++;
							if(ptr >= total_stub_len) ptr = 0;
						}
						//
						Command vice_cmd = (Command)(this.cmd_list.get(ptr));
						Storage vice_Strg = (Storage)(this.Strg_list.get(ptr));
						try {
							vice_cmd.copy(my_key, my_value.getStorageStub());
							//
							System.out.println("......copy called!!!......");
							//
							my_value.cmd.add(vice_cmd);
							my_value.Strg.add(vice_Strg);
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (RMIException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						//
						synchronized(my_value)
						{
							my_value.chg_valid_cpy(true);
							my_value.notifyAll();
						}
					}
				}
			}
		}
	}*/
}
