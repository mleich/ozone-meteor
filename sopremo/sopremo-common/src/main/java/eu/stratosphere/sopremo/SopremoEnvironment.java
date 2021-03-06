/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.serialization.SopremoRecordLayout;

/**
 * @author Arvid Heise
 */
public class SopremoEnvironment {
	/**
	 * 
	 */
	private static final ThreadLocal<SopremoEnvironment> INSTANCE = new ThreadLocal<SopremoEnvironment>() {
		@Override
		protected SopremoEnvironment initialValue() {
			return new SopremoEnvironment();
		};
	};

	private EvaluationContext evaluationContext = new EvaluationContext();

	private ClassLoader classLoader = getClass().getClassLoader();

	private Configuration configuration;

	private SopremoRecordLayout layout;

	public static SopremoEnvironment getInstance() {
		return INSTANCE.get();
	}

	/**
	 * Returns the evaluationContext.
	 * 
	 * @return the evaluationContext
	 */
	public EvaluationContext getEvaluationContext() {
		return this.evaluationContext;
	}

	/**
	 * Returns the classLoader.
	 * 
	 * @return the classLoader
	 */
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

	/**
	 * Returns the configuration.
	 * 
	 * @return the configuration
	 */
	public Configuration getConfiguration() {
		return this.configuration;
	}

	/**
	 * Returns the layout.
	 * 
	 * @return the layout
	 */
	public SopremoRecordLayout getLayout() {
		return this.layout;
	}

	/**
	 * Sets the evaluationContext to the specified value.
	 * 
	 * @param evaluationContext
	 *        the evaluationContext to set
	 */
	public void setEvaluationContext(EvaluationContext evaluationContext) {
		if (evaluationContext == null)
			throw new NullPointerException("evaluationContext must not be null");

		this.evaluationContext = evaluationContext;
	}

	/**
	 * Sets the classLoader to the specified value.
	 * 
	 * @param classLoader
	 *        the classLoader to set
	 */
	public void setConfiguration(Configuration configuration) {
		if (configuration == null)
			throw new NullPointerException("configuration must not be null");

		this.configuration = configuration;
		this.classLoader = configuration.getClassLoader();
		this.evaluationContext = SopremoUtil.getEvaluationContext(configuration);
		this.layout = SopremoUtil.getLayout(configuration);
	}
}
