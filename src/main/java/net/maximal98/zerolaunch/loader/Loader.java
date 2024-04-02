package net.maximal98.zerolaunch.loader;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Loader {
	public static void main(String[] args) throws
			ClassNotFoundException,
			IllegalAccessException,
			NoSuchMethodException,
			InvocationTargetException,
			IOException,
			InstantiationException,
			NoSuchFieldException
	{ //TODO: stop with this bs and actually implement try-catch statements so we can inform the user what went wrong
		System.out.println("hello from loader");
		boolean auth = true; //TODO: implement what this will be used in
		int argPlace = -1;
		if( args.length > 0 ) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (arg.equals("--noauth")) {
					auth = false;
					argPlace = i;
				}
			}
		}
		String fileString = URLDecoder.decode( net.maximal98.zerolaunch.loader.Loader.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");
		System.out.println("Path to jar is: "+fileString);
		File file = new File(fileString);
		ZipFile zipfile = new ZipFile(file);
		Enumeration<? extends ZipEntry> entries = zipfile.entries();
		Path assetDir = Paths.get("assets");
		if( !Files.exists(assetDir) ) {
			Files.createDirectory(assetDir);
			Files.createDirectory(Paths.get("assets/indexes"));
			Files.createDirectory(Paths.get("assets/objects"));//we can do this ahead of time because we know the structure already
			while (entries.hasMoreElements()) { //TODO: actually extract assets
				ZipEntry entry = entries.nextElement();
				if (entry.getName().contains("net/maximal98/zerolaunch/assets")) {
					if (!entry.isDirectory()) { //none of the entries should be directories, but eh it's not like this check costs us much
						String entryName = entry.getName();
						entryName = entryName.substring(entryName.indexOf("assets"));
						Path entryPath = Paths.get(entryName);
						if(entryName.contains("objects")) {
							Files.createDirectories(entryPath);
							Files.copy(zipfile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
						} else {
							Files.copy(zipfile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
						}
						System.out.println(entryName);
					}
				}
			}
		}
		if( !auth ) {
			System.setProperty("minecraft.minecraft.api.env", "http://127.0.0.1");
			System.setProperty("minecraft.api.auth.host", "http://127.0.0.1");
			System.setProperty("minecraft.api.account.host", "http://127.0.0.1");
			System.setProperty("minecraft.api.session.host", "http://127.0.0.1");
			System.setProperty("minecraft.api.services.host", "http://127.0.0.1");
		}
		String natives = System.getProperty("user.dir")+"/natives";
		System.out.println(natives);
		System.setProperty("java.library.path", natives );
		Field JNIPathField = ClassLoader.class.getDeclaredField("sys_paths");
		JNIPathField.setAccessible( true );
		JNIPathField.set( null, null ); //Shitty hack because you're not supposed to be able to do this
		Class<?> minecraftClass = Class.forName( "net.minecraft.client.main.Main" );
		minecraftClass.getMethod( "main", new Class[]{String[].class} ).invoke( null, (Object) new String[]{ "--accessToken", "0", "--username", "micheal", "--version", "minecraft", "--assetIndex", "default" } );
		System.exit( 0 );
	}
}