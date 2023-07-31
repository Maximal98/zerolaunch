package net.maximal98.zerolaunch;

import org.apache.commons.cli.*;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
	public static void main(String[] args) throws ParseException, IOException {
		//TODO: change to make compliant with this: https://www.oracle.com/java/technologies/javase/codeconventions-namingconventions.html
		final String optionDDesc = "The directory where MultiMC/PolyMC/PrismLauncher store their data (should contain a 'libraries' and a 'meta' folder)";
		Options MainOptions = new Options();
		MainOptions.addOption( Option.builder("d").longOpt("launcher-directory").hasArg(true).desc( optionDDesc ).required().build() );
		CommandLineParser MainParser = new DefaultParser();
		CommandLine MainCommandLine;
		MainCommandLine = MainParser.parse( MainOptions, args );

		final String LauncherPath = MainCommandLine.getOptionValue( "d" );

		//TODO: Version checking everywhere where its possible.
		JSONObject MetaIndexObject = new JSONObject( new String( Files.readAllBytes( Paths.get( LauncherPath + "/meta/index.json" ) ) ) );
		if( MetaIndexObject.getInt("formatVersion") != 1 ) {
			System.out.println( "Meta Index is unknown version. Aborting." );
			System.exit(1);
		}
		JSONArray MetaPackageArray = MetaIndexObject.getJSONArray( "packages" );

		String[] PackagePromptArray = new String[MetaPackageArray.length()];
		for( int iterator = 0; iterator < MetaPackageArray.length(); iterator++ ) {
			PackagePromptArray[iterator] = MetaPackageArray.getJSONObject( iterator ).getString( "uid" );
		}

		int packagePrompt = PromptUser( PackagePromptArray, "Please select a package to bootstrap." );



		File SelectionDir = new File( LauncherPath + "/meta/" + PackagePromptArray[packagePrompt] + '/' );
		String[] SelectionDirArray = SelectionDir.list();
		for( int iterator = 0; iterator < SelectionDirArray.length; iterator++ ) {
			SelectionDirArray[iterator] = SelectionDirArray[iterator].substring( 0, SelectionDirArray[iterator].lastIndexOf( '.' ) );
			if( SelectionDirArray[iterator].equals( "index" ) ) {
				SelectionDirArray[iterator] = null;
			}
		}

		int versionRet = PromptUser( SelectionDirArray, "Please select a Version." );

		System.out.println( "Selected Version " + SelectionDirArray[versionRet] );



		JSONObject VersionJSON = new JSONObject( new String( Files.readAllBytes( Paths.get( LauncherPath + "/meta/" + PackagePromptArray[packagePrompt] + "/" + SelectionDirArray[versionRet] + ".json" ) ) ) );

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
			System.gc();
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
	public static int PromptUser( String[] Choices, String Prompt ) {
		int longestChoice = 0;
		for( String Choice : Choices ) {
			if( Choice.length() > longestChoice ) {
				longestChoice = Choice.length();
			}
		}

		StringBuilder separatorBuilder = new StringBuilder();
		for( int iterator = 0; iterator < longestChoice; iterator++ ) {
			separatorBuilder.append( '-' );
		}
		System.out.println( separatorBuilder );

		for( String Choice : Choices ) {
			System.out.println( Choice );
		}
		System.out.println( separatorBuilder );
		Scanner Input = new Scanner( System.in );
		int result = -1;
		while ( result == -1 ) {
			System.out.print( Prompt + " : " );
			String inputLine = Input.nextLine();
			for( int iterator = 0; iterator < Choices.length; iterator++ ) {
				if( Choices[iterator].equals( inputLine ) ) {
					result = iterator;
				}
			}
		}
		return result;
	}
}