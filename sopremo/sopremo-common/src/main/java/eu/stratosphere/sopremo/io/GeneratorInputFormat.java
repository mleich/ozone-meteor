/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.sopremo.io;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.template.GenericInputSplit;
import eu.stratosphere.pact.generic.io.GenericInputFormat;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.NullNode;

/**
 * Input format that reads values from the config and outputs them.
 * 
 * @author skruse
 * @author Arvid Heise
 */
public class GeneratorInputFormat extends GenericInputFormat<SopremoRecord> {
	/**
	 * Config key which describes the adhoc expression.
	 */
	public static final String ADHOC_EXPRESSION_PARAMETER_KEY = "sopremo.source.generator.expression";

	/**
	 * Iterates over all values.
	 */
	private Iterator<IJsonNode> valueIterator;

	private int numValues = 1;

	@SuppressWarnings("unchecked")
	@Override
	public void configure(final Configuration parameters) {
		super.configure(parameters);

		final EvaluationExpression expression =
			(EvaluationExpression) SopremoUtil.getObject(parameters, ADHOC_EXPRESSION_PARAMETER_KEY, null);
		final IJsonNode value = expression.evaluate(NullNode.getInstance());

		if (value instanceof ArrayNode<?>) {
			this.numValues = ((ArrayNode<?>) value).size();
			this.valueIterator = ((IArrayNode<IJsonNode>) value).iterator();
		}
		else
			this.valueIterator = Collections.singleton(value).iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.pact.common.io.GenericInputFormat#createInputSplits(int)
	 */
	@Override
	public GeneratorInputSplit[] createInputSplits(final int minNumSplits)
			throws IOException {
		final int numInputSplits = Math.min(minNumSplits, this.numValues);
		final GeneratorInputSplit[] inputSplits = new GeneratorInputSplit[numInputSplits];

		int start = 0;
		int end;
		for (int i = 0; i < numInputSplits; i++) {
			end = (i + 1) * this.numValues / numInputSplits;
			inputSplits[i] = new GeneratorInputSplit(i, start, end);
			start = end;
		}

		return inputSplits;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.pact.common.io.GenericInputFormat#getInputSplitType()
	 */
	@Override
	public Class<GeneratorInputSplit> getInputSplitType() {
		return GeneratorInputSplit.class;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.pact.common.io.GenericInputFormat#open(eu.stratosphere
	 * .nephele.template.GenericInputSplit)
	 */
	@Override
	public void open(final GenericInputSplit split) throws IOException {
		super.open(split);

		if (split == null || !(split instanceof GeneratorInputSplit))
			throw new IOException("Invalid InputSplit: " + split);
	}

	@Override
	public boolean reachedEnd() throws IOException {
		return !this.valueIterator.hasNext();
	}

	@Override
	public boolean nextRecord(final SopremoRecord record) throws IOException {
		if (this.reachedEnd())
			throw new IOException("End of input split is reached");

		final IJsonNode value = this.valueIterator.next();
		record.setNode(value);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.pact.common.io.InputFormat#close()
	 */
	@Override
	public void close() throws IOException {
		// nothing to do here
	}

}
