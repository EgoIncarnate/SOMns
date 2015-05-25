package som.interpreter.nodes;

import som.compiler.ClassDefinition;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public abstract class SlotAccessNode extends ExpressionNode {

  public SlotAccessNode() { super(null); }

  public abstract Object doRead(VirtualFrame frame, SObject rcvr);

  public static final class SlotReadNode extends SlotAccessNode {
    // TODO: may be, we can get rid of this completely?? could directly use AbstractReadFieldNode
    // TODO: we only got read support at the moment
    @Child protected AbstractReadFieldNode read;

    public SlotReadNode(final AbstractReadFieldNode read) {
      this.read = read;
    }

    @Override
    public Object doRead(final VirtualFrame frame, final SObject rcvr) {
      return read.read(rcvr);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return read.read((SObject) SArguments.rcvr(frame));
    }
  }

  // TODO: try to remove, should only be used in getCallTarget version of mutator slots
  public static final class SlotWriteNode extends ExpressionNode {
    @Child protected AbstractWriteFieldNode write;

    public SlotWriteNode(final AbstractWriteFieldNode write) {
      super(null);
      this.write = write;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return write.write((SObject) SArguments.rcvr(frame), SArguments.arg(frame, 1));
    }
  }

  public static final class ClassSlotAccessNode extends SlotAccessNode {
    private final ClassDefinition classDefinition;
    @Child protected DirectCallNode classObjectInstantiation;

    @Child protected AbstractReadFieldNode  read;
    @Child protected AbstractWriteFieldNode write;

    public ClassSlotAccessNode(final ClassDefinition classDefinition,
        final AbstractReadFieldNode read, final AbstractWriteFieldNode write) {
      this.read = read;
      this.write = write;
      this.classDefinition = classDefinition;
    }

    @Override
    public SClass doRead(final VirtualFrame frame, final SObject rcvr) {
      Object cacheValue = read.read(rcvr);

      // check whether cache is initialized with class object
      if (cacheValue == Nil.nilObject) {
        SClass classObject = instantiateClassObject(frame, rcvr);
        write.write(rcvr, classObject);
        return classObject;
      } else {
        assert cacheValue instanceof SClass;
        return (SClass) cacheValue;
      }
    }

    private SClass instantiateClassObject(final VirtualFrame frame,
        final SObject rcvr) {
      if (classObjectInstantiation == null) {
        Invokable invokable = classDefinition.getSuperclassResolutionInvokable();
        classObjectInstantiation = Truffle.getRuntime().createDirectCallNode(
            invokable.createCallTarget());
      }

      SClass superClass = (SClass) classObjectInstantiation.call(frame,
          new Object[] {rcvr});
      SClass classObject = classDefinition.instantiateClass(rcvr, superClass);
      return classObject;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return doRead(frame, (SObject) SArguments.rcvr(frame));
    }
  }
}
