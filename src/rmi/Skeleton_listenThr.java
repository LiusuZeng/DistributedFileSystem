package rmi;

import java.io.IOException;
import java.net.*;

public class Skeleton_listenThr<T> extends Thread {
	private Class<T> my_c;
	private T my_server;
	private InetSocketAddress my_address;
	private MutableUtil tool;
	private Object lock;

	public Skeleton_listenThr(InetSocketAddress given_address, MutableUtil given_tool, Class<T> given_c, T given_server, Object given_lock)
	{
		this.my_address = given_address;
		this.tool = given_tool;
		this.my_c = given_c;
		this.my_server = given_server;
		this.lock = given_lock;
	}

	public void run()
	{
		try {
			ServerSocket skeleton_server = new ServerSocket(this.my_address.getPort());
			//skeleton_server.setSoTimeout(1000);
			//System.out.println("Hostname: " + this.my_address.getHostName() + " Port: " + this.my_address.getPort());
			//
			while(this.tool.stop != 1)
			{
				System.out.println("In while!");
				//
				Socket req = skeleton_server.accept();
				synchronized(this.lock)
				{
					if(this.tool.stop == 2)
					{
						this.tool.stop = 1;
						this.lock.notify();
						skeleton_server.close();
						return;
					}
				}
				//System.out.println("After accept!");
				InetAddress detail = req.getInetAddress();
				System.out.println("Request from ==> name: " + detail.getHostName() + " addr: " + detail.getHostAddress());
				//
				Skeleton_processThr<T> process_req = new Skeleton_processThr<T>(req, this.my_c, this.my_server, this.lock);
				process_req.start();
			}
			//
			if(this.tool.stop == 2) skeleton_server.accept();
			//
			synchronized(this.lock)
			{
				this.tool.stop = 1;
				this.lock.notify();
			}
			//
			if(!skeleton_server.isClosed()) skeleton_server.close();
			System.out.println("Out of while!");
			//
			//
			return;
			//
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}