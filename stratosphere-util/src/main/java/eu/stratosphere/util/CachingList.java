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
package eu.stratosphere.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrays;

/**
 * A list implementation that does not release removed elements but allows them to be reused.
 * 
 * @author Arvid Heise
 */
public class CachingList<T> extends ObjectArrayList<T> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7946169971753399385L;

	private int usedElements;

	@Override
	public void add(int index, T element) {
		this.checkRange(index, this.usedElements + 1);

		if (index < this.usedElements) // insert
			super.add(index, element);
		else if (this.usedElements == super.size()) // append
			super.add(element);
		else
			super.set(index, element);

		this.usedElements++;
	}

	@Override
	public boolean add(T element) {
		if (this.usedElements == super.size()) // append
			super.add(element);
		else
			super.set(this.usedElements, element);

		this.usedElements++;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.AbstractList#clear()
	 */
	@Override
	public void clear() {
		this.usedElements = 0;
	}

	@Override
	public T remove(int index) {
		this.checkRange(index, this.usedElements);

		final T oldObject = super.remove(index);
		super.add(oldObject);
		this.usedElements--;
		return oldObject;
	}

	private void checkRange(int index, int size) {
		if (index >= size)
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.AbstractList#get(int)
	 */
	@Override
	public T get(int index) {
		this.checkRange(index, this.usedElements);

		return super.get(index);
	}

	/**
	 * Reactivates the last element if such an element exists.<br />
	 * Do not call {@link #add(Object)} in the successful case.
	 * 
	 * @return a reactivated element or null
	 */
	public T reuseUnusedElement() {
		if (super.size() == this.usedElements)
			return null;
		return super.get(this.usedElements++);
	}

	public T getUnusedElement() {
		if (super.size() == this.usedElements)
			return null;
		return super.get(this.usedElements);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.AbstractCollection#size()
	 */
	@Override
	public int size() {
		return this.usedElements;
	}

	/*
	 * (non-Javadoc)
	 * @see it.unimi.dsi.fastutil.objects.ObjectArrayList#size(int)
	 */
	@Override
	public void size(int size) {
		if (size > this.a.length)
			ensureCapacity(size);
		if (size > this.size) {
			ObjectArrays.fill(this.a, this.size, size, null);
			this.size = size;
		}
		this.usedElements = size;
	}

	/*
	 * (non-Javadoc)
	 * @see it.unimi.dsi.fastutil.objects.ObjectArrayList#size(int)
	 */
	public void size(int size, T defaultElement) {
		if (size > this.a.length)
			ensureCapacity(size);
		if (size > this.size) {
			ObjectArrays.fill(this.a, this.size, size, defaultElement);
			this.size = size;
		}
		this.usedElements = size;
	}
}
