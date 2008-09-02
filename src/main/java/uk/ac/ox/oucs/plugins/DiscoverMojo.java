package uk.ac.ox.oucs.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

/**
 * Attempt to find all the required artifacts for a sakai deployment.
 *
 * @goal discover
 * 
 */
public class DiscoverMojo extends AbstractMojo
{
	/** @parameter expression="${project}" */
	protected MavenProject project;
	
	/** @component */
	protected MavenProjectBuilder mavenProjectBuilder;

	/** @component roleHint="custom" */
	protected ArtifactResolver customResolver;

	/** @component roleHint="custom" */
	protected ArtifactFactory customArtifactFactory;
	
	/** @component roleHint="custom" */
	protected ArtifactMetadataSource customMetadataSource;
		
	/** @parameter expression="${localRepository}" */
	protected ArtifactRepository localRepository;
	
	
	/** @parameter expression="${project.remoteArtifactRepositories}" */
	protected List remoteRepositories;
	
	/** 
	 * @parameter expression="${project.build.directory}/cache/implementations.properties"
	 * @required 
	 */
	private File cachedImplementationFile;
	
	/**
	 * @parameter
	 */
	private List<String> implementationMatches = new ArrayList<String>(); 

	private List<Pattern> implementationPatterns = new ArrayList<Pattern>();
	/**
	 * @parameter
	 */
	private Properties preferred = new Properties();
	
	private Properties cachedImplementations;

	private Set<String> checkedArtifacts = new HashSet<String>();

	private Set<Artifact> toDeploy = new HashSet<Artifact>();

	public void execute() throws MojoExecutionException, MojoFailureException
	{
		getLog().info("Building for " + project);
		initImplementationPatterns();
		
			// Get all the dependency artifacts.
		Set<Artifact> artifacts;
		try {
			artifacts = project
			.createArtifacts(customArtifactFactory, null, null);
		} catch (InvalidDependencyVersionException ideve) {
			// TODO Auto-generated catch block
			throw new MojoExecutionException("Failed to find dependencies", ideve);
		}

		deployAndDependents(artifacts);

	}
	
	private void initImplementationPatterns() throws MojoFailureException
	{
		for (String pattern : implementationMatches)
		{
			implementationPatterns.add(Pattern.compile(pattern));
			
		}
	}

	protected void deployAndDependents(Set<Artifact> artifacts)
			throws MojoExecutionException, MojoFailureException {
		loadCachedImplentations();
		try {
			toDeploy.addAll(artifacts);
			do
			{
				ArtifactResolutionResult arr = customResolver.resolveTransitively(artifacts,
						project.getArtifact(), localRepository, remoteRepositories,
						customMetadataSource, null);
				Set<Artifact> resolvedArtifacts = arr.getArtifacts();
				
				Set<ResolutionNode> arrRes = arr.getArtifactResolutionNodes();
				
				for (ResolutionNode node: arrRes)
				{
					getLog().info(node.getArtifact().getArtifactId());
					for(String artifactId : (List<String>)node.getDependencyTrail())
					{
						getLog().info("  +"+ artifactId);
					}
				}

				Set<Artifact> artifactsToFind = new HashSet<Artifact>();
				
				for (Artifact artifact : resolvedArtifacts)
				{
					if (needsImplementation(artifact))
					{
						getLog().debug(
								"Needed : " + artifact.toString() + " "
										+ artifact.getDependencyTrail());
						artifactsToFind.add(artifact);
					}
					else
					{
						getLog().debug(
								"Ignored : " + artifact.toString() + " "
										+ artifact.getDependencyTrail());
					}

				}

				artifacts = new HashSet<Artifact>();
				for (Artifact artifact : artifactsToFind)
				{
					String artifactKey = artifact.getGroupId() + ":"
							+ artifact.getArtifactId();
					if (!checkedArtifacts.contains(artifactKey))
					{
						toDeploy.add(artifact);
						MavenProject project = findImplementation(artifact);
						if (project != null)
						{
							getLog().info(
									"Found implementation: " + artifactKey + " to "
											+ project.getGroupId() + ":"
											+ project.getArtifactId() + ":"
											+ project.getVersion());
							Set<Artifact> projectArtifacts = project.createArtifacts(
									customArtifactFactory, null, null);
							//artifacts.addAll(projectArtifacts);
							if (shouldExpand(project))
							{
								
								toDeploy.addAll(projectArtifacts);
							}
							artifacts.add(project.getArtifact());
							toDeploy.add(project.getArtifact());
							
						}
						else
						{
							getLog().info("Unresolved implementation: " + artifactKey);

						}
						checkedArtifacts.add(artifactKey);
					}
				}
			}
			while (artifacts.size() > 0);
		}
		catch (InvalidDependencyVersionException e1)
		{
			throw new MojoExecutionException("Failed to create artifacts", e1);
		}
		catch (ArtifactResolutionException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (ArtifactNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			saveCachedImplmentations();
		}
		

		addToDependencies();
		
	}

	private void addToDependencies() {
		List dependencies = project.getDependencies();
		for (Artifact artifact : toDeploy)
		{
			getLog().info("Deploy: " + artifact.toString());
			MavenProject project = locateImplmentation(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
			//deployProjectToContainer(project);
			Dependency dependency = new Dependency();
			dependency.setArtifactId(artifact.getArtifactId());
			dependency.setGroupId(artifact.getGroupId());
			dependency.setVersion(artifact.getVersion());
			dependency.setType(artifact.getType());
			dependencies.add(dependency);
		}
		
		project.setDependencies(dependencies);
		
		toDeploy = new HashSet<Artifact>();
	}

	private void loadCachedImplentations()
	{
		cachedImplementations = new Properties();
		if (!cachedImplementationFile.exists())
		{
			return;
		}
		try
		{
			cachedImplementations.load(new FileInputStream(cachedImplementationFile));
		}
		catch (FileNotFoundException e)
		{
			getLog().warn("Couldn't find cache file to load from: "+ cachedImplementationFile.getPath());
		}
		catch (IOException e)
		{
			getLog().warn("Problem reading file: "+ cachedImplementationFile.getPath());
		}
	}
	
	private void saveCachedImplmentations()
	{
		try
		{
			cachedImplementationFile.getParentFile().mkdirs();
			cachedImplementations.store(new FileOutputStream(cachedImplementationFile), "Automatically Cached");
		}
		catch (FileNotFoundException e)
		{
			getLog().warn("Couldn't find cache file to save to: "+ cachedImplementationFile.getPath());
		}
		catch (IOException e)
		{
			getLog().warn("Problem writing to file: "+ cachedImplementationFile.getPath());
		}
	}

	protected boolean shouldExpand(MavenProject project)
	{
		return Boolean.valueOf(project.getProperties().getProperty("bundle.expand", "false"));
	}

	private boolean needsImplementation(Artifact artifact)
	{
		String artifactId = artifact.getGroupId()+ ":"+ artifact.getArtifactId()+ ":"+ artifact.getType()+ ":"+ artifact.getScope() ;
		for (Pattern pattern: implementationPatterns) 
		{
			if (pattern.matcher(artifactId).matches())
				return true;
		}
		return false;
	}

	public MavenProject findImplementation(Artifact artifact)
	{
		MavenProject project = null;
		String groupId = artifact.getGroupId();
		String artifactId = artifact.getArtifactId();
		String version = artifact.getVersion();
		
		String key = groupId + ":" + artifactId;
		String prefer = preferred.getProperty(key);
		if (prefer != null)
		{
			if (prefer.equalsIgnoreCase("exclude"))
			{
				return null;
			}
			getLog().debug("Prefered: "+ prefer);
			String[] parts = prefer.split(":");
			if (parts.length > 0)
			{
				groupId = parts[0];
				if (parts.length > 1)
				{
					artifactId = parts[1];
					if (parts.length > 2) version = parts[2];
				}
			}
			project = locateImplmentation(groupId, artifactId, version);

		}
		
		else
		{
			if (cachedImplementations.containsKey(key))
			{
				String implementationId = cachedImplementations.getProperty(key);
				if (!"none".equals(implementationId))
				{
					project =  locateImplmentation(groupId, implementationId, version);
				}
			}
			else
			{
				if (artifactId.endsWith("-api"))
				{	
					String baseId = artifactId
					.substring(0, artifactId.length() - "-api".length());
					String implmentationId = baseId + "-bundle";
					project = locateImplmentation(groupId, implmentationId, version);
					if (project == null)
					{
						implmentationId = baseId + "-pack";
						project = locateImplmentation(groupId, implmentationId, version);
						if (project == null)
						{
							implmentationId = baseId + "-components";
							project = locateImplmentation(groupId, implmentationId, version);
							if (project == null)
							{
								// OSP fix
								implmentationId = baseId + "-component";
								project = locateImplmentation(groupId, implmentationId, version);
							}
						}
					}
				}
				else if (artifactId.endsWith("-tool"))
				{
					String baseId = artifactId
					.substring(0, artifactId.length() - "-tool".length());
					String implmentationId = baseId + "-help";
					project = locateImplmentation(groupId, implmentationId, version);
				}
				cachedImplementations.setProperty(key, (project == null)?"none":project.getArtifactId());
			}
			

		}
		return project;
	}

	private MavenProject locateImplmentation(String groupId, String artifactId,
			String version)
	{
		MavenProject project = null;
		try
		{
			Artifact implProjectArtifact = customArtifactFactory.createProjectArtifact(groupId,
					artifactId, version);
			// Check we can find it.
			customResolver.resolve(implProjectArtifact, remoteRepositories, localRepository);
			project = mavenProjectBuilder.buildFromRepository(implProjectArtifact,
					remoteRepositories, localRepository);
			// Projects built from the repository don't have our artifact handler.
			// Things all blow up if we try to have a custom MavenProjectBuilder component.
			project.setArtifact(implProjectArtifact);
		}
		catch (ArtifactNotFoundException anfe)
		{

		}
		catch (ArtifactResolutionException are)
		{

		}
		catch (ProjectBuildingException pbe)
		{
		}
		return project;
	}

}