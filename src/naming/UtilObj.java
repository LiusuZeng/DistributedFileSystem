package naming;
import rmi.RMIException;
import storage.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import common.Path;

public class UtilObj implements Runnable {
	public ArrayList<Object> cmd = new ArrayList<Object>(0);
	public ArrayList<Object> Strg = new ArrayList<Object>(0);
	private Boolean isDir;
	// for locking control
	private int numRdreq;
	private boolean hasWrreq;
	// for replication control
	private int num_rep;
	private int valid_cpy;
	private int ptr;
	//
	private NamingServer outside;
	private Path src_file;
	//
	private Thread thisFile_replication;
	private volatile boolean con;

	public UtilObj(Object src_cmd, Object src_Strg, Boolean src_isDir, NamingServer src_NS, Path src_f)
	{
		this.cmd.add(src_cmd);
		this.Strg.add(src_Strg);
		this.isDir = src_isDir;
		//
		this.numRdreq = 0;
		this.hasWrreq = false;
		//
		this.num_rep = 0;
		this.valid_cpy = 1;
		this.ptr = 0;
		//
		this.outside = src_NS;
		this.src_file = src_f;
		//
		this.con = true;
		this.thisFile_replication = new Thread(this);
		thisFile_replication.start();
		
	}
	
	public void stopMe()
	{
		this.con = false;
	}
	//???
	public void chg_valid_cpy(boolean op)
	{
		if(op) this.valid_cpy++;
		else this.valid_cpy = 1; // ???
	}

	public int get_valid_cpy()
	{
		return this.valid_cpy;
	}

	public void chg_num_rep(boolean op)
	{
		if(op) this.num_rep++;
		else this.num_rep = 0;
	}

	public int get_num_rep()
	{
		return this.num_rep;
	}

	public int get_numRdreq()
	{
		return this.numRdreq;
	}

	public void chg_numRdreq(boolean op)
	{
		if(op) this.numRdreq++;
		else this.numRdreq--;
	}
	
	public void rst_numRdreq()
	{
		this.numRdreq = 0;
	}

	public boolean get_hasWrreq()
	{
		return this.hasWrreq;
	}

	public void set_hasWrreq(boolean src)
	{
		this.hasWrreq = src;
	}

	public Command getCommandStub()
	{
		if(this.ptr == this.valid_cpy) this.ptr = 0;
		return (Command)(this.cmd.get(this.ptr++));
	}

	public Storage getStorageStub()
	{
		if(this.ptr == this.valid_cpy) this.ptr = 0;
		return (Storage)(this.Strg.get(this.ptr++));
	}

	public Boolean getIsDir()
	{
		return this.isDir;
	}

	public int getListsize()
	{
		return this.cmd.size();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(this.con)
		{
			// Delay
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// do replication
			if(!this.isDir && this.num_rep >= 20)
			{
				//
				System.out.println("===========Ready to replicate!===========");
				//
				try {
					this.outside.lock(new Path("/"), false);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//
				if(this.outside.cmd_list.size() == this.cmd.size()) break; // see if there is redundant servers
				//
				System.out.println("[DEBUG]--->");
				System.out.println("ava reg server: " + this.outside.cmd_list.size() + " file server: " + this.cmd.size());
				System.out.println("NS data: " + this.outside.ptr);
				//
				if(this.outside.ptr >= this.outside.cmd_list.size()) this.outside.ptr = 0;
				while(this.cmd.contains(this.outside.cmd_list.get(this.outside.ptr)))
				{
					this.outside.ptr = this.outside.ptr >= this.outside.cmd_list.size() ? 0 : this.outside.ptr + 1;
				}
				Command vice_cmd = (Command)(this.outside.cmd_list.get(this.outside.ptr));
				Storage vice_Strg = (Storage)(this.outside.Strg_list.get(this.outside.ptr));
				/**/
				try {
					vice_cmd.copy(this.src_file, (Storage)this.Strg.get(ptr));
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
				/**/
				synchronized(this)
				{
					this.Strg.add(vice_Strg);
					this.cmd.add(vice_cmd);
					this.num_rep -= 20;
					this.valid_cpy++;
					//
					this.notifyAll();
				}
				//
				this.outside.unlock(new Path("/"), false);
			}
			else if(this.isDir) break; // no need to replicate dir
			else continue;
		}
		//
		return;
	}
}
