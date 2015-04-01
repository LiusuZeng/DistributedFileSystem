package rmi;
import java.net.*;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Stub {	
	@SuppressWarnings("unchecked")	
	public static <T> T create(Class<T> c, Skeleton<T> skeleton) throws UnknownHostException
	{
		if(c == null || skeleton == null) throw new NullPointerException("Null input detected!");
		else if(!c.isInterface()) throw new Error("Input must be an interface!");
		else if(!isRemoteInterface(c)) throw new Error("Non-remote interface detected!");
		else
		{
			//
			//
			final Class<?>[] interfaces = new Class[]{c};
			InetSocketAddress address = skeleton.getAddr();
			if(address == null) throw new IllegalStateException();
			else
			{
				StubInvocationHandler inv_obj = new StubInvocationHandler(address, c);
				Object ret = Proxy.newProxyInstance(c.getClassLoader(), interfaces, inv_obj);
				//
				return (T) ret;
				//throw new UnsupportedOperationException("not implemented");
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T create(Class<T> c, Skeleton<T> skeleton,
			String hostname)
	{
		if(c == null || skeleton == null || hostname == null) throw new NullPointerException("Null input detected!");
		else if(!c.isInterface()) throw new Error("Input must be an interface!");
		else if(!isRemoteInterface(c)) throw new Error("Non-remote interface detected!");
		else
		{
			final Class<?>[] interfaces = new Class[]{c};
			InetSocketAddress address = skeleton.getAddr();
			if(address == null) throw new IllegalStateException();
			else
			{
				InetSocketAddress new_address = new InetSocketAddress(hostname, address.getPort()); // reload hostname for the assigned address
				StubInvocationHandler inv_obj = new StubInvocationHandler(new_address, c);
				return (T) Proxy.newProxyInstance(c.getClassLoader(), interfaces, inv_obj);
			}
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	@SuppressWarnings("unchecked")
	public static <T> T create(Class<T> c, InetSocketAddress address)
	{
		if(c == null || address == null) throw new NullPointerException("Null input detected!");
		else if(!c.isInterface()) throw new Error("Input must be an interface!");
		else if(!isRemoteInterface(c)) throw new Error("Non-remote interface detected!");
		else
		{
			final Class<?>[] interfaces = new Class[]{c};
			StubInvocationHandler inv_obj = new StubInvocationHandler(address, c);
			//
			return (T) Proxy.newProxyInstance(c.getClassLoader(), interfaces, inv_obj);
		}
	}

	protected static boolean isRemoteInterface(Class<?> c)
	{
		Method[] met = c.getMethods();
		for(int i = 0; i < met.length; i++) {
			//
			//System.out.println("Method: " + met[i].getName());
			// Find all exceptions thrown by a certain method in this interface
			Class[] ex = met[i].getExceptionTypes();
			boolean found = false;
			for(int j = 0; j < ex.length; j++) {
				//
				//System.out.println("==> " + ex[j].getName());
				//
				if(ex[j].getTypeName().equals("rmi.RMIException")) {
					found = true;
					break;
				}
			}
			//
			if(found) continue;
			else return false;
		}
		//
		return true;
	}

}