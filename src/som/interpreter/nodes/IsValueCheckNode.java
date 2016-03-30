package som.interpreter.nodes;

import som.VM;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.ObjectPrims.IsValue;
import som.vm.constants.KernelObj;
import som.vmobjects.SObject.SImmutableObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


/**
 * This node is used for the instantiation of objects to check whether all
 * fields have been initialized to values, in case, the object is declared
 * as a Value.
 */
public abstract class IsValueCheckNode extends UnaryExpressionNode {

  public static IsValueCheckNode create(final SourceSection source, final ExpressionNode self) {
    return new UninitializedNode(source, self);
  }

  @Child protected ExpressionNode self;

  protected IsValueCheckNode(final SourceSection source, final ExpressionNode self) {
    super(source);
    this.self = self;
  }

  private static final class UninitializedNode extends IsValueCheckNode {
    UninitializedNode(final SourceSection source, final ExpressionNode self) {
      super(source, self);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Need to specialize node");
      return super.executeGeneric(frame);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      return specialize(frame, receiver);
    }

    private Object specialize(final VirtualFrame frame, final Object receiver) {
      if (!(receiver instanceof SImmutableObject)) {
        // can remove ourselves, this node is only used in initializers,
        // which are by definition monomorphic
        replace(self);
        return receiver;
      }

      SImmutableObject rcvr = (SImmutableObject) receiver;

      if (rcvr.isValue()) {
        return replace(new ValueCheckNode(sourceSection, self)).
            executeEvaluated(frame, receiver);
      } else {
        // neither transfer object nor value, so nothing to check
        replace(self);
        return receiver;
      }
    }
  }

  private static final class ValueCheckNode extends IsValueCheckNode {
    ValueCheckNode(final SourceSection source, final ExpressionNode self) {
      super(source, self);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      SImmutableObject rcvr = (SImmutableObject) receiver;

      boolean allFieldsContainValues = allFieldsContainValues(rcvr);
      if (allFieldsContainValues) {
        return rcvr;
      }
      return KernelObj.signalException("signalNotAValueWith:", receiver);
    }

    private boolean allFieldsContainValues(final SImmutableObject rcvr) {
      VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

      if (rcvr.field1 == null) {
        return true;
      }

      boolean result = IsValue.isObjectValue(rcvr.field1);
      if (rcvr.field2 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field2);
      if (rcvr.field3 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field3);
      if (rcvr.field4 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field4);
      if (rcvr.field5 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field5);
      if (rcvr.getExtensionObjFields() == null || !result) {
        return result;
      }

      Object[] ext = rcvr.getExtensionObjFields();
      for (Object o : ext) {
        if (!IsValue.isObjectValue(o)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeEvaluated(frame, self.executeGeneric(frame));
  }
}
