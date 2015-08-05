package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ValueProfile;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExpressionNode implements ISpecialSend {

  protected static final int INLINE_CACHE_SIZE = 6;

  protected final int contextLevel;
  private final ClassDefinitionId classDefId;
  private final ClassDefinitionId enclosingLexicalClassId;

  private final ValueProfile enclosingObj;

  public OuterObjectRead(final int contextLevel,
      final ClassDefinitionId classDefId,
      final ClassDefinitionId enclosingLexicalClassId,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.contextLevel = contextLevel;
    this.classDefId = classDefId;
    this.enclosingLexicalClassId = enclosingLexicalClassId;
    this.enclosingObj = ValueProfile.createIdentityProfile();
  }

  public ClassDefinitionId getClassId() {
    return classDefId;
  }

  @Override
  public ClassDefinitionId getLexicalClass() {
    return enclosingLexicalClassId;
  }

  @Override
  public boolean isSuperSend() { return false; }

  protected final SClass getLexicalClass(final SObjectWithoutFields rcvr) {
    SClass lexicalClass = rcvr.getSOMClass().getClassCorrespondingTo(classDefId);
    assert lexicalClass != null;
    return lexicalClass;
  }

  @Specialization(guards = "contextLevel == 0")
  public final Object doForOuterIsDirectClass(final SObjectWithoutFields receiver) {
    return enclosingObj.profile(receiver);
  }

  @Specialization(limit = "INLINE_CACHE_SIZE",
      guards = {"contextLevel != 0", "receiver.getSOMClass() == rcvrClass"})
  public final Object doForFurtherOuter(final SObjectWithoutFields receiver,
      @Cached("receiver.getSOMClass()") final SClass rcvrClass,
      @Cached("getLexicalClass(receiver)") final SClass lexicalClass) {
    return getEnclosingObject(receiver, lexicalClass);
  }

  @Specialization(guards = "contextLevel != 0", contains = "doForFurtherOuter")
  public final Object fallback(final SObjectWithoutFields receiver) {
    return getEnclosingObject(receiver, getLexicalClass(receiver));
  }

  public abstract Object executeEvaluated(Object receiver);

  protected static final boolean notSObjectWithoutFields(final Object receiver) {
    return !(receiver instanceof SObjectWithoutFields);
  }

  @Specialization(guards = "notSObjectWithoutFields(receiver)")
  public Object doObject(final Object receiver) {
    return KernelObj.kernel;
  }

  @ExplodeLoop
  private Object getEnclosingObject(final SObjectWithoutFields receiver,
      final SClass lexicalClass) {
    int ctxLevel = contextLevel - 1; // 0 is already covered with specialization
    SObjectWithoutFields enclosing = lexicalClass.getEnclosingObject();

    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getSOMClass().getEnclosingObject();
    }
    return enclosingObj.profile(enclosing);
  }
}
