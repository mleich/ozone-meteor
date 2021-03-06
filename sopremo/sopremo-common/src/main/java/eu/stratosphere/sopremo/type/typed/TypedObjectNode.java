package eu.stratosphere.sopremo.type.typed;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IObjectNode;
import eu.stratosphere.sopremo.type.JavaToJsonMapper;
import eu.stratosphere.sopremo.type.JsonToJavaMapper;
import eu.stratosphere.sopremo.type.MissingNode;
import eu.stratosphere.sopremo.type.NullNode;
import eu.stratosphere.sopremo.type.ObjectNode;

/**
 * This class is the abstract superclass for all concrete TypedObjectNodes. It
 * stores a backingObject inside, storing all the object information. It also
 * delegates most of the {@link IObjectNode}s calls to this backingObject.
 * Exceptions are: - public TypedObjectNode {@link TypedObjectNode#clone()} -
 * public IObjectNode {@link TypedObjectNode#put(String fieldName, IJsonNode value)} - public
 * IJsonNode {@link TypedObjectNode#get(String fieldName)}
 * 
 * @author ftschirschnitz
 */

public abstract class TypedObjectNode implements ITypedObjectNode {
	protected IObjectNode backingObject;

	protected TypedObjectNode() {
		this.backingObject = new ObjectNode();
	}

	@Override
	public TypedObjectNode clone() {
		try {
			TypedObjectNode clone = this.getClass().newInstance();
			clone.backingObject = this.backingObject.clone();
			return clone;
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void clear() {
		this.backingObject.clear();
	}

	@Override
	public Class<IObjectNode> getType() {
		return IObjectNode.class;
	}

	@Override
	public void copyValueFrom(IJsonNode otherNode) {
		this.backingObject.copyValueFrom(otherNode);
	}

	@Override
	public int compareTo(IJsonNode other) {
		return this.backingObject.compareTo(other);
	}

	@Override
	public int compareToSameType(IJsonNode other) {
		return this.backingObject.compareTo(other);
	}

	@Override
	public void appendAsString(Appendable appendable) throws IOException {
		this.backingObject.appendAsString(appendable);

	}

	@Override
	public IObjectNode put(String fieldName, IJsonNode value) {
		return this.backingObject.put(fieldName, value);
	}

	public IObjectNode putOrNull(String fieldName, IJsonNode value) {
		return this.backingObject.put(fieldName, value == null ? NullNode.getInstance() : value);
	}

	@Override
	public final <T extends IJsonNode> T get(String fieldName) {
		return this.backingObject.get(fieldName);
	}

	@SuppressWarnings("cast")
	public final <T extends IJsonNode> T getOrNull(String fieldName) {
		final T result = this.backingObject.get(fieldName);
		if (result == MissingNode.getInstance() || result == NullNode.getInstance())
			return null;
		return (T) result;
	}

	public final <T extends ITypedObjectNode> T getTyped(String fieldName, T object) {
		IJsonNode result = this.get(fieldName);
		if (result == MissingNode.getInstance() || result == NullNode.getInstance())
			return null;
		((TypedObjectNode) object).setBackingNode((IObjectNode) result);
		return object;
	}

	public final void putTyped(String fieldName, ITypedObjectNode value) {
		this.backingObject.put(fieldName,
			value == null ? NullNode.getInstance() : ((TypedObjectNode) value).getBackingNode());
	}

	protected static final JavaToJsonMapper JavaToJsonMapperInstance = JavaToJsonMapper.INSTANCE;

	protected static final JsonToJavaMapper JsonToJavaMapperInstance = JsonToJavaMapper.INSTANCE;

	protected final <T extends ITypedObjectNode> T createWrappingObject(Class<T> aDesiredClass) {
		return TypedObjectNodeFactory.getInstance().getTypedObjectForInterface(aDesiredClass);
	}

	@Override
	public void remove(String fieldName) {
		this.backingObject.remove(fieldName);
	}

	@Override
	public IObjectNode putAll(IObjectNode jsonNode) {
		return this.backingObject.putAll(jsonNode);
	}

	@Override
	public Iterator<Entry<String, IJsonNode>> iterator() {
		return this.backingObject.iterator();
	}

	@Override
	public int size() {
		return this.backingObject.size();
	}

	public IObjectNode getBackingNode() {
		return this.backingObject;
	}

	public void setBackingNode(IObjectNode backingNode) {
		this.backingObject = backingNode;
	}
}
