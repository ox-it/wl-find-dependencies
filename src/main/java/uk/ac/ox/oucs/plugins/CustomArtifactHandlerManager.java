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

import java.util.Map;

import org.apache.maven.artifact.handler.ArtifactHandler;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl </a>
 * @version $Id: DefaultArtifactHandlerManager.java,v 1.1.1.1 2004/08/09
 *          18:37:32 jvanzyl Exp $
 */
public class CustomArtifactHandlerManager implements ArtifactHandlerManager
{
	public static final class MissingDependencyArtifactHandler implements
			ArtifactHandler {
		private final ArtifactHandler handler;

		public MissingDependencyArtifactHandler(ArtifactHandler handler) {
			this.handler = handler;
		}

		public String getClassifier()
		{
			return handler.getClassifier();
		}

		public String getDirectory()
		{
			return handler.getDirectory();
		}

		public String getExtension()
		{
			return handler.getExtension();
		}

		public String getLanguage()
		{
			return handler.getLanguage();
		}

		public String getPackaging()
		{
			return handler.getPackaging();
		}

		public boolean isAddedToClasspath()
		{
			return handler.isAddedToClasspath();
		}

		public boolean isIncludesDependencies()
		{
			return false;
		}
	}

	private org.apache.maven.artifact.handler.manager.ArtifactHandlerManager originalArtifactManager;

	public ArtifactHandler getArtifactHandler(String type)
	{

		final ArtifactHandler handler = originalArtifactManager.getArtifactHandler(type);

		return new MissingDependencyArtifactHandler(handler);
	}

	public void addHandlers(Map handlers) {
		originalArtifactManager.addHandlers(handlers);
	}

}
