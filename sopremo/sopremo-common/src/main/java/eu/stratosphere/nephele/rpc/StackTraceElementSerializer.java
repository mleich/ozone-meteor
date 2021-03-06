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

package eu.stratosphere.nephele.rpc;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * This class implements a {@link Serializer} for the {@link StackTraceElement} type. A custom serializer is necessary
 * since the class {@link StackTraceElement} does not offer a default constructor, i.e. a construction with no
 * arguments. Therefore, the default kryo deserializer cannot instantiate objects of it.
 * <p>
 * This class is not thread-safe.
 * 
 * @author warneke
 */
final class StackTraceElementSerializer extends Serializer<StackTraceElement> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final Kryo kryo, final Output output, final StackTraceElement object) {

		output.writeString(object.getClassName());
		output.writeString(object.getMethodName());
		output.writeString(object.getFileName());
		output.writeInt(object.getLineNumber());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StackTraceElement read(final Kryo kryo, final Input input, final Class<StackTraceElement> type) {

		final String declaringClass = input.readString();
		final String methodName = input.readString();
		final String fileName = input.readString();
		final int lineNumber = input.readInt();

		return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
	}
}
