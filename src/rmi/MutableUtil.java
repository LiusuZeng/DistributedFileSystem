package rmi;

import java.io.*;

public class MutableUtil {
	public Integer stop;
	
	public MutableUtil(Integer given_stop)
	{
		this.stop = 1;
		// 0 stands for running
		// 1 stands for stop
		// 2 stands for stop req sent
	}
	
}