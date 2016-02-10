package dym;

import java.util.HashMap;
import java.util.Map;

import som.compiler.Tags;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.EventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.SourceSection;

import dym.nodes.AllocationProfilingNode;
import dym.nodes.ArrayAllocationProfilingNode;
import dym.nodes.ControlFlowProfileNode;
import dym.nodes.CountingNode;
import dym.nodes.FieldReadProfilingNode;
import dym.nodes.InvocationProfilingNode;
import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.BranchProfile;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;
import dym.profiles.MethodCallsiteProbe;
import dym.profiles.ReadValueProfile;


/**
 * DynamicMetric is a Truffle instrumentation tool to measure a wide range of
 * dynamic metrics to characterize the behavior of executing code.
 *
 * WARNING:
 *   - designed for single-threaded use only
 *   - designed for use in interpreted mode only
 */
@Registration(id = DynamicMetrics.ID, autostart = false)
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
  private final Map<SourceSection, Counter> basicOperationCounter;

  public DynamicMetrics() {
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

  @Override
  protected void onCreate(final Env env, final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(ROOT_TAG);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          return createInvocationCountingNode(context);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(UNSPECIFIED_INVOKE);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          return createMethodCallsiteNode(context);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(NEW_OBJECT);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          AllocationProfile profile = newObjectCounter.computeIfAbsent(
              context.getInstrumentedSourceSection(), AllocationProfile::new);
          return new AllocationProfilingNode(profile);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(NEW_ARRAY);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          ArrayCreationProfile profile = newArrayCounter.computeIfAbsent(
              context.getInstrumentedSourceSection(), ArrayCreationProfile::new);
          return new ArrayAllocationProfilingNode(profile);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.SYNTAX_LITERAL);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          Counter counter = literalReadCounter.computeIfAbsent(
              context.getInstrumentedSourceSection(), Counter::new);
          return new CountingNode<>(counter);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.BASIC_PRIMITIVE_OPERATION);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          Counter counter = basicOperationCounter.computeIfAbsent(
              context.getInstrumentedSourceSection(), Counter::new);
          return new CountingNode<>(counter);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(FIELD_READ);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          ReadValueProfile p = fieldReadProfiles.computeIfAbsent(
              context.getInstrumentedSourceSection(), ReadValueProfile::new);
          return new FieldReadProfilingNode(p);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(FIELD_WRITE);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          Counter counter = fieldWriteProfiles.computeIfAbsent(
              context.getInstrumentedSourceSection(), Counter::new);
          return new CountingNode<>(counter);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.LOCAL_ARG_READ, Tags.LOCAL_VAR_READ);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          ReadValueProfile counter = localsReadProfiles.computeIfAbsent(
              context.getInstrumentedSourceSection(), ReadValueProfile::new);
          return new FieldReadProfilingNode(counter);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.LOCAL_VAR_WRITE);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          Counter counter = localsWriteProfiles.computeIfAbsent(
              context.getInstrumentedSourceSection(), Counter::new);
          return new CountingNode<>(counter);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.CONTROL_FLOW_CONDITION);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          BranchProfile profile = controlFlowProfiles.computeIfAbsent(
              context.getInstrumentedSourceSection(), BranchProfile::new);
          return new ControlFlowProfileNode(profile);
        });
  }

  @Override
  protected void onDispose(final Env env) {
    String outputFile = System.getProperty("dm.output", "dynamic-metrics.json");
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = collectData();
    JsonWriter.fileOut(data, outputFile);

    String metricsFolder = System.getProperty("dm.metrics", "metrics");
    MetricsCsvWriter.fileOut(data, metricsFolder);
  }

  private EventNode createInvocationCountingNode(final EventContext context) {
    SourceSection source = context.getInstrumentedSourceSection();
    InvocationProfile counter = methodInvocationCounter.computeIfAbsent(
        source, src -> new InvocationProfile(src));
    return new InvocationProfilingNode(this, counter);
  }

  private EventNode createMethodCallsiteNode(final EventContext context) {
    SourceSection source = context.getInstrumentedSourceSection();
    MethodCallsiteProbe probe = methodCallsiteProbes.computeIfAbsent(
        source, src -> new MethodCallsiteProbe(src));
    return new CountingNode<>(probe);
  }

  private Map<String, Map<SourceSection, ? extends JsonSerializable>> collectData() {
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = new HashMap<>();
    data.put("methodInvocationProfile", methodInvocationCounter);
    data.put("methodCallsite",          methodCallsiteProbes);
    data.put("newObjectCount",          newObjectCounter);
    data.put("newArrayCount",           newArrayCounter);
    data.put("fieldReads",              fieldReadProfiles);
    data.put("fieldWrites",             fieldWriteProfiles);
    data.put("branchProfile",           controlFlowProfiles);
    data.put("literalReads",            literalReadCounter);
    data.put("localReads",              localsReadProfiles);
    data.put("localWrites",             localsWriteProfiles);
    data.put("basicOperations",         basicOperationCounter);
    return data;
  }

}
