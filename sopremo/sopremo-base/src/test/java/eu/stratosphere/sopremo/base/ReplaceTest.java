package eu.stratosphere.sopremo.base;

import java.util.Arrays;
import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;

import eu.stratosphere.sopremo.CoreFunctions;
import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.base.replace.ReplaceBase;
import eu.stratosphere.sopremo.expressions.ArrayAccess;
import eu.stratosphere.sopremo.expressions.ConstantExpression;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.FunctionCall;
import eu.stratosphere.sopremo.expressions.ObjectAccess;
import eu.stratosphere.sopremo.testing.SopremoOperatorTestBase;
import eu.stratosphere.sopremo.testing.SopremoTestPlan;

//import eu.stratosphere.sopremo.SopremoTestPlan;

public class ReplaceTest extends SopremoOperatorTestBase<Replace> {
	@Override
	protected Replace createDefaultInstance(int index) {
		return new Replace().withReplaceExpression(new ArrayAccess(index));
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.EqualCloneTest#initVerifier(nl.jqno.equalsverifier.EqualsVerifier)
	 */
	@Override
	protected void initVerifier(EqualsVerifier<Replace> equalVerifier) {
		super.initVerifier(equalVerifier);
		equalVerifier.withPrefabValues(List.class, Arrays.asList(null, null), Arrays.asList(null, null, null));
	}

	@Test
	public void shouldLookupValuesStrictly() {
		final Replace replace = new Replace().
			withReplaceExpression(new ObjectAccess("fieldToReplace")).
			withDefaultExpression(ReplaceBase.FILTER_RECORDS).
			withDictionaryKeyExtraction(new ArrayAccess(0)).
			withDictionaryValueExtraction(new ArrayAccess(1));
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);
		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", "key1", "field2", 2).
			addObject("field1", 2, "fieldToReplace", "notInList", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "key2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "key1", "field2", 2);

		sopremoPlan.getInput(1).
			addArray("key1", "value1").
			addArray("key2", "value2").
			addArray("key3", "value3");
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", "value1", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "value2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "value1", "field2", 2);

		sopremoPlan.run();
	}

	@Test
	public void shouldKeepValuesNotInDictionary() {
		final Replace replace = new Replace().
			withReplaceExpression(new ObjectAccess("fieldToReplace")).
			withDictionaryKeyExtraction(new ArrayAccess(0)).
			withDictionaryValueExtraction(new ArrayAccess(1));
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);
		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", "key1", "field2", 2).
			addObject("field1", 2, "fieldToReplace", "notInList", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "key2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "key1", "field2", 2);

		sopremoPlan.getInput(1).
			addArray("key1", "value1").
			addArray("key2", "value2").
			addArray("key3", "value3");
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", "value1", "field2", 2).
			addObject("field1", 2, "fieldToReplace", "notInList", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "value2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "value1", "field2", 2);

		sopremoPlan.run();
	}

	@Test
	public void shouldLookupValuesWithDefaultValue() {
		final Replace replace = new Replace();
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);
		final EvaluationContext context = sopremoPlan.getEvaluationContext();
		context.getFunctionRegistry().put(CoreFunctions.class);
		replace.withReplaceExpression(new ObjectAccess("fieldToReplace")).
			withDefaultExpression(new FunctionCall("format", context,
				new ConstantExpression("default %s"), new ObjectAccess("fieldToReplace"))).
			withDictionaryKeyExtraction(new ArrayAccess(0)).
			withDictionaryValueExtraction(new ArrayAccess(1));
		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", "key1", "field2", 2).
			addObject("field1", 2, "fieldToReplace", "notInList", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "key2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "notInList2", "field2", 2);

		sopremoPlan.getInput(1).
			addArray("key1", "value1").
			addArray("key2", "value2").
			addArray("key3", "value3");
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", "value1", "field2", 2).
			addObject("field1", 2, "fieldToReplace", "default notInList", "field2", 2).
			addObject("field1", 3, "fieldToReplace", "value2", "field2", 2).
			addObject("field1", 4, "fieldToReplace", "default notInList2", "field2", 2);

		sopremoPlan.run();
	}

	@Test
	public void shouldLookupArrayValuesStrictly() {

		final ReplaceAll replace = new ReplaceAll().
			withReplaceExpression(new ObjectAccess("fieldToReplace")).
			withDefaultExpression(ReplaceBase.FILTER_RECORDS).
			withDictionaryKeyExtraction(new ArrayAccess(0)).
			withDictionaryValueExtraction(new ArrayAccess(1));
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);

		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 1, 2, 3 }, "field2", 2).
			addObject("field1", 2, "fieldToReplace", new Object[] { 1, "notInList" }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 2, 3 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.getInput(1).
			addArray(1, 11).
			addArray(2, 22).
			addArray(3, 33);
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 11, 22, 33 }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 22, 33 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.run();
	}

	@Test
	public void shouldKeepArrayValuesNotInDictionary() {

		final ReplaceAll replace = new ReplaceAll().
			withReplaceExpression(new ObjectAccess("fieldToReplace")).
			withDictionaryKeyExtraction(new ArrayAccess(0)).
			withDictionaryValueExtraction(new ArrayAccess(1));
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);

		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 1, 2, 3 }, "field2", 2).
			addObject("field1", 2, "fieldToReplace", new Object[] { 1, "notInList" }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 2, 3 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.getInput(1).
			addArray(1, 11).
			addArray(2, 22).
			addArray(3, 33);
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 11, 22, 33 }, "field2", 2).
			addObject("field1", 2, "fieldToReplace", new Object[] { 11, "notInList" }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 22, 33 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.run();
	}

	@Test
	public void shouldLookupArrayValuesWithDefault() {
		final ReplaceAll replace = new ReplaceAll();
		final SopremoTestPlan sopremoPlan = new SopremoTestPlan(replace);
		final EvaluationContext context = sopremoPlan.getEvaluationContext();
		sopremoPlan.getEvaluationContext().getFunctionRegistry().put(CoreFunctions.class);
		replace.setReplaceExpression(new ObjectAccess("fieldToReplace"));
		replace.setDictionaryKeyExtraction(new ArrayAccess(0));
		replace.setDictionaryValueExtraction(new ArrayAccess(1));
		replace.setDefaultExpression(new FunctionCall("format", context, new ConstantExpression("default %s"),
			EvaluationExpression.VALUE));

		sopremoPlan.getInput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 1, 2, 3 }, "field2", 2).
			addObject("field1", 2, "fieldToReplace", new Object[] { 1, "notInList" }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 2, 3 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.getInput(1).
			addArray(1, 11).
			addArray(2, 22).
			addArray(3, 33);
		sopremoPlan.getExpectedOutput(0).
			addObject("field1", 1, "fieldToReplace", new int[] { 11, 22, 33 }, "field2", 2).
			addObject("field1", 2, "fieldToReplace", new Object[] { 11, "default notInList" }, "field2", 2).
			addObject("field1", 3, "fieldToReplace", new int[] { 22, 33 }, "field2", 2).
			addObject("field1", 4, "fieldToReplace", new int[] {}, "field2", 2);

		sopremoPlan.run();
	}
}
