package net.maximal98.zerolaunch.bootstrap.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LibraryDownload {
	public LibraryArtifact artifact;

}
