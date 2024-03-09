package net.maximal98.zerolaunch.bootstrap.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true) //this is necessary because some packages do not implement certain things
public class Package {
	// public String[] traits; //the field is called +traits, actually. this will fail.
	public PackageAssetIndex assetIndex;
	public String[] compatibleJavaMajors;
	@JsonProperty( required = true )
	public int formatVersion;
	@JsonProperty( required = true )
	public LibraryArrayEntry[] libraries;
	@JsonProperty( required = true )
	public String mainClass;
	public LibraryArrayEntry[] mavenFiles;
	public LibraryArrayEntry mainJar;
	@JsonProperty( required = true )
	public String minecraftArguments;
	@JsonProperty( required = true )
	public String name;
	@JsonProperty( required = true )
	public int order;
	@JsonProperty( required = true )
	public String releaseTime;
	public PackageDependency[] requires;
	public String type;
	@JsonProperty( required = true )
	public String uid;
	@JsonProperty( required = true )
	public String version;
}
