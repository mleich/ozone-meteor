package eu.stratosphere.sopremo.testing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.fs.FSDataInputStream;
import eu.stratosphere.nephele.fs.FileSystem;
import eu.stratosphere.nephele.fs.Path;
import eu.stratosphere.pact.common.contract.FileDataSink;
import eu.stratosphere.pact.common.contract.FileDataSource;
import eu.stratosphere.pact.common.contract.GenericDataSink;
import eu.stratosphere.pact.common.contract.GenericDataSource;
import eu.stratosphere.pact.common.plan.PactModule;
import eu.stratosphere.pact.common.plan.Plan;
import eu.stratosphere.pact.generic.contract.Contract;
import eu.stratosphere.pact.testing.AssertUtil;
import eu.stratosphere.pact.testing.GenericTestPlan;
import eu.stratosphere.pact.testing.GenericTestRecords;
import eu.stratosphere.pact.testing.TypeConfig;
import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.io.JsonFormat;
import eu.stratosphere.sopremo.io.JsonFormat.JsonInputFormat;
import eu.stratosphere.sopremo.io.JsonParser;
import eu.stratosphere.sopremo.io.Sink;
import eu.stratosphere.sopremo.io.SopremoFormat;
import eu.stratosphere.sopremo.io.SopremoFormat.SopremoFileInputFormat;
import eu.stratosphere.sopremo.io.Source;
import eu.stratosphere.sopremo.operator.JsonStream;
import eu.stratosphere.sopremo.operator.Operator;
import eu.stratosphere.sopremo.operator.OperatorNavigator;
import eu.stratosphere.sopremo.operator.PlanWithSopremoPostPass;
import eu.stratosphere.sopremo.operator.SopremoModule;
import eu.stratosphere.sopremo.operator.SopremoPlan;
import eu.stratosphere.sopremo.pact.RecordToJsonIterator;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.sopremo.serialization.SopremoRecordLayout;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.JsonUtil;
import eu.stratosphere.util.AbstractIterator;
import eu.stratosphere.util.IteratorUtil;
import eu.stratosphere.util.dag.OneTimeTraverser;

/**
 * The primary resource to test one or more implemented {@link Operator}s. It is created in a unit test and performs the
 * following operations.
 * <ul>
 * <li>Adds {@link MockupSource}s and {@link MockupSink}s if not explicitly specified,
 * <li>locally runs the {@link Operator}s,
 * <li>checks the results against the expectations specified in {@link #getExpectedOutput(int)}, and
 * <li>provides comfortable access to the results with {@link #getActualOutput(int)}. <br>
 * To investigate the execution of the Operators the execution steps can be traced. The typical usage is inside a unit
 * test and might look like one of the following examples. <br>
 * <b>Test complete plan<br>
 * <code><pre>
 * Source source = new Source(...);
 * 
 * Identity projection = new Identity();
 * projection.setInputs(source);
 * 
 * Sink sink = new Sink(...);
 * sink.setInputs(projection);
 * 
 * SopremoTestPlan testPlan = new SopremoTestPlan(sink);
 * testPlan.run();
 * </pre></code> <b>SopremoTestPlan with MockupSource and MockupSink<br>
 * <code><pre>
 * Identity identity = new Identity();
 * SopremoTestPlan testPlan = new SopremoTestPlan(identity);
 * testPlan.getInput(0).
 * 	addValue(value1).
 * 	addValue(value2);
 * testPlan.getExpectedOutput(0).
 * 	addValue(value1).
 * 	addValue(value2);
 * testPlan.run();
 * </pre></code>
 */
public class SopremoTestPlan {
	/**
	 * @author arv
	 */
	public class SopremoRecordTestPlan extends GenericTestPlan<SopremoRecord, SopremoTestRecords> {

		protected SopremoRecordTestPlan(Collection<? extends Contract> contracts) {
			super(contracts);
		}

		protected SopremoRecordTestPlan(Contract... contracts) {
			super(contracts);
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.pact.testing.GenericTestPlan#createPlan(java.util.Collection)
		 */
		@Override
		protected Plan createPlan(Collection<GenericDataSink> wrappedSinks) {
			return new PlanWithSopremoPostPass(wrappedSinks);
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.pact.testing.GenericTestPlan#createTestRecords(eu.stratosphere.pact.testing.TypeConfig)
		 */
		@Override
		protected SopremoTestRecords createTestRecords(TypeConfig<SopremoRecord> typeConfig) {
			return new SopremoTestRecords(typeConfig);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return PactModule.valueOf(getSinks()).toString();
		}
	}

	private Input[] inputs;

	private ActualOutput[] actualOutputs;

	private ExpectedOutput[] expectedOutputs;

	private SopremoRecordTestPlan testPlan;

	private final EvaluationContext evaluationContext = new EvaluationContext();

	private boolean trace;

	private int dop = -1;

	/**
	 * Initializes a SopremoTestPlan with the given number of in/outputs. All inputs are initialized with {@link Input}s
	 * and all expected/actual outputs are initialized with {@link ExpectedOutput}s/{@link ActualOutput}s.
	 * 
	 * @param numInputs
	 *        the number of inputs that should be initialized
	 * @param numOutputs
	 *        the number of outputs that should be initialized
	 */
	public SopremoTestPlan(final int numInputs, final int numOutputs) {
		this.initInputsAndOutputs(numInputs, numOutputs);
	}

	/**
	 * Initializes a SopremoTestPlan with the given {@link Operator}s. For each Operator that has no source or no sink,
	 * {@link MockupSource}s and {@link MockupSink}s are automatically set.
	 * 
	 * @param sinks
	 *        the Operators that should be executed
	 */
	public SopremoTestPlan(final Operator<?>... sinks) {
		this(Arrays.asList(sinks));
	}

	/**
	 * Initializes a SopremoTestPlan with the given {@link Operator}s. For each Operator that has no source or no sink,
	 * {@link MockupSource}s and {@link MockupSink}s are automatically set.
	 * 
	 * @param sinks
	 *        the Operators that should be executed
	 */
	public SopremoTestPlan(final List<Operator<?>> sinks) {
		final List<JsonStream> unconnectedOutputs = new ArrayList<JsonStream>();
		final List<Operator<?>> unconnectedInputs = new ArrayList<Operator<?>>();
		for (final Operator<?> operator : sinks) {
			unconnectedOutputs.addAll(operator.getOutputs());
			if (operator.getNumOutputs() == 0)
				unconnectedOutputs.add(operator);
		}

		for (final Operator<?> operator : OneTimeTraverser.INSTANCE
			.getReachableNodes(sinks, OperatorNavigator.INSTANCE))
			if (operator instanceof Source)
				unconnectedInputs.add(operator);
			else
				for (final JsonStream input : operator.getInputs())
					if (input == null)
						unconnectedInputs.add(operator);

		this.inputs = new Input[unconnectedInputs.size()];
		for (int index = 0; index < this.inputs.length; index++) {
			this.inputs[index] = new Input(this, index);
			final Operator<?> unconnectedNode = unconnectedInputs.get(index);
			if (unconnectedNode instanceof Source)
				this.setInputOperator(index, (Source) unconnectedNode);
			else {
				final List<JsonStream> missingInputs = new ArrayList<JsonStream>(unconnectedNode.getInputs());
				for (int missingIndex = 0; missingIndex < missingInputs.size(); missingIndex++)
					if (missingInputs.get(missingIndex) == null) {
						missingInputs.set(missingIndex, this.inputs[index].getOperator().getOutput(0));
						break;
					}
				unconnectedNode.setInputs(missingInputs);
			}
		}
		this.actualOutputs = new ActualOutput[unconnectedOutputs.size()];
		this.expectedOutputs = new ExpectedOutput[unconnectedOutputs.size()];
		for (int index = 0; index < this.actualOutputs.length; index++) {
			this.actualOutputs[index] = new ActualOutput(index);
			if (unconnectedOutputs.get(index) instanceof Sink)
				this.actualOutputs[index].setOperator((Sink) unconnectedOutputs.get(index));
			else
				this.actualOutputs[index].getOperator().setInput(0, unconnectedOutputs.get(index));
			this.expectedOutputs[index] = new ExpectedOutput(this, index);
		}
	}

	/**
	 * Initializes SopremoTestPlan.
	 */
	SopremoTestPlan() {
	}

	/**
	 * If called, the execution of the Operators will be traced by {@link SopremoUtil}.
	 */
	public void trace() {
		this.trace = true;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		final SopremoTestPlan other = (SopremoTestPlan) obj;
		return Arrays.equals(this.inputs, other.inputs) && Arrays.equals(this.expectedOutputs, other.expectedOutputs)
			&& this.getSopremoPlan().equals(other.getSopremoPlan());
	}

	/**
	 * Returns the output of the operator that is associated with the given index. The return value is only meaningful
	 * after a {@link #run()}.
	 * 
	 * @param index
	 *        the index of the operator
	 * @return the output of the execution of the specified operator
	 */
	public ActualOutput getActualOutput(final int index) {
		return this.actualOutputs[index];
	}

	/**
	 * Returns the output of the operator that is associated with the given {@link JsonStream}. The return value is only
	 * meaningful
	 * after a {@link #run()}. Should no operator have an association with the given stream: null will be returned.
	 * 
	 * @param stream
	 *        the stream
	 * @return the output of the execution of the specified operator
	 */
	public ActualOutput getActualOutputForStream(final JsonStream stream) {
		for (final ActualOutput output : this.actualOutputs)
			if (output.getOperator().getInput(0) == stream.getSource())
				return output;
		return null;
	}

	/**
	 * Returns the {@link EvaluationContext} of this plan.
	 * 
	 * @return the context
	 */
	public EvaluationContext getEvaluationContext() {
		return this.evaluationContext;
	}

	/**
	 * Returns the expected output of the operator that is associated with the given index.
	 * 
	 * @param index
	 *        the index of the operator
	 * @return the expected output
	 */
	public ExpectedOutput getExpectedOutput(final int index) {
		return this.expectedOutputs[index];
	}

	/**
	 * Returns the expected output of the operator that is associated with the given {@link JsonStream}.
	 * 
	 * @param stream
	 *        the stream
	 * @return the expected output
	 */
	public ExpectedOutput getExpectedOutputForStream(final JsonStream stream) {
		return this.expectedOutputs[this.getActualOutputForStream(stream).getIndex()];
	}

	/**
	 * Returns the input for the given index.
	 * 
	 * @param index
	 *        the index
	 * @return the input at the specified index
	 */
	public Input getInput(final int index) {
		return this.inputs[index];
	}

	/**
	 * Returns the input that is associated with the given stream.
	 * 
	 * @param stream
	 *        the stream
	 * @return the input
	 */
	public Input getInputForStream(final JsonStream stream) {
		for (final Input input : this.inputs)
			if (input.getOperator().getOutput(0) == stream.getSource())
				return input;
		return null;
	}

	/**
	 * Returns the input operator for the given index.
	 * 
	 * @param index
	 *        the index
	 * @return the input operator
	 */
	public Source getInputOperator(final int index) {
		return this.getInput(index).getOperator();
	}

	/**
	 * Returns all input operators for the given range of indices.
	 * 
	 * @param from
	 *        the start index (inclusive)
	 * @param to
	 *        the end index (exclusive)
	 * @return array of the input operators
	 */
	public Source[] getInputOperators(final int from, final int to) {
		final Source[] operators = new Source[to - from];
		for (int index = 0; index < operators.length; index++)
			operators[index] = this.getInputOperator(from + index);
		return operators;
	}

	/**
	 * Returns the output operator for the given index.
	 * 
	 * @param index
	 *        the index
	 * @return the output operator
	 */
	public Sink getOutputOperator(final int index) {
		return this.getActualOutput(index).getOperator();
	}

	/**
	 * Returns alls output operators for the given range of indices.
	 * 
	 * @param from
	 *        the start index (inclusive)
	 * @param to
	 *        the end index (exclusive)
	 * @return array of the output operators
	 */
	public Sink[] getOutputOperators(final int from, final int to) {
		final Sink[] operators = new Sink[to - from];
		for (int index = 0; index < operators.length; index++)
			operators[index] = this.getOutputOperator(from + index);
		return operators;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(this.inputs);
		result = prime * result + Arrays.hashCode(this.actualOutputs);
		result = prime * result + Arrays.hashCode(this.expectedOutputs);
		return result;
	}

	/**
	 * Initializes the given number of in-/outputs.
	 * 
	 * @param numInputs
	 *        the number of inputs
	 * @param numOutputs
	 *        the number of outputs
	 */
	protected void initInputsAndOutputs(final int numInputs, final int numOutputs) {
		this.inputs = new Input[numInputs];
		for (int index = 0; index < numInputs; index++)
			this.inputs[index] = new Input(this, index);
		this.expectedOutputs = new ExpectedOutput[numOutputs];
		for (int index = 0; index < numOutputs; index++)
			this.expectedOutputs[index] = new ExpectedOutput(this, index);
		this.actualOutputs = new ActualOutput[numOutputs];
		for (int index = 0; index < numOutputs; index++)
			this.actualOutputs[index] = new ActualOutput(index);
	}

	/**
	 * Executes all operators. If expected values have been specified, the actual outputs values are
	 * compared to the expected values.
	 */
	public void run() {
		final SopremoPlan sopremoPlan = this.getSopremoPlan();
		final Collection<Contract> sinks = sopremoPlan.assemblePact();
		this.testPlan = new SopremoRecordTestPlan(sinks);
		SopremoRecordLayout layout = sopremoPlan.getLayout();
		for (final Input input : this.inputs)
			input.prepare(this.testPlan, layout);
		for (final ExpectedOutput output : this.expectedOutputs)
			output.prepare(this.testPlan, layout);
		if (this.dop > 0)
			this.testPlan.setDegreeOfParallelism(this.dop);
		if (this.trace)
			SopremoUtil.trace();
		try {
			this.testPlan.run();
		} finally {
			if (this.trace)
				SopremoUtil.untrace();
		}
		for (final ActualOutput output : this.actualOutputs)
			output.load(this.testPlan);
	}

	private SopremoPlan getSopremoPlan() {
		final SopremoPlan sopremoPlan = new SopremoPlan();
		sopremoPlan.setContext(this.evaluationContext);
		sopremoPlan.setSinks(this.getOutputOperators(0, this.expectedOutputs.length));
		return sopremoPlan;
	}

	/**
	 * Sets the input operator of the specified index. The operator that is saved at the specified index will be
	 * overwritten.
	 * 
	 * @param index
	 *        the index where the operator should be saved
	 * @param operator
	 *        the new operator
	 */
	public void setInputOperator(final int index, final Source operator) {
		this.inputs[index].setOperator(operator);
	}

	/**
	 * Sets the output operator of the specified index. The operator that is saved at the specified index will be
	 * overwritten.
	 * 
	 * @param index
	 *        the index where the operator should be saved
	 * @param operator
	 *        the new operator
	 */
	public void setOutputOperator(final int index, final Sink operator) {
		this.actualOutputs[index].setOperator(operator);
	}

	/**
	 * Returns the degree of parallelism of the
	 * test plan.
	 */
	public void setDegreeOfParallelism(final int dop) {
		if (dop < 1)
			throw new IllegalArgumentException("Degree of parallelism must be greater than 0!");
		this.dop = dop;
	}

	@Override
	public String toString() {
		return SopremoModule.valueOf(this.getOutputOperators(0, this.actualOutputs.length)).toString();
	}

	/**
	 * Represents the actual output of a {@link SopremoTestPlan}.
	 */
	public static class ActualOutput extends InternalChannel<Sink, ActualOutput> {
		private GenericTestRecords<SopremoRecord> actualRecords;

		/**
		 * Initializes an ActualOutput with the given index.
		 * 
		 * @param index
		 *        the index that should be used
		 */
		public ActualOutput(final int index) {
			super(new MockupSink(index), index);
		}

		/**
		 * Initializes SopremoTestPlan.ActualOutput.
		 */
		ActualOutput() {
		}

		/**
		 * Loads the actual output values into this ActualOutput.
		 * 
		 * @param testPlan
		 *        the {@link GenericTestPlan<SopremoRecord, SopremoTestRecords>} where the actual output values should
		 *        be loaded from
		 */
		void load(GenericTestPlan<SopremoRecord, SopremoTestRecords> testPlan) {
			this.actualRecords = testPlan.getActualOutput(this.getIndex());
		}

		public Iterator<IJsonNode> unsortedIterator() {
			if (this.actualRecords == null)
				throw new IllegalStateException("Can only access actual output after a complete test run");
			final RecordToJsonIterator<IJsonNode> iterator = new RecordToJsonIterator<IJsonNode>();
			iterator.setIterator(this.actualRecords.unsortedIterator());
			return iterator;
		}
		
		@Override
		public Iterator<IJsonNode> iterator() {
			if (this.actualRecords == null)
				throw new IllegalStateException("Can only access actual output after a complete test run");
			final RecordToJsonIterator<IJsonNode> iterator = new RecordToJsonIterator<IJsonNode>();
			iterator.setIterator(this.actualRecords.iterator());
			return iterator;
		}
	}

	static abstract class ModifiableChannel<O extends Operator<?>, C extends ModifiableChannel<O, C>> extends
			InternalChannel<O, C> implements Iterable<IJsonNode> {
		private final List<IJsonNode> values = new ArrayList<IJsonNode>();

		protected SopremoTestRecords testRecords;

		private boolean empty = false;

		private final transient SopremoTestPlan testPlan;

		public ModifiableChannel(final SopremoTestPlan testPlan, final O operator, final int index) {
			super(operator, index);
			this.testPlan = testPlan;
		}

		/**
		 * @param testPlan
		 * @param layout
		 * @return
		 */
		abstract SopremoTestRecords getTestRecords(SopremoRecordTestPlan testPlan, SopremoRecordLayout layout);

		/**
		 * Initializes SopremoTestPlan.ModifiableChannel.
		 */
		ModifiableChannel() {
			this.testPlan = null;
		}

		protected EvaluationContext getContext() {
			return this.testPlan.getEvaluationContext();
		}

		@SuppressWarnings("unchecked")
		public C add(final IJsonNode value) {
			this.empty = false;
			this.file = null;
			this.values.add(value);
			return (C) this;
		}

		public void load(final String file) throws IOException {
			try {
				if (!FileSystem.get(new URI(file)).exists(new Path(file)))
					throw new FileNotFoundException();
			} catch (final URISyntaxException e) {
				throw new IllegalArgumentException(String.format(
					"File %s is not a valid URI", file));
			}

			this.empty = false;
			this.values.clear();
			this.file = file;
		}

		public C addObject(final Object... fields) {
			return this.add(JsonUtil.createObjectNode(fields));
		}

		public C addValue(final Object value) {
			return this.add(JsonUtil.createValueNode(value));
		}

		public C addArray(final Object... values) {
			return this.add(JsonUtil.createArrayNode(values));
		}

		void prepare(final SopremoRecordTestPlan testPlan, final SopremoRecordLayout layout) {
			this.testRecords = this.getTestRecords(testPlan, layout);
			if (this.operator instanceof MockupSource) {
				// testRecords.setSopremoRecordLayout(layout.getPactSopremoRecordLayout());
				if (this.isEmpty())
					this.testRecords.setEmpty();
				else if (this.file != null) {
					Configuration configuration = new Configuration();
					SopremoUtil.setEvaluationContext(configuration, this.getContext().clone());
					SopremoUtil.setLayout(configuration, layout);
					SopremoUtil.transferFieldsToConfiguration(new JsonFormat(), SopremoFormat.class, configuration,
						JsonInputFormat.class, SopremoFileInputFormat.class);
					this.testRecords.load(JsonFormat.JsonInputFormat.class, this.file, configuration);
				}
				else
					for (final IJsonNode node : this.values) {
						final SopremoRecord record = new SopremoRecord(layout);
						record.setNode(node);
						this.testRecords.add(record);
					}
			}
		}

		@SuppressWarnings("unchecked")
		public C setEmpty() {
			this.empty = true;
			this.file = null;
			return (C) this;
		}

		/**
		 * Returns the empty.
		 * 
		 * @return the empty
		 */
		public boolean isEmpty() {
			return this.empty;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Iterator<IJsonNode> iterator() {
			if (this.isEmpty())
				return Collections.EMPTY_LIST.iterator();
			if (this.testRecords != null) {
				final RecordToJsonIterator<IJsonNode> recordToJsonIterator = new RecordToJsonIterator<IJsonNode>();
				recordToJsonIterator.setIterator(this.testRecords.iterator());
				return recordToJsonIterator;
			}
			if (this.file != null)
				return this.iteratorFromFile(this.file);
			return this.values.iterator();
		}

		protected Iterator<IJsonNode> iteratorFromFile(final String file) {
			try {
				final FSDataInputStream stream = FileSystem.get(new URI(file))
					.open(new Path(file));
				final JsonParser parser = new JsonParser(stream);
				parser.setWrappingArraySkipping(true);
				return new AbstractIterator<IJsonNode>() {
					/*
					 * (non-Javadoc)
					 * @see eu.stratosphere.util.AbstractIterator#loadNext()
					 */
					@Override
					protected IJsonNode loadNext() {
						if (parser.checkEnd())
							return this.noMoreElements();
						try {
							return parser.readValueAsTree();
						} catch (final IOException e) {
							throw new IllegalStateException(String.format(
								"Cannot parse json file %s", file), e);
						}
					}
				};
			} catch (final IOException e) {
				throw new IllegalStateException(String.format(
					"Cannot open json file %s", this.file), e);
			} catch (final URISyntaxException e) {
				// should definitely not happen, checked in #load
				throw new IllegalStateException();
			}
		}
	}

	static abstract class InternalChannel<O extends Operator<?>, C extends InternalChannel<O, C>> implements
			Iterable<IJsonNode> {
		protected String file;

		protected O operator;

		private final int index;

		public InternalChannel(final O operator, final int index) {
			this.operator = operator;
			this.index = index;
		}

		/**
		 * Initializes SopremoTestPlan.InternalChannel.
		 */
		InternalChannel() {
			this.index = 0;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof InternalChannel))
				return false;
			final InternalChannel<?, ?> other = (InternalChannel<?, ?>) obj;
			return IteratorUtil.equal(this.iterator(), other.iterator());
		}

		public void assertEquals(final ActualOutput expectedValues) {
			AssertUtil.assertIteratorEquals(this.iterator(),
				expectedValues.iterator());
		}

		int getIndex() {
			return this.index;
		}

		O getOperator() {
			return this.operator;
		}

		@Override
		public int hashCode() {
			return IteratorUtil.hashCode(this.iterator());
		}

		void setOperator(final O operator) {
			if (operator == null)
				throw new NullPointerException("operator must not be null");

			this.operator = operator;
		}

		@Override
		public String toString() {
			return IteratorUtil.toString(this.iterator(), 10);
		}

		public List<IJsonNode> getAllNodes() {
			final ArrayList<IJsonNode> list = new ArrayList<IJsonNode>();
			final Iterator<IJsonNode> iterator = this.iterator();
			while (iterator.hasNext())
				list.add(iterator.next().clone());
			return list;
		}
	}

	/**
	 * Represents the expected output of a {@link SopremotestPlan}.
	 */
	public static class ExpectedOutput extends ModifiableChannel<Source, ExpectedOutput> {

		/**
		 * Initializes an ExpectedOutput with the given index.
		 * 
		 * @param index
		 *        the index
		 */
		public ExpectedOutput(SopremoTestPlan testPlan, final int index) {
			super(testPlan, new MockupSource(index), index);
		}

		/**
		 * Initializes SopremoTestPlan.ExpectedOutput.
		 */
		ExpectedOutput() {
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * eu.stratosphere.sopremo.testing.SopremoTestPlan.ModifiableChannel#getTestRecords(eu.stratosphere.pact.testing
		 * .GenericTestPlan<SopremoRecord, SopremoTestRecords>)
		 */
		@Override
		SopremoTestRecords getTestRecords(final SopremoRecordTestPlan testPlan, final SopremoRecordLayout layout) {
			final int sinkIndex = this.findSinkIndex(testPlan);
			return testPlan.getExpectedOutput(sinkIndex, SopremoTestRecords.getTypeConfig(layout));
		}

		private int findSinkIndex(final GenericTestPlan<SopremoRecord, SopremoTestRecords> testPlan) {
			int sinkIndex = -1;
			final List<GenericDataSink> sinks = testPlan.getSinks();
			for (int index = 0; index < sinks.size(); index++)
				if (sinks.get(index).getName().equals(this.getOperator().getInputPath())) {
					sinkIndex = index;
					break;
				}
			return sinkIndex == -1 ? this.getIndex() : sinkIndex;
		}

		private double doublePrecision;

		public double getDoublePrecision() {
			return this.doublePrecision;
		}

		public ExpectedOutput setDoublePrecision(final double doublePrecision) {
			this.doublePrecision = doublePrecision;
			throw new UnsupportedOperationException("Currently unsupported; please add ticket when needed");
			// return this;
		}
	}

	/**
	 * Represents the input of a {@link SopremoTestPlan}.
	 */
	public static class Input extends ModifiableChannel<Source, Input> {

		/**
		 * Initializes an Input with the given index.
		 * 
		 * @param index
		 *        the index
		 */
		public Input(SopremoTestPlan testPlan, final int index) {
			super(testPlan, new MockupSource(index), index);
		}

		/**
		 * Initializes SopremoTestPlan.Input.
		 */
		Input() {
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * eu.stratosphere.sopremo.testing.SopremoTestPlan.ModifiableChannel#getTestRecords(eu.stratosphere.pact.testing
		 * .GenericTestPlan<SopremoRecord, SopremoTestRecords>,
		 * eu.stratosphere.sopremo.serialization.SopremoRecordLayout)
		 */
		@Override
		SopremoTestRecords getTestRecords(final SopremoRecordTestPlan testPlan, final SopremoRecordLayout layout) {
			int sourceIndex = getSourceIndex(testPlan);
			return testPlan.getInput(sourceIndex == -1 ? this.getIndex() : sourceIndex,
				SopremoTestRecords.getTypeConfig(layout));
		}

		public int getSourceIndex(final GenericTestPlan<SopremoRecord, SopremoTestRecords> testPlan) {
			int sourceIndex = -1;
			final List<GenericDataSource<?>> sources = testPlan.getSources();
			for (int index = 0; index < sources.size(); index++)
				if (sources.get(index).getName().equals(this.getOperator().getInputPath())) {
					sourceIndex = index;
					break;
				}
			return sourceIndex;
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.testing.SopremoTestPlan.ModifiableChannel#iterator()
		 */
		@Override
		public Iterator<IJsonNode> iterator() {
			if (this.operator != null && !(this.operator instanceof MockupSource)) {
				if (this.operator.isAdhoc())
					return JsonUtil.asArray(this.operator.getAdhocValues()).iterator();

				if (this.testRecords == null)
					return iteratorFromFile(this.operator.getInputPath());
			}
			return super.iterator();
		}
	}

	/**
	 * Creates a mocked {@link Sink}. This sink simply writes the received data to a temporary file.
	 */
	public static class MockupSink extends Sink {
		private final int index;

		/**
		 * Initializes a MockupSink with the given index.
		 * 
		 * @param index
		 *        the index
		 */
		public MockupSink(final int index) {
			super("file:///" + index);
			setName("Mockup Output" + index);
			this.index = index;
		}

		/**
		 * Initializes SopremoTestPlan.MockupSink.
		 */
		MockupSink() {
			super("file:///");
			this.index = 0;
		}

		@Override
		public PactModule asPactModule(final EvaluationContext context, SopremoRecordLayout layout) {
			final PactModule pactModule = new PactModule(1, 0);
			final FileDataSink contract = GenericTestPlan.createDefaultSink(this.getOutputPath());
			contract.setInput(pactModule.getInput(0));
			pactModule.addInternalOutput(contract);
			SopremoUtil.setEvaluationContext(contract.getParameters(), context);
			SopremoUtil.setLayout(contract.getParameters(), layout);
			return pactModule;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (this.getClass() != obj.getClass())
				return false;
			final MockupSink other = (MockupSink) obj;
			return this.index == other.index;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + this.index;
			return result;
		}

		@Override
		public String toString() {
			return String.format("MockupSink [%s]", this.index);
		}
	}

	/**
	 * Creates a mocked {@link Source}. This source simply reads the data from a temporary file.
	 */
	public static class MockupSource extends Source {

		private final int index;

		/**
		 * Initializes a MockupSource with the given.
		 * 
		 * @param index
		 *        the index
		 */
		public MockupSource(final int index) {
			super("file:///" + index);
			setName("Mockup-input " + index);
			this.index = index;
		}

		/**
		 * Initializes SopremoTestPlan.MockupSource.
		 */
		MockupSource() {
			super("file:///");
			this.index = 0;
		}

		@Override
		public PactModule asPactModule(final EvaluationContext context, SopremoRecordLayout layout) {
			final PactModule pactModule = new PactModule(0, 1);
			final FileDataSource contract = GenericTestPlan.createDefaultSource(this.getInputPath());
			pactModule.getOutput(0).setInput(contract);
			SopremoUtil.setEvaluationContext(contract.getParameters(), context);
			SopremoUtil.setLayout(contract.getParameters(), layout);
			return pactModule;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (this.getClass() != obj.getClass())
				return false;
			final MockupSource other = (MockupSource) obj;
			return this.index == other.index;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + this.index;
			return result;
		}

		@Override
		public String toString() {
			return String.format("MockupSource [%s]", this.index);
		}
	}

}
