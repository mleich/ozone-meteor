package eu.stratosphere.sopremo.expressions;

import static eu.stratosphere.sopremo.type.JsonUtil.createArrayNode;
import org.junit.Assert;

import org.junit.Test;

import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.BooleanNode;
import eu.stratosphere.sopremo.type.CoercionException;
import eu.stratosphere.sopremo.type.DoubleNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IntNode;
import eu.stratosphere.sopremo.type.LongNode;
import eu.stratosphere.sopremo.type.TextNode;

public class CoerceExpressionTest extends EvaluableExpressionTest<CoerceExpression> {

	@Override
	protected CoerceExpression createDefaultInstance(final int index) {
		switch (index) {
		case 0:
			return new CoerceExpression(LongNode.class);
		case 1:			
			return new CoerceExpression(IntNode.class);
		default:
			return new CoerceExpression(DoubleNode.class);
		}
	}

	@Test
	public void shouldChangeTypeOfIntToText() {
		final IJsonNode result = new CoerceExpression(TextNode.class).evaluate(IntNode.valueOf(42));

		Assert.assertEquals(TextNode.valueOf("42"), result);
	}

	@Test
	public void shouldChangeTypeOfTextInterpretedNumberToInt() {
		final IJsonNode result = new CoerceExpression(IntNode.class).evaluate(TextNode.valueOf("42"));

		Assert.assertEquals(IntNode.valueOf(42), result);
	}

	@Test
	public void shouldOnlyChangeOuterType() {
		final IJsonNode result = new CoerceExpression(ArrayNode.class).evaluate(
			createArrayNode(IntNode.valueOf(42), BooleanNode.TRUE));

		Assert.assertEquals(createArrayNode(IntNode.valueOf(42), BooleanNode.TRUE), result);
	}

	@Test(expected = CoercionException.class)
	public void shouldThrowExceptionWhenChangingTextToInt() {
		new CoerceExpression(IntNode.class).evaluate(TextNode.valueOf("testname"));
	}
}
