package dym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import som.VM;
import som.compiler.MixinDefinition;
import som.compiler.Tags;
import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import dym.nodes.AllocationProfilingNode;
import dym.nodes.ArrayAllocationProfilingNode;
import dym.nodes.ControlFlowProfileNode;
import dym.nodes.CountingNode;
import dym.nodes.FieldReadProfilingNode;
import dym.nodes.InvocationProfilingNode;
import dym.nodes.PrimitiveOperationProfilingNode;
import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.BranchProfile;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;
import dym.profiles.MethodCallsiteProbe;
import dym.profiles.PrimitiveOperationProfile;
import dym.profiles.ReadValueProfile;
import dym.profiles.ReportResultNode;
import dym.profiles.StructuralProbe;


/**
 * DynamicMetric is a Truffle instrumentation tool to measure a wide range of
 * dynamic metrics to characterize the behavior of executing code.
 *
 * WARNING:
 *   - designed for single-threaded use only
 *   - designed for use in interpreted mode only
 */
@Registration(id = DynamicMetrics.ID)
public class DynamicMetrics extends TruffleInstrument {

  public static final String ID       = "dym-dynamic-metrics";

  // Tags used by the DynamicMetrics tool
  public static final String ROOT_TAG           = "ROOT";
  public static final String UNSPECIFIED_INVOKE = "UNSPECIFIED_INVOKE"; // this is some form of invoke in the source, unclear what it is during program execution
  public static final String INVOKE_WITH_LOOKUP = "INVOKE_WITH_LOOKUP";
  public static final String NEW_OBJECT         = "NEW_OBJECT";
  public static final String NEW_ARRAY          = "NEW_ARRAY";
  public static final String CONTROL_FLOW_CONDITION  = "CONTROL_FLOW_CONDITION"; // a condition expression that results in a control-flow change

  // TODO
  public static final String FIELD_READ         = "FIELD_READ";
  public static final String FIELD_WRITE        = "FIELD_WRITE";
  public static final String ARRAY_READ         = "ARRAY_READ";
  public static final String ARRAY_WRITE        = "ARRAY_WRITE";
  public static final String LOOP_BODY          = "LOOP_BODY";

  private final Map<SourceSection, InvocationProfile> methodInvocationCounter;
  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, MethodCallsiteProbe> methodCallsiteProbes;
  private final Map<SourceSection, AllocationProfile> newObjectCounter;
  private final Map<SourceSection, ArrayCreationProfile> newArrayCounter;
  private final Map<SourceSection, ReadValueProfile> fieldReadProfiles;
  private final Map<SourceSection, Counter> fieldWriteProfiles;
  private final Map<SourceSection, BranchProfile> controlFlowProfiles;
  private final Map<SourceSection, Counter> literalReadCounter;
  private final Map<SourceSection, ReadValueProfile> localsReadProfiles;
  private final Map<SourceSection, Counter> localsWriteProfiles;
  private final Map<SourceSection, PrimitiveOperationProfile> basicOperationCounter;
  private final Map<SourceSection, Counter> loopProfiles;

  private final StructuralProbe structuralProbe;

  public DynamicMetrics() {
    // TODO: avoid this major hack, there should be some event interface
    //       or a way from the polyglot engine to obtain a reference
    structuralProbe = VM.getStructuralProbe();

    methodInvocationCounter = new HashMap<>();
    methodCallsiteProbes    = new HashMap<>();
    newObjectCounter        = new HashMap<>();
    newArrayCounter         = new HashMap<>();
    fieldReadProfiles       = new HashMap<>();
    fieldWriteProfiles      = new HashMap<>();
    controlFlowProfiles     = new HashMap<>();
    literalReadCounter      = new HashMap<>();
    localsReadProfiles      = new HashMap<>();
    localsWriteProfiles     = new HashMap<>();
    basicOperationCounter   = new HashMap<>();
    loopProfiles            = new HashMap<>();

    assert "DefaultTruffleRuntime".equals(
        Truffle.getRuntime().getClass().getSimpleName())
        : "To get metrics for the lexical, unoptimized behavior, please run this tool without Graal";
  }

  public void enterMethod() {
    methodStackDepth += 1;
    maxStackDepth = Math.max(methodStackDepth, maxStackDepth);
    assert methodStackDepth > 0;
  }

  public void leaveMethod() {
    methodStackDepth -= 1;
    assert methodStackDepth >= 0;
  }

  private <N extends ExecutionEventNode, PRO extends Counter>
    void addInstrumentation(final Instrumenter instrumenter,
        final Map<SourceSection, PRO> storageMap,
        final String[] tagsIs,
        final String[] tagsIsNot,
        final Function<SourceSection, PRO> pCtor,
        final Function<PRO, N> nCtor) {
    Builder filters = SourceSectionFilter.newBuilder();
    if (tagsIs != null && tagsIs.length > 0) {
      filters.tagIs(tagsIs);
    }
    if (tagsIsNot != null && tagsIsNot.length > 0) {
      filters.tagIsNot(tagsIsNot);
    }
    instrumenter.attachFactory(filters.build(),
        (final EventContext ctx) -> {
          PRO p = storageMap.computeIfAbsent(ctx.getInstrumentedSourceSection(),
              pCtor);
          return nCtor.apply(p);
        });
  }

  private void addRootTagInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(ROOT_TAG);
    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      RootNode root = ctx.getInstrumentedNode().getRootNode();
      assert root instanceof Invokable : "TODO: make language independent";
      InvocationProfile p = methodInvocationCounter.computeIfAbsent(
          ctx.getInstrumentedSourceSection(), ss -> new InvocationProfile(ss, (Invokable) root));
      return new InvocationProfilingNode(this, p);
    });
  }

  private static int numberOfChildren(final Node node) {
    int i = 0;
    for (@SuppressWarnings("unused") Node child : node.getChildren()) {
      i += 1;
    }
    return i;
  }

  private ExecutionEventNodeFactory addPrimitiveInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.BASIC_PRIMITIVE_OPERATION);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numSubExpr = numberOfChildren(ctx.getInstrumentedNode());

      PrimitiveOperationProfile p = basicOperationCounter.computeIfAbsent(
        ctx.getInstrumentedSourceSection(),
        (final SourceSection src) -> new PrimitiveOperationProfile(src, numSubExpr));
      return new PrimitiveOperationProfilingNode(p, ctx);
    };

    instrumenter.attachFactory(filters.build(), primExpFactory);
    return primExpFactory;
  }

  private void addPrimitiveSubexpressionInstrumentation(
      final Instrumenter instrumenter,
      final ExecutionEventNodeFactory basicPrimInstrFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.BASIC_PRIMITIVE_ARGUMENT);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findParentEventNode(basicPrimInstrFactory);

      PrimitiveOperationProfilingNode p = (PrimitiveOperationProfilingNode) parent;
      int idx = p.registerSubexpressionAndGetIdx(ctx.getInstrumentedNode());
      return new ReportResultNode(p.getProfile(), idx);
    });
  }

  @Override
  protected void onCreate(final Env env) {
    Instrumenter instrumenter = env.getInstrumenter();
    addRootTagInstrumentation(instrumenter);

    addInstrumentation(instrumenter, methodCallsiteProbes,
        new String[] {UNSPECIFIED_INVOKE}, new String[] {Tags.EAGERLY_WRAPPED},
        MethodCallsiteProbe::new, CountingNode<Counter>::new);

    addInstrumentation(instrumenter, newObjectCounter,
        new String[] {NEW_OBJECT}, new String[] {},
        AllocationProfile::new, AllocationProfilingNode::new);
    addInstrumentation(instrumenter, newArrayCounter,
        new String[] {NEW_ARRAY}, new String[] {},
        ArrayCreationProfile::new, ArrayAllocationProfilingNode::new);

    addInstrumentation(instrumenter, literalReadCounter,
        new String[] {Tags.SYNTAX_LITERAL}, new String[] {},
        Counter::new, CountingNode<Counter>::new);

    ExecutionEventNodeFactory basicPrimInstrFact = addPrimitiveInstrumentation(instrumenter);
    addPrimitiveSubexpressionInstrumentation(instrumenter, basicPrimInstrFact);

    addInstrumentation(instrumenter, fieldReadProfiles,
        new String[] {Tags.FIELD_READ}, new String[] {},
        ReadValueProfile::new,
        FieldReadProfilingNode::new);
    addInstrumentation(instrumenter, localsReadProfiles,
        new String[] {Tags.LOCAL_ARG_READ, Tags.LOCAL_VAR_READ}, new String[] {},
        ReadValueProfile::new, FieldReadProfilingNode::new);

    addInstrumentation(instrumenter, fieldWriteProfiles,
        new String[] {Tags.FIELD_WRITE}, new String[] {},
        Counter::new, CountingNode<Counter>::new);
    addInstrumentation(instrumenter, localsWriteProfiles,
        new String[] {Tags.LOCAL_VAR_WRITE}, new String[] {},
        Counter::new, CountingNode<Counter>::new);

    addInstrumentation(instrumenter, controlFlowProfiles,
        new String[] {Tags.CONTROL_FLOW_CONDITION}, new String[] {},
        BranchProfile::new, ControlFlowProfileNode::new);

    addInstrumentation(instrumenter, loopProfiles,
        new String[] {Tags.LOOP_BODY}, new String[] {},
        Counter::new, CountingNode<Counter>::new);
  }

  @Override
  protected void onDispose(final Env env) {
    String outputFile = System.getProperty("dm.output", "dynamic-metrics.json");
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = collectData();
    JsonWriter.fileOut(data, outputFile);

    String metricsFolder = System.getProperty("dm.metrics", "metrics");
    MetricsCsvWriter.fileOut(data, metricsFolder, structuralProbe);

  }

  private Map<String, Map<SourceSection, ? extends JsonSerializable>> collectData() {
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = new HashMap<>();
    data.put(JsonWriter.METHOD_INVOCATION_PROFILE, methodInvocationCounter);
    data.put(JsonWriter.METHOD_CALLSITE,          methodCallsiteProbes);
    data.put(JsonWriter.NEW_OBJECT_COUNT,         newObjectCounter);
    data.put(JsonWriter.NEW_ARRAY_COUNT,          newArrayCounter);
    data.put(JsonWriter.FIELD_READS,              fieldReadProfiles);
    data.put(JsonWriter.FIELD_WRITES,             fieldWriteProfiles);
    data.put(JsonWriter.BRANCH_PROFILES,          controlFlowProfiles);
    data.put(JsonWriter.LITERAL_READS,            literalReadCounter);
    data.put(JsonWriter.LOCAL_READS,              localsReadProfiles);
    data.put(JsonWriter.LOCAL_WRITES,             localsWriteProfiles);
    data.put(JsonWriter.BASIC_OPERATIONS,         basicOperationCounter);
    data.put(JsonWriter.LOOPS,                    loopProfiles);
    return data;
  }

}
