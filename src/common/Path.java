package common;

import java.io.*;
import java.util.*;

/** Distributed filesystem paths.

    <p>
    Objects of type <code>Path</code> are used by all filesystem interfaces.
    Path objects are immutable.

    <p>
    The string representation of paths is a forward-slash-delimeted sequence of
    path components. The root directory is represented as a single forward
    slash.

    <p>
    The colon (<code>:</code>) and forward slash (<code>/</code>) characters are
    not permitted within path components. The forward slash is the delimeter,
    and the colon is reserved as a delimeter for application use.
 */
public class Path implements Iterable<String>, Comparable<Path>, Serializable
{
	private ArrayList<String> cmpnt_grp = new ArrayList<String>(0);

	/*************Self-helper*************/
	public ArrayList<String> getComponentGroup()
	{
		return this.cmpnt_grp;
	}

	/** Creates a new path which represents the root directory. */
	public Path()
	{
		this.cmpnt_grp.add("");
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Creates a new path by appending the given component to an existing path.

        @param path The existing path.
        @param component The new component.
        @throws IllegalArgumentException If <code>component</code> includes the
                                         separator, a colon, or
                                         <code>component</code> is the empty
                                         string.
	 */
	public Path(Path path, String component)
	{
		if(component == null || component.isEmpty() || component.contains("/") || component.contains(":"))
		{
			throw new IllegalArgumentException();
		}
		else
		{
			this.cmpnt_grp = new ArrayList<String>(path.getComponentGroup());
			this.cmpnt_grp.add(component);
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Creates a new path from a path string.

        <p>
        The string is a sequence of components delimited with forward slashes.
        Empty components are dropped. The string must begin with a forward
        slash.

        @param path The path string.
        @throws IllegalArgumentException If the path string does not begin with
                                         a forward slash, or if the path
                                         contains a colon character.
	 */
	public Path(String path)
	{
		if(path == null || path.isEmpty() || path.charAt(0) != '/' || path.contains(":"))
		{
			throw new IllegalArgumentException();
		}
		else
		{
			this.cmpnt_grp.add("");
			//
			String[] components = path.split("/");
			int length = components.length;
			for(int i = 0; i < length; i++)
			{
				if(!components[i].isEmpty()) this.cmpnt_grp.add(components[i]);
			}
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Returns an iterator over the components of the path.

        <p>
        The iterator cannot be used to modify the path object - the
        <code>remove</code> method is not supported.

        @return The iterator.
	 */
	@Override
	public Iterator<String> iterator()
	{
		// Need to make the returning iterator non-editable! (Missing)
		// Sol: create a self-made iterator class
		return new PathIterator(this.cmpnt_grp);
		//throw new UnsupportedOperationException("not implemented");
	}
	
	private class PathIterator implements Iterator<String>
	{
		private ArrayList<String> inner;
		private int index;
		
		public PathIterator(ArrayList<String> src)
		{
			this.inner = new ArrayList<String>(src);
			this.index = 1;
		}
		
		public boolean hasNext()
		{
			return this.index != this.inner.size();
		}
		
		public String next()
		{
			if(this.index == this.inner.size()) throw new NoSuchElementException();
			else return this.inner.get(this.index++);
		}
		
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}

	/** Lists the paths of all files in a directory tree on the local
        filesystem.

        @param directory The root directory of the directory tree.
        @return An array of relative paths, one for each file in the directory
                tree.
        @throws FileNotFoundException If the root directory does not exist.
        @throws IllegalArgumentException If <code>directory</code> exists but
                                         does not refer to a directory.
	 */
	public static Path[] list(File directory) throws FileNotFoundException
	{
		if(!directory.isDirectory()) throw new IllegalArgumentException();
		else if(!directory.exists()) throw new FileNotFoundException();
		else
		{
			// Step 1: get all files
			Queue<File> tool = new LinkedList<File>();
			ArrayList<File> ret_temp = new ArrayList<File>(0);
			tool.add(directory);
			while(!tool.isEmpty())
			{
				File temp = tool.peek();
				if(temp.isFile()) ret_temp.add(temp);
				else if(temp.isDirectory())
				{
					File[] next = temp.listFiles();
					int length = next.length;
					for(int i = 0; i < length; i++)
					{
						tool.offer(next[i]);
					}
				}
				else {}
				//
				tool.poll();
			}
			// Step 2: get all files' relative paths
			int ret_length = ret_temp.size();
			Path[] ret = new Path[ret_length];
			String rootPath = directory.getAbsolutePath();
			for(int i = 0; i < ret_length; i++)
			{
				String ready = ret_temp.get(i).getAbsolutePath();
				int end = ready.length();
				ready = ready.substring(rootPath.length(), end).replace('\\', '/');
				ret[i] = new Path(ready);
			}
			//
			return ret;
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Determines whether the path represents the root directory.

        @return <code>true</code> if the path does represent the root directory,
                and <code>false</code> if it does not.
	 */
	public boolean isRoot()
	{
		return this.cmpnt_grp.size() == 1 && this.cmpnt_grp.get(0).equals("");
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Returns the path to the parent of this path.

        @throws IllegalArgumentException If the path represents the root
                                         directory, and therefore has no parent.
	 */
	public Path parent()
	{
		if(this.isRoot()) throw new IllegalArgumentException();
		else
		{
			String curr = this.toString();
			int op = curr.length()-1;
			while(curr.charAt(op) != '/') op--;
			String prt = curr.substring(0, op+1);
			return new Path(prt);
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Returns the last component in the path.

        @throws IllegalArgumentException If the path represents the root
                                         directory, and therefore has no last
                                         component.
	 */
	public String last()
	{
		if(this.isRoot()) throw new IllegalArgumentException();
		else
		{
			ArrayList<String> inner = this.getComponentGroup();
			return inner.get(inner.size()-1);
		}
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Determines if the given path is a subpath of this path.

        <p>
        The other path is a subpath of this path if it is a prefix of this path.
        Note that by this definition, each path is a subpath of itself.

        @param other The path to be tested.
        @return <code>true</code> If and only if the other path is a subpath of
                this path.
	 */
	public boolean isSubpath(Path other)
	{
		String s1 = this.toString();
		String s2 = other.toString();
		char[] local = s1.toCharArray();
		char[] cmp = s2.toCharArray();
		int local_length = local.length;
		int cmp_length = cmp.length;
		//
		if(cmp_length < local_length)
		{
			for(int i = 0; i < cmp_length; i++)
			{
				if(local[i] != cmp[i]) return false;
			}
			//
			return true;
		}
		else if(cmp_length == local_length) return s1.equals(s2);
		else return false;
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Converts the path to <code>File</code> object.

        @param root The resulting <code>File</code> object is created relative
                    to this directory.
        @return The <code>File</code> object.
	 */
	public File toFile(File root)
	{
		// Just create the obj, not written in local disk
		String local = root.getAbsolutePath().replace('\\', '/');
		String ret_pa = local.concat(this.toString());
		return new File(ret_pa);
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Compares this path to another.

        <p>
        An ordering upon <code>Path</code> objects is provided to prevent
        deadlocks between applications that need to lock multiple filesystem
        objects simultaneously. By convention, paths that need to be locked
        simultaneously are locked in increasing order.

        <p>
        Because locking a path requires locking every component along the path,
        the order is not arbitrary. For example, suppose the paths were ordered
        first by length, so that <code>/etc</code> precedes
        <code>/bin/cat</code>, which precedes <code>/etc/dfs/conf.txt</code>.

        <p>
        Now, suppose two users are running two applications, such as two
        instances of <code>cp</code>. One needs to work with <code>/etc</code>
        and <code>/bin/cat</code>, and the other with <code>/bin/cat</code> and
        <code>/etc/dfs/conf.txt</code>.

        <p>
        Then, if both applications follow the convention and lock paths in
        increasing order, the following situation can occur: the first
        application locks <code>/etc</code>. The second application locks
        <code>/bin/cat</code>. The first application tries to lock
        <code>/bin/cat</code> also, but gets blocked because the second
        application holds the lock. Now, the second application tries to lock
        <code>/etc/dfs/conf.txt</code>, and also gets blocked, because it would
        need to acquire the lock for <code>/etc</code> to do so. The two
        applications are now deadlocked.

        @param other The other path.
        @return Zero if the two paths are equal, a negative number if this path
                precedes the other path, or a positive number if this path
                follows the other path.
	 */
	// For locking...
	@Override
	public int compareTo(Path other)
	{
		// compare path obj lexicographically so that it will not lead to deadlocks
		if(this.equals(other)) return 0;
		//
		String left = this.toString();
		String right = other.toString();
		int boundary = left.length() > right.length() ? right.length() : left.length();
		//
		for(int i = 0; i < boundary; i++)
		{
			if(left.charAt(i) != right.charAt(i)) return (int)(left.charAt(i) - right.charAt(i));
		}
		//
		if(boundary == left.length()) return (int)(right.charAt(boundary));
		if(boundary == right.length()) return (int)(left.charAt(boundary));
		return java.lang.Integer.MIN_VALUE;
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Compares two paths for equality.

        <p>
        Two paths are equal if they share all the same components.

        @param other The other path.
        @return <code>true</code> if and only if the two paths are equal.
	 */
	@Override
	public boolean equals(Object other)
	{
		return this.toString().equals(other.toString());
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Returns the hash code of the path. */
	@Override
	public int hashCode()
	{
		// hashCode method should be compatible with equals method
		int ret = 0;
		int length = this.cmpnt_grp.size();
		for(int i = 0; i < length; i++)
		{
			ret += i*this.cmpnt_grp.get(i).hashCode();
		}
		//
		return ret;
		//throw new UnsupportedOperationException("not implemented");
	}

	/** Converts the path to a string.

        <p>
        The string may later be used as an argument to the
        <code>Path(String)</code> constructor.

        @return The string representation of the path.
	 */
	@Override
	public String toString()
	{
		if(this.isRoot()) return "/";
		int length = this.cmpnt_grp.size();
		StringBuilder temp = new StringBuilder();
		for(int i = 0; i < length; i++)
		{
			temp.append(this.cmpnt_grp.get(i));
			temp.append("/");
		}
		//
		temp.deleteCharAt(temp.length()-1);
		return temp.toString();
		//throw new UnsupportedOperationException("not implemented");
	}
}
