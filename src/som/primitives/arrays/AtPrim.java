package som.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SArguments;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.primitives.Primitive;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import tools.dym.Tags.ArrayRead;


@GenerateNodeFactory
@Primitive(primitive = "array:at:", selector = "at:", receiverType = SArray.class, inParser = false)
public abstract class AtPrim extends BinaryBasicOperation {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Child protected AbstractMessageSendNode exception;

  protected AtPrim(final boolean eagWrap, final SourceSection source) {
    super(eagWrap, source);
    exception = MessageSendNode.createGeneric(
        Symbols.symbolFor("signalWith:index:"), null, getSourceSection());
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ArrayRead.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  private Object triggerException(final VirtualFrame frame,
      final SArray arr, final long idx) {
    int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
    return exception.doPreEvaluated(frame,
        new Object[] {KernelObj.indexOutOfBoundsClass, arr, idx});
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final Object doEmptySArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    if (idx < 1 || idx > receiver.getEmptyStorage(storageType)) {
      return triggerException(frame, receiver, idx);
    }
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final Object doPartiallyEmptySArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    try {
      return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    try {
      return receiver.getObjectStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    try {
      return receiver.getLongStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(frame, receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    try {
      return receiver.getDoubleStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(frame, receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final VirtualFrame frame,
      final SArray receiver, final long idx) {
    try {
      return receiver.getBooleanStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(frame, receiver, idx);
    }
  }
}
