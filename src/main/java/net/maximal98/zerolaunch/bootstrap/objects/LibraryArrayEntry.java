package net.maximal98.zerolaunch.bootstrap.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LibraryArrayEntry {
	public LibraryDownload downloads;
	public String name;
	public LibraryRule[] rules;
}
