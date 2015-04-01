package rmi;

import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.net.*;

public class StubInvocationHandler implements InvocationHandler, Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//
	private InetSocketAddress DA;
	private Class<?> my_c;

	public StubInvocationHandler(InetSocketAddress src, Class<?> c)
	{
		this.DA = src;
		this.my_c = c;
	}

	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		// Get method name and parameters to be sent over TCP
		String method_name = method.getName();
		Object[] para = args;
		// Overwrite equals, hashCode, toString methods
		if(method_name.equals("equals"))
		{
			if(para.length != 1) throw new Error("Wrong number of input para for equals method!");
			if(para[0] == null) return false;
			if(!Proxy.isProxyClass(para[0].getClass())) throw new Error("Wrong input for equals method!");
			else
			{
				StubInvocationHandler comp = (StubInvocationHandler)Proxy.getInvocationHandler(para[0]);
				return this.DA.equals(comp.DA) && this.my_c.equals(comp.my_c);
			}
		}
		//
		else if(method_name.equals("toString"))
		{
			if(para != null) throw new Error("Wrong number of input para for toString method!");
			return "Interface Name: " + this.my_c.getName() + " Remote Address: " + this.DA.getHostName() + ":" + this.DA.getPort();
		}
		//
		else if(method_name.equals("hashCode"))
		{
			if(para != null) throw new Error("Wrong number of input para for hashCode method!");
			int hash = 1;
			hash = hash*17 + this.DA.hashCode();
			hash = hash*31 + this.my_c.hashCode();
			return hash;
		}
		//
		/*************************************************************************/
		// Establish networking
		else
		{
			// para initialization
			Boolean good = null;
			Socket my_socket = null;
			ObjectInputStream is = null;
			//
			try
			{
				my_socket = new Socket();
				my_socket.connect(DA);
				//
				ObjectOutputStream os = new ObjectOutputStream(my_socket.getOutputStream());
				os.writeObject(method_name);
				os.flush();
				//
				Integer para_len = para == null ? 0 : para.length;
				os.writeObject(para_len);
				os.flush();
				//
				for(int xx = 0; xx < para_len; xx++)
				{
					//if(Proxy.isProxyClass(para[xx].getClass())) os.writeObject("$$Proxy");
					/*else*/ os.writeObject(para[xx]);
					os.flush();
				}
				// Get results
				is = new ObjectInputStream(my_socket.getInputStream());
				//
				good = (Boolean)is.readObject();
			}
			catch(Exception e)
			{
				throw new RMIException("Network error!"); // make sure not to block any non-networking exceptions
			}

			if(good)
			{
				Boolean raiseException = (Boolean)is.readObject();
				if(!raiseException)
				{
					Object res = is.readObject();
					my_socket.close();
					return res;
				}
				else
				{
					Throwable res = (Throwable)is.readObject();
					my_socket.close();
					throw res;
				}
			}
			else
			{
				my_socket.close();
				throw new Error("Method does not exist.");
			}
		}
	}
}