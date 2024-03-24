package net.maximal98.zerolaunch.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.maximal98.zerolaunch.bootstrap.objects.*;
import net.maximal98.zerolaunch.bootstrap.objects.Package;
//imported manually because it could have double meaning with java.lang.Package
//change maybe?

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.*;

public class Main {
	public final static String LOADER_CLASS_DEFAULT = "some epic symbol that can't exist as a file name";
	public final static String MAIN_CLASS_DEFAULT =   "net.minecraft.client.main.Main";
	//ULTIMATE TODO: make gui for lazy/normal people
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		String launcherPath = "error";
		String loaderClass;
		String mainClass;
		boolean checkHashes = true;
		OptionParser optionParser = new OptionParser();
		optionParser.accepts("d").withOptionalArg();
		optionParser.accepts("l").withOptionalArg();
		optionParser.accepts("m").withOptionalArg();
		optionParser.accepts("h");
		optionParser.accepts("s");
		OptionSet options = optionParser.parse(args);

		if( options.has("h")) {
			System.out.println("zerolaunch - tool to bootstrap minecraft");
			System.out.println("\t-h\tdisplay this message");
			System.out.println("\t-d\tPrism/Poly/Multi launcher directory");
			System.out.println("\t-l\tpath to loader class\t(advanced use)");
			System.out.println("\t-m\tcustom main class\t(advanced use)");
			System.out.println("\t-s\tdo not check hashes of libraries and assets (better performance)");
		}
		if( options.has("s") )
			checkHashes = false;

		if ( !options.has("d" ) ) {
			System.out.println("-d is a required option.");
			System.out.println("try -h to see available options");
			System.exit(3);
		} else if(!options.hasArgument("d")) {
			System.out.println("-d requires an Argument");
			System.out.println("try -h to see available options");
			System.exit(3);
		} else {
			launcherPath = options.valueOf("d").toString();
		}

		if (!options.has("l") || !options.hasArgument("l")){
			loaderClass = LOADER_CLASS_DEFAULT;
		} else {
			loaderClass = options.valueOf("l").toString();
		}

		if (!options.has("m") || !options.hasArgument("m")) {
			mainClass = MAIN_CLASS_DEFAULT;
		} else {
			mainClass = options.valueOf("m").toString();
		}

		if( !loaderClass.equals(LOADER_CLASS_DEFAULT) && !( new File(loaderClass).exists() ) ) {
			System.out.println("/!\\ custom loader class does not exist!");
			System.out.println("/!\\ using default loader...");
			System.out.println("/!\\ if you set a custom main class, this probably wont work!");
			try {Thread.sleep(5000);}
			catch (InterruptedException exception) { /*ok*/ }
		}



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

		//this wasn't hard to implement but it was kind of a brainfuck to think about so im leaving a comment here
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
		outputManifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

		Path outputJar = Paths.get( mainPackage.name + "_" + mainPackage.version + ".jar" );
		if(Files.exists(outputJar)) {
			System.out.println("fart");
			Files.move(outputJar, Paths.get( mainPackage.name+"_"+mainPackage.version+"ARCHIVED-"+System.currentTimeMillis()/1000L+".jar" ));
		}

		JarOutputStream outputJarStream = new JarOutputStream( Files.newOutputStream( outputJar ), outputManifest );
		List<String> entryList = new ArrayList<>();

		for ( Package packageObject : packageList ) {
			if( packageObject.formatVersion != 1 ) {
				System.out.println("\t\t\t/!\\ meta file of " + packageObject.uid + ":" + packageObject.version + " is wrong version!");
				System.out.println("\t\t\t/!\\ Bootstrapped JAR file will probably not work!!!");
				try {Thread.sleep(2500);}
				catch( InterruptedException exception) {/*ok boss*/}
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

			for (int i = 0; i < packageObject.libraries.length; i++) { //using "i" and co is ok in for loops (legally binding)
				LibraryArrayEntry library = packageObject.libraries[i];
				if(library.downloads != null ) {
					String name = library.name; //TODO: still cluttered
					System.out.println("loading library " + name + " into jar");
					LibraryArtifact artifact = library.downloads.artifact;
					if( artifact != null ) {
						String URL = artifact.url;

						String filePath = launcherPath
								+ "/libraries/"
								+ name.substring(0, name.indexOf(":")).replaceAll("\\.", "/")
								+ name.substring(name.indexOf(":")).replaceAll(":", "/")
								+ URL.substring(URL.lastIndexOf("/"));

						Path libraryPath = Paths.get(filePath);
						if( checkHashes && !artifact.sha1.equals( CalculateSHA1String(libraryPath) )  )
							//the fact .equals() has to exist is so fucking stupid
							System.out.println(
									"/!\\ library "+name+" has mismatched SHA-1!!\n" +
									"This most likely means that these files have been tampered with!!"
							);
						if (Files.exists(libraryPath)) {
							ZipInputStream libraryZipStream = new ZipInputStream(Files.newInputStream(libraryPath));
							mergeZipIntoJar(libraryZipStream, outputJarStream, entryList, new String[]{"META-INF", ".git"});
						}
					} else {
						System.out.println( "\t/!\\library " + name + " has null artifact at iterator " + i );
					}
				}
			}
		}
		System.out.println( "packing assets." );

		ByteArrayOutputStream assetByteAStream = new ByteArrayOutputStream();
		ZipOutputStream assetZipStream = new ZipOutputStream( assetByteAStream );
		assetZipStream.setMethod(ZipOutputStream.DEFLATED);
		assetZipStream.setLevel(Deflater.NO_COMPRESSION);

		for ( Package packageObject : packageList ) {
			if (packageObject.assetIndex != null) {
				Path indexPath = Paths.get(launcherPath + "/assets/indexes/" + mainPackage.assetIndex.id + ".json");

				JsonNode assetNode = mapper.readTree( new File( launcherPath + "/assets/indexes/" + mainPackage.assetIndex.id + ".json" ) );
				Iterator<Map.Entry<String, JsonNode>> assetIterator = assetNode.get( "objects" ).fields();

				if( !(assetIterator.hasNext()) ) {
					System.out.println("assetIterator has no next()!!!");
				}

				while (assetIterator.hasNext()) {
					Map.Entry<String, JsonNode> assetIndex = assetIterator.next();

					Asset asset = mapper.convertValue( assetIndex.getValue(), Asset.class );

					String hashPath = "/" + asset.hash.substring(0, 2) + "/" + asset.hash;
					String assetKey = assetIndex.getKey();
					Path assetpath = Paths.get(launcherPath + "/assets/objects" + hashPath);
					if( checkHashes && !asset.hash.equals( CalculateSHA1String( assetpath ) ) )
						System.out.println("/!\\ asset "+assetKey+'('+hashPath+") has wrong hash compared to index!");
					//TODO: possibly add a display where it's like "Packing Assets (000/579)" instead of this
					if( assetKey.contains(".ogg") ) {
						System.out.println("Packing File (Fast Mode): " + assetKey );
						addFileIntoZip(assetpath, "assets/" + assetKey, outputJarStream, entryList);
					} else {
						System.out.println("Packing File: " + assetKey );
						addFileIntoZip(assetpath,hashPath,assetZipStream,entryList);
					}
				}
				addFileIntoZip(indexPath, "net/maximal98/zerolaunch/data/assets/indexes/default.json", outputJarStream, entryList);
				//TODO: handle theoretical case of multiple indexes (maybe just append?)
			}
		}
		outputJarStream.putNextEntry( new ZipEntry("assets.zip") );
		outputJarStream.write(assetByteAStream.toByteArray());
		entryList.add("assets.zip");
		outputJarStream.close();
		System.out.println("done!");
	}

	private static void addFileIntoZip(Path FilePath, String JarPath, ZipOutputStream outStream, List<String> entryList ) throws IOException {
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
	private static void mergeZipIntoJar(ZipInputStream inStream, ZipOutputStream outStream, List<String> EntryList, String[] excludeArray ) throws IOException {
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
		char[] chars = new char[prompt.length() + 2];
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
	private static String CalculateSHA1String( Path inputFile ) throws NoSuchAlgorithmException, IOException {
		DigestInputStream digestStream = new DigestInputStream( Files.newInputStream( inputFile ), MessageDigest.getInstance("sha1") );
		while( digestStream.read() != -1 ) {} //TODO: this is an IEEE certified bruh moment
		return new HexBinaryAdapter().marshal( digestStream.getMessageDigest().digest() );
	}
}