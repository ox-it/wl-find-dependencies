package uk.ac.ox.oucs.plugins;

/**
 * Need a local interface as the standard artifacthandlermanager doesn't have a role so we can't distinguish it from out one.
 * @author buckett
 *
 */
public interface ArtifactHandlerManager extends org.apache.maven.artifact.handler.manager.ArtifactHandlerManager {

}
