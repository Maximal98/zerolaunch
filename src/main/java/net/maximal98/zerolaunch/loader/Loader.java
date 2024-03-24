package net.maximal98.zerolaunch.loader;

import java.lang.reflect.InvocationTargetException;

public class Loader {
	public static void main(String[] args) throws
			ClassNotFoundException,
			IllegalAccessException,
			NoSuchMethodException,
			InvocationTargetException
	{
		System.out.println("hello from loader");
		Class<?> MinecraftClass = Class.forName( "net.minecraft.client.main" );
		MinecraftClass.getMethod( "Main" ).invoke( new String[]{ "--accessToken", "0", "--username", "micheal", "--version", "pee" } );
		System.exit( 0 );
	}
}