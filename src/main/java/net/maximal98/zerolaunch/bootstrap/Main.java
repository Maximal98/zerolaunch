package net.maximal98.zerolaunch.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import net.maximal98.zerolaunch.bootstrap.objects.*;
import net.maximal98.zerolaunch.bootstrap.objects.Package;
import picocli.CommandLine.Option;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
	@Option(names = {"-D", "--launcher-directory"}, required = true, description = "The directory where Multi/Poly/Prism store their data")
	static String launcherPath;

	//TODO: make these use the official loader class if none is given
	@Option(names = {"-L", "--loader-class"}, description = "Path to class file of loader (advanced usage)")
	static String loaderClass; //TODO: implement LoaderClass (-> Loader implement)

	@Option(names = {"-M", "--main-class"}, description = "main class to call in jar (advanced usage)")
	static String mainClass;

	public static void main(String[] args) throws IOException {

		ObjectMapper mapper = new ObjectMapper();

		PackageIndex index = mapper.readValue( new File( launcherPath + "/meta/index.json" ), PackageIndex.class );

		if( index.formatVersion != 1 ) {
			System.out.println( "/meta/index.json is wrong version (!= 1)!" );
			System.exit(1);
		}

		String[] packagePromptArray = new String[index.packages.length];
		for( int iterator = 0; iterator < index.packages.length; iterator++ ) {
			packagePromptArray[iterator] = index.packages[iterator].uid;
		}

		int packagePrompt = promptUser( packagePromptArray, "Please select a package to bootstrap." );
		String[] selectionDirArray = new File( launcherPath + "/meta/" + packagePromptArray[packagePrompt] + '/' ).list();
		int versionIterator = 0;
		String[] versionPromptArray = new String[ selectionDirArray.length-1 ]; //TODO: fix NullPointerException Warn
		for( String Entry : selectionDirArray ) {
			if( Entry.equals( "index.json" ) ) {
				continue;
			}
			versionPromptArray[versionIterator] = Entry.substring( 0, Entry.lastIndexOf( '.' ) );
			versionIterator++;
		}

		int versionRet = promptUser( versionPromptArray, "Please select a Version." );

		System.out.println( "Selected Version " + selectionDirArray[versionRet] );

		Package mainPackage = mapper.readValue( new File( launcherPath
				+ "/meta/" + packagePromptArray[packagePrompt]
				+ "/" + versionPromptArray[versionRet]
				+ ".json" ), Package.class );
		if( mainPackage.formatVersion != 1 ) {
			System.out.println("/meta/"
					+ packagePromptArray[packagePrompt]
					+ "/" + versionPromptArray[versionRet]
					+ ".json is wrong version! (!= 1)");
			System.exit(2);
		}

		List<Package> packageList = new ArrayList<>();
		List<Package> dependencyList = new ArrayList<>();
		List<Package> removeList = new ArrayList<>();
		List<Package> addList = new ArrayList<>();
		dependencyList.add( mainPackage );
		//we love recursive dependencies!!
		while( !dependencyList.isEmpty() ) {
			for ( Package packageObject : dependencyList ) {
				if ( packageObject.requires != null ) {
					for (int iterator = 0; iterator < packageObject.requires.length; iterator++) {
						PackageDependency dependency = packageObject.requires[iterator];
						String dependencyPath = launcherPath
								+ "/meta/"
								+ dependency.uid
								+ "/"
								+ dependency.suggests
								+ ".json";
						addList.add( mapper.readValue( new File( dependencyPath ), Package.class ) );
					}
				}
				packageList.add(packageObject);
				removeList.add(packageObject);
			}
			dependencyList.addAll( addList );
			dependencyList.removeAll( removeList );
		}

		Manifest outputManifest = new Manifest();
		outputManifest.getMainAttributes().put( Attributes.Name.MANIFEST_VERSION, "1.0" );
		outputManifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "net.minecraft.client.main.Main");

		File outputJar = new File( mainPackage.name + "_" + mainPackage.version + ".jar" );
		JarOutputStream outputJarStream = new JarOutputStream( Files.newOutputStream( outputJar.toPath() ), outputManifest );
		List<String> entryList = new ArrayList<>();

		for ( Package packageObject : packageList ) {
			if( packageObject.formatVersion != 1 ) {
				System.out.println("\t\t\t[WARNING] meta file of " + packageObject.uid + ":" + packageObject.version + " is wrong version!");
				System.out.println("\t\t\tBootstrapped JAR file will probably not work!!!");
				continue;
			}
			System.out.println("loading package " + packageObject.uid + ":" + packageObject.version + " into jar" );

			if( packageObject.uid.equals( "net.minecraft" ) ) {
				//TODO: trash specific fix
				String clientName = packageObject.mainJar.name.substring( 0, packageObject.mainJar.name.lastIndexOf( ":" ) );
				clientName = clientName.substring(0, clientName.indexOf(":")).replaceAll("\\.", "/")
						+ clientName.substring(clientName.indexOf(":")).replaceAll(":", "/");


				File clientFolder = new File( launcherPath + "/libraries/" + clientName );
				String clientPath = launcherPath + "/libraries/" + clientName + "/";
				String[] clientListing = clientFolder.list();
				if ( !( clientListing.length == 1 ) ) { //TODO: fix NullPointerException Warn
					for ( String search : clientListing ) {
						if( search.contains( "client" ) ) {
							clientPath = clientPath + search;
							break;
						}
					}
				} else {
					clientPath = clientPath + clientListing[0];
				}

				ZipInputStream clientZipStream = new ZipInputStream( Files.newInputStream( Paths.get( clientPath ) ) );
				mergeZipIntoJar( clientZipStream, outputJarStream, entryList, new String[]{ "META-INF" } );
			}

			for (int iterator = 0; iterator < packageObject.libraries.length; iterator++) {
				if( packageObject.libraries[iterator].downloads != null ) {
					String name = packageObject.libraries[iterator].name;
					System.out.println("loading library " + name + " into jar");
					if( packageObject.libraries[iterator].downloads.artifact != null ) {
						String URL = packageObject.libraries[iterator].downloads.artifact.url;

						String filePath = launcherPath
								+ "/libraries/"
								+ name.substring(0, name.indexOf(":")).replaceAll("\\.", "/")
								+ name.substring(name.indexOf(":")).replaceAll(":", "/")
								+ URL.substring(URL.lastIndexOf("/"));

						File libraryFile = new File(filePath);
						if (libraryFile.exists()) {
							ZipInputStream libraryZipStream = new ZipInputStream(Files.newInputStream(Paths.get(filePath)));
							mergeZipIntoJar(libraryZipStream, outputJarStream, entryList, new String[]{"META-INF", ".git"});
						}
					} else {
						System.out.println( "---library " + name + " has null artifact at iterator " + iterator );
					}
				}
			}
		}
		System.out.println( "loading assets into jar file." );

		//Asset packing
		for ( Package packageObject : packageList ) {
			if (packageObject.assetIndex != null) {
				Path indexPath = Paths.get(launcherPath + "/assets/indexes/" + mainPackage.assetIndex.id + ".json");

				JsonNode assetNode = mapper.readTree( new File( launcherPath + "/assets/indexes/" + mainPackage.assetIndex.id + ".json" ) );
				Iterator<Map.Entry<String, JsonNode>> assetFields = assetNode.get( "objects" ).fields();

				if( !(assetFields.hasNext()) ) {
					System.out.println("assetFields has no next()!!!");
				}
				while (assetFields.hasNext()) {
					Map.Entry<String, JsonNode> assetIndex = assetFields.next();

					Asset asset = mapper.convertValue( assetIndex.getValue(), Asset.class );

					String hashPath = "/" + asset.hash.substring(0, 2) + "/" + asset.hash;
					String assetKey = assetIndex.getKey();
					if( assetKey.contains(".ogg") ) {
						System.out.println("Adding file (Fast Mode) " + assetKey);
						addFileIntoJar(Paths.get(launcherPath + "/assets/objects" + hashPath), "assets/" + assetKey, outputJarStream, entryList);
					} else {
						System.out.println("Adding file " + assetKey);
						addFileIntoJar(Paths.get(launcherPath + "/assets/objects" + hashPath), "net/maximal98/zerolaunch/data/assets/objects" + hashPath, outputJarStream, entryList);
					}
				}

				addFileIntoJar(indexPath, "net/maximal98/zerolaunch/data/assets/indexes/default.json", outputJarStream, entryList);

				outputJarStream.close();
				System.out.println("done!");
			}
		}
	}

	private static void addFileIntoJar(Path FilePath, String JarPath, JarOutputStream outStream, List<String> entryList ) throws IOException {
		byte[] byteBuff = new byte[1024];
		InputStream fileStream = Files.newInputStream( FilePath );
		StringTokenizer pathTokenizer = new StringTokenizer( JarPath );
		while( pathTokenizer.hasMoreElements() ) {
			String token = pathTokenizer.nextToken();
			boolean run = true;
			for( String search : entryList ) {
				if( search.equals( token ) ) {
					run = false;
					break;
				}
			}
			if( run ) {
				outStream.putNextEntry(new ZipEntry(token));
				if (JarPath.endsWith(token)) {
					for (int bytesRead; (bytesRead = fileStream.read(byteBuff)) != -1; ) {
						outStream.write(byteBuff, 0, bytesRead);
					}
				}
				entryList.add(token);
			}
		}
		fileStream.close();
	}
	private static void mergeZipIntoJar(ZipInputStream inStream, JarOutputStream outStream, List<String> EntryList, String[] excludeArray ) throws IOException {
		byte[] byteBuff = new byte[1024];
		for (ZipEntry entry; (entry = inStream.getNextEntry()) != null; ) {
			boolean run = true;
			for (String Search : excludeArray) {
				if (entry.getName().contains(Search)) {
					run = false;
					break;
				}
			}

			for (String Search : EntryList) {
				if (entry.getName().equals(Search)) {
					run = false;
					break;
				}
			}

			if (run) {
				JarEntry newEntry = new JarEntry(entry.getName());
				outStream.putNextEntry(newEntry);
				if (!(newEntry.isDirectory())) {
					for (int bytesRead; (bytesRead = inStream.read(byteBuff)) != -1; ) {
						outStream.write(byteBuff, 0, bytesRead);
					}
				}
				EntryList.add(newEntry.getName());
			}
		}
		inStream.close();
	}
	private static int promptUser(String[] choices, String prompt ) {
		char[] chars = new char[prompt.length() + 2]; //TODO: what kinda caveman uses char in 2024
		Arrays.fill(chars, '-');
		String separator = new String(chars);

		System.out.println( separator );

		for( String choice : choices ) {
			System.out.println( choice );
		}
		System.out.println( separator );
		Scanner Input = new Scanner( System.in );
		int result = -1;
		while ( result == -1 ) {
			System.out.print( prompt + " : " );
			String inputLine = Input.nextLine();
			for( int iterator = 0; iterator < choices.length; iterator++ ) {
				if( choices[iterator].equals( inputLine ) ) {
					result = iterator;
				}
			}
		}
		return result;
	}
}