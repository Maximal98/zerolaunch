package net.maximal98.zerolaunch;

import org.json.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
	public static void main(String[] args) {
		System.out.println("Hello world!");

		final String LauncherPath = "/home/maximilian/.local/share/PrismLauncher";

		try {
			JSONObject MetaIndexObject = new JSONObject( new String( Files.readAllBytes( Paths.get( LauncherPath + "/meta/index.json" ) ) ) );
			if( MetaIndexObject.getInt("formatVersion") != 1 ) {
				System.out.println( "Meta Index is unknown version. Aborting." );
				System.exit(1);
			}
			JSONArray MetaPackageArray = MetaIndexObject.getJSONArray( "packages" );
			System.out.println( "Available Packages to bootstrap:" );
			for( int iterator = 0; iterator < MetaPackageArray.length(); iterator++ ) {
				JSONObject MetaPackage = MetaPackageArray.getJSONObject( iterator );
				System.out.println( MetaPackage.getString( "uid" ) );
			}
			System.out.print( "Please select Package " );
			Scanner Input = new Scanner( System.in );
			String PackageSelection = Input.nextLine();
			String SelectedPackageUID = "";
			for( int iterator = 0; iterator < MetaPackageArray.length(); iterator++ ) {
				if( MetaPackageArray.getJSONObject( iterator ).getString( "uid" ).equals( PackageSelection ) ) {
					SelectedPackageUID = MetaPackageArray.getJSONObject( iterator ).getString( "uid" );
				}
			}
			System.out.println( "Selected Package " + SelectedPackageUID );

			File SelectionDir = new File( System.getProperty( "user.home" ) + "/.local/share/PrismLauncher/meta/" + SelectedPackageUID );
			String[] SelectionDirArray = SelectionDir.list();
			System.out.println( "Please select Version." );
			// ↓ produces a warning
			for (String Element : SelectionDirArray) {
				if (!(Objects.equals(Element, "index.json"))) {
					System.out.println(Element.substring(0, Element.lastIndexOf(".")));
				}
			}
			System.out.print( "Please input Version. " );
			String SelectedVersion = Input.nextLine();

			boolean CorrectVersion = false;
			for (String Element : SelectionDirArray) {
				if (Element.substring(0, Element.lastIndexOf(".")).equals( SelectedVersion )) {
					CorrectVersion = true;
					break;
				}
			}

			if( !CorrectVersion ) {
				//TODO: make it a loop to keep prompting a version. this is silly.
				System.out.println( "Invalid version selected." );
				System.exit(1);
			}

			System.out.println( "Selected Version " + SelectedVersion.substring( 0, SelectedVersion.lastIndexOf( "." ) ) );



			JSONObject VersionJSON = new JSONObject( new String( Files.readAllBytes( Paths.get( LauncherPath + "/meta/" + SelectedPackageUID + "/" + SelectedVersion + ".json" ) ) ) );

			List<JSONObject> PackageList = new ArrayList<>();
			List<JSONObject> DependencyList = new ArrayList<>();
			List<JSONObject> RemoveList = new ArrayList<>();
			List<JSONObject> AddList = new ArrayList<>();
			DependencyList.add( VersionJSON );
			//we love recursive dependencies!!
			while( !DependencyList.isEmpty() ) {
				for ( JSONObject PackageObject : DependencyList ) {
					if (PackageObject.has("requires")) {
						JSONArray DependencyArray = PackageObject.getJSONArray("requires");
						for (int iterator = 0; iterator < DependencyArray.length(); iterator++) {
							JSONObject DependencyArrayObject = DependencyArray.getJSONObject(iterator);
							String DependencyPath = LauncherPath
									+ "/meta/"
									+ DependencyArrayObject.getString("uid")
									+ "/"
									+ DependencyArrayObject.getString("suggests")
									+ ".json";
							AddList.add(new JSONObject(new String(Files.readAllBytes(Paths.get(DependencyPath)))));
						}
					}
					PackageList.add(PackageObject);
					RemoveList.add(PackageObject);
				}
				DependencyList.addAll( AddList );
				DependencyList.removeAll( RemoveList );
			}

			Manifest MainManifest = new Manifest();
			MainManifest.getMainAttributes().put( Attributes.Name.MANIFEST_VERSION, "1.0" );
			MainManifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "net.minecraft.client.main.Main");

			File NewJar = new File("minecraft.jar");
			JarOutputStream NewJarStream = new JarOutputStream( Files.newOutputStream( NewJar.toPath() ), MainManifest );
			List<String> EntryList = new ArrayList<>();

			for ( JSONObject PackageObject : PackageList ) {
				System.out.println("bootstrapping " + PackageObject.getString( "uid" ) + ":" + PackageObject.getString( "version" ) );

				if( PackageObject.getString( "uid" ).equals( "net.minecraft" ) ) {
					JSONObject MainJarObject = PackageObject.getJSONObject( "mainJar" );
					String ClientName = MainJarObject.getString( "name" ).substring( 0, MainJarObject.getString( "name" ).lastIndexOf( ":" ) );
					ClientName = ClientName.substring(0, ClientName.indexOf(":")).replaceAll("\\.", "/")
							+ ClientName.substring(ClientName.indexOf(":")).replaceAll(":", "/");


					File ClientFolder = new File( LauncherPath + "/libraries/" + ClientName );
					String ClientPath = LauncherPath + "/libraries/" + ClientName + "/";
					String[] ClientListing = ClientFolder.list();
					if ( !( ClientListing.length == 1 ) ) {
						for ( String Search : ClientListing ) {
							if( Search.contains( "client" ) ) {
								ClientPath = ClientPath + Search;
								break;
							}
						}
					} else {
						ClientPath = ClientPath + ClientListing[0];
					}

					ZipInputStream ClientZipStream = new ZipInputStream( Files.newInputStream( Paths.get( ClientPath ) ) );
					EntryList.addAll( AddDataToZip( ClientZipStream, NewJarStream, EntryList, new String[]{ "META-INF" } ) );
				}


				JSONArray LibraryArray = PackageObject.getJSONArray("libraries");



				for (int iterator = 0; iterator < LibraryArray.length(); iterator++) {
					if( LibraryArray.getJSONObject( iterator ).getJSONObject( "downloads" ).has( "artifact" ) ) {
						JSONObject Library = LibraryArray.getJSONObject(iterator);
						String Name = Library.getString("name");
						String URL = Library.getJSONObject("downloads").getJSONObject("artifact").getString("url");

						String FilePath = LauncherPath
								+ "/libraries/"
								+ Name.substring(0, Name.indexOf(":")).replaceAll("\\.", "/")
								+ Name.substring(Name.indexOf(":")).replaceAll(":", "/")
								+ URL.substring(URL.lastIndexOf("/"));

						File LibraryFile = new File(FilePath);
						if (LibraryFile.exists()) {
							ZipInputStream LibraryZipStream = new ZipInputStream( Files.newInputStream( Paths.get( FilePath ) ) );

							EntryList.addAll( AddDataToZip( LibraryZipStream, NewJarStream, EntryList, new String[]{ "META-INF", ".git" } ) );
						}
					}
				}
			}
			NewJarStream.close();
			System.gc();
		} catch ( IOException Exception ) {
			//I don't think this is good practice but uhh, uhh look over there a cool bird
			System.out.println( "IOException | " + Exception.getMessage() );
			Exception.printStackTrace();
			System.exit(1);
		}
	}

	public static List<String> AddDataToZip( ZipInputStream InStream, JarOutputStream OutStream, List<String> EntryList, String[] ExcludeArray ) throws IOException {
		List<String> NewEntryList = new ArrayList<>();
		byte[] byteBuff = new byte[1024];
		for (ZipEntry Entry; (Entry = InStream.getNextEntry()) != null; ) {
			boolean Run = true;
			for (String Search : ExcludeArray) {
				if (Entry.getName().contains(Search)) {
					Run = false;
					break;
				}
			}

			for (String Search : EntryList) {
				if (Entry.getName().equals(Search)) {
					Run = false;
					break;
				}
			}

			if (Run) {
				JarEntry NewEntry = new JarEntry(Entry.getName());
				OutStream.putNextEntry(NewEntry);
				if (!(NewEntry.isDirectory())) {
					for (int bytesRead; (bytesRead = InStream.read(byteBuff)) != -1; ) {
						OutStream.write(byteBuff, 0, bytesRead);
					}
				}
				NewEntryList.add(NewEntry.getName());
			}
		}
		InStream.close();
		return NewEntryList;
	}

}